package net.inkyquill.delightfulkitchen.pouch;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class FoodPouchInventoryOps {
    private FoodPouchInventoryOps() {
    }

    public static FoodPouchInventory normalized(FoodPouchInventory inventory, FoodPouchTier tier) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(tier, "tier");

        List<ItemStack> normalized = new ArrayList<>(Collections.nCopies(tier.slots(), ItemStack.EMPTY));
        int copied = Math.min(inventory.size(), tier.slots());
        for (int slot = 0; slot < copied; slot++) {
            normalized.set(slot, clampToSlotLimit(FoodPouchInventory.stackAt(inventory, slot), tier));
        }
        return FoodPouchInventory.fromInternalStacks(normalized);
    }

    public static InsertResult insert(FoodPouchInventory inventory, FoodPouchTier tier, ItemStack stack) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(tier, "tier");
        if (stack == null || stack.isEmpty()) {
            return new InsertResult(normalized(inventory, tier), ItemStack.EMPTY);
        }

        List<ItemStack> slots = new ArrayList<>(normalized(inventory, tier).stacks());
        ItemStack remainder = stack.copy();

        for (int slot = 0; slot < slots.size() && !remainder.isEmpty(); slot++) {
            ItemStack existing = slots.get(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, remainder)) {
                remainder = mergeIntoSlot(slots, slot, remainder, tier);
            }
        }
        for (int slot = 0; slot < slots.size() && !remainder.isEmpty(); slot++) {
            if (slots.get(slot).isEmpty()) {
                remainder = mergeIntoSlot(slots, slot, remainder, tier);
            }
        }

        return new InsertResult(FoodPouchInventory.fromInternalStacks(slots), remainder);
    }

    public static ExtractResult extract(FoodPouchInventory inventory, int slot, int amount) {
        Objects.requireNonNull(inventory, "inventory");
        if (!inventory.isValidSlot(slot) || amount <= 0) {
            return new ExtractResult(inventory, ItemStack.EMPTY);
        }

        ItemStack existing = inventory.get(slot);
        if (existing.isEmpty()) {
            return new ExtractResult(inventory, ItemStack.EMPTY);
        }

        int extractedCount = Math.min(amount, existing.getCount());
        ItemStack extracted = existing.copyWithCount(extractedCount);
        ItemStack remaining = existing.copy();
        remaining.shrink(extractedCount);
        return new ExtractResult(inventory.withSlot(slot, remaining), extracted);
    }

    public static int slotLimit(FoodPouchTier tier, ItemStack stack) {
        Objects.requireNonNull(tier, "tier");
        return tier.slotLimit(stack);
    }

    private static ItemStack mergeIntoSlot(List<ItemStack> slots, int slot, ItemStack remainder, FoodPouchTier tier) {
        ItemStack existing = slots.get(slot);
        int limit = tier.slotLimit(remainder);
        if (limit <= 0) {
            return remainder;
        }

        if (existing.isEmpty()) {
            int inserted = Math.min(limit, remainder.getCount());
            slots.set(slot, remainder.copyWithCount(inserted));
            return copyRemainder(remainder, inserted);
        }

        int available = limit - existing.getCount();
        if (available <= 0) {
            return remainder;
        }

        int inserted = Math.min(available, remainder.getCount());
        ItemStack merged = existing.copy();
        merged.grow(inserted);
        slots.set(slot, merged);
        return copyRemainder(remainder, inserted);
    }

    private static ItemStack clampToSlotLimit(ItemStack stack, FoodPouchTier tier) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int limit = tier.slotLimit(stack);
        if (limit <= 0) {
            return ItemStack.EMPTY;
        }
        return stack.copyWithCount(Math.min(stack.getCount(), limit));
    }

    private static ItemStack copyRemainder(ItemStack original, int inserted) {
        int remaining = original.getCount() - inserted;
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }
        return original.copyWithCount(remaining);
    }

    public record InsertResult(FoodPouchInventory inventory, ItemStack remainder) {
        public InsertResult {
            inventory = Objects.requireNonNull(inventory, "inventory");
            remainder = remainder == null ? ItemStack.EMPTY : remainder.copy();
        }

        @Override
        public ItemStack remainder() {
            return remainder.copy();
        }
    }

    public record ExtractResult(FoodPouchInventory inventory, ItemStack extracted) {
        public ExtractResult {
            inventory = Objects.requireNonNull(inventory, "inventory");
            extracted = extracted == null ? ItemStack.EMPTY : extracted.copy();
        }

        @Override
        public ItemStack extracted() {
            return extracted.copy();
        }
    }
}
