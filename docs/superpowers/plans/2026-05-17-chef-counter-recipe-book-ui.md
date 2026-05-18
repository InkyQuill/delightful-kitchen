# Milestone 4: Chef's Counter Recipe Book UI And Details Panel

**Goal:** Replace the current Chef's Counter summary screen with the usable recipe book UI described in the standalone kitchen spec: searchable edible recipe grid, craftability filters, selected plan details, quantity controls, and confirm/queue execution.

**Architecture:** Keep Chef's Counter as the cooking command center, not storage. The server remains authoritative for kitchen scans, inventory snapshots, recipe planning, fingerprints, and queue confirmation. The client gets compact view models over a small menu-scoped networking layer, then performs search/filter/page interactions locally. Confirmation sends a selected recipe id and requested quantity back to the server; the server rebuilds the plan from the current kitchen state before executing it.

**Date:** 2026-05-17

## Scope

- Full Chef's Counter recipe book grid for indexed edible outputs.
- Client-side search and craftability filters: all, ready, intermediate, blocked.
- Selected recipe details panel with dependency chain, blockers, stations, fuel, containers, utilities, expected output count, and queue status.
- Quantity controls for batch queue requests.
- Confirm/queue button wired to the existing kitchen plan executor.
- Network payloads and immutable view models for Chef's Counter screen sync.
- Tests for view-model conversion, batch planning, menu/session behavior where practical, and payload codecs.
- Documentation and roadmap updates as the final task.

## Out Of Scope

- Alternate recipe path selection beyond the existing deterministic best path.
- JEI/REI deep recipe transfer integration.
- New recipe adapters or new station types.
- Reworking station progress screens.
- Replacing the transitional CfB BFS fallback in `InventoryHelper`.

## Current Starting Point

- `ChefCounterMenu` currently exposes only 9 integer summary data slots.
- `ChefCounterScreen` is a simple status panel at 176x210.
- `RecipePlanner` can plan all edible recipes and returns `RecipePlan`.
- `KitchenPlanExecutor` can confirm a single `RecipePlan` with a `KitchenPlanFingerprint`.
- There is no custom networking layer yet.

## Design Decisions

1. Use custom NeoForge payloads for recipe book data. `ContainerData` is integer-only and is not appropriate for lists of recipe ids, item stacks, blockers, and plan steps.
2. Keep the client view model compact. The client receives enough to render the recipe book and details panel, but it does not make authoritative planning or execution decisions.
3. Rebuild plans on server confirmation. The client-selected plan is a preview; confirmation uses the current kitchen scan and inventory snapshot to prevent stale UI from consuming items.
4. Batch quantity is implemented server-side by constructing a combined plan from repeated deterministic planning passes against a simulated snapshot. If quantity 4 is requested and only 3 crafts can be planned, the server blocks the request and returns readable blockers instead of partially queueing.
5. Screen filtering is local after initial sync. Search text and filter buttons do not cause network traffic.

## Task 1: Add Chef Counter View Models

**Files:**

- Create `src/main/java/net/inkyquill/delightfulkitchen/menu/chef/ChefRecipeFilter.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/menu/chef/ChefRecipeSummary.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/menu/chef/ChefPlanStepView.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/menu/chef/ChefPlanDetails.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/menu/chef/ChefQueueResultView.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/menu/chef/ChefCounterViewState.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/menu/chef/ChefCounterViewModels.java`
- Add tests in `src/test/java/net/inkyquill/delightfulkitchen/menu/chef/ChefCounterViewModelsTest.java`

**Model shape:**

```java
public enum ChefRecipeFilter {
    ALL,
    READY,
    INTERMEDIATE,
    BLOCKED
}
```

```java
public record ChefRecipeSummary(
        int index,
        ResourceLocation recipeId,
        ItemStack output,
        PlanState state,
        EnumSet<PlanningBlocker> blockers,
        int stepCount
) {
}
```

