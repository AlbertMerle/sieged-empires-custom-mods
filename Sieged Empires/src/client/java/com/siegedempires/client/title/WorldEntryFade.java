package com.siegedempires.client.title;

import com.siegedempires.client.gui.GameMenuScreen;
import com.siegedempires.client.session.SessionJoinCinematic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * After joining a world while the session gate is active: opens the Game Menu
 * under a full-screen black overlay, holds opaque black for 2 seconds (buffer
 * while chunks catch up), then fades opaque → clear over 2 seconds while fading
 * title/menu music out. Ambient world sounds keep playing.
 */
public final class WorldEntryFade {
	private static final long HOLD_BLACK_MS = 2000L;
	private static final long FADE_DURATION_MS = 2000L;

	private static boolean pending;
	private static boolean holding;
	private static boolean fading;
	private static long phaseStartMs = -1L;

	private WorldEntryFade() {
	}

	/** Call when a play session is about to start (connect / join packet). */
	public static void arm() {
		pending = true;
		holding = false;
		fading = false;
		phaseStartMs = -1L;
	}

	public static boolean isActive() {
		return pending || holding || fading;
	}

	/** True while the music gain should be driven by {@link #musicVolume()}. */
	public static boolean isFadingMusic() {
		return fading;
	}

	public static boolean isFading() {
		return fading;
	}

	/**
	 * Black overlay alpha. Holds fully opaque once the world exists and the
	 * session gate is waiting (including the post-ready 2s buffer), then fades
	 * 1 → 0 over the entry reveal.
	 */
	public static float screenBlackAlpha() {
		if (fading) {
			return 1.0F - fadeProgress();
		}
		if (holding) {
			return 1.0F;
		}
		if (pending && SessionJoinCinematic.isGateActive() && shouldHoldBlack(Minecraft.getInstance())) {
			return 1.0F;
		}
		return 0.0F;
	}

	/** MUSIC category gain multiplier from 1 → 0 while the entry fade runs. */
	public static float musicVolume() {
		if (!fading) {
			return 1.0F;
		}
		return 1.0F - fadeProgress();
	}

	public static void tick(Minecraft minecraft) {
		if (pending) {
			if (isReadyToReveal(minecraft)) {
				pending = false;
				holding = true;
				phaseStartMs = Util.getMillis();
				if (!SessionJoinCinematic.isSessionMenuScreen(minecraft.gui.screen())) {
					minecraft.gui.setScreen(new GameMenuScreen());
				}
			}
			return;
		}

		if (holding) {
			if (Util.getMillis() - phaseStartMs >= HOLD_BLACK_MS) {
				holding = false;
				fading = true;
				phaseStartMs = Util.getMillis();
			}
			return;
		}

		if (!fading) {
			return;
		}

		if (fadeProgress() >= 1.0F) {
			finish(minecraft);
		}
	}

	private static boolean isReadyToReveal(Minecraft minecraft) {
		if (!SessionJoinCinematic.isGateActive()) {
			return false;
		}
		if (minecraft.level == null || minecraft.player == null) {
			return false;
		}
		var screen = minecraft.gui.screen();
		if (screen instanceof ConnectScreen
				|| screen instanceof LevelLoadingScreen
				|| screen instanceof ProgressScreen
				|| screen instanceof JoinFadeScreen) {
			return false;
		}
		return minecraft.gui.overlay() == null;
	}

	private static boolean shouldHoldBlack(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null) {
			return false;
		}
		var screen = minecraft.gui.screen();
		return !(screen instanceof ConnectScreen
				|| screen instanceof LevelLoadingScreen
				|| screen instanceof ProgressScreen
				|| screen instanceof JoinFadeScreen);
	}

	private static float fadeProgress() {
		if (phaseStartMs < 0L) {
			return 0.0F;
		}
		return Mth.clamp((float) (Util.getMillis() - phaseStartMs) / (float) FADE_DURATION_MS, 0.0F, 1.0F);
	}

	private static void finish(Minecraft minecraft) {
		fading = false;
		phaseStartMs = -1L;
		minecraft.getMusicManager().stopPlaying();
		minecraft.getSoundManager().updateCategoryVolume(SoundSource.MUSIC, 1.0F);
		if (SessionJoinCinematic.isGateActive()
				&& !SessionJoinCinematic.isSessionMenuScreen(minecraft.gui.screen())) {
			minecraft.gui.setScreen(new GameMenuScreen());
		}
	}

	/** Clears state on disconnect so a later join can arm again. */
	public static void reset() {
		pending = false;
		holding = false;
		fading = false;
		phaseStartMs = -1L;
	}
}
