package net.inkyquill.delightfulkitchen.network.payload;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.inkyquill.delightfulkitchen.kitchen.execution.KitchenPlanFingerprint;
import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanState;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;
import net.inkyquill.delightfulkitchen.kitchen.utility.KitchenUtilityRequirement;
import net.inkyquill.delightfulkitchen.menu.chef.ChefPlanDetails;
import net.inkyquill.delightfulkitchen.menu.chef.ChefPlanStepView;
import net.inkyquill.delightfulkitchen.menu.chef.ChefQueueResultView;
import net.inkyquill.delightfulkitchen.menu.chef.ChefRecipeSummary;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

final class ChefCounterPayloadCodecs {
    static final int MAX_SUMMARIES = 512;
    static final int MAX_PLAN_STEPS = 128;
    static final int MAX_STEP_STACKS = 32;

    static final StreamCodec<RegistryFriendlyByteBuf, ChefRecipeSummary> SUMMARY =
            StreamCodec.of(ChefCounterPayloadCodecs::writeSummary, ChefCounterPayloadCodecs::readSummary);
    static final StreamCodec<RegistryFriendlyByteBuf, ChefPlanDetails> DETAILS =
            StreamCodec.of(ChefCounterPayloadCodecs::writeDetails, ChefCounterPayloadCodecs::readDetails);
    static final StreamCodec<RegistryFriendlyByteBuf, ChefQueueResultView> QUEUE_RESULT =
            StreamCodec.of(ChefCounterPayloadCodecs::writeQueueResult, ChefCounterPayloadCodecs::readQueueResult);
    static final StreamCodec<RegistryFriendlyByteBuf, KitchenPlanFingerprint> FINGERPRINT =
            StreamCodec.of(ChefCounterPayloadCodecs::writeFingerprint, ChefCounterPayloadCodecs::readFingerprint);
    private static final StreamCodec<RegistryFriendlyByteBuf, ChefPlanStepView> STEP =
            StreamCodec.of(ChefCounterPayloadCodecs::writeStep, ChefCounterPayloadCodecs::readStep);
    private static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> ITEM_STACK =
            StreamCodec.of(ChefCounterPayloadCodecs::writeItemStack, ChefCounterPayloadCodecs::readItemStack);

    private ChefCounterPayloadCodecs() {
    }

    static void writeSummaries(RegistryFriendlyByteBuf buf, List<ChefRecipeSummary> summaries) {
        writeList(buf, summaries, MAX_SUMMARIES, SUMMARY);
    }

    static List<ChefRecipeSummary> readSummaries(RegistryFriendlyByteBuf buf) {
        return readList(buf, MAX_SUMMARIES, SUMMARY);
    }

    static void writeOptionalDetails(RegistryFriendlyByteBuf buf, Optional<ChefPlanDetails> details) {
        buf.writeBoolean(details.isPresent());
        details.ifPresent(value -> DETAILS.encode(buf, value));
    }

    static Optional<ChefPlanDetails> readOptionalDetails(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean() ? Optional.of(DETAILS.decode(buf)) : Optional.empty();
    }

    private static void writeSummary(RegistryFriendlyByteBuf buf, ChefRecipeSummary summary) {
        buf.writeVarInt(summary.index());
        buf.writeResourceLocation(summary.recipeId());
        writeItemStack(buf, summary.output());
        buf.writeEnum(summary.state());
        buf.writeEnumSet(summary.blockers(), PlanningBlocker.class);
        buf.writeVarInt(summary.stepCount());
    }

    private static ChefRecipeSummary readSummary(RegistryFriendlyByteBuf buf) {
        int index = buf.readVarInt();
        ResourceLocation recipeId = buf.readResourceLocation();
        ItemStack output = readItemStack(buf);
        PlanState state = buf.readEnum(PlanState.class);
        EnumSet<PlanningBlocker> blockers = buf.readEnumSet(PlanningBlocker.class);
        int stepCount = buf.readVarInt();
        return new ChefRecipeSummary(index, recipeId, output, state, blockers, stepCount);
    }

    private static void writeDetails(RegistryFriendlyByteBuf buf, ChefPlanDetails details) {
        buf.writeVarInt(details.selectedIndex());
        buf.writeResourceLocation(details.targetRecipeId());
        writeItemStack(buf, details.output());
        buf.writeEnum(details.state());
        buf.writeVarInt(details.quantity());
        buf.writeVarInt(details.expectedOutputCount());
        writeList(buf, details.steps(), MAX_PLAN_STEPS, STEP);
        buf.writeEnumSet(details.blockers(), PlanningBlocker.class);
        writeFingerprint(buf, details.fingerprint());
    }

