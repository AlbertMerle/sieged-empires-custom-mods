package com.siegedmusic.client.music;

import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;

/**
 * Optional integration with Sieged Empires {@code WorldEntryFade} (menu music fade on world join).
 * Uses reflection so sieged-music does not hard-depend on siegedempires.
 */
public final class WorldEntryFadeHelper {
	private static final String CLASS_NAME = "com.siegedempires.client.title.WorldEntryFade";

	private static @Nullable Method isFadingMusic;
	private static @Nullable Method isActive;
	private static boolean unavailable;

	private WorldEntryFadeHelper() {
	}

	public static boolean isAvailable() {
		resolve();
		return !unavailable;
	}

	public static boolean isFadingMusic() {
		Method method = resolve();
		if (method == null) {
			return false;
		}
		try {
			return (boolean) method.invoke(null);
		} catch (ReflectiveOperationException ignored) {
			return false;
		}
	}

	public static boolean isActive() {
		Method method = resolveIsActive();
		if (method == null) {
			return false;
		}
		try {
			return (boolean) method.invoke(null);
		} catch (ReflectiveOperationException ignored) {
			return false;
		}
	}

	private static @Nullable Method resolve() {
		if (unavailable) {
			return null;
		}
		if (isFadingMusic == null) {
			try {
				Class<?> clazz = Class.forName(CLASS_NAME);
				isFadingMusic = clazz.getMethod("isFadingMusic");
			} catch (ReflectiveOperationException e) {
				unavailable = true;
				return null;
			}
		}
		return isFadingMusic;
	}

	private static @Nullable Method resolveIsActive() {
		if (unavailable) {
			return null;
		}
		if (isActive == null) {
			try {
				Class<?> clazz = Class.forName(CLASS_NAME);
				isActive = clazz.getMethod("isActive");
			} catch (ReflectiveOperationException e) {
				unavailable = true;
				return null;
			}
		}
		return isActive;
	}
}
