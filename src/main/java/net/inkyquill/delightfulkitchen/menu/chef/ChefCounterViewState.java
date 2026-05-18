package net.inkyquill.delightfulkitchen.menu.chef;

import net.inkyquill.delightfulkitchen.kitchen.execution.KitchenPlanFingerprint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ChefCounterViewState(
        List<ChefRecipeSummary> summaries,
        Optional<ChefPlanDetails> details,
        Optional<ChefQueueResultView> queueResult,
        ChefRecipeFilter filter,
        int recipeCount,
        int readyCount,
        int intermediateCount,
        int blockedCount,
        KitchenPlanFingerprint fingerprint
) {
    public ChefCounterViewState {
        summaries = List.copyOf(Objects.requireNonNull(summaries, "summaries"));
        details = Objects.requireNonNull(details, "details");
        queueResult = Objects.requireNonNull(queueResult, "queueResult");
        filter = Objects.requireNonNull(filter, "filter");
        if (recipeCount < 0 || readyCount < 0 || intermediateCount < 0 || blockedCount < 0) {
            throw new IllegalArgumentException("counts cannot be negative");
        }
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
    }
}
