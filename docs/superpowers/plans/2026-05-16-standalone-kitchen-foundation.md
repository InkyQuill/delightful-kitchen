# Standalone Kitchen Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the standalone kitchen foundation: Kitchen Floor registration, room scanning, normalized kitchen inventory routing, Chef's Counter shell, and Cabinet of Holding merged-inventory behavior.

**Architecture:** Keep the first implementation focused on standalone infrastructure, not the full recipe planner. Kitchen Floor defines a connected footprint, KitchenScanner builds a room snapshot, KitchenInventoryService wraps discovered containers, Chef's Counter opens a status shell, and Cabinet of Holding exposes routed storage over the discovered kitchen inventory.

**Tech Stack:** Minecraft 1.21.1, NeoForge 21.1.221, Java 21, Gradle, existing DeferredRegister patterns, Farmer's Delight as required dependency, Cooking for Blockheads code treated as legacy/prototype.

---

## Scope And Sequencing

This plan implements the foundation slice of the approved spec at `docs/superpowers/specs/2026-05-16-standalone-kitchen-design.md`.

Included:

- Kitchen Floor block and Mop item.
- Chef's Counter block entity and menu shell.
- KitchenScanner for connected floor footprint plus vertical scan.
- KitchenInventoryEndpoint and KitchenInventoryService for merged inventory operations.
- Cabinet of Holding reworked from local 54-slot chest to merged kitchen terminal.
- Metadata text changed away from Cooking for Blockheads replacement language.

Not included:

- Full Chef's Counter recipe book UI.
- RecipeAdapter graph planning.
- Station queue rewrite.
- Curios pouch.
- Persistent Kitchen Floor camouflage rendering. This plan registers the interaction points and uses base Kitchen Floor art.

Use `minecraft-modding` for implementation. Confirm NeoForge and MC version from `gradle.properties` before code changes.

## Planned File Structure

- Create `src/main/java/net/inkyquill/cfbdelight/block/KitchenFloorBlock.java`: floor block with top-face camouflage state hooks.
- Create `src/main/java/net/inkyquill/cfbdelight/block/ChefCounterBlock.java`: block entity backed controller shell.
- Create `src/main/java/net/inkyquill/cfbdelight/item/MopItem.java`: clears Kitchen Floor camouflage on use.
- Create `src/main/java/net/inkyquill/cfbdelight/block/entity/ChefCounterBlockEntity.java`: opens Chef's Counter shell and reports scanner status.
- Modify `src/main/java/net/inkyquill/cfbdelight/block/ModBlocks.java`: register `kitchen_floor` and `chef_counter`.
- Modify `src/main/java/net/inkyquill/cfbdelight/item/ModItems.java`: register block items and `mop`.
- Modify `src/main/java/net/inkyquill/cfbdelight/block/entity/ModBlockEntities.java`: register Chef's Counter block entity.
- Create `src/main/java/net/inkyquill/cfbdelight/kitchen/KitchenScanResult.java`: immutable scan result.
- Create `src/main/java/net/inkyquill/cfbdelight/kitchen/KitchenScanner.java`: connected floor and vertical member scanning.
- Create `src/main/java/net/inkyquill/cfbdelight/kitchen/KitchenInventoryEndpoint.java`: wrapper around `Container`.
- Create `src/main/java/net/inkyquill/cfbdelight/kitchen/KitchenInventoryService.java`: count, extract, insert, route.
- Create `src/main/java/net/inkyquill/cfbdelight/kitchen/KitchenInventoryView.java`: `Container` view used by Cabinet menu.
- Modify `src/main/java/net/inkyquill/cfbdelight/block/entity/CabinetOfHoldingBlockEntity.java`: open merged service, no longer model itself as only a 54-slot chest.
- Modify `src/main/java/net/inkyquill/cfbdelight/menu/CabinetOfHoldingMenu.java`: accept `KitchenInventoryView` with 54 visible slots for first version.
- Create `src/main/java/net/inkyquill/cfbdelight/menu/ChefCounterMenu.java`: status shell menu.
- Modify `src/main/java/net/inkyquill/cfbdelight/menu/ModMenuTypes.java`: register Chef's Counter menu.
- Create `src/main/java/net/inkyquill/cfbdelight/client/screen/ChefCounterScreen.java`: minimal screen showing kitchen status.
- Modify `src/main/java/net/inkyquill/cfbdelight/client/CfBDelightClient.java`: register Chef's Counter screen.
- Modify resource JSON under `src/main/resources/assets/cfb_delight/` and `src/main/resources/data/cfb_delight/`: models, blockstates, item models, loot tables, lang entries, and recipes for new block/items.
- Modify `src/main/resources/META-INF/neoforge.mods.toml`: keep the required CfB dependency until a dedicated dependency-removal plan retires Java imports. In this foundation plan, update description only.

