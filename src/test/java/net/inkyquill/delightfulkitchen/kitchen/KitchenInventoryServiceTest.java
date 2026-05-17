package net.inkyquill.delightfulkitchen.kitchen;

import net.inkyquill.delightfulkitchen.kitchen.recipe.PlannedIngredient;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.items.IItemHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitchenInventoryServiceTest {
    @BeforeAll
    static void bootStrapMinecraft() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void extractsIngredientAcrossEndpoints() {
        SimpleContainer first = new SimpleContainer(new ItemStack(Items.WHEAT, 2));
        SimpleContainer second = new SimpleContainer(new ItemStack(Items.WHEAT, 2));
        KitchenInventoryService service = new KitchenInventoryService(List.of(
                new KitchenInventoryEndpoint(BlockPos.ZERO, first),
                new KitchenInventoryEndpoint(BlockPos.ZERO.above(), second)
        ));

        Optional<List<ItemStack>> extracted = service.extractIngredient(
                new PlannedIngredient(Ingredient.of(Items.WHEAT), 3), false);

        assertTrue(extracted.isPresent());
        assertEquals(3, extracted.orElseThrow().stream().mapToInt(ItemStack::getCount).sum());
        assertEquals(0, first.getItem(0).getCount());
        assertEquals(1, second.getItem(0).getCount());
    }

    @Test
    void failedExtractionDoesNotConsumeItems() {
        SimpleContainer container = new SimpleContainer(new ItemStack(Items.WHEAT, 1));
        KitchenInventoryService service = new KitchenInventoryService(List.of(
                new KitchenInventoryEndpoint(BlockPos.ZERO, container)
        ));

        Optional<List<ItemStack>> extracted = service.extractIngredient(
                new PlannedIngredient(Ingredient.of(Items.WHEAT), 2), false);

        assertTrue(extracted.isEmpty());
        assertEquals(1, container.getItem(0).getCount());
    }

    @Test
    void failedExtractionRestoresItemsToSourceSlot() {
        RejectWheatPlacementContainer container =
                new RejectWheatPlacementContainer(new ItemStack(Items.WHEAT, 1));
        KitchenInventoryService service = new KitchenInventoryService(List.of(
                new KitchenInventoryEndpoint(BlockPos.ZERO, container)
        ));

        Optional<List<ItemStack>> extracted = service.extractIngredient(
                new PlannedIngredient(Ingredient.of(Items.WHEAT), 2), false);

        assertTrue(extracted.isEmpty());
        assertEquals(1, container.getItem(0).getCount());
    }

    @Test
    void failedExtractionFromRejectingItemHandlerDoesNotMutate() {
        RejectingInsertItemHandler itemHandler = new RejectingInsertItemHandler(new ItemStack(Items.WHEAT, 1));
        KitchenInventoryService service = new KitchenInventoryService(List.of(
                new KitchenInventoryEndpoint(BlockPos.ZERO, itemHandler)
        ));

        Optional<List<ItemStack>> extracted = service.extractIngredient(
                new PlannedIngredient(Ingredient.of(Items.WHEAT), 2), false);

        assertTrue(extracted.isEmpty());
        assertEquals(1, itemHandler.getStackInSlot(0).getCount());
    }

    @Test
    void extractionMismatchRestoresCurrentTakenStack() {
        ShortRealExtractionItemHandler itemHandler = new ShortRealExtractionItemHandler(new ItemStack(Items.WHEAT, 2));
        KitchenInventoryService service = new KitchenInventoryService(List.of(
                new KitchenInventoryEndpoint(BlockPos.ZERO, itemHandler)
        ));

        assertThrows(IllegalStateException.class, () -> service.extractIngredient(
                new PlannedIngredient(Ingredient.of(Items.WHEAT), 2), false));
        assertEquals(2, itemHandler.getStackInSlot(0).getCount());
    }

    @Test
    void canInsertAllSimulatesWithoutMutating() {
        SimpleContainer container = new SimpleContainer(1);
        KitchenInventoryService service = new KitchenInventoryService(List.of(
                new KitchenInventoryEndpoint(BlockPos.ZERO, container)
        ));

        assertTrue(service.canInsertAll(List.of(new ItemStack(Items.BREAD))));
        assertTrue(container.getItem(0).isEmpty());
    }

    @Test
    void canInsertAllAccountsForCompetingStacks() {
        SimpleContainer container = new SimpleContainer(1);
        KitchenInventoryService service = new KitchenInventoryService(List.of(
                new KitchenInventoryEndpoint(BlockPos.ZERO, container)
        ));

        assertFalse(service.canInsertAll(List.of(new ItemStack(Items.BREAD), new ItemStack(Items.APPLE))));
        assertTrue(container.getItem(0).isEmpty());
    }

    @Test
    void canInsertAllPreservesEndpointPlacementRules() {
        RejectAppleContainer container = new RejectAppleContainer(1);
        KitchenInventoryService service = new KitchenInventoryService(List.of(
                new KitchenInventoryEndpoint(BlockPos.ZERO, container)
        ));

        assertFalse(service.canInsertAll(List.of(new ItemStack(Items.APPLE))));
        assertTrue(container.getItem(0).isEmpty());
    }

    @Test
    void canInsertAllPreservesEndpointSlotLimits() {
        MaxOneContainer container = new MaxOneContainer(1);
        KitchenInventoryService service = new KitchenInventoryService(List.of(
                new KitchenInventoryEndpoint(BlockPos.ZERO, container)
        ));

        assertFalse(service.canInsertAll(List.of(new ItemStack(Items.BREAD, 2))));
        assertTrue(container.getItem(0).isEmpty());
    }

    @Test
    void canInsertAllUsesItemHandlerSimulatedInsertion() {
        RejectAppleItemHandler itemHandler = new RejectAppleItemHandler();
        KitchenInventoryService service = new KitchenInventoryService(List.of(
                new KitchenInventoryEndpoint(BlockPos.ZERO, itemHandler)
        ));

        assertFalse(service.canInsertAll(List.of(new ItemStack(Items.APPLE))));
        assertTrue(itemHandler.getStackInSlot(0).isEmpty());
    }

    @Test
    void insertAllDoesNotPartiallyInsertWhenBatchCannotFit() {
        SimpleContainer container = new SimpleContainer(1);
        KitchenInventoryService service = new KitchenInventoryService(List.of(
                new KitchenInventoryEndpoint(BlockPos.ZERO, container)
        ));

        assertFalse(service.insertAll(List.of(new ItemStack(Items.BREAD), new ItemStack(Items.APPLE))));
        assertTrue(container.getItem(0).isEmpty());
    }

    @Test
    void insertAllDoesNotPartiallyInsertWhenItemHandlerRejectsSecondInsert() {
        EmptySlotOnlyItemHandler itemHandler = new EmptySlotOnlyItemHandler();
        KitchenInventoryService service = new KitchenInventoryService(List.of(
                new KitchenInventoryEndpoint(BlockPos.ZERO, itemHandler)
        ));

        assertFalse(service.insertAll(List.of(new ItemStack(Items.BREAD), new ItemStack(Items.BREAD))));
        assertTrue(itemHandler.getStackInSlot(0).isEmpty());
    }

    private static final class RejectAppleContainer extends SimpleContainer {
        RejectAppleContainer(int size) {
            super(size);
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return !stack.is(Items.APPLE);
        }
    }

    private static final class MaxOneContainer extends SimpleContainer {
        MaxOneContainer(int size) {
            super(size);
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }

    private static final class RejectWheatPlacementContainer extends SimpleContainer {
        RejectWheatPlacementContainer(ItemStack stack) {
            super(stack);
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return !stack.is(Items.WHEAT);
        }
    }

    private static final class RejectingInsertItemHandler implements IItemHandler {
        private ItemStack stack;

        private RejectingInsertItemHandler(ItemStack stack) {
            this.stack = stack.copy();
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return stack;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (stack.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            int extracted = Math.min(amount, stack.getCount());
            ItemStack result = stack.copyWithCount(extracted);
            if (!simulate) {
                stack.shrink(extracted);
                if (stack.isEmpty()) {
                    stack = ItemStack.EMPTY;
                }
            }
            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    }

    private static final class ShortRealExtractionItemHandler implements IItemHandler {
        private ItemStack stack;

        private ShortRealExtractionItemHandler(ItemStack stack) {
            this.stack = stack.copy();
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return stack;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (this.stack.isEmpty()) {
                if (!simulate) {
                    this.stack = stack.copy();
                }
                return ItemStack.EMPTY;
            }
            if (!ItemStack.isSameItemSameComponents(this.stack, stack)) {
                return stack;
            }
            if (!simulate) {
                this.stack.grow(stack.getCount());
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (stack.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            int extracted = simulate ? Math.min(amount, stack.getCount()) : 1;
            ItemStack result = stack.copyWithCount(extracted);
            if (!simulate) {
                stack.shrink(extracted);
                if (stack.isEmpty()) {
                    stack = ItemStack.EMPTY;
                }
            }
            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    }

    private static final class RejectAppleItemHandler implements IItemHandler {
        private ItemStack stack = ItemStack.EMPTY;

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return stack;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.is(Items.APPLE)) {
                return stack;
            }
            if (!simulate) {
                this.stack = stack.copy();
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    }

    private static final class EmptySlotOnlyItemHandler implements IItemHandler {
        private ItemStack stack = ItemStack.EMPTY;

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return stack;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!this.stack.isEmpty()) {
                return stack;
            }
            if (!simulate) {
                this.stack = stack.copy();
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    }
}
