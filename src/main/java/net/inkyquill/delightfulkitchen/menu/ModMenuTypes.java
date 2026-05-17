package net.inkyquill.delightfulkitchen.menu;

import net.inkyquill.delightfulkitchen.DelightfulKitchen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, DelightfulKitchen.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<PotStandMenu>> POT_STAND =
            MENUS.register("pot_stand", () ->
                    IMenuTypeExtension.create(PotStandMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<AutoCutterMenu>> AUTO_CUTTER =
            MENUS.register("auto_cutter", () ->
                    IMenuTypeExtension.create(AutoCutterMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<GrillMenu>> GRILL =
            MENUS.register("grill", () ->
                    IMenuTypeExtension.create(GrillMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ContainerStorageMenu>> CONTAINER_STORAGE =
            MENUS.register("container_storage", () ->
                    IMenuTypeExtension.create(ContainerStorageMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<CabinetOfHoldingMenu>> CABINET_OF_HOLDING =
            MENUS.register("cabinet_of_holding", () ->
                    IMenuTypeExtension.create(CabinetOfHoldingMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ChefCounterMenu>> CHEF_COUNTER =
            MENUS.register("chef_counter", () ->
                    IMenuTypeExtension.create(ChefCounterMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<FuelProviderMenu>> FUEL_PROVIDER =
            MENUS.register("fuel_provider", () ->
                    IMenuTypeExtension.create(FuelProviderMenu::new));
}
