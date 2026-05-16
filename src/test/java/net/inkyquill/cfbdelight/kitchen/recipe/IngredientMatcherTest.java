package net.inkyquill.cfbdelight.kitchen.recipe;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngredientMatcherTest {
    @BeforeAll
    static void bootStrapMinecraft() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void consumesAcrossStacks() {
        KitchenInventorySnapshot snapshot = new KitchenInventorySnapshot(List.of(
                new ItemStack(Items.WHEAT, 2),
                new ItemStack(Items.WHEAT, 2)
        ));
        PlannedIngredient ingredient = new PlannedIngredient(Ingredient.of(Items.WHEAT), 3);

        Optional<List<ItemStack>> matches = IngredientMatcher.findMatches(snapshot, List.of(ingredient));

        assertTrue(matches.isPresent());
        List<ItemStack> consumed = matches.orElseThrow();
        assertEquals(2, consumed.size());
        assertTrue(consumed.stream().allMatch(stack -> stack.is(Items.WHEAT)));
        assertEquals(List.of(2, 1), consumed.stream().map(ItemStack::getCount).toList());
        assertEquals(List.of(2, 2), snapshot.stacks().stream().map(ItemStack::getCount).toList());
    }

    @Test
    void failsWhenMissingItems() {
        KitchenInventorySnapshot snapshot = new KitchenInventorySnapshot(List.of(new ItemStack(Items.WHEAT)));
        PlannedIngredient ingredient = new PlannedIngredient(Ingredient.of(Items.WHEAT), 2);

        Optional<List<ItemStack>> matches = IngredientMatcher.findMatches(snapshot, List.of(ingredient));

        assertTrue(matches.isEmpty());
    }
}
