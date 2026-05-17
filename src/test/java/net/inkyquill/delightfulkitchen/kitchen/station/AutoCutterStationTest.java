package net.inkyquill.delightfulkitchen.kitchen.station;

import net.inkyquill.delightfulkitchen.block.entity.AutoCutterBlockEntity;
import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoCutterStationTest {
    @BeforeAll
    static void bootStrapMinecraft() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void acceptsCuttingBoardJobIntoSingleSlotQueue() {
        KitchenStation station = assertInstanceOf(KitchenStation.class, newAutoCutter());

        KitchenStationAcceptResult result = station.accept(job(KitchenStationType.CUTTING_BOARD));

        assertTrue(result.accepted());
        assertEquals(KitchenStationType.CUTTING_BOARD, station.stationType());
        assertEquals(1, station.queueCapacity());
        assertEquals(1, station.queuedJobCount());
        assertEquals(KitchenStationStatus.RUNNING, station.stationStatus());
    }

    @Test
    void blocksWrongStationTypeAndFullQueue() {
        KitchenStation station = assertInstanceOf(KitchenStation.class, newAutoCutter());

        KitchenStationAcceptResult wrongType = station.canAccept(job(KitchenStationType.GRILL));
        station.accept(job(KitchenStationType.CUTTING_BOARD));
        KitchenStationAcceptResult full = station.canAccept(job(KitchenStationType.CUTTING_BOARD));

        assertEquals(List.of(PlanningBlocker.MISSING_STATION), List.copyOf(wrongType.blockers()));
        assertEquals(List.of(PlanningBlocker.STATION_QUEUE_FULL), List.copyOf(full.blockers()));
    }

    @Test
    void restoresBlockedQueuedOutputFromSavedPendingResults() {
        AutoCutterBlockEntity original = newAutoCutter();
        KitchenStation originalStation = assertInstanceOf(KitchenStation.class, original);
        originalStation.accept(job(KitchenStationType.CUTTING_BOARD));
        CompoundTag tag = original.saveCustomOnly(registries());
        tag.putInt("ActiveTimer", 0);
        tag.putBoolean("OutputBlocked", true);

        AutoCutterBlockEntity loaded = newAutoCutter();
        loaded.loadCustomOnly(tag, registries());
        KitchenStation loadedStation = assertInstanceOf(KitchenStation.class, loaded);

        assertEquals(1, loadedStation.queuedJobCount());
        assertEquals(KitchenStationStatus.OUTPUT_BLOCKED, loadedStation.stationStatus());
        assertEquals(List.of(PlanningBlocker.OUTPUT_STORAGE_FULL),
                List.copyOf(loadedStation.canAccept(job(KitchenStationType.CUTTING_BOARD)).blockers()));
    }

    private static AutoCutterBlockEntity newAutoCutter() {
        try {
            Constructor<AutoCutterBlockEntity> constructor = AutoCutterBlockEntity.class.getDeclaredConstructor(
                    BlockEntityType.class,
                    BlockPos.class,
                    net.minecraft.world.level.block.state.BlockState.class);
            constructor.setAccessible(true);
            return constructor.newInstance(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("failed to construct AutoCutterBlockEntity for unit test", exception);
        }
    }

    private static KitchenStationJob job(KitchenStationType type) {
        return new KitchenStationJob(
                ResourceLocation.fromNamespaceAndPath("delightful_kitchen", "auto_cutter_test"),
                type,
                List.of(new ItemStack(Items.OAK_LOG)),
                List.of(new ItemStack(Items.STICK, 2)),
                Optional.empty(),
                Optional.empty(),
                20
        );
    }

    private static HolderLookup.Provider registries() {
        return HolderLookup.Provider.create(BuiltInRegistries.REGISTRY.stream()
                .map(registry -> ((Registry<?>) registry).asLookup()));
    }
}
