package com.distantnoise.client.sound;

import com.distantnoise.Distantnoise;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;

/**
 * Close-range explosion relay needs OpenAL gain &gt; 1 — same approach as WeaponsMod Addon gun bangs.
 */
public final class ExplosionBangBoost {
	/** Multiplier after MC's 0..1 clamp (local + remote explosion relay). */
	public static final float GAIN_MULT = 2.75f;
	/** OpenAL Soft source max gain (must be ≥ applied AL_GAIN). */
	public static final float MAX_GAIN = 4.0f;

	private static final Identifier EXPLOSION = Distantnoise.id("explosion");

	private static final Set<Integer> BOOSTED_SOURCES = ConcurrentHashMap.newKeySet();

	private ExplosionBangBoost() {
	}

	public static boolean isExplosionBang(SoundInstance instance) {
		if (instance == null) {
			return false;
		}
		Identifier event = instance.getIdentifier();
		if (EXPLOSION.equals(event)) {
			return true;
		}
		Sound sound = instance.getSound();
		if (sound == null) {
			return false;
		}
		return EXPLOSION.equals(sound.getLocation());
	}

	public static float boostedGain(float clampedVolume) {
		return Math.min(clampedVolume * GAIN_MULT, MAX_GAIN);
	}

	public static void markSource(int source) {
		BOOSTED_SOURCES.add(source);
	}

	public static boolean isBoostedSource(int source) {
		return BOOSTED_SOURCES.contains(source);
	}

	public static void forgetSource(int source) {
		BOOSTED_SOURCES.remove(source);
	}
}
