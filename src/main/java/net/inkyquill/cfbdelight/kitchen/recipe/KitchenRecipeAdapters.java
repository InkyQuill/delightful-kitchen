package net.inkyquill.cfbdelight.kitchen.recipe;

import net.inkyquill.cfbdelight.kitchen.recipe.adapters.CampfireCookingAdapter;
import net.inkyquill.cfbdelight.kitchen.recipe.adapters.CookingPotAdapter;
import net.inkyquill.cfbdelight.kitchen.recipe.adapters.CuttingBoardAdapter;
import net.inkyquill.cfbdelight.kitchen.recipe.adapters.ShapedCraftingAdapter;
import net.inkyquill.cfbdelight.kitchen.recipe.adapters.ShapelessCraftingAdapter;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class KitchenRecipeAdapters {
    private static final List<KitchenRecipeAdapter<? extends Recipe<?>>> ADAPTERS = new ArrayList<>();

    static {
        register(new ShapedCraftingAdapter());
        register(new ShapelessCraftingAdapter());
        register(new CuttingBoardAdapter());
        register(new CookingPotAdapter());
        register(new CampfireCookingAdapter());
    }

    private KitchenRecipeAdapters() {
    }

    public static void register(KitchenRecipeAdapter<? extends Recipe<?>> adapter) {
        ADAPTERS.add(Objects.requireNonNull(adapter, "adapter"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Optional<PlannedRecipe> adapt(ResourceLocation id, Recipe<?> recipe, HolderLookup.Provider registries) {
        for (KitchenRecipeAdapter adapter : ADAPTERS) {
            if (adapter.supports(recipe)) {
                return adapter.adapt(id, recipe, registries);
            }
        }
        return Optional.empty();
    }
}
