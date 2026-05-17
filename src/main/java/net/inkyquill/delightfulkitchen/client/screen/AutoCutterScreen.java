package net.inkyquill.delightfulkitchen.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.inkyquill.delightfulkitchen.DelightfulKitchen;
import net.inkyquill.delightfulkitchen.menu.AutoCutterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Auto Cutter.
 * <p>
 * Single input on the left, 2×2 output grid on the right, standard player
 * inventory below.
 */
public class AutoCutterScreen extends AbstractContainerScreen<AutoCutterMenu> {

    private static final ResourceLocation TEXTURE =
            DelightfulKitchen.id("textures/gui/auto_cutter.png");

    public AutoCutterScreen(AutoCutterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth  = 176;
        imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // Horizontal arrow between input and output slots (y≈34, x 53–88)
        graphics.blit(TEXTURE,
                leftPos + 53, topPos + 34,
                176, 0,
                22, 16,
                256, 256);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);

        int color = 0x636363;
        // Label above the single input slot (slot at x=26, y=35)
        graphics.drawString(font, Component.translatable("gui.delightful_kitchen.input"),  18, 23, color, false);
        // Label to the right of the 2×2 output grid (grid ends at x=132)
        graphics.drawString(font, Component.translatable("gui.delightful_kitchen.output"), 134, 35, color, false);
    }
}
