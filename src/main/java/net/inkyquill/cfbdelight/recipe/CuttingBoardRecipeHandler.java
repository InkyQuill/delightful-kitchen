package net.inkyquill.cfbdelight.recipe;

import net.blay09.mods.cookingforblockheads.api.IngredientToken;
import net.blay09.mods.cookingforblockheads.api.KitchenRecipeHandler;
import net.blay09.mods.cookingforblockheads.crafting.CraftingContext;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;

import java.util.List;

/**
 * Bridges Farmer's Delight {@link CuttingBoardRecipe} with Cooking for Blockheads'
 * kitchen recipe system.
 * <p>
 * Cutting board recipes accept a single ingredient and a tool. We expose only the
 * ingredient to CfB (the tool is provided internally by the
 * {@link net.inkyquill.cfbdelight.block.entity.AutoCutterBlockEntity} which
 * carries a built-in knife). The tool ingredient is shown in slot 5 for visual
 * reference in the recipe book.
 *
 * <h3>Multi-output display</h3>
 * CfB's {@link #predictResultItem} can only return a single {@link ItemStack}.
 * We return the first (primary) result. All results, including chance-based ones,
 * are still produced by the {@code AutoCutterBlockEntity} processor.
 */
public class CuttingBoardRecipeHandler implements KitchenRecipeHandler<CuttingBoardRecipe> {

    /**
     * Slot 0 – ingredient to cut.<br>
     * Slot 5 – the required tool (shown but not consumed by CfB; the cutter has a built-in knife).
     */
    @Override
    public int mapToMatrixSlot(CuttingBoardRecipe recipe, int ingredientIndex) {
        return switch (ingredientIndex) {
            case 0 -> 0; // ingredient
            case 1 -> 5; // tool (displayed only)
            default -> ingredientIndex;
        };
    }

    /**
     * Delegates to the {@link net.inkyquill.cfbdelight.block.entity.AutoCutterBlockEntity}
     * processor. Only the ingredient token (index 0) is forwarded; the tool token (if
     * present) is not consumed here since the cutter supplies its own tool.
     */
    @Override
    public ItemStack assemble(CraftingContext context, CuttingBoardRecipe recipe,
                              List<IngredientToken> ingredientTokens, RegistryAccess registryAccess) {
        for (var processor : context.getItemProcessors()) {
            if (processor.canProcess(recipe.getType())) {
                // Pass only the ingredient token, not the tool token
                List<IngredientToken> ingredientOnly = ingredientTokens.isEmpty()
                        ? ingredientTokens
                        : List.of(ingredientTokens.get(0));
                context.notify(processor.processRecipe(recipe, ingredientOnly));
                return ItemStack.EMPTY;
            }
        }
        return ItemStack.EMPTY;
    }
}
