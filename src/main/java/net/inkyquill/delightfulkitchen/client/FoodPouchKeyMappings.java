package net.inkyquill.delightfulkitchen.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.inkyquill.delightfulkitchen.network.ModPayloads;
import net.inkyquill.delightfulkitchen.network.payload.ServerboundOpenEquippedPouchPayload;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

import static net.inkyquill.delightfulkitchen.DelightfulKitchen.MOD_ID;

@EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
public final class FoodPouchKeyMappings {
    private static final KeyMapping OPEN_FOOD_POUCH = new KeyMapping(
            "key.delightful_kitchen.open_food_pouch",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.delightful_kitchen"
    );

    private FoodPouchKeyMappings() {
    }

    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_FOOD_POUCH);
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        while (OPEN_FOOD_POUCH.consumeClick()) {
            ModPayloads.sendToServer(new ServerboundOpenEquippedPouchPayload());
        }
    }
}
