package com.tertonbiome;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * Warm/wet swamp biomes belong in Warm + Hot only. North of the Temperate|Warm
 * cutoff (Temperate, Cold, Freezing) they are replaced. Does not touch
 * {@code terralith:ice_marsh} (cold swamp, belongs north).
 */
public final class SwampBan {
	private SwampBan() {}

	/**
	 * Vanilla swamp / mangrove and Terralith orchid swamp. Not ice_marsh.
	 */
	public static boolean isWarmSwamp(Holder<Biome> biome) {
		if (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) {
			return true;
		}
		return biome.unwrapKey().map(key -> {
			String id = key.identifier().toString();
			return id.equals("terralith:orchid_swamp");
		}).orElse(false);
	}

	/**
	 * How strongly to ban swamps at (X, Z): 1 = always replace (Temperate and
	 * north), 0 = allow (Warm and south). Blend strip uses the same width as
	 * zone borders.
	 */
	public static double banStrength(int blockX, int blockZ) {
		TerratonicbiomesConfig c = TerratonicbiomesConfig.get();
		// southBlend is 0 north of cutoff, 1 south — invert for a northern ban.
		return 1.0 - southBlend(
			LatitudeTemperature.effectiveZ(blockX, blockZ),
			c.zSouth(),
			c.boundaryBlendWidth
		);
	}

	public static boolean shouldReplace(int blockX, int blockZ) {
		double strength = banStrength(blockX, blockZ);
		if (strength <= 0.0) {
			return false;
		}
		if (strength >= 1.0) {
			return true;
		}
		return hash01(blockX, blockZ, 55291) < strength;
	}

	/**
	 * Inland fallback when a humidity re-query still returns a warm swamp.
	 * Meadow north of Temperate|Warm mid-band feel; plains south of that.
	 */
	public static ResourceKey<Biome> fallbackKey(int blockX, int blockZ) {
		TerratonicbiomesConfig c = TerratonicbiomesConfig.get();
		if (LatitudeTemperature.effectiveZ(blockX, blockZ) < c.plainsMeadowSwapZ()) {
			return Biomes.MEADOW;
		}
		return Biomes.PLAINS;
	}

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
