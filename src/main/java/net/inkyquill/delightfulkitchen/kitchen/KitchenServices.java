package net.inkyquill.delightfulkitchen.kitchen;

import net.inkyquill.delightfulkitchen.block.entity.AutoCutterBlockEntity;
import net.inkyquill.delightfulkitchen.block.entity.GrillBlockEntity;
import net.inkyquill.delightfulkitchen.block.entity.PotStandBlockEntity;
import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class KitchenServices {
    private KitchenServices() {
    }

    public static List<KitchenInventoryEndpoint> inventoryEndpoints(Level level, KitchenScanResult scan,
                                                                    BlockPos terminalPos) {
        List<KitchenInventoryEndpoint> endpoints = new ArrayList<>();
        for (BlockPos member : scan.memberBlocks()) {
            if (member.equals(terminalPos)) {
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(member);
            if (KitchenScanner.isKitchenStation(blockEntity)) {
                continue;
            }
            if (blockEntity instanceof Container container) {
                endpoints.add(new KitchenInventoryEndpoint(member, container));
                continue;
            }

            var itemHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, member, null);
            if (itemHandler != null) {
                endpoints.add(new KitchenInventoryEndpoint(member, itemHandler));
            }
        }
        return List.copyOf(endpoints);
    }

    public static EnumSet<KitchenStationType> stationTypes(Level level, KitchenScanResult scan) {
        EnumSet<KitchenStationType> stationTypes = EnumSet.of(KitchenStationType.CRAFTING);
        for (BlockPos member : scan.memberBlocks()) {
            BlockEntity blockEntity = level.getBlockEntity(member);
            if (blockEntity instanceof AutoCutterBlockEntity) {
                stationTypes.add(KitchenStationType.CUTTING_BOARD);
            } else if (blockEntity instanceof PotStandBlockEntity) {
                stationTypes.add(KitchenStationType.COOKING_POT);
            } else if (blockEntity instanceof GrillBlockEntity) {
                stationTypes.add(KitchenStationType.GRILL);
            }
        }
        return stationTypes;
    }
}
