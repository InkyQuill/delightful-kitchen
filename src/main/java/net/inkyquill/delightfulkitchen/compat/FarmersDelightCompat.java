package net.inkyquill.delightfulkitchen.compat;

import net.blay09.mods.cookingforblockheads.api.CookingForBlockheadsAPI;
import net.blay09.mods.cookingforblockheads.api.KitchenItemProcessor;
import net.inkyquill.delightfulkitchen.DelightfulKitchen;
import net.inkyquill.delightfulkitchen.block.entity.ModBlockEntities;
import net.inkyquill.delightfulkitchen.recipe.CookingPotRecipeHandler;
import net.inkyquill.delightfulkitchen.recipe.CuttingBoardRecipeHandler;
import net.inkyquill.delightfulkitchen.recipe.GrillRecipeHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;

/**
 * Registers all cross-mod integrations between Cooking for Blockheads and Farmer's Delight.
 *
 * <h3>What this sets up</h3>
 * <ol>
 *   <li><b>KitchenRecipeHandlers</b> – teach CfB's recipe book how to find ingredients
 *       for {@code CookingPotRecipe}, {@code CuttingBoardRecipe}, and
 *       {@code CampfireCookingRecipe} (grill).</li>
 *   <li><b>KitchenItemProcessor capabilities</b> – register our three block entities
 *       with NeoForge so CfB's kitchen discovery can pick them up when scanning
 *       adjacent blocks.</li>
 * </ol>
 *
 * <p>Recipe handler registration is called from {@link net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent#enqueueWork}.
 * Capability registration is done in {@link RegisterCapabilitiesEvent}.
 */
public class FarmersDelightCompat {

    /**
     * The KitchenItemProcessor capability, using the same ResourceLocation that
     * CfB defines internally. NeoForge's BlockCapability is a singleton keyed by
     * name, so this returns the same instance as CfB's private field.
     */
    public static final BlockCapability<KitchenItemProcessor, Void> KITCHEN_ITEM_PROCESSOR =
            BlockCapability.createVoid(
                    ResourceLocation.fromNamespaceAndPath("cookingforblockheads", "kitchen_item_processor"),
                    KitchenItemProcessor.class);

    /** Called during FMLCommonSetupEvent to register recipe handlers with CfB's recipe book. */
    public static void registerRecipeHandlers() {
        CookingForBlockheadsAPI.registerKitchenRecipeHandler(
                CookingPotRecipe.class,
                new CookingPotRecipeHandler());

        CookingForBlockheadsAPI.registerKitchenRecipeHandler(
                CuttingBoardRecipe.class,
                new CuttingBoardRecipeHandler());

        CookingForBlockheadsAPI.registerKitchenRecipeHandler(
                CampfireCookingRecipe.class,
                new GrillRecipeHandler());

        DelightfulKitchen.LOGGER.info("[Delightful Kitchen] Farmer's Delight recipe handlers registered.");
    }

    /** Called from {@link RegisterCapabilitiesEvent} to expose our block entities as KitchenItemProcessors. */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(KITCHEN_ITEM_PROCESSOR,
                ModBlockEntities.POT_STAND.get(),
                (be, ctx) -> be.getKitchenItemProcessor());

        event.registerBlockEntity(KITCHEN_ITEM_PROCESSOR,
                ModBlockEntities.AUTO_CUTTER.get(),
                (be, ctx) -> be.getKitchenItemProcessor());

        event.registerBlockEntity(KITCHEN_ITEM_PROCESSOR,
                ModBlockEntities.GRILL.get(),
                (be, ctx) -> be.getKitchenItemProcessor());
    }
}
