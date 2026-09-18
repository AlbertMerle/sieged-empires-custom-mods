package com.croplite.block;

import com.mojang.serialization.MapCodec;

import com.croplite.crop.CropClimate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

/**
 * A crop that keeps growing after reaching max age: once mature it periodically enters a
 * fruiting state, and right-clicking picks the fruit and returns it to the mature state.
 */
public class FruitingCropBlock extends CropBlock {
	public static final BooleanProperty FRUITING = BooleanProperty.create("fruiting");

	/** One in this many random ticks regrows the fruit on a mature plant. */
	private static final int FRUIT_REGROW_CHANCE = 5;

	private final Supplier<Item> seed;
	private final Supplier<Item> fruit;
	private final int minFruit;
	private final int maxFruit;
	private final MapCodec<? extends CropBlock> codec;

	public FruitingCropBlock(Supplier<Item> seed, Supplier<Item> fruit, Properties properties) {
		this(seed, fruit, 1, 1, properties);
	}

	public FruitingCropBlock(Supplier<Item> seed, Supplier<Item> fruit, int minFruit, int maxFruit, Properties properties) {
		super(properties);
		this.seed = seed;
		this.fruit = fruit;
		this.minFruit = Math.max(1, minFruit);
		this.maxFruit = Math.max(this.minFruit, maxFruit);
		this.codec = simpleCodec(props -> new FruitingCropBlock(seed, fruit, minFruit, maxFruit, props));
		this.registerDefaultState(this.stateDefinition.any()
				.setValue(this.getAgeProperty(), 0)
				.setValue(FRUITING, false));
	}

	@Override
	public MapCodec<? extends CropBlock> codec() {
		return this.codec;
	}

	public Item getFruit() {
		return this.fruit.get();
	}

	@Override
	protected ItemLike getBaseSeedId() {
		return this.seed.get();
	}

	/** Mature plants keep ticking so their fruit can regrow. */
	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (CropClimate.isRotting(this, level, pos)) {
			return;
		}

		if (!this.isMaxAge(state)) {
			super.randomTick(state, level, pos, random);
			return;
		}

		if (!state.getValue(FRUITING)
				&& hasSufficientLight(level, pos)
				&& random.nextInt(FRUIT_REGROW_CHANCE) == 0) {
			level.setBlock(pos, state.setValue(FRUITING, true), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!state.getValue(FRUITING)) {
			return InteractionResult.PASS;
		}

		if (!level.isClientSide()) {
			harvest(level, pos, state);
		}

		return InteractionResult.SUCCESS;
	}

	protected int fruitDropCount(RandomSource random) {
		return this.minFruit + random.nextInt(this.maxFruit - this.minFruit + 1);
	}

	protected void harvest(Level level, BlockPos pos, BlockState state) {
		Block.popResource(level, pos, new ItemStack(getFruit(), fruitDropCount(level.getRandom())));
		level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS,
				1.0F, 0.8F + level.getRandom().nextFloat() * 0.4F);
		level.setBlock(pos, state.setValue(FRUITING, false), Block.UPDATE_CLIENTS);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FRUITING);
	}
}
