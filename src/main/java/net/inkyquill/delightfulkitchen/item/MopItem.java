package net.inkyquill.delightfulkitchen.item;

import net.inkyquill.delightfulkitchen.block.ModBlocks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class MopItem extends Item {

    public MopItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.getBlockState(context.getClickedPos()).is(ModBlocks.KITCHEN_FLOOR.get())) {
            if (!level.isClientSide() && context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(
                        Component.translatable("message.delightful_kitchen.kitchen_floor.reset"),
                        true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        return InteractionResult.PASS;
    }
}
