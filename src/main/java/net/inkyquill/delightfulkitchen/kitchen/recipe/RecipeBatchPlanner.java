package net.inkyquill.delightfulkitchen.kitchen.recipe;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

public final class RecipeBatchPlanner {
    public static final int MIN_QUANTITY = 1;
    public static final int MAX_QUANTITY = 64;

    private final RecipePlanner planner;

    public RecipeBatchPlanner(RecipePlanner planner) {
        this.planner = Objects.requireNonNull(planner, "planner");
    }

    public RecipeBatchPlan plan(PlannedRecipe target, KitchenInventorySnapshot snapshot, int requestedQuantity) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(snapshot, "snapshot");

        int clampedQuantity = clampQuantity(requestedQuantity);
        int expectedOutputCount = expectedOutputCount(target, clampedQuantity);
        KitchenInventorySnapshot working = snapshot;
        List<PlannedRecipe> steps = new ArrayList<>();
        PlanState combinedState = PlanState.READY;
        int plannedQuantity = 0;

        for (int pass = 0; pass < clampedQuantity; pass++) {
            RecipePlanner.RecipePlanningResult result = planner.planWithResult(target, working);
            RecipePlan passPlan = result.plan();
            steps.addAll(passPlan.steps());
            if (passPlan.state() == PlanState.BLOCKED) {
                RecipePlan blockedPlan = RecipePlan.blocked(target.id(), outputFor(target, expectedOutputCount),
                        steps, passPlan.blockers());
                return new RecipeBatchPlan(blockedPlan, clampedQuantity, plannedQuantity, expectedOutputCount);
            }
            if (passPlan.state() == PlanState.CRAFTABLE_WITH_INTERMEDIATES) {
                combinedState = PlanState.CRAFTABLE_WITH_INTERMEDIATES;
            }
            working = result.snapshotBeforeOutput();
            plannedQuantity++;
        }

        RecipePlan batchPlan = new RecipePlan(target.id(), outputFor(target, expectedOutputCount), combinedState,
                steps, EnumSet.noneOf(PlanningBlocker.class));
        return new RecipeBatchPlan(batchPlan, clampedQuantity, plannedQuantity, expectedOutputCount);
    }

    public static int clampQuantity(int requestedQuantity) {
        return Math.clamp(requestedQuantity, MIN_QUANTITY, MAX_QUANTITY);
    }

    private static int expectedOutputCount(PlannedRecipe target, int quantity) {
        ItemStack output = target.primaryOutput();
        return Math.min(output.getCount() * quantity, output.getMaxStackSize());
    }

    private static ItemStack outputFor(PlannedRecipe target, int count) {
        ItemStack output = target.primaryOutput();
        output.setCount(count);
        return output;
    }
}
