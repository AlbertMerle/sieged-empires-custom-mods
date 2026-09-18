package com.tertonbiome;

/**
 * Maps block Z → multi-noise temperature (−1…1) using five equal latitude bands.
 * Within each band, the sampler's original temperature is remapped into that
 * band's wiki-display range so humidity can still mix jungles/deserts, etc.
 *
 * Band edges are not straight: a 2D noise offset (±{@code boundaryNoiseAmplitude})
 * meanders the cutoff, and a short lerp of adjacent display ranges
 * ({@code boundaryBlendWidth}) lets Terralith fill a mixed strip. Humidity,
 * continentalness, erosion, weirdness, and Tectonic density are not changed.
 *
 * {@code noise = clamp((display − 0.65) / 1.15, −1, 1)}
 *
 * Five zones span {@code world-size} (N–S diameter). Each is a hard 20% of that value.
 * Default world-size 30000 → 6000-block zones (south = +Z):
 * <ul>
 *   <li>Freezing — Z ≤ −9000: display ≤ 0.3</li>
 *   <li>Cold — −9000…−3000: 0.25–0.70</li>
 *   <li>Temperate — −3000…+3000: 0.50–0.75</li>
 *   <li>Warm — +3000…+9000: 0.75–0.95 (swamps / warm mix)</li>
 *   <li>Hot — Z ≥ +9000: 0.80–2.0 (jungle + desert mix; Hot humidity remapped separately)</li>
 * </ul>
 * Beyond ±world-size/2, Freezing continues north and Hot continues south.
 */
public final class LatitudeTemperature {
	private LatitudeTemperature() {}

	/** Wiki display temperature range [min, max] for this Z (no X wobble). */
	public static double[] displayRangeFromZ(int blockZ) {
		return displayRangeFromXZ(0, blockZ, false);
	}

	/** Wiki display temperature range [min, max] at (X, Z), with edge wobble + blend. */
	public static double[] displayRangeFromXZ(int blockX, int blockZ) {
		return displayRangeFromXZ(blockX, blockZ, true);
	}

	private static double[] displayRangeFromXZ(int blockX, int blockZ, boolean varyEdges) {
		TerratonicbiomesConfig c = TerratonicbiomesConfig.get();
		double z = varyEdges ? effectiveZ(blockX, blockZ) : blockZ;
		return blendedRange(z, c);
	}

	/**
	 * Z used for band lookup and plains↔meadow swap: block Z plus a slow 2D
	 * noise of ±{@code boundaryNoiseAmplitude} so cutoffs meander instead of
	 * drawing a straight east–west line.
	 */
	public static double effectiveZ(int blockX, int blockZ) {
		TerratonicbiomesConfig c = TerratonicbiomesConfig.get();
		int amplitude = c.boundaryNoiseAmplitude;
		int scale = c.boundaryNoiseScale;
		if (amplitude == 0 || scale <= 0) {
			return blockZ;
		}
		double n = fbm(blockX / (double) scale, blockZ / (double) scale, c.boundaryNoiseSeed);
		return blockZ + n * amplitude;
	}

	/** Midpoint display temp (for logging / simple queries). */
	public static double displayFromZ(int blockZ) {
		double[] r = displayRangeFromZ(blockZ);
		return (r[0] + r[1]) * 0.5;
	}

	/**
	 * Multi-noise temperature from block Z, remapping {@code originalNoiseTemp}
	 * (−1…1 from the climate sampler) into the band's display range.
	 * No X wobble — prefer {@link #noiseFromXZ(int, int, float)}.
	 */
	public static float noiseFromZ(int blockZ, float originalNoiseTemp) {
		return remapToRange(displayRangeFromZ(blockZ), originalNoiseTemp);
	}

	/**
	 * Multi-noise temperature at (X, Z), remapping {@code originalNoiseTemp}
	 * into the (wobbled / blended) band display range.
	 */
	public static float noiseFromXZ(int blockX, int blockZ, float originalNoiseTemp) {
		return noiseFromEffectiveZ(effectiveZ(blockX, blockZ), originalNoiseTemp);
	}

