package com.weaponsmodaddon.client.sound;

import com.weaponsmodaddon.sound.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * Relative MASTER tinnitus clip; volume driven by {@link GunEarRingClient} envelope.
 * Must {@link #canStartSilent()} so SoundEngine accepts the 0-volume fade-in start.
 */
public final class EarRingSoundInstance extends AbstractTickableSoundInstance {
	public EarRingSoundInstance() {
		super(ModSounds.EAR_RING, SoundSource.MASTER, SoundInstance.createUnseededRandom());
		this.looping = true;
		this.delay = 0;
		this.volume = 0.0f;
		this.pitch = 1.0f;
		this.x = 0.0;
		this.y = 0.0;
		this.z = 0.0;
		this.relative = true;
		this.attenuation = SoundInstance.Attenuation.NONE;
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
		float amount = GunEarRingClient.effectAmount();
		if (amount <= 0.0f && GunEarRingClient.isFinished()) {
			stop();
			return;
		}
		this.volume = Mth.clamp(amount, 0.0f, 1.0f) * GunEarRingClient.RING_MAX_VOLUME;
	}
}
