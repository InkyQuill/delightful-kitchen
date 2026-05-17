# Food Pouch Specification

Date: 2026-05-16
Status: Draft

## Purpose

The Food Pouch is a Curios-compatible wearable inventory item that holds food and automatically feeds the player when hungry. It is inspired by the auto-feeding upgrade from Sophisticated Backpacks but designed as a standalone item with its own storage, stack multipliers, smart feeding thresholds, and container return logic.

Anything the player places in the pouch is considered safe to eat — the player is responsible for curating its contents. There is no food filter.

The Food Pouch is a later roadmap milestone. It should be implemented after the core kitchen loop and Chef's Counter recipe book UI are usable. It is intentionally independent from kitchen automation.

## Scope

This specification covers the complete Food Pouch feature:

- Three upgrade tiers with increasing slot count and stack multipliers.
- Curios integration so the pouch occupies a dedicated equipment slot.
- Smart auto-feeding with configurable hunger thresholds and health-aware feeding.
- Container return: bowls, bottles, buckets, and other container items left over after eating are saved back into the pouch (or the player's inventory, or dropped).
- Pouch UI for viewing inventory and adjusting feeding settings.
- Data components for persistence of tier, inventory contents, and feeding preferences.
- Crafting recipes for each tier.

Not included in this milestone:

- Food filter (anything placed in the pouch is used for feeding).
- Kitchen planner integration (food preferences pulled from the kitchen).
- Pouch-to-kitchen item routing.
- Curios slot upgrade/expansion via additional items (the tier IS the upgrade).

Curios is a required dependency for the Food Pouch milestone only. Earlier kitchen milestones should not depend on Curios.

## Tiers

The pouch has three tiers. Each tier is a separate item with its own inventory size and stack multiplier. A player cannot upgrade a pouch in-place; they craft the next tier and transfer contents manually.

| Tier      | Item ID                  | Slots | Stack Multiplier | Max Food Stack |
|-----------|--------------------------|-------|-------------------|----------------|
| Basic     | `food_pouch`             | 18    | 1×                | 64             |
| Advanced  | `advanced_food_pouch`    | 36    | 4×                | 256            |
| Ultimate  | `ultimate_food_pouch`    | 72    | 16×               | 1024           |

The stack multiplier applies only to items that have `FoodProperties` (i.e., are edible). Non-food items placed in the pouch store at the item's default max stack size (normally 64). The pouch accepts non-food items because the player may want to carry ingredients or containers alongside their food, but only edible items participate in auto-feeding.

## Curios Integration

The pouch occupies a dedicated Curios slot `delightful_kitchen:pouch`. This avoids conflicts with other mods that use the `belt` or `back` slots.

A Curios slot definition is registered so the player can equip exactly one pouch at a time. The slot is added to `minecraft:player` entities.

The pouch ticks only while equipped in its Curios slot. If the pouch is in the player's main inventory or a chest, it does not auto-feed.

## Inventory Model

Each pouch stores its contents in a custom item inventory saved to the item stack's data components.

The inventory must support stack counts above normal vanilla menu assumptions: Advanced tier allows up to 256 food items per slot, and Ultimate tier allows up to 1024. Do not assume vanilla `ItemContainerContents` alone is sufficient for this storage model. Implementation should use a custom codec-backed data component or another explicit serialized format that preserves large counts and tier-sized slot lists.

On every content change (insertion, extraction, or container return), the updated inventory is persisted back to the item stack. The stack tooltip shows a summary (e.g., "12/18 slots used").

The inventory size, per-slot stack limit, and max stack size for food items all derive from the tier configuration.

Stack multiplier enforcement:

- The per-slot maximum for a food item is `tier.stackMultiplier × item.getDefaultMaxStackSize()` (capped at 1024).
- Non-food items use the item's default max stack size.
- On insert, if a food stack would exceed the slot limit, only the allowable amount is inserted and the remainder is returned.
- On shift-click from player inventory into the pouch UI, the stack is split across compatible slots using the standard merge-into-existing-then-fill-empty algorithm.

---

## Smart Auto-Feeding

### Tick Cadence

The pouch ticks once per second (20 ticks) while the player has it equipped. Each tick:

1. If the cooldown has not elapsed, skip.
2. Scan pouch inventory slots for edible items.
3. For each edible item, evaluate whether the player should be fed right now (see Feeding Decision below).
4. If a suitable food is found, consume one item, apply nutrition, return any container, and set the cooldown.
5. If no food was consumed, set the scanning cooldown.

### Cooldown Behavior

Three cooldown durations, chosen based on the outcome of the tick:

| Outcome                               | Cooldown | Rationale                                              |
|--------------------------------------|----------|--------------------------------------------------------|
| Food was consumed AND player is still hungry | 10 ticks (0.5 s) | Keep feeding rapidly until hunger is full. |
| Food was consumed AND player is now full    | 100 ticks (5 s)  | Player satisfied — wait before checking again. |
| No food was consumed (not hungry or no match) | 100 ticks (5 s)  | No point scanning every tick.              |

### Feeding Decision — Step By Step

For a given food item in the pouch, the pouch decides to feed it when ALL of these are true:

1. **Player is hungry at all.** `player.getFoodData().getFoodLevel() < 20`. If the hunger bar is already full, no food is needed.

2. **Food provides real nutrition.** The food's `FoodProperties.nutrition() >= 1`. Foods that provide only status effects with zero nutrition are skipped.

3. **Food is not Ominous Bottle.** `minecraft:ominous_bottle` is always skipped — its Bad Omen effect is harmful and never desired from auto-feeding.

4. **One of the hunger-threshold conditions is met**, OR the health-override condition fires.

#### Hunger Threshold

The hunger threshold is configurable. It controls how many hunger points the player must be missing before a food with a given nutrition value is used. The goal is to let the player control how aggressively the pouch feeds and how much nutrition is wasted due to overflow.

Let `hungerDeficit = 20 - player.getFoodData().getFoodLevel()` (how many shanks are empty).

| Setting | Rule | Behavior |
|---------|------|----------|
| **ANY** | Feed whenever hungry | Feeds as soon as even one hunger point is missing. A steak (8 nutrition) will be eaten when only 1 point is missing, wasting 7 points. Most aggressive, most wasteful. |
| **HALF** (default) | Feed when `hungerDeficit ≥ nutrition / 2` | A steak (8 nutrition) is eaten when at least 4 points are missing. Wastes at most half the nutrition. Balanced. |
| **FULL** | Feed when `hungerDeficit ≥ nutrition` | A steak (8 nutrition) is eaten only when at least 8 points are missing. Never wastes nutrition. Most conservative — the player may dip to low hunger before the pouch acts. |

#### Health Override (Feed When Hurt)

When the "feed immediately when hurt" setting is ON (default), the hunger threshold is bypassed whenever the player is damaged:

- Condition: `player.getHealth() < player.getMaxHealth() - 0.1F` (player has taken damage).
- Effect: if the player is hungry (rule 1) and hurt, feed immediately even if the hunger threshold wouldn't normally trigger.
- This ensures the player starts regenerating health via saturation as soon as possible after taking damage.

When the health override is OFF, the pouch only considers the hunger threshold — it ignores the player's health entirely.

**Summary decision flow:**

```
Is player hungry? (foodLevel < 20)
  ├─ No → skip (cooldown 100)
  └─ Yes
       Is the food edible? (nutrition ≥ 1, not ominous bottle)
         ├─ No → skip this food, try next
         └─ Yes
              Is player hurt AND feed-when-hurt is ON?
                ├─ Yes → FEED (override hunger threshold)
                └─ No
                     Does hunger threshold pass?
                       ├─ ANY → FEED (any hunger qualifies)
                       ├─ HALF → FEED if deficit ≥ nutrition/2
                       └─ FULL → FEED if deficit ≥ nutrition
```

### Food Consumption

When the pouch decides to feed:

1. **Copy one food item** from the pouch slot into a temporary single-item stack. Do not mutate the player's hands.
2. **Validate consumption** using the copied stack's food properties. If it no longer has valid food data, skip it.
3. **Apply hunger/saturation** without disturbing the held item:
    - Calculate `nutrition` from `FoodProperties.nutrition()`.
    - Calculate `saturation` from `FoodProperties.saturation()`. In vanilla Minecraft, saturation added is `nutrition × saturationModifier × 2.0F`. Apply this directly via `player.getFoodData().eat(nutrition, saturationModifier)`.
    - Apply any status effects from `FoodProperties.effects()` as if the player had eaten the item.
    - Note: `FoodData.eat(int nutrition, float saturationModifier)` is the vanilla method for programmatic feeding without requiring an `ItemStack` — but effects still must be applied manually since `eat(ItemStack)` is not called.
4. **Capture the container** in a non-duplicating way. Call `Item.finishUsingItem` only on the temporary single-item stack after the pouch has decided to consume it. Compare the returned stack with the original one-item food stack:
   - If the returned stack is empty, no container is returned.
   - If the returned stack is a different item, treat it as the leftover container.
   - If the returned stack is still the same food item, do not return it as a container; this avoids duplicating food for items whose finish logic does not produce a remainder.
5. **Decrement** the food stack in the pouch by 1 only after hunger/effects and container result have been determined successfully.
6. **Return the container** via the container return pipeline (see next section).
7. **Persist** the updated inventory to the item stack.
8. **Set cooldown** based on whether the player is still hungry.

The player's main hand item is never swapped or disturbed. Feeding is transparent — the player just sees their hunger bar replenish.

If for any reason the food's consumption fails before the decrement step, the food is NOT decremented and no cooldown is set. The pouch moves on to the next candidate.

---

## Container Return

After consuming a food item, `Item.finishUsingItem()` returns the leftover container. For example:

- Mushroom stew → **bowl**
- Honey bottle → **glass bottle**
- Milk bucket → **bucket**
- Beetroot soup → **bowl**
- Suspicious stew → **bowl**

These containers must be saved, not voided.

Container return is attempted in priority order:

1. **Insert back into the pouch.** Try to merge with an existing stack of the same container type in the pouch. If no matching stack has room, try an empty slot. Success if the container fits entirely.
2. **Insert into player inventory.** If the pouch cannot accept the container, attempt to insert it into the player's main inventory (36 slots). Use standard merge-then-empty routing.
3. **Drop at player's feet.** If neither the pouch nor the player inventory has room, spawn the container as a dropped `ItemEntity` at the player's position (using `player.drop(resultItem, false)` or equivalent).

If `finishUsingItem` returns `ItemStack.EMPTY`, no container needs to be returned (steak, bread, apples, golden carrots, etc.).

---

## Configuration Data Components

The following data is persisted as NeoForge `DataComponentType` entries on the pouch item stack:

| Component | Type | Default | Description |
|-----------|------|---------|-------------|
| `delightful_kitchen:pouch_inventory` | Custom codec-backed pouch inventory value | empty | The pouch's item contents, including tier-sized slot list and large stack counts. |
| `delightful_kitchen:feed_at_hunger_level` | `String` | `"half"` | `any`, `half`, or `full`. |
| `delightful_kitchen:feed_when_hurt` | `boolean` | `true` | Whether health damage bypasses the hunger threshold. |

Inventory size and per-slot stack limits are NOT stored as data components — they derive from the tier, which is determined by which item type the stack is (`food_pouch`, `advanced_food_pouch`, or `ultimate_food_pouch`).

---

## Pouch UI

The pouch UI opens on right-click when the pouch is in the player's hand. When equipped in the Curios slot, the player can open it via a keybind (default: unset, configurable by the player in Minecraft's controls menu). A later polish pass may add an inventory-screen button, but the keybind is the required equipped-open path.

