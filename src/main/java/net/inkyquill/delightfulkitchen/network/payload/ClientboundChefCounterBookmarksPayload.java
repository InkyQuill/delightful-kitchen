package net.inkyquill.delightfulkitchen.network.payload;

import io.netty.handler.codec.DecoderException;
import net.inkyquill.delightfulkitchen.DelightfulKitchen;
import net.inkyquill.delightfulkitchen.bookmark.RecipeBookmarkStore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record ClientboundChefCounterBookmarksPayload(
        int containerId,
        List<ResourceLocation> pinnedRecipeIds
) implements CustomPacketPayload {
    public static final int MAX_PINNED_RECIPES = RecipeBookmarkStore.MAX_PINNED_RECIPES;
    public static final Type<ClientboundChefCounterBookmarksPayload> TYPE =
            new Type<>(DelightfulKitchen.id("chef_counter_bookmarks"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundChefCounterBookmarksPayload> STREAM_CODEC =
            StreamCodec.of(ClientboundChefCounterBookmarksPayload::write, ClientboundChefCounterBookmarksPayload::read);

    public ClientboundChefCounterBookmarksPayload {
        pinnedRecipeIds = List.copyOf(pinnedRecipeIds);
    }

    private static void write(RegistryFriendlyByteBuf buf, ClientboundChefCounterBookmarksPayload payload) {
        buf.writeVarInt(payload.containerId);
        buf.writeVarInt(payload.pinnedRecipeIds.size());
        for (ResourceLocation recipeId : payload.pinnedRecipeIds) {
            buf.writeResourceLocation(recipeId);
        }
    }

    private static ClientboundChefCounterBookmarksPayload read(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_PINNED_RECIPES) {
            throw new DecoderException("Chef counter bookmark list too large: " + size);
        }
        List<ResourceLocation> recipeIds = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            recipeIds.add(buf.readResourceLocation());
        }
        return new ClientboundChefCounterBookmarksPayload(containerId, recipeIds);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
