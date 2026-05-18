package net.inkyquill.delightfulkitchen.item;

import net.inkyquill.delightfulkitchen.component.ModDataComponents;
import net.inkyquill.delightfulkitchen.menu.FoodPouchMenu;
import net.inkyquill.delightfulkitchen.pouch.FeedThreshold;
import net.inkyquill.delightfulkitchen.pouch.FoodPouchFeeder;
import net.inkyquill.delightfulkitchen.pouch.FoodPouchInventory;
import net.inkyquill.delightfulkitchen.pouch.FoodPouchInventoryOps;
import net.inkyquill.delightfulkitchen.pouch.FoodPouchTier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

public class FoodPouchItem extends Item implements ICurioItem {
    private final FoodPouchTier tier;

    public FoodPouchItem(FoodPouchTier tier, Properties properties) {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    public FoodPouchTier tier() {
        return tier;
    }

    public static FoodPouchTier tierOf(ItemStack stack) {
        if (stack.getItem() instanceof FoodPouchItem pouchItem) {
            return pouchItem.tier();
        }
        return FoodPouchTier.BASIC;
    }

    public static FoodPouchInventory inventory(ItemStack pouch) {
        FoodPouchTier tier = tierOf(pouch);
        FoodPouchInventory inventory = pouch.getOrDefault(ModDataComponents.POUCH_INVENTORY.get(),
                FoodPouchInventory.empty(tier.slots()));
        return FoodPouchInventoryOps.normalized(inventory, tier);
    }

    public static FoodPouchInventory mutableInventory(ItemStack pouch) {
        FoodPouchInventory inventory = inventory(pouch);
        pouch.set(ModDataComponents.POUCH_INVENTORY.get(), inventory);
        return inventory;
    }

    public static FeedThreshold threshold(ItemStack pouch) {
        return pouch.getOrDefault(ModDataComponents.FEED_AT_HUNGER_LEVEL.get(), FeedThreshold.HALF);
    }

    public static boolean feedWhenHurt(ItemStack pouch) {
        return pouch.getOrDefault(ModDataComponents.FEED_WHEN_HURT.get(), true);
    }

    public static int feedCooldown(ItemStack pouch) {
        return pouch.getOrDefault(ModDataComponents.POUCH_FEED_COOLDOWN.get(), 0);
    }

    public static void setFeedCooldown(ItemStack pouch, int ticks) {
        pouch.set(ModDataComponents.POUCH_FEED_COOLDOWN.get(), Math.max(0, ticks));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            open(serverPlayer, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof ServerPlayer player) {
            FoodPouchFeeder.tickEquipped(player, stack);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        FoodPouchTier tier = tierOf(stack);
        FoodPouchInventory inventory = inventory(stack);
        tooltip.add(Component.translatable("tooltip.delightful_kitchen.food_pouch.tier",
                Component.translatable("tooltip.delightful_kitchen.food_pouch.tier." + tier.name().toLowerCase())));
        tooltip.add(Component.translatable("tooltip.delightful_kitchen.food_pouch.slots",
                inventory.usedSlots(), tier.slots()));
        tooltip.add(Component.translatable("tooltip.delightful_kitchen.food_pouch.stack_multiplier",
                tier.stackMultiplier()));
        tooltip.add(Component.translatable("tooltip.delightful_kitchen.food_pouch.threshold",
                Component.translatable("gui.delightful_kitchen.food_pouch.threshold."
                        + threshold(stack).getSerializedName())));
        tooltip.add(Component.translatable(feedWhenHurt(stack)
                ? "tooltip.delightful_kitchen.food_pouch.hurt_on"
                : "tooltip.delightful_kitchen.food_pouch.hurt_off"));
    }

    public static void open(ServerPlayer player, ItemStack pouchStack) {
        if (!(pouchStack.getItem() instanceof FoodPouchItem)) {
            return;
        }
        player.openMenu(provider(pouchStack), buf -> buf.writeVarInt(tierOf(pouchStack).ordinal()));
    }

    private static MenuProvider provider(ItemStack pouchStack) {
        return new SimpleMenuProvider(
                (containerId, inventory, player) -> createMenu(containerId, inventory, pouchStack),
                Component.translatable("container.delightful_kitchen.food_pouch")
        );
    }

    private static AbstractContainerMenu createMenu(int containerId,
                                                    net.minecraft.world.entity.player.Inventory inventory,
                                                    ItemStack pouchStack) {
        return new FoodPouchMenu(containerId, inventory, pouchStack);
    }
}
