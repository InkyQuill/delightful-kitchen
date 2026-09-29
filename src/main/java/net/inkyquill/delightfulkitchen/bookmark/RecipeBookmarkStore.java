package net.inkyquill.delightfulkitchen.bookmark;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RecipeBookmarkStore {
    public static final int MAX_PINNED_RECIPES = 32;
    public static final RecipeBookmarkStore INSTANCE = new RecipeBookmarkStore();

    private final Map<UUID, Set<ResourceLocation>> bookmarks = new ConcurrentHashMap<>();

    public boolean setPinned(UUID playerId, ResourceLocation recipeId, boolean pin) {
        Set<ResourceLocation> pinned = bookmarks.computeIfAbsent(playerId, ignored -> new LinkedHashSet<>());
        synchronized (pinned) {
            if (pin) {
                if (pinned.size() >= MAX_PINNED_RECIPES && !pinned.contains(recipeId)) {
                    return false;
                }
                return pinned.add(recipeId);
            }
            return pinned.remove(recipeId);
        }
    }

    public List<ResourceLocation> get(UUID playerId) {
        Set<ResourceLocation> pinned = bookmarks.get(playerId);
        if (pinned == null) {
            return List.of();
        }
        synchronized (pinned) {
            return new ArrayList<>(pinned);
        }
    }

    public void clear(UUID playerId) {
        bookmarks.remove(playerId);
    }
}
