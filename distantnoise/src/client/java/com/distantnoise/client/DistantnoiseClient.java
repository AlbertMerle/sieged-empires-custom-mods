package com.distantnoise.client;

import com.distantnoise.config.DistantNoiseConfig;
import com.distantnoise.client.sound.ExplosionRelaySounds;
import com.distantnoise.client.sound.GrassRustleSounds;
import com.distantnoise.client.sound.GunEarRingCompat;
import com.distantnoise.network.DistantFootstepPayload;
import com.distantnoise.network.DistantNoisePayload;
import com.distantnoise.network.DistantVocalPayload;
import com.distantnoise.network.GrassRustleStartPayload;
import com.distantnoise.network.GrassRustleStopPayload;
import com.distantnoise.sound.DeathSounds;
import com.distantnoise.sound.FootstepRelayKind;
import com.distantnoise.sound.HorseSounds;
import com.distantnoise.sound.ModSounds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Client: receive distant events and play positional muffled tiers by distance.
 * Sound Physics Remastered (if present) further occludes through terrain.
 */
public class DistantnoiseClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(DistantNoisePayload.TYPE, (payload, context) -> {
			context.client().execute(() -> playDistant(payload));
		});
		ClientPlayNetworking.registerGlobalReceiver(DistantFootstepPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> playFootstep(payload));
		});
		ClientPlayNetworking.registerGlobalReceiver(DistantVocalPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> playVocal(payload));
		});
		ClientPlayNetworking.registerGlobalReceiver(GrassRustleStartPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> GrassRustleSounds.start(
					payload.entityId(),
					payload.startOffsetSeconds(),
					payload.fleeing()
			));
		});
		ClientPlayNetworking.registerGlobalReceiver(GrassRustleStopPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> GrassRustleSounds.stop(payload.entityId()));
		});
	}

	private static void playDistant(DistantNoisePayload payload) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		DistantNoiseConfig cfg = DistantNoiseConfig.get();
		if (!cfg.enabled) {
			return;
		}

		Vec3 origin = new Vec3(payload.x(), payload.y(), payload.z());
		double dist = mc.gameRenderer.mainCamera().position().distanceTo(origin);
		double maxDist = payload.kind().isExplosion()
				? cfg.explosion.maxVolumeRange
				: cfg.radius;
		if (dist > maxDist) {
			return;
		}

		float volume = volumeAtDistance(dist, cfg);
		float pitch = cfg.pitch;
		RandomSource random = RandomSource.create(payload.seed());

		if (payload.kind().isGun()) {
			Identifier gunSound = payload.kind().gunSoundId();
			if (BuiltInRegistries.SOUND_EVENT.get(gunSound).isEmpty()) {
				return;
			}
			DistantNoiseConfig.GunRelaySettings gun = cfg.gun;
			float volumeMul = relayVolumeAtDistance(
					dist,
					gun.fullVolumeRange,
					cfg.radius,
					gun.volumeNear,
					gun.volumeFar
			);
			float mufflingMul = relayMufflingAtDistance(dist, gun.fullVolumeRange, cfg.radius);
			float gunPitch = gun.pitch * mufflingMul * (0.95f + random.nextFloat() * 0.1f);
			playGunRelayPositional(gunSound, payload.x(), payload.y(), payload.z(), volumeMul, gunPitch);
			GunEarRingCompat.triggerNearby(dist, gun.earRingRange);
			return;
		}

		if (payload.kind().isExplosion()) {
			if (BuiltInRegistries.SOUND_EVENT.get(ModSounds.EXPLOSION.value().location()).isEmpty()) {
				return;
			}
			ExplosionRelaySounds.play(
					payload.x(),
					payload.y(),
					payload.z(),
					cfg.explosion,
					payload.seed()
			);
			return;
		}

		int tier = ModSounds.tierForDistance(dist, cfg.nearCutoff, cfg.radius);
		float explodePitch = pitch * (0.95f + random.nextFloat() * 0.1f);
		float explodeVol = payload.kind().isExplosion() ? volume * 1.2f : volume;
		playGunRelayPositional(
				ModSounds.explodeTier(tier).value().location(),
				payload.x(),
				payload.y(),
				payload.z(),
				explodeVol,
				explodePitch
		);
	}

	/** Gun relay: no vanilla distance falloff — volume tier is computed from listener distance. */
	private static void playGunRelayPositional(
			Identifier soundId,
			double x,
			double y,
			double z,
			float volume,
			float pitch
	) {
		Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(
				soundId,
				SoundSource.MASTER,
				volume,
				pitch,
				RandomSource.create(),
				false,
				0,
				SoundInstance.Attenuation.NONE,
				x,
				y,
				z,
				false
		));
	}

	private static void playFootstep(DistantFootstepPayload payload) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		DistantNoiseConfig cfg = DistantNoiseConfig.get();
		if (!cfg.enabled) {
			return;
		}
		FootstepRelayKind kind = FootstepRelayKind.fromId(payload.relayKind());
		boolean caveKind = kind == FootstepRelayKind.CAVE_WALK
				|| kind == FootstepRelayKind.CAVE_SPRINT
				|| kind == FootstepRelayKind.CAVE_FALL;
		if (caveKind) {
			if (!cfg.caveEchoesEnabled) {
				return;
			}
		} else if (kind == FootstepRelayKind.SURFACE_SPRINT) {
			if (!cfg.sprintFootstepsEnabled) {
				return;
			}
		} else if (kind == FootstepRelayKind.PLANT_WALK
				|| kind == FootstepRelayKind.PLANT_SPRINT
				|| kind == FootstepRelayKind.PLANT_SNEAK) {
			if (!cfg.plantGrassWalkEnabled) {
				return;
			}
		} else if (kind == FootstepRelayKind.SNAKE) {
			if (!cfg.snakeMovementEnabled) {
				return;
			}
		} else if (kind == FootstepRelayKind.HORSE_RUN) {
			if (!cfg.horseSoundsEnabled) {
				return;
			}
		} else if (kind == FootstepRelayKind.HORSE_ARMOR) {
			if (!cfg.horseSoundsEnabled) {
				return;
			}
		} else if (!cfg.footstepsEnabled) {
			return;
		}

		Holder.Reference<SoundEvent> sound = BuiltInRegistries.SOUND_EVENT.get(payload.soundId()).orElse(null);
		if (sound == null) {
			return;
		}

		Vec3 origin = new Vec3(payload.x(), payload.y(), payload.z());
		double dist = mc.gameRenderer.mainCamera().position().distanceTo(origin);
		DistantNoiseConfig.RelayRangeSettings range = rangeForFootstep(kind, cfg);
		double maxRange = payload.maxRange() > 0.0f ? payload.maxRange() : range.maxVolumeRange;
		if (dist > maxRange) {
			return;
		}

		double closeupRange = Math.min(range.fullVolumeCloseupRange, Math.max(0.0, maxRange - 1.0));
		float distanceMul = relayVolumeAtDistance(
				dist,
				closeupRange,
				maxRange,
				range.volumeNear,
				range.volumeFar
		);
		float mufflingMul = relayMufflingAtDistance(dist, closeupRange, maxRange);
		RandomSource random = RandomSource.create(payload.seed());
		float pitch = range.pitch * payload.pitch() * mufflingMul * (0.94f + random.nextFloat() * 0.12f);
		float volume = payload.baseVolume() * distanceMul;
		playRelayPositional(sound.value().location(), payload.x(), payload.y(), payload.z(), volume, pitch, SoundSource.NEUTRAL);
	}

	private static DistantNoiseConfig.RelayRangeSettings rangeForFootstep(
			FootstepRelayKind kind,
			DistantNoiseConfig cfg
	) {
		return switch (kind) {
			case RUNNING -> cfg.running;
			case CAVE_WALK -> cfg.caveWalk;
			case CAVE_SPRINT -> cfg.caveSprint;
			case CAVE_FALL -> cfg.caveFall;
			case SURFACE_SPRINT -> cfg.sprint;
			case PLANT_WALK -> cfg.plantWalk;
			case PLANT_SPRINT -> cfg.plantSprint;
			case PLANT_SNEAK -> cfg.plantSneak;
			case SNAKE -> cfg.snake;
			case HORSE_RUN -> cfg.horse;
			case HORSE_ARMOR -> cfg.horseArmor;
			case STOMP -> cfg.stomp;
		};
	}

	private static void playVocal(DistantVocalPayload payload) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		DistantNoiseConfig cfg = DistantNoiseConfig.get();
		if (!cfg.enabled) {
			return;
		}

		boolean isDeath = DeathSounds.DEATH_ID.equals(payload.soundId());
		boolean isHorseNeigh = HorseSounds.neighSoundId().equals(payload.soundId());
		boolean isHorseSnort = HorseSounds.snortSoundId().equals(payload.soundId());
		if (isDeath) {
			if (!cfg.deathSoundsEnabled) {
				return;
			}
		} else if (isHorseNeigh || isHorseSnort) {
			if (!cfg.horseSoundsEnabled) {
				return;
			}
		} else if (!cfg.vocalAnimals) {
			return;
		}

		Holder.Reference<SoundEvent> sound = BuiltInRegistries.SOUND_EVENT.get(payload.soundId()).orElse(null);
		if (sound == null) {
			return;
		}

		DistantNoiseConfig.RelayRangeSettings range = isDeath
				? cfg.death
				: (isHorseNeigh || isHorseSnort) ? cfg.horse : cfg.vocal;
		Vec3 origin = new Vec3(payload.x(), payload.y(), payload.z());
		double dist = mc.gameRenderer.mainCamera().position().distanceTo(origin);
		if (dist > range.maxVolumeRange) {
			return;
		}

		float distanceMul = relayVolumeAtDistance(
				dist,
				range.fullVolumeCloseupRange,
				range.maxVolumeRange,
				range.volumeNear,
				range.volumeFar
		);
		float mufflingMul = relayMufflingAtDistance(dist, range.fullVolumeCloseupRange, range.maxVolumeRange);
		RandomSource random = RandomSource.create(payload.seed());
		float pitch = range.pitch * payload.pitch() * mufflingMul * (0.94f + random.nextFloat() * 0.12f);
		float volume = payload.baseVolume() * distanceMul;
		SoundSource source = isDeath ? SoundSource.HOSTILE : SoundSource.NEUTRAL;
		playRelayPositional(sound.value().location(), payload.x(), payload.y(), payload.z(), volume, pitch, source);
	}

	private static float relayVolumeAtDistance(
			double dist,
			double near,
			double far,
			float volumeNear,
			float volumeFar
	) {
		if (dist <= near) {
			return volumeNear;
		}
		if (far <= near) {
			return volumeFar;
		}
		double t = (dist - near) / (far - near);
		t = Mth.clamp(t, 0.0, 1.0);
		t = t * t * (3.0 - 2.0 * t);
		return (float) Mth.lerp(t, volumeNear, volumeFar);
	}

	/** Pitch drops with distance so far sounds feel muffled. */
	private static float relayMufflingAtDistance(double dist, double near, double far) {
		if (dist <= near) {
			return 1.0f;
		}
		if (far <= near) {
			return 0.72f;
		}
		double t = (dist - near) / (far - near);
		t = Mth.clamp(t, 0.0, 1.0);
		t = t * t * (3.0 - 2.0 * t);
		return (float) Mth.lerp(t, 1.0, 0.72);
	}

	private static float volumeAtDistance(double dist, DistantNoiseConfig cfg) {
		double near = cfg.nearCutoff;
		double far = cfg.radius;
		if (far <= near) {
			return cfg.volumeFar;
		}
		double t = (dist - near) / (far - near);
		t = Mth.clamp(t, 0.0, 1.0);
		// Smoothstep ease: louder near cutoff, quieter at rim.
		t = t * t * (3.0 - 2.0 * t);
		return (float) Mth.lerp(t, cfg.volumeNear, cfg.volumeFar);
	}

	private static void playPositional(
			Holder<SoundEvent> sound,
			double x,
			double y,
			double z,
			float volume,
			float pitch
	) {
		playPositional(sound, x, y, z, volume, pitch, SoundSource.PLAYERS);
	}

	private static void playPositional(
			Holder<SoundEvent> sound,
			double x,
			double y,
			double z,
			float volume,
			float pitch,
			SoundSource source
	) {
		Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(
				sound.value(),
				source,
				volume,
				pitch,
				RandomSource.create(),
				x,
				y,
				z
		));
	}

	/** Positional playback with no vanilla distance attenuation — relay code scales volume. */
	private static void playRelayPositional(
			Identifier soundId,
			double x,
			double y,
			double z,
			float volume,
			float pitch,
			SoundSource source
	) {
		Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(
				soundId,
				source,
				volume,
				pitch,
				RandomSource.create(),
				false,
				0,
				SoundInstance.Attenuation.NONE,
				x,
				y,
				z,
				false
		));
	}
}
