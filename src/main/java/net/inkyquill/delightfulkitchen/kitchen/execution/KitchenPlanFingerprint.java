package net.inkyquill.delightfulkitchen.kitchen.execution;

import net.inkyquill.delightfulkitchen.kitchen.KitchenScanResult;
import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenInventorySnapshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

public record KitchenPlanFingerprint(int floorCount, int memberCount, int stackCount, int totalItemCount,
                                     int itemIdentityHash) {
    public KitchenPlanFingerprint(int floorCount, int memberCount, int stackCount, int totalItemCount) {
        this(floorCount, memberCount, stackCount, totalItemCount, 0);
    }

    public static KitchenPlanFingerprint of(KitchenScanResult scan, KitchenInventorySnapshot snapshot) {
        Objects.requireNonNull(scan, "scan");
        List<ItemStack> stacks = Objects.requireNonNull(snapshot, "snapshot").stacks();
        int totalItemCount = stacks.stream().mapToInt(ItemStack::getCount).sum();
        return new KitchenPlanFingerprint(scan.floorCount(), scan.memberCount(), stacks.size(), totalItemCount,
                itemIdentityHash(stacks));
    }

    public boolean matches(KitchenScanResult scan, KitchenInventorySnapshot snapshot) {
        return equals(of(scan, snapshot));
    }

    private static int itemIdentityHash(List<ItemStack> stacks) {
        int hash = 1;
        for (ItemStack stack : stacks) {
            hash = 31 * hash + stackIdentityHash(stack);
        }
        return hash;
    }

    private static int stackIdentityHash(ItemStack stack) {
        return Objects.hash(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getComponents(), stack.getCount());
    }
}