## Task 1: Register Kitchen Floor And Mop

**Files:**
- Create: `src/main/java/net/inkyquill/cfbdelight/block/KitchenFloorBlock.java`
- Create: `src/main/java/net/inkyquill/cfbdelight/item/MopItem.java`
- Modify: `src/main/java/net/inkyquill/cfbdelight/block/ModBlocks.java`
- Modify: `src/main/java/net/inkyquill/cfbdelight/item/ModItems.java`
- Modify: `src/main/resources/assets/cfb_delight/lang/en_us.json`
- Create: `src/main/resources/assets/cfb_delight/blockstates/kitchen_floor.json`
- Create: `src/main/resources/assets/cfb_delight/models/block/kitchen_floor.json`
- Create: `src/main/resources/assets/cfb_delight/models/item/kitchen_floor.json`
- Create: `src/main/resources/assets/cfb_delight/models/item/mop.json`
- Create: `src/main/resources/assets/cfb_delight/textures/block/kitchen_floor_top.png`
- Create: `src/main/resources/assets/cfb_delight/textures/item/mop.png`
- Create: `src/main/resources/data/cfb_delight/loot_table/blocks/kitchen_floor.json`

- [ ] **Step 1: Confirm platform**

Run:

```bash
rg -n "minecraft_version=|neo_version=|net.neoforged.gradle" gradle.properties build.gradle
```

Expected: output includes `minecraft_version=1.21.1`, `neo_version=21.1.221`, and `net.neoforged.gradle.userdev`.

- [ ] **Step 2: Add deterministic block and item classes**

Create `KitchenFloorBlock.java`:

```java
package net.inkyquill.cfbdelight.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class KitchenFloorBlock extends Block {
    public static final MapCodec<KitchenFloorBlock> CODEC = simpleCodec(KitchenFloorBlock::new);

    public KitchenFloorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, net.minecraft.world.InteractionHand hand,
                                          BlockHitResult hit) {
        if (hit.getDirection() != Direction.UP || !(stack.getItem() instanceof BlockItem)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.cfb_delight.kitchen_floor.camouflage_not_ready"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
```

Create `MopItem.java`:

```java
package net.inkyquill.cfbdelight.item;

import net.inkyquill.cfbdelight.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class MopItem extends Item {
    public MopItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos).is(ModBlocks.KITCHEN_FLOOR.get())) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && context.getPlayer() != null) {
            context.getPlayer().displayClientMessage(Component.translatable(
                    "message.cfb_delight.kitchen_floor.reset"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
```

- [ ] **Step 3: Register block and item**

In `ModBlocks.java`, add:

```java
public static final DeferredBlock<KitchenFloorBlock> KITCHEN_FLOOR =
        BLOCKS.register("kitchen_floor",
                () -> new KitchenFloorBlock(BlockBehaviour.Properties.of()
                        .mapColor(MapColor.WOOD)
                        .strength(1.5f, 3.0f)
                        .sound(SoundType.WOOD)));
```

In `ModItems.java`, add:

```java
public static final DeferredItem<BlockItem> KITCHEN_FLOOR =
        ITEMS.registerSimpleBlockItem(ModBlocks.KITCHEN_FLOOR);

public static final DeferredItem<MopItem> MOP =
        ITEMS.register("mop", () -> new MopItem(new net.minecraft.world.item.Item.Properties().stacksTo(1)));
```

