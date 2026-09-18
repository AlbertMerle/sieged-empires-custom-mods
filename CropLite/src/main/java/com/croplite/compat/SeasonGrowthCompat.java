package com.croplite.compat;

import com.croplite.crop.TemperatureCategory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import sereneseasons.api.season.ISeasonState;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

/**
 * Direct Serene Seasons API calls. Only loaded when the mod is present
 * (gated by {@link SeasonGrowth}).
 */
final class SeasonGrowthCompat {
	private SeasonGrowthCompat() {
	}

	static float multiplier(Level level, BlockPos pos) {
		ISeasonState state = SeasonHelper.getSeasonState(level);
		if (state == null) {
			return 1.0F;
		}

		Holder<Biome> biome = level.getBiome(pos);
		if (SeasonHelper.usesTropicalSeasons(biome)) {
			Season.TropicalSeason tropical = state.getTropicalSeason();
			if (tropical == Season.TropicalSeason.EARLY_DRY
					|| tropical == Season.TropicalSeason.MID_DRY
					|| tropical == Season.TropicalSeason.LATE_DRY) {
				return SeasonGrowth.drySeasonPercent();
			}
			return 1.0F;
		}

		if (state.getSeason() == Season.WINTER) {
			return SeasonGrowth.winterPercent();
		}
		return 1.0F;
	}

	/** Winter in a temperate-band biome that does not use tropical seasons. */
	static boolean isWinterInTemperate(Level level, BlockPos pos) {
		ISeasonState state = SeasonHelper.getSeasonState(level);
		if (state == null || state.getSeason() != Season.WINTER) {
			return false;
		}

		Holder<Biome> biome = level.getBiome(pos);
		if (SeasonHelper.usesTropicalSeasons(biome)) {
			return false;
		}

		float temperature = biome.value().getBaseTemperature();
		return TemperatureCategory.TEMPERATE.allowsTemperature(temperature);
	}
}
