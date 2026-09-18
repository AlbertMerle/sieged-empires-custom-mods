package com.siegedempires.client.nametag;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/**
 * Hides player gamertags for stealth poses:
 * <ul>
 *   <li>always while crawling ({@link Entity#isVisuallyCrawling()})</li>
 *   <li>sneaking while intersecting a 2-block-tall plant (vanilla {@link DoublePlantBlock}
 *       or modded plants using {@code half}, or two stacked plant blocks of the same type)</li>
 * </ul>
 */
public final class PlantStealthHelper {
	private PlantStealthHelper() {
	}

	public static boolean shouldHideNametag(Entity entity) {
		if (entity.isVisuallyCrawling()) {
			return true;
		}
		if (!(entity.isCrouching() || entity.isDiscrete())) {
			return false;
		}

		AABB box = entity.getBoundingBox();
		BlockGetter level = entity.level();
		for (BlockPos pos : BlockPos.betweenClosed(box)) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			if (isTallPlant(state, level, pos)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Short or tall plant-like blocks a crawling player can nest in.
	 * Prefer class/tag checks so modded vegetation that extends
	 * {@link VegetationBlock} or uses crop/flower tags is included.
	 */
	public static boolean isPlantCover(BlockState state) {
		if (state.getBlock() instanceof VegetationBlock) {
			return true;
		}
		if (state.is(BlockTags.CROPS) || state.is(BlockTags.FLOWERS)) {
			return true;
		}
		// Modded tall plants that use the vanilla half property but do not
		// extend VegetationBlock / DoublePlantBlock.
		return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
	}

	/**
	 * Two-block-tall plants: vanilla double plants, anything with {@code half},
	 * or two plant-cover blocks of the same type stacked vertically (common
	 * modded pattern).
	 */
	public static boolean isTallPlant(BlockState state, BlockGetter level, BlockPos pos) {
		if (state.getBlock() instanceof DoublePlantBlock) {
			return true;
		}
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			return true;
		}
		if (!isPlantCover(state)) {
			return false;
		}
		BlockState above = level.getBlockState(pos.above());
		if (above.is(state.getBlock()) && isPlantCover(above)) {
			return true;
		}
		BlockState below = level.getBlockState(pos.below());
		return below.is(state.getBlock()) && isPlantCover(below);
	}
}
