package net.inkyquill.cfbdelight.block.entity;

import net.blay09.mods.cookingforblockheads.api.CacheHint;
import net.blay09.mods.cookingforblockheads.api.CookingForBlockheadsAPI;
import net.blay09.mods.cookingforblockheads.api.IngredientToken;
import net.blay09.mods.cookingforblockheads.api.Kitchen;
import net.blay09.mods.cookingforblockheads.api.KitchenItemProcessor;
import net.blay09.mods.cookingforblockheads.api.KitchenItemProvider;
import net.blay09.mods.cookingforblockheads.api.KitchenOperation;
import net.inkyquill.cfbdelight.block.PotStandBlock;
import net.inkyquill.cfbdelight.menu.PotStandMenu;
import net.inkyquill.cfbdelight.util.InventoryHelper;
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
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

import java.util.List;

/**
 * Block entity for the Pot Stand.
 * <p>
 * Slot layout:
 * <ul>
 *   <li>0-5 : ingredient inputs</li>
 *   <li>6   : container slot (bowl / bottle)</li>
 *   <li>7   : output slot (cooked meal)</li>
 * </ul>
 */
public class PotStandBlockEntity extends BlockEntity
        implements Container, MenuProvider, Nameable {

    public static final int INPUT_SLOTS    = 6;
    public static final int SLOT_CONTAINER = 6;
    public static final int SLOT_OUTPUT    = 7;
    public static final int SLOT_FUEL      = 8;
    public static final int SLOT_COUNT     = 9;
    public static final int DEFAULT_COOK_TIME = 200;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int cookTime      = 0;
    private int cookTimeTotal = DEFAULT_COOK_TIME;
    private int burnTime      = 0;
    private int burnTimeTotal = 200;
    @Nullable private ItemStack pendingOutput = null;
    @Nullable private Component customName    = null;

    // ContainerData wired to the menu for client syncing
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> cookTime;
                case 1 -> cookTimeTotal;
                case 2 -> burnTime;
                case 3 -> burnTimeTotal;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> cookTime      = value;
                case 1 -> cookTimeTotal = value;
                case 2 -> burnTime      = value;
                case 3 -> burnTimeTotal = value;
            }
        }
        @Override public int getCount() { return 4; }
    };

    // KitchenItemProcessor exposed to CfB
    private final KitchenItemProcessor processor = new KitchenItemProcessor() {
        @Override
        public boolean canProcess(RecipeType<?> recipeType) {
            return recipeType == ModRecipeTypes.COOKING.get();
        }

        @Override
        public KitchenOperation processRecipe(Recipe<?> recipe, List<IngredientToken> ingredientTokens) {
            if (!(recipe instanceof CookingPotRecipe cpRecipe)) return KitchenOperation.EMPTY;
            if (!items.get(SLOT_OUTPUT).isEmpty()) return KitchenOperation.EMPTY;
            if (pendingOutput != null) return KitchenOperation.EMPTY;

            int ingredientCount = cpRecipe.getIngredients().size();
            int slot = 0;
            for (int i = 0; i < ingredientTokens.size(); i++) {
                ItemStack consumed = ingredientTokens.get(i).consume();
                if (consumed.isEmpty()) continue;
                if (i < ingredientCount) {
                    if (slot < INPUT_SLOTS) items.set(slot++, consumed);
                } else {
                    items.set(SLOT_CONTAINER, consumed);
                }
            }
            setChanged();
            return KitchenOperation.EMPTY;
        }
    };

    public PotStandBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POT_STAND.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // Server tick
    // ------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, PotStandBlockEntity be) {
        if (be.pendingOutput != null) {
            if (be.burnTime == 0) be.refuel();
            if (be.burnTime > 0) {
                be.burnTime--;
                be.cookTime++;
                if (be.cookTime >= be.cookTimeTotal) be.finishCooking(level, pos, state);
                be.setChanged();
            }
        } else {
            // Autonomous: try to find a recipe for the current inputs
            be.tryStartCooking(level, pos, state);
        }
    }

    private void refuel() {
        ItemStack fuel = items.get(SLOT_FUEL);
        if (fuel.isEmpty()) return;
        int bt = getBurnTime(fuel);
        if (bt <= 0) return;
        burnTimeTotal = bt;
        burnTime = bt;
        fuel.shrink(1);
        if (fuel.isEmpty()) items.set(SLOT_FUEL, ItemStack.EMPTY);
        setChanged();
    }

    private static int getBurnTime(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        var fuel = stack.getItemHolder().getData(
                net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps.FURNACE_FUELS);
        return fuel != null ? fuel.burnTime() : 0;
    }

    private void tryStartCooking(Level level, BlockPos pos, BlockState state) {
        if (pendingOutput != null) return;
        if (!items.get(SLOT_OUTPUT).isEmpty()) return;

        // Check if any input slot has items
        boolean hasInput = false;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (!items.get(i).isEmpty()) { hasInput = true; break; }
        }
        if (!hasInput) return;

        List<RecipeHolder<CookingPotRecipe>> recipes = level.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.COOKING.get());
        for (RecipeHolder<CookingPotRecipe> holder : recipes) {
            CookingPotRecipe recipe = holder.value();
            // If container needed and slot empty, try to pull from adjacent storage
            ItemStack containerRequired = recipe.getOutputContainer();
            if (!containerRequired.isEmpty() && items.get(SLOT_CONTAINER).isEmpty()) {
                tryPullContainer(level, containerRequired);
            }
            if (matchesRecipe(recipe)) {
                consumeForRecipe(recipe);
                pendingOutput = recipe.getResultItem(level.registryAccess()).copy();
                cookTimeTotal = recipe.getCookTime();
                cookTime = 0;
                level.setBlock(pos, state.setValue(PotStandBlock.ACTIVE, true), 3);
                setChanged();
                return;
            }
        }
    }

    /**
     * Searches the connected CfB kitchen network for the required container item and
     * pulls one from the first provider that has it.
     */
    private boolean tryPullContainer(Level level, ItemStack required) {
        Ingredient ingredient = Ingredient.of(required.getItem());
        Kitchen kitchen = CookingForBlockheadsAPI.createKitchen(level, worldPosition);
        for (KitchenItemProvider provider : kitchen.getItemProviders(null)) {
            IngredientToken token = provider.findIngredient(ingredient, List.of(), CacheHint.NONE);
            if (token != null) {
                ItemStack found = token.consume();
                if (!found.isEmpty()) {
                    items.set(SLOT_CONTAINER, found.copyWithCount(1));
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks that all recipe ingredients are satisfied by items in slots 0-5,
     * and if the recipe requires a container, checks slot 6.
     */
    private boolean matchesRecipe(CookingPotRecipe recipe) {
        List<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty()) return false;

        boolean[] slotUsed = new boolean[INPUT_SLOTS];
        for (Ingredient ingredient : ingredients) {
            boolean matched = false;
            for (int i = 0; i < INPUT_SLOTS; i++) {
                if (slotUsed[i]) continue;
                if (!items.get(i).isEmpty() && ingredient.test(items.get(i))) {
                    slotUsed[i] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }

        // Check container requirement
        ItemStack containerRequired = recipe.getOutputContainer();
        if (!containerRequired.isEmpty()) {
            ItemStack containerSlot = items.get(SLOT_CONTAINER);
            if (containerSlot.isEmpty() || !ItemStack.isSameItemSameComponents(containerSlot, containerRequired)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Consumes one item from matching slots for each ingredient, and shrinks slot 6 if container needed.
     */
    private void consumeForRecipe(CookingPotRecipe recipe) {
        List<Ingredient> ingredients = recipe.getIngredients();
        boolean[] slotUsed = new boolean[INPUT_SLOTS];

        for (Ingredient ingredient : ingredients) {
            for (int i = 0; i < INPUT_SLOTS; i++) {
                if (slotUsed[i]) continue;
                if (!items.get(i).isEmpty() && ingredient.test(items.get(i))) {
                    slotUsed[i] = true;
                    items.get(i).shrink(1);
                    if (items.get(i).isEmpty()) items.set(i, ItemStack.EMPTY);
                    break;
                }
            }
        }

        ItemStack containerRequired = recipe.getOutputContainer();
        if (!containerRequired.isEmpty()) {
            ItemStack containerSlot = items.get(SLOT_CONTAINER);
            containerSlot.shrink(1);
            if (containerSlot.isEmpty()) items.set(SLOT_CONTAINER, ItemStack.EMPTY);
        }
    }

    private void finishCooking(Level level, BlockPos pos, BlockState state) {
        if (pendingOutput != null) {
            // Try pushing output to kitchen network first
            if (!InventoryHelper.pushToKitchen(level, pos, pendingOutput)) {
                items.set(SLOT_OUTPUT, pendingOutput);
            }
        }
        pendingOutput = null;
        cookTime = 0;
        level.setBlock(pos, state.setValue(PotStandBlock.ACTIVE, false), 3);
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
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == SLOT_OUTPUT) return false;
        if (slot == SLOT_FUEL) return getBurnTime(stack) > 0;
        return true;
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

    public KitchenItemProcessor getKitchenItemProcessor() { return processor; }

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
        tag.putInt("BurnTime", burnTime);
        tag.putInt("BurnTimeTotal", burnTimeTotal);
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
        burnTime = tag.getInt("BurnTime");
        burnTimeTotal = tag.getInt("BurnTimeTotal");
        if (tag.contains("PendingOutput"))
            pendingOutput = ItemStack.parse(registries, tag.getCompound("PendingOutput")).orElse(null);
        if (tag.contains("CustomName"))
            customName = Component.Serializer.fromJson(tag.getString("CustomName"), registries);
    }
}
