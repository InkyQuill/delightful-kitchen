package net.inkyquill.delightfulkitchen.kitchen.execution;

import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryEndpoint;
import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryService;
import net.inkyquill.delightfulkitchen.kitchen.KitchenScanResult;
import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenInventorySnapshot;
import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanState;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlannedIngredient;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlannedRecipe;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;
import net.inkyquill.delightfulkitchen.kitchen.recipe.RecipePlan;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStation;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStationAcceptResult;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStationEndpoint;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStationJob;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStationService;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStationStatus;
import net.inkyquill.delightfulkitchen.kitchen.utility.BorrowedContainer;
import net.inkyquill.delightfulkitchen.kitchen.utility.KitchenUtilityRequirement;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitchenPlanExecutorTest {
    @BeforeAll
    static void bootStrapMinecraft() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void doesNotExtractWhenStationQueueIsFull() {
        SimpleContainer source = new SimpleContainer(new ItemStack(Items.BEEF), ItemStack.EMPTY);
        FakeStation station = new FakeStation(KitchenStationType.GRILL, 1, 1);
        KitchenInventoryService inventory = inventory(source);
        KitchenScanResult scan = scan();
        RecipePlan plan = plan(recipe("grilled_beef", KitchenStationType.GRILL,
                List.of(ingredient(Items.BEEF, 1)), List.of(new ItemStack(Items.COOKED_BEEF))));

        KitchenPlanExecutionResult result = executor(inventory, station, scan)
                .confirm(plan, KitchenPlanFingerprint.of(scan, inventory.snapshot()));

        assertFalse(result.queued());
        assertTrue(result.blockers().contains(PlanningBlocker.STATION_QUEUE_FULL));
        assertEquals(1, source.getItem(0).getCount());
        assertEquals(0, station.acceptedJobCount());
    }

    @Test
    void returnsKitchenChangedWhenFingerprintDiffers() {
        SimpleContainer source = new SimpleContainer(new ItemStack(Items.BEEF), ItemStack.EMPTY);
        FakeStation station = new FakeStation(KitchenStationType.GRILL, 1, 0);
        KitchenInventoryService inventory = inventory(source);
        KitchenScanResult scan = scan();
        RecipePlan plan = plan(recipe("grilled_beef", KitchenStationType.GRILL,
                List.of(ingredient(Items.BEEF, 1)), List.of(new ItemStack(Items.COOKED_BEEF))));
        KitchenPlanFingerprint staleFingerprint = new KitchenPlanFingerprint(99, 99, 99, 99);

        KitchenPlanExecutionResult result = executor(inventory, station, scan).confirm(plan, staleFingerprint);

        assertFalse(result.queued());
        assertTrue(result.blockers().contains(PlanningBlocker.KITCHEN_CHANGED));
        assertEquals(1, source.getItem(0).getCount());
        assertEquals(0, station.acceptedJobCount());
    }

    @Test
    void returnsKitchenChangedWhenFingerprintItemIdentityDiffers() {
        SimpleContainer source = new SimpleContainer(new ItemStack(Items.BEEF), ItemStack.EMPTY);
        FakeStation station = new FakeStation(KitchenStationType.GRILL, 1, 0);
        KitchenInventoryService inventory = inventory(source);
        KitchenScanResult scan = scan();
        RecipePlan plan = plan(recipe("grilled_beef", KitchenStationType.GRILL,
                List.of(ingredient(Items.BEEF, 1)), List.of(new ItemStack(Items.COOKED_BEEF))));
        KitchenPlanFingerprint appleFingerprint = KitchenPlanFingerprint.of(scan,
                new KitchenInventorySnapshot(List.of(new ItemStack(Items.APPLE))));

        KitchenPlanExecutionResult result = executor(inventory, station, scan).confirm(plan, appleFingerprint);

        assertFalse(result.queued());
        assertTrue(result.blockers().contains(PlanningBlocker.KITCHEN_CHANGED));
        assertEquals(1, source.getItem(0).getCount());
        assertEquals(0, station.acceptedJobCount());
    }

    @Test
    void returnsKitchenChangedWhenFingerprintComponentsDiffer() {
        ItemStack namedBeef = new ItemStack(Items.BEEF);
        namedBeef.set(DataComponents.CUSTOM_NAME, Component.literal("Counter Beef"));
        SimpleContainer source = new SimpleContainer(namedBeef, ItemStack.EMPTY);
        FakeStation station = new FakeStation(KitchenStationType.GRILL, 1, 0);
        KitchenInventoryService inventory = inventory(source);
        KitchenScanResult scan = scan();
        RecipePlan plan = plan(recipe("grilled_beef", KitchenStationType.GRILL,
                List.of(ingredient(Items.BEEF, 1)), List.of(new ItemStack(Items.COOKED_BEEF))));
        KitchenPlanFingerprint unnamedBeefFingerprint = KitchenPlanFingerprint.of(scan,
                new KitchenInventorySnapshot(List.of(new ItemStack(Items.BEEF))));

        KitchenPlanExecutionResult result = executor(inventory, station, scan).confirm(plan, unnamedBeefFingerprint);

        assertFalse(result.queued());
        assertTrue(result.blockers().contains(PlanningBlocker.KITCHEN_CHANGED));
        assertEquals(1, source.getItem(0).getCount());
        assertEquals(0, station.acceptedJobCount());
    }

    @Test
    void doesNotExtractWhenCombinedStationOutputsDoNotFit() {
        SimpleContainer source = new SimpleContainer(
                new ItemStack(Items.BEEF),
                new ItemStack(Items.CHICKEN),
                ItemStack.EMPTY
        );
        FakeStation station = new FakeStation(KitchenStationType.GRILL, 2, 0);
        KitchenInventoryService inventory = inventory(source);
        KitchenScanResult scan = scan();
        RecipePlan plan = plan(
                recipe("grilled_beef", KitchenStationType.GRILL,
                        List.of(ingredient(Items.BEEF, 1)), List.of(new ItemStack(Items.COOKED_BEEF))),
                recipe("grilled_chicken", KitchenStationType.GRILL,
                        List.of(ingredient(Items.CHICKEN, 1)), List.of(new ItemStack(Items.COOKED_CHICKEN)))
        );

        KitchenPlanExecutionResult result = executor(inventory, station, scan)
                .confirm(plan, KitchenPlanFingerprint.of(scan, inventory.snapshot()));

        assertFalse(result.queued());
        assertTrue(result.blockers().contains(PlanningBlocker.OUTPUT_STORAGE_FULL));
        assertEquals(1, source.getItem(0).getCount());
        assertEquals(1, source.getItem(1).getCount());
        assertTrue(source.getItem(2).isEmpty());
        assertEquals(0, station.acceptedJobCount());
    }

    @Test
    void queuesStepsAfterExtractingInputs() {
        SimpleContainer source = new SimpleContainer(new ItemStack(Items.BEEF, 2), ItemStack.EMPTY);
        FakeStation station = new FakeStation(KitchenStationType.GRILL, 1, 0);
        KitchenInventoryService inventory = inventory(source);
        KitchenScanResult scan = scan();
        RecipePlan plan = plan(recipe("grilled_beef", KitchenStationType.GRILL,
                List.of(ingredient(Items.BEEF, 1)), List.of(new ItemStack(Items.COOKED_BEEF))));

        KitchenPlanExecutionResult result = executor(inventory, station, scan)
                .confirm(plan, KitchenPlanFingerprint.of(scan, inventory.snapshot()));

        assertTrue(result.queued());
        assertTrue(result.blockers().isEmpty());
        assertEquals(1, source.getItem(0).getCount());
        assertEquals(1, station.acceptedJobCount());
        assertEquals(1, station.acceptedJobs().getFirst().inputs().stream().mapToInt(ItemStack::getCount).sum());
    }

    @Test
    void insertsCraftingIntermediateBeforeExtractingStationInput() {
        SimpleContainer source = new SimpleContainer(new ItemStack(Items.WHEAT, 3), ItemStack.EMPTY, ItemStack.EMPTY);
        FakeStation station = new FakeStation(KitchenStationType.GRILL, 1, 0);
        KitchenInventoryService inventory = inventory(source);
        KitchenScanResult scan = scan();
        RecipePlan plan = plan(
                recipe("bread", KitchenStationType.CRAFTING,
                        List.of(ingredient(Items.WHEAT, 3)), List.of(new ItemStack(Items.BREAD))),
                recipe("grilled_bread", KitchenStationType.GRILL,
                        List.of(ingredient(Items.BREAD, 1)), List.of(new ItemStack(Items.COOKED_BEEF)))
        );

        KitchenPlanExecutionResult result = executor(inventory, station, scan)
                .confirm(plan, KitchenPlanFingerprint.of(scan, inventory.snapshot()));

        assertTrue(result.queued());
        assertEquals(0, source.getItem(0).getCount());
        assertTrue(source.getItem(1).isEmpty());
        assertEquals(1, station.acceptedJobCount());
        assertEquals(Items.BREAD, station.acceptedJobs().getFirst().inputs().getFirst().getItem());
    }

    @Test
    void rollsBackInstantCraftingOutputsWhenLaterExtractionFails() {
        SimpleContainer source = new SimpleContainer(
                new ItemStack(Items.WHEAT, 3),
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
        FakeStation station = new FakeStation(KitchenStationType.GRILL, 1, 0);
        KitchenInventoryService inventory = inventory(source);
        KitchenScanResult scan = scan();
        RecipePlan plan = plan(
                recipe("bread", KitchenStationType.CRAFTING,
                        List.of(ingredient(Items.WHEAT, 3)), List.of(new ItemStack(Items.BREAD))),
                recipeWithFuel("grilled_bread", KitchenStationType.GRILL,
                        List.of(ingredient(Items.BREAD, 1)), List.of(new ItemStack(Items.COOKED_BEEF)),
                        ingredient(Items.COAL, 1))
        );

        KitchenPlanExecutionResult result = executor(inventory, station, scan)
                .confirm(plan, KitchenPlanFingerprint.of(scan, inventory.snapshot()));

        assertFalse(result.queued());
        assertTrue(result.blockers().contains(PlanningBlocker.MISSING_FUEL));
        assertEquals(3, source.getItem(0).getCount());
        assertTrue(source.getItem(1).isEmpty());
        assertEquals(0, station.acceptedJobCount());
    }

    @Test
    void throwsWhenInstantCraftingRollbackWouldBeAmbiguous() {
        SimpleContainer source = new SimpleContainer(
                new ItemStack(Items.WHEAT, 3),
                new ItemStack(Items.BREAD, 2),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
        FakeStation station = new FakeStation(KitchenStationType.GRILL, 1, 0);
        KitchenInventoryService inventory = inventory(source);
        KitchenScanResult scan = scan();
        RecipePlan plan = plan(
                recipe("bread", KitchenStationType.CRAFTING,
                        List.of(ingredient(Items.WHEAT, 3)), List.of(new ItemStack(Items.BREAD))),
                recipeWithFuel("grilled_bread", KitchenStationType.GRILL,
                        List.of(ingredient(Items.BREAD, 1)), List.of(new ItemStack(Items.COOKED_BEEF)),
                        ingredient(Items.COAL, 1))
        );

        assertThrows(IllegalStateException.class, () -> executor(inventory, station, scan)
                .confirm(plan, KitchenPlanFingerprint.of(scan, inventory.snapshot())));

        assertEquals(0, source.getItem(0).getCount());
        assertEquals(3, source.getItem(1).getCount());
        assertEquals(0, station.acceptedJobCount());
    }

    @Test
    void throwsWhenStationAcceptFailsAfterAnotherJobWasAccepted() {
        SimpleContainer source = new SimpleContainer(
                new ItemStack(Items.BEEF),
                new ItemStack(Items.CHICKEN),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        );
        FakeStation station = new FakeStation(KitchenStationType.GRILL, 2, 0, 2);
        KitchenInventoryService inventory = inventory(source);
        KitchenScanResult scan = scan();
        RecipePlan plan = plan(
                recipe("grilled_beef", KitchenStationType.GRILL,
                        List.of(ingredient(Items.BEEF, 1)), List.of(new ItemStack(Items.COOKED_BEEF))),
                recipe("grilled_chicken", KitchenStationType.GRILL,
                        List.of(ingredient(Items.CHICKEN, 1)), List.of(new ItemStack(Items.COOKED_CHICKEN)))
        );

        assertThrows(IllegalStateException.class, () -> executor(inventory, station, scan)
                .confirm(plan, KitchenPlanFingerprint.of(scan, inventory.snapshot())));

        assertEquals(0, inventory.count(new ItemStack(Items.BEEF)));
        assertEquals(1, inventory.count(new ItemStack(Items.CHICKEN)));
        assertEquals(1, station.acceptedJobCount());
    }

    @Test
    void confirmsUtilityCraftingByBorrowingAndReleasingContainer() {
        SimpleContainer source = new SimpleContainer(
                new ItemStack(Items.WHEAT),
                new ItemStack(Items.BUCKET),
                ItemStack.EMPTY
        );
        FakeStation station = new FakeStation(KitchenStationType.GRILL, 1, 0);
        KitchenInventoryService inventory = inventory(source);
        KitchenScanResult scan = scan(true, false);
        RecipePlan plan = plan(recipeWithUtility("dough", KitchenUtilityRequirement.WATER,
                List.of(ingredient(Items.WHEAT, 1)), List.of(new ItemStack(Items.BREAD))));

        KitchenPlanExecutionResult result = executor(inventory, station, scan)
                .confirm(plan, KitchenPlanFingerprint.of(scan, inventory.snapshot()));

        assertTrue(result.queued());
        assertTrue(result.blockers().isEmpty());
        assertEquals(1, inventory.count(new ItemStack(Items.BREAD)));
        assertEquals(1, inventory.count(new ItemStack(Items.BUCKET)));
        assertEquals(0, inventory.count(new ItemStack(Items.WHEAT)));
    }

    @Test
    void utilityCraftingDoesNotCreateOutputWhenBorrowIsUnavailableAtConfirmation() {
        SimpleContainer source = new SimpleContainer(new ItemStack(Items.WHEAT), ItemStack.EMPTY);
        FakeStation station = new FakeStation(KitchenStationType.GRILL, 1, 0);
        KitchenInventoryService inventory = inventory(source);
        KitchenScanResult scan = scan(true, false);
        RecipePlan plan = plan(recipeWithUtility("dough", KitchenUtilityRequirement.WATER,
                List.of(ingredient(Items.WHEAT, 1)), List.of(new ItemStack(Items.BREAD))));

        KitchenPlanExecutionResult result = executor(inventory, station, scan)
                .confirm(plan, KitchenPlanFingerprint.of(scan, inventory.snapshot()));

        assertFalse(result.queued());
        assertTrue(result.blockers().contains(PlanningBlocker.MISSING_WATER_CONTAINER));
        assertEquals(1, inventory.count(new ItemStack(Items.WHEAT)));
        assertEquals(0, inventory.count(new ItemStack(Items.BREAD)));
    }

    @Test
    void rollsBackIngredientsWhenUtilityBorrowFailsAfterFingerprintValidation() {
        SimpleContainer source = new SimpleContainer(
                new ItemStack(Items.WHEAT),
                new ItemStack(Items.BUCKET),
                ItemStack.EMPTY
        );
        BorrowFailsInventoryService inventory = new BorrowFailsInventoryService(
                List.of(new KitchenInventoryEndpoint(BlockPos.ZERO, source))
        );
        FakeStation station = new FakeStation(KitchenStationType.GRILL, 1, 0);
        KitchenScanResult scan = scan(true, false);
        RecipePlan plan = plan(recipeWithUtility("dough", KitchenUtilityRequirement.WATER,
                List.of(ingredient(Items.WHEAT, 1)), List.of(new ItemStack(Items.BREAD))));

        KitchenPlanExecutionResult result = executor(inventory, station, scan)
                .confirm(plan, KitchenPlanFingerprint.of(scan, inventory.snapshot()));

        assertFalse(result.queued());
        assertTrue(result.blockers().contains(PlanningBlocker.MISSING_WATER_CONTAINER));
        assertEquals(1, inventory.count(new ItemStack(Items.WHEAT)));
        assertEquals(1, inventory.count(new ItemStack(Items.BUCKET)));
        assertEquals(0, inventory.count(new ItemStack(Items.BREAD)));
    }

    @Test
    void returnsBorrowedContainerWhenOutputInsertionFailsAfterBorrowing() {
        SimpleContainer source = new SimpleContainer(
                new ItemStack(Items.WHEAT),
                new ItemStack(Items.BUCKET),
                ItemStack.EMPTY
        );
        InsertFailsInventoryService inventory = new InsertFailsInventoryService(
                List.of(new KitchenInventoryEndpoint(BlockPos.ZERO, source))
        );
        FakeStation station = new FakeStation(KitchenStationType.GRILL, 1, 0);
        KitchenScanResult scan = scan(true, false);
        RecipePlan plan = plan(recipeWithUtility("dough", KitchenUtilityRequirement.WATER,
                List.of(ingredient(Items.WHEAT, 1)), List.of(new ItemStack(Items.BREAD))));

        KitchenPlanExecutionResult result = executor(inventory, station, scan)
                .confirm(plan, KitchenPlanFingerprint.of(scan, inventory.snapshot()));

        assertFalse(result.queued());
        assertTrue(result.blockers().contains(PlanningBlocker.OUTPUT_STORAGE_FULL));
        assertEquals(1, inventory.count(new ItemStack(Items.WHEAT)));
        assertEquals(1, inventory.count(new ItemStack(Items.BUCKET)));
        assertEquals(0, inventory.count(new ItemStack(Items.BREAD)));
    }

    @Test
    void preservesBorrowedContainerWhenOutputInsertionFailsAndReleaseNeedsRestoredInputs() {
        InputDependentContainer source = new InputDependentContainer(
                new ItemStack(Items.WHEAT),
                new ItemStack(Items.BUCKET)
        );
        InsertFailsInventoryService inventory = new InsertFailsInventoryService(
                List.of(new KitchenInventoryEndpoint(BlockPos.ZERO, source))
        );
        FakeStation station = new FakeStation(KitchenStationType.GRILL, 1, 0);
        KitchenScanResult scan = scan(true, false);
        RecipePlan plan = plan(recipeWithUtility("dough", KitchenUtilityRequirement.WATER,
                List.of(ingredient(Items.WHEAT, 1)), List.of(new ItemStack(Items.BREAD))));

        KitchenPlanExecutionResult result = executor(inventory, station, scan)
                .confirm(plan, KitchenPlanFingerprint.of(scan, inventory.snapshot()));

        assertFalse(result.queued());
        assertTrue(result.blockers().contains(PlanningBlocker.OUTPUT_STORAGE_FULL));
        assertEquals(1, inventory.count(new ItemStack(Items.WHEAT)));
        assertEquals(1, inventory.count(new ItemStack(Items.BUCKET)));
        assertEquals(0, inventory.count(new ItemStack(Items.BREAD)));
    }

    @Test
    void rollsBackInstantUtilityCraftingWhenBorrowedContainerCannotReleaseAfterOutputInsertion() {
        FilteredSimpleContainer source = new FilteredSimpleContainer(
                new ItemStack(Items.WHEAT),
                new ItemStack(Items.BUCKET)
        );
        ValidationTrustingInventoryService inventory = new ValidationTrustingInventoryService(
                List.of(new KitchenInventoryEndpoint(BlockPos.ZERO, source))
        );
        FakeStation station = new FakeStation(KitchenStationType.GRILL, 1, 0);
        KitchenScanResult scan = scan(true, false);
        RecipePlan plan = plan(recipeWithUtility("dough", KitchenUtilityRequirement.WATER,
                List.of(ingredient(Items.WHEAT, 1)), List.of(new ItemStack(Items.BREAD))));

        KitchenPlanExecutionResult result = executor(inventory, station, scan)
                .confirm(plan, KitchenPlanFingerprint.of(scan, inventory.snapshot()));

        assertFalse(result.queued());
        assertTrue(result.blockers().contains(PlanningBlocker.OUTPUT_STORAGE_FULL));
        assertEquals(1, inventory.count(new ItemStack(Items.WHEAT)));
        assertEquals(1, inventory.count(new ItemStack(Items.BUCKET)));
        assertEquals(0, inventory.count(new ItemStack(Items.BREAD)));
    }

    @Test
    void preservesBorrowedContainerWhenReleaseRemainsBlockedUntilInputsAreRestored() {
        InputDependentContainer source = new InputDependentContainer(
                new ItemStack(Items.WHEAT),
                new ItemStack(Items.BUCKET)
        );
        ValidationTrustingInventoryService inventory = new ValidationTrustingInventoryService(
                List.of(new KitchenInventoryEndpoint(BlockPos.ZERO, source))
        );
        FakeStation station = new FakeStation(KitchenStationType.GRILL, 1, 0);
        KitchenScanResult scan = scan(true, false);
        RecipePlan plan = plan(recipeWithUtility("dough", KitchenUtilityRequirement.WATER,
                List.of(ingredient(Items.WHEAT, 1)), List.of(new ItemStack(Items.BREAD))));

        KitchenPlanExecutionResult result = executor(inventory, station, scan)
                .confirm(plan, KitchenPlanFingerprint.of(scan, inventory.snapshot()));

        assertFalse(result.queued());
        assertTrue(result.blockers().contains(PlanningBlocker.OUTPUT_STORAGE_FULL));
        assertEquals(1, inventory.count(new ItemStack(Items.WHEAT)));
        assertEquals(1, inventory.count(new ItemStack(Items.BUCKET)));
        assertEquals(0, inventory.count(new ItemStack(Items.BREAD)));
    }

    @Test
    void blocksStationUtilityStepsWithoutExtractingOrQueuing() {
        SimpleContainer source = new SimpleContainer(
                new ItemStack(Items.WHEAT),
                new ItemStack(Items.BUCKET),
                ItemStack.EMPTY
        );
        FakeStation station = new FakeStation(KitchenStationType.GRILL, 1, 0);
        KitchenInventoryService inventory = inventory(source);
        KitchenScanResult scan = scan(true, false);
        RecipePlan plan = plan(new PlannedRecipe(id("grilled_dough"),
                List.of(ingredient(Items.WHEAT, 1)), List.of(new ItemStack(Items.BREAD)),
                KitchenStationType.GRILL, Optional.empty(), Optional.empty(),
                KitchenUtilityRequirement.WATER, 20, true));

        KitchenPlanExecutionResult result = executor(inventory, station, scan)
                .confirm(plan, KitchenPlanFingerprint.of(scan, inventory.snapshot()));

        assertFalse(result.queued());
        assertTrue(result.blockers().contains(PlanningBlocker.MISSING_RAW_INGREDIENT));
        assertEquals(1, inventory.count(new ItemStack(Items.WHEAT)));
        assertEquals(1, inventory.count(new ItemStack(Items.BUCKET)));
        assertEquals(0, station.acceptedJobCount());
    }

    private static KitchenPlanExecutor executor(KitchenInventoryService inventory, FakeStation station,
                                                KitchenScanResult scan) {
        KitchenStationService stations = new KitchenStationService(List.of(
                new KitchenStationEndpoint(new BlockPos(1, 64, 0), station)
        ));
        return new KitchenPlanExecutor(inventory, stations, scan);
    }

    private static KitchenInventoryService inventory(SimpleContainer source) {
        return new KitchenInventoryService(List.of(new KitchenInventoryEndpoint(BlockPos.ZERO, source)));
    }

    private static KitchenScanResult scan() {
        return scan(false, false);
    }

    private static KitchenScanResult scan(boolean hasWaterProvider, boolean hasFreezer) {
        return new KitchenScanResult(true, Set.of(BlockPos.ZERO.below()),
                List.of(BlockPos.ZERO, new BlockPos(1, 64, 0)), List.of(),
                hasWaterProvider ? List.of(BlockPos.ZERO.east()) : List.of(),
                hasFreezer ? List.of(BlockPos.ZERO.west()) : List.of());
    }

    private static RecipePlan plan(PlannedRecipe... steps) {
        PlannedRecipe target = steps[steps.length - 1];
        return new RecipePlan(target.id(), target.primaryOutput(), PlanState.READY, List.of(steps),
                EnumSet.noneOf(PlanningBlocker.class));
    }

    private static PlannedRecipe recipe(String path, KitchenStationType stationType,
                                        List<PlannedIngredient> ingredients, List<ItemStack> outputs) {
        return new PlannedRecipe(id(path), ingredients, outputs, stationType,
                Optional.empty(), Optional.empty(), KitchenUtilityRequirement.NONE, 20,
                stationType != KitchenStationType.CRAFTING);
    }

    private static PlannedRecipe recipeWithFuel(String path, KitchenStationType stationType,
                                                List<PlannedIngredient> ingredients, List<ItemStack> outputs,
                                                PlannedIngredient fuel) {
        return new PlannedRecipe(id(path), ingredients, outputs, stationType,
                Optional.empty(), Optional.of(fuel), KitchenUtilityRequirement.NONE, 20, true);
    }

    private static PlannedRecipe recipeWithUtility(String path, KitchenUtilityRequirement requirement,
                                                   List<PlannedIngredient> ingredients, List<ItemStack> outputs) {
        return new PlannedRecipe(id(path), ingredients, outputs, KitchenStationType.CRAFTING,
                Optional.empty(), Optional.empty(), requirement, 20, false);
    }

    private static PlannedIngredient ingredient(ItemLike item, int count) {
        return new PlannedIngredient(Ingredient.of(item), count);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("delightful_kitchen", path);
    }

    private static final class FakeStation implements KitchenStation {
        private final KitchenStationType type;
        private final int capacity;
        private final int initialQueued;
        private final int failingAcceptCall;
        private final List<KitchenStationJob> acceptedJobs = new ArrayList<>();
        private int acceptCalls;

        private FakeStation(KitchenStationType type, int capacity, int initialQueued) {
            this(type, capacity, initialQueued, 0);
        }

        private FakeStation(KitchenStationType type, int capacity, int initialQueued, int failingAcceptCall) {
            this.type = type;
            this.capacity = capacity;
            this.initialQueued = initialQueued;
            this.failingAcceptCall = failingAcceptCall;
        }

        @Override
        public KitchenStationType stationType() {
            return type;
        }

        @Override
        public int queueCapacity() {
            return capacity;
        }

        @Override
        public int queuedJobCount() {
            return initialQueued + acceptedJobs.size();
        }

        @Override
        public KitchenStationStatus stationStatus() {
            return hasQueueSpace() ? KitchenStationStatus.IDLE : KitchenStationStatus.QUEUE_FULL;
        }

        @Override
        public KitchenStationAcceptResult canAccept(KitchenStationJob job) {
            if (job.stationType() != type) {
                return KitchenStationAcceptResult.blocked(PlanningBlocker.MISSING_STATION);
            }
            return hasQueueSpace()
                    ? KitchenStationAcceptResult.acceptedResult()
                    : KitchenStationAcceptResult.blocked(PlanningBlocker.STATION_QUEUE_FULL);
        }

        @Override
        public KitchenStationAcceptResult accept(KitchenStationJob job) {
            acceptCalls++;
            if (acceptCalls == failingAcceptCall) {
                return KitchenStationAcceptResult.blocked(PlanningBlocker.STATION_QUEUE_FULL);
            }
            KitchenStationAcceptResult result = canAccept(job);
            if (result.accepted()) {
                acceptedJobs.add(job);
            }
            return result;
        }

        @Override
        public BlockPos stationPos() {
            return new BlockPos(1, 64, 0);
        }

        private int acceptedJobCount() {
            return acceptedJobs.size();
        }

        private List<KitchenStationJob> acceptedJobs() {
            return List.copyOf(acceptedJobs);
        }
    }

    private static final class BorrowFailsInventoryService extends KitchenInventoryService {
        private BorrowFailsInventoryService(List<KitchenInventoryEndpoint> endpoints) {
            super(endpoints);
        }

        @Override
        public boolean hasReservable(Predicate<ItemStack> matcher) {
            return true;
        }

        @Override
        public Optional<BorrowedContainer> borrowOne(Predicate<ItemStack> matcher) {
            return Optional.empty();
        }
    }

    private static final class InsertFailsInventoryService extends KitchenInventoryService {
        private boolean failedOutputInsertion;

        private InsertFailsInventoryService(List<KitchenInventoryEndpoint> endpoints) {
            super(endpoints);
        }

        @Override
        public boolean canInsertAll(List<ItemStack> stacks) {
            return true;
        }

        @Override
        public boolean insertAll(List<ItemStack> stacks) {
            if (!failedOutputInsertion && stacks.stream().anyMatch(stack -> stack.is(Items.BREAD))) {
                failedOutputInsertion = true;
                return false;
            }
            return super.insertAll(stacks);
        }
    }

    private static final class ValidationTrustingInventoryService extends KitchenInventoryService {
        private ValidationTrustingInventoryService(List<KitchenInventoryEndpoint> endpoints) {
            super(endpoints);
        }

        @Override
        public boolean canInsertAll(List<ItemStack> stacks) {
            return true;
        }
    }

    private static final class FilteredSimpleContainer extends SimpleContainer {
        private FilteredSimpleContainer(ItemStack... items) {
            super(items);
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            if (slot == 0) {
                return stack.is(Items.WHEAT);
            }
            return stack.is(Items.BUCKET) || stack.is(Items.BREAD);
        }
    }

    private static final class InputDependentContainer extends SimpleContainer {
        private InputDependentContainer(ItemStack... items) {
            super(items);
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            if (slot == 0) {
                return stack.is(Items.WHEAT);
            }
            if (stack.is(Items.BREAD)) {
                return true;
            }
            return stack.is(Items.BUCKET) && getItem(0).is(Items.WHEAT);
        }
    }
}
