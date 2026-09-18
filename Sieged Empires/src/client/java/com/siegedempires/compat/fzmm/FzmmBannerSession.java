package com.siegedempires.compat.fzmm;

import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * Client-only handshake between Create Town/Empire screens and FZMM's
 * {@code BannerEditorScreen}. While a session is active, the FZMM mixin
 * replaces Item:Banner/Shield with Finished! (save-and-return) and removes Give.
 */
public final class FzmmBannerSession {
	private static ItemStack initialBanner = ItemStack.EMPTY;
	private static Consumer<ItemStack> onSaved;
	private static boolean active;

	private FzmmBannerSession() {}

	public static void begin(ItemStack initial, Consumer<ItemStack> onSavedCallback) {
		initialBanner = initial == null || initial.isEmpty() ? ItemStack.EMPTY : initial.copy();
		onSaved = onSavedCallback;
		active = true;
	}

	public static boolean isActive() {
		return active;
	}

	/** Initial design to seed FZMM with; empty means white banner. */
	public static ItemStack initialBanner() {
		return initialBanner;
	}

	/**
	 * Apply the edited banner to the SE create screen, then clear the session.
	 * Call this before {@code onClose()} so the parent re-inits with the new design.
	 */
	public static void finish(ItemStack result) {
		Consumer<ItemStack> callback = onSaved;
		clear();
		if (callback != null && result != null && !result.isEmpty()) {
			callback.accept(result.copy());
		}
	}

	/** Discard an open session (Back / Escape without Save). */
	public static void cancel() {
		clear();
	}

	private static void clear() {
		active = false;
		onSaved = null;
		initialBanner = ItemStack.EMPTY;
	}
}