Add both to the creative tab display list:

```java
output.accept(KITCHEN_FLOOR.get());
output.accept(MOP.get());
```

- [ ] **Step 4: Add JSON assets and lang**

Add language keys:

```json
"block.cfb_delight.kitchen_floor": "Kitchen Floor",
"item.cfb_delight.mop": "Mop",
"message.cfb_delight.kitchen_floor.camouflage_not_ready": "Kitchen floor appearance copying is not active in this build.",
"message.cfb_delight.kitchen_floor.reset": "Kitchen floor reset."
```

Create `blockstates/kitchen_floor.json`:

```json
{
  "variants": {
    "": {
      "model": "cfb_delight:block/kitchen_floor"
    }
  }
}
```

Create `models/block/kitchen_floor.json`:

```json
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "cfb_delight:block/kitchen_floor_top"
  }
}
```

Create `models/item/kitchen_floor.json`:

```json
{
  "parent": "cfb_delight:block/kitchen_floor"
}
```

Create `models/item/mop.json`:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "cfb_delight:item/mop"
  }
}
```

Create `loot_table/blocks/kitchen_floor.json`:

```json
{
  "type": "minecraft:block",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "cfb_delight:kitchen_floor"
        }
      ],
      "conditions": [
        {
          "condition": "minecraft:survives_explosion"
        }
      ]
    }
  ]
}
```

Use `imagegen` during execution for the new Kitchen Floor and Mop textures.

Kitchen Floor top texture prompt:

```text
Use case: stylized-concept
Asset type: Minecraft 16x16 block texture
Primary request: top-down wooden parquet floor tile for a cozy Minecraft kitchen floor
Style/medium: pixel art, Minecraft mod texture, 16x16, crisp nearest-neighbor pixels
Composition/framing: seamless square tile, top face only
Color palette: warm medium wood, subtle plank variation
Constraints: no text, no shadow, no perspective, no border, no objects
```

Mop item texture prompt:

```text
Use case: stylized-concept
Asset type: Minecraft 16x16 item texture
Primary request: a simple wooden-handled mop item for cleaning a kitchen floor
Style/medium: pixel art, Minecraft item texture, 16x16, transparent background
Composition/framing: diagonal item sprite, readable silhouette
Color palette: light wood handle, off-white mop head
Constraints: no text, no watermark, no background after processing
```

- [ ] **Step 5: Verify compile reaches asset loading**

Run:

```bash
./gradlew build
```

Expected: build passes or fails only on pre-existing CfB jar dependency availability. If it fails on missing new imports/assets, fix before continuing.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/net/inkyquill/cfbdelight/block/KitchenFloorBlock.java src/main/java/net/inkyquill/cfbdelight/item/MopItem.java src/main/java/net/inkyquill/cfbdelight/block/ModBlocks.java src/main/java/net/inkyquill/cfbdelight/item/ModItems.java src/main/resources/assets/cfb_delight src/main/resources/data/cfb_delight/loot_table/blocks/kitchen_floor.json
git commit -m "feat: add kitchen floor and mop"
```

## Task 2: Add Chef's Counter Shell

**Files:**
- Create: `src/main/java/net/inkyquill/cfbdelight/block/ChefCounterBlock.java`
- Create: `src/main/java/net/inkyquill/cfbdelight/block/entity/ChefCounterBlockEntity.java`
- Create: `src/main/java/net/inkyquill/cfbdelight/menu/ChefCounterMenu.java`
- Create: `src/main/java/net/inkyquill/cfbdelight/client/screen/ChefCounterScreen.java`
- Modify: `src/main/java/net/inkyquill/cfbdelight/block/ModBlocks.java`
- Modify: `src/main/java/net/inkyquill/cfbdelight/item/ModItems.java`
- Modify: `src/main/java/net/inkyquill/cfbdelight/block/entity/ModBlockEntities.java`
- Modify: `src/main/java/net/inkyquill/cfbdelight/menu/ModMenuTypes.java`
- Modify: `src/main/java/net/inkyquill/cfbdelight/client/CfBDelightClient.java`
- Modify: `src/main/resources/assets/cfb_delight/lang/en_us.json`
- Create: blockstate/model/item model/loot table for `chef_counter`

