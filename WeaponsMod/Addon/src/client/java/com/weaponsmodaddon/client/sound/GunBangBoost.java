package com.weaponsmodaddon.client.sound;

import com.weaponsmodaddon.WeaponsModAddon;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;

/**
 * Gun bangs need true OpenAL gain &gt; 1 — Minecraft clamps {@code SoundEngine} volume to 1.0.
 * Mark bang sources and multiply gain; raise {@code AL_MAX_GAIN} so Soft does not re-clamp.
 */
public final class GunBangBoost {
	/** Multiplier after MC’s 0..1 clamp (local + remote gunshots). */
	public static final float GAIN_MULT = 2.75f;
	/** OpenAL Soft source max gain (must be ≥ applied AL_GAIN). */
	public static final float MAX_GAIN = 4.0f;

	private static final Identifier MUSKET = WeaponsModAddon.id("musket_fire");
	private static final Identifier FLINTLOCK = WeaponsModAddon.id("flintlock_fire");

	private static final Set<Integer> BANG_SOURCES = ConcurrentHashMap.newKeySet();

	private GunBangBoost() {
	}

	public static boolean isGunBang(SoundInstance instance) {
		if (instance == null) {
			return false;
		}
		Identifier event = instance.getIdentifier();
		if (MUSKET.equals(event) || FLINTLOCK.equals(event)) {
			return true;
		}
		// Fallback: resolved file entry (same path for our clips).
		Sound sound = instance.getSound();
		if (sound == null) {
			return false;
		}
		Identifier loc = sound.getLocation();
		return MUSKET.equals(loc) || FLINTLOCK.equals(loc);
	}

	/** Effective OpenAL gain for a bang (category volume already baked into {@code clamped}). */
	public static float boostedGain(float clampedVolume) {
		return Math.min(clampedVolume * GAIN_MULT, MAX_GAIN);
	}

	public static void markSource(int source) {
		BANG_SOURCES.add(source);
	}

	public static boolean isBangSource(int source) {
		return BANG_SOURCES.contains(source);
	}

	public static void forgetSource(int source) {
		BANG_SOURCES.remove(source);
	}
}
