package com.siegedempires.util;

import com.siegedempires.recipe.GoldCoinSmelting;
import com.siegedempires.item.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class InventoryHelper {
	/** Gold bar costs may also be paid with gold coins at this rate (e.g. 5 bars → 80 coins). */
	public static final int COINS_PER_GOLD_BAR = GoldCoinSmelting.COINS_PER_INGOT;

	private InventoryHelper() {
	}

	public static int countGoldBarEquivalent(Player player) {
		int ingots = 0;
		int coins = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.is(Items.GOLD_INGOT)) {
				ingots += stack.getCount();
			} else if (stack.is(ModItems.GOLD_COIN)) {
				coins += stack.getCount();
			}
		}
		return ingots + coins / COINS_PER_GOLD_BAR;
	}

	public static boolean hasEnoughGold(Player player, int barCount) {
		return countGoldBarEquivalent(player) >= barCount;
	}

	public static void removeGold(ServerPlayer player, int barCount) {
		int remainingBars = barCount;

		for (int i = 0; i < player.getInventory().getContainerSize() && remainingBars > 0; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(Items.GOLD_INGOT)) {
				int toRemove = Math.min(stack.getCount(), remainingBars);
				stack.shrink(toRemove);
				remainingBars -= toRemove;
			}
		}

		if (remainingBars > 0) {
			int remainingCoins = remainingBars * COINS_PER_GOLD_BAR;
			for (int i = 0; i < player.getInventory().getContainerSize() && remainingCoins > 0; i++) {
				ItemStack stack = player.getInventory().getItem(i);
				if (stack.is(ModItems.GOLD_COIN)) {
					int toRemove = Math.min(stack.getCount(), remainingCoins);
					stack.shrink(toRemove);
					remainingCoins -= toRemove;
				}
			}
		}

		PurchaseFeedback.playSound(player);
	}
}
