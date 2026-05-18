package net.inkyquill.delightfulkitchen.pouch;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class FoodPouchContainerReturn {
    private FoodPouchContainerReturn() {
    }

    public static ItemStack extractContainer(ItemStack consumedOneFood, ItemStack finishResult) {
        if (finishResult.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (ItemStack.isSameItemSameComponents(consumedOneFood, finishResult)) {
            return ItemStack.EMPTY;
        }
        return finishResult.copy();
    }

    public static FoodPouchInventory returnContainer(FoodPouchInventory inventory, FoodPouchTier tier,
                                                     ItemStack container, Player player) {
        if (container.isEmpty()) {
            return inventory;
        }
        FoodPouchInventoryOps.InsertResult pouchInsert = FoodPouchInventoryOps.insert(inventory, tier, container);
        ItemStack remainder = pouchInsert.remainder();
        if (remainder.isEmpty()) {
            return pouchInsert.inventory();
        }

        ItemStack playerRemainder = remainder.copy();
        if (player.getInventory().add(playerRemainder)) {
            return pouchInsert.inventory();
        }
        if (!playerRemainder.isEmpty()) {
            player.drop(playerRemainder, false);
        }
        return pouchInsert.inventory();
    }
}
