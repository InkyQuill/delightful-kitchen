package net.inkyquill.cfbdelight.kitchen.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.List;

public record RecipePlan(
        ResourceLocation targetRecipeId,
        ItemStack output,
        PlanState state,
        List<PlannedRecipe> steps,
        EnumSet<PlanningBlocker> blockers
) {
    public RecipePlan {
        output = output.copy();
        steps = List.copyOf(steps);
        blockers = blockers.clone();
    }

    public static RecipePlan blocked(ResourceLocation targetRecipeId, ItemStack output,
                                     List<PlannedRecipe> steps, EnumSet<PlanningBlocker> blockers) {
        return new RecipePlan(targetRecipeId, output, PlanState.BLOCKED, steps, blockers);
    }
}
