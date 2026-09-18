package com.tertonbiome;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

import java.util.List;

/**
 * Replaces Terralith/Tectonic multi-noise <em>land</em> picks that fall outside
 * the latitude zone whitelist. Oceans, rivers, and beaches are never rewritten
 * (temperature mods keep ocean climates). Uses climate distance (temperature +
 * humidity) among allowed land candidates — terrain shape is unchanged because
 * continentalness, erosion, depth, and weirdness are never modified here.
 */
public final class ZoneBiomeEnforcer {
	private ZoneBiomeEnforcer() {}

	@FunctionalInterface
	public interface BiomeFinder {
		Holder<Biome> find(ResourceKey<Biome> key);
	}

	public static Holder<Biome> enforce(
		Holder<Biome> biome,
		int blockX,
		int blockZ,
		Climate.TargetPoint forced,
		BiomeFinder finder
	) {
		if (LandBiomes.isWaterOrCoast(biome)) {
			return biome;
		}
		ClimateZone zone = ClimateZone.at(blockX, blockZ);
		if (isWhitelisted(zone, biome) && !needsHotMoistureReplace(zone, biome, blockX, blockZ)) {
			return biome;
		}
		return replace(biome, zone, blockX, blockZ, forced, finder);
	}

	/** Final guard for surface columns: strip any cave/underground biome. */
	public static Holder<Biome> enforceSurface(
		Holder<Biome> biome,
		int blockX,
		int blockZ,
		Climate.TargetPoint forced,
		BiomeFinder finder
	) {
		if (LandBiomes.isWaterOrCoast(biome)) {
			return biome;
		}
		ClimateZone zone = ClimateZone.at(blockX, blockZ);
		if (!CaveBiomeBan.isCaveBiome(biome)
			&& isWhitelisted(zone, biome)
			&& !needsHotMoistureReplace(zone, biome, blockX, blockZ)) {
			return biome;
		}
		return replace(biome, zone, blockX, blockZ, forced, finder);
	}

	private static boolean needsHotMoistureReplace(ClimateZone zone, Holder<Biome> biome, int blockX, int blockZ) {
		if (zone != ClimateZone.HOT || !TerratonicbiomesConfig.get().hotDesertJungleMix) {
			return false;
		}
		if (LandBiomes.isWaterOrCoast(biome)) {
			return false;
		}
		return !ZoneBiomeCatalog.matchesHotMoisture(biome, HotClimateMix.wantWet(blockX, blockZ));
	}

	private static boolean isWhitelisted(ClimateZone zone, Holder<Biome> biome) {
		return ZoneBiomeCatalog.isAllowed(zone, biome)
			&& !ZoneBiomeCatalog.isGloballyBanned(biome)
			&& !CaveBiomeBan.isCaveBiome(biome);
	}

	/**
	 * True when a surface <em>land</em> biome violates the zone whitelist or Hot
	 * moisture rules. Oceans/rivers/beaches are never illegal.
	 */
	public static boolean isIllegal(Holder<Biome> biome, int blockX, int blockZ) {
		if (LandBiomes.isWaterOrCoast(biome)) {
			return false;
		}
		ClimateZone zone = ClimateZone.at(blockX, blockZ);
		return !isWhitelisted(zone, biome) || needsHotMoistureReplace(zone, biome, blockX, blockZ);
	}

	/**
	 * Pick a random allowed land replacement for an illegal surface biome. Uses
	 * position-seeded selection so reruns are stable unless the seed inputs change.
	 * Water/coast originals are returned unchanged.
	 */
	public static Holder<Biome> pickRandomReplacement(
		Holder<Biome> original,
		int blockX,
		int blockY,
		int blockZ,
		BiomeFinder finder
	) {
		if (LandBiomes.isWaterOrCoast(original)) {
			return original;
		}
		ClimateZone zone = ClimateZone.at(blockX, blockZ);
		ZoneBiomeCatalog.HotMoisture hotMoisture = hotMoistureFilter(zone, blockX, blockZ);
		List<ResourceKey<Biome>> candidates = candidateList(zone, hotMoisture);
		if (candidates.isEmpty()) {
			return original;
		}
		int index = (int) (hash01(blockX, blockY, blockZ) * candidates.size());
		if (index >= candidates.size()) {
			index = candidates.size() - 1;
		}
		ResourceKey<Biome> key = candidates.get(index);
		Holder<Biome> found = finder.find(key);
		return found != null ? found : original;
	}

