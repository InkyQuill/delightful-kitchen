# Recipe Planning Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the standalone, read-only recipe planning core that lets Chef's Counter index edible recipes and classify them as ready, craftable with intermediates, or blocked from the current kitchen inventory.

**Architecture:** This milestone adds planner data types under `net.inkyquill.cfbdelight.kitchen.recipe` and keeps all planning read-only. Recipe adapters normalize supported Minecraft and Farmer's Delight recipe types into `PlannedRecipe` data, `RecipeIndex` collects edible final outputs, and `RecipePlanner` evaluates inventory snapshots without consuming items or queueing station work.

**Tech Stack:** Minecraft 1.21.1, NeoForge 21.1.221, Java 21, Gradle, existing DeferredRegister patterns, Farmer's Delight recipe classes from `../FarmersDelight`, current standalone `KitchenInventoryService`.

---

## Scope And Sequencing

This plan implements the next foundation slice of the approved spec at `docs/superpowers/specs/2026-05-16-standalone-kitchen-design.md`.

Included:

- Internal recipe adapter model.
- Initial adapters for vanilla shaped crafting, vanilla shapeless crafting, Farmer's Delight cutting, Farmer's Delight cooking pot, and campfire cooking.
- Read-only inventory snapshot and ingredient matching.
- Recipe index of edible final outputs.
- Deterministic planner for direct recipes plus one or more intermediate dependency steps.
- Chef's Counter status data expanded to show indexed food count and craftability counts.
- Focused game-test coverage for scan-independent planner behavior where practical.

Not included:

- Full Chef's Counter recipe book grid and details panel.
- Confirm/queue execution.
- Station queue rewrite.
- Removing Cooking for Blockheads dependency.
- Curios pouch.
- Persistent Kitchen Floor camouflage.

Use `minecraft-modding` for implementation. Confirm NeoForge and MC version from `gradle.properties` before code changes.

## Planned File Structure

- Create `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/KitchenRecipeAdapter.java`: adapter interface for normalizing recipe types.
- Create `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/KitchenRecipeAdapters.java`: static registry for built-in adapters.
- Create `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/KitchenStationType.java`: enum of logical station requirements.
- Create `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/PlannedIngredient.java`: normalized ingredient requirement.
- Create `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/PlannedRecipe.java`: normalized recipe step.
- Create `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/PlanningBlocker.java`: blocker categories.
- Create `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/PlanState.java`: `READY`, `CRAFTABLE_WITH_INTERMEDIATES`, `BLOCKED`.
- Create `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/RecipePlan.java`: planner output.
- Create `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/KitchenInventorySnapshot.java`: immutable item count snapshot.
- Create `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/IngredientMatcher.java`: chooses matching stacks from a snapshot.
- Create `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/RecipeIndex.java`: indexes supported recipes and edible final outputs.
- Create `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/RecipePlanner.java`: read-only deterministic planner.
- Create `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/adapters/ShapedCraftingAdapter.java`.
- Create `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/adapters/ShapelessCraftingAdapter.java`.
- Create `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/adapters/CuttingBoardAdapter.java`.
- Create `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/adapters/CookingPotAdapter.java`.
- Create `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/adapters/CampfireCookingAdapter.java`.
- Modify `src/main/java/net/inkyquill/cfbdelight/menu/ChefCounterMenu.java`: add planner summary data slots.
- Modify `src/main/java/net/inkyquill/cfbdelight/client/screen/ChefCounterScreen.java`: display recipe planning summary counts.
- Modify `src/main/resources/assets/cfb_delight/lang/en_us.json`: add new Chef's Counter summary labels.
- Create `src/main/java/net/inkyquill/cfbdelight/gametest/RecipePlanningGameTests.java`: focused tests for snapshot matching and planner states.
- Modify `src/main/java/net/inkyquill/cfbdelight/CfBDelight.java`: register game tests only if required by NeoForge event wiring.

## Task 1: Add Planner Data Model

**Files:**
- Create: `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/KitchenStationType.java`
- Create: `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/PlanningBlocker.java`
- Create: `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/PlanState.java`
- Create: `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/PlannedIngredient.java`
- Create: `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/PlannedRecipe.java`
- Create: `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/RecipePlan.java`

- [ ] **Step 1: Confirm platform**

Run:

```bash
rg -n "minecraft_version=|neo_version=|net.neoforged.gradle" gradle.properties build.gradle
```

Expected: output includes `minecraft_version=1.21.1`, `neo_version=21.1.221`, and `net.neoforged.gradle.userdev`.

- [ ] **Step 2: Create station and state enums**

Create `KitchenStationType.java`:

```java
package net.inkyquill.cfbdelight.kitchen.recipe;

public enum KitchenStationType {
    CRAFTING,
    CUTTING_BOARD,
    COOKING_POT,
    GRILL
}
```

Create `PlanState.java`:

