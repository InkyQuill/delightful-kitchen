package net.inkyquill.delightfulkitchen.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class PotStandTestAccess {
    private PotStandTestAccess() {
    }

    public static PotStandBlockEntity newPotStand() {
        return new PotStandBlockEntity(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
    }

    public static void finishQueuedCookingWithRemainder(PotStandBlockEntity potStand, ItemStack remainder) {
        potStand.finishQueuedCookingWithRemainder(remainder);
    }
}
