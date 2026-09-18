package com.croplite.worldgen;

import com.croplite.config.CropLiteConfig;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * Exclusive wild-spawn biome categories from the CropLite climate brief.
 *
 * <p>Vanilla biomes use tags / known IDs. Modded biomes (Terralith soft-dep and others)
 * are classified by <strong>base temperature + downfall (humidity)</strong>, with optional
 * common biome tags ({@code c:is_jungle}, {@code c:is_desert}, …) when present.
 *
 * <ul>
 *   <li>Tropical wet: jungles, warm swamps, or tropical temp + high humidity
 *   <li>Jungle: {@link BiomeTags#IS_JUNGLE} / {@code c:is_jungle} (basil / banana)
 *   <li>Savanna: {@link BiomeTags#IS_SAVANNA} / hot + arid (not desert-hot)
 *   <li>Desert: vanilla desert/badlands, {@code c:is_desert}, or very hot + arid
 *       (e.g. Terralith shrubland / brushland)
 *   <li>Cold / temperate: remaining land biomes by temperature band
 * </ul>
 */
public final class BiomeSpawnCategory {
	/** Downfall ≤ this + very hot → desert crop pool (shrubland, brushland, deserts). */
	public static final float ARID_MAX_HUMIDITY = 0.25F;
	/** Downfall ≤ this + hot (below desert-hot) → savanna crop pool. */
	public static final float SAVANNA_MAX_HUMIDITY = 0.35F;
	/** Downfall ≥ this → wet (jungle / swamp climate). */
	public static final float WET_MIN_HUMIDITY = 0.6F;
	/**
	 * Arid biomes at or above this temperature use the desert crop pool
	 * (shrubland, brushland, deserts). Hotter-than-temperate but cooler arid → savanna.
	 */
	public static final float DESERT_CLIMATE_MIN_TEMPERATURE = 1.15F;

	private static final TagKey<Biome> C_IS_JUNGLE = commonBiomeTag("is_jungle");
	private static final TagKey<Biome> C_IS_DESERT = commonBiomeTag("is_desert");
	private static final TagKey<Biome> C_IS_SAVANNA = commonBiomeTag("is_savanna");
	private static final TagKey<Biome> C_IS_SWAMP = commonBiomeTag("is_swamp");
	private static final TagKey<Biome> C_IS_BADLANDS = commonBiomeTag("is_badlands");

	private BiomeSpawnCategory() {
	}

	private static TagKey<Biome> commonBiomeTag(String path) {
		return TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", path));
	}

	/** Plains + sunflower plains — fruit trees never generate here. */
	public static boolean isPlains(Holder<Biome> biome) {
		return biome.is(Biomes.PLAINS) || biome.is(Biomes.SUNFLOWER_PLAINS);
	}

	/**
	 * Peach: cold/temperate land, not plains, base temp &lt; temperate max (default 0.8).
	 */
	public static boolean allowsPeachTree(Holder<Biome> biome) {
		return allowsPeachTree(biome, CropLiteConfig.get());
	}

	public static boolean allowsPeachTree(Holder<Biome> biome, CropLiteConfig config) {
		if (isPlains(biome) || isOceanOrRiver(biome)) {
			return false;
		}
		float temperature = biome.value().getBaseTemperature();
		if (temperature >= config.temperateCropMaxTemperature) {
			return false;
		}
		return isTemperateSpawn(biome) || isColdSpawn(biome);
	}

	/**
	 * Lemon: temperate land (sapling climate), not plains, base temp &lt; temperate max (default 0.8).
	 * Hot biomes (≥0.8) are excluded by the temperature gate.
	 */
	public static boolean allowsLemonTree(Holder<Biome> biome) {
		return allowsLemonTree(biome, CropLiteConfig.get());
	}

	public static boolean allowsLemonTree(Holder<Biome> biome, CropLiteConfig config) {
		if (isPlains(biome) || isOceanOrRiver(biome)) {
			return false;
		}
		float temperature = biome.value().getBaseTemperature();
		if (temperature >= config.temperateCropMaxTemperature) {
			return false;
		}
		return isTemperateSpawn(biome);
	}

	/**
	 * Banana: jungle biomes only, base temp &gt; temperate max (default 0.8).
	 */
	public static boolean allowsBananaTree(Holder<Biome> biome) {
		return allowsBananaTree(biome, CropLiteConfig.get());
	}

	public static boolean allowsBananaTree(Holder<Biome> biome, CropLiteConfig config) {
		if (!isJungle(biome)) {
			return false;
		}
		return biome.value().getBaseTemperature() > config.temperateCropMaxTemperature;
	}

	private static boolean isOceanOrRiver(Holder<Biome> biome) {
		return biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH);
	}

	public static boolean isTropicalWet(Holder<Biome> biome) {
		if (isOceanOrRiver(biome)) {
			return false;
		}
		if (isJungle(biome)) {
			return true;
		}

		Biome value = biome.value();
		float temperature = value.getBaseTemperature();
		var config = CropLiteConfig.get();
		if (temperature < config.tropicalCropMinTemperature) {
			return false;
		}

		// Vanilla / tagged warm swamps (ice marsh is cold — excluded by temp gate above).
		if (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP) || biome.is(C_IS_SWAMP)) {
			return true;
		}

		// Modded rainforests / wet tropics without jungle tags: high humidity + tropical temp.
		return BiomeClimate.humidityNonNegative(value) >= WET_MIN_HUMIDITY;
	}

	/** Jungle / sparse jungle / bamboo jungle / Terralith jungles — basil wild spawn only. */
	public static boolean isJungle(Holder<Biome> biome) {
		return biome.is(BiomeTags.IS_JUNGLE) || biome.is(C_IS_JUNGLE);
	}

	public static boolean isSavanna(Holder<Biome> biome) {
		if (biome.is(BiomeTags.IS_SAVANNA) || biome.is(C_IS_SAVANNA)) {
			return true;
		}
		if (isOceanOrRiver(biome) || isDesert(biome) || isTropicalWet(biome)) {
			return false;
		}
		// Hot + arid but not desert-hot (e.g. volcanic slopes, untagged dry tropics).
		Biome value = biome.value();
		float temperature = value.getBaseTemperature();
		float humidity = BiomeClimate.humidityNonNegative(value);
		var config = CropLiteConfig.get();
		return temperature >= config.tropicalCropMinTemperature
				&& temperature < DESERT_CLIMATE_MIN_TEMPERATURE
				&& humidity <= SAVANNA_MAX_HUMIDITY;
	}

	/**
	 * Hot arid land: vanilla desert/badlands, common desert/badlands tags (when hot),
	 * or climate (very hot + low humidity) — Terralith shrubland, brushland, deserts.
	 */
	public static boolean isDesert(Holder<Biome> biome) {
		if (isOceanOrRiver(biome)) {
			return false;
		}
		if (biome.is(Biomes.DESERT)
				|| biome.is(Biomes.BADLANDS)
				|| biome.is(Biomes.WOODED_BADLANDS)
				|| biome.is(Biomes.ERODED_BADLANDS)) {
			return true;
		}

		Biome value = biome.value();
		float temperature = value.getBaseTemperature();
		var config = CropLiteConfig.get();

		// Tagged deserts / hot badlands (excludes snowy_badlands via temperature).
		if (temperature >= config.tropicalCropMinTemperature
				&& (biome.is(BiomeTags.IS_BADLANDS)
						|| biome.is(C_IS_DESERT)
						|| biome.is(C_IS_BADLANDS))) {
			return true;
		}

		// Do not steal tagged savannas into the desert pool.
		if (biome.is(BiomeTags.IS_SAVANNA) || biome.is(C_IS_SAVANNA)) {
			return false;
		}
		if (isJungle(biome)) {
			return false;
		}

		// Shrubland / brushland / other very hot arid biomes.
		float humidity = BiomeClimate.humidityNonNegative(value);
		return temperature >= DESERT_CLIMATE_MIN_TEMPERATURE && humidity <= ARID_MAX_HUMIDITY;
	}

	/** Cold land biomes excluding ice spikes and cold/frozen oceans. */
	public static boolean isColdSpawn(Holder<Biome> biome) {
		if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER)) {
			return false;
		}
		if (biome.is(Biomes.ICE_SPIKES)) {
			return false;
		}
		if (biome.is(Biomes.FROZEN_OCEAN) || biome.is(Biomes.DEEP_FROZEN_OCEAN)
				|| biome.is(Biomes.COLD_OCEAN) || biome.is(Biomes.DEEP_COLD_OCEAN)) {
			return false;
		}
		if (isDesert(biome) || isSavanna(biome) || isTropicalWet(biome)) {
			return false;
		}
		float temperature = biome.value().getBaseTemperature();
		return temperature <= CropLiteConfig.get().coldCropMaxTemperature;
	}

	/**
	 * Temperate land biomes for wild spawns: in temperate temperature band,
	 * not tropical-wet / savanna / desert / ocean.
	 */
	public static boolean isTemperateSpawn(Holder<Biome> biome) {
		if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)) {
			return false;
		}
		if (isTropicalWet(biome) || isSavanna(biome) || isDesert(biome)) {
			return false;
		}
		float temperature = biome.value().getBaseTemperature();
		var config = CropLiteConfig.get();
		return temperature >= config.temperateCropMinTemperature
				&& temperature <= config.temperateCropMaxTemperature;
	}

	/** Biomes where sugar cane may generate (tropical wet, savanna, desert — not cold). */
	public static boolean allowsSugarCane(Holder<Biome> biome) {
		return isTropicalWet(biome) || isSavanna(biome) || isDesert(biome);
	}
}
