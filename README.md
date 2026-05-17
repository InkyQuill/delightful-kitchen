# Delightful Kitchen

Delightful Kitchen is a NeoForge 1.21.1 Minecraft mod aiming to build a standalone Farmer's Delight kitchen.

The current implementation is transitioning away from Cooking for Blockheads compatibility toward a room-based kitchen defined by Kitchen Floor blocks, a Chef's Counter recipe planner, and a Cabinet of Holding merged inventory terminal. Development builds still require Cooking for Blockheads until the legacy Java integrations are retired.

## Current Kitchen Utilities

- Water Provider: kitchen utility block that satisfies water-backed crafting plans by borrowing and returning empty buckets or glass bottles.
- Freezer: kitchen utility block that satisfies ice/snowball-backed crafting plans and passively produces ice or snowballs when connected to a Water Provider and supplied with the matching empty container.

## Development

- Minecraft: 1.21.1
- Loader: NeoForge
- Java: 21

Build with:

```bash
./gradlew build
```
