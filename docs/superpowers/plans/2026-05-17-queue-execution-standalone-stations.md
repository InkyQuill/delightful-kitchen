# Queue Execution And Standalone Station Interfaces Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn read-only recipe plans into non-destructive queued station jobs for Auto Cutter, Grill, and Pot Stand without using Cooking for Blockheads execution APIs.

**Architecture:** Add an internal `kitchen.station` package that represents station capacity, queue state, accepted jobs, and execution blockers. Add a `KitchenPlanExecutor` confirmation service that re-scans the room kitchen, validates station capacity and storage routes, extracts ingredients only after validation succeeds, and hands jobs to stations. Existing station block entities keep their menus and autonomous tick loops, but accept `KitchenStationJob` objects directly and route completed outputs through `KitchenInventoryService`.

**Tech Stack:** Java 21, NeoForge 1.21.1, Minecraft `Container`/block entity APIs, existing `KitchenScanner`, `KitchenInventoryService`, `RecipePlanner`, and JUnit 5.

---

## Scope

This plan implements roadmap milestone 2: Queue Execution And Standalone Station Interfaces.

In scope:

- Internal standalone station API.
- Confirmation-time validation and extraction.
- Queue capacity and output-blocked status.
- Auto Cutter, Grill, and Pot Stand accepting queued `PlannedRecipe` steps.
- Output routing through the room-based kitchen inventory service.
- Removing Cooking for Blockheads processor usage from station execution paths.
- Focused unit tests for confirmation, station acceptance, and capacity.
- Basic game tests if the current dev runtime supports them without the known Farmer's Delight dedicated-server crash.

Out of scope:

- Full Chef's Counter recipe book UI and details panel.
- Water Provider and Freezer.
- Food Pouch.
- Public third-party API.
- Removing the `cookingforblockheads` Gradle dependency entirely. Keep it until all remaining compatibility classes are retired by a dedicated dependency-removal milestone.

---

## File Structure

Create:

- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/station/KitchenStation.java`
  - Common internal station interface.
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/station/KitchenStationJob.java`
  - Immutable queued job payload built from one planned recipe step and already-extracted inputs.
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/station/KitchenStationStatus.java`
  - Station runtime state: idle, running, queue full, output blocked.
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/station/KitchenStationAcceptResult.java`
  - Acceptance result with blocker mapping.
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/station/KitchenStationEndpoint.java`
  - Block-position plus interface wrapper for scanner-discovered stations.
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/station/KitchenStationService.java`
  - Discovers stations from a `KitchenScanResult` and chooses endpoints for planned steps.
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/execution/KitchenPlanExecutor.java`
  - Confirmation-time validation, extraction, and station enqueue orchestration.
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/execution/KitchenPlanExecutionResult.java`
  - Result for confirmed, stale-kitchen, or blocked execution.
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/execution/KitchenPlanFingerprint.java`
  - Lightweight scan/snapshot fingerprint used to detect kitchen changes between display and confirmation.
- `src/test/java/net/inkyquill/delightfulkitchen/kitchen/execution/KitchenPlanExecutorTest.java`
  - Unit tests for validation before extraction, capacity blockers, and extraction ordering.
- `src/test/java/net/inkyquill/delightfulkitchen/kitchen/station/KitchenStationServiceTest.java`
  - Unit tests for station endpoint selection.

Modify:

- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/recipe/PlanningBlocker.java`
  - Add `OUTPUT_STORAGE_FULL`, `STATION_QUEUE_FULL`, `KITCHEN_CHANGED`.
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/KitchenInventoryService.java`
  - Add exact ingredient extraction helpers and simulated output insertion.
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/KitchenServices.java`
  - Add `stationEndpoints(...)` and output routing helper.
- `src/main/java/net/inkyquill/delightfulkitchen/block/entity/AutoCutterBlockEntity.java`
  - Implement `KitchenStation`; accept cutting jobs; hold output if blocked; remove CfB processor field.
- `src/main/java/net/inkyquill/delightfulkitchen/block/entity/GrillBlockEntity.java`
  - Implement `KitchenStation`; accept grill jobs; consume queued fuel from job payload; hold output if blocked; remove CfB processor field.
- `src/main/java/net/inkyquill/delightfulkitchen/block/entity/PotStandBlockEntity.java`
  - Implement `KitchenStation`; accept cooking-pot jobs; consume queued fuel/container from job payload; hold output if blocked; remove CfB processor and CfB container pull path.
- `src/main/java/net/inkyquill/delightfulkitchen/compat/FarmersDelightCompat.java`
  - Stop registering Cooking for Blockheads `KitchenItemProcessor` capabilities for Auto Cutter, Grill, and Pot Stand.
- `src/main/java/net/inkyquill/delightfulkitchen/util/InventoryHelper.java`
  - Either remove CfB BFS routing helpers if unused, or leave only generic `insertIntoContainer` if station menus still need it.
- `src/main/java/net/inkyquill/delightfulkitchen/menu/ChefCounterMenu.java`
  - Add server-side confirmation hook that calls `KitchenPlanExecutor` for a selected `RecipePlan`. The full recipe browser is not part of this milestone.
- `src/main/resources/assets/delightful_kitchen/lang/en_us.json`
  - Add player-readable blocker/status strings for queue full, output full, and kitchen changed.
- `docs/ROADMAP.md`
  - Mark milestone 2 complete after implementation and verification. This docs update must be the last implementation task.

---

### Task 1: Station API And Blocker Vocabulary

**Files:**

- Create: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/station/KitchenStation.java`
- Create: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/station/KitchenStationJob.java`
- Create: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/station/KitchenStationStatus.java`
- Create: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/station/KitchenStationAcceptResult.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/recipe/PlanningBlocker.java`

- [ ] **Step 1: Extend `PlanningBlocker`**

Replace the enum with:

```java
package net.inkyquill.delightfulkitchen.kitchen.recipe;

public enum PlanningBlocker {
    MISSING_RAW_INGREDIENT,
    MISSING_INTERMEDIATE_RECIPE,
    MISSING_STATION,
    MISSING_FUEL,
    MISSING_CONTAINER,
    OUTPUT_STORAGE_FULL,
    STATION_QUEUE_FULL,
    KITCHEN_CHANGED
}
```

- [ ] **Step 2: Create `KitchenStationStatus`**

