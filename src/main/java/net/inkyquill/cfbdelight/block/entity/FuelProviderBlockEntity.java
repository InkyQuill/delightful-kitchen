package net.inkyquill.cfbdelight.block.entity;

import net.inkyquill.cfbdelight.menu.FuelProviderMenu;
import net.inkyquill.cfbdelight.util.InventoryHelper;
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
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for the Fuel Provider.
 * <p>
 * Contains 9 fuel slots. Every 20 ticks, pushes fuel to adjacent
 * GrillBlockEntity and PotStandBlockEntity fuel slots.
 */
public class FuelProviderBlockEntity extends BlockEntity
        implements Container, MenuProvider, Nameable {

    public static final int SLOT_COUNT = 9;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int tickCounter = 0;
    @Nullable private Component customName = null;

    public FuelProviderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FUEL_PROVIDER.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // Server tick
    // ------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, FuelProviderBlockEntity be) {
        be.tickCounter++;
        if (be.tickCounter < 20) return;
        be.tickCounter = 0;
        be.pushFuelToNeighbors(level, pos);
    }

    private void pushFuelToNeighbors(Level level, BlockPos pos) {
        // Find a fuel item in our slots
        int sourceSlot = -1;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (!items.get(i).isEmpty()) { sourceSlot = i; break; }
        }
        if (sourceSlot < 0) return;

        // Work on a copy so we can track how many items were consumed
        ItemStack fuelCopy = items.get(sourceSlot).copy();
        int countBefore = fuelCopy.getCount();

        InventoryHelper.pushFuelToKitchen(level, pos, fuelCopy);

        int sent = countBefore - fuelCopy.getCount();
        if (sent > 0) {
            items.get(sourceSlot).shrink(sent);
            if (items.get(sourceSlot).isEmpty()) items.set(sourceSlot, ItemStack.EMPTY);
            setChanged();
        }
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
        var fuel = stack.getItemHolder().getData(
                net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps.FURNACE_FUELS);
        return fuel != null && fuel.burnTime() > 0;
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
    // MenuProvider / Nameable
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("block.cfb_delight.fuel_provider");
    }

    @Override public Component getName() { return getDisplayName(); }
    @Override @Nullable public Component getCustomName() { return customName; }
    public void setCustomName(Component name) { this.customName = name; setChanged(); }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new FuelProviderMenu(id, playerInventory, this,
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
        items.replaceAll(ignored -> ItemStack.EMPTY);
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("TickCounter", tickCounter);
        if (customName != null)
            tag.putString("CustomName", Component.Serializer.toJson(customName, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        tickCounter = tag.getInt("TickCounter");
        if (tag.contains("CustomName"))
            customName = Component.Serializer.fromJson(tag.getString("CustomName"), registries);
    }
}
