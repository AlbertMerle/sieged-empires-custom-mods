package com.croplite.block;

import com.mojang.serialization.MapCodec;

import com.croplite.crop.CropClimate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/** Two-block-tall crop used by the grain and legume plants. The lower half drives all growth. */
public class TallCropBlock extends CropBlock {
	public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

	/** Age at which the plant grows its second block. */
	public static final int TALL_AGE = 4;

	private final Supplier<Item> seed;
	private final MapCodec<? extends CropBlock> codec;

	public TallCropBlock(Supplier<Item> seed, Properties properties) {
		super(properties);
		this.seed = seed;
		this.codec = simpleCodec(props -> new TallCropBlock(seed, props));
		this.registerDefaultState(this.stateDefinition.any()
				.setValue(this.getAgeProperty(), 0)
				.setValue(HALF, DoubleBlockHalf.LOWER));
	}

	@Override
	public MapCodec<? extends CropBlock> codec() {
		return this.codec;
	}

	@Override
	protected ItemLike getBaseSeedId() {
		return this.seed.get();
	}

	public static boolean isLower(BlockState state) {
		return state.getValue(HALF) == DoubleBlockHalf.LOWER;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		if (!isLower(state)) {
			BlockState below = level.getBlockState(pos.below());
			return below.is(this) && isLower(below);
		}

		return super.canSurvive(state, level, pos);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide()) {
			BlockPos lowerPos = isLower(state) ? pos : pos.below();
			BlockState lowerState = level.getBlockState(lowerPos);

			if (!player.preventsBlockDrops() && lowerState.is(this) && isLower(lowerState)) {
				// Loot tables only match the lower half; drop once regardless of which half is broken.
				dropResources(lowerState, level, lowerPos, null, player, player.getMainHandItem());
			}

			BlockPos partnerPos = isLower(state) ? pos.above() : pos.below();
			BlockState partnerState = level.getBlockState(partnerPos);

			if (partnerState.is(this) && isLower(partnerState) != isLower(state)) {
				level.setBlock(partnerPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}

		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
			@Nullable BlockEntity blockEntity, ItemStack destroyedWith) {
		super.playerDestroy(level, player, pos, Blocks.AIR.defaultBlockState(), blockEntity, destroyedWith);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!isLower(state) || CropClimate.isRotting(this, level, pos) || !hasSufficientLight(level, pos)) {
			return;
		}

		if (this.isMaxAge(state)) {
			growUpperHalf(level, pos, state);
			return;
		}

		float speed = getGrowthSpeed(this, level, pos);

		if (random.nextInt((int) (25.0F / speed) + 1) == 0) {
			setAge(level, pos, state, this.getAge(state) + 1);
		}
	}

	/** Mature plants stay random-ticked so a broken top can grow back. */
	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	public void growCrops(Level level, BlockPos pos, BlockState state) {
		if (!isLower(state) || CropClimate.isRotting(this, level, pos)) {
			return;
		}

		int age = Math.min(this.getAge(state) + this.getBonemealAgeIncrease(level), this.getMaxAge());
		setAge(level, pos, state, age);
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
		return isLower(state)
				&& !CropClimate.isRotting(this, level, pos)
				&& super.isValidBonemealTarget(level, pos, state);
	}

	private void setAge(Level level, BlockPos pos, BlockState state, int age) {
		BlockState lower = state.setValue(this.getAgeProperty(), age).setValue(HALF, DoubleBlockHalf.LOWER);
		level.setBlock(pos, lower, Block.UPDATE_CLIENTS);
		growUpperHalf(level, pos, lower);
	}

	private void growUpperHalf(Level level, BlockPos pos, BlockState lower) {
		if (this.getAge(lower) < TALL_AGE) {
			return;
		}

		BlockPos above = pos.above();
		BlockState aboveState = level.getBlockState(above);
		BlockState upper = lower.setValue(HALF, DoubleBlockHalf.UPPER);

		// An already-grown top must follow the lower half's age, otherwise it keeps rendering
		// the stage it was created at and never reaches the mature texture.
		boolean canPlace = aboveState.isAir() || (aboveState.is(this) && !isLower(aboveState));

		if (canPlace && aboveState != upper) {
			level.setBlock(above, upper, Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(HALF);
	}
}
