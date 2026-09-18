package com.weaponsmodaddon.client.sound;

import com.weaponsmodaddon.ModItemTags;
import com.weaponsmodaddon.sound.GunFireEffects;
import com.weaponsmodaddon.sound.GunFireSoundMarkers;
import com.weaponsmodaddon.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Plays the gunshot bang the instant the local player fires (before server round-trip).
 * Uses a <b>world-positioned mono</b> play so Sound Physics Remastered can apply
 * occlusion / reverb. Category is {@link SoundSource#MASTER} so ear-ring category duck
 * (all non-MASTER) does not quiet the bang itself.
 */
public final class GunFireSoundClient {
	private GunFireSoundClient() {
	}

	/** Call on ADS LMB fire — bang first, then schedule ear-ring. */
	public static void playInstantBang(ItemStack stack) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		Level level = client.level;
		if (player == null || level == null || stack == null || stack.isEmpty()) {
			return;
		}
		SoundEvent sound = soundFor(stack);
		if (sound == null) {
			return;
		}
		// MASTER + world coords + mono → full gain under duck; SPR still gets a position.
		level.playLocalSound(
				player.getX(),
				player.getEyeY(),
				player.getZ(),
				sound,
				SoundSource.MASTER,
				GunFireEffects.VOLUME,
				1.0f,
				false
		);
		GunBangEchoClient.schedule(player.getX(), player.getEyeY(), player.getZ(), sound);
		GunFireSoundMarkers.markLocalBangPlayed();
	}

	private static SoundEvent soundFor(ItemStack stack) {
		if (stack.is(ModItemTags.PISTOLS)) {
			return ModSounds.FLINTLOCK_FIRE;
		}
		if (stack.is(ModItemTags.TWO_HANDED_GUNS)) {
			return ModSounds.MUSKET_FIRE;
		}
		return null;
	}
}
