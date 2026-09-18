package com.weaponsmodaddon.client.mixin;

import com.weaponsmodaddon.client.sound.GunBangBoost;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MC clamps play volume to 1.0 — bump gun bangs so OpenAL can apply real gain
 * (see {@link com.weaponsmodaddon.client.mixin.ChannelMixin}).
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
	@Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
	private void weaponsmodaddon$boostGunBangVolume(SoundInstance instance, CallbackInfoReturnable<Float> cir) {
		if (!GunBangBoost.isGunBang(instance)) {
			return;
		}
		cir.setReturnValue(GunBangBoost.boostedGain(cir.getReturnValueF()));
	}
}