### Inventory View

The main grid shows the pouch's slots: 18 (6×3), 36 (6×6), or 72 (9×8) depending on tier. The slot background uses a slight tint for food items vs non-food items.

Standard slot interactions:

- Left-click: pick up / place single item.
- Right-click: split stack in half.
- Shift-click: move between pouch and player inventory using merge-then-empty routing.
- Number keys (1–9): swap with hotbar slot.

Tooltip on each item shows the stack count and the tier's max for that slot.

### Settings Panel

A small panel (toggle or side-tab) with two controls:

1. **Hunger threshold** — cycles `Any` → `Half` → `Full` with a tooltip describing the behavior at each level.
2. **Feed when hurt** — ON / OFF toggle.

No food filter UI is needed.

---

## Crafting Recipes

| Tier | Recipe |
|------|--------|
| Basic | Leather × 6 + String × 2 in a pouch shape: `L L L` / `L S L` / `L L L` |
| Advanced | Basic Food Pouch + Diamond + Gold Ingot × 4 (shapeless; result is empty) |
| Ultimate | Advanced Food Pouch + Netherite Ingot + Ender Chest (shapeless; result is empty) |

Upgrading to a higher tier loses the contents of the lower-tier pouch. The player must empty it before crafting.

---

## Limitations And Edge Cases

