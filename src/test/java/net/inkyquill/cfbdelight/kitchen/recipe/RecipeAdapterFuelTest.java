package net.inkyquill.cfbdelight.kitchen.recipe;

import net.inkyquill.cfbdelight.kitchen.recipe.adapters.CampfireCookingAdapter;
import net.inkyquill.cfbdelight.kitchen.recipe.adapters.CookingPotAdapter;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeAdapterFuelTest {
    @BeforeAll
    static void bootStrapMinecraft() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void campfireRecipesRequireFuelForGrillPlanning() {
        CampfireCookingRecipe recipe = new CampfireCookingRecipe("",
                CookingBookCategory.FOOD,
                Ingredient.of(Items.BEEF),
                new ItemStack(Items.COOKED_BEEF),
                0.35F,
                600);

        PlannedRecipe planned = new CampfireCookingAdapter()
                .adapt(ResourceLocation.fromNamespaceAndPath("cfb_delight", "test_grilled_beef"), recipe, null)
                .orElseThrow();

        assertTrue(planned.fuel().isPresent());
        assertTrue(planned.fuel().orElseThrow().ingredient().test(new ItemStack(Items.COAL)));
    }

    @Test
    void cookingPotRecipesRequireFuelForPotPlanning() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.of(Items.BEEF));
        CookingPotRecipe recipe = new CookingPotRecipe("",
                null,
                ingredients,
                new ItemStack(Items.RABBIT_STEW),
                new ItemStack(Items.BOWL),
                1.0F,
                200);

        PlannedRecipe planned = new CookingPotAdapter()
                .adapt(ResourceLocation.fromNamespaceAndPath("cfb_delight", "test_stew"), recipe, null)
                .orElseThrow();

        assertTrue(planned.fuel().isPresent());
        assertTrue(planned.fuel().orElseThrow().ingredient().test(new ItemStack(Items.COAL)));
    }
}
