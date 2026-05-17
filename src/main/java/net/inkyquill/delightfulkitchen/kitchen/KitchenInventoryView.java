package net.inkyquill.delightfulkitchen.kitchen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class KitchenInventoryView implements Container {
    public static final int VISIBLE_SLOTS = 54;

    private final KitchenInventoryService service;
    private final BlockPos terminalPos;
    private NonNullList<ItemStack> visibleStacks;

    public KitchenInventoryView(KitchenInventoryService service, BlockPos terminalPos) {
        this.service = service;
        this.terminalPos = terminalPos.immutable();
        refresh();
    }

    @Override
    public int getContainerSize() {
        return VISIBLE_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : visibleStacks) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (!isValidSlot(slot)) {
            return ItemStack.EMPTY;
        }
        return visibleStacks.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (!isValidSlot(slot) || amount <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack visible = visibleStacks.get(slot);
        if (visible.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack extracted = service.extract(visible, Math.min(amount, visible.getCount()), false);
        if (!extracted.isEmpty()) {
            refresh();
        }
        return extracted;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (!isValidSlot(slot)) {
            return ItemStack.EMPTY;
        }

        ItemStack visible = visibleStacks.get(slot);
        if (visible.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack extracted = service.extract(visible, visible.getCount(), false);
        if (!extracted.isEmpty()) {
            refresh();
        }
        return extracted;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        throw new UnsupportedOperationException("KitchenInventoryView is read-only; route inserts through KitchenInventoryService");
    }

    @Override
    public void setChanged() {
        refresh();
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(
                terminalPos.getX() + 0.5,
                terminalPos.getY() + 0.5,
                terminalPos.getZ() + 0.5) <= 4096.0;
    }

    @Override
    public void clearContent() {
        for (ItemStack stack : visibleStacks) {
            if (!stack.isEmpty()) {
                service.extract(stack, stack.getCount(), false);
            }
        }
        refresh();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return Math.min(64, stack.getMaxStackSize());
    }

    private void refresh() {
        visibleStacks = service.visibleStacks(VISIBLE_SLOTS);
    }

    private static boolean isValidSlot(int slot) {
        return slot >= 0 && slot < VISIBLE_SLOTS;
    }

}
