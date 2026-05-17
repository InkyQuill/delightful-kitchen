package net.inkyquill.delightfulkitchen.kitchen.utility;

import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryEndpoint;
import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryService;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public final class BorrowedContainer {
    private final ItemStack stack;
    private final KitchenInventoryEndpoint source;
    private final int sourceSlot;

    private BorrowedContainer(ItemStack stack, KitchenInventoryEndpoint source, int sourceSlot) {
        this.stack = Objects.requireNonNull(stack, "stack").copy();
        this.source = Objects.requireNonNull(source, "source");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Borrowed stack must not be empty");
        }
        if (sourceSlot < 0) {
            throw new IllegalArgumentException("Source slot must be non-negative");
        }
        this.sourceSlot = sourceSlot;
    }

    public static BorrowedContainer create(ItemStack stack, KitchenInventoryEndpoint source, int sourceSlot,
                                           KitchenInventoryService.BorrowKey borrowKey) {
        Objects.requireNonNull(borrowKey, "borrowKey");
        return new BorrowedContainer(stack, source, sourceSlot);
    }

    public ItemStack stack() {
        return stack.copy();
    }

    public KitchenInventoryEndpoint source() {
        return source;
    }

    public int sourceSlot() {
        return sourceSlot;
    }
}
