package net.inkyquill.delightfulkitchen.menu;

import net.inkyquill.delightfulkitchen.block.ModBlocks;
import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryService;
import net.inkyquill.delightfulkitchen.kitchen.KitchenScanResult;
import net.inkyquill.delightfulkitchen.kitchen.KitchenScanner;
import net.inkyquill.delightfulkitchen.kitchen.KitchenServices;
import net.inkyquill.delightfulkitchen.kitchen.execution.KitchenPlanExecutionResult;
import net.inkyquill.delightfulkitchen.kitchen.execution.KitchenPlanExecutor;
import net.inkyquill.delightfulkitchen.kitchen.execution.KitchenPlanFingerprint;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;
import net.inkyquill.delightfulkitchen.kitchen.recipe.RecipePlan;
import net.inkyquill.delightfulkitchen.menu.chef.ChefCounterClientState;
import net.inkyquill.delightfulkitchen.menu.chef.ChefCounterServerSession;
import net.inkyquill.delightfulkitchen.menu.chef.ChefCounterViewState;
import net.inkyquill.delightfulkitchen.menu.chef.ChefQueueResultView;
import net.inkyquill.delightfulkitchen.network.ModPayloads;
import net.inkyquill.delightfulkitchen.network.payload.ClientboundChefCounterDetailsPayload;
import net.inkyquill.delightfulkitchen.network.payload.ClientboundChefCounterQueueResultPayload;
import net.inkyquill.delightfulkitchen.network.payload.ClientboundChefCounterSyncPayload;
import net.inkyquill.delightfulkitchen.network.payload.ServerboundChefCounterConfirmPayload;
import net.inkyquill.delightfulkitchen.network.payload.ServerboundChefCounterSelectPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ChefCounterMenu extends AbstractContainerMenu {
    private static final int DATA_SIZE = 9;
    private static final int DATA_FLOOR_COUNT = 0;
    private static final int DATA_INVENTORY_COUNT = 1;
    private static final int DATA_MULTIPLE_COUNTERS = 2;
    private static final int DATA_RECIPE_COUNT = 3;
    private static final int DATA_READY_COUNT = 4;
    private static final int DATA_INTERMEDIATE_COUNT = 5;
    private static final int DATA_BLOCKED_COUNT = 6;
    private static final int DATA_WATER_PROVIDER_COUNT = 7;
    private static final int DATA_FREEZER_COUNT = 8;

    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final Player owner;
    private final ChefCounterClientState clientState;
    private final ChefCounterServerSession serverSession;
    private boolean initialSyncSent;

    public ChefCounterMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL, new SimpleContainerData(DATA_SIZE), null);
    }

    public ChefCounterMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
        this(containerId, playerInventory, access, new SimpleContainerData(DATA_SIZE), createServerSession(access));
    }

    private ChefCounterMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access, ContainerData data,
                            ChefCounterServerSession serverSession) {
        super(ModMenuTypes.CHEF_COUNTER.get(), containerId);
        this.access = access;
        this.data = data;
        this.owner = playerInventory.player;
        this.clientState = new ChefCounterClientState();
        this.serverSession = serverSession;
        copySessionData();
        addDataSlots(data);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 128 + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 186));
        }
    }

    private static ChefCounterServerSession createServerSession(ContainerLevelAccess access) {
        return access.evaluate(ChefCounterServerSession::new).orElse(null);
    }

    public KitchenPlanExecutionResult confirmPlan(RecipePlan plan, KitchenPlanFingerprint fingerprint) {
        return access.evaluate((level, pos) -> confirmPlan(level, pos, plan, fingerprint))
                .orElseGet(() -> KitchenPlanExecutionResult.blocked(PlanningBlocker.KITCHEN_CHANGED));
    }

    public ChefCounterClientState clientState() {
        return clientState;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!initialSyncSent && serverSession != null && owner instanceof ServerPlayer serverPlayer) {
            initialSyncSent = true;
            sendFullState(serverPlayer, 0, 1);
        }
    }

    public void handleChefCounterSync(ClientboundChefCounterSyncPayload payload) {
        clientState.handleSync(payload);
        data.set(DATA_FLOOR_COUNT, payload.floorCount());
        data.set(DATA_INVENTORY_COUNT, payload.inventoryCount());
        data.set(DATA_MULTIPLE_COUNTERS, payload.hasMultipleCounters() ? 1 : 0);
        data.set(DATA_RECIPE_COUNT, payload.recipeCount());
        data.set(DATA_READY_COUNT, payload.readyCount());
        data.set(DATA_INTERMEDIATE_COUNT, payload.intermediateCount());
        data.set(DATA_BLOCKED_COUNT, payload.blockedCount());
        data.set(DATA_WATER_PROVIDER_COUNT, payload.waterProviderCount());
        data.set(DATA_FREEZER_COUNT, payload.freezerCount());
    }

    public void handleChefCounterDetails(ClientboundChefCounterDetailsPayload payload) {
        clientState.handleDetails(payload);
    }

    public void handleChefCounterQueueResult(ClientboundChefCounterQueueResultPayload payload) {
        clientState.handleQueueResult(payload);
    }

    public void handleClientState(ChefCounterViewState state) {
        clientState.handleState(state);
    }

    public void handleChefCounterSelect(ServerboundChefCounterSelectPayload payload) {
        if (owner instanceof ServerPlayer serverPlayer) {
            handleClientSelect(serverPlayer, payload.selectedIndex(), payload.requestedQuantity());
        }
    }

    public void handleChefCounterConfirm(ServerboundChefCounterConfirmPayload payload) {
        if (owner instanceof ServerPlayer serverPlayer) {
            handleClientConfirm(serverPlayer, payload.selectedIndex(), payload.requestedQuantity(), payload.fingerprint());
        }
    }

    public void handleClientSelect(ServerPlayer player, int index, int quantity) {
        if (serverSession == null || player.containerMenu != this) {
            return;
        }
        ClientboundChefCounterDetailsPayload detailsPayload = serverSession.detailsPayload(containerId, index, quantity);
        copySessionData();
        ModPayloads.sendToPlayer(player, serverSession.syncPayload(containerId));
        ModPayloads.sendToPlayer(player, detailsPayload);
    }

    public void handleClientConfirm(ServerPlayer player, int index, int quantity, KitchenPlanFingerprint fingerprint) {
        if (serverSession == null || player.containerMenu != this) {
            return;
        }

        ChefCounterServerSession.Confirmation confirmation = serverSession.confirm(index, quantity, fingerprint);
        copySessionData();
        ModPayloads.sendToPlayer(player, serverSession.queueResultPayload(containerId, confirmation));
        ModPayloads.sendToPlayer(player, serverSession.syncPayload(containerId));
        ModPayloads.sendToPlayer(player, detailsPayload(containerId, confirmation));
    }

    static ClientboundChefCounterDetailsPayload detailsPayload(int containerId,
                                                               ChefCounterServerSession.Confirmation confirmation) {
        return new ClientboundChefCounterDetailsPayload(containerId, confirmation.details());
    }

    private static KitchenPlanExecutionResult confirmPlan(Level level, BlockPos pos, RecipePlan plan,
                                                          KitchenPlanFingerprint fingerprint) {
        KitchenScanResult scan = KitchenScanner.scan(level, pos);
        if (!scan.hasKitchenFloor()) {
            return KitchenPlanExecutionResult.blocked(PlanningBlocker.KITCHEN_CHANGED);
        }

        var inventoryEndpoints = KitchenServices.inventoryEndpoints(level, scan, pos);
        var inventory = new KitchenInventoryService(inventoryEndpoints);
        var stations = KitchenServices.stationService(level, scan);
        var executor = new KitchenPlanExecutor(inventory, stations, scan);
        return executor.confirm(plan, fingerprint);
    }

    public int getFloorCount() {
        return data.get(DATA_FLOOR_COUNT);
    }

    public int getInventoryCount() {
        return data.get(DATA_INVENTORY_COUNT);
    }

    public boolean hasMultipleCounters() {
        return data.get(DATA_MULTIPLE_COUNTERS) != 0;
    }

    public int getWaterProviderCount() {
        return data.get(DATA_WATER_PROVIDER_COUNT);
    }

    public boolean hasWaterProvider() {
        return getWaterProviderCount() > 0;
    }

    public int getFreezerCount() {
        return data.get(DATA_FREEZER_COUNT);
    }

    public boolean hasFreezer() {
        return getFreezerCount() > 0;
    }

    public int getRecipeCount() {
        return data.get(DATA_RECIPE_COUNT);
    }

    public int getReadyCount() {
        return data.get(DATA_READY_COUNT);
    }

    public int getIntermediateCount() {
        return data.get(DATA_INTERMEDIATE_COUNT);
    }

    public int getBlockedCount() {
        return data.get(DATA_BLOCKED_COUNT);
    }

    private void sendFullState(ServerPlayer player, int selectedIndex, int quantity) {
        ClientboundChefCounterDetailsPayload detailsPayload = serverSession.detailsPayload(containerId, selectedIndex,
                quantity);
        copySessionData();
        ModPayloads.sendToPlayer(player, serverSession.syncPayload(containerId));
        ModPayloads.sendToPlayer(player, detailsPayload);
        if (serverSession.floorCount() == 0) {
            ModPayloads.sendToPlayer(player, new ClientboundChefCounterQueueResultPayload(containerId,
                    ChefQueueResultView.blocked(PlanningBlocker.KITCHEN_CHANGED)));
        }
    }

    private void copySessionData() {
        if (serverSession == null) {
            return;
        }
        data.set(DATA_FLOOR_COUNT, serverSession.floorCount());
        data.set(DATA_INVENTORY_COUNT, serverSession.inventoryCount());
        data.set(DATA_MULTIPLE_COUNTERS, serverSession.hasMultipleCounters() ? 1 : 0);
        data.set(DATA_RECIPE_COUNT, serverSession.recipeCount());
        data.set(DATA_READY_COUNT, serverSession.readyCount());
        data.set(DATA_INTERMEDIATE_COUNT, serverSession.intermediateCount());
        data.set(DATA_BLOCKED_COUNT, serverSession.blockedCount());
        data.set(DATA_WATER_PROVIDER_COUNT, serverSession.waterProviderCount());
        data.set(DATA_FREEZER_COUNT, serverSession.freezerCount());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.CHEF_COUNTER.get());
    }
}
