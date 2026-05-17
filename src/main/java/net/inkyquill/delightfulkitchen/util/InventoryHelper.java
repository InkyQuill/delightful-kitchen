package net.inkyquill.delightfulkitchen.util;

import net.blay09.mods.cookingforblockheads.api.KitchenItemProvider;
import net.inkyquill.delightfulkitchen.block.ModBlocks;
import net.inkyquill.delightfulkitchen.block.entity.CabinetOfHoldingBlockEntity;
import net.inkyquill.delightfulkitchen.block.entity.AutoCutterBlockEntity;
import net.inkyquill.delightfulkitchen.block.entity.FuelProviderBlockEntity;
import net.inkyquill.delightfulkitchen.block.entity.GrillBlockEntity;
import net.inkyquill.delightfulkitchen.block.entity.PotStandBlockEntity;
import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryService;
import net.inkyquill.delightfulkitchen.kitchen.KitchenScanner;
import net.inkyquill.delightfulkitchen.kitchen.KitchenServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class InventoryHelper {
    private InventoryHelper() {}

    // ── Kitchen network capabilities (same ResourceLocations as CfB internal) ──

    /** Mirrors CfB's internal KitchenItemProvider BlockCapability. */
    static final BlockCapability<KitchenItemProvider, Void> KITCHEN_ITEM_PROVIDER =
            BlockCapability.createVoid(
                    ResourceLocation.fromNamespaceAndPath("cookingforblockheads", "kitchen_item_provider"),
                    KitchenItemProvider.class);

    /** CfB tag that allows non-provider blocks to propagate kitchen connectivity. */
    private static final TagKey<net.minecraft.world.level.block.Block> KITCHEN_CONNECTORS =
            TagKey.create(Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath("cookingforblockheads", "kitchen_connectors"));

    // ── Kitchen-network-wide BFS ──────────────────────────────────────────────

    /**
     * Pushes {@code stack} into the kitchen connected to {@code from},
     * using the standalone Kitchen Floor inventory service when present.
     * The legacy provider-network fallback still prefers
     * {@link CabinetOfHoldingBlockEntity} over other containers.
     * Mutates stack.count. Skips machine block entities (Grill, PotStand, etc.)
     * that should not receive output.
     *
     * @return true if stack was fully inserted
     */
    public static boolean pushToKitchen(Level level, BlockPos from, ItemStack stack) {
        if (stack.isEmpty()) return true;

        pushToKitchenFloor(level, from, stack);
        if (stack.isEmpty()) return true;

        Set<BlockPos> network = new HashSet<>();
        network.add(from);
        collectKitchenNetwork(level, from, network, true);

        List<CabinetOfHoldingBlockEntity> cabinets = new ArrayList<>();
        List<Container> others = new ArrayList<>();

        for (BlockPos pos : network) {
            if (pos.equals(from)) continue;
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof CabinetOfHoldingBlockEntity c) {
                cabinets.add(c);
            } else if (be instanceof Container c && isOutputTarget(be)) {
                others.add(c);
            }
        }

        for (CabinetOfHoldingBlockEntity c : cabinets) {
            insertIntoContainer(c, stack);
            if (stack.isEmpty()) return true;
        }
        for (Container c : others) {
            insertIntoContainer(c, stack);
            if (stack.isEmpty()) return true;
        }
        return stack.isEmpty();
    }

    private static void pushToKitchenFloor(Level level, BlockPos from, ItemStack stack) {
        Optional<BlockPos> anchor = findKitchenScanAnchor(level, from);
        if (anchor.isEmpty()) {
            return;
        }

        var scan = KitchenScanner.scan(level, anchor.orElseThrow());
        if (!scan.hasKitchenFloor()) {
            return;
        }

        var endpoints = KitchenServices.inventoryEndpoints(level, scan, from).stream()
                .filter(endpoint -> isOutputTarget(level.getBlockEntity(endpoint.pos())))
                .toList();
        if (endpoints.isEmpty()) {
            return;
        }

        var inventory = new KitchenInventoryService(endpoints);
        ItemStack remainder = inventory.insert(stack, false);
        stack.setCount(remainder.getCount());
    }

    /**
     * Finds all fuel-consuming block entities (Grill, PotStand) reachable through
     * the kitchen network and pushes one fuel item from {@code fuelStack} to each
     * that has an empty fuel slot. Mutates fuelStack.count.
     */
    public static void pushFuelToKitchen(Level level, BlockPos from, ItemStack fuelStack) {
        if (fuelStack.isEmpty()) return;

        pushFuelToKitchenFloor(level, from, fuelStack);
        if (fuelStack.isEmpty()) return;

        Set<BlockPos> network = new HashSet<>();
        network.add(from);
        collectKitchenNetwork(level, from, network, true);

        pushFuelToStations(level, from, network, fuelStack);
    }

    private static void pushFuelToKitchenFloor(Level level, BlockPos from, ItemStack fuelStack) {
        Optional<BlockPos> anchor = findKitchenScanAnchor(level, from);
        if (anchor.isEmpty()) {
            return;
        }

        var scan = KitchenScanner.scan(level, anchor.orElseThrow());
        if (!scan.hasKitchenFloor()) {
            return;
        }
        pushFuelToStations(level, from, scan.memberBlocks(), fuelStack);
    }

    private static Optional<BlockPos> findKitchenScanAnchor(Level level, BlockPos from) {
        for (int yOffset = 1; yOffset <= KitchenScanner.MAX_SCAN_HEIGHT; yOffset++) {
            BlockPos floor = from.below(yOffset);
            if (level.getBlockState(floor).is(ModBlocks.KITCHEN_FLOOR.get())) {
                return Optional.of(floor.above());
            }
        }
        return Optional.empty();
    }

    private static void pushFuelToStations(Level level, BlockPos from, Iterable<BlockPos> positions, ItemStack fuelStack) {
        for (BlockPos pos : positions) {
            if (pos.equals(from)) continue;
            if (fuelStack.isEmpty()) break;

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof GrillBlockEntity grill) {
                if (grill.getItem(GrillBlockEntity.SLOT_FUEL).isEmpty()) {
                    grill.setItem(GrillBlockEntity.SLOT_FUEL, fuelStack.split(1));
                }
            } else if (be instanceof PotStandBlockEntity pot) {
                if (pot.getItem(PotStandBlockEntity.SLOT_FUEL).isEmpty()) {
                    pot.setItem(PotStandBlockEntity.SLOT_FUEL, fuelStack.split(1));
                }
            }
        }
    }

    /**
     * BFS through the remaining CfB provider network. Any block with a
     * KitchenItemProvider capability continues the BFS; blocks tagged
     * {@code cookingforblockheads:kitchen_connectors} also continue the BFS
     * without extended up-search. Extended up-search checks two blocks upward.
     */
    private static void collectKitchenNetwork(Level level, BlockPos pos,
                                               Set<BlockPos> visited, boolean extendedUp) {
        for (Direction dir : Direction.values()) {
            int steps = (extendedUp && dir == Direction.UP) ? 2 : 1;
            for (int n = 1; n <= steps; n++) {
                BlockPos neighbor = pos.relative(dir, n);
                if (visited.contains(neighbor)) continue;
                visited.add(neighbor);

                BlockState state = level.getBlockState(neighbor);
                BlockEntity be = level.getBlockEntity(neighbor);

                boolean hasProvider = be != null && level.getCapability(KITCHEN_ITEM_PROVIDER, neighbor, state, be, null) != null;
                boolean isConnector = state.is(KITCHEN_CONNECTORS);

                if (hasProvider) {
                    collectKitchenNetwork(level, neighbor, visited, true);
                } else if (isConnector) {
                    collectKitchenNetwork(level, neighbor, visited, false);
                }
            }
        }
    }

    /** Returns true if the block entity is a valid output destination (not a machine). */
    private static boolean isOutputTarget(BlockEntity be) {
        return !(be instanceof GrillBlockEntity)
                && !(be instanceof PotStandBlockEntity)
                && !(be instanceof AutoCutterBlockEntity)
                && !(be instanceof FuelProviderBlockEntity);
    }

    // ── Adjacent-only fallback ────────────────────────────────────────────────

    /**
     * Tries to push {@code stack} into an adjacent inventory, preferring
     * CabinetOfHoldingBlockEntity over generic containers. Mutates stack.count.
     * @return true if stack was fully inserted
     */
    public static boolean pushToNeighbor(Level level, BlockPos from, ItemStack stack) {
        // First pass: Cabinet of Holding
        for (Direction dir : Direction.values()) {
            BlockEntity be = level.getBlockEntity(from.relative(dir));
            if (be instanceof CabinetOfHoldingBlockEntity cabinet) {
                insertIntoContainer(cabinet, stack);
                if (stack.isEmpty()) return true;
            }
        }
        // Second pass: any other Container
        for (Direction dir : Direction.values()) {
            BlockEntity be = level.getBlockEntity(from.relative(dir));
            if (be instanceof Container container && !(be instanceof CabinetOfHoldingBlockEntity)) {
                insertIntoContainer(container, stack);
                if (stack.isEmpty()) return true;
            }
        }
        return stack.isEmpty();
    }

    /** Inserts as much of stack as possible into container, respecting canPlaceItem. Mutates stack.count. */
    public static void insertIntoContainer(Container container, ItemStack stack) {
        int size = container.getContainerSize();
        // First pass: top off existing stacks
        for (int i = 0; i < size && !stack.isEmpty(); i++) {
            if (!container.canPlaceItem(i, stack)) continue;
            ItemStack existing = container.getItem(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                int transfer = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                if (transfer > 0) {
                    existing.grow(transfer);
                    stack.shrink(transfer);
                    container.setChanged();
                }
            }
        }
        // Second pass: fill empty slots
        for (int i = 0; i < size && !stack.isEmpty(); i++) {
            if (!container.canPlaceItem(i, stack)) continue;
            if (container.getItem(i).isEmpty()) {
                container.setItem(i, stack.split(stack.getCount()));
                container.setChanged();
                return;
            }
        }
    }
}
