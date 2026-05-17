package net.inkyquill.delightfulkitchen.kitchen.recipe;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipePlannerTest {
    @BeforeAll
    static void bootStrapMinecraft() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void plansMultiStepIntermediateRecipe() {
        PlannedRecipe bread = recipe(
                "bread",
                List.of(new PlannedIngredient(Ingredient.of(Items.WHEAT), 3)),
                new ItemStack(Items.BREAD),
                KitchenStationType.CRAFTING
        );
        PlannedRecipe sandwich = recipe(
                "sandwich",
                List.of(new PlannedIngredient(Ingredient.of(Items.BREAD), 1)),
                new ItemStack(Items.COOKED_BEEF),
                KitchenStationType.CRAFTING
        );
        RecipePlanner planner = new RecipePlanner(new RecipeIndex(List.of(bread, sandwich)),
                Set.of(KitchenStationType.CRAFTING));

        RecipePlan plan = planner.plan(sandwich, new KitchenInventorySnapshot(List.of(new ItemStack(Items.WHEAT, 3))));

        assertEquals(PlanState.CRAFTABLE_WITH_INTERMEDIATES, plan.state());
        assertTrue(plan.blockers().isEmpty());
        assertEquals(List.of(bread.id(), sandwich.id()), plan.steps().stream().map(PlannedRecipe::id).toList());
    }

    @Test
    void blocksWhenRequiredStationIsMissing() {
        PlannedRecipe grilledBeef = recipe(
                "grilled_beef",
                List.of(new PlannedIngredient(Ingredient.of(Items.BEEF), 1)),
                new ItemStack(Items.COOKED_BEEF),
                KitchenStationType.GRILL
        );
        RecipePlanner planner = new RecipePlanner(new RecipeIndex(List.of(grilledBeef)),
                Set.of(KitchenStationType.CRAFTING));

        RecipePlan plan = planner.plan(grilledBeef, new KitchenInventorySnapshot(List.of(new ItemStack(Items.BEEF))));

        assertEquals(PlanState.BLOCKED, plan.state());
        assertTrue(plan.blockers().contains(PlanningBlocker.MISSING_STATION));
        assertEquals(List.of(grilledBeef.id()), plan.steps().stream().map(PlannedRecipe::id).toList());
    }

    private static PlannedRecipe recipe(String path, List<PlannedIngredient> ingredients, ItemStack output,
                                        KitchenStationType stationType) {
        return new PlannedRecipe(
                ResourceLocation.fromNamespaceAndPath("delightful_kitchen", path),
                ingredients,
                List.of(output),
                stationType,
                Optional.empty(),
                Optional.empty(),
                0,
                false
        );
    }
}
