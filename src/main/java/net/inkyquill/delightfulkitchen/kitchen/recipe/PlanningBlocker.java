package net.inkyquill.delightfulkitchen.kitchen.recipe;

public enum PlanningBlocker {
    MISSING_RAW_INGREDIENT,
    MISSING_INTERMEDIATE_RECIPE,
    MISSING_STATION,
    MISSING_FUEL,
    MISSING_CONTAINER,
    OUTPUT_STORAGE_FULL,
    STATION_QUEUE_FULL,
    KITCHEN_CHANGED
}
