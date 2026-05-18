# Delightful Kitchen

Delightful Kitchen is a NeoForge 1.21.1 Minecraft mod aiming to build a standalone Farmer's Delight kitchen.

The current implementation is transitioning away from Cooking for Blockheads compatibility toward a room-based kitchen defined by Kitchen Floor blocks, a Chef's Counter recipe book, and a Cabinet of Holding merged inventory terminal. Development builds still require Cooking for Blockheads until the legacy Java integrations are retired.

## Chef's Counter Recipe Book

Chef's Counter is the kitchen command screen. It indexes edible recipe outputs, shows craftability filters for ready/intermediate/blocked plans, and displays the selected dependency chain with required stations, utilities, fuel, containers, blockers, quantity, and expected output.

Queue confirmation is server-authoritative: the kitchen is rescanned before execution, stale details are rejected, and blocked or changed kitchens return visible blockers instead of consuming partial plans.

## Current Kitchen Utilities

- Water Provider: kitchen utility block that satisfies water-backed crafting plans by borrowing and returning empty buckets or glass bottles.
- Freezer: kitchen utility block that satisfies ice/snowball-backed crafting plans and passively produces ice or snowballs when connected to a Water Provider and supplied with the matching empty container.

## Food Pouch

Food Pouch is a Curios-equipped wearable food inventory. It requires Curios and adds a dedicated `delightful_kitchen:pouch` slot.

- Basic Food Pouch: 18 slots, normal food stacks.
- Advanced Food Pouch: 36 slots, 4x food stacks.
- Ultimate Food Pouch: 72 slots, 16x food stacks, capped at 1024 items per food slot.

Right-click a pouch in hand to open it. When equipped, use the "Open Food Pouch" keybind from the Delightful Kitchen controls category; it is unbound by default. The pouch can store non-food items at normal stack limits, but auto-feeding only considers edible items.

Auto-feeding runs while the pouch is equipped. The UI can cycle hunger thresholds between Any, Half, and Full, and can toggle the default-on hurt override. Leftover bowls, bottles, buckets, and other returned containers go back to the pouch first, then the player inventory, then drop at the player's feet.

## Development

- Minecraft: 1.21.1
- Loader: NeoForge
- Java: 21
- Required runtime dependencies: Farmer's Delight, Cooking for Blockheads, Balm, Curios

Build with:

```bash
./gradlew build
```