- [ ] **Step 1: Create block and block entity**

Create `ChefCounterBlock.java` as a `BaseEntityBlock` matching the existing cabinet block style: horizontal facing, `newBlockEntity`, `useWithoutItem` opens its menu, `RenderShape.MODEL`.

Use this exact interaction body:

```java
@Override
protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ChefCounterBlockEntity be) {
        player.openMenu(be);
    }
    return InteractionResult.sidedSuccess(level.isClientSide());
}
```

Create `ChefCounterBlockEntity.java`:

```java
package net.inkyquill.cfbdelight.block.entity;

import net.inkyquill.cfbdelight.menu.ChefCounterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ChefCounterBlockEntity extends BlockEntity implements MenuProvider {
    public ChefCounterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHEF_COUNTER.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.cfb_delight.chef_counter");
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new ChefCounterMenu(id, playerInventory, ContainerLevelAccess.create(level, worldPosition));
    }
}
```

- [ ] **Step 2: Create menu and screen shell**

Create `ChefCounterMenu.java` with no custom slots beyond player inventory:

```java
package net.inkyquill.cfbdelight.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ChefCounterMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;

    public ChefCounterMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL);
    }

    public ChefCounterMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
        super(ModMenuTypes.CHEF_COUNTER.get(), containerId);
        this.access = access;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0, true);
    }
}
```

Create `ChefCounterScreen.java` as a minimal `AbstractContainerScreen<ChefCounterMenu>` that draws the title and `container.cfb_delight.chef_counter.status_unlinked`.

- [ ] **Step 3: Register Chef's Counter**

Add `CHEF_COUNTER` block, block item, block entity type, menu type, creative tab entry, and client screen registration following existing project patterns.

Menu registration snippet:

```java
public static final DeferredHolder<MenuType<?>, MenuType<ChefCounterMenu>> CHEF_COUNTER =
        MENUS.register("chef_counter", () -> IMenuTypeExtension.create(ChefCounterMenu::new));
```

Client registration snippet:

```java
event.register(ModMenuTypes.CHEF_COUNTER.get(), ChefCounterScreen::new);
```

- [ ] **Step 4: Add JSON resources**

Use the existing `cabinet_of_holding` resource files as the template. Create `chef_counter` blockstate, block model, item model, loot table, and lang keys:

```json
"block.cfb_delight.chef_counter": "Chef's Counter",
"container.cfb_delight.chef_counter": "Chef's Counter",
"container.cfb_delight.chef_counter.status_unlinked": "Kitchen scanner not connected."
```

- [ ] **Step 5: Verify and commit**

Run:

```bash
./gradlew build
```

Expected: compile passes or exposes exact missing registration/screen import errors to fix.

Commit:

```bash
git add src/main/java/net/inkyquill/cfbdelight src/main/resources/assets/cfb_delight src/main/resources/data/cfb_delight/loot_table/blocks/chef_counter.json
git commit -m "feat: add chef counter shell"
```

## Task 3: Implement Kitchen Scanner

**Files:**
- Create: `src/main/java/net/inkyquill/cfbdelight/kitchen/KitchenScanResult.java`
- Create: `src/main/java/net/inkyquill/cfbdelight/kitchen/KitchenScanner.java`
- Modify: `src/main/java/net/inkyquill/cfbdelight/menu/ChefCounterMenu.java`
- Modify: `src/main/java/net/inkyquill/cfbdelight/client/screen/ChefCounterScreen.java`

- [ ] **Step 1: Add immutable scan result**

Create `KitchenScanResult.java`:

