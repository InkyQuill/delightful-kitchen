# Kitchen Utilities: Water Provider And Freezer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Milestone 3 kitchen utility system: Water Provider, Freezer, utility scanning, borrowed-container reservation, recipe planning blockers, execution-time release, and Freezer passive output.

**Architecture:** Keep utilities separate from stations and storage. `KitchenScanResult` records utility presence and positions, recipe plans carry explicit utility requirements, and a small resource reservation layer borrows empty buckets/bottles from kitchen storage without creating containers. Virtual provision is implemented before passive Freezer production; passive production uses the same container borrowing and return rules.

**Tech Stack:** Java 21, NeoForge 1.21.1, DeferredRegister, vanilla `Container`/`ItemStack`, NeoForge item handler capabilities, JUnit 5.

---

## Scope

This plan implements roadmap Milestone 3 only.

In scope:

- `water_provider` block with assets, recipe, loot table, lang, creative tab entry.
- `freezer` block entity with 9 output slots, menu, screen, assets, recipe, loot table, lang, creative tab entry.
- Kitchen scanner utility discovery separate from inventory endpoints and stations.
- Utility resource requirements on planned recipe steps.
- Planner blockers for missing Water Provider, missing Freezer, and missing borrowed containers.
- Confirmation-time reservation of buckets/bottles and rollback-safe return.
- Freezer passive production of ice and snowballs using Water Provider plus borrowed containers.
- Focused unit tests and build verification.

Out of scope:

- Full Chef's Counter recipe book UI and details panel.
- Freezer throughput upgrades or parallel Freezer scaling.
- Arbitrary fluid handling.
- Food Pouch.
- Animal stations.

---

## File Structure

Create:

- `src/main/java/net/inkyquill/delightfulkitchen/block/WaterProviderBlock.java`
  - Simple utility block, no block entity, no UI.
- `src/main/java/net/inkyquill/delightfulkitchen/block/FreezerBlock.java`
  - Directional block with active/blocker state and block entity support.
- `src/main/java/net/inkyquill/delightfulkitchen/block/entity/FreezerBlockEntity.java`
  - 9-slot output inventory, passive production tick, container borrowing through kitchen services.
- `src/main/java/net/inkyquill/delightfulkitchen/menu/FreezerMenu.java`
  - Output-only menu plus player inventory.
