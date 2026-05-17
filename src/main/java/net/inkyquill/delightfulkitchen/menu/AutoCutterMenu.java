package net.inkyquill.delightfulkitchen.menu;

import net.inkyquill.delightfulkitchen.block.entity.AutoCutterBlockEntity;
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
 * Menu for the Auto Cutter.
 * <p>
 * Layout (176×166, matches {@code auto_cutter.png}):
 * <pre>
 *  [input  ]    [out0][out1]
 *               [out2][out3]
 * ─── player inventory ───
 * </pre>
 */
public class AutoCutterMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerLevelAccess access;

    public AutoCutterMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
                new SimpleContainer(AutoCutterBlockEntity.SLOT_COUNT),
                ContainerLevelAccess.NULL);
    }

    public AutoCutterMenu(int containerId, Inventory playerInventory,
                          Container container, ContainerLevelAccess access) {
        super(ModMenuTypes.AUTO_CUTTER.get(), containerId);
        checkContainerSize(container, AutoCutterBlockEntity.SLOT_COUNT);
        this.container = container;
        this.access = access;

        container.startOpen(playerInventory.player);

        // Input slot (left side)
        addSlot(new Slot(container, AutoCutterBlockEntity.SLOT_INPUT, 26, 35));

        // 4 output slots (2×2 grid, right side) – read-only
        addSlot(new Slot(container, 1, 98, 26) { @Override public boolean mayPlace(ItemStack s) { return false; } });
        addSlot(new Slot(container, 2, 116, 26) { @Override public boolean mayPlace(ItemStack s) { return false; } });
        addSlot(new Slot(container, 3, 98, 44) { @Override public boolean mayPlace(ItemStack s) { return false; } });
        addSlot(new Slot(container, 4, 116, 44) { @Override public boolean mayPlace(ItemStack s) { return false; } });

        // Player inventory (y=84, 102, 120) + hotbar (y=142)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        int containerEnd = AutoCutterBlockEntity.SLOT_COUNT;
        int invStart = containerEnd;
        int invEnd = invStart + 36;

        if (index < containerEnd) {
            if (!moveItemStackTo(stack, invStart, invEnd, true)) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY; // only input slot accepts items
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
