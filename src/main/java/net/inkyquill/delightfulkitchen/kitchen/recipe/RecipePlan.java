package net.inkyquill.delightfulkitchen.kitchen.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

public record RecipePlan(
        ResourceLocation targetRecipeId,
        ItemStack output,
        PlanState state,
        List<PlannedRecipe> steps,
        EnumSet<PlanningBlocker> blockers
) {
    public RecipePlan {
        targetRecipeId = Objects.requireNonNull(targetRecipeId, "targetRecipeId");
        output = Objects.requireNonNull(output, "output").copy();
        state = Objects.requireNonNull(state, "state");
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        blockers = Objects.requireNonNull(blockers, "blockers").clone();
    }

    public static RecipePlan blocked(ResourceLocation targetRecipeId, ItemStack output,
                                     List<PlannedRecipe> steps, EnumSet<PlanningBlocker> blockers) {
        return new RecipePlan(targetRecipeId, output, PlanState.BLOCKED, steps, blockers);
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
