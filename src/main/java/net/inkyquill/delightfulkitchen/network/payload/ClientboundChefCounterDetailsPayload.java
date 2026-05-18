package net.inkyquill.delightfulkitchen.network.payload;

import net.inkyquill.delightfulkitchen.DelightfulKitchen;
import net.inkyquill.delightfulkitchen.menu.chef.ChefPlanDetails;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Optional;
import java.util.Objects;

public record ClientboundChefCounterDetailsPayload(
        int containerId,
        Optional<ChefPlanDetails> details
) implements CustomPacketPayload {
    public static final Type<ClientboundChefCounterDetailsPayload> TYPE =
            new Type<>(DelightfulKitchen.id("chef_counter_details"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundChefCounterDetailsPayload> STREAM_CODEC =
            StreamCodec.of(ClientboundChefCounterDetailsPayload::write, ClientboundChefCounterDetailsPayload::read);

    public ClientboundChefCounterDetailsPayload {
        details = Objects.requireNonNull(details, "details");
    }

    private static void write(RegistryFriendlyByteBuf buf, ClientboundChefCounterDetailsPayload payload) {
        buf.writeVarInt(payload.containerId);
        ChefCounterPayloadCodecs.writeOptionalDetails(buf, payload.details);
    }

    private static ClientboundChefCounterDetailsPayload read(RegistryFriendlyByteBuf buf) {
        return new ClientboundChefCounterDetailsPayload(buf.readVarInt(),
                ChefCounterPayloadCodecs.readOptionalDetails(buf));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
