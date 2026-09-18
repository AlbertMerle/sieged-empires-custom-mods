package com.siegedmusic.sound;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.minecraft.sounds.SoundEvents;

/**
 * Detects vanilla situational music that should be replaced with Sieged Music tracks.
 * Menu, credits, dragon boss, and jukebox discs are left untouched.
 */
public final class VanillaGameMusic {
	private VanillaGameMusic() {
	}

	public static boolean isReplaceable(Music music) {
		if (music == null) {
			return false;
		}
		if (music == Musics.MENU || music == Musics.CREDITS || music == Musics.END_BOSS) {
			return false;
		}
		Identifier id = music.sound().value().location();
		if (!"minecraft".equals(id.getNamespace())) {
			return false;
		}
		String path = id.getPath();
		if (path.startsWith("music_disc.")) {
			return false;
		}
		if (path.equals("music.menu") || path.equals("music.credits") || path.equals("music.dragon")) {
			return false;
		}
		return path.startsWith("music.");
	}

	public static boolean isReplaceableVanillaSound(Identifier soundId) {
		if (soundId == null || !"minecraft".equals(soundId.getNamespace())) {
			return false;
		}
		String path = soundId.getPath();
		if (path.startsWith("music_disc.")) {
			return false;
		}
		if (path.equals("music.menu") || path.equals("music.credits") || path.equals("music.dragon")) {
			return false;
		}
		if (path.equals(SoundEvents.MUSIC_GAME.value().location().getPath())) {
			return true;
		}
		if (path.equals(SoundEvents.MUSIC_CREATIVE.value().location().getPath())) {
			return true;
		}
		if (path.equals(SoundEvents.MUSIC_UNDER_WATER.value().location().getPath())) {
			return true;
		}
		if (path.equals(SoundEvents.MUSIC_END.value().location().getPath())) {
			return true;
		}
		return path.startsWith("music.overworld.") || path.startsWith("music.nether.");
	}
}
