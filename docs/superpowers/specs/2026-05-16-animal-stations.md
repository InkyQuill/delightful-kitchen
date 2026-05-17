# Animal Stations Specification

Date: 2026-05-16
Status: Draft

## Purpose

Animal Stations are kitchen blocks and entities that bring animals into the kitchen automation fold. Production stations (Milking Machine, Chicken Nest) capture animals and produce resources automatically when fed. The Kitchen Cat is a living mascot that roams the kitchen and provides buffs when cared for.

The production stations are inspired by Cooking for Blockheads' Cow-In-A-Jar. The Kitchen Cat draws from vanilla tamed cat mechanics but reimagines the cat as a permanent kitchen companion.

## Scope

This specification describes the animal feature family, but it should be implemented as separate roadmap milestones:

1. **Animal Production Stations** — Milking Machine and Chicken Nest.
2. **Kitchen Cat** — cat entity behavior, decorative blocks, buffs, and gifts.

The production stations should be implemented first. Kitchen Cat should come later because it involves entity AI, kitchen-bound roaming, buffs, and additional decorative/storage blocks.

Three animal features are defined here:

- **Milking Machine** — captures a cow; consumes hay/wheat; produces milk buckets.
- **Chicken Nest** — captures a chicken; consumes seeds; produces eggs.
- **Kitchen Cat** — a later milestone: a tamed cat that becomes a permanent kitchen mascot. Roams the kitchen, eats from a Food Bowl, provides buffs to players in the kitchen, and occasionally finds small ingredients.

Shared behaviour across production stations (feed consumption, output routing, animal capture/release, kitchen integration) is defined once. The Kitchen Cat has its own mechanics described in its dedicated section.

Production stations depend on the standalone kitchen inventory and queue/resource systems. They should pull feed and push output through the same post-CfB kitchen services used by Auto Cutter, Grill, and Pot Stand, not through Cooking for Blockheads APIs.

Not in this milestone:

- Sheep shearer, pig station, or other animal types.
- Breeding automation.
- Butchering stations.

---

## Roadmap Milestone: Animal Production Stations

This milestone contains Milking Machine and Chicken Nest only. It should happen after the core kitchen queue execution path is stable, because both stations depend on kitchen feed pulling and output routing.

## Milking Machine

### Structure

The Milking Machine is a constructed multiblock: the player places blocks to form a 2-wide × 1-deep × 2-tall enclosure. The bottom row consists of two **Milking Machine Base** blocks placed side by side. The top row consists of two **Milking Machine Bars** blocks placed directly above the bases.

Any complete 2×1×2 arrangement of these four blocks forms a valid machine. The machine has a horizontal facing direction (the side the cow enters from), determined by the base block the player placed second.

**Block IDs:**

| Block                     | Description                                      |
|---------------------------|--------------------------------------------------|
| `milking_machine_base`    | Bottom half. Two side-by-side form the floor.    |
| `milking_machine_bars`    | Top half. Two side-by-side form the enclosure.   |

The **base** block is the controller — it holds the block entity, the captured cow reference, the internal inventory, and the feed/output slots. The bars blocks are decorative extensions that exist to complete the visual structure; they store no data and simply check that their base is still present.

If either bars block is broken, the structure invalidates, the cow is released, and the base reverts to a "no cow" state (but keeps its feed and milk inventory). If a base block is broken, both bars blocks pop off as items and the cow is released.

### Capturing A Cow

A cow is captured by leading it (or having it walk) into the 1-block-deep gap in front of the open side of the structure. When a living `minecraft:cow` entity is within the structure's interior volume and the machine has no cow already, the cow is consumed (despawned) and the machine enters the "occupied" state. The base block renders a cow model inside the enclosure.

The player can also capture a cow by right-clicking the base block with wheat while a cow is standing inside the structure volume.

If the structure is incomplete (missing bars or bases), the cow is not captured.

### Releasing The Cow

Breaking either base block (or both) releases the cow: a new `minecraft:cow` entity spawns at the machine's position with full health. The feed inventory and milk output items remain in the block entity and drop as separate item entities.

Right-clicking the machine with an empty hand while sneaking also releases the cow (if the machine contains one). This is the non-destructive release method.

