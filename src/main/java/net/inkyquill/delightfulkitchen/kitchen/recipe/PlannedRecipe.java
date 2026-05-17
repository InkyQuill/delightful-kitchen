package net.inkyquill.delightfulkitchen.kitchen.recipe;

import net.inkyquill.delightfulkitchen.kitchen.utility.KitchenUtilityRequirement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record PlannedRecipe(
        ResourceLocation id,
        List<PlannedIngredient> ingredients,
        List<ItemStack> outputs,
        KitchenStationType stationType,
        Optional<ItemStack> servingContainer,
        Optional<PlannedIngredient> fuel,
        KitchenUtilityRequirement utilityRequirement,
        int processingTime,
        boolean asynchronous
) {
    public PlannedRecipe {
        id = Objects.requireNonNull(id, "id");
        ingredients = List.copyOf(Objects.requireNonNull(ingredients, "ingredients"));
        outputs = Objects.requireNonNull(outputs, "outputs").stream()
                .map(output -> Objects.requireNonNull(output, "outputs cannot contain null"))
                .filter(output -> !output.isEmpty())
                .map(ItemStack::copy)
                .toList();
        stationType = Objects.requireNonNull(stationType, "stationType");
        servingContainer = Objects.requireNonNull(servingContainer, "servingContainer").map(ItemStack::copy);
        fuel = Objects.requireNonNull(fuel, "fuel");
        utilityRequirement = Objects.requireNonNull(utilityRequirement, "utilityRequirement");
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("planned recipe must have a non-empty output");
        }
        if (processingTime < 0) {
            throw new IllegalArgumentException("processingTime cannot be negative");
        }
    }

    public ItemStack primaryOutput() {
        return outputs.getFirst().copy();
    }

    @Override
    public List<ItemStack> outputs() {
        return outputs.stream().map(ItemStack::copy).toList();
    }

    @Override
    public Optional<ItemStack> servingContainer() {
        return servingContainer.map(ItemStack::copy);
    }
}
