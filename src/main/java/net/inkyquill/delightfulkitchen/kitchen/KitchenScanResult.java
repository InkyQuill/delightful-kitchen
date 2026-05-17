package net.inkyquill.delightfulkitchen.kitchen;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Set;

public record KitchenScanResult(
        boolean hasKitchenFloor,
        Set<BlockPos> floorTiles,
        List<BlockPos> memberBlocks,
        List<BlockPos> counters
) {
    public KitchenScanResult {
        floorTiles = Set.copyOf(floorTiles);
        memberBlocks = List.copyOf(memberBlocks);
        counters = List.copyOf(counters);
    }

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
