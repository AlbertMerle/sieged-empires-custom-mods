package com.weaponsmodaddon.gun;

/**
 * While set, musket/flintlock bullets fired via the sticky-ADS path use zero cone spread
 * so the shot matches the aim point (crosshair / spyglass center).
 */
public final class PreciseGunAim {
	private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

	private PreciseGunAim() {
	}

	public static void enter() {
		ACTIVE.set(Boolean.TRUE);
	}

	public static void exit() {
		ACTIVE.set(Boolean.FALSE);
	}

	public static boolean isActive() {
		return Boolean.TRUE.equals(ACTIVE.get());
	}
}
