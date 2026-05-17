package net.inkyquill.delightfulkitchen.kitchen.execution;

import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryEndpoint;
import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryService;
import net.inkyquill.delightfulkitchen.kitchen.KitchenScanResult;
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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        KitchenPlanExecutionResult result = executor(inventory, station, scan)
                .confirm(plan, KitchenPlanFingerprint.of(scan, inventory.snapshot()));

        assertFalse(result.queued());
        assertTrue(result.blockers().contains(PlanningBlocker.MISSING_FUEL));
        assertEquals(3, source.getItem(0).getCount());
        assertEquals(2, source.getItem(1).getCount());
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
        return new KitchenScanResult(true, Set.of(BlockPos.ZERO.below()),
                List.of(BlockPos.ZERO, new BlockPos(1, 64, 0)), List.of());
    }

    private static RecipePlan plan(PlannedRecipe... steps) {
        PlannedRecipe target = steps[steps.length - 1];
        return new RecipePlan(target.id(), target.primaryOutput(), PlanState.READY, List.of(steps),
                EnumSet.noneOf(PlanningBlocker.class));
    }

    private static PlannedRecipe recipe(String path, KitchenStationType stationType,
                                        List<PlannedIngredient> ingredients, List<ItemStack> outputs) {
        return new PlannedRecipe(id(path), ingredients, outputs, stationType,
                Optional.empty(), Optional.empty(), 20, stationType != KitchenStationType.CRAFTING);
    }

    private static PlannedRecipe recipeWithFuel(String path, KitchenStationType stationType,
                                                List<PlannedIngredient> ingredients, List<ItemStack> outputs,
                                                PlannedIngredient fuel) {
        return new PlannedRecipe(id(path), ingredients, outputs, stationType,
                Optional.empty(), Optional.of(fuel), 20, true);
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
        private final List<KitchenStationJob> acceptedJobs = new ArrayList<>();

        private FakeStation(KitchenStationType type, int capacity, int initialQueued) {
            this.type = type;
            this.capacity = capacity;
            this.initialQueued = initialQueued;
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
}
