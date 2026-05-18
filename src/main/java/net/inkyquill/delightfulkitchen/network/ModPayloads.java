package net.inkyquill.delightfulkitchen.network;

import net.inkyquill.delightfulkitchen.menu.ChefCounterMenu;
import net.inkyquill.delightfulkitchen.item.FoodPouchItem;
import net.inkyquill.delightfulkitchen.network.payload.ClientboundChefCounterDetailsPayload;
import net.inkyquill.delightfulkitchen.network.payload.ClientboundChefCounterQueueResultPayload;
import net.inkyquill.delightfulkitchen.network.payload.ClientboundChefCounterSyncPayload;
import net.inkyquill.delightfulkitchen.network.payload.ServerboundChefCounterConfirmPayload;
import net.inkyquill.delightfulkitchen.network.payload.ServerboundChefCounterSelectPayload;
import net.inkyquill.delightfulkitchen.network.payload.ServerboundOpenEquippedPouchPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import top.theillusivec4.curios.api.CuriosApi;

public final class ModPayloads {
    private static final String NETWORK_VERSION = "1";

    private ModPayloads() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToClient(ClientboundChefCounterSyncPayload.TYPE,
                        ClientboundChefCounterSyncPayload.STREAM_CODEC, ModPayloads::handleSync)
                .playToClient(ClientboundChefCounterDetailsPayload.TYPE,
                        ClientboundChefCounterDetailsPayload.STREAM_CODEC, ModPayloads::handleDetails)
                .playToClient(ClientboundChefCounterQueueResultPayload.TYPE,
                        ClientboundChefCounterQueueResultPayload.STREAM_CODEC, ModPayloads::handleQueueResult)
                .playToServer(ServerboundChefCounterSelectPayload.TYPE,
                        ServerboundChefCounterSelectPayload.STREAM_CODEC, ModPayloads::handleSelect)
                .playToServer(ServerboundChefCounterConfirmPayload.TYPE,
                        ServerboundChefCounterConfirmPayload.STREAM_CODEC, ModPayloads::handleConfirm)
                .playToServer(ServerboundOpenEquippedPouchPayload.TYPE,
                        ServerboundOpenEquippedPouchPayload.STREAM_CODEC, ModPayloads::handleOpenEquippedPouch);
    }

    public static void sendToPlayer(ServerPlayer player, ClientboundChefCounterSyncPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToPlayer(ServerPlayer player, ClientboundChefCounterDetailsPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToPlayer(ServerPlayer player, ClientboundChefCounterQueueResultPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToServer(ServerboundChefCounterSelectPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToServer(ServerboundChefCounterConfirmPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToServer(ServerboundOpenEquippedPouchPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private static void handleSync(ClientboundChefCounterSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ChefCounterMenu menu = matchingMenu(context.player(), payload.containerId());
            if (menu != null) {
                menu.handleChefCounterSync(payload);
            }
        });
    }

    private static void handleDetails(ClientboundChefCounterDetailsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ChefCounterMenu menu = matchingMenu(context.player(), payload.containerId());
            if (menu != null) {
                menu.handleChefCounterDetails(payload);
            }
        });
    }

    private static void handleQueueResult(ClientboundChefCounterQueueResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ChefCounterMenu menu = matchingMenu(context.player(), payload.containerId());
            if (menu != null) {
                menu.handleChefCounterQueueResult(payload);
            }
        });
    }

    private static void handleSelect(ServerboundChefCounterSelectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ChefCounterMenu menu = matchingMenu(context.player(), payload.containerId());
            if (menu != null) {
                menu.handleChefCounterSelect(payload);
            }
        });
    }

    private static void handleConfirm(ServerboundChefCounterConfirmPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ChefCounterMenu menu = matchingMenu(context.player(), payload.containerId());
            if (menu != null) {
                menu.handleChefCounterConfirm(payload);
            }
        });
    }

    private static void handleOpenEquippedPouch(ServerboundOpenEquippedPouchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                CuriosApi.getCuriosInventory(player)
                        .flatMap(handler -> handler.findFirstCurio(stack -> stack.getItem() instanceof FoodPouchItem,
                                "delightful_kitchen:pouch"))
                        .map(result -> result.stack())
                        .filter(stack -> !stack.isEmpty())
                        .ifPresent(stack -> FoodPouchItem.open(player, stack));
            }
        });
    }

    private static ChefCounterMenu matchingMenu(Player player, int containerId) {
        if (player.containerMenu instanceof ChefCounterMenu menu && menu.containerId == containerId) {
            return menu;
        }
        return null;
    }
}
