package net.inkyquill.delightfulkitchen.menu;

import net.inkyquill.delightfulkitchen.block.entity.ContainerStorageBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Menu for the Container Storage block.
 * <p>
 * Slot layout (176×196, matches {@code container_storage.png}):
 * <pre>
 *  [b0][b1][b2][b3][b4][b5][b6][b7]     ← 8 bowl slots      (y=18)
 *  [t0][t1][t2][t3][t4][t5][t6][t7]     ← 8 bottle slots    (y=36)
 *  [g0][g1][g2][g3][g4][g5][g6][g7][g8] ← 9 general slots   (y=54)
 * ──────────── player inventory ────────────
 * </pre>
 */
public class ContainerStorageMenu extends AbstractContainerMenu {

    // Bowl-only filter slot
    private static class BowlSlot extends Slot {
        BowlSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }
        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(Items.BOWL);
        }
    }

    // Bottle-only filter slot
    private static class BottleSlot extends Slot {
        BottleSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }
        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(Items.GLASS_BOTTLE);
        }
    }

    private final Container container;

    public ContainerStorageMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
                new SimpleContainer(ContainerStorageBlockEntity.SLOT_COUNT),
                ContainerLevelAccess.NULL);
    }

    public ContainerStorageMenu(int containerId, Inventory playerInventory,
                                Container container, ContainerLevelAccess access) {
        super(ModMenuTypes.CONTAINER_STORAGE.get(), containerId);
        checkContainerSize(container, ContainerStorageBlockEntity.SLOT_COUNT);
        this.container = container;

        container.startOpen(playerInventory.player);

        // 8 bowl slots (y=28)
        for (int i = 0; i < ContainerStorageBlockEntity.BOWL_SLOTS; i++) {
            addSlot(new BowlSlot(container,
                    ContainerStorageBlockEntity.SLOT_BOWL_START + i,
                    8 + i * 18, 28));
        }

        // 8 bottle slots (y=56)
        for (int i = 0; i < ContainerStorageBlockEntity.BOTTLE_SLOTS; i++) {
            addSlot(new BottleSlot(container,
                    ContainerStorageBlockEntity.SLOT_BOTTLE_START + i,
                    8 + i * 18, 56));
        }

        // 9 general container slots (y=84)
        for (int i = 0; i < ContainerStorageBlockEntity.GENERAL_SLOTS; i++) {
            addSlot(new Slot(container,
                    ContainerStorageBlockEntity.SLOT_GENERAL_START + i,
                    8 + i * 18, 84));
        }

        // Player inventory (y=114, 132, 150) + hotbar (y=172)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 114 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 172));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        int containerEnd = ContainerStorageBlockEntity.SLOT_COUNT;
        int invStart = containerEnd;
        int invEnd = invStart + 36;

        if (index < containerEnd) {
            if (!moveItemStackTo(stack, invStart, invEnd, true)) return ItemStack.EMPTY;
        } else {
            // Try typed slots first (bowl, bottle), then general
            if (stack.is(Items.BOWL)) {
                if (!moveItemStackTo(stack,
                        ContainerStorageBlockEntity.SLOT_BOWL_START,
                        ContainerStorageBlockEntity.SLOT_BOWL_START + ContainerStorageBlockEntity.BOWL_SLOTS,
                        false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.is(Items.GLASS_BOTTLE)) {
                if (!moveItemStackTo(stack,
                        ContainerStorageBlockEntity.SLOT_BOTTLE_START,
                        ContainerStorageBlockEntity.SLOT_BOTTLE_START + ContainerStorageBlockEntity.BOTTLE_SLOTS,
                        false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!moveItemStackTo(stack,
                        ContainerStorageBlockEntity.SLOT_GENERAL_START,
                        containerEnd, false)) {
                    return ItemStack.EMPTY;
                }
            }
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
