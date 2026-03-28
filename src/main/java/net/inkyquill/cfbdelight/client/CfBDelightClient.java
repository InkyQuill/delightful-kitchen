package net.inkyquill.cfbdelight.client;

import net.inkyquill.cfbdelight.client.screen.AutoCutterScreen;
import net.inkyquill.cfbdelight.client.screen.ContainerStorageScreen;
import net.inkyquill.cfbdelight.client.screen.GrillScreen;
import net.inkyquill.cfbdelight.client.screen.PotStandScreen;
import net.inkyquill.cfbdelight.menu.ModMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import static net.inkyquill.cfbdelight.CfBDelight.MOD_ID;

@EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CfBDelightClient {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.POT_STAND.get(),          PotStandScreen::new);
        event.register(ModMenuTypes.AUTO_CUTTER.get(),        AutoCutterScreen::new);
        event.register(ModMenuTypes.GRILL.get(),              GrillScreen::new);
        event.register(ModMenuTypes.CONTAINER_STORAGE.get(),  ContainerStorageScreen::new);
    }
}
