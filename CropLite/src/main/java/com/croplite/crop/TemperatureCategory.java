package com.croplite.crop;

import com.croplite.config.CropLiteConfig;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.biome.Biome;

/**
 * Climate types for crops. Growth uses overlapping temperature ranges
 * ({@link #allowsTemperature(float)}); exclusive banding via {@link #of(float)} is for loot.
 *
 * <p>Defaults: cold ≤ 0.5, temperate 0.4–0.8, tropical 0.75–1.9.
 */
public enum TemperatureCategory implements StringRepresentable {
	COLD("cold"),
	TEMPERATE("temperate"),
	TROPICAL("tropical");

	public static final StringRepresentable.EnumCodec<TemperatureCategory> CODEC =
			StringRepresentable.fromEnum(TemperatureCategory::values);

	private final String name;

	TemperatureCategory(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	/** Whether this climate's growth band includes the biome base temperature. */
	public boolean allowsTemperature(float baseTemperature) {
		CropLiteConfig config = CropLiteConfig.get();
		return switch (this) {
			case COLD -> baseTemperature <= config.coldCropMaxTemperature;
			case TEMPERATE -> baseTemperature >= config.temperateCropMinTemperature
					&& baseTemperature <= config.temperateCropMaxTemperature;
			case TROPICAL -> baseTemperature >= config.tropicalCropMinTemperature
					&& baseTemperature <= config.tropicalCropMaxTemperature;
		};
	}

	public static TemperatureCategory of(Biome biome) {
		return of(biome.getBaseTemperature());
	}

	/**
	 * Exclusive band for loot / coarse classification:
	 * cold ≤ cold crop max, tropical ≥ tropical crop min, else temperate.
	 */
	public static TemperatureCategory of(float baseTemperature) {
		CropLiteConfig config = CropLiteConfig.get();
		if (baseTemperature <= config.coldCropMaxTemperature) {
			return COLD;
		}
		if (baseTemperature >= config.tropicalCropMinTemperature) {
			return TROPICAL;
		}
		return TEMPERATE;
	}
}
