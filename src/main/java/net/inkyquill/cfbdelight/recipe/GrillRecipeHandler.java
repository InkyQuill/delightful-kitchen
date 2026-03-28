package net.inkyquill.cfbdelight.recipe;

import net.blay09.mods.cookingforblockheads.api.IngredientToken;
import net.blay09.mods.cookingforblockheads.api.KitchenRecipeHandler;
import net.blay09.mods.cookingforblockheads.crafting.CraftingContext;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SingleRecipeInput;

import java.util.List;
import java.util.Optional;

/**
 * Bridges vanilla {@link CampfireCookingRecipe} (the recipe type used by
 * Farmer's Delight's Stove / our Grill block) with CfB's kitchen recipe system.
 * <p>
 * This allows the CfB recipe book to display grill recipes and automatically
 * route ingredient dispatch to a nearby {@link net.inkyquill.cfbdelight.block.entity.GrillBlockEntity}.
 */
public class GrillRecipeHandler implements KitchenRecipeHandler<SingleRecipeInput, CampfireCookingRecipe> {

    @Override
    public int mapToMatrixSlot(CampfireCookingRecipe recipe, int ingredientIndex) {
        // Campfire cooking is single-ingredient; place it in the center cell.
        return 4;
    }

    @Override
    public List<Optional<Ingredient>> getIngredients(CampfireCookingRecipe recipe) {
        return List.of(Optional.of(recipe.input()));
    }

    @Override
    public ItemStack predictResultItem(CampfireCookingRecipe recipe) {
        return recipe.result().copy();
    }

    /**
     * Delegates to the nearest {@link net.inkyquill.cfbdelight.block.entity.GrillBlockEntity}
     * processor in the kitchen.
     */
    @Override
    public ItemStack assemble(CraftingContext context, CampfireCookingRecipe recipe,
                              List<IngredientToken> ingredientTokens, RegistryAccess registryAccess) {
        for (var processor : context.getItemProcessors()) {
            if (processor.canProcess(recipe.getType())) {
                context.notify(processor.processRecipe(recipe, ingredientTokens));
                return ItemStack.EMPTY;
            }
        }
        return ItemStack.EMPTY;
    }
}
