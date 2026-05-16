package net.inkyquill.cfbdelight.kitchen.recipe.adapters;

import net.inkyquill.cfbdelight.kitchen.recipe.PlannedIngredient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class FuelRequirements {
    private FuelRequirements() {
    }

    static Optional<PlannedIngredient> furnaceFuel() {
        List<ItemStack> fuels = BuiltInRegistries.ITEM.holders()
                .filter(holder -> holder.getData(NeoForgeDataMaps.FURNACE_FUELS) != null)
                .map(holder -> new ItemStack(holder.value()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        addFallbackFuel(fuels, new ItemStack(Items.COAL));
        addFallbackFuel(fuels, new ItemStack(Items.CHARCOAL));
        return Optional.of(new PlannedIngredient(Ingredient.of(fuels.stream()), 1));
    }

    private static void addFallbackFuel(List<ItemStack> fuels, ItemStack fallback) {
        boolean alreadyPresent = fuels.stream().anyMatch(stack -> ItemStack.isSameItemSameComponents(stack, fallback));
        if (!alreadyPresent) {
            fuels.add(fallback);
        }
    }
}
