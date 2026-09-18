package com.moremeatmilk.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import toughasnails.api.thirst.IThirst;
import toughasnails.api.thirst.ThirstHelper;

public final class ToughAsNailsCompat {

	private static final float THIRST_EXHAUSTION = 4.0F;
	private static final float HYDRATION_PENALTY = 0.5F;
	private static final int THIRST_PENALTY = 1;

	private ToughAsNailsCompat() {
	}

	public static void applyDehydration(Player player) {
		if (!ThirstHelper.isThirstEnabled()) {
			return;
		}

		IThirst thirst = ThirstHelper.getThirst(player);
		thirst.addExhaustion(THIRST_EXHAUSTION);
		thirst.addHydration(-HYDRATION_PENALTY);

		if (player instanceof ServerPlayer) {
			thirst.addThirst(-THIRST_PENALTY);
		}
	}

}
