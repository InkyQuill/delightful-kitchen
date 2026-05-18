package net.inkyquill.delightfulkitchen.menu.chef;

import net.inkyquill.delightfulkitchen.kitchen.execution.KitchenPlanFingerprint;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanState;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

public record ChefPlanDetails(
        int selectedIndex,
        ResourceLocation targetRecipeId,
        ItemStack output,
        PlanState state,
        int quantity,
        int expectedOutputCount,
        List<ChefPlanStepView> steps,
        EnumSet<PlanningBlocker> blockers,
        KitchenPlanFingerprint fingerprint
) {
    public ChefPlanDetails {
        targetRecipeId = Objects.requireNonNull(targetRecipeId, "targetRecipeId");
        output = Objects.requireNonNull(output, "output").copy();
        state = Objects.requireNonNull(state, "state");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (expectedOutputCount < 0) {
            throw new IllegalArgumentException("expectedOutputCount cannot be negative");
        }
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        blockers = Objects.requireNonNull(blockers, "blockers").clone();
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
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
