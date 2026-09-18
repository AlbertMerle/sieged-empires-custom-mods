package com.siegedempires.client.title;

import com.siegedempires.sound.ModSounds;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import net.minecraft.util.RandomSource;

/**
 * Title / menu BGM: pick a random track to start, then cycle through
 * {@link ModSounds#TITLE_TRACKS} each time a track finishes.
 */
public final class TitleMusicPlaylist {
	private static final RandomSource RANDOM = RandomSource.create();

	private static int index = -1;

	private TitleMusicPlaylist() {
	}

	/** Current playlist entry for situational / screen background music. */
	public static Music current() {
		ensureStarted();
		return ModSounds.TITLE_TRACKS[index];
	}

	/**
	 * Call when a title track finishes (MusicManager clears the active instance).
	 * Advances so the next {@link #current()} is the following song in the cycle.
	 */
	public static void onTitleTrackEnded(Identifier endedSoundId) {
		if (!ModSounds.isTitleTrack(endedSoundId)) {
			return;
		}
		ensureStarted();
		Identifier currentId = ModSounds.TITLE_TRACKS[index].sound().value().location();
		if (currentId.equals(endedSoundId)) {
			index = (index + 1) % ModSounds.TITLE_TRACKS.length;
		}
	}

	/** Re-roll the starting track (e.g. after leaving a world back to menus). */
	public static void reshuffle() {
		index = -1;
	}

	private static void ensureStarted() {
		if (index < 0) {
			index = RANDOM.nextInt(ModSounds.TITLE_TRACKS.length);
		}
	}
}
