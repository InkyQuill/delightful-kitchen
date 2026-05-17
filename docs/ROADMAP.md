# Delightful Kitchen Roadmap

Status: Canonical planning document

`Plans.md` is historical scratch notes. This roadmap is the source of truth for milestone order and scope.

## Current Direction

Delightful Kitchen is a standalone Farmer's Delight focused kitchen mod. The project is still pre-release, so breaking prototype-world changes are acceptable when they simplify the long-term design.

The current code uses the `delightful_kitchen` mod id, resource namespace, and `net.inkyquill.delightfulkitchen` Java package. New gameplay systems should build on that identity.

## Milestone Order

### 1. Rename To Delightful Kitchen (Complete)

Purpose: remove the temporary prototype identity before the project grows larger.

Deliverables:

- Rename mod id, resource namespace, lang keys, tags, recipes, loot tables, model paths, Java package, main mod class, creative tab, README references, and Gradle properties to `delightful_kitchen` / Delightful Kitchen.
- Keep the project directory name unchanged unless handled manually outside the mod build.
- Treat pre-rename development worlds as prototype worlds. No migration alias or remapping layer is required before public release.

Out of scope:

- Public world migration.
- Removing Cooking for Blockheads runtime/API usage.

Verification:

- `./gradlew test`
- `./gradlew build`
- A targeted grep shows no remaining accidental pre-rename identity references except intentional compatibility notes or archived docs.

### 2. Queue Execution And Standalone Station Interfaces (Complete)

Purpose: turn read-only planning into a usable kitchen automation loop and retire Cooking for Blockheads execution paths.

Deliverables:

- Define standalone station interfaces for queued planned steps.
- Make Auto Cutter, Grill, and Pot Stand accept planned steps without CfB APIs.
- Add confirmation flow that revalidates the kitchen snapshot before consuming items.
- Add reservation/extraction ordering for raw ingredients, intermediate outputs, serving containers, and fuel.
- Make station capacity visible to planning: queue full, output blocked, busy/running, and missing fuel/container states.
- Keep outputs non-destructive: blocked outputs stay in station buffers where possible.

Implemented as an internal backend milestone: station jobs, confirmation-time validation, station queues, and non-destructive output handling are in place; the full recipe book UI remains Milestone 4.

Out of scope:

- Full recipe book UI polish.
- Water Provider and Freezer.
- Food Pouch.

Verification:

- Focused tests for station acceptance, queue capacity, and confirmation abort behavior.
- Game tests for basic queue execution through Auto Cutter, Grill, and Pot Stand.

### 3. Kitchen Utilities: Water Provider And Freezer (Complete)

Purpose: support water, ice, and snowball recipe requirements without reintroducing infinite-container exploits.

Deliverables:

- Water Provider utility block.
- Freezer utility block.
- Scanner support for utility presence separate from inventory endpoints and stations.
- Planner blockers for missing water provider, freezer, and required empty containers.
- Shared resource reservation model for borrowed buckets/bottles.
- Freezer passive physical production of ice and snowballs after the virtual provision path is stable.

Implemented Water Provider and Freezer utility blocks, scanner utility discovery, borrowed vanilla bucket/bottle reservation, planner blockers, confirmation-time borrow/release for utility-backed crafting steps, and passive Freezer production with safe container return.

Out of scope:

- Throughput upgrades for multiple providers/freezers.
- Arbitrary modded fluids or custom tagged containers beyond the explicit vanilla bucket/bottle v1 behavior.

Verification:

- Tests for container reservation and release.
- Game tests for water/ice/snow recipe blockers and Freezer passive production.

### 4. Chef's Counter Recipe Book UI And Details Panel

Purpose: expose the planner as a player-friendly recipe browser and guided automation UI.

Deliverables:

- Recipe book grid of edible outputs.
- Search and filters: all, ready, intermediate, blocked.
- Details panel with dependency chain, blockers, stations, fuel, containers, output count, and quantity controls.
- Confirm/queue button for executable plans.
- Status display for queued/running/completed/blocked jobs.

Out of scope:

- Alternate recipe path selection beyond the deterministic best path.
- JEI/REI deep integration beyond existing compatibility work.

Verification:

- Menu sync tests where practical.
- Client smoke test for layout, filtering, and queue confirmation.

### 5. Food Pouch

Purpose: add a Curios-compatible wearable food inventory with smart auto-feeding.

Deliverables:

- Curios dependency and dedicated pouch slot.
- Basic, Advanced, and Ultimate pouch items.
- Custom pouch inventory serialization that supports large stack counts.
- Pouch UI and feeding settings.
- Auto-feeding with threshold settings, health override, cooldowns, and container return.

Out of scope:

- Kitchen planner integration.
- Pouch-to-kitchen routing.

Verification:

- Focused tests for pouch inventory limits, feeding decisions, and container return.
- Game tests for Curios equip/tick behavior and UI.

### 6. Animal Production Stations

Purpose: integrate animal-derived resources into kitchen automation.

Deliverables:

- Milking Machine multiblock.
- Chicken Nest block.
- Animal capture/release.
- Feed pulling through the kitchen inventory service.
- Output routing through the kitchen inventory service.
- Feed tags for cow and chicken feed.

Out of scope:

- Kitchen Cat.
- Breeding automation.
- Butchering stations.

Verification:

- Game tests for capture, release, feed use, production, output routing, and output-full behavior.

### 7. Kitchen Cat

Purpose: add a kitchen mascot with ambience and light utility.

Deliverables:

- Cat Bed, Food Bowl, Bin, and Cat Food.
- Kitchen Cat conversion from a tamed vanilla cat.
- Kitchen-bound AI, hunger, feeding, sleeping, perching, and gift behavior.
- Kitchen buffs while the cat is cared for.

Out of scope:

- Production-station implementation.
- Any animal breeding or combat behavior.

Verification:

- Game tests for conversion/release rules, kitchen boundary behavior, food bowl feeding, buffs, and gift deposits.

### 8. Later Polish And Integrations

Potential later work:

- Persistent Kitchen Floor camouflage.
- Pouch-kitchen food preference integration.
- Public third-party recipe adapter/station registration API.
- Additional animal station types.
- More utility upgrades and throughput controls.

## Roadmap Rules

- Keep the `delightful_kitchen` identity stable unless a roadmap plan explicitly changes it.
- Do not build the full recipe book UI until queue execution and utilities are stable.
- Do not implement Food Pouch before the core kitchen loop and recipe book UI are usable.
- Do not bundle Kitchen Cat with Milking Machine / Chicken Nest implementation.
- Treat `Plans.md` as historical notes, not the source of truth.

## Open Decisions

- Exact standalone station queue API shape.
- Exact reservation model for water, ice, fuel, containers, and output storage.
- Exact Food Pouch custom inventory serialization format.
- Whether third-party adapter/station registration starts as an internal API only or a public integration API.
