package com.weaponsmodaddon.client.scope;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Bakes scope sway into the player's real {@code xRot}/{@code yRot} so the spyglass center
 * (camera) and {@code shootFromRotation} share the same angles. View-only sway on
 * {@code getView*Rot} left the bullet on the unsaved look vector.
 */
public final class ScopedMusketSwayApplier {
	private static float appliedYaw;
	private static float appliedPitch;
	private static boolean active;

	private ScopedMusketSwayApplier() {
	}

	public static void tick(LocalPlayer player) {
		if (player == null || !ScopedMusketAimClient.isAimingScoped(player)) {
			clear(player);
			return;
		}

		float yaw = ScopedMusketSway.yawOffset(player, 1.0F);
		float pitch = ScopedMusketSway.pitchOffset(player, 1.0F);

		player.setYRot(player.getYRot() - appliedYaw + yaw);
		player.setXRot(Mth.clamp(player.getXRot() - appliedPitch + pitch, -90.0F, 90.0F));

		appliedYaw = yaw;
		appliedPitch = pitch;
		active = true;
	}

	/** Strip the last sway offsets so leaving ADS does not leave a stuck bias. */
	public static void clear(LocalPlayer player) {
		if (!active) {
			appliedYaw = 0.0F;
			appliedPitch = 0.0F;
			return;
		}
		if (player != null) {
			player.setYRot(player.getYRot() - appliedYaw);
			player.setXRot(Mth.clamp(player.getXRot() - appliedPitch, -90.0F, 90.0F));
		}
		appliedYaw = 0.0F;
		appliedPitch = 0.0F;
		active = false;
	}
}
