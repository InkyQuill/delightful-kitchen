package net.inkyquill.delightfulkitchen;

import com.mojang.logging.LogUtils;
import net.inkyquill.delightfulkitchen.block.ModBlocks;
import net.inkyquill.delightfulkitchen.block.entity.ModBlockEntities;
import net.inkyquill.delightfulkitchen.compat.FarmersDelightCompat;
import net.inkyquill.delightfulkitchen.item.ModItems;
import net.inkyquill.delightfulkitchen.menu.ModMenuTypes;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;

@Mod(DelightfulKitchen.MOD_ID)
public class DelightfulKitchen {

    public static final String MOD_ID = "delightful_kitchen";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DelightfulKitchen(IEventBus modEventBus) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.CREATIVE_TABS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenuTypes.MENUS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(FarmersDelightCompat::registerRecipeHandlers);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        FarmersDelightCompat.registerCapabilities(event);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
