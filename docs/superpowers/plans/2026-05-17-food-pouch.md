# Milestone 5: Food Pouch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a standalone Curios-equipped Food Pouch with tiered storage, large food stacks, configurable smart auto-feeding, container return, and a pouch UI.

**Architecture:** Keep the pouch independent from the kitchen planner. The pouch item owns a codec-backed data component for inventory, two small data components for feeding settings, a server-side feeding service for tick behavior, and a normal menu/screen pair for storage and settings. Curios is the runtime integration layer only: it provides the dedicated equipment slot and invokes pouch ticking while equipped.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.221, Curios NeoForge 9.5.1+1.21.1, NeoForge data components, vanilla `AbstractContainerMenu`, NeoForge custom payloads for equipped-pouch opening, JUnit 5 focused tests.

---

## Scope

- Add required Curios dependency and register a dedicated `delightful_kitchen:pouch` Curios slot.
- Add Basic, Advanced, and Ultimate Food Pouch items.
- Store pouch contents in a custom codec-backed data component that supports tier-sized slot lists and food stack counts up to 1024.
- Add right-click hand opening, equipped opening through a default-unbound keybind, and a pouch menu/screen.
- Add standard pouch slot behavior: left/right click, shift-click, number keys, food stack limits, non-food default stack limits.
- Add configurable auto-feeding with hunger threshold, hurt override, cooldowns, skipped items, potion/effect application, and container return.
- Add recipes, lang keys, item models, and item textures.
- Add focused tests for serialization, insertion/extraction limits, feeding decisions, cooldowns, and container return.
- Update README, specs, roadmap, and milestone status as the final task.

## Out Of Scope

- Kitchen planner integration.
- Pouch-to-kitchen routing.
- Preserving pouch contents during tier upgrade recipes.
- Curios slot upgrades or extra slots.
- Inventory-screen button polish; the equipped-open path is the keybind.
- Tag-driven food/container rules beyond explicit v1 checks.

## Starting Point

- The project is already renamed to `delightful_kitchen`.
- The mod has existing item, menu, client screen, payload, lang, recipe, and test patterns.
- There is no existing Curios dependency or data component registry.
- The current roadmap lists Milestone 5 as Food Pouch and marks kitchen planner integration out of scope.
- The food pouch spec has one recipe inconsistency: it says "Leather x6" but shows a pattern with eight leather. This plan follows the shown pouch-shape pattern and updates the spec note in the final documentation task.

## Task 1: Add Curios Dependency And Slot Data

**Files:**

- Modify `gradle.properties`
- Modify `build.gradle`
- Modify `src/main/resources/META-INF/neoforge.mods.toml`
- Create `src/main/resources/data/curios/curios/slots/delightful_kitchen.json`
- Create `src/main/resources/data/curios/curios/entities/player.json`
- Create `src/main/resources/data/curios/tags/item/pouch.json`
- Create `src/main/resources/assets/delightful_kitchen/textures/gui/sprites/curios/pouch_slot.png`

**Steps:**

- [ ] Add a Curios version property:

```properties
curios_version=9.5.1+1.21.1
```

- [ ] Add TheIllusiveC4 Maven repository:

```groovy
maven {
    name = "TheIllusiveC4"
    url = "https://maven.theillusivec4.top/"
}
```

- [ ] Add Curios dependencies:

```groovy
compileOnly "top.theillusivec4.curios:curios-neoforge:${curios_version}:api"
localRuntime "top.theillusivec4.curios:curios-neoforge:${curios_version}"
testImplementation "top.theillusivec4.curios:curios-neoforge:${curios_version}:api"
```

- [ ] Add required Curios metadata in `neoforge.mods.toml`:

```toml
[[dependencies.${mod_id}]]
modId="curios"
type="required"
versionRange="[9.5.1,10)"
ordering="NONE"
side="BOTH"
```

- [ ] Register the Curios player slot with one pouch slot and a Delightful Kitchen slot icon:

