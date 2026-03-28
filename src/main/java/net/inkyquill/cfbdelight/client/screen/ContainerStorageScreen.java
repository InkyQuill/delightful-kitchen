package net.inkyquill.cfbdelight.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.inkyquill.cfbdelight.CfBDelight;
import net.inkyquill.cfbdelight.menu.ContainerStorageMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Container Storage block.
 * <p>
 * Three rows of container slots are labeled:
 * <ul>
 *   <li>Row 1 – "Bowls"</li>
 *   <li>Row 2 – "Bottles"</li>
 *   <li>Row 3 – "Other"</li>
 * </ul>
 */
public class ContainerStorageScreen extends AbstractContainerScreen<ContainerStorageMenu> {

    private static final ResourceLocation TEXTURE =
            CfBDelight.id("textures/gui/container_storage.png");

    private static final Component LABEL_BOWLS   = Component.translatable("gui.cfb_delight.bowls");
    private static final Component LABEL_BOTTLES = Component.translatable("gui.cfb_delight.bottles");
    private static final Component LABEL_OTHER   = Component.translatable("gui.cfb_delight.other");

    public ContainerStorageScreen(ContainerStorageMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth  = 176;
        imageHeight = 196;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);

        int color = 0x636363;
        graphics.drawString(font, LABEL_BOWLS,   8, 18, color, false);
        graphics.drawString(font, LABEL_BOTTLES, 8, 44, color, false);
        graphics.drawString(font, LABEL_OTHER,   8, 74, color, false);

        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
