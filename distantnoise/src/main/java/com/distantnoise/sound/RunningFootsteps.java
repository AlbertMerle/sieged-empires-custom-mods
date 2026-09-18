package com.distantnoise.sound;

import com.distantnoise.config.DistantNoiseConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Running / fleeing mob footfalls relayed farther than vanilla step range. */
public final class RunningFootsteps {
	private RunningFootsteps() {
	}

	public static boolean shouldRelay(LivingEntity entity, DistantNoiseConfig cfg) {
		if (entity.isBaby() || !VocalAnimals.isVocalAnimal(entity)) {
			return false;
		}
		if (cfg.isStompingEntity(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) {
			return false;
		}
		if (cfg.isSnakeEntity(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) {
			return false;
		}
		return isFleeing(entity, cfg);
	}

	/** Panicking or moving fast enough to count as running/fleeing. */
	public static boolean isFleeing(LivingEntity entity, DistantNoiseConfig cfg) {
		if (isPanicking(entity)) {
			return true;
		}
		return horizontalBlocksPerSecond(entity) >= cfg.runningMinSpeedBps;
	}

	public static Optional<FootstepProfile> profileFor(LivingEntity entity, BlockState steppedOn) {
		if (entity.isBaby()) {
			return Optional.empty();
		}
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

	private static double horizontalBlocksPerSecond(LivingEntity entity) {
		Vec3 delta = entity.getDeltaMovement();
		return delta.horizontalDistance() * 20.0;
	}

	private static boolean isPanicking(LivingEntity entity) {
		if (!(entity instanceof Mob mob)) {
			return false;
		}
		return mob.getBrain().checkMemory(MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_PRESENT);
	}
}