```json
{
  "replace": false,
  "slots": [
    "delightful_kitchen:pouch"
  ]
}
```

```json
{
  "replace": false,
  "size": 1,
  "icon": "delightful_kitchen:curios/pouch_slot"
}
```

```json
{
  "replace": false,
  "values": [
    "delightful_kitchen:food_pouch",
    "delightful_kitchen:advanced_food_pouch",
    "delightful_kitchen:ultimate_food_pouch"
  ]
}
```

- [ ] Run `./gradlew compileJava` after dependency wiring before touching Curios APIs in code. If the artifact classifier or repository layout differs, fix this task before continuing.

## Task 2: Register Data Components, Tiers, And Items

**Files:**

- Create `src/main/java/net/inkyquill/delightfulkitchen/component/ModDataComponents.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/pouch/FoodPouchTier.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/pouch/FeedThreshold.java`
- Modify `src/main/java/net/inkyquill/delightfulkitchen/DelightfulKitchen.java`
- Modify `src/main/java/net/inkyquill/delightfulkitchen/item/ModItems.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/item/FoodPouchItem.java`
- Add tests in `src/test/java/net/inkyquill/delightfulkitchen/pouch/FoodPouchTierTest.java`

**Model shape:**

```java
public enum FoodPouchTier {
    BASIC(18, 1, 6, 3),
    ADVANCED(36, 4, 6, 6),
    ULTIMATE(72, 16, 9, 8);

    public static final int MAX_FOOD_STACK_SIZE = 1024;

    private final int slots;
    private final int stackMultiplier;
    private final int columns;
    private final int rows;

    FoodPouchTier(int slots, int stackMultiplier, int columns, int rows) {
        this.slots = slots;
        this.stackMultiplier = stackMultiplier;
        this.columns = columns;
        this.rows = rows;
    }

    public int slotLimit(ItemStack stack) {
        if (!stack.isEmpty() && stack.getFoodProperties(null) != null) {
            return Math.min(MAX_FOOD_STACK_SIZE, stack.getMaxStackSize() * stackMultiplier);
        }
        return stack.getMaxStackSize();
    }
}
```

```java
public enum FeedThreshold implements StringRepresentable {
    ANY("any"),
    HALF("half"),
    FULL("full");

    public static final Codec<FeedThreshold> CODEC = StringRepresentable.fromEnum(FeedThreshold::values);
}
```

```java
public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, DelightfulKitchen.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FoodPouchInventory>> POUCH_INVENTORY =
            DATA_COMPONENT_TYPES.register("pouch_inventory", () -> DataComponentType.<FoodPouchInventory>builder()
                    .persistent(FoodPouchInventory.CODEC)
                    .networkSynchronized(FoodPouchInventory.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FeedThreshold>> FEED_AT_HUNGER_LEVEL =
            DATA_COMPONENT_TYPES.register("feed_at_hunger_level", () -> DataComponentType.<FeedThreshold>builder()
                    .persistent(FeedThreshold.CODEC)
                    .networkSynchronized(ByteBufCodecs.idMapper(
                            value -> value.ordinal(),
                            ordinal -> FeedThreshold.values()[Mth.clamp(ordinal, 0, FeedThreshold.values().length - 1)]))
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> FEED_WHEN_HURT =
            DATA_COMPONENT_TYPES.register("feed_when_hurt", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    private ModDataComponents() {
    }
}
```

**Steps:**

- [ ] Register `ModDataComponents.DATA_COMPONENT_TYPES` in `DelightfulKitchen`.
- [ ] Register three pouch items in `ModItems` with stack size 1:

