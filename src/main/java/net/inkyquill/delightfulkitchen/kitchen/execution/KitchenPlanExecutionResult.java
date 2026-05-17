package net.inkyquill.delightfulkitchen.kitchen.execution;

import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;

import java.util.EnumSet;
import java.util.Objects;

public record KitchenPlanExecutionResult(boolean queued, EnumSet<PlanningBlocker> blockers) {
    public KitchenPlanExecutionResult {
        blockers = Objects.requireNonNull(blockers, "blockers").clone();
        if (queued && !blockers.isEmpty()) {
            throw new IllegalArgumentException("queued execution result cannot contain blockers");
        }
        if (!queued && blockers.isEmpty()) {
            throw new IllegalArgumentException("blocked execution result must contain at least one blocker");
        }
    }

    public static KitchenPlanExecutionResult queuedResult() {
        return new KitchenPlanExecutionResult(true, EnumSet.noneOf(PlanningBlocker.class));
    }

    public static KitchenPlanExecutionResult blocked(EnumSet<PlanningBlocker> blockers) {
        return new KitchenPlanExecutionResult(false, blockers);
    }

    public static KitchenPlanExecutionResult blocked(PlanningBlocker blocker) {
        return blocked(EnumSet.of(blocker));
    }

    @Override
    public EnumSet<PlanningBlocker> blockers() {
        return blockers.clone();
    }
}
