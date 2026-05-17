# Mod Rename & Kitchen Utilities Specification

Date: 2026-05-16
Status: Rename complete; Water Provider and Freezer complete for Milestone 3 v1

## Part 1: Mod Rename — Delightful Kitchen

> **Note:** The name "Delightful" is already taken by an existing Farmer's Delight addon mod. The new name is **Delightful Kitchen** to avoid conflict.

The rename portion of this specification has been implemented. The Water Provider and Freezer portions are implemented for Milestone 3 v1 with explicit vanilla container handling.

The project has not had a public release, so the rename is allowed to be a breaking prototype-world change.

### New Identity

The mod is renamed from **CfB-Delight** to **Delightful Kitchen**.

This reflects the project's transition away from Cooking for Blockheads compatibility toward a standalone Farmer's Delight kitchen mod. The old name tied the mod to CfB; the new name stands on its own.

### What Changes

| Property | Old Value | New Value |
|----------|-----------|-----------|
| Mod ID | `cfb_delight` | `delightful_kitchen` |
| Mod name | `CfB-Delight` | `Delightful Kitchen` |
| Java package | `net.inkyquill.cfbdelight` | `net.inkyquill.delightfulkitchen` |
| Resource namespace | `cfb_delight` | `delightful_kitchen` |
| Creative tab ID | `cfb_delight` | `delightful_kitchen` |
| Gradle property `mod_id` | `cfb_delight` | `delightful_kitchen` |
| Gradle property `mod_name` | `CfB-Delight` | `Delightful Kitchen` |
| Gradle property `mod_group_id` | `net.inkyquill` | `net.inkyquill` (unchanged — group stays, artifact is the sub-path) |
| `neoforge.mods.toml` | references `cfb_delight` | references `delightful_kitchen` |
| All resource paths | `assets/cfb_delight/` →, `data/cfb_delight/` → | `assets/delightful_kitchen/` →, `data/delightful_kitchen/` → |
| All lang keys | `block.cfb_delight.*`, `item.cfb_delight.*`, etc. | `block.delightful_kitchen.*`, `item.delightful_kitchen.*`, etc. |
| All registered names | `cfb_delight:*` | `delightful_kitchen:*` |
| All tag paths | `data/cfb_delight/tags/` | `data/delightful_kitchen/tags/` |
| README and Plans.md | references to CfB-Delight | references to Delightful Kitchen |
| Mod class | `CfBDelight.java` | `DelightfulKitchen.java` |

### Data Migration

Existing prototype worlds with the old mod ID (`cfb_delight`) will lose old blocks and items after the rename. This is acceptable because the mod has not been publicly released.

No old-mod-ID alias or registry remapping layer is required for this rename milestone. A migration layer would add complexity without protecting public worlds.

The changelog/README should warn that pre-rename development worlds are incompatible.

### What Does NOT Change

- The `cookingforblockheads` dependency stays in `neoforge.mods.toml` until the dedicated dependency-removal plan retires Java imports. The dependency is required only for legacy code that references CfB APIs.
- The `farmersdelight` dependency stays.
- The project directory name (`cfb-delight`) can stay as-is (GitHub repo name) or be renamed separately — repo naming is a human concern, not a code concern.
- Existing Java class names (e.g., `ModBlocks`, `ModItems`, `KitchenScanner`) do not need to change — only their package and import paths move.

---

## Part 2: Kitchen Utilities — Water & Ice Provision

### Purpose

Cooking for Blockheads provides a Sink (infinite water) and a Fridge (cold storage, ice access) as kitchen utilities. These satisfy water and cold-ingredient requirements in recipes without the player manually filling buckets.

Delightful Kitchen needs equivalent blocks:

- **Water Provider** — satisfies water requirements in kitchen recipes.
- **Freezer** — satisfies ice and snowball requirements in kitchen recipes.

Both blocks function as **virtual resource providers**. They do not create actual item stacks out of thin air — instead, they require the kitchen to have empty containers (buckets, bottles) in storage. The provider "borrows" the container, fills it virtually during recipe execution, and returns the empty container afterward. No buckets or bottles are permanently created or destroyed; the only net change is water/ice being consumed from the provider.

This design avoids the exploit of generating infinite buckets/bottles while still giving the kitchen practical infinite water/ice for cooking.

