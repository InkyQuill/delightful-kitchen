package net.inkyquill.cfbdelight.menu;

import net.inkyquill.cfbdelight.block.entity.GrillBlockEntity;
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

/**
 * Menu for the Grill.
 * <p>
 * Layout (176×196, matches {@code grill.png}):
 * <pre>
 *  [in0][in1][in2][in3][in4][in5]    ← input row  (y=18)
 *  [ou0][ou1][ou2][ou3][ou4][ou5]    ← output row (y=54)
 * ──────── player inventory ────────
 * </pre>
 * {@link ContainerData}: 6 cook times + 6 cook totals = 12 ints.
 */
public class GrillMenu extends AbstractContainerMenu {

    /** 6 cook times + 6 cook totals + burnTime + burnTimeTotal. */
    private static final int DATA_SIZE = 14;

    private final Container container;
    private final ContainerData data;

    public GrillMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
                new SimpleContainer(GrillBlockEntity.SLOT_COUNT),
                new SimpleContainerData(DATA_SIZE),
                ContainerLevelAccess.NULL);
    }

    public GrillMenu(int containerId, Inventory playerInventory,
                     Container container, ContainerData data,
                     ContainerLevelAccess access) {
        super(ModMenuTypes.GRILL.get(), containerId);
        checkContainerSize(container, GrillBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, DATA_SIZE);
        this.container = container;
        this.data = data;

        container.startOpen(playerInventory.player);

        // 6 input slots (top row)
        for (int i = 0; i < GrillBlockEntity.GRILL_SLOTS; i++) {
            addSlot(new Slot(container, GrillBlockEntity.SLOT_INPUT_START + i, 8 + i * 18, 18));
        }
        // 6 output slots (below, read-only)
        for (int i = 0; i < GrillBlockEntity.GRILL_SLOTS; i++) {
            final int fi = i;
            addSlot(new Slot(container, GrillBlockEntity.SLOT_OUTPUT_START + i, 8 + i * 18, 54) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
            });
        }

        // Fuel slot
        addSlot(new Slot(container, GrillBlockEntity.SLOT_FUEL, 152, 36));

        // Player inventory (y=102, 120, 138) + hotbar (y=160)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 114 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 172));
        }

        addDataSlots(data);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        int containerEnd = GrillBlockEntity.SLOT_COUNT;
        int invStart = containerEnd;
        int invEnd = invStart + 36;

        if (index < containerEnd) {
            if (!moveItemStackTo(stack, invStart, invEnd, true)) return ItemStack.EMPTY;
        } else {
            // Fuel items go to fuel slot first, then input slots
            if (GrillBlockEntity.SLOT_FUEL < containerEnd
                    && container.canPlaceItem(GrillBlockEntity.SLOT_FUEL, stack)
                    && !moveItemStackTo(stack, GrillBlockEntity.SLOT_FUEL, GrillBlockEntity.SLOT_FUEL + 1, false)) {
                // Fuel slot full or not a fuel item, try input slots
            }
            if (!stack.isEmpty() && !moveItemStackTo(stack, 0, GrillBlockEntity.GRILL_SLOTS, false)) return ItemStack.EMPTY;
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

    // Data accessors for the screen's per-slot progress bars
    public int getCookTime(int slot) { return data.get(slot); }
    public int getCookTimeTotal(int slot) { return data.get(6 + slot); }
    public float getCookProgress(int slot) {
        int total = getCookTimeTotal(slot);
        return total == 0 ? 0f : (float) getCookTime(slot) / total;
    }

    // Burn / fuel accessors
    public int getBurnTime() { return data.get(12); }
    public int getBurnTotal() { return data.get(13); }
    public float getBurnProgress() {
        int total = getBurnTotal();
        return total == 0 ? 0f : (float) getBurnTime() / total;
    }
}
