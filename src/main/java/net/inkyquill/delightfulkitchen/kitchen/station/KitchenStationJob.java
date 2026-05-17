package net.inkyquill.delightfulkitchen.kitchen.station;

import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record KitchenStationJob(
        ResourceLocation recipeId,
        KitchenStationType stationType,
        List<ItemStack> inputs,
        List<ItemStack> outputs,
        Optional<ItemStack> servingContainer,
        Optional<ItemStack> fuel,
        int processingTime
) {
    public KitchenStationJob {
        recipeId = Objects.requireNonNull(recipeId, "recipeId");
        stationType = Objects.requireNonNull(stationType, "stationType");
        inputs = copyStacks(inputs, "inputs");
        outputs = copyStacks(outputs, "outputs");
        servingContainer = copyOptionalStack(servingContainer, "servingContainer");
        fuel = copyOptionalStack(fuel, "fuel");
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("station job must have at least one output");
        }
        if (processingTime < 0) {
            throw new IllegalArgumentException("processingTime cannot be negative");
        }
    }

    @Override
    public List<ItemStack> inputs() {
        return inputs.stream().map(ItemStack::copy).toList();
    }

    @Override
    public List<ItemStack> outputs() {
        return outputs.stream().map(ItemStack::copy).toList();
    }

    @Override
    public Optional<ItemStack> servingContainer() {
        return servingContainer.map(ItemStack::copy);
    }

    @Override
    public Optional<ItemStack> fuel() {
        return fuel.map(ItemStack::copy);
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks, String name) {
        return Objects.requireNonNull(stacks, name).stream()
                .map(stack -> Objects.requireNonNull(stack, name + " cannot contain null"))
                .filter(stack -> !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
    }

    private static Optional<ItemStack> copyOptionalStack(Optional<ItemStack> stack, String name) {
        return Objects.requireNonNull(stack, name)
                .filter(itemStack -> !itemStack.isEmpty())
                .map(ItemStack::copy);
    }
}
