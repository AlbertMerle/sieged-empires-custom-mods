package com.croplite.worldgen;

import com.croplite.CropLite;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

public final class ModPlacementModifiers {
	private ModPlacementModifiers() {
	}

	public static final PlacementModifierType<ConfigChunkRarityPlacement> CONFIG_CHUNK_RARITY =
			Registry.register(
					BuiltInRegistries.PLACEMENT_MODIFIER_TYPE,
					CropLite.id("config_chunk_rarity"),
					() -> ConfigChunkRarityPlacement.CODEC);

	public static void initialize() {
		// Static registration.
	}
}