- `src/main/java/net/inkyquill/delightfulkitchen/client/screen/FreezerScreen.java`
  - Simple output grid/status screen.
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/utility/KitchenUtilityService.java`
  - Utility presence and borrowed-container reservation facade.
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/utility/KitchenUtilityRequirement.java`
  - `NONE`, `WATER`, `ICE`, `SNOWBALL`.
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/utility/BorrowedContainer.java`
  - Extracted empty container plus return target metadata.
- `src/test/java/net/inkyquill/delightfulkitchen/kitchen/utility/KitchenUtilityServiceTest.java`
  - Reservation, release, rollback, and blocker behavior.
- `src/test/java/net/inkyquill/delightfulkitchen/kitchen/recipe/RecipeUtilityPlannerTest.java`
  - Planner classification with water/ice/snow utility requirements.
- `src/test/java/net/inkyquill/delightfulkitchen/block/entity/FreezerTestAccess.java`
  - Package test bridge for passive production state.
- `src/test/java/net/inkyquill/delightfulkitchen/kitchen/utility/FreezerPassiveProductionTest.java`
  - Passive production behavior.

Modify:

- `src/main/java/net/inkyquill/delightfulkitchen/block/ModBlocks.java`
- `src/main/java/net/inkyquill/delightfulkitchen/item/ModItems.java`
- `src/main/java/net/inkyquill/delightfulkitchen/block/entity/ModBlockEntities.java`
- `src/main/java/net/inkyquill/delightfulkitchen/menu/ModMenuTypes.java`
- `src/main/java/net/inkyquill/delightfulkitchen/client/DelightfulKitchenClient.java`
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/KitchenScanResult.java`
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/KitchenScanner.java`
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/KitchenServices.java`
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/KitchenInventoryEndpoint.java`
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/KitchenInventoryService.java`
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/recipe/PlannedRecipe.java`
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/recipe/PlanningBlocker.java`
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/recipe/RecipePlanner.java`
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/recipe/adapters/ShapedCraftingAdapter.java`
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/recipe/adapters/ShapelessCraftingAdapter.java`
- `src/main/java/net/inkyquill/delightfulkitchen/kitchen/execution/KitchenPlanExecutor.java`
- `src/main/java/net/inkyquill/delightfulkitchen/menu/ChefCounterMenu.java`
- `src/main/java/net/inkyquill/delightfulkitchen/client/screen/ChefCounterScreen.java`
- `src/main/resources/assets/delightful_kitchen/lang/en_us.json`
- blockstate/model/item model/loot table/recipe/tag JSON files under `src/main/resources/`
- `docs/ROADMAP.md`
- `docs/superpowers/specs/2026-05-16-delightful-rename-and-utilities.md`

---

## Task 1: Utility Block Registrations And Assets

**Files:**

- Create: `src/main/java/net/inkyquill/delightfulkitchen/block/WaterProviderBlock.java`
- Create: `src/main/java/net/inkyquill/delightfulkitchen/block/FreezerBlock.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/block/ModBlocks.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/item/ModItems.java`
- Modify: `src/main/resources/assets/delightful_kitchen/lang/en_us.json`
- Create: `src/main/resources/assets/delightful_kitchen/blockstates/water_provider.json`
- Create: `src/main/resources/assets/delightful_kitchen/blockstates/freezer.json`
- Create: `src/main/resources/assets/delightful_kitchen/models/block/water_provider.json`
- Create: `src/main/resources/assets/delightful_kitchen/models/block/freezer.json`
- Create: `src/main/resources/assets/delightful_kitchen/models/block/freezer_active.json`
- Create: `src/main/resources/assets/delightful_kitchen/models/item/water_provider.json`
- Create: `src/main/resources/assets/delightful_kitchen/models/item/freezer.json`
- Create: `src/main/resources/assets/delightful_kitchen/textures/block/water_provider_top.png`
- Create: `src/main/resources/assets/delightful_kitchen/textures/block/water_provider_side.png`
- Create: `src/main/resources/assets/delightful_kitchen/textures/block/freezer_top.png`
- Create: `src/main/resources/assets/delightful_kitchen/textures/block/freezer_side.png`
- Create: `src/main/resources/assets/delightful_kitchen/textures/block/freezer_front.png`
- Create: `src/main/resources/assets/delightful_kitchen/textures/block/freezer_front_active.png`
- Create: `src/main/resources/data/delightful_kitchen/loot_table/blocks/water_provider.json`
- Create: `src/main/resources/data/delightful_kitchen/loot_table/blocks/freezer.json`
- Create: `src/main/resources/data/delightful_kitchen/recipe/water_provider.json`
- Create: `src/main/resources/data/delightful_kitchen/recipe/freezer.json`
- Create: `src/main/resources/data/delightful_kitchen/advancements/recipes/water_provider.json`
- Create: `src/main/resources/data/delightful_kitchen/advancements/recipes/freezer.json`

- [ ] **Step 1: Add block classes**

`WaterProviderBlock` should extend `Block` only. `FreezerBlock` should follow existing directional blocks and expose `ACTIVE` for passive production.

```java
public class WaterProviderBlock extends Block {
    public WaterProviderBlock(Properties properties) {
        super(properties);
    }
}
```

```java
public class FreezerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public FreezerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FreezerBlockEntity(pos, state);
    }
}
```

- [ ] **Step 2: Register blocks and block items**

Add to `ModBlocks`:

```java
public static final DeferredBlock<WaterProviderBlock> WATER_PROVIDER =
        BLOCKS.register("water_provider",
                () -> new WaterProviderBlock(BlockBehaviour.Properties.of()
                        .mapColor(MapColor.STONE)
                        .strength(2.0f, 6.0f)
                        .sound(SoundType.STONE)));

