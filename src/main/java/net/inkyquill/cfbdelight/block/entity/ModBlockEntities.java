package net.inkyquill.cfbdelight.block.entity;

import net.inkyquill.cfbdelight.CfBDelight;
import net.inkyquill.cfbdelight.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CfBDelight.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PotStandBlockEntity>> POT_STAND =
            BLOCK_ENTITIES.register("pot_stand", () ->
                    BlockEntityType.Builder.of(PotStandBlockEntity::new, ModBlocks.POT_STAND.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutoCutterBlockEntity>> AUTO_CUTTER =
            BLOCK_ENTITIES.register("auto_cutter", () ->
                    BlockEntityType.Builder.of(AutoCutterBlockEntity::new, ModBlocks.AUTO_CUTTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GrillBlockEntity>> GRILL =
            BLOCK_ENTITIES.register("grill", () ->
                    BlockEntityType.Builder.of(GrillBlockEntity::new, ModBlocks.GRILL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ContainerStorageBlockEntity>> CONTAINER_STORAGE =
            BLOCK_ENTITIES.register("container_storage", () ->
                    BlockEntityType.Builder.of(ContainerStorageBlockEntity::new, ModBlocks.CONTAINER_STORAGE.get()).build(null));
}
