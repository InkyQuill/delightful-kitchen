package net.inkyquill.delightfulkitchen.block.entity;

import net.inkyquill.delightfulkitchen.block.AutoCutterBlock;
import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryService;
import net.inkyquill.delightfulkitchen.kitchen.KitchenScanner;
import net.inkyquill.delightfulkitchen.kitchen.KitchenServices;
import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStation;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStationAcceptResult;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStationJob;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStationStatus;
import net.inkyquill.delightfulkitchen.menu.AutoCutterMenu;
import net.inkyquill.delightfulkitchen.util.InventoryHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * Block entity for the Auto Cutter.
 * <p>
 * Slot layout:
 * <ul>
 *   <li>0   : input item</li>
 *   <li>1-4 : output slots (up to 4 chance-rolled results)</li>
 * </ul>
 */
public class AutoCutterBlockEntity extends BlockEntity
        implements Container, MenuProvider, Nameable, KitchenStation {

    public static final int SLOT_COUNT       = 5;
    public static final int SLOT_INPUT       = 0;
    public static final int SLOT_OUTPUT_START = 1;
    public static final int SLOT_OUTPUT_END   = 4;

    private static final int PROCESS_TICKS = 100;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final List<ItemStack> pendingResults = new ArrayList<>();
    private int activeTimer = 0;
    private boolean queuedJobActive = false;
    private boolean outputBlocked = false;
    @Nullable private Component customName = null;

    public AutoCutterBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.AUTO_CUTTER.get(), pos, state);
    }

    AutoCutterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ------------------------------------------------------------------
    // Server tick
    // ------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, AutoCutterBlockEntity be) {
        if (be.queuedJobActive) {
            if (be.activeTimer > 0) {
                be.activeTimer--;
            }
            if (be.activeTimer <= 0) {
                be.finishQueuedJob(level, pos, state);
            }
            be.setChanged();
            return;
        }

        if (be.activeTimer > 0) {
            be.activeTimer--;
            if (be.activeTimer == 0) {
                be.finishCutting(level, pos, state);
            }
            be.setChanged();
        } else {
            be.tryStartCutting(level, pos, state);
        }
    }

    private void tryStartCutting(Level level, BlockPos pos, BlockState state) {
        ItemStack input = items.get(SLOT_INPUT);
        if (input.isEmpty()) return;

        List<RecipeHolder<CuttingBoardRecipe>> recipes = level.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.CUTTING.get());
        for (RecipeHolder<CuttingBoardRecipe> holder : recipes) {
            CuttingBoardRecipe recipe = holder.value();
            if (recipe.getIngredients().isEmpty()) continue;
            if (recipe.getIngredients().get(0).test(input)) {
                // Consume input
                input.shrink(1);
                if (input.isEmpty()) items.set(SLOT_INPUT, ItemStack.EMPTY);

                // Pre-roll results
                RandomSource rand = level.getRandom();
                pendingResults.clear();
                pendingResults.addAll(recipe.rollResults(rand, 0, null));

                activeTimer = PROCESS_TICKS;
                level.setBlock(pos, state.setValue(AutoCutterBlock.ACTIVE, true), 3);
                setChanged();
                return;
            }
        }
    }

    private void finishCutting(Level level, BlockPos pos, BlockState state) {
        for (ItemStack result : pendingResults) {
            if (result.isEmpty()) continue;
            // Try pushing to kitchen network first
            if (InventoryHelper.pushToKitchen(level, pos, result)) continue;
            // Fall back to internal output slots
            boolean placed = false;
            for (int i = SLOT_OUTPUT_START; i <= SLOT_OUTPUT_END && !result.isEmpty(); i++) {
                ItemStack existing = items.get(i);
                if (existing.isEmpty()) {
                    items.set(i, result.copy());
                    result.setCount(0);
                    placed = true;
                } else if (ItemStack.isSameItemSameComponents(existing, result)
                        && existing.getCount() + result.getCount() <= existing.getMaxStackSize()) {
                    existing.grow(result.getCount());
                    result.setCount(0);
                    placed = true;
                }
            }
            // Drop on ground if no space
            if (!result.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), result);
            }
        }
        pendingResults.clear();
        level.setBlock(pos, state.setValue(AutoCutterBlock.ACTIVE, false), 3);
        setChanged();
    }

    private void finishQueuedJob(Level level, BlockPos pos, BlockState state) {
        List<ItemStack> remainingResults = new ArrayList<>();
        outputBlocked = false;

        for (ItemStack output : pendingResults) {
            if (output.isEmpty()) {
                continue;
            }
            ItemStack remainder = routeOutput(level, output);
            if (!remainder.isEmpty()) {
                remainder = placeInOutputSlots(remainder);
            }
            if (!remainder.isEmpty()) {
                remainingResults.add(remainder);
                outputBlocked = true;
            }
        }

        pendingResults.clear();
        pendingResults.addAll(remainingResults);
        if (!outputBlocked) {
            queuedJobActive = false;
            activeTimer = 0;
        }
        if (state.hasProperty(AutoCutterBlock.ACTIVE)) {
            level.setBlock(pos, state.setValue(AutoCutterBlock.ACTIVE, outputBlocked), 3);
        }
        setChanged();
    }

    private ItemStack routeOutput(Level level, ItemStack stack) {
        var scan = KitchenScanner.scan(level, worldPosition);
        var endpoints = KitchenServices.inventoryEndpoints(level, scan, worldPosition);
        var inventory = new KitchenInventoryService(endpoints);
        return inventory.insert(stack, false);
    }

    private ItemStack placeInOutputSlots(ItemStack stack) {
        ItemStack remainder = stack.copy();
        for (int i = SLOT_OUTPUT_START; i <= SLOT_OUTPUT_END && !remainder.isEmpty(); i++) {
            ItemStack existing = items.get(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, remainder)) {
                int transfer = Math.min(remainder.getCount(), existing.getMaxStackSize() - existing.getCount());
                if (transfer > 0) {
                    existing.grow(transfer);
                    remainder.shrink(transfer);
                }
            }
        }
        for (int i = SLOT_OUTPUT_START; i <= SLOT_OUTPUT_END && !remainder.isEmpty(); i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, remainder.split(remainder.getCount()));
            }
        }
        return remainder.isEmpty() ? ItemStack.EMPTY : remainder;
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
        return slot == SLOT_INPUT;
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
        return KitchenStationType.CUTTING_BOARD;
    }

    @Override
    public int queueCapacity() {
        return 1;
    }

    @Override
    public int queuedJobCount() {
        return queuedJobActive ? 1 : 0;
    }

    @Override
    public KitchenStationStatus stationStatus() {
        if (outputBlocked) {
            return KitchenStationStatus.OUTPUT_BLOCKED;
        }
        if (queuedJobActive || activeTimer > 0 || !pendingResults.isEmpty()) {
            return KitchenStationStatus.RUNNING;
        }
        return KitchenStationStatus.IDLE;
    }

    @Override
    public KitchenStationAcceptResult canAccept(KitchenStationJob job) {
        if (job.stationType() != KitchenStationType.CUTTING_BOARD) {
            return KitchenStationAcceptResult.blocked(PlanningBlocker.MISSING_STATION);
        }
        if (outputBlocked) {
            return KitchenStationAcceptResult.blocked(PlanningBlocker.OUTPUT_STORAGE_FULL);
        }
        if (isBusy()) {
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

        queuedJobActive = true;
        outputBlocked = false;
        pendingResults.clear();
        pendingResults.addAll(job.outputs());
        activeTimer = Math.max(1, job.processingTime());

        if (level != null) {
            BlockState state = getBlockState();
            if (state.hasProperty(AutoCutterBlock.ACTIVE)) {
                level.setBlock(worldPosition, state.setValue(AutoCutterBlock.ACTIVE, true), 3);
            }
        }
        setChanged();
        return KitchenStationAcceptResult.acceptedResult();
    }

    @Override
    public BlockPos stationPos() {
        return worldPosition.immutable();
    }

    private boolean isBusy() {
        return queuedJobActive || activeTimer > 0 || !pendingResults.isEmpty();
    }

    // ------------------------------------------------------------------
    // MenuProvider / Nameable
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("block.delightful_kitchen.auto_cutter");
    }

    @Override public Component getName() { return getDisplayName(); }
    @Override @Nullable public Component getCustomName() { return customName; }
    public void setCustomName(Component name) { this.customName = name; setChanged(); }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new AutoCutterMenu(id, playerInventory, this,
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
        for (ItemStack stack : pendingResults) {
            if (!stack.isEmpty())
                net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
        items.replaceAll(ignored -> ItemStack.EMPTY);
        pendingResults.clear();
        queuedJobActive = false;
        outputBlocked = false;
    }

    public boolean isActive() { return activeTimer > 0 || queuedJobActive; }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("ActiveTimer", activeTimer);
        tag.putBoolean("OutputBlocked", outputBlocked);
        if (!pendingResults.isEmpty()) {
            CompoundTag pending = new CompoundTag();
            pending.putInt("Count", pendingResults.size());
            for (int i = 0; i < pendingResults.size(); i++) {
                pending.put("Item" + i, pendingResults.get(i).save(registries));
            }
            tag.put("PendingResults", pending);
        }
        if (customName != null)
            tag.putString("CustomName", Component.Serializer.toJson(customName, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        activeTimer = tag.getInt("ActiveTimer");
        outputBlocked = tag.getBoolean("OutputBlocked");
        pendingResults.clear();
        if (tag.contains("PendingResults")) {
            CompoundTag pending = tag.getCompound("PendingResults");
            int count = pending.getInt("Count");
            for (int i = 0; i < count; i++) {
                if (pending.contains("Item" + i)) {
                    ItemStack.parse(registries, pending.getCompound("Item" + i))
                            .ifPresent(pendingResults::add);
                }
            }
        }
        queuedJobActive = !pendingResults.isEmpty();
        if (!queuedJobActive) {
            outputBlocked = false;
        }
        if (tag.contains("CustomName"))
            customName = Component.Serializer.fromJson(tag.getString("CustomName"), registries);
    }
}
