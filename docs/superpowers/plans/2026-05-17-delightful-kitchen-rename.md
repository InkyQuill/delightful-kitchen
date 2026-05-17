# Delightful Kitchen Rename Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the unreleased mod from CfB-Delight / `cfb_delight` / `net.inkyquill.cfbdelight` to Delightful Kitchen / `delightful_kitchen` / `net.inkyquill.delightfulkitchen`.

**Architecture:** This is a breaking pre-release mechanical rename. Do not add migration aliases, do not remove Cooking for Blockheads yet, and do not start new gameplay work. Change code and resources first, verify the build, then update roadmap/docs as the final task.

**Tech Stack:** NeoForge 1.21.1, Java 21, Gradle 9.2.1, JUnit Jupiter.

---

## Scope And Rules

- This plan implements Milestone 1 from `docs/ROADMAP.md`.
- Old development worlds using `cfb_delight` blocks/items are allowed to break.
- Do not add a `cfb_delight` compatibility mod id, remap layer, or alias.
- Keep Cooking for Blockheads dependencies and legacy API references where they are still needed for current code to compile.
- Update `docs/ROADMAP.md`, `AGENTS.md`, `README.md`, and specs only in the final task.
- Historical implementation plans under `docs/superpowers/plans/` may continue to mention old names because they describe completed work.

## File Responsibility Map

### Java Source

- Rename package root: `src/main/java/net/inkyquill/cfbdelight/` → `src/main/java/net/inkyquill/delightfulkitchen/`.
- Rename main mod class: `CfBDelight.java` → `DelightfulKitchen.java`.
- Rename client bootstrap class: `CfBDelightClient.java` → `DelightfulKitchenClient.java`.
- Rename plugin classes:
  - `CfbDelightJeiPlugin.java` → `DelightfulKitchenJeiPlugin.java`.
  - `CfbDelightReiPlugin.java` → `DelightfulKitchenReiPlugin.java`.
- Update all Java package declarations, imports, class references, logger messages, hardcoded namespaces, and translatable keys.

### Tests

- Rename package root: `src/test/java/net/inkyquill/cfbdelight/` → `src/test/java/net/inkyquill/delightfulkitchen/`.
- Update package declarations, imports, test recipe ids, and namespace assertions.

### Assets And Data

- Rename resource namespace directories:
  - `src/main/resources/assets/cfb_delight/` → `src/main/resources/assets/delightful_kitchen/`.
  - `src/main/resources/data/cfb_delight/` → `src/main/resources/data/delightful_kitchen/`.
- Update all JSON references from `cfb_delight` to `delightful_kitchen`.
- Keep `src/main/resources/data/minecraft/tags/blocks/cookingforblockheads/kitchen_item_providers.json`, but update its item ids to `delightful_kitchen:*`.

### Metadata

- Update `gradle.properties`.
- Update `src/main/resources/META-INF/neoforge.mods.toml`.
- Keep `settings.gradle` project name unless a build failure points to a direct need; the repository directory name is not part of this milestone.

---

## Task 1: Rename Build Metadata And Main Mod Identity

**Files:**
- Modify: `gradle.properties`
- Modify: `src/main/resources/META-INF/neoforge.mods.toml`
- Move: `src/main/java/net/inkyquill/cfbdelight/CfBDelight.java` → `src/main/java/net/inkyquill/delightfulkitchen/DelightfulKitchen.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/DelightfulKitchen.java`

- [ ] **Step 1: Update Gradle properties**

Change these values in `gradle.properties`:

```properties
mod_id=delightful_kitchen
mod_name=Delightful Kitchen
```

Leave `mod_group_id=net.inkyquill` unchanged.

- [ ] **Step 2: Update mod metadata**

In `src/main/resources/META-INF/neoforge.mods.toml`, ensure the primary mod entry uses:

```toml
modId="${mod_id}"
displayName="${mod_name}"
```

Keep dependencies on `neoforge`, `minecraft`, `farmersdelight`, and `cookingforblockheads` if they already exist. Do not add a second `[[mods]]` entry for `cfb_delight`.

- [ ] **Step 3: Move and rewrite the main mod class**

Run:

```bash
mkdir -p src/main/java/net/inkyquill/delightfulkitchen
git mv src/main/java/net/inkyquill/cfbdelight/CfBDelight.java src/main/java/net/inkyquill/delightfulkitchen/DelightfulKitchen.java
```

