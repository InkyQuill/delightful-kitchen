package net.inkyquill.delightfulkitchen.kitchen.execution;

import net.inkyquill.delightfulkitchen.kitchen.KitchenScanResult;
import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenInventorySnapshot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

public record KitchenPlanFingerprint(int floorCount, int memberCount, int stackCount, int totalItemCount) {
    public static KitchenPlanFingerprint of(KitchenScanResult scan, KitchenInventorySnapshot snapshot) {
        Objects.requireNonNull(scan, "scan");
        List<ItemStack> stacks = Objects.requireNonNull(snapshot, "snapshot").stacks();
        int totalItemCount = stacks.stream().mapToInt(ItemStack::getCount).sum();
        return new KitchenPlanFingerprint(scan.floorCount(), scan.memberCount(), stacks.size(), totalItemCount);
    }

    public boolean matches(KitchenScanResult scan, KitchenInventorySnapshot snapshot) {
        return equals(of(scan, snapshot));
    }
}
