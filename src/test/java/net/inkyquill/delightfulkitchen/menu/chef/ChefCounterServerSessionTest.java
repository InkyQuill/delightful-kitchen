package net.inkyquill.delightfulkitchen.menu.chef;

import net.inkyquill.delightfulkitchen.kitchen.KitchenScanResult;
import net.inkyquill.delightfulkitchen.kitchen.execution.KitchenPlanExecutionResult;
import net.inkyquill.delightfulkitchen.kitchen.execution.KitchenPlanFingerprint;
import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenInventorySnapshot;
import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanState;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlannedIngredient;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlannedRecipe;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;
import net.inkyquill.delightfulkitchen.kitchen.recipe.RecipeBatchPlan;
import net.inkyquill.delightfulkitchen.kitchen.recipe.RecipeBatchPlanner;
import net.inkyquill.delightfulkitchen.kitchen.recipe.RecipePlan;
import net.inkyquill.delightfulkitchen.kitchen.utility.KitchenUtilityRequirement;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChefCounterServerSessionTest {
    @BeforeAll
    static void bootStrapMinecraft() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void createsEmptyRecipeStateWhenNoKitchenFloorExists() {
        FakeBackend backend = new FakeBackend(ChefCounterServerSession.SessionData.empty(noFloor()));

        ChefCounterServerSession session = new ChefCounterServerSession(backend);

        assertTrue(session.summaries().isEmpty());
        assertEquals(0, session.recipeCount());
        assertFalse(session.details(0, 1).isPresent());
    }

    @Test
    void selectsDetailsBySortedSummaryIndex() {
        PlannedRecipe bread = recipe("bread", named(new ItemStack(Items.BREAD), "Meal B"));
        PlannedRecipe apple = recipe("apple", named(new ItemStack(Items.APPLE), "Meal A"));
        FakeBackend backend = new FakeBackend(data(List.of(bread, apple), List.of(
                plan(bread, PlanState.READY),
                plan(apple, PlanState.READY)
        ), List.of(new ItemStack(Items.WHEAT, 2))));

        ChefCounterServerSession session = new ChefCounterServerSession(backend);

        assertEquals(List.of(id("apple"), id("bread")), session.summaries().stream()
                .map(ChefRecipeSummary::recipeId)
                .toList());
        assertEquals(id("apple"), session.details(0, 1).orElseThrow().targetRecipeId());
    }

    @Test
    void clearsDetailsWhenSelectedRecipeDisappearsAfterRefresh() {
        PlannedRecipe bread = recipe("bread", named(new ItemStack(Items.BREAD), "Meal B"));
        PlannedRecipe apple = recipe("apple", named(new ItemStack(Items.APPLE), "Meal A"));
        ChefCounterServerSession.SessionData initial = data(List.of(bread, apple), List.of(
                plan(bread, PlanState.READY),
                plan(apple, PlanState.READY)
        ), List.of(new ItemStack(Items.WHEAT, 2)));
        ChefCounterServerSession.SessionData refreshed = data(List.of(bread), List.of(
                plan(bread, PlanState.READY)
        ), List.of(new ItemStack(Items.WHEAT, 2)));
        FakeBackend backend = new FakeBackend(initial, refreshed);

        ChefCounterServerSession session = new ChefCounterServerSession(backend);

        assertEquals(id("apple"), session.summaries().getFirst().recipeId());
        assertFalse(session.details(0, 1).isPresent());
        assertEquals(List.of(id("bread")), session.summaries().stream().map(ChefRecipeSummary::recipeId).toList());
    }

    @Test
    void invalidSelectedIndexReturnsNoDetailsAndDoesNotThrow() {
        PlannedRecipe bread = recipe("bread", new ItemStack(Items.BREAD));
        ChefCounterServerSession session = new ChefCounterServerSession(
                new FakeBackend(data(List.of(bread), List.of(plan(bread, PlanState.READY)),
                        List.of(new ItemStack(Items.WHEAT)))));

        assertFalse(session.details(-1, 1).isPresent());
        assertFalse(session.details(1, 1).isPresent());
    }

    @Test
    void confirmationWithStaleFingerprintReturnsKitchenChangedBlocker() {
        PlannedRecipe bread = recipe("bread", new ItemStack(Items.BREAD));
        FakeBackend backend = new FakeBackend(data(List.of(bread), List.of(plan(bread, PlanState.READY)),
                List.of(new ItemStack(Items.WHEAT))));
        ChefCounterServerSession session = new ChefCounterServerSession(backend);

        ChefCounterServerSession.Confirmation confirmation = session.confirm(0, 1,
                new KitchenPlanFingerprint(99, 99, 99, 99, 99));

        assertFalse(confirmation.result().queued());
        assertEquals(EnumSet.of(PlanningBlocker.KITCHEN_CHANGED), confirmation.result().blockers());
        assertEquals(0, backend.confirmCallCount());
        assertEquals(2, backend.buildCallCount());
    }

    @Test
    void blockedBatchPlanDoesNotCallBackendConfirm() {
        PlannedRecipe bread = recipe("bread", new ItemStack(Items.BREAD));
        FakeBackend backend = new FakeBackend(data(List.of(bread), List.of(plan(bread, PlanState.BLOCKED)),
                List.of()));
        ChefCounterServerSession session = new ChefCounterServerSession(backend);

        ChefCounterServerSession.Confirmation confirmation = session.confirm(0, 1, session.fingerprint());

        assertFalse(confirmation.result().queued());
        assertEquals(EnumSet.of(PlanningBlocker.MISSING_RAW_INGREDIENT), confirmation.result().blockers());
        assertEquals(0, backend.confirmCallCount());
    }

    @Test
    void confirmationRebuildsBeforeReturningCachedBlockedPlan() {
        PlannedRecipe bread = recipe("bread", new ItemStack(Items.BREAD));
        ChefCounterServerSession.SessionData blocked = data(List.of(bread), List.of(plan(bread, PlanState.BLOCKED)),
                List.of());
        FakeBackend backend = new FakeBackend(blocked,
                data(List.of(bread), List.of(plan(bread, PlanState.READY)), List.of(new ItemStack(Items.WHEAT))));
        ChefCounterServerSession session = new ChefCounterServerSession(backend);
        KitchenPlanFingerprint staleFingerprint = session.fingerprint();

        ChefCounterServerSession.Confirmation confirmation = session.confirm(0, 1, staleFingerprint);

        assertFalse(confirmation.result().queued());
        assertEquals(EnumSet.of(PlanningBlocker.KITCHEN_CHANGED), confirmation.result().blockers());
        assertEquals(0, backend.confirmCallCount());
        assertEquals(PlanState.READY, session.details(0, 1).orElseThrow().state());
    }

    @Test
    void successfulConfirmationRefreshesSessionState() {
        PlannedRecipe bread = recipe("bread", new ItemStack(Items.BREAD));
        PlannedRecipe apple = recipe("apple", new ItemStack(Items.APPLE));
        ChefCounterServerSession.SessionData initial = data(List.of(bread), List.of(plan(bread, PlanState.READY)),
                List.of(new ItemStack(Items.WHEAT)));
        FakeBackend backend = new FakeBackend(
                initial,
                initial,
                data(List.of(apple), List.of(plan(apple, PlanState.READY)), List.of(new ItemStack(Items.APPLE)))
        );
        ChefCounterServerSession session = new ChefCounterServerSession(backend);

        ChefCounterServerSession.Confirmation confirmation = session.confirm(0, 1, session.fingerprint());

        assertTrue(confirmation.result().queued());
        assertEquals(1, backend.confirmCallCount());
        assertEquals(3, backend.buildCallCount());
        assertEquals(List.of(id("apple")), session.summaries().stream().map(ChefRecipeSummary::recipeId).toList());
    }

    @Test
    void ordinaryConfirmationFailureClearsDetailsWhenSelectedRecipeDisappears() {
        PlannedRecipe bread = recipe("bread", new ItemStack(Items.BREAD));
        PlannedRecipe apple = recipe("apple", new ItemStack(Items.APPLE));
        ChefCounterServerSession.SessionData initial = data(List.of(bread), List.of(plan(bread, PlanState.READY)),
                List.of(new ItemStack(Items.WHEAT)));
        FakeBackend backend = new FakeBackend(
                KitchenPlanExecutionResult.blocked(PlanningBlocker.STATION_QUEUE_FULL),
                initial,
                initial,
                data(List.of(apple), List.of(plan(apple, PlanState.READY)), List.of(new ItemStack(Items.APPLE)))
        );
        ChefCounterServerSession session = new ChefCounterServerSession(backend);

        ChefCounterServerSession.Confirmation confirmation = session.confirm(0, 1, session.fingerprint());

        assertFalse(confirmation.result().queued());
        assertEquals(EnumSet.of(PlanningBlocker.STATION_QUEUE_FULL), confirmation.result().blockers());
        assertEquals(3, backend.buildCallCount());
        assertFalse(confirmation.details().isPresent());
        assertEquals(List.of(id("apple")), session.summaries().stream().map(ChefRecipeSummary::recipeId).toList());
    }

    private static ChefCounterServerSession.SessionData data(List<PlannedRecipe> recipes, List<RecipePlan> plans,
                                                            List<ItemStack> inventory) {
        KitchenInventorySnapshot snapshot = new KitchenInventorySnapshot(inventory);
        return new ChefCounterServerSession.SessionData(hasFloor(), 1, snapshot, (target, currentSnapshot, quantity) -> {
            int clampedQuantity = RecipeBatchPlanner.clampQuantity(quantity);
            RecipePlan plan = plans.stream()
                    .filter(candidate -> candidate.targetRecipeId().equals(target.id()))
                    .findFirst()
                    .orElseThrow();
            ItemStack output = plan.output();
            output.setCount(Math.min(output.getCount() * clampedQuantity, output.getMaxStackSize()));
            RecipePlan batchPlan = new RecipePlan(plan.targetRecipeId(), output, plan.state(), plan.steps(),
                    plan.blockers());
            return new RecipeBatchPlan(batchPlan, clampedQuantity, plan.state() == PlanState.BLOCKED ? 0 : clampedQuantity,
                    output.getCount());
        }, recipes, plans);
    }

    private static KitchenScanResult noFloor() {
        return new KitchenScanResult(false, Set.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static KitchenScanResult hasFloor() {
        BlockPos pos = new BlockPos(0, 64, 0);
        return new KitchenScanResult(true, Set.of(pos), List.of(pos), List.of(pos), List.of(), List.of());
    }

    private static RecipePlan plan(PlannedRecipe recipe, PlanState state) {
        return new RecipePlan(recipe.id(), recipe.primaryOutput(), state, List.of(recipe),
                state == PlanState.BLOCKED
                        ? EnumSet.of(PlanningBlocker.MISSING_RAW_INGREDIENT)
                        : EnumSet.noneOf(PlanningBlocker.class));
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

    private static final class FakeBackend implements ChefCounterServerSession.Backend {
        private final List<ChefCounterServerSession.SessionData> builds;
        private final KitchenPlanExecutionResult confirmResult;
        private int buildCallCount;
        private int confirmCallCount;

        private FakeBackend(ChefCounterServerSession.SessionData first,
                            ChefCounterServerSession.SessionData... nextBuilds) {
            this(KitchenPlanExecutionResult.queuedResult(), first, nextBuilds);
        }

        private FakeBackend(KitchenPlanExecutionResult confirmResult, ChefCounterServerSession.SessionData first,
                            ChefCounterServerSession.SessionData... nextBuilds) {
            this.confirmResult = confirmResult;
            this.builds = new java.util.ArrayList<>();
            this.builds.add(first);
            this.builds.addAll(List.of(nextBuilds));
        }

        @Override
        public ChefCounterServerSession.SessionData build() {
            int index = Math.min(buildCallCount, builds.size() - 1);
            buildCallCount++;
            return builds.get(index);
        }

        @Override
        public KitchenPlanExecutionResult confirm(RecipePlan plan, KitchenPlanFingerprint fingerprint) {
            confirmCallCount++;
            return confirmResult;
        }

        private int buildCallCount() {
            return buildCallCount;
        }

        private int confirmCallCount() {
            return confirmCallCount;
        }
    }
}
