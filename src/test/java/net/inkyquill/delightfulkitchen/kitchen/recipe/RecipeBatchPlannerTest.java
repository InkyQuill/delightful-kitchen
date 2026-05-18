package net.inkyquill.delightfulkitchen.kitchen.recipe;

import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryEndpoint;
import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryService;
import net.inkyquill.delightfulkitchen.kitchen.KitchenScanResult;
import net.inkyquill.delightfulkitchen.kitchen.utility.KitchenUtilityRequirement;
import net.inkyquill.delightfulkitchen.kitchen.utility.KitchenUtilityService;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
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

class RecipeBatchPlannerTest {
    @BeforeAll
    static void bootStrapMinecraft() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void quantityOneReturnsSameStateAndStepsAsSinglePlanner() {
        PlannedRecipe bread = recipe(
                "bread",
                List.of(new PlannedIngredient(Ingredient.of(Items.WHEAT), 3)),
                new ItemStack(Items.BREAD)
        );
        RecipePlanner singlePlanner = planner(List.of(bread));
        RecipeBatchPlanner batchPlanner = new RecipeBatchPlanner(singlePlanner);
        KitchenInventorySnapshot snapshot = new KitchenInventorySnapshot(List.of(new ItemStack(Items.WHEAT, 3)));

        RecipePlan singlePlan = singlePlanner.plan(bread, snapshot);
        RecipeBatchPlan batchPlan = batchPlanner.plan(bread, snapshot, 1);

        assertEquals(singlePlan.state(), batchPlan.plan().state());
        assertEquals(singlePlan.steps().stream().map(PlannedRecipe::id).toList(),
                batchPlan.plan().steps().stream().map(PlannedRecipe::id).toList());
        assertEquals(1, batchPlan.requestedQuantity());
        assertEquals(1, batchPlan.plannedQuantity());
        assertEquals(1, batchPlan.expectedOutputCount());
    }

    @Test
    void quantityTwoConsumesFromSimulatedSnapshotBetweenPasses() {
        PlannedRecipe bread = recipe(
                "bread",
                List.of(new PlannedIngredient(Ingredient.of(Items.WHEAT), 3)),
                new ItemStack(Items.BREAD)
        );
        RecipeBatchPlanner batchPlanner = new RecipeBatchPlanner(planner(List.of(bread)));

        RecipeBatchPlan batchPlan = batchPlanner.plan(bread,
                new KitchenInventorySnapshot(List.of(new ItemStack(Items.WHEAT, 6))), 2);

        assertEquals(PlanState.READY, batchPlan.plan().state());
        assertTrue(batchPlan.plan().blockers().isEmpty());
        assertEquals(List.of(bread.id(), bread.id()), batchPlan.plan().steps().stream().map(PlannedRecipe::id).toList());
        assertEquals(2, batchPlan.plannedQuantity());
        assertEquals(2, batchPlan.expectedOutputCount());
        assertEquals(2, batchPlan.plan().output().getCount());
    }

    @Test
    void quantityTwoBlocksWhenOnlyOneCraftHasRawIngredients() {
        PlannedRecipe bread = recipe(
                "bread",
                List.of(new PlannedIngredient(Ingredient.of(Items.WHEAT), 3)),
                new ItemStack(Items.BREAD)
        );
        RecipePlanner singlePlanner = planner(List.of(bread));
        RecipeBatchPlanner batchPlanner = new RecipeBatchPlanner(singlePlanner);
        RecipePlan failedPass = singlePlanner.plan(bread, new KitchenInventorySnapshot(List.of()));

        RecipeBatchPlan batchPlan = batchPlanner.plan(bread,
                new KitchenInventorySnapshot(List.of(new ItemStack(Items.WHEAT, 3))), 2);

        assertEquals(PlanState.BLOCKED, batchPlan.plan().state());
        assertEquals(failedPass.blockers(), batchPlan.plan().blockers());
        assertEquals(List.of(bread.id(), bread.id()), batchPlan.plan().steps().stream().map(PlannedRecipe::id).toList());
        assertEquals(2, batchPlan.requestedQuantity());
        assertEquals(1, batchPlan.plannedQuantity());
        assertEquals(2, batchPlan.expectedOutputCount());
    }

    @Test
    void quantityTwoBlocksWhenTargetOutputWouldSatisfyNextPassIngredient() {
        PlannedRecipe slicedBread = recipe(
                "sliced_bread",
                List.of(new PlannedIngredient(Ingredient.of(Items.BREAD), 1)),
                new ItemStack(Items.BREAD)
        );
        RecipeBatchPlanner batchPlanner = new RecipeBatchPlanner(planner(List.of(slicedBread)));

        RecipeBatchPlan batchPlan = batchPlanner.plan(slicedBread,
                new KitchenInventorySnapshot(List.of(new ItemStack(Items.BREAD))), 2);

        assertEquals(PlanState.BLOCKED, batchPlan.plan().state());
        assertEquals(List.of(slicedBread.id(), slicedBread.id()),
                batchPlan.plan().steps().stream().map(PlannedRecipe::id).toList());
        assertEquals(1, batchPlan.plannedQuantity());
    }

