package net.inkyquill.cfbdelight.kitchen;

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
    private NonNullList<ItemStack> syncedStacks;

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

        ItemStack extracted = service.extract(visible, amount, false);
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
        if (!isValidSlot(slot) || stack.isEmpty()) {
            refresh();
            return;
        }

        service.insert(stack, false);
        refresh();
    }

    @Override
    public void setChanged() {
        reconcileVisibleChanges();
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
        return isValidSlot(slot) && !stack.isEmpty() && service.insert(stack, true).getCount() < stack.getCount();
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    private void refresh() {
        visibleStacks = service.visibleStacks(VISIBLE_SLOTS);
        syncedStacks = copyStacks(visibleStacks);
    }

    private static boolean isValidSlot(int slot) {
        return slot >= 0 && slot < VISIBLE_SLOTS;
    }

    private void reconcileVisibleChanges() {
        if (syncedStacks == null) {
            return;
        }

        for (int slot = 0; slot < VISIBLE_SLOTS; slot++) {
            ItemStack before = syncedStacks.get(slot);
            ItemStack after = visibleStacks.get(slot);
            if (ItemStack.isSameItemSameComponents(before, after)) {
                int difference = after.getCount() - before.getCount();
                if (difference > 0) {
                    service.insert(after.copyWithCount(difference), false);
                } else if (difference < 0) {
                    service.extract(before, -difference, false);
                }
                continue;
            }

            if (!before.isEmpty()) {
                service.extract(before, before.getCount(), false);
            }
            if (!after.isEmpty()) {
                service.insert(after, false);
            }
        }
    }

    private static NonNullList<ItemStack> copyStacks(NonNullList<ItemStack> stacks) {
        NonNullList<ItemStack> copies = NonNullList.withSize(stacks.size(), ItemStack.EMPTY);
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty()) {
                copies.set(i, stack.copy());
            }
        }
        return copies;
    }
}
