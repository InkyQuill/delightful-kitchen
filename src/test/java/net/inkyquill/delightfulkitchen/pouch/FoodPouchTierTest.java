package net.inkyquill.delightfulkitchen.pouch;

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

class FoodPouchTierTest {
    @BeforeAll
    static void bootStrapMinecraft() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void exposesTierSlotLayoutAndMultiplier() {
        assertEquals(18, FoodPouchTier.BASIC.slots());
        assertEquals(1, FoodPouchTier.BASIC.stackMultiplier());
        assertEquals(6, FoodPouchTier.BASIC.columns());
        assertEquals(3, FoodPouchTier.BASIC.rows());

        assertEquals(36, FoodPouchTier.ADVANCED.slots());
        assertEquals(4, FoodPouchTier.ADVANCED.stackMultiplier());
        assertEquals(6, FoodPouchTier.ADVANCED.columns());
        assertEquals(6, FoodPouchTier.ADVANCED.rows());

        assertEquals(72, FoodPouchTier.ULTIMATE.slots());
        assertEquals(16, FoodPouchTier.ULTIMATE.stackMultiplier());
        assertEquals(9, FoodPouchTier.ULTIMATE.columns());
        assertEquals(8, FoodPouchTier.ULTIMATE.rows());
    }

    @Test
    void foodStackLimitUsesTierMultiplierAndCap() {
        assertEquals(64, FoodPouchTier.BASIC.slotLimit(new ItemStack(Items.BREAD)));
        assertEquals(256, FoodPouchTier.ADVANCED.slotLimit(new ItemStack(Items.BREAD)));
        assertEquals(1024, FoodPouchTier.ULTIMATE.slotLimit(new ItemStack(Items.BREAD)));
    }

    @Test
    void nonFoodStackLimitUsesVanillaMax() {
        assertEquals(64, FoodPouchTier.ULTIMATE.slotLimit(new ItemStack(Items.COBBLESTONE)));
        assertEquals(1, FoodPouchTier.ULTIMATE.slotLimit(new ItemStack(Items.WATER_BUCKET)));
    }
}
