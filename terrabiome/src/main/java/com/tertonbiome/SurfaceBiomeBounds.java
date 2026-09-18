package com.tertonbiome;

import net.minecraft.world.level.biome.Climate;

/**
 * Surface column rules: below {@link #minY()} only vanilla cave biomes may
 * appear (rates in {@code cave-biomes}; Terralith caves banned). At and above
 * {@link #minY()} only {@link ZoneBiomeCatalog} surface biomes — no caves.
 */
public final class SurfaceBiomeBounds {
	private SurfaceBiomeBounds() {}

	public static final int DEFAULT_MIN_Y = 45;
	public static final int DEFAULT_MAX_Y = 320;

	/**
	 * Multi-noise depth floor for surface biome picks. Cave slots sit below this;
	 * clamping depth here steers Terralith toward surface suggestions without
	 * changing Tectonic terrain density.
	 */
	public static final float SURFACE_DEPTH_FLOOR = -0.11f;

	public static boolean isEnabled() {
		return TerratonicbiomesConfig.get().surfaceBiomeBounds;
	}

	public static int minY() {
		return TerratonicbiomesConfig.get().surfaceBiomeMinY;
	}

	public static int maxY() {
		return TerratonicbiomesConfig.get().surfaceBiomeMaxY;
	}

	/** True when terrabiome latitude + whitelist placement applies. */
	public static boolean useSurfacePlacement(int blockY) {
		if (!isEnabled()) {
			return true;
		}
		return blockY >= minY();
	}

	/**
	 * True when the column is below the surface min Y (cave filter applies;
	 * latitude whitelist does not).
	 */
	public static boolean isUnderground(int blockY) {
		return isEnabled() && blockY < minY();
	}

	public static boolean contains(int blockY) {
		if (!isEnabled()) {
			return true;
		}
		return blockY >= minY() && blockY <= maxY();
	}

	/**
	 * Bias climate depth toward the surface slot so multi-noise does not suggest
	 * cave biomes for columns at {@code blockY >= minY()}.
	 */
	public static Climate.TargetPoint biasToSurface(Climate.TargetPoint point, int blockY) {
		if (!isEnabled() || blockY < minY()) {
			return point;
		}
		float depth = Climate.unquantizeCoord(point.depth());
		if (depth >= SURFACE_DEPTH_FLOOR) {
			return point;
		}
		return new Climate.TargetPoint(
			point.temperature(),
			point.humidity(),
			point.continentalness(),
			point.erosion(),
			Climate.quantizeCoord(SURFACE_DEPTH_FLOOR),
			point.weirdness()
		);
	}
}
