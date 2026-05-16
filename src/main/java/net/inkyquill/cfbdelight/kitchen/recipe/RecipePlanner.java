package net.inkyquill.cfbdelight.kitchen.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class RecipePlanner {
    private static final int MAX_DEPTH = 4;
    private static final Comparator<PlannedRecipe> INTERMEDIATE_ORDER = Comparator
            .comparingInt((PlannedRecipe recipe) -> recipe.ingredients().size())
            .thenComparing(recipe -> recipe.id().toString());

    private final RecipeIndex recipeIndex;
    private final Set<KitchenStationType> availableStations;

    public RecipePlanner(RecipeIndex recipeIndex, Set<KitchenStationType> availableStations) {
        this.recipeIndex = Objects.requireNonNull(recipeIndex, "recipeIndex");
        this.availableStations = Set.copyOf(Objects.requireNonNull(availableStations, "availableStations"));
    }

    public List<RecipePlan> planAllEdible(KitchenInventorySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return recipeIndex.edibleOutputs().stream()
                .map(recipe -> plan(recipe, snapshot))
                .sorted(Comparator
                        .comparing((RecipePlan plan) -> plan.state().ordinal())
                        .thenComparing(plan -> plan.output().getHoverName().getString())
                        .thenComparing(plan -> plan.targetRecipeId().toString()))
                .toList();
    }

    public RecipePlan plan(PlannedRecipe target, KitchenInventorySnapshot snapshot) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(snapshot, "snapshot");
        return plan(target, snapshot, 0, Set.of()).toRecipePlan();
    }

    private PlanAttempt plan(PlannedRecipe target, KitchenInventorySnapshot snapshot, int depth, Set<String> visiting) {
        EnumSet<PlanningBlocker> blockers = baseBlockers(target, snapshot);
        Optional<List<ItemStack>> directMatches = IngredientMatcher.findMatches(snapshot, requiredIngredients(target));
        if (directMatches.isPresent() && blockers.isEmpty()) {
            return PlanAttempt.ready(target, snapshot.withConsumed(directMatches.orElseThrow()));
        }

        String targetKey = target.id().toString();
        if (depth >= MAX_DEPTH || visiting.contains(targetKey)) {
            blockers.add(PlanningBlocker.MISSING_RAW_INGREDIENT);
            return PlanAttempt.blocked(target, snapshot, List.of(target), blockers);
        }

        Set<String> nextVisiting = withVisited(visiting, targetKey);
        KitchenInventorySnapshot working = snapshot;
        List<PlannedRecipe> steps = new ArrayList<>();
        boolean usedIntermediate = false;

        for (PlannedIngredient ingredient : target.ingredients()) {
            Optional<List<ItemStack>> ingredientMatches = IngredientMatcher.findMatches(working, List.of(ingredient));
            if (ingredientMatches.isPresent()) {
                working = working.withConsumed(ingredientMatches.orElseThrow());
                continue;
            }

            IntermediateAttempt intermediateAttempt = craftIntermediateFor(ingredient, working, depth, nextVisiting);
            if (intermediateAttempt.plan().isEmpty()) {
                blockers.addAll(intermediateAttempt.blockers());
                steps.addAll(intermediateAttempt.steps());
                continue;
            }

            PlanAttempt intermediatePlan = intermediateAttempt.plan().orElseThrow();
            steps.addAll(intermediatePlan.steps());
            working = intermediatePlan.snapshot();
            usedIntermediate = true;

            Optional<List<ItemStack>> craftedMatches = IngredientMatcher.findMatches(working, List.of(ingredient));
            if (craftedMatches.isPresent()) {
                working = working.withConsumed(craftedMatches.orElseThrow());
            } else {
                blockers.add(PlanningBlocker.MISSING_RAW_INGREDIENT);
            }
        }

        blockers.addAll(baseBlockers(target, working));
        if (!blockers.isEmpty()) {
            steps.add(target);
            return PlanAttempt.blocked(target, working, steps, blockers);
        }

        steps.add(target);
        working = working.withConsumed(matchingServingContainer(target, working).orElse(List.of()));
        return new PlanAttempt(
                target,
                working.withAdded(target.outputs()),
                usedIntermediate ? PlanState.CRAFTABLE_WITH_INTERMEDIATES : PlanState.READY,
                steps,
                EnumSet.noneOf(PlanningBlocker.class)
        );
    }

    private IntermediateAttempt craftIntermediateFor(PlannedIngredient ingredient, KitchenInventorySnapshot working,
                                                     int depth, Set<String> visiting) {
        EnumSet<PlanningBlocker> blockers = EnumSet.noneOf(PlanningBlocker.class);
        List<PlannedRecipe> attemptedSteps = new ArrayList<>();
        for (PlannedRecipe candidate : intermediateProducers(ingredient)) {
            PlanAttempt candidatePlan = plan(candidate, working, depth + 1, visiting);
            if (candidatePlan.state() != PlanState.BLOCKED) {
                return IntermediateAttempt.success(candidatePlan);
            }
            blockers.addAll(candidatePlan.blockers());
            attemptedSteps.addAll(candidatePlan.steps());
        }

        if (blockers.isEmpty()) {
            blockers.add(PlanningBlocker.MISSING_INTERMEDIATE_RECIPE);
        }
        return IntermediateAttempt.blocked(attemptedSteps, blockers);
    }

    private List<PlannedRecipe> intermediateProducers(PlannedIngredient ingredient) {
        Map<String, PlannedRecipe> producersById = new LinkedHashMap<>();
        for (ItemStack candidateStack : ingredient.displayStacks()) {
            for (PlannedRecipe producer : recipeIndex.recipesProducing(candidateStack)) {
                producersById.putIfAbsent(producer.id().toString(), producer);
            }
        }
        return producersById.values().stream()
                .filter(producer -> producesMatchingOutput(producer, ingredient.ingredient()))
                .sorted(INTERMEDIATE_ORDER)
                .toList();
    }

    private static boolean producesMatchingOutput(PlannedRecipe recipe, Ingredient ingredient) {
        for (ItemStack output : recipe.outputs()) {
            if (ingredient.test(output)) {
                return true;
            }
        }
        return false;
    }

    private EnumSet<PlanningBlocker> baseBlockers(PlannedRecipe target, KitchenInventorySnapshot snapshot) {
        EnumSet<PlanningBlocker> blockers = EnumSet.noneOf(PlanningBlocker.class);
        if (!availableStations.contains(target.stationType())) {
            blockers.add(PlanningBlocker.MISSING_STATION);
        }
        if (target.servingContainer().isPresent() && !hasServingContainer(target, snapshot)) {
            blockers.add(PlanningBlocker.MISSING_CONTAINER);
        }
        return blockers;
    }

    private static List<PlannedIngredient> requiredIngredients(PlannedRecipe target) {
        List<PlannedIngredient> ingredients = new ArrayList<>(target.ingredients());
        target.servingContainer().map(RecipePlanner::servingContainerIngredient).ifPresent(ingredients::add);
        return List.copyOf(ingredients);
    }

    private static boolean hasServingContainer(PlannedRecipe target, KitchenInventorySnapshot snapshot) {
        return matchingServingContainer(target, snapshot).isPresent();
    }

    private static Optional<List<ItemStack>> matchingServingContainer(PlannedRecipe target,
                                                                     KitchenInventorySnapshot snapshot) {
        return target.servingContainer()
                .map(RecipePlanner::servingContainerIngredient)
                .map(containerIngredient -> IngredientMatcher.findMatches(snapshot, List.of(containerIngredient)))
                .orElseGet(() -> Optional.of(List.of()));
    }

    private static PlannedIngredient servingContainerIngredient(ItemStack servingContainer) {
        return new PlannedIngredient(Ingredient.of(servingContainer.getItem()), servingContainer.getCount());
    }

    private static Set<String> withVisited(Set<String> visiting, String recipeId) {
        java.util.HashSet<String> nextVisiting = new java.util.HashSet<>(visiting);
        nextVisiting.add(recipeId);
        return Set.copyOf(nextVisiting);
    }

    private record PlanAttempt(
            PlannedRecipe target,
            KitchenInventorySnapshot snapshot,
            PlanState state,
            List<PlannedRecipe> steps,
            EnumSet<PlanningBlocker> blockers
    ) {
        private PlanAttempt {
            target = Objects.requireNonNull(target, "target");
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
            state = Objects.requireNonNull(state, "state");
            steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
            blockers = Objects.requireNonNull(blockers, "blockers").clone();
        }

        private static PlanAttempt ready(PlannedRecipe target, KitchenInventorySnapshot snapshot) {
            return new PlanAttempt(target, snapshot.withAdded(target.outputs()), PlanState.READY, List.of(target),
                    EnumSet.noneOf(PlanningBlocker.class));
        }

        private static PlanAttempt blocked(PlannedRecipe target, KitchenInventorySnapshot snapshot,
                                           List<PlannedRecipe> steps, EnumSet<PlanningBlocker> blockers) {
            return new PlanAttempt(target, snapshot, PlanState.BLOCKED, steps, blockers);
        }

        private RecipePlan toRecipePlan() {
            if (state == PlanState.BLOCKED) {
                return RecipePlan.blocked(target.id(), target.primaryOutput(), steps, blockers);
            }
            return new RecipePlan(target.id(), target.primaryOutput(), state, steps, blockers);
        }

        @Override
        public EnumSet<PlanningBlocker> blockers() {
            return blockers.clone();
        }
    }

    private record IntermediateAttempt(
            Optional<PlanAttempt> plan,
            List<PlannedRecipe> steps,
            EnumSet<PlanningBlocker> blockers
    ) {
        private IntermediateAttempt {
            plan = Objects.requireNonNull(plan, "plan");
            steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
            blockers = Objects.requireNonNull(blockers, "blockers").clone();
        }

        private static IntermediateAttempt success(PlanAttempt plan) {
            return new IntermediateAttempt(Optional.of(plan), List.of(), EnumSet.noneOf(PlanningBlocker.class));
        }

        private static IntermediateAttempt blocked(List<PlannedRecipe> steps, EnumSet<PlanningBlocker> blockers) {
            return new IntermediateAttempt(Optional.empty(), steps, blockers);
        }

        @Override
        public EnumSet<PlanningBlocker> blockers() {
            return blockers.clone();
        }
    }
}
