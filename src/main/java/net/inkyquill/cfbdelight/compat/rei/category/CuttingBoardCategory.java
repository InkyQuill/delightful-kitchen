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
import net.inkyquill.cfbdelight.compat.rei.display.CuttingBoardDisplay;
import net.inkyquill.cfbdelight.item.ModItems;
import net.minecraft.network.chat.Component;

import java.util.List;

public class CuttingBoardCategory implements DisplayCategory<CuttingBoardDisplay> {

    @Override
    public CategoryIdentifier<? extends CuttingBoardDisplay> getCategoryIdentifier() {
        return CuttingBoardDisplay.id();
    }

    @Override
    public Renderer getIcon() {
        return EntryStacks.of(ModItems.AUTO_CUTTER.get());
    }

    @Override
    public Component getTitle() {
        return Component.translatable("category.cfb_delight.cutting_board");
    }

    @Override
    public int getDisplayHeight() {
        return 49;
    }

    @Override
    public List<Widget> setupDisplay(CuttingBoardDisplay display, Rectangle bounds) {
        Point start = new Point(bounds.getCenterX() - 50, bounds.y + 8);
        List<Widget> widgets = Lists.newArrayList();

        widgets.add(Widgets.createRecipeBase(bounds));
        widgets.add(Widgets.createArrow(new Point(start.x + 38, start.y + 8))
                .animationDurationTicks(100));

        List<EntryIngredient> inputs = display.getInputEntries();
        // inputs[0] = tool, inputs[1] = ingredient
        if (inputs.size() > 0) {
            widgets.add(Widgets.createSlot(new Point(start.x, start.y))
                    .entries(inputs.get(0)).markInput());
        }
        if (inputs.size() > 1) {
            widgets.add(Widgets.createSlot(new Point(start.x, start.y + 19))
                    .entries(inputs.get(1)).markInput());
        }

        // Output items in a 2-column grid
        List<EntryIngredient> outputs = display.getOutputEntries();
        for (int i = 0; i < outputs.size() && i < 4; i++) {
            int col = i % 2;
            int row = i / 2;
            widgets.add(Widgets.createSlot(new Point(start.x + 66 + col * 19, start.y + row * 19))
                    .entries(outputs.get(i)).markOutput());
        }

        return widgets;
    }
}
