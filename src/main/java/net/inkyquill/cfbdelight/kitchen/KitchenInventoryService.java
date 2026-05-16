package net.inkyquill.cfbdelight.kitchen;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;

public class KitchenInventoryService {
    private final List<KitchenInventoryEndpoint> endpoints;

    public KitchenInventoryService(Collection<KitchenInventoryEndpoint> endpoints) {
        this.endpoints = List.copyOf(endpoints);
    }

    public int count(ItemStack prototype) {
        if (prototype.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (KitchenInventoryEndpoint endpoint : endpoints) {
            for (int slot = 0; slot < endpoint.size(); slot++) {
                ItemStack stack = endpoint.get(slot);
                if (matches(prototype, stack)) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    public ItemStack extract(ItemStack prototype, int amount, boolean simulate) {
        if (prototype.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }

        int extracted = 0;
        for (KitchenInventoryEndpoint endpoint : endpoints) {
            Container container = endpoint.container();
            for (int slot = 0; slot < endpoint.size() && extracted < amount; slot++) {
                ItemStack stack = endpoint.get(slot);
                if (!matches(prototype, stack)) {
                    continue;
                }

                int toExtract = Math.min(amount - extracted, stack.getCount());
                if (!simulate) {
                    stack.shrink(toExtract);
                    if (stack.isEmpty()) {
                        container.setItem(slot, ItemStack.EMPTY);
                    }
                    container.setChanged();
                }
                extracted += toExtract;
            }
        }

        return extracted > 0 ? prototype.copyWithCount(extracted) : ItemStack.EMPTY;
    }

    public ItemStack insert(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remainder = stack.copy();
        insertIntoMatchingSlots(remainder, simulate);
        if (!remainder.isEmpty()) {
            insertIntoEmptySlots(remainder, simulate);
        }
        return remainder.isEmpty() ? ItemStack.EMPTY : remainder;
    }

    public NonNullList<ItemStack> visibleStacks(int limit) {
        int size = Math.max(0, limit);
        NonNullList<ItemStack> stacks = NonNullList.withSize(size, ItemStack.EMPTY);
        int index = 0;

        for (KitchenInventoryEndpoint endpoint : endpoints) {
            for (int slot = 0; slot < endpoint.size() && index < size; slot++) {
                ItemStack stack = endpoint.get(slot);
                if (!stack.isEmpty()) {
                    stacks.set(index, stack.copy());
                    index++;
                }
            }
            if (index >= size) {
                break;
            }
        }

        return stacks;
    }

    private void insertIntoMatchingSlots(ItemStack remainder, boolean simulate) {
        for (KitchenInventoryEndpoint endpoint : endpoints) {
            Container container = endpoint.container();
            for (int slot = 0; slot < endpoint.size() && !remainder.isEmpty(); slot++) {
                ItemStack existing = endpoint.get(slot);
                if (existing.isEmpty()
                        || !ItemStack.isSameItemSameComponents(existing, remainder)
                        || !endpoint.mayPlace(slot, remainder)) {
                    continue;
                }

                int maxSize = container.getMaxStackSize(existing);
                int transfer = Math.min(remainder.getCount(), maxSize - existing.getCount());
                if (transfer <= 0) {
                    continue;
                }

                if (!simulate) {
                    existing.grow(transfer);
                    container.setChanged();
                }
                remainder.shrink(transfer);
            }
        }
    }

    private void insertIntoEmptySlots(ItemStack remainder, boolean simulate) {
        for (KitchenInventoryEndpoint endpoint : endpoints) {
            Container container = endpoint.container();
            for (int slot = 0; slot < endpoint.size() && !remainder.isEmpty(); slot++) {
                if (!endpoint.get(slot).isEmpty() || !endpoint.mayPlace(slot, remainder)) {
                    continue;
                }

                int transfer = Math.min(remainder.getCount(), container.getMaxStackSize(remainder));
                if (transfer <= 0) {
                    continue;
                }

                if (!simulate) {
                    container.setItem(slot, remainder.copyWithCount(transfer));
                    container.setChanged();
                }
                remainder.shrink(transfer);
            }
        }
    }

    private static boolean matches(ItemStack prototype, ItemStack stack) {
        return !stack.isEmpty() && ItemStack.isSameItemSameComponents(prototype, stack);
    }
}
