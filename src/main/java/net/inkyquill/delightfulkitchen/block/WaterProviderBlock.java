package net.inkyquill.delightfulkitchen.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Passive kitchen utility block used by future water-aware recipe planning. */
public class WaterProviderBlock extends Block {

    public WaterProviderBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