    @Test
    void quantityWithIntermediatesConcatenatesIntermediateStepsBeforeFinalStepsForEachCraft() {
        PlannedRecipe bread = recipe(
                "bread",
                List.of(new PlannedIngredient(Ingredient.of(Items.WHEAT), 3)),
                new ItemStack(Items.BREAD)
        );
        PlannedRecipe sandwich = recipe(
                "sandwich",
                List.of(new PlannedIngredient(Ingredient.of(Items.BREAD), 1)),
                new ItemStack(Items.COOKED_BEEF)
        );
        RecipeBatchPlanner batchPlanner = new RecipeBatchPlanner(planner(List.of(bread, sandwich)));

        RecipeBatchPlan batchPlan = batchPlanner.plan(sandwich,
                new KitchenInventorySnapshot(List.of(new ItemStack(Items.WHEAT, 6))), 2);

        assertEquals(PlanState.CRAFTABLE_WITH_INTERMEDIATES, batchPlan.plan().state());
        assertEquals(List.of(bread.id(), sandwich.id(), bread.id(), sandwich.id()),
                batchPlan.plan().steps().stream().map(PlannedRecipe::id).toList());
        assertEquals(2, batchPlan.plannedQuantity());
    }

    @Test
    void utilityBackedBatchReusesBorrowedContainerBetweenPasses() {
        PlannedRecipe dough = utilityRecipe(
                "dough",
                List.of(
                        new PlannedIngredient(Ingredient.of(Items.WHEAT), 1),
                        new PlannedIngredient(Ingredient.of(Items.WATER_BUCKET), 1)
                ),
                new ItemStack(Items.BREAD)
        );
        KitchenInventoryService inventory = new KitchenInventoryService(List.of(
                new KitchenInventoryEndpoint(BlockPos.ZERO,
                        new SimpleContainer(new ItemStack(Items.WHEAT, 2), new ItemStack(Items.BUCKET)))
        ));
        RecipePlanner planner = new RecipePlanner(new RecipeIndex(List.of(dough)), Set.of(KitchenStationType.CRAFTING),
                new KitchenUtilityService(scan(true, false), inventory));
        RecipeBatchPlanner batchPlanner = new RecipeBatchPlanner(planner);

        RecipeBatchPlan batchPlan = batchPlanner.plan(dough, inventory.snapshot(), 2);

        assertEquals(PlanState.READY, batchPlan.plan().state());
        assertTrue(batchPlan.plan().blockers().isEmpty());
        assertEquals(2, batchPlan.plannedQuantity());
        assertEquals(List.of(dough.id(), dough.id()),
                batchPlan.plan().steps().stream().map(PlannedRecipe::id).toList());
    }

    @Test
    void quantityIsClampedToSupportedRange() {
        PlannedRecipe cookie = recipe(
                "cookie",
                List.of(new PlannedIngredient(Ingredient.of(Items.WHEAT), 1)),
                new ItemStack(Items.COOKIE, 4)
        );
        RecipeBatchPlanner batchPlanner = new RecipeBatchPlanner(planner(List.of(cookie)));

        RecipeBatchPlan belowMinimum = batchPlanner.plan(cookie,
                new KitchenInventorySnapshot(List.of(new ItemStack(Items.WHEAT, 64))), 0);
        RecipeBatchPlan aboveMaximum = batchPlanner.plan(cookie,
                new KitchenInventorySnapshot(List.of(new ItemStack(Items.WHEAT, 64))), 65);

        assertEquals(1, belowMinimum.requestedQuantity());
        assertEquals(64, aboveMaximum.requestedQuantity());
        assertEquals(64, aboveMaximum.plannedQuantity());
        assertEquals(64, aboveMaximum.expectedOutputCount());
        assertEquals(64, aboveMaximum.plan().output().getCount());
    }

    private static RecipePlanner planner(List<PlannedRecipe> recipes) {
        return new RecipePlanner(new RecipeIndex(recipes), Set.of(KitchenStationType.CRAFTING));
    }

    private static PlannedRecipe recipe(String path, List<PlannedIngredient> ingredients, ItemStack output) {
        return new PlannedRecipe(
                ResourceLocation.fromNamespaceAndPath("delightful_kitchen", path),
                ingredients,
                List.of(output),
                KitchenStationType.CRAFTING,
                Optional.empty(),
                Optional.empty(),
                KitchenUtilityRequirement.NONE,
                0,
                false
        );
    }

    private static PlannedRecipe utilityRecipe(String path, List<PlannedIngredient> ingredients, ItemStack output) {
        return new PlannedRecipe(
                ResourceLocation.fromNamespaceAndPath("delightful_kitchen", path),
                ingredients,
                List.of(output),
                KitchenStationType.CRAFTING,
                Optional.empty(),
                Optional.empty(),
                KitchenUtilityRequirement.NONE,
                0,
                false
        );
    }

    private static KitchenScanResult scan(boolean hasWaterProvider, boolean hasFreezer) {
        return new KitchenScanResult(true, Set.of(BlockPos.ZERO.below()), List.of(BlockPos.ZERO), List.of(),
                hasWaterProvider ? List.of(BlockPos.ZERO.east()) : List.of(),
                hasFreezer ? List.of(BlockPos.ZERO.west()) : List.of());
    }
}