```java
public static final DeferredItem<FoodPouchItem> FOOD_POUCH = ITEMS.register("food_pouch",
        () -> new FoodPouchItem(FoodPouchTier.BASIC, new Item.Properties().stacksTo(1)));
public static final DeferredItem<FoodPouchItem> ADVANCED_FOOD_POUCH = ITEMS.register("advanced_food_pouch",
        () -> new FoodPouchItem(FoodPouchTier.ADVANCED, new Item.Properties().stacksTo(1)));
public static final DeferredItem<FoodPouchItem> ULTIMATE_FOOD_POUCH = ITEMS.register("ultimate_food_pouch",
        () -> new FoodPouchItem(FoodPouchTier.ULTIMATE, new Item.Properties().stacksTo(1)));
```

- [ ] Add pouch items to the existing Delightful Kitchen creative tab.
- [ ] Keep defaults on the item stack, not in global config:

```java
public static FoodPouchInventory inventory(ItemStack pouch) {
    FoodPouchTier tier = FoodPouchItem.tierOf(pouch);
    FoodPouchInventory inventory = pouch.get(ModDataComponents.POUCH_INVENTORY.get());
    if (inventory == null || inventory.size() != tier.slots()) {
        inventory = FoodPouchInventory.empty(tier.slots());
        pouch.set(ModDataComponents.POUCH_INVENTORY.get(), inventory);
    }
    return inventory;
}
```

## Task 3: Implement Codec-Backed Pouch Inventory

**Files:**

- Create `src/main/java/net/inkyquill/delightfulkitchen/pouch/FoodPouchInventory.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/pouch/FoodPouchSlotView.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/pouch/FoodPouchInventoryOps.java`
- Add tests in `src/test/java/net/inkyquill/delightfulkitchen/pouch/FoodPouchInventoryTest.java`

**Inventory rules:**

- Slot count derives from tier.
- Food stack limit is `min(1024, item max stack * tier multiplier)`.
- Non-food stack limit is the item max stack size.
- Insert order is merge-compatible-slots first, then empty slots.
- Extract by slot never returns more than the slot's count.
- All public getters return copies of `ItemStack`.
- Serialization must preserve counts above 64.

**Core record shape:**

```java
public record FoodPouchInventory(List<ItemStack> stacks) {
    public static final Codec<FoodPouchInventory> CODEC = ItemStack.OPTIONAL_CODEC
            .listOf()
            .xmap(FoodPouchInventory::new, FoodPouchInventory::stacks);

    public static final StreamCodec<RegistryFriendlyByteBuf, FoodPouchInventory> STREAM_CODEC =
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()).map(
                    FoodPouchInventory::new,
                    FoodPouchInventory::stacks
            );

    public FoodPouchInventory {
        stacks = stacks.stream().map(ItemStack::copy).toList();
    }

    public static FoodPouchInventory empty(int slots) {
        return new FoodPouchInventory(Collections.nCopies(slots, ItemStack.EMPTY));
    }

    public ItemStack get(int slot) {
        if (slot < 0 || slot >= stacks.size()) {
            return ItemStack.EMPTY;
        }
        return stacks.get(slot).copy();
    }

    public FoodPouchInventory withSlot(int slot, ItemStack stack) {
        List<ItemStack> copy = new ArrayList<>(stacks);
        copy.set(slot, stack.copy());
        return new FoodPouchInventory(copy);
    }
}
```

**Tests:**

- [ ] Basic inventory serializes/deserializes 18 slots.
- [ ] Advanced food stack preserves count 256.
- [ ] Ultimate food stack preserves count 1024.
- [ ] Non-food is clamped to the vanilla item max.
- [ ] Insert splits one large food stack across existing and empty slots.
- [ ] Extract from invalid slot returns empty and does not mutate.
- [ ] `usedSlots()` ignores empty stacks.

## Task 4: Add Pouch Menu, Server Container, And Slot Behavior

**Files:**

- Create `src/main/java/net/inkyquill/delightfulkitchen/menu/FoodPouchMenu.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/menu/slot/FoodPouchSlot.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/pouch/FoodPouchContainer.java`
- Modify `src/main/java/net/inkyquill/delightfulkitchen/menu/ModMenuTypes.java`
- Modify `src/main/java/net/inkyquill/delightfulkitchen/item/FoodPouchItem.java`
- Add tests in `src/test/java/net/inkyquill/delightfulkitchen/menu/FoodPouchMenuTest.java`