### Foods Skipped By Default

- **Ominous Bottle** (`minecraft:ominous_bottle`): Hardcoded exclusion — Bad Omen is harmful.
- **Foods with 0 nutrition** (some mod foods that only apply effects): Skipped by the `nutrition >= 1` check.
- **Chorus Fruit**: NOT excluded. If the player puts it in the pouch, it will be fed and will teleport the player. The player is responsible for what they put in the pouch.
- **Suspicious Stew, potion foods**: NOT excluded. If the player puts them in, they accept the effects.

### Player States

| State | Behavior |
|-------|----------|
| Creative mode | Auto-feeding disabled (player does not get hungry). |
| Spectator mode | Auto-feeding disabled. |
| Dead | Auto-feeding disabled (pouch does not tick on dead players). |
| Sleeping | Auto-feeding disabled (player is in bed). |

### Concurrent Modification

If the pouch inventory is modified externally while the player is being fed (extremely rare — the pouch ticks on its own item stack), the tick handler validates slot indices before reading/writing.

### Stack Overflow

The maximum food stack size is capped at 1024. Any attempt to exceed this (creative mode, commands, mod interactions) clamps to the tier's maximum.

### Pouch Stackability

A pouch item always stacks to 1 (it carries inventory data). NeoForge's `ItemStack` data component system ensures that two pouches never merge accidentally.

