package com.croplite.compat;

import com.croplite.config.CropLiteConfig;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;

/**
 * Optional Serene Seasons growth multipliers. Safe when the mod is absent.
 *
 * <ul>
 *   <li>Winter (non-tropical season biomes): {@code winter_growth_percent} (default 30%)
 *   <li>Dry season (tropical season biomes): {@code dry_season_growth_percent} (default 70% = slowed 30%)
 * </ul>
 */
public final class SeasonGrowth {
	private static final String SERENE_SEASONS = "sereneseasons";

	private SeasonGrowth() {
	}

	public static float multiplier(LevelReader level, BlockPos pos) {
		if (!(level instanceof Level serverLevel)) {
			return 1.0F;
		}
		if (!FabricLoader.getInstance().isModLoaded(SERENE_SEASONS)) {
			return 1.0F;
		}
		return SeasonGrowthCompat.multiplier(serverLevel, pos);
	}

	/**
	 * Temperate (non-tropical-season) biomes during Serene Seasons winter.
	 * Used for seasonal rot on basil / cantelope. False when SS is absent.
	 */
	public static boolean isWinterInTemperate(LevelReader level, BlockPos pos) {
		if (!(level instanceof Level serverLevel)) {
			return false;
		}
		if (!FabricLoader.getInstance().isModLoaded(SERENE_SEASONS)) {
			return false;
		}
		return SeasonGrowthCompat.isWinterInTemperate(serverLevel, pos);
	}

	public static float winterPercent() {
		return CropLiteConfig.get().winterGrowthPercent / 100.0F;
	}

	public static float drySeasonPercent() {
		return CropLiteConfig.get().drySeasonGrowthPercent / 100.0F;
	}
}
