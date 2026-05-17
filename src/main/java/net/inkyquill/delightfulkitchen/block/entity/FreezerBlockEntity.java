package net.inkyquill.delightfulkitchen.block.entity;

import net.inkyquill.delightfulkitchen.block.FreezerBlock;
import net.inkyquill.delightfulkitchen.kitchen.KitchenScanner;
import net.inkyquill.delightfulkitchen.kitchen.KitchenServices;
import net.inkyquill.delightfulkitchen.kitchen.utility.BorrowedContainer;
import net.inkyquill.delightfulkitchen.kitchen.utility.KitchenUtilityRequirement;
import net.inkyquill.delightfulkitchen.kitchen.utility.KitchenUtilityService;
import net.inkyquill.delightfulkitchen.menu.FreezerMenu;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Output inventory shell for the Freezer utility block. */
public class FreezerBlockEntity extends BlockEntity
        implements Container, MenuProvider, Nameable {

    public static final int OUTPUT_SLOTS = 9;
    public static final int PASSIVE_CYCLE_TICKS = 1200;

    private static final String TAG_PASSIVE_MODE = "PassiveMode";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_NEXT_ICE = "NextIce";

    public enum PassiveMode {
        ICE_AND_SNOW,
        ICE_ONLY,
        SNOW_ONLY,
        PAUSED
    }

    private final NonNullList<ItemStack> items = NonNullList.withSize(OUTPUT_SLOTS, ItemStack.EMPTY);
    private PassiveMode passiveMode = PassiveMode.ICE_AND_SNOW;
    private int progressTicks = 0;
    private boolean nextProductIce = true;
    private boolean active = false;
    @Nullable private Component customName = null;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> passiveMode.ordinal();
                case 1 -> progressTicks;
                case 2 -> nextProductIce ? 1 : 0;
                case 3 -> active ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> passiveMode = PassiveMode.values()[Math.floorMod(value, PassiveMode.values().length)];
                case 1 -> progressTicks = Math.clamp(value, 0, PASSIVE_CYCLE_TICKS);
                case 2 -> nextProductIce = value != 0;
                case 3 -> active = value != 0;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public FreezerBlockEntity(BlockPos pos, BlockState blockState) {
        this(ModBlockEntities.FREEZER.get(), pos, blockState);
    }

    FreezerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FreezerBlockEntity be) {
        if (level.isClientSide()) {
            return;
        }
        if (be.passiveMode == PassiveMode.PAUSED) {
            be.setActive(level, pos, state, false);
            return;
        }

        var scan = KitchenScanner.scanFromKitchenBlock(level, pos);
        if (!scan.hasKitchenFloor()) {
            be.setActive(level, pos, state, false);
            return;
        }

        KitchenUtilityService utility = KitchenServices.utilityService(level, scan, pos);
        be.tickPassiveProduction(utility);
        be.syncActiveBlockState(level, pos, state);
    }

    void tickPassiveProduction(KitchenUtilityService utility) {
        if (passiveMode == PassiveMode.PAUSED) {
            setActiveFlag(false);
            return;
        }

        KitchenUtilityRequirement requirement = nextRequirement();
        ItemStack product = productFor(requirement);
        if (!utility.blockersFor(requirement).isEmpty() || !canAcceptOutput(product)) {
            setActiveFlag(false);
            return;
        }

        setActiveFlag(true);
        progressTicks++;
        if (progressTicks < PASSIVE_CYCLE_TICKS) {
            setChanged();
            return;
        }

        BorrowedContainer borrowed = utility.borrow(requirement).orElse(null);
        if (borrowed == null) {
            progressTicks = PASSIVE_CYCLE_TICKS - 1;
            setActiveFlag(false);
            setChanged();
            return;
        }

        ItemStack remainder = insertOutput(product, false);
        utility.release(borrowed);
        if (!remainder.isEmpty()) {
            progressTicks = PASSIVE_CYCLE_TICKS - 1;
            setChanged();
            return;
        }

        progressTicks = 0;
        if (passiveMode == PassiveMode.ICE_AND_SNOW) {
            nextProductIce = !nextProductIce;
        }
        setChanged();
    }

    public PassiveMode passiveMode() {
        return passiveMode;
    }

    public int progressTicks() {
        return progressTicks;
    }

    public boolean nextProductIce() {
        return nextProductIce;
    }

    public boolean isProducing() {
        return active;
    }

    public ContainerData data() {
        return data;
    }

    public void setPassiveMode(PassiveMode mode) {
        passiveMode = mode;
        progressTicks = 0;
        setActiveFlag(false);
        setChanged();
    }

    public void cyclePassiveMode() {
        PassiveMode[] modes = PassiveMode.values();
        setPassiveMode(modes[(passiveMode.ordinal() + 1) % modes.length]);
    }

    private KitchenUtilityRequirement nextRequirement() {
        return switch (passiveMode) {
            case ICE_AND_SNOW -> nextProductIce ? KitchenUtilityRequirement.ICE : KitchenUtilityRequirement.SNOWBALL;
            case ICE_ONLY -> KitchenUtilityRequirement.ICE;
            case SNOW_ONLY -> KitchenUtilityRequirement.SNOWBALL;
            case PAUSED -> KitchenUtilityRequirement.NONE;
        };
    }

    private static ItemStack productFor(KitchenUtilityRequirement requirement) {
        return switch (requirement) {
            case ICE -> new ItemStack(Items.ICE);
            case SNOWBALL -> new ItemStack(Items.SNOWBALL);
            case NONE, WATER -> ItemStack.EMPTY;
        };
    }

    private boolean canAcceptOutput(ItemStack stack) {
        return !stack.isEmpty() && insertOutput(stack, true).isEmpty();
    }

    private ItemStack insertOutput(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < items.size() && !remainder.isEmpty(); slot++) {
            ItemStack existing = items.get(slot);
            if (existing.isEmpty()
                    || !ItemStack.isSameItemSameComponents(existing, remainder)
                    || !canPlaceItem(slot, remainder)) {
                continue;
            }

            int transfer = Math.min(remainder.getCount(), getMaxStackSize(existing) - existing.getCount());
            if (transfer <= 0) {
                continue;
            }
            if (!simulate) {
                existing.grow(transfer);
            }
            remainder.shrink(transfer);
        }

        for (int slot = 0; slot < items.size() && !remainder.isEmpty(); slot++) {
            if (!items.get(slot).isEmpty() || !canPlaceItem(slot, remainder)) {
                continue;
            }

            int transfer = Math.min(remainder.getCount(), getMaxStackSize(remainder));
            if (transfer <= 0) {
                continue;
            }
            if (!simulate) {
                items.set(slot, remainder.copyWithCount(transfer));
            }
            remainder.shrink(transfer);
        }

        if (!simulate && remainder.getCount() != stack.getCount()) {
            setChanged();
        }
        return remainder.isEmpty() ? ItemStack.EMPTY : remainder;
    }

    private void setActive(Level level, BlockPos pos, BlockState state, boolean value) {
        setActiveFlag(value);
        syncActiveBlockState(level, pos, state);
    }

    private void setActiveFlag(boolean value) {
        if (active != value) {
            active = value;
            setChanged();
        }
    }

    private void syncActiveBlockState(Level level, BlockPos pos, BlockState state) {
        if (state.hasProperty(FreezerBlock.ACTIVE) && state.getValue(FreezerBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(FreezerBlock.ACTIVE, active), 3);
        }
    }

    @Override
    public int getContainerSize() {
        return OUTPUT_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize(stack)) {
            stack.setCount(getMaxStackSize(stack));
        }
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return isAllowedOutput(stack);
    }

    private static boolean isAllowedOutput(ItemStack stack) {
        return stack.is(Items.ICE) || stack.is(Items.SNOWBALL);
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5,
                        worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        items.replaceAll(ignored -> ItemStack.EMPTY);
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("block.delightful_kitchen.freezer");
    }

    @Override
    public Component getName() {
        return getDisplayName();
    }

    @Override
    @Nullable
    public Component getCustomName() {
        return customName;
    }

    public void setCustomName(Component name) {
        customName = name;
        setChanged();
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new FreezerMenu(id, playerInventory, this, data, ContainerLevelAccess.create(level, worldPosition));
    }

    public void dropContents(Level level, BlockPos pos) {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
        items.replaceAll(ignored -> ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putString(TAG_PASSIVE_MODE, passiveMode.name());
        tag.putInt(TAG_PROGRESS, progressTicks);
        tag.putBoolean(TAG_NEXT_ICE, nextProductIce);
        if (customName != null) {
            tag.putString("CustomName", Component.Serializer.toJson(customName, registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        if (tag.contains(TAG_PASSIVE_MODE)) {
            try {
                passiveMode = PassiveMode.valueOf(tag.getString(TAG_PASSIVE_MODE));
            } catch (IllegalArgumentException ignored) {
                passiveMode = PassiveMode.ICE_AND_SNOW;
            }
        }
        progressTicks = Math.clamp(tag.getInt(TAG_PROGRESS), 0, PASSIVE_CYCLE_TICKS);
        if (tag.contains(TAG_NEXT_ICE)) {
            nextProductIce = tag.getBoolean(TAG_NEXT_ICE);
        }
        if (tag.contains("CustomName")) {
            customName = Component.Serializer.fromJson(tag.getString("CustomName"), registries);
        }
    }
}
