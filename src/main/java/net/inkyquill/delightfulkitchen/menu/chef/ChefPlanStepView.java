package net.inkyquill.delightfulkitchen.menu.chef;

import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;
import net.inkyquill.delightfulkitchen.kitchen.utility.KitchenUtilityRequirement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

public record ChefPlanStepView(
        ResourceLocation recipeId,
        ItemStack primaryOutput,
        List<ItemStack> inputs,
        List<ItemStack> outputs,
        KitchenStationType stationType,
        ItemStack servingContainer,
        ItemStack fuel,
        KitchenUtilityRequirement utilityRequirement,
        int processingTime,
        boolean asynchronous
) {
    public ChefPlanStepView {
        recipeId = Objects.requireNonNull(recipeId, "recipeId");
        primaryOutput = Objects.requireNonNull(primaryOutput, "primaryOutput").copy();
        inputs = copyStacks(inputs, "inputs");
        outputs = copyStacks(outputs, "outputs");
        stationType = Objects.requireNonNull(stationType, "stationType");
        servingContainer = Objects.requireNonNull(servingContainer, "servingContainer").copy();
        fuel = Objects.requireNonNull(fuel, "fuel").copy();
        utilityRequirement = Objects.requireNonNull(utilityRequirement, "utilityRequirement");
        if (processingTime < 0) {
            throw new IllegalArgumentException("processingTime cannot be negative");
        }
    }

    @Override
    public ItemStack primaryOutput() {
        return primaryOutput.copy();
    }

    @Override
    public List<ItemStack> inputs() {
        return copyStacks(inputs, "inputs");
    }

    @Override
    public List<ItemStack> outputs() {
        return copyStacks(outputs, "outputs");
    }

    @Override
    public ItemStack servingContainer() {
        return servingContainer.copy();
    }

    @Override
    public ItemStack fuel() {
        return fuel.copy();
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks, String name) {
        return Objects.requireNonNull(stacks, name).stream()
                .map(stack -> Objects.requireNonNull(stack, name + " cannot contain null").copy())
                .toList();
    }
}
