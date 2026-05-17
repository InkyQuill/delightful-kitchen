package net.inkyquill.delightfulkitchen.kitchen.station;

import net.minecraft.core.BlockPos;

import java.util.Objects;

public record KitchenStationEndpoint(BlockPos pos, KitchenStation station) {
    public KitchenStationEndpoint {
        pos = Objects.requireNonNull(pos, "pos").immutable();
        station = Objects.requireNonNull(station, "station");
    }
}
