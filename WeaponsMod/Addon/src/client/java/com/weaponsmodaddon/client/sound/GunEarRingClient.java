package com.weaponsmodaddon.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * After the local player fires a gun: instantly duck other categories so the bang
 * dominates, wait 500ms, then fade in tinnitus (1s), hold (4s), fade out (2s), while
 * keeping the world muffled (category gain duck + OpenAL low-pass when available).
 */
public final class GunEarRingClient {
	/** Delay so BANG lands before ringing; world is already ducked during this window. */
	private static final int START_DELAY_TICKS = 10; // 500ms
	private static final int FADE_IN_TICKS = 20;   // 1s
	private static final int HOLD_TICKS = 80;      // 4s
	private static final int FADE_OUT_TICKS = 40;  // 2s
	private static final int TOTAL_TICKS = FADE_IN_TICKS + HOLD_TICKS + FADE_OUT_TICKS; // 140

	/**
	 * World category gain at full muffling (MASTER left alone for bang + ring).
	 * Harder than prior 0.55 so the shot punches through the mix.
	 */
	private static final float MIN_CATEGORY_GAIN = 0.18f;

	/** Peak ring loudness (25% of original full-volume tinnitus; half of prior 0.5 peak). */
	public static final float RING_MAX_VOLUME = 0.25f;

	/** Countdown before envelope starts; −1 = idle/active handled by {@link #ticks}. */
	private static int delayTicks = -1;
	private static int ticks = -1;
	private static EarRingSoundInstance sound;
	private static float lastAppliedCategoryGain = 1.0f;
	private static boolean categoryDucked;

	private GunEarRingClient() {
	}

	/** Schedule the ear-ring effect (local shooter only); bang should already have played. */
	public static void trigger() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		// Restart: stop any active ring, wait 500ms, then bang→ring again.
		if (sound != null && !sound.isStopped()) {
			client.getSoundManager().stop(sound);
			sound = null;
		}
		if (categoryDucked) {
			restoreWorldMuffle(client);
		}
		GunEarRingLowpass.setEffectAmount(0.0f);
		ticks = -1;
		delayTicks = START_DELAY_TICKS;
		// Instant duck — bang is MASTER so it stays full while everything else drops.
		applyWorldMuffle(client, 1.0f);
	}

	public static void tick(Minecraft client) {
		if (delayTicks >= 0) {
			if (client.player == null) {
				clear(client);
				return;
			}
			// Keep full shock-duck through the bang window before ringing starts.
			applyWorldMuffle(client, 1.0f);
			delayTicks--;
			if (delayTicks < 0) {
				startRing(client);
			}
			return;
		}
		if (ticks < 0) {
			return;
		}
		if (client.player == null) {
			clear(client);
			return;
		}

		ticks++;
		float amount = effectAmount();
		applyWorldMuffle(client, amount);

		if (ticks >= TOTAL_TICKS) {
			finish(client);
		}
	}

	private static void startRing(Minecraft client) {
		ticks = 0;
		SoundManager sounds = client.getSoundManager();
		sound = new EarRingSoundInstance();
		// Volume starts at 0 for fade-in; EarRingSoundInstance.canStartSilent() is required
		// or SoundEngine skips the clip entirely.
		sounds.play(sound);
		applyWorldMuffle(client, effectAmount());
	}

	/** 0..1 envelope (fade in / hold / fade out). 0 while still in start delay. */
	public static float effectAmount() {
		if (delayTicks >= 0 || ticks < 0) {
			return 0.0f;
		}
		if (ticks < FADE_IN_TICKS) {
			return ticks / (float) FADE_IN_TICKS;
		}
		int afterIn = ticks - FADE_IN_TICKS;
		if (afterIn < HOLD_TICKS) {
			return 1.0f;
		}
		int afterHold = afterIn - HOLD_TICKS;
		if (afterHold >= FADE_OUT_TICKS) {
			return 0.0f;
		}
		return 1.0f - afterHold / (float) FADE_OUT_TICKS;
	}

	public static boolean isFinished() {
		return delayTicks < 0 && (ticks < 0 || ticks >= TOTAL_TICKS);
	}

	public static void clear(Minecraft client) {
		if (delayTicks < 0 && ticks < 0 && !categoryDucked && sound == null) {
			return;
		}
		finish(client);
	}

	private static void finish(Minecraft client) {
		delayTicks = -1;
		ticks = -1;
		if (sound != null) {
			SoundManager sounds = client.getSoundManager();
			if (!sound.isStopped()) {
				sounds.stop(sound);
			}
			sound = null;
		}
		restoreWorldMuffle(client);
		GunEarRingLowpass.setEffectAmount(0.0f);
	}

	private static void applyWorldMuffle(Minecraft client, float amount) {
		float gain = Mth.lerp(Mth.clamp(amount, 0.0f, 1.0f), 1.0f, MIN_CATEGORY_GAIN);
		if (Math.abs(gain - lastAppliedCategoryGain) > 0.002f || (amount > 0.0f && !categoryDucked)) {
			SoundManager sounds = client.getSoundManager();
			for (SoundSource source : SoundSource.values()) {
				if (source == SoundSource.MASTER) {
					continue;
				}
				sounds.updateCategoryVolume(source, gain);
			}
			lastAppliedCategoryGain = gain;
			categoryDucked = gain < 0.999f;
		}
		GunEarRingLowpass.setEffectAmount(amount);
	}

	private static void restoreWorldMuffle(Minecraft client) {
		if (!categoryDucked && lastAppliedCategoryGain >= 0.999f) {
			GunEarRingLowpass.clearAllFilters();
			return;
		}
		SoundManager sounds = client.getSoundManager();
		for (SoundSource source : SoundSource.values()) {
			if (source == SoundSource.MASTER) {
				continue;
			}
			sounds.updateCategoryVolume(source, 1.0f);
		}
		lastAppliedCategoryGain = 1.0f;
		categoryDucked = false;
		GunEarRingLowpass.clearAllFilters();
	}
}
