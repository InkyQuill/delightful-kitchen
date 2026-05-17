package net.inkyquill.delightfulkitchen.kitchen.station;

import net.inkyquill.delightfulkitchen.block.entity.PotStandBlockEntity;
import net.inkyquill.delightfulkitchen.block.entity.PotStandTestAccess;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PotStandStationTest {
    @BeforeAll
    static void bootStrapMinecraft() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void acceptsCookingPotJobIntoSingleSlotQueueAndStoresVisibleInputsContainerAndFuel() {
        PotStandBlockEntity potStand = newPotStand();
        KitchenStation station = assertInstanceOf(KitchenStation.class, potStand);

        KitchenStationAcceptResult result = station.accept(job(KitchenStationType.COOKING_POT));

        assertTrue(result.accepted());
        assertEquals(KitchenStationType.COOKING_POT, station.stationType());
        assertEquals(1, station.queueCapacity());
        assertEquals(1, station.queuedJobCount());
        assertEquals(KitchenStationStatus.QUEUE_FULL, station.stationStatus());
        assertEquals(Items.BEEF, potStand.getItem(0).getItem());
        assertEquals(Items.CARROT, potStand.getItem(1).getItem());
        assertEquals(Items.BOWL, potStand.getItem(PotStandBlockEntity.SLOT_CONTAINER).getItem());
        assertEquals(Items.COAL, potStand.getItem(PotStandBlockEntity.SLOT_FUEL).getItem());
        assertEquals(2, potStand.getItem(PotStandBlockEntity.SLOT_FUEL).getCount());
    }

    @Test
    void blocksWrongStationTypeFullQueueBlockedOutputAndOverflowingFuel() {
        PotStandBlockEntity potStand = newPotStand();
        KitchenStation station = assertInstanceOf(KitchenStation.class, potStand);

        KitchenStationAcceptResult wrongType = station.canAccept(job(KitchenStationType.GRILL));
        station.accept(job(KitchenStationType.COOKING_POT));
        KitchenStationAcceptResult full = station.canAccept(job(KitchenStationType.COOKING_POT));

        PotStandBlockEntity blockedPotStand = newPotStand();
        KitchenStation blockedStation = assertInstanceOf(KitchenStation.class, blockedPotStand);
        blockedStation.accept(job(KitchenStationType.COOKING_POT));
        blockedPotStand.loadCustomOnly(blockedOutputTag(), registries());
        KitchenStationAcceptResult blocked = blockedStation.canAccept(job(KitchenStationType.COOKING_POT));

        PotStandBlockEntity fullFuelPotStand = newPotStand();
        fullFuelPotStand.setItem(PotStandBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 64));
        KitchenStation fullFuelStation = assertInstanceOf(KitchenStation.class, fullFuelPotStand);
        KitchenStationAcceptResult overflowingFuel = fullFuelStation.canAccept(job(KitchenStationType.COOKING_POT));

        assertEquals(List.of(PlanningBlocker.MISSING_STATION), List.copyOf(wrongType.blockers()));
        assertEquals(List.of(PlanningBlocker.STATION_QUEUE_FULL), List.copyOf(full.blockers()));
        assertEquals(List.of(PlanningBlocker.OUTPUT_STORAGE_FULL), List.copyOf(blocked.blockers()));
        assertEquals(List.of(PlanningBlocker.STATION_QUEUE_FULL), List.copyOf(overflowingFuel.blockers()));
        assertEquals(KitchenStationStatus.OUTPUT_BLOCKED, blockedStation.stationStatus());
    }

    @Test
    void persistsQueuedPendingOutputSeparatelyFromManualPendingOutput() {
        PotStandBlockEntity queued = newPotStand();
        KitchenStation queuedStation = assertInstanceOf(KitchenStation.class, queued);
        queuedStation.accept(job(KitchenStationType.COOKING_POT));
        CompoundTag queuedTag = queued.saveCustomOnly(registries());

        PotStandBlockEntity loadedQueued = newPotStand();
        loadedQueued.loadCustomOnly(queuedTag, registries());
        KitchenStation loadedQueuedStation = assertInstanceOf(KitchenStation.class, loadedQueued);

        CompoundTag manualTag = new CompoundTag();
        manualTag.put("PendingOutput", new ItemStack(Items.MUSHROOM_STEW).save(registries()));
        manualTag.putInt("CookTime", 10);
        manualTag.putInt("CookTimeTotal", 100);
        manualTag.putBoolean("QueuedJobActive", false);
        PotStandBlockEntity loadedManual = newPotStand();
        loadedManual.loadCustomOnly(manualTag, registries());
        KitchenStation loadedManualStation = assertInstanceOf(KitchenStation.class, loadedManual);

        assertTrue(queuedTag.getBoolean("QueuedJobActive"));
        assertEquals(1, loadedQueuedStation.queuedJobCount());
        assertEquals(KitchenStationStatus.QUEUE_FULL, loadedQueuedStation.stationStatus());
        assertEquals(0, loadedManualStation.queuedJobCount());
        assertEquals(KitchenStationStatus.QUEUE_FULL, loadedManualStation.stationStatus());
        assertEquals(List.of(PlanningBlocker.STATION_QUEUE_FULL),
                List.copyOf(loadedManualStation.canAccept(job(KitchenStationType.COOKING_POT)).blockers()));
    }

    @Test
    void blockedQueuedOutputStaysPendingWhenInternalOutputCannotAcceptRemainder() {
        PotStandBlockEntity potStand = newPotStand();
        KitchenStation station = assertInstanceOf(KitchenStation.class, potStand);
        station.accept(job(KitchenStationType.COOKING_POT));
        potStand.setItem(PotStandBlockEntity.SLOT_OUTPUT, new ItemStack(Items.STONE));

        PotStandTestAccess.finishQueuedCookingWithRemainder(potStand, new ItemStack(Items.MUSHROOM_STEW));
        CompoundTag tag = potStand.saveCustomOnly(registries());

        assertEquals(1, station.queuedJobCount());
        assertEquals(KitchenStationStatus.OUTPUT_BLOCKED, station.stationStatus());
        assertTrue(tag.getBoolean("QueuedJobActive"));
        assertTrue(tag.getBoolean("OutputBlocked"));
        assertTrue(tag.contains("PendingOutput"));
    }

    @Test
    void queuedInputsAndContainerCannotBeRemovedOrReplacedThroughContainerMethods() {
        PotStandBlockEntity potStand = newPotStand();
        KitchenStation station = assertInstanceOf(KitchenStation.class, potStand);
        station.accept(job(KitchenStationType.COOKING_POT));

        ItemStack removedInput = potStand.removeItem(0, 1);
        ItemStack removedContainer = potStand.removeItemNoUpdate(PotStandBlockEntity.SLOT_CONTAINER);
        potStand.setItem(1, new ItemStack(Items.DIAMOND));
        potStand.setItem(PotStandBlockEntity.SLOT_CONTAINER, new ItemStack(Items.GLASS_BOTTLE));

        assertTrue(removedInput.isEmpty());
        assertTrue(removedContainer.isEmpty());
        assertEquals(Items.BEEF, potStand.getItem(0).getItem());
        assertEquals(Items.CARROT, potStand.getItem(1).getItem());
        assertEquals(Items.BOWL, potStand.getItem(PotStandBlockEntity.SLOT_CONTAINER).getItem());
    }

    @Test
    void queuedDisplaySlotsDisallowMenuPickupButManualSlotsRemainPickable() {
        PotStandBlockEntity queued = newPotStand();
        KitchenStation station = assertInstanceOf(KitchenStation.class, queued);
        station.accept(job(KitchenStationType.COOKING_POT));

        PotStandBlockEntity manual = newPotStand();
        manual.setItem(0, new ItemStack(Items.BEEF));
        manual.setItem(PotStandBlockEntity.SLOT_CONTAINER, new ItemStack(Items.BOWL));

        assertFalse(queued.canPlayerTakeItem(0));
        assertFalse(queued.canPlayerTakeItem(PotStandBlockEntity.SLOT_CONTAINER));
        assertTrue(queued.canPlayerTakeItem(PotStandBlockEntity.SLOT_OUTPUT));
        assertTrue(queued.canPlayerTakeItem(PotStandBlockEntity.SLOT_FUEL));
        assertTrue(manual.canPlayerTakeItem(0));
        assertTrue(manual.canPlayerTakeItem(PotStandBlockEntity.SLOT_CONTAINER));
    }

    @Test
    void queuedBreakDropsPendingOutputButNotConsumedVisibleInputsOrContainer() {
        PotStandBlockEntity potStand = newPotStand();
        KitchenStation station = assertInstanceOf(KitchenStation.class, potStand);
        station.accept(job(KitchenStationType.COOKING_POT));

        List<ItemStack> drops = PotStandTestAccess.breakDrops(potStand);

        assertTrue(contains(drops, Items.MUSHROOM_STEW));
        assertFalse(contains(drops, Items.BEEF));
        assertFalse(contains(drops, Items.CARROT));
        assertFalse(contains(drops, Items.BOWL));
    }

    @Test
    void queuedOutputFallbackPartiallyMergesIntoInternalOutputSlotAndKeepsOnlyRemainderBlocked() {
        PotStandBlockEntity potStand = newPotStand();
        KitchenStation station = assertInstanceOf(KitchenStation.class, potStand);
        station.accept(multiOutputJob());
        potStand.setItem(PotStandBlockEntity.SLOT_OUTPUT, new ItemStack(Items.STICK, 63));

        PotStandTestAccess.finishQueuedCookingWithRemainder(potStand, new ItemStack(Items.STICK, 3));
        CompoundTag tag = potStand.saveCustomOnly(registries());

        assertEquals(64, potStand.getItem(PotStandBlockEntity.SLOT_OUTPUT).getCount());
        assertEquals(1, station.queuedJobCount());
        assertEquals(KitchenStationStatus.OUTPUT_BLOCKED, station.stationStatus());
        assertEquals(2, ItemStack.parse(registries(), tag.getCompound("PendingOutput")).orElseThrow().getCount());
    }

    private static PotStandBlockEntity newPotStand() {
        return PotStandTestAccess.newPotStand();
    }

    private static KitchenStationJob job(KitchenStationType type) {
        return new KitchenStationJob(
                ResourceLocation.fromNamespaceAndPath("delightful_kitchen", "pot_stand_test"),
                type,
                List.of(new ItemStack(Items.BEEF), new ItemStack(Items.CARROT)),
                List.of(new ItemStack(Items.MUSHROOM_STEW)),
                Optional.of(new ItemStack(Items.BOWL)),
                Optional.of(new ItemStack(Items.COAL, 2)),
                20
        );
    }

    private static KitchenStationJob multiOutputJob() {
        return new KitchenStationJob(
                ResourceLocation.fromNamespaceAndPath("delightful_kitchen", "pot_stand_multi_output_test"),
                KitchenStationType.COOKING_POT,
                List.of(new ItemStack(Items.BEEF), new ItemStack(Items.CARROT)),
                List.of(new ItemStack(Items.STICK, 3)),
                Optional.of(new ItemStack(Items.BOWL)),
                Optional.of(new ItemStack(Items.COAL, 2)),
                20
        );
    }

    private static boolean contains(List<ItemStack> stacks, net.minecraft.world.item.Item item) {
        return stacks.stream().anyMatch(stack -> stack.is(item));
    }

    private static CompoundTag blockedOutputTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("PendingOutput", new ItemStack(Items.MUSHROOM_STEW).save(registries()));
        tag.putInt("CookTime", 20);
        tag.putInt("CookTimeTotal", 20);
        tag.putBoolean("QueuedJobActive", true);
        tag.putBoolean("OutputBlocked", true);
        return tag;
    }

    private static HolderLookup.Provider registries() {
        return HolderLookup.Provider.create(BuiltInRegistries.REGISTRY.stream()
                .map(registry -> ((Registry<?>) registry).asLookup()));
    }
}
