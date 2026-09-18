package com.siegedempires.client.session;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Relative, non-attenuated Join cinematic ambient with a live volume envelope.
 * Uses {@link SoundSource#UI} so world-category muting does not silence it.
 */
public final class JoinAmbientSoundInstance extends AbstractTickableSoundInstance {
	private float envelope = 0.0F;

	public JoinAmbientSoundInstance(SoundEvent event, RandomSource random) {
		super(event, SoundSource.UI, random);
		this.looping = true;
		this.delay = 0;
		this.volume = 0.0F;
		this.pitch = 1.0F;
		this.relative = true;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.x = 0.0;
		this.y = 0.0;
		this.z = 0.0;
	}

	public void setEnvelope(float envelope) {
		this.envelope = Mth.clamp(envelope, 0.0F, 1.0F);
		this.volume = this.envelope;
	}

	public float envelope() {
		return this.envelope;
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
		this.volume = this.envelope;
	}

	public void finish() {
		stop();
	}
}
