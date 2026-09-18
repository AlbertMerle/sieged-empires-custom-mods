package com.distantnoise.client.sound;

import net.minecraft.util.Mth;

/** Shared distance curves for gun + explosion distant relays. */
public final class RelayAudioMath {
	private RelayAudioMath() {
	}

	public static float relayVolumeAtDistance(
			double dist,
			double near,
			double far,
			float volumeNear,
			float volumeFar
	) {
		if (dist <= near) {
			return volumeNear;
		}
		if (far <= near) {
			return volumeFar;
		}
		double t = smoothstep((dist - near) / (far - near));
		return (float) Mth.lerp(t, volumeNear, volumeFar);
	}

	/** Pitch drops with distance so far sounds feel muffled (gun-style). */
	public static float relayMufflingAtDistance(double dist, double near, double far) {
		if (dist <= near) {
			return 1.0f;
		}
		if (far <= near) {
			return 0.72f;
		}
		double t = smoothstep((dist - near) / (far - near));
		return (float) Mth.lerp(t, 1.0, 0.72);
	}

	public static float relayPitchAtDistance(
			double dist,
			double near,
			double far,
			float pitchNear,
			float pitchFar
	) {
		if (dist <= near) {
			return pitchNear;
		}
		if (far <= near) {
			return pitchFar;
		}
		double t = smoothstep((dist - near) / (far - near));
		return (float) Mth.lerp(t, pitchNear, pitchFar);
	}

	/**
	 * 0 at/below {@code near} (direct blast only), 1 at {@code far} (echo/reverb only).
	 * Crossfades between the custom explosion clip and muffled distant tiers.
	 */
	public static float echoBlend(double dist, double near, double far) {
		if (dist <= near) {
			return 0.0f;
		}
		if (far <= near) {
			return 1.0f;
		}
		return (float) smoothstep((dist - near) / (far - near));
	}

	public static float directBlend(double dist, double near, double far) {
		return 1.0f - echoBlend(dist, near, far);
	}

	public static double smoothstep(double t) {
		t = Mth.clamp(t, 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}
}
