# Chef's Counter Recipe Book Smoke Test

Manual smoke coverage for Milestone 4 Chef's Counter recipe book UI.

## Setup

Use a dev world with cheats enabled.

Prepare these test areas:

1. A standalone Chef's Counter with no connected Kitchen Floor.
2. A connected Kitchen Floor room with one Chef's Counter, a Cabinet of Holding, food ingredients, and enough station support to make at least one recipe ready or craftable with intermediates.
3. A connected Kitchen Floor room with two Chef's Counters.
4. A connected kitchen that is intentionally missing at least one requirement for a known food recipe, such as fuel, container, utility, station, or ingredient.

Keep a known searchable food recipe available in the ingredient kitchen. Bread, soup, or another simple edible recipe is enough as long as the output name is visible in the recipe grid.

## Checks

### Missing Kitchen Empty State

1. Open the standalone Chef's Counter that has no Kitchen Floor.
2. Verify the recipe list is empty.
3. Verify the screen shows the missing-kitchen empty state instead of recipe summaries.
4. Verify the confirm or queue action is unavailable.

### Recipe Summaries

1. Open the Chef's Counter in the kitchen with food ingredients.
2. Verify the recipe grid renders food recipe summaries.
3. Verify each visible summary has an output icon and a craftability state marker.
4. Hover a visible recipe and verify the tooltip includes the output name and state.

### Search

1. Type part of a visible recipe output display name into the search box.
2. Verify the list narrows to matching recipes.
3. Type a string that should not match any output name.
4. Verify the empty result state is shown without closing the screen.
5. Clear the search box and verify the full list returns.

### Craftability Filters

For each filter, verify every visible recipe matches the requested state:

1. All: ready, intermediate, and blocked recipes may appear.
2. Ready: only directly executable recipes appear.
3. Intermediate: only recipes craftable through intermediate station work appear.
4. Blocked: only recipes with blockers appear.

If the current kitchen does not naturally provide all three states, adjust ingredients, fuel, containers, utilities, or stations and reopen or refresh the Chef's Counter before retesting the missing state.

### Selection Details

1. Select a recipe in the grid.
2. Verify the details panel updates to that recipe.
3. Verify the details panel shows expected output, quantity, stations or steps when applicable, and any blockers for blocked plans.
4. Select a different recipe and verify stale details from the prior recipe are replaced.

### Quantity Refresh

1. Select a recipe that can be planned.
2. Increase the requested quantity.
3. Verify the expected output count and requested craft count refresh.
4. Continue increasing quantity until the kitchen can no longer satisfy the plan, when practical.
5. Verify blockers refresh for the selected quantity and the confirm button becomes disabled when the plan is blocked.
6. Decrease quantity back to a satisfiable value and verify the blockers clear when the plan is executable again.

### Confirm Queue

1. Select a ready or intermediate recipe with an executable plan.
2. Confirm or queue the plan.
3. Verify a queued result is shown.
4. Verify the recipe list refreshes after confirmation.
5. Verify consumed ingredients or queued station work match the selected plan and quantity.
6. Verify the selected recipe's craftability state updates if the queued plan changes available ingredients.

### Blocked Recipe

1. Open the intentionally blocked kitchen.
2. Select a blocked recipe.
3. Verify the details panel shows one or more blockers.
4. Verify the recipe tooltip includes the blocked state and first blocker.
5. Verify the confirm or queue button is disabled and cannot queue the blocked plan.

### Multiple Chef's Counters Warning

1. Open a Chef's Counter in the room with two connected Chef's Counters.
2. Verify the multiple Chef's Counters warning remains visible.
3. Search, change filters, select recipes, and change quantity.
4. Verify the warning remains visible throughout those interactions.

## Automated Coverage Notes

Existing unit tests cover stale detail matching and confirmability helpers in `ChefCounterScreenTest`.

Additional automated tests should be added when the screen exposes pure helpers without requiring a Minecraft client instance:

1. Client-side filtering by search text and craftability state.
2. Quantity clamping to the supported `1..64` range.
3. Blocked recipe tooltip text construction.

At the time this note was added, filtering, quantity clamping, and tooltip construction are still embedded in client widget or screen internals, so this task does not add test-only helper code.
