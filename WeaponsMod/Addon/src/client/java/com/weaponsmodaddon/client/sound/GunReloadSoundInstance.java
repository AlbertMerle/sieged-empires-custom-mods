package com.weaponsmodaddon.client.sound;

import com.weaponsmodaddon.config.AddonConfig;
import com.weaponsmodaddon.gun.GunReload;
import com.weaponsmodaddon.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Plays {@link ModSounds#GUN_RELOAD} while a player is reloading; stops the moment
 * reload ends or is cancelled. Loops when the reload lasts longer than the ~8s clip
 * (e.g. 16s muskets); flintlock’s 6s reload cuts the clip short.
 * <p>
 * Hear distance is capped at {@link AddonConfig#reloadSoundMaxRange} (default 12 blocks)
 * with smooth volume falloff — not audible hundreds of blocks away.
 */
public final class GunReloadSoundInstance extends AbstractTickableSoundInstance {
	/** Clip length ≈ 8.2s; loop when reload is longer than this. */
	private static final int CLIP_TICKS = 164;

	private final Player player;

	public GunReloadSoundInstance(Player player, int reloadDurationTicks) {
		super(ModSounds.GUN_RELOAD, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.player = player;
		this.looping = reloadDurationTicks > CLIP_TICKS;
		this.pitch = 1.0f;
		this.x = player.getX();
		this.y = player.getY();
		this.z = player.getZ();
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.volume = volumeForListener();
	}

	@Override
	public void tick() {
		if (player.isRemoved() || !GunReload.isReloading(player)) {
			stop();
			return;
		}
		this.x = player.getX();
		this.y = player.getY();
		this.z = player.getZ();
		float next = volumeForListener();
		if (next <= 0.001f) {
			stop();
			return;
		}
		this.volume = next;
	}

	static boolean isWithinHearRange(Player listener, Player source) {
		if (listener == null || source == null) {
			return false;
		}
		if (listener.getUUID().equals(source.getUUID())) {
			return true;
		}
		double max = AddonConfig.get().reloadSoundMaxRange;
		return listener.position().distanceTo(source.position()) < max;
	}

	static float volumeAtDistance(double dist, double maxRange) {
		if (dist >= maxRange) {
			return 0.0f;
		}
		if (dist <= 0.0) {
			return 1.0f;
		}
		double t = dist / maxRange;
		t = Mth.clamp(t, 0.0, 1.0);
		t = t * t * (3.0 - 2.0 * t);
		return (float) (1.0 - t);
	}

	private float volumeForListener() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return 0.0f;
		}
		if (player.getUUID().equals(mc.player.getUUID())) {
			return 1.0f;
		}
		double max = AddonConfig.get().reloadSoundMaxRange;
		return volumeAtDistance(mc.player.position().distanceTo(player.position()), max);
	}
}
