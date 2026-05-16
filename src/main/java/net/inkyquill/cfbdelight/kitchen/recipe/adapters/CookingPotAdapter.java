package net.inkyquill.cfbdelight.kitchen.recipe.adapters;

import net.inkyquill.cfbdelight.kitchen.recipe.KitchenRecipeAdapter;
import net.inkyquill.cfbdelight.kitchen.recipe.KitchenStationType;
import net.inkyquill.cfbdelight.kitchen.recipe.PlannedIngredient;
import net.inkyquill.cfbdelight.kitchen.recipe.PlannedRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import java.util.List;
import java.util.Optional;

public final class CookingPotAdapter implements KitchenRecipeAdapter<CookingPotRecipe> {
    @Override
    public boolean supports(Recipe<?> recipe) {
        return recipe instanceof CookingPotRecipe;
    }

    @Override
    public Optional<PlannedRecipe> adapt(ResourceLocation id, CookingPotRecipe recipe, HolderLookup.Provider registries) {
        ItemStack output = recipe.getResultItem(registries).copy();
        if (output.isEmpty()) {
            return Optional.empty();
        }

        List<PlannedIngredient> ingredients = recipe.getIngredients().stream()
                .filter(ingredient -> !ingredient.isEmpty())
                .map(ingredient -> new PlannedIngredient(ingredient, 1))
                .toList();

        Optional<ItemStack> container = Optional.of(recipe.getOutputContainer())
                .filter(stack -> !stack.isEmpty())
                .map(ItemStack::copy);
        return Optional.of(new PlannedRecipe(id, ingredients, List.of(output), KitchenStationType.COOKING_POT,
                container, Optional.empty(), recipe.getCookTime(), true));
    }
}
