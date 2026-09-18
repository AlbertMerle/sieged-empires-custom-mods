package com.distantnoise.sound;

import com.distantnoise.config.DistantNoiseConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Player grass-walk clips while moving through non-collidable plants.
 * Triggered from {@code playStepSound} (footstep cadence), not a tick poll.
 */
public final class PlantGrassWalk {
	private static final float BASE_VOLUME = 0.35f;
	private static final float SNEAK_VOLUME_SCALE = 0.25f;

	private PlantGrassWalk() {
	}

	public static boolean shouldRelay(Player player, BlockState steppedOn, DistantNoiseConfig cfg) {
		if (!cfg.enabled || !cfg.plantGrassWalkEnabled) {
			return false;
		}
		if (player.isSpectator() || player.getAbilities().flying) {
			return false;
		}
		return PlantVegetation.isInRustlePlants(player, steppedOn);
	}

	public static FootstepRelayKind kindFor(Player player) {
		if (isSneakingOrCrawling(player)) {
			return FootstepRelayKind.PLANT_SNEAK;
		}
		if (player.isSprinting()) {
			return FootstepRelayKind.PLANT_SPRINT;
		}
		return FootstepRelayKind.PLANT_WALK;
	}

	public static Optional<FootstepProfile> profile(Player player) {
		var soundId = BuiltInRegistries.SOUND_EVENT.getKey(ModSounds.GRASS_WALK.value());
		if (soundId == null) {
			return Optional.empty();
		}
		float volume = BASE_VOLUME;
		if (isSneakingOrCrawling(player)) {
			volume *= SNEAK_VOLUME_SCALE;
		}
		return Optional.of(new FootstepProfile(soundId, volume, 1.0f));
	}

	private static boolean isSneakingOrCrawling(Player player) {
		if (player.isVisuallyCrawling()) {
			return true;
		}
		Pose pose = player.getPose();
		if (pose == Pose.SWIMMING && player.onGround()) {
			return true;
		}
		return player.isCrouching() || player.isShiftKeyDown();
	}
}
