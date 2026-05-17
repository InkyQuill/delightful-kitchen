package net.inkyquill.delightfulkitchen.kitchen;

import net.inkyquill.delightfulkitchen.block.ModBlocks;
import net.inkyquill.delightfulkitchen.block.entity.AutoCutterBlockEntity;
import net.inkyquill.delightfulkitchen.block.entity.GrillBlockEntity;
import net.inkyquill.delightfulkitchen.block.entity.PotStandBlockEntity;
import net.inkyquill.delightfulkitchen.kitchen.station.KitchenStation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class KitchenScanner {
    public static final int MAX_SCAN_HEIGHT = 6;
    public static final int MAX_FLOOR_TILES = 512;

    private KitchenScanner() {
    }

    public static KitchenScanResult scan(Level level, BlockPos counterPos) {
        BlockPos counter = counterPos.immutable();
        BlockPos startFloor = counter.below();
        if (!level.getBlockState(startFloor).is(ModBlocks.KITCHEN_FLOOR.get())) {
            return new KitchenScanResult(false, Set.of(), List.of(), List.of(counter), List.of(), List.of());
        }

        Set<BlockPos> floors = findConnectedFloors(level, startFloor);
        List<BlockPos> members = new ArrayList<>();
        Set<BlockPos> counters = new LinkedHashSet<>();
        List<BlockPos> waterProviders = new ArrayList<>();
        List<BlockPos> freezers = new ArrayList<>();

        for (BlockPos floor : floors) {
            for (int yOffset = 1; yOffset <= MAX_SCAN_HEIGHT; yOffset++) {
                BlockPos pos = floor.above(yOffset).immutable();
                if (level.getBlockState(pos).is(ModBlocks.WATER_PROVIDER.get())) {
                    waterProviders.add(pos);
                    continue;
                }
                if (level.getBlockState(pos).is(ModBlocks.FREEZER.get())) {
                    freezers.add(pos);
                    continue;
                }
                if (!isCompatibleMember(level, pos)) {
                    continue;
                }

                members.add(pos);
                if (level.getBlockState(pos).is(ModBlocks.CHEF_COUNTER.get())) {
                    counters.add(pos);
                }
            }
        }

        counters.add(counter);
        return new KitchenScanResult(true, floors, List.copyOf(members), List.copyOf(counters),
                List.copyOf(waterProviders), List.copyOf(freezers));
    }

    private static boolean isCompatibleMember(Level level, BlockPos pos) {
        if (level.getBlockState(pos).is(ModBlocks.WATER_PROVIDER.get())
                || level.getBlockState(pos).is(ModBlocks.FREEZER.get())) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof Container
                || blockEntity instanceof KitchenStation
                || level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null
                || level.getBlockState(pos).is(ModBlocks.CHEF_COUNTER.get());
    }

    public static boolean isInventoryProvider(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (isKitchenStation(blockEntity)) {
            return false;
        }
        return blockEntity instanceof Container
                || level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null;
    }

    public static boolean isKitchenStation(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof KitchenStation
                || blockEntity instanceof AutoCutterBlockEntity
                || blockEntity instanceof GrillBlockEntity
                || blockEntity instanceof PotStandBlockEntity;
    }

    private static Set<BlockPos> findConnectedFloors(Level level, BlockPos start) {
        Set<BlockPos> visited = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());

        while (!queue.isEmpty() && visited.size() < MAX_FLOOR_TILES) {
            BlockPos pos = queue.removeFirst().immutable();
            if (!visited.add(pos)) {
                continue;
            }

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = pos.relative(direction).immutable();
                if (!visited.contains(next) && level.getBlockState(next).is(ModBlocks.KITCHEN_FLOOR.get())) {
                    queue.add(next);
                }
            }
        }

        return Set.copyOf(visited);
    }
}
