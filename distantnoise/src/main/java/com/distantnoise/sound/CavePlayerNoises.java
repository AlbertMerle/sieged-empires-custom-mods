package com.distantnoise.sound;

import com.distantnoise.config.DistantNoiseConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.Set;

/** Player cave-echo profiles: walk / sprint steps and fall-damage impacts. */
public final class CavePlayerNoises {
	private static final Set<Identifier> FALL_SOUND_IDS = Set.of(
			Identifier.parse("minecraft:entity.player.small_fall"),
			Identifier.parse("minecraft:entity.player.big_fall"),
			Identifier.parse("minecraft:entity.generic.small_fall"),
			Identifier.parse("minecraft:entity.generic.big_fall")
	);

	private CavePlayerNoises() {
	}

	public static boolean shouldRelaySteps(Player player, DistantNoiseConfig cfg) {
		if (!cfg.enabled || !cfg.caveEchoesEnabled) {
			return false;
		}
		if (player.isSpectator() || player.getAbilities().flying) {
			return false;
		}
		return CaveEnclosure.isEchoingCave(player);
	}

	public static boolean shouldRelayFall(Player player, DistantNoiseConfig cfg) {
		if (!cfg.enabled || !cfg.caveEchoesEnabled) {
			return false;
		}
		if (player.isSpectator()) {
			return false;
		}
		return CaveEnclosure.isEchoingCave(player);
	}

	public static boolean isFallDamageSound(SoundEvent event) {
		Identifier id = BuiltInRegistries.SOUND_EVENT.getKey(event);
		return id != null && FALL_SOUND_IDS.contains(id);
	}

	public static FootstepRelayKind stepKind(Player player) {
		return player.isSprinting() ? FootstepRelayKind.CAVE_SPRINT : FootstepRelayKind.CAVE_WALK;
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

	public static Optional<FootstepProfile> fallProfile(SoundEvent event, float volume, float pitch) {
		Identifier soundId = BuiltInRegistries.SOUND_EVENT.getKey(event);
		if (soundId == null) {
			return Optional.empty();
		}
		return Optional.of(new FootstepProfile(soundId, volume, pitch));
	}
}
