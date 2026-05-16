package net.inkyquill.cfbdelight.block;

import net.inkyquill.cfbdelight.CfBDelight;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CfBDelight.MOD_ID);

    /** Processes Farmer's Delight Cooking Pot recipes. */
    public static final DeferredBlock<PotStandBlock> POT_STAND = BLOCKS.register("pot_stand",
            () -> new PotStandBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    /** Processes Farmer's Delight Cutting Board recipes automatically. */
    public static final DeferredBlock<AutoCutterBlock> AUTO_CUTTER = BLOCKS.register("auto_cutter",
            () -> new AutoCutterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.5f, 3.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    /** Grills items using campfire cooking recipes (same recipe type as FD's Stove). */
    public static final DeferredBlock<GrillBlock> GRILL = BLOCKS.register("grill",
            () -> new GrillBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.5f, 6.0f)
                    .sound(SoundType.STONE)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(GrillBlock.LIT) ? 13 : 0)));

    /**
     * Dedicated kitchen storage for bowls, bottles, and other container items.
     * Tagged {@code cookingforblockheads:kitchen_item_providers} so CfB's kitchen
     * automatically treats it as an ingredient source.
     */
    public static final DeferredBlock<ContainerStorageBlock> CONTAINER_STORAGE =
            BLOCKS.register("container_storage",
                    () -> new ContainerStorageBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(1.5f, 3.0f)
                            .sound(SoundType.WOOD)));

    /** Large 54-slot kitchen storage that doubles as a KitchenItemProvider. */
    public static final DeferredBlock<CabinetOfHoldingBlock> CABINET_OF_HOLDING =
            BLOCKS.register("cabinet_of_holding",
                    () -> new CabinetOfHoldingBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(3.0f, 6.0f)
                            .sound(SoundType.WOOD)));

    /** Standalone kitchen controller shell. */
    public static final DeferredBlock<ChefCounterBlock> CHEF_COUNTER =
            BLOCKS.register("chef_counter",
                    () -> new ChefCounterBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(3.0f, 6.0f)
                            .sound(SoundType.WOOD)));

    /** Stores fuel and pushes it to adjacent Grill and PotStand blocks. */
    public static final DeferredBlock<FuelProviderBlock> FUEL_PROVIDER =
            BLOCKS.register("fuel_provider",
                    () -> new FuelProviderBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(2.5f, 6.0f)
                            .sound(SoundType.STONE)));

    /** Foundation block for standalone kitchen floor interactions. */
    public static final DeferredBlock<KitchenFloorBlock> KITCHEN_FLOOR =
            BLOCKS.register("kitchen_floor",
                    () -> new KitchenFloorBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(1.5f, 3.0f)
                            .sound(SoundType.WOOD)));
}
