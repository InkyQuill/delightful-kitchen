package net.inkyquill.delightfulkitchen.menu.slot;

import net.inkyquill.delightfulkitchen.pouch.FoodPouchTier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Slot that applies pouch tier limits and rejects the pouch stack bound to its menu.
 */
public class FoodPouchSlot extends Slot {
    private final FoodPouchTier tier;
    private final Predicate<ItemStack> boundPouchStack;

    public FoodPouchSlot(Container container, int slot, int x, int y, FoodPouchTier tier,
                         Predicate<ItemStack> boundPouchStack) {
        super(container, slot, x, y);
        this.tier = Objects.requireNonNull(tier, "tier");
        this.boundPouchStack = Objects.requireNonNull(boundPouchStack, "boundPouchStack");
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !boundPouchStack.test(stack);
    }

    @Override
    public int getMaxStackSize() {
        return FoodPouchTier.MAX_FOOD_STACK_SIZE;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return tier.slotLimit(stack);
    }

    public int slotLimit(ItemStack stack) {
        return getMaxStackSize(stack);
    }
}
