package net.inkyquill.delightfulkitchen.pouch;

public final class FoodPouchCooldowns {
    public static final int CONSUMED_STILL_HUNGRY = 10;
    public static final int CONSUMED_FULL = 100;
    public static final int NO_CONSUMPTION = 100;

    private FoodPouchCooldowns() {
    }

    public static int afterConsumption(boolean stillHungry) {
        return stillHungry ? CONSUMED_STILL_HUNGRY : CONSUMED_FULL;
    }
}
