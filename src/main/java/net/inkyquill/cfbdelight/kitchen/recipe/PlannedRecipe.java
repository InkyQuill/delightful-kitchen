package net.inkyquill.cfbdelight.kitchen.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public record PlannedRecipe(
        ResourceLocation id,
        List<PlannedIngredient> ingredients,
        List<ItemStack> outputs,
        KitchenStationType stationType,
        Optional<ItemStack> servingContainer,
        Optional<PlannedIngredient> fuel,
        int processingTime,
        boolean asynchronous
) {
    public PlannedRecipe {
        ingredients = List.copyOf(ingredients);
        outputs = outputs.stream().map(ItemStack::copy).toList();
        servingContainer = servingContainer.map(ItemStack::copy);
        if (outputs.isEmpty() || outputs.stream().allMatch(ItemStack::isEmpty)) {
            throw new IllegalArgumentException("planned recipe must have a non-empty output");
        }
        if (processingTime < 0) {
            throw new IllegalArgumentException("processingTime cannot be negative");
        }
    }

    public ItemStack primaryOutput() {
        return outputs.getFirst().copy();
    }
}
