package net.inkyquill.delightfulkitchen.compat.rei.display;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class CookingPotDisplay extends BasicDisplay {

    public static final String COOK_TIME_KEY = "cookTime";

    private final int cookTime;

    public CookingPotDisplay(RecipeHolder<CookingPotRecipe> holder) {
        super(buildInputs(holder.value()), buildOutputs(holder.value()), Optional.of(holder.id()));
        this.cookTime = holder.value().getCookTime();
    }

    private static List<EntryIngredient> buildInputs(CookingPotRecipe recipe) {
        List<EntryIngredient> inputs = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            inputs.add(EntryIngredients.ofIngredient(ingredient));
        }
        // Container (bowl/bottle) is treated as the last input
        ItemStack container = recipe.getOutputContainer();
        if (!container.isEmpty()) {
            inputs.add(EntryIngredients.of(container));
        }
        return inputs;
    }

    private static List<EntryIngredient> buildOutputs(CookingPotRecipe recipe) {
        return Collections.singletonList(
                EntryIngredients.of(recipe.getResultItem(BasicDisplay.registryAccess())));
    }

    public int getCookTime() {
        return cookTime;
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return CookingPotDisplay.id();
    }

    public static CategoryIdentifier<CookingPotDisplay> id() {
        return CategoryIdentifier.of("delightful_kitchen", "cooking_pot");
    }
}
