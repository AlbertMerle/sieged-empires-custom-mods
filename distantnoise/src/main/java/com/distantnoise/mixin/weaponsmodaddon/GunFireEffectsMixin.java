package com.distantnoise.mixin.weaponsmodaddon;

import com.distantnoise.config.DistantNoiseConfig;
import com.weaponsmodaddon.sound.GunFireEffects;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Distantnoise relay handles all non-shooter gun audio (0–800 blocks). Skip the addon's
 * server {@code playSound} broadcast so nearby listeners are not doubled.
 */
@Mixin(GunFireEffects.class)
public class GunFireEffectsMixin {
	@Inject(method = "playBang", at = @At("HEAD"), cancellable = true, remap = false)
	private static void distantnoise$skipServerBang(
			Level world,
			double x,
			double y,
			double z,
			float yaw,
			float pitch,
			SoundEvent sound,
			CallbackInfo ci
	) {
		if (!world.isClientSide() && DistantNoiseConfig.get().enabled) {
			ci.cancel();
		}
	}
}
