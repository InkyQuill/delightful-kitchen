package net.inkyquill.cfbdelight.menu;

import net.inkyquill.cfbdelight.kitchen.KitchenInventoryService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Menu for the Cabinet of Holding.
 * <p>
 * Layout: 6 rows x 9 columns (54 visible kitchen or fallback slots) then
 * player inventory (3x9 + hotbar).
 */
public class CabinetOfHoldingMenu extends AbstractContainerMenu {

    private static final int ROWS = 6;
    private static final int COLS = 9;
    private static final int CONTAINER_SLOTS = ROWS * COLS; // 54

    private final Container container;
    @Nullable private final KitchenInventoryService kitchenService;

    /** Client-side constructor (called from FriendlyByteBuf factory). */
    public CabinetOfHoldingMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
                new SimpleContainer(CONTAINER_SLOTS),
                ContainerLevelAccess.NULL);
    }

    public CabinetOfHoldingMenu(int containerId, Inventory playerInventory,
                                Container container, ContainerLevelAccess access) {
        this(containerId, playerInventory, container, null, access);
    }

    public CabinetOfHoldingMenu(int containerId, Inventory playerInventory,
                                Container container, KitchenInventoryService kitchenService,
                                ContainerLevelAccess access) {
        super(ModMenuTypes.CABINET_OF_HOLDING.get(), containerId);
        checkContainerSize(container, CONTAINER_SLOTS);
        this.container = container;
        this.kitchenService = kitchenService;
        container.startOpen(playerInventory.player);

        // 6 rows x 9 cabinet slots (y=18, 36, 54, 72, 90, 108)
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int slot = col + row * COLS;
                int x = 8 + col * 18;
                int y = 18 + row * 18;
                addSlot(kitchenService == null
                        ? new Slot(container, slot, x, y)
                        : new VirtualKitchenSlot(container, slot, x, y));
            }
        }

        // Player inventory (y=140, 158, 176)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }

        // Hotbar (y=198)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 198));
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (kitchenService != null
                && slotId >= 0
                && slotId < CONTAINER_SLOTS
                && (clickType == ClickType.SWAP || clickType == ClickType.CLONE)) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;

        if (kitchenService != null) {
            return quickMoveKitchenStack(player, index, slot);
        }

        ItemStack stack = slot.getItem();
        result = stack.copy();

        int invStart = CONTAINER_SLOTS;
        int invEnd   = invStart + 36;

        if (index < CONTAINER_SLOTS) {
            // Cabinet -> player inventory
            if (!moveItemStackTo(stack, invStart, invEnd, true)) return ItemStack.EMPTY;
        } else {
            // Player inventory -> cabinet
            if (!moveItemStackTo(stack, 0, CONTAINER_SLOTS, false)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    private ItemStack quickMoveKitchenStack(Player player, int index, Slot slot) {
        int invStart = CONTAINER_SLOTS;
        int invEnd = invStart + 36;

        if (index < CONTAINER_SLOTS) {
            ItemStack visible = slot.getItem();
            if (visible.isEmpty()) {
                return ItemStack.EMPTY;
            }

            int amount = Math.min(visible.getCount(), getMoveCapacityToSlots(visible, invStart, invEnd));
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }

            ItemStack extracted = kitchenService.extract(visible, amount, false);
            if (extracted.isEmpty()) {
                return ItemStack.EMPTY;
            }

            ItemStack remaining = extracted.copy();
            if (!moveItemStackTo(remaining, invStart, invEnd, true)) {
                ItemStack rejected = kitchenService.insert(extracted, false);
                return rejected.isEmpty() ? extracted.copy() : ItemStack.EMPTY;
            }

            container.setChanged();
            slot.setChanged();
            return extracted.copy();
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        ItemStack remainder = kitchenService.insert(stack, false);
        int accepted = stack.getCount() - remainder.getCount();
        if (accepted <= 0) {
            return ItemStack.EMPTY;
        }

        stack.shrink(accepted);
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        container.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    private int getMoveCapacityToSlots(ItemStack stack, int startIndex, int endIndex) {
        int capacity = 0;
        for (int slotIndex = startIndex; slotIndex < endIndex; slotIndex++) {
            Slot target = slots.get(slotIndex);
            ItemStack existing = target.getItem();
            if (!existing.isEmpty()
                    && ItemStack.isSameItemSameComponents(existing, stack)
                    && target.mayPlace(stack)) {
                capacity += Math.max(0, Math.min(target.getMaxStackSize(existing), existing.getMaxStackSize())
                        - existing.getCount());
            }
        }

        for (int slotIndex = startIndex; slotIndex < endIndex; slotIndex++) {
            Slot target = slots.get(slotIndex);
            if (target.getItem().isEmpty() && target.mayPlace(stack)) {
                capacity += Math.min(target.getMaxStackSize(stack), stack.getMaxStackSize());
            }
        }
        return capacity;
    }

    private static final class VirtualKitchenSlot extends Slot {
        private VirtualKitchenSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return true;
        }

        @Override
        public boolean allowModification(Player player) {
            return false;
        }

        @Override
        public ItemStack remove(int amount) {
            return ItemStack.EMPTY;
        }

        @Override
        public Optional<ItemStack> tryRemove(int count, int decrement, Player player) {
            return Optional.empty();
        }

        @Override
        public ItemStack safeTake(int count, int decrement, Player player) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack safeInsert(ItemStack stack) {
            return stack;
        }

        @Override
        public ItemStack safeInsert(ItemStack stack, int increment) {
            return stack;
        }

        @Override
        public void setByPlayer(ItemStack stack) {
        }

        @Override
        public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
        }

        @Override
        public void set(ItemStack stack) {
        }

        @Override
        public boolean isFake() {
            return true;
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