### Production

While occupied, the Milking Machine runs a production cycle:

- **Cycle duration**: 1200 ticks (60 seconds) per milk bucket.
- **Feed consumed per cycle**: 1 wheat or 1 hay bale. Hay bales are equivalent to wheat for feed purposes. Any item in the `delightful_kitchen:cow_feed` item tag counts as valid feed.
- **Output per cycle**: 1 milk bucket.
- **Internal feed storage**: 1 slot (stack of wheat/hay up to 64).
- **Internal output storage**: 16 slots, each holding 1 milk bucket (milk bucket does not stack in vanilla; 16 slots = 16 buckets).

### Feed Consumption

The machine pulls feed from two sources in priority order:

1. **Internal feed slot** — if the machine has wheat/hay already in its dedicated feed slot, consume from there.
2. **Kitchen inventory** — if the internal slot is empty, request wheat or hay from the kitchen inventory service (pulling from Cabinet of Holding, Container Storage, or any general kitchen inventory). The machine does not pull from other stations.

If neither source has feed, the cycle pauses. The machine remains occupied and resumes when feed becomes available.

### Output Routing

When a milk bucket is produced, the machine pushes it to a destination in priority order:

1. **Kitchen Cabinet of Holding** — preferred output target. Insert via kitchen inventory service.
2. **Adjacent kitchen inventories** — any container block that is a kitchen member (chest, barrel, Container Storage).
3. **Internal output slots** — if no external destination accepts the bucket, store it in the machine's own 16-slot output buffer.
4. **Halt production** — if the output buffer is full (16 buckets), the machine skips the next cycle entirely and does NOT consume feed.

Player can extract milk buckets from the machine's output buffer either by opening its UI or by piping items out (hopper, etc.).

### Machine Inventory

The base block's internal inventory, accessed via UI:

| Section    | Slots | Purpose                                     |
|------------|-------|---------------------------------------------|
| Feed       | 1     | Wheat, hay bale, or tagged cow_feed items   |
| Output     | 16    | Milk buckets produced by the machine         |

The UI shows whether the machine is occupied (cow present) and the progress toward the next milk bucket (as an arrow or progress bar).

### Visual States

| State                      | Appearance                                                    |
|----------------------------|---------------------------------------------------------------|
| Incomplete structure       | Base block alone, no bars. Dull/missing texture variant.      |
| Complete, empty            | Full 2×1×2 structure, empty interior.                        |
| Complete, occupied         | Cow model visible inside the bars. Happy/idle animation.     |
| Occupied, working          | Cow model + particles (milk drips). Progress indicator.      |
| Occupied, output full      | Cow model. Red indicator light or similar "blocked" signal.  |
| Occupied, no feed          | Cow model. Yellow indicator or "hungry" particles.          |

### Crafting Recipe

```
Milking Machine Base:
  I I I
  B W B
  I P I
where I = Iron Ingot, B = Iron Bars, W = Water Bucket, P = Iron Pressure Plate

Yield: 2 Milking Machine Base blocks (enough for one machine)
```

```
Milking Machine Bars:
  I I I
  I   I
  I I I
where I = Iron Bars

Yield: 2 Milking Machine Bars blocks (enough for one machine)
```

---

## Chicken Nest

### Structure

The Chicken Nest is a single block — no multiblock. It is placed on any solid surface in the kitchen (above Kitchen Floor).

**Block ID:** `chicken_nest`

### Capturing A Chicken

A chicken is captured by leading it (or having it walk) onto the nest block. When a living `minecraft:chicken` entity is standing on the nest block and the nest has no chicken already, the chicken is consumed (despawned) and the nest enters the "occupied" state. The block renders a chicken sitting in the nest.

The player can also right-click the nest with seeds while a chicken is standing on it to force capture.

### Releasing The Chicken

Breaking the nest block releases the chicken: a new `minecraft:chicken` entity spawns at the nest's position. All internal items (feed and eggs) drop as separate item entities.

Right-clicking the nest with an empty hand while sneaking also releases the chicken.

### Production

While occupied, the Chicken Nest runs a production cycle:

- **Cycle duration**: 600 ticks (30 seconds) per egg.
- **Feed consumed per cycle**: 1 seed item. Any item tagged `delightful_kitchen:chicken_feed` counts (wheat seeds, pumpkin seeds, melon seeds, beetroot seeds by default).
- **Output per cycle**: 1 egg.
- **Internal feed storage**: 1 slot (stack of seeds up to 64).
- **Internal output storage**: 64 eggs (eggs stack to 16; 4 slots).

### Feed Consumption

Same priority as Milking Machine: internal feed slot first, then kitchen inventory.

### Output Routing

Same priority as Milking Machine: Cabinet of Holding → adjacent inventories → internal output buffer → halt.

Eggs stack to 16 in vanilla. The nest can hold up to 4 stacks (64 eggs total) in its internal output buffer. When the buffer is full, the cycle halts and no feed is consumed.

### Nest Inventory

| Section    | Slots | Purpose                                     |
|------------|-------|---------------------------------------------|
| Feed       | 1     | Seeds (any tagged `chicken_feed`)           |
| Output     | 4     | Eggs (up to 64 total, stacks of 16)         |

### Visual States

| State                   | Appearance                                                    |
|-------------------------|---------------------------------------------------------------|
| Empty                   | Straw nest, no chicken.                                       |
| Occupied                | Chicken sitting in nest. Idle bobbing animation.             |
| Occupied, working       | Chicken in nest + occasional egg appearing particle.         |
| Occupied, output full   | Chicken + pile of visible eggs in/around nest. Red indicator.|
| Occupied, no feed       | Chicken pecking at empty ground. Yellow indicator.           |

### Crafting Recipe

```
Chicken Nest:
  W W W
  S H S
where W = Wheat, S = Stick, H = Hay Bale

Yield: 1 Chicken Nest
```

---

## Roadmap Milestone: Kitchen Cat

Kitchen Cat is separate from the production-station milestone. It should be planned and implemented after Milking Machine and Chicken Nest, with its own entity AI, blocks, buffs, and tests.

## Kitchen Cat

The Kitchen Cat is not a production machine — it is a living entity, a permanent kitchen mascot that provides buffs when cared for. Unlike the cow and chicken (which are captured inside blocks), the cat roams freely within the kitchen footprint as a modified tamed cat entity.

### Becoming A Kitchen Cat

A Kitchen Cat starts as a normal tamed `minecraft:cat`. To register it as a Kitchen Cat, three conditions must all be met simultaneously:

1. A **Cat Bed** block exists within the kitchen footprint (above Kitchen Floor).
2. A **Food Bowl** block exists within the kitchen footprint.
3. A tamed cat walks into (or is led into) the kitchen volume — the vertical space 1–6 blocks above Kitchen Floor tiles.

When all three conditions are true, the cat entity is converted into a Kitchen Cat. Its AI is replaced with kitchen-specific behaviour, its size is reduced slightly (making it cuter), and it is bound to the kitchen footprint. A subtle particle effect and a chat message confirm the registration: "<cat name> has made the kitchen their home."

The cat retains its collar colour, name, and variant (tabby, tuxedo, etc.). If the cat was sitting when registered, it stands up.

### Unregistering / Release

If BOTH the Cat Bed AND the Food Bowl are broken (or removed from the kitchen footprint), the Kitchen Cat reverts to a normal tamed cat:

- It regains vanilla AI (follows player, sits on command, teleports).
- It can leave the kitchen area.
- It becomes vulnerable to damage again.
- Its size returns to normal.

Breaking only one of the two blocks does NOT release the cat — both must be absent. This prevents accidental release from a creeper explosion taking out one block.

### Properties

| Property        | Value                                                        |
|-----------------|--------------------------------------------------------------|
| Damage          | Immune to all damage (fire, fall, lava, mobs, players)       |
| Despawning      | Never despawns (persistence required = true)                 |
| Size            | ~70% of normal cat size (visibly smaller and cuter)          |
| Movement        | Roams only within kitchen footprint + 1 block margin         |
| Teleportation   | Does not teleport to player. Stays in kitchen.               |
| Breeding        | Cannot be bred while a Kitchen Cat.                          |
| Death           | If somehow killed (commands, bugs), respawns at Cat Bed after 60 seconds with full hunger. |

