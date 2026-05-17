package net.inkyquill.delightfulkitchen.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.inkyquill.delightfulkitchen.DelightfulKitchen;
import net.inkyquill.delightfulkitchen.menu.PotStandMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Pot Stand.
 * <p>
 * Draws the 176×188 container background, a progress arrow/flame between the
 * ingredient area and the output slot, and standard player inventory labels.
 */
public class PotStandScreen extends AbstractContainerScreen<PotStandMenu> {

    private static final ResourceLocation TEXTURE =
            DelightfulKitchen.id("textures/gui/pot_stand.png");

    /** Pixel dimensions of the GUI panel (must match the texture and menu slot positions). */
    private static final int GUI_WIDTH  = 176;
    private static final int GUI_HEIGHT = 188;

    public PotStandScreen(PotStandMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth  = GUI_WIDTH;
        imageHeight = GUI_HEIGHT;
        // Move inventory label down to match the taller GUI
        inventoryLabelY = GUI_HEIGHT - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        // Draw the main panel from the texture
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // --- Cook progress flame (drawn over the background) ---
        float progress = menu.getCookProgress();
        if (progress > 0f) {
            // Small downward arrow: 9×14 px, sourced from row 188 in the texture sheet
            int arrowHeight = (int) (14 * progress);
            graphics.blit(TEXTURE,
                    leftPos + 80, topPos + 37,   // screen position (center gap)
                    176, 0,                       // texture UV (right of main panel)
                    9, arrowHeight,               // width × partial height
                    256, 256);
        }

        // Fuel fire
        float burnProgress = menu.getBurnProgress();
        if (burnProgress > 0f) {
            int fireHeight = (int) (14 * burnProgress);
            graphics.blit(TEXTURE, leftPos + 10, topPos + 72 + (14 - fireHeight),
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
        // Label for the 3+3 ingredient section (right of the ingredient slots, x>44)
        graphics.drawString(font, Component.translatable("gui.delightful_kitchen.ingredients"), 62, 26, color, false);
        // Labels above container and output slots (in the empty right-half area at y=44)
        graphics.drawString(font, Component.translatable("gui.delightful_kitchen.container"),   62, 44, color, false);
        graphics.drawString(font, Component.translatable("gui.delightful_kitchen.output"),     116, 44, color, false);
        // Label to the right of fuel slot (fuel slot is at x=8, y=72)
        graphics.drawString(font, Component.translatable("gui.delightful_kitchen.fuel"),        27, 76, color, false);
    }
}
