package com.tertonbiome;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;

import java.util.List;
import java.util.Map;

/**
 * Underground (below {@link SurfaceBiomeBounds#minY()}) cave rules:
 * <ul>
 *   <li>No Terralith / modded cave biomes — only vanilla caves may appear.</li>
 *   <li>Each vanilla cave’s keep chance is {@code cave-biomes} rate
 *       ({@code 1.0} = vanilla, {@code 0.1} ≈ 1/10 of vanilla picks).</li>
 * </ul>
 * At and above {@link SurfaceBiomeBounds#minY()} caves are stripped elsewhere
 * ({@link ZoneBiomeEnforcer#enforceSurface}).
 */
public final class CaveBiomeRules {
	private CaveBiomeRules() {}

	@FunctionalInterface
	public interface NoisePicker {
		Holder<Biome> pick(Climate.TargetPoint target);
	}

	@FunctionalInterface
	public interface BiomeFinder {
		Holder<Biome> find(ResourceKey<Biome> key);
	}

	/** Vanilla cave biomes configurable via {@code cave-biomes}. */
	public static final List<ResourceKey<Biome>> VANILLA_CAVES = List.of(
		Biomes.LUSH_CAVES,
		Biomes.DRIPSTONE_CAVES,
		Biomes.DEEP_DARK,
		Biomes.SULFUR_CAVES
	);

	/**
	 * Filter a multi-noise pick for columns below the surface min Y.
	 * Non-cave biomes pass through. Terralith caves are always replaced.
	 * Vanilla caves are kept with probability = configured rate.
	 */
	public static Holder<Biome> applyBelowSurface(
		Holder<Biome> biome,
		int quartX,
		int quartY,
		int quartZ,
		Climate.TargetPoint sample,
		NoisePicker picker,
		BiomeFinder finder
	) {
		if (!CaveBiomeBan.isCaveBiome(biome)) {
			return biome;
		}
		int blockX = QuartPos.toBlock(quartX);
		int blockY = QuartPos.toBlock(quartY);
		int blockZ = QuartPos.toBlock(quartZ);

		if (!CaveBiomeBan.isVanillaCave(biome)) {
			return replaceWithNonCave(sample, picker, finder);
		}

		double rate = spawnRate(biome);
		if (rate <= 0.0) {
			return replaceWithNonCave(sample, picker, finder);
		}
		if (rate < 1.0) {
			double roll = hash01(blockX, blockY, blockZ, caveSeed(biome));
			if (roll >= rate) {
				return replaceWithNonCave(sample, picker, finder);
			}
		}
		return biome;
	}

	/**
	 * Spawn rate for a vanilla cave key. {@code 1.0} = keep every multi-noise pick
	 * (vanilla). Missing keys default to {@code 1.0}. Values above 1 clamp to 1
	 * (cannot inject extra caves beyond multi-noise).
	 */
	public static double spawnRate(Holder<Biome> biome) {
		return biome.unwrapKey().map(CaveBiomeRules::spawnRate).orElse(1.0);
	}

	public static double spawnRate(ResourceKey<Biome> key) {
		Map<String, Double> rates = TerratonicbiomesConfig.get().caveBiomes;
		if (rates == null || rates.isEmpty()) {
			return 1.0;
		}
		Identifier id = key.identifier();
		Double rate = rates.get(id.toString());
		if (rate == null) {
			rate = rates.get(id.getPath());
		}
		if (rate == null && "minecraft".equals(id.getNamespace())) {
			rate = rates.get("minecraft:" + id.getPath());
		}
		if (rate == null) {
			return 1.0;
		}
		if (rate.isNaN() || rate < 0.0) {
			return 0.0;
		}
		return Math.min(1.0, rate);
	}

	/** Bias depth toward the surface slot so multi-noise picks a non-cave neighbor. */
	public static Holder<Biome> replaceWithNonCave(
		Climate.TargetPoint sample,
		NoisePicker picker,
		BiomeFinder finder
	) {
		Climate.TargetPoint biased = new Climate.TargetPoint(
			sample.temperature(),
			sample.humidity(),
			sample.continentalness(),
			sample.erosion(),
			Climate.quantizeCoord(SurfaceBiomeBounds.SURFACE_DEPTH_FLOOR),
			sample.weirdness()
		);
		Holder<Biome> neighbor = picker.pick(biased);
		if (!CaveBiomeBan.isCaveBiome(neighbor)) {
			return neighbor;
		}
		for (ResourceKey<Biome> key : List.of(Biomes.PLAINS, Biomes.FOREST, Biomes.TAIGA, Biomes.DESERT)) {
			Holder<Biome> found = finder.find(key);
			if (found != null && !CaveBiomeBan.isCaveBiome(found)) {
				return found;
			}
		}
		return neighbor;
	}

	/** Resolve a biome holder from a multi-noise source’s possible set. */
	public static Holder<Biome> findInSource(MultiNoiseBiomeSource source, ResourceKey<Biome> key) {
		for (Holder<Biome> holder : source.possibleBiomes()) {
			if (holder.is(key)) {
				return holder;
			}
		}
		return null;
	}

	private static int caveSeed(Holder<Biome> biome) {
		return biome.unwrapKey()
			.map(key -> key.identifier().getPath().hashCode())
			.orElse(0xCAFE);
	}

	private static double hash01(int x, int y, int z, int seed) {
		int n = x * 374761393 + y * 668265263 + z * 1274126177 + seed * 1103515245;
		n = (n ^ (n >> 13)) * 1274126177;
		n = n ^ (n >> 16);
		return (n & 0x7fffffff) / (double) Integer.MAX_VALUE;
	}
}