### Roaming Behaviour

The Kitchen Cat has a custom AI that cycles through these activities:

1. **Wander** (60% weight) — walk to a random position within the kitchen footprint. Sit down for a few seconds, look around. Repeat.
2. **Sleep** (25% weight) — if the Cat Bed is present, walk to it, curl up, and sleep for 30–120 seconds. Occasional purring particles. During sleep, the cat cannot be interacted with (right-click does nothing, but it will wake if the bed is broken).
3. **Perch** (10% weight) — walk to a Chef's Counter, Cabinet of Holding, or any solid block at counter height (Y+1 above floor tile), jump onto it, and sit. The cat watches the kitchen from its vantage point.
4. **Hungry** (5% weight, forced when hunger is below 25%) — walk to the Food Bowl and sit beside it, facing the bowl. The cat meows periodically. This behaviour overrides all others when the cat is hungry.

The cat avoids standing on kitchen stations that are actively working (Auto Cutter, Grill, Pot Stand) — it won't block the player from interacting with them.

### Hunger System

The Kitchen Cat has a hidden hunger value (0–100). Hunger depletes at a rate of **1 point every 600 ticks (30 seconds)** when the cat is awake, and **1 point every 1200 ticks (60 seconds)** when sleeping. At this rate, a fully-fed cat stays satisfied for ~50 minutes of awake time.

| Hunger Range | State     | Behaviour                                              |
|-------------|-----------|--------------------------------------------------------|
| 76–100      | Full      | Normal roaming. Purring particles occasionally.         |
| 51–75       | Content   | Normal roaming, slightly less purring.                  |
| 26–50       | Peckish   | Starts visiting the Food Bowl more often in wander cycle. |
| 0–25        | Hungry    | Sits at Food Bowl, meows. Overrides all other AI.       |

The cat meows at increasing frequency as hunger drops: every 30 seconds at 25 hunger, every 15 seconds at 10 hunger, and every 5 seconds at 0 hunger.

### Feeding

Feeding happens through the **Food Bowl** block, not by right-clicking the cat directly.

**Food Bowl block:** A small decorative block (like a stone or ceramic bowl on the floor). It has one internal slot accepting items tagged `delightful_kitchen:cat_food`. When a food item is placed in the bowl's slot, the cat will pathfind to the bowl and consume it.

**Valid cat food** (item tag `delightful_kitchen:cat_food`):

| Item                        | Restores | Notes                                      |
|-----------------------------|----------|--------------------------------------------|
| Raw Cod / Raw Salmon        | 25       | Basic fish.                                |
| Cooked Cod / Cooked Salmon  | 35       | Cooked fish — better.                      |
| Raw Chicken / Raw Beef / Raw Porkchop / Raw Mutton / Raw Rabbit | 30 | Raw meat.           |
| Cooked Chicken / Cooked Beef / Cooked Porkchop / Cooked Mutton / Cooked Rabbit | 40 | Cooked meat — best natural food. |
| Tropical Fish / Pufferfish  | 15       | Low quality. Pufferfish does NOT poison the cat. |
| Rotten Flesh                | 10       | Barely edible. Cat makes a disgusted face. |
| **Cat Food (crafted)**      | 50       | Dedicated cat food — restores half the bar.|

**Cat Food recipe** (crafted item, `delightful_kitchen:cat_food`):

```
  F F F
  B B B
  W W W
where F = any Cooked Fish, B = Bone Meal, W = Wheat

Yield: 3 Cat Food
```

Cat Food is the most efficient feed — one item restores 50 hunger points. It also gives the cat a temporary "well-fed" glow (sparkle particles) for 2 minutes after eating.

When the cat eats from the bowl:
- The food item is consumed from the bowl's slot.
- The cat's hunger is restored by the item's value (capped at 100).
- The cat plays a happy animation and produces heart particles.
- All buffs are refreshed (see Buffs below).

The bowl can be refilled at any time. The cat eats when it reaches the bowl naturally — there is no "feed now" button. The player's role is to keep the bowl stocked.

### Buffs

