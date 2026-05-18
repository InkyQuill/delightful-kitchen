package net.inkyquill.delightfulkitchen.menu.chef;

import net.inkyquill.delightfulkitchen.kitchen.execution.KitchenPlanExecutionResult;
import net.inkyquill.delightfulkitchen.kitchen.execution.KitchenPlanFingerprint;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanState;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlannedIngredient;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlannedRecipe;
import net.inkyquill.delightfulkitchen.kitchen.recipe.RecipePlan;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ChefCounterViewModels {
    private static final Comparator<IndexedPlan> SUMMARY_ORDER = Comparator
            .comparing((IndexedPlan indexed) -> indexed.plan().output().getHoverName().getString())
            .thenComparing(indexed -> registryId(indexed.plan().output()))
            .thenComparing(indexed -> indexed.plan().targetRecipeId().toString());

    private ChefCounterViewModels() {
    }

    public static List<ChefRecipeSummary> summaries(List<RecipePlan> plans) {
        Objects.requireNonNull(plans, "plans");
        return indexedPlans(plans).stream()
                .sorted(SUMMARY_ORDER)
                .map(indexed -> summary(indexed.index(), indexed.plan()))
                .toList();
    }

    public static ChefRecipeSummary summary(int index, RecipePlan plan) {
        Objects.requireNonNull(plan, "plan");
        return new ChefRecipeSummary(index, plan.targetRecipeId(), plan.output(), plan.state(), plan.blockers(),
                plan.steps().size());
    }

    public static ChefPlanDetails details(int selectedIndex, RecipePlan plan, int quantity,
                                          KitchenPlanFingerprint fingerprint) {
        Objects.requireNonNull(plan, "plan");
        ItemStack output = plan.output();
        return new ChefPlanDetails(
                selectedIndex,
                plan.targetRecipeId(),
                output,
                plan.state(),
                quantity,
                output.getCount() * quantity,
                plan.steps().stream().map(ChefCounterViewModels::step).toList(),
                plan.blockers(),
                fingerprint
        );
    }

    public static ChefPlanStepView step(PlannedRecipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        return new ChefPlanStepView(
                recipe.id(),
                recipe.primaryOutput(),
                recipe.ingredients().stream()
                        .flatMap(ingredient -> ingredient.displayStacks().stream())
                        .toList(),
                recipe.outputs(),
                recipe.stationType(),
                recipe.servingContainer().orElse(ItemStack.EMPTY),
                recipe.fuel().map(ChefCounterViewModels::firstDisplayStack).orElse(ItemStack.EMPTY),
                recipe.utilityRequirement(),
                recipe.processingTime(),
                recipe.asynchronous()
        );
    }

    public static ChefQueueResultView queueResult(KitchenPlanExecutionResult result) {
        Objects.requireNonNull(result, "result");
        if (result.queued()) {
            return ChefQueueResultView.queued();
        }
        return ChefQueueResultView.blocked(result.blockers());
    }

    public static ChefCounterViewState state(List<RecipePlan> plans, Optional<ChefPlanDetails> details,
                                             Optional<ChefQueueResultView> queueResult, ChefRecipeFilter filter,
                                             KitchenPlanFingerprint fingerprint) {
        Objects.requireNonNull(plans, "plans");
        return new ChefCounterViewState(
                summaries(plans),
                details,
                queueResult,
                filter,
                plans.size(),
                countPlans(plans, PlanState.READY),
                countPlans(plans, PlanState.CRAFTABLE_WITH_INTERMEDIATES),
                countPlans(plans, PlanState.BLOCKED),
                fingerprint
        );
    }

    private static List<IndexedPlan> indexedPlans(List<RecipePlan> plans) {
        ArrayList<IndexedPlan> indexed = new ArrayList<>(plans.size());
        for (int index = 0; index < plans.size(); index++) {
            indexed.add(new IndexedPlan(index, Objects.requireNonNull(plans.get(index), "plans cannot contain null")));
        }
        return indexed;
    }

    private static int countPlans(List<RecipePlan> plans, PlanState state) {
        int count = 0;
        for (RecipePlan plan : plans) {
            if (plan.state() == state) {
                count++;
            }
        }
        return count;
    }

    private static ItemStack firstDisplayStack(PlannedIngredient ingredient) {
        List<ItemStack> displays = ingredient.displayStacks();
        if (displays.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return displays.getFirst();
    }

    private static String registryId(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private record IndexedPlan(int index, RecipePlan plan) {
    }
}
