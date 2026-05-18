package net.inkyquill.delightfulkitchen.menu.chef;

import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;

import java.util.EnumSet;
import java.util.Objects;

public record ChefQueueResultView(boolean isQueued, EnumSet<PlanningBlocker> blockers) {
    public ChefQueueResultView {
        blockers = Objects.requireNonNull(blockers, "blockers").clone();
        if (isQueued && !blockers.isEmpty()) {
            throw new IllegalArgumentException("queued result cannot contain blockers");
        }
        if (!isQueued && blockers.isEmpty()) {
            throw new IllegalArgumentException("blocked result must contain at least one blocker");
        }
    }

    public static ChefQueueResultView queued() {
        return new ChefQueueResultView(true, EnumSet.noneOf(PlanningBlocker.class));
    }

    public static ChefQueueResultView blocked(EnumSet<PlanningBlocker> blockers) {
        return new ChefQueueResultView(false, blockers);
    }

    public static ChefQueueResultView blocked(PlanningBlocker blocker) {
        return blocked(EnumSet.of(blocker));
    }

    @Override
    public EnumSet<PlanningBlocker> blockers() {
        return blockers.clone();
    }
}