When the cat eats from a stocked Food Bowl, it provides buffs to all players currently standing within the kitchen footprint. Buffs last for a fixed duration after the cat eats and can be refreshed by feeding again.

#### Regeneration I (immediate, always active)

Every time the cat eats, all players in the kitchen receive **Regeneration I for 10 seconds**. This is the cat's direct health-giving effect — the kitchen is a place of comfort and recovery.

#### Purrfect Timing (persistent, depends on cat happiness)

While the cat's hunger is above 25 (not hungry), all kitchen stations within the footprint receive a **5% speed bonus** to their processing time. A station that would take 100 ticks takes 95 ticks. This represents the cat's calming presence making the kitchen run more smoothly.

When the cat is hungry (hunger ≤ 25), the bonus is lost until the cat is fed again.

#### Chef's Familiar (persistent, depends on cat happiness)

While the cat's hunger is above 25, players standing in the kitchen have their food saturation drain **10% slower**. Their existing saturation lasts longer — the kitchen feels like a cozy, comfortable place where you don't need to eat as often.

When the cat is hungry, the bonus is lost.

#### Curious Paws (periodic gift-finding)

If a **Bin** block exists within the kitchen footprint and the cat's hunger is above 50, the cat has a chance to find small ingredients and deposit them in the Bin.

**Bin block** (`delightful_kitchen:bin`): A small decorative block (like a wicker basket or metal bin) with 9 internal inventory slots. The cat drops found items into the Bin's inventory. If the Bin is full, items are dropped on the floor beside it.

The Bin can also be used by the player as a small general storage — it's a 9-slot container.

**Gift mechanic:**
- The cat attempts to find a gift every **5–15 minutes** (randomised), while awake and not hungry.
- Roll chance: 40% to find something useful, 60% to find something trivial or nothing.
- The item is chosen from a loot table (`delightful_kitchen:gameplay/kitchen_cat_gifts`).
- When a gift is found, the cat pathfinds to the Bin, deposits the item (with a particle effect), and a subtle chat message appears: "<cat name> found something in the kitchen..."

**Default gift loot table entries:**

| Weight | Item(s) | Category |
|--------|---------|----------|
| 5 | Sugar × 1–3 | Common ingredient |
| 4 | Wheat Seeds × 1–2 | Common ingredient |
| 4 | String × 1 | Trinket |
| 3 | Wheat × 1 | Common ingredient |
| 3 | Egg × 1 | Common ingredient |
| 3 | Bone Meal × 1–2 | Common ingredient |
| 2 | Apple × 1 | Fruit |
| 2 | Sweet Berries × 1–2 | Fruit |
| 2 | Brown Mushroom × 1 | Ingredient |
| 2 | Red Mushroom × 1 | Ingredient |
| 1 | Glow Berries × 1 | Fruit |
| 1 | Rabbit Foot × 1 | Rare trinket |
| 1 | Iron Nugget × 1 | Rare trinket |
| 1 | Gold Nugget × 1 | Rare trinket |
| 10 | Nothing | Miss |

The loot table is defined as a datapack JSON so other mods can add or adjust entries.

### Cat Bed Block

**Block ID:** `cat_bed`

A small decorative block placed on the floor (like a pet bed). The cat sleeps here.

- Occupies 1 block (similar to a carpet — can be slightly lower than a full block).
- Emits soft particles when the cat is sleeping in it (Zzz particles, purr hearts).
- Breaking the bed: if the cat was sleeping, it wakes up. If both bed and bowl are now missing, the cat reverts to normal.

**Crafting recipe:**

```
  W W W
  C C C
where W = any Wool (colour does not affect function; the cat bed inherits the wool colour), C = any Wooden Slab

Yield: 1 Cat Bed
```

### Food Bowl Block

**Block ID:** `food_bowl`

A small decorative block with one inventory slot for cat food.

- Occupies 1 block on the floor.
- Renders with the food item visible inside if stocked (like a flower pot).
- Can be right-clicked by the player to open its single-slot inventory.
- Hopper-compatible: a hopper pointing into the bowl can insert cat food items.
- The cat pathfinds to this bowl when hungry.

**Crafting recipe:**

