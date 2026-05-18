package net.inkyquill.delightfulkitchen.client.screen.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class ChefQuantityControl extends AbstractWidget {
    private static final int MIN_QUANTITY = 1;
    private static final int MAX_QUANTITY = 64;

    private final IntSupplier quantity;
    private final IntConsumer onQuantityChanged;

    public ChefQuantityControl(int x, int y, int width, int height, IntSupplier quantity,
                               IntConsumer onQuantityChanged) {
        super(x, y, width, height, Component.translatable("gui.delightful_kitchen.chef_counter.quantity"));
        this.quantity = quantity;
        this.onQuantityChanged = onQuantityChanged;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF404040);
        graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0xFFD8D8D8);
        drawStepper(graphics, getX() + 1, getY() + 1, "-", mouseX, mouseY, canDecrease());
        drawStepper(graphics, getX() + width - 15, getY() + 1, "+", mouseX, mouseY, canIncrease());

        String value = Integer.toString(quantity.getAsInt());
        graphics.drawCenteredString(minecraft.font, value, getX() + width / 2, getY() + 5, 0x404040);
    }

    private void drawStepper(GuiGraphics graphics, int x, int y, String text, int mouseX, int mouseY, boolean enabled) {
        Minecraft minecraft = Minecraft.getInstance();
        int color = contains(mouseX, mouseY, x, y, 14, height - 2) && enabled ? 0xFFEAEAEA : 0xFFC6C6C6;
        graphics.fill(x, y, x + 14, y + height - 2, color);
        graphics.drawCenteredString(minecraft.font, text, x + 7, y + 5, enabled ? 0x404040 : 0x909090);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int current = quantity.getAsInt();
        if (mouseX < getX() + 16 && canDecrease()) {
            onQuantityChanged.accept(current - 1);
            return true;
        }
        if (mouseX >= getX() + width - 16 && canIncrease()) {
            onQuantityChanged.accept(current + 1);
            return true;
        }
        return true;
    }

    private boolean canDecrease() {
        return quantity.getAsInt() > MIN_QUANTITY;
    }

    private boolean canIncrease() {
        return quantity.getAsInt() < MAX_QUANTITY;
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }
}
