package net.inkyquill.cfbdelight.kitchen.recipe;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class IngredientMatcher {
    private IngredientMatcher() {
    }

    public static Optional<List<ItemStack>> findMatches(KitchenInventorySnapshot snapshot,
                                                        List<PlannedIngredient> ingredients) {
        List<ItemStack> available = Objects.requireNonNull(snapshot, "snapshot").stacks();
        List<PlannedIngredient> orderedIngredients = Objects.requireNonNull(ingredients, "ingredients").stream()
                .map(ingredient -> Objects.requireNonNull(ingredient, "ingredients cannot contain null"))
                .sorted(Comparator.comparingInt(ingredient -> countAvailableItems(available, ingredient)))
                .toList();
        List<ItemStack> consumed = new ArrayList<>();

        for (PlannedIngredient ingredient : orderedIngredients) {
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

        return Optional.of(copyConsumedStacks(consumed));
    }

    private static int countAvailableItems(List<ItemStack> available, PlannedIngredient ingredient) {
        int count = 0;
        for (ItemStack stack : available) {
            if (ingredient.ingredient().test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static List<ItemStack> copyConsumedStacks(List<ItemStack> consumed) {
        return List.copyOf(consumed.stream()
                .map(ItemStack::copy)
                .toList());
    }
}
