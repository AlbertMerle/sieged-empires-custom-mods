package com.tertonbiome;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * Post-pick biome replace only — does not change Tectonic density or climate
 * parameters. Cold mountain biomes ({@code frozen_peaks}, {@code jagged_peaks},
 * {@code snowy_slopes}) stay north of {@code coldMountainCutoffZ} (default 0).
 * South of that line they become {@code windswept_hills}, except in the Hot
 * band where they become {@code stony_peaks}. Cutoffs use the same wobbled Z
 * and blend width as latitude zone borders.
 */
public final class ColdMountainOverride {
	private ColdMountainOverride() {}

	public static boolean isColdMountain(Holder<Biome> biome) {
		return biome.is(Biomes.FROZEN_PEAKS)
			|| biome.is(Biomes.JAGGED_PEAKS)
			|| biome.is(Biomes.SNOWY_SLOPES);
	}

	/**
	 * How strongly to replace cold mountains at (X, Z): 0 = keep (north),
	 * 1 = always replace (south). Inside {@code boundaryBlendWidth} around the
	 * cutoff, returns a smoothstep so callers can dither a mixed strip.
	 */
	public static double replaceStrength(int blockX, int blockZ) {
		TerratonicbiomesConfig c = TerratonicbiomesConfig.get();
		return southBlend(LatitudeTemperature.effectiveZ(blockX, blockZ), c.coldMountainCutoffZ, c.boundaryBlendWidth);
	}

	/**
	 * How strongly the Hot-band replacement applies: 0 = windswept hills,
	 * 1 = stony peaks. Uses Warm|Hot cutoff with the same blend width.
	 */
	public static double hotStrength(int blockX, int blockZ) {
		TerratonicbiomesConfig c = TerratonicbiomesConfig.get();
		return southBlend(LatitudeTemperature.effectiveZ(blockX, blockZ), c.zFarSouth(), c.boundaryBlendWidth);
	}

	public static boolean shouldReplace(int blockX, int blockZ) {
		double strength = replaceStrength(blockX, blockZ);
		if (strength <= 0.0) {
			return false;
		}
		if (strength >= 1.0) {
			return true;
		}
		return hash01(blockX, blockZ, 44127) < strength;
	}

	/** Target biome when replacing a cold mountain at (X, Z). */
	public static ResourceKey<Biome> replacementKey(int blockX, int blockZ) {
		double hot = hotStrength(blockX, blockZ);
		if (hot <= 0.0) {
			return Biomes.WINDSWEPT_HILLS;
		}
		if (hot >= 1.0) {
			return Biomes.STONY_PEAKS;
		}
		return hash01(blockX, blockZ, 91837) < hot ? Biomes.STONY_PEAKS : Biomes.WINDSWEPT_HILLS;
	}

	/**
	 * 0 north of {@code cutoff − halfBlend}, 1 south of {@code cutoff + halfBlend},
	 * smoothstep in between (same pattern as latitude display-temp blend).
	 */
	private static double southBlend(double effectiveZ, int cutoff, int blendWidth) {
		int blend = Math.max(0, blendWidth);
		if (blend <= 0) {
			return effectiveZ >= cutoff ? 1.0 : 0.0;
		}
		double half = blend * 0.5;
		double d = effectiveZ - cutoff;
		if (d <= -half) {
			return 0.0;
		}
		if (d >= half) {
			return 1.0;
		}
		return smoothstep((d + half) / blend);
	}

	private static double smoothstep(double t) {
		if (t <= 0.0) return 0.0;
		if (t >= 1.0) return 1.0;
		return t * t * (3.0 - 2.0 * t);
	}

	private static double hash01(int x, int z, int seed) {
		int n = x * 374761393 + z * 668265263 + seed * 1274126177;
		n = (n ^ (n >> 13)) * 1274126177;
		n = n ^ (n >> 16);
		return (n & 0x7fffffff) / (double) Integer.MAX_VALUE;
	}
}
