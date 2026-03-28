package net.inkyquill.cfbdelight.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import net.inkyquill.cfbdelight.CfBDelight;
import net.inkyquill.cfbdelight.item.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import vectorwing.farmersdelight.integration.jei.FDRecipeTypes;

/**
 * JEI plugin — registers our blocks as recipe catalysts for FD's existing categories.
 * FD registers the actual recipe categories and recipe lists; we just add our blocks
 * as additional stations so players can click on them to see the relevant recipes.
 */
@JeiPlugin
@SuppressWarnings("unused")
public class CfbDelightJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = CfBDelight.id("jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // Grill processes campfire-cooking recipes (same as FD's Stove / Skillet)
        registration.addRecipeCatalyst(new ItemStack(ModItems.GRILL.get()), RecipeTypes.CAMPFIRE_COOKING);

        // Pot Stand processes FD's Cooking Pot recipes
        registration.addRecipeCatalyst(new ItemStack(ModItems.POT_STAND.get()), FDRecipeTypes.COOKING);

        // Auto Cutter processes FD's Cutting Board recipes
        registration.addRecipeCatalyst(new ItemStack(ModItems.AUTO_CUTTER.get()), FDRecipeTypes.CUTTING);
    }
}
