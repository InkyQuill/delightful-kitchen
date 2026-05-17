package net.inkyquill.delightfulkitchen.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.inkyquill.delightfulkitchen.DelightfulKitchen;
import net.inkyquill.delightfulkitchen.block.entity.FreezerBlockEntity;
import net.inkyquill.delightfulkitchen.menu.FreezerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Screen for the Freezer's output-only inventory. */
public class FreezerScreen extends AbstractContainerScreen<FreezerMenu> {

    private static final ResourceLocation TEXTURE =
            DelightfulKitchen.id("textures/gui/freezer.png");

    private Button modeButton;

    public FreezerScreen(FreezerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = 165;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        modeButton = Button.builder(modeLabel(), button -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
                    }
                })
                .bounds(leftPos + 91, topPos + 15, 77, 20)
                .build();
        addRenderableWidget(modeButton);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (modeButton != null) {
            modeButton.setMessage(modeLabel());
        }
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
        graphics.drawString(font, statusLabel(), 8, 18, 0x636363, false);
        graphics.drawString(font, progressLabel(), 8, 55, 0x636363, false);
    }

    private Component modeLabel() {
        return Component.translatable("gui.delightful_kitchen.freezer.mode." + modeKey(menu.passiveMode()));
    }

    private Component statusLabel() {
        if (menu.passiveMode() == FreezerBlockEntity.PassiveMode.PAUSED) {
            return Component.translatable("gui.delightful_kitchen.freezer.status.paused");
        }
        if (menu.isProducing()) {
            return Component.translatable("gui.delightful_kitchen.freezer.status.active",
                    nextProductLabel());
        }
        return Component.translatable("gui.delightful_kitchen.freezer.status.waiting",
                nextProductLabel());
    }

    private Component progressLabel() {
        return Component.translatable("gui.delightful_kitchen.freezer.progress",
                menu.progressTicks(), FreezerBlockEntity.PASSIVE_CYCLE_TICKS);
    }

    private Component nextProductLabel() {
        return Component.translatable(menu.nextProductIce()
                ? "item.minecraft.ice"
                : "item.minecraft.snowball");
    }

    private static String modeKey(FreezerBlockEntity.PassiveMode mode) {
        return switch (mode) {
            case ICE_AND_SNOW -> "ice_and_snow";
            case ICE_ONLY -> "ice_only";
            case SNOW_ONLY -> "snow_only";
            case PAUSED -> "paused";
        };
    }
}