The utilities milestone depends on the standalone queue execution and resource reservation model. The Water Provider and Freezer should not be implemented as isolated scanner booleans only; they need to participate in planner blockers, confirmation-time reservation, and execution-time release of borrowed containers.

### Design Principle: Container-Neutral Provision

The core rule: **a provider only works if the kitchen has containers to carry the resource.**

- For water: empty buckets or empty glass bottles must exist in Container Storage (or general kitchen inventory).
- For ice: empty buckets (to carry water that gets frozen) must exist.
- For snowballs: empty glass bottles (to carry water that gets snowified) must exist.

The container is never consumed — it is reserved during the recipe, used as the transport medium, and returned empty to the same storage afterward. The resource (water, cold) comes from the provider block itself, which is connected to an infinite source (water provider) or generates it (freezer).

If the kitchen lacks the required empty containers, recipes that need those resources show as **blocked: missing water container** or **blocked: missing ice container** in the Chef's Counter UI.

---

## Water Provider

### Block

**Block ID:** `water_provider`

A decorative kitchen sink/well block. Visually resembles a stone or ceramic basin with water inside, mounted on a counter-height pedestal.

Placed in the kitchen footprint (above Kitchen Floor). Only one Water Provider is needed per kitchen — multiple providers do not stack or increase throughput; they simply provide redundancy.

**Crafting recipe:**

```
  I I I
  B W B
  S S S
where I = Iron Ingot, B = Iron Bars, W = Water Bucket, S = Stone

Yield: 1 Water Provider
```

### How It Works

The Water Provider is discovered by the kitchen scanner as a **utility block** (distinct from stations and storage). It does not have a UI or internal inventory — it is purely a presence-based service.

When the Chef's Counter recipe planner evaluates a recipe that requires water:

1. Check: is a Water Provider present in the kitchen?
2. Check: does Container Storage (or kitchen inventory) contain at least one empty **bucket** or empty **glass bottle**?
3. If yes → water requirement is satisfied. Reserve one empty container for the duration of the recipe step.
4. During recipe execution: the container is "filled" (virtually), water is consumed in the recipe, the container is returned empty to the same storage it came from.
5. If multiple recipes need water simultaneously, multiple containers are reserved (one per concurrent water-using step).

The container is the **limiting factor**: a kitchen with 3 empty buckets can run up to 3 water-using recipe steps in parallel. The water itself is infinite.

### What Counts As "Water" In Recipes

The planner resolves direct shaped/shapeless crafting ingredients for `minecraft:water_bucket` against the Water Provider when no physical water bucket is available. Example recipe concepts that need water:

- Farmer's Delight dough (wheat + water)
- Farmer's Delight pasta (flour + water + egg)
- Vanilla cake (milk + sugar + egg + wheat — milk is separate from water)
- Any modded recipe that declares a water ingredient

Water is distinct from milk (provided by the Milking Machine) and from other fluids.

### Integration With Kitchen Inventory Service

The Water Provider is NOT an inventory endpoint — it does not hold items. It is a capability block that reports:

- `canProvideWater() → boolean` — true when the block is intact.
- `getMaxConcurrentWaterUses() → int` — derived from available empty containers in the kitchen.

The Chef's Counter planner queries the Water Provider during recipe evaluation. The Kitchen Inventory Service is responsible for counting and reserving empty containers (buckets, bottles) in Container Storage.

### Visual States

| State | Appearance |
|-------|-----------|
| Idle | Water visible in basin. Subtle water animation. |
| In use | Water ripples / splash particles when a recipe is actively using water. |
| No containers available (planner blocked) | No visual change — the block itself is fine. The UI shows the blocker. |

---

## Freezer

### Block

**Block ID:** `freezer`

A cold storage block that provides ice and snowballs for kitchen recipes. Visually resembles an ice chest or freezer unit with frost on the exterior.

Placed in the kitchen footprint. One Freezer is sufficient per kitchen.

**Crafting recipe:**

```
  I I I
  I P I
  I S I
where I = Iron Ingot, P = Packed Ice, S = Snow Block

Yield: 1 Freezer
```

### How It Works

The Freezer provides two resources: **ice** and **snowballs**. Both are derived from freezing water, so the Freezer depends on the Water Provider being present.