```java
public record ChefPlanStepView(
        ResourceLocation recipeId,
        ItemStack primaryOutput,
        List<ItemStack> inputs,
        List<ItemStack> outputs,
        KitchenStationType stationType,
        ItemStack servingContainer,
        ItemStack fuel,
        KitchenUtilityRequirement utilityRequirement,
        int processingTime,
        boolean asynchronous
) {
}
```

```java
public record ChefPlanDetails(
        int selectedIndex,
        ResourceLocation targetRecipeId,
        ItemStack output,
        PlanState state,
        int quantity,
        int expectedOutputCount,
        List<ChefPlanStepView> steps,
        EnumSet<PlanningBlocker> blockers,
        KitchenPlanFingerprint fingerprint
) {
}
```

`ChefCounterViewModels` should convert `RecipePlan` and `PlannedRecipe` into these records. It should:

- Copy all `ItemStack` values defensively.
- Sort summaries deterministically by output display name, item registry id, then recipe id.
- Keep `EnumSet` immutable from the caller's perspective by copying in record compact constructors.
- Use `ItemStack.EMPTY` for missing container or fuel displays.
- Collapse repeated blockers but preserve all distinct blocker enum values.

**Tests:**

- Summary conversion copies stack counts and blockers.
- Step conversion includes station type, utility requirement, fuel, serving container, and async flag.
- Sorting is stable and deterministic for duplicate output names.
- Record accessors do not expose mutable blocker sets.

## Task 2: Add Batch Planning For Quantity Controls

**Files:**

- Modify `src/main/java/net/inkyquill/delightfulkitchen/kitchen/recipe/RecipePlanner.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/kitchen/recipe/RecipeBatchPlanner.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/kitchen/recipe/RecipeBatchPlan.java`
- Add tests in `src/test/java/net/inkyquill/delightfulkitchen/kitchen/recipe/RecipeBatchPlannerTest.java`

**Implementation requirements:**

- Add a public planner result type that exposes both the visible `RecipePlan` and the simulated snapshot after applying that plan.
- Keep the existing `RecipePlanner.plan(...)` and `planAllEdible(...)` methods source-compatible.
- Add a method equivalent to:

```java
public RecipePlanningResult planWithResult(PlannedRecipe target, KitchenInventorySnapshot snapshot)
```

where `RecipePlanningResult` contains:

```java
public record RecipePlanningResult(RecipePlan plan, KitchenInventorySnapshot snapshotAfterPlan) {
}
```

- `RecipeBatchPlanner` should build a combined `RecipePlan` for a target recipe id and requested quantity:
  - Clamp accepted UI quantity to `1..64`.
  - For each requested craft, call `planWithResult(...)` using the snapshot produced by the previous pass.
  - If any pass becomes blocked, return a blocked batch plan with the blockers from the failed pass and the already-planned preview steps included for display.
  - If all passes succeed, return one `RecipePlan` whose steps are the concatenation of all planned steps in execution order.
  - Set the output count to `singleOutput.getCount() * quantity`, clamped to the item max stack size for display. The details panel should also show the requested craft count separately so large recipes are not misrepresented.
  - Preserve `READY` if every repeated plan is ready; use `CRAFTABLE_WITH_INTERMEDIATES` if any repeated plan uses intermediates.

**Tests:**

- Quantity 1 returns the same state and steps as the single planner.
- Quantity 2 consumes from the simulated snapshot between passes.
- Quantity 2 blocks when only one craft's raw ingredients exist.
- Quantity with intermediates concatenates intermediate steps before final steps for each craft.
- Quantity is clamped to the supported range.

## Task 3: Add Menu-Scoped Networking

**Files:**

- Create `src/main/java/net/inkyquill/delightfulkitchen/network/ModPayloads.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/network/payload/ClientboundChefCounterSyncPayload.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/network/payload/ClientboundChefCounterDetailsPayload.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/network/payload/ClientboundChefCounterQueueResultPayload.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/network/payload/ServerboundChefCounterSelectPayload.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/network/payload/ServerboundChefCounterConfirmPayload.java`
- Modify `src/main/java/net/inkyquill/delightfulkitchen/DelightfulKitchen.java`
- Add codec tests in `src/test/java/net/inkyquill/delightfulkitchen/network/payload/ChefCounterPayloadCodecTest.java`

