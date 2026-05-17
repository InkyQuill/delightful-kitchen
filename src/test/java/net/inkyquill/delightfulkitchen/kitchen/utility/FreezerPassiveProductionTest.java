package net.inkyquill.delightfulkitchen.kitchen.utility;

import net.inkyquill.delightfulkitchen.block.entity.FreezerBlockEntity;
import net.inkyquill.delightfulkitchen.block.entity.FreezerTestAccess;
import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryEndpoint;
import net.inkyquill.delightfulkitchen.kitchen.KitchenInventoryService;
import net.inkyquill.delightfulkitchen.kitchen.KitchenScanResult;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreezerPassiveProductionTest {
    @BeforeAll
    static void bootStrapMinecraft() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void freezerStartsWithNineEmptyOutputSlots() {
        FreezerBlockEntity freezer = FreezerTestAccess.create();

        assertEquals(9, FreezerBlockEntity.OUTPUT_SLOTS);
        assertEquals(9, freezer.getContainerSize());
        for (int slot = 0; slot < freezer.getContainerSize(); slot++) {
            assertTrue(freezer.getItem(slot).isEmpty());
        }
    }

    @Test
    void freezerOutputSlotsOnlyAcceptIceAndSnowballsInternally() {
        FreezerBlockEntity freezer = FreezerTestAccess.create();

        assertTrue(freezer.canPlaceItem(0, new ItemStack(Items.ICE)));
        assertTrue(freezer.canPlaceItem(0, new ItemStack(Items.SNOWBALL)));
        assertFalse(freezer.canPlaceItem(0, new ItemStack(Items.WATER_BUCKET)));
        assertFalse(freezer.canPlaceItem(0, new ItemStack(Items.PACKED_ICE)));
    }

    @Test
    void freezerProducesIceAndReturnsBucketAfterCycle() {
        FreezerBlockEntity freezer = FreezerTestAccess.create();
        SimpleContainer storage = new SimpleContainer(new ItemStack(Items.BUCKET));

        tick(freezer, service(true, storage), FreezerBlockEntity.PASSIVE_CYCLE_TICKS);

        assertTrue(freezer.getItem(0).is(Items.ICE));
        assertEquals(1, freezer.getItem(0).getCount());
        assertTrue(storage.getItem(0).is(Items.BUCKET));
        assertEquals(0, FreezerTestAccess.progress(freezer));
    }

    @Test
    void freezerProducesSnowballAndReturnsBottleAfterCycle() {
        FreezerBlockEntity freezer = FreezerTestAccess.create();
        FreezerTestAccess.setMode(freezer, FreezerBlockEntity.PassiveMode.SNOW_ONLY);
        SimpleContainer storage = new SimpleContainer(new ItemStack(Items.GLASS_BOTTLE));

        tick(freezer, service(true, storage), FreezerBlockEntity.PASSIVE_CYCLE_TICKS);

        assertTrue(freezer.getItem(0).is(Items.SNOWBALL));
        assertEquals(1, freezer.getItem(0).getCount());
        assertTrue(storage.getItem(0).is(Items.GLASS_BOTTLE));
        assertEquals(0, FreezerTestAccess.progress(freezer));
    }

    @Test
    void defaultModeAlternatesIceThenSnowball() {
        FreezerBlockEntity freezer = FreezerTestAccess.create();
        SimpleContainer storage = new SimpleContainer(new ItemStack(Items.BUCKET), new ItemStack(Items.GLASS_BOTTLE));

        tick(freezer, service(true, storage), FreezerBlockEntity.PASSIVE_CYCLE_TICKS);
        tick(freezer, service(true, storage), FreezerBlockEntity.PASSIVE_CYCLE_TICKS);

        assertTrue(freezer.getItem(0).is(Items.ICE));
        assertEquals(1, freezer.getItem(0).getCount());
        assertTrue(freezer.getItem(1).is(Items.SNOWBALL));
        assertEquals(1, freezer.getItem(1).getCount());
        assertTrue(FreezerTestAccess.prefersIceNext(freezer));
    }

    @Test
    void productionPausesWhenNoWaterProviderPresent() {
        FreezerBlockEntity freezer = FreezerTestAccess.create();
        SimpleContainer storage = new SimpleContainer(new ItemStack(Items.BUCKET));

        tick(freezer, service(false, storage), 20);

        assertTrue(freezer.isEmpty());
        assertEquals(0, FreezerTestAccess.progress(freezer));
        assertFalse(FreezerTestAccess.isActive(freezer));
    }

    @Test
    void productionPausesWhenRequiredContainerIsMissing() {
        FreezerBlockEntity freezer = FreezerTestAccess.create();
        SimpleContainer storage = new SimpleContainer(new ItemStack(Items.GLASS_BOTTLE));

        tick(freezer, service(true, storage), 20);

        assertTrue(freezer.isEmpty());
        assertEquals(0, FreezerTestAccess.progress(freezer));
        assertFalse(FreezerTestAccess.isActive(freezer));
    }

    @Test
    void productionPausesWhenOutputCannotAcceptProduct() {
        FreezerBlockEntity freezer = FreezerTestAccess.create();
        SimpleContainer storage = new SimpleContainer(new ItemStack(Items.BUCKET));
        for (int slot = 0; slot < freezer.getContainerSize(); slot++) {
            freezer.setItem(slot, new ItemStack(Items.SNOWBALL, Items.SNOWBALL.getDefaultMaxStackSize()));
        }

        tick(freezer, service(true, storage), 20);

        assertEquals(0, FreezerTestAccess.progress(freezer));
        assertTrue(storage.getItem(0).is(Items.BUCKET));
        assertFalse(FreezerTestAccess.isActive(freezer));
    }

    @Test
    void modeCycleChangesProductionBehavior() {
        FreezerBlockEntity freezer = FreezerTestAccess.create();
        SimpleContainer storage = new SimpleContainer(new ItemStack(Items.BUCKET), new ItemStack(Items.GLASS_BOTTLE));

        FreezerTestAccess.cycleMode(freezer);
        assertEquals(FreezerBlockEntity.PassiveMode.ICE_ONLY, FreezerTestAccess.mode(freezer));
        tick(freezer, service(true, storage), FreezerBlockEntity.PASSIVE_CYCLE_TICKS);
        FreezerTestAccess.cycleMode(freezer);
        assertEquals(FreezerBlockEntity.PassiveMode.SNOW_ONLY, FreezerTestAccess.mode(freezer));
        tick(freezer, service(true, storage), FreezerBlockEntity.PASSIVE_CYCLE_TICKS);

        assertTrue(freezer.getItem(0).is(Items.ICE));
        assertTrue(freezer.getItem(1).is(Items.SNOWBALL));
    }

    @Test
    void passiveStatePersistsToNbt() {
        FreezerBlockEntity freezer = FreezerTestAccess.create();
        FreezerTestAccess.setMode(freezer, FreezerBlockEntity.PassiveMode.SNOW_ONLY);
        tick(freezer, service(true, new SimpleContainer(new ItemStack(Items.GLASS_BOTTLE))), 37);

        CompoundTag tag = FreezerTestAccess.save(freezer, registries());
        FreezerBlockEntity loaded = FreezerTestAccess.create();
        FreezerTestAccess.load(loaded, tag, registries());

        assertEquals(FreezerBlockEntity.PassiveMode.SNOW_ONLY, FreezerTestAccess.mode(loaded));
        assertEquals(37, FreezerTestAccess.progress(loaded));
        assertTrue(FreezerTestAccess.prefersIceNext(loaded));
    }

    private static void tick(FreezerBlockEntity freezer, KitchenUtilityService service, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            FreezerTestAccess.tickWithUtility(freezer, service);
        }
    }

    private static KitchenUtilityService service(boolean hasWaterProvider, SimpleContainer storage) {
        KitchenScanResult scan = new KitchenScanResult(true, Set.of(BlockPos.ZERO.below()), List.of(BlockPos.ZERO),
                List.of(BlockPos.ZERO), hasWaterProvider ? List.of(BlockPos.ZERO.east()) : List.of(),
                List.of(BlockPos.ZERO.west()));
        return new KitchenUtilityService(scan, new KitchenInventoryService(List.of(
                new KitchenInventoryEndpoint(BlockPos.ZERO, storage)
        )));
    }

    private static HolderLookup.Provider registries() {
        return HolderLookup.Provider.create(BuiltInRegistries.REGISTRY.stream()
                .map(registry -> ((Registry<?>) registry).asLookup()));
    }
}
