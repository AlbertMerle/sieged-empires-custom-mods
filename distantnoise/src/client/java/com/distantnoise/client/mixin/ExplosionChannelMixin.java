package com.distantnoise.client.mixin;

import com.distantnoise.client.sound.ExplosionBangBoost;
import com.mojang.blaze3d.audio.Channel;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raises OpenAL gain for boosted explosion relay clips so Soft does not re-clamp to 1.0.
 */
@Mixin(Channel.class)
public abstract class ExplosionChannelMixin {
	@Shadow
	@Final
	private int source;

	@Inject(method = "setVolume", at = @At("HEAD"), cancellable = true)
	private void distantnoise$loudExplosionBang(float volume, CallbackInfo ci) {
		boolean boosted = volume > 1.001f || ExplosionBangBoost.isBoostedSource(this.source);
		if (!boosted) {
			return;
		}
		ExplosionBangBoost.markSource(this.source);
		float gain = volume > 1.001f ? volume : ExplosionBangBoost.boostedGain(volume);
		AL10.alSourcef(this.source, AL10.AL_MAX_GAIN, ExplosionBangBoost.MAX_GAIN);
		AL10.alSourcef(this.source, AL10.AL_GAIN, gain);
		ci.cancel();
	}

	@Inject(method = "destroy", at = @At("HEAD"))
	private void distantnoise$explosionBoostDestroy(CallbackInfo ci) {
		ExplosionBangBoost.forgetSource(this.source);
	}
}
