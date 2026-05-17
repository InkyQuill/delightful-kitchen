package net.inkyquill.delightfulkitchen.compat.rei.display;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.world.item.crafting.RecipeHolder;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.crafting.ingredient.ChanceResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CuttingBoardDisplay extends BasicDisplay {

    public CuttingBoardDisplay(RecipeHolder<CuttingBoardRecipe> holder) {
        super(buildInputs(holder.value()), buildOutputs(holder.value()), Optional.of(holder.id()));
    }

    private static List<EntryIngredient> buildInputs(CuttingBoardRecipe recipe) {
        List<EntryIngredient> inputs = new ArrayList<>();
        // Tool is the first ingredient
        inputs.add(EntryIngredients.ofIngredient(recipe.getTool()));
        // Material to cut
        inputs.add(EntryIngredients.ofIngredient(recipe.getIngredients().get(0)));
        return inputs;
    }

    private static List<EntryIngredient> buildOutputs(CuttingBoardRecipe recipe) {
        List<EntryIngredient> outputs = new ArrayList<>();
        for (ChanceResult result : recipe.getRollableResults()) {
            outputs.add(EntryIngredients.of(result.stack()));
        }
        return outputs;
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return CuttingBoardDisplay.id();
    }

    public static CategoryIdentifier<CuttingBoardDisplay> id() {
        return CategoryIdentifier.of("delightful_kitchen", "cutting_board");
    }
}