public static final DeferredBlock<FreezerBlock> FREEZER =
        BLOCKS.register("freezer",
                () -> new FreezerBlock(BlockBehaviour.Properties.of()
                        .mapColor(MapColor.METAL)
                        .strength(2.5f, 6.0f)
                        .sound(SoundType.METAL)));
```

Add matching `BlockItem` registrations to `ModItems` using the existing project pattern.

- [ ] **Step 3: Add JSON assets and recipes**

Use `$imagegen` for the new bitmap block and GUI textures if a previous texture cannot be adapted cleanly. Keep generated textures committed as PNG files under `assets/delightful_kitchen/textures/`.

Use `cube_bottom_top` or oriented model patterns matching existing blocks. Recipes:

`water_provider`: `III/BWB/SSS`, where `I=iron_ingot`, `B=iron_bars`, `W=water_bucket`, `S=stone`.

`freezer`: `III/IPI/ISI`, where `I=iron_ingot`, `P=packed_ice`, `S=snow_block`.

- [ ] **Step 4: Verify resources load**

Run:

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/inkyquill/delightfulkitchen/block \
  src/main/java/net/inkyquill/delightfulkitchen/item/ModItems.java \
  src/main/resources/assets/delightful_kitchen \
  src/main/resources/data/delightful_kitchen
git commit -m "feat: add kitchen utility blocks"
```

---

## Task 2: Freezer Block Entity, Menu, And Screen Shell

**Files:**

- Create: `src/main/java/net/inkyquill/delightfulkitchen/block/entity/FreezerBlockEntity.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/block/entity/ModBlockEntities.java`
- Create: `src/main/java/net/inkyquill/delightfulkitchen/menu/FreezerMenu.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/menu/ModMenuTypes.java`
- Create: `src/main/java/net/inkyquill/delightfulkitchen/client/screen/FreezerScreen.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/client/DelightfulKitchenClient.java`
- Create: `src/main/resources/assets/delightful_kitchen/textures/gui/freezer.png`

- [ ] **Step 1: Write a failing menu/inventory test**

Create `FreezerPassiveProductionTest` with an initial test that asserts the Freezer exposes 9 output slots and rejects non-output insertion through the menu.

```java
@Test
void freezerStartsWithNineEmptyOutputSlots() {
    FreezerBlockEntity freezer = FreezerTestAccess.create();
    assertEquals(9, freezer.getContainerSize());
    for (int slot = 0; slot < freezer.getContainerSize(); slot++) {
        assertTrue(freezer.getItem(slot).isEmpty());
    }
}
```

Run:

```bash
./gradlew test --tests net.inkyquill.delightfulkitchen.kitchen.utility.FreezerPassiveProductionTest
```

Expected: FAIL because `FreezerBlockEntity` and `FreezerTestAccess` do not exist yet.

- [ ] **Step 2: Implement Freezer inventory shell**

`FreezerBlockEntity` should extend the same base interfaces as existing storage block entities: `BlockEntity`, `MenuProvider`, `Container`, and `WorldlyContainer` only if sided extraction is already used locally. It stores exactly 9 output slots and persists them under an `Items` list.

Required constants:

```java
public static final int OUTPUT_SLOTS = 9;
public static final int PASSIVE_CYCLE_TICKS = 1200;
```

Allowed items in output slots:

```java
private static boolean isAllowedOutput(ItemStack stack) {
    return stack.is(Items.ICE) || stack.is(Items.SNOWBALL);
}
```

- [ ] **Step 3: Add menu and screen**

`FreezerMenu` should display the 9 output slots and player inventory. Shift-click from Freezer to player should work. Shift-click from player into Freezer should reject all items because the Freezer has no manual input.

`FreezerScreen` can mirror the simple style of `FuelProviderScreen`: title, output grid, player inventory, and text status lines.

- [ ] **Step 4: Register block entity, menu, and client screen**

Register `FREEZER` in `ModBlockEntities`, `FREEZER` menu in `ModMenuTypes`, and the screen in `DelightfulKitchenClient`.

