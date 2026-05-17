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
import net.neoforged.neoforge.items.IItemHandler;
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
        return scan(new LevelScanView(level), counterPos);
    }

    static KitchenScanResult scan(ScanView view, BlockPos counterPos) {
        BlockPos counter = counterPos.immutable();
        BlockPos startFloor = counter.below();
        if (!view.isKitchenFloor(startFloor)) {
            return new KitchenScanResult(false, Set.of(), List.of(), List.of(counter), List.of(), List.of());
        }

        Set<BlockPos> floors = findConnectedFloors(view, startFloor);
        List<BlockPos> members = new ArrayList<>();
        Set<BlockPos> counters = new LinkedHashSet<>();
        List<BlockPos> waterProviders = new ArrayList<>();
        List<BlockPos> freezers = new ArrayList<>();

        for (BlockPos floor : floors) {
            for (int yOffset = 1; yOffset <= MAX_SCAN_HEIGHT; yOffset++) {
                BlockPos pos = floor.above(yOffset).immutable();
                if (view.isWaterProvider(pos)) {
                    waterProviders.add(pos);
                    continue;
                }
                if (view.isFreezer(pos)) {
                    freezers.add(pos);
                    continue;
                }
                if (!isCompatibleMember(view, pos)) {
                    continue;
                }

                members.add(pos);
                if (view.isChefCounter(pos)) {
                    counters.add(pos);
                }
            }
        }

        counters.add(counter);
        return new KitchenScanResult(true, floors, List.copyOf(members), List.copyOf(counters),
                List.copyOf(waterProviders), List.copyOf(freezers));
    }

    private static boolean isCompatibleMember(ScanView view, BlockPos pos) {
        if (view.isWaterProvider(pos) || view.isFreezer(pos)) {
            return false;
        }
        BlockEntity blockEntity = view.getBlockEntity(pos);
        return blockEntity instanceof Container
                || blockEntity instanceof KitchenStation
                || view.getItemHandler(pos) != null
                || view.isChefCounter(pos);
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

    private static Set<BlockPos> findConnectedFloors(ScanView view, BlockPos start) {
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
                if (!visited.contains(next) && view.isKitchenFloor(next)) {
                    queue.add(next);
                }
            }
        }

        return Set.copyOf(visited);
    }

    interface ScanView {
        boolean isKitchenFloor(BlockPos pos);

        boolean isWaterProvider(BlockPos pos);

        boolean isFreezer(BlockPos pos);

        boolean isChefCounter(BlockPos pos);

        @Nullable
        BlockEntity getBlockEntity(BlockPos pos);

        @Nullable
        IItemHandler getItemHandler(BlockPos pos);
    }

    private record LevelScanView(Level level) implements ScanView {
        @Override
        public boolean isKitchenFloor(BlockPos pos) {
            return level.getBlockState(pos).is(ModBlocks.KITCHEN_FLOOR.get());
        }

        @Override
        public boolean isWaterProvider(BlockPos pos) {
            return level.getBlockState(pos).is(ModBlocks.WATER_PROVIDER.get());
        }

        @Override
        public boolean isFreezer(BlockPos pos) {
            return level.getBlockState(pos).is(ModBlocks.FREEZER.get());
        }

        @Override
        public boolean isChefCounter(BlockPos pos) {
            return level.getBlockState(pos).is(ModBlocks.CHEF_COUNTER.get());
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return level.getBlockEntity(pos);
        }

        @Override
        public IItemHandler getItemHandler(BlockPos pos) {
            return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        }
    }
}
