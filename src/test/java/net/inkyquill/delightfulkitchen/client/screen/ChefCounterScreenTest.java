package net.inkyquill.delightfulkitchen.client.screen;

import net.inkyquill.delightfulkitchen.kitchen.execution.KitchenPlanFingerprint;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanState;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;
import net.inkyquill.delightfulkitchen.menu.chef.ChefPlanDetails;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChefCounterScreenTest {
    @BeforeAll
    static void bootStrapMinecraft() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void pendingDetailsMatchSurvivingRecipeByIdAfterIndexChanges() {
        ChefPlanDetails details = details(0, id("stew"), 2);

        assertTrue(ChefCounterScreen.matchesPendingDetails(details, 1, 2, id("stew")));
    }

    @Test
    void pendingDetailsRejectDifferentRecipeAtSameIndexWhenIdKnown() {
        ChefPlanDetails details = details(1, id("bread"), 2);

        assertFalse(ChefCounterScreen.matchesPendingDetails(details, 1, 2, id("stew")));
    }

    @Test
    void pendingDetailsFallbackToIndexWhenRecipeIdUnknown() {
        ChefPlanDetails details = details(1, id("bread"), 2);

        assertTrue(ChefCounterScreen.matchesPendingDetails(details, 1, 2, null));
    }

    @Test
    void confirmableDetailsRejectStaleRecipeWhileAwaitingServerDetails() {
        ChefPlanDetails staleDetails = details(0, id("stew"), 2);

        assertFalse(ChefCounterScreen.canConfirmDetails(Optional.of(staleDetails), true, false, 1, 2, id("bread")));
    }

    @Test
    void confirmableDetailsAllowMatchingRecipeWhileAwaitingServerDetails() {
        ChefPlanDetails matchingDetails = details(1, id("bread"), 2);

        assertTrue(ChefCounterScreen.canConfirmDetails(Optional.of(matchingDetails), true, false, 1, 2, id("bread")));
    }

    @Test
    void confirmableDetailsAllowReadyDetailsWhenNotWaiting() {
        ChefPlanDetails details = details(0, id("stew"), 2);

        assertTrue(ChefCounterScreen.canConfirmDetails(Optional.of(details), false, false, 0, 2, id("stew")));
    }

    @Test
    void confirmableDetailsRejectMismatchedRecipeWhenNotWaiting() {
        ChefPlanDetails details = details(0, id("stew"), 2);

        assertFalse(ChefCounterScreen.canConfirmDetails(Optional.of(details), false, false, 1, 2, id("bread")));
    }

    private static ChefPlanDetails details(int selectedIndex, ResourceLocation recipeId, int quantity) {
        return new ChefPlanDetails(selectedIndex, recipeId, new ItemStack(Items.BREAD), PlanState.READY, quantity, 1,
                List.of(), EnumSet.noneOf(PlanningBlocker.class), new KitchenPlanFingerprint(1, 2, 3, 4, 5));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("delightful_kitchen", path);
    }
}
