package net.inkyquill.cfbdelight.kitchen.recipe;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class IngredientMatcher {
    private IngredientMatcher() {
    }

    public static Optional<List<ItemStack>> findMatches(KitchenInventorySnapshot snapshot,
                                                        List<PlannedIngredient> ingredients) {
        List<ItemStack> available = Objects.requireNonNull(snapshot, "snapshot").stacks();
        List<ItemStack> consumed = new ArrayList<>();

        for (PlannedIngredient ingredient : Objects.requireNonNull(ingredients, "ingredients")) {
            Objects.requireNonNull(ingredient, "ingredients cannot contain null");

            int remaining = ingredient.count();
            for (ItemStack stack : available) {
                if (remaining <= 0) {
                    break;
                }
                if (!ingredient.ingredient().test(stack)) {
                    continue;
                }
                int taken = Math.min(remaining, stack.getCount());
                ItemStack consumedStack = stack.copyWithCount(taken);
                consumed.add(consumedStack);
                stack.shrink(taken);
                remaining -= taken;
            }
            if (remaining > 0) {
                return Optional.empty();
            }
        }

        return Optional.of(consumed);
    }
}
