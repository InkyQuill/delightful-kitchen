package net.inkyquill.delightfulkitchen.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class GrillTestAccess {
    private GrillTestAccess() {
    }

    public static GrillBlockEntity newGrill() {
        return new GrillBlockEntity(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
    }

    public static void finishQueuedSlotWithRemainder(GrillBlockEntity grill, int slot, ItemStack remainder) {
        grill.finishQueuedSlotWithRemainder(slot, remainder);
    }
}
