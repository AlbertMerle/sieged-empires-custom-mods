package com.croplite.worldgen;

import com.croplite.CropLite;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class ModFeatures {
	private ModFeatures() {
	}

	public static final Feature<NoneFeatureConfiguration> WILD_CROP_PATCH =
			Registry.register(
					BuiltInRegistries.FEATURE,
					CropLite.id("wild_crop_patch"),
					new WildCropFeature(NoneFeatureConfiguration.CODEC));

	public static final Feature<NoneFeatureConfiguration> BANANA_TREE =
			Registry.register(
					BuiltInRegistries.FEATURE,
					CropLite.id("banana_tree"),
					new BananaTreeFeature(NoneFeatureConfiguration.CODEC));

	public static void initialize() {
		// Static registration.
	}
}
