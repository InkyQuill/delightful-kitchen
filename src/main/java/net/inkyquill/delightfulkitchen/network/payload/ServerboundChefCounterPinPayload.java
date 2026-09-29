package net.inkyquill.delightfulkitchen.network.payload;

import net.inkyquill.delightfulkitchen.DelightfulKitchen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerboundChefCounterPinPayload(
        int containerId,
        ResourceLocation recipeId,
        boolean pin
) implements CustomPacketPayload {
    public static final Type<ServerboundChefCounterPinPayload> TYPE =
            new Type<>(DelightfulKitchen.id("chef_counter_pin"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundChefCounterPinPayload> STREAM_CODEC =
            StreamCodec.of(ServerboundChefCounterPinPayload::write, ServerboundChefCounterPinPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, ServerboundChefCounterPinPayload payload) {
        buf.writeVarInt(payload.containerId);
        buf.writeResourceLocation(payload.recipeId);
        buf.writeBoolean(payload.pin);
    }

    private static ServerboundChefCounterPinPayload read(RegistryFriendlyByteBuf buf) {
        return new ServerboundChefCounterPinPayload(buf.readVarInt(), buf.readResourceLocation(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
