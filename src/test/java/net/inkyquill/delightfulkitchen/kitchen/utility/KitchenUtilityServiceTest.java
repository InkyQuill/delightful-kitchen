package net.inkyquill.delightfulkitchen.kitchen.utility;

import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryEndpoint;
import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryService;
import net.inkyquill.delightfulkitchen.kitchen.KitchenScanResult;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.items.IItemHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitchenUtilityServiceTest {
    @BeforeAll
    static void bootStrapMinecraft() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void waterCanBorrowBucketOrGlassBottleWhenProviderExists() {
        SimpleContainer buckets = new SimpleContainer(new ItemStack(Items.BUCKET));
        SimpleContainer bottles = new SimpleContainer(new ItemStack(Items.GLASS_BOTTLE));

        Optional<BorrowedContainer> bucket = service(scan(true, false), buckets)
                .borrow(KitchenUtilityRequirement.WATER);
        Optional<BorrowedContainer> bottle = service(scan(true, false), bottles)
                .borrow(KitchenUtilityRequirement.WATER);

        assertTrue(bucket.isPresent());
        assertTrue(bucket.orElseThrow().stack().is(Items.BUCKET));
        assertTrue(bottle.isPresent());
        assertTrue(bottle.orElseThrow().stack().is(Items.GLASS_BOTTLE));
    }

    @Test
    void iceRequiresFreezerWaterProviderAndBucket() {
        SimpleContainer bucket = new SimpleContainer(new ItemStack(Items.BUCKET));
        SimpleContainer bottle = new SimpleContainer(new ItemStack(Items.GLASS_BOTTLE));

        assertTrue(service(scan(true, true), bucket).borrow(KitchenUtilityRequirement.ICE).isPresent());
        assertTrue(service(scan(false, true), new SimpleContainer(new ItemStack(Items.BUCKET)))
                .borrow(KitchenUtilityRequirement.ICE).isEmpty());
        assertTrue(service(scan(true, false), new SimpleContainer(new ItemStack(Items.BUCKET)))
                .borrow(KitchenUtilityRequirement.ICE).isEmpty());
        assertTrue(service(scan(true, true), bottle).borrow(KitchenUtilityRequirement.ICE).isEmpty());
    }

    @Test
    void snowballRequiresFreezerWaterProviderAndGlassBottle() {
        SimpleContainer bottle = new SimpleContainer(new ItemStack(Items.GLASS_BOTTLE));
        SimpleContainer bucket = new SimpleContainer(new ItemStack(Items.BUCKET));

        assertTrue(service(scan(true, true), bottle).borrow(KitchenUtilityRequirement.SNOWBALL).isPresent());
        assertTrue(service(scan(false, true), new SimpleContainer(new ItemStack(Items.GLASS_BOTTLE)))
                .borrow(KitchenUtilityRequirement.SNOWBALL).isEmpty());
        assertTrue(service(scan(true, false), new SimpleContainer(new ItemStack(Items.GLASS_BOTTLE)))
                .borrow(KitchenUtilityRequirement.SNOWBALL).isEmpty());
        assertTrue(service(scan(true, true), bucket).borrow(KitchenUtilityRequirement.SNOWBALL).isEmpty());
    }

    @Test
    void borrowExtractsExactlyOneContainer() {
        SimpleContainer container = new SimpleContainer(new ItemStack(Items.BUCKET, 3));

        Optional<BorrowedContainer> borrowed = service(scan(true, true), container)
                .borrow(KitchenUtilityRequirement.ICE);

        assertTrue(borrowed.isPresent());
        assertEquals(1, borrowed.orElseThrow().stack().getCount());
        assertEquals(2, container.getItem(0).getCount());
    }

    @Test
    void releasePrefersOriginalEndpointAndSlot() {
        SimpleContainer container = new SimpleContainer(new ItemStack(Items.BUCKET, 2), ItemStack.EMPTY);
        KitchenUtilityService service = service(scan(true, true), container);

        BorrowedContainer borrowed = service.borrow(KitchenUtilityRequirement.ICE).orElseThrow();
        Optional<ItemStack> remainder = service.release(borrowed);

        assertTrue(remainder.isEmpty());
        assertEquals(2, container.getItem(0).getCount());
        assertTrue(container.getItem(1).isEmpty());
    }

    @Test
    void releaseFallsBackToGeneralInsertionWhenOriginalSlotIsFull() {
        SimpleContainer container = new SimpleContainer(new ItemStack(Items.BUCKET), ItemStack.EMPTY);
        KitchenUtilityService service = service(scan(true, true), container);

        BorrowedContainer borrowed = service.borrow(KitchenUtilityRequirement.ICE).orElseThrow();
        container.setItem(0, new ItemStack(Items.APPLE));
        Optional<ItemStack> remainder = service.release(borrowed);

        assertTrue(remainder.isEmpty());
        assertTrue(container.getItem(1).is(Items.BUCKET));
    }

    @Test
    void releaseReturnsLeftoverWhenOriginalAndFallbackInsertionFail() {
        RejectBucketContainer container = new RejectBucketContainer(new ItemStack(Items.BUCKET));
        KitchenUtilityService service = service(scan(true, true), container);

        BorrowedContainer borrowed = service.borrow(KitchenUtilityRequirement.ICE).orElseThrow();
        container.setItem(0, new ItemStack(Items.APPLE));
        Optional<ItemStack> remainder = service.release(borrowed);

        assertTrue(remainder.isPresent());
        assertTrue(remainder.orElseThrow().is(Items.BUCKET));
        assertEquals(1, remainder.orElseThrow().getCount());
    }

    @Test
    void blockersMatchMissingProviderFreezerAndContainerMapping() {
        assertEquals(EnumSet.of(PlanningBlocker.MISSING_WATER_PROVIDER, PlanningBlocker.MISSING_WATER_CONTAINER),
                service(scan(false, false), new SimpleContainer(1)).blockersFor(KitchenUtilityRequirement.WATER));
        assertEquals(EnumSet.of(PlanningBlocker.MISSING_FREEZER, PlanningBlocker.MISSING_WATER_PROVIDER,
                        PlanningBlocker.MISSING_ICE_CONTAINER),
                service(scan(false, false), new SimpleContainer(1)).blockersFor(KitchenUtilityRequirement.ICE));
        assertEquals(EnumSet.of(PlanningBlocker.MISSING_FREEZER, PlanningBlocker.MISSING_WATER_PROVIDER,
                        PlanningBlocker.MISSING_SNOWBALL_CONTAINER),
                service(scan(false, false), new SimpleContainer(1)).blockersFor(KitchenUtilityRequirement.SNOWBALL));
    }

    @Test
    void blockersIgnoreVisibleButUnextractableContainers() {
        KitchenUtilityService service = new KitchenUtilityService(scan(true, false), new KitchenInventoryService(List.of(
                new KitchenInventoryEndpoint(BlockPos.ZERO, new UnextractableItemHandler(new ItemStack(Items.BUCKET)))
        )));

        assertEquals(EnumSet.of(PlanningBlocker.MISSING_WATER_CONTAINER),
                service.blockersFor(KitchenUtilityRequirement.WATER));
        assertTrue(service.borrow(KitchenUtilityRequirement.WATER).isEmpty());
    }

    private static KitchenUtilityService service(KitchenScanResult scan, SimpleContainer container) {
        return new KitchenUtilityService(scan, inventory(container));
    }

    private static KitchenInventoryService inventory(SimpleContainer container) {
        return new KitchenInventoryService(List.of(new KitchenInventoryEndpoint(BlockPos.ZERO, container)));
    }

    private static KitchenScanResult scan(boolean hasWaterProvider, boolean hasFreezer) {
        return new KitchenScanResult(true, Set.of(BlockPos.ZERO.below()), List.of(BlockPos.ZERO),
                List.of(BlockPos.ZERO), hasWaterProvider ? List.of(BlockPos.ZERO.east()) : List.of(),
                hasFreezer ? List.of(BlockPos.ZERO.west()) : List.of());
    }

    private static final class RejectBucketContainer extends SimpleContainer {
        RejectBucketContainer(ItemStack stack) {
            super(stack);
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return !stack.is(Items.BUCKET);
        }
    }

    private static final class UnextractableItemHandler implements IItemHandler {
        private final ItemStack stack;

        private UnextractableItemHandler(ItemStack stack) {
            this.stack = stack.copy();
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return stack;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    }
}
