package net.inkyquill.cfbdelight.item;

import net.inkyquill.cfbdelight.CfBDelight;
import net.inkyquill.cfbdelight.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(CfBDelight.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CfBDelight.MOD_ID);

    public static final DeferredItem<BlockItem> POT_STAND         = ITEMS.registerSimpleBlockItem(ModBlocks.POT_STAND);
    public static final DeferredItem<BlockItem> AUTO_CUTTER       = ITEMS.registerSimpleBlockItem(ModBlocks.AUTO_CUTTER);
    public static final DeferredItem<BlockItem> GRILL             = ITEMS.registerSimpleBlockItem(ModBlocks.GRILL);
    public static final DeferredItem<BlockItem> CONTAINER_STORAGE = ITEMS.registerSimpleBlockItem(ModBlocks.CONTAINER_STORAGE);
    public static final DeferredItem<BlockItem> CABINET_OF_HOLDING = ITEMS.registerSimpleBlockItem(ModBlocks.CABINET_OF_HOLDING);
    public static final DeferredItem<BlockItem> CHEF_COUNTER      = ITEMS.registerSimpleBlockItem(ModBlocks.CHEF_COUNTER);
    public static final DeferredItem<BlockItem> FUEL_PROVIDER     = ITEMS.registerSimpleBlockItem(ModBlocks.FUEL_PROVIDER);
    public static final DeferredItem<BlockItem> KITCHEN_FLOOR     = ITEMS.registerSimpleBlockItem(ModBlocks.KITCHEN_FLOOR);

    public static final DeferredItem<MopItem> MOP = ITEMS.register("mop",
            () -> new MopItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CFB_DELIGHT_TAB =
            CREATIVE_TABS.register("cfb_delight", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.cfb_delight"))
                    .icon(() -> GRILL.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(AUTO_CUTTER.get());
                        output.accept(GRILL.get());
                        output.accept(POT_STAND.get());
                        output.accept(CONTAINER_STORAGE.get());
                        output.accept(CABINET_OF_HOLDING.get());
                        output.accept(CHEF_COUNTER.get());
                        output.accept(FUEL_PROVIDER.get());
                        output.accept(KITCHEN_FLOOR.get());
                        output.accept(MOP.get());
                    })
                    .build());
}
