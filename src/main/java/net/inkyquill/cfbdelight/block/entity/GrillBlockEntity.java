package net.inkyquill.cfbdelight.block.entity;

import net.blay09.mods.cookingforblockheads.api.IngredientToken;
import net.blay09.mods.cookingforblockheads.api.KitchenItemProcessor;
import net.blay09.mods.cookingforblockheads.api.KitchenOperation;
import net.blay09.mods.cookingforblockheads.capability.KitchenItemProcessorHolder;
import net.inkyquill.cfbdelight.block.GrillBlock;
import net.inkyquill.cfbdelight.menu.GrillMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Block entity for the Grill.
 * <p>
 * Slot layout:
 * <ul>
 *   <li>0–5  : input (cooking) slots</li>
 *   <li>6–11 : output slots (one per input slot)</li>
 * </ul>
 * Uses campfire cooking recipes – the same type as Farmer's Delight's Stove.
 */
public class GrillBlockEntity extends BlockEntity
        implements Container, KitchenItemProcessorHolder, MenuProvider, Nameable {

    public static final int GRILL_SLOTS      = 6;
    public static final int SLOT_COUNT       = GRILL_SLOTS * 2;
    public static final int SLOT_INPUT_START  = 0;
    public static final int SLOT_OUTPUT_START = GRILL_SLOTS;

    public static final int DEFAULT_COOK_TIME = 600;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final int[]       cookTimes    = new int[GRILL_SLOTS];
    private final int[]       cookTotals   = new int[GRILL_SLOTS];
    private final ItemStack[] pending      = new ItemStack[GRILL_SLOTS];
    @Nullable private Component customName = null;

    // Sync 6 cook times + 6 totals to the menu
    private final ContainerData data = new ContainerData() {
        @Override public int get(int i) { return i < GRILL_SLOTS ? cookTimes[i] : cookTotals[i - GRILL_SLOTS]; }
        @Override public void set(int i, int v) { if (i < GRILL_SLOTS) cookTimes[i] = v; else cookTotals[i - GRILL_SLOTS] = v; }
        @Override public int getCount() { return GRILL_SLOTS * 2; }
    };

    private final KitchenItemProcessor processor = new KitchenItemProcessor() {
        @Override
        public boolean canProcess(RecipeType<?> recipeType) {
            return recipeType == RecipeType.CAMPFIRE_COOKING;
        }

        @Override
        public KitchenOperation processRecipe(Recipe<?> recipe, List<IngredientToken> ingredientTokens) {
            if (!(recipe instanceof CampfireCookingRecipe campfire)) return KitchenOperation.EMPTY;

            int slot = -1;
            for (int i = 0; i < GRILL_SLOTS; i++) {
                if (items.get(SLOT_INPUT_START + i).isEmpty() && pending[i] == null) { slot = i; break; }
            }
            if (slot == -1) return KitchenOperation.EMPTY;

            if (ingredientTokens.isEmpty()) return KitchenOperation.EMPTY;
            ItemStack consumed = ingredientTokens.get(0).consume();
            if (consumed.isEmpty()) return KitchenOperation.EMPTY;

            items.set(SLOT_INPUT_START + slot, consumed);
            if (level != null) pending[slot] = campfire.getResultItem(level.registryAccess()).copy();
            cookTimes[slot]  = 0;
            cookTotals[slot] = campfire.getCookingTime();

            BlockState state = level != null ? level.getBlockState(worldPosition) : null;
            if (state != null && !state.getValue(GrillBlock.LIT))
                level.setBlock(worldPosition, state.setValue(GrillBlock.LIT, true), 3);

            setChanged();
            return KitchenOperation.EMPTY;
        }
    };

    public GrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRILL.get(), pos, state);
        Arrays.fill(cookTotals, DEFAULT_COOK_TIME);
    }

    // ------------------------------------------------------------------
    // Server tick
    // ------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, GrillBlockEntity be) {
        if (!state.getValue(GrillBlock.LIT)) return;

        boolean anyActive = false;
        for (int i = 0; i < GRILL_SLOTS; i++) {
            if (be.pending[i] == null) continue;
            anyActive = true;
            be.cookTimes[i]++;
            if (be.cookTimes[i] >= be.cookTotals[i]) be.finishSlot(i);
        }

        if (!anyActive) level.setBlock(pos, state.setValue(GrillBlock.LIT, false), 3);
        be.setChanged();
    }

    private void finishSlot(int i) {
        if (pending[i] == null) return;
        int outIdx = SLOT_OUTPUT_START + i;
        ItemStack existing = items.get(outIdx);
        if (existing.isEmpty()) {
            items.set(outIdx, pending[i]);
        } else if (ItemStack.isSameItemSameComponents(existing, pending[i])
                && existing.getCount() + pending[i].getCount() <= existing.getMaxStackSize()) {
            existing.grow(pending[i].getCount());
        } else {
            return; // output backed up; keep cooking time frozen until space frees
        }
        items.set(SLOT_INPUT_START + i, ItemStack.EMPTY);
        pending[i] = null;
        cookTimes[i] = 0;
    }

    // ------------------------------------------------------------------
    // Container
    // ------------------------------------------------------------------

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }

    @Override
    public void setItem(int slot, ItemStack stack) { items.set(slot, stack); setChanged(); }

    @Override
    public boolean stillValid(Player player) {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5,
                        worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override public void clearContent() { items.replaceAll(ignored -> ItemStack.EMPTY); setChanged(); }

    // ------------------------------------------------------------------
    // KitchenItemProcessorHolder
    // ------------------------------------------------------------------

    @Override public KitchenItemProcessor getKitchenItemProcessor() { return processor; }

    // ------------------------------------------------------------------
    // MenuProvider / Nameable
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("block.cfb_delight.grill");
    }

    @Override public Component getName() { return getDisplayName(); }
    @Override @Nullable public Component getCustomName() { return customName; }
    public void setCustomName(Component name) { this.customName = name; setChanged(); }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new GrillMenu(id, playerInventory, this, data,
                ContainerLevelAccess.create(level, worldPosition));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    public void dropContents(Level level, BlockPos pos) {
        for (ItemStack stack : items) {
            if (!stack.isEmpty())
                net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
        for (ItemStack p : pending) {
            if (p != null && !p.isEmpty())
                net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), p);
        }
        items.replaceAll(ignored -> ItemStack.EMPTY);
        Arrays.fill(pending, null);
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putIntArray("CookTimes",  cookTimes);
        tag.putIntArray("CookTotals", cookTotals);
        for (int i = 0; i < GRILL_SLOTS; i++) {
            if (pending[i] != null && !pending[i].isEmpty())
                tag.put("Pending" + i, pending[i].save(registries));
        }
        if (customName != null)
            tag.putString("CustomName", Component.Serializer.toJson(customName, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        int[] ct = tag.getIntArray("CookTimes"),  tot = tag.getIntArray("CookTotals");
        for (int i = 0; i < GRILL_SLOTS && i < ct.length;  i++) cookTimes[i]  = ct[i];
        for (int i = 0; i < GRILL_SLOTS && i < tot.length; i++) cookTotals[i] = tot[i];
        for (int i = 0; i < GRILL_SLOTS; i++) {
            if (tag.contains("Pending" + i))
                pending[i] = ItemStack.parse(registries, tag.getCompound("Pending" + i)).orElse(null);
        }
        if (tag.contains("CustomName"))
            customName = Component.Serializer.fromJson(tag.getString("CustomName"), registries);
    }
}
