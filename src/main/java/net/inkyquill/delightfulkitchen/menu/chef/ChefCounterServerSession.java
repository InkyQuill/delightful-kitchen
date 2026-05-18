package net.inkyquill.delightfulkitchen.menu.chef;

import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryService;
import net.inkyquill.delightfulkitchen.kitchen.KitchenScanResult;
import net.inkyquill.delightfulkitchen.kitchen.KitchenScanner;
import net.inkyquill.delightfulkitchen.kitchen.KitchenServices;
import net.inkyquill.delightfulkitchen.kitchen.execution.KitchenPlanExecutionResult;
import net.inkyquill.delightfulkitchen.kitchen.execution.KitchenPlanExecutor;
import net.inkyquill.delightfulkitchen.kitchen.execution.KitchenPlanFingerprint;
import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenInventorySnapshot;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanState;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlannedRecipe;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;
import net.inkyquill.delightfulkitchen.kitchen.recipe.RecipeBatchPlan;
import net.inkyquill.delightfulkitchen.kitchen.recipe.RecipeBatchPlanner;
import net.inkyquill.delightfulkitchen.kitchen.recipe.RecipeIndex;
import net.inkyquill.delightfulkitchen.kitchen.recipe.RecipePlan;
import net.inkyquill.delightfulkitchen.kitchen.recipe.RecipePlanner;
import net.inkyquill.delightfulkitchen.kitchen.utility.KitchenUtilityService;
import net.inkyquill.delightfulkitchen.network.payload.ClientboundChefCounterDetailsPayload;
import net.inkyquill.delightfulkitchen.network.payload.ClientboundChefCounterQueueResultPayload;
import net.inkyquill.delightfulkitchen.network.payload.ClientboundChefCounterSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ChefCounterServerSession {
    private static final KitchenPlanFingerprint EMPTY_FINGERPRINT = new KitchenPlanFingerprint(0, 0, 0, 0, 0);
    private static final Comparator<PlanEntry> PLAN_ORDER = Comparator
            .comparing((PlanEntry entry) -> entry.plan().output().getHoverName().getString())
            .thenComparing(entry -> RecipeIndex.stackKey(entry.plan().output()))
            .thenComparing(entry -> entry.plan().targetRecipeId().toString());

    private final Backend backend;
    private SessionData data;
    private List<PlanEntry> entries = List.of();
    private List<RecipePlan> plans = List.of();
    private List<ChefRecipeSummary> summaries = List.of();
    private KitchenPlanFingerprint fingerprint = EMPTY_FINGERPRINT;

    public ChefCounterServerSession(Level level, BlockPos counterPos) {
        this(new LevelBackend(level, counterPos));
    }

    ChefCounterServerSession(Backend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
        rebuild();
    }

    public void rebuild() {
        data = backend.build();
        if (!data.scan().hasKitchenFloor()) {
            entries = List.of();
            plans = List.of();
            summaries = List.of();
            fingerprint = EMPTY_FINGERPRINT;
            return;
        }

        entries = data.plans().stream()
                .map(plan -> targetFor(plan, data.targets()))
                .flatMap(Optional::stream)
                .sorted(PLAN_ORDER)
                .toList();
        plans = entries.stream().map(PlanEntry::plan).toList();
        summaries = indexedSummaries(plans);
        fingerprint = KitchenPlanFingerprint.of(data.scan(), data.snapshot());
    }

    public List<RecipePlan> plans() {
        return plans;
    }

    public List<ChefRecipeSummary> summaries() {
        return summaries;
    }

    public KitchenPlanFingerprint fingerprint() {
        return fingerprint;
    }

    public int floorCount() {
        return data.scan().floorCount();
    }

    public int inventoryCount() {
        return data.inventoryCount();
    }

    public boolean hasMultipleCounters() {
        return data.scan().hasMultipleCounters();
    }

    public int waterProviderCount() {
        return data.scan().waterProviders().size();
    }

    public int freezerCount() {
        return data.scan().freezers().size();
    }

    public int recipeCount() {
        return plans.size();
    }

    public int readyCount() {
        return countPlans(PlanState.READY);
    }

    public int intermediateCount() {
        return countPlans(PlanState.CRAFTABLE_WITH_INTERMEDIATES);
    }

    public int blockedCount() {
        return countPlans(PlanState.BLOCKED);
    }

    public ClientboundChefCounterSyncPayload syncPayload(int containerId) {
        return new ClientboundChefCounterSyncPayload(containerId, summaries, floorCount(), inventoryCount(),
                recipeCount(), readyCount(), intermediateCount(), blockedCount(), waterProviderCount(), freezerCount(),
                hasMultipleCounters());
    }

    public ClientboundChefCounterDetailsPayload detailsPayload(int containerId, int selectedIndex, int quantity) {
        return new ClientboundChefCounterDetailsPayload(containerId, details(selectedIndex, quantity));
    }

    public Optional<ChefPlanDetails> details(int selectedIndex, int quantity) {
        Optional<ResourceLocation> selectedRecipeId = recipeIdAt(selectedIndex);
        rebuild();
        return selectedRecipeId.flatMap(recipeId -> detailsFromCurrentSession(recipeId, quantity));
    }

    private Optional<ChefPlanDetails> detailsFromCurrentSession(ResourceLocation selectedRecipeId, int quantity) {
        Optional<IndexedBatchPlan> batchPlan = batchPlan(selectedRecipeId, quantity);
        return batchPlan.map(plan -> details(plan.index(), plan.batchPlan()));
    }

    public Confirmation confirm(int selectedIndex, int quantity, KitchenPlanFingerprint expectedFingerprint) {
        KitchenPlanFingerprint clientFingerprint = Objects.requireNonNull(expectedFingerprint, "expectedFingerprint");
        Optional<ResourceLocation> selectedRecipeId = recipeIdAt(selectedIndex);
        rebuild();
        if (!fingerprint.equals(clientFingerprint)) {
            KitchenPlanExecutionResult result = KitchenPlanExecutionResult.blocked(PlanningBlocker.KITCHEN_CHANGED);
            return new Confirmation(result, Optional.empty());
        }

        Optional<IndexedBatchPlan> batchPlan = selectedRecipeId.flatMap(recipeId -> batchPlan(recipeId, quantity));
        if (batchPlan.isEmpty()) {
            return new Confirmation(KitchenPlanExecutionResult.blocked(PlanningBlocker.KITCHEN_CHANGED),
                    Optional.empty());
        }

        RecipePlan plan = batchPlan.orElseThrow().batchPlan().plan();
        if (plan.state() == PlanState.BLOCKED) {
            return new Confirmation(KitchenPlanExecutionResult.blocked(plan.blockers()),
                    Optional.of(details(batchPlan.orElseThrow().index(), batchPlan.orElseThrow().batchPlan())));
        }

        KitchenPlanExecutionResult result = backend.confirm(plan, clientFingerprint);
        rebuild();
        return new Confirmation(result, selectedRecipeId.flatMap(recipeId -> detailsFromCurrentSession(recipeId, quantity)));
    }

    public ClientboundChefCounterQueueResultPayload queueResultPayload(int containerId, Confirmation confirmation) {
        return new ClientboundChefCounterQueueResultPayload(containerId,
                ChefCounterViewModels.queueResult(confirmation.result()));
    }

    private Optional<IndexedBatchPlan> batchPlan(ResourceLocation selectedRecipeId, int quantity) {
        for (int index = 0; index < entries.size(); index++) {
            PlanEntry entry = entries.get(index);
            if (entry.target().id().equals(selectedRecipeId)) {
                return Optional.of(new IndexedBatchPlan(index,
                        data.batchPlanner().plan(entry.target(), data.snapshot(), quantity)));
            }
        }
        return Optional.empty();
    }

    private Optional<ResourceLocation> recipeIdAt(int selectedIndex) {
        if (selectedIndex < 0 || selectedIndex >= entries.size()) {
            return Optional.empty();
        }
        return Optional.of(entries.get(selectedIndex).target().id());
    }

    private ChefPlanDetails details(int selectedIndex, RecipeBatchPlan batchPlan) {
        RecipePlan plan = batchPlan.plan();
        return new ChefPlanDetails(
                selectedIndex,
                plan.targetRecipeId(),
                plan.output(),
                plan.state(),
                batchPlan.requestedQuantity(),
                batchPlan.expectedOutputCount(),
                plan.steps().stream().map(ChefCounterViewModels::step).toList(),
                plan.blockers(),
                fingerprint
        );
    }

    private int countPlans(PlanState state) {
        int count = 0;
        for (RecipePlan plan : plans) {
            if (plan.state() == state) {
                count++;
            }
        }
        return count;
    }

    private static Optional<PlanEntry> targetFor(RecipePlan plan, List<PlannedRecipe> targets) {
        for (PlannedRecipe target : targets) {
            if (target.id().equals(plan.targetRecipeId())) {
                return Optional.of(new PlanEntry(plan, target));
            }
        }
        if (!plan.steps().isEmpty()) {
            PlannedRecipe target = plan.steps().getLast();
            if (target.id().equals(plan.targetRecipeId())) {
                return Optional.of(new PlanEntry(plan, target));
            }
        }
        return Optional.empty();
    }

    private static List<ChefRecipeSummary> indexedSummaries(List<RecipePlan> sortedPlans) {
        java.util.ArrayList<ChefRecipeSummary> indexed = new java.util.ArrayList<>(sortedPlans.size());
        for (int index = 0; index < sortedPlans.size(); index++) {
            indexed.add(ChefCounterViewModels.summary(index, sortedPlans.get(index)));
        }
        return List.copyOf(indexed);
    }

    public record Confirmation(KitchenPlanExecutionResult result, Optional<ChefPlanDetails> details) {
        public Confirmation {
            result = Objects.requireNonNull(result, "result");
            details = Objects.requireNonNull(details, "details");
        }
    }

    record SessionData(
            KitchenScanResult scan,
            int inventoryCount,
            KitchenInventorySnapshot snapshot,
            BatchPlanner batchPlanner,
            List<PlannedRecipe> targets,
            List<RecipePlan> plans
    ) {
        SessionData {
            scan = Objects.requireNonNull(scan, "scan");
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
            batchPlanner = Objects.requireNonNull(batchPlanner, "batchPlanner");
            targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
            plans = List.copyOf(Objects.requireNonNull(plans, "plans"));
        }

        static SessionData empty(KitchenScanResult scan) {
            return new SessionData(scan, 0, new KitchenInventorySnapshot(List.of()), (target, snapshot, quantity) -> {
                throw new IllegalStateException("empty session cannot create batch plans");
            }, List.of(), List.of());
        }
    }

    interface Backend {
        SessionData build();

        KitchenPlanExecutionResult confirm(RecipePlan plan, KitchenPlanFingerprint fingerprint);
    }

    interface BatchPlanner {
        RecipeBatchPlan plan(PlannedRecipe target, KitchenInventorySnapshot snapshot, int requestedQuantity);
    }

    private record PlanEntry(RecipePlan plan, PlannedRecipe target) {
        private PlanEntry {
            plan = Objects.requireNonNull(plan, "plan");
            target = Objects.requireNonNull(target, "target");
        }
    }

    private record IndexedBatchPlan(int index, RecipeBatchPlan batchPlan) {
        private IndexedBatchPlan {
            batchPlan = Objects.requireNonNull(batchPlan, "batchPlan");
        }
    }

    private static final class LevelBackend implements Backend {
        private final Level level;
        private final BlockPos counterPos;

        private LevelBackend(Level level, BlockPos counterPos) {
            this.level = Objects.requireNonNull(level, "level");
            this.counterPos = Objects.requireNonNull(counterPos, "counterPos");
        }

        @Override
        public SessionData build() {
            KitchenScanResult scan = KitchenScanner.scan(level, counterPos);
            if (!scan.hasKitchenFloor()) {
                return SessionData.empty(scan);
            }

            var endpoints = KitchenServices.inventoryEndpoints(level, scan, counterPos);
            var inventory = new KitchenInventoryService(endpoints);
            var utilityService = new KitchenUtilityService(scan, inventory);
            RecipeIndex recipeIndex = RecipeIndex.build(level.getRecipeManager(), level.registryAccess());
            RecipePlanner planner = new RecipePlanner(recipeIndex, KitchenServices.stationTypes(level, scan),
                    utilityService);
            RecipeBatchPlanner batchPlanner = new RecipeBatchPlanner(planner);
            KitchenInventorySnapshot snapshot = inventory.snapshot();
            return new SessionData(scan, endpoints.size(), snapshot, batchPlanner::plan, recipeIndex.edibleOutputs(),
                    planner.planAllEdible(snapshot));
        }

        @Override
        public KitchenPlanExecutionResult confirm(RecipePlan plan, KitchenPlanFingerprint fingerprint) {
            KitchenScanResult scan = KitchenScanner.scan(level, counterPos);
            if (!scan.hasKitchenFloor()) {
                return KitchenPlanExecutionResult.blocked(PlanningBlocker.KITCHEN_CHANGED);
            }

            var inventory = new KitchenInventoryService(KitchenServices.inventoryEndpoints(level, scan, counterPos));
            var executor = new KitchenPlanExecutor(inventory, KitchenServices.stationService(level, scan), scan);
            return executor.confirm(plan, fingerprint);
        }
    }
}
