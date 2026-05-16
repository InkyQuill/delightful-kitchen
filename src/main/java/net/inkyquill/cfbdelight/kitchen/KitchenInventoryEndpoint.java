package net.inkyquill.cfbdelight.kitchen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public record KitchenInventoryEndpoint(BlockPos pos, Container container) {
    public KitchenInventoryEndpoint {
        pos = pos.immutable();
    }

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
