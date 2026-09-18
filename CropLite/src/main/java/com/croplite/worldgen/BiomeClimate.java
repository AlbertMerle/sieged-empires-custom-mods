package com.croplite.worldgen;

import net.minecraft.world.level.biome.Biome;

/**
 * Reads biome base temperature and downfall (humidity) for climate-based spawn categories.
 *
 * <p>Downfall is on the private {@code Biome.ClimateSettings} record in MC 26.2; exposed via
 * {@code croplite.accesswidener}.
 */
public final class BiomeClimate {
	private BiomeClimate() {
	}

	/** Biome JSON {@code temperature} / {@link Biome#getBaseTemperature()}. */
	public static float temperature(Biome biome) {
		return biome.getBaseTemperature();
	}

	/**
	 * Biome JSON {@code downfall} — humidity / rainfall amount used for arid vs wet pools.
	 * Negative values (some Terralith biomes) are treated as fully arid.
	 */
	public static float humidity(Biome biome) {
		return biome.climateSettings.downfall();
	}

	/** Same as {@link #humidity(Biome)} but clamps to {@code ≥ 0} for threshold checks. */
	public static float humidityNonNegative(Biome biome) {
		return Math.max(0.0F, humidity(biome));
	}
}
