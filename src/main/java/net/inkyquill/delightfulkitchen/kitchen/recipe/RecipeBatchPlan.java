package net.inkyquill.delightfulkitchen.kitchen.recipe;

import java.util.Objects;

public record RecipeBatchPlan(
        RecipePlan plan,
        int requestedQuantity,
        int plannedQuantity,
        int expectedOutputCount
) {
    public RecipeBatchPlan {
        plan = Objects.requireNonNull(plan, "plan");
        if (requestedQuantity < RecipeBatchPlanner.MIN_QUANTITY
                || requestedQuantity > RecipeBatchPlanner.MAX_QUANTITY) {
            throw new IllegalArgumentException("requestedQuantity must be clamped before creating a batch plan");
        }
        if (plannedQuantity < 0 || plannedQuantity > requestedQuantity) {
            throw new IllegalArgumentException("plannedQuantity must be between 0 and requestedQuantity");
        }
        if (expectedOutputCount < 0) {
            throw new IllegalArgumentException("expectedOutputCount cannot be negative");
        }
    }
}
