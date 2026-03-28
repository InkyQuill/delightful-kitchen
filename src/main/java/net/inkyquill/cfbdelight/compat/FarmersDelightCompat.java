package net.inkyquill.cfbdelight.compat;

import net.blay09.mods.balm.Balm;
import net.blay09.mods.cookingforblockheads.api.CookingForBlockheadsAPI;
import net.blay09.mods.cookingforblockheads.capability.KitchenItemProcessorHolder;
import net.blay09.mods.cookingforblockheads.capability.ModCapabilities;
import net.inkyquill.cfbdelight.CfBDelight;
import net.inkyquill.cfbdelight.block.entity.ModBlockEntities;
import net.inkyquill.cfbdelight.recipe.CookingPotRecipeHandler;
import net.inkyquill.cfbdelight.recipe.CuttingBoardRecipeHandler;
import net.inkyquill.cfbdelight.recipe.GrillRecipeHandler;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;

import java.util.Set;

/**
 * Registers all cross-mod integrations between Cooking for Blockheads and Farmer's Delight.
 *
 * <h3>What this sets up</h3>
 * <ol>
 *   <li><b>KitchenRecipeHandlers</b> – teach CfB's recipe book how to find ingredients
 *       for {@code CookingPotRecipe}, {@code CuttingBoardRecipe}, and
 *       {@code CampfireCookingRecipe} (grill), and how to dispatch those recipes
 *       to the appropriate processor blocks.</li>
 *   <li><b>KitchenItemProcessor capabilities</b> – register our three block entities
 *       with Balm so CfB's kitchen discovery (KitchenImpl) can pick them up when
 *       scanning adjacent blocks.</li>
 *   <li><b>KitchenItemProvider capabilities</b> – register our blocks as item
 *       providers so their output slots appear as ingredient sources in CfB's
 *       kitchen inventory.</li>
 * </ol>
 *
 * <h3>Bowls, bottles, and other FD holders</h3>
 * Farmer's Delight meal recipes typically require a {@code minecraft:bowl} or
 * {@code minecraft:glass_bottle} as an output container. CfB's recipe book
 * already searches the player's inventory and any {@code KitchenItemProvider}
 * blocks for these items. No extra work is needed here as long as the player
 * has bowls / bottles somewhere reachable in the kitchen.
 *
 * <p>Called via {@link FMLCommonSetupEvent#enqueueWork} from
 * {@link net.inkyquill.cfbdelight.CfBDelight#commonSetup}, which guarantees
 * that CfB (and Balm) have been fully initialised before we touch their APIs.
 */
public class FarmersDelightCompat {

    public static void init() {
        registerRecipeHandlers();
        registerCapabilities();
        CfBDelight.LOGGER.info("[CfB-Delight] Farmer's Delight compatibility loaded.");
    }

    // -------------------------------------------------------------------------
    // Recipe Handlers
    // -------------------------------------------------------------------------

    private static void registerRecipeHandlers() {
        // CookingPotRecipe → PotStandBlockEntity
        CookingForBlockheadsAPI.registerKitchenRecipeHandler(
                CookingPotRecipe.class,
                new CookingPotRecipeHandler());

        // CuttingBoardRecipe → AutoCutterBlockEntity
        CookingForBlockheadsAPI.registerKitchenRecipeHandler(
                CuttingBoardRecipe.class,
                new CuttingBoardRecipeHandler());

        // CampfireCookingRecipe → GrillBlockEntity
        // Note: this overrides CfB's built-in campfire handler if one exists.
        // If CfB has no built-in campfire handler, this adds a new capability.
        CookingForBlockheadsAPI.registerKitchenRecipeHandler(
                CampfireCookingRecipe.class,
                new GrillRecipeHandler());
    }

    // -------------------------------------------------------------------------
    // Balm Capabilities
    // -------------------------------------------------------------------------

    private static void registerCapabilities() {
        // Expose KitchenItemProcessor for all three compat blocks.
        // Balm will wire these up to NeoForge's capability system automatically.
        Balm.capabilities().registerProvider(
                net.inkyquill.cfbdelight.CfBDelight.id("kitchen_item_processor"),
                ModCapabilities.KITCHEN_ITEM_PROCESSOR,
                (blockEntity, context) ->
                        blockEntity instanceof KitchenItemProcessorHolder holder
                                ? holder.getKitchenItemProcessor()
                                : null,
                () -> Set.of(
                        ModBlockEntities.POT_STAND.get(),
                        ModBlockEntities.AUTO_CUTTER.get(),
                        ModBlockEntities.GRILL.get()
                ));

        // NOTE: ContainerStorageBlockEntity is a Container and is tagged with
        // cookingforblockheads:kitchen_item_providers, so CfB's tag-based fallback
        // provider wraps it automatically – no explicit registration needed here.
    }
}
