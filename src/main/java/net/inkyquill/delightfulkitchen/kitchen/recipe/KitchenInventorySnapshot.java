package net.inkyquill.delightfulkitchen.kitchen.recipe;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class KitchenInventorySnapshot {
    private final List<ItemStack> stacks;

    public KitchenInventorySnapshot(List<ItemStack> stacks) {
        this.stacks = Objects.requireNonNull(stacks, "stacks").stream()
                .map(stack -> Objects.requireNonNull(stack, "stacks cannot contain null"))
                .filter(stack -> !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
    }

    public List<ItemStack> stacks() {
        return stacks.stream().map(ItemStack::copy).toList();
    }

    public KitchenInventorySnapshot withConsumed(List<ItemStack> consumed) {
        List<ItemStack> remaining = stacks();
        for (ItemStack toConsume : Objects.requireNonNull(consumed, "consumed")) {
            Objects.requireNonNull(toConsume, "consumed cannot contain null");

            int needed = toConsume.getCount();
            for (ItemStack stack : remaining) {
                if (needed <= 0) {
                    break;
                }
                if (ItemStack.isSameItemSameComponents(stack, toConsume)) {
                    int taken = Math.min(needed, stack.getCount());
                    stack.shrink(taken);
                    needed -= taken;
                }
            }
        }
        return new KitchenInventorySnapshot(remaining);
    }

    public KitchenInventorySnapshot withAdded(List<ItemStack> added) {
        List<ItemStack> next = new ArrayList<>(stacks());
        for (ItemStack stack : Objects.requireNonNull(added, "added")) {
            Objects.requireNonNull(stack, "added cannot contain null");
            if (!stack.isEmpty()) {
                next.add(stack.copy());
            }
        }
        return new KitchenInventorySnapshot(next);
    }
}
