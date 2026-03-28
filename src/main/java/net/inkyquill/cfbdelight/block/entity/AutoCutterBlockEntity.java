package net.inkyquill.cfbdelight.block.entity;

import net.blay09.mods.cookingforblockheads.api.IngredientToken;
import net.blay09.mods.cookingforblockheads.api.KitchenItemProcessor;
import net.blay09.mods.cookingforblockheads.api.KitchenOperation;
import net.blay09.mods.cookingforblockheads.capability.KitchenItemProcessorHolder;
import net.inkyquill.cfbdelight.block.AutoCutterBlock;
import net.inkyquill.cfbdelight.menu.AutoCutterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

import java.util.List;

/**
 * Block entity for the Auto Cutter.
 * <p>
 * Slot layout:
 * <ul>
 *   <li>0   : input item</li>
 *   <li>1–4 : output slots (up to 4 chance-rolled results)</li>
 * </ul>
 */
public class AutoCutterBlockEntity extends BlockEntity
        implements Container, KitchenItemProcessorHolder, MenuProvider, Nameable {

    public static final int SLOT_COUNT       = 5;
    public static final int SLOT_INPUT       = 0;
    public static final int SLOT_OUTPUT_START = 1;
    public static final int SLOT_OUTPUT_END   = 4;

    private static final int ACTIVE_TICKS = 10;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int activeTimer = 0;
    @Nullable private Component customName = null;

    private final KitchenItemProcessor processor = new KitchenItemProcessor() {
        @Override
        public boolean canProcess(RecipeType<?> recipeType) {
            return recipeType == ModRecipeTypes.CUTTING.get();
        }

        @Override
        public KitchenOperation processRecipe(Recipe<?> recipe, List<IngredientToken> ingredientTokens) {
            if (!(recipe instanceof CuttingBoardRecipe cuttingRecipe)) return KitchenOperation.EMPTY;

            ItemStack consumed = ingredientTokens.isEmpty() ? ItemStack.EMPTY
                    : ingredientTokens.get(0).consume();
            if (consumed.isEmpty()) return KitchenOperation.EMPTY;

            if (level != null) {
                RandomSource rand = level.getRandom();
                List<ItemStack> results = cuttingRecipe.rollResults(rand, 0);
                int outSlot = SLOT_OUTPUT_START;
                for (ItemStack result : results) {
                    if (outSlot > SLOT_OUTPUT_END) break;
                    ItemStack existing = items.get(outSlot);
                    if (existing.isEmpty()) {
                        items.set(outSlot, result.copy());
                        outSlot++;
                    } else if (ItemStack.isSameItemSameComponents(existing, result)
                            && existing.getCount() + result.getCount() <= existing.getMaxStackSize()) {
                        existing.grow(result.getCount());
                        outSlot++;
                    }
                }
            }
            activeTimer = ACTIVE_TICKS;
            setChanged();
            return KitchenOperation.EMPTY;
        }
    };

    public AutoCutterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AUTO_CUTTER.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // Server tick
    // ------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, AutoCutterBlockEntity be) {
        if (be.activeTimer > 0) {
            be.activeTimer--;
            if (be.activeTimer == 0)
                level.setBlock(pos, state.setValue(AutoCutterBlock.ACTIVE, false), 3);
            be.setChanged();
        }
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
        return customName != null ? customName : Component.translatable("block.cfb_delight.auto_cutter");
    }

    @Override public Component getName() { return getDisplayName(); }
    @Override @Nullable public Component getCustomName() { return customName; }
    public void setCustomName(Component name) { this.customName = name; setChanged(); }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new AutoCutterMenu(id, playerInventory, this,
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
        items.replaceAll(ignored -> ItemStack.EMPTY);
    }

    public boolean isActive() { return activeTimer > 0; }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("ActiveTimer", activeTimer);
        if (customName != null)
            tag.putString("CustomName", Component.Serializer.toJson(customName, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        activeTimer = tag.getInt("ActiveTimer");
        if (tag.contains("CustomName"))
            customName = Component.Serializer.fromJson(tag.getString("CustomName"), registries);
    }
}
