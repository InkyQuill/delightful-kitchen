package net.inkyquill.delightfulkitchen.menu;

import net.inkyquill.delightfulkitchen.block.entity.FreezerBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Menu for the Freezer's output-only inventory. */
public class FreezerMenu extends AbstractContainerMenu {
    private static final int DATA_SIZE = 4;

    private static class OutputSlot extends Slot {
        OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    private final Container container;
    private final ContainerData data;

    public FreezerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
                new SimpleContainer(FreezerBlockEntity.OUTPUT_SLOTS),
                new SimpleContainerData(DATA_SIZE),
                ContainerLevelAccess.NULL);
    }

    public FreezerMenu(int containerId, Inventory playerInventory,
                       Container container, ContainerData data, ContainerLevelAccess access) {
        super(ModMenuTypes.FREEZER.get(), containerId);
        checkContainerSize(container, FreezerBlockEntity.OUTPUT_SLOTS);
        checkContainerDataCount(data, DATA_SIZE);
        this.container = container;
        this.data = data;

        container.startOpen(playerInventory.player);

        for (int i = 0; i < FreezerBlockEntity.OUTPUT_SLOTS; i++) {
            addSlot(new OutputSlot(container, i, 8 + i * 18, 35));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 83 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 141));
        }

        addDataSlots(data);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return result;
        }

        ItemStack stack = slot.getItem();
        result = stack.copy();

        int containerEnd = FreezerBlockEntity.OUTPUT_SLOTS;
        int invStart = containerEnd;
        int invEnd = invStart + 36;

        if (index < containerEnd) {
            if (!moveItemStackTo(stack, invStart, invEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != 0 || !(container instanceof FreezerBlockEntity freezer)) {
            return false;
        }
        freezer.cyclePassiveMode();
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    public FreezerBlockEntity.PassiveMode passiveMode() {
        return FreezerBlockEntity.PassiveMode.values()[Math.floorMod(data.get(0),
                FreezerBlockEntity.PassiveMode.values().length)];
    }

    public int progressTicks() {
        return data.get(1);
    }

    public boolean nextProductIce() {
        return data.get(2) != 0;
    }

    public boolean isProducing() {
        return data.get(3) != 0;
    }

    public float progress() {
        return (float) progressTicks() / FreezerBlockEntity.PASSIVE_CYCLE_TICKS;
    }
}