**Menu behavior:**

- Right-clicking a pouch in hand opens the menu.
- Menu binds to the exact pouch `ItemStack` from main hand, off hand, or Curios slot.
- Pouch slots are backed by `FoodPouchContainer`, which persists changes to the pouch stack after every mutation.
- Shift-click from player inventory inserts into the pouch using pouch slot limits.
- Shift-click from pouch slots routes to player inventory.
- Number keys and swap interactions cannot move the pouch stack into itself.
- The menu exposes tier, rows, columns, threshold, and hurt override through `ContainerData`.

**Important menu guard:**

```java
private boolean isBoundPouchStack(ItemStack stack) {
    return !stack.isEmpty() && stack == pouchStack;
}

@Override
public ItemStack quickMoveStack(Player player, int index) {
    Slot slot = slots.get(index);
    if (!slot.hasItem()) {
        return ItemStack.EMPTY;
    }
    ItemStack original = slot.getItem();
    if (isBoundPouchStack(original)) {
        return ItemStack.EMPTY;
    }
    // Pouch -> player, player -> pouch, then persist container.
}
```

**Settings through menu buttons:**

- Button id `0`: cycle `ANY -> HALF -> FULL -> ANY`.
- Button id `1`: toggle feed when hurt.

```java
@Override
public boolean clickMenuButton(Player player, int id) {
    if (id == 0) {
        pouchStack.set(ModDataComponents.FEED_AT_HUNGER_LEVEL.get(), nextThreshold());
        return true;
    }
    if (id == 1) {
        pouchStack.set(ModDataComponents.FEED_WHEN_HURT.get(), !feedWhenHurt());
        return true;
    }
    return false;
}
```

## Task 5: Add Pouch Screen And Client Keybind

**Files:**

- Create `src/main/java/net/inkyquill/delightfulkitchen/client/screen/FoodPouchScreen.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/client/FoodPouchKeyMappings.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/network/payload/ServerboundOpenEquippedPouchPayload.java`
- Modify `src/main/java/net/inkyquill/delightfulkitchen/client/DelightfulKitchenClient.java`
- Modify `src/main/java/net/inkyquill/delightfulkitchen/network/ModPayloads.java`
- Add tests in `src/test/java/net/inkyquill/delightfulkitchen/client/screen/FoodPouchScreenTest.java`

**Screen layout:**

- Basic: 6 columns by 3 rows.
- Advanced: 6 columns by 6 rows.
- Ultimate: 9 columns by 8 rows.
- Player inventory below the pouch grid.
- Settings panel on the right with two icon/text buttons:
  - Hunger threshold cycle.
  - Feed when hurt toggle.
- Food and non-food slot backgrounds use subtle different tints.
- Tooltips include stack count and slot limit, for example `128 / 256`.

**Equipped-open payload:**

```java
public record ServerboundOpenEquippedPouchPayload() implements CustomPacketPayload {
    public static final Type<ServerboundOpenEquippedPouchPayload> TYPE =
            new Type<>(DelightfulKitchen.id("open_equipped_pouch"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundOpenEquippedPouchPayload> STREAM_CODEC =
            StreamCodec.unit(new ServerboundOpenEquippedPouchPayload());
}
```

**Server handling:**

- [ ] Register `ServerboundOpenEquippedPouchPayload`.
- [ ] On server receipt, find the equipped Curios pouch in slot `delightful_kitchen:pouch`.
- [ ] If found, open `FoodPouchMenu` bound to that stack.
- [ ] If not found, do nothing.

**Keybind:**

- [ ] Register a default-unbound key mapping in a Delightful Kitchen category.
- [ ] On key press, send `ServerboundOpenEquippedPouchPayload`.

## Task 6: Implement Auto-Feeding Decision Logic

**Files:**