```java
package net.inkyquill.delightfulkitchen.kitchen.station;

public enum KitchenStationStatus {
    IDLE,
    RUNNING,
    QUEUE_FULL,
    OUTPUT_BLOCKED
}
```

- [ ] **Step 3: Create `KitchenStationAcceptResult`**

```java
package net.inkyquill.delightfulkitchen.kitchen.station;

import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;

import java.util.EnumSet;
import java.util.Objects;

public record KitchenStationAcceptResult(boolean accepted, EnumSet<PlanningBlocker> blockers) {
    public KitchenStationAcceptResult {
        blockers = Objects.requireNonNull(blockers, "blockers").clone();
        if (accepted && !blockers.isEmpty()) {
            throw new IllegalArgumentException("accepted station result cannot contain blockers");
        }
    }

    public static KitchenStationAcceptResult accepted() {
        return new KitchenStationAcceptResult(true, EnumSet.noneOf(PlanningBlocker.class));
    }

    public static KitchenStationAcceptResult blocked(PlanningBlocker blocker) {
        return new KitchenStationAcceptResult(false, EnumSet.of(blocker));
    }

    @Override
    public EnumSet<PlanningBlocker> blockers() {
        return blockers.clone();
    }
}
```

- [ ] **Step 4: Create `KitchenStationJob`**

```java
package net.inkyquill.delightfulkitchen.kitchen.station;

import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record KitchenStationJob(
        ResourceLocation recipeId,
        KitchenStationType stationType,
        List<ItemStack> inputs,
        List<ItemStack> outputs,
        Optional<ItemStack> servingContainer,
        Optional<ItemStack> fuel,
        int processingTime
) {
    public KitchenStationJob {
        recipeId = Objects.requireNonNull(recipeId, "recipeId");
        stationType = Objects.requireNonNull(stationType, "stationType");
        inputs = copyStacks(inputs, "inputs");
        outputs = copyStacks(outputs, "outputs");
        servingContainer = Objects.requireNonNull(servingContainer, "servingContainer").map(ItemStack::copy);
        fuel = Objects.requireNonNull(fuel, "fuel").map(ItemStack::copy);
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("station job must have at least one output");
        }
        if (processingTime < 0) {
            throw new IllegalArgumentException("processingTime cannot be negative");
        }
    }

    @Override
    public List<ItemStack> inputs() {
        return inputs.stream().map(ItemStack::copy).toList();
    }

    @Override
    public List<ItemStack> outputs() {
        return outputs.stream().map(ItemStack::copy).toList();
    }

    @Override
    public Optional<ItemStack> servingContainer() {
        return servingContainer.map(ItemStack::copy);
    }

    @Override
    public Optional<ItemStack> fuel() {
        return fuel.map(ItemStack::copy);
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks, String name) {
        return Objects.requireNonNull(stacks, name).stream()
                .map(stack -> Objects.requireNonNull(stack, name + " cannot contain null"))
                .filter(stack -> !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
    }
}
```

- [ ] **Step 5: Create `KitchenStation`**

```java
package net.inkyquill.delightfulkitchen.kitchen.station;

import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;
import net.minecraft.core.BlockPos;

public interface KitchenStation {
    KitchenStationType stationType();

    int queueCapacity();

    int queuedJobCount();

    KitchenStationStatus stationStatus();

    default boolean hasQueueSpace() {
        return queuedJobCount() < queueCapacity();
    }

    KitchenStationAcceptResult canAccept(KitchenStationJob job);

    KitchenStationAcceptResult accept(KitchenStationJob job);

    BlockPos stationPos();
}
```

- [ ] **Step 6: Run compile and commit**

Run: `./gradlew compileJava`

Expected: compile succeeds or fails only because no station block entity implements the new interface yet. If it fails for unrelated reasons, stop and fix before committing.

Commit:

```bash
git add src/main/java/net/inkyquill/delightfulkitchen/kitchen/recipe/PlanningBlocker.java \
  src/main/java/net/inkyquill/delightfulkitchen/kitchen/station
git commit -m "feat: add standalone kitchen station API"
```

---

### Task 2: Station Discovery Service

**Files:**

- Create: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/station/KitchenStationEndpoint.java`
- Create: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/station/KitchenStationService.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/KitchenServices.java`
- Test: `src/test/java/net/inkyquill/delightfulkitchen/kitchen/station/KitchenStationServiceTest.java`

- [ ] **Step 1: Create endpoint record**

```java
package net.inkyquill.delightfulkitchen.kitchen.station;

import net.minecraft.core.BlockPos;

import java.util.Objects;

public record KitchenStationEndpoint(BlockPos pos, KitchenStation station) {
    public KitchenStationEndpoint {
        pos = Objects.requireNonNull(pos, "pos").immutable();
        station = Objects.requireNonNull(station, "station");
    }
}
```

- [ ] **Step 2: Create `KitchenStationService`**

```java
package net.inkyquill.delightfulkitchen.kitchen.station;

import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class KitchenStationService {
    private final List<KitchenStationEndpoint> endpoints;

    public KitchenStationService(List<KitchenStationEndpoint> endpoints) {
        this.endpoints = List.copyOf(Objects.requireNonNull(endpoints, "endpoints"));
    }

    public List<KitchenStationEndpoint> endpoints() {
        return endpoints;
    }

    public Optional<KitchenStationEndpoint> firstAccepting(KitchenStationJob job) {
        return endpoints.stream()
                .filter(endpoint -> endpoint.station().stationType() == job.stationType())
                .filter(endpoint -> endpoint.station().canAccept(job).accepted())
                .min(Comparator
                        .comparingInt((KitchenStationEndpoint endpoint) -> endpoint.station().queuedJobCount())
                        .thenComparing(endpoint -> endpoint.pos().asLong()));
    }

    public boolean hasStation(KitchenStationType type) {
        return endpoints.stream().anyMatch(endpoint -> endpoint.station().stationType() == type);
    }
}
```

- [ ] **Step 3: Add scanner bridge in `KitchenServices`**

Add imports:

```java
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStation;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStationEndpoint;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStationService;
```

Add this method:

```java
public static KitchenStationService stationService(Level level, KitchenScanResult scan) {
    List<KitchenStationEndpoint> endpoints = new ArrayList<>();
    for (BlockPos member : scan.memberBlocks()) {
        BlockEntity blockEntity = level.getBlockEntity(member);
        if (blockEntity instanceof KitchenStation station) {
            endpoints.add(new KitchenStationEndpoint(member, station));
        }
    }
    return new KitchenStationService(endpoints);
}
```

- [ ] **Step 4: Write service test**

Create `src/test/java/net/inkyquill/delightfulkitchen/kitchen/station/KitchenStationServiceTest.java`:

```java
package net.inkyquill.delightfulkitchen.kitchen.station;

import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitchenStationServiceTest {
    @Test
    void choosesLeastQueuedAcceptingStation() {
        FakeStation busy = new FakeStation(KitchenStationType.GRILL, 4, 3, true);
        FakeStation open = new FakeStation(KitchenStationType.GRILL, 4, 1, true);
        KitchenStationService service = new KitchenStationService(List.of(
                new KitchenStationEndpoint(new BlockPos(10, 64, 10), busy),
                new KitchenStationEndpoint(new BlockPos(5, 64, 5), open)
        ));

        Optional<KitchenStationEndpoint> endpoint = service.firstAccepting(job(KitchenStationType.GRILL));

        assertTrue(endpoint.isPresent());
        assertEquals(new BlockPos(5, 64, 5), endpoint.orElseThrow().pos());
    }

    @Test
    void skipsStationsThatCannotAccept() {
        KitchenStationService service = new KitchenStationService(List.of(
                new KitchenStationEndpoint(new BlockPos(1, 64, 1), new FakeStation(KitchenStationType.GRILL, 1, 1, false))
        ));

        assertTrue(service.firstAccepting(job(KitchenStationType.GRILL)).isEmpty());
    }

    private static KitchenStationJob job(KitchenStationType type) {
        return new KitchenStationJob(
                ResourceLocation.fromNamespaceAndPath("delightful_kitchen", "test"),
                type,
                List.of(),
                List.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKED_BEEF)),
                Optional.empty(),
                Optional.empty(),
                20
        );
    }

    private record FakeStation(KitchenStationType type, int capacity, int queued, boolean accepts)
            implements KitchenStation {
        @Override public KitchenStationType stationType() { return type; }
        @Override public int queueCapacity() { return capacity; }
        @Override public int queuedJobCount() { return queued; }
        @Override public KitchenStationStatus stationStatus() { return queued >= capacity ? KitchenStationStatus.QUEUE_FULL : KitchenStationStatus.IDLE; }
        @Override public KitchenStationAcceptResult canAccept(KitchenStationJob job) { return accepts && hasQueueSpace() ? KitchenStationAcceptResult.acceptedResult() : KitchenStationAcceptResult.blocked(net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker.STATION_QUEUE_FULL); }
        @Override public KitchenStationAcceptResult accept(KitchenStationJob job) { return canAccept(job); }
        @Override public BlockPos stationPos() { return BlockPos.ZERO; }
    }
}
```

- [ ] **Step 5: Run targeted test and commit**

Run: `./gradlew test --tests net.inkyquill.delightfulkitchen.kitchen.station.KitchenStationServiceTest`

Expected: PASS.

Commit:

```bash
git add src/main/java/net/inkyquill/delightfulkitchen/kitchen/KitchenServices.java \
  src/main/java/net/inkyquill/delightfulkitchen/kitchen/station/KitchenStationEndpoint.java \
  src/main/java/net/inkyquill/delightfulkitchen/kitchen/station/KitchenStationService.java \
  src/test/java/net/inkyquill/delightfulkitchen/kitchen/station/KitchenStationServiceTest.java
git commit -m "feat: discover standalone kitchen stations"
```

---

### Task 3: Inventory Extraction And Output Simulation Helpers

**Files:**

- Modify: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/KitchenInventoryService.java`
- Test: `src/test/java/net/inkyquill/delightfulkitchen/kitchen/KitchenInventoryServiceTest.java`

- [ ] **Step 1: Add helper methods**

Add imports:

```java
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlannedIngredient;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Optional;
```

Add methods before `visibleStacks`:

```java
public Optional<List<ItemStack>> extractIngredient(PlannedIngredient ingredient, boolean simulate) {
    List<ItemStack> extracted = new ArrayList<>();
    int remaining = ingredient.count();
    Ingredient matcher = ingredient.ingredient();

    for (KitchenInventoryEndpoint endpoint : endpoints) {
        for (int slot = 0; slot < endpoint.size() && remaining > 0; slot++) {
            ItemStack stack = endpoint.get(slot);
            if (stack.isEmpty() || !matcher.test(stack)) {
                continue;
            }

            int amount = Math.min(remaining, stack.getCount());
            ItemStack taken = endpoint.extract(slot, amount, simulate);
            if (!taken.isEmpty()) {
                extracted.add(taken);
                remaining -= taken.getCount();
            }
        }
    }

    if (remaining > 0) {
        if (!simulate) {
            for (ItemStack stack : extracted) {
                insert(stack, false);
            }
        }
        return Optional.empty();
    }
    return Optional.of(List.copyOf(extracted));
}

public boolean canInsertAll(List<ItemStack> stacks) {
    for (ItemStack stack : stacks) {
        if (!insert(stack, true).isEmpty()) {
            return false;
        }
    }
    return true;
}

public boolean insertAll(List<ItemStack> stacks) {
    for (ItemStack stack : stacks) {
        if (!insert(stack, false).isEmpty()) {
            return false;
        }
    }
    return true;
}
```

- [ ] **Step 2: Write extraction tests**

Create `src/test/java/net/inkyquill/delightfulkitchen/kitchen/KitchenInventoryServiceTest.java`:

```java
package net.inkyquill.delightfulkitchen.kitchen;