Then update `DelightfulKitchen.java`:

```java
package net.inkyquill.delightfulkitchen;

import com.mojang.logging.LogUtils;
import net.inkyquill.delightfulkitchen.block.ModBlocks;
import net.inkyquill.delightfulkitchen.block.entity.ModBlockEntities;
import net.inkyquill.delightfulkitchen.item.ModItems;
import net.inkyquill.delightfulkitchen.menu.ModMenuTypes;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(DelightfulKitchen.MOD_ID)
public class DelightfulKitchen {
    public static final String MOD_ID = "delightful_kitchen";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DelightfulKitchen(IEventBus modEventBus) {
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModMenuTypes.register(modEventBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
```

If the current class has additional registrations not shown here, preserve them and update only names/imports.

- [ ] **Step 4: Run compile to expose dependent import failures**

Run:

```bash
./gradlew compileJava
```

Expected: this may fail because the rest of the Java source still imports `net.inkyquill.cfbdelight.CfBDelight`. Record the first 20 compile errors, then continue to Task 2.

- [ ] **Step 5: Commit Task 1**

If `compileJava` fails only because dependent packages have not been renamed yet, commit the metadata/main-class rename anyway:

```bash
git add gradle.properties src/main/resources/META-INF/neoforge.mods.toml src/main/java/net/inkyquill/delightfulkitchen/DelightfulKitchen.java
git commit -m "refactor: rename mod metadata"
```

If `compileJava` unexpectedly passes, use the same commit.

---

## Task 2: Rename Java Package Root And Class References

**Files:**
- Move: `src/main/java/net/inkyquill/cfbdelight/**` → `src/main/java/net/inkyquill/delightfulkitchen/**`
- Move: `src/test/java/net/inkyquill/cfbdelight/**` → `src/test/java/net/inkyquill/delightfulkitchen/**`
- Rename: `src/main/java/net/inkyquill/delightfulkitchen/client/CfBDelightClient.java` → `src/main/java/net/inkyquill/delightfulkitchen/client/DelightfulKitchenClient.java`
- Rename: `src/main/java/net/inkyquill/delightfulkitchen/compat/jei/CfbDelightJeiPlugin.java` → `src/main/java/net/inkyquill/delightfulkitchen/compat/jei/DelightfulKitchenJeiPlugin.java`
- Rename: `src/main/java/net/inkyquill/delightfulkitchen/compat/rei/CfbDelightReiPlugin.java` → `src/main/java/net/inkyquill/delightfulkitchen/compat/rei/DelightfulKitchenReiPlugin.java`

- [ ] **Step 1: Move remaining package directories**

Run:

```bash
mkdir -p src/main/java/net/inkyquill/delightfulkitchen
for child in block client compat item kitchen menu recipe util; do
  git mv "src/main/java/net/inkyquill/cfbdelight/$child" "src/main/java/net/inkyquill/delightfulkitchen/$child"
done
mkdir -p src/test/java/net/inkyquill/delightfulkitchen
git mv src/test/java/net/inkyquill/cfbdelight/kitchen src/test/java/net/inkyquill/delightfulkitchen/kitchen
```

After this step, `src/main/java/net/inkyquill/cfbdelight` and `src/test/java/net/inkyquill/cfbdelight` should be empty or absent.

- [ ] **Step 2: Rename identity-bearing Java classes**

Run:

```bash
git mv src/main/java/net/inkyquill/delightfulkitchen/client/CfBDelightClient.java src/main/java/net/inkyquill/delightfulkitchen/client/DelightfulKitchenClient.java
git mv src/main/java/net/inkyquill/delightfulkitchen/compat/jei/CfbDelightJeiPlugin.java src/main/java/net/inkyquill/delightfulkitchen/compat/jei/DelightfulKitchenJeiPlugin.java
git mv src/main/java/net/inkyquill/delightfulkitchen/compat/rei/CfbDelightReiPlugin.java src/main/java/net/inkyquill/delightfulkitchen/compat/rei/DelightfulKitchenReiPlugin.java
```

- [ ] **Step 3: Rewrite package declarations and imports**

Run:

```bash
perl -0pi -e 's/net\.inkyquill\.cfbdelight/net.inkyquill.delightfulkitchen/g' $(rg -l 'net\.inkyquill\.cfbdelight' src/main/java src/test/java)
perl -0pi -e 's/\bCfBDelight\b/DelightfulKitchen/g; s/\bCfbDelightJeiPlugin\b/DelightfulKitchenJeiPlugin/g; s/\bCfbDelightReiPlugin\b/DelightfulKitchenReiPlugin/g; s/CfB-Delight/Delightful Kitchen/g' $(rg -l 'CfBDelight|CfbDelightJeiPlugin|CfbDelightReiPlugin|CfB-Delight' src/main/java src/test/java)
```

- [ ] **Step 4: Update Java hardcoded namespaces and translatable keys**

Run:

```bash
perl -0pi -e 's/cfb_delight/delightful_kitchen/g' $(rg -l 'cfb_delight' src/main/java src/test/java)
```

Keep `cookingforblockheads` strings unchanged.

- [ ] **Step 5: Check renamed client class annotation**

Open `src/main/java/net/inkyquill/delightfulkitchen/client/DelightfulKitchenClient.java` and verify any event subscriber or mod annotation now references `DelightfulKitchen.MOD_ID` or `"delightful_kitchen"`, not `CfBDelight.MOD_ID`.

- [ ] **Step 6: Run compile**

Run:

```bash
./gradlew compileJava
```

Expected: Java packages compile, or errors are limited to JSON/resource namespace issues handled in Task 3. If compile errors mention `net.inkyquill.cfbdelight`, run:

```bash
rg -n 'net\.inkyquill\.cfbdelight|CfBDelight|CfbDelight' src/main/java src/test/java
```

Fix every match in Java/test source before continuing.

- [ ] **Step 7: Commit Task 2**

```bash
git add src/main/java src/test/java
git commit -m "refactor: rename Java package to delightful kitchen"
```

---

## Task 3: Rename Resource And Data Namespaces

**Files:**
- Move: `src/main/resources/assets/cfb_delight/` → `src/main/resources/assets/delightful_kitchen/`
- Move: `src/main/resources/data/cfb_delight/` → `src/main/resources/data/delightful_kitchen/`
- Modify: `src/main/resources/data/minecraft/tags/blocks/cookingforblockheads/kitchen_item_providers.json`
- Modify: all JSON files under `src/main/resources/assets/delightful_kitchen/`
- Modify: all JSON files under `src/main/resources/data/delightful_kitchen/`

- [ ] **Step 1: Move namespace directories**

Run:

```bash
git mv src/main/resources/assets/cfb_delight src/main/resources/assets/delightful_kitchen
git mv src/main/resources/data/cfb_delight src/main/resources/data/delightful_kitchen
```

- [ ] **Step 2: Rewrite JSON namespaces**

Run:

```bash
perl -0pi -e 's/cfb_delight/delightful_kitchen/g; s/CfB-Delight/Delightful Kitchen/g' $(rg -l 'cfb_delight|CfB-Delight' src/main/resources)
```

This must update:

- blockstate model references such as `delightful_kitchen:block/grill`
- model texture references such as `delightful_kitchen:block/grill_top`
- item model parents such as `delightful_kitchen:block/kitchen_floor`
- lang keys such as `block.delightful_kitchen.kitchen_floor`
- recipe result ids such as `delightful_kitchen:grill`
- loot table item names such as `delightful_kitchen:chef_counter`
- Cooking for Blockheads provider tag entries pointing at this mod's blocks

- [ ] **Step 3: Verify resources have no old namespace**

Run:

```bash
rg -n 'cfb_delight|CfB-Delight' src/main/resources
```

Expected: no matches.

- [ ] **Step 4: Run resource processing**

Run:

```bash
./gradlew processResources
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit Task 3**

```bash
git add src/main/resources
git commit -m "refactor: rename resource namespace"
```

---

## Task 4: Update Compatibility Plugin IDs And Test Namespaces

**Files:**
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/compat/jei/DelightfulKitchenJeiPlugin.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/compat/rei/DelightfulKitchenReiPlugin.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/compat/rei/display/CookingPotDisplay.java`
- Modify: `src/main/java/net/inkyquill/delightfulkitchen/compat/rei/display/CuttingBoardDisplay.java`
- Modify: `src/test/java/net/inkyquill/delightfulkitchen/kitchen/recipe/RecipeAdapterFuelTest.java`

- [ ] **Step 1: Verify JEI plugin UID**

In `DelightfulKitchenJeiPlugin.java`, the UID should use:

```java
private static final ResourceLocation UID = DelightfulKitchen.id("jei_plugin");
```

