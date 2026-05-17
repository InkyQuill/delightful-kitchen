package net.inkyquill.delightfulkitchen.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Minimal block entity placeholder until freezer behavior is implemented. */
public class FreezerBlockEntity extends BlockEntity {

    public FreezerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FREEZER.get(), pos, blockState);
    }
}
