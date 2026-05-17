package net.inkyquill.delightfulkitchen.kitchen.station;

import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;

import java.util.EnumSet;
import java.util.Objects;

public record KitchenStationAcceptResult(boolean accepted, EnumSet<PlanningBlocker> blockers) {
    public KitchenStationAcceptResult {
        blockers = Objects.requireNonNull(blockers, "blockers").clone();
        if (accepted && !blockers.isEmpty()) {
            throw new IllegalArgumentException("accepted station result cannot contain blockers");
        }
        if (!accepted && blockers.isEmpty()) {
            throw new IllegalArgumentException("blocked station result must contain at least one blocker");
        }
    }

    public static KitchenStationAcceptResult acceptedResult() {
        return new KitchenStationAcceptResult(true, EnumSet.noneOf(PlanningBlocker.class));
    }

    public static KitchenStationAcceptResult blocked(PlanningBlocker blocker) {
        return new KitchenStationAcceptResult(false, EnumSet.of(blocker));
    }

    @Override
    public EnumSet<PlanningBlocker> blockers() {
        return blockers.clone();
    }
}
