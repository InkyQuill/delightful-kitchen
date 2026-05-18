package net.inkyquill.delightfulkitchen.network.payload;

import net.inkyquill.delightfulkitchen.DelightfulKitchen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ServerboundChefCounterSelectPayload(
        int containerId,
        int selectedIndex,
        int requestedQuantity
) implements CustomPacketPayload {
    public static final Type<ServerboundChefCounterSelectPayload> TYPE =
            new Type<>(DelightfulKitchen.id("chef_counter_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundChefCounterSelectPayload> STREAM_CODEC =
            StreamCodec.of(ServerboundChefCounterSelectPayload::write, ServerboundChefCounterSelectPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, ServerboundChefCounterSelectPayload payload) {
        buf.writeVarInt(payload.containerId);
        buf.writeVarInt(payload.selectedIndex);
        buf.writeVarInt(payload.requestedQuantity);
    }

    private static ServerboundChefCounterSelectPayload read(RegistryFriendlyByteBuf buf) {
        return new ServerboundChefCounterSelectPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
