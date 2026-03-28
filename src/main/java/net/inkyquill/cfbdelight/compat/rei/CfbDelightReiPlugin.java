package net.inkyquill.cfbdelight.compat.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;
import me.shedaniel.rei.plugin.common.BuiltinPlugin;
import net.inkyquill.cfbdelight.compat.rei.category.CookingPotCategory;
import net.inkyquill.cfbdelight.compat.rei.category.CuttingBoardCategory;
import net.inkyquill.cfbdelight.compat.rei.display.CookingPotDisplay;
import net.inkyquill.cfbdelight.compat.rei.display.CuttingBoardDisplay;
import net.inkyquill.cfbdelight.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

/**
 * REI plugin — registers categories and displays for FD's Cooking Pot and Cutting Board
 * recipe types, and adds our blocks as workstations for the relevant categories.
 *
 * <p>Note: FD does not ship a REI plugin, so we register the full categories here.
 * The Grill uses vanilla CAMPFIRE_COOKING, which REI already handles; we only add
 * our block as an extra workstation for that built-in category.
 */
@REIPluginClient
@SuppressWarnings("unused")
public class CfbDelightReiPlugin implements REIClientPlugin {

    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.add(new CookingPotCategory());
        registry.addWorkstations(CookingPotDisplay.id(),
                EntryStacks.of(ModItems.POT_STAND.get()));

        registry.add(new CuttingBoardCategory());
        registry.addWorkstations(CuttingBoardDisplay.id(),
                EntryStacks.of(ModItems.AUTO_CUTTER.get()));

        // Grill uses the built-in campfire category
        registry.addWorkstations(BuiltinPlugin.CAMPFIRE,
                EntryStacks.of(ModItems.GRILL.get()));
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        RecipeManager rm = mc.level.getRecipeManager();

        for (RecipeHolder<CookingPotRecipe> holder : rm.getAllRecipesFor(ModRecipeTypes.COOKING.get())) {
            registry.add(new CookingPotDisplay(holder));
        }

        for (RecipeHolder<CuttingBoardRecipe> holder : rm.getAllRecipesFor(ModRecipeTypes.CUTTING.get())) {
            registry.add(new CuttingBoardDisplay(holder));
        }
    }
}
