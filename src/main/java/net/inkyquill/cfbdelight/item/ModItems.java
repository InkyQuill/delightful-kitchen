package net.inkyquill.cfbdelight.item;

import net.inkyquill.cfbdelight.CfBDelight;
import net.inkyquill.cfbdelight.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CfBDelight.MOD_ID);

    public static final DeferredItem<BlockItem> POT_STAND        = ITEMS.registerSimpleBlockItem(ModBlocks.POT_STAND);
    public static final DeferredItem<BlockItem> AUTO_CUTTER      = ITEMS.registerSimpleBlockItem(ModBlocks.AUTO_CUTTER);
    public static final DeferredItem<BlockItem> GRILL            = ITEMS.registerSimpleBlockItem(ModBlocks.GRILL);
    public static final DeferredItem<BlockItem> CONTAINER_STORAGE = ITEMS.registerSimpleBlockItem(ModBlocks.CONTAINER_STORAGE);

    /** Populate a creative tab (e.g. in a {@code BuildCreativeModeTabContentsEvent} handler). */
    public static void addToTab(CreativeModeTab.Output output) {
        output.accept(POT_STAND.get());
        output.accept(AUTO_CUTTER.get());
        output.accept(GRILL.get());
        output.accept(CONTAINER_STORAGE.get());
    }
}
