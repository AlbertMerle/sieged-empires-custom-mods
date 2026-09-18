package com.tertonbiome;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * High-Y highland rules (default Y ≥ 200). Classifies columns as
 * {@link TerrainForm#MOUNTAIN} vs {@link TerrainForm#PLATEAU} from multi-noise
 * erosion/weirdness, then forces matching biome families:
 * <ul>
 *   <li><b>Mountains</b> — always peak biomes ({@code jagged_peaks},
 *       {@code frozen_peaks}, {@code stony_peaks}, volcanic peaks, etc.).
 *       Never forests or meadows.</li>
 *   <li><b>Plateaus</b> — forests, meadows, highlands, sakura. Never peaks.</li>
 *   <li><b>Warm / Hot</b> — no snowy peak biomes (use {@code stony_peaks} etc.).</li>
 * </ul>
 */
public final class HighAltitudeBiomes {
	private HighAltitudeBiomes() {}

	/** Terrain form inferred from climate parameters (not block heightmaps). */
	public enum TerrainForm {
		/** Low erosion / peak PV — jagged mountains → peak biomes only. */
		MOUNTAIN,
		/** Mid erosion — high flats → forests / highland plateaus only. */
		PLATEAU,
		/** Flat / eroded lowlands — no high-altitude zone shift. */
		LOWLAND
	}

	/** True peak / cliff / spire biomes — mountains may only pick these. */
	private static final Set<ResourceKey<Biome>> PEAK_BIOMES = Set.of(
		mc("frozen_peaks"),
		mc("jagged_peaks"),
		mc("snowy_slopes"),
		mc("stony_peaks"),
		mc("windswept_gravelly_hills"),
		tl("emerald_peaks"),
		tl("scarlet_mountains"),
		tl("frozen_cliffs"),
		tl("glacial_chasm"),
		tl("rocky_mountains"),
		tl("windswept_spires"),
		tl("stony_spires"),
		tl("basalt_cliffs"),
		tl("granite_cliffs"),
		tl("yosemite_cliffs"),
		tl("white_cliffs"),
		tl("haze_mountain"),
		tl("volcanic_peaks"),
		tl("volcanic_crater"),
		tl("painted_mountains"),
		tl("jungle_mountains"),
		tl("desert_spires")
	);

	/** Preferred Warm/Hot mountain peaks (never snow; keep list matches zone shortlist). */
	private static final List<ResourceKey<Biome>> WARM_HOT_PEAKS = List.of(
		tl("jungle_mountains"),
		mc("stony_peaks")
	);

	/** Preferred cool-climate mountain peaks (snow OK; shortlist + vanilla). */
	private static final List<ResourceKey<Biome>> COOL_PEAKS = List.of(
		mc("jagged_peaks"),
		mc("frozen_peaks"),
		mc("snowy_slopes"),
		tl("scarlet_mountains"),
		mc("windswept_gravelly_hills")
	);

	/** Plateau / high-flat biomes — forests and highland flats, never peaks. */
	private static final Set<ResourceKey<Biome>> PLATEAU_BIOMES = Set.of(
		mc("forest"),
		mc("flower_forest"),
		mc("birch_forest"),
		mc("old_growth_birch_forest"),
		mc("dark_forest"),
		mc("pale_garden"),
		mc("meadow"),
		mc("cherry_grove"),
		mc("plains"),
		mc("sunflower_plains"),
		mc("taiga"),
		mc("old_growth_pine_taiga"),
		mc("old_growth_spruce_taiga"),
		tl("blooming_plateau"),
		tl("blooming_valley"),
		tl("temperate_highlands"),
		tl("forested_highlands"),
		tl("alpine_highlands"),
		tl("highlands"),
		tl("lavender_forest"),
		tl("lavender_valley"),
		tl("sakura_grove"),
		tl("sakura_valley"),
		tl("moonlight_grove"),
		tl("moonlight_valley"),
		tl("cloud_forest"),
		tl("shield"),
		tl("steppe"),
		tl("valley_clearing"),
		tl("yosemite_lowlands"),
		tl("lush_valley")
	);

	@FunctionalInterface
	public interface BiomeFinder {
		Holder<Biome> find(ResourceKey<Biome> key);
	}

	public static boolean applies(int blockY) {
		TerratonicbiomesConfig c = TerratonicbiomesConfig.get();
		return c.highAltitudeZoneShift && blockY >= c.highAltitudeMinY;
	}

	/**
	 * Freezing/Cold/Temperate at high Y: keep snowy mountain biomes
	 * ({@link ColdMountainOverride} should not strip them).
	 */
	public static boolean allowSnowyPeaks(int blockY, int blockX, int blockZ) {
		if (!applies(blockY)) {
			return false;
		}
		ClimateZone zone = ClimateZone.at(blockX, blockZ);
		return zone == ClimateZone.FREEZING
			|| zone == ClimateZone.COLD
			|| zone == ClimateZone.TEMPERATE;
	}

	/**
	 * Force peak biomes on mountains and forest/highland biomes on plateaus.
	 */
	public static Holder<Biome> apply(
		Holder<Biome> biome,
		int blockX,
		int blockY,
		int blockZ,
		Climate.TargetPoint forced,
		BiomeFinder finder
	) {
		if (!applies(blockY) || LandBiomes.isWaterOrCoast(biome)) {
			return biome;
		}
		TerratonicbiomesConfig c = TerratonicbiomesConfig.get();
		TerrainForm form = classifyTerrain(forced, c);
		if (form == TerrainForm.LOWLAND) {
			return biome;
		}
		ClimateZone zone = ClimateZone.at(blockX, blockZ);
		boolean warmHot = zone == ClimateZone.WARM || zone == ClimateZone.HOT;

		if (form == TerrainForm.MOUNTAIN) {
			return forcePeak(biome, warmHot, blockX, blockZ, forced, finder);
		}
		return forcePlateau(biome, zone, warmHot, blockX, blockZ, forced, finder);
	}

	/**
	 * Classify using multi-noise erosion (+ peak-ish weirdness). Does not read
	 * chunk heightmaps — Tectonic height and these parameters are correlated.
	 */
	public static TerrainForm classifyTerrain(Climate.TargetPoint point, TerratonicbiomesConfig c) {
		float erosion = Climate.unquantizeCoord(point.erosion());
		float weirdness = Climate.unquantizeCoord(point.weirdness());
		float cont = Climate.unquantizeCoord(point.continentalness());
		if (cont < c.oceanBleedContinentalnessMin) {
			return TerrainForm.LOWLAND;
		}
		boolean peakWeird = Math.abs(weirdness) > c.highAltitudePeakWeirdnessMin;
		if (erosion <= c.highAltitudeMountainErosionMax || (erosion <= 0.05f && peakWeird)) {
			return TerrainForm.MOUNTAIN;
		}
		if (erosion <= c.highAltitudePlateauErosionMax) {
			return TerrainForm.PLATEAU;
		}
		return TerrainForm.LOWLAND;
	}

	/** Mountains always become a peak biome (stony/jagged/frozen/volcanic…). */
	private static Holder<Biome> forcePeak(
		Holder<Biome> biome,
		boolean warmHot,
		int blockX,
		int blockZ,
		Climate.TargetPoint forced,
		BiomeFinder finder
	) {
		ClimateZone zone = ClimateZone.at(blockX, blockZ);
		if (zone == ClimateZone.HOT && TerratonicbiomesConfig.get().hotDesertJungleMix) {
			boolean wantWet = HotClimateMix.wantWet(blockX, blockZ);
			List<ResourceKey<Biome>> hotPeaks = wantWet
				? List.of(tl("jungle_mountains"), mc("stony_peaks"))
				: List.of(mc("stony_peaks"), tl("jungle_mountains"));
			if (isAllowedPeak(biome, true) && ZoneBiomeCatalog.matchesHotMoisture(biome, wantWet)) {
				return biome;
			}
			Holder<Biome> picked = pickBest(hotPeaks, blockX, blockZ, forced, finder, true);
			if (picked != null && ZoneBiomeCatalog.matchesHotMoisture(picked, wantWet)) {
				return picked;
			}
			ResourceKey<Biome> fallbackKey = wantWet ? tl("jungle_mountains") : mc("stony_peaks");
			Holder<Biome> fallback = finder.find(fallbackKey);
			if (fallback != null) {
				return fallback;
			}
			Holder<Biome> stony = finder.find(mc("stony_peaks"));
			return stony != null ? stony : biome;
		}
		if (isAllowedPeak(biome, warmHot)) {
			return biome;
		}
		List<ResourceKey<Biome>> preferred = warmHot ? WARM_HOT_PEAKS : COOL_PEAKS;
		Holder<Biome> picked = pickBest(preferred, blockX, blockZ, forced, finder, warmHot);
		if (picked != null) {
			return picked;
		}
		// Fallbacks that must exist in Terralith+vanilla overworlds.
		if (warmHot) {
			Holder<Biome> stony = finder.find(mc("stony_peaks"));
			return stony != null ? stony : biome;
		}
		Holder<Biome> jagged = finder.find(mc("jagged_peaks"));
		if (jagged != null) {
			return jagged;
		}
		Holder<Biome> frozen = finder.find(mc("frozen_peaks"));
		return frozen != null ? frozen : biome;
	}

	/** Plateaus become forests / highland flats — never peaks. */
	private static Holder<Biome> forcePlateau(
		Holder<Biome> biome,
		ClimateZone zone,
		boolean warmHot,
		int blockX,
		int blockZ,
		Climate.TargetPoint forced,
		BiomeFinder finder
	) {
		ZoneBiomeCatalog.HotMoisture hotMoisture = null;
		if (zone == ClimateZone.HOT && TerratonicbiomesConfig.get().hotDesertJungleMix) {
			hotMoisture = HotClimateMix.wantWet(blockX, blockZ)
				? ZoneBiomeCatalog.HotMoisture.WET
				: ZoneBiomeCatalog.HotMoisture.DRY;
		}
		if (isPlateauBiome(biome)
			&& !(warmHot && isSnowy(biome))
			&& (hotMoisture == null || ZoneBiomeCatalog.matchesHotMoisture(
				biome, hotMoisture == ZoneBiomeCatalog.HotMoisture.WET))
			&& (zone != ClimateZone.HOT || ZoneBiomeCatalog.isAllowed(zone, biome))) {
			return biome;
		}
		LinkedHashSet<ResourceKey<Biome>> candidates = new LinkedHashSet<>();
		if (zone == ClimateZone.HOT) {
			// Stay inside Hot catalog + moisture class (do not import Temperate forests).
			addHotPlateaus(candidates, hotMoisture);
		} else {
			// Prefer zone-one-north forests for Warm; local + cooler for others.
			ClimateZone donor = warmHot ? ClimateZone.TEMPERATE : zone;
			addPlateausFromZone(candidates, donor);
			if (warmHot) {
				addPlateausFromZone(candidates, ClimateZone.WARM);
				addPlateausFromZone(candidates, ClimateZone.TEMPERATE);
			} else if (zone == ClimateZone.TEMPERATE) {
				addPlateausFromZone(candidates, ClimateZone.TEMPERATE);
				addPlateausFromZone(candidates, ClimateZone.COLD);
			} else {
				addPlateausFromZone(candidates, zone);
			}
			candidates.add(mc("forest"));
			candidates.add(mc("flower_forest"));
			candidates.add(mc("meadow"));
			candidates.add(tl("temperate_highlands"));
			candidates.add(tl("moonlight_grove"));
			candidates.add(tl("alpine_highlands"));
		}

		List<ResourceKey<Biome>> list = new ArrayList<>(candidates);
		if (warmHot) {
			list.removeIf(HighAltitudeBiomes::isSnowyKey);
		}
		Holder<Biome> picked = pickBest(list, blockX, blockZ, forced, finder, warmHot);
		if (picked != null) {
			return picked;
		}
		if (hotMoisture == ZoneBiomeCatalog.HotMoisture.WET) {
			Holder<Biome> jungle = finder.find(mc("jungle"));
			return jungle != null ? jungle : biome;
		}
		if (hotMoisture == ZoneBiomeCatalog.HotMoisture.DRY) {
			Holder<Biome> savanna = finder.find(mc("savanna_plateau"));
			return savanna != null ? savanna : biome;
		}
		Holder<Biome> forest = finder.find(mc("forest"));
		return forest != null ? forest : biome;
	}

	/** Hot-zone plateau candidates: highland-ish land from the Hot whitelist only. */
	private static void addHotPlateaus(
		Set<ResourceKey<Biome>> out,
		ZoneBiomeCatalog.HotMoisture moisture
	) {
		for (ResourceKey<Biome> key : ZoneBiomeCatalog.candidates(ClimateZone.HOT, false, moisture)) {
			if (PEAK_BIOMES.contains(key) || pathLooksLikePeak(key.identifier().getPath())) {
				continue;
			}
			if (PLATEAU_BIOMES.contains(key) || pathLooksLikePlateau(key.identifier().getPath())) {
				out.add(key);
			}
		}
		// Guaranteed fallbacks inside Hot wet/dry lists.
		if (moisture == ZoneBiomeCatalog.HotMoisture.WET) {
			out.add(mc("jungle"));
			out.add(mc("bamboo_jungle"));
			out.add(mc("sparse_jungle"));
			out.add(tl("tropical_jungle"));
		} else {
			out.add(mc("savanna_plateau"));
			out.add(mc("savanna"));
			out.add(mc("wooded_badlands"));
			out.add(tl("brushland"));
		}
	}

	private static void addPlateausFromZone(Set<ResourceKey<Biome>> out, ClimateZone zone) {
		for (ResourceKey<Biome> key : ZoneBiomeCatalog.allForZone(zone)) {
			if (PLATEAU_BIOMES.contains(key) && !PEAK_BIOMES.contains(key)) {
				out.add(key);
			}
		}
		for (ResourceKey<Biome> key : PLATEAU_BIOMES) {
			if (ZoneBiomeCatalog.isAllowed(zone, key)) {
				out.add(key);
			}
		}
	}

	private static Holder<Biome> pickBest(
		List<ResourceKey<Biome>> candidates,
		int blockX,
		int blockZ,
		Climate.TargetPoint forced,
		BiomeFinder finder,
		boolean banSnow
	) {
		float wantHumid = Climate.unquantizeCoord(forced.humidity());
		ResourceKey<Biome> bestKey = null;
		float bestScore = Float.MAX_VALUE;
		for (ResourceKey<Biome> key : candidates) {
			if (CaveBiomeBan.isCaveKey(key)) {
				continue;
			}
			if (banSnow && isSnowyKey(key)) {
				continue;
			}
			Holder<Biome> found = finder.find(key);
			if (found == null) {
				continue;
			}
			ZoneBiomeCatalog.Entry entry = ZoneBiomeCatalog.entry(key);
			float biomeHumid = entry != null ? downfallToHumidity(entry.downfall()) : 0f;
			float score = (biomeHumid - wantHumid) * (biomeHumid - wantHumid);
			score += peakOrForestBias(key);
			score += (float) hash01(blockX, blockZ, key.hashCode()) * 0.02f;
			if (score < bestScore) {
				bestScore = score;
				bestKey = key;
			}
		}
		return bestKey != null ? finder.find(bestKey) : null;
	}

	private static float peakOrForestBias(ResourceKey<Biome> key) {
		String path = key.identifier().getPath().toLowerCase(Locale.ROOT);
		if (path.equals("stony_peaks") || path.equals("jagged_peaks") || path.equals("frozen_peaks")) {
			return -0.12f;
		}
		if (path.contains("peaks") || path.contains("spires")) {
			return -0.06f;
		}
		if (path.equals("forest") || path.equals("flower_forest") || path.contains("highlands")) {
			return -0.08f;
		}
		if (path.contains("forest") || path.equals("meadow") || path.contains("plateau")) {
			return -0.05f;
		}
		return 0f;
	}

	private static boolean isAllowedPeak(Holder<Biome> biome, boolean warmHot) {
		return biome.unwrapKey().map(key -> {
			if (!PEAK_BIOMES.contains(key) && !pathLooksLikePeak(key.identifier().getPath())) {
				return false;
			}
			if (warmHot && isSnowyKey(key)) {
				return false;
			}
			return true;
		}).orElse(false);
	}

	private static boolean isPlateauBiome(Holder<Biome> biome) {
		return biome.unwrapKey().map(key -> {
			if (PEAK_BIOMES.contains(key) || pathLooksLikePeak(key.identifier().getPath())) {
				return false;
			}
			return PLATEAU_BIOMES.contains(key) || pathLooksLikePlateau(key.identifier().getPath());
		}).orElse(false);
	}

	private static boolean pathLooksLikePeak(String path) {
		String p = path.toLowerCase(Locale.ROOT);
		return p.contains("peaks")
			|| p.contains("peak")
			|| p.endsWith("_cliffs")
			|| p.contains("spires")
			|| p.contains("volcanic")
			|| p.equals("rocky_mountains")
			|| p.equals("haze_mountain")
			|| p.equals("jungle_mountains")
			|| p.equals("scarlet_mountains");
	}

	private static boolean pathLooksLikePlateau(String path) {
		String p = path.toLowerCase(Locale.ROOT);
		return p.contains("forest")
			|| p.contains("plateau")
			|| p.contains("highland")
			|| p.contains("meadow")
			|| p.contains("grove")
			|| p.contains("sakura")
			|| p.contains("lavender")
			|| p.equals("plains")
			|| p.equals("sunflower_plains")
			|| p.contains("steppe")
			|| p.equals("shield");
	}

	public static boolean isSnowy(Holder<Biome> biome) {
		return biome.unwrapKey().map(HighAltitudeBiomes::isSnowyKey).orElse(false);
	}

	public static boolean isSnowyKey(ResourceKey<Biome> key) {
		String path = key.identifier().getPath().toLowerCase(Locale.ROOT);
		return path.equals("frozen_peaks")
			|| path.equals("jagged_peaks")
			|| path.equals("snowy_slopes")
			|| path.startsWith("snowy_")
			|| path.startsWith("frozen_")
			|| path.startsWith("wintry_")
			|| path.contains("ice_")
			|| path.equals("grove")
			|| path.equals("alpine_grove")
			|| path.equals("siberian_grove")
			|| path.equals("siberian_taiga")
			|| path.equals("glacial_chasm")
			|| path.equals("frozen_cliffs")
			|| path.equals("emerald_peaks")
			|| path.equals("scarlet_mountains");
	}

	private static float downfallToHumidity(float downfall) {
		return Math.max(-1f, Math.min(1f, downfall * 2f - 0.5f));
	}

	private static double hash01(int x, int z, int seed) {
		int n = x * 374761393 + z * 668265263 + seed * 1274126177;
		n = (n ^ (n >> 13)) * 1274126177;
		n = n ^ (n >> 16);
		return (n & 0x7fffffff) / (double) Integer.MAX_VALUE;
	}

	private static ResourceKey<Biome> mc(String path) {
		return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("minecraft", path));
	}

	private static ResourceKey<Biome> tl(String path) {
		return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("terralith", path));
	}
}
