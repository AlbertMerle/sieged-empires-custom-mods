package com.weaponsmodaddon.spear;

import net.minecraft.util.Mth;

/**
 * Shared throw-clip timing so server projectile spawn matches client {@code spear_throw}
 * playback (5.0× real-time).
 */
public final class SpearThrowTiming {
	/** Matches {@code player_spear_anims.json} {@code spear_throw.duration} (~2s). */
	public static final float THROW_CLIP_SECONDS = 1.95833F;
	/** Twice the old Blockbench 250 (2.5×) → 5.0× real-time. */
	public static final float THROW_PLAYBACK_SPEED = 5.0F;
	/**
	 * Fraction through the throw clip (original Blockbench timing) when the spear leaves the hand
	 * and the projectile spawns — halfway ≈ 1s into a ~2s clip.
	 */
	public static final float THROW_RELEASE_FRACTION = 0.5F;

	private SpearThrowTiming() {
	}

	/**
	 * Ticks from release until the spear is removed from the hand and thrown
	 * ({@code clipSeconds * fraction / playbackSpeed * 20}).
	 */
	public static int releaseDelayTicks() {
		return Math.max(
				1,
				Mth.ceil(THROW_CLIP_SECONDS * THROW_RELEASE_FRACTION / THROW_PLAYBACK_SPEED * 20.0F));
	}
}
