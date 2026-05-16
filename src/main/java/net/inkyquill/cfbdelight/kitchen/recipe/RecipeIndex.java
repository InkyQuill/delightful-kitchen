package net.inkyquill.cfbdelight.kitchen.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RecipeIndex {
    private final List<PlannedRecipe> recipes;
    private final List<PlannedRecipe> edibleOutputs;
    private final Map<String, List<PlannedRecipe>> recipesByOutputKey;

    private RecipeIndex(List<PlannedRecipe> recipes) {
        this.recipes = Objects.requireNonNull(recipes, "recipes").stream()
                .sorted(Comparator.comparing(recipe -> recipe.id().toString()))
                .toList();
        this.edibleOutputs = this.recipes.stream()
                .filter(recipe -> recipe.outputs().stream().anyMatch(RecipeIndex::isEdible))
                .toList();

        Map<String, List<PlannedRecipe>> recipesByOutputKey = new LinkedHashMap<>();
        for (PlannedRecipe recipe : this.recipes) {
            for (ItemStack output : recipe.outputs()) {
                recipesByOutputKey.computeIfAbsent(stackKey(output), key -> new ArrayList<>()).add(recipe);
            }
        }
        recipesByOutputKey.replaceAll((key, value) -> List.copyOf(value));
        this.recipesByOutputKey = Map.copyOf(recipesByOutputKey);
    }

    public static RecipeIndex build(RecipeManager recipeManager, HolderLookup.Provider registries) {
        List<PlannedRecipe> plannedRecipes = Objects.requireNonNull(recipeManager, "recipeManager")
                .getRecipes()
                .stream()
                .map(holder -> adapt(holder, registries))
                .flatMap(Optional::stream)
                .toList();
        return new RecipeIndex(plannedRecipes);
    }

    public List<PlannedRecipe> recipes() {
        return recipes;
    }

    public List<PlannedRecipe> edibleOutputs() {
        return edibleOutputs;
    }

    public List<PlannedRecipe> recipesProducing(ItemStack stack) {
        return recipesByOutputKey.getOrDefault(stackKey(stack), List.of());
    }

    public static String stackKey(ItemStack stack) {
        return Objects.requireNonNull(stack, "stack").getItemHolder().unwrapKey()
                .map(key -> key.location().toString())
                .orElse(stack.getItem().toString());
    }

    private static Optional<PlannedRecipe> adapt(RecipeHolder<?> holder, HolderLookup.Provider registries) {
        return KitchenRecipeAdapters.adapt(holder.id(), holder.value(), registries);
    }

    private static boolean isEdible(ItemStack stack) {
        return stack.getFoodProperties(null) != null;
    }
}
