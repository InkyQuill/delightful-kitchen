package net.inkyquill.delightfulkitchen.kitchen.utility;

import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryEndpoint;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public record BorrowedContainer(ItemStack stack, KitchenInventoryEndpoint source, int sourceSlot) {
    public BorrowedContainer {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(source, "source");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Borrowed stack must not be empty");
        }
        if (sourceSlot < 0) {
            throw new IllegalArgumentException("Source slot must be non-negative");
        }
        stack = stack.copy();
    }

    @Override
    public ItemStack stack() {
        return stack.copy();
    }
}
