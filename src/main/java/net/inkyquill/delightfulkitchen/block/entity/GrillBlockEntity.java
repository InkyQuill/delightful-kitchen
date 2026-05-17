package net.inkyquill.delightfulkitchen.block.entity;

import net.inkyquill.delightfulkitchen.block.GrillBlock;
import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryService;
import net.inkyquill.delightfulkitchen.kitchen.KitchenScanner;
import net.inkyquill.delightfulkitchen.kitchen.KitchenServices;
import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStation;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStationAcceptResult;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStationJob;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStationStatus;
import net.inkyquill.delightfulkitchen.menu.GrillMenu;
import net.inkyquill.delightfulkitchen.util.InventoryHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Block entity for the Grill.
 * <p>
 * Slot layout:
 * <ul>
 *   <li>0-5  : input (cooking) slots</li>
 *   <li>6-11 : output slots (one per input slot)</li>
 * </ul>
 * Uses campfire cooking recipes - the same type as Farmer's Delight's Stove.
 */
public class GrillBlockEntity extends BlockEntity
        implements Container, MenuProvider, Nameable, KitchenStation {

    public static final int GRILL_SLOTS      = 6;
    public static final int SLOT_INPUT_START  = 0;
    public static final int SLOT_OUTPUT_START = GRILL_SLOTS;
    public static final int SLOT_FUEL         = GRILL_SLOTS * 2; // = 12
    public static final int SLOT_COUNT        = GRILL_SLOTS * 2 + 1; // = 13

    public static final int DEFAULT_COOK_TIME = 600;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final int[]       cookTimes    = new int[GRILL_SLOTS];
    private final int[]       cookTotals   = new int[GRILL_SLOTS];
    private final ItemStack[] pending      = new ItemStack[GRILL_SLOTS];
    private final KitchenStationJob[] queuedJobs = new KitchenStationJob[GRILL_SLOTS];
    private final boolean[] queuedSlots = new boolean[GRILL_SLOTS];
    private final boolean[] outputBlocked = new boolean[GRILL_SLOTS];
    private int burnTime = 0;
    private int burnTimeTotal = 200;
    @Nullable private Component customName = null;

    // Sync 6 cook times + 6 totals + burnTime + burnTimeTotal to the menu
    private final ContainerData data = new ContainerData() {
        @Override public int get(int i) {
            if (i < GRILL_SLOTS) return cookTimes[i];
            if (i < GRILL_SLOTS * 2) return cookTotals[i - GRILL_SLOTS];
            if (i == 12) return burnTime;
            if (i == 13) return burnTimeTotal;
            return 0;
        }
        @Override public void set(int i, int v) {
            if (i < GRILL_SLOTS) cookTimes[i] = v;
            else if (i < GRILL_SLOTS * 2) cookTotals[i - GRILL_SLOTS] = v;
            else if (i == 12) burnTime = v;
            else if (i == 13) burnTimeTotal = v;
        }
        @Override public int getCount() { return 14; }
    };

    public GrillBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.GRILL.get(), pos, state);
    }

    GrillBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        Arrays.fill(cookTotals, DEFAULT_COOK_TIME);
    }

    // ------------------------------------------------------------------
    // Server tick
    // ------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, GrillBlockEntity be) {
        // Autonomous: try to start cooking items in input slots that aren't active yet
        for (int i = 0; i < GRILL_SLOTS; i++) {
            if (!be.isQueuedSlot(i) && be.pending[i] == null && !be.items.get(SLOT_INPUT_START + i).isEmpty()) {
                be.tryStartCooking(level, pos, state, i);
            }
        }

        // Process pending slots using fuel
        boolean anyPending = false;
        for (int i = 0; i < GRILL_SLOTS; i++) {
            if (be.pending[i] == null) continue;
            anyPending = true;

            if (be.outputBlocked[i]) {
                be.finishSlot(level, pos, i);
                continue;
            }

            if (be.burnTime == 0) be.refuel();
            if (be.burnTime > 0) {
                be.burnTime--;
                be.cookTimes[i]++;
                if (be.cookTimes[i] >= be.cookTotals[i]) be.finishSlot(level, pos, i);
            }
        }

        // Update LIT blockstate
        boolean shouldBeLit = be.burnTime > 0 && anyPending;
        if (state.getValue(GrillBlock.LIT) != shouldBeLit) {
            level.setBlock(pos, state.setValue(GrillBlock.LIT, shouldBeLit), 3);
        }

        be.setChanged();
    }

    private void tryStartCooking(Level level, BlockPos pos, BlockState state, int slot) {
        ItemStack input = items.get(SLOT_INPUT_START + slot);
        if (input.isEmpty()) return;

        List<RecipeHolder<CampfireCookingRecipe>> recipes = level.getRecipeManager()
                .getAllRecipesFor(RecipeType.CAMPFIRE_COOKING);
        for (RecipeHolder<CampfireCookingRecipe> holder : recipes) {
            CampfireCookingRecipe recipe = holder.value();
            if (recipe.getIngredients().isEmpty()) continue;
            if (recipe.getIngredients().get(0).test(input)) {
                input.shrink(1);
                if (input.isEmpty()) items.set(SLOT_INPUT_START + slot, ItemStack.EMPTY);

                pending[slot] = recipe.getResultItem(level.registryAccess()).copy();
                cookTimes[slot] = 0;
                cookTotals[slot] = recipe.getCookingTime();

                if (!state.getValue(GrillBlock.LIT)) {
                    level.setBlock(pos, state.setValue(GrillBlock.LIT, true), 3);
                }
                setChanged();
                return;
            }
        }
    }

    private void finishSlot(Level level, BlockPos pos, int i) {
        if (pending[i] == null) return;

        if (isQueuedSlot(i)) {
            finishQueuedSlot(level, i);
            return;
        }

        // Try pushing to kitchen network first
        ItemStack result = pending[i];
        if (InventoryHelper.pushToKitchen(level, pos, result)) {
            // Fully pushed
            items.set(SLOT_INPUT_START + i, ItemStack.EMPTY);
            pending[i] = null;
            cookTimes[i] = 0;
            return;
        }

        // Fall back to internal output slot
        int outIdx = SLOT_OUTPUT_START + i;
        ItemStack existing = items.get(outIdx);
        if (existing.isEmpty()) {
            items.set(outIdx, pending[i]);
        } else if (ItemStack.isSameItemSameComponents(existing, pending[i])
                && existing.getCount() + pending[i].getCount() <= existing.getMaxStackSize()) {
            existing.grow(pending[i].getCount());
        } else {
            return; // output backed up; keep cooking time frozen until space frees
        }
        items.set(SLOT_INPUT_START + i, ItemStack.EMPTY);
        pending[i] = null;
        cookTimes[i] = 0;
    }

    private void finishQueuedSlot(Level level, int i) {
        ItemStack remainder = routeOutput(level, pending[i]);
        finishQueuedSlotWithRemainder(i, remainder);
    }

    void finishQueuedSlotWithRemainder(int i, ItemStack remainder) {
        if (remainder.isEmpty()) {
            queuedJobs[i] = null;
            queuedSlots[i] = false;
            pending[i] = null;
            outputBlocked[i] = false;
            cookTimes[i] = 0;
            setChanged();
            return;
        }

        pending[i] = remainder.copy();
        outputBlocked[i] = true;
        setChanged();
    }

    private ItemStack routeOutput(Level level, ItemStack stack) {
        var scan = KitchenScanner.scan(level, worldPosition);
        var endpoints = KitchenServices.inventoryEndpoints(level, scan, worldPosition);
        var inventory = new KitchenInventoryService(endpoints);
        return inventory.insert(stack, false);
    }

    private void refuel() {
        ItemStack fuel = items.get(SLOT_FUEL);
        if (fuel.isEmpty()) return;
        int bt = getBurnTime(fuel);
        if (bt <= 0) return;
        burnTimeTotal = bt;
        burnTime = bt;
        fuel.shrink(1);
        if (fuel.isEmpty()) items.set(SLOT_FUEL, ItemStack.EMPTY);
        setChanged();
    }

    private static int getBurnTime(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        var fuel = stack.getItemHolder().getData(
                net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps.FURNACE_FUELS);
        return fuel != null ? fuel.burnTime() : 0;
    }

    // ------------------------------------------------------------------
    // Container
    // ------------------------------------------------------------------

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }

    @Override
    public void setItem(int slot, ItemStack stack) { items.set(slot, stack); setChanged(); }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot >= SLOT_INPUT_START && slot < SLOT_OUTPUT_START) return true; // input slots
        if (slot >= SLOT_OUTPUT_START && slot < SLOT_FUEL) return false; // output slots
        if (slot == SLOT_FUEL) return getBurnTime(stack) > 0; // fuel slot
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5,
                        worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override public void clearContent() { items.replaceAll(ignored -> ItemStack.EMPTY); setChanged(); }

    // ------------------------------------------------------------------
    // KitchenStation
    // ------------------------------------------------------------------

    @Override
    public KitchenStationType stationType() {
        return KitchenStationType.GRILL;
    }

    @Override
    public int queueCapacity() {
        return GRILL_SLOTS;
    }

    @Override
    public int queuedJobCount() {
        int count = 0;
        for (int i = 0; i < GRILL_SLOTS; i++) {
            if (isQueuedSlot(i) || pending[i] != null || !items.get(SLOT_INPUT_START + i).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public KitchenStationStatus stationStatus() {
        if (hasBlockedOutput()) {
            return KitchenStationStatus.OUTPUT_BLOCKED;
        }
        if (findEmptyQueueSlot() == -1) {
            return KitchenStationStatus.QUEUE_FULL;
        }
        if (hasQueuedJobOrPendingOutput()) {
            return KitchenStationStatus.RUNNING;
        }
        return KitchenStationStatus.IDLE;
    }

    @Override
    public KitchenStationAcceptResult canAccept(KitchenStationJob job) {
        if (job.stationType() != KitchenStationType.GRILL) {
            return KitchenStationAcceptResult.blocked(PlanningBlocker.MISSING_STATION);
        }
        if (hasBlockedOutput()) {
            return KitchenStationAcceptResult.blocked(PlanningBlocker.OUTPUT_STORAGE_FULL);
        }
        if (findEmptyQueueSlot() == -1) {
            return KitchenStationAcceptResult.blocked(PlanningBlocker.STATION_QUEUE_FULL);
        }
        if (!canStoreFuel(job)) {
            return KitchenStationAcceptResult.blocked(PlanningBlocker.STATION_QUEUE_FULL);
        }
        return KitchenStationAcceptResult.acceptedResult();
    }

    @Override
    public KitchenStationAcceptResult accept(KitchenStationJob job) {
        KitchenStationAcceptResult result = canAccept(job);
        if (!result.accepted()) {
            return result;
        }

        int slot = findEmptyQueueSlot();
        if (slot == -1) {
            return KitchenStationAcceptResult.blocked(PlanningBlocker.STATION_QUEUE_FULL);
        }

        queuedJobs[slot] = job;
        queuedSlots[slot] = true;
        pending[slot] = job.outputs().getFirst().copy();
        outputBlocked[slot] = false;
        cookTimes[slot] = 0;
        cookTotals[slot] = Math.max(1, job.processingTime());
        job.fuel().ifPresent(this::addFuel);
        setChanged();
        return KitchenStationAcceptResult.acceptedResult();
    }

    @Override
    public BlockPos stationPos() {
        return worldPosition.immutable();
    }

    private int findEmptyQueueSlot() {
        for (int i = 0; i < GRILL_SLOTS; i++) {
            if (!isQueuedSlot(i) && pending[i] == null && items.get(SLOT_INPUT_START + i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private boolean hasBlockedOutput() {
        for (boolean blocked : outputBlocked) {
            if (blocked) {
                return true;
            }
        }
        return false;
    }

    private boolean hasQueuedJobOrPendingOutput() {
        for (int i = 0; i < GRILL_SLOTS; i++) {
            if (isQueuedSlot(i) || pending[i] != null) {
                return true;
            }
        }
        return false;
    }

    private boolean isQueuedSlot(int slot) {
        return queuedJobs[slot] != null || queuedSlots[slot];
    }

    private void addFuel(ItemStack fuel) {
        if (fuel.isEmpty()) {
            return;
        }
        ItemStack existing = items.get(SLOT_FUEL);
        if (existing.isEmpty()) {
            if (fuel.getCount() > fuel.getMaxStackSize()) {
                throw new IllegalStateException("Fuel stack exceeds maximum stack size");
            }
            items.set(SLOT_FUEL, fuel.copy());
            return;
        }

        if (!ItemStack.isSameItemSameComponents(existing, fuel)) {
            throw new IllegalStateException("Fuel stack is not compatible with the existing grill fuel");
        }
        int remainingCapacity = existing.getMaxStackSize() - existing.getCount();
        if (fuel.getCount() > remainingCapacity) {
            throw new IllegalStateException("Fuel stack exceeds available grill fuel capacity");
        }
        existing.grow(fuel.getCount());
    }

    private boolean canStoreFuel(KitchenStationJob job) {
        return job.fuel()
                .map(this::canStoreFuel)
                .orElse(true);
    }

    private boolean canStoreFuel(ItemStack fuel) {
        if (fuel.isEmpty()) {
            return true;
        }
        ItemStack existing = items.get(SLOT_FUEL);
        if (existing.isEmpty()) {
            return fuel.getCount() <= fuel.getMaxStackSize();
        }
        if (!ItemStack.isSameItemSameComponents(existing, fuel)) {
            return false;
        }
        return fuel.getCount() <= existing.getMaxStackSize() - existing.getCount();
    }

    // ------------------------------------------------------------------
    // MenuProvider / Nameable
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("block.delightful_kitchen.grill");
    }

    @Override public Component getName() { return getDisplayName(); }
    @Override @Nullable public Component getCustomName() { return customName; }
    public void setCustomName(Component name) { this.customName = name; setChanged(); }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new GrillMenu(id, playerInventory, this, data,
                ContainerLevelAccess.create(level, worldPosition));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    public void dropContents(Level level, BlockPos pos) {
        for (ItemStack stack : items) {
            if (!stack.isEmpty())
                net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
        for (ItemStack p : pending) {
            if (p != null && !p.isEmpty())
                net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), p);
        }
        items.replaceAll(ignored -> ItemStack.EMPTY);
        Arrays.fill(pending, null);
        Arrays.fill(queuedJobs, null);
        Arrays.fill(queuedSlots, false);
        Arrays.fill(outputBlocked, false);
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putIntArray("CookTimes",  cookTimes);
        tag.putIntArray("CookTotals", cookTotals);
        tag.putIntArray("QueuedSlots", toIntArray(queuedSlots));
        tag.putIntArray("OutputBlocked", toIntArray(outputBlocked));
        tag.putInt("BurnTime", burnTime);
        tag.putInt("BurnTimeTotal", burnTimeTotal);
        for (int i = 0; i < GRILL_SLOTS; i++) {
            if (pending[i] != null && !pending[i].isEmpty())
                tag.put("Pending" + i, pending[i].save(registries));
        }
        if (customName != null)
            tag.putString("CustomName", Component.Serializer.toJson(customName, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        Arrays.fill(pending, null);
        Arrays.fill(queuedJobs, null);
        Arrays.fill(queuedSlots, false);
        Arrays.fill(outputBlocked, false);
        int[] ct = tag.getIntArray("CookTimes"),  tot = tag.getIntArray("CookTotals");
        for (int i = 0; i < GRILL_SLOTS && i < ct.length;  i++) cookTimes[i]  = ct[i];
        for (int i = 0; i < GRILL_SLOTS && i < tot.length; i++) cookTotals[i] = tot[i];
        int[] queued = tag.getIntArray("QueuedSlots");
        int[] blocked = tag.getIntArray("OutputBlocked");
        burnTime = tag.getInt("BurnTime");
        burnTimeTotal = tag.getInt("BurnTimeTotal");
        for (int i = 0; i < GRILL_SLOTS; i++) {
            if (tag.contains("Pending" + i))
                pending[i] = ItemStack.parse(registries, tag.getCompound("Pending" + i)).orElse(null);
            queuedSlots[i] = i < queued.length && queued[i] != 0 && pending[i] != null;
            outputBlocked[i] = i < blocked.length && blocked[i] != 0 && queuedSlots[i];
        }
        if (tag.contains("CustomName"))
            customName = Component.Serializer.fromJson(tag.getString("CustomName"), registries);
    }

    private static int[] toIntArray(boolean[] values) {
        int[] tags = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            tags[i] = values[i] ? 1 : 0;
        }
        return tags;
    }
}
