package net.inkyquill.delightfulkitchen.network.payload;

import net.inkyquill.delightfulkitchen.DelightfulKitchen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ServerboundOpenEquippedPouchPayload() implements CustomPacketPayload {
    public static final Type<ServerboundOpenEquippedPouchPayload> TYPE =
            new Type<>(DelightfulKitchen.id("open_equipped_pouch"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundOpenEquippedPouchPayload> STREAM_CODEC =
            StreamCodec.unit(new ServerboundOpenEquippedPouchPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
