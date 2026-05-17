package net.inkyquill.delightfulkitchen.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class FreezerTestAccess {
    private FreezerTestAccess() {
    }

    public static FreezerBlockEntity create() {
        return new FreezerBlockEntity(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
    }
}
