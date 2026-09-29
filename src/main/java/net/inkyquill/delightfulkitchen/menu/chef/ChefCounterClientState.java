package net.inkyquill.delightfulkitchen.menu.chef;

import net.inkyquill.delightfulkitchen.kitchen.execution.KitchenPlanFingerprint;
import net.inkyquill.delightfulkitchen.network.payload.ClientboundChefCounterDetailsPayload;
import net.inkyquill.delightfulkitchen.network.payload.ClientboundChefCounterBookmarksPayload;
import net.inkyquill.delightfulkitchen.network.payload.ClientboundChefCounterQueueResultPayload;
import net.inkyquill.delightfulkitchen.network.payload.ClientboundChefCounterSyncPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ChefCounterClientState {
    private static final KitchenPlanFingerprint EMPTY_FINGERPRINT = new KitchenPlanFingerprint(0, 0, 0, 0, 0);

    private List<ChefRecipeSummary> summaries = List.of();
    private Optional<ChefPlanDetails> details = Optional.empty();
    private Optional<ChefQueueResultView> queueResult = Optional.empty();
    private List<ResourceLocation> pinnedRecipeIds = List.of();

    public List<ChefRecipeSummary> summaries() {
        return summaries;
    }

    public Optional<ChefPlanDetails> details() {
        return details;
    }

    public Optional<ChefQueueResultView> queueResult() {
        return queueResult;
    }

    public List<ResourceLocation> pinnedRecipeIds() {
        return pinnedRecipeIds;
    }

    public boolean isPinned(ResourceLocation recipeId) {
        return pinnedRecipeIds.contains(recipeId);
    }

    public ChefCounterViewState viewState() {
        KitchenPlanFingerprint fingerprint = details.map(ChefPlanDetails::fingerprint).orElse(EMPTY_FINGERPRINT);
        return new ChefCounterViewState(summaries, details, queueResult, ChefRecipeFilter.ALL, summaries.size(),
                count(PlanStateCounter.READY), count(PlanStateCounter.INTERMEDIATE), count(PlanStateCounter.BLOCKED),
                fingerprint);
    }

    public void handleSync(ClientboundChefCounterSyncPayload payload) {
        summaries = List.copyOf(Objects.requireNonNull(payload, "payload").summaries());
    }

    public void handleDetails(ClientboundChefCounterDetailsPayload payload) {
        details = Objects.requireNonNull(payload, "payload").details();
    }

    public void handleBookmarks(ClientboundChefCounterBookmarksPayload payload) {
        pinnedRecipeIds = List.copyOf(Objects.requireNonNull(payload, "payload").pinnedRecipeIds());
    }

    public void clearDetails() {
        details = Optional.empty();
    }

    public void handleQueueResult(ClientboundChefCounterQueueResultPayload payload) {
        queueResult = Optional.of(Objects.requireNonNull(payload, "payload").result());
    }

    public void handleState(ChefCounterViewState state) {
        Objects.requireNonNull(state, "state");
        summaries = state.summaries();
        details = state.details();
        queueResult = state.queueResult();
    }

    private int count(PlanStateCounter counter) {
        int count = 0;
        for (ChefRecipeSummary summary : summaries) {
            if (counter.matches(summary)) {
                count++;
            }
        }
        return count;
    }

    private enum PlanStateCounter {
        READY {
            @Override
            boolean matches(ChefRecipeSummary summary) {
                return summary.state() == net.inkyquill.delightfulkitchen.kitchen.recipe.PlanState.READY;
            }
        },
        INTERMEDIATE {
            @Override
            boolean matches(ChefRecipeSummary summary) {
                return summary.state()
                        == net.inkyquill.delightfulkitchen.kitchen.recipe.PlanState.CRAFTABLE_WITH_INTERMEDIATES;
            }
        },
        BLOCKED {
            @Override
            boolean matches(ChefRecipeSummary summary) {
                return summary.state() == net.inkyquill.delightfulkitchen.kitchen.recipe.PlanState.BLOCKED;
            }
        };

        abstract boolean matches(ChefRecipeSummary summary);
    }
}
