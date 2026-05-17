package net.inkyquill.delightfulkitchen.block.entity;

import net.inkyquill.delightfulkitchen.menu.ChefCounterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ChefCounterBlockEntity extends BlockEntity implements MenuProvider {

    public ChefCounterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHEF_COUNTER.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.delightful_kitchen.chef_counter");
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new ChefCounterMenu(id, playerInventory, ContainerLevelAccess.create(level, worldPosition));
    }
}
