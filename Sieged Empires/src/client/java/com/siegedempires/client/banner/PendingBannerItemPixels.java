package com.siegedempires.client.banner;

import org.jspecify.annotations.Nullable;

/**
 * Bridges {@code BannerSpecialRenderer#extractArgument} →
 * {@code ItemStackRenderState.LayerRenderState#setupSpecialModel} on the same
 * call stack so inventory/item special models can carry freeform banner paint.
 */
public final class PendingBannerItemPixels {
	private static final ThreadLocal<String> PENDING = new ThreadLocal<>();

	private PendingBannerItemPixels() {
	}

	public static void set(@Nullable String pixels) {
		if (pixels == null || pixels.isEmpty()) {
			PENDING.remove();
		} else {
			PENDING.set(pixels);
		}
	}

	public static @Nullable String take() {
		String pixels = PENDING.get();
		PENDING.remove();
		return pixels;
	}

	public static void clear() {
		PENDING.remove();
	}
}
