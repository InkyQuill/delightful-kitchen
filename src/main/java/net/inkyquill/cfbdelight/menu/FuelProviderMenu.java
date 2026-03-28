package net.inkyquill.cfbdelight.menu;

import net.inkyquill.cfbdelight.block.entity.FuelProviderBlockEntity;
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
 * Menu for the Fuel Provider.
 * <p>
 * 9 fuel slots in a row (y=35), player inventory below.
 */
public class FuelProviderMenu extends AbstractContainerMenu {

    private final Container container;

    /** Network (client-side) constructor. */
    public FuelProviderMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
                new SimpleContainer(FuelProviderBlockEntity.SLOT_COUNT),
                ContainerLevelAccess.NULL);
    }

    /** Server-side constructor. */
    public FuelProviderMenu(int containerId, Inventory playerInventory,
                            Container container, ContainerLevelAccess access) {
        super(ModMenuTypes.FUEL_PROVIDER.get(), containerId);
        checkContainerSize(container, FuelProviderBlockEntity.SLOT_COUNT);
        this.container = container;

        container.startOpen(playerInventory.player);

        // 9 fuel slots in a row
        for (int i = 0; i < FuelProviderBlockEntity.SLOT_COUNT; i++) {
            addSlot(new Slot(container, i, 8 + i * 18, 35));
        }

        // Player inventory (y=83, 101, 119)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 83 + row * 18));
            }
        }
        // Hotbar (y=141)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 141));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        int containerEnd = FuelProviderBlockEntity.SLOT_COUNT;
        int invStart = containerEnd;
        int invEnd = invStart + 36;

        if (index < containerEnd) {
            if (!moveItemStackTo(stack, invStart, invEnd, true)) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(stack, 0, containerEnd, false)) return ItemStack.EMPTY;
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
