package com.tertonbiome;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * config/terrabiome.json — {@code world-size} climate diameter (five hard 20%
 * Z bands), display-temp ranges, land-over-ocean, plains↔meadow swap, skyland
 * ban, and structure whitelist. Cutoffs are not config keys; they are computed
 * from {@code world-size}.
 */
public final class TerratonicbiomesConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static TerratonicbiomesConfig INSTANCE = new TerratonicbiomesConfig();

	public static final int ZONE_COUNT = 5;
	/** Default N–S climate diameter: 5 zones × 6000 blocks. */
	public static final int DEFAULT_WORLD_SIZE = 30000;

	public boolean enabled = true;

	/**
	 * Log the enabled datapack stack (low→high) whenever packs are rebuilt.
	 * Useful to verify Terralith &lt; Tectonic &lt; Terrabiome ordering.
	 */
	@SerializedName("log-datapack-stack")
	public boolean logDatapackStack = true;

	/**
	 * Only these structures generate. Empty list = none.
	 * Names can be ids ({@code minecraft:mineshaft}, {@code terralith:mage_tower})
	 * or short names ({@code mineshafts}, {@code villages}).
	 * {@code amethyst_geodes} is a feature (not a structure) but is gated here.
	 * Terralith structures are banned unless listed. {@code *} or {@code all} allows everything.
	 */
	@SerializedName("allowed-structures")
	public List<String> allowedStructures = defaultAllowedStructures();
	/** Prefer land biomes when multi-noise would place ocean on land. */
	public boolean preferLandOverOcean = true;
	/** Continentalness at/above this is treated as beach/land for ocean bleed. */
	public float oceanBleedContinentalnessMin = -0.19f;
	/** Re-query biome pick with at least this continentalness when swapping ocean→land. */
	public float landBiasContinentalness = 0.05f;

	/**
	 * North–south diameter of the five climate zones, in blocks.
	 * Each zone is a hard 20% of this value. Default 30000 → 6000-block zones
	 * with temperate at −3000…+3000. Example: 60000 → 12000-block zones.
	 * This is the only knob for zone width; Z cutoffs are derived in code.
	 */
	@SerializedName("world-size")
	public int worldSize = DEFAULT_WORLD_SIZE;

	public double farNorthDisplayMin = -0.5;
	public double farNorthDisplayMax = 0.3;
	public double northDisplayMin = 0.25;
	public double northDisplayMax = 0.7;
	public double temperateDisplayMin = 0.5;
	public double temperateDisplayMax = 0.75;
	public double subtropicDisplayMin = 0.75;
	public double subtropicDisplayMax = 0.95;
	public double tropicalDisplayMin = 0.8;
	public double tropicalDisplayMax = 2.0;

	/**
	 * Meadow (wiki ~0.5) north of the Temperate|Warm cutoff; plains (wiki ~0.8)
	 * south of it. North: plains / sunflower_plains → meadow. South: meadow → plains.
	 */
	public boolean swapPlainsMeadow = true;

	/**
	 * ±blocks of slow 2D noise added to Z before band lookup and plains↔meadow swap.
	 * Makes latitude cutoffs meander instead of a straight east–west line. 0 = off.
	 */
	public int boundaryNoiseAmplitude = 100;
	/** Noise cell size in blocks. Larger = longer, smoother meanders (default 256). */
	public int boundaryNoiseScale = 256;
	/**
	 * Width in blocks of the display-temp lerp across each (wobbled) cutoff.
	 * 100 → ±50 around the line, so Terralith can mix both bands. 0 = hard edge.
	 */
	public int boundaryBlendWidth = 100;
	public int boundaryNoiseSeed = 87421;

	/**
	 * Replace Terralith skyland biomes and cancel {@code terralith:skylands/} features
	 * (floating islands). Default on.
	 */
	public boolean disableSkylands = true;

	/**
	 * Hot band only: stretch humidity so deserts/arid and jungles/bamboo each get
	 * about half the land (Terralith's raw parameter list is ~80% arid).
	 */
	public boolean hotDesertJungleMix = true;
	/**
	 * Target fraction of Hot <em>land</em> that should be wet (jungle/swamp)
	 * patches. Default {@code 0.5} = equal wet vs dry area. Dry = {@code 1 − this}.
	 */
	@SerializedName("hot-wet-land-fraction")
	public float hotWetLandFraction = 0.5f;
	/**
	 * When remapping Hot humidity, the dry half of noise maps into
	 * {@code [-1, hotDesertHumidityMax]}. Default −0.15 (arid slots).
	 */
	public float hotDesertHumidityMax = -0.15f;
	/**
	 * When remapping Hot humidity, the wet half of noise maps into
	 * {@code [hotJungleHumidityMin, 1]}. Default 0.30 (jungle/bamboo/swamp slots).
	 */
	public float hotJungleHumidityMin = 0.30f;
	/**
	 * Noise cell size in blocks for Hot wet/dry patch layout. Larger = bigger
	 * spaced-out regions (default 768 ≈ multi-kilometer patches).
	 */
	public int hotMixPatchScale = 768;
	public int hotMixPatchSeed = 91337;

	/**
	 * Replace cold mountain biomes ({@code frozen_peaks}, {@code jagged_peaks},
	 * {@code snowy_slopes}) south of {@link #coldMountainCutoffZ} with
	 * {@code windswept_hills}, or {@code stony_peaks} in the Hot band. Uses the
	 * same wobbled Z + {@code boundaryBlendWidth} as zone borders. Does not
	 * change Tectonic terrain — biome id only.
	 */
	public boolean overrideColdMountains = true;
	/**
	 * South of this Z (wobbled), cold mountains are replaced. Default 0 (middle
	 * of Temperate) so cold peaks stay north and temperate/warm can still snow
	 * when multi-noise would pick peaks, but not as frozen/jagged/snowy.
	 */
	public int coldMountainCutoffZ = 0;

	/**
	 * Ban warm swamps ({@code swamp}, {@code mangrove_swamp},
	 * {@code terralith:orchid_swamp}) in Temperate and anywhere north.
	 * Allowed only in Warm + Hot (south of Temperate|Warm cutoff). Does not
	 * affect {@code terralith:ice_marsh}. Uses the same wobbled Z + blend as
	 * zone borders.
	 */
	public boolean banSwampsNorthOfWarm = true;

	/**
	 * Beaches only as a thin Warm/Hot coastal strip ({@link #beachInlandMinBlocks}–
	 * {@link #beachInlandMaxBlocks} inland) plus a short ocean bleed.
	 * Freezing/Cold/Temperate never keep beaches. Rivers are never rewritten.
	 */
	@SerializedName("coastal-beach-rules")
	public boolean coastalBeachRules = true;
	/** Inland edge of the beach strip (blocks from ocean). Default 1. */
	@SerializedName("beach-inland-min-blocks")
	public int beachInlandMinBlocks = 1;
	/** How far inland beaches may extend from ocean (blocks). Default 12. */
	@SerializedName("beach-inland-max-blocks")
	public int beachInlandMaxBlocks = 12;
	/** How far beaches may paint into ocean from land (blocks). Default 4. */
	@SerializedName("beach-ocean-bleed-blocks")
	public int beachOceanBleedBlocks = 4;

	/** Legacy; migrated into {@link #coastalBeachRules}. */
	@Deprecated
	public boolean northernBeachRules = true;
	/** Unused; coastal strip replaced northern Z cutoffs. */
	@Deprecated
	public int northernBeachLandCutoffZ = 0;
	/** Unused. */
	@Deprecated
	public int northernSnowyBeachCutoffZ = 0;

	/**
	 * Hard zone whitelist: only biomes listed in {@code ZONE_BIOMES.md} may appear
	 * in each latitude band. Terralith multi-noise suggests a biome; this replaces
	 * anything outside the catalog. Terrain shape (Tectonic) is untouched.
	 */
	@SerializedName("enforce-zone-biomes")
	public boolean enforceZoneBiomes = true;

	/**
	 * Legacy partial overrides (alpha islands, sakura moves, etc.). Used only when
	 * {@link #enforceZoneBiomes} is false.
	 */
	public boolean zoneBiomeOverrides = true;

	/**
	 * Land biomes only: if a pocket is smaller than {@link #minLandBiomeSize}
	 * chunks, replace it with the nearest neighboring land biome.
	 * Oceans/rivers/beaches are never absorbed and never used as replacements.
	 * Multi-noise has no native minimum size — this is a post-filter.
	 */
	public boolean absorbSmallLandBiomes = true;
	/**
	 * Minimum land-biome span in chunks (16 blocks). Pockets that do not fill
	 * enough of a neighborhood of this scale are replaced with the nearest
	 * land biome (never beach/ocean/river). Default 64.
	 * Set {@code absorbSmallLandBiomes} false or this to 0 to disable.
	 */
	public int minLandBiomeSize = 64;

	/**
	 * When true, latitude + zone whitelist apply only at and above {@link #surfaceBiomeMinY}.
	 * Below that Y, Terralith caves are banned; only vanilla caves may spawn
	 * (rates in {@link #caveBiomes}).
	 */
	@SerializedName("surface-biome-bounds")
	public boolean surfaceBiomeBounds = true;
	/** No cave or underground biomes at or above this Y (default 45). */
	@SerializedName("surface-biome-min-y")
	public int surfaceBiomeMinY = SurfaceBiomeBounds.DEFAULT_MIN_Y;
	/** Upper bound for terrabiome surface placement (default 320). */
	@SerializedName("surface-biome-max-y")
	public int surfaceBiomeMaxY = SurfaceBiomeBounds.DEFAULT_MAX_Y;

	/**
	 * Vanilla cave spawn rates below {@link #surfaceBiomeMinY}.
	 * {@code 1.0} = keep every multi-noise pick (vanilla rate);
	 * {@code 0.1} ≈ one-tenth; {@code 0} = never.
	 * Keys: {@code minecraft:lush_caves}, {@code dripstone_caves}, etc.
	 * Terralith caves are never listed — they are always banned.
	 */
	@SerializedName("cave-biomes")
	public Map<String, Double> caveBiomes = defaultCaveBiomes();

	/**
	 * At and above {@link #highAltitudeMinY}, Warm/Hot land uses cooler highland
	 * biomes (zone-one-north + terrain form). Freezing/Cold/Temperate keep snowy
	 * peaks. See {@link HighAltitudeBiomes}.
	 */
	@SerializedName("high-altitude-zone-shift")
	public boolean highAltitudeZoneShift = true;
	/** Block Y at which high-altitude highland rules start (default 200). */
	@SerializedName("high-altitude-min-y")
	public int highAltitudeMinY = 200;
	/**
	 * Multi-noise erosion at or below this → {@link HighAltitudeBiomes.TerrainForm#MOUNTAIN}.
	 * Vanilla mountain slots are roughly ≤ −0.22.
	 */
	@SerializedName("high-altitude-mountain-erosion-max")
	public float highAltitudeMountainErosionMax = -0.22f;
	/**
	 * Erosion above mountain max and at or below this → plateau/highland.
	 * Above this → lowland (no Warm/Hot zone shift).
	 */
	@SerializedName("high-altitude-plateau-erosion-max")
	public float highAltitudePlateauErosionMax = 0.45f;
	/**
	 * |weirdness| above this with mild erosion counts as mountain (peak PV).
	 */
	@SerializedName("high-altitude-peak-weirdness-min")
	public float highAltitudePeakWeirdnessMin = 0.55f;

	/**
	 * Background {@code /terrabiome fix}: chunks processed per server-thread batch.
	 * Default 1 keeps load minimal while players are online.
	 */
	@SerializedName("fix-chunks-per-batch")
	public int fixChunksPerBatch = 1;
	/** Pause on the fixer worker thread between batches (ms). Default 500. */
	@SerializedName("fix-batch-delay-ms")
	public int fixBatchDelayMs = 500;

	public static List<String> defaultAllowedStructures() {
		List<String> defaults = new ArrayList<>();
		defaults.add("mineshafts");
		defaults.add("amethyst_geodes");
		return defaults;
	}

	/** Default {@code cave-biomes}: all vanilla caves at rate 1.0. */
	public static Map<String, Double> defaultCaveBiomes() {
		Map<String, Double> defaults = new LinkedHashMap<>();
		defaults.put("minecraft:lush_caves", 1.0);
		defaults.put("minecraft:dripstone_caves", 1.0);
		defaults.put("minecraft:deep_dark", 1.0);
		defaults.put("minecraft:sulfur_caves", 1.0);
		return defaults;
	}

	public static TerratonicbiomesConfig get() {
		return INSTANCE;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("terrabiome.json");
		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				TerratonicbiomesConfig loaded = GSON.fromJson(reader, TerratonicbiomesConfig.class);
				if (loaded != null) {
					INSTANCE = loaded;
					if (INSTANCE.allowedStructures == null) {
						INSTANCE.allowedStructures = defaultAllowedStructures();
					}
					if (INSTANCE.caveBiomes == null) {
						INSTANCE.caveBiomes = defaultCaveBiomes();
					}
					migrateLegacyIfNeeded(INSTANCE);
				}
			} catch (IOException e) {
				Terratonicbiomes.LOGGER.error("Failed to read {}", path, e);
			}
		}
		INSTANCE.applyWorldSize();
		save();
	}

	/**
	 * Width of one climate zone (Freezing, Cold, Temperate, Warm, Hot).
	 * Always exactly {@code world-size / 5} (20%).
	 */
	public int zoneWidth() {
		return Math.max(1, worldSize / ZONE_COUNT);
	}

	/**
	 * Snaps {@code world-size} to a multiple of 5 so each zone is an integer 20%.
	 */
	public void applyWorldSize() {
		if (worldSize < ZONE_COUNT) {
			worldSize = DEFAULT_WORLD_SIZE;
		}
		int zone = worldSize / ZONE_COUNT;
		worldSize = zone * ZONE_COUNT;
	}

	/** Freezing | Cold cutoff. Default −9000. Z ≤ this is Freezing. */
	public int zFarNorth() {
		return -zoneWidth() * 3 / 2;
	}

	/** Cold | Temperate cutoff. Default −3000. */
	public int zNorth() {
		return -zoneWidth() / 2;
	}

	/** Temperate | Warm cutoff. Default +3000. Also the plains↔meadow swap. */
	public int zSouth() {
		return zoneWidth() / 2;
	}

	/** Warm | Hot cutoff. Default +9000. */
	public int zFarSouth() {
		return zoneWidth() * 3 / 2;
	}

	/** Southern edge of the mapped diameter ({@code +world-size/2}). Default +15000. */
	public int zHotEdge() {
		return worldSize / 2;
	}

	/** Plains↔meadow swap Z: same as Temperate | Warm ({@link #zSouth()}). */
	public int plainsMeadowSwapZ() {
		return zSouth();
	}

	/**
	 * Four Z cutoffs between the five equal zones, derived from {@code world-size}:
	 * Freezing|Cold, Cold|Temperate, Temperate|Warm, Warm|Hot.
	 */
	public int[] zoneCutoffs() {
		return new int[] {zFarNorth(), zNorth(), zSouth(), zFarSouth()};
	}

	/**
	 * If an old config is missing {@code world-size} or display ranges, fill defaults.
	 * Extra JSON keys (old {@code zFarNorth} etc.) are ignored by Gson.
	 */
	private static void migrateLegacyIfNeeded(TerratonicbiomesConfig c) {
		boolean looksUninitialized =
			c.worldSize == 0
				|| (c.temperateDisplayMin == 0.0 && c.temperateDisplayMax == 0.0 && c.tropicalDisplayMax == 0.0);
		if (looksUninitialized) {
			c.worldSize = DEFAULT_WORLD_SIZE;
			c.farNorthDisplayMin = -0.5;
			c.farNorthDisplayMax = 0.3;
			c.northDisplayMin = 0.25;
			c.northDisplayMax = 0.7;
			c.temperateDisplayMin = 0.5;
			c.temperateDisplayMax = 0.75;
			c.subtropicDisplayMin = 0.75;
			c.subtropicDisplayMax = 0.95;
			c.tropicalDisplayMin = 0.8;
			c.tropicalDisplayMax = 2.0;
			c.swapPlainsMeadow = true;
			c.boundaryNoiseAmplitude = 100;
			c.boundaryNoiseScale = 256;
			c.boundaryBlendWidth = 100;
			c.boundaryNoiseSeed = 87421;
			c.disableSkylands = true;
			c.hotDesertJungleMix = true;
			c.hotWetLandFraction = 0.5f;
			c.hotDesertHumidityMax = -0.15f;
			c.hotJungleHumidityMin = 0.30f;
			c.hotMixPatchScale = 768;
			c.hotMixPatchSeed = 91337;
			c.overrideColdMountains = true;
			c.coldMountainCutoffZ = 0;
			c.banSwampsNorthOfWarm = true;
			c.coastalBeachRules = true;
			c.beachInlandMinBlocks = 1;
			c.beachInlandMaxBlocks = 12;
			c.beachOceanBleedBlocks = 4;
			c.northernBeachRules = true;
			c.enforceZoneBiomes = true;
			c.zoneBiomeOverrides = true;
			c.absorbSmallLandBiomes = true;
			c.minLandBiomeSize = 64;
			c.surfaceBiomeBounds = true;
			c.surfaceBiomeMinY = SurfaceBiomeBounds.DEFAULT_MIN_Y;
			c.surfaceBiomeMaxY = SurfaceBiomeBounds.DEFAULT_MAX_Y;
			c.caveBiomes = defaultCaveBiomes();
		}
		if (c.worldSize < ZONE_COUNT) {
			c.worldSize = DEFAULT_WORLD_SIZE;
		}
		if (c.boundaryNoiseScale <= 0) {
			c.boundaryNoiseScale = 256;
			if (c.boundaryNoiseSeed == 0) {
				c.boundaryNoiseSeed = 87421;
			}
			if (c.boundaryBlendWidth == 0 && c.boundaryNoiseAmplitude == 0) {
				c.boundaryNoiseAmplitude = 100;
				c.boundaryBlendWidth = 100;
			}
		}
		// Old configs omit these keys; Gson leaves floats/ints at 0 and booleans false.
		if (c.hotJungleHumidityMin == 0f && c.hotDesertHumidityMax == 0f) {
			c.hotDesertJungleMix = true;
			c.hotDesertHumidityMax = -0.15f;
			c.hotJungleHumidityMin = 0.30f;
		}
		if (c.hotWetLandFraction <= 0f || c.hotWetLandFraction > 1f) {
			c.hotWetLandFraction = 0.5f;
		}
		if (c.hotMixPatchScale <= 0) {
			c.hotMixPatchScale = 768;
		}
		if (c.hotMixPatchSeed == 0) {
			c.hotMixPatchSeed = 91337;
		}
		if (c.minLandBiomeSize == 0 || c.minLandBiomeSize == 24) {
			c.absorbSmallLandBiomes = true;
			c.minLandBiomeSize = 64;
		}
		if (c.beachInlandMaxBlocks <= 0) {
			c.beachInlandMaxBlocks = 12;
		}
		if (c.beachInlandMinBlocks <= 0) {
			c.beachInlandMinBlocks = 1;
		}
		if (c.beachOceanBleedBlocks < 0) {
			c.beachOceanBleedBlocks = 4;
		}
		// Old configs only had northernBeachRules; keep strip on unless explicitly off.
		if (!c.northernBeachRules) {
			c.coastalBeachRules = false;
		}
		if (c.surfaceBiomeMinY <= 0) {
			c.surfaceBiomeMinY = SurfaceBiomeBounds.DEFAULT_MIN_Y;
		}
		if (c.surfaceBiomeMaxY <= c.surfaceBiomeMinY) {
			c.surfaceBiomeMaxY = SurfaceBiomeBounds.DEFAULT_MAX_Y;
		}
		if (c.caveBiomes == null || c.caveBiomes.isEmpty()) {
			c.caveBiomes = defaultCaveBiomes();
		} else {
			// Keep user rates; only add missing vanilla caves at 1.0.
			Map<String, Double> merged = new LinkedHashMap<>();
			for (Map.Entry<String, Double> entry : c.caveBiomes.entrySet()) {
				if (entry.getKey() != null && entry.getValue() != null) {
					merged.put(entry.getKey(), entry.getValue());
				}
			}
			for (Map.Entry<String, Double> def : defaultCaveBiomes().entrySet()) {
				String full = def.getKey();
				String path = full.contains(":") ? full.substring(full.indexOf(':') + 1) : full;
				if (!merged.containsKey(full)
					&& !merged.containsKey(path)
					&& !merged.containsKey("minecraft:" + path)) {
					merged.put(full, def.getValue());
				}
			}
			c.caveBiomes = merged;
		}
		if (c.highAltitudeMinY <= 0) {
			c.highAltitudeMinY = 200;
		}
		if (c.highAltitudePlateauErosionMax == 0f && c.highAltitudeMountainErosionMax == 0f) {
			c.highAltitudeZoneShift = true;
			c.highAltitudeMountainErosionMax = -0.22f;
			c.highAltitudePlateauErosionMax = 0.45f;
			c.highAltitudePeakWeirdnessMin = 0.55f;
		}
		// Legacy: configs that disabled zoneBiomeOverrides should not enforce whitelist either.
		if (!c.zoneBiomeOverrides) {
			c.enforceZoneBiomes = false;
		}
	}

	public static void save() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("terrabiome.json");
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(INSTANCE, writer);
			}
		} catch (IOException e) {
			Terratonicbiomes.LOGGER.error("Failed to write {}", path, e);
		}
	}
}
