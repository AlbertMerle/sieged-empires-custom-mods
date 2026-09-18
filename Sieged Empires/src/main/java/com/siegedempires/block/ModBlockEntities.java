package com.siegedempires.block;

import com.siegedempires.Siegedempires;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;

public final class ModBlockEntities {

    public static final BlockEntityType<LocksmithingTableBlockEntity> LOCKSMITHING_TABLE =
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Siegedempires.id("locksmithing_table"),
            new BlockEntityType<>(LocksmithingTableBlockEntity::new, Set.of(ModBlocks.LOCKSMITHING_TABLE)));

    public static void initialize() {}
}