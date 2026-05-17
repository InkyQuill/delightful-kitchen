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
import net.minecraft.world.level.ItemLike;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeUtilityPlannerTest {
    @BeforeAll
    static void bootStrapMinecraft() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void waterBucketIngredientCanBePlannedFromWaterProviderAndEmptyBucket() {
        PlannedRecipe dough = recipe("dough", Items.WATER_BUCKET, Items.BREAD);
        KitchenInventoryService inventory = inventory(new ItemStack(Items.WHEAT), new ItemStack(Items.BUCKET));
        RecipePlan plan = planner(dough, service(scan(true, false), inventory)).plan(dough, inventory.snapshot());

        assertEquals(PlanState.READY, plan.state());
        assertTrue(plan.blockers().isEmpty());
        assertEquals(KitchenUtilityRequirement.WATER, plan.steps().getFirst().utilityRequirement());
        assertEquals(List.of(Items.WHEAT), plannedIngredientItems(plan.steps().getFirst()));
    }

    @Test
    void waterBucketIngredientIsBlockedWithoutWaterProvider() {
        PlannedRecipe dough = recipe("dough", Items.WATER_BUCKET, Items.BREAD);
        KitchenInventoryService inventory = inventory(new ItemStack(Items.WHEAT), new ItemStack(Items.BUCKET));
        RecipePlan plan = planner(dough, service(scan(false, false), inventory)).plan(dough, inventory.snapshot());

        assertEquals(PlanState.BLOCKED, plan.state());
        assertTrue(plan.blockers().contains(PlanningBlocker.MISSING_WATER_PROVIDER));
    }

    @Test
    void waterBucketIngredientIsBlockedWithoutWaterContainer() {
        PlannedRecipe dough = recipe("dough", Items.WATER_BUCKET, Items.BREAD);
        KitchenInventoryService inventory = inventory(new ItemStack(Items.WHEAT));
        RecipePlan plan = planner(dough, service(scan(true, false), inventory)).plan(dough, inventory.snapshot());

        assertEquals(PlanState.BLOCKED, plan.state());
        assertTrue(plan.blockers().contains(PlanningBlocker.MISSING_WATER_CONTAINER));
    }

    @Test
    void iceIngredientCanBePlannedFromFreezerWaterProviderAndEmptyBucket() {
        PlannedRecipe chilledFood = recipe("chilled_food", Items.ICE, Items.COOKED_BEEF);
        KitchenInventoryService inventory = inventory(new ItemStack(Items.WHEAT), new ItemStack(Items.BUCKET));
        RecipePlan plan = planner(chilledFood, service(scan(true, true), inventory)).plan(chilledFood, inventory.snapshot());

        assertEquals(PlanState.READY, plan.state());
        assertTrue(plan.blockers().isEmpty());
        assertEquals(KitchenUtilityRequirement.ICE, plan.steps().getFirst().utilityRequirement());
        assertEquals(List.of(Items.WHEAT), plannedIngredientItems(plan.steps().getFirst()));
    }

    @Test
    void snowballIngredientCanBePlannedFromFreezerWaterProviderAndEmptyBottle() {
        PlannedRecipe chilledFood = recipe("powdered_food", Items.SNOWBALL, Items.COOKED_BEEF);
        KitchenInventoryService inventory = inventory(new ItemStack(Items.WHEAT), new ItemStack(Items.GLASS_BOTTLE));
        RecipePlan plan = planner(chilledFood, service(scan(true, true), inventory)).plan(chilledFood, inventory.snapshot());

        assertEquals(PlanState.READY, plan.state());
        assertTrue(plan.blockers().isEmpty());
        assertEquals(KitchenUtilityRequirement.SNOWBALL, plan.steps().getFirst().utilityRequirement());
        assertEquals(List.of(Items.WHEAT), plannedIngredientItems(plan.steps().getFirst()));
    }

    @Test
    void physicalUtilityIngredientsAreUsedWithoutUtilityRequirement() {
        assertPhysicalIngredientUsesNoUtility(recipe("water_recipe", Items.WATER_BUCKET, Items.BREAD),
                new ItemStack(Items.WATER_BUCKET));
        assertPhysicalIngredientUsesNoUtility(recipe("ice_recipe", Items.ICE, Items.COOKED_BEEF),
                new ItemStack(Items.ICE));
        assertPhysicalIngredientUsesNoUtility(recipe("snowball_recipe", Items.SNOWBALL, Items.COOKED_BEEF),
                new ItemStack(Items.SNOWBALL));
    }

    private static void assertPhysicalIngredientUsesNoUtility(PlannedRecipe recipe, ItemStack physicalStack) {
        KitchenInventoryService inventory = inventory(new ItemStack(Items.WHEAT), physicalStack);
        RecipePlan plan = planner(recipe, service(scan(false, false), inventory)).plan(recipe, inventory.snapshot());

        assertEquals(PlanState.READY, plan.state());
        assertTrue(plan.blockers().isEmpty());
        assertEquals(KitchenUtilityRequirement.NONE, plan.steps().getFirst().utilityRequirement());
    }

    @Test
    void utilityBackedIntermediateReservesContainerForLaterUtilityStep() {
        PlannedRecipe dough = recipe("dough", Items.WATER_BUCKET, Items.BREAD);
        PlannedRecipe packedLunch = new PlannedRecipe(
                ResourceLocation.fromNamespaceAndPath("delightful_kitchen", "packed_lunch"),
                List.of(
                        new PlannedIngredient(Ingredient.of(Items.BREAD), 1),
                        new PlannedIngredient(Ingredient.of(Items.WATER_BUCKET), 1)
                ),
                List.of(new ItemStack(Items.COOKED_BEEF)),
                KitchenStationType.CRAFTING,
                Optional.empty(),
                Optional.empty(),
                KitchenUtilityRequirement.NONE,
                0,
                false
        );
        KitchenInventoryService inventory = inventory(new ItemStack(Items.WHEAT), new ItemStack(Items.BUCKET));
        RecipePlanner planner = new RecipePlanner(new RecipeIndex(List.of(dough, packedLunch)),
                Set.of(KitchenStationType.CRAFTING), service(scan(true, false), inventory));

        RecipePlan plan = planner.plan(packedLunch, inventory.snapshot());

        assertEquals(PlanState.BLOCKED, plan.state());
        assertTrue(plan.blockers().contains(PlanningBlocker.MISSING_WATER_CONTAINER));
    }

    @Test
    void singleRecipeWithMultipleMissingUtilityIngredientsIsNotReady() {
        PlannedRecipe chilledDough = new PlannedRecipe(
                ResourceLocation.fromNamespaceAndPath("delightful_kitchen", "chilled_dough"),
                List.of(
                        new PlannedIngredient(Ingredient.of(Items.WHEAT), 1),
                        new PlannedIngredient(Ingredient.of(Items.WATER_BUCKET), 1),
                        new PlannedIngredient(Ingredient.of(Items.ICE), 1)
                ),
                List.of(new ItemStack(Items.BREAD)),
                KitchenStationType.CRAFTING,
                Optional.empty(),
                Optional.empty(),
                KitchenUtilityRequirement.NONE,
                0,
                false
        );
        KitchenInventoryService inventory = inventory(new ItemStack(Items.WHEAT), new ItemStack(Items.BUCKET, 2));

        RecipePlan plan = planner(chilledDough, service(scan(true, true), inventory))
                .plan(chilledDough, inventory.snapshot());

        assertEquals(PlanState.BLOCKED, plan.state());
        assertTrue(plan.blockers().contains(PlanningBlocker.MISSING_RAW_INGREDIENT));
    }

    @Test
    void utilityContainerCannotAlsoSatisfyPhysicalIngredientInSameStep() {
        PlannedRecipe bucketedDough = new PlannedRecipe(
                ResourceLocation.fromNamespaceAndPath("delightful_kitchen", "bucketed_dough"),
                List.of(
                        new PlannedIngredient(Ingredient.of(Items.WATER_BUCKET), 1),
                        new PlannedIngredient(Ingredient.of(Items.BUCKET), 1),
                        new PlannedIngredient(Ingredient.of(Items.WHEAT), 1)
                ),
                List.of(new ItemStack(Items.BREAD)),
                KitchenStationType.CRAFTING,
                Optional.empty(),
                Optional.empty(),
                KitchenUtilityRequirement.NONE,
                0,
                false
        );
        KitchenInventoryService oneBucket = inventory(new ItemStack(Items.WHEAT), new ItemStack(Items.BUCKET));
        KitchenInventoryService twoBuckets = inventory(new ItemStack(Items.WHEAT), new ItemStack(Items.BUCKET, 2));

        RecipePlan blockedPlan = planner(bucketedDough, service(scan(true, false), oneBucket))
                .plan(bucketedDough, oneBucket.snapshot());
        RecipePlan readyPlan = planner(bucketedDough, service(scan(true, false), twoBuckets))
                .plan(bucketedDough, twoBuckets.snapshot());

        assertEquals(PlanState.BLOCKED, blockedPlan.state());
        assertEquals(PlanState.READY, readyPlan.state());
        assertEquals(KitchenUtilityRequirement.WATER, readyPlan.steps().getFirst().utilityRequirement());
        assertEquals(List.of(Items.BUCKET, Items.WHEAT), plannedIngredientItems(readyPlan.steps().getFirst()));
    }

    private static RecipePlanner planner(PlannedRecipe recipe, KitchenUtilityService utilityService) {
        return new RecipePlanner(new RecipeIndex(List.of(recipe)), Set.of(KitchenStationType.CRAFTING), utilityService);
    }

    private static PlannedRecipe recipe(String path, ItemLike utilityItem, ItemLike output) {
        return new PlannedRecipe(
                ResourceLocation.fromNamespaceAndPath("delightful_kitchen", path),
                List.of(
                        new PlannedIngredient(Ingredient.of(Items.WHEAT), 1),
                        new PlannedIngredient(Ingredient.of(utilityItem), 1)
                ),
                List.of(new ItemStack(output)),
                KitchenStationType.CRAFTING,
                Optional.empty(),
                Optional.empty(),
                KitchenUtilityRequirement.NONE,
                0,
                false
        );
    }

    private static List<net.minecraft.world.item.Item> plannedIngredientItems(PlannedRecipe recipe) {
        return recipe.ingredients().stream()
                .flatMap(ingredient -> List.of(ingredient.ingredient().getItems()).stream())
                .map(ItemStack::getItem)
                .toList();
    }

    private static KitchenUtilityService service(KitchenScanResult scan, KitchenInventoryService inventory) {
        return new KitchenUtilityService(scan, inventory);
    }

    private static KitchenInventoryService inventory(ItemStack... stacks) {
        return new KitchenInventoryService(List.of(new KitchenInventoryEndpoint(BlockPos.ZERO, new SimpleContainer(stacks))));
    }

    private static KitchenScanResult scan(boolean hasWaterProvider, boolean hasFreezer) {
        return new KitchenScanResult(true, Set.of(BlockPos.ZERO.below()), List.of(BlockPos.ZERO), List.of(),
                hasWaterProvider ? List.of(BlockPos.ZERO.east()) : List.of(),
                hasFreezer ? List.of(BlockPos.ZERO.west()) : List.of());
    }
}