**NeoForge integration:**

- Register payload handlers from the mod event bus in `DelightfulKitchen`.
- Use NeoForge 1.21.1 custom packet payload APIs:
  - `RegisterPayloadHandlersEvent`
  - `PayloadRegistrar`
  - `CustomPacketPayload.Type`
  - `StreamCodec<RegistryFriendlyByteBuf, PayloadType>`
- If the local NeoForge API has minor naming differences, follow the version in this workspace and keep the payload layer isolated under `network/`.

**Payload responsibilities:**

- `ClientboundChefCounterSyncPayload`
  - `containerId`
  - `List<ChefRecipeSummary>`
  - floor/inventory/member/utility counts currently exposed by `ContainerData`
  - multiple-counter warning flag
- `ClientboundChefCounterDetailsPayload`
  - `containerId`
  - nullable or optional `ChefPlanDetails`
- `ClientboundChefCounterQueueResultPayload`
  - `containerId`
  - `ChefQueueResultView`
- `ServerboundChefCounterSelectPayload`
  - `containerId`
  - selected summary index
  - requested quantity
- `ServerboundChefCounterConfirmPayload`
  - `containerId`
  - selected summary index
  - requested quantity
  - fingerprint from the last details payload

**Handler rules:**

- Serverbound handlers must verify that `player.containerMenu` is a `ChefCounterMenu` with the matching `containerId`.
- Serverbound selection must not mutate inventory.
- Serverbound confirmation must rebuild scan, inventory service, utility service, planner, and batch plan before queueing.
- Clientbound handlers should only update the matching open `ChefCounterMenu`; ignore stale payloads.

**Tests:**

- Encode/decode summary payload preserves item stack, recipe id, state, and blockers.
- Encode/decode details payload preserves step data, fingerprint, and quantity.
- Encode/decode queue result payload preserves success and blocker list.

## Task 4: Rework ChefCounterMenu Into A Recipe Session

**Files:**

- Modify `src/main/java/net/inkyquill/delightfulkitchen/menu/ChefCounterMenu.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/menu/chef/ChefCounterServerSession.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/menu/chef/ChefCounterClientState.java`
- Add tests in `src/test/java/net/inkyquill/delightfulkitchen/menu/chef/ChefCounterServerSessionTest.java`

**Server session responsibilities:**

- Scan the kitchen from the Chef's Counter position.
- Build inventory endpoints, station service, utility service, recipe index, and planner.
- Create and store the sorted `List<RecipePlan>` and matching `List<ChefRecipeSummary>`.
- Produce details for a selected index and quantity via `RecipeBatchPlanner`.
- Confirm a selected index and quantity through `KitchenPlanExecutor`.
- Rebuild state after a successful queue or after a stale fingerprint failure.

**Menu changes:**

- Keep `ContainerData` for lightweight counters until the network sync is fully trusted.
- On server menu construction, create a `ChefCounterServerSession`.
- Send initial `ClientboundChefCounterSyncPayload` and first selected details after the menu opens.
- Add package-visible methods used by payload handlers:
  - `handleClientSelect(ServerPlayer player, int index, int quantity)`
  - `handleClientConfirm(ServerPlayer player, int index, int quantity, KitchenPlanFingerprint fingerprint)`
  - `handleClientState(ChefCounterViewState state)` for clientbound updates.
- Client constructor still uses `ContainerLevelAccess.NULL`, but now owns a `ChefCounterClientState` for the screen to read.
- If the kitchen has no floor, send an empty summary list and a details payload with `KITCHEN_CHANGED` or a dedicated missing-floor result.

**Confirmation behavior:**

