package net.inkyquill.delightfulkitchen.pouch;

import net.inkyquill.delightfulkitchen.item.FoodPouchItem;
import net.inkyquill.delightfulkitchen.component.ModDataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.effect.MobEffectInstance;

public final class FoodPouchFeeder {
    private FoodPouchFeeder() {
    }

    public static void tickEquipped(Player player, ItemStack pouchStack) {
        if (pouchStack.isEmpty() || !(pouchStack.getItem() instanceof FoodPouchItem)) {
            return;
        }

        int cooldown = FoodPouchItem.feedCooldown(pouchStack);
        if (cooldown > 0) {
            FoodPouchItem.setFeedCooldown(pouchStack, cooldown - 1);
            return;
        }

        FoodPouchFeedingResult result = tryFeed(player, pouchStack);
        FoodPouchItem.setFeedCooldown(pouchStack, result.cooldownTicks());
    }

    public static FoodPouchFeedingResult tryFeed(Player player, ItemStack pouchStack) {
        if (shouldSkipPlayer(player)) {
            return FoodPouchFeedingResult.notConsumed();
        }

        FoodPouchTier tier = FoodPouchItem.tierOf(pouchStack);
        FoodPouchInventory inventory = FoodPouchItem.mutableInventory(pouchStack);
        FeedThreshold threshold = FoodPouchItem.threshold(pouchStack);
        boolean feedWhenHurt = FoodPouchItem.feedWhenHurt(pouchStack);

        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack source = inventory.get(slot);
            FoodProperties food = source.getFoodProperties(player);
            if (!isCandidateFood(source, food)) {
                continue;
            }
            if (!shouldEat(player.getFoodData(), player, food, threshold, feedWhenHurt)) {
                continue;
            }

            FoodPouchInventory updated = consumeFromSlot(player, inventory, tier, slot, source, food);
            pouchStack.set(ModDataComponents.POUCH_INVENTORY.get(), updated);
            return FoodPouchFeedingResult.consumed(player.getFoodData().needsFood());
        }

        return FoodPouchFeedingResult.notConsumed();
    }

    public static boolean shouldEat(FoodData foodData, LivingEntity player, FoodProperties food,
                                    FeedThreshold threshold, boolean feedWhenHurt) {
        int missing = 20 - foodData.getFoodLevel();
        if (missing <= 0 || food.nutrition() < 1) {
            return false;
        }
        if (feedWhenHurt && player.getHealth() < player.getMaxHealth()) {
            return true;
        }
        return switch (threshold) {
            case ANY -> missing >= 1;
            case HALF -> missing >= Math.max(1, Mth.ceil(food.nutrition() / 2.0F));
            case FULL -> missing >= food.nutrition();
        };
    }

    private static boolean shouldSkipPlayer(Player player) {
        return player.isCreative()
                || player.isSpectator()
                || !player.isAlive()
                || player.isSleeping()
                || !player.getFoodData().needsFood();
    }

    private static boolean isCandidateFood(ItemStack stack, FoodProperties food) {
        return !stack.isEmpty()
                && food != null
                && food.nutrition() >= 1
                && !stack.is(Items.OMINOUS_BOTTLE);
    }

    private static FoodPouchInventory consumeFromSlot(Player player, FoodPouchInventory inventory, FoodPouchTier tier,
                                                      int slot, ItemStack source, FoodProperties food) {
        ItemStack oneFood = source.copyWithCount(1);
        player.getFoodData().eat(food.nutrition(), food.saturation());
        for (FoodProperties.PossibleEffect effect : food.effects()) {
            if (player.getRandom().nextFloat() < effect.probability()) {
                player.addEffect(new MobEffectInstance(effect.effect()));
            }
        }

        ItemStack finishResult = oneFood.finishUsingItem(player.level(), player);
        ItemStack container = FoodPouchContainerReturn.extractContainer(oneFood, finishResult);
        FoodPouchInventoryOps.ExtractResult extracted = FoodPouchInventoryOps.extract(inventory, slot, 1);
        return FoodPouchContainerReturn.returnContainer(extracted.inventory(), tier, container, player);
    }
}
