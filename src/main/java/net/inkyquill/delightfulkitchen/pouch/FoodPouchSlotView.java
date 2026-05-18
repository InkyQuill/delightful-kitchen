package net.inkyquill.delightfulkitchen.pouch;

import net.minecraft.world.item.ItemStack;

public record FoodPouchSlotView(int slot, ItemStack stack, int limit) {
    public FoodPouchSlotView {
        stack = stack == null ? ItemStack.EMPTY : stack.copy();
    }

    @Override
    public ItemStack stack() {
        return stack.copy();
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public boolean isFood() {
        return !stack.isEmpty() && stack.getFoodProperties(null) != null;
    }
}
