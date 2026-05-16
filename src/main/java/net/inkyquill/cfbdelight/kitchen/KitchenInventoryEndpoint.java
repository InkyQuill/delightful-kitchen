package net.inkyquill.cfbdelight.kitchen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public final class KitchenInventoryEndpoint {
    private final BlockPos pos;
    @Nullable private final Container container;
    @Nullable private final IItemHandler itemHandler;

    public KitchenInventoryEndpoint(BlockPos pos, Container container) {
        this.pos = pos.immutable();
        this.container = container;
        this.itemHandler = null;
    }

    public KitchenInventoryEndpoint(BlockPos pos, IItemHandler itemHandler) {
        this.pos = pos.immutable();
        this.container = null;
        this.itemHandler = itemHandler;
    }

    public BlockPos pos() {
        return pos;
    }

    public int size() {
        return container != null ? container.getContainerSize() : itemHandler.getSlots();
    }

    public ItemStack get(int slot) {
        return container != null ? container.getItem(slot) : itemHandler.getStackInSlot(slot);
    }

    public boolean mayPlace(int slot, ItemStack stack) {
        return container != null ? container.canPlaceItem(slot, stack) : itemHandler.isItemValid(slot, stack);
    }

    public int getSlotLimit(int slot, ItemStack stack) {
        if (container != null) {
            return Math.min(container.getMaxStackSize(stack), stack.getMaxStackSize());
        }
        return Math.min(itemHandler.getSlotLimit(slot), stack.getMaxStackSize());
    }

    public ItemStack extract(int slot, int amount, boolean simulate) {
        if (itemHandler != null) {
            return itemHandler.extractItem(slot, amount, simulate);
        }

        ItemStack existing = container.getItem(slot);
        if (existing.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        int extracted = Math.min(amount, existing.getCount());
        ItemStack result = existing.copyWithCount(extracted);
        if (!simulate) {
            existing.shrink(extracted);
            if (existing.isEmpty()) {
                container.setItem(slot, ItemStack.EMPTY);
            }
            container.setChanged();
        }
        return result;
    }

    public ItemStack insert(int slot, ItemStack stack, boolean simulate) {
        if (itemHandler != null) {
            return itemHandler.insertItem(slot, stack, simulate);
        }

        if (stack.isEmpty() || !container.canPlaceItem(slot, stack)) {
            return stack;
        }

        ItemStack existing = container.getItem(slot);
        if (existing.isEmpty()) {
            int transfer = Math.min(stack.getCount(), getSlotLimit(slot, stack));
            if (transfer <= 0) {
                return stack;
            }
            if (!simulate) {
                container.setItem(slot, stack.copyWithCount(transfer));
                container.setChanged();
            }
            ItemStack remainder = stack.copy();
            remainder.shrink(transfer);
            return remainder.isEmpty() ? ItemStack.EMPTY : remainder;
        }

        if (!ItemStack.isSameItemSameComponents(existing, stack)) {
            return stack;
        }

        int transfer = Math.min(stack.getCount(), getSlotLimit(slot, existing) - existing.getCount());
        if (transfer <= 0) {
            return stack;
        }
        if (!simulate) {
            existing.grow(transfer);
            container.setChanged();
        }
        ItemStack remainder = stack.copy();
        remainder.shrink(transfer);
        return remainder.isEmpty() ? ItemStack.EMPTY : remainder;
    }
}
