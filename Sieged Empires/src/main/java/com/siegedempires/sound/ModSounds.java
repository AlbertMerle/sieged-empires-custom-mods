package com.siegedempires.sound;

import com.siegedempires.Siegedempires;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;

/**
 * Custom sound events: title-screen BGM playlist + post-Join location ambients.
 */
public final class ModSounds {
	public static final Holder.Reference<SoundEvent> MUSIC_TITLE_ADVENTURE = register("music.title.adventure");
	public static final Holder.Reference<SoundEvent> MUSIC_TITLE_MARKED = register("music.title.marked");
	public static final Holder.Reference<SoundEvent> MUSIC_TITLE_MEDIEVAL_TAVERN = register("music.title.medieval_tavern");

	public static final Holder.Reference<SoundEvent> AMBIENT_JOIN_OCEAN = register("ambient.join.ocean");
	public static final Holder.Reference<SoundEvent> AMBIENT_JOIN_OCEANSTORM = register("ambient.join.oceanstorm");
	public static final Holder.Reference<SoundEvent> AMBIENT_JOIN_SNOWDAY = register("ambient.join.snowday");
	public static final Holder.Reference<SoundEvent> AMBIENT_JOIN_VILLAGE = register("ambient.join.village");
	public static final Holder.Reference<SoundEvent> AMBIENT_JOIN_WILDERNESS_DAY = register("ambient.join.wildernessday");
	public static final Holder.Reference<SoundEvent> AMBIENT_JOIN_WILDERNESS_NIGHT = register("ambient.join.wildernessnight");
	public static final Holder.Reference<SoundEvent> COIN_CRAFT = register("item.gold_coin.craft");
	public static final Holder.Reference<SoundEvent> GOLD_BAR_PLACE = register("block.gold_bar.place");

	/**
	 * Title / menu BGM tracks. Delays are {@code 0}/{@code 0} (like {@code Musics.CREDITS})
	 * so MusicManager restarts as soon as a track ends while still on menus.
	 * {@code replaceCurrentMusic} is false so navigating Options / other menus does
	 * not cut the track. World entry fades music out; in-game situational music is no
	 * longer a title track, so these do not play again.
	 * <p>
	 * Playlist order / random start is handled client-side by {@code TitleMusicPlaylist}.
	 */
	public static final Music TITLE_ADVENTURE = new Music(MUSIC_TITLE_ADVENTURE, 0, 0, false);
	public static final Music TITLE_MARKED = new Music(MUSIC_TITLE_MARKED, 0, 0, false);
	public static final Music TITLE_MEDIEVAL_TAVERN = new Music(MUSIC_TITLE_MEDIEVAL_TAVERN, 0, 0, false);

	/** All title playlist entries, in cycle order. */
	public static final Music[] TITLE_TRACKS = {
			TITLE_ADVENTURE,
			TITLE_MARKED,
			TITLE_MEDIEVAL_TAVERN
	};

	private ModSounds() {}

	private static Holder.Reference<SoundEvent> register(String path) {
		Identifier id = Siegedempires.id(path);
		return Registry.registerForHolder(
				BuiltInRegistries.SOUND_EVENT,
				id,
				SoundEvent.createVariableRangeEvent(id)
		);
	}

	public static void initialize() {}

	public static boolean isTitleTrack(Music music) {
		if (music == null) {
			return false;
		}
		Identifier id = music.sound().value().location();
		for (Music track : TITLE_TRACKS) {
			if (track.sound().value().location().equals(id)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isTitleTrack(Identifier soundId) {
		if (soundId == null) {
			return false;
		}
		for (Music track : TITLE_TRACKS) {
			if (track.sound().value().location().equals(soundId)) {
				return true;
			}
		}
		return false;
	}

	/** Resolve the Join cinematic ambient for location kind / subtitle / snowy. */
	public static Holder.Reference<SoundEvent> joinAmbientFor(String kind, String subtitle, boolean snowy) {
		if ("claim".equals(kind)) {
			return AMBIENT_JOIN_VILLAGE;
		}
		if ("ocean".equals(kind)) {
			if ("(Thunderstorm)".equals(subtitle)) {
				return AMBIENT_JOIN_OCEANSTORM;
			}
			return AMBIENT_JOIN_OCEAN;
		}
		// wilderness
		if (snowy) {
			return AMBIENT_JOIN_SNOWDAY;
		}
		if ("(Nighttime)".equals(subtitle)) {
			return AMBIENT_JOIN_WILDERNESS_NIGHT;
		}
		return AMBIENT_JOIN_WILDERNESS_DAY;
	}
}
