package net.inkyquill.delightfulkitchen.client.screen.widget;

import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanState;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;
import net.inkyquill.delightfulkitchen.menu.chef.ChefRecipeSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class ChefRecipeGrid extends AbstractWidget {
    public static final int COLUMNS = 6;
    public static final int ROWS = 2;
    public static final int CELL_SIZE = 22;
    private static final int VISIBLE_RECIPES = COLUMNS * ROWS;

    private final Supplier<List<ChefRecipeSummary>> recipes;
    private final IntSupplier selectedIndex;
    private final Consumer<ChefRecipeSummary> onSelected;
    private int scrollRow;

    public ChefRecipeGrid(int x, int y, Supplier<List<ChefRecipeSummary>> recipes, IntSupplier selectedIndex,
                          Consumer<ChefRecipeSummary> onSelected) {
        super(x, y, COLUMNS * CELL_SIZE, ROWS * CELL_SIZE,
                Component.translatable("gui.delightful_kitchen.chef_counter.recipe_grid"));
        this.recipes = recipes;
        this.selectedIndex = selectedIndex;
        this.onSelected = onSelected;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        List<ChefRecipeSummary> visibleRecipes = recipes.get();
        clampScroll(visibleRecipes.size());
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF7A7A7A);

        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                renderCell(graphics, mouseX, mouseY, visibleRecipes, row, column);
            }
        }

        if (canScroll(visibleRecipes.size())) {
            int barX = getX() + width - 3;
            int barHeight = Math.max(8, height * ROWS / totalRows(visibleRecipes.size()));
            int maxTravel = height - barHeight;
            int barY = getY() + (maxScrollRow(visibleRecipes.size()) == 0
                    ? 0
                    : maxTravel * scrollRow / maxScrollRow(visibleRecipes.size()));
            graphics.fill(barX, getY() + 1, barX + 2, getY() + height - 1, 0xFF5F5F5F);
            graphics.fill(barX, barY, barX + 2, barY + barHeight, 0xFFE0E0E0);
        }
    }

    private void renderCell(GuiGraphics graphics, int mouseX, int mouseY, List<ChefRecipeSummary> visibleRecipes,
                            int row, int column) {
        int cellX = getX() + column * CELL_SIZE;
        int cellY = getY() + row * CELL_SIZE;
        int recipeOffset = (scrollRow + row) * COLUMNS + column;
        boolean hovered = contains(mouseX, mouseY, cellX, cellY, CELL_SIZE, CELL_SIZE);
        graphics.fill(cellX + 1, cellY + 1, cellX + CELL_SIZE - 1, cellY + CELL_SIZE - 1,
                hovered ? 0xFFE8E8E8 : 0xFFCFCFCF);

        if (recipeOffset >= visibleRecipes.size()) {
            return;
        }

        ChefRecipeSummary summary = visibleRecipes.get(recipeOffset);
        if (summary.index() == selectedIndex.getAsInt()) {
            graphics.fill(cellX, cellY, cellX + CELL_SIZE, cellY + CELL_SIZE, 0xFFFFFF80);
            graphics.fill(cellX + 2, cellY + 2, cellX + CELL_SIZE - 2, cellY + CELL_SIZE - 2, 0xFFE8E8E8);
        }

        ItemStack output = summary.output();
        graphics.renderFakeItem(output, cellX + 3, cellY + 3);
        graphics.renderItemDecorations(Minecraft.getInstance().font, output, cellX + 3, cellY + 3);
        renderStateMarker(graphics, cellX + CELL_SIZE - 6, cellY + CELL_SIZE - 6, summary.state());
    }

    private static void renderStateMarker(GuiGraphics graphics, int x, int y, PlanState state) {
        int color = switch (state) {
            case READY -> 0xFF41B752;
            case CRAFTABLE_WITH_INTERMEDIATES -> 0xFFD89A2B;
            case BLOCKED -> 0xFFD64636;
        };
        graphics.fill(x, y, x + 4, y + 4, 0xFF303030);
        graphics.fill(x + 1, y + 1, x + 3, y + 3, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) {
            return false;
        }

        hoveredRecipe(mouseX, mouseY).ifPresent(onSelected);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<ChefRecipeSummary> visibleRecipes = recipes.get();
        if (!isMouseOver(mouseX, mouseY) || !canScroll(visibleRecipes.size())) {
            return false;
        }
        scrollRow -= (int) Math.signum(scrollY);
        clampScroll(visibleRecipes.size());
        return true;
    }

    public Optional<List<Component>> tooltipAt(double mouseX, double mouseY) {
        return hoveredRecipe(mouseX, mouseY).map(ChefRecipeGrid::tooltip);
    }

    private Optional<ChefRecipeSummary> hoveredRecipe(double mouseX, double mouseY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return Optional.empty();
        }

        int column = ((int) mouseX - getX()) / CELL_SIZE;
        int row = ((int) mouseY - getY()) / CELL_SIZE;
        int recipeOffset = (scrollRow + row) * COLUMNS + column;
        List<ChefRecipeSummary> visibleRecipes = recipes.get();
        if (recipeOffset < 0 || recipeOffset >= visibleRecipes.size()) {
            return Optional.empty();
        }
        return Optional.of(visibleRecipes.get(recipeOffset));
    }

    private static List<Component> tooltip(ChefRecipeSummary summary) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(summary.output().getHoverName());
        tooltip.add(Component.translatable("gui.delightful_kitchen.chef_counter.tooltip.state",
                stateLabel(summary.state())));
        if (summary.state() == PlanState.BLOCKED) {
            firstBlocker(summary.blockers()).ifPresent(blocker -> tooltip.add(
                    Component.translatable("gui.delightful_kitchen.chef_counter.tooltip.blocker",
                            blockerLabel(blocker))));
        }
        return tooltip;
    }

    private static Component stateLabel(PlanState state) {
        return Component.translatable("gui.delightful_kitchen.chef_counter.state." + stateKey(state));
    }

    private static String stateKey(PlanState state) {
        return switch (state) {
            case READY -> "ready";
            case CRAFTABLE_WITH_INTERMEDIATES -> "intermediate";
            case BLOCKED -> "blocked";
        };
    }

    private static Optional<PlanningBlocker> firstBlocker(EnumSet<PlanningBlocker> blockers) {
        return blockers.stream().min(Comparator.comparing(Enum::ordinal));
    }

    private static Component blockerLabel(PlanningBlocker blocker) {
        return Component.translatable("gui.delightful_kitchen.chef_counter.blocker." + blocker.name().toLowerCase());
    }

    private void clampScroll(int recipeCount) {
        scrollRow = Math.max(0, Math.min(scrollRow, maxScrollRow(recipeCount)));
    }

    private static boolean canScroll(int recipeCount) {
        return recipeCount > VISIBLE_RECIPES;
    }

    private static int maxScrollRow(int recipeCount) {
        return Math.max(0, totalRows(recipeCount) - ROWS);
    }

    private static int totalRows(int recipeCount) {
        return Math.max(ROWS, (recipeCount + COLUMNS - 1) / COLUMNS);
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }
}
