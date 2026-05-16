package net.inkyquill.cfbdelight.kitchen.recipe;

public enum PlanningBlocker {
    MISSING_RAW_INGREDIENT,
    MISSING_INTERMEDIATE_RECIPE,
    MISSING_STATION,
    MISSING_CONTAINER,
    MISSING_FUEL,
    FULL_OUTPUT_STORAGE,
    STATION_QUEUE_FULL,
    KITCHEN_CHANGED
}
