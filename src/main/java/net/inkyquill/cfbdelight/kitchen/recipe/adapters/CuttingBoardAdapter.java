package net.inkyquill.cfbdelight.kitchen.recipe.adapters;

import net.inkyquill.cfbdelight.kitchen.recipe.KitchenRecipeAdapter;
import net.inkyquill.cfbdelight.kitchen.recipe.KitchenStationType;
import net.inkyquill.cfbdelight.kitchen.recipe.PlannedIngredient;
import net.inkyquill.cfbdelight.kitchen.recipe.PlannedRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;

import java.util.List;
import java.util.Optional;

public final class CuttingBoardAdapter implements KitchenRecipeAdapter<CuttingBoardRecipe> {
    @Override
    public boolean supports(Recipe<?> recipe) {
        return recipe instanceof CuttingBoardRecipe;
    }

    @Override
    public Optional<PlannedRecipe> adapt(ResourceLocation id, CuttingBoardRecipe recipe, HolderLookup.Provider registries) {
        List<ItemStack> outputs = recipe.getRollableResults().stream()
                .filter(result -> result.chance() >= 1.0f)
                .map(result -> result.stack().copy())
                .filter(stack -> !stack.isEmpty())
                .toList();
        if (outputs.isEmpty() || recipe.getIngredients().isEmpty()) {
            return Optional.empty();
        }

        List<PlannedIngredient> ingredients = List.of(new PlannedIngredient(recipe.getIngredients().getFirst(), 1));
        return Optional.of(new PlannedRecipe(id, ingredients, outputs, KitchenStationType.CUTTING_BOARD,
                Optional.empty(), Optional.empty(), 100, true));
    }
}