- Create `src/main/java/net/inkyquill/delightfulkitchen/pouch/FoodPouchFeeder.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/pouch/FoodPouchFeedingResult.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/pouch/FoodPouchCooldowns.java`
- Add tests in `src/test/java/net/inkyquill/delightfulkitchen/pouch/FoodPouchFeederTest.java`

**Decision rules:**

- Skip creative players.
- Skip spectator players.
- Skip dead players.
- Skip sleeping players.
- Feed only if hunger is below 20.
- Skip non-food stacks.
- Skip foods with nutrition below 1.
- Skip `minecraft:ominous_bottle`.
- If `feed_when_hurt` is true and `player.getHealth() < player.getMaxHealth()`, bypass the hunger threshold.
- Otherwise apply threshold:
  - `ANY`: missing hunger >= 1.
  - `HALF`: missing hunger >= ceil(food nutrition / 2).
  - `FULL`: missing hunger >= food nutrition.

**Pure helper:**

```java
public static boolean shouldEat(FoodData foodData, LivingEntity player, FoodProperties food,
                                FeedThreshold threshold, boolean feedWhenHurt) {
    int missing = 20 - foodData.getFoodLevel();
    if (missing <= 0 || food.nutrition() < 1) {
        return false;
    }
    if (feedWhenHurt && player.getHealth() < player.getMaxHealth()) {
        return true;
    }
    return switch (threshold) {
        case ANY -> missing >= 1;
        case HALF -> missing >= Math.max(1, Mth.ceil(food.nutrition() / 2.0F));
        case FULL -> missing >= food.nutrition();
    };
}
```

**Cooldown rules:**

- Consumed food and player is still hungry: 10 ticks.
- Consumed food and player is full: 100 ticks.
- No food consumed: 100 ticks.

**Tests:**

- [ ] `ANY` feeds with one missing hunger point.
- [ ] `HALF` waits until half the nutrition would fit.
- [ ] `FULL` waits until the full nutrition would fit.
- [ ] Hurt override feeds even when `HALF` would wait.
- [ ] Hurt override off respects threshold.
- [ ] Ominous bottle is ignored.
- [ ] Cooldown returns 10 when food was consumed and hunger remains below full.
- [ ] Cooldown returns 100 when food was consumed and hunger is full.
- [ ] Cooldown returns 100 when no food was consumed.

## Task 7: Implement Consumption, Effects, Containers, And Curios Tick

**Files:**

- Modify `src/main/java/net/inkyquill/delightfulkitchen/item/FoodPouchItem.java`
- Modify `src/main/java/net/inkyquill/delightfulkitchen/pouch/FoodPouchFeeder.java`
- Create `src/main/java/net/inkyquill/delightfulkitchen/pouch/FoodPouchContainerReturn.java`
- Add tests in `src/test/java/net/inkyquill/delightfulkitchen/pouch/FoodPouchContainerReturnTest.java`

**Consumption pipeline:**

```java
ItemStack oneFood = sourceStack.copyWithCount(1);
FoodProperties food = oneFood.getFoodProperties(player);
if (food == null || !shouldEat(player.getFoodData(), player, food, threshold, feedWhenHurt)) {
    return FoodPouchFeedingResult.notConsumed(FoodPouchCooldowns.NO_MATCH);
}

player.getFoodData().eat(food.nutrition(), food.saturation());
for (FoodProperties.PossibleEffect effect : food.effects()) {
    if (player.getRandom().nextFloat() < effect.probability()) {
        player.addEffect(new MobEffectInstance(effect.effect()));
    }
}

ItemStack finishResult = oneFood.finishUsingItem(player.level(), player);
ItemStack container = FoodPouchContainerReturn.extractContainer(oneFood, finishResult);
FoodPouchInventory updated = inventory.extractOne(slot);
updated = FoodPouchContainerReturn.returnContainer(updated, tier, container, player);
pouchStack.set(ModDataComponents.POUCH_INVENTORY.get(), updated);
```

**Container extraction rule:**

