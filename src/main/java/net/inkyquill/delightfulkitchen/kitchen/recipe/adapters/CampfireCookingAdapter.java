package net.inkyquill.delightfulkitchen.kitchen.recipe.adapters;

import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenRecipeAdapter;
import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlannedIngredient;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlannedRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;

import java.util.List;
import java.util.Optional;

public final class CampfireCookingAdapter implements KitchenRecipeAdapter<CampfireCookingRecipe> {
    @Override
    public boolean supports(Recipe<?> recipe) {
        return recipe instanceof CampfireCookingRecipe;
    }

    @Override
    public Optional<PlannedRecipe> adapt(ResourceLocation id, CampfireCookingRecipe recipe,
                                        HolderLookup.Provider registries) {
        ItemStack output = recipe.getResultItem(registries).copy();
        if (output.isEmpty() || recipe.getIngredients().isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new PlannedRecipe(id, List.of(new PlannedIngredient(recipe.getIngredients().getFirst(), 1)),
                List.of(output), KitchenStationType.GRILL, Optional.empty(), FuelRequirements.furnaceFuel(),
                recipe.getCookingTime(), true));
    }
}
