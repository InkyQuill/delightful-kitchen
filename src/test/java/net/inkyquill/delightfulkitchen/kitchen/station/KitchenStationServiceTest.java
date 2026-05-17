package net.inkyquill.delightfulkitchen.kitchen.station;

import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitchenStationServiceTest {
    @BeforeAll
    static void bootStrapMinecraft() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void choosesLeastQueuedAcceptingStation() {
        FakeStation busy = new FakeStation(KitchenStationType.GRILL, 4, 3, true);
        FakeStation open = new FakeStation(KitchenStationType.GRILL, 4, 1, true);
        KitchenStationService service = new KitchenStationService(List.of(
                new KitchenStationEndpoint(new BlockPos(10, 64, 10), busy),
                new KitchenStationEndpoint(new BlockPos(5, 64, 5), open)
        ));

        Optional<KitchenStationEndpoint> endpoint = service.firstAccepting(job(KitchenStationType.GRILL));

        assertTrue(endpoint.isPresent());
        assertEquals(new BlockPos(5, 64, 5), endpoint.orElseThrow().pos());
    }

    @Test
    void skipsStationsThatCannotAccept() {
        KitchenStationService service = new KitchenStationService(List.of(
                new KitchenStationEndpoint(new BlockPos(1, 64, 1),
                        new FakeStation(KitchenStationType.GRILL, 1, 1, false))
        ));

        assertTrue(service.firstAccepting(job(KitchenStationType.GRILL)).isEmpty());
    }

    @Test
    void skipsStationsWithDifferentType() {
        KitchenStationService service = new KitchenStationService(List.of(
                new KitchenStationEndpoint(new BlockPos(1, 64, 1),
                        new FakeStation(KitchenStationType.GRILL, 4, 0, true))
        ));

        assertTrue(service.firstAccepting(job(KitchenStationType.CUTTING_BOARD)).isEmpty());
    }

    @Test
    void choosesLowerPositionWhenQueuedCountsTie() {
        BlockPos lower = new BlockPos(1, 64, 1);
        BlockPos higher = new BlockPos(2, 64, 2);
        KitchenStationService service = new KitchenStationService(List.of(
                new KitchenStationEndpoint(higher, new FakeStation(KitchenStationType.GRILL, 4, 1, true)),
                new KitchenStationEndpoint(lower, new FakeStation(KitchenStationType.GRILL, 4, 1, true))
        ));

        Optional<KitchenStationEndpoint> endpoint = service.firstAccepting(job(KitchenStationType.GRILL));

        assertTrue(endpoint.isPresent());
        assertEquals(lower, endpoint.orElseThrow().pos());
    }

    @Test
    void reportsWhetherStationTypeExists() {
        KitchenStationService service = new KitchenStationService(List.of(
                new KitchenStationEndpoint(new BlockPos(1, 64, 1),
                        new FakeStation(KitchenStationType.GRILL, 4, 0, true))
        ));

        assertTrue(service.hasStation(KitchenStationType.GRILL));
        assertFalse(service.hasStation(KitchenStationType.CRAFTING));
    }

    private static KitchenStationJob job(KitchenStationType type) {
        return new KitchenStationJob(
                ResourceLocation.fromNamespaceAndPath("delightful_kitchen", "test"),
                type,
                List.of(),
                List.of(new ItemStack(Items.COOKED_BEEF)),
                Optional.empty(),
                Optional.empty(),
                20
        );
    }

    private record FakeStation(KitchenStationType type, int capacity, int queued, boolean accepts)
            implements KitchenStation {
        @Override
        public KitchenStationType stationType() {
            return type;
        }

        @Override
        public int queueCapacity() {
            return capacity;
        }

        @Override
        public int queuedJobCount() {
            return queued;
        }

        @Override
        public KitchenStationStatus stationStatus() {
            return queued >= capacity ? KitchenStationStatus.QUEUE_FULL : KitchenStationStatus.IDLE;
        }

        @Override
        public KitchenStationAcceptResult canAccept(KitchenStationJob job) {
            return accepts && hasQueueSpace()
                    ? KitchenStationAcceptResult.acceptedResult()
                    : KitchenStationAcceptResult.blocked(PlanningBlocker.STATION_QUEUE_FULL);
        }

        @Override
        public KitchenStationAcceptResult accept(KitchenStationJob job) {
            return canAccept(job);
        }

        @Override
        public BlockPos stationPos() {
            return BlockPos.ZERO;
        }
    }
}
