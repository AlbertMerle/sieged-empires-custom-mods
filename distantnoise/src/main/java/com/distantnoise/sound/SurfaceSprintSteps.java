package com.distantnoise.sound;

import com.distantnoise.config.DistantNoiseConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Above-ground player sprint footfalls — relayed with distance muffling out to
 * {@code sprint.max-volume-range}. Cave echoes take priority when enclosed.
 */
public final class SurfaceSprintSteps {
	private SurfaceSprintSteps() {
	}

	public static boolean shouldRelay(Player player, DistantNoiseConfig cfg) {
		if (!cfg.enabled || !cfg.sprintFootstepsEnabled) {
			return false;
		}
		if (player.isSpectator() || player.getAbilities().flying) {
			return false;
		}
		if (!player.isSprinting()) {
			return false;
		}
		// Cave path owns enclosed spaces (longer sprint range there).
		if (cfg.caveEchoesEnabled && CaveEnclosure.isEchoingCave(player)) {
			return false;
		}
		return true;
	}

	public static Optional<FootstepProfile> stepProfile(BlockState steppedOn) {
		SoundType soundType = steppedOn.getSoundType();
		var stepSound = soundType.getStepSound();
		var soundId = BuiltInRegistries.SOUND_EVENT.getKey(stepSound);
		if (soundId == null) {
			return Optional.empty();
		}
		float volume = soundType.getVolume() * 0.15f;
		float pitch = soundType.getPitch();
		return Optional.of(new FootstepProfile(soundId, volume, pitch));
	}
}