- [ ] **Step 5: Verify**

Run:

```bash
./gradlew test --tests net.inkyquill.delightfulkitchen.kitchen.utility.FreezerPassiveProductionTest
./gradlew build
```

Expected: both pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/net/inkyquill/delightfulkitchen/block/entity \
  src/main/java/net/inkyquill/delightfulkitchen/menu \
  src/main/java/net/inkyquill/delightfulkitchen/client \
  src/main/resources/assets/delightful_kitchen/textures/gui/freezer.png \
  src/test/java/net/inkyquill/delightfulkitchen
git commit -m "feat: add freezer storage UI"
```

---

## Task 3: Scanner Utility Discovery

**Files:**

- Modify: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/KitchenScanResult.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/KitchenScanner.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/KitchenServices.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/menu/ChefCounterMenu.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/client/screen/ChefCounterScreen.java`
- Modify: `src/main/resources/assets/delightful_kitchen/lang/en_us.json`

- [ ] **Step 1: Write scanner tests**

Add tests covering:

- Water Provider above connected Kitchen Floor sets `hasWaterProvider()`.
- Freezer above connected Kitchen Floor sets `hasFreezer()`.
- Utility blocks do not inflate `memberCount()`.
- Utility positions are stable and copied defensively.

- [ ] **Step 2: Expand scan result**

Change `KitchenScanResult` to:

```java
public record KitchenScanResult(
        boolean hasKitchenFloor,
        Set<BlockPos> floorTiles,
        List<BlockPos> memberBlocks,
        List<BlockPos> counters,
        List<BlockPos> waterProviders,
        List<BlockPos> freezers
) {
    public boolean hasWaterProvider() {
        return !waterProviders.isEmpty();
    }

    public boolean hasFreezer() {
        return !freezers.isEmpty();
    }
}
```

Update all existing constructor calls to pass `List.of()` for both utility lists when absent.

- [ ] **Step 3: Update scanner classification**

In `KitchenScanner.scan`, classify utility blocks before `isCompatibleMember`.

Rules:

- `water_provider` positions go into `waterProviders`.
- `freezer` positions go into `freezers`.
- Neither utility list is added to `memberBlocks`.
- Chef's Counters remain counters and compatible members as before.

- [ ] **Step 4: Surface utility counts in Chef's Counter summary**

Expand `ChefCounterMenu` data slots by two values:

- `DATA_WATER_PROVIDER_COUNT`
- `DATA_FREEZER_COUNT`

In `ChefCounterScreen`, add compact status lines:

- `Water Provider: present/missing`
- `Freezer: present/missing`

- [ ] **Step 5: Verify**

Run:

```bash
./gradlew test
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/net/inkyquill/delightfulkitchen/kitchen \
  src/main/java/net/inkyquill/delightfulkitchen/menu/ChefCounterMenu.java \
  src/main/java/net/inkyquill/delightfulkitchen/client/screen/ChefCounterScreen.java \
  src/main/resources/assets/delightful_kitchen/lang/en_us.json \
  src/test/java/net/inkyquill/delightfulkitchen
git commit -m "feat: scan kitchen utility blocks"
```

---

## Task 4: Borrowed Container Resource Service

**Files:**