	/**
	 * Allocation-free temperature remap for callers that already computed the
	 * wobbled Z for this sample.
	 */
	public static float noiseFromEffectiveZ(double z, float originalNoiseTemp) {
		TerratonicbiomesConfig c = TerratonicbiomesConfig.get();
		double min;
		double max;
		int blend = Math.max(0, c.boundaryBlendWidth);
		double half = blend * 0.5;
		int c0 = c.zFarNorth();
		int c1 = c.zNorth();
		int c2 = c.zSouth();
		int c3 = c.zFarSouth();

		if (blend > 0 && Math.abs(z - c0) < half) {
			double t = smoothstep((z - c0 + half) / blend);
			min = lerp(c.farNorthDisplayMin, c.northDisplayMin, t);
			max = lerp(c.farNorthDisplayMax, c.northDisplayMax, t);
		} else if (blend > 0 && Math.abs(z - c1) < half) {
			double t = smoothstep((z - c1 + half) / blend);
			min = lerp(c.northDisplayMin, c.temperateDisplayMin, t);
			max = lerp(c.northDisplayMax, c.temperateDisplayMax, t);
		} else if (blend > 0 && Math.abs(z - c2) < half) {
			double t = smoothstep((z - c2 + half) / blend);
			min = lerp(c.temperateDisplayMin, c.subtropicDisplayMin, t);
			max = lerp(c.temperateDisplayMax, c.subtropicDisplayMax, t);
		} else if (blend > 0 && Math.abs(z - c3) < half) {
			double t = smoothstep((z - c3 + half) / blend);
			min = lerp(c.subtropicDisplayMin, c.tropicalDisplayMin, t);
			max = lerp(c.subtropicDisplayMax, c.tropicalDisplayMax, t);
		} else if (z <= c0) {
			min = c.farNorthDisplayMin;
			max = c.farNorthDisplayMax;
		} else if (z < c1) {
			min = c.northDisplayMin;
			max = c.northDisplayMax;
		} else if (z < c2) {
			min = c.temperateDisplayMin;
			max = c.temperateDisplayMax;
		} else if (z < c3) {
			min = c.subtropicDisplayMin;
			max = c.subtropicDisplayMax;
		} else {
			min = c.tropicalDisplayMin;
			max = c.tropicalDisplayMax;
		}
		return remapToRange(min, max, originalNoiseTemp);
	}

	/** @deprecated Prefer {@link #noiseFromXZ(int, int, float)} so bands keep variety. */
	@Deprecated
	public static float noiseFromZ(int blockZ) {
		return noiseFromZ(blockZ, 0.0f);
	}

	public static float displayToNoise(double display) {
		double noise = (display - 0.65) / 1.15;
		if (noise < -1.0) return -1.0f;
		if (noise > 1.0) return 1.0f;
		return (float) noise;
	}

	private static float remapToRange(double[] range, float originalNoiseTemp) {
		return remapToRange(range[0], range[1], originalNoiseTemp);
	}

	private static float remapToRange(double min, double max, float originalNoiseTemp) {
		double t = (originalNoiseTemp + 1.0) * 0.5;
		if (t < 0.0) t = 0.0;
		if (t > 1.0) t = 1.0;
		double display = min + t * (max - min);
		return displayToNoise(display);
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	private static double[] blendedRange(double z, TerratonicbiomesConfig c) {
		int[] cuts = c.zoneCutoffs();
		double[][] ranges = {
			{c.farNorthDisplayMin, c.farNorthDisplayMax},
			{c.northDisplayMin, c.northDisplayMax},
			{c.temperateDisplayMin, c.temperateDisplayMax},
			{c.subtropicDisplayMin, c.subtropicDisplayMax},
			{c.tropicalDisplayMin, c.tropicalDisplayMax}
		};
		int blend = Math.max(0, c.boundaryBlendWidth);
		if (blend > 0) {
			double half = blend * 0.5;
			for (int i = 0; i < cuts.length; i++) {
				double d = z - cuts[i];
				if (Math.abs(d) < half) {
					double t = smoothstep((d + half) / blend);
					return lerpRange(ranges[i], ranges[i + 1], t);
				}
			}
		}
		if (z <= cuts[0]) return ranges[0];
		if (z < cuts[1]) return ranges[1];
		if (z < cuts[2]) return ranges[2];
		if (z < cuts[3]) return ranges[3];
		return ranges[4];
	}

	private static double[] lerpRange(double[] a, double[] b, double t) {
		return new double[] {
			a[0] + (b[0] - a[0]) * t,
			a[1] + (b[1] - a[1]) * t
		};
	}

	private static double smoothstep(double t) {
		if (t <= 0.0) return 0.0;
		if (t >= 1.0) return 1.0;
		return t * t * (3.0 - 2.0 * t);
	}

	/** Two-octave value noise in roughly −1…1. */
	private static double fbm(double x, double z, int seed) {
		double n = valueNoise(x, z, seed);
		n += 0.5 * valueNoise(x * 2.03, z * 2.03, seed + 19);
		return n / 1.5;
	}

	private static double valueNoise(double x, double z, int seed) {
		int x0 = (int) Math.floor(x);
		int z0 = (int) Math.floor(z);
		double fx = fade(x - x0);
		double fz = fade(z - z0);
		double n00 = hash01(x0, z0, seed);
		double n10 = hash01(x0 + 1, z0, seed);
		double n01 = hash01(x0, z0 + 1, seed);
		double n11 = hash01(x0 + 1, z0 + 1, seed);
		double ix0 = n00 + (n10 - n00) * fx;
		double ix1 = n01 + (n11 - n01) * fx;
		double v = ix0 + (ix1 - ix0) * fz;
		return v * 2.0 - 1.0;
	}

	private static double fade(double t) {
		return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
	}

	private static double hash01(int x, int z, int seed) {
		int n = x * 374761393 + z * 668265263 + seed * 1274126177;
		n = (n ^ (n >> 13)) * 1274126177;
		n = n ^ (n >> 16);
		return (n & 0x7fffffff) / (double) Integer.MAX_VALUE;
	}
}
