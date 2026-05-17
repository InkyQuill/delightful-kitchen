package net.inkyquill.delightfulkitchen.kitchen.station;

import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;
import net.minecraft.core.BlockPos;

public interface KitchenStation {
    KitchenStationType stationType();

    int queueCapacity();

    int queuedJobCount();

    KitchenStationStatus stationStatus();

    default boolean hasQueueSpace() {
        return queuedJobCount() < queueCapacity();
    }

    KitchenStationAcceptResult canAccept(KitchenStationJob job);

    KitchenStationAcceptResult accept(KitchenStationJob job);

    BlockPos stationPos();
}
