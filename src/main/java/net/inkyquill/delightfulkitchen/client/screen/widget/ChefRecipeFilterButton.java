package net.inkyquill.delightfulkitchen.client.screen.widget;

import net.inkyquill.delightfulkitchen.menu.chef.ChefRecipeFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class ChefRecipeFilterButton extends Button {
    private final ChefRecipeFilter filter;
    private final Supplier<ChefRecipeFilter> selectedFilter;

    public ChefRecipeFilterButton(int x, int y, int width, int height, ChefRecipeFilter filter,
                                  Supplier<ChefRecipeFilter> selectedFilter, Consumer<ChefRecipeFilter> onSelect) {
        super(x, y, width, height, label(filter), button -> onSelect.accept(filter), DEFAULT_NARRATION);
        this.filter = filter;
        this.selectedFilter = selectedFilter;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean selected = selectedFilter.get() == filter;
        int background = selected ? 0xFFE0C36B : isHoveredOrFocused() ? 0xFFE5E5E5 : 0xFFD0D0D0;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF404040);
        graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, background);

        Minecraft minecraft = Minecraft.getInstance();
        String text = getMessage().getString();
        int textWidth = minecraft.font.width(text);
        int textX = getX() + (width - textWidth) / 2;
        int textY = getY() + (height - 8) / 2;
        graphics.drawString(minecraft.font, text, textX, textY, 0x404040, false);
    }

    private static Component label(ChefRecipeFilter filter) {
        return Component.translatable("gui.delightful_kitchen.chef_counter.filter." + key(filter));
    }

    private static String key(ChefRecipeFilter filter) {
        return switch (filter) {
            case ALL -> "all";
            case READY -> "ready";
            case INTERMEDIATE -> "intermediate";
            case BLOCKED -> "blocked";
        };
    }
}