- Reject blocked batch plans before extraction.
- Reject stale fingerprints with `PlanningBlocker.KITCHEN_CHANGED`.
- On success, send a success queue result and refresh summaries/details so the UI immediately reflects consumed ingredients and newly queued station capacity.
- On failure, send blockers and refresh details for the selected recipe.

**Tests:**

- Server session creates empty recipe state when no kitchen floor exists.
- Server session selects details by sorted summary index.
- Invalid selected index returns no details and does not throw.
- Confirmation with stale fingerprint returns kitchen-changed blocker.
- Successful confirmation refreshes the session state.

## Task 5: Build The Recipe Book Screen

**Files:**

- Modify `src/main/java/net/inkyquill/delightfulkitchen/client/screen/ChefCounterScreen.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/client/screen/widget/ChefRecipeFilterButton.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/client/screen/widget/ChefRecipeGrid.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/client/screen/widget/ChefQuantityControl.java`
- Modify `src/main/resources/assets/delightful_kitchen/lang/en_us.json`

**Layout:**

- Expand the Chef's Counter GUI to a wider kitchen command screen while preserving the vanilla player inventory slots.
- Suggested size: `imageWidth = 276`, `imageHeight = 222`.
- Top left: search field.
- Below search: compact filter buttons for All, Ready, Intermediate, Blocked.
- Left/middle: recipe grid with item icons, page/scroll support, hover tooltips, and status overlays.
- Right: details panel.
- Bottom: player inventory and hotbar.

**Recipe grid behavior:**

- Render edible output item stacks from `ChefCounterClientState.summaries()`.
- Search should match:
  - rendered output display name
  - recipe namespace/path
- Filters:
  - `ALL`: every summary
  - `READY`: `PlanState.READY`
  - `INTERMEDIATE`: `PlanState.CRAFTABLE_WITH_INTERMEDIATES`
  - `BLOCKED`: `PlanState.BLOCKED`
- Selection sends `ServerboundChefCounterSelectPayload` with current quantity.
- Mouse wheel scrolls the grid if there are more recipes than visible slots.
- Status overlays:
  - ready: small green check or green corner pixel marker
  - intermediate: small amber step marker
  - blocked: small red cross or red corner marker
- Tooltips include output name, state, and first blocker when blocked.

**Screen quality requirements:**

- No text overlap in the 276x222 screen.
- Use Minecraft-style concise labels, not explanatory paragraphs.
- Keep compact panel headings; no oversized text.
- Keep controls stable in size so changing filters or result text does not shift the inventory area.
- `Esc` and inventory key close the screen as usual.

**Language keys:**

Add keys for:

- filter labels
- search placeholder
- selected recipe labels
- quantity labels
- button labels: queue, queued, blocked
- empty states: no kitchen, no recipes, no search matches
- state labels: ready, intermediate, blocked
- detail labels: steps, stations, fuel, containers, utilities, blockers, output
- all `PlanningBlocker` enum values currently in the codebase

## Task 6: Build The Details Panel And Queue Result UX

**Files:**

- Modify `src/main/java/net/inkyquill/delightfulkitchen/client/screen/ChefCounterScreen.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/client/screen/widget/ChefPlanDetailsPanel.java`
- Modify `src/main/resources/assets/delightful_kitchen/lang/en_us.json`

**Details panel content:**

- Selected output icon and name.
- Current state label.
- Quantity controls with `-`, numeric value, and `+`.
- Expected output count.
- Required station list deduplicated from steps.
- Required utility list deduplicated from steps.
- Required fuel and serving container icons where present.
- Step list in execution order:
  - intermediate steps first when present
  - final step last for each batch craft
  - show station type and primary output for each step
- Blocker list when blocked.
- Confirm/queue button when state is not blocked.
- Queue result line after confirmation:
  - queued
  - blocked with blockers
  - kitchen changed, refresh required

**Interaction rules:**