import net.inkyquill.delightfulkitchen.kitchen.recipe.PlannedIngredient;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitchenInventoryServiceTest {
    @Test
    void extractsIngredientAcrossEndpoints() {
        SimpleContainer first = new SimpleContainer(new ItemStack(Items.WHEAT, 2));
        SimpleContainer second = new SimpleContainer(new ItemStack(Items.WHEAT, 2));
        KitchenInventoryService service = new KitchenInventoryService(List.of(
                new KitchenInventoryEndpoint(net.minecraft.core.BlockPos.ZERO, first),
                new KitchenInventoryEndpoint(net.minecraft.core.BlockPos.ZERO.above(), second)
        ));

        Optional<List<ItemStack>> extracted = service.extractIngredient(
                new PlannedIngredient(Ingredient.of(Items.WHEAT), 3), false);

        assertTrue(extracted.isPresent());
        assertEquals(3, extracted.orElseThrow().stream().mapToInt(ItemStack::getCount).sum());
        assertEquals(0, first.getItem(0).getCount());
        assertEquals(1, second.getItem(0).getCount());
    }

    @Test
    void failedExtractionDoesNotConsumeItems() {
        SimpleContainer container = new SimpleContainer(new ItemStack(Items.WHEAT, 1));
        KitchenInventoryService service = new KitchenInventoryService(List.of(
                new KitchenInventoryEndpoint(net.minecraft.core.BlockPos.ZERO, container)
        ));

        Optional<List<ItemStack>> extracted = service.extractIngredient(
                new PlannedIngredient(Ingredient.of(Items.WHEAT), 2), false);

        assertTrue(extracted.isEmpty());
        assertEquals(1, container.getItem(0).getCount());
    }

    @Test
    void canInsertAllSimulatesWithoutMutating() {
        SimpleContainer container = new SimpleContainer(1);
        KitchenInventoryService service = new KitchenInventoryService(List.of(
                new KitchenInventoryEndpoint(net.minecraft.core.BlockPos.ZERO, container)
        ));

        assertTrue(service.canInsertAll(List.of(new ItemStack(Items.BREAD))));
        assertTrue(container.getItem(0).isEmpty());
    }
}
```

- [ ] **Step 3: Run targeted test and commit**

Run: `./gradlew test --tests net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryServiceTest`

Expected: PASS.

Commit:

```bash
git add src/main/java/net/inkyquill/delightfulkitchen/kitchen/KitchenInventoryService.java \
  src/test/java/net/inkyquill/delightfulkitchen/kitchen/KitchenInventoryServiceTest.java
git commit -m "feat: add kitchen inventory reservation helpers"
```

---

### Task 4: Plan Confirmation And Job Building

**Files:**

- Create: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/execution/KitchenPlanExecutionResult.java`
- Create: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/execution/KitchenPlanFingerprint.java`
- Create: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/execution/KitchenPlanExecutor.java`
- Test: `src/test/java/net/inkyquill/delightfulkitchen/kitchen/execution/KitchenPlanExecutorTest.java`

- [ ] **Step 1: Create execution result**

```java
package net.inkyquill.delightfulkitchen.kitchen.execution;

import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;

import java.util.EnumSet;
import java.util.Objects;

public record KitchenPlanExecutionResult(boolean queued, EnumSet<PlanningBlocker> blockers) {
    public KitchenPlanExecutionResult {
        blockers = Objects.requireNonNull(blockers, "blockers").clone();
    }

    public static KitchenPlanExecutionResult queued() {
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
```

- [ ] **Step 2: Create kitchen fingerprint**

```java
package net.inkyquill.delightfulkitchen.kitchen.execution;

import net.inkyquill.delightfulkitchen.kitchen.KitchenScanResult;
import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenInventorySnapshot;

import java.util.Objects;

public record KitchenPlanFingerprint(int floorCount, int memberCount, int stackCount, int totalItemCount) {
    public static KitchenPlanFingerprint of(KitchenScanResult scan, KitchenInventorySnapshot snapshot) {
        int stackCount = snapshot.stacks().size();
        int totalItemCount = snapshot.stacks().stream().mapToInt(stack -> stack.getCount()).sum();
        return new KitchenPlanFingerprint(scan.floorCount(), scan.memberCount(), stackCount, totalItemCount);
    }

    public boolean matches(KitchenScanResult scan, KitchenInventorySnapshot snapshot) {
        return equals(of(Objects.requireNonNull(scan, "scan"), Objects.requireNonNull(snapshot, "snapshot")));
    }
}
```

- [ ] **Step 3: Create executor**

Create `KitchenPlanExecutor` with this public shape:

```java
package net.inkyquill.delightfulkitchen.kitchen.execution;

import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryService;
import net.inkyquill.delightfulkitchen.kitchen.KitchenScanResult;
import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlannedIngredient;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlannedRecipe;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;
import net.inkyquill.delightfulkitchen.kitchen.recipe.RecipePlan;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStationJob;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStationService;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class KitchenPlanExecutor {
    private final KitchenInventoryService inventory;
    private final KitchenStationService stations;
    private final KitchenScanResult scan;

    public KitchenPlanExecutor(KitchenInventoryService inventory, KitchenStationService stations, KitchenScanResult scan) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.stations = Objects.requireNonNull(stations, "stations");
        this.scan = Objects.requireNonNull(scan, "scan");
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

        List<KitchenStationJob> extractedJobs = new ArrayList<>();
        for (PlannedRecipe step : plan.steps()) {
            Optional<KitchenStationJob> extracted = extractJob(step);
            if (extracted.isEmpty()) {
                rollback(extractedJobs);
                return KitchenPlanExecutionResult.blocked(PlanningBlocker.MISSING_RAW_INGREDIENT);
            }
            KitchenStationJob job = extracted.orElseThrow();
            if (job.stationType() == KitchenStationType.CRAFTING) {
                if (!inventory.insertAll(job.outputs())) {
                    rollback(extractedJobs);
                    rollback(List.of(job));
                    return KitchenPlanExecutionResult.blocked(PlanningBlocker.OUTPUT_STORAGE_FULL);
                }
                extractedJobs.add(job);
                continue;
            }
            extractedJobs.add(job);
        }

        List<KitchenStationJob> queuedJobs = new ArrayList<>();
        for (KitchenStationJob job : extractedJobs) {
            if (job.stationType() == KitchenStationType.CRAFTING) {
                continue;
            }

            var endpoint = stations.firstAccepting(job);
            if (endpoint.isEmpty()) {
                rollback(extractedJobs);
                return KitchenPlanExecutionResult.blocked(PlanningBlocker.STATION_QUEUE_FULL);
            }

            var accepted = endpoint.orElseThrow().station().accept(job);
            if (!accepted.accepted()) {
                rollback(extractedJobs);
                return KitchenPlanExecutionResult.blocked(accepted.blockers());
            }
            queuedJobs.add(job);
        }

        return KitchenPlanExecutionResult.queued();
    }

    private EnumSet<PlanningBlocker> validatePlan(RecipePlan plan) {
        EnumSet<PlanningBlocker> blockers = EnumSet.noneOf(PlanningBlocker.class);
        for (PlannedRecipe step : plan.steps()) {
            KitchenStationJob job = buildJobWithoutExtractedInputs(step);
            if (job.stationType() != KitchenStationType.CRAFTING && stations.firstAccepting(job).isEmpty()) {
                blockers.add(PlanningBlocker.STATION_QUEUE_FULL);
            }
            if (!inventory.canInsertAll(job.outputs())) {
                blockers.add(PlanningBlocker.OUTPUT_STORAGE_FULL);
            }
        }
        return blockers;
    }

    private KitchenStationJob buildJobWithoutExtractedInputs(PlannedRecipe step) {
        return new KitchenStationJob(step.id(), step.stationType(), List.of(), step.outputs(),
                step.servingContainer(), Optional.empty(), step.processingTime());
    }

    private Optional<KitchenStationJob> extractJob(PlannedRecipe step) {
        List<ItemStack> inputs = new ArrayList<>();
        for (PlannedIngredient ingredient : step.ingredients()) {
            Optional<List<ItemStack>> extracted = inventory.extractIngredient(ingredient, false);
            if (extracted.isEmpty()) {
                return Optional.empty();
            }
            inputs.addAll(extracted.orElseThrow());
        }

        Optional<ItemStack> container = Optional.empty();
        if (step.servingContainer().isPresent()) {
            ItemStack required = step.servingContainer().orElseThrow();
            ItemStack extracted = inventory.extract(required, required.getCount(), false);
            if (extracted.getCount() < required.getCount()) {
                return Optional.empty();
            }
            container = Optional.of(extracted);
        }

        Optional<ItemStack> fuel = Optional.empty();
        if (step.fuel().isPresent()) {
            Optional<List<ItemStack>> extracted = inventory.extractIngredient(step.fuel().orElseThrow(), false);
            if (extracted.isEmpty()) {
                return Optional.empty();
            }
            fuel = extracted.orElseThrow().stream().findFirst().map(ItemStack::copy);
        }

        return Optional.of(new KitchenStationJob(step.id(), step.stationType(), inputs, step.outputs(),
                container, fuel, step.processingTime()));
    }

    private void rollback(List<KitchenStationJob> jobs) {
        for (KitchenStationJob job : jobs) {
            inventory.insertAll(job.inputs());
            job.servingContainer().ifPresent(stack -> inventory.insert(stack, false));
            job.fuel().ifPresent(stack -> inventory.insert(stack, false));
            if (job.stationType() == KitchenStationType.CRAFTING) {
                for (ItemStack output : job.outputs()) {
                    inventory.extract(output, output.getCount(), false);
                }
            }
        }
    }
}
```

Crafting steps are instant executor steps, not station jobs. They consume extracted inputs and immediately insert their outputs into kitchen storage so later station steps in the same plan can extract those intermediates. Keep the executor small; do not add UI behavior here.

- [ ] **Step 4: Write executor tests**

Create tests with fake `KitchenStation` and `SimpleContainer` endpoints. Required test names:

```java
@Test
void doesNotExtractWhenStationQueueIsFull()
```

```java
@Test
void returnsKitchenChangedWhenFingerprintDiffers()
```

```java
@Test
void queuesStepsAfterExtractingInputs()
```

The assertions must verify:

- Queue-full validation leaves the source `SimpleContainer` unchanged.
- Kitchen-changed validation leaves the source `SimpleContainer` unchanged.
- Successful confirmation decreases source ingredients and increments fake station accepted job count.
- A plan with a `KitchenStationType.CRAFTING` intermediate followed by a station step inserts the intermediate before extracting the station input.

- [ ] **Step 5: Run tests and commit**

Run:

```bash
./gradlew test --tests net.inkyquill.delightfulkitchen.kitchen.execution.KitchenPlanExecutorTest
```

Expected: PASS.

Commit:

```bash
git add src/main/java/net/inkyquill/delightfulkitchen/kitchen/execution \
  src/test/java/net/inkyquill/delightfulkitchen/kitchen/execution/KitchenPlanExecutorTest.java
git commit -m "feat: add kitchen plan confirmation executor"
```

---

### Task 5: Implement Auto Cutter Standalone Queue

**Files:**

- Modify: `src/main/java/net/inkyquill/delightfulkitchen/block/entity/AutoCutterBlockEntity.java`
- Test: `src/test/java/net/inkyquill/delightfulkitchen/kitchen/station/AutoCutterStationTest.java`

- [ ] **Step 1: Remove Cooking for Blockheads processor field**

Remove imports:

```java
import net.blay09.mods.cookingforblockheads.api.IngredientToken;
import net.blay09.mods.cookingforblockheads.api.KitchenItemProcessor;
import net.blay09.mods.cookingforblockheads.api.KitchenOperation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
```

Remove the `processor` field and `getKitchenItemProcessor()` method.

- [ ] **Step 2: Implement `KitchenStation`**

Add imports:

```java
import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStation;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStationAcceptResult;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStationJob;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStationStatus;
```

Change class declaration:

```java
public class AutoCutterBlockEntity extends BlockEntity
        implements Container, MenuProvider, Nameable, KitchenStation {
```

Add fields:

```java
@Nullable private KitchenStationJob activeJob = null;
private boolean outputBlocked = false;
```

Add methods:

```java
@Override
public KitchenStationType stationType() {
    return KitchenStationType.CUTTING_BOARD;
}

@Override
public int queueCapacity() {
    return 1;
}

@Override
public int queuedJobCount() {
    return activeJob == null ? 0 : 1;
}

@Override
public KitchenStationStatus stationStatus() {
    if (outputBlocked) return KitchenStationStatus.OUTPUT_BLOCKED;
    if (activeJob != null || activeTimer > 0) return KitchenStationStatus.RUNNING;
    return KitchenStationStatus.IDLE;
}

@Override
public KitchenStationAcceptResult canAccept(KitchenStationJob job) {
    if (job.stationType() != KitchenStationType.CUTTING_BOARD) {
        return KitchenStationAcceptResult.blocked(PlanningBlocker.MISSING_STATION);
    }
    if (!hasQueueSpace()) {
        return KitchenStationAcceptResult.blocked(PlanningBlocker.STATION_QUEUE_FULL);
    }
    if (outputBlocked) {
        return KitchenStationAcceptResult.blocked(PlanningBlocker.OUTPUT_STORAGE_FULL);
    }
    return KitchenStationAcceptResult.acceptedResult();
}

@Override
public KitchenStationAcceptResult accept(KitchenStationJob job) {
    KitchenStationAcceptResult result = canAccept(job);
    if (!result.accepted()) {
        return result;
    }
    activeJob = job;
    pendingResults.clear();
    pendingResults.addAll(job.outputs());
    activeTimer = Math.max(1, job.processingTime());
    setChanged();
    return KitchenStationAcceptResult.acceptedResult();
}

@Override
public BlockPos stationPos() {
    return worldPosition.immutable();
}
```

- [ ] **Step 3: Change tick completion to route job outputs**

In `serverTick`, keep `tryStartCutting` for manual input-slot behavior for now, but make `finishCutting` handle `activeJob` first:

```java
if (activeJob != null) {
    if (be.activeTimer > 0) {
        be.activeTimer--;
        if (be.activeTimer == 0) {
            be.finishQueuedJob(level, pos, state);
        }
        be.setChanged();
    }
    return;
}
```

Add:

```java
private void finishQueuedJob(Level level, BlockPos pos, BlockState state) {
    outputBlocked = false;
    for (ItemStack output : pendingResults) {
        ItemStack remainder = output.copy();
        if (!routeOutput(level, remainder)) {
            outputBlocked = true;
            placeInOutputSlots(remainder);
        }
    }
    if (!outputBlocked) {
        activeJob = null;
        pendingResults.clear();
    }
    level.setBlock(pos, state.setValue(AutoCutterBlock.ACTIVE, false), 3);
    setChanged();
}

private boolean routeOutput(Level level, ItemStack stack) {
    var scan = net.inkyquill.delightfulkitchen.kitchen.KitchenScanner.scan(level, worldPosition);
    var endpoints = net.inkyquill.delightfulkitchen.kitchen.KitchenServices.inventoryEndpoints(level, scan, worldPosition);
    var inventory = new net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryService(endpoints);
    return inventory.insert(stack, false).isEmpty();
}

private void placeInOutputSlots(ItemStack result) {
    for (int i = SLOT_OUTPUT_START; i <= SLOT_OUTPUT_END && !result.isEmpty(); i++) {
        ItemStack existing = items.get(i);
        if (existing.isEmpty()) {
            items.set(i, result.split(result.getCount()));
        } else if (ItemStack.isSameItemSameComponents(existing, result)) {
            int transfer = Math.min(result.getCount(), existing.getMaxStackSize() - existing.getCount());
            if (transfer > 0) {
                existing.grow(transfer);
                result.shrink(transfer);
            }
        }
    }
}
```

Do not drop queued outputs on the ground.

- [ ] **Step 4: Persist active job minimal state**

Persist `activeJob` outputs and `outputBlocked`. Use the existing `PendingResults` tag for outputs and add:

```java
tag.putBoolean("OutputBlocked", outputBlocked);
```

Load:

```java
outputBlocked = tag.getBoolean("OutputBlocked");
```

Do not persist input stacks from `activeJob`; extracted inputs are already consumed at confirmation. Persisting outputs and timer is enough to avoid item loss.

- [ ] **Step 5: Run compile and commit**

Run: `./gradlew compileJava`

Expected: PASS.

Commit:

```bash
git add src/main/java/net/inkyquill/delightfulkitchen/block/entity/AutoCutterBlockEntity.java
git commit -m "feat: queue auto cutter station jobs"
```

---

### Task 6: Implement Grill Standalone Queue

**Files:**

- Modify: `src/main/java/net/inkyquill/delightfulkitchen/block/entity/GrillBlockEntity.java`

- [ ] **Step 1: Remove Cooking for Blockheads processor field**

Remove CfB imports and the `processor` field plus `getKitchenItemProcessor()` method from `GrillBlockEntity`.

- [ ] **Step 2: Implement `KitchenStation`**

Class declaration:

```java
public class GrillBlockEntity extends BlockEntity
        implements Container, MenuProvider, Nameable, KitchenStation {
```

Add queued job storage:

```java
private final KitchenStationJob[] queuedJobs = new KitchenStationJob[GRILL_SLOTS];
private final boolean[] outputBlocked = new boolean[GRILL_SLOTS];
```

Implement:

```java
@Override public KitchenStationType stationType() { return KitchenStationType.GRILL; }
@Override public int queueCapacity() { return GRILL_SLOTS; }
@Override public int queuedJobCount() { return (int) java.util.Arrays.stream(queuedJobs).filter(java.util.Objects::nonNull).count(); }
@Override public BlockPos stationPos() { return worldPosition.immutable(); }
```

`stationStatus()` returns `OUTPUT_BLOCKED` if any output is blocked, `QUEUE_FULL` if no queue space, `RUNNING` if any queued job or pending output exists, otherwise `IDLE`.

`canAccept(job)` blocks when:

- wrong station type -> `MISSING_STATION`
- no queue space -> `STATION_QUEUE_FULL`
- any output blocked -> `OUTPUT_STORAGE_FULL`

`accept(job)` stores the job in the first empty `queuedJobs` slot, copies its output into `pending[slot]`, sets `cookTimes[slot] = 0`, and sets `cookTotals[slot] = Math.max(1, job.processingTime())`.

- [ ] **Step 3: Consume queued fuel inside job acceptance**

The executor extracts fuel and passes it in the job. Add job fuel to the Grill fuel slot during `accept`:

```java
job.fuel().ifPresent(fuel -> {
    ItemStack remainder = fuel.copy();
    if (items.get(SLOT_FUEL).isEmpty()) {
        items.set(SLOT_FUEL, remainder);
    } else if (ItemStack.isSameItemSameComponents(items.get(SLOT_FUEL), remainder)) {
        items.get(SLOT_FUEL).grow(remainder.getCount());
    }
});
```

- [ ] **Step 4: Route output without drops**

Update `finishSlot` so queued jobs route through `KitchenInventoryService` and keep `pending[i]` when output cannot move. Never drop queued output.

