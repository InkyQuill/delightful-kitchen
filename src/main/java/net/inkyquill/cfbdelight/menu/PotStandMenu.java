package net.inkyquill.cfbdelight.menu;

import net.inkyquill.cfbdelight.block.entity.PotStandBlockEntity;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Menu for the Pot Stand.
 * <p>
 * Layout (pixel offsets match the {@code pot_stand.png} GUI texture, 176×188):
 * <pre>
 * [slot 0][slot 1][slot 2][slot 3][slot 4][slot 5]   ← ingredient slots (y=18)
 *                  [slot 6 – container]               ← bowl / bottle (y=36)
 *                  [slot 7 – output  ]                ← cooked meal   (y=54)
 * ─────────────── player inventory ───────────────
 * </pre>
 * {@link ContainerData} indices:
 * <ul>
 *   <li>0 – current cook time</li>
 *   <li>1 – total cook time</li>
 * </ul>
 */
public class PotStandMenu extends AbstractContainerMenu {

    public static final int DATA_COOK_TIME = 0;
    public static final int DATA_COOK_TOTAL = 1;
    public static final int DATA_BURN_TIME = 2;
    public static final int DATA_BURN_TOTAL = 3;
    private static final int DATA_SIZE = 4;

    private final Container container;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    /** Network (client-side) constructor – called by {@link ModMenuTypes#POT_STAND}. */
    public PotStandMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
                new SimpleContainer(PotStandBlockEntity.SLOT_COUNT),
                new SimpleContainerData(DATA_SIZE),
                ContainerLevelAccess.NULL);
    }

    /** Server-side constructor – called from {@link PotStandBlockEntity#createMenu}. */
    public PotStandMenu(int containerId, Inventory playerInventory,
                        Container container, ContainerData data,
                        ContainerLevelAccess access) {
        super(ModMenuTypes.POT_STAND.get(), containerId);
        checkContainerSize(container, PotStandBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, DATA_SIZE);
        this.container = container;
        this.data = data;
        this.access = access;

        container.startOpen(playerInventory.player);
        addIngredientSlots();
        addPlayerSlots(playerInventory);
        addDataSlots(data);
    }

    // ------------------------------------------------------------------
    // Slot setup
    // ------------------------------------------------------------------

    private void addIngredientSlots() {
        // 6 ingredient slots – two rows of 3, top section (y=18)
        for (int i = 0; i < 3; i++) {
            addSlot(new Slot(container, i,     8 + i * 18, 18));
        }
        for (int i = 0; i < 3; i++) {
            addSlot(new Slot(container, 3 + i, 8 + i * 18, 36));
        }
        // Container slot (bowl / bottle) – center below ingredients (y=54)
        addSlot(new Slot(container, PotStandBlockEntity.SLOT_CONTAINER, 62, 54));
        // Output slot – non-insertable (y=54, right side)
        addSlot(new Slot(container, PotStandBlockEntity.SLOT_OUTPUT, 116, 54) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        // Fuel slot (y=72, left side)
        addSlot(new Slot(container, PotStandBlockEntity.SLOT_FUEL, 8, 72));
    }

    private void addPlayerSlots(Inventory inv) {
        // Player inventory rows (y=96, 114, 132)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 96 + row * 18));
            }
        }
        // Hotbar (y=154)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 154));
        }
    }

    // ------------------------------------------------------------------
    // Shift-click transfer
    // ------------------------------------------------------------------

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        int containerEnd = PotStandBlockEntity.SLOT_COUNT;
        int invStart = containerEnd;
        int invEnd = invStart + 36;

        if (index < containerEnd) {
            if (!moveItemStackTo(stack, invStart, invEnd, true)) return ItemStack.EMPTY;
        } else {
            // Fuel items go to fuel slot first
            if (container.canPlaceItem(PotStandBlockEntity.SLOT_FUEL, stack)) {
                moveItemStackTo(stack, PotStandBlockEntity.SLOT_FUEL, PotStandBlockEntity.SLOT_FUEL + 1, false);
            }
            // Remaining goes to ingredient/container slots
            if (!stack.isEmpty() && !moveItemStackTo(stack, 0, PotStandBlockEntity.SLOT_OUTPUT, false))
                return ItemStack.EMPTY;
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

    // ------------------------------------------------------------------
    // Data accessors (for screen progress bar)
    // ------------------------------------------------------------------

    public int getCookTime() { return data.get(DATA_COOK_TIME); }
    public int getCookTimeTotal() { return data.get(DATA_COOK_TOTAL); }

    /** 0.0 – 1.0 cook progress for the progress bar. */
    public float getCookProgress() {
        int total = getCookTimeTotal();
        return total == 0 ? 0f : (float) getCookTime() / total;
    }

    // Burn / fuel accessors
    public int getBurnTime() { return data.get(DATA_BURN_TIME); }
    public int getBurnTotal() { return data.get(DATA_BURN_TOTAL); }
    public float getBurnProgress() {
        int total = getBurnTotal();
        return total == 0 ? 0f : (float) getBurnTime() / total;
    }
}
