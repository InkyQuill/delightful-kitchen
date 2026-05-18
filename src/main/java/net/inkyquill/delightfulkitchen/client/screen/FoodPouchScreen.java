package net.inkyquill.delightfulkitchen.client.screen;

import net.inkyquill.delightfulkitchen.menu.FoodPouchMenu;
import net.inkyquill.delightfulkitchen.menu.slot.FoodPouchSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class FoodPouchScreen extends AbstractContainerScreen<FoodPouchMenu> {
    private static final int FRAME = 0xFFC6C6C6;
    private static final int BACKGROUND = 0xFFEFEFEF;
    private static final int PANEL = 0xFFDCDCDC;
    private static final int BORDER = 0xFF777777;
    private static final int FOOD_SLOT = 0x2245A86A;
    private static final int NON_FOOD_SLOT = 0x223B6EA8;
    private static final int EMPTY_SLOT = 0x33000000;
    private static final int TEXT = 0xFF404040;

    private Button thresholdButton;
    private Button hurtButton;

    public FoodPouchScreen(FoodPouchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = menu.screenImageWidth();
        imageHeight = menu.screenImageHeight();
        inventoryLabelX = 8;
        inventoryLabelY = menu.playerInventoryY() - 11;
    }

    @Override
    protected void init() {
        super.init();
        int panelX = leftPos + settingsPanelX();
        thresholdButton = Button.builder(thresholdLabel(), button -> clickMenuButton(0))
                .bounds(panelX + 8, topPos + 18, settingsButtonWidth(), 20)
                .build();
        hurtButton = Button.builder(hurtLabel(), button -> clickMenuButton(1))
                .bounds(panelX + 8, topPos + 44, settingsButtonWidth(), 20)
                .build();
        addRenderableWidget(thresholdButton);
        addRenderableWidget(hurtButton);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, FRAME);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, BORDER);
        graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, BACKGROUND);

        drawPouchSlots(graphics);
        drawPlayerInventoryBand(graphics);
        drawSettingsPanel(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (thresholdButton != null) {
            thresholdButton.setMessage(thresholdLabel());
        }
        if (hurtButton != null) {
            hurtButton.setMessage(hurtLabel());
        }
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        graphics.drawString(font, Component.translatable("gui.delightful_kitchen.food_pouch.settings"),
                settingsPanelX() + 8, 8, TEXT, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderPouchSlotLimitTooltip(graphics, mouseX, mouseY);
    }

    private void clickMenuButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void drawPouchSlots(GuiGraphics graphics) {
        for (int i = 0; i < menu.pouchSlots(); i++) {
            Slot slot = menu.slots.get(i);
            ItemStack stack = slot.getItem();
            int color = stack.isEmpty() ? EMPTY_SLOT : isFood(stack) ? FOOD_SLOT : NON_FOOD_SLOT;
            graphics.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, color);
        }
    }

    private void drawPlayerInventoryBand(GuiGraphics graphics) {
        int y = menu.playerInventoryY() - 4;
        graphics.fill(leftPos + 7, topPos + y, leftPos + 169, topPos + y + 58, 0xFFD6D6D6);
        graphics.fill(leftPos + 7, topPos + y + 58, leftPos + 169, topPos + y + 78, 0xFFD6D6D6);
    }

    private void drawSettingsPanel(GuiGraphics graphics) {
        int x = settingsPanelX();
        graphics.fill(leftPos + x, topPos + 7, leftPos + imageWidth - 7, topPos + 72, BORDER);
        graphics.fill(leftPos + x + 1, topPos + 8, leftPos + imageWidth - 8, topPos + 71, PANEL);
    }

    private void renderPouchSlotLimitTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!(hoveredSlot instanceof FoodPouchSlot pouchSlot) || !hoveredSlot.hasItem()) {
            return;
        }
        ItemStack stack = hoveredSlot.getItem();
        graphics.renderComponentTooltip(font, List.of(
                Component.translatable("gui.delightful_kitchen.food_pouch.slot_limit",
                        stack.getCount(), pouchSlot.slotLimit(stack))
        ), mouseX, mouseY + 12);
    }

    private Component thresholdLabel() {
        return Component.translatable("gui.delightful_kitchen.food_pouch.threshold." + menu.threshold().getSerializedName());
    }

    private Component hurtLabel() {
        return Component.translatable(menu.feedWhenHurt()
                ? "gui.delightful_kitchen.food_pouch.hurt_on"
                : "gui.delightful_kitchen.food_pouch.hurt_off");
    }

    private int settingsPanelX() {
        return Math.max(176, 16 + menu.columns() * 18);
    }

    private int settingsButtonWidth() {
        return imageWidth - settingsPanelX() - 16;
    }

    private static boolean isFood(ItemStack stack) {
        return !stack.isEmpty() && stack.getFoodProperties(null) != null;
    }
}