When output fully routes:

```java
queuedJobs[i] = null;
pending[i] = null;
outputBlocked[i] = false;
cookTimes[i] = 0;
```

When output cannot route:

```java
outputBlocked[i] = true;
return;
```

- [ ] **Step 5: Persist queue status**

Persist `outputBlocked` as an int array and infer queued jobs from existing `PendingN` tags on load. If `PendingN` exists, set a lightweight queued marker by leaving `pending[N]` non-null and `outputBlocked[N]` loaded. Do not try to reconstruct original inputs.

- [ ] **Step 6: Run compile and commit**

Run: `./gradlew compileJava`

Expected: PASS.

Commit:

```bash
git add src/main/java/net/inkyquill/delightfulkitchen/block/entity/GrillBlockEntity.java
git commit -m "feat: queue grill station jobs"
```

---

### Task 7: Implement Pot Stand Standalone Queue

**Files:**

- Modify: `src/main/java/net/inkyquill/delightfulkitchen/block/entity/PotStandBlockEntity.java`

- [ ] **Step 1: Remove Cooking for Blockheads execution code**

Remove imports:

```java
import net.blay09.mods.cookingforblockheads.api.CacheHint;
import net.blay09.mods.cookingforblockheads.api.CookingForBlockheadsAPI;
import net.blay09.mods.cookingforblockheads.api.IngredientToken;
import net.blay09.mods.cookingforblockheads.api.Kitchen;
import net.blay09.mods.cookingforblockheads.api.KitchenItemProcessor;
import net.blay09.mods.cookingforblockheads.api.KitchenItemProvider;
import net.blay09.mods.cookingforblockheads.api.KitchenOperation;
```

Remove `processor`, `getKitchenItemProcessor()`, and `tryPullContainer(...)`.

- [ ] **Step 2: Implement `KitchenStation`**

Class declaration:

```java
public class PotStandBlockEntity extends BlockEntity
        implements Container, MenuProvider, Nameable, KitchenStation {
```

Add fields:

```java
@Nullable private KitchenStationJob activeJob = null;
private boolean outputBlocked = false;
```

Implement station methods with `KitchenStationType.COOKING_POT`, `queueCapacity() == 1`, queue full when `activeJob != null || pendingOutput != null`, and output blocked when `outputBlocked`.

- [ ] **Step 3: Accept queued inputs, container, and fuel**

In `accept(job)`:

- Put job inputs into slots `0..5` for menu visibility.
- Put `job.servingContainer()` into `SLOT_CONTAINER` when present.
- Put `job.fuel()` into `SLOT_FUEL` when present.
- Set `pendingOutput` to `job.outputs().getFirst().copy()`.
- Set `cookTime = 0`.
- Set `cookTimeTotal = Math.max(1, job.processingTime())`.
- Set `activeJob = job`.

Do not run recipe matching during queued jobs; the executor already validated and extracted inputs.

- [ ] **Step 4: Route output without drops**

In `finishCooking`, try kitchen inventory insertion first. If insertion fails and `SLOT_OUTPUT` can accept the stack, place it there. If both fail, keep `pendingOutput`, set `outputBlocked = true`, and do not reset `activeJob`.

Only clear `activeJob`, `pendingOutput`, and `cookTime` after output has been fully stored.

- [ ] **Step 5: Keep manual slot behavior separate**

Existing manual slot cooking can remain for player-placed inputs, but it must not call any Cooking for Blockheads API. Container pulling should use only the station's own container slot during this milestone. Kitchen-wide container pulling happens through `KitchenPlanExecutor`.

- [ ] **Step 6: Persist output blocked**

Save:

```java
tag.putBoolean("OutputBlocked", outputBlocked);
```

Load:

```java
outputBlocked = tag.getBoolean("OutputBlocked");
```

- [ ] **Step 7: Run compile and commit**

Run: `./gradlew compileJava`

Expected: PASS.

Commit:

```bash
git add src/main/java/net/inkyquill/delightfulkitchen/block/entity/PotStandBlockEntity.java
git commit -m "feat: queue pot stand station jobs"
```

---

### Task 8: Retire Cooking For Blockheads Station Capabilities

**Files:**

- Modify: `src/main/java/net/inkyquill/delightfulkitchen/compat/FarmersDelightCompat.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/util/InventoryHelper.java`

- [ ] **Step 1: Remove station processor capability registrations**

In `FarmersDelightCompat.registerCapabilities`, remove registrations that expose `KitchenItemProcessor` from:

- `ModBlockEntities.AUTO_CUTTER`
- `ModBlockEntities.GRILL`
- `ModBlockEntities.POT_STAND`

Keep item-provider capabilities only if Cabinet of Holding or other legacy compatibility still needs them for current builds.

- [ ] **Step 2: Remove unused processor handler classes only if no callers remain**

Run:

```bash
rg -n 'CookingPotRecipeHandler|CuttingBoardRecipeHandler|GrillRecipeHandler|getKitchenItemProcessor|KitchenItemProcessor' src/main/java
```

If the only matches are unused compatibility classes and imports, delete:

- `src/main/java/net/inkyquill/delightfulkitchen/recipe/CookingPotRecipeHandler.java`
- `src/main/java/net/inkyquill/delightfulkitchen/recipe/CuttingBoardRecipeHandler.java`
- `src/main/java/net/inkyquill/delightfulkitchen/recipe/GrillRecipeHandler.java`

If any provider compatibility still references them, keep them and remove only dead imports.

- [ ] **Step 3: Remove CfB BFS output routing if unused**

Run:

```bash
rg -n 'InventoryHelper\\.pushToKitchen|InventoryHelper\\.pushFuelToKitchen|InventoryHelper\\.pushToNeighbor|InventoryHelper\\.insertIntoContainer' src/main/java
```

If only `insertIntoContainer` is still used, shrink `InventoryHelper` to:

```java
package net.inkyquill.delightfulkitchen.util;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class InventoryHelper {
    private InventoryHelper() {
    }

    public static void insertIntoContainer(Container container, ItemStack stack) {
        int size = container.getContainerSize();
        for (int i = 0; i < size && !stack.isEmpty(); i++) {
            if (!container.canPlaceItem(i, stack)) continue;
            ItemStack existing = container.getItem(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                int transfer = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                if (transfer > 0) {
                    existing.grow(transfer);
                    stack.shrink(transfer);
                    container.setChanged();
                }
            }
        }
        for (int i = 0; i < size && !stack.isEmpty(); i++) {
            if (!container.canPlaceItem(i, stack)) continue;
            if (container.getItem(i).isEmpty()) {
                container.setItem(i, stack.split(stack.getCount()));
                container.setChanged();
                return;
            }
        }
    }
}
```

