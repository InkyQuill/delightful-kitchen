package net.inkyquill.cfbdelight.block.entity;

import net.inkyquill.cfbdelight.menu.ContainerStorageMenu;
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
 * Block entity for the Container Storage block.
 * <p>
 * Implements {@link Container} so CfB's tag-based fallback provider
 * ({@code cookingforblockheads:kitchen_item_providers}) can automatically wrap
 * this block entity in a {@code ContainerKitchenItemProvider}, exposing all
 * 25 slots to the kitchen's ingredient search.
 *
 * <h3>Slot layout</h3>
 * <ul>
 *   <li>0–7   : bowl slots  (filtered to {@code minecraft:bowl})</li>
 *   <li>8–15  : bottle slots (filtered to {@code minecraft:glass_bottle})</li>
 *   <li>16–24 : general container slots (any item)</li>
 * </ul>
 */
public class ContainerStorageBlockEntity extends BlockEntity
        implements Container, MenuProvider, Nameable {

    // --- slot constants ---
    public static final int BOWL_SLOTS    = 8;
    public static final int BOTTLE_SLOTS  = 8;
    public static final int GENERAL_SLOTS = 9;
    public static final int SLOT_COUNT    = BOWL_SLOTS + BOTTLE_SLOTS + GENERAL_SLOTS; // 25

    public static final int SLOT_BOWL_START    = 0;
    public static final int SLOT_BOTTLE_START  = BOWL_SLOTS;                  // 8
    public static final int SLOT_GENERAL_START = BOWL_SLOTS + BOTTLE_SLOTS;   // 16

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    @Nullable private Component customName = null;

    public ContainerStorageBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONTAINER_STORAGE.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // Container implementation
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
        return level != null &&
               level.getBlockEntity(worldPosition) == this &&
               player.distanceToSqr(worldPosition.getX() + 0.5,
                                     worldPosition.getY() + 0.5,
                                     worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override public void clearContent() {
        items.replaceAll(ignored -> ItemStack.EMPTY);
        setChanged();
    }

    // ------------------------------------------------------------------
    // MenuProvider / Nameable
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return customName != null ? customName
                : Component.translatable("block.cfb_delight.container_storage");
    }

    @Override public Component getName() { return getDisplayName(); }

    @Override @Nullable public Component getCustomName() { return customName; }

    public void setCustomName(Component name) { this.customName = name; setChanged(); }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new ContainerStorageMenu(id, playerInventory, this,
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
