package net.inkyquill.delightfulkitchen.kitchen.station;

import net.inkyquill.delightfulkitchen.block.entity.GrillBlockEntity;
import net.inkyquill.delightfulkitchen.block.entity.GrillTestAccess;
import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrillStationTest {
    @BeforeAll
    static void bootStrapMinecraft() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void acceptsGrillJobIntoMultiSlotQueueAndStoresFuel() {
        GrillBlockEntity grill = newGrill();
        KitchenStation station = assertInstanceOf(KitchenStation.class, grill);

        KitchenStationAcceptResult result = station.accept(job(KitchenStationType.GRILL));

        assertTrue(result.accepted());
        assertEquals(KitchenStationType.GRILL, station.stationType());
        assertEquals(GrillBlockEntity.GRILL_SLOTS, station.queueCapacity());
        assertEquals(1, station.queuedJobCount());
        assertEquals(KitchenStationStatus.RUNNING, station.stationStatus());
        assertEquals(Items.COAL, grill.getItem(GrillBlockEntity.SLOT_FUEL).getItem());
        assertEquals(2, grill.getItem(GrillBlockEntity.SLOT_FUEL).getCount());
    }

    @Test
    void blocksWrongStationTypeFullQueueAndBlockedOutput() {
        GrillBlockEntity grill = newGrill();
        KitchenStation station = assertInstanceOf(KitchenStation.class, grill);

        KitchenStationAcceptResult wrongType = station.canAccept(job(KitchenStationType.CUTTING_BOARD));
        for (int i = 0; i < GrillBlockEntity.GRILL_SLOTS; i++) {
            station.accept(job(KitchenStationType.GRILL));
        }
        KitchenStationAcceptResult full = station.canAccept(job(KitchenStationType.GRILL));

        GrillBlockEntity blockedGrill = newGrill();
        KitchenStation blockedStation = assertInstanceOf(KitchenStation.class, blockedGrill);
        blockedStation.accept(job(KitchenStationType.GRILL));
        GrillTestAccess.finishQueuedSlotWithRemainder(blockedGrill, 0, new ItemStack(Items.COOKED_BEEF));
        KitchenStationAcceptResult blocked = blockedStation.canAccept(job(KitchenStationType.GRILL));

        assertEquals(List.of(PlanningBlocker.MISSING_STATION), List.copyOf(wrongType.blockers()));
        assertEquals(List.of(PlanningBlocker.STATION_QUEUE_FULL), List.copyOf(full.blockers()));
        assertEquals(List.of(PlanningBlocker.OUTPUT_STORAGE_FULL), List.copyOf(blocked.blockers()));
        assertEquals(KitchenStationStatus.OUTPUT_BLOCKED, blockedStation.stationStatus());
    }

    @Test
    void blocksFuelThatCannotFitTheFuelSlot() {
        GrillBlockEntity grill = newGrill();
        grill.setItem(GrillBlockEntity.SLOT_FUEL, new ItemStack(Items.CHARCOAL, 64));
        KitchenStation station = assertInstanceOf(KitchenStation.class, grill);

        KitchenStationAcceptResult result = station.canAccept(job(KitchenStationType.GRILL));

        assertEquals(List.of(PlanningBlocker.STATION_QUEUE_FULL), List.copyOf(result.blockers()));
    }

    @Test
    void persistsQueuedSlotsSeparatelyFromManualPendingOutput() {
        GrillBlockEntity queued = newGrill();
        KitchenStation queuedStation = assertInstanceOf(KitchenStation.class, queued);
        queuedStation.accept(job(KitchenStationType.GRILL));
        CompoundTag queuedTag = queued.saveCustomOnly(registries());

        GrillBlockEntity loadedQueued = newGrill();
        loadedQueued.loadCustomOnly(queuedTag, registries());
        KitchenStation loadedQueuedStation = assertInstanceOf(KitchenStation.class, loadedQueued);

        CompoundTag manualTag = new CompoundTag();
        manualTag.put("Pending0", new ItemStack(Items.COOKED_BEEF).save(registries()));
        manualTag.putIntArray("CookTimes", new int[]{10, 0, 0, 0, 0, 0});
        manualTag.putIntArray("CookTotals", new int[]{100, 600, 600, 600, 600, 600});
        manualTag.putIntArray("QueuedSlots", new int[]{0, 0, 0, 0, 0, 0});
        GrillBlockEntity loadedManual = newGrill();
        loadedManual.loadCustomOnly(manualTag, registries());
        KitchenStation loadedManualStation = assertInstanceOf(KitchenStation.class, loadedManual);

        assertEquals(1, loadedQueuedStation.queuedJobCount());
        assertEquals(KitchenStationStatus.RUNNING, loadedQueuedStation.stationStatus());
        assertArrayEquals(new int[]{1, 0, 0, 0, 0, 0}, queuedTag.getIntArray("QueuedSlots"));
        assertEquals(0, loadedManualStation.queuedJobCount());
        assertEquals(KitchenStationStatus.RUNNING, loadedManualStation.stationStatus());
    }

    @Test
    void blockedQueuedOutputStaysPendingAndDoesNotUseInternalOutputSlot() {
        GrillBlockEntity grill = newGrill();
        KitchenStation station = assertInstanceOf(KitchenStation.class, grill);
        station.accept(job(KitchenStationType.GRILL));

        GrillTestAccess.finishQueuedSlotWithRemainder(grill, 0, new ItemStack(Items.COOKED_BEEF));
        CompoundTag tag = grill.saveCustomOnly(registries());

        assertEquals(1, station.queuedJobCount());
        assertEquals(KitchenStationStatus.OUTPUT_BLOCKED, station.stationStatus());
        assertTrue(grill.getItem(GrillBlockEntity.SLOT_OUTPUT_START).isEmpty());
        assertArrayEquals(new int[]{1, 0, 0, 0, 0, 0}, tag.getIntArray("QueuedSlots"));
        assertArrayEquals(new int[]{1, 0, 0, 0, 0, 0}, tag.getIntArray("OutputBlocked"));
    }

    private static GrillBlockEntity newGrill() {
        return GrillTestAccess.newGrill();
    }

    private static KitchenStationJob job(KitchenStationType type) {
        return new KitchenStationJob(
                ResourceLocation.fromNamespaceAndPath("delightful_kitchen", "grill_test"),
                type,
                List.of(new ItemStack(Items.BEEF)),
                List.of(new ItemStack(Items.COOKED_BEEF)),
                Optional.empty(),
                Optional.of(new ItemStack(Items.COAL, 2)),
                20
        );
    }

    private static HolderLookup.Provider registries() {
        return HolderLookup.Provider.create(BuiltInRegistries.REGISTRY.stream()
                .map(registry -> ((Registry<?>) registry).asLookup()));
    }
}