- Changing quantity sends a details refresh request after the value changes.
- Confirm button sends `ServerboundChefCounterConfirmPayload`.
- Disable confirm while waiting for the server response.
- Re-enable confirm when a result payload or refreshed details arrives.
- If the selected recipe disappears after refresh, clear details and keep the list visible.

**Queue status scope:**

- Milestone 4 should show confirmation result and refreshed craftability immediately.
- Live per-station progress remains visible in station GUIs for now.
- The details panel may show a generic "Queued" status after successful confirmation; detailed running/completed status can be expanded later if it requires persistent job ids.

## Task 7: Add Client Smoke Test Hooks And Manual Verification Notes

**Files:**

- Create `docs/testing/chef-counter-recipe-book-smoke-test.md`
- Optionally add test-only helpers under `src/test/java/net/inkyquill/delightfulkitchen/client/` if current test tooling can instantiate screen state without a Minecraft client.

**Smoke test document must cover:**

- Chef's Counter without Kitchen Floor shows missing-kitchen empty state.
- Kitchen with food ingredients shows recipe summaries.
- Search narrows the list by display name.
- Each craftability filter shows only matching states.
- Selecting a recipe updates details.
- Quantity change refreshes expected output and blockers.
- Confirm queues executable plans and refreshes the list.
- Blocked recipe shows blockers and disabled confirm button.
- Multiple Chef's Counters warning remains visible.

**Automated tests where practical:**

- Client state filtering helper test if filtering is extracted from the screen.
- Quantity clamping helper test.
- Tooltip text builder test for blocked summaries.

## Task 8: Verification

Run these checks before documentation updates:

```bash
./gradlew test
./gradlew build
git diff --check
```

Manual in-game checks:

1. Place connected Kitchen Floor, a Chef's Counter, Cabinet of Holding, a chest with simple ingredients, and at least one station.
2. Open Chef's Counter and verify the recipe grid renders.
3. Search for a known food and select it.
4. Switch through all filters.
5. Queue a ready recipe and verify ingredients are consumed through the kitchen service.
6. Queue a recipe with intermediates and verify intermediate station jobs are queued before the final step.
7. Remove required fuel/container/station and verify blockers appear.
8. Test at the smallest supported GUI scale that text does not overlap the player inventory.

## Task 9: Update Documentation And Roadmap

This task must be last.

**Files:**

- Modify `docs/ROADMAP.md`
- Modify `docs/superpowers/specs/2026-05-16-standalone-kitchen-design.md`
- Modify `README.md`
- Modify `Plans.md`
- Modify `AGENTS.md` only if new project rules or milestone-specific caveats are discovered during implementation.

**Documentation updates:**

- Mark Milestone 4 as complete after implementation and verification.
- Add a short README section describing the Chef's Counter recipe book workflow.
- Update the standalone kitchen spec with any final UI behavior that differs from the original plan, especially queue result scope and quantity behavior.
- Move the next roadmap focus to the next approved milestone.
- Document any deferred work explicitly:
  - live station job ids and completed/running status if not implemented beyond generic queued feedback
  - JEI/REI deep integration
  - alternate recipe path selection

## Review Checklist

- Server is authoritative for recipe planning and confirmation.
- Client filtering never mutates inventory or stations.
- Stale fingerprints block confirmation instead of queueing against old data.
- Quantity requests either fully queue or return blockers; no partial batch queue.
- Recipe summaries and details copy mutable `ItemStack` and `EnumSet` data.
- Packet handlers ignore stale container ids.
- The screen remains usable with no kitchen floor, no recipes, no search matches, and blocked plans.
- Documentation and roadmap are updated last.

## Execution Options

1. **Guided implementation in this branch** - implement task by task with review checkpoints after networking, menu/session, and UI.
2. **UI-first prototype** - build the screen against fake client state first, then wire networking and server confirmation after the layout is stable.
3. **Backend-first** - implement batch planning, payloads, and menu/session tests first, then build the screen once sync is verified.

Recommended: **Option 1**. It keeps the user-visible UI and server authority moving together, while still giving clean checkpoints for review.
