package net.inkyquill.delightfulkitchen.pouch;

import com.mojang.serialization.DynamicOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoodPouchInventoryTest {
    private static DynamicOps<Tag> ops;

    @BeforeAll
    static void bootStrapMinecraft() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = registries().createSerializationContext(NbtOps.INSTANCE);
    }

    @Test
    void emptyInventoryHasRequestedSlots() {
        FoodPouchInventory inventory = FoodPouchInventory.empty(FoodPouchTier.BASIC.slots());

        assertEquals(18, inventory.size());
        assertEquals(0, inventory.usedSlots());
        assertTrue(inventory.get(0).isEmpty());
    }

    @Test
    void storesAndExposesCopies() {
        ItemStack original = new ItemStack(Items.BREAD, 7);
        FoodPouchInventory inventory = FoodPouchInventory.empty(1).withSlot(0, original);
        original.setCount(1);

        ItemStack exposed = inventory.get(0);
        exposed.setCount(2);

        assertEquals(7, inventory.get(0).getCount());
        assertNotSame(exposed, inventory.get(0));
    }

    @Test
    void codecRoundTripPreservesLargeFoodStackCounts() {
        FoodPouchInventory inventory = FoodPouchInventory.empty(FoodPouchTier.ULTIMATE.slots())
                .withSlot(0, new ItemStack(Items.BREAD, 1024));

        Tag encoded = FoodPouchInventory.CODEC.encodeStart(ops, inventory).getOrThrow();
        FoodPouchInventory decoded = FoodPouchInventory.CODEC.parse(ops, encoded).getOrThrow();

        assertEquals(FoodPouchTier.ULTIMATE.slots(), decoded.size());
        assertEquals(1024, decoded.get(0).getCount());
    }

    @Test
    void normalizedClampsSlotCountAndItemLimitsForTier() {
        FoodPouchInventory inventory = new FoodPouchInventory(List.of(
                new ItemStack(Items.BREAD, 300),
                new ItemStack(Items.COBBLESTONE, 300)
        ));

        FoodPouchInventory normalized = FoodPouchInventoryOps.normalized(inventory, FoodPouchTier.ADVANCED);

        assertEquals(36, normalized.size());
        assertEquals(256, normalized.get(0).getCount());
        assertEquals(64, normalized.get(1).getCount());
        assertTrue(normalized.get(2).isEmpty());
    }

    @Test
    void insertMergesExistingSlotsThenUsesEmptySlots() {
        FoodPouchInventory inventory = FoodPouchInventory.empty(3)
                .withSlot(0, new ItemStack(Items.BREAD, 250))
                .withSlot(1, new ItemStack(Items.APPLE, 1));

        FoodPouchInventoryOps.InsertResult result =
                FoodPouchInventoryOps.insert(inventory, FoodPouchTier.ADVANCED, new ItemStack(Items.BREAD, 20));

        assertTrue(result.remainder().isEmpty());
        assertEquals(256, result.inventory().get(0).getCount());
        assertEquals(14, result.inventory().get(2).getCount());
        assertEquals(250, inventory.get(0).getCount());
    }

    @Test
    void insertReturnsRemainderWhenFull() {
        FoodPouchInventory inventory = FoodPouchInventory.empty(FoodPouchTier.ADVANCED.slots())
                .withSlot(0, new ItemStack(Items.BREAD, 250));
        for (int slot = 1; slot < FoodPouchTier.ADVANCED.slots(); slot++) {
            inventory = inventory.withSlot(slot, new ItemStack(Items.BREAD, 256));
        }

        FoodPouchInventoryOps.InsertResult result =
                FoodPouchInventoryOps.insert(inventory, FoodPouchTier.ADVANCED, new ItemStack(Items.BREAD, 20));

        assertEquals(256, result.inventory().get(0).getCount());
        assertEquals(14, result.remainder().getCount());
    }

    @Test
    void insertSplitsLargeFoodStackAcrossEmptySlots() {
        FoodPouchInventory inventory = FoodPouchInventory.empty(3);

        FoodPouchInventoryOps.InsertResult result =
                FoodPouchInventoryOps.insert(inventory, FoodPouchTier.ADVANCED, new ItemStack(Items.BREAD, 600));

        assertTrue(result.remainder().isEmpty());
        assertEquals(256, result.inventory().get(0).getCount());
        assertEquals(256, result.inventory().get(1).getCount());
        assertEquals(88, result.inventory().get(2).getCount());
    }

    @Test
    void extractBySlotReturnsUpdatedInventoryWithoutMutation() {
        FoodPouchInventory inventory = FoodPouchInventory.empty(1)
                .withSlot(0, new ItemStack(Items.BREAD, 12));

        FoodPouchInventoryOps.ExtractResult result = FoodPouchInventoryOps.extract(inventory, 0, 5);

        assertEquals(5, result.extracted().getCount());
        assertEquals(7, result.inventory().get(0).getCount());
        assertEquals(12, inventory.get(0).getCount());
    }

    @Test
    void extractFromInvalidSlotReturnsEmptyAndDoesNotMutate() {
        FoodPouchInventory inventory = FoodPouchInventory.empty(1)
                .withSlot(0, new ItemStack(Items.BREAD, 12));

        FoodPouchInventoryOps.ExtractResult result = FoodPouchInventoryOps.extract(inventory, 2, 5);

        assertTrue(result.extracted().isEmpty());
        assertEquals(12, result.inventory().get(0).getCount());
        assertEquals(12, inventory.get(0).getCount());
    }

    @Test
    void usedSlotsIgnoresEmptyStacks() {
        FoodPouchInventory inventory = FoodPouchInventory.empty(3)
                .withSlot(0, new ItemStack(Items.BREAD, 1))
                .withSlot(2, new ItemStack(Items.COBBLESTONE, 1));

        assertEquals(2, inventory.usedSlots());
    }

    private static HolderLookup.Provider registries() {
        return HolderLookup.Provider.create(BuiltInRegistries.REGISTRY.stream()
                .map(registry -> ((Registry<?>) registry).asLookup()));
    }
}
