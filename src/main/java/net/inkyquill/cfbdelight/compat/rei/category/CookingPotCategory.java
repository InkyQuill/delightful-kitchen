package net.inkyquill.cfbdelight.compat.rei.category;

import com.google.common.collect.Lists;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.inkyquill.cfbdelight.compat.rei.display.CookingPotDisplay;
import net.inkyquill.cfbdelight.item.ModItems;
import net.minecraft.network.chat.Component;

import java.text.DecimalFormat;
import java.util.List;

public class CookingPotCategory implements DisplayCategory<CookingPotDisplay> {

    private static final DecimalFormat DF = new DecimalFormat("###.##");

    @Override
    public CategoryIdentifier<? extends CookingPotDisplay> getCategoryIdentifier() {
        return CookingPotDisplay.id();
    }

    @Override
    public Renderer getIcon() {
        return EntryStacks.of(ModItems.POT_STAND.get());
    }

    @Override
    public Component getTitle() {
        return Component.translatable("category.cfb_delight.cooking_pot");
    }

    @Override
    public int getDisplayHeight() {
        return 60;
    }

    @Override
    public List<Widget> setupDisplay(CookingPotDisplay display, Rectangle bounds) {
        Point start = new Point(bounds.getCenterX() - 58, bounds.y + 8);
        List<Widget> widgets = Lists.newArrayList();

        widgets.add(Widgets.createRecipeBase(bounds));
        widgets.add(Widgets.createArrow(new Point(start.x + 60, start.y + 9))
                .animationDurationTicks(display.getCookTime()));
        widgets.add(Widgets.createResultSlotBackground(new Point(start.x + 100, start.y + 9)));
        widgets.add(Widgets.createLabel(
                new Point(bounds.x + bounds.width - 4, bounds.y + 4),
                Component.translatable("category.rei.campfire.time",
                        DF.format(display.getCookTime() / 20d)))
                .noShadow().rightAligned().color(0xFF404040, 0xFFBBBBBB));

        // Input ingredients (up to 6), arranged in a 3×2 grid on the left
        List<EntryIngredient> inputs = display.getInputEntries();
        int ingredientCount = inputs.size();
        // Last entry is the container if present; put it separately below the grid
        boolean hasContainer = ingredientCount > 0 &&
                !inputs.get(ingredientCount - 1).isEmpty();
        int gridCount = hasContainer ? ingredientCount - 1 : ingredientCount;

        for (int i = 0; i < gridCount && i < 6; i++) {
            int col = i % 3;
            int row = i / 3;
            widgets.add(Widgets.createSlot(new Point(start.x + col * 19, start.y + row * 19))
                    .entries(inputs.get(i)).markInput());
        }

        // Container slot (bottom-left if present)
        if (hasContainer) {
            widgets.add(Widgets.createSlot(new Point(start.x, start.y + 38))
                    .entries(inputs.get(ingredientCount - 1)).markInput());
        }

        // Output
        List<EntryIngredient> outputs = display.getOutputEntries();
        if (!outputs.isEmpty()) {
            widgets.add(Widgets.createSlot(new Point(start.x + 100, start.y + 9))
                    .entries(outputs.get(0)).disableBackground().markOutput());
        }

        return widgets;
    }
}
