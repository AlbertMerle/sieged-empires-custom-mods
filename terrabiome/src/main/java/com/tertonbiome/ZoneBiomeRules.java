package com.tertonbiome;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

import java.util.Set;

/**
 * Hard zone placement rules on top of latitude temperature remapping:
 * <ul>
 *   <li>Ban all Terralith alpha island biomes</li>
 *   <li>Move windswept/gravel/yellowstone family Freezing → Cold</li>
 *   <li>Ban mushroom_fields + hot_shrubland from Warm</li>
 *   <li>Sakura grove/valley only in Temperate + Warm (inject in Warm from cherry)</li>
 *   <li>Keep / promote snowy_cherry_grove in Freezing</li>
 * </ul>
 */
public final class ZoneBiomeRules {
	private ZoneBiomeRules() {}

	public static final ResourceKey<Biome> SNOWY_CHERRY_GROVE = terralith("snowy_cherry_grove");
	public static final ResourceKey<Biome> GRAVEL_DESERT = terralith("gravel_desert");
	public static final ResourceKey<Biome> WINDSWEPT_SPIRES = terralith("windswept_spires");
	public static final ResourceKey<Biome> YELLOWSTONE = terralith("yellowstone");
	public static final ResourceKey<Biome> SAKURA_GROVE = terralith("sakura_grove");
	public static final ResourceKey<Biome> SAKURA_VALLEY = terralith("sakura_valley");

	private static final Set<String> ALPHA_PATHS = Set.of("alpha_islands", "alpha_islands_winter");

	/** Banned in Freezing; injected into Cold from rocky/steppe neighbors. */
	private static final Set<String> FREEZING_TO_COLD = Set.of(
		"windswept_hills",
		"windswept_forest",
		"windswept_gravelly_hills",
		"gravel_desert",
		"windswept_spires",
		"yellowstone"
	);

	private static final ResourceKey<Biome>[] COLD_PROMOTIONS = keys(
		Biomes.WINDSWEPT_GRAVELLY_HILLS,
		WINDSWEPT_SPIRES,
		GRAVEL_DESERT,
		YELLOWSTONE
	);

	private static final Set<String> COLD_PROMOTE_FROM = Set.of(
		"rocky_mountains",
		"steppe",
		"alpine_highlands",
		"granite_cliffs",
		"white_cliffs",
		"forested_highlands",
		"highlands",
		"haze_mountain"
	);

	@FunctionalInterface
	public interface BiomeFinder {
		Holder<Biome> find(ResourceKey<Biome> key);
	}

	@FunctionalInterface
	public interface ClimatePicker {
		Holder<Biome> pick(Climate.TargetPoint target);
	}

	public static Holder<Biome> apply(
		Holder<Biome> biome,
		int blockX,
		int blockZ,
		Climate.TargetPoint forced,
		ClimatePicker picker,
		BiomeFinder finder
	) {
		if (LandBiomes.isWaterOrCoast(biome)) {
			return biome;
		}

		String path = pathOf(biome);
		ClimateZone zone = ClimateZone.at(blockX, blockZ);

		if (ALPHA_PATHS.contains(path)) {
			return replaceAlpha(path, forced, picker, finder);
		}

		if (zone == ClimateZone.FREEZING && FREEZING_TO_COLD.contains(path)) {
			return firstFound(finder, biome, SNOWY_CHERRY_GROVE, Biomes.SNOWY_PLAINS, Biomes.GROVE);
		}

		if (zone == ClimateZone.COLD
			&& COLD_PROMOTE_FROM.contains(path)
			&& hash01(blockX, blockZ, 77123) < 0.40) {
			ResourceKey<Biome> promo = COLD_PROMOTIONS[hashIndex(blockX, blockZ, 88231, COLD_PROMOTIONS.length)];
			Holder<Biome> found = finder.find(promo);
			if (found != null) {
				return found;
			}
		}

		if (zone == ClimateZone.WARM) {
			if (path.equals("mushroom_fields") || path.equals("hot_shrubland")) {
				return firstFound(finder, biome, Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS);
			}
			if (path.equals("cherry_grove")) {
				ResourceKey<Biome> sakura = hash01(blockX, blockZ, 33911) < 0.5
					? SAKURA_GROVE
					: SAKURA_VALLEY;
				Holder<Biome> found = finder.find(sakura);
				if (found != null) {
					return found;
				}
			}
		}

		if (isSakura(path) && zone != ClimateZone.TEMPERATE && zone != ClimateZone.WARM) {
			return firstFound(finder, biome, Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.PLAINS);
		}

		if (zone == ClimateZone.FREEZING
			&& (path.equals("snowy_plains") || path.equals("grove") || path.equals("snowy_taiga"))
			&& hash01(blockX, blockZ, 19283) < 0.12) {
			Holder<Biome> cherry = finder.find(SNOWY_CHERRY_GROVE);
			if (cherry != null) {
				return cherry;
			}
		}

		return biome;
	}

	public static boolean isAlphaIsland(Holder<Biome> biome) {
		return ALPHA_PATHS.contains(pathOf(biome));
	}

	private static Holder<Biome> replaceAlpha(
		String path,
		Climate.TargetPoint forced,
		ClimatePicker picker,
		BiomeFinder finder
	) {
		float erosion = Math.max(Climate.unquantizeCoord(forced.erosion()), 0.25f);
		Climate.TargetPoint retarget = new Climate.TargetPoint(
			forced.temperature(),
			forced.humidity(),
			forced.continentalness(),
			Climate.quantizeCoord(erosion),
			forced.depth(),
			forced.weirdness()
		);
		Holder<Biome> neighbor = picker.pick(retarget);
		if (!isAlphaIsland(neighbor) && !LandBiomes.isWaterOrCoast(neighbor)) {
			return neighbor;
		}
		if (path.equals("alpha_islands_winter")) {
			return firstFound(finder, neighbor, SNOWY_CHERRY_GROVE, Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA);
		}
		return firstFound(finder, neighbor, Biomes.FOREST, Biomes.PLAINS, Biomes.BIRCH_FOREST);
	}

	@SafeVarargs
	private static Holder<Biome> firstFound(BiomeFinder finder, Holder<Biome> fallback, ResourceKey<Biome>... keys) {
		for (ResourceKey<Biome> key : keys) {
			Holder<Biome> found = finder.find(key);
			if (found != null) {
				return found;
			}
		}
		return fallback;
	}

	private static boolean isSakura(String path) {
		return path.equals("sakura_grove") || path.equals("sakura_valley");
	}

	private static String pathOf(Holder<Biome> biome) {
		return biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
	}

	private static int hashIndex(int x, int z, int seed, int mod) {
		int i = (int) (hash01(x, z, seed) * mod);
		return Math.floorMod(i, mod);
	}

	private static double hash01(int x, int z, int seed) {
		int n = x * 374761393 + z * 668265263 + seed * 1274126177;
		n = (n ^ (n >> 13)) * 1274126177;
		n = n ^ (n >> 16);
		return (n & 0x7fffffff) / (double) Integer.MAX_VALUE;
	}

	private static ResourceKey<Biome> terralith(String path) {
		return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("terralith", path));
	}

	@SafeVarargs
	private static ResourceKey<Biome>[] keys(ResourceKey<Biome>... keys) {
		return keys;
	}
}
