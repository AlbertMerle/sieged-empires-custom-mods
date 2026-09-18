package com.croplite.crop;

import com.croplite.block.ModBlocks;
import com.croplite.compat.SeasonGrowth;
import com.croplite.config.CropLiteConfig;
import com.croplite.worldgen.BiomeSpawnCategory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Climate rules for crops. When a crop is outside its allowed temperature / season,
 * it <em>rots</em>: growth stops (natural ticks <em>and</em> bonemeal) and the plant tints brown.
 *
 * <p>A crop grows when the biome base temperature falls in <em>any</em> of its allowed bands
 * (and it is not subject to a seasonal rot rule such as winter-in-temperate).
 *
 * <p>Deserts / badlands: cactus, coffee, pepper, and cantelope grow freely. Other tropical
 * crops need water within 2 blocks (then grow at a reduced rate). Cold/temperate-only crops rot.
 *
 * <p>All growth entry points must call {@link #isRotting} / {@link #canGrow}: {@code randomTick},
 * {@code isValidBonemealTarget}, {@code performBonemeal}, and {@code growCrops}.
 */
public final class CropClimate {
	private CropClimate() {
	}

	/** {@code true} when the crop may grow (not rotting). */
	public static boolean canGrow(Block crop, LevelReader level, BlockPos pos) {
		return !isRotting(crop, level, pos);
	}

	/**
	 * Rot: wrong temperature band, below a crop-specific minimum, seasonal rot
	 * (basil / cantelope in temperate winter), or arid biome rules.
	 */
	public static boolean isRotting(Block crop, LevelReader level, BlockPos pos) {
		if (!CropLiteConfig.get().temperatureChecks) {
			return false;
		}

		if (rotsInTemperateWinter(crop) && SeasonGrowth.isWinterInTemperate(level, pos)) {
			return true;
		}

		Holder<Biome> biome = level.getBiome(pos);
		if (BiomeSpawnCategory.isDesert(biome)) {
			return isRottingInDesert(crop, level, pos);
		}

		float temperature = biome.value().getBaseTemperature();
		if (isBasil(crop) && temperature < CropLiteConfig.get().coldCropMaxTemperature) {
			// Basil: plantable from temperate up, but only at ≥ cold max (default 0.5).
			return true;
		}

		for (TemperatureCategory category : allowedCategories(crop)) {
			if (category.allowsTemperature(temperature)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Desert / badlands: hardy crops never rot from climate; other tropical crops rot
	 * without nearby water; cold/temperate-only crops always rot.
	 */
	private static boolean isRottingInDesert(Block crop, LevelReader level, BlockPos pos) {
		if (isDesertHardy(crop)) {
			return false;
		}
		if (!allowedCategories(crop).contains(TemperatureCategory.TROPICAL)) {
			return true;
		}
		return !CropGrowth.hasWaterWithin(level, pos, 2);
	}

	/**
	 * Other tropical crops in desert/badlands with irrigation: grow at reduced rate
	 * ({@code desert_irrigated_growth_percent}).
	 */
	public static boolean usesDesertIrrigationPenalty(Block crop, LevelReader level, BlockPos pos) {
		if (!CropLiteConfig.get().temperatureChecks) {
			return false;
		}
		if (!BiomeSpawnCategory.isDesert(level.getBiome(pos))) {
			return false;
		}
		if (isDesertHardy(crop) || !allowedCategories(crop).contains(TemperatureCategory.TROPICAL)) {
			return false;
		}
		return CropGrowth.hasWaterWithin(level, pos, 2);
	}

	/** Cactus, coffee, pepper, cantelope — thrive in desert/badlands without irrigation. */
	public static boolean isDesertHardy(Block crop) {
		return crop == Blocks.CACTUS
				|| crop == ModBlocks.COFFEE_CROP
				|| crop == ModBlocks.PEPPER_CROP
				|| isCantelopeStem(crop);
	}

	/** Basil and cantelope rot in temperate biomes during Serene Seasons winter. */
	private static boolean rotsInTemperateWinter(Block crop) {
		return isBasil(crop) || isCantelopeStem(crop);
	}

	private static boolean isBasil(Block crop) {
		return crop == ModBlocks.BASIL_CROP;
	}

	private static boolean isCantelopeStem(Block crop) {
		return crop == ModBlocks.CANTELOPE_STEM || crop == ModBlocks.ATTACHED_CANTELOPE_STEM;
	}

	public static Set<TemperatureCategory> allowedCategories(Block crop) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(crop);
		Map<String, Set<TemperatureCategory>> map = CropLiteConfig.get().cropClimates;
		Set<TemperatureCategory> configured = map.get(id.toString());
		if (configured != null && !configured.isEmpty()) {
			return configured;
		}
		return defaultsFor(crop);
	}

	/** Built-in defaults when a block is missing from config. */
	public static Set<TemperatureCategory> defaultsFor(Block crop) {
		if (crop == Blocks.WHEAT || crop == Blocks.CARROTS || crop == Blocks.BEETROOTS) {
			return EnumSet.of(TemperatureCategory.COLD, TemperatureCategory.TEMPERATE);
		}
		if (crop == Blocks.POTATOES) {
			return EnumSet.of(TemperatureCategory.TEMPERATE);
		}
		if (crop == Blocks.SWEET_BERRY_BUSH) {
			return EnumSet.of(TemperatureCategory.COLD);
		}
		if (crop == Blocks.SUGAR_CANE) {
			return EnumSet.of(TemperatureCategory.TROPICAL);
		}
		if (crop == Blocks.MELON_STEM || crop == Blocks.ATTACHED_MELON_STEM) {
			return EnumSet.of(TemperatureCategory.TROPICAL);
		}
		if (crop == Blocks.PUMPKIN_STEM || crop == Blocks.ATTACHED_PUMPKIN_STEM) {
			return EnumSet.of(TemperatureCategory.TEMPERATE, TemperatureCategory.TROPICAL);
		}
		if (crop == Blocks.COCOA) {
			return EnumSet.of(TemperatureCategory.TROPICAL);
		}
		if (crop == Blocks.CACTUS) {
			return EnumSet.of(TemperatureCategory.TROPICAL);
		}
		if (crop == Blocks.BROWN_MUSHROOM || crop == Blocks.RED_MUSHROOM
				|| crop == Blocks.BROWN_MUSHROOM_BLOCK || crop == Blocks.RED_MUSHROOM_BLOCK) {
			return EnumSet.of(TemperatureCategory.COLD);
		}

		Identifier id = BuiltInRegistries.BLOCK.getKey(crop);
		if ("croplite".equals(id.getNamespace())) {
			return switch (id.getPath()) {
				case "oats_crop" -> EnumSet.of(TemperatureCategory.TEMPERATE);
				case "beans_crop" -> EnumSet.of(TemperatureCategory.TEMPERATE, TemperatureCategory.TROPICAL);
				case "rice_crop" -> EnumSet.of(TemperatureCategory.TROPICAL);
				case "tomato_crop", "eggplant_crop", "cucumber_crop", "sweet_potato_crop",
						"pepper_crop", "coffee_crop", "banana_sapling" ->
						EnumSet.of(TemperatureCategory.TROPICAL);
				case "cantelope_stem", "attached_cantelope_stem" ->
						EnumSet.of(TemperatureCategory.TEMPERATE, TemperatureCategory.TROPICAL);
				case "garlic_crop" -> EnumSet.of(TemperatureCategory.COLD, TemperatureCategory.TEMPERATE);
				case "peach_sapling" -> EnumSet.of(TemperatureCategory.COLD, TemperatureCategory.TEMPERATE);
				case "lemon_sapling" -> EnumSet.of(TemperatureCategory.TEMPERATE, TemperatureCategory.TROPICAL);
				case "basil_crop" ->
						EnumSet.of(TemperatureCategory.TEMPERATE, TemperatureCategory.TROPICAL);
				default -> EnumSet.allOf(TemperatureCategory.class);
			};
		}

		return EnumSet.allOf(TemperatureCategory.class);
	}
}