```java
package net.inkyquill.cfbdelight.kitchen;

import net.minecraft.core.BlockPos;
import java.util.List;
import java.util.Set;

public record KitchenScanResult(
        boolean hasKitchenFloor,
        Set<BlockPos> floorTiles,
        List<BlockPos> memberBlocks,
        List<BlockPos> counters
) {
    public int floorCount() {
        return floorTiles.size();
    }

    public int memberCount() {
        return memberBlocks.size();
    }

    public boolean hasMultipleCounters() {
        return counters.size() > 1;
    }
}
```

- [ ] **Step 2: Add scanner**

Create `KitchenScanner.java`:

```java
package net.inkyquill.cfbdelight.kitchen;

import net.inkyquill.cfbdelight.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class KitchenScanner {
    public static final int MAX_SCAN_HEIGHT = 6;
    public static final int MAX_FLOOR_TILES = 512;

    private KitchenScanner() {
    }

    public static KitchenScanResult scan(Level level, BlockPos counterPos) {
        BlockPos startFloor = counterPos.below();
        if (!level.getBlockState(startFloor).is(ModBlocks.KITCHEN_FLOOR.get())) {
            return new KitchenScanResult(false, Set.of(), List.of(), List.of(counterPos));
        }

        Set<BlockPos> floors = findConnectedFloors(level, startFloor);
        List<BlockPos> members = new ArrayList<>();
        List<BlockPos> counters = new ArrayList<>();

        for (BlockPos floor : floors) {
            for (int y = 1; y <= MAX_SCAN_HEIGHT; y++) {
                BlockPos pos = floor.above(y);
                if (pos.equals(counterPos)) {
                    counters.add(pos);
                    continue;
                }
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof Container || level.getBlockState(pos).is(ModBlocks.CHEF_COUNTER.get())) {
                    members.add(pos);
                    if (level.getBlockState(pos).is(ModBlocks.CHEF_COUNTER.get())) {
                        counters.add(pos);
                    }
                }
            }
        }

        if (!counters.contains(counterPos)) {
            counters.add(counterPos);
        }
        return new KitchenScanResult(true, floors, List.copyOf(members), List.copyOf(counters));
    }

    private static Set<BlockPos> findConnectedFloors(Level level, BlockPos start) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty() && visited.size() < MAX_FLOOR_TILES) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos.immutable())) {
                continue;
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = pos.relative(direction);
                if (!visited.contains(next) && level.getBlockState(next).is(ModBlocks.KITCHEN_FLOOR.get())) {
                    queue.add(next);
                }
            }
        }
        return Set.copyOf(visited);
    }
}
```

- [ ] **Step 3: Surface scanner status in Chef's Counter**

Add fields to `ChefCounterMenu`:

```java
private final net.minecraft.world.inventory.ContainerData data;
```

Build a `SimpleContainerData(3)` where index `0` is floor count, `1` is member count, and `2` is `1` if multiple counters exist. On the server constructor, call `KitchenScanner.scan` through `ContainerLevelAccess.evaluate` and fill the data.

In `ChefCounterScreen`, render:

```java
graphics.drawString(font, Component.translatable("gui.cfb_delight.chef_counter.floor_tiles", menu.getFloorCount()), leftPos + 8, topPos + 20, 0x404040, false);
graphics.drawString(font, Component.translatable("gui.cfb_delight.chef_counter.members", menu.getMemberCount()), leftPos + 8, topPos + 32, 0x404040, false);
```

Add lang:

```json
"gui.cfb_delight.chef_counter.floor_tiles": "Floor tiles: %s",
"gui.cfb_delight.chef_counter.members": "Kitchen blocks: %s",
"gui.cfb_delight.chef_counter.multiple_counters": "Multiple Chef's Counters share this floor."
```

- [ ] **Step 4: Verify and commit**

Run:

```bash
./gradlew build
```

Commit:

```bash
git add src/main/java/net/inkyquill/cfbdelight/kitchen src/main/java/net/inkyquill/cfbdelight/menu/ChefCounterMenu.java src/main/java/net/inkyquill/cfbdelight/client/screen/ChefCounterScreen.java src/main/resources/assets/cfb_delight/lang/en_us.json
git commit -m "feat: add standalone kitchen scanner"
```

