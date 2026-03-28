package net.inkyquill.cfbdelight.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Cabinet of Holding.
 * <p>
 * Layout: 6 rows x 9 columns (54 slots) then player inventory (3x9 + hotbar).
 * Mirrors the layout of a vanilla large chest.
 */
public class CabinetOfHoldingMenu extends AbstractContainerMenu {

    private static final int ROWS = 6;
    private static final int COLS = 9;
    private static final int CONTAINER_SLOTS = ROWS * COLS; // 54

    private final Container container;

    /** Client-side constructor (called from FriendlyByteBuf factory). */
    public CabinetOfHoldingMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
                new SimpleContainer(CONTAINER_SLOTS),
                ContainerLevelAccess.NULL);
    }

    public CabinetOfHoldingMenu(int containerId, Inventory playerInventory,
                                Container container, ContainerLevelAccess access) {
        super(ModMenuTypes.CABINET_OF_HOLDING.get(), containerId);
        checkContainerSize(container, CONTAINER_SLOTS);
        this.container = container;
        container.startOpen(playerInventory.player);

        // 6 rows x 9 cabinet slots (y=18, 36, 54, 72, 90, 108)
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                addSlot(new Slot(container, col + row * COLS, 8 + col * 18, 18 + row * 18));
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
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;

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