```java
package net.inkyquill.cfbdelight.kitchen.recipe;

public enum PlanState {
    READY,
    CRAFTABLE_WITH_INTERMEDIATES,
    BLOCKED
}
```

Create `PlanningBlocker.java`:

```java
package net.inkyquill.cfbdelight.kitchen.recipe;

public enum PlanningBlocker {
    MISSING_RAW_INGREDIENT,
    MISSING_INTERMEDIATE_RECIPE,
    MISSING_STATION,
    MISSING_CONTAINER,
    MISSING_FUEL,
    FULL_OUTPUT_STORAGE,
    STATION_QUEUE_FULL,
    KITCHEN_CHANGED
}
```

- [ ] **Step 3: Create normalized ingredient and recipe records**

Create `PlannedIngredient.java`:

```java
package net.inkyquill.cfbdelight.kitchen.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Arrays;
import java.util.List;

public record PlannedIngredient(Ingredient ingredient, int count) {
    public PlannedIngredient {
        if (ingredient == null) {
            throw new IllegalArgumentException("ingredient cannot be null");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }

    public List<ItemStack> displayStacks() {
        return Arrays.stream(ingredient.getItems())
                .map(stack -> stack.copyWithCount(count))
                .toList();
    }
}
```

Create `PlannedRecipe.java`:

```java
package net.inkyquill.cfbdelight.kitchen.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public record PlannedRecipe(
        ResourceLocation id,
        List<PlannedIngredient> ingredients,
        List<ItemStack> outputs,
        KitchenStationType stationType,
        Optional<ItemStack> servingContainer,
        Optional<PlannedIngredient> fuel,
        int processingTime,
        boolean asynchronous
) {
    public PlannedRecipe {
        ingredients = List.copyOf(ingredients);
        outputs = outputs.stream().map(ItemStack::copy).toList();
        servingContainer = servingContainer.map(ItemStack::copy);
        if (outputs.isEmpty() || outputs.stream().allMatch(ItemStack::isEmpty)) {
            throw new IllegalArgumentException("planned recipe must have a non-empty output");
        }
        if (processingTime < 0) {
            throw new IllegalArgumentException("processingTime cannot be negative");
        }
    }

    public ItemStack primaryOutput() {
        return outputs.getFirst().copy();
    }
}
```

Create `RecipePlan.java`:

```java
package net.inkyquill.cfbdelight.kitchen.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.List;

public record RecipePlan(
        ResourceLocation targetRecipeId,
        ItemStack output,
        PlanState state,
        List<PlannedRecipe> steps,
        EnumSet<PlanningBlocker> blockers
) {
    public RecipePlan {
        output = output.copy();
        steps = List.copyOf(steps);
        blockers = blockers.clone();
    }

    public static RecipePlan blocked(ResourceLocation targetRecipeId, ItemStack output,
                                     List<PlannedRecipe> steps, EnumSet<PlanningBlocker> blockers) {
        return new RecipePlan(targetRecipeId, output, PlanState.BLOCKED, steps, blockers);
    }
}
```

- [ ] **Step 4: Compile**

