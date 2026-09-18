package com.distantnoise.client.sound;

import com.distantnoise.Distantnoise;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Soft-dep bridge to WeaponsMod Addon {@code GunEarRingClient} for nearby listeners.
 */
public final class GunEarRingCompat {
	private GunEarRingCompat() {
	}

	public static void triggerNearby(double dist, double earRingRange) {
		if (dist > earRingRange) {
			return;
		}
		if (!FabricLoader.getInstance().isModLoaded("weaponsmodaddon")) {
			return;
		}
		try {
			Class<?> clazz = Class.forName("com.weaponsmodaddon.client.sound.GunEarRingClient");
			clazz.getMethod("trigger").invoke(null);
		} catch (ReflectiveOperationException e) {
			Distantnoise.LOGGER.debug("Gun ear-ring trigger skipped", e);
		}
	}
}
