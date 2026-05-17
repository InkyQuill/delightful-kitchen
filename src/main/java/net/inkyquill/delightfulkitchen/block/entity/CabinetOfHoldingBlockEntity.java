package net.inkyquill.delightfulkitchen.block.entity;

import net.inkyquill.delightfulkitchen.block.ModBlocks;
import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryService;
import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryView;
import net.inkyquill.delightfulkitchen.kitchen.KitchenScanner;
import net.inkyquill.delightfulkitchen.kitchen.KitchenServices;
import net.inkyquill.delightfulkitchen.menu.CabinetOfHoldingMenu;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for the Cabinet of Holding.
 * <p>
 * When placed directly above Kitchen Floor, this opens a merged kitchen
 * inventory terminal. The local 54-slot storage remains as a fallback for old
 * worlds and cabinets outside a kitchen footprint.
 */
public class CabinetOfHoldingBlockEntity extends BlockEntity
        implements Container, MenuProvider, Nameable {

    public static final int SLOT_COUNT = 54;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    @Nullable private Component customName = null;

    public CabinetOfHoldingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CABINET_OF_HOLDING.get(), pos, state);
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
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5,
                        worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        items.replaceAll(ignored -> ItemStack.EMPTY);
        setChanged();
    }

    // ------------------------------------------------------------------
    // MenuProvider / Nameable
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return customName != null ? customName
                : Component.translatable("block.delightful_kitchen.cabinet_of_holding");
    }

    @Override public Component getName() { return getDisplayName(); }
    @Override @Nullable public Component getCustomName() { return customName; }
    public void setCustomName(Component name) { this.customName = name; setChanged(); }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        if (level != null && level.getBlockState(worldPosition.below()).is(ModBlocks.KITCHEN_FLOOR.get())) {
            var scan = KitchenScanner.scan(level, worldPosition);
            var endpoints = KitchenServices.inventoryEndpoints(level, scan, worldPosition);

            if (!endpoints.isEmpty()) {
                var service = new KitchenInventoryService(endpoints);
                var view = new KitchenInventoryView(service, worldPosition);
                return new CabinetOfHoldingMenu(id, playerInventory, view, service,
                        ContainerLevelAccess.create(level, worldPosition));
            }
        }

        return new CabinetOfHoldingMenu(id, playerInventory, this,
                ContainerLevelAccess.create(level, worldPosition));
    }

    // ------------------------------------------------------------------
    // Drop on break
    // ------------------------------------------------------------------

    public void dropContents(net.minecraft.world.level.Level level, BlockPos pos) {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(
                        level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
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
        if (customName != null) {
            tag.putString("CustomName", Component.Serializer.toJson(customName, registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        if (tag.contains("CustomName")) {
            customName = Component.Serializer.fromJson(tag.getString("CustomName"), registries);
        }
    }
}
