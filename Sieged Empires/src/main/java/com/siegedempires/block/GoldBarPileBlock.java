package com.siegedempires.block;

import com.mojang.math.OctahedralGroup;
import com.mojang.serialization.MapCodec;
import com.siegedempires.gold.GoldBarSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GoldBarPileBlock extends Block {
	public static final MapCodec<GoldBarPileBlock> CODEC = simpleCodec(GoldBarPileBlock::new);
	public static final IntegerProperty COUNT = IntegerProperty.create("count", 1, 15);
	public static final BooleanProperty ROTATED = BooleanProperty.create("rotated");
	public static final int MAX_COUNT = 15;

	private static final Vec3 ROTATION_ORIGIN = new Vec3(0.5, 0.5, 0.5);

	private static final int MAX_LAYERS = 5;
	private static final int BARS_PER_LAYER = 3;
	private static final double BAR_WIDTH = 5.0;
	private static final double BAR_HEIGHT = 3.0;
	private static final double GAP = 1.0;
	private static final double[] LAYER_Y = {0.0, 3.0, 6.0, 9.0, 12.0};

	private static final VoxelShape[] SHAPES = buildShapes();

	public GoldBarPileBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(COUNT, 1).setValue(ROTATED, false));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(COUNT, ROTATED);
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
		VoxelShape shape = SHAPES[state.getValue(COUNT) - 1];
		if (state.getValue(ROTATED)) {
			return Shapes.rotate(shape, OctahedralGroup.ROT_90_Y_POS, ROTATION_ORIGIN);
		}
		return shape;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel serverLevel) {
			GoldBarSounds.play(serverLevel, pos);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {
		level.levelEvent(player, 2001, pos, Block.getId(Blocks.GOLD_BLOCK.defaultBlockState()));
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
			Block.popResource(serverLevel, pos, new ItemStack(Items.GOLD_INGOT, state.getValue(COUNT)));
		}
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		return List.of(new ItemStack(Items.GOLD_INGOT, state.getValue(COUNT)));
	}

	private static VoxelShape[] buildShapes() {
		VoxelShape[] shapes = new VoxelShape[MAX_COUNT];
		for (int count = 1; count <= MAX_COUNT; count++) {
			VoxelShape shape = Shapes.empty();
			int remaining = count;
			for (int layer = 0; layer < MAX_LAYERS && remaining > 0; layer++) {
				int layerCount = Math.min(BARS_PER_LAYER, remaining);
				remaining -= layerCount;
				double y0 = LAYER_Y[layer] / 16.0;
				double y1 = y0 + BAR_HEIGHT / 16.0;
				for (double xStart : rowXStarts(layerCount)) {
					shape = Shapes.or(shape, Shapes.box(
							xStart / 16.0,
							y0,
							0.0,
							(xStart + BAR_WIDTH) / 16.0,
							y1,
							1.0
					));
				}
			}
			shapes[count - 1] = shape;
		}
		return shapes;
	}

	private static double[] rowXStarts(int layerCount) {
		if (layerCount == 1) {
			return new double[]{(16.0 - BAR_WIDTH) / 2.0};
		}
		if (layerCount == 2) {
			double total = 2.0 * BAR_WIDTH + GAP;
			double start = (16.0 - total) / 2.0;
			return new double[]{start, start + BAR_WIDTH + GAP};
		}
		double total = 3.0 * BAR_WIDTH;
		double start = (16.0 - total) / 2.0;
		return new double[]{start, start + BAR_WIDTH, start + 2.0 * BAR_WIDTH};
	}
}
