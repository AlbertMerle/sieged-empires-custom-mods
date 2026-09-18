package com.siegedempires.block;

import com.siegedempires.Siegedempires;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenuTypes {

    public static final MenuType<LocksmithingTableMenu> LOCKSMITHING_TABLE =
        Registry.register(BuiltInRegistries.MENU,
            Siegedempires.id("locksmithing_table"),
            new MenuType<>((syncId, inv) -> new LocksmithingTableMenu(syncId, inv), FeatureFlags.VANILLA_SET));

    public static void initialize() {}
}