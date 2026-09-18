package com.weaponsmodaddon.client.camera;

import com.weaponsmodaddon.client.scope.ScopedMusketAimClient;
import com.weaponsmodaddon.gun.GunAimState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Shoulder-surfing aim camera: sit just above the right shoulder (no FOV zoom here).
 * Vanilla / WeaponMod bow FOV handles first-person aim zoom separately.
 */
public final class WeaponAimShoulderCamera {
	/**
	 * SS offset space: negative X = over right shoulder, +Y up, +Z back from player.
	 * Back ~0.5 from the prior 0.85 shoulder sit; Y nudged down slightly.
	 */
	public static final Vec3 AIM_OFFSET = new Vec3(-0.4, 0.37, 1.35);

	private WeaponAimShoulderCamera() {
	}

	public static boolean shouldUseAimOffset(LivingEntity entity) {
		if (entity == null) {
			return false;
		}
		if (entity instanceof LocalPlayer local && ScopedMusketAimClient.isAimingScoped(local)) {
			return false;
		}
		return GunAimState.shouldLockBodyToLook(entity);
	}
}
