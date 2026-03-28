package net.inkyquill.cfbdelight.block.entity;

import net.blay09.mods.cookingforblockheads.api.IngredientToken;
import net.blay09.mods.cookingforblockheads.api.KitchenItemProcessor;
import net.blay09.mods.cookingforblockheads.api.KitchenOperation;
import net.blay09.mods.cookingforblockheads.capability.KitchenItemProcessorHolder;
import net.inkyquill.cfbdelight.block.PotStandBlock;
import net.inkyquill.cfbdelight.menu.PotStandMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

import java.util.List;

/**
 * Block entity for the Pot Stand.
 * <p>
 * Slot layout:
 * <ul>
 *   <li>0–5 : ingredient inputs</li>
 *   <li>6   : container slot (bowl / bottle)</li>
 *   <li>7   : output slot (cooked meal)</li>
 * </ul>
 */
public class PotStandBlockEntity extends BlockEntity
        implements Container, KitchenItemProcessorHolder, MenuProvider, Nameable {

    public static final int SLOT_COUNT     = 8;
    public static final int INPUT_SLOTS    = 6;
    public static final int SLOT_CONTAINER = 6;
    public static final int SLOT_OUTPUT    = 7;
    public static final int DEFAULT_COOK_TIME = 200;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int cookTime      = 0;
    private int cookTimeTotal = DEFAULT_COOK_TIME;
    @Nullable private ItemStack pendingOutput = null;
    @Nullable private Component customName    = null;

    // ContainerData wired to the menu for client syncing
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> cookTime;
                case 1 -> cookTimeTotal;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> cookTime      = value;
                case 1 -> cookTimeTotal = value;
            }
        }
        @Override public int getCount() { return 2; }
    };

    // KitchenItemProcessor exposed to CfB
    private final KitchenItemProcessor processor = new KitchenItemProcessor() {
        @Override
        public boolean canProcess(RecipeType<?> recipeType) {
            return recipeType == ModRecipeTypes.COOKING.get();
        }

        @Override
        public KitchenOperation processRecipe(Recipe<?> recipe, List<IngredientToken> ingredientTokens) {
            if (!items.get(SLOT_OUTPUT).isEmpty()) return KitchenOperation.EMPTY;

            int slot = 0;
            for (IngredientToken token : ingredientTokens) {
                ItemStack consumed = token.consume();
                if (!consumed.isEmpty() && slot < SLOT_COUNT) items.set(slot++, consumed);
            }
            if (level != null) pendingOutput = recipe.getResultItem(level.registryAccess()).copy();
            cookTimeTotal = getCookTimeFromRecipe(recipe);
            cookTime = 0;
            setChanged();
            return KitchenOperation.EMPTY;
        }

        private int getCookTimeFromRecipe(Recipe<?> recipe) {
            try {
                return (int) recipe.getClass().getMethod("getCookTime").invoke(recipe);
            } catch (Exception e) {
                return DEFAULT_COOK_TIME;
            }
        }
    };

    public PotStandBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POT_STAND.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // Server tick
    // ------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, PotStandBlockEntity be) {
        if (be.pendingOutput == null) return;
        if (!be.hasHeatSource(level, pos)) return;

        be.cookTime++;
        if (be.cookTime >= be.cookTimeTotal) be.finishCooking(level, state);
        be.setChanged();
    }

    private boolean hasHeatSource(Level level, BlockPos pos) {
        var below = level.getBlockState(pos.below());
        return below.is(BlockTags.CAMPFIRES)
                || below.is(BlockTags.FIRE)
                || below.getBlock() == Blocks.LAVA;
    }

    private void finishCooking(Level level, BlockState state) {
        if (pendingOutput != null) items.set(SLOT_OUTPUT, pendingOutput);
        pendingOutput = null;
        cookTime = 0;
        for (int i = 0; i < INPUT_SLOTS; i++) items.set(i, ItemStack.EMPTY);
        level.setBlock(worldPosition, state.setValue(PotStandBlock.ACTIVE, false), 3);
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
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

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
        return customName != null ? customName : Component.translatable("block.cfb_delight.pot_stand");
    }

    @Override public Component getName() { return getDisplayName(); }
    @Override @Nullable public Component getCustomName() { return customName; }
    public void setCustomName(Component name) { this.customName = name; setChanged(); }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new PotStandMenu(id, playerInventory, this, data,
                ContainerLevelAccess.create(level, worldPosition));
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public void dropContents(Level level, BlockPos pos) {
        for (ItemStack stack : items) {
            if (!stack.isEmpty())
                net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
        if (pendingOutput != null && !pendingOutput.isEmpty())
            net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), pendingOutput);
        items.replaceAll(ignored -> ItemStack.EMPTY);
        pendingOutput = null;
    }

    public boolean isCooking() { return pendingOutput != null; }

    @Override
    public AABB getRenderBoundingBox() { return new AABB(worldPosition).inflate(1); }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("CookTime", cookTime);
        tag.putInt("CookTimeTotal", cookTimeTotal);
        if (pendingOutput != null && !pendingOutput.isEmpty())
            tag.put("PendingOutput", pendingOutput.save(registries));
        if (customName != null)
            tag.putString("CustomName", Component.Serializer.toJson(customName, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        cookTime = tag.getInt("CookTime");
        cookTimeTotal = tag.getInt("CookTimeTotal");
        if (tag.contains("PendingOutput"))
            pendingOutput = ItemStack.parse(registries, tag.getCompound("PendingOutput")).orElse(null);
        if (tag.contains("CustomName"))
            customName = Component.Serializer.fromJson(tag.getString("CustomName"), registries);
    }
}
