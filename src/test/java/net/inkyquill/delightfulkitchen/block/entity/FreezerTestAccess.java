package net.inkyquill.delightfulkitchen.block.entity;

import net.inkyquill.delightfulkitchen.kitchen.utility.KitchenUtilityService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Consumer;

public final class FreezerTestAccess {
    private FreezerTestAccess() {
    }

    public static FreezerBlockEntity create() {
        return new FreezerBlockEntity(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
    }

    public static void tickWithUtility(FreezerBlockEntity freezer, KitchenUtilityService service,
                                       Consumer<ItemStack> unreleasedContainerHandler) {
        freezer.tickPassiveProduction(service, unreleasedContainerHandler);
    }

    public static int progress(FreezerBlockEntity freezer) {
        return freezer.progressTicks();
    }

    public static boolean isActive(FreezerBlockEntity freezer) {
        return freezer.isProducing();
    }

    public static boolean prefersIceNext(FreezerBlockEntity freezer) {
        return freezer.nextProductIce();
    }

    public static FreezerBlockEntity.PassiveMode mode(FreezerBlockEntity freezer) {
        return freezer.passiveMode();
    }

    public static void setMode(FreezerBlockEntity freezer, FreezerBlockEntity.PassiveMode mode) {
        freezer.setPassiveMode(mode);
    }

    public static void cycleMode(FreezerBlockEntity freezer) {
        freezer.cyclePassiveMode();
    }

    public static CompoundTag save(FreezerBlockEntity freezer, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        freezer.saveAdditional(tag, registries);
        return tag;
    }

    public static void load(FreezerBlockEntity freezer, CompoundTag tag, HolderLookup.Provider registries) {
        freezer.loadAdditional(tag, registries);
    }
}
