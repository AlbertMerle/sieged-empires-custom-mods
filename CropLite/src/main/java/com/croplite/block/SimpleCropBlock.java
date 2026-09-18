package com.croplite.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;

import java.util.function.Supplier;

/** A vanilla-style crop whose seed item is supplied at registration time. */
public class SimpleCropBlock extends CropBlock {
	private final Supplier<Item> seed;
	private final MapCodec<? extends CropBlock> codec;

	public SimpleCropBlock(Supplier<Item> seed, Properties properties) {
		super(properties);
		this.seed = seed;
		this.codec = simpleCodec(props -> new SimpleCropBlock(seed, props));
	}

	@Override
	public MapCodec<? extends CropBlock> codec() {
		return this.codec;
	}

	@Override
	protected ItemLike getBaseSeedId() {
		return this.seed.get();
	}
}
