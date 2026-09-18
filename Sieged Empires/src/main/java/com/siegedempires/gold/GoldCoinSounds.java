package com.siegedempires.gold;

import com.siegedempires.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.SoundType;

public final class GoldCoinSounds {
	public static final SoundType BLOCK_SOUND = new SoundType(
			1.0F,
			1.0F,
			ModSounds.COIN_CRAFT.value(),
			ModSounds.COIN_CRAFT.value(),
			ModSounds.COIN_CRAFT.value(),
			ModSounds.COIN_CRAFT.value(),
			ModSounds.COIN_CRAFT.value()
	);

	private GoldCoinSounds() {
	}

	public static void play(ServerLevel level, BlockPos pos) {
		level.playSound(
				null,
				pos,
				ModSounds.COIN_CRAFT.value(),
				SoundSource.BLOCKS,
				0.8F,
				0.95F + level.getRandom().nextFloat() * 0.1F
		);
	}
}
