package net.inkyquill.cfbdelight.client.screen;

import net.inkyquill.cfbdelight.menu.ChefCounterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public class ChefCounterScreen extends AbstractContainerScreen<ChefCounterMenu> {

    private static final Component STATUS_UNLINKED =
            Component.translatable("container.cfb_delight.chef_counter.status_unlinked");

    public ChefCounterScreen(ChefCounterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = 72;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFF8B8B8B);
        graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFFEFEFEF);
        graphics.fill(leftPos + 7, topPos + 83, leftPos + 169, topPos + 141, 0xFFD6D6D6);
        graphics.fill(leftPos + 7, topPos + 141, leftPos + 169, topPos + 161, 0xFFD6D6D6);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        graphics.drawString(font,
                Component.translatable("gui.cfb_delight.chef_counter.floor_tiles", menu.getFloorCount()),
                titleLabelX, 22, 0x404040, false);
        graphics.drawString(font,
                Component.translatable("gui.cfb_delight.chef_counter.members", menu.getMemberCount()),
                titleLabelX, 34, 0x404040, false);
        if (menu.hasMultipleCounters()) {
            List<FormattedCharSequence> warningLines = font.split(
                    Component.translatable("gui.cfb_delight.chef_counter.multiple_counters"), imageWidth - 16);
            for (int i = 0; i < Math.min(warningLines.size(), 2); i++) {
                graphics.drawString(font, warningLines.get(i), titleLabelX, 48 + i * 10, 0xAA3322, false);
            }
        } else if (menu.getFloorCount() == 0) {
            graphics.drawString(font, STATUS_UNLINKED, titleLabelX, 50, 0x404040, false);
        }
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
