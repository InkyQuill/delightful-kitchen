package net.inkyquill.delightfulkitchen.menu.chef;

import net.inkyquill.delightfulkitchen.kitchen.execution.KitchenPlanFingerprint;
import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanState;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlannedIngredient;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlannedRecipe;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;
import net.inkyquill.delightfulkitchen.kitchen.recipe.RecipePlan;
import net.inkyquill.delightfulkitchen.kitchen.utility.KitchenUtilityRequirement;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChefCounterViewModelsTest {
    @BeforeAll
    static void bootStrapMinecraft() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void summaryConversionCopiesStackCountsAndBlockers() {
        ItemStack output = new ItemStack(Items.BREAD, 2);
        EnumSet<PlanningBlocker> blockers = EnumSet.of(PlanningBlocker.MISSING_FUEL);
        RecipePlan plan = new RecipePlan(id("bread"), output, PlanState.BLOCKED, List.of(recipe("bread", output)),
                blockers);

        ChefRecipeSummary summary = ChefCounterViewModels.summary(3, plan);
        output.setCount(7);
        blockers.add(PlanningBlocker.MISSING_STATION);
        summary.output().setCount(9);

        assertEquals(3, summary.index());
        assertEquals(2, summary.output().getCount());
        assertEquals(EnumSet.of(PlanningBlocker.MISSING_FUEL), summary.blockers());
        assertEquals(1, summary.stepCount());
    }

    @Test
    void stepConversionIncludesStationUtilityFuelContainerAndAsyncFlag() {
        PlannedRecipe recipe = new PlannedRecipe(
                id("stew"),
                List.of(new PlannedIngredient(Ingredient.of(Items.CARROT), 2)),
                List.of(new ItemStack(Items.RABBIT_STEW, 1)),
                KitchenStationType.COOKING_POT,
                Optional.of(new ItemStack(Items.BOWL)),
                Optional.of(new PlannedIngredient(Ingredient.of(Items.COAL), 1)),
                KitchenUtilityRequirement.WATER,
                200,
                true
        );

        ChefPlanStepView step = ChefCounterViewModels.step(recipe);

        assertEquals(KitchenStationType.COOKING_POT, step.stationType());
        assertEquals(KitchenUtilityRequirement.WATER, step.utilityRequirement());
        assertEquals(Items.COAL, step.fuel().getItem());
        assertEquals(Items.BOWL, step.servingContainer().getItem());
        assertEquals(200, step.processingTime());
        assertTrue(step.asynchronous());
        assertEquals(Items.CARROT, step.inputs().getFirst().getItem());
        assertEquals(2, step.inputs().getFirst().getCount());
    }

    @Test
    void summariesSortDeterministicallyForDuplicateOutputNames() {
        ItemStack apple = named(new ItemStack(Items.APPLE), "Meal");
        ItemStack bread = named(new ItemStack(Items.BREAD), "Meal");
        RecipePlan second = new RecipePlan(id("b_recipe"), bread, PlanState.READY, List.of(recipe("b_recipe", bread)),
                EnumSet.noneOf(PlanningBlocker.class));
        RecipePlan first = new RecipePlan(id("a_recipe"), apple, PlanState.READY, List.of(recipe("a_recipe", apple)),
                EnumSet.noneOf(PlanningBlocker.class));

        List<ChefRecipeSummary> summaries = ChefCounterViewModels.summaries(List.of(second, first));

        assertEquals(List.of(id("a_recipe"), id("b_recipe")),
                summaries.stream().map(ChefRecipeSummary::recipeId).toList());
        assertEquals(List.of(1, 0), summaries.stream().map(ChefRecipeSummary::index).toList());
    }

    @Test
    void recordAccessorsDoNotExposeMutableBlockerSets() {
        EnumSet<PlanningBlocker> blockers = EnumSet.of(PlanningBlocker.MISSING_FUEL);
        ChefRecipeSummary summary = new ChefRecipeSummary(0, id("blocked"), new ItemStack(Items.BREAD),
                PlanState.BLOCKED, blockers, 1);

        blockers.add(PlanningBlocker.MISSING_STATION);
        EnumSet<PlanningBlocker> accessed = summary.blockers();
        accessed.clear();

        assertEquals(EnumSet.of(PlanningBlocker.MISSING_FUEL), summary.blockers());
    }

    @Test
    void detailsCopiesStacksAndIncludesFingerprint() {
        ItemStack output = new ItemStack(Items.BREAD, 2);
        RecipePlan plan = new RecipePlan(id("bread"), output, PlanState.READY, List.of(recipe("bread", output)),
                EnumSet.noneOf(PlanningBlocker.class));
        KitchenPlanFingerprint fingerprint = new KitchenPlanFingerprint(1, 2, 3, 4, 5);

        ChefPlanDetails details = ChefCounterViewModels.details(4, plan, 3, fingerprint);
        output.setCount(10);
        details.output().setCount(99);

        assertEquals(4, details.selectedIndex());
        assertEquals(2, details.output().getCount());
        assertEquals(6, details.expectedOutputCount());
        assertEquals(fingerprint, details.fingerprint());
    }

    private static PlannedRecipe recipe(String path, ItemStack output) {
        return new PlannedRecipe(
                id(path),
                List.of(new PlannedIngredient(Ingredient.of(Items.WHEAT), 1)),
                List.of(output),
                KitchenStationType.CRAFTING,
                Optional.empty(),
                Optional.empty(),
                KitchenUtilityRequirement.NONE,
                0,
                false
        );
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("delightful_kitchen", path);
    }
}
