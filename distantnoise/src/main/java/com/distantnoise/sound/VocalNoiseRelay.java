package com.distantnoise.sound;

import com.distantnoise.config.DistantNoiseConfig;
import com.distantnoise.network.DistantVocalPayload;
import com.distantnoise.network.ModNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Server-side relay for distance-scaled animal vocal sounds. */
public final class VocalNoiseRelay {
	private VocalNoiseRelay() {
	}

	public static void relay(LivingEntity entity, SoundEvent sound, float volume, float pitch) {
		if (entity.level().isClientSide() || !(entity.level() instanceof ServerLevel level)) {
			return;
		}

		DistantNoiseConfig cfg = DistantNoiseConfig.get();
		if (!cfg.enabled || !cfg.vocalAnimals) {
			return;
		}

		if (!VocalAnimals.shouldRelayVocal(entity, sound)) {
			return;
		}

		Identifier soundId = sound.location();
		if (!BuiltInRegistries.SOUND_EVENT.containsKey(soundId)) {
			return;
		}

		double radius = cfg.vocal.maxVolumeRange;
		long seed = level.getRandom().nextLong();
		DistantVocalPayload payload = new DistantVocalPayload(
				soundId,
				entity.getX(),
				entity.getY(),
				entity.getZ(),
				volume,
				pitch,
				seed
		);
		Vec3 origin = new Vec3(payload.x(), payload.y(), payload.z());

		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			double dist = player.position().distanceTo(origin);
			if (dist > radius) {
				continue;
			}
			ModNetworking.sendVocal(player, payload);
		}
	}
}
