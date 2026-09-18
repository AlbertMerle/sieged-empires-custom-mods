package com.croplite.crop;

import com.croplite.block.ModBlocks;
import com.croplite.compat.SeasonGrowth;
import com.croplite.config.CropLiteConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Applies CropLite soil / water / global / seasonal / desert-irrigation multipliers to crop growth.
 *
 * <p>{@code crop_growth_rate} stacks with soil rates for {@code CropBlock} plants, and also
 * gates natural ticks for stems, sugar cane, cocoa, berries, and saplings via
 * {@link #allowRandomGrowthTick}.
 */
public final class CropGrowth {
	/** Matches {@code FarmlandBlock.isNearWater}: ±4 on X/Z, Y 0–1 above the soil. */
	private static final int WATER_RANGE = 4;

	private CropGrowth() {
	}

	/**
	 * Scales a vanilla {@code CropBlock.getGrowthSpeed} result by config soil rate,
	 * optional nearby-water bonus, {@code crop_growth_rate}, Serene Seasons multipliers,
	 * and desert irrigation penalty for non-hardy tropical crops.
	 * Rot is handled separately (random-tick cancel) so this never returns 0 from climate alone.
	 */
	public static float modify(float vanillaSpeed, BlockGetter level, BlockPos cropPos) {
		CropLiteConfig config = CropLiteConfig.get();
		BlockPos soilPos = cropPos.below();
		BlockState soil = level.getBlockState(soilPos);
		Block crop = level.getBlockState(cropPos).getBlock();

		float soilRate = config.soilGrowthPercent(soil) / 100.0F;
		if (isNearWater(level, soilPos)) {
			soilRate += config.waterBonusPercent / 100.0F;
		}

		float global = config.cropGrowthRate / 100.0F;
		float season = 1.0F;
		if (level instanceof LevelReader reader) {
			season = SeasonGrowth.multiplier(reader, cropPos);
			if (CropClimate.usesDesertIrrigationPenalty(crop, reader, cropPos)) {
				season *= config.desertIrrigatedGrowthPercent / 100.0F;
			}
		}
		float speed = vanillaSpeed * soilRate * global * season * cropMultiplier(crop);

		return Math.max(0.01F, speed);
	}

	/** Per-crop speed multipliers (garlic grows at 30% of other plants). */
	private static float cropMultiplier(Block crop) {
		if (crop == ModBlocks.GARLIC_CROP) {
			return 0.3F;
		}
		return 1.0F;
	}

	/**
	 * Probabilistic growth gate for blocks without a growth-speed hook
	 * (stems, sugar cane, cocoa, sweet berries, saplings).
	 * Applies {@code crop_growth_rate}, Serene Seasons multipliers, and desert irrigation.
	 * Returns true when this random tick should proceed.
	 */
	public static boolean allowRandomGrowthTick(LevelReader level, BlockPos pos, RandomSource random) {
		Block crop = level.getBlockState(pos).getBlock();
		float chance = CropLiteConfig.get().cropGrowthRate / 100.0F;
		chance *= SeasonGrowth.multiplier(level, pos);
		if (CropClimate.usesDesertIrrigationPenalty(crop, level, pos)) {
			chance *= CropLiteConfig.get().desertIrrigatedGrowthPercent / 100.0F;
		}
		if (chance >= 1.0F) {
			return true;
		}
		if (chance <= 0.0F) {
			return false;
		}
		return random.nextFloat() < chance;
	}

	/**
	 * Bonemeal success chance scaled by {@code crop_growth_rate}.
	 * Vanilla still consumes the item when this returns false, so 50% ≈ twice as much
	 * bonemeal for the same growth. Rates ≥ 100 always succeed; ≤ 0 never do.
	 */
	public static boolean allowBonemealSuccess(RandomSource random) {
		int rate = CropLiteConfig.get().cropGrowthRate;
		if (rate >= 100) {
			return true;
		}
		if (rate <= 0) {
			return false;
		}
		return random.nextFloat() < rate / 100.0F;
	}

	/** Same search volume as vanilla farmland hydration. */
	public static boolean isNearWater(BlockGetter level, BlockPos soilPos) {
		for (BlockPos pos : BlockPos.betweenClosed(
				soilPos.offset(-WATER_RANGE, 0, -WATER_RANGE),
				soilPos.offset(WATER_RANGE, 1, WATER_RANGE))) {
			if (level.getFluidState(pos).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	/** Water fluid within {@code range} blocks of {@code center} (cube, inclusive). */
	public static boolean hasWaterWithin(BlockGetter level, BlockPos center, int range) {
		for (BlockPos pos : BlockPos.betweenClosed(
				center.offset(-range, -range, -range),
				center.offset(range, range, range))) {
			if (level.getFluidState(pos).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}
}