Run:

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/KitchenStationType.java src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/PlanningBlocker.java src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/PlanState.java src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/PlannedIngredient.java src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/PlannedRecipe.java src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/RecipePlan.java
git commit -m "feat: add recipe planning data model"
```

## Task 2: Add Inventory Snapshot And Ingredient Matching

**Files:**
- Create: `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/KitchenInventorySnapshot.java`
- Create: `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/IngredientMatcher.java`
- Modify: `src/main/java/net/inkyquill/cfbdelight/kitchen/KitchenInventoryService.java`

- [ ] **Step 1: Add immutable snapshot**

Create `KitchenInventorySnapshot.java`:

```java
package net.inkyquill.cfbdelight.kitchen.recipe;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class KitchenInventorySnapshot {
    private final List<ItemStack> stacks;

    public KitchenInventorySnapshot(List<ItemStack> stacks) {
        this.stacks = stacks.stream()
                .filter(stack -> !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
    }

    public List<ItemStack> stacks() {
        return stacks.stream().map(ItemStack::copy).toList();
    }

    public KitchenInventorySnapshot withConsumed(List<ItemStack> consumed) {
        List<ItemStack> remaining = stacks();
        for (ItemStack toConsume : consumed) {
            int needed = toConsume.getCount();
            for (ItemStack stack : remaining) {
                if (needed <= 0) {
                    break;
                }
                if (ItemStack.isSameItemSameComponents(stack, toConsume)) {
                    int taken = Math.min(needed, stack.getCount());
                    stack.shrink(taken);
                    needed -= taken;
                }
            }
        }
        return new KitchenInventorySnapshot(remaining);
    }

    public KitchenInventorySnapshot withAdded(List<ItemStack> added) {
        List<ItemStack> next = new ArrayList<>(stacks());
        for (ItemStack stack : added) {
            if (!stack.isEmpty()) {
                next.add(stack.copy());
            }
        }
        return new KitchenInventorySnapshot(next);
    }
}
```

- [ ] **Step 2: Add ingredient matcher**

Create `IngredientMatcher.java`:

```java
package net.inkyquill.cfbdelight.kitchen.recipe;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class IngredientMatcher {
    private IngredientMatcher() {
    }

    public static Optional<List<ItemStack>> findMatches(KitchenInventorySnapshot snapshot,
                                                        List<PlannedIngredient> ingredients) {
        List<ItemStack> available = snapshot.stacks();
        List<ItemStack> consumed = new ArrayList<>();

        for (PlannedIngredient ingredient : ingredients) {
            int remaining = ingredient.count();
            for (ItemStack stack : available) {
                if (remaining <= 0) {
                    break;
                }
                if (!ingredient.ingredient().test(stack)) {
                    continue;
                }
                int taken = Math.min(remaining, stack.getCount());
                ItemStack consumedStack = stack.copyWithCount(taken);
                consumed.add(consumedStack);
                stack.shrink(taken);
                remaining -= taken;
            }
            if (remaining > 0) {
                return Optional.empty();
            }
        }

        return Optional.of(consumed);
    }
}
```

- [ ] **Step 3: Add snapshot export to kitchen service**

In `KitchenInventoryService.java`, add:

```java
public KitchenInventorySnapshot snapshot() {
    java.util.ArrayList<ItemStack> stacks = new java.util.ArrayList<>();
    for (KitchenInventoryEndpoint endpoint : endpoints) {
        for (int slot = 0; slot < endpoint.size(); slot++) {
            ItemStack stack = endpoint.get(slot);
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }
    }
    return new KitchenInventorySnapshot(stacks);
}
```

Add import:

```java
import net.inkyquill.cfbdelight.kitchen.recipe.KitchenInventorySnapshot;
```

- [ ] **Step 4: Compile**

Run:

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/KitchenInventorySnapshot.java src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/IngredientMatcher.java src/main/java/net/inkyquill/cfbdelight/kitchen/KitchenInventoryService.java
git commit -m "feat: add kitchen inventory snapshot matching"
```

## Task 3: Add Recipe Adapters

**Files:**
- Create: `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/KitchenRecipeAdapter.java`
- Create: `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/KitchenRecipeAdapters.java`
- Create: `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/adapters/ShapedCraftingAdapter.java`
- Create: `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/adapters/ShapelessCraftingAdapter.java`
- Create: `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/adapters/CuttingBoardAdapter.java`
- Create: `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/adapters/CookingPotAdapter.java`
- Create: `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/adapters/CampfireCookingAdapter.java`

- [ ] **Step 1: Define adapter interface**

Create `KitchenRecipeAdapter.java`:

```java
package net.inkyquill.cfbdelight.kitchen.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import java.util.Optional;

public interface KitchenRecipeAdapter<T extends Recipe<?>> {
    boolean supports(Recipe<?> recipe);

    Optional<PlannedRecipe> adapt(ResourceLocation id, T recipe, HolderLookup.Provider registries);
}
```

- [ ] **Step 2: Add adapter registry**

Create `KitchenRecipeAdapters.java`:

```java
package net.inkyquill.cfbdelight.kitchen.recipe;

import net.inkyquill.cfbdelight.kitchen.recipe.adapters.CampfireCookingAdapter;
import net.inkyquill.cfbdelight.kitchen.recipe.adapters.CookingPotAdapter;
import net.inkyquill.cfbdelight.kitchen.recipe.adapters.CuttingBoardAdapter;
import net.inkyquill.cfbdelight.kitchen.recipe.adapters.ShapedCraftingAdapter;
import net.inkyquill.cfbdelight.kitchen.recipe.adapters.ShapelessCraftingAdapter;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class KitchenRecipeAdapters {
    private static final List<KitchenRecipeAdapter<? extends Recipe<?>>> ADAPTERS = new ArrayList<>();

    static {
        register(new ShapedCraftingAdapter());
        register(new ShapelessCraftingAdapter());
        register(new CuttingBoardAdapter());
        register(new CookingPotAdapter());
        register(new CampfireCookingAdapter());
    }

    private KitchenRecipeAdapters() {
    }

    public static void register(KitchenRecipeAdapter<? extends Recipe<?>> adapter) {
        ADAPTERS.add(adapter);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Optional<PlannedRecipe> adapt(ResourceLocation id, Recipe<?> recipe, HolderLookup.Provider registries) {
        for (KitchenRecipeAdapter adapter : ADAPTERS) {
            if (adapter.supports(recipe)) {
                return adapter.adapt(id, recipe, registries);
            }
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 3: Add vanilla crafting adapters**

Create `ShapedCraftingAdapter.java`:

```java
package net.inkyquill.cfbdelight.kitchen.recipe.adapters;

import net.inkyquill.cfbdelight.kitchen.recipe.KitchenRecipeAdapter;
import net.inkyquill.cfbdelight.kitchen.recipe.KitchenStationType;
import net.inkyquill.cfbdelight.kitchen.recipe.PlannedIngredient;
import net.inkyquill.cfbdelight.kitchen.recipe.PlannedRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.List;
import java.util.Optional;

public final class ShapedCraftingAdapter implements KitchenRecipeAdapter<ShapedRecipe> {
    @Override
    public boolean supports(Recipe<?> recipe) {
        return recipe instanceof ShapedRecipe;
    }

    @Override
    public Optional<PlannedRecipe> adapt(ResourceLocation id, ShapedRecipe recipe, HolderLookup.Provider registries) {
        ItemStack output = recipe.getResultItem(registries).copy();
        if (output.isEmpty()) {
            return Optional.empty();
        }
        List<PlannedIngredient> ingredients = recipe.getIngredients().stream()
                .filter(ingredient -> !ingredient.isEmpty())
                .map(ingredient -> new PlannedIngredient(ingredient, 1))
                .toList();
        return Optional.of(new PlannedRecipe(id, ingredients, List.of(output), KitchenStationType.CRAFTING,
                Optional.empty(), Optional.empty(), 0, false));
    }
}
```

Create `ShapelessCraftingAdapter.java`:

```java
package net.inkyquill.cfbdelight.kitchen.recipe.adapters;

import net.inkyquill.cfbdelight.kitchen.recipe.KitchenRecipeAdapter;
import net.inkyquill.cfbdelight.kitchen.recipe.KitchenStationType;
import net.inkyquill.cfbdelight.kitchen.recipe.PlannedIngredient;
import net.inkyquill.cfbdelight.kitchen.recipe.PlannedRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.util.List;
import java.util.Optional;

public final class ShapelessCraftingAdapter implements KitchenRecipeAdapter<ShapelessRecipe> {
    @Override
    public boolean supports(Recipe<?> recipe) {
        return recipe instanceof ShapelessRecipe;
    }

    @Override
    public Optional<PlannedRecipe> adapt(ResourceLocation id, ShapelessRecipe recipe, HolderLookup.Provider registries) {
        ItemStack output = recipe.getResultItem(registries).copy();
        if (output.isEmpty()) {
            return Optional.empty();
        }
        List<PlannedIngredient> ingredients = recipe.getIngredients().stream()
                .filter(ingredient -> !ingredient.isEmpty())
                .map(ingredient -> new PlannedIngredient(ingredient, 1))
                .toList();
        return Optional.of(new PlannedRecipe(id, ingredients, List.of(output), KitchenStationType.CRAFTING,
                Optional.empty(), Optional.empty(), 0, false));
    }
}
```

- [ ] **Step 4: Add Farmer's Delight and cooking adapters**

Create `CuttingBoardAdapter.java`:

```java
package net.inkyquill.cfbdelight.kitchen.recipe.adapters;

import net.inkyquill.cfbdelight.kitchen.recipe.KitchenRecipeAdapter;
import net.inkyquill.cfbdelight.kitchen.recipe.KitchenStationType;
import net.inkyquill.cfbdelight.kitchen.recipe.PlannedIngredient;
import net.inkyquill.cfbdelight.kitchen.recipe.PlannedRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;

import java.util.List;
import java.util.Optional;

public final class CuttingBoardAdapter implements KitchenRecipeAdapter<CuttingBoardRecipe> {
    @Override
    public boolean supports(Recipe<?> recipe) {
        return recipe instanceof CuttingBoardRecipe;
    }

    @Override
    public Optional<PlannedRecipe> adapt(ResourceLocation id, CuttingBoardRecipe recipe, HolderLookup.Provider registries) {
        List<ItemStack> outputs = recipe.getRollableResults().stream()
                .map(result -> result.stack().copy())
                .filter(stack -> !stack.isEmpty())
                .toList();
        if (outputs.isEmpty() || recipe.getIngredients().isEmpty()) {
            return Optional.empty();
        }
        List<PlannedIngredient> ingredients = List.of(new PlannedIngredient(recipe.getIngredients().getFirst(), 1));
        return Optional.of(new PlannedRecipe(id, ingredients, outputs, KitchenStationType.CUTTING_BOARD,
                Optional.empty(), Optional.empty(), 100, true));
    }
}
```

Create `CookingPotAdapter.java`:

```java
package net.inkyquill.cfbdelight.kitchen.recipe.adapters;

import net.inkyquill.cfbdelight.kitchen.recipe.KitchenRecipeAdapter;
import net.inkyquill.cfbdelight.kitchen.recipe.KitchenStationType;
import net.inkyquill.cfbdelight.kitchen.recipe.PlannedIngredient;
import net.inkyquill.cfbdelight.kitchen.recipe.PlannedRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import java.util.List;
import java.util.Optional;

public final class CookingPotAdapter implements KitchenRecipeAdapter<CookingPotRecipe> {
    @Override
    public boolean supports(Recipe<?> recipe) {
        return recipe instanceof CookingPotRecipe;
    }

    @Override
    public Optional<PlannedRecipe> adapt(ResourceLocation id, CookingPotRecipe recipe, HolderLookup.Provider registries) {
        ItemStack output = recipe.getResultItem(registries).copy();
        if (output.isEmpty()) {
            return Optional.empty();
        }
        List<PlannedIngredient> ingredients = recipe.getIngredients().stream()
                .filter(ingredient -> !ingredient.isEmpty())
                .map(ingredient -> new PlannedIngredient(ingredient, 1))
                .toList();
        Optional<ItemStack> container = Optional.of(recipe.getOutputContainer()).filter(stack -> !stack.isEmpty()).map(ItemStack::copy);
        return Optional.of(new PlannedRecipe(id, ingredients, List.of(output), KitchenStationType.COOKING_POT,
                container, Optional.empty(), recipe.getCookTime(), true));
    }
}
```

Create `CampfireCookingAdapter.java`:

```java
package net.inkyquill.cfbdelight.kitchen.recipe.adapters;

import net.inkyquill.cfbdelight.kitchen.recipe.KitchenRecipeAdapter;
import net.inkyquill.cfbdelight.kitchen.recipe.KitchenStationType;
import net.inkyquill.cfbdelight.kitchen.recipe.PlannedIngredient;
import net.inkyquill.cfbdelight.kitchen.recipe.PlannedRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;

import java.util.List;
import java.util.Optional;

public final class CampfireCookingAdapter implements KitchenRecipeAdapter<CampfireCookingRecipe> {
    @Override
    public boolean supports(Recipe<?> recipe) {
        return recipe instanceof CampfireCookingRecipe;
    }

    @Override
    public Optional<PlannedRecipe> adapt(ResourceLocation id, CampfireCookingRecipe recipe, HolderLookup.Provider registries) {
        ItemStack output = recipe.getResultItem(registries).copy();
        if (output.isEmpty() || recipe.getIngredients().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PlannedRecipe(id, List.of(new PlannedIngredient(recipe.getIngredients().getFirst(), 1)),
                List.of(output), KitchenStationType.GRILL, Optional.empty(), Optional.empty(),
                recipe.getCookingTime(), true));
    }
}
```

- [ ] **Step 5: Compile**

Run:

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/net/inkyquill/cfbdelight/kitchen/recipe
git commit -m "feat: add kitchen recipe adapters"
```

## Task 4: Add Recipe Index

**Files:**
- Create: `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/RecipeIndex.java`

- [ ] **Step 1: Add recipe index**

Create `RecipeIndex.java`:

```java
package net.inkyquill.cfbdelight.kitchen.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class RecipeIndex {
    private final List<PlannedRecipe> recipes;
    private final List<PlannedRecipe> edibleOutputs;
    private final Map<String, List<PlannedRecipe>> recipesByOutputKey;

    private RecipeIndex(List<PlannedRecipe> recipes) {
        this.recipes = recipes.stream()
                .sorted(Comparator.comparing(recipe -> recipe.id().toString()))
                .toList();
        this.edibleOutputs = this.recipes.stream()
                .filter(recipe -> recipe.primaryOutput().isEdible())
                .toList();
        this.recipesByOutputKey = this.recipes.stream()
                .collect(Collectors.groupingBy(recipe -> stackKey(recipe.primaryOutput())));
    }

    public static RecipeIndex build(RecipeManager recipeManager, HolderLookup.Provider registries) {
        List<PlannedRecipe> plannedRecipes = recipeManager.getRecipes().stream()
                .map(holder -> adapt(holder, registries))
                .flatMap(Optional::stream)
                .toList();
        return new RecipeIndex(plannedRecipes);
    }

    private static Optional<PlannedRecipe> adapt(RecipeHolder<?> holder, HolderLookup.Provider registries) {
        return KitchenRecipeAdapters.adapt(holder.id(), holder.value(), registries);
    }

    public List<PlannedRecipe> recipes() {
        return recipes;
    }

    public List<PlannedRecipe> edibleOutputs() {
        return edibleOutputs;
    }

    public List<PlannedRecipe> recipesProducing(ItemStack stack) {
        return recipesByOutputKey.getOrDefault(stackKey(stack), List.of());
    }

    public static String stackKey(ItemStack stack) {
        return stack.getItemHolder().unwrapKey()
                .map(key -> key.location().toString())
                .orElse(stack.getItem().toString());
    }
}
```

- [ ] **Step 2: Compile**

Run:

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/RecipeIndex.java
git commit -m "feat: index supported edible kitchen recipes"
```

## Task 5: Add Read-Only Recipe Planner

**Files:**
- Create: `src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/RecipePlanner.java`

- [ ] **Step 1: Add planner implementation**

Create `RecipePlanner.java`:

```java
package net.inkyquill.cfbdelight.kitchen.recipe;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RecipePlanner {
    private static final int MAX_DEPTH = 4;

    private final RecipeIndex recipeIndex;
    private final Set<KitchenStationType> availableStations;

    public RecipePlanner(RecipeIndex recipeIndex, Set<KitchenStationType> availableStations) {
        this.recipeIndex = recipeIndex;
        this.availableStations = Set.copyOf(availableStations);
    }

    public List<RecipePlan> planAllEdible(KitchenInventorySnapshot snapshot) {
        return recipeIndex.edibleOutputs().stream()
                .map(recipe -> plan(recipe, snapshot))
                .sorted(Comparator
                        .comparing((RecipePlan plan) -> plan.state().ordinal())
                        .thenComparing(plan -> plan.output().getHoverName().getString())
                        .thenComparing(plan -> plan.targetRecipeId().toString()))
                .toList();
    }

    public RecipePlan plan(PlannedRecipe target, KitchenInventorySnapshot snapshot) {
        return plan(target, snapshot, 0, new HashSet<>());
    }

    private RecipePlan plan(PlannedRecipe target, KitchenInventorySnapshot snapshot, int depth, Set<String> visiting) {
        EnumSet<PlanningBlocker> blockers = EnumSet.noneOf(PlanningBlocker.class);
        if (!availableStations.contains(target.stationType())) {
            blockers.add(PlanningBlocker.MISSING_STATION);
        }
        if (target.servingContainer().isPresent()
                && IngredientMatcher.findMatches(snapshot, List.of(new PlannedIngredient(
                net.minecraft.world.item.crafting.Ingredient.of(target.servingContainer().get().getItem()),
                target.servingContainer().get().getCount()))).isEmpty()) {
            blockers.add(PlanningBlocker.MISSING_CONTAINER);
        }

        var directMatches = IngredientMatcher.findMatches(snapshot, target.ingredients());
        if (directMatches.isPresent() && blockers.isEmpty()) {
            return new RecipePlan(target.id(), target.primaryOutput(), PlanState.READY, List.of(target), blockers);
        }

        if (depth >= MAX_DEPTH || visiting.contains(target.id().toString())) {
            blockers.add(PlanningBlocker.MISSING_RAW_INGREDIENT);
            return RecipePlan.blocked(target.id(), target.primaryOutput(), List.of(target), blockers);
        }

        visiting.add(target.id().toString());
        KitchenInventorySnapshot working = snapshot;
        List<PlannedRecipe> steps = new ArrayList<>();
        boolean usedIntermediate = false;

        for (PlannedIngredient ingredient : target.ingredients()) {
            if (IngredientMatcher.findMatches(working, List.of(ingredient)).isPresent()) {
                working = working.withConsumed(IngredientMatcher.findMatches(working, List.of(ingredient)).get());
                continue;
            }

            PlannedRecipe intermediate = findIntermediateFor(ingredient);
            if (intermediate == null) {
                blockers.add(PlanningBlocker.MISSING_INTERMEDIATE_RECIPE);
                continue;
            }

            RecipePlan intermediatePlan = plan(intermediate, working, depth + 1, visiting);
            if (intermediatePlan.state() == PlanState.BLOCKED) {
                blockers.addAll(intermediatePlan.blockers());
                continue;
            }

            steps.addAll(intermediatePlan.steps());
            working = working.withAdded(intermediate.outputs());
            usedIntermediate = true;

            var afterCraftMatches = IngredientMatcher.findMatches(working, List.of(ingredient));
            if (afterCraftMatches.isPresent()) {
                working = working.withConsumed(afterCraftMatches.get());
            } else {
                blockers.add(PlanningBlocker.MISSING_RAW_INGREDIENT);
            }
        }

        visiting.remove(target.id().toString());

        if (!blockers.isEmpty()) {
            steps.add(target);
            return RecipePlan.blocked(target.id(), target.primaryOutput(), steps, blockers);
        }

        steps.add(target);
        return new RecipePlan(target.id(), target.primaryOutput(),
                usedIntermediate ? PlanState.CRAFTABLE_WITH_INTERMEDIATES : PlanState.READY,
                steps, EnumSet.noneOf(PlanningBlocker.class));
    }

    private PlannedRecipe findIntermediateFor(PlannedIngredient ingredient) {
        return recipeIndex.recipes().stream()
                .filter(recipe -> ingredient.ingredient().test(recipe.primaryOutput()))
                .min(Comparator
                        .comparingInt((PlannedRecipe recipe) -> recipe.ingredients().size())
                        .thenComparing(recipe -> recipe.id().toString()))
                .orElse(null);
    }
}
```

During implementation, simplify repeated `IngredientMatcher.findMatches` calls into local variables where needed. The compiled code must not call `.get()` on an empty `Optional`.

- [ ] **Step 2: Compile and fix planner API mismatches**

Run:

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/net/inkyquill/cfbdelight/kitchen/recipe/RecipePlanner.java
git commit -m "feat: add read-only kitchen recipe planner"
```

## Task 6: Surface Planner Summary In Chef's Counter

**Files:**
- Modify: `src/main/java/net/inkyquill/cfbdelight/menu/ChefCounterMenu.java`
- Modify: `src/main/java/net/inkyquill/cfbdelight/client/screen/ChefCounterScreen.java`
- Modify: `src/main/resources/assets/cfb_delight/lang/en_us.json`

- [ ] **Step 1: Expand Chef Counter menu data slots**

In `ChefCounterMenu.java`, expand `SimpleContainerData` from 3 values to 7 values:

```java
private static final int DATA_FLOOR_COUNT = 0;
private static final int DATA_INVENTORY_PROVIDER_COUNT = 1;
private static final int DATA_MULTIPLE_COUNTERS = 2;
private static final int DATA_EDIBLE_RECIPE_COUNT = 3;
private static final int DATA_READY_COUNT = 4;
private static final int DATA_INTERMEDIATE_COUNT = 5;
private static final int DATA_BLOCKED_COUNT = 6;
```

Add getters:

```java
public int getEdibleRecipeCount() {
    return data.get(DATA_EDIBLE_RECIPE_COUNT);
}

public int getReadyRecipeCount() {
    return data.get(DATA_READY_COUNT);
}

public int getIntermediateRecipeCount() {
    return data.get(DATA_INTERMEDIATE_COUNT);
}

public int getBlockedRecipeCount() {
    return data.get(DATA_BLOCKED_COUNT);
}
```

In `createServerData`, after scanning and counting inventory providers, build a `KitchenInventoryService` the same way `CabinetOfHoldingBlockEntity#createMenu` does. Use it to create a snapshot. Build a `RecipeIndex`, infer available stations from scan members, run `RecipePlanner#planAllEdible`, and fill the summary counts. If no floor exists, set all recipe counts to `0`.

- [ ] **Step 2: Add a small shared kitchen endpoint builder if duplication becomes awkward**

Create `src/main/java/net/inkyquill/cfbdelight/kitchen/KitchenServices.java` and use it from both `ChefCounterMenu` and `CabinetOfHoldingBlockEntity`:

```java
package net.inkyquill.cfbdelight.kitchen;

import net.inkyquill.cfbdelight.block.entity.AutoCutterBlockEntity;
import net.inkyquill.cfbdelight.block.entity.GrillBlockEntity;
import net.inkyquill.cfbdelight.block.entity.PotStandBlockEntity;
import net.inkyquill.cfbdelight.kitchen.recipe.KitchenStationType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class KitchenServices {
    private KitchenServices() {
    }

    public static List<KitchenInventoryEndpoint> inventoryEndpoints(Level level, KitchenScanResult scan, BlockPos terminalPos) {
        List<KitchenInventoryEndpoint> endpoints = new ArrayList<>();
        for (BlockPos member : scan.memberBlocks()) {
            if (member.equals(terminalPos)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(member);
            if (KitchenScanner.isKitchenStation(blockEntity)) {
                continue;
            }
            if (blockEntity instanceof Container container) {
                endpoints.add(new KitchenInventoryEndpoint(member, container));
                continue;
            }
            var itemHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, member, null);
            if (itemHandler != null) {
                endpoints.add(new KitchenInventoryEndpoint(member, itemHandler));
            }
        }
        return List.copyOf(endpoints);
    }

    public static Set<KitchenStationType> stationTypes(Level level, KitchenScanResult scan) {
        Set<KitchenStationType> stationTypes = new LinkedHashSet<>();
        stationTypes.add(KitchenStationType.CRAFTING);
        for (BlockPos member : scan.memberBlocks()) {
            BlockEntity blockEntity = level.getBlockEntity(member);
            if (blockEntity instanceof AutoCutterBlockEntity) {
                stationTypes.add(KitchenStationType.CUTTING_BOARD);
            } else if (blockEntity instanceof PotStandBlockEntity) {
                stationTypes.add(KitchenStationType.COOKING_POT);
            } else if (blockEntity instanceof GrillBlockEntity) {
                stationTypes.add(KitchenStationType.GRILL);
            }
        }
        return Set.copyOf(stationTypes);
    }
}
```

- [ ] **Step 3: Update Chef Counter screen labels**

In `ChefCounterScreen.java`, draw the summary below the existing floor/member lines:

```java
graphics.drawString(font,
        Component.translatable("gui.cfb_delight.chef_counter.recipes", menu.getEdibleRecipeCount()),
        titleLabelX, 46, 0x404040, false);
graphics.drawString(font,
        Component.translatable("gui.cfb_delight.chef_counter.ready", menu.getReadyRecipeCount()),
        titleLabelX, 58, 0x2F6F3E, false);
graphics.drawString(font,
        Component.translatable("gui.cfb_delight.chef_counter.intermediates", menu.getIntermediateRecipeCount()),
        titleLabelX + 78, 58, 0x6F5A2F, false);
graphics.drawString(font,
        Component.translatable("gui.cfb_delight.chef_counter.blocked", menu.getBlockedRecipeCount()),
        titleLabelX, 70, 0x7A3030, false);
```

Move the multiple-counter warning down to avoid text overlap.

- [ ] **Step 4: Add lang keys**

In `en_us.json`, add:

```json
"gui.cfb_delight.chef_counter.recipes": "Foods indexed: %s",
"gui.cfb_delight.chef_counter.ready": "Ready: %s",
"gui.cfb_delight.chef_counter.intermediates": "Intermediate: %s",
"gui.cfb_delight.chef_counter.blocked": "Blocked: %s"
```

- [ ] **Step 5: Compile**

Run:

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/net/inkyquill/cfbdelight/menu/ChefCounterMenu.java src/main/java/net/inkyquill/cfbdelight/client/screen/ChefCounterScreen.java src/main/resources/assets/cfb_delight/lang/en_us.json src/main/java/net/inkyquill/cfbdelight/kitchen/KitchenServices.java
git commit -m "feat: show recipe planning summary on chef counter"
```

If `KitchenServices.java` was not needed, omit it from `git add`.

## Task 7: Add Focused Verification

**Files:**
- Modify: `build.gradle`
- Create: `src/test/java/net/inkyquill/cfbdelight/kitchen/recipe/IngredientMatcherTest.java`

- [ ] **Step 1: Add JUnit test dependencies**

In `build.gradle`, add these dependencies inside the existing `dependencies` block:

```groovy
testImplementation "org.junit.jupiter:junit-jupiter-api:5.11.3"
testRuntimeOnly "org.junit.jupiter:junit-jupiter-engine:5.11.3"
```

Add this task configuration near the existing Java compile configuration:

```groovy
tasks.named('test', Test).configure {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Add unit tests for ingredient matching**

Create `IngredientMatcherTest.java`:

```java
package net.inkyquill.cfbdelight.kitchen.recipe;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngredientMatcherTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void consumesAcrossStacks() {
        KitchenInventorySnapshot snapshot = new KitchenInventorySnapshot(List.of(
                new ItemStack(Items.WHEAT, 2),
                new ItemStack(Items.WHEAT, 2)
        ));
        var matches = IngredientMatcher.findMatches(snapshot, List.of(new PlannedIngredient(Ingredient.of(Items.WHEAT), 3)));
        assertTrue(matches.isPresent());
        assertEquals(3, matches.get().stream().mapToInt(ItemStack::getCount).sum());
    }

    @Test
    void failsWhenMissingItems() {
        KitchenInventorySnapshot snapshot = new KitchenInventorySnapshot(List.of(new ItemStack(Items.WHEAT, 1)));
        var matches = IngredientMatcher.findMatches(snapshot, List.of(new PlannedIngredient(Ingredient.of(Items.WHEAT), 2)));
        assertTrue(matches.isEmpty());
    }
}
```

- [ ] **Step 3: Run focused tests**

Run:

```bash
./gradlew test --tests net.inkyquill.cfbdelight.kitchen.recipe.IngredientMatcherTest
```

Expected: the two `IngredientMatcherTest` tests pass.

- [ ] **Step 4: Run build**

Run:

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add build.gradle src/test/java/net/inkyquill/cfbdelight/kitchen/recipe/IngredientMatcherTest.java
git commit -m "test: cover recipe planning primitives"
```

## Final Verification

- [ ] **Step 1: Run build**

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run game tests**

```bash
./gradlew runGameTestServer
```

Expected: if no game tests exist yet, NeoForge may report no registered game tests. This is acceptable for this milestone because Task 7 covers the new pure planner primitives with JUnit.

- [ ] **Step 3: Run client smoke test**

```bash
./gradlew runClient
```

Manual checks:

- A world can load with Farmer's Delight and current transitional Cooking for Blockheads runtime dependencies.
- Chef's Counter over Kitchen Floor opens.
- Chef's Counter without Kitchen Floor shows the missing kitchen status.
- Chef's Counter over a kitchen with a chest of ingredients shows non-zero indexed food counts.
- Ready, intermediate, and blocked counts change when ingredients or station blocks are added or removed.
- Cabinet of Holding still opens and routes merged inventory after the planner changes.

- [ ] **Step 4: Record follow-up plan list**

Add a short section to `Plans.md`:

```markdown
## Next Milestones

- Chef's Counter recipe book UI and details panel.
- Confirmation and station queue execution.
- Standalone station interfaces and removal of Cooking for Blockheads APIs.
- Persistent Kitchen Floor camouflage.
- Curios pouch.
```

- [ ] **Step 5: Commit follow-up notes**

```bash
git add Plans.md
git commit -m "docs: record recipe planning follow-up milestones"
```

## Self-Review Checklist

- The milestone produces useful player-visible output without requiring the full recipe book UI.
- Planning remains read-only and does not consume inventory.
- The adapter model is modular enough for later mod integrations.
- The Chef's Counter summary can be replaced by the full recipe book UI in the next milestone.
- Queue execution and station rewrites remain out of scope.
- The plan avoids touching the Curios pouch.
