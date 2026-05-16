# Standalone Farmer's Delight Kitchen Design

Date: 2026-05-16

## Purpose

CfB-Delight will become a standalone Farmer's Delight focused kitchen mod. It should replace the Cooking for Blockheads style workflow with a room-based multiblock kitchen, a recipe planner that understands Farmer's Delight cooking chains, and kitchen-specific storage and automation.

The current Cooking for Blockheads integrations in the repository are prototype code. The standalone kitchen core should not depend on Cooking for Blockheads APIs for scanning, planning, storage, or execution.

## Scope

The first kitchen-core milestone includes:

- Kitchen Floor
- Chef's Counter
- Cabinet of Holding
- Container Storage
- Fuel Provider
- Auto Cutter
- Pot Stand
- Grill
- Mop

The Curios pouch is documented as a later milestone and should not block the kitchen-core implementation.

## Kitchen Boundary

The kitchen is controlled through a Chef's Counter. A counter belongs to the connected Kitchen Floor component directly below it. If there is no Kitchen Floor below the counter, the counter has no active kitchen and should show a clear "missing kitchen floor" blocker.

Kitchen Floor is always a real CfB-Delight block. Without customization, its top face uses a wooden parquet texture. Right-clicking the floor with a block copies that block's top appearance onto the Kitchen Floor top face only. The sides and bottom remain identifiable as Kitchen Floor. Using a Mop on a customized Kitchen Floor clears the copied appearance and restores the default parquet top.

For every tile in the connected Kitchen Floor footprint, the kitchen scans blocks from one to six blocks above the floor tile. Compatible inventories, stations, fuel providers, container stores, and item-handler inventories in that volume are members of the kitchen. Walls, ceilings, doors, and enclosure detection do not affect membership.

Multiple Chef's Counters on one connected floor component are allowed in v1, but they share the same footprint and inventory service. The UI should warn that multiple counters are present. Explicit ownership can be added later if shared counters become confusing.

## Inventory Model

The kitchen has a central KitchenInventoryService. It builds a normalized view over all discovered kitchen inventories and provides counting, extraction, insertion, and routing.

Discovered inventory roles:

- General item providers: chests, barrels, cabinets, item-handler inventories, and suitable machine inventories above Kitchen Floor.
- Cabinet of Holding: merged kitchen inventory terminal.
- Container Storage: preferred source and storage for bowls, bottles, and serving containers.
- Fuel Provider: preferred source and storage for burnable fuel.
- Stations: blocks that accept queued recipe steps.

Cabinet of Holding is not just a large chest. It exposes the whole kitchen inventory to the player as one merged inventory. Taking an item pulls it from whichever kitchen inventory contains it. Inserting an item routes it first to an inventory that already contains the same stack type, then to a compatible empty slot elsewhere in the kitchen.

Container Storage and Fuel Provider participate in the merged inventory, but the planner and stations prefer them for their specialized item classes.

Output insertion follows the same routing policy: merge with existing stacks first, then fill compatible empty slots, then keep output in the station if no kitchen storage can accept it.

## Stations

Stations expose a common KitchenStation interface. A station reports:

- supported station type or recipe types
- queue capacity
- whether it can accept a planned step
- fuel requirements
- container requirements
- current running and blocked state

Initial stations:

- Auto Cutter executes Farmer's Delight cutting recipes. It has a built-in knife/tool concept.
- Pot Stand executes Farmer's Delight cooking pot recipes. It pulls serving containers and fuel through kitchen services.
- Grill executes campfire, stove, or skillet-style cooking recipes that the Grill adapter supports. It pulls fuel through kitchen services.

Stations process asynchronously. They should not drop completed outputs merely because the kitchen is full. If output routing fails, the station keeps output internally where possible and reports an output-blocked state.

## Recipe System

Chef's Counter indexes all loaded recipes whose result item is edible food. Those edible outputs appear in the main recipe book grid.

Executable planning is limited to recipe types with registered RecipeAdapters. Initial adapters:

- vanilla shaped crafting
- vanilla shapeless crafting
- Farmer's Delight cutting
- Farmer's Delight cooking pot
- campfire cooking for Grill
- furnace or smoker style cooking only if a v1 station explicitly supports it

A RecipeAdapter normalizes a concrete recipe into:

- input ingredients
- output stacks
- required station type, if any
- optional serving container
- optional fuel requirement
- processing time
- whether the step is instant or asynchronous

The recipe system must be modular. Other mods should be able to add adapters and stations later without changing Chef's Counter logic.

## Recipe Planning

The RecipePlanner builds dependency chains from the current KitchenInventoryService snapshot. The main recipe book shows final edible outputs, while intermediates are shown in the selected recipe details panel.

A final food has one of these states:

- Ready: all inputs exist and the required station or crafting executor exists.
- Craftable with intermediates: missing inputs can be produced by supported intermediate recipes from available kitchen items.
- Blocked: raw ingredients, containers, fuel, station support, output space, or a supported recipe path are missing.

When a player selects a food, Chef's Counter shows the chosen plan: raw inputs, intermediate steps, stations used, fuel, containers, blockers, expected output count, and quantity controls.

For v1, the planner chooses one deterministic best path:

1. shortest supported dependency chain
2. fewer queued station jobs
3. fewer total raw ingredient stacks consumed

Alternate path selection can be added later.

## Confirmation and Consumption

Planning is read-only. No items are consumed while calculating craftability or displaying details.

When the player confirms a plan, the kitchen validates that each required station can accept its queued steps and that required storage routes are available. The system should avoid partial item loss:

- Abort before consuming inputs for a job if the required station cannot accept that job.
- Reserve or extract inputs in a controlled order.
- Prefer to fail with a player-readable blocker instead of silently consuming partial plans.
- If kitchen membership changes during confirmation, rebuild the snapshot and ask the player to confirm again.

## Chef's Counter UI

Chef's Counter is the cooking command center, not a storage UI.

The UI has a recipe book grid and a selected recipe details panel. The grid shows edible final outputs from all loaded recipes. It supports search, category filters, and craftability filters:

- all foods
- ready now
- craftable with intermediates
- blocked

Each recipe icon has a small status overlay.

The details panel shows:

- selected output
- quantity controls
- final craftability state
- dependency chain
- intermediate outputs that will be crafted
- required stations
- required fuel and containers
- missing blockers
- queue or confirm button when craftable
- queued, running, completed, or blocked status after confirmation

The UI should be guided batch automation. It should show the plan before queueing, then enqueue intermediate steps before final steps after confirmation.

## Cabinet of Holding UI

Cabinet of Holding is the merged kitchen storage UI. It should feel like one inventory for the whole kitchen.

The first version should support:

- searchable item grid
- taking items from the merged kitchen inventory
- inserting items into the routed kitchen inventory
- stack merging before filling empty slots

Sorting, compacting, and advanced filters can be added later.

## Blocks and Items

Kitchen Floor defines the kitchen footprint and supports top-face camouflage.

Chef's Counter controls recipe planning and queue confirmation.

Cabinet of Holding exposes the merged kitchen inventory.

Container Storage specializes in bowls, bottles, and other serving containers.

Fuel Provider specializes in burnable fuels and supplies stations.

Auto Cutter queues and processes cutting steps.

Pot Stand queues and processes cooking pot steps.

Grill queues and processes grillable cooking steps.

Mop resets Kitchen Floor camouflage.

## Later Milestone: Curios Pouch

The pouch is a separate later module. It should be documented now but implemented after the kitchen core.

Target behavior:

- Curios-compatible wearable inventory.
- Upgrade tiers with 18, 36, and 72 slots.
- Stack multipliers of 1x, 4x, and 16x.
- Auto-feeding behavior inspired by SophisticatedBackpacks.
- Configurable food choice and feeding thresholds.

The pouch should be designed independently from the kitchen planner, though later integration with kitchen food preferences may be useful.

## Extension Points

RecipeAdapter converts concrete recipe types into normalized planning data.

KitchenStation accepts queued planned steps and executes them.

KitchenInventoryEndpoint wraps inventories and item handlers for consistent reading, extraction, and insertion.

KitchenInventoryService coordinates inventory endpoints and item routing.

RecipePlanner consumes normalized recipes and inventory snapshots to produce player-readable plans.

These extension points should be internal-first but shaped so other mods can register support later.

## Error Handling

Errors should be player-readable and non-destructive.

Planner blockers should distinguish at least:

- missing raw ingredients
- missing intermediate recipe support
- missing station
- missing fuel
- missing container
- full output storage
- station queue full
- kitchen changed during confirmation

Stations that cannot output should report output blocked and hold items internally where possible.

## Testing Strategy

Planner and inventory behavior should be covered with focused unit-style tests where possible:

- inventory counting
- ingredient matching
- multi-step dependency resolution
- blocker classification
- output routing
- insert and extract behavior through Cabinet of Holding

Game tests should cover:

- connected Kitchen Floor footprint scanning
- vertical scan from one to six blocks above floor tiles
- station discovery
- basic queue execution
- Container Storage and Fuel Provider preference
- Cabinet of Holding merged insert and extract
- Kitchen Floor camouflage
- Mop reset behavior

## Open Implementation Notes

The repository already has assets and block shells for several planned blocks. Existing CfB-specific recipe handlers and kitchen processor integrations should be replaced or retired as the standalone core is implemented.

The first implementation plan should start with the standalone kitchen boundary and inventory service before rebuilding Chef's Counter recipe planning.