```java
public static ItemStack extractContainer(ItemStack consumedOneFood, ItemStack finishResult) {
    if (finishResult.isEmpty()) {
        return ItemStack.EMPTY;
    }
    if (ItemStack.isSameItemSameComponents(consumedOneFood, finishResult)) {
        return ItemStack.EMPTY;
    }
    return finishResult.copy();
}
```

**Container return order:**

- [ ] Insert into pouch using normal merge-then-empty rules.
- [ ] If any remainder remains, insert into player inventory.
- [ ] If any remainder remains, drop with `player.drop(remainder, false)`.

**Curios integration:**

- [ ] Implement the Curios item tick hook supported by Curios 9.5.1+1.21.1.
- [ ] Store the current cooldown on the pouch stack as an internal int data component or as a private NBT-compatible component value named `delightful_kitchen:pouch_feed_cooldown`.
- [ ] Decrement cooldown every Curios tick.
- [ ] Run feeding only when cooldown reaches zero.
- [ ] Persist any inventory, container return, settings, or cooldown mutation back to the equipped pouch stack.

## Task 8: Add Recipes, Assets, Models, Lang, And Tooltips

**Files:**

- Create `src/main/resources/data/delightful_kitchen/recipe/food_pouch.json`
- Create `src/main/resources/data/delightful_kitchen/recipe/advanced_food_pouch.json`
- Create `src/main/resources/data/delightful_kitchen/recipe/ultimate_food_pouch.json`
- Create `src/main/resources/assets/delightful_kitchen/models/item/food_pouch.json`
- Create `src/main/resources/assets/delightful_kitchen/models/item/advanced_food_pouch.json`
- Create `src/main/resources/assets/delightful_kitchen/models/item/ultimate_food_pouch.json`
- Create `src/main/resources/assets/delightful_kitchen/textures/item/food_pouch.png`
- Create `src/main/resources/assets/delightful_kitchen/textures/item/advanced_food_pouch.png`
- Create `src/main/resources/assets/delightful_kitchen/textures/item/ultimate_food_pouch.png`
- Modify `src/main/resources/assets/delightful_kitchen/lang/en_us.json`
- Modify `src/main/java/net/inkyquill/delightfulkitchen/item/FoodPouchItem.java`

**Recipes:**

Use the shown pouch-shape pattern from the spec. This is eight leather and one string; the final docs task must correct the old "Leather x6 + String x2" text.

```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": [
    "LLL",
    "LSL",
    "LLL"
  ],
  "key": {
    "L": { "item": "minecraft:leather" },
    "S": { "item": "minecraft:string" }
  },
  "result": {
    "id": "delightful_kitchen:food_pouch",
    "count": 1
  }
}
```

```json
{
  "type": "minecraft:crafting_shapeless",
  "ingredients": [
    { "item": "delightful_kitchen:food_pouch" },
    { "item": "minecraft:diamond" },
    { "item": "minecraft:gold_ingot" },
    { "item": "minecraft:gold_ingot" },
    { "item": "minecraft:gold_ingot" },
    { "item": "minecraft:gold_ingot" }
  ],
  "result": {
    "id": "delightful_kitchen:advanced_food_pouch",
    "count": 1
  }
}
```

```json
{
  "type": "minecraft:crafting_shapeless",
  "ingredients": [
    { "item": "delightful_kitchen:advanced_food_pouch" },
    { "item": "minecraft:netherite_ingot" },
    { "item": "minecraft:ender_chest" }
  ],
  "result": {
    "id": "delightful_kitchen:ultimate_food_pouch",
    "count": 1
  }
}
```

**Assets:**

- [ ] Use `$imagegen` during implementation to create three 16x16 transparent item textures:
  - `food_pouch.png`: leather pouch, warm brown.
  - `advanced_food_pouch.png`: reinforced pouch with gold trim and small diamond clasp.
  - `ultimate_food_pouch.png`: dark reinforced pouch with netherite/ender accent.
