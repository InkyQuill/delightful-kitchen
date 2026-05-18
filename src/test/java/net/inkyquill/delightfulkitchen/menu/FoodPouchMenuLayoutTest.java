package net.inkyquill.delightfulkitchen.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FoodPouchMenuLayoutTest {
    @Test
    void layoutForBasicPouchUsesSixByThreeGrid() {
        FoodPouchMenu.Layout layout = FoodPouchMenu.layoutFor(6, 3);

        assertEquals(18, layout.pouchSlots());
        assertEquals(86, layout.playerInventoryY());
        assertEquals(162, layout.imageHeight());
    }

    @Test
    void layoutForAdvancedPouchPushesPlayerInventoryBelowSixRows() {
        FoodPouchMenu.Layout layout = FoodPouchMenu.layoutFor(6, 6);

        assertEquals(36, layout.pouchSlots());
        assertEquals(140, layout.playerInventoryY());
        assertEquals(216, layout.imageHeight());
    }

    @Test
    void layoutForUltimatePouchUsesNineByEightGrid() {
        FoodPouchMenu.Layout layout = FoodPouchMenu.layoutFor(9, 8);

        assertEquals(72, layout.pouchSlots());
        assertEquals(176, layout.playerInventoryY());
        assertEquals(252, layout.imageHeight());
    }
}
