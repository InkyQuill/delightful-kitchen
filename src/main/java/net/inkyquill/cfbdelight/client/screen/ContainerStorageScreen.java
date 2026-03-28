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
        // Block title
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);

        // Section labels – drawn to the right of the 8-slot rows.
        // x=152 is just past the 8th slot (8 + 8*18 = 152). Scale to 6px of right margin.
        // TODO: Adjust these coordinates to match the final texture layout.
        graphics.drawString(font, LABEL_BOWLS,   152, 20, 0x707070, false);
        graphics.drawString(font, LABEL_BOTTLES, 152, 38, 0x707070, false);
        graphics.drawString(font, LABEL_OTHER,   152, 56, 0x707070, false);

        // Player inventory label
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
