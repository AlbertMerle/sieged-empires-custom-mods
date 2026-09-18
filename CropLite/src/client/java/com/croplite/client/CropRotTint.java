package com.croplite.client;

import com.croplite.block.ModBlocks;
import com.croplite.crop.CropClimate;

import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.List;
import java.util.Set;

/**
 * Tints crops with biome foliage color when healthy (greyscale plant textures),
 * and brown when <em>rotting</em> (wrong climate / seasonal rot).
 * Fruit/grain overlays omit {@code tintindex} so peaches, tomatoes, etc. stay fixed.
 */
public final class CropRotTint {
	private static final int ROTTED = ARGB.color(120, 78, 36);
	/** Vanilla attached melon/pumpkin stem tint (BlockColors). */
	private static final int ATTACHED_STEM = -2046180;

	private static final BlockTintSource FOLIAGE = BlockTintSources.foliage();

	private CropRotTint() {
	}

	public static void register() {
		// Vanilla crops keep authored colors (white multiply); only brown when rotting.
		BlockColorRegistry.register(List.of(cropConstantTint(ARGB.color(255, 255, 255))),
				Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS);

		// CropLite plant sheets are greyscale — biome foliage tint when healthy.
		BlockColorRegistry.register(List.of(cropFoliageTint()),
				ModBlocks.TOMATO_CROP, ModBlocks.PEPPER_CROP, ModBlocks.EGGPLANT_CROP,
				ModBlocks.CUCUMBER_CROP, ModBlocks.SWEET_POTATO_CROP, ModBlocks.BASIL_CROP,
				ModBlocks.OATS_CROP, ModBlocks.BEANS_CROP, ModBlocks.RICE_CROP,
				ModBlocks.COFFEE_CROP, ModBlocks.GARLIC_CROP);

		// Growing stems use age-based tint (StemBlock.AGE). Attached stems have no AGE —
		// registering stem() on them crashes ModelGroupCollector and blacks out the client.
		BlockColorRegistry.register(List.of(stemTint(BlockTintSources.stem())),
				Blocks.MELON_STEM, Blocks.PUMPKIN_STEM);
		BlockColorRegistry.register(List.of(stemTint(BlockTintSources.constant(ATTACHED_STEM))),
				Blocks.ATTACHED_MELON_STEM, Blocks.ATTACHED_PUMPKIN_STEM);

		BlockColorRegistry.register(List.of(stemTint(FOLIAGE)),
				ModBlocks.CANTELOPE_STEM, ModBlocks.ATTACHED_CANTELOPE_STEM);
	}

	private static BlockTintSource cropConstantTint(int healthyColor) {
		return new BlockTintSource() {
			@Override
			public int color(BlockState state) {
				return healthyColor;
			}

			@Override
			public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
				if (isRotted(state.getBlock(), level, pos)) {
					return ROTTED;
				}
				return healthyColor;
			}
		};
	}

	private static BlockTintSource cropFoliageTint() {
		return new BlockTintSource() {
			@Override
			public int color(BlockState state) {
				return FOLIAGE.color(state);
			}

			@Override
			public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
				if (isRotted(state.getBlock(), level, samplePos(state, pos))) {
					return ROTTED;
				}
				return FOLIAGE.colorInWorld(state, level, samplePos(state, pos));
			}

			@Override
			public Set<Property<?>> relevantProperties() {
				return FOLIAGE.relevantProperties();
			}
		};
	}

	private static BlockTintSource stemTint(BlockTintSource healthy) {
		return new BlockTintSource() {
			@Override
			public int color(BlockState state) {
				return healthy.color(state);
			}

			@Override
			public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
				if (isRotted(state.getBlock(), level, pos)) {
					return ROTTED;
				}
				return healthy.colorInWorld(state, level, pos);
			}

			@Override
			public Set<Property<?>> relevantProperties() {
				return healthy.relevantProperties();
			}
		};
	}

	/** Upper halves of tall crops sample the lower block, like vanilla tall grass. */
	private static BlockPos samplePos(BlockState state, BlockPos pos) {
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
				&& state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
			return pos.below();
		}
		return pos;
	}

	/**
	 * Chunk meshing uses {@code RenderSectionRegion}, which is not a {@link LevelReader}.
	 * Fall back to the client level so climate rot still tints brown in-world.
	 */
	private static boolean isRotted(Block block, BlockAndTintGetter level, BlockPos pos) {
		LevelReader reader = level instanceof LevelReader levelReader
				? levelReader
				: Minecraft.getInstance().level;
		return reader != null && CropClimate.isRotting(block, reader, pos);
	}
}
