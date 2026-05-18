package net.inkyquill.delightfulkitchen.network.payload;

import net.inkyquill.delightfulkitchen.DelightfulKitchen;
import net.inkyquill.delightfulkitchen.kitchen.execution.KitchenPlanFingerprint;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ServerboundChefCounterConfirmPayload(
        int containerId,
        int selectedIndex,
        int requestedQuantity,
        KitchenPlanFingerprint fingerprint
) implements CustomPacketPayload {
    public static final Type<ServerboundChefCounterConfirmPayload> TYPE =
            new Type<>(DelightfulKitchen.id("chef_counter_confirm"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundChefCounterConfirmPayload> STREAM_CODEC =
            StreamCodec.of(ServerboundChefCounterConfirmPayload::write, ServerboundChefCounterConfirmPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, ServerboundChefCounterConfirmPayload payload) {
        buf.writeVarInt(payload.containerId);
        buf.writeVarInt(payload.selectedIndex);
        buf.writeVarInt(payload.requestedQuantity);
        ChefCounterPayloadCodecs.FINGERPRINT.encode(buf, payload.fingerprint);
    }

    private static ServerboundChefCounterConfirmPayload read(RegistryFriendlyByteBuf buf) {
        return new ServerboundChefCounterConfirmPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                ChefCounterPayloadCodecs.FINGERPRINT.decode(buf));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
