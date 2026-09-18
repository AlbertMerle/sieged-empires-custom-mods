package com.siegedempires.block;

import com.mojang.serialization.MapCodec;
import com.siegedempires.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GoldCoinPileBlock extends Block {
	public static final MapCodec<GoldCoinPileBlock> CODEC = simpleCodec(GoldCoinPileBlock::new);
	public static final IntegerProperty COUNT = IntegerProperty.create("count", 1, 64);
	public static final int MAX_COUNT = 64;

	/** Axis-aligned bounds from each block model's element from/to (16×16×16 space). */
	private static final VoxelShape SHAPE_SMALL = Shapes.box(0.5 / 16.0, -0.5 / 16.0, 5.5 / 16.0, 11.5 / 16.0, 1.5 / 16.0, 12.5 / 16.0);
	private static final VoxelShape SHAPE_MEDIUM = Shapes.box(-0.5 / 16.0, -0.5 / 16.0, 5.5 / 16.0, 14.5 / 16.0, 1.5 / 16.0, 14.5 / 16.0);
	private static final VoxelShape SHAPE_LARGE = Shapes.box(-0.5 / 16.0, -0.5 / 16.0, 4.5 / 16.0, 15.5 / 16.0, 2.5 / 16.0, 13.5 / 16.0);
	private static final VoxelShape SHAPE_XLARGE = Shapes.box(0.5 / 16.0, -0.5 / 16.0, 0.5 / 16.0, 15.5 / 16.0, 3.5 / 16.0, 13.5 / 16.0);

	public GoldCoinPileBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(COUNT, 1));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(COUNT);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapeForCount(state.getValue(COUNT));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapeForCount(state.getValue(COUNT));
	}

	private static VoxelShape shapeForCount(int count) {
		if (count >= 49) {
			return SHAPE_XLARGE;
		}
		if (count >= 33) {
			return SHAPE_LARGE;
		}
		if (count >= 17) {
			return SHAPE_MEDIUM;
		}
		return SHAPE_SMALL;
	}

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		return 1.0F;
	}

	@Override
	public void playerDestroy(
			Level level,
			Player player,
			BlockPos pos,
			BlockState state,
			@Nullable BlockEntity blockEntity,
			ItemStack destroyedWith
	) {
		player.awardStat(Stats.BLOCK_MINED.get(this));
		player.causeFoodExhaustion(0.005F);
		if (level instanceof ServerLevel serverLevel && !player.preventsBlockDrops()) {
			Block.popResource(serverLevel, pos, new ItemStack(ModItems.GOLD_COIN, state.getValue(COUNT)));
		}
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		return List.of(new ItemStack(ModItems.GOLD_COIN, state.getValue(COUNT)));
	}

	/** Visual tier for block models: small (1–16), medium (17–32), large (33–48), xlarge (49–64). */
	public static String sizeIdForCount(int count) {
		if (count >= 49) {
			return "xlarge";
		}
		if (count >= 33) {
			return "large";
		}
		if (count >= 17) {
			return "medium";
		}
		return "small";
	}
}
