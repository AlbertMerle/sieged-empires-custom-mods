package com.siegedempires.recipe;

import com.siegedempires.item.ModItems;
import net.minecraft.world.item.ItemStack;

/** 16 gold coins melt back into one gold ingot in furnaces / blast furnaces. */
public final class GoldCoinSmelting {
	public static final int COINS_PER_INGOT = 16;
	/** Vanilla smelting/blasting is 200 / 100 ticks; coins melt three times faster. */
	public static final int SMELTING_TIME = 200 / 3;
	public static final int BLASTING_TIME = 100 / 3;

	private GoldCoinSmelting() {
	}

	public static boolean isGoldCoin(ItemStack stack) {
		return !stack.isEmpty() && stack.is(ModItems.GOLD_COIN);
	}

	public static boolean hasEnoughCoins(ItemStack stack) {
		return isGoldCoin(stack) && stack.getCount() >= COINS_PER_INGOT;
	}
}
