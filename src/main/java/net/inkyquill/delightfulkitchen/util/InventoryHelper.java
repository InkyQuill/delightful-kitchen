package net.inkyquill.delightfulkitchen.util;

import net.blay09.mods.cookingforblockheads.api.KitchenItemProcessor;
import net.blay09.mods.cookingforblockheads.api.KitchenItemProvider;
import net.inkyquill.delightfulkitchen.block.entity.CabinetOfHoldingBlockEntity;
import net.inkyquill.delightfulkitchen.block.entity.AutoCutterBlockEntity;
import net.inkyquill.delightfulkitchen.block.entity.FuelProviderBlockEntity;
import net.inkyquill.delightfulkitchen.block.entity.GrillBlockEntity;
import net.inkyquill.delightfulkitchen.block.entity.PotStandBlockEntity;
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
import java.util.Set;

public final class InventoryHelper {
    private InventoryHelper() {}

    // ── Kitchen network capabilities (same ResourceLocations as CfB internal) ──

    /** Mirrors CfB's internal KitchenItemProvider BlockCapability. */
    static final BlockCapability<KitchenItemProvider, Void> KITCHEN_ITEM_PROVIDER =
            BlockCapability.createVoid(
                    ResourceLocation.fromNamespaceAndPath("cookingforblockheads", "kitchen_item_provider"),
                    KitchenItemProvider.class);

    /** Same as FarmersDelightCompat.KITCHEN_ITEM_PROCESSOR — referenced here to avoid circular dep. */
    static final BlockCapability<KitchenItemProcessor, Void> KITCHEN_ITEM_PROCESSOR =
            BlockCapability.createVoid(
                    ResourceLocation.fromNamespaceAndPath("cookingforblockheads", "kitchen_item_processor"),
                    KitchenItemProcessor.class);

    /** CfB tag that allows non-provider/non-processor blocks to propagate kitchen connectivity. */
    private static final TagKey<net.minecraft.world.level.block.Block> KITCHEN_CONNECTORS =
            TagKey.create(Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath("cookingforblockheads", "kitchen_connectors"));

    // ── Kitchen-network-wide BFS ──────────────────────────────────────────────

    /**
     * Pushes {@code stack} into the CfB kitchen network connected to {@code from},
     * preferring {@link CabinetOfHoldingBlockEntity} over other containers.
     * Mutates stack.count. Skips machine block entities (Grill, PotStand, etc.)
     * that should not receive output.
     *
     * @return true if stack was fully inserted
     */
    public static boolean pushToKitchen(Level level, BlockPos from, ItemStack stack) {
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

    /**
     * Finds all fuel-consuming block entities (Grill, PotStand) reachable through
     * the kitchen network and pushes one fuel item from {@code fuelStack} to each
     * that has an empty fuel slot. Mutates fuelStack.count.
     */
    public static void pushFuelToKitchen(Level level, BlockPos from, ItemStack fuelStack) {
        if (fuelStack.isEmpty()) return;

        Set<BlockPos> network = new HashSet<>();
        network.add(from);
        collectKitchenNetwork(level, from, network, true);

        for (BlockPos pos : network) {
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
     * BFS through the CfB kitchen network using the same traversal rules as
     * {@code KitchenImpl.findNeighbourCraftingBlocks}: any block with a
     * KitchenItemProvider or KitchenItemProcessor capability continues the BFS;
     * blocks tagged {@code cookingforblockheads:kitchen_connectors} also continue
     * the BFS (but without extended up-search). Extended up-search checks two
     * blocks upward.
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

                boolean hasProvider  = be != null && level.getCapability(KITCHEN_ITEM_PROVIDER, neighbor, state, be, null) != null;
                boolean hasProcessor = be != null && level.getCapability(KITCHEN_ITEM_PROCESSOR, neighbor, state, be, null) != null;
                boolean isConnector  = state.is(KITCHEN_CONNECTORS);

                if (hasProvider || hasProcessor) {
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
