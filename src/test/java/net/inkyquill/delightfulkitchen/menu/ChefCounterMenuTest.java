package net.inkyquill.delightfulkitchen.menu;

import net.inkyquill.delightfulkitchen.kitchen.execution.KitchenPlanExecutionResult;
import net.inkyquill.delightfulkitchen.menu.chef.ChefCounterServerSession;
import net.inkyquill.delightfulkitchen.network.payload.ClientboundChefCounterDetailsPayload;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ChefCounterMenuTest {
    @Test
    void confirmationDetailsPayloadPreservesEmptyDetails() {
        ChefCounterServerSession.Confirmation confirmation = new ChefCounterServerSession.Confirmation(
                KitchenPlanExecutionResult.queuedResult(), Optional.empty());

        ClientboundChefCounterDetailsPayload payload = ChefCounterMenu.detailsPayload(7, confirmation);

        assertFalse(payload.details().isPresent());
    }
}
