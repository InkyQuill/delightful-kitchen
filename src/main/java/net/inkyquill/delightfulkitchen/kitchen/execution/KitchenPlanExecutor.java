package net.inkyquill.delightfulkitchen.kitchen.execution;

import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryService;
import net.inkyquill.delightfulkitchen.kitchen.KitchenScanResult;
import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlannedIngredient;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlannedRecipe;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;
import net.inkyquill.delightfulkitchen.kitchen.recipe.RecipePlan;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStationEndpoint;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStationJob;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStationService;
import net.inkyquill.delightfulkitchen.kitchen.utility.BorrowedContainer;
import net.inkyquill.delightfulkitchen.kitchen.utility.KitchenUtilityRequirement;
import net.inkyquill.delightfulkitchen.kitchen.utility.KitchenUtilityService;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class KitchenPlanExecutor {
    private final KitchenInventoryService inventory;
    private final KitchenStationService stations;
    private final KitchenScanResult scan;
    private final KitchenUtilityService utilityService;

    public KitchenPlanExecutor(KitchenInventoryService inventory, KitchenStationService stations, KitchenScanResult scan) {
        this(inventory, stations, scan, new KitchenUtilityService(scan, inventory));
    }

    public KitchenPlanExecutor(KitchenInventoryService inventory, KitchenStationService stations, KitchenScanResult scan,
                               KitchenUtilityService utilityService) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.stations = Objects.requireNonNull(stations, "stations");
        this.scan = Objects.requireNonNull(scan, "scan");
        this.utilityService = Objects.requireNonNull(utilityService, "utilityService");
    }

    public KitchenPlanExecutionResult confirm(RecipePlan plan, KitchenPlanFingerprint expectedFingerprint) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(expectedFingerprint, "expectedFingerprint");

        if (!expectedFingerprint.matches(scan, inventory.snapshot())) {
            return KitchenPlanExecutionResult.blocked(PlanningBlocker.KITCHEN_CHANGED);
        }

        EnumSet<PlanningBlocker> blockers = validatePlan(plan);
        if (!blockers.isEmpty()) {
            return KitchenPlanExecutionResult.blocked(blockers);
        }

        List<ExtractedJob> extractedJobs = new ArrayList<>();
        List<KitchenStationJob> stationJobs = new ArrayList<>();
        for (PlannedRecipe step : plan.steps()) {
            ExtractJobResult extracted = extractJob(step);
            if (extracted.job().isEmpty()) {
                rollback(extractedJobs);
                return KitchenPlanExecutionResult.blocked(extracted.blockers());
            }

            KitchenStationJob job = extracted.job().orElseThrow();
            if (job.stationType() == KitchenStationType.CRAFTING) {
                if (!inventory.insertAll(job.outputs())) {
                    assertReleasedBorrowedContainer(extracted.borrowedContainer());
                    rollbackExtractedJob(job, false);
                    rollback(extractedJobs);
                    return KitchenPlanExecutionResult.blocked(PlanningBlocker.OUTPUT_STORAGE_FULL);
                }
                if (!releaseBorrowedContainer(extracted.borrowedContainer())) {
                    rollbackInstantCraftingAfterReleaseFailure(extractedJobs, job, extracted.borrowedContainer());
                    return KitchenPlanExecutionResult.blocked(PlanningBlocker.OUTPUT_STORAGE_FULL);
                }
                extractedJobs.add(new ExtractedJob(job, true));
                continue;
            }

            if (extracted.borrowedContainer().isPresent()) {
                assertReleasedBorrowedContainer(extracted.borrowedContainer());
                rollbackExtractedJob(job, false);
                rollback(extractedJobs);
                return KitchenPlanExecutionResult.blocked(PlanningBlocker.MISSING_RAW_INGREDIENT);
            }
            extractedJobs.add(new ExtractedJob(job, false));
            stationJobs.add(job);
        }

        Optional<List<KitchenStationEndpoint>> endpoints = selectAcceptingEndpoints(stationJobs);
        if (endpoints.isEmpty()) {
            rollback(extractedJobs);
            return KitchenPlanExecutionResult.blocked(PlanningBlocker.STATION_QUEUE_FULL);
        }

        for (int index = 0; index < stationJobs.size(); index++) {
            KitchenStationJob job = stationJobs.get(index);
            var accepted = endpoints.orElseThrow().get(index).station().accept(job);
            if (!accepted.accepted()) {
                rollbackUnacceptedStationJobs(stationJobs, index);
                throw new IllegalStateException(
                        "Station accept failed after validation for " + job.recipeId() + ": " + accepted.blockers()
                );
            }
        }

        return KitchenPlanExecutionResult.queuedResult();
    }

    private EnumSet<PlanningBlocker> validatePlan(RecipePlan plan) {
        EnumSet<PlanningBlocker> blockers = EnumSet.noneOf(PlanningBlocker.class);
        List<KitchenStationJob> stationJobs = new ArrayList<>();
        List<ItemStack> futureStationOutputs = new ArrayList<>();
        for (PlannedRecipe step : plan.steps()) {
            if (step.stationType() == KitchenStationType.CRAFTING) {
                if (!inventory.canInsertAll(step.outputs())) {
                    blockers.add(PlanningBlocker.OUTPUT_STORAGE_FULL);
                }
            } else if (step.utilityRequirement() != KitchenUtilityRequirement.NONE) {
                blockers.add(PlanningBlocker.MISSING_RAW_INGREDIENT);
            } else {
                stationJobs.add(jobWithoutExtractedInputs(step));
                futureStationOutputs.addAll(step.outputs());
            }
        }
        if (!futureStationOutputs.isEmpty() && !inventory.canInsertAll(futureStationOutputs)) {
            blockers.add(PlanningBlocker.OUTPUT_STORAGE_FULL);
        }
        blockers.addAll(validateStationJobs(stationJobs));
        return blockers;
    }

    private EnumSet<PlanningBlocker> validateStationJobs(List<KitchenStationJob> stationJobs) {
        EnumSet<PlanningBlocker> blockers = EnumSet.noneOf(PlanningBlocker.class);
        Map<KitchenStationEndpoint, Integer> reservations = new HashMap<>();
        for (KitchenStationJob job : stationJobs) {
            if (stations.firstAccepting(job).isEmpty()) {
                blockers.addAll(stationBlockers(job));
                continue;
            }
            Optional<KitchenStationEndpoint> endpoint = firstAcceptingWithReservation(job, reservations);
            if (endpoint.isEmpty()) {
                blockers.add(PlanningBlocker.STATION_QUEUE_FULL);
                continue;
            }
            reservations.merge(endpoint.orElseThrow(), 1, Integer::sum);
        }
        return blockers;
    }

    private Optional<List<KitchenStationEndpoint>> selectAcceptingEndpoints(List<KitchenStationJob> stationJobs) {
        List<KitchenStationEndpoint> selected = new ArrayList<>();
        Map<KitchenStationEndpoint, Integer> reservations = new HashMap<>();
        for (KitchenStationJob job : stationJobs) {
            if (stations.firstAccepting(job).isEmpty()) {
                return Optional.empty();
            }
            Optional<KitchenStationEndpoint> endpoint = firstAcceptingWithReservation(job, reservations);
            if (endpoint.isEmpty()) {
                return Optional.empty();
            }
            KitchenStationEndpoint selectedEndpoint = endpoint.orElseThrow();
            reservations.merge(selectedEndpoint, 1, Integer::sum);
            selected.add(selectedEndpoint);
        }
        return Optional.of(List.copyOf(selected));
    }

    private Optional<KitchenStationEndpoint> firstAcceptingWithReservation(KitchenStationJob job,
                                                                          Map<KitchenStationEndpoint, Integer> reservations) {
        return stations.endpoints().stream()
                .filter(endpoint -> endpoint.station().stationType() == job.stationType())
                .filter(endpoint -> endpoint.station().canAccept(job).accepted())
                .filter(endpoint -> endpoint.station().queuedJobCount()
                        + reservations.getOrDefault(endpoint, 0) < endpoint.station().queueCapacity())
                .min(java.util.Comparator
                        .comparingInt((KitchenStationEndpoint endpoint) ->
                                endpoint.station().queuedJobCount() + reservations.getOrDefault(endpoint, 0))
                        .thenComparing(endpoint -> endpoint.pos().asLong()));
    }

    private EnumSet<PlanningBlocker> stationBlockers(KitchenStationJob job) {
        EnumSet<PlanningBlocker> blockers = EnumSet.noneOf(PlanningBlocker.class);
        if (!stations.hasStation(job.stationType())) {
            blockers.add(PlanningBlocker.MISSING_STATION);
            return blockers;
        }
        for (KitchenStationEndpoint endpoint : stations.endpoints()) {
            if (endpoint.station().stationType() == job.stationType()) {
                blockers.addAll(endpoint.station().canAccept(job).blockers());
            }
        }
        if (blockers.isEmpty()) {
            blockers.add(PlanningBlocker.STATION_QUEUE_FULL);
        }
        return blockers;
    }

    private KitchenStationJob jobWithoutExtractedInputs(PlannedRecipe step) {
        return new KitchenStationJob(step.id(), step.stationType(), List.of(), step.outputs(),
                step.servingContainer(), Optional.empty(), step.processingTime());
    }

    private ExtractJobResult extractJob(PlannedRecipe step) {
        List<ItemStack> inputs = new ArrayList<>();
        Optional<ItemStack> container = Optional.empty();
        Optional<ItemStack> fuel = Optional.empty();

        for (PlannedIngredient ingredient : step.ingredients()) {
            Optional<List<ItemStack>> extracted = inventory.extractIngredient(ingredient, false);
            if (extracted.isEmpty()) {
                rollbackExtractedParts(inputs, container, fuel);
                return ExtractJobResult.blocked(PlanningBlocker.MISSING_RAW_INGREDIENT);
            }
            inputs.addAll(extracted.orElseThrow());
        }

        if (step.servingContainer().isPresent()) {
            ItemStack required = step.servingContainer().orElseThrow();
            ItemStack extracted = inventory.extract(required, required.getCount(), false);
            if (extracted.getCount() != required.getCount()
                    || !ItemStack.isSameItemSameComponents(extracted, required)) {
                rollbackExtractedParts(inputs, optionalNonEmpty(extracted), fuel);
                return ExtractJobResult.blocked(PlanningBlocker.MISSING_CONTAINER);
            }
            container = Optional.of(extracted);
        }

        if (step.fuel().isPresent()) {
            Optional<List<ItemStack>> extractedFuel = inventory.extractIngredient(step.fuel().orElseThrow(), false);
            if (extractedFuel.isEmpty()) {
                rollbackExtractedParts(inputs, container, fuel);
                return ExtractJobResult.blocked(PlanningBlocker.MISSING_FUEL);
            }
            Optional<ItemStack> consolidatedFuel = consolidateFuel(extractedFuel.orElseThrow());
            if (consolidatedFuel.isEmpty()) {
                restoreStacks(extractedFuel.orElseThrow());
                rollbackExtractedParts(inputs, container, fuel);
                return ExtractJobResult.blocked(PlanningBlocker.MISSING_FUEL);
            }
            fuel = consolidatedFuel;
        }

        Optional<BorrowedContainer> borrowedContainer = Optional.empty();
        if (step.utilityRequirement() != KitchenUtilityRequirement.NONE) {
            if (step.stationType() != KitchenStationType.CRAFTING) {
                rollbackExtractedParts(inputs, container, fuel);
                return ExtractJobResult.blocked(PlanningBlocker.MISSING_RAW_INGREDIENT);
            }
            EnumSet<PlanningBlocker> blockers = utilityService.blockersFor(step.utilityRequirement());
            if (!blockers.isEmpty()) {
                rollbackExtractedParts(inputs, container, fuel);
                return ExtractJobResult.blocked(blockers);
            }
            borrowedContainer = utilityService.borrow(step.utilityRequirement());
            if (borrowedContainer.isEmpty()) {
                rollbackExtractedParts(inputs, container, fuel);
                EnumSet<PlanningBlocker> refreshedBlockers = utilityService.blockersFor(step.utilityRequirement());
                if (refreshedBlockers.isEmpty()) {
                    refreshedBlockers.add(containerBlockerFor(step.utilityRequirement()));
                }
                return ExtractJobResult.blocked(refreshedBlockers);
            }
        }

        KitchenStationJob job = new KitchenStationJob(step.id(), step.stationType(), inputs, step.outputs(),
                container, fuel, step.processingTime());
        return ExtractJobResult.success(job, borrowedContainer);
    }

    private Optional<ItemStack> consolidateFuel(List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return Optional.empty();
        }

        ItemStack prototype = stacks.getFirst();
        int count = 0;
        for (ItemStack stack : stacks) {
            if (!ItemStack.isSameItemSameComponents(prototype, stack)) {
                return Optional.empty();
            }
            count += stack.getCount();
        }
        return Optional.of(prototype.copyWithCount(count));
    }

    private void rollback(List<ExtractedJob> jobs) {
        assertNoAmbiguousInstantOutputRollback(jobs);
        for (int index = jobs.size() - 1; index >= 0; index--) {
            ExtractedJob extracted = jobs.get(index);
            rollbackExtractedJob(extracted.job(), extracted.insertedInstantOutputs());
        }
    }

    private void rollbackUnacceptedStationJobs(List<KitchenStationJob> stationJobs, int firstUnacceptedIndex) {
        for (int index = stationJobs.size() - 1; index >= firstUnacceptedIndex; index--) {
            KitchenStationJob job = stationJobs.get(index);
            rollbackExtractedParts(job.inputs(), job.servingContainer(), job.fuel());
        }
    }

    private void assertNoAmbiguousInstantOutputRollback(List<ExtractedJob> jobs) {
        for (int index = 0; index < jobs.size(); index++) {
            ExtractedJob extracted = jobs.get(index);
            if (!extracted.insertedInstantOutputs()) {
                continue;
            }

            for (ItemStack output : extracted.job().outputs()) {
                int recoverableMatchingCount = inventory.count(output) + heldMatchingCount(jobs, index + 1, output);
                int generatedMatchingCount = generatedMatchingCount(jobs, index, output);
                if (recoverableMatchingCount > generatedMatchingCount) {
                    // Failing here preserves player items. Retrying this same executor after
                    // this exception is not safe because ingredients and outputs may be mutated.
                    throw new IllegalStateException(
                            "Cannot safely rollback instant crafting output with pre-existing matching kitchen items"
                    );
                }
            }
        }
    }

    private int heldMatchingCount(List<ExtractedJob> jobs, int startIndex, ItemStack prototype) {
        int count = 0;
        for (int index = startIndex; index < jobs.size(); index++) {
            KitchenStationJob job = jobs.get(index).job();
            count += matchingCount(job.inputs(), prototype);
            count += job.servingContainer().filter(stack -> ItemStack.isSameItemSameComponents(stack, prototype))
                    .map(ItemStack::getCount)
                    .orElse(0);
            count += job.fuel().filter(stack -> ItemStack.isSameItemSameComponents(stack, prototype))
                    .map(ItemStack::getCount)
                    .orElse(0);
        }
        return count;
    }

    private int generatedMatchingCount(List<ExtractedJob> jobs, int startIndex, ItemStack prototype) {
        int count = 0;
        for (int index = startIndex; index < jobs.size(); index++) {
            ExtractedJob job = jobs.get(index);
            if (job.insertedInstantOutputs()) {
                count += matchingCount(job.job().outputs(), prototype);
            }
        }
        return count;
    }

    private int matchingCount(List<ItemStack> stacks, ItemStack prototype) {
        int count = 0;
        for (ItemStack stack : stacks) {
            if (ItemStack.isSameItemSameComponents(stack, prototype)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void rollbackExtractedJob(KitchenStationJob job, boolean removeInstantOutputs) {
        if (removeInstantOutputs) {
            removeInstantOutputs(job.outputs());
        }
        rollbackExtractedParts(job.inputs(), job.servingContainer(), job.fuel());
    }

    private void rollbackExtractedParts(List<ItemStack> inputs, Optional<ItemStack> container, Optional<ItemStack> fuel) {
        List<ItemStack> stacks = new ArrayList<>(inputs);
        container.ifPresent(stacks::add);
        fuel.ifPresent(stacks::add);
        restoreStacks(stacks);
    }

    private void restoreStacks(List<ItemStack> stacks) {
        if (!stacks.isEmpty() && !inventory.insertAll(stacks)) {
            throw new IllegalStateException("Failed to restore extracted kitchen stacks");
        }
    }

    private void removeInstantOutputs(List<ItemStack> outputs) {
        for (ItemStack output : outputs) {
            ItemStack removed = inventory.extract(output, output.getCount(), false);
            if (removed.getCount() != output.getCount()
                    || !ItemStack.isSameItemSameComponents(removed, output)) {
                throw new IllegalStateException("Failed to remove instant crafting output during rollback");
            }
        }
    }

    private Optional<ItemStack> optionalNonEmpty(ItemStack stack) {
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack);
    }

    private void rollbackInstantCraftingAfterReleaseFailure(List<ExtractedJob> extractedJobs, KitchenStationJob job,
                                                           Optional<BorrowedContainer> borrowedContainer) {
        List<ExtractedJob> rollbackJobs = new ArrayList<>(extractedJobs);
        rollbackJobs.add(new ExtractedJob(job, true));
        assertNoAmbiguousInstantOutputRollback(rollbackJobs);

        removeInstantOutputs(job.outputs());
        assertReleasedBorrowedContainer(borrowedContainer);
        rollbackExtractedParts(job.inputs(), job.servingContainer(), job.fuel());
        rollback(extractedJobs);
    }

    private boolean releaseBorrowedContainer(Optional<BorrowedContainer> borrowedContainer) {
        Optional<ItemStack> remainder = borrowedContainer.flatMap(utilityService::release);
        return remainder.isEmpty();
    }

    private void assertReleasedBorrowedContainer(Optional<BorrowedContainer> borrowedContainer) {
        if (!releaseBorrowedContainer(borrowedContainer)) {
            throw new IllegalStateException("Failed to release borrowed utility container");
        }
    }

    private static PlanningBlocker containerBlockerFor(KitchenUtilityRequirement requirement) {
        return switch (requirement) {
            case NONE -> PlanningBlocker.MISSING_RAW_INGREDIENT;
            case WATER -> PlanningBlocker.MISSING_WATER_CONTAINER;
            case ICE -> PlanningBlocker.MISSING_ICE_CONTAINER;
            case SNOWBALL -> PlanningBlocker.MISSING_SNOWBALL_CONTAINER;
        };
    }

    private record ExtractedJob(KitchenStationJob job, boolean insertedInstantOutputs) {
    }

    private record ExtractJobResult(
            Optional<KitchenStationJob> job,
            Optional<BorrowedContainer> borrowedContainer,
            EnumSet<PlanningBlocker> blockers
    ) {
        private ExtractJobResult {
            job = Objects.requireNonNull(job, "job");
            borrowedContainer = Objects.requireNonNull(borrowedContainer, "borrowedContainer");
            blockers = Objects.requireNonNull(blockers, "blockers").clone();
        }

        private static ExtractJobResult blocked(PlanningBlocker blocker) {
            return blocked(EnumSet.of(blocker));
        }

        private static ExtractJobResult blocked(EnumSet<PlanningBlocker> blockers) {
            return new ExtractJobResult(Optional.empty(), Optional.empty(), blockers);
        }

        private static ExtractJobResult success(KitchenStationJob job, Optional<BorrowedContainer> borrowedContainer) {
            return new ExtractJobResult(Optional.of(job), borrowedContainer, EnumSet.noneOf(PlanningBlocker.class));
        }

        @Override
        public EnumSet<PlanningBlocker> blockers() {
            return blockers.clone();
        }
    }
}
