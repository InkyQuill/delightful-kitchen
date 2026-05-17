# AGENTS.md

Guidance for agents working in this repository.

## Project

This is a NeoForge 1.21.1 Minecraft mod for a standalone Farmer's Delight focused kitchen.

Current technical identity is still `cfb_delight`, but the next roadmap milestone is a breaking pre-release rename to:

- Mod name: `Delightful Kitchen`
- Mod id / namespace: `delightful_kitchen`
- Java package: `net.inkyquill.delightfulkitchen`

The canonical roadmap is [docs/ROADMAP.md](docs/ROADMAP.md). `Plans.md` is historical scratch notes and should not be treated as the source of truth.

## Current Architecture Direction

The mod is moving away from Cooking for Blockheads APIs. Development builds may still depend on Cooking for Blockheads while legacy integrations are retired, but new kitchen behavior should be standalone.

Core concepts:

- `Kitchen Floor` defines a room-based connected kitchen footprint.
- `Chef's Counter` owns recipe planning and queue confirmation.
- `Cabinet of Holding` exposes the merged kitchen inventory.
- `KitchenInventoryService` reads and routes items across discovered kitchen inventories.
- Recipe planning is read-only until the player confirms a queue.
- Stations should eventually expose standalone queue/capacity/output state instead of CfB processor APIs.

## Roadmap Rules

- Rename to Delightful Kitchen before starting new gameplay systems.
- Build queue execution and standalone station interfaces before the full recipe book UI.
- Build Water Provider and Freezer before the full recipe book UI.
- Implement Food Pouch after the core kitchen loop and recipe book UI are usable.
- Split animal work: Milking Machine and Chicken Nest first, Kitchen Cat later.

## Development Rules

- Prefer existing project patterns over new abstractions.
- Keep changes scoped to the current milestone.
- Do not revert unrelated user or generated changes.
- Use `rg` / `rg --files` for search.
- Use `apply_patch` for manual edits.
- Keep Java code compatible with Java 21 and NeoForge 1.21.1.
- Do not add public integration APIs until the internal extension points are stable.

## Verification

Default verification for code changes:

```bash
./gradlew test
./gradlew build
```

Gradle may need access to the user's existing `~/.gradle` wrapper/cache. If the sandbox reports a read-only Gradle lock file, rerun the same Gradle command with escalation.

`./gradlew runGameTestServer` currently reaches Gradle success but may crash during Farmer's Delight dedicated-server loading in this dev runtime because Farmer's Delight attempts to load a client screen class on `DEDICATED_SERVER`. Do not treat that specific crash as a regression in this mod without checking whether the local Farmer's Delight build has changed.

## Documentation

Specs live under `docs/superpowers/specs/`.

Implementation plans live under `docs/superpowers/plans/`.

When changing roadmap-level direction, update `docs/ROADMAP.md` first, then patch affected specs only where they conflict with the current roadmap.