### Death And Keep-Inventory

- `keepInventory = true`: Pouch stays in the Curios slot with all contents.
- `keepInventory = false`: Pouch drops as an item with all contents preserved.

---

## Extension Points

Deferred to future milestones:

- **Kitchen integration**: Chef's Counter queries the equipped pouch for food availability; kitchen stations can suggest restocking.
- **Upgrade preservation**: A recipe variant or ritual that transfers contents from a lower-tier pouch to a higher-tier one.
- **On-screen HUD indicator**: A small icon showing pouch fullness and whether auto-feeding is active.

---

## Testing Strategy

### Unit / Focused Tests

- Custom inventory serialization/deserialization with large stack counts at each tier.
- Inventory insert/extract with stack multiplier limits at each tier.
- Hunger threshold logic: ANY, HALF, FULL against foods with different nutrition values.
- Health override: verify feeding triggers when hurt regardless of hunger threshold.
- Container return routing: pouch → player inventory → world drop.
- Cooldown transitions: 10-tick vs 100-tick based on hunger state.
- Ominous Bottle exclusion.

### Game Tests

- Equip pouch in Curios slot; verify auto-feeding triggers when hunger drops.
- Verify pouch does NOT auto-feed in Creative or Spectator mode.
- Verify container (bowl) returns to pouch after eating stew.
- Verify container routes to player inventory when pouch is full.
- Verify container drops at player feet when both pouch and player inventory are full.
- Verify Advanced tier allows 4× stacks of food items.
- Verify Ultimate tier allows 16× stacks of food items.
- Verify non-food items are limited to normal stack sizes.
- Verify hunger threshold cycling (Any/Half/Full) changes feeding behavior.
- Verify feed-when-hurt toggle enables/disables health override.
- Verify pouch UI opens and displays inventory grid.
- Verify crafting recipes produce correct tier with empty inventory.

---

## Implementation Notes

- The pouch item class implements the Curios API tick interface (`ICurioItem` or equivalent for 1.21.1 NeoForge Curios) so it receives tick callbacks while equipped.
- The Curios slot is registered via the Curios datapack system (`data/curios/curios/slots/delightful_kitchen.json` for the slot icon, `data/curios/tags/item/pouch.json` for which items go into the slot, `data/curios/curios/entities/player.json` for entity slot assignment).
- Data components use NeoForge's `DataComponentType` registration with codec-based serialization. Inventory uses a custom codec-backed component rather than vanilla `ItemContainerContents` so large stack counts and tier slot counts are preserved. Custom components register for feeding settings.
- Container return uses the `InventoryHelper.insertOrDropItem` utility already present in the project.
- The pouch UI follows the standard `AbstractContainerScreen` + `AbstractContainerMenu` + `BlockEntity`-less pattern (similar to vanilla bundle or shulker box item menus, opened from an item rather than a block).
- Food consumption uses `Player.getFoodData().eat(nutrition, saturationModifier)` for hunger/saturation, applies `FoodProperties.effects()` manually via `FoodProperties.possibleEffects()` iterating and rolling the probability, and uses `Item.finishUsingItem()` for the container result.
