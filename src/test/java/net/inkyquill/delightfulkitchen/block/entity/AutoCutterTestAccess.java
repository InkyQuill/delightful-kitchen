package net.inkyquill.delightfulkitchen.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;

public final class AutoCutterTestAccess {
    private AutoCutterTestAccess() {
    }

    public static AutoCutterBlockEntity newAutoCutter() {
        return new AutoCutterBlockEntity(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
    }

    public static void finishQueuedJobWithRemainders(AutoCutterBlockEntity autoCutter, List<ItemStack> remainders) {
        autoCutter.finishQueuedJobWithRemainders(remainders);
    }
}
