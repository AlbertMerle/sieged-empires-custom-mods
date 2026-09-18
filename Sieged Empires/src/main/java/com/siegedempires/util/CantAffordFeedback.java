package com.siegedempires.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class CantAffordFeedback {
	private CantAffordFeedback() {
	}

	public static void playSound(ServerPlayer player) {
		if (player == null) {
			return;
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.COPPER_GOLEM_STATUE_FALL, SoundSource.PLAYERS, 1.0F, 1.0F);
	}
}
