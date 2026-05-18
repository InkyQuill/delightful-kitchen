package net.inkyquill.delightfulkitchen.menu.chef;

import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanState;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.Objects;

public record ChefRecipeSummary(
        int index,
        ResourceLocation recipeId,
        ItemStack output,
        PlanState state,
        EnumSet<PlanningBlocker> blockers,
        int stepCount
) {
    public ChefRecipeSummary {
        recipeId = Objects.requireNonNull(recipeId, "recipeId");
        output = Objects.requireNonNull(output, "output").copy();
        state = Objects.requireNonNull(state, "state");
        blockers = Objects.requireNonNull(blockers, "blockers").clone();
        if (stepCount < 0) {
            throw new IllegalArgumentException("stepCount cannot be negative");
        }
    }

    @Override
    public ItemStack output() {
        return output.copy();
    }

    @Override
    public EnumSet<PlanningBlocker> blockers() {
        return blockers.clone();
    }
}
