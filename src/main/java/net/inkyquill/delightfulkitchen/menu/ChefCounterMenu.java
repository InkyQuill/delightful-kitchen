package net.inkyquill.delightfulkitchen.menu;

import net.inkyquill.delightfulkitchen.block.ModBlocks;
import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryService;
import net.inkyquill.delightfulkitchen.kitchen.KitchenScanResult;
import net.inkyquill.delightfulkitchen.kitchen.KitchenScanner;
import net.inkyquill.delightfulkitchen.kitchen.KitchenServices;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanState;
import net.inkyquill.delightfulkitchen.kitchen.recipe.RecipeIndex;
import net.inkyquill.delightfulkitchen.kitchen.recipe.RecipePlan;
import net.inkyquill.delightfulkitchen.kitchen.recipe.RecipePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Set;

public class ChefCounterMenu extends AbstractContainerMenu {
    private static final int DATA_SIZE = 7;
    private static final int DATA_FLOOR_COUNT = 0;
    private static final int DATA_MEMBER_COUNT = 1;
    private static final int DATA_MULTIPLE_COUNTERS = 2;
    private static final int DATA_RECIPE_COUNT = 3;
    private static final int DATA_READY_COUNT = 4;
    private static final int DATA_INTERMEDIATE_COUNT = 5;
    private static final int DATA_BLOCKED_COUNT = 6;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    public ChefCounterMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL, new SimpleContainerData(DATA_SIZE));
    }

    public ChefCounterMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
        this(containerId, playerInventory, access, createServerData(access));
    }

    private ChefCounterMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access, ContainerData data) {
        super(ModMenuTypes.CHEF_COUNTER.get(), containerId);
        this.access = access;
        this.data = data;
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

    private static ContainerData createServerData(ContainerLevelAccess access) {
        SimpleContainerData data = new SimpleContainerData(DATA_SIZE);
        KitchenScanResult scan = access.evaluate(KitchenScanner::scan)
                .orElseGet(() -> new KitchenScanResult(false, Set.of(), List.of(), List.of()));
        data.set(DATA_FLOOR_COUNT, scan.floorCount());
        data.set(DATA_MULTIPLE_COUNTERS, scan.hasMultipleCounters() ? 1 : 0);

        if (!scan.hasKitchenFloor()) {
            return data;
        }

        access.evaluate((level, pos) -> populatePlanningData(data, level, pos, scan));
        return data;
    }

    private static boolean populatePlanningData(SimpleContainerData data, Level level, BlockPos pos,
                                                KitchenScanResult scan) {
        var endpoints = KitchenServices.inventoryEndpoints(level, scan, pos);
        data.set(DATA_MEMBER_COUNT, endpoints.size());

        var inventory = new KitchenInventoryService(endpoints);
        var recipeIndex = RecipeIndex.build(level.getRecipeManager(), level.registryAccess());
        var planner = new RecipePlanner(recipeIndex, KitchenServices.stationTypes(level, scan));
        List<RecipePlan> plans = planner.planAllEdible(inventory.snapshot());

        data.set(DATA_RECIPE_COUNT, plans.size());
        data.set(DATA_READY_COUNT, countPlans(plans, PlanState.READY));
        data.set(DATA_INTERMEDIATE_COUNT, countPlans(plans, PlanState.CRAFTABLE_WITH_INTERMEDIATES));
        data.set(DATA_BLOCKED_COUNT, countPlans(plans, PlanState.BLOCKED));
        return true;
    }

    private static int countPlans(List<RecipePlan> plans, PlanState state) {
        int count = 0;
        for (RecipePlan plan : plans) {
            if (plan.state() == state) {
                count++;
            }
        }
        return count;
    }

    public int getFloorCount() {
        return data.get(DATA_FLOOR_COUNT);
    }

    public int getMemberCount() {
        return data.get(DATA_MEMBER_COUNT);
    }

    public boolean hasMultipleCounters() {
        return data.get(DATA_MULTIPLE_COUNTERS) != 0;
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

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.CHEF_COUNTER.get());
    }
}