## Task 4: Implement Kitchen Inventory Service

**Files:**
- Create: `src/main/java/net/inkyquill/cfbdelight/kitchen/KitchenInventoryEndpoint.java`
- Create: `src/main/java/net/inkyquill/cfbdelight/kitchen/KitchenInventoryService.java`
- Create: `src/main/java/net/inkyquill/cfbdelight/kitchen/KitchenInventoryView.java`

- [ ] **Step 1: Add endpoint wrapper**

Create `KitchenInventoryEndpoint.java`:

```java
package net.inkyquill.cfbdelight.kitchen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public record KitchenInventoryEndpoint(BlockPos pos, Container container) {
    public int size() {
        return container.getContainerSize();
    }

    public ItemStack get(int slot) {
        return container.getItem(slot);
    }

    public boolean mayPlace(int slot, ItemStack stack) {
        return container.canPlaceItem(slot, stack);
    }
}
```

- [ ] **Step 2: Add inventory service**

Create `KitchenInventoryService.java` with these public methods:

```java
public int count(ItemStack prototype)
public ItemStack extract(ItemStack prototype, int amount, boolean simulate)
public ItemStack insert(ItemStack stack, boolean simulate)
public NonNullList<ItemStack> visibleStacks(int limit)
```

Insertion order must be:

1. endpoints that already contain a matching stack and can grow it
2. compatible empty slots

Extraction must pull matching stacks until the requested count is satisfied or no more matching stacks exist. Use `ItemStack.isSameItemSameComponents`.

- [ ] **Step 3: Add merged inventory view**

Create `KitchenInventoryView.java` implementing `Container`. It should expose a fixed 54-slot visible snapshot from `KitchenInventoryService.visibleStacks(54)`.

Implement `removeItem` by extracting from the service using the visible slot stack as prototype. Implement `setItem` by inserting through the service. `stillValid` returns true while the player is within 64 blocks of the terminal position.

- [ ] **Step 4: Verify and commit**

Run:

```bash
./gradlew build
```

Commit:

```bash
git add src/main/java/net/inkyquill/cfbdelight/kitchen
git commit -m "feat: add kitchen inventory service"
```

## Task 5: Convert Cabinet Of Holding To Merged Terminal

**Files:**
- Modify: `src/main/java/net/inkyquill/cfbdelight/block/entity/CabinetOfHoldingBlockEntity.java`
- Modify: `src/main/java/net/inkyquill/cfbdelight/menu/CabinetOfHoldingMenu.java`
- Modify: `src/main/resources/assets/cfb_delight/lang/en_us.json`

- [ ] **Step 1: Build service on open**

In `CabinetOfHoldingBlockEntity.createMenu`, scan from `worldPosition`, build endpoints for member blocks that are `Container`, skip the cabinet block entity itself unless no other endpoints exist, then create `KitchenInventoryView`.

Use:

```java
var scan = net.inkyquill.cfbdelight.kitchen.KitchenScanner.scan(level, worldPosition);
var endpoints = new java.util.ArrayList<net.inkyquill.cfbdelight.kitchen.KitchenInventoryEndpoint>();
for (BlockPos member : scan.memberBlocks()) {
    if (member.equals(worldPosition)) {
        continue;
    }
    if (level.getBlockEntity(member) instanceof Container container) {
        endpoints.add(new net.inkyquill.cfbdelight.kitchen.KitchenInventoryEndpoint(member, container));
    }
}
var service = new net.inkyquill.cfbdelight.kitchen.KitchenInventoryService(endpoints);
var view = new net.inkyquill.cfbdelight.kitchen.KitchenInventoryView(service, worldPosition);
return new CabinetOfHoldingMenu(id, playerInventory, view, ContainerLevelAccess.create(level, worldPosition));
```

- [ ] **Step 2: Keep local fallback storage**

