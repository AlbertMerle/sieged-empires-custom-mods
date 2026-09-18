package com.siegedempires.gold;

import com.siegedempires.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;

public final class GoldBarSounds {
	private GoldBarSounds() {
	}

	public static void play(ServerLevel level, BlockPos pos) {
		level.playSound(
				null,
				pos,
				ModSounds.GOLD_BAR_PLACE.value(),
				SoundSource.BLOCKS,
				0.8F,
				0.95F + level.getRandom().nextFloat() * 0.1F
		);
	}
}
