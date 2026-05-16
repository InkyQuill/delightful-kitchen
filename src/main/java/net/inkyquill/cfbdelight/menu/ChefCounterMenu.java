package net.inkyquill.cfbdelight.menu;

import net.inkyquill.cfbdelight.block.ModBlocks;
import net.inkyquill.cfbdelight.kitchen.KitchenScanResult;
import net.inkyquill.cfbdelight.kitchen.KitchenScanner;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;

public class ChefCounterMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    private final ContainerData data;

    public ChefCounterMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL, new SimpleContainerData(3));
    }

    public ChefCounterMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
        this(containerId, playerInventory, access, createServerData(access));
    }

    private ChefCounterMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access, ContainerData data) {
        super(ModMenuTypes.CHEF_COUNTER.get(), containerId);
        this.access = access;
        this.data = data;
        addDataSlots(data);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    private static ContainerData createServerData(ContainerLevelAccess access) {
        SimpleContainerData data = new SimpleContainerData(3);
        KitchenScanResult scan = access.evaluate(KitchenScanner::scan)
                .orElseGet(() -> new KitchenScanResult(false, Set.of(), List.of(), List.of()));
        data.set(0, scan.floorCount());
        data.set(1, scan.memberCount());
        data.set(2, scan.hasMultipleCounters() ? 1 : 0);
        return data;
    }

    public int getFloorCount() {
        return data.get(0);
    }

    public int getMemberCount() {
        return data.get(1);
    }

    public boolean hasMultipleCounters() {
        return data.get(2) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.CHEF_COUNTER.get());
    }
}
