package net.inkyquill.delightfulkitchen.recipe;

import net.blay09.mods.cookingforblockheads.api.IngredientToken;
import net.blay09.mods.cookingforblockheads.api.KitchenRecipeHandler;
import net.blay09.mods.cookingforblockheads.crafting.CraftingContext;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import java.util.List;

/**
 * Bridges Farmer's Delight {@link CookingPotRecipe} with Cooking for Blockheads'
 * kitchen recipe system.
 * <p>
 * Responsibilities:
 * <ol>
 *   <li>Expose the recipe's ingredients (including the output container – bowl or
 *       bottle) so CfB's recipe book knows what needs to be in the kitchen.</li>
 *   <li>Map each ingredient to a slot in CfB's 3×3 display matrix.</li>
 *   <li>On assemble, forward the ingredient tokens to whatever
 *       {@link net.blay09.mods.cookingforblockheads.api.KitchenItemProcessor} can
 *       handle {@code farmersdelight:cooking} (i.e. a {@code PotStandBlockEntity}).</li>
 * </ol>
 *
 * <h3>Holder / container ingredient</h3>
 * The cooking-pot recipe has an explicit {@code container} output (the bowl or
 * bottle the meal is served in). We append that as the <em>last</em> ingredient so
 * CfB will search the kitchen for it and include it in the ingredient-token list.
 * The {@link net.inkyquill.delightfulkitchen.block.entity.PotStandBlockEntity} processor
 * separates the container token from the ingredient tokens before cooking starts.
 */
public class CookingPotRecipeHandler implements KitchenRecipeHandler<CookingPotRecipe> {

    /**
     * Maps each recipe ingredient (and trailing container) to a slot on CfB's 3×3
     * display grid. Cooking-pot recipes have 1–6 ingredients, so we fill slots
     * left-to-right starting from slot 0 (top-left). The container occupies slot 8
     * (bottom-right) to visually separate it from the ingredient block.
     */
    @Override
    public int mapToMatrixSlot(CookingPotRecipe recipe, int ingredientIndex) {
        int ingredientCount = recipe.getIngredients().size();
        if (ingredientIndex >= ingredientCount) {
            // container ingredient – put it in the last cell of the grid
            return 8;
        }
        // ingredients fill slots 0-5 (we only use top two rows for ≤ 6 items)
        return ingredientIndex;
    }

    /**
     * Delegates actual processing to whichever {@link net.blay09.mods.cookingforblockheads.api.KitchenItemProcessor}
     * in the kitchen reports {@code canProcess(farmersdelight:cooking)}, i.e. the
     * {@link net.inkyquill.delightfulkitchen.block.entity.PotStandBlockEntity}.
     */
    @Override
    public ItemStack assemble(CraftingContext context, CookingPotRecipe recipe,
                              List<IngredientToken> ingredientTokens, RegistryAccess registryAccess) {
        for (var processor : context.getItemProcessors()) {
            if (processor.canProcess(recipe.getType())) {
                context.notify(processor.processRecipe(recipe, ingredientTokens));
                return ItemStack.EMPTY; // result arrives asynchronously via the block entity
            }
        }
        return ItemStack.EMPTY;
    }
}
