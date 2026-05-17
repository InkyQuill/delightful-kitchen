package net.inkyquill.delightfulkitchen.client;

import net.inkyquill.delightfulkitchen.client.screen.AutoCutterScreen;
import net.inkyquill.delightfulkitchen.client.screen.CabinetOfHoldingScreen;
import net.inkyquill.delightfulkitchen.client.screen.ChefCounterScreen;
import net.inkyquill.delightfulkitchen.client.screen.ContainerStorageScreen;
import net.inkyquill.delightfulkitchen.client.screen.FuelProviderScreen;
import net.inkyquill.delightfulkitchen.client.screen.FreezerScreen;
import net.inkyquill.delightfulkitchen.client.screen.GrillScreen;
import net.inkyquill.delightfulkitchen.client.screen.PotStandScreen;
import net.inkyquill.delightfulkitchen.menu.ModMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import static net.inkyquill.delightfulkitchen.DelightfulKitchen.MOD_ID;

@EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class DelightfulKitchenClient {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.POT_STAND.get(),          PotStandScreen::new);
        event.register(ModMenuTypes.AUTO_CUTTER.get(),        AutoCutterScreen::new);
        event.register(ModMenuTypes.GRILL.get(),              GrillScreen::new);
        event.register(ModMenuTypes.CONTAINER_STORAGE.get(),  ContainerStorageScreen::new);
        event.register(ModMenuTypes.CABINET_OF_HOLDING.get(), CabinetOfHoldingScreen::new);
        event.register(ModMenuTypes.CHEF_COUNTER.get(),       ChefCounterScreen::new);
        event.register(ModMenuTypes.FUEL_PROVIDER.get(),      FuelProviderScreen::new);
        event.register(ModMenuTypes.FREEZER.get(),            FreezerScreen::new);
    }
}
