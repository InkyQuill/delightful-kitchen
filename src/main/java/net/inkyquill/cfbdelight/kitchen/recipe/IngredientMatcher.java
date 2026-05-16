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
        List<ItemStack> available = Objects.requireNonNull(snapshot, "snapshot").stacks().stream()
                .map(ItemStack::copy)
                .toList();
        List<PlannedIngredient> orderedIngredients = Objects.requireNonNull(ingredients, "ingredients").stream()
                .map(ingredient -> Objects.requireNonNull(ingredient, "ingredients cannot contain null"))
                .sorted(Comparator.comparingInt(ingredient -> countAvailableItems(available, ingredient)))
                .toList();
        List<ItemStack> consumed = new ArrayList<>();

        if (!matchIngredient(orderedIngredients, available, consumed, 0)) {
            return Optional.empty();
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

    private static boolean matchIngredient(List<PlannedIngredient> ingredients,
                                           List<ItemStack> available,
                                           List<ItemStack> consumed,
                                           int ingredientIndex) {
        if (ingredientIndex >= ingredients.size()) {
            return true;
        }
        PlannedIngredient ingredient = ingredients.get(ingredientIndex);
        return allocateIngredient(ingredients, available, consumed, ingredientIndex, ingredient.count(), 0);
    }

    private static boolean allocateIngredient(List<PlannedIngredient> ingredients,
                                              List<ItemStack> available,
                                              List<ItemStack> consumed,
                                              int ingredientIndex,
                                              int remaining,
                                              int stackIndex) {
        if (remaining <= 0) {
            return matchIngredient(ingredients, available, consumed, ingredientIndex + 1);
        }
        if (stackIndex >= available.size()) {
            return false;
        }

        ItemStack stack = available.get(stackIndex);
        if (stack.getCount() > 0 && ingredients.get(ingredientIndex).ingredient().test(stack)) {
            int maxTaken = Math.min(remaining, stack.getCount());
            for (int taken = maxTaken; taken > 0; taken--) {
                ItemStack consumedStack = stack.copyWithCount(taken);
                stack.shrink(taken);
                consumed.add(consumedStack);
                if (allocateIngredient(ingredients, available, consumed, ingredientIndex, remaining - taken, stackIndex + 1)) {
                    return true;
                }
                consumed.remove(consumed.size() - 1);
                stack.grow(taken);
            }
        }

        return allocateIngredient(ingredients, available, consumed, ingredientIndex, remaining, stackIndex + 1);
    }

    private static List<ItemStack> copyConsumedStacks(List<ItemStack> consumed) {
        return consumed.stream()
                .map(ItemStack::copy)
                .toList();
    }
}