```
  B B B
  B S B
where B = any Stone (or Stone Brick), S = any Wooden Slab

Yield: 1 Food Bowl
```

### Bin Block

**Block ID:** `bin`

A small decorative storage block where the cat deposits found gifts.

- 9 inventory slots.
- Player can open via right-click.
- Hopper-compatible for item extraction (automation).
- If the KItchen Cat is not present, the Bin functions as a simple 9-slot container.

**Crafting recipe:**

```
  S S S
  I   I
  I I I
where S = Stick, I = Iron Ingot

Yield: 1 Bin
```

### Kitchen Cat AI States Summary

```
  [Wandering]
    |  60% chance per decision
    v
  [Sleeping] ←── if Cat Bed exists, 25% chance
    |
    v
  [Perching] ←── if perch point found, 10% chance
    |
    v
  [Hungry] ←── forced when hunger ≤ 25
    |  (sits at Food Bowl, meows)
    |  if food in bowl → eat → buffs → resume wandering
    v
  [Finding Gift] ←── if Bin exists & hunger > 50 & timer expired
    |  (pathfinds to Bin, deposits item)
    v
  [Wandering] (loop)
```

### Visual Design Notes

- Kitchen Cat model: 70% scale, slightly larger eyes, softer fur texture than vanilla cat.
- Cat Bed: a round/oval cushioned bed, wool colour matches the crafting wool. A small name tag plaque shows the cat's name.
- Food Bowl: ceramic or stone bowl on the floor. Food item rendered inside at reduced scale.
- Bin: wicker basket or wire-mesh bin, about 0.7 blocks tall.
- Particles: purring → heart particles; gift found → sparkle trail from cat to Bin; well-fed → golden sparkles around cat for 2 min after eating Cat Food.

---

## Shared Production Station Behaviour

The following applies to the Milking Machine and Chicken Nest only. The Kitchen Cat has its own mechanics.

### Kitchen Integration

Both production stations are kitchen members when placed above Kitchen Floor. The kitchen scanner discovers them and they participate in:

- **Feed pulling**: stations request feed items (`cow_feed` / `chicken_feed` tags) from the kitchen inventory service. They do NOT pull from other stations — only from storage blocks (Cabinet of Holding, Container Storage, chests, barrels).
- **Output pushing**: produced items (milk buckets, eggs) are routed back into the kitchen inventory. Cabinet of Holding is preferred.

If the station is not in a kitchen (no Kitchen Floor below, or kitchen scan finds no inventory members), it can still function using only its internal feed slot and output buffer. It just won't pull feed from or push output to the kitchen network.

### Block Entity Pattern

Both station block entities follow the project's existing `Container + MenuProvider + Nameable` pattern (as seen in `FuelProviderBlockEntity`):

- Implement `Container` for item access.
- Register a `serverTick` that runs the production cycle.
- Save/load inventory via `ContainerHelper` and NBT.
- Drop contents on break via a `dropContents` helper.
- Open a UI when right-clicked (except for capture/release interactions).

### Animal Persistence

The captured animal's identity is stored as an enum or registry reference on the block entity:

- `animal_type`: `"cow"` or `"chicken"` (or `"none"`)
- This persists across chunk unloads and server restarts.
- The animal is NOT stored as an NBT entity — just the type.

On structure validation (for milking machine), if the multiblock is broken and rebuilt, a previously-occupied base block retains its cow state and resumes production once the structure is complete again.

### Feed Tags

Two item tags define valid feed:

- `delightful_kitchen:cow_feed` — default entries: `minecraft:wheat`, `minecraft:hay_block`.
- `delightful_kitchen:chicken_feed` — default entries: `minecraft:wheat_seeds`, `minecraft:pumpkin_seeds`, `minecraft:melon_seeds`, `minecraft:beetroot_seeds`.

Other mods can add to these tags via datapack.

### GUI

Both stations share a simple UI with:

- Feed slot (accepts only tag-matched items).
- Output slots (extract-only from machine side; player can take items out).
- Progress arrow showing time to next output.
- Occupied indicator (icon or label showing animal type).
- Status indicator: working / no feed / output full.

---

## Error Handling & Edge Cases

### Animal Escape