Keep the existing 54-slot local `items` container for now so old worlds do not lose contents. If no floor exists below the cabinet or no endpoints are found, the menu should use `this` as the container.

- [ ] **Step 3: Update shift-click routing**

In `CabinetOfHoldingMenu.quickMoveStack`, keep the current slot ranges, but rely on `KitchenInventoryView.setItem` for player-to-kitchen insertion. Confirm that shift-click from player inventory into the cabinet routes through the view.

- [ ] **Step 4: Verify in client**

Run:

```bash
./gradlew runClient
```

Manual check:

- Place connected Kitchen Floor.
- Place a Cabinet of Holding above it.
- Place a chest above another floor tile.
- Put apples in the chest.
- Open Cabinet of Holding.
- Confirm apples appear in the merged view.
- Take apples from the Cabinet view and confirm they leave the chest.
- Shift-click another apple stack into Cabinet and confirm it merges into the chest stack.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/inkyquill/cfbdelight/block/entity/CabinetOfHoldingBlockEntity.java src/main/java/net/inkyquill/cfbdelight/menu/CabinetOfHoldingMenu.java src/main/resources/assets/cfb_delight/lang/en_us.json
git commit -m "feat: make cabinet a merged kitchen terminal"
```

## Task 6: Update Mod Metadata And Legacy Notes

**Files:**
- Modify: `src/main/resources/META-INF/neoforge.mods.toml`
- Modify: `README.md`
- Modify: `Plans.md`

- [ ] **Step 1: Update description**

Change `neoforge.mods.toml` description to:

```toml
description='''
A standalone Farmer's Delight focused kitchen mod. Build a room-based kitchen,
link inventories through Kitchen Floor, browse cookable foods at the Chef's
Counter, and automate Farmer's Delight preparation through kitchen stations.
'''
```

Do not remove the `cookingforblockheads` dependency in this plan because current Java files still import CfB APIs. Dependency removal belongs in a dedicated "retire CfB integration" plan.

- [ ] **Step 2: Update README**

Replace the template README with a short project overview:

````markdown
# CfB-Delight

CfB-Delight is a NeoForge 1.21.1 Minecraft mod for building a standalone Farmer's Delight kitchen.

The current implementation is transitioning away from Cooking for Blockheads compatibility toward a room-based kitchen defined by Kitchen Floor blocks, a Chef's Counter recipe planner, and a Cabinet of Holding merged inventory terminal.

## Development

- Minecraft: 1.21.1
- Loader: NeoForge
- Java: 21

Build with:

```bash
./gradlew build
```
````

- [ ] **Step 3: Add implementation status to Plans.md**

Add a top section in `Plans.md`:

```markdown
## Current Direction

The approved standalone design lives in `docs/superpowers/specs/2026-05-16-standalone-kitchen-design.md`.

Implementation is split into milestones. The first milestone builds Kitchen Floor scanning, Chef's Counter shell, and Cabinet of Holding as a merged kitchen inventory terminal. Full recipe planning and the Curios pouch are separate future milestones.
```

- [ ] **Step 4: Verify and commit**

Run:

```bash
./gradlew build
```

Commit:

```bash
git add src/main/resources/META-INF/neoforge.mods.toml README.md Plans.md
git commit -m "docs: describe standalone kitchen direction"
```

## Final Verification

- [ ] **Step 1: Run build**

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run client smoke test**

```bash
./gradlew runClient
```

Manual expected behavior:

- Kitchen Floor, Mop, and Chef's Counter appear in the creative tab.
- Chef's Counter opens a status screen.
- Chef's Counter reports nonzero floor tile count when placed above Kitchen Floor.
- Cabinet of Holding can show and route items from another inventory in the same Kitchen Floor footprint.

- [ ] **Step 3: Record follow-up plan list**

Add issues or follow-up plan entries for:

- Kitchen Floor persistent camouflage data/rendering.
- Full recipe adapter and planner system.
- Station queue rewrite against KitchenInventoryService.
- Removal of Cooking for Blockheads dependency.
- Curios pouch milestone.
