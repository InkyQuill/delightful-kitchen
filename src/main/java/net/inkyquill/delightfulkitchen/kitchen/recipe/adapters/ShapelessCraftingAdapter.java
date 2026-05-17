package net.inkyquill.delightfulkitchen.kitchen.recipe.adapters;

import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenRecipeAdapter;
import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlannedIngredient;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlannedRecipe;
import net.inkyquill.delightfulkitchen.kitchen.utility.KitchenUtilityRequirement;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.util.List;
import java.util.Optional;

public final class ShapelessCraftingAdapter implements KitchenRecipeAdapter<ShapelessRecipe> {
    @Override
    public boolean supports(Recipe<?> recipe) {
        return recipe instanceof ShapelessRecipe;
    }

    @Override
    public Optional<PlannedRecipe> adapt(ResourceLocation id, ShapelessRecipe recipe, HolderLookup.Provider registries) {
        ItemStack output = recipe.getResultItem(registries).copy();
        if (output.isEmpty()) {
            return Optional.empty();
        }

        List<PlannedIngredient> ingredients = recipe.getIngredients().stream()
                .filter(ingredient -> !ingredient.isEmpty())
                .map(ingredient -> new PlannedIngredient(ingredient, 1))
                .toList();

        return Optional.of(new PlannedRecipe(id, ingredients, List.of(output), KitchenStationType.CRAFTING,
                Optional.empty(), Optional.empty(), KitchenUtilityRequirement.NONE, 0, false));
    }
}
