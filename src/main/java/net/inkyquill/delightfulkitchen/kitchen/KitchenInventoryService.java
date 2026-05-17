package net.inkyquill.delightfulkitchen.kitchen;

import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenInventorySnapshot;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlannedIngredient;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
            for (int slot = 0; slot < endpoint.size() && extracted < amount; slot++) {
                ItemStack stack = endpoint.get(slot);
                if (!matches(prototype, stack)) {
                    continue;
                }

                int toExtract = Math.min(amount - extracted, stack.getCount());
                ItemStack extractedStack = endpoint.extract(slot, toExtract, simulate);
                if (!extractedStack.isEmpty()) {
                    extracted += extractedStack.getCount();
                }
            }
        }

        return extracted > 0 ? prototype.copyWithCount(extracted) : ItemStack.EMPTY;
    }

    public ItemStack insert(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remainder = stack.copy();
        remainder = insertIntoMatchingSlots(remainder, simulate);
        if (!remainder.isEmpty()) {
            remainder = insertIntoEmptySlots(remainder, simulate);
        }
        return remainder.isEmpty() ? ItemStack.EMPTY : remainder;
    }

    public KitchenInventorySnapshot snapshot() {
        ArrayList<ItemStack> stacks = new ArrayList<>();
        for (KitchenInventoryEndpoint endpoint : endpoints) {
            for (int slot = 0; slot < endpoint.size(); slot++) {
                ItemStack stack = endpoint.get(slot);
                if (!stack.isEmpty()) {
                    stacks.add(stack.copy());
                }
            }
        }
        return new KitchenInventorySnapshot(stacks);
    }

    public Optional<List<ItemStack>> extractIngredient(PlannedIngredient ingredient, boolean simulate) {
        List<ExtractedSlot> extracted = new ArrayList<>();
        int remaining = ingredient.count();
        Ingredient matcher = ingredient.ingredient();

        for (KitchenInventoryEndpoint endpoint : endpoints) {
            for (int slot = 0; slot < endpoint.size() && remaining > 0; slot++) {
                ItemStack stack = endpoint.get(slot);
                if (stack.isEmpty() || !matcher.test(stack)) {
                    continue;
                }

                int amount = Math.min(remaining, stack.getCount());
                ItemStack taken = endpoint.extract(slot, amount, simulate);
                if (!taken.isEmpty()) {
                    extracted.add(new ExtractedSlot(endpoint, slot, taken));
                    remaining -= taken.getCount();
                }
            }
        }

        if (remaining > 0) {
            if (!simulate) {
                rollbackExtracted(extracted);
            }
            return Optional.empty();
        }
        return Optional.of(extractedStacks(extracted));
    }

    public boolean canInsertAll(List<ItemStack> stacks) {
        List<SimulatedSlot> slots = simulatedSlots();
        for (ItemStack stack : stacks) {
            if (!simulateInsert(slots, stack).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public boolean insertAll(List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (!insert(stack, false).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private List<SimulatedSlot> simulatedSlots() {
        List<SimulatedSlot> slots = new ArrayList<>();
        for (KitchenInventoryEndpoint endpoint : endpoints) {
            for (int slot = 0; slot < endpoint.size(); slot++) {
                slots.add(new SimulatedSlot(endpoint, slot, endpoint.get(slot).copy()));
            }
        }
        return slots;
    }

    private ItemStack simulateInsert(List<SimulatedSlot> slots, ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remainder = stack.copy();
        remainder = simulateInsertIntoMatchingSlots(slots, remainder);
        if (!remainder.isEmpty()) {
            remainder = simulateInsertIntoEmptySlots(slots, remainder);
        }
        return remainder.isEmpty() ? ItemStack.EMPTY : remainder;
    }

    private ItemStack simulateInsertIntoMatchingSlots(List<SimulatedSlot> slots, ItemStack remainder) {
        for (SimulatedSlot slot : slots) {
            if (remainder.isEmpty()) {
                break;
            }
            if (slot.stack.isEmpty()
                    || !ItemStack.isSameItemSameComponents(slot.stack, remainder)
                    || !slot.endpoint.mayPlace(slot.slot, remainder)) {
                continue;
            }

            int transfer = Math.min(
                    remainder.getCount(),
                    slot.endpoint.getSlotLimit(slot.slot, slot.stack) - slot.stack.getCount()
            );
            if (transfer <= 0) {
                continue;
            }
            slot.stack.grow(transfer);
            remainder.shrink(transfer);
        }
        return remainder;
    }

    private ItemStack simulateInsertIntoEmptySlots(List<SimulatedSlot> slots, ItemStack remainder) {
        for (SimulatedSlot slot : slots) {
            if (remainder.isEmpty()) {
                break;
            }
            if (!slot.stack.isEmpty() || !slot.endpoint.mayPlace(slot.slot, remainder)) {
                continue;
            }

            int transfer = Math.min(remainder.getCount(), slot.endpoint.getSlotLimit(slot.slot, remainder));
            if (transfer <= 0) {
                continue;
            }
            slot.stack = remainder.copyWithCount(transfer);
            remainder.shrink(transfer);
        }
        return remainder;
    }

    private static final class SimulatedSlot {
        private final KitchenInventoryEndpoint endpoint;
        private final int slot;
        private ItemStack stack;

        private SimulatedSlot(KitchenInventoryEndpoint endpoint, int slot, ItemStack stack) {
            this.endpoint = endpoint;
            this.slot = slot;
            this.stack = stack;
        }
    }

    private void rollbackExtracted(List<ExtractedSlot> extracted) {
        for (int index = extracted.size() - 1; index >= 0; index--) {
            ExtractedSlot extractedSlot = extracted.get(index);
            ItemStack remainder = extractedSlot.endpoint.restoreExtracted(extractedSlot.slot, extractedSlot.stack);
            if (!remainder.isEmpty()) {
                throw new IllegalStateException(
                        "Failed to restore extracted stack to " + extractedSlot.endpoint.pos()
                                + " slot " + extractedSlot.slot
                );
            }
        }
    }

    private List<ItemStack> extractedStacks(List<ExtractedSlot> extracted) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ExtractedSlot extractedSlot : extracted) {
            stacks.add(extractedSlot.stack);
        }
        return List.copyOf(stacks);
    }

    private record ExtractedSlot(KitchenInventoryEndpoint endpoint, int slot, ItemStack stack) {
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

    private ItemStack insertIntoMatchingSlots(ItemStack remainder, boolean simulate) {
        for (KitchenInventoryEndpoint endpoint : endpoints) {
            for (int slot = 0; slot < endpoint.size() && !remainder.isEmpty(); slot++) {
                ItemStack existing = endpoint.get(slot);
                if (existing.isEmpty()
                        || !ItemStack.isSameItemSameComponents(existing, remainder)
                        || !endpoint.mayPlace(slot, remainder)) {
                    continue;
                }

                remainder = endpoint.insert(slot, remainder, simulate);
            }
        }
        return remainder;
    }

    private ItemStack insertIntoEmptySlots(ItemStack remainder, boolean simulate) {
        for (KitchenInventoryEndpoint endpoint : endpoints) {
            for (int slot = 0; slot < endpoint.size() && !remainder.isEmpty(); slot++) {
                if (!endpoint.get(slot).isEmpty() || !endpoint.mayPlace(slot, remainder)) {
                    continue;
                }

                remainder = endpoint.insert(slot, remainder, simulate);
            }
        }
        return remainder;
    }

    private static boolean matches(ItemStack prototype, ItemStack stack) {
        return !stack.isEmpty() && ItemStack.isSameItemSameComponents(prototype, stack);
    }
}