- If the chunk unloads while the machine is occupied, the animal type persists. On chunk reload, the animal is re-rendered.
- If a mod or command kills all entities of that type in the world, the captured animal is unaffected (it's a type reference, not a living entity).
- Breaking the machine in Creative mode: animal is released (spawned) as normal.

### Multiblock Validation (Milking Machine)

The base block validates its multiblock on every block update in the 2×1×2 volume. If the structure is invalid (bars missing), the machine pauses production but the cow remains captured. The block renders in the "incomplete" state.

When the structure is restored (player replaces broken bars), production resumes automatically.

### Machine Destruction

If the block entity is destroyed (explosion, command, world edit):

- Feed items drop.
- Output items drop.
- A cow/chicken entity spawns at the position.
- No items are lost.

### Multiplayer

- Two players cannot capture the same animal simultaneously — the first right-click/interaction wins.
- A player cannot release another player's animal unless they have permission to break the block.

### Stacking Machines Adjacent

Two Milking Machines placed adjacent to each other must not interfere. Each base block validates only the 2×1×2 volume relative to its own position and facing.

---

## Extension Points (Future Milestones)

- **Additional animal types**: sheep → wool, pig → porkchops (butchering), mooshroom → mushroom stew.
- **Breeding support**: two-cow machine that produces calves and milk.
- **Animal happiness**: better feed → faster production or bonus output.
- **Integration with Chef's Counter recipe planning**: kitchen planner knows milk and eggs are available and uses them in recipe dependency resolution.

---

## Testing Strategy

### Production Stations

### Game Tests

- Place Milking Machine structure, verify it detects valid and invalid configurations.
- Lure cow into machine, verify cow is captured and rendered.
- Provide wheat in feed slot, verify milk bucket produced after 60 seconds.
- Verify machine pulls wheat from adjacent chest when internal slot is empty.
- Verify machine pushes milk bucket to Cabinet of Holding.
- Verify production halts when output buffer is full (16 milk buckets).
- Verify feed is NOT consumed when output is full.
- Verify cow is released when base block is broken.
- Verify cow is released on sneak-right-click with empty hand.
- Place Chicken Nest, capture chicken, provide seeds, verify egg production.
- Verify egg production halts at 64 eggs.
- Verify chicken is released on nest break.
- Verify station does not pull from another station (only from storage).

### Kitchen Cat

- Place Cat Bed and Food Bowl in kitchen footprint. Lead tamed cat into kitchen. Verify cat converts to Kitchen Cat.
- Verify Kitchen Cat shrinks to 70% size and gains custom AI.
- Verify Kitchen Cat does not leave kitchen footprint.
- Verify Kitchen Cat is immune to damage (test: lava, fall, mob attack, /kill).
- Verify Kitchen Cat does not despawn.
- Break Food Bowl only. Verify cat does NOT revert to normal. Replace bowl.
- Break Cat Bed only. Verify cat does NOT revert. Replace bed.
- Break BOTH bed and bowl. Verify cat reverts to normal tamed cat.
- Verify cat hunger depletes over time and cat starts visiting Food Bowl when peckish.
- Place food item in Food Bowl. Verify cat pathfinds to bowl and eats.
- Verify cat's hunger is restored by correct amount per food type.
- Verify Regeneration I is applied to players in kitchen when cat eats.
- Verify Purrfect Timing speed bonus is applied to stations while cat is not hungry (test with Grill or Auto Cutter).
- Verify Purrfect Timing bonus disappears when cat becomes hungry.
- Verify Chef's Familiar saturation drain slowdown while cat is content.
- Place Bin in kitchen. Keep cat fed above 50 hunger. Wait and verify cat deposits a gift in Bin eventually.
- Verify Bin full → cat drops items on floor beside it.
- Verify gift loot table produces expected items (run many cycles).
- Craft Cat Food using recipe, place in bowl, verify cat restores 50 hunger.
- Verify cat sleeping behaviour: cat uses Cat Bed, Zzz particles appear.
- Verify cat perching behaviour: cat jumps onto Chef's Counter.
- Verify cat meows at increasing frequency as hunger drops.
- Verify breaking bed wakes sleeping cat.
- Verify cat respawns at Cat Bed if killed by command.
