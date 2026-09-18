package com.siegedempires.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class PurchaseFeedback {
	private PurchaseFeedback() {
	}

	public static void playSound(ServerPlayer player) {
		if (player == null) {
			return;
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.75F, 1.0F);
	}
}
