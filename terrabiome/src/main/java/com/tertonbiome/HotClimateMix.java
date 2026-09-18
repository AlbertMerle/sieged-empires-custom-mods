package com.tertonbiome;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

/**
 * In the Hot latitude band, Terralith's parameter list is ~80% arid climate
 * volume vs ~20% wet tropical. Equal-area spatial patches split the band into
 * wet and dry land ({@link TerratonicbiomesConfig#hotWetLandFraction}, default
 * 0.5), humidity is stretched toward the matching extreme, then mismatches
 * are corrected by the zone enforcer.
 */
public final class HotClimateMix {
	private HotClimateMix() {}

	private static final TagKey<Biome> C_IS_DESERT = TagKey.create(
		Registries.BIOME,
		Identifier.fromNamespaceAndPath("c", "is_desert")
	);
	private static final TagKey<Biome> C_IS_JUNGLE = TagKey.create(
		Registries.BIOME,
		Identifier.fromNamespaceAndPath("c", "is_jungle")
	);

	/** True when wobbled Z is in the Hot band (south of Warm|Hot cutoff). */
	public static boolean inHotZone(int blockX, int blockZ) {
		return LatitudeTemperature.effectiveZ(blockX, blockZ) >= TerratonicbiomesConfig.get().zFarSouth();
	}

	/**
	 * Large-scale wet vs dry regions in Hot. Each noise cell is assigned wet or
	 * dry with probability {@link TerratonicbiomesConfig#hotWetLandFraction}
	 * (default exactly ½ of cells), then bilinear-smoothed so patches are
	 * multi-kilometer regions rather than humidity speckles. Threshold at 0.5
	 * keeps land area very close to the configured fraction.
	 */
	public static boolean wantWet(int blockX, int blockZ) {
		TerratonicbiomesConfig c = TerratonicbiomesConfig.get();
		int scale = Math.max(64, c.hotMixPatchScale);
		float fraction = clamp(c.hotWetLandFraction, 0f, 1f);
		double x = blockX / (double) scale;
		double z = blockZ / (double) scale;
		int x0 = (int) Math.floor(x);
		int z0 = (int) Math.floor(z);
		double fx = fade(x - x0);
		double fz = fade(z - z0);
		double v00 = cellWetBit(x0, z0, c.hotMixPatchSeed, fraction);
		double v10 = cellWetBit(x0 + 1, z0, c.hotMixPatchSeed, fraction);
		double v01 = cellWetBit(x0, z0 + 1, c.hotMixPatchSeed, fraction);
		double v11 = cellWetBit(x0 + 1, z0 + 1, c.hotMixPatchSeed, fraction);
		double ix0 = v00 + (v10 - v00) * fx;
		double ix1 = v01 + (v11 - v01) * fx;
		double v = ix0 + (ix1 - ix0) * fz;
		return v >= 0.5;
	}

	/** Per-cell wet (1) / dry (0) with probability {@code fraction}. */
	private static double cellWetBit(int cellX, int cellZ, int seed, float fraction) {
		return hash01(cellX, cellZ, seed) < fraction ? 1.0 : 0.0;
	}

	/**
	 * Stretch humidity into wet or dry Terralith slots. Original humidity still
	 * picks variety within the patch (jungle vs bamboo vs swamp, desert vs badlands vs oasis).
	 */
	public static float remapHumidity(float originalHumidity, boolean wantWet) {
		TerratonicbiomesConfig c = TerratonicbiomesConfig.get();
		float h = clamp(originalHumidity, -1f, 1f);
		float t = (h + 1f) * 0.5f;
		if (wantWet) {
			float min = c.hotJungleHumidityMin;
			return min + t * (1f - min);
		}
		float max = c.hotDesertHumidityMax;
		return -1f + t * (max + 1f);
	}

	public static boolean isWetTropicalFamily(Holder<Biome> biome) {
		if (biome.is(BiomeTags.IS_JUNGLE) || biome.is(C_IS_JUNGLE)) {
			return true;
		}
		if (SwampBan.isWarmSwamp(biome)) {
			return true;
		}
		return biome.unwrapKey().map(key -> {
			String path = key.identifier().getPath();
			return path.contains("jungle")
				|| path.contains("bamboo")
				|| path.contains("rainforest")
				|| path.contains("swamp")
				|| path.contains("amethyst");
		}).orElse(false);
	}

	public static boolean isAridFamily(Holder<Biome> biome) {
		if (biome.is(BiomeTags.IS_BADLANDS)
			|| biome.is(BiomeTags.IS_SAVANNA)
			|| biome.is(C_IS_DESERT)) {
			return true;
		}
		return biome.unwrapKey().map(key -> {
			String path = key.identifier().getPath();
			return path.contains("desert")
				|| path.contains("badlands")
				|| path.contains("savanna")
				|| path.contains("shrubland")
				|| path.contains("arid")
				|| path.contains("brushland")
				|| path.contains("wasteland")
				|| path.contains("ancient_sands")
				|| path.contains("sandstone")
				|| path.contains("bryce")
				|| path.contains("mesa")
				|| path.contains("oasis")
				|| path.equals("white_mesa")
				|| path.contains("desert_canyon");
		}).orElse(false);
	}

	/**
	 * If the Hot-zone pick is the wrong moisture class (arid in a wet patch or wet in a
	 * dry patch), re-query with forced humidity. Leaves oceans/rivers/coasts alone.
	 */
	public static Holder<Biome> correctMismatch(
		Holder<Biome> biome,
		Climate.TargetPoint forced,
		boolean wantWet,
		BiomePicker picker
	) {
		if (LandBiomes.isWaterOrCoast(biome)) {
			return biome;
		}
		if (ZoneBiomeCatalog.matchesHotMoisture(biome, wantWet)) {
			return biome;
		}
		float[] humidities = wantWet
			? new float[] {0.65f, 0.85f, 0.95f}
			: new float[] {-0.55f, -0.75f, -0.9f};
		return tryHumidity(
			picker,
			forced,
			humidities,
			candidate -> ZoneBiomeCatalog.matchesHotMoisture(candidate, wantWet),
			biome
		);
	}

	private static Holder<Biome> tryHumidity(
		BiomePicker picker,
		Climate.TargetPoint base,
		float[] humidities,
		java.util.function.Predicate<Holder<Biome>> ok,
		Holder<Biome> fallback
	) {
		for (float h : humidities) {
			Holder<Biome> pick = picker.pick(withHumidity(base, h));
			if (ok.test(pick)) {
				return pick;
			}
		}
		return fallback;
	}

	private static Climate.TargetPoint withHumidity(Climate.TargetPoint base, float humidity) {
		return new Climate.TargetPoint(
			base.temperature(),
			Climate.quantizeCoord(clamp(humidity, -1f, 1f)),
			base.continentalness(),
			base.erosion(),
			base.depth(),
			base.weirdness()
		);
	}

	private static float clamp(float v, float lo, float hi) {
		return Math.max(lo, Math.min(hi, v));
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

	@FunctionalInterface
	public interface BiomePicker {
		Holder<Biome> pick(Climate.TargetPoint target);
	}
}