- Create: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/utility/KitchenUtilityRequirement.java`
- Create: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/utility/BorrowedContainer.java`
- Create: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/utility/KitchenUtilityService.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/KitchenServices.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/KitchenInventoryEndpoint.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/KitchenInventoryService.java`
- Test: `src/test/java/net/inkyquill/delightfulkitchen/kitchen/utility/KitchenUtilityServiceTest.java`

- [ ] **Step 1: Write reservation tests**

Cover:

- Water reservation succeeds with either empty bucket or glass bottle when Water Provider exists.
- Ice reservation requires Freezer, Water Provider, and an empty bucket.
- Snowball reservation requires Freezer, Water Provider, and an empty glass bottle.
- Reservation extracts exactly one empty container.
- Release inserts the same container back, preferring the original endpoint/slot when possible.
- If original return is full, release falls back to general kitchen insertion.
- If both return routes fail, release reports the dropped stack to the caller rather than deleting it.

- [ ] **Step 2: Add utility requirement enum**

```java
public enum KitchenUtilityRequirement {
    NONE,
    WATER,
    ICE,
    SNOWBALL
}
```

- [ ] **Step 3: Add borrowed container value**

```java
public record BorrowedContainer(
        ItemStack stack,
        KitchenInventoryEndpoint source,
        int sourceSlot
) {
    public BorrowedContainer {
        stack = Objects.requireNonNull(stack, "stack").copy();
        source = Objects.requireNonNull(source, "source");
        if (sourceSlot < 0) {
            throw new IllegalArgumentException("sourceSlot cannot be negative");
        }
    }
}
```

- [ ] **Step 4: Add endpoint-aware extraction helpers**

Extend `KitchenInventoryService` with a package-private or public helper used by `KitchenUtilityService`:

```java
public Optional<BorrowedContainer> borrowOne(Predicate<ItemStack> matcher) {
    // Iterate endpoints in normal kitchen priority order.
    // Extract exactly one matching stack from its real endpoint.
    // Return source endpoint and source slot for best-effort release.
}
```

Do not special-case Water Provider or Freezer here; this service only manages item containers.

- [ ] **Step 5: Add utility service**

`KitchenUtilityService` constructor:

```java
public KitchenUtilityService(KitchenScanResult scan, KitchenInventoryService inventory)
```

Required methods:

```java
public EnumSet<PlanningBlocker> blockersFor(KitchenUtilityRequirement requirement);
public Optional<BorrowedContainer> borrow(KitchenUtilityRequirement requirement);
public Optional<ItemStack> release(BorrowedContainer borrowed);
```

Blocker mapping:

- `WATER` missing provider -> `MISSING_WATER_PROVIDER`
- `WATER` missing bucket/bottle -> `MISSING_WATER_CONTAINER`
- `ICE` missing freezer -> `MISSING_FREEZER`
- `ICE` missing water provider -> `MISSING_WATER_PROVIDER`
- `ICE` missing bucket -> `MISSING_ICE_CONTAINER`
- `SNOWBALL` missing freezer -> `MISSING_FREEZER`
- `SNOWBALL` missing water provider -> `MISSING_WATER_PROVIDER`
- `SNOWBALL` missing bottle -> `MISSING_SNOWBALL_CONTAINER`

- [ ] **Step 6: Use explicit v1 container matching**

Do not create public container tags for this task. V1 matching must stay explicit to avoid false extension points before custom container semantics exist:

- `WATER`: `Items.BUCKET` or `Items.GLASS_BOTTLE`.
- `ICE`: `Items.BUCKET` only.
- `SNOWBALL`: `Items.GLASS_BOTTLE` only.

Custom container tags are deferred until the planner/executor can define how custom containers are filled, emptied, returned, and represented in recipes.

- [ ] **Step 7: Verify**

Run:

```bash
./gradlew test --tests net.inkyquill.delightfulkitchen.kitchen.utility.KitchenUtilityServiceTest
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/net/inkyquill/delightfulkitchen/kitchen \
  src/test/java/net/inkyquill/delightfulkitchen/kitchen/utility
git commit -m "feat: add kitchen utility reservations"
```

---

## Task 5: Recipe Planner Utility Requirements

**Files:**

- Modify: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/recipe/PlannedRecipe.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/recipe/PlanningBlocker.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/recipe/RecipePlanner.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/recipe/adapters/ShapedCraftingAdapter.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/recipe/adapters/ShapelessCraftingAdapter.java`
- Test: `src/test/java/net/inkyquill/delightfulkitchen/kitchen/recipe/RecipeUtilityPlannerTest.java`

- [ ] **Step 1: Write planner tests**

Add tests for synthetic recipes:

- Dough-like recipe with `Items.WATER_BUCKET` ingredient becomes ready when Water Provider plus empty bucket exists, and blocked without provider.
- Same recipe is blocked with `MISSING_WATER_CONTAINER` when provider exists but no empty bucket/bottle exists.
- Ice recipe becomes craftable through Freezer + Water Provider + empty bucket even when no physical ice is stored.
- Snowball recipe becomes craftable through Freezer + Water Provider + empty bottle.
- Physical stored water bucket/ice/snowball still works without utility conversion if already present in kitchen storage.

- [ ] **Step 2: Expand blockers**

Add:

```java
MISSING_WATER_PROVIDER,
MISSING_FREEZER,
MISSING_WATER_CONTAINER,
MISSING_ICE_CONTAINER,
MISSING_SNOWBALL_CONTAINER
```

to `PlanningBlocker`.

- [ ] **Step 3: Add utility requirement to PlannedRecipe**

Add `KitchenUtilityRequirement utilityRequirement` to the record after `fuel`.

Constructor rules:

- Default adapters pass `KitchenUtilityRequirement.NONE`.
- A recipe step can have one utility requirement in v1.
- Return defensive values as with existing record fields.

- [ ] **Step 4: Detect utility-convertible ingredients in crafting adapters**

For shaped and shapeless recipes only in this milestone:

- If an ingredient directly matches `Items.WATER_BUCKET` and no physical match is available, planner may satisfy it with `KitchenUtilityRequirement.WATER`.
- If an ingredient directly matches `Items.ICE` and no physical match is available, planner may satisfy it with `KitchenUtilityRequirement.ICE`.
- If an ingredient directly matches `Items.SNOWBALL` and no physical match is available, planner may satisfy it with `KitchenUtilityRequirement.SNOWBALL`.

Keep the adapter format simple: the adapter still emits normal `PlannedIngredient`s. `RecipePlanner` decides whether a missing ingredient can be replaced by a utility requirement.

- [ ] **Step 5: Integrate utility service into planner**

`RecipePlanner` should accept or construct a `KitchenUtilityService` from `KitchenScanResult` and `KitchenInventoryService`.

When a direct physical ingredient allocation fails:

1. Check whether the missing ingredient is utility-convertible.
2. Ask `KitchenUtilityService.blockersFor(requirement)`.
3. If no blockers, add the requirement to the planned step and do not consume a physical ingredient for that slot.
4. If blockers exist, add them to the plan blockers.

Do not allow utilities to satisfy non-crafting station inputs unless the adapter explicitly marks them in a later milestone. This keeps the first version bounded to shaped/shapeless food recipes.

- [ ] **Step 6: Verify**

Run:

```bash
./gradlew test --tests net.inkyquill.delightfulkitchen.kitchen.recipe.RecipeUtilityPlannerTest
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/net/inkyquill/delightfulkitchen/kitchen/recipe \
  src/test/java/net/inkyquill/delightfulkitchen/kitchen/recipe/RecipeUtilityPlannerTest.java
git commit -m "feat: plan water and cold utility requirements"
```

---

## Task 6: Execution-Time Borrow And Release

**Files:**