- [ ] Use the standard generated item model parent:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "delightful_kitchen:item/food_pouch"
  }
}
```

**Tooltips:**

- [ ] Add item tooltip lines:
  - Tier name.
  - Used slots, for example `12 / 18 slots used`.
  - Food stack multiplier, for example `Food stacks: 4x`.
  - Auto-feed threshold.
  - Feed when hurt on/off.

## Task 9: Verification And Game Smoke Tests

**Files:**

- Add or extend focused tests under `src/test/java/net/inkyquill/delightfulkitchen/pouch/`
- Optionally add game test classes if the existing game test setup is active enough for Curios-equipped behavior.

**Commands:**

- [ ] Run focused pouch tests:

```bash
./gradlew test --tests '*FoodPouch*'
```

- [ ] Run all unit tests:

```bash
./gradlew test
```

- [ ] Run full build:

```bash
./gradlew build
```

- [ ] Run whitespace check:

```bash
git diff --check
```

**Manual smoke checklist:**

- [ ] Launch client with Curios present.
- [ ] Confirm the Curios pouch slot exists for the player.
- [ ] Equip each pouch tier in the Curios slot.
- [ ] Right-click a pouch in hand and confirm the correct grid size opens.
- [ ] Use the keybind to open an equipped pouch.
- [ ] Shift-click food into the pouch and confirm tier stack limits.
- [ ] Shift-click non-food into the pouch and confirm vanilla stack limits.
- [ ] Lower hunger and confirm auto-feeding consumes one item.
- [ ] Eat a stew-like food and confirm the bowl returns to the pouch first.
- [ ] Fill pouch and inventory, then confirm a returned container drops instead of disappearing.
- [ ] Confirm creative, spectator, dead, and sleeping players are not auto-fed.

## Task 10: Update Documentation And Roadmap

**Files:**

- Modify `docs/superpowers/specs/2026-05-16-food-pouch.md`
- Modify `docs/ROADMAP.md`
- Modify `README.md`
- Modify `Plans.md`
- Modify `AGENTS.md` only if new project rules are discovered during implementation.

**Steps:**

- [ ] Mark Milestone 5 as implemented in `docs/ROADMAP.md` after code and verification are complete.
- [ ] Update the food pouch spec with the final Curios artifact version and implementation notes.
- [ ] Correct the basic pouch recipe description to match the implemented recipe.
- [ ] Document that Curios is now a required dependency starting with Milestone 5.
- [ ] Add README usage notes:
  - How to craft the three tiers.
  - How to equip the pouch.
  - How to open it from hand and with the keybind.
  - How hunger threshold and hurt override work.
  - Container return priority.
- [ ] Update `Plans.md` with the milestone completion summary and any deferred follow-ups.
- [ ] Leave kitchen planner integration and pouch-to-kitchen routing explicitly deferred.

---

## Implementation Order

1. Dependency and Curios slot data.
2. Data components, item registration, and tier model.
3. Inventory serialization and insertion/extraction tests.
4. Menu and server-side pouch container behavior.
5. Screen and equipped-open payload/keybind.
6. Pure feeding decision logic and tests.
7. Curios tick, consumption, effects, and container return.
8. Recipes, assets, lang, and tooltips.
9. Verification and manual smoke pass.
10. Documentation and roadmap updates.

## Risks And Notes

- Curios API names should be verified immediately after Task 1 with `./gradlew compileJava`; do not build the rest of the milestone against guessed imports.
- Large stack counts require custom inventory persistence. Do not use vanilla `ItemContainerContents` for the pouch contents.
- `Item.finishUsingItem()` must run on a copied one-item stack. The player's hands must not be mutated by auto-feeding.
- Container return must happen after the food stack decrement is decided and before the updated pouch inventory is persisted.
- The keybind is client-only; the server must still validate that the player has an equipped pouch before opening the menu.
- Documentation and roadmap updates are intentionally last so they reflect the implemented behavior, not the intended behavior.
