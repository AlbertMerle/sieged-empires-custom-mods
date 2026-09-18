package com.distantnoise.client.sound;

import com.distantnoise.config.DistantNoiseConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Looping grass rustle bound to a moving animal with config-scaled distance falloff. */
public final class GrassRustleSoundInstance extends AbstractTickableSoundInstance {
	private static final float BASE_VOLUME = 0.55f;

	private final Entity entity;
	private final float startOffsetSeconds;
	private final boolean fleeing;

	public GrassRustleSoundInstance(
			SoundEvent sound,
			Entity entity,
			float startOffsetSeconds,
			boolean fleeing
	) {
		super(sound, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
		this.entity = entity;
		this.startOffsetSeconds = startOffsetSeconds;
		this.fleeing = fleeing;
		this.looping = true;
		this.volume = BASE_VOLUME;
		this.pitch = 1.0f;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.relative = false;
		this.x = entity.getX();
		this.y = entity.getY();
		this.z = entity.getZ();
	}

	public float getStartOffsetSeconds() {
		return startOffsetSeconds;
	}

	@Override
	public float getVolume() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return 0.0f;
		}
		DistantNoiseConfig cfg = DistantNoiseConfig.get();
		DistantNoiseConfig.RelayRangeSettings range = fleeing ? cfg.russlingFlee : cfg.russlingWalk;
		double dist = listenerDistance(mc);
		if (dist > range.maxVolumeRange) {
			return 0.0f;
		}
		float distanceMul = volumeAtDistance(
				dist,
				range.fullVolumeCloseupRange,
				range.maxVolumeRange,
				range.volumeNear,
				range.volumeFar
		);
		return BASE_VOLUME * distanceMul;
	}

	@Override
	public void tick() {
		if (entity.isRemoved() || !entity.isAlive()) {
			stop();
			return;
		}
		if (getVolume() <= 0.0f) {
			stop();
			return;
		}
		this.x = entity.getX();
		this.y = entity.getY();
		this.z = entity.getZ();
	}

	private double listenerDistance(Minecraft mc) {
		Vec3 listener = mc.gameRenderer.mainCamera().position();
		return listener.distanceTo(entity.getEyePosition());
	}

	static float volumeAtDistance(
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
}