- Modify: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/execution/KitchenPlanExecutor.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/kitchen/station/KitchenStationJob.java`
- Modify: station block entities only if `KitchenStationJob` needs to carry utility metadata through queued work.
- Test: `src/test/java/net/inkyquill/delightfulkitchen/kitchen/execution/KitchenPlanExecutorTest.java`

- [ ] **Step 1: Add executor tests**

Cover:

- Confirmation for water utility recipe extracts one empty bucket/bottle, queues/inserts outputs, then returns the container on instant craft completion.
- If utility borrowing fails after fingerprint validation, extracted ingredients are rolled back.
- If output insertion fails after borrowing, borrowed containers are returned before reporting `OUTPUT_STORAGE_FULL`.
- Station jobs preserve borrowed containers until station completion and return them with normal output routing.

- [ ] **Step 2: Extend execution data**

Prefer not to put utility containers into `KitchenStationJob.inputs()` because stations should not cook the container. Add:

```java
List<BorrowedContainer> borrowedContainers
```

to `KitchenStationJob`, or keep it executor-local for instant crafting and add only if asynchronous station utility jobs are required.

For Milestone 3 shaped/shapeless utility support, executor-local handling is enough because those steps are instant crafting. If utility requirements are later added to Pot Stand/Campfire steps, update `KitchenStationJob`.

- [ ] **Step 3: Borrow before mutating step outputs**

In `extractJob`:

1. Extract normal ingredients.
2. Extract serving container.
3. Extract fuel.
4. Borrow utility container for `step.utilityRequirement()`.
5. If any stage fails, rollback all previous extracts and releases.

- [ ] **Step 4: Release containers on all completion paths**

For instant crafting:

- Insert crafted outputs.
- Release borrowed containers.
- If release cannot fully insert, drop at nearest utility position and return a blocked result only if the item could not be spawned.

For rollback:

- Release borrowed containers before restoring normal ingredients if release target may share storage with restored stacks.
- Preserve the existing ambiguous instant-output rollback guard.

- [ ] **Step 5: Verify**

Run:

```bash
./gradlew test --tests net.inkyquill.delightfulkitchen.kitchen.execution.KitchenPlanExecutorTest
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/net/inkyquill/delightfulkitchen/kitchen/execution \
  src/main/java/net/inkyquill/delightfulkitchen/kitchen/station \
  src/test/java/net/inkyquill/delightfulkitchen/kitchen/execution/KitchenPlanExecutorTest.java
git commit -m "feat: execute borrowed utility containers"
```

---

## Task 7: Freezer Passive Production

**Files:**

- Modify: `src/main/java/net/inkyquill/delightfulkitchen/block/entity/FreezerBlockEntity.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/block/FreezerBlock.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/menu/FreezerMenu.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/client/screen/FreezerScreen.java`
- Test: `src/test/java/net/inkyquill/delightfulkitchen/kitchen/utility/FreezerPassiveProductionTest.java`

- [ ] **Step 1: Add passive production tests**

Cover:

- With Water Provider and empty bucket, after 1200 ticks the Freezer outputs one ice and returns the bucket.
- With Water Provider and empty bottle, after 1200 ticks the Freezer outputs one snowball and returns the bottle.
- With both containers available, default mode alternates ice then snowball.
- Production pauses when no Water Provider is present.
- Production pauses when no required container is present.
- Production pauses when output slots cannot accept the product.

- [ ] **Step 2: Add mode enum**

Inside `FreezerBlockEntity`:

```java
public enum PassiveMode {
    ICE_AND_SNOW,
    ICE_ONLY,
    SNOW_ONLY,
    PAUSED
}
```

Persist mode, progress ticks, and next product preference to NBT.

- [ ] **Step 3: Implement server tick**

Rules:

- Tick server-side only.
- If mode is `PAUSED`, set `ACTIVE=false` and do nothing.
- Build `KitchenUtilityService` by scanning from the Freezer's kitchen floor/counter context. If no connected Kitchen Floor exists below the Freezer column, pause.
- Select next requirement by mode.
- If requirement has blockers, pause without mutating inventory.
- Increment progress.
- At 1200 ticks, borrow container, insert output into Freezer output slots, release container, reset progress.
- If insertion fails after borrow, release container and keep progress at 1199 so the next tick can retry.

- [ ] **Step 4: Add UI mode toggle**

The first UI can use a simple button cycling:

`Ice & Snow` -> `Ice Only` -> `Snow Only` -> `Paused` -> `Ice & Snow`.

Use vanilla menu button handling instead of adding a custom network channel:

```java
// FreezerScreen button click
this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
```

```java
// FreezerMenu
@Override
public boolean clickMenuButton(Player player, int id) {
    if (id != 0 || !(container instanceof FreezerBlockEntity freezer)) {
        return false;
    }
    freezer.cyclePassiveMode();
    return true;
}
```

- [ ] **Step 5: Verify**

Run:

```bash
./gradlew test --tests net.inkyquill.delightfulkitchen.kitchen.utility.FreezerPassiveProductionTest
./gradlew test
./gradlew build
```

Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/net/inkyquill/delightfulkitchen/block/entity/FreezerBlockEntity.java \
  src/main/java/net/inkyquill/delightfulkitchen/block/FreezerBlock.java \
  src/main/java/net/inkyquill/delightfulkitchen/menu/FreezerMenu.java \
  src/main/java/net/inkyquill/delightfulkitchen/client/screen/FreezerScreen.java \
  src/test/java/net/inkyquill/delightfulkitchen/kitchen/utility/FreezerPassiveProductionTest.java
git commit -m "feat: add freezer passive production"
```

