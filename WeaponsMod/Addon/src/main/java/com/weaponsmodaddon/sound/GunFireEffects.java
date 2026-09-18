package com.weaponsmodaddon.sound;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Shared gun-bang VFX/SFX for musket / flintlock / blunderbuss / mortar {@code effectShoot}.
 * Skips local duplicate when {@link GunFireSoundMarkers} says the client already banged.
 */
public final class GunFireEffects {
	/**
	 * Requested play volume. Minecraft clamps to 1.0 in {@code SoundEngine}; real punch
	 * comes from client {@code GunBangBoost} (~2.75× OpenAL gain) + shock ducking.
	 */
	public static final float VOLUME = 1.0f;

	private GunFireEffects() {
	}

	public static void playBang(
			Level world,
			double x,
			double y,
			double z,
			float yaw,
			float pitch,
			SoundEvent sound
	) {
		boolean skipSound = world.isClientSide() && GunFireSoundMarkers.consumeLocalBangPlayed();
		Player except = GunFireSoundMarkers.consumeExceptPlayer();
		if (!skipSound) {
			world.playSound(except, x, y, z, sound, SoundSource.PLAYERS, VOLUME, 1.0f);
		}
		float particleX = -Mth.sin((yaw + 23.0f) * 0.017453292f) * Mth.cos(pitch * 0.017453292f);
		float particleY = -Mth.sin(pitch * 0.017453292f) + 1.6f;
		float particleZ = Mth.cos((yaw + 23.0f) * 0.017453292f) * Mth.cos(pitch * 0.017453292f);
		if (world.isClientSide()) {
			for (int i = 0; i < 3; ++i) {
				world.addParticle(ParticleTypes.SMOKE, x + particleX, y + particleY, z + particleZ, 0.0, 0.0, 0.0);
			}
			world.addParticle(ParticleTypes.FLAME, x + particleX, y + particleY, z + particleZ, 0.0, 0.0, 0.0);
		}
	}
}
