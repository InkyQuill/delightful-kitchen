package net.inkyquill.delightfulkitchen.network.payload;

import net.inkyquill.delightfulkitchen.DelightfulKitchen;
import net.inkyquill.delightfulkitchen.menu.chef.ChefQueueResultView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClientboundChefCounterQueueResultPayload(
        int containerId,
        ChefQueueResultView result
) implements CustomPacketPayload {
    public static final Type<ClientboundChefCounterQueueResultPayload> TYPE =
            new Type<>(DelightfulKitchen.id("chef_counter_queue_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundChefCounterQueueResultPayload> STREAM_CODEC =
            StreamCodec.of(ClientboundChefCounterQueueResultPayload::write,
                    ClientboundChefCounterQueueResultPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, ClientboundChefCounterQueueResultPayload payload) {
        buf.writeVarInt(payload.containerId);
        ChefCounterPayloadCodecs.QUEUE_RESULT.encode(buf, payload.result);
    }

    private static ClientboundChefCounterQueueResultPayload read(RegistryFriendlyByteBuf buf) {
        return new ClientboundChefCounterQueueResultPayload(buf.readVarInt(),
                ChefCounterPayloadCodecs.QUEUE_RESULT.decode(buf));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
