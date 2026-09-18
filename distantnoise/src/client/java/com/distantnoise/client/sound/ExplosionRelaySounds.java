package com.distantnoise.client.sound;

import com.distantnoise.config.DistantNoiseConfig;
import com.distantnoise.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Starts direct + echo layers for a distant explosion relay (gun-style volume curve, echo crossfade at range).
 */
public final class ExplosionRelaySounds {
	private ExplosionRelaySounds() {
	}

	public static void play(
			double x,
			double y,
			double z,
			DistantNoiseConfig.ExplosionRelaySettings settings,
			long seed
	) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		double dist = mc.gameRenderer.mainCamera().position().distanceTo(new Vec3(x, y, z));
		GunEarRingCompat.triggerNearby(dist, settings.earRingRange);
		mc.getSoundManager().play(new DirectLayer(
				ModSounds.EXPLOSION.value(),
				x,
				y,
				z,
				settings,
				seed
		));
		mc.getSoundManager().play(new EchoLayer(
				x,
				y,
				z,
				settings,
				seed,
				dist
		));
	}

	/** Custom {@code distantnoise:explosion} — loud transient; fades out with distance. */
	private static final class DirectLayer extends AbstractTickableSoundInstance {
		private static final int MAX_TICKS = 200;

		private final Vec3 origin;
		private final DistantNoiseConfig.ExplosionRelaySettings settings;
		private final float pitchJitter;
		private int age;

		DirectLayer(
				SoundEvent sound,
				double x,
				double y,
				double z,
				DistantNoiseConfig.ExplosionRelaySettings settings,
				long seed
		) {
			super(sound, SoundSource.MASTER, SoundInstance.createUnseededRandom());
			this.origin = new Vec3(x, y, z);
			this.settings = settings;
			this.pitchJitter = 0.95f + RandomSource.create(seed).nextFloat() * 0.1f;
			this.looping = false;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.relative = false;
			this.x = x;
			this.y = y;
			this.z = z;
		}

		@Override
		public float getVolume() {
			double dist = listenerDistance();
			double maxRange = settings.maxVolumeRange;
			if (dist > maxRange) {
				return 0.0f;
			}
			float direct = RelayAudioMath.directBlend(dist, settings.fullVolumeRange, maxRange);
			if (direct <= 0.001f) {
				return 0.0f;
			}
			float volumeMul = RelayAudioMath.relayVolumeAtDistance(
					dist,
					settings.fullVolumeRange,
					maxRange,
					settings.volumeNear,
					settings.volumeFar
			);
			float vol = volumeMul * direct;
			if (dist <= settings.fullVolumeRange) {
				return vol * ExplosionBangBoost.GAIN_MULT;
			}
			return vol;
		}

		@Override
		public float getPitch() {
			double dist = listenerDistance();
			if (dist > settings.maxVolumeRange) {
				return 1.0f;
			}
			float muffling = RelayAudioMath.relayMufflingAtDistance(
					dist,
					settings.fullVolumeRange,
					settings.maxVolumeRange
			);
			return settings.pitch * muffling * pitchJitter;
		}

		@Override
		public void tick() {
			age++;
			if (age > MAX_TICKS || getVolume() <= 0.0f) {
				stop();
			}
		}

		private double listenerDistance() {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) {
				return settings.maxVolumeRange + 1.0;
			}
			return mc.gameRenderer.mainCamera().position().distanceTo(origin);
		}
	}

	/** Low-pass muffled explode tiers — reverb tail; dominates at long range. */
	private static final class EchoLayer extends AbstractTickableSoundInstance {
		private static final int MAX_TICKS = 260;
		/** Slight lift so distant echo remains audible at the rim. */
		private static final float ECHO_GAIN = 1.15f;

		private final Vec3 origin;
		private final DistantNoiseConfig.ExplosionRelaySettings settings;
		private final float pitchJitter;
		private int age;

		EchoLayer(
				double x,
				double y,
				double z,
				DistantNoiseConfig.ExplosionRelaySettings settings,
				long seed,
				double initialDist
		) {
			super(
					ModSounds.explodeTier(ModSounds.tierForDistance(
							initialDist,
							settings.fullVolumeRange,
							settings.maxVolumeRange
					)).value(),
					SoundSource.MASTER,
					SoundInstance.createUnseededRandom()
			);
			this.origin = new Vec3(x, y, z);
			this.settings = settings;
			this.pitchJitter = 0.92f + RandomSource.create(seed ^ 0x5DEECE66DL).nextFloat() * 0.12f;
			this.looping = false;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.relative = false;
			this.x = x;
			this.y = y;
			this.z = z;
		}

		@Override
		public float getVolume() {
			double dist = listenerDistance();
			double maxRange = settings.maxVolumeRange;
			if (dist > maxRange) {
				return 0.0f;
			}
			float echo = RelayAudioMath.echoBlend(dist, settings.fullVolumeRange, maxRange);
			if (echo <= 0.001f) {
				return 0.0f;
			}
			float volumeMul = RelayAudioMath.relayVolumeAtDistance(
					dist,
					settings.fullVolumeRange,
					maxRange,
					settings.volumeNear,
					settings.volumeFar
			);
			return volumeMul * echo * ECHO_GAIN;
		}

		@Override
		public float getPitch() {
			double dist = listenerDistance();
			if (dist > settings.maxVolumeRange) {
				return 1.0f;
			}
			float pitchMul = RelayAudioMath.relayPitchAtDistance(
					dist,
					settings.fullVolumeRange,
					settings.maxVolumeRange,
					settings.pitch,
					settings.pitchFar
			);
			float muffling = RelayAudioMath.relayMufflingAtDistance(
					dist,
					settings.fullVolumeRange,
					settings.maxVolumeRange
			);
			return pitchMul * muffling * pitchJitter;
		}

		@Override
		public void tick() {
			age++;
			if (age > MAX_TICKS || getVolume() <= 0.0f) {
				stop();
			}
		}

		private double listenerDistance() {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) {
				return settings.maxVolumeRange + 1.0;
			}
			return mc.gameRenderer.mainCamera().position().distanceTo(origin);
		}
	}
}
