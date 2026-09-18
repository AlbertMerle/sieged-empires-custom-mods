package com.siegedmusic.client.music;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Cross-fades menu → game music and ambient ↔ cave ↔ war music.
 * Menu fade-out is handled by Sieged Empires {@code WorldEntryFade}; this controller
 * waits for that to finish, then fades game music in over 8 seconds.
 */
public final class MusicFadeController {
	public static final long FADE_IN_WORLD_MS = 8000L;
	public static final long FADE_OUT_TRANSITION_MS = 3000L;
	public static final long FADE_IN_TRANSITION_MS = 4000L;

	private enum State {
		IDLE,
		AWAITING_MENU_FADE,
		FADING_OUT,
		FADING_IN
	}

	private static State state = State.IDLE;
	private static long fadeStartMs = -1L;
	private static long fadeDurationMs;
	private static long fadeInDurationMs = FADE_IN_WORLD_MS;
	private static boolean pendingModeApply;

	private MusicFadeController() {
	}

	public static void armWorldEntry() {
		reset();
		state = State.AWAITING_MENU_FADE;
	}

	public static void reset() {
		state = State.IDLE;
		fadeStartMs = -1L;
		fadeDurationMs = 0L;
		fadeInDurationMs = FADE_IN_WORLD_MS;
		pendingModeApply = false;
	}

	public static boolean canStartModeTransition() {
		return state == State.IDLE;
	}

	public static boolean isIdle() {
		return state == State.IDLE;
	}

	public static boolean shouldOverrideMusicTick() {
		return state != State.IDLE;
	}

	public static void requestModeTransition() {
		if (!canStartModeTransition()) {
			return;
		}
		pendingModeApply = true;
		fadeInDurationMs = FADE_IN_TRANSITION_MS;
		beginFadeOut(FADE_OUT_TRANSITION_MS);
	}

	public static void tick(Minecraft minecraft, MusicManager musicManager, @Nullable SoundInstance currentMusic) {
		if (minecraft.player == null || minecraft.level == null) {
			return;
		}

		switch (state) {
			case AWAITING_MENU_FADE -> tickAwaitingMenuFade(minecraft, musicManager);
			case FADING_OUT -> tickFadeOut(minecraft, musicManager, currentMusic);
			case FADING_IN -> tickFadeIn(minecraft);
			case IDLE -> {
			}
		}
	}

	private static void tickAwaitingMenuFade(Minecraft minecraft, MusicManager musicManager) {
		if (WorldEntryFadeHelper.isAvailable()
				&& (WorldEntryFadeHelper.isFadingMusic() || WorldEntryFadeHelper.isActive())) {
			return;
		}
		GameMusicPlaylist.applyDesiredImmediately(minecraft);
		beginFadeIn(minecraft, musicManager, FADE_IN_WORLD_MS);
	}

	private static void tickFadeOut(
			Minecraft minecraft,
			MusicManager musicManager,
			@Nullable SoundInstance currentMusic
	) {
		if (currentMusic == null || !minecraft.getSoundManager().isActive(currentMusic)) {
			finishFadeOut(minecraft, musicManager);
			return;
		}

		float volume = 1.0F - fadeProgress();
		applyMusicVolume(minecraft, volume);
		if (fadeProgress() >= 1.0F) {
			finishFadeOut(minecraft, musicManager);
		}
	}

	private static void finishFadeOut(Minecraft minecraft, MusicManager musicManager) {
		musicManager.stopPlaying();
		applyMusicVolume(minecraft, 0.0F);
		if (pendingModeApply) {
			GameMusicPlaylist.applyPendingMode();
			pendingModeApply = false;
		}
		beginFadeIn(minecraft, musicManager, fadeInDurationMs);
	}

	private static void beginFadeOut(long durationMs) {
		state = State.FADING_OUT;
		fadeStartMs = Util.getMillis();
		fadeDurationMs = durationMs;
	}

	private static void beginFadeIn(Minecraft minecraft, MusicManager musicManager, long durationMs) {
		state = State.FADING_IN;
		fadeStartMs = Util.getMillis();
		fadeDurationMs = durationMs;
		applyMusicVolume(minecraft, 0.0F);

		Music track = GameMusicPlaylist.current(minecraft);
		musicManager.startPlaying(track);
	}

	private static void tickFadeIn(Minecraft minecraft) {
		float volume = fadeProgress();
		applyMusicVolume(minecraft, volume);
		if (fadeProgress() >= 1.0F) {
			applyMusicVolume(minecraft, 1.0F);
			state = State.IDLE;
			fadeStartMs = -1L;
		}
	}

	private static float fadeProgress() {
		if (fadeStartMs < 0L || fadeDurationMs <= 0L) {
			return 1.0F;
		}
		return Mth.clamp((float) (Util.getMillis() - fadeStartMs) / (float) fadeDurationMs, 0.0F, 1.0F);
	}

	private static void applyMusicVolume(Minecraft minecraft, float multiplier) {
		minecraft.getSoundManager().updateCategoryVolume(SoundSource.MUSIC, Mth.clamp(multiplier, 0.0F, 1.0F));
	}
}
