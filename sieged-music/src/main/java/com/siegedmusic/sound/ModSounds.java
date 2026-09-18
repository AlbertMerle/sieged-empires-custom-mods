package com.siegedmusic.sound;

import com.siegedmusic.SiegedMusic;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;

/**
 * Custom in-game background music tracks (ambient, cave, and war variants).
 */
public final class ModSounds {
	/** Six minutes between ambient / cave tracks (20 ticks/s). */
	private static final int SIX_MINUTES_TICKS = 6 * 60 * 20;

	public static final Holder.Reference<SoundEvent> MUSIC_GAME_TUNE1 = register("music.game.tune1");
	public static final Holder.Reference<SoundEvent> MUSIC_GAME_TUNE2 = register("music.game.tune2");
	public static final Holder.Reference<SoundEvent> MUSIC_GAME_TUNE3 = register("music.game.tune3");
	public static final Holder.Reference<SoundEvent> MUSIC_GAME_TUNE4 = register("music.game.tune4");
	public static final Holder.Reference<SoundEvent> MUSIC_GAME_TUNE5 = register("music.game.tune5");
	public static final Holder.Reference<SoundEvent> MUSIC_GAME_TUNE6 = register("music.game.tune6");
	public static final Holder.Reference<SoundEvent> MUSIC_GAME_TUNE7 = register("music.game.tune7");
	public static final Holder.Reference<SoundEvent> MUSIC_GAME_TUNE8 = register("music.game.tune8");
	public static final Holder.Reference<SoundEvent> MUSIC_GAME_TUNE9 = register("music.game.tune9");

	public static final Holder.Reference<SoundEvent> MUSIC_GAME_CAVESONG1 = register("music.game.cavesong1");
	public static final Holder.Reference<SoundEvent> MUSIC_GAME_CAVESONG2 = register("music.game.cavesong2");
	public static final Holder.Reference<SoundEvent> MUSIC_GAME_CAVESONG3 = register("music.game.cavesong3");
	public static final Holder.Reference<SoundEvent> MUSIC_GAME_CAVESONG4 = register("music.game.cavesong4");
	public static final Holder.Reference<SoundEvent> MUSIC_GAME_CAVESONG5 = register("music.game.cavesong5");
	public static final Holder.Reference<SoundEvent> MUSIC_GAME_CAVESONG6 = register("music.game.cavesong6");

	public static final Holder.Reference<SoundEvent> MUSIC_GAME_WAR1 = register("music.game.war1");
	public static final Holder.Reference<SoundEvent> MUSIC_GAME_WAR2 = register("music.game.war2");
	public static final Holder.Reference<SoundEvent> MUSIC_GAME_WAR3 = register("music.game.war3");

	public static final Music TUNE1 = ambientMusic(MUSIC_GAME_TUNE1);
	public static final Music TUNE2 = ambientMusic(MUSIC_GAME_TUNE2);
	public static final Music TUNE3 = ambientMusic(MUSIC_GAME_TUNE3);
	public static final Music TUNE4 = ambientMusic(MUSIC_GAME_TUNE4);
	public static final Music TUNE5 = ambientMusic(MUSIC_GAME_TUNE5);
	public static final Music TUNE6 = ambientMusic(MUSIC_GAME_TUNE6);
	public static final Music TUNE7 = ambientMusic(MUSIC_GAME_TUNE7);
	public static final Music TUNE8 = ambientMusic(MUSIC_GAME_TUNE8);
	public static final Music TUNE9 = ambientMusic(MUSIC_GAME_TUNE9);

	public static final Music CAVESONG1 = ambientMusic(MUSIC_GAME_CAVESONG1);
	public static final Music CAVESONG2 = ambientMusic(MUSIC_GAME_CAVESONG2);
	public static final Music CAVESONG3 = ambientMusic(MUSIC_GAME_CAVESONG3);
	public static final Music CAVESONG4 = ambientMusic(MUSIC_GAME_CAVESONG4);
	public static final Music CAVESONG5 = ambientMusic(MUSIC_GAME_CAVESONG5);
	public static final Music CAVESONG6 = ambientMusic(MUSIC_GAME_CAVESONG6);

	public static final Music WAR1 = warMusic(MUSIC_GAME_WAR1);
	public static final Music WAR2 = warMusic(MUSIC_GAME_WAR2);
	public static final Music WAR3 = warMusic(MUSIC_GAME_WAR3);

	public static final Music[] AMBIENT_TRACKS = {
			TUNE1, TUNE2, TUNE3, TUNE4, TUNE5, TUNE6, TUNE7, TUNE8, TUNE9
	};

	public static final Music[] CAVE_TRACKS = {
			CAVESONG1, CAVESONG2, CAVESONG3, CAVESONG4, CAVESONG5, CAVESONG6
	};

	public static final Music[] WAR_TRACKS = {
			WAR1, WAR2, WAR3
	};

	private ModSounds() {
	}

	public static void initialize() {
	}

	private static Music ambientMusic(Holder.Reference<SoundEvent> sound) {
		return new Music(sound, SIX_MINUTES_TICKS, SIX_MINUTES_TICKS, false);
	}

	private static Music warMusic(Holder.Reference<SoundEvent> sound) {
		return new Music(sound, 0, 0, false);
	}

	public static boolean isAmbientTrack(Identifier soundId) {
		return isTrackIn(soundId, AMBIENT_TRACKS);
	}

	public static boolean isCaveTrack(Identifier soundId) {
		return isTrackIn(soundId, CAVE_TRACKS);
	}

	public static boolean isWarTrack(Identifier soundId) {
		return isTrackIn(soundId, WAR_TRACKS);
	}

	public static boolean isGameTrack(Identifier soundId) {
		return isAmbientTrack(soundId) || isCaveTrack(soundId) || isWarTrack(soundId);
	}

	private static boolean isTrackIn(Identifier soundId, Music[] tracks) {
		if (soundId == null) {
			return false;
		}
		for (Music track : tracks) {
			if (track.sound().value().location().equals(soundId)) {
				return true;
			}
		}
		return false;
	}

	private static Holder.Reference<SoundEvent> register(String path) {
		Identifier id = SiegedMusic.id(path);
		return Registry.registerForHolder(
				BuiltInRegistries.SOUND_EVENT,
				id,
				SoundEvent.createVariableRangeEvent(id)
		);
	}
}