    private static ChefPlanDetails readDetails(RegistryFriendlyByteBuf buf) {
        int selectedIndex = buf.readVarInt();
        ResourceLocation targetRecipeId = buf.readResourceLocation();
        ItemStack output = readItemStack(buf);
        PlanState state = buf.readEnum(PlanState.class);
        int quantity = buf.readVarInt();
        int expectedOutputCount = buf.readVarInt();
        List<ChefPlanStepView> steps = readList(buf, MAX_PLAN_STEPS, STEP);
        EnumSet<PlanningBlocker> blockers = buf.readEnumSet(PlanningBlocker.class);
        KitchenPlanFingerprint fingerprint = readFingerprint(buf);
        return new ChefPlanDetails(selectedIndex, targetRecipeId, output, state, quantity, expectedOutputCount, steps,
                blockers, fingerprint);
    }

    private static void writeStep(RegistryFriendlyByteBuf buf, ChefPlanStepView step) {
        buf.writeResourceLocation(step.recipeId());
        writeItemStack(buf, step.primaryOutput());
        writeList(buf, step.inputs(), MAX_STEP_STACKS, ITEM_STACK);
        writeList(buf, step.outputs(), MAX_STEP_STACKS, ITEM_STACK);
        buf.writeEnum(step.stationType());
        writeItemStack(buf, step.servingContainer());
        writeItemStack(buf, step.fuel());
        buf.writeEnum(step.utilityRequirement());
        buf.writeVarInt(step.processingTime());
        buf.writeBoolean(step.asynchronous());
    }

    private static ChefPlanStepView readStep(RegistryFriendlyByteBuf buf) {
        ResourceLocation recipeId = buf.readResourceLocation();
        ItemStack primaryOutput = readItemStack(buf);
        List<ItemStack> inputs = readList(buf, MAX_STEP_STACKS, ITEM_STACK);
        List<ItemStack> outputs = readList(buf, MAX_STEP_STACKS, ITEM_STACK);
        KitchenStationType stationType = buf.readEnum(KitchenStationType.class);
        ItemStack servingContainer = readItemStack(buf);
        ItemStack fuel = readItemStack(buf);
        KitchenUtilityRequirement utilityRequirement = buf.readEnum(KitchenUtilityRequirement.class);
        int processingTime = buf.readVarInt();
        boolean asynchronous = buf.readBoolean();
        return new ChefPlanStepView(recipeId, primaryOutput, inputs, outputs, stationType, servingContainer, fuel,
                utilityRequirement, processingTime, asynchronous);
    }

    private static void writeQueueResult(RegistryFriendlyByteBuf buf, ChefQueueResultView result) {
        buf.writeBoolean(result.isQueued());
        buf.writeEnumSet(result.blockers(), PlanningBlocker.class);
    }

    private static ChefQueueResultView readQueueResult(RegistryFriendlyByteBuf buf) {
        boolean isQueued = buf.readBoolean();
        EnumSet<PlanningBlocker> blockers = buf.readEnumSet(PlanningBlocker.class);
        return isQueued ? ChefQueueResultView.queued() : ChefQueueResultView.blocked(blockers);
    }

    private static void writeFingerprint(RegistryFriendlyByteBuf buf, KitchenPlanFingerprint fingerprint) {
        buf.writeVarInt(fingerprint.floorCount());
        buf.writeVarInt(fingerprint.memberCount());
        buf.writeVarInt(fingerprint.stackCount());
        buf.writeVarInt(fingerprint.totalItemCount());
        buf.writeInt(fingerprint.itemIdentityHash());
    }

    private static KitchenPlanFingerprint readFingerprint(RegistryFriendlyByteBuf buf) {
        return new KitchenPlanFingerprint(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readInt());
    }

    private static <T> void writeList(RegistryFriendlyByteBuf buf, List<T> values, int maxSize,
                                      StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        if (values.size() > maxSize) {
            throw new EncoderException("Cannot encode list with " + values.size() + " entries; maximum is " + maxSize);
        }
        buf.writeVarInt(values.size());
        for (T value : values) {
            codec.encode(buf, value);
        }
    }

    private static <T> List<T> readList(RegistryFriendlyByteBuf buf, int maxSize,
                                        StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        int size = buf.readVarInt();
        if (size < 0 || size > maxSize) {
            throw new DecoderException("Cannot decode list with " + size + " entries; maximum is " + maxSize);
        }
        List<T> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(codec.decode(buf));
        }
        return values;
    }

    private static void writeItemStack(RegistryFriendlyByteBuf buf, ItemStack stack) {
        buf.writeNbt(stack.saveOptional(buf.registryAccess()));
    }

    private static ItemStack readItemStack(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return tag == null ? ItemStack.EMPTY : ItemStack.parseOptional(buf.registryAccess(), tag);
    }
}