When the Chef's Counter planner evaluates a recipe that requires ice or snowballs:

1. Check: is a Freezer present in the kitchen?
2. Check: is a Water Provider present?
3. For ice: does Container Storage contain at least one empty **bucket**?
4. For snowballs: does Container Storage contain at least one empty **glass bottle**?
5. If all checks pass → ice/snowball requirement is satisfied. Reserve the container.
6. During recipe execution: water is drawn from the Water Provider, frozen by the Freezer, used in the recipe, the container is returned empty.

### Ice Production Cycle

The Freezer has two related modes:

1. **Virtual recipe provision** — the primary mode. During queued recipe execution, the Freezer borrows a container, uses Water Provider access, provides ice/snow to the recipe step, and returns the container.
2. **Passive physical production** — the secondary mode. When not being used for queued recipes, it slowly generates physical ice and snowballs using available water and containers. This lets the player build up a buffer of ice items for recipes that consume physical ice rather than a virtual cold resource.

Virtual recipe provision should be implemented first because it uses the same reservation model as queue execution. Passive production can be added after that path is stable.

- **Cycle duration**: 1200 ticks (60 seconds) per unit.
- **Input per cycle**: 1 empty bucket (for ice) OR 1 empty glass bottle (for snowball). Water is drawn from the Water Provider.
- **Output per cycle**: 1 ice (from bucket path) OR 1 snowball (from bottle path).
- **Internal storage**: 9 slots. Ice stacks to 64; snowballs stack to 16.
- **Priority**: recipe provision takes priority over passive production. If a recipe needs ice, any queued passive production is interrupted.

The Freezer alternates between producing ice and snowballs round-robin by default. The player can configure passive production with a simple UI toggle.

### Freezer Inventory

Accessed via right-click UI:

| Section | Slots | Purpose |
|---------|-------|---------|
| Output | 9 | Ice and snowballs produced passively. Player can extract. |

There are no input slots — the Freezer pulls water and containers from the kitchen.

### What Counts As "Ice" / "Snowball" In Recipes

Recipe adapters declare that a recipe step requires `ice` or `snowball`. Examples:

- Ice: chilling recipes, certain desserts, ice cream bases.
- Snowball: certain modded foods, snow cones, cold soups.
- Farmer's Delight compatible recipes that reference ice or snow ingredients.

### Visual States

| State | Appearance |
|-------|-----------|
| Idle | Frost on exterior. Subtle cold particles. |
| Active (recipe provision) | Frost intensifies. Blue glow. |
| Passive production running | Slow frost buildup animation. Output item visible in a small window. |
| Blocked (no containers) | Dim blue indicator. No production. |
| Blocked (no Water Provider) | Red indicator. No water source available. |

### Freezer UI

A simple 9-slot output grid. A toggle button switches the passive production mode:

- **Ice & Snow** (default): alternates between producing ice and snowballs.
- **Ice Only**: only produces ice (requires buckets).
- **Snow Only**: only produces snowballs (requires bottles).
- **Paused**: no passive production. Still provides for recipes.

---

## Kitchen Scanner Changes

The kitchen scanner must recognise two new utility block types when scanning above Kitchen Floor:

- `water_provider` — tagged or checked by block type as a water utility.
- `freezer` — tagged or checked by block type as a cold utility.

These blocks are NOT added as `memberBlocks` in the scan result (they are not inventory containers). Instead, they are recorded in a new field:

```java
record KitchenScanResult(
    boolean hasKitchenFloor,
    Set<BlockPos> floorTiles,
    List<BlockPos> memberBlocks,
    List<BlockPos> counters,
    // new fields:
    boolean hasWaterProvider,
    boolean hasFreezer
) { ... }
```

The Chef's Counter UI updates to show utility availability alongside floor/member counts.

---

## Recipe Planner Changes

The Recipe Planner gains resource checks during recipe evaluation. Milestone 3 v1 applies these checks to shaped/shapeless crafting ingredients for `minecraft:water_bucket`, `minecraft:ice`, and `minecraft:snowball`.

### Water Check

For a shaped/shapeless crafting step that is missing a direct `minecraft:water_bucket` ingredient:

1. Query kitchen scan: `hasWaterProvider()`?
2. Query inventory: count empty buckets + empty glass bottles in Container Storage (preferred) or general kitchen inventory.
3. If ≥ 1 container → water is available. The planner allocates one empty container as reserved for this step.
4. If 0 containers → blocker: `missing_water_container`.

### Ice / Snowball Check

For a shaped/shapeless crafting step that is missing a direct `minecraft:ice` or `minecraft:snowball` ingredient:

1. Query kitchen scan: `hasFreezer()` AND `hasWaterProvider()`?
2. For ice: count empty buckets in Container Storage.
3. For snowball: count empty glass bottles in Container Storage.
4. If ≥ 1 matching container → ice/snowball is available. Allocate one container.
5. If Freezer or Water Provider missing → blocker: `missing_freezer` or `missing_water_provider`.
6. If no container → blocker: `missing_ice_container` or `missing_snowball_container`.

### Resource Allocation

During planning, the simulated snapshot reserves allocated containers so another step cannot use the same container. During confirmation, the executor revalidates the kitchen, borrows the real container, executes the instant crafting step, and returns the container.

If a player manually removes the container from storage while a recipe is queued, confirmation fails with a blocker and the player must re-plan/re-confirm.

---

## Empty Container Tracking

Container Storage is the preferred source for empty containers. Its role, already defined in the kitchen design spec, now extends to:

- Reporting available empty bucket count to the planner.
- Reporting available empty bottle count to the planner.
- Reserving containers during recipe execution.
- Receiving returned containers after recipe completion.

If Container Storage is not present, the planner falls back to counting empty buckets/bottles across all general kitchen inventories (Cabinet of Holding, chests, barrels). This fallback works but may cause contention if multiple recipes compete for the same containers.

---

## Limits And Edge Cases

### Preventing Infinite Containers

The core guard: containers are borrowed and returned, never created. The only way a new bucket or bottle enters the kitchen is through player action (crafting, looting, trading). The Water Provider and Freezer never generate containers.

### Concurrent Recipe Steps

If 5 recipes need water simultaneously but only 3 empty buckets exist, only 3 steps can be queued as "ready." The remaining 2 show as "blocked: missing water container." As steps complete and containers are returned, the blocked steps automatically unblock on the next planner cycle.

### Container Storage Full

If Container Storage is full when a container needs to be returned after a recipe, the container is routed to general kitchen inventory (Cabinet of Holding or nearest chest). If that is also full, the container is dropped at the Water Provider or Freezer position as a world item (worst case — but extremely unlikely for a single bucket/bottle).

### Mod Compatibility

Milestone 3 v1 intentionally uses explicit vanilla empty containers only:

- Water Provider borrows `minecraft:bucket` or `minecraft:glass_bottle`.
- Freezer ice provision borrows `minecraft:bucket`.
- Freezer snowball provision borrows `minecraft:glass_bottle`.

Custom tagged containers are deferred until custom container semantics are designed. The utility system must know how a custom container is filled, emptied, returned, and represented in recipes before public compatibility tags can be a truthful extension point.

### Multiple Water Providers / Freezers

Multiple Water Provider blocks in one kitchen do not increase water capacity — one infinite source is enough. Multiple Freezers provide redundancy only; production speed does not stack (one freezer is enough for any kitchen). Future upgrades could allow parallel Freezer production.

---

## Testing Strategy

### Game Tests

- Place Water Provider in kitchen. Place empty bucket in Container Storage. Queue a water-requiring recipe at Chef's Counter. Verify recipe becomes craftable.
- Remove bucket from Container Storage. Verify recipe becomes blocked with "missing water container."
- Place Freezer + Water Provider in kitchen. Queue an ice-requiring recipe. Verify craftable when empty bucket is available.
- Verify Freezer passive production generates ice over time when Water Provider is present and buckets are in storage.
- Verify Freezer passive production halts when no empty buckets are available.
- Verify Freezer passive production halts when Water Provider is removed.
- Verify empty bucket is returned to Container Storage after water-using recipe completes.
- Verify multiple concurrent water-using recipes consume multiple containers and return them all.
- Verify Freezer snowball production uses glass bottles correctly.
- Verify recipe is blocked when Freezer present but Water Provider missing.
- Verify mod rename: check that mod ID `delightful_kitchen` is the active ID, old resource paths do not remain in generated output, and no unintended `cfb_delight` references remain.
