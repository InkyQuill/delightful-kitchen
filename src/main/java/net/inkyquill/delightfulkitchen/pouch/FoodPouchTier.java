package net.inkyquill.delightfulkitchen.pouch;

import net.minecraft.world.item.ItemStack;

public enum FoodPouchTier {
    BASIC(18, 1, 6, 3),
    ADVANCED(36, 4, 6, 6),
    ULTIMATE(72, 16, 9, 8);

    public static final int MAX_FOOD_STACK_SIZE = 1024;

    private final int slots;
    private final int stackMultiplier;
    private final int columns;
    private final int rows;

    FoodPouchTier(int slots, int stackMultiplier, int columns, int rows) {
        this.slots = slots;
        this.stackMultiplier = stackMultiplier;
        this.columns = columns;
        this.rows = rows;
    }

    public int slots() {
        return slots;
    }

    public int stackMultiplier() {
        return stackMultiplier;
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    public int slotLimit(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (stack.getFoodProperties(null) != null) {
            return Math.min(MAX_FOOD_STACK_SIZE, stack.getMaxStackSize() * stackMultiplier);
        }
        return stack.getMaxStackSize();
    }
}
