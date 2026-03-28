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
    private static final int DATA_SIZE = 2;

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
    }

    private void addPlayerSlots(Inventory inv) {
        // Player inventory rows (y=92, 110, 128)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 92 + row * 18));
            }
        }
        // Hotbar (y=150)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 150));
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
}
