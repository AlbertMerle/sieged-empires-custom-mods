package com.croplite.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;

/** Leaves for the fruit trees. Fruit and sapling drops are handled by the block's loot table. */
public class FruitLeavesBlock extends LeavesBlock {
	public static final MapCodec<FruitLeavesBlock> CODEC = simpleCodec(props -> new FruitLeavesBlock(props));

	public FruitLeavesBlock(Properties properties) {
		super(0.0F, properties);
	}

	@Override
	public MapCodec<FruitLeavesBlock> codec() {
		return CODEC;
	}

	@Override
	protected void spawnFallingLeavesParticle(Level level, BlockPos pos, RandomSource random) {
	}
}
