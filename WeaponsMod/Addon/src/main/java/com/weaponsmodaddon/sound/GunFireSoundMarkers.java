package com.weaponsmodaddon.sound;

import net.minecraft.world.entity.player.Player;

/**
 * Cross-side markers so gun fire audio is not doubled for the local shooter:
 * client plays an instant bang, server broadcasts to everyone else.
 */
public final class GunFireSoundMarkers {
	private static final ThreadLocal<Boolean> LOCAL_BANG_PLAYED = ThreadLocal.withInitial(() -> false);
	private static final ThreadLocal<Player> EXCEPT_PLAYER = new ThreadLocal<>();

	private GunFireSoundMarkers() {
	}

	/** Client: instant bang already played this fire. */
	public static void markLocalBangPlayed() {
		LOCAL_BANG_PLAYED.set(Boolean.TRUE);
	}

	public static boolean consumeLocalBangPlayed() {
		Boolean v = LOCAL_BANG_PLAYED.get();
		LOCAL_BANG_PLAYED.set(Boolean.FALSE);
		return Boolean.TRUE.equals(v);
	}

	/** Server: exclude this shooter from the broadcast (they already heard locally). */
	public static void setExceptPlayer(Player player) {
		EXCEPT_PLAYER.set(player);
	}

	public static Player consumeExceptPlayer() {
		Player p = EXCEPT_PLAYER.get();
		EXCEPT_PLAYER.remove();
		return p;
	}

	public static void clearExceptPlayer() {
		EXCEPT_PLAYER.remove();
	}
}
