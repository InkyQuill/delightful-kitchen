package net.inkyquill.delightfulkitchen.bookmark;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeBookmarkStoreTest {
    @Test
    void pinningPreservesInsertionOrderAndIgnoresDuplicates() {
        RecipeBookmarkStore store = new RecipeBookmarkStore();
        UUID player = UUID.randomUUID();
        ResourceLocation first = id("recipe/first");
        ResourceLocation second = id("recipe/second");

        assertTrue(store.setPinned(player, first, true));
        assertTrue(store.setPinned(player, second, true));
        assertFalse(store.setPinned(player, first, true));

        assertEquals(List.of(first, second), store.get(player));
    }

    @Test
    void unpinningRemovesOnlyThatPlayersRecipe() {
        RecipeBookmarkStore store = new RecipeBookmarkStore();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        ResourceLocation recipe = id("recipe/stew");

        store.setPinned(firstPlayer, recipe, true);
        store.setPinned(secondPlayer, recipe, true);

        assertTrue(store.setPinned(firstPlayer, recipe, false));
        assertFalse(store.setPinned(firstPlayer, recipe, false));

        assertEquals(List.of(), store.get(firstPlayer));
        assertEquals(List.of(recipe), store.get(secondPlayer));
    }

    @Test
    void pinningRejectsNewRecipesAfterCap() {
        RecipeBookmarkStore store = new RecipeBookmarkStore();
        UUID player = UUID.randomUUID();

        for (int i = 0; i < RecipeBookmarkStore.MAX_PINNED_RECIPES; i++) {
            assertTrue(store.setPinned(player, id("recipe/" + i), true));
        }

        assertFalse(store.setPinned(player, id("recipe/over_cap"), true));
        assertEquals(RecipeBookmarkStore.MAX_PINNED_RECIPES, store.get(player).size());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("delightful_kitchen", path);
    }
}
