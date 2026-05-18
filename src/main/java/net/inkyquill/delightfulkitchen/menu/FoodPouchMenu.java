package net.inkyquill.delightfulkitchen.menu;

import net.inkyquill.delightfulkitchen.component.ModDataComponents;
import net.inkyquill.delightfulkitchen.item.FoodPouchItem;
import net.inkyquill.delightfulkitchen.menu.slot.FoodPouchSlot;
import net.inkyquill.delightfulkitchen.pouch.FeedThreshold;
import net.inkyquill.delightfulkitchen.pouch.FoodPouchContainer;
import net.inkyquill.delightfulkitchen.pouch.FoodPouchTier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class FoodPouchMenu extends AbstractContainerMenu {
    public static final int DATA_SIZE = 5;
    public static final int DATA_TIER = 0;
    public static final int DATA_ROWS = 1;
    public static final int DATA_COLUMNS = 2;
    public static final int DATA_THRESHOLD = 3;
    public static final int DATA_FEED_WHEN_HURT = 4;

    private static final int SLOT_SIZE = 18;
    private static final int POUCH_X = 8;
    private static final int POUCH_Y = 18;
    private static final int PLAYER_X = 8;
    private static final int PLAYER_INVENTORY_GAP = 14;
    private static final int PLAYER_INVENTORY_HEIGHT = 76;

    private final FoodPouchContainer pouchContainer;
    private final ItemStack pouchStack;
    private final FoodPouchTier tier;
    private final ContainerData data;
    private final Layout layout;

    public FoodPouchMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, ItemStack.EMPTY, readTier(buf), new SimpleContainerData(DATA_SIZE));
    }

    public FoodPouchMenu(int containerId, Inventory playerInventory, ItemStack pouchStack) {
        this(containerId, playerInventory, pouchStack,
                pouchStack.isEmpty() ? FoodPouchTier.BASIC : FoodPouchItem.tierOf(pouchStack),
                new PouchSettingsData(pouchStack));
    }

    private FoodPouchMenu(int containerId, Inventory playerInventory, ItemStack pouchStack, FoodPouchTier tier,
                          ContainerData data) {
        super(ModMenuTypes.FOOD_POUCH.get(), containerId);
        checkContainerDataCount(data, DATA_SIZE);
        this.pouchStack = pouchStack;
        this.tier = tier;
        this.data = data;
        this.layout = layoutFor(tier.columns(), tier.rows());
        this.pouchContainer = new FoodPouchContainer(pouchStack, tier);

        pouchContainer.startOpen(playerInventory.player);
        addPouchSlots();
        addPlayerSlots(playerInventory);
        addDataSlots(data);
    }

    public static Layout layoutFor(int columns, int rows) {
        int pouchSlots = columns * rows;
        int playerInventoryY = POUCH_Y + rows * SLOT_SIZE + PLAYER_INVENTORY_GAP;
        int contentWidth = Math.max(176, POUCH_X * 2 + columns * SLOT_SIZE);
        int imageWidth = contentWidth + settingsPanelWidth(columns);
        int imageHeight = playerInventoryY + PLAYER_INVENTORY_HEIGHT;
        return new Layout(columns, rows, pouchSlots, playerInventoryY, imageWidth, imageHeight);
    }

    public FoodPouchTier tier() {
        return tier;
    }

    public int rows() {
        return data.get(DATA_ROWS);
    }

    public int columns() {
        return data.get(DATA_COLUMNS);
    }

    public int pouchSlots() {
        return layout.pouchSlots();
    }

    public int playerInventoryY() {
        return layout.playerInventoryY();
    }

    public int screenImageWidth() {
        return layout.imageWidth();
    }

    public int screenImageHeight() {
        return layout.imageHeight();
    }

    public FeedThreshold threshold() {
        FeedThreshold[] values = FeedThreshold.values();
        return values[Math.floorMod(data.get(DATA_THRESHOLD), values.length)];
    }

    public boolean feedWhenHurt() {
        return data.get(DATA_FEED_WHEN_HURT) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        if (isBoundPouchStack(stack)) {
            return ItemStack.EMPTY;
        }

        ItemStack original = stack.copy();
        int pouchEnd = pouchSlots();
        int playerStart = pouchEnd;
        int playerEnd = playerStart + 36;

        if (index < pouchEnd) {
            if (!moveItemStackTo(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, pouchEnd, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        pouchContainer.setChanged();
        return original;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (isUnsafeBoundPouchTransfer(slotId, button, clickType, player)) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
        pouchContainer.setChanged();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0) {
            setThreshold(nextThreshold());
            return true;
        }
        if (id == 1) {
            setFeedWhenHurt(!feedWhenHurt());
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return pouchContainer.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        pouchContainer.stopOpen(player);
        pouchContainer.setChanged();
    }

    private void addPouchSlots() {
        for (int row = 0; row < tier.rows(); row++) {
            for (int col = 0; col < tier.columns(); col++) {
                int slot = col + row * tier.columns();
                addSlot(new FoodPouchSlot(pouchContainer, slot, POUCH_X + col * SLOT_SIZE,
                        POUCH_Y + row * SLOT_SIZE, tier, this::isBoundPouchStack));
            }
        }
    }

    private void addPlayerSlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, PLAYER_X + col * SLOT_SIZE,
                        layout.playerInventoryY() + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, PLAYER_X + col * SLOT_SIZE,
                    layout.playerInventoryY() + 58));
        }
    }

    private boolean isUnsafeBoundPouchTransfer(int slotId, int button, ClickType clickType, Player player) {
        if (slotId < 0 || slotId >= pouchSlots()) {
            return false;
        }
        if (clickType == ClickType.SWAP && button >= 0 && button < 9) {
            return isBoundPouchStack(player.getInventory().getItem(button));
        }
        if (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_CRAFT) {
            return isBoundPouchStack(getCarried());
        }
        return false;
    }

    private boolean isBoundPouchStack(ItemStack stack) {
        return !stack.isEmpty() && stack == pouchStack;
    }

    private FeedThreshold nextThreshold() {
        FeedThreshold[] values = FeedThreshold.values();
        return values[(threshold().ordinal() + 1) % values.length];
    }

    private void setThreshold(FeedThreshold threshold) {
        if (!pouchStack.isEmpty()) {
            pouchStack.set(ModDataComponents.FEED_AT_HUNGER_LEVEL.get(), threshold);
        }
        data.set(DATA_THRESHOLD, threshold.ordinal());
    }

    private void setFeedWhenHurt(boolean value) {
        if (!pouchStack.isEmpty()) {
            pouchStack.set(ModDataComponents.FEED_WHEN_HURT.get(), value);
        }
        data.set(DATA_FEED_WHEN_HURT, value ? 1 : 0);
    }

    private static FoodPouchTier readTier(FriendlyByteBuf buf) {
        FoodPouchTier[] values = FoodPouchTier.values();
        if (buf == null || buf.readableBytes() <= 0) {
            return FoodPouchTier.BASIC;
        }
        return values[Mth.clamp(buf.readVarInt(), 0, values.length - 1)];
    }

    private static int settingsPanelWidth(int columns) {
        return columns >= 9 ? 72 : 88;
    }

    public record Layout(int columns, int rows, int pouchSlots, int playerInventoryY, int imageWidth,
                         int imageHeight) {
    }

    private static final class PouchSettingsData implements ContainerData {
        private int tierOrdinal;
        private int rows;
        private int columns;
        private int thresholdOrdinal;
        private int feedWhenHurt;

        private PouchSettingsData(ItemStack pouchStack) {
            FoodPouchTier tier = pouchStack.isEmpty()
                    ? FoodPouchTier.BASIC
                    : FoodPouchItem.tierOf(pouchStack);
            this.tierOrdinal = tier.ordinal();
            this.rows = tier.rows();
            this.columns = tier.columns();
            FeedThreshold threshold = pouchStack.getOrDefault(ModDataComponents.FEED_AT_HUNGER_LEVEL.get(),
                    FeedThreshold.HALF);
            this.thresholdOrdinal = threshold.ordinal();
            this.feedWhenHurt = pouchStack.getOrDefault(ModDataComponents.FEED_WHEN_HURT.get(), true) ? 1 : 0;
        }

        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_TIER -> tierOrdinal;
                case DATA_ROWS -> rows;
                case DATA_COLUMNS -> columns;
                case DATA_THRESHOLD -> thresholdOrdinal;
                case DATA_FEED_WHEN_HURT -> feedWhenHurt;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_TIER -> tierOrdinal = value;
                case DATA_ROWS -> rows = value;
                case DATA_COLUMNS -> columns = value;
                case DATA_THRESHOLD -> thresholdOrdinal = value;
                case DATA_FEED_WHEN_HURT -> feedWhenHurt = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_SIZE;
        }
    }
}
