package com.distantnoise.sound;

import com.distantnoise.config.DistantNoiseConfig;
import com.distantnoise.network.DistantVocalPayload;
import com.distantnoise.network.ModNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Extra custom neigh / snort layered on top of vanilla horse vocals (vanilla is not cancelled). */
public final class HorseVocals {
	private HorseVocals() {
	}

	public static void relayExtra(LivingEntity entity, SoundEvent vanilla, float volume, float pitch) {
		if (entity.level().isClientSide() || !(entity.level() instanceof ServerLevel level)) {
			return;
		}

		DistantNoiseConfig cfg = DistantNoiseConfig.get();
		if (!cfg.enabled || !cfg.horseSoundsEnabled) {
			return;
		}
		if (!HorseSounds.isHorse(entity)) {
			return;
		}

		Identifier extraId;
		if (HorseSounds.isNeighSound(vanilla)) {
			extraId = HorseSounds.neighSoundId();
		} else if (HorseSounds.isSnortSound(vanilla)) {
			extraId = HorseSounds.snortSoundId();
		} else {
			return;
		}

		long seed = level.getRandom().nextLong();
		DistantVocalPayload payload = new DistantVocalPayload(
				extraId,
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
			if (dist > cfg.horse.maxVolumeRange) {
				continue;
			}
			ModNetworking.sendVocal(player, payload);
		}
	}
}
