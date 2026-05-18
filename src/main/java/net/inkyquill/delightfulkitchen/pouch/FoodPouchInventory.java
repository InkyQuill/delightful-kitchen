package net.inkyquill.delightfulkitchen.pouch;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record FoodPouchInventory(List<ItemStack> stacks) {
    public static final Codec<FoodPouchInventory> CODEC = StoredStack.CODEC
            .listOf()
            .xmap(stored -> new FoodPouchInventory(stored.stream()
                            .map(StoredStack::toStack)
                            .toList()),
                    inventory -> inventory.stacks.stream()
                            .map(StoredStack::fromStack)
                            .toList());

    public static final StreamCodec<RegistryFriendlyByteBuf, FoodPouchInventory> STREAM_CODEC =
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()).map(
                    FoodPouchInventory::new,
                    FoodPouchInventory::stacks
            );

    public FoodPouchInventory {
        Objects.requireNonNull(stacks, "stacks");
        stacks = stacks.stream()
                .map(FoodPouchInventory::normalizedStoredCopy)
                .toList();
    }

    public static FoodPouchInventory empty(int slots) {
        if (slots <= 0) {
            return new FoodPouchInventory(List.of());
        }
        return new FoodPouchInventory(Collections.nCopies(slots, ItemStack.EMPTY));
    }

    @Override
    public List<ItemStack> stacks() {
        return stacks.stream().map(ItemStack::copy).toList();
    }

    public int size() {
        return stacks.size();
    }

    public boolean isValidSlot(int slot) {
        return slot >= 0 && slot < stacks.size();
    }

    public ItemStack get(int slot) {
        if (!isValidSlot(slot)) {
            return ItemStack.EMPTY;
        }
        return stacks.get(slot).copy();
    }

    public FoodPouchSlotView viewSlot(int slot, FoodPouchTier tier) {
        ItemStack stack = get(slot);
        int limit = stack.isEmpty() ? FoodPouchTier.MAX_FOOD_STACK_SIZE : tier.slotLimit(stack);
        return new FoodPouchSlotView(slot, stack, limit);
    }

    public List<FoodPouchSlotView> viewSlots(FoodPouchTier tier) {
        List<FoodPouchSlotView> views = new ArrayList<>(stacks.size());
        for (int slot = 0; slot < stacks.size(); slot++) {
            views.add(viewSlot(slot, tier));
        }
        return List.copyOf(views);
    }

    public int usedSlots() {
        int used = 0;
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                used++;
            }
        }
        return used;
    }

    public FoodPouchInventory withSlot(int slot, ItemStack stack) {
        if (!isValidSlot(slot)) {
            return this;
        }
        List<ItemStack> copy = new ArrayList<>(stacks());
        copy.set(slot, normalizedStoredCopy(stack));
        return new FoodPouchInventory(copy);
    }

    static ItemStack stackAt(FoodPouchInventory inventory, int slot) {
        if (!inventory.isValidSlot(slot)) {
            return ItemStack.EMPTY;
        }
        return inventory.stacks.get(slot);
    }

    static FoodPouchInventory fromInternalStacks(List<ItemStack> stacks) {
        return new FoodPouchInventory(stacks);
    }

    private static ItemStack normalizedStoredCopy(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getCount() <= 0) {
            return ItemStack.EMPTY;
        }
        return stack.copy();
    }

    private record StoredStack(Holder<Item> item, int count, DataComponentPatch components) {
        private static final Codec<StoredStack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("id").forGetter(StoredStack::item),
                Codec.INT.fieldOf("count").forGetter(StoredStack::count),
                DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                        .forGetter(StoredStack::components)
        ).apply(instance, StoredStack::new));

        private static StoredStack fromStack(ItemStack stack) {
            if (stack.isEmpty()) {
                return new StoredStack(Items.AIR.builtInRegistryHolder(), 0, DataComponentPatch.EMPTY);
            }
            return new StoredStack(stack.getItemHolder(), stack.getCount(), stack.getComponentsPatch());
        }

        private ItemStack toStack() {
            if (count <= 0 || item.value() == Items.AIR) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item, count, components);
        }
    }
}
