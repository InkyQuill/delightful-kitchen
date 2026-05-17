package net.inkyquill.delightfulkitchen.kitchen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitchenScanResultTest {
    @Test
    void scannerDiscoversUtilityBlocksWithoutAddingMembers() {
        BlockPos counter = new BlockPos(0, 65, 0);
        BlockPos waterProvider = new BlockPos(1, 65, 0);
        BlockPos freezer = new BlockPos(2, 65, 0);
        KitchenScanner.ScanView view = new TestScanView(
                Set.of(counter.below(), waterProvider.below(), freezer.below()),
                Set.of(waterProvider),
                Set.of(freezer),
                Set.of(counter)
        );

        KitchenScanResult result = KitchenScanner.scan(view, counter);

        assertTrue(result.hasWaterProvider());
        assertTrue(result.hasFreezer());
        assertEquals(List.of(waterProvider), result.waterProviders());
        assertEquals(List.of(freezer), result.freezers());
        assertEquals(List.of(counter), result.memberBlocks());
        assertEquals(1, result.memberCount());
    }

    @Test
    void copiesUtilityPositionsDefensively() {
        List<BlockPos> waterProviders = new ArrayList<>(List.of(new BlockPos(1, 64, 0)));
        List<BlockPos> freezers = new ArrayList<>(List.of(new BlockPos(2, 64, 0)));

        KitchenScanResult result = new KitchenScanResult(true, Set.of(BlockPos.ZERO),
                List.of(), List.of(), waterProviders, freezers);

        waterProviders.add(new BlockPos(3, 64, 0));
        freezers.clear();

        assertEquals(List.of(new BlockPos(1, 64, 0)), result.waterProviders());
        assertEquals(List.of(new BlockPos(2, 64, 0)), result.freezers());
        assertThrows(UnsupportedOperationException.class, () -> result.waterProviders().add(BlockPos.ZERO));
        assertThrows(UnsupportedOperationException.class, () -> result.freezers().clear());
    }

    @Test
    void reportsUtilityPresenceWithoutCountingUtilitiesAsMembers() {
        KitchenScanResult result = new KitchenScanResult(true,
                new LinkedHashSet<>(List.of(BlockPos.ZERO, BlockPos.ZERO.east())),
                List.of(new BlockPos(0, 65, 0)),
                List.of(new BlockPos(0, 65, 0)),
                List.of(new BlockPos(1, 65, 0)),
                List.of(new BlockPos(2, 65, 0)));

        assertTrue(result.hasWaterProvider());
        assertTrue(result.hasFreezer());
        assertEquals(1, result.memberCount());
        assertEquals(2, result.floorCount());
    }

    @Test
    void reportsMissingUtilities() {
        KitchenScanResult result = new KitchenScanResult(true, Set.of(BlockPos.ZERO),
                List.of(), List.of(), List.of(), List.of());

        assertFalse(result.hasWaterProvider());
        assertFalse(result.hasFreezer());
    }

    private record TestScanView(
            Set<BlockPos> floors,
            Set<BlockPos> waterProviders,
            Set<BlockPos> freezers,
            Set<BlockPos> chefCounters
    ) implements KitchenScanner.ScanView {
        @Override
        public boolean isKitchenFloor(BlockPos pos) {
            return floors.contains(pos);
        }

        @Override
        public boolean isWaterProvider(BlockPos pos) {
            return waterProviders.contains(pos);
        }

        @Override
        public boolean isFreezer(BlockPos pos) {
            return freezers.contains(pos);
        }

        @Override
        public boolean isChefCounter(BlockPos pos) {
            return chefCounters.contains(pos);
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public IItemHandler getItemHandler(BlockPos pos) {
            return null;
        }
    }
}
