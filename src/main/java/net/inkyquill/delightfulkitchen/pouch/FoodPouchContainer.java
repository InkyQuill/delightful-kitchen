package net.inkyquill.delightfulkitchen.pouch;

import net.inkyquill.delightfulkitchen.component.ModDataComponents;
import net.inkyquill.delightfulkitchen.item.FoodPouchItem;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Menu-facing mutable view over a pouch stack's immutable inventory component.
 */
public class FoodPouchContainer implements Container {
    private final ItemStack pouchStack;
    private final FoodPouchTier tier;
    private final NonNullList<ItemStack> items;

    public FoodPouchContainer(ItemStack pouchStack) {
        this(pouchStack, pouchStack.isEmpty() ? FoodPouchTier.BASIC : FoodPouchItem.tierOf(pouchStack));
    }

    public FoodPouchContainer(ItemStack pouchStack, FoodPouchTier fallbackTier) {
        this.pouchStack = pouchStack;
        this.tier = pouchStack.isEmpty() ? fallbackTier : FoodPouchItem.tierOf(pouchStack);
        this.items = NonNullList.withSize(tier.slots(), ItemStack.EMPTY);
        loadFromStack();
    }

    public FoodPouchTier tier() {
        return tier;
    }

    public ItemStack pouchStack() {
        return pouchStack;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        if (!validSlot(slot)) {
            return ItemStack.EMPTY;
        }
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (!validSlot(slot)) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        persist();
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (!validSlot(slot)) {
            return;
        }
        ItemStack normalized = stack.copy();
        int limit = getMaxStackSize(normalized);
        if (normalized.getCount() > limit) {
            normalized.setCount(limit);
        }
        items.set(slot, normalized);
        setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return FoodPouchTier.MAX_FOOD_STACK_SIZE;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return tier.slotLimit(stack);
    }

    @Override
    public void setChanged() {
        persist();
    }

    @Override
    public boolean stillValid(Player player) {
        return !pouchStack.isEmpty();
    }

    @Override
    public void clearContent() {
        items.replaceAll(ignored -> ItemStack.EMPTY);
        setChanged();
    }

    private void loadFromStack() {
        if (pouchStack.isEmpty()) {
            return;
        }
        FoodPouchInventory inventory = FoodPouchItem.mutableInventory(pouchStack);
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = slot < inventory.size() ? inventory.get(slot) : ItemStack.EMPTY;
            ItemStack normalized = stack.copy();
            int limit = getMaxStackSize(normalized);
            if (normalized.getCount() > limit) {
                normalized.setCount(limit);
            }
            items.set(slot, normalized);
        }
    }

    private void persist() {
        if (pouchStack.isEmpty()) {
            return;
        }
        FoodPouchInventory inventory = FoodPouchInventory.empty(items.size());
        for (int slot = 0; slot < items.size(); slot++) {
            inventory = inventory.withSlot(slot, items.get(slot));
        }
        pouchStack.set(ModDataComponents.POUCH_INVENTORY.get(), inventory);
    }

    private boolean validSlot(int slot) {
        return slot >= 0 && slot < items.size();
    }
}
