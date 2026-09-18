package com.weaponsmodaddon.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * Local gun bangs play on {@link SoundSource#MASTER} (so shock-duck does not quiet them),
 * which skips Sound Physics reverb. Schedule a short delayed, quieter copy so the shooter
 * hears a tiny slapback echo from their own shot.
 */
public final class GunBangEchoClient {
	/** Ticks after main bang before echo (~120ms). */
	private static final int DELAY_TICKS = 6;
	/** Base volume before {@link GunBangBoost} (MASTER → full category gain). */
	private static final float ECHO_VOLUME = 0.14f;
	private static final float ECHO_PITCH = 0.93f;

	private static int delayTicks = -1;
	private static SoundEvent pendingSound;
	private static double echoX;
	private static double echoY;
	private static double echoZ;

	private GunBangEchoClient() {
	}

	public static void schedule(double x, double y, double z, SoundEvent sound) {
		pendingSound = sound;
		echoX = x;
		echoY = y;
		echoZ = z;
		delayTicks = DELAY_TICKS;
	}

	public static void tick(Minecraft client) {
		if (delayTicks < 0) {
			return;
		}
		if (client.player == null || client.level == null) {
			clear();
			return;
		}
		delayTicks--;
		if (delayTicks < 0) {
			playEcho(client);
		}
	}

	public static void clear() {
		delayTicks = -1;
		pendingSound = null;
	}

	private static void playEcho(Minecraft client) {
		SoundEvent sound = pendingSound;
		clear();
		if (sound == null || client.level == null) {
			return;
		}
		client.level.playLocalSound(
				echoX,
				echoY,
				echoZ,
				sound,
				SoundSource.MASTER,
				Mth.clamp(ECHO_VOLUME, 0.0f, 1.0f),
				ECHO_PITCH,
				false
		);
	}
}
