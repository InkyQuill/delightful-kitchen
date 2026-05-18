package net.inkyquill.delightfulkitchen.kitchen.recipe;

import net.inkyquill.delightfulkitchen.kitchen.utility.KitchenUtilityRequirement;
import net.inkyquill.delightfulkitchen.kitchen.utility.KitchenUtilityService;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    private final Optional<KitchenUtilityService> utilityService;

    public RecipePlanner(RecipeIndex recipeIndex, Set<KitchenStationType> availableStations) {
        this(recipeIndex, availableStations, Optional.empty());
    }

    public RecipePlanner(RecipeIndex recipeIndex, Set<KitchenStationType> availableStations,
                         KitchenUtilityService utilityService) {
        this(recipeIndex, availableStations, Optional.of(Objects.requireNonNull(utilityService, "utilityService")));
    }

    private RecipePlanner(RecipeIndex recipeIndex, Set<KitchenStationType> availableStations,
                          Optional<KitchenUtilityService> utilityService) {
        this.recipeIndex = Objects.requireNonNull(recipeIndex, "recipeIndex");
        this.availableStations = Set.copyOf(Objects.requireNonNull(availableStations, "availableStations"));
        this.utilityService = Objects.requireNonNull(utilityService, "utilityService");
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
        return planWithResult(target, snapshot).plan();
    }

    public RecipePlanningResult planWithResult(PlannedRecipe target, KitchenInventorySnapshot snapshot) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(snapshot, "snapshot");
        PlanAttempt attempt = plan(target, snapshot, 0, Set.of());
        return new RecipePlanningResult(attempt.toRecipePlan(), attempt.snapshot(), attempt.snapshotBeforeOutput());
    }

    private PlanAttempt plan(PlannedRecipe target, KitchenInventorySnapshot snapshot, int depth, Set<String> visiting) {
        EnumSet<PlanningBlocker> blockers = baseBlockers(target, snapshot);
        Optional<List<ItemStack>> directMatches = IngredientMatcher.findMatches(snapshot, requiredIngredients(target));
        if (directMatches.isPresent() && blockers.isEmpty()) {
            return PlanAttempt.ready(target, snapshot.withConsumed(directMatches.orElseThrow()));
        }
        Optional<PlanAttempt> utilityAttempt = planWithUtility(target, snapshot, blockers, List.of(), false);
        if (utilityAttempt.isPresent()) {
            return utilityAttempt.orElseThrow();
        }

        String targetKey = target.id().toString();
        if (depth >= MAX_DEPTH || visiting.contains(targetKey)) {
            blockers.add(PlanningBlocker.MISSING_RAW_INGREDIENT);
            return PlanAttempt.blocked(target, snapshot, List.of(target), blockers);
        }

        Set<String> nextVisiting = withVisited(visiting, targetKey);
        IntermediateAttempt intermediateAttempt = planIngredients(target, snapshot, depth, nextVisiting,
                List.of(), false);
        if (intermediateAttempt.plan().isPresent() && blockers.isEmpty()) {
            return intermediateAttempt.plan().orElseThrow();
        }

        blockers.addAll(intermediateAttempt.blockers());
        List<PlannedRecipe> attemptedSteps = intermediateAttempt.plan()
                .map(PlanAttempt::steps)
                .orElseGet(intermediateAttempt::steps);
        List<PlannedRecipe> steps = attemptedSteps.isEmpty()
                ? List.of(target)
                : attemptedSteps;
        return PlanAttempt.blocked(target, snapshot, steps, blockers);
    }

    private IntermediateAttempt planIngredients(PlannedRecipe target, KitchenInventorySnapshot working, int depth,
                                                Set<String> visiting, List<PlannedRecipe> steps,
                                                boolean usedIntermediate) {
        EnumSet<PlanningBlocker> blockers = baseBlockers(target, working);
        Optional<List<ItemStack>> targetMatches = IngredientMatcher.findMatches(working, requiredIngredients(target));
        if (targetMatches.isPresent()) {
            if (blockers.isEmpty()) {
                KitchenInventorySnapshot completed = working.withConsumed(targetMatches.orElseThrow());
                List<PlannedRecipe> completedSteps = appendStep(steps, target);
                return IntermediateAttempt.success(new PlanAttempt(
                        target,
                        completed.withAdded(target.outputs()),
                        completed,
                        usedIntermediate ? PlanState.CRAFTABLE_WITH_INTERMEDIATES : PlanState.READY,
                        completedSteps,
                        EnumSet.noneOf(PlanningBlocker.class)
                ));
            }
            return IntermediateAttempt.blocked(appendStep(steps, target), blockers);
        }
        Optional<PlanAttempt> utilityAttempt = planWithUtility(target, working, blockers, steps, usedIntermediate);
        if (utilityAttempt.isPresent()) {
            PlanAttempt attempt = utilityAttempt.orElseThrow();
            if (attempt.state() == PlanState.BLOCKED) {
                return IntermediateAttempt.blocked(attempt.steps(), attempt.blockers());
            }
            return IntermediateAttempt.success(attempt);
        }

        Optional<List<ItemStack>> ingredientMatches = IngredientMatcher.findMatches(working, target.ingredients());
        if (ingredientMatches.isPresent() && !blockers.isEmpty()) {
            return IntermediateAttempt.blocked(appendStep(steps, target), blockers);
        }

        if (depth >= MAX_DEPTH) {
            blockers.add(PlanningBlocker.MISSING_RAW_INGREDIENT);
            return IntermediateAttempt.blocked(appendStep(steps, target), blockers);
        }

        IntermediateAttempt intermediateAttempt = craftIntermediateForAny(target, working, depth, visiting, steps);
        if (intermediateAttempt.plan().isPresent()) {
            return intermediateAttempt;
        }

        blockers.addAll(intermediateAttempt.blockers());
        List<PlannedRecipe> attemptedSteps = intermediateAttempt.steps().isEmpty()
                ? appendStep(steps, target)
                : intermediateAttempt.steps();
        return IntermediateAttempt.blocked(attemptedSteps, blockers);
    }

    private IntermediateAttempt craftIntermediateForAny(PlannedRecipe target, KitchenInventorySnapshot working,
                                                        int depth, Set<String> visiting,
                                                        List<PlannedRecipe> steps) {
        CandidateIngredients candidates = candidateIngredients(target, working);
        EnumSet<PlanningBlocker> blockers = EnumSet.noneOf(PlanningBlocker.class);
        List<PlannedRecipe> attemptedSteps = new ArrayList<>();
        boolean triedProducer = false;
        boolean missingCandidateWithoutProducer = false;

        for (PlannedIngredient ingredient : candidates.ingredients()) {
            List<PlannedRecipe> producers = intermediateProducers(ingredient);
            if (producers.isEmpty()) {
                if (candidates.hasMissingIngredient()) {
                    missingCandidateWithoutProducer = true;
                }
                continue;
            }

            triedProducer = true;
            IntermediateAttempt attempt = craftIntermediateFor(target, ingredient, producers, working, depth, visiting,
                    steps);
            if (attempt.plan().isPresent()) {
                return attempt;
            }
            blockers.addAll(attempt.blockers());
            rememberFirstFailedBranch(attemptedSteps, attempt.steps());
        }

        if (missingCandidateWithoutProducer) {
            blockers.add(PlanningBlocker.MISSING_INTERMEDIATE_RECIPE);
        }
        if (!triedProducer && blockers.isEmpty()) {
            blockers.add(candidates.hasMissingIngredient()
                    ? PlanningBlocker.MISSING_INTERMEDIATE_RECIPE
                    : PlanningBlocker.MISSING_RAW_INGREDIENT);
        }
        return IntermediateAttempt.blocked(attemptedSteps, blockers);
    }

    private IntermediateAttempt craftIntermediateFor(PlannedRecipe target, PlannedIngredient ingredient,
                                                     List<PlannedRecipe> producers, KitchenInventorySnapshot working,
                                                     int depth, Set<String> visiting, List<PlannedRecipe> steps) {
        EnumSet<PlanningBlocker> blockers = EnumSet.noneOf(PlanningBlocker.class);
        List<PlannedRecipe> attemptedSteps = new ArrayList<>();
        for (PlannedRecipe candidate : producers) {
            PlanAttempt candidatePlan = plan(candidate, working, depth + 1, visiting);
            if (candidatePlan.state() != PlanState.BLOCKED) {
                if (countMatchingItems(candidatePlan.snapshot(), ingredient) > countMatchingItems(working, ingredient)) {
                    List<PlannedRecipe> nextSteps = appendSteps(steps, candidatePlan.steps());
                    IntermediateAttempt continuation = planIngredients(target, candidatePlan.snapshot(), depth,
                            visiting, nextSteps, true);
                    if (continuation.plan().isPresent()) {
                        return continuation;
                    }
                    blockers.addAll(continuation.blockers());
                    rememberFirstFailedBranch(attemptedSteps, continuation.steps());
                } else {
                    blockers.add(PlanningBlocker.MISSING_RAW_INGREDIENT);
                    rememberFirstFailedBranch(attemptedSteps, candidatePlan.steps());
                }
                continue;
            }
            blockers.addAll(candidatePlan.blockers());
            rememberFirstFailedBranch(attemptedSteps, candidatePlan.steps());
        }

        if (blockers.isEmpty()) {
            blockers.add(PlanningBlocker.MISSING_INTERMEDIATE_RECIPE);
        }
        return IntermediateAttempt.blocked(attemptedSteps, blockers);
    }

    private static CandidateIngredients candidateIngredients(PlannedRecipe target, KitchenInventorySnapshot working) {
        List<PlannedIngredient> missingIngredients = target.ingredients().stream()
                .filter(ingredient -> IngredientMatcher.findMatches(working, List.of(ingredient)).isEmpty())
                .toList();
        if (!missingIngredients.isEmpty()) {
            return new CandidateIngredients(missingIngredients, true);
        }
        return new CandidateIngredients(target.ingredients(), false);
    }

    private static int countMatchingItems(KitchenInventorySnapshot snapshot, PlannedIngredient ingredient) {
        int count = 0;
        for (ItemStack stack : snapshot.stacks()) {
            if (ingredient.ingredient().test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
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
        if (target.fuel().isPresent() && !hasFuel(target, snapshot)) {
            blockers.add(PlanningBlocker.MISSING_FUEL);
        }
        return blockers;
    }

    private static List<PlannedIngredient> requiredIngredients(PlannedRecipe target) {
        List<PlannedIngredient> ingredients = new ArrayList<>(target.ingredients());
        ingredients.addAll(requiredExtras(target));
        return List.copyOf(ingredients);
    }

    private Optional<PlanAttempt> planWithUtility(PlannedRecipe target, KitchenInventorySnapshot snapshot,
                                                  EnumSet<PlanningBlocker> baseBlockers, List<PlannedRecipe> steps,
                                                  boolean usedIntermediate) {
        UtilityIngredientAnalysis utilityIngredient = missingUtilityIngredient(target, snapshot);
        if (utilityIngredient.blockedByMultipleUtilityIngredients()) {
            EnumSet<PlanningBlocker> blockers = baseBlockers.clone();
            blockers.add(PlanningBlocker.MISSING_RAW_INGREDIENT);
            return Optional.of(PlanAttempt.blocked(target, snapshot, appendStep(steps, target), blockers));
        }
        if (utilityIngredient.ingredient().isEmpty()) {
            return Optional.empty();
        }

        UtilityIngredient utility = utilityIngredient.ingredient().orElseThrow();
        PlannedRecipe utilityStep = withUtilityRequirement(target, utility);
        EnumSet<PlanningBlocker> blockers = baseBlockers.clone();
        utilityService.map(service -> service.blockersFor(utility.requirement()))
                .ifPresent(blockers::addAll);
        Optional<List<ItemStack>> containerMatch = IngredientMatcher.findMatches(snapshot,
                List.of(containerIngredientFor(utility.requirement())));
        if (containerMatch.isEmpty()) {
            blockers.add(containerBlockerFor(utility.requirement()));
        }
        if (!blockers.isEmpty()) {
            return Optional.of(PlanAttempt.blocked(target, snapshot, appendStep(steps, utilityStep), blockers));
        }

        KitchenInventorySnapshot reserved = snapshot.withConsumed(containerMatch.orElseThrow());
        Optional<List<ItemStack>> matchesWithoutUtility = IngredientMatcher.findMatches(reserved,
                requiredIngredients(utilityStep));
        if (matchesWithoutUtility.isEmpty()) {
            return Optional.empty();
        }

        KitchenInventorySnapshot completed = reserved.withConsumed(matchesWithoutUtility.orElseThrow());
        KitchenInventorySnapshot beforeOutput = completed.withAdded(containerMatch.orElseThrow());
        List<PlannedRecipe> completedSteps = appendStep(steps, utilityStep);
        return Optional.of(new PlanAttempt(
                target,
                beforeOutput.withAdded(target.outputs()),
                beforeOutput,
                usedIntermediate ? PlanState.CRAFTABLE_WITH_INTERMEDIATES : PlanState.READY,
                completedSteps,
                EnumSet.noneOf(PlanningBlocker.class)
        ));
    }

    private UtilityIngredientAnalysis missingUtilityIngredient(PlannedRecipe target, KitchenInventorySnapshot snapshot) {
        if (utilityService.isEmpty()
                || target.stationType() != KitchenStationType.CRAFTING
                || target.utilityRequirement() != KitchenUtilityRequirement.NONE) {
            return UtilityIngredientAnalysis.none();
        }

        Optional<UtilityIngredient> missingUtilityIngredient = Optional.empty();
        for (PlannedIngredient ingredient : target.ingredients()) {
            if (IngredientMatcher.findMatches(snapshot, List.of(ingredient)).isPresent()) {
                continue;
            }
            Optional<KitchenUtilityRequirement> requirement = utilityRequirementFor(ingredient);
            if (requirement.isPresent()) {
                if (missingUtilityIngredient.isPresent()) {
                    // V1 plans at most one utility substitution per crafting step; Task 6 can lift this with
                    // explicit per-step borrowed-container accounting.
                    return UtilityIngredientAnalysis.multipleUtilityIngredients();
                }
                missingUtilityIngredient = Optional.of(new UtilityIngredient(ingredient, requirement.orElseThrow()));
            }
        }
        return new UtilityIngredientAnalysis(missingUtilityIngredient, false);
    }

    private static Optional<KitchenUtilityRequirement> utilityRequirementFor(PlannedIngredient ingredient) {
        if (ingredient.count() != 1) {
            return Optional.empty();
        }
        ItemStack[] displayStacks = ingredient.ingredient().getItems();
        if (displayStacks.length != 1) {
            return Optional.empty();
        }

        ItemStack stack = displayStacks[0];
        if (stack.is(Items.WATER_BUCKET)) {
            return Optional.of(KitchenUtilityRequirement.WATER);
        }
        if (stack.is(Items.ICE)) {
            return Optional.of(KitchenUtilityRequirement.ICE);
        }
        if (stack.is(Items.SNOWBALL)) {
            return Optional.of(KitchenUtilityRequirement.SNOWBALL);
        }
        return Optional.empty();
    }

    private static PlannedRecipe withUtilityRequirement(PlannedRecipe target, UtilityIngredient utility) {
        List<PlannedIngredient> ingredients = new ArrayList<>(target.ingredients());
        ingredients.remove(utility.ingredient());
        return new PlannedRecipe(
                target.id(),
                ingredients,
                target.outputs(),
                target.stationType(),
                target.servingContainer(),
                target.fuel(),
                utility.requirement(),
                target.processingTime(),
                target.asynchronous()
        );
    }

    private static PlannedIngredient containerIngredientFor(KitchenUtilityRequirement requirement) {
        return switch (requirement) {
            case NONE -> throw new IllegalArgumentException("NONE does not require a utility container");
            case WATER -> new PlannedIngredient(Ingredient.of(Items.BUCKET, Items.GLASS_BOTTLE), 1);
            case ICE -> new PlannedIngredient(Ingredient.of(Items.BUCKET), 1);
            case SNOWBALL -> new PlannedIngredient(Ingredient.of(Items.GLASS_BOTTLE), 1);
        };
    }

    private static PlanningBlocker containerBlockerFor(KitchenUtilityRequirement requirement) {
        return switch (requirement) {
            case NONE -> throw new IllegalArgumentException("NONE does not require a utility container");
            case WATER -> PlanningBlocker.MISSING_WATER_CONTAINER;
            case ICE -> PlanningBlocker.MISSING_ICE_CONTAINER;
            case SNOWBALL -> PlanningBlocker.MISSING_SNOWBALL_CONTAINER;
        };
    }

    private static List<PlannedIngredient> requiredExtras(PlannedRecipe target) {
        List<PlannedIngredient> extras = new ArrayList<>();
        target.servingContainer().map(RecipePlanner::servingContainerIngredient).ifPresent(extras::add);
        target.fuel().ifPresent(extras::add);
        return List.copyOf(extras);
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

    private static boolean hasFuel(PlannedRecipe target, KitchenInventorySnapshot snapshot) {
        return matchingFuel(target, snapshot).isPresent();
    }

    private static Optional<List<ItemStack>> matchingFuel(PlannedRecipe target, KitchenInventorySnapshot snapshot) {
        return target.fuel()
                .map(fuelIngredient -> IngredientMatcher.findMatches(snapshot, List.of(fuelIngredient)))
                .orElseGet(() -> Optional.of(List.of()));
    }

    private static PlannedIngredient servingContainerIngredient(ItemStack servingContainer) {
        return new PlannedIngredient(Ingredient.of(servingContainer.getItem()), servingContainer.getCount());
    }

    private static List<PlannedRecipe> appendStep(List<PlannedRecipe> steps, PlannedRecipe step) {
        List<PlannedRecipe> next = new ArrayList<>(steps);
        next.add(step);
        return List.copyOf(next);
    }

    private static List<PlannedRecipe> appendSteps(List<PlannedRecipe> steps, List<PlannedRecipe> nextSteps) {
        List<PlannedRecipe> next = new ArrayList<>(steps);
        next.addAll(nextSteps);
        return List.copyOf(next);
    }

    private static void rememberFirstFailedBranch(List<PlannedRecipe> attemptedSteps, List<PlannedRecipe> branchSteps) {
        if (attemptedSteps.isEmpty()) {
            attemptedSteps.addAll(branchSteps);
        }
    }

    private static Set<String> withVisited(Set<String> visiting, String recipeId) {
        java.util.HashSet<String> nextVisiting = new java.util.HashSet<>(visiting);
        nextVisiting.add(recipeId);
        return Set.copyOf(nextVisiting);
    }

    private record PlanAttempt(
            PlannedRecipe target,
            KitchenInventorySnapshot snapshot,
            KitchenInventorySnapshot snapshotBeforeOutput,
            PlanState state,
            List<PlannedRecipe> steps,
            EnumSet<PlanningBlocker> blockers
    ) {
        private PlanAttempt {
            target = Objects.requireNonNull(target, "target");
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
            snapshotBeforeOutput = Objects.requireNonNull(snapshotBeforeOutput, "snapshotBeforeOutput");
            state = Objects.requireNonNull(state, "state");
            steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
            blockers = Objects.requireNonNull(blockers, "blockers").clone();
        }

        private static PlanAttempt ready(PlannedRecipe target, KitchenInventorySnapshot snapshot) {
            return new PlanAttempt(target, snapshot.withAdded(target.outputs()), snapshot, PlanState.READY, List.of(target),
                    EnumSet.noneOf(PlanningBlocker.class));
        }

        private static PlanAttempt blocked(PlannedRecipe target, KitchenInventorySnapshot snapshot,
                                           List<PlannedRecipe> steps, EnumSet<PlanningBlocker> blockers) {
            return new PlanAttempt(target, snapshot, snapshot, PlanState.BLOCKED, steps, blockers);
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

    private record CandidateIngredients(List<PlannedIngredient> ingredients, boolean hasMissingIngredient) {
        private CandidateIngredients {
            ingredients = List.copyOf(Objects.requireNonNull(ingredients, "ingredients"));
        }
    }

    private record UtilityIngredient(PlannedIngredient ingredient, KitchenUtilityRequirement requirement) {
        private UtilityIngredient {
            ingredient = Objects.requireNonNull(ingredient, "ingredient");
            requirement = Objects.requireNonNull(requirement, "requirement");
        }
    }

    private record UtilityIngredientAnalysis(
            Optional<UtilityIngredient> ingredient,
            boolean blockedByMultipleUtilityIngredients
    ) {
        private UtilityIngredientAnalysis {
            ingredient = Objects.requireNonNull(ingredient, "ingredient");
        }

        private static UtilityIngredientAnalysis none() {
            return new UtilityIngredientAnalysis(Optional.empty(), false);
        }

        private static UtilityIngredientAnalysis multipleUtilityIngredients() {
            return new UtilityIngredientAnalysis(Optional.empty(), true);
        }
    }

    public record RecipePlanningResult(
            RecipePlan plan,
            KitchenInventorySnapshot snapshotAfterPlan,
            KitchenInventorySnapshot snapshotBeforeOutput
    ) {
        public RecipePlanningResult {
            plan = Objects.requireNonNull(plan, "plan");
            snapshotAfterPlan = Objects.requireNonNull(snapshotAfterPlan, "snapshotAfterPlan");
            snapshotBeforeOutput = Objects.requireNonNull(snapshotBeforeOutput, "snapshotBeforeOutput");
        }
    }
}