---

## Task 8: Polish, Compatibility Scan, And Final Docs

**This task must be last. Do not update roadmap/docs earlier in the implementation.**

**Files:**

- Modify: `docs/ROADMAP.md`
- Modify: `docs/superpowers/specs/2026-05-16-delightful-rename-and-utilities.md`
- Modify: `README.md` only if player-visible setup changed.
- Modify: `AGENTS.md` only if roadmap rules changed.

- [ ] **Step 1: Run full verification**

Run:

```bash
./gradlew test
./gradlew build
git diff --check
```

Expected:

- `./gradlew test`: `BUILD SUCCESSFUL`
- `./gradlew build`: `BUILD SUCCESSFUL`
- `git diff --check`: no output

- [ ] **Step 2: Optional game-test/manual runtime check**

Run if the local Farmer's Delight dedicated-server crash is resolved:

```bash
./gradlew runGameTestServer
```

If it crashes with the known Farmer's Delight `Screen` class loading issue on `DEDICATED_SERVER`, document that exact crash and do not treat it as a Delightful Kitchen regression.

- [ ] **Step 3: Update roadmap**

In `docs/ROADMAP.md`, mark Milestone 3 as implemented. Preserve Milestone 4 as next:

```markdown
### 3. Kitchen Utilities: Water Provider And Freezer

Status: complete.

Implemented utility scanning, Water Provider, Freezer, borrowed-container reservation, utility planner blockers, execution-time container release, and passive Freezer production. Multiple providers/freezers still do not increase throughput; upgrades remain future work.
```

- [ ] **Step 4: Update utility spec status**

In `docs/superpowers/specs/2026-05-16-delightful-rename-and-utilities.md`, update the status note from future work to implemented for Water Provider and Freezer, and record any intentional deviation. At minimum document:

- Virtual utility provision supports shaped/shapeless crafting in this milestone.
- Container tags are intentionally deferred; v1 uses buckets and glass bottles only.
- Passive Freezer production is single-Freezer throughput; multiple Freezers provide redundancy only.

- [ ] **Step 5: Update README if needed**

If the mod now has additional craftable blocks and player behavior worth mentioning, add a short section listing Water Provider and Freezer.

- [ ] **Step 6: Commit docs last**

```bash
git add docs/ROADMAP.md docs/superpowers/specs/2026-05-16-delightful-rename-and-utilities.md README.md AGENTS.md
git commit -m "docs: mark kitchen utilities milestone complete"
```

---

## Self-Review

Spec coverage:

- Water Provider block: Task 1.
- Freezer block, inventory, UI: Tasks 1, 2, 7.
- Scanner utility presence separate from members: Task 3.
- Planner blockers for missing providers and containers: Tasks 4 and 5.
- Shared reservation model for buckets/bottles: Task 4.
- Confirmation-time borrow/release: Task 6.
- Passive Freezer production: Task 7.
- Tests and game/manual verification: Tasks 2 through 8.
- Roadmap/docs updated last: Task 8.

Known risk:

- Utility provision for station recipes is intentionally deferred unless adapters explicitly mark those station steps. Milestone 3 starts with shaped/shapeless utility replacement because the current adapters do not yet encode fluid/cold semantic requirements for all modded stations.

Execution recommendation:

- Use subagent-driven execution. Assign Tasks 1-2 to one worker, Tasks 3-4 to another, and Tasks 5-6 to a third only after Task 4 types are stable. Task 7 should run after Task 4, and Task 8 must remain main-agent owned as the final documentation pass.
