package com.croplite.worldgen;

import com.croplite.block.FruitingCropBlock;
import com.croplite.block.ModBlocks;
import com.croplite.block.TallCropBlock;
import com.croplite.block.TallFruitingCropBlock;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Places a group of 2–6 wild crops on grass/dirt/coarse dirt/sand.
 * Crop type is chosen from the biome's allowed pool at a random growth stage.
 *
 * <p>Placement scans downward from the heightmap origin so canopy / snow / water
 * columns still find plantable ground. Runs after vegetal decoration and
 * replaces grass / fern / tall grass / flowers so patch rarity is honored.
 *
 * <p>Does <strong>not</strong> call {@link BlockState#canSurvive} during place:
 * chunk {@code FEATURES} run before light is calculated, and {@code CropBlock}
 * rejects anything under brightness 8 — which wiped every wild patch.
 */
public class WildCropFeature extends Feature<NoneFeatureConfiguration> {
	private static final int MIN_GROUP = 2;
	private static final int MAX_GROUP = 6;
	private static final int SPREAD = 7;
	private static final int MAX_ATTEMPTS = 64;
	private static final int SCAN_UP = 3;
	private static final int SCAN_DOWN = 12;

	public WildCropFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		BlockPos origin = context.origin();
		RandomSource random = context.random();

		List<Block> pool = cropPoolFor(level.getBiome(origin));
		if (pool.isEmpty()) {
			return false;
		}

		Block crop = pool.get(random.nextInt(pool.size()));
		int count = MIN_GROUP + random.nextInt(MAX_GROUP - MIN_GROUP + 1);
		int placed = 0;

		for (int i = 0; i < MAX_ATTEMPTS && placed < count; i++) {
			BlockPos column = origin.offset(
					random.nextInt(SPREAD * 2 + 1) - SPREAD,
					0,
					random.nextInt(SPREAD * 2 + 1) - SPREAD);

			BlockPos pos = findPlantablePos(level, column);
			if (pos != null && tryPlaceCrop(level, pos, crop, random)) {
				placed++;
			}
		}

		return placed > 0;
	}

	/**
	 * From a heightmap column, search nearby Y levels for replaceable air/plants/snow
	 * sitting on valid wild-crop soil.
	 */
	private static BlockPos findPlantablePos(WorldGenLevel level, BlockPos column) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int maxY = column.getY() + SCAN_UP;
		int minY = column.getY() - SCAN_DOWN;

		for (int y = maxY; y >= minY; y--) {
			pos.set(column.getX(), y, column.getZ());
			if (!isPlantableSpace(level, pos)) {
				continue;
			}
			if (!isValidSoil(level.getBlockState(pos.below()))) {
				continue;
			}
			return pos.immutable();
		}

		return null;
	}

	private static boolean isPlantableSpace(WorldGenLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!level.getFluidState(pos).isEmpty()) {
			return false;
		}
		return isOverridableGroundCover(state);
	}

	/**
	 * Air plus flora that wild crops are allowed to displace (grass, ferns, flowers, etc.).
	 */
	private static boolean isOverridableGroundCover(BlockState state) {
		if (state.isAir()) {
			return true;
		}
		if (state.canBeReplaced()) {
			return true;
		}

		Block block = state.getBlock();
		return block == Blocks.SHORT_GRASS
				|| block == Blocks.FERN
				|| block == Blocks.TALL_GRASS
				|| block == Blocks.LARGE_FERN
				|| block == Blocks.DEAD_BUSH
				|| block == Blocks.SNOW
				|| state.is(BlockTags.FLOWERS)
				|| state.is(BlockTags.REPLACEABLE)
				|| state.is(BlockTags.REPLACEABLE_BY_TREES);
	}

	private static boolean tryPlaceCrop(WorldGenLevel level, BlockPos pos, Block crop, RandomSource random) {
		BlockState toPlace = randomStage(crop, random);
		if (toPlace == null) {
			return false;
		}

		// FEATURES run before INITIALIZE_LIGHT / LIGHT. CropBlock.canSurvive requires
		// brightness ≥ 8, so calling it here rejects every wild crop. Soil was already
		// validated by findPlantablePos; skip the light gate during worldgen.
		if (!isValidSoil(level.getBlockState(pos.below()))) {
			return false;
		}

		boolean tall = needsUpper(toPlace);
		if (tall && !isPlantableSpace(level, pos.above())) {
			return false;
		}

		preparePlantSpace(level, pos, tall);
		// UPDATE_CLIENTS only — avoid neighbor updates that would re-check canSurvive
		// (still dark) and pop the crop before the light engine runs.
		level.setBlock(pos, toPlace, Block.UPDATE_CLIENTS);
		if (tall) {
			BlockState upper = toPlace.setValue(tallHalfProperty(toPlace), DoubleBlockHalf.UPPER);
			level.setBlock(pos.above(), upper, Block.UPDATE_CLIENTS);
		}
		return true;
	}

	/**
	 * Clears grass / flowers / snow at the plant position, and any double-plant
	 * upper half above so we do not leave floating tall grass after overwrite.
	 */
	private static void preparePlantSpace(WorldGenLevel level, BlockPos pos, boolean needAbove) {
		clearGroundCover(level, pos);

		BlockPos above = pos.above();
		BlockState aboveState = level.getBlockState(above);
		if (isDoublePlantUpper(aboveState) || (needAbove && isOverridableGroundCover(aboveState))) {
			clearGroundCover(level, above);
		}
	}

	private static void clearGroundCover(WorldGenLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!isOverridableGroundCover(state)) {
			return;
		}

		// If we hit a double-plant upper, also clear the lower half.
		if (isDoublePlantUpper(state)) {
			BlockPos below = pos.below();
			if (isOverridableGroundCover(level.getBlockState(below))) {
				level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}

		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);

		// If we cleared a double-plant lower, clear the upper half.
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
				&& state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
			BlockPos above = pos.above();
			if (isDoublePlantUpper(level.getBlockState(above))) {
				level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
	}

	private static boolean isDoublePlantUpper(BlockState state) {
		return state.getBlock() instanceof DoublePlantBlock
				&& state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
				&& state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
	}

	private static boolean isValidSoil(BlockState soil) {
		Block block = soil.getBlock();
		return block == Blocks.GRASS_BLOCK
				|| block == Blocks.DIRT
				|| block == Blocks.COARSE_DIRT
				|| block == Blocks.PODZOL
				|| block == Blocks.SAND
				|| soil.is(BlockTags.SAND)
				|| soil.is(BlockTags.SUPPORTS_CROPS);
	}

	private static BlockState randomStage(Block crop, RandomSource random) {
		if (crop instanceof StemBlock) {
			return crop.defaultBlockState().setValue(StemBlock.AGE, random.nextInt(8));
		}
		if (!(crop instanceof CropBlock cropBlock)) {
			return crop.defaultBlockState();
		}

		int age = random.nextInt(cropBlock.getMaxAge() + 1);
		BlockState state = cropBlock.getStateForAge(age);

		if (crop instanceof TallFruitingCropBlock || crop instanceof TallCropBlock) {
			state = state.setValue(
					crop instanceof TallFruitingCropBlock ? TallFruitingCropBlock.HALF : TallCropBlock.HALF,
					DoubleBlockHalf.LOWER);
		}
		if (crop instanceof FruitingCropBlock && state.hasProperty(FruitingCropBlock.FRUITING)) {
			state = state.setValue(FruitingCropBlock.FRUITING, age == cropBlock.getMaxAge() && random.nextBoolean());
		}
		return state;
	}

	private static boolean needsUpper(BlockState state) {
		if (state.getBlock() instanceof TallFruitingCropBlock) {
			return state.getValue(TallFruitingCropBlock.HALF) == DoubleBlockHalf.LOWER
					&& state.getValue(CropBlock.AGE) >= TallFruitingCropBlock.TALL_AGE;
		}
		if (state.getBlock() instanceof TallCropBlock) {
			return state.getValue(TallCropBlock.HALF) == DoubleBlockHalf.LOWER
					&& state.getValue(CropBlock.AGE) >= TallCropBlock.TALL_AGE;
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private static net.minecraft.world.level.block.state.properties.EnumProperty<DoubleBlockHalf> tallHalfProperty(BlockState state) {
		if (state.getBlock() instanceof TallFruitingCropBlock) {
			return TallFruitingCropBlock.HALF;
		}
		return TallCropBlock.HALF;
	}

	/**
	 * Crops allowed to spawn naturally in this biome category.
	 * Pool follows {@link BiomeSpawnCategory} (tags + temperature/humidity), so each crop
	 * only appears where its climate band matches.
	 */
	static List<Block> cropPoolFor(net.minecraft.core.Holder<Biome> biome) {
		List<Block> pool = new ArrayList<>();

		if (BiomeSpawnCategory.isTropicalWet(biome)) {
			pool.add(ModBlocks.TOMATO_CROP);
			pool.add(ModBlocks.PEPPER_CROP);
			pool.add(ModBlocks.EGGPLANT_CROP);
			pool.add(ModBlocks.CUCUMBER_CROP);
			pool.add(ModBlocks.SWEET_POTATO_CROP);
			pool.add(ModBlocks.RICE_CROP);
			pool.add(ModBlocks.BEANS_CROP);
			pool.add(ModBlocks.CANTELOPE_STEM);
			pool.add(Blocks.MELON_STEM);
			// Basil: jungles / sparse jungles / bamboo only (not swamp / mangrove).
			if (BiomeSpawnCategory.isJungle(biome)) {
				pool.add(ModBlocks.BASIL_CROP);
			}
			return pool;
		}

		if (BiomeSpawnCategory.isSavanna(biome)) {
			pool.add(ModBlocks.CANTELOPE_STEM);
			pool.add(ModBlocks.PEPPER_CROP);
			pool.add(Blocks.PUMPKIN_STEM);
			return pool;
		}

		if (BiomeSpawnCategory.isDesert(biome)) {
			pool.add(ModBlocks.PEPPER_CROP);
			return pool;
		}

		if (BiomeSpawnCategory.isColdSpawn(biome)) {
			pool.add(Blocks.CARROTS);
			pool.add(Blocks.BEETROOTS);
			return pool;
		}

		if (BiomeSpawnCategory.isTemperateSpawn(biome)) {
			pool.add(ModBlocks.OATS_CROP);
			pool.add(ModBlocks.BEANS_CROP);
			pool.add(Blocks.CARROTS);
			pool.add(Blocks.POTATOES);
			pool.add(Blocks.BEETROOTS);
			pool.add(Blocks.PUMPKIN_STEM);
			return pool;
		}

		return pool;
	}
}
