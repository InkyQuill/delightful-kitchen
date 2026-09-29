package net.inkyquill.delightfulkitchen.network.payload;

import io.netty.buffer.Unpooled;
import net.inkyquill.delightfulkitchen.kitchen.execution.KitchenPlanFingerprint;
import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanState;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;
import net.inkyquill.delightfulkitchen.kitchen.utility.KitchenUtilityRequirement;
import net.inkyquill.delightfulkitchen.menu.chef.ChefPlanDetails;
import net.inkyquill.delightfulkitchen.menu.chef.ChefPlanStepView;
import net.inkyquill.delightfulkitchen.menu.chef.ChefQueueResultView;
import net.inkyquill.delightfulkitchen.menu.chef.ChefRecipeSummary;
import net.minecraft.SharedConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChefCounterPayloadCodecTest {
    @BeforeAll
    static void bootStrapMinecraft() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void syncPayloadPreservesSummaryItemRecipeStateAndBlockers() {
        ChefRecipeSummary summary = new ChefRecipeSummary(
                4,
                id("recipe/bread"),
                new ItemStack(Items.BREAD, 3),
                PlanState.BLOCKED,
                EnumSet.of(PlanningBlocker.MISSING_FUEL, PlanningBlocker.MISSING_STATION),
                2
        );
        ClientboundChefCounterSyncPayload payload = new ClientboundChefCounterSyncPayload(
                11,
                List.of(summary),
                5,
                6,
                7,
                8,
                9,
                10,
                1,
                2,
                true
        );

        ClientboundChefCounterSyncPayload decoded = roundTrip(ClientboundChefCounterSyncPayload.STREAM_CODEC, payload);
        ChefRecipeSummary decodedSummary = decoded.summaries().getFirst();

        assertEquals(11, decoded.containerId());
        assertEquals(Items.BREAD, decodedSummary.output().getItem());
        assertEquals(3, decodedSummary.output().getCount());
        assertEquals(id("recipe/bread"), decodedSummary.recipeId());
        assertEquals(PlanState.BLOCKED, decodedSummary.state());
        assertEquals(EnumSet.of(PlanningBlocker.MISSING_FUEL, PlanningBlocker.MISSING_STATION),
                decodedSummary.blockers());
        assertEquals(2, decodedSummary.stepCount());
        assertTrue(decoded.hasMultipleCounters());
    }

    @Test
    void detailsPayloadPreservesStepDataFingerprintAndQuantity() {
        KitchenPlanFingerprint fingerprint = new KitchenPlanFingerprint(1, 2, 3, 4, 5);
        ChefPlanStepView step = new ChefPlanStepView(
                id("recipe/stew_step"),
                new ItemStack(Items.RABBIT_STEW),
                List.of(new ItemStack(Items.CARROT, 2), new ItemStack(Items.POTATO)),
                List.of(new ItemStack(Items.RABBIT_STEW)),
                KitchenStationType.COOKING_POT,
                new ItemStack(Items.BOWL),
                new ItemStack(Items.COAL),
                KitchenUtilityRequirement.WATER,
                200,
                true
        );
        ChefPlanDetails details = new ChefPlanDetails(
                3,
                id("recipe/stew"),
                new ItemStack(Items.RABBIT_STEW, 2),
                PlanState.READY,
                4,
                8,
                List.of(step),
                EnumSet.noneOf(PlanningBlocker.class),
                fingerprint
        );

        ClientboundChefCounterDetailsPayload decoded = roundTrip(
                ClientboundChefCounterDetailsPayload.STREAM_CODEC,
                new ClientboundChefCounterDetailsPayload(12, Optional.of(details))
        );

        assertTrue(decoded.details().isPresent());
        ChefPlanDetails decodedDetails = decoded.details().orElseThrow();
        ChefPlanStepView decodedStep = decodedDetails.steps().getFirst();
        assertEquals(12, decoded.containerId());
        assertEquals(4, decodedDetails.quantity());
        assertEquals(8, decodedDetails.expectedOutputCount());
        assertEquals(fingerprint, decodedDetails.fingerprint());
        assertEquals(id("recipe/stew_step"), decodedStep.recipeId());
        assertEquals(Items.CARROT, decodedStep.inputs().getFirst().getItem());
        assertEquals(2, decodedStep.inputs().getFirst().getCount());
        assertEquals(KitchenStationType.COOKING_POT, decodedStep.stationType());
        assertEquals(KitchenUtilityRequirement.WATER, decodedStep.utilityRequirement());
        assertEquals(Items.BOWL, decodedStep.servingContainer().getItem());
        assertEquals(Items.COAL, decodedStep.fuel().getItem());
        assertEquals(200, decodedStep.processingTime());
        assertTrue(decodedStep.asynchronous());
    }

    @Test
    void queueResultPayloadPreservesSuccessAndBlockerList() {
        ClientboundChefCounterQueueResultPayload queued = roundTrip(
                ClientboundChefCounterQueueResultPayload.STREAM_CODEC,
                new ClientboundChefCounterQueueResultPayload(13, ChefQueueResultView.queued())
        );
        ClientboundChefCounterQueueResultPayload blocked = roundTrip(
                ClientboundChefCounterQueueResultPayload.STREAM_CODEC,
                new ClientboundChefCounterQueueResultPayload(13,
                        ChefQueueResultView.blocked(EnumSet.of(PlanningBlocker.OUTPUT_STORAGE_FULL,
                                PlanningBlocker.STATION_QUEUE_FULL)))
        );

        assertTrue(queued.result().isQueued());
        assertTrue(queued.result().blockers().isEmpty());
        assertFalse(blocked.result().isQueued());
        assertEquals(EnumSet.of(PlanningBlocker.OUTPUT_STORAGE_FULL, PlanningBlocker.STATION_QUEUE_FULL),
                blocked.result().blockers());
    }

    @Test
    void serverboundSelectPayloadPreservesMenuSelectionAndQuantity() {
        ServerboundChefCounterSelectPayload decoded = roundTrip(
                ServerboundChefCounterSelectPayload.STREAM_CODEC,
                new ServerboundChefCounterSelectPayload(14, 6, 3)
        );

        assertEquals(14, decoded.containerId());
        assertEquals(6, decoded.selectedIndex());
        assertEquals(3, decoded.requestedQuantity());
    }

    @Test
    void serverboundConfirmPayloadPreservesMenuSelectionQuantityAndFingerprint() {
        KitchenPlanFingerprint fingerprint = new KitchenPlanFingerprint(2, 4, 6, 8, 10);

        ServerboundChefCounterConfirmPayload decoded = roundTrip(
                ServerboundChefCounterConfirmPayload.STREAM_CODEC,
                new ServerboundChefCounterConfirmPayload(15, 7, 5, fingerprint)
        );

        assertEquals(15, decoded.containerId());
        assertEquals(7, decoded.selectedIndex());
        assertEquals(5, decoded.requestedQuantity());
        assertEquals(fingerprint, decoded.fingerprint());
    }

    @Test
    void bookmarkSyncPayloadPreservesMenuAndOrderedRecipeIds() {
        ClientboundChefCounterBookmarksPayload decoded = roundTrip(
                ClientboundChefCounterBookmarksPayload.STREAM_CODEC,
                new ClientboundChefCounterBookmarksPayload(16, List.of(id("recipe/first"), id("recipe/second")))
        );

        assertEquals(16, decoded.containerId());
        assertEquals(List.of(id("recipe/first"), id("recipe/second")), decoded.pinnedRecipeIds());
    }

    @Test
    void serverboundPinPayloadPreservesMenuRecipeAndPinnedState() {
        ServerboundChefCounterPinPayload decoded = roundTrip(
                ServerboundChefCounterPinPayload.STREAM_CODEC,
                new ServerboundChefCounterPinPayload(17, id("recipe/stew"), true)
        );

        assertEquals(17, decoded.containerId());
        assertEquals(id("recipe/stew"), decoded.recipeId());
        assertTrue(decoded.pin());
    }

    @Test
    void oversizedBookmarkListIsRejectedBeforeAllocation() {
        RegistryFriendlyByteBuf buf = newBuffer();
        buf.writeVarInt(18);
        buf.writeVarInt(ClientboundChefCounterBookmarksPayload.MAX_PINNED_RECIPES + 1);

        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> ClientboundChefCounterBookmarksPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void oversizedSummaryListIsRejectedBeforeAllocation() {
        RegistryFriendlyByteBuf buf = newBuffer();
        buf.writeVarInt(ChefCounterPayloadCodecs.MAX_SUMMARIES + 1);

        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> ChefCounterPayloadCodecs.readSummaries(buf));
    }

    @Test
    void oversizedDetailsStepListIsRejectedBeforeAllocation() {
        RegistryFriendlyByteBuf buf = newBuffer();
        buf.writeVarInt(16);
        buf.writeBoolean(true);
        writeDetailsHeader(buf);
        buf.writeVarInt(ChefCounterPayloadCodecs.MAX_PLAN_STEPS + 1);

        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> ClientboundChefCounterDetailsPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void oversizedStepStackListIsRejectedBeforeAllocation() {
        RegistryFriendlyByteBuf buf = newBuffer();
        buf.writeVarInt(17);
        buf.writeBoolean(true);
        writeDetailsHeader(buf);
        buf.writeVarInt(1);
        buf.writeResourceLocation(id("recipe/oversized_step"));
        writeStack(buf, new ItemStack(Items.BREAD));
        buf.writeVarInt(ChefCounterPayloadCodecs.MAX_STEP_STACKS + 1);

        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> ClientboundChefCounterDetailsPayload.STREAM_CODEC.decode(buf));
    }

    private static <T> T roundTrip(net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buf = newBuffer();
        codec.encode(buf, value);
        return codec.decode(buf);
    }

    private static RegistryFriendlyByteBuf newBuffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryLayer.createRegistryAccess().compositeAccess());
    }

    private static void writeDetailsHeader(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(0);
        buf.writeResourceLocation(id("recipe/header"));
        writeStack(buf, new ItemStack(Items.BREAD));
        buf.writeEnum(PlanState.READY);
        buf.writeVarInt(1);
        buf.writeVarInt(1);
    }

    private static void writeStack(RegistryFriendlyByteBuf buf, ItemStack stack) {
        buf.writeNbt(stack.saveOptional(buf.registryAccess()));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("delightful_kitchen", path);
    }
}
