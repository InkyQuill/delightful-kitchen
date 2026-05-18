package net.inkyquill.delightfulkitchen.pouch;

public record FoodPouchFeedingResult(boolean consumed, int cooldownTicks) {
    public static FoodPouchFeedingResult consumed(boolean stillHungry) {
        return new FoodPouchFeedingResult(true, FoodPouchCooldowns.afterConsumption(stillHungry));
    }

    public static FoodPouchFeedingResult notConsumed() {
        return new FoodPouchFeedingResult(false, FoodPouchCooldowns.NO_CONSUMPTION);
    }
}
