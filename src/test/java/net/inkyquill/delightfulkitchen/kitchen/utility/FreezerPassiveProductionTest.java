package net.inkyquill.delightfulkitchen.kitchen.utility;

import net.inkyquill.delightfulkitchen.block.entity.FreezerBlockEntity;
import net.inkyquill.delightfulkitchen.block.entity.FreezerTestAccess;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
}
