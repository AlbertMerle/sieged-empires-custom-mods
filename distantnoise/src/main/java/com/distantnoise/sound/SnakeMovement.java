package com.distantnoise.sound;

import com.distantnoise.config.DistantNoiseConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/**
 * Snake movement (rattlesnake, anaconda) plays spaced-out grass walking sounds.
 */
public final class SnakeMovement {
	private static final float BASE_VOLUME = 0.35f;
	/** Distance in blocks moved before triggering the next spaced-out sound. */
	public static final double STEP_DISTANCE = 1.3;

	private SnakeMovement() {
	}

	public static boolean isSnake(LivingEntity entity, DistantNoiseConfig cfg) {
		if (entity == null || entity.isBaby() || entity.isDeadOrDying() || entity.isRemoved()) {
			return false;
		}
		var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		return cfg.isSnakeEntity(typeId);
	}

	public static boolean shouldRelay(LivingEntity entity, DistantNoiseConfig cfg) {
		if (!cfg.enabled || !cfg.snakeMovementEnabled) {
			return false;
		}
		if (!isSnake(entity, cfg)) {
			return false;
		}
		if (entity.isPassenger()) {
			return false;
		}
		return true;
	}

	public static Optional<FootstepProfile> profile(LivingEntity entity) {
		var soundId = BuiltInRegistries.SOUND_EVENT.getKey(ModSounds.GRASS_WALK.value());
		if (soundId == null) {
			return Optional.empty();
		}
		return Optional.of(new FootstepProfile(soundId, BASE_VOLUME, 1.0f));
	}
}
