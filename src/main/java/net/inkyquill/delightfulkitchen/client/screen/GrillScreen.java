package net.inkyquill.delightfulkitchen.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.inkyquill.delightfulkitchen.DelightfulKitchen;
import net.inkyquill.delightfulkitchen.block.entity.GrillBlockEntity;
import net.inkyquill.delightfulkitchen.menu.GrillMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Grill.
 * <p>
 * Six input slots (top row), six output slots (second row), then player
 * inventory below. A small downward progress arrow is drawn between each
 * input–output column pair.
 */
public class GrillScreen extends AbstractContainerScreen<GrillMenu> {

    private static final ResourceLocation TEXTURE =
            DelightfulKitchen.id("textures/gui/grill.png");

    public GrillScreen(GrillMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth  = 176;
        imageHeight = 196;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // Per-slot progress flames (between input row y=18 and output row y=54, gap at y=37)
        for (int i = 0; i < GrillBlockEntity.GRILL_SLOTS; i++) {
            float progress = menu.getCookProgress(i);
            if (progress > 0f) {
                int flameHeight = (int) (14 * progress);
                graphics.blit(TEXTURE,
                        leftPos + 10 + i * 18, topPos + 37,
                        176, 0,
                        9, flameHeight,
                        256, 256);
            }
        }

        // Fuel fire at right side
        float burnProgress = menu.getBurnProgress();
        if (burnProgress > 0f) {
            int fireHeight = (int) (14 * burnProgress);
            graphics.blit(TEXTURE, leftPos + 154, topPos + 36 + (14 - fireHeight),
                    185, 14 - fireHeight, 9, fireHeight, 256, 256);
        }
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
        graphics.drawString(font, Component.translatable("gui.delightful_kitchen.input"),  116, 22, color, false);
        graphics.drawString(font, Component.translatable("gui.delightful_kitchen.output"), 116, 58, color, false);
        graphics.drawString(font, Component.translatable("gui.delightful_kitchen.fuel"),   152, 25, color, false);
    }
}
