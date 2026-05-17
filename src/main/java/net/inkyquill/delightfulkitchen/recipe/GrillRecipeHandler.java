package net.inkyquill.delightfulkitchen.recipe;

import net.blay09.mods.cookingforblockheads.api.IngredientToken;
import net.blay09.mods.cookingforblockheads.api.KitchenRecipeHandler;
import net.blay09.mods.cookingforblockheads.crafting.CraftingContext;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import java.util.List;

/**
 * Bridges vanilla {@link CampfireCookingRecipe} (the recipe type used by
 * Farmer's Delight's Stove / our Grill block) with CfB's kitchen recipe system.
 * <p>
 * This allows the CfB recipe book to display grill recipes and automatically
 * route ingredient dispatch to a nearby {@link net.inkyquill.delightfulkitchen.block.entity.GrillBlockEntity}.
 */
public class GrillRecipeHandler implements KitchenRecipeHandler<CampfireCookingRecipe> {

    @Override
    public int mapToMatrixSlot(CampfireCookingRecipe recipe, int ingredientIndex) {
        // Campfire cooking is single-ingredient; place it in the center cell.
        return 4;
    }

    /**
     * Delegates to the nearest {@link net.inkyquill.delightfulkitchen.block.entity.GrillBlockEntity}
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
