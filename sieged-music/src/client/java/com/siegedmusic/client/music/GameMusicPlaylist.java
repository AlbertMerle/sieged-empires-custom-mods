package com.siegedmusic.client.music;

import com.siegedmusic.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import net.minecraft.util.RandomSource;

/**
 * In-world BGM: ambient / cave / war playlists.
 * Cave replaces ambient when the player is below Y 40.
 * War (near war banner) overrides both. Playlist switches use {@link MusicFadeController} cross-fades.
 */
public final class GameMusicPlaylist {
	public enum Mode {
		AMBIENT,
		CAVE,
		WAR
	}

	/** Feet below this Y use cave music instead of overworld ambient. */
	public static final int CAVE_Y_MAX = 40;

	private static final RandomSource RANDOM = RandomSource.create();

	private static int ambientIndex = -1;
	private static int caveIndex = -1;
	private static int warIndex = -1;
	private static Mode mode = Mode.AMBIENT;
	private static Mode pendingMode = Mode.AMBIENT;

	private GameMusicPlaylist() {
	}

	public static Music current(Minecraft minecraft) {
		return switch (mode) {
			case WAR -> {
				ensureWarStarted();
				yield ModSounds.WAR_TRACKS[warIndex];
			}
			case CAVE -> {
				ensureCaveStarted();
				yield ModSounds.CAVE_TRACKS[caveIndex];
			}
			case AMBIENT -> {
				ensureAmbientStarted();
				yield ModSounds.AMBIENT_TRACKS[ambientIndex];
			}
		};
	}

	public static void onEnterWorld() {
		reshuffle();
		MusicFadeController.armWorldEntry();
	}

	public static void onLeaveWorld() {
		reshuffle();
		MusicFadeController.reset();
	}

	public static void reshuffle() {
		ambientIndex = -1;
		caveIndex = -1;
		warIndex = -1;
		mode = Mode.AMBIENT;
		pendingMode = Mode.AMBIENT;
	}

	public static void onTrackEnded(Identifier endedSoundId) {
		if (ModSounds.isWarTrack(endedSoundId)) {
			ensureWarStarted();
			Identifier currentId = ModSounds.WAR_TRACKS[warIndex].sound().value().location();
			if (currentId.equals(endedSoundId)) {
				warIndex = (warIndex + 1) % ModSounds.WAR_TRACKS.length;
			}
			return;
		}
		if (ModSounds.isCaveTrack(endedSoundId)) {
			ensureCaveStarted();
			Identifier currentId = ModSounds.CAVE_TRACKS[caveIndex].sound().value().location();
			if (currentId.equals(endedSoundId)) {
				caveIndex = (caveIndex + 1) % ModSounds.CAVE_TRACKS.length;
			}
			return;
		}
		if (ModSounds.isAmbientTrack(endedSoundId)) {
			ensureAmbientStarted();
			Identifier currentId = ModSounds.AMBIENT_TRACKS[ambientIndex].sound().value().location();
			if (currentId.equals(endedSoundId)) {
				ambientIndex = (ambientIndex + 1) % ModSounds.AMBIENT_TRACKS.length;
			}
		}
	}

	public static void updateMode(Minecraft minecraft) {
		if (!MusicFadeController.isIdle()) {
			return;
		}
		Mode desired = desiredMode(minecraft);
		if (desired == mode) {
			return;
		}
		pendingMode = desired;
		MusicFadeController.requestModeTransition();
	}

	public static void applyPendingMode() {
		mode = pendingMode;
		switch (mode) {
			case WAR -> warIndex = RANDOM.nextInt(ModSounds.WAR_TRACKS.length);
			case CAVE -> caveIndex = RANDOM.nextInt(ModSounds.CAVE_TRACKS.length);
			case AMBIENT -> ambientIndex = RANDOM.nextInt(ModSounds.AMBIENT_TRACKS.length);
		}
	}

	/** World-entry: pick ambient / cave / war from current location with no fade. */
	public static void applyDesiredImmediately(Minecraft minecraft) {
		pendingMode = desiredMode(minecraft);
		applyPendingMode();
	}

	public static Mode getMode() {
		return mode;
	}

	public static boolean isWarMode() {
		return mode == Mode.WAR;
	}

	public static boolean isCaveMode() {
		return mode == Mode.CAVE;
	}

	public static Mode desiredMode(Minecraft minecraft) {
		if (WarBannerProximity.isNearActiveWarBanner(minecraft)) {
			return Mode.WAR;
		}
		if (minecraft.player != null && minecraft.player.getY() < CAVE_Y_MAX) {
			return Mode.CAVE;
		}
		return Mode.AMBIENT;
	}

	private static void ensureAmbientStarted() {
		if (ambientIndex < 0) {
			ambientIndex = RANDOM.nextInt(ModSounds.AMBIENT_TRACKS.length);
		}
	}

	private static void ensureCaveStarted() {
		if (caveIndex < 0) {
			caveIndex = RANDOM.nextInt(ModSounds.CAVE_TRACKS.length);
		}
	}

	private static void ensureWarStarted() {
		if (warIndex < 0) {
			warIndex = RANDOM.nextInt(ModSounds.WAR_TRACKS.length);
		}
	}
}
