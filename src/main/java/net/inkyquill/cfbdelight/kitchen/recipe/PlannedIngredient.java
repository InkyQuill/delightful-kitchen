package net.inkyquill.cfbdelight.kitchen.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Arrays;
import java.util.List;

public record PlannedIngredient(Ingredient ingredient, int count) {
    public PlannedIngredient {
        if (ingredient == null) {
            throw new IllegalArgumentException("ingredient cannot be null");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }

    public List<ItemStack> displayStacks() {
        return Arrays.stream(ingredient.getItems())
                .map(stack -> stack.copyWithCount(count))
                .toList();
    }
}
