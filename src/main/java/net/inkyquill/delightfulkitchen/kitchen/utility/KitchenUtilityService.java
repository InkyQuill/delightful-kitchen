package net.inkyquill.delightfulkitchen.kitchen.utility;

import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryService;
import net.inkyquill.delightfulkitchen.kitchen.KitchenScanResult;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class KitchenUtilityService {
    private final KitchenScanResult scan;
    private final KitchenInventoryService inventory;

    public KitchenUtilityService(KitchenScanResult scan, KitchenInventoryService inventory) {
        this.scan = Objects.requireNonNull(scan, "scan");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    public EnumSet<PlanningBlocker> blockersFor(KitchenUtilityRequirement requirement) {
        EnumSet<PlanningBlocker> blockers = EnumSet.noneOf(PlanningBlocker.class);
        switch (requirement) {
            case NONE -> {
            }
            case WATER -> {
                addWaterProviderBlocker(blockers);
                if (!hasContainer(this::isWaterContainer)) {
                    blockers.add(PlanningBlocker.MISSING_WATER_CONTAINER);
                }
            }
            case ICE -> {
                addFreezerBlocker(blockers);
                addWaterProviderBlocker(blockers);
                if (!hasContainer(KitchenUtilityService::isBucket)) {
                    blockers.add(PlanningBlocker.MISSING_ICE_CONTAINER);
                }
            }
            case SNOWBALL -> {
                addFreezerBlocker(blockers);
                addWaterProviderBlocker(blockers);
                if (!hasContainer(KitchenUtilityService::isGlassBottle)) {
                    blockers.add(PlanningBlocker.MISSING_SNOWBALL_CONTAINER);
                }
            }
        }
        return blockers;
    }

    public Optional<BorrowedContainer> borrow(KitchenUtilityRequirement requirement) {
        if (!blockersFor(requirement).isEmpty()) {
            return Optional.empty();
        }

        return switch (requirement) {
            case NONE -> Optional.empty();
            case WATER -> inventory.borrowOne(this::isWaterContainer);
            case ICE -> inventory.borrowOne(KitchenUtilityService::isBucket);
            case SNOWBALL -> inventory.borrowOne(KitchenUtilityService::isGlassBottle);
        };
    }

    public Optional<ItemStack> release(BorrowedContainer borrowed) {
        ItemStack remainder = borrowed.source().releaseToSlot(borrowed.sourceSlot(), borrowed.stack());
        if (!remainder.isEmpty()) {
            remainder = inventory.insert(remainder, false);
        }
        return remainder.isEmpty() ? Optional.empty() : Optional.of(remainder.copy());
    }

    private void addWaterProviderBlocker(EnumSet<PlanningBlocker> blockers) {
        if (!scan.hasWaterProvider()) {
            blockers.add(PlanningBlocker.MISSING_WATER_PROVIDER);
        }
    }

    private void addFreezerBlocker(EnumSet<PlanningBlocker> blockers) {
        if (!scan.hasFreezer()) {
            blockers.add(PlanningBlocker.MISSING_FREEZER);
        }
    }

    private boolean hasContainer(Predicate<ItemStack> matcher) {
        return inventory.snapshot().stacks().stream().anyMatch(matcher);
    }

    private boolean isWaterContainer(ItemStack stack) {
        return isBucket(stack) || isGlassBottle(stack);
    }

    private static boolean isBucket(ItemStack stack) {
        return stack.is(Items.BUCKET);
    }

    private static boolean isGlassBottle(ItemStack stack) {
        return stack.is(Items.GLASS_BOTTLE);
    }
}