- [ ] **Step 4: Compile and commit**

Run: `./gradlew compileJava`

Expected: PASS and no station block entity imports `net.blay09.mods.cookingforblockheads.api.KitchenItemProcessor`.

Commit:

```bash
git add src/main/java/net/inkyquill/delightfulkitchen/compat/FarmersDelightCompat.java \
  src/main/java/net/inkyquill/delightfulkitchen/util/InventoryHelper.java \
  src/main/java/net/inkyquill/delightfulkitchen/recipe
git commit -m "refactor: retire cfb station execution hooks"
```

---

### Task 9: Chef Counter Confirmation Hook

**Files:**

- Modify: `src/main/java/net/inkyquill/delightfulkitchen/menu/ChefCounterMenu.java`
- Modify: `src/main/resources/assets/delightful_kitchen/lang/en_us.json`

- [ ] **Step 1: Add blocker/status lang keys**

Add:

```json
"gui.delightful_kitchen.chef_counter.queue_full": "Station queue is full.",
"gui.delightful_kitchen.chef_counter.output_full": "Kitchen output storage is full.",
"gui.delightful_kitchen.chef_counter.kitchen_changed": "Kitchen changed. Reopen the counter and confirm again."
```

Keep JSON commas valid.

- [ ] **Step 2: Add a server-side confirmation method**

Add this method to `ChefCounterMenu`:

```java
public KitchenPlanExecutionResult confirmPlan(RecipePlan plan, KitchenPlanFingerprint fingerprint) {
    return access.evaluate((level, pos) -> {
        KitchenScanResult scan = KitchenScanner.scan(level, pos);
        if (!scan.hasKitchenFloor()) {
            return KitchenPlanExecutionResult.blocked(PlanningBlocker.KITCHEN_CHANGED);
        }
        var endpoints = KitchenServices.inventoryEndpoints(level, scan, pos);
        var inventory = new KitchenInventoryService(endpoints);
        var stations = KitchenServices.stationService(level, scan);
        var executor = new KitchenPlanExecutor(inventory, stations, scan);
        return executor.confirm(plan, fingerprint);
    }).orElseGet(() -> KitchenPlanExecutionResult.blocked(PlanningBlocker.KITCHEN_CHANGED));
}
```

Add imports for `KitchenPlanExecutor`, `KitchenPlanExecutionResult`, `KitchenPlanFingerprint`, and `PlanningBlocker`.

This is intentionally not wired to a full UI button in this milestone.

- [ ] **Step 3: Compile and commit**

Run: `./gradlew compileJava`

Expected: PASS.

Commit:

```bash
git add src/main/java/net/inkyquill/delightfulkitchen/menu/ChefCounterMenu.java \
  src/main/resources/assets/delightful_kitchen/lang/en_us.json
git commit -m "feat: add chef counter plan confirmation hook"
```

---

### Task 10: Verification And Roadmap Update

**Files:**

- Modify last: `docs/ROADMAP.md`

- [ ] **Step 1: Run source checks**

Run:

```bash
rg -n 'KitchenItemProcessor|KitchenOperation|IngredientToken|CookingForBlockheadsAPI|pushToKitchen|pushFuelToKitchen' src/main/java/net/inkyquill/delightfulkitchen/block/entity src/main/java/net/inkyquill/delightfulkitchen/util src/main/java/net/inkyquill/delightfulkitchen/recipe
```

Expected: no matches in station block entities or `InventoryHelper`. If matches remain in compatibility-only files outside station execution, document why in the final summary.

- [ ] **Step 2: Run tests**

Run:

```bash
./gradlew test
```

Expected: PASS.

- [ ] **Step 3: Run build**

Run:

```bash
./gradlew build
```

Expected: PASS.

- [ ] **Step 4: Try game tests when practical**

Run:

```bash
./gradlew runGameTestServer
```

Expected:

- PASS if the current local Farmer's Delight runtime no longer has the documented dedicated-server client-class crash.
- If it crashes with the known Farmer's Delight dedicated-server client screen load issue documented in `AGENTS.md`, record the exact crash class in the final summary and do not treat that specific crash as this milestone failing.

- [ ] **Step 5: Update roadmap last**

In `docs/ROADMAP.md`, change:

```md
### 2. Queue Execution And Standalone Station Interfaces
```

to:

```md
### 2. Queue Execution And Standalone Station Interfaces (Complete)
```

Add one sentence under the deliverables:

```md
Implemented as an internal backend milestone: station jobs, confirmation-time validation, station queues, and non-destructive output handling are in place; the full recipe book UI remains Milestone 4.
```

- [ ] **Step 6: Commit docs**

```bash
git add docs/ROADMAP.md
git commit -m "docs: mark queue execution milestone complete"
```

- [ ] **Step 7: Final status check**

Run:

```bash
git status --short
```

Expected: no tracked modifications. Existing unrelated untracked local files may remain and must not be included unless the user explicitly asks.

---

## Self-Review

Spec coverage:

- Standalone station interface: Tasks 1, 2, 5, 6, 7.
- Queue capacity and station state: Tasks 1, 2, 5, 6, 7.
- Confirmation revalidation before consumption: Task 4.
- Controlled extraction and rollback: Tasks 3 and 4.
- Fuel and container extraction through kitchen inventory: Tasks 4, 6, 7.
- Non-destructive blocked outputs: Tasks 5, 6, 7.
- Planner-visible queue blockers: Tasks 1, 2, 4, 9.
- Retire Cooking for Blockheads station execution paths: Task 8.
- Full recipe book UI: explicitly out of scope and left for roadmap milestone 4.

Verification coverage:

- Unit tests for station selection, extraction rollback, confirmation blockers, and successful queueing.
- Compile after each station migration.
- Full `./gradlew test` and `./gradlew build`.
- Attempted `runGameTestServer` with documented handling of the known Farmer's Delight dedicated-server dev-runtime issue.
