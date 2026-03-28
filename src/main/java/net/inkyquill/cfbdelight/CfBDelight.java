package net.inkyquill.cfbdelight;

import net.inkyquill.cfbdelight.block.ModBlocks;
import net.inkyquill.cfbdelight.block.entity.ModBlockEntities;
import net.inkyquill.cfbdelight.compat.FarmersDelightCompat;
import net.inkyquill.cfbdelight.item.ModItems;
import net.inkyquill.cfbdelight.menu.ModMenuTypes;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(CfBDelight.MOD_ID)
public class CfBDelight {

    public static final String MOD_ID = "cfb_delight";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CfBDelight(IEventBus modEventBus) {
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
