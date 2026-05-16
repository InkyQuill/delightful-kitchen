package net.inkyquill.cfbdelight.kitchen.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import java.util.Optional;

public interface KitchenRecipeAdapter<T extends Recipe<?>> {
    boolean supports(Recipe<?> recipe);

    Optional<PlannedRecipe> adapt(ResourceLocation id, T recipe, HolderLookup.Provider registries);
}
