package com.distantnoise.sound;

import com.distantnoise.config.DistantNoiseConfig;
import com.distantnoise.network.DistantVocalPayload;
import com.distantnoise.network.ModNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Server-side relay for mob death sounds at the dying entity's position. */
public final class DeathNoiseRelay {
	private DeathNoiseRelay() {
	}

	public static void relay(LivingEntity entity, float volume, float pitch) {
		if (entity.level().isClientSide() || !(entity.level() instanceof ServerLevel level)) {
			return;
		}

		DistantNoiseConfig cfg = DistantNoiseConfig.get();
		if (!DeathSounds.enabled(cfg)) {
			return;
		}

		double radius = cfg.death.maxVolumeRange;
		long seed = level.getRandom().nextLong();
		DistantVocalPayload payload = new DistantVocalPayload(
				DeathSounds.deathSoundId(),
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