and imports should reference `net.inkyquill.delightfulkitchen.DelightfulKitchen`.

- [ ] **Step 2: Verify REI plugin IDs**

In `DelightfulKitchenReiPlugin.java` and REI display/category files, replace old explicit namespace strings with:

```java
"delightful_kitchen"
```

or, where the API accepts `ResourceLocation`, prefer:

```java
DelightfulKitchen.id("cutting_board")
```

Keep external `cookingforblockheads` category identifiers unchanged only if they intentionally refer to Cooking for Blockheads.

- [ ] **Step 3: Verify test recipe ids**

In `RecipeAdapterFuelTest.java`, the test ids should use:

```java
ResourceLocation.fromNamespaceAndPath("delightful_kitchen", "test_grilled_beef")
ResourceLocation.fromNamespaceAndPath("delightful_kitchen", "test_stew")
```

- [ ] **Step 4: Run targeted namespace grep**

Run:

```bash
rg -n 'cfb_delight|CfBDelight|CfbDelight|CfB-Delight|net\.inkyquill\.cfbdelight' src/main src/test
```

Expected: no matches.

- [ ] **Step 5: Run tests**

Run:

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit Task 4**

```bash
git add src/main/java src/test/java
git commit -m "refactor: update compatibility namespaces"
```

---

## Task 5: Full Build And Runtime Metadata Verification

**Files:**
- No planned edits. Fix only compile/resource mistakes introduced by Tasks 1-4.

- [ ] **Step 1: Run full build**

Run:

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Inspect expanded metadata**

Run:

```bash
rg -n 'modId|displayName|delightful_kitchen|cfb_delight|CfB-Delight|Delightful Kitchen' build/resources/main/META-INF/neoforge.mods.toml build/resources/main/assets/delightful_kitchen/lang/en_us.json
```

Expected:

- `modId="delightful_kitchen"` or equivalent expanded TOML output.
- `displayName="Delightful Kitchen"` or equivalent expanded TOML output.
- Lang file uses `itemGroup.delightful_kitchen`.
- No `cfb_delight` or `CfB-Delight` in these generated files.

- [ ] **Step 3: Inspect built jar namespace**

Run:

```bash
jar tf build/libs/delightful_kitchen-*.jar | rg 'assets/|data/|net/inkyquill' | rg 'cfb_delight|cfbdelight|delightful_kitchen|delightfulkitchen'
```

Expected:

- Entries under `assets/delightful_kitchen/`.
- Entries under `data/delightful_kitchen/`.
- Classes under `net/inkyquill/delightfulkitchen/`.
- No entries under `assets/cfb_delight/`, `data/cfb_delight/`, or `net/inkyquill/cfbdelight/`.

- [ ] **Step 4: Optional client smoke launch**

If an interactive Minecraft client is available, run:

```bash
./gradlew runClient
```

Manual checks:

- Mods list shows `Delightful Kitchen`.
- Creative tab title shows `Delightful Kitchen`.
- Existing blocks/items render with their textures.

If a client window cannot be launched in the current environment, record that it was not run.

- [ ] **Step 5: Commit verification fixes if needed**

If Task 5 required fixes:

```bash
git add src build.gradle gradle.properties
git commit -m "fix: complete delightful kitchen rename"
```

If Task 5 required no fixes, do not create an empty commit.

---

## Task 6: Final Documentation And Roadmap Update

**This task must be last. Do not update roadmap/docs earlier in the implementation.**

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `docs/ROADMAP.md`
- Modify: `docs/superpowers/specs/2026-05-16-delightful-rename-and-utilities.md`
- Modify: `docs/superpowers/specs/2026-05-16-standalone-kitchen-design.md`
- Modify: `Plans.md`

- [ ] **Step 1: Update README**

Replace the title and opening description with:

```markdown
# Delightful Kitchen

Delightful Kitchen is a NeoForge 1.21.1 Minecraft mod aiming to build a standalone Farmer's Delight kitchen.

The current implementation is transitioning away from Cooking for Blockheads compatibility toward a room-based kitchen defined by Kitchen Floor blocks, a Chef's Counter recipe planner, and a Cabinet of Holding merged inventory terminal. Development builds still require Cooking for Blockheads until the legacy Java integrations are retired.
```

- [ ] **Step 2: Update AGENTS current identity**

In `AGENTS.md`, replace the pre-rename wording with:

```markdown
The current technical identity is:

- Mod name: `Delightful Kitchen`
- Mod id / namespace: `delightful_kitchen`
- Java package: `net.inkyquill.delightfulkitchen`
```

Keep the roadmap rules, but remove wording that says the rename is still the next milestone.

- [ ] **Step 3: Update roadmap milestone status**

In `docs/ROADMAP.md`, change the current direction paragraph to say the code now uses `delightful_kitchen`. Mark Milestone 1 as complete by changing the heading:

```markdown
### 1. Rename To Delightful Kitchen (Complete)
```

Leave Milestone 2 as the next gameplay milestone.

- [ ] **Step 4: Update rename spec status**

In `docs/superpowers/specs/2026-05-16-delightful-rename-and-utilities.md`, change:

```markdown
Status: Draft
```

to:

```markdown
Status: Rename complete; utilities still draft
```

Add one sentence under Part 1:

```markdown
The rename portion of this specification has been implemented. The Water Provider and Freezer portions remain future work.
```

- [ ] **Step 5: Update standalone design old name references**

In `docs/superpowers/specs/2026-05-16-standalone-kitchen-design.md`, replace player-facing old mod names:

- `CfB-Delight` → `Delightful Kitchen`
- `CfB-Delight block` → `Delightful Kitchen block`

Keep historical mentions of Cooking for Blockheads where they refer to the dependency or inspiration.

- [ ] **Step 6: Update Plans.md note**

At the top of `Plans.md`, keep the note that `docs/ROADMAP.md` is canonical. If the file still says the first milestone is upcoming, change that sentence to:

```markdown
The foundation and read-only recipe planning milestones are implemented. Current canonical milestone order lives in `docs/ROADMAP.md`.
```

- [ ] **Step 7: Documentation grep**

Run:

```bash
rg -n 'CfB-Delight|cfb_delight|cfbdelight|CfBDelight|net\.inkyquill\.cfbdelight' README.md AGENTS.md docs/ROADMAP.md docs/superpowers/specs/2026-05-16-delightful-rename-and-utilities.md docs/superpowers/specs/2026-05-16-standalone-kitchen-design.md Plans.md
```

Expected:

- No matches in `README.md`, `AGENTS.md`, or `docs/ROADMAP.md`.
- In specs, matches are allowed only when explaining old-to-new rename tables or prototype-world breakage.
- In `Plans.md`, matches are allowed only in historical scratch recipe notes.

- [ ] **Step 8: Final build after docs**

Run:

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit Task 6**

```bash
git add README.md AGENTS.md docs/ROADMAP.md docs/superpowers/specs/2026-05-16-delightful-rename-and-utilities.md docs/superpowers/specs/2026-05-16-standalone-kitchen-design.md Plans.md
git commit -m "docs: mark delightful kitchen rename complete"
```

---

## Final Verification

- [ ] **Step 1: Run all tests**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run full build**

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run source namespace grep**

```bash
rg -n 'cfb_delight|cfbdelight|CfBDelight|CfbDelight|CfB-Delight|net\.inkyquill\.cfbdelight' src/main src/test build.gradle gradle.properties README.md AGENTS.md docs/ROADMAP.md
```

Expected: no matches.

- [ ] **Step 4: Run generated output namespace grep**

```bash
rg -n 'cfb_delight|cfbdelight|CfBDelight|CfbDelight|CfB-Delight|net\.inkyquill\.cfbdelight' build/resources/main build/classes/java/main build/classes/java/test
```

Expected: no matches.

- [ ] **Step 5: Run jar namespace inspection**

```bash
jar tf build/libs/delightful_kitchen-*.jar | rg 'cfb_delight|cfbdelight|CfBDelight|CfbDelight|net/inkyquill/cfbdelight'
```

Expected: no matches.

---

## Self-Review Checklist

- Java packages and classes use `net.inkyquill.delightfulkitchen`.
- Main mod class is `DelightfulKitchen`.
- `DelightfulKitchen.MOD_ID` is `delightful_kitchen`.
- Assets and data live under `assets/delightful_kitchen` and `data/delightful_kitchen`.
- Lang keys use `*.delightful_kitchen.*`.
- JEI/REI plugin ids use the new namespace.
- Tests use the new package and namespace.
- Cooking for Blockheads dependencies remain intact.
- No migration alias was added.
- Roadmap and docs were updated only in the final implementation task.
