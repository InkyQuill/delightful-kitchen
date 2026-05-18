package net.inkyquill.delightfulkitchen.item;

import net.inkyquill.delightfulkitchen.DelightfulKitchen;
import net.inkyquill.delightfulkitchen.block.ModBlocks;
import net.inkyquill.delightfulkitchen.pouch.FoodPouchTier;
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
            DeferredRegister.createItems(DelightfulKitchen.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DelightfulKitchen.MOD_ID);

    public static final DeferredItem<BlockItem> POT_STAND         = ITEMS.registerSimpleBlockItem(ModBlocks.POT_STAND);
    public static final DeferredItem<BlockItem> AUTO_CUTTER       = ITEMS.registerSimpleBlockItem(ModBlocks.AUTO_CUTTER);
    public static final DeferredItem<BlockItem> GRILL             = ITEMS.registerSimpleBlockItem(ModBlocks.GRILL);
    public static final DeferredItem<BlockItem> CONTAINER_STORAGE = ITEMS.registerSimpleBlockItem(ModBlocks.CONTAINER_STORAGE);
    public static final DeferredItem<BlockItem> CABINET_OF_HOLDING = ITEMS.registerSimpleBlockItem(ModBlocks.CABINET_OF_HOLDING);
    public static final DeferredItem<BlockItem> CHEF_COUNTER      = ITEMS.registerSimpleBlockItem(ModBlocks.CHEF_COUNTER);
    public static final DeferredItem<BlockItem> FUEL_PROVIDER     = ITEMS.registerSimpleBlockItem(ModBlocks.FUEL_PROVIDER);
    public static final DeferredItem<BlockItem> WATER_PROVIDER    = ITEMS.registerSimpleBlockItem(ModBlocks.WATER_PROVIDER);
    public static final DeferredItem<BlockItem> FREEZER           = ITEMS.registerSimpleBlockItem(ModBlocks.FREEZER);
    public static final DeferredItem<BlockItem> KITCHEN_FLOOR     = ITEMS.registerSimpleBlockItem(ModBlocks.KITCHEN_FLOOR);

    public static final DeferredItem<MopItem> MOP = ITEMS.register("mop",
            () -> new MopItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<FoodPouchItem> FOOD_POUCH = ITEMS.register("food_pouch",
            () -> new FoodPouchItem(FoodPouchTier.BASIC, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<FoodPouchItem> ADVANCED_FOOD_POUCH = ITEMS.register("advanced_food_pouch",
            () -> new FoodPouchItem(FoodPouchTier.ADVANCED, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<FoodPouchItem> ULTIMATE_FOOD_POUCH = ITEMS.register("ultimate_food_pouch",
            () -> new FoodPouchItem(FoodPouchTier.ULTIMATE, new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CFB_DELIGHT_TAB =
            CREATIVE_TABS.register("delightful_kitchen", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.delightful_kitchen"))
                    .icon(() -> GRILL.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(AUTO_CUTTER.get());
                        output.accept(GRILL.get());
                        output.accept(POT_STAND.get());
                        output.accept(CONTAINER_STORAGE.get());
                        output.accept(CABINET_OF_HOLDING.get());
                        output.accept(CHEF_COUNTER.get());
                        output.accept(FUEL_PROVIDER.get());
                        output.accept(WATER_PROVIDER.get());
                        output.accept(FREEZER.get());
                        output.accept(KITCHEN_FLOOR.get());
                        output.accept(MOP.get());
                        output.accept(FOOD_POUCH.get());
                        output.accept(ADVANCED_FOOD_POUCH.get());
                        output.accept(ULTIMATE_FOOD_POUCH.get());
                    })
                    .build());
}
