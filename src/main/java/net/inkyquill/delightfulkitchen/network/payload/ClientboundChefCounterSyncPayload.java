package net.inkyquill.delightfulkitchen.network.payload;

import net.inkyquill.delightfulkitchen.DelightfulKitchen;
import net.inkyquill.delightfulkitchen.menu.chef.ChefRecipeSummary;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

public record ClientboundChefCounterSyncPayload(
        int containerId,
        List<ChefRecipeSummary> summaries,
        int floorCount,
        int inventoryCount,
        int recipeCount,
        int readyCount,
        int intermediateCount,
        int blockedCount,
        int waterProviderCount,
        int freezerCount,
        boolean hasMultipleCounters
) implements CustomPacketPayload {
    public static final Type<ClientboundChefCounterSyncPayload> TYPE =
            new Type<>(DelightfulKitchen.id("chef_counter_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundChefCounterSyncPayload> STREAM_CODEC =
            StreamCodec.of(ClientboundChefCounterSyncPayload::write, ClientboundChefCounterSyncPayload::read);

    public ClientboundChefCounterSyncPayload {
        summaries = List.copyOf(summaries);
    }

    private static void write(RegistryFriendlyByteBuf buf, ClientboundChefCounterSyncPayload payload) {
        buf.writeVarInt(payload.containerId);
        ChefCounterPayloadCodecs.writeSummaries(buf, payload.summaries);
        buf.writeVarInt(payload.floorCount);
        buf.writeVarInt(payload.inventoryCount);
        buf.writeVarInt(payload.recipeCount);
        buf.writeVarInt(payload.readyCount);
        buf.writeVarInt(payload.intermediateCount);
        buf.writeVarInt(payload.blockedCount);
        buf.writeVarInt(payload.waterProviderCount);
        buf.writeVarInt(payload.freezerCount);
        buf.writeBoolean(payload.hasMultipleCounters);
    }

    private static ClientboundChefCounterSyncPayload read(RegistryFriendlyByteBuf buf) {
        return new ClientboundChefCounterSyncPayload(
                buf.readVarInt(),
                ChefCounterPayloadCodecs.readSummaries(buf),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
