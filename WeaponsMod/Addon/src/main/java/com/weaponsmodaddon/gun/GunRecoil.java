package com.weaponsmodaddon.gun;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Horizontal fire knockback — push the shooter away from look direction with no damage
 * and no pitch kick. Distances are WeaponMod-style {@link Player#push} magnitudes (blocks of
 * impulse), not fall-damage knockback.
 */
public final class GunRecoil {
	/** Musket / bayonet / scoped musket. */
	public static final double MUSKET = 0.15;
	/** Blunderbuss. */
	public static final double BLUNDERBUSS = 0.25;
	/** Hand mortar. */
	public static final double MORTAR = 0.25;

	private GunRecoil() {
	}

	/**
	 * Push the player horizontally opposite their look yaw. {@code blocks <= 0} is a no-op
	 * (flintlock). Y is unchanged so this never adds fall damage by itself.
	 * <p>
	 * Sets {@link Player#hurtMarked} so the server sends motion to the shooter (ADS fire runs
	 * server-side only; plain {@link Player#push} syncs trackers via {@code needsSync}, not self).
	 */
	public static void apply(Player player, double blocks) {
		if (blocks <= 0.0) {
			return;
		}
		float yawRad = player.getYRot() * 0.017453292f;
		// Look XZ is (-sin, cos); opposite is (sin, -cos).
		player.push(Mth.sin(yawRad) * blocks, 0.0, -Mth.cos(yawRad) * blocks);
		player.hurtMarked = true;
	}
}
