package net.inkyquill.delightfulkitchen.component;

import com.mojang.serialization.Codec;
import net.inkyquill.delightfulkitchen.DelightfulKitchen;
import net.inkyquill.delightfulkitchen.pouch.FeedThreshold;
import net.inkyquill.delightfulkitchen.pouch.FoodPouchInventory;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.IntFunction;

public final class ModDataComponents {
    private static final IntFunction<FeedThreshold> FEED_THRESHOLD_BY_ID =
            ByIdMap.continuous(FeedThreshold::ordinal, FeedThreshold.values(), ByIdMap.OutOfBoundsStrategy.CLAMP);

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, DelightfulKitchen.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FoodPouchInventory>> POUCH_INVENTORY =
            DATA_COMPONENT_TYPES.register("pouch_inventory", () -> DataComponentType.<FoodPouchInventory>builder()
                    .persistent(FoodPouchInventory.CODEC)
                    .networkSynchronized(FoodPouchInventory.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FeedThreshold>> FEED_AT_HUNGER_LEVEL =
            DATA_COMPONENT_TYPES.register("feed_at_hunger_level", () -> DataComponentType.<FeedThreshold>builder()
                    .persistent(StringRepresentable.fromEnum(FeedThreshold::values))
                    .networkSynchronized(ByteBufCodecs.idMapper(FEED_THRESHOLD_BY_ID, FeedThreshold::ordinal))
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> FEED_WHEN_HURT =
            DATA_COMPONENT_TYPES.register("feed_when_hurt", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> POUCH_FEED_COOLDOWN =
            DATA_COMPONENT_TYPES.register("pouch_feed_cooldown", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    private ModDataComponents() {
    }
}
