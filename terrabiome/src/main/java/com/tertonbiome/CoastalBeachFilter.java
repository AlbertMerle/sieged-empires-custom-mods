package com.tertonbiome;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

/**
 * Constant-time Warm/Hot beach placement. The underlying one-shot multi-noise
 * result classifies water/coast; continentalness supplies the coast distance
 * approximation without sampling any neighboring biome positions.
 */
public final class CoastalBeachFilter {
	private CoastalBeachFilter() {}

	@FunctionalInterface
	public interface BiomeFinder {
		Holder<Biome> find(ResourceKey<Biome> key);
	}

	public static boolean isBeach(Holder<Biome> biome) {
		if (biome.is(BiomeTags.IS_BEACH)) {
			return true;
		}
		return biome.unwrapKey()
			.map(key -> {
				String path = key.identifier().getPath();
				return path.equals("beach") || path.endsWith("_beach");
			})
			.orElse(false);
	}

	public static boolean isOcean(Holder<Biome> biome) {
		return biome.is(BiomeTags.IS_OCEAN);
	}

	public static boolean isRiver(Holder<Biome> biome) {
		return biome.is(BiomeTags.IS_RIVER);
	}

	public static Holder<Biome> applyConstantTime(
		Holder<Biome> landBiome,
		Holder<Biome> classified,
		ClimateZone zone,
		Climate.TargetPoint climate,
		BiomeFinder finder
	) {
		TerratonicbiomesConfig c = TerratonicbiomesConfig.get();
		if (!c.coastalBeachRules || isRiver(classified)) {
			return isRiver(classified) ? classified : landBiome;
		}

		boolean warmHot = zone == ClimateZone.WARM || zone == ClimateZone.HOT;
		float continentalness = Climate.unquantizeCoord(climate.continentalness());
		float oceanEdge = c.oceanBleedContinentalnessMin;
		float oceanBleed = Math.max(0, c.beachOceanBleedBlocks) / 512.0f;
		float inlandMin = Math.max(0, c.beachInlandMinBlocks) / 512.0f;
		float inlandMax = Math.max(c.beachInlandMinBlocks, c.beachInlandMaxBlocks) / 512.0f;

		if (isOcean(classified) && continentalness < oceanEdge - oceanBleed) {
			return classified;
		}
		if (!warmHot) {
			return landBiome;
		}
		boolean coastal = isBeach(classified)
			|| (isOcean(classified) && continentalness >= oceanEdge - oceanBleed)
			|| (!isOcean(classified)
				&& continentalness >= oceanEdge + inlandMin
				&& continentalness <= oceanEdge + inlandMax);
		if (!coastal) {
			return landBiome;
		}
		if (isBeach(classified)) {
			return classified;
		}
		Holder<Biome> beach = finder.find(Biomes.BEACH);
		return beach != null ? beach : landBiome;
	}
}