	private static List<ResourceKey<Biome>> candidateList(
		ClimateZone zone,
		ZoneBiomeCatalog.HotMoisture hotMoisture
	) {
		List<ResourceKey<Biome>> candidates = ZoneBiomeCatalog.candidates(zone, false, hotMoisture);
		if (candidates.isEmpty()) {
			candidates = ZoneBiomeCatalog.candidates(zone, false);
		}
		return candidates.stream().filter(key -> !CaveBiomeBan.isCaveKey(key)).toList();
	}

	private static Holder<Biome> replace(
		Holder<Biome> original,
		ClimateZone zone,
		int blockX,
		int blockZ,
		Climate.TargetPoint forced,
		BiomeFinder finder
	) {
		if (LandBiomes.isWaterOrCoast(original)) {
			return original;
		}
		ZoneBiomeCatalog.HotMoisture hotMoisture = hotMoistureFilter(zone, blockX, blockZ);
		List<ResourceKey<Biome>> candidates = ZoneBiomeCatalog.candidates(zone, false, hotMoisture);
		if (candidates.isEmpty()) {
			candidates = ZoneBiomeCatalog.candidates(zone, false);
		}
		if (candidates.isEmpty()) {
			return original;
		}

		float wantTemp = Climate.unquantizeCoord(forced.temperature());
		float wantHumid = Climate.unquantizeCoord(forced.humidity());

		ResourceKey<Biome> bestKey = null;
		float bestScore = Float.MAX_VALUE;
		for (ResourceKey<Biome> key : candidates) {
			if (CaveBiomeBan.isCaveKey(key)) {
				continue;
			}
			ZoneBiomeCatalog.Entry entry = ZoneBiomeCatalog.entry(key);
			if (entry == null) {
				continue;
			}
			float biomeTemp = LatitudeTemperature.displayToNoise(entry.displayTemp());
			float biomeHumid = downfallToHumidity(entry.downfall());
			float score = (biomeTemp - wantTemp) * (biomeTemp - wantTemp)
				+ (biomeHumid - wantHumid) * (biomeHumid - wantHumid);
			score += (float) hash01(blockX, blockZ, key.hashCode()) * 0.02f;
			if (score < bestScore) {
				bestScore = score;
				bestKey = key;
			}
		}

		if (bestKey == null) {
			return original;
		}
		Holder<Biome> found = finder.find(bestKey);
		return found != null ? found : original;
	}

	private static ZoneBiomeCatalog.HotMoisture hotMoistureFilter(
		ClimateZone zone,
		int blockX,
		int blockZ
	) {
		if (zone != ClimateZone.HOT || !TerratonicbiomesConfig.get().hotDesertJungleMix) {
			return null;
		}
		return HotClimateMix.wantWet(blockX, blockZ)
			? ZoneBiomeCatalog.HotMoisture.WET
			: ZoneBiomeCatalog.HotMoisture.DRY;
	}

	/** Rough mapping from wiki downfall to multi-noise humidity for scoring. */
	private static float downfallToHumidity(float downfall) {
		return Math.max(-1f, Math.min(1f, downfall * 2f - 0.5f));
	}

	private static double hash01(int x, int z, int seed) {
		int n = x * 374761393 + z * 668265263 + seed * 1274126177;
		n = (n ^ (n >> 13)) * 1274126177;
		n = n ^ (n >> 16);
		return (n & 0x7fffffff) / (double) Integer.MAX_VALUE;
	}
}
