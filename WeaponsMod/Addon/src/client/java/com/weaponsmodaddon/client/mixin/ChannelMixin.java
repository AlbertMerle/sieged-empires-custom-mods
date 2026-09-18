package com.weaponsmodaddon.client.mixin;

import com.mojang.blaze3d.audio.Channel;
import com.weaponsmodaddon.client.sound.GunBangBoost;
import com.weaponsmodaddon.client.sound.GunEarRingLowpass;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tracks OpenAL sources so ear-ring can apply / clear a low-pass filter.
 * Relative sources (tinnitus) stay unfiltered.
 * <p>
 * Also raises {@code AL_MAX_GAIN} for gun bangs so boosted volumes &gt; 1.0 are not Soft-clamped.
 */
@Mixin(Channel.class)
public abstract class ChannelMixin {
	@Shadow
	@Final
	private int source;

	@Inject(method = "setRelative", at = @At("TAIL"))
	private void weaponsmodaddon$earRingRelative(boolean relative, CallbackInfo ci) {
		GunEarRingLowpass.markRelative(this.source, relative);
	}

	@Inject(method = "setVolume", at = @At("HEAD"), cancellable = true)
	private void weaponsmodaddon$loudGunBang(float volume, CallbackInfo ci) {
		boolean bang = volume > 1.001f || GunBangBoost.isBangSource(this.source);
		if (!bang) {
			return;
		}
		GunBangBoost.markSource(this.source);
		float gain = volume > 1.001f ? volume : GunBangBoost.boostedGain(volume);
		AL10.alSourcef(this.source, AL10.AL_MAX_GAIN, GunBangBoost.MAX_GAIN);
		AL10.alSourcef(this.source, AL10.AL_GAIN, gain);
		GunEarRingLowpass.onSourceUsed(this.source);
		ci.cancel();
	}

	@Inject(method = "setVolume", at = @At("TAIL"))
	private void weaponsmodaddon$earRingVolume(float volume, CallbackInfo ci) {
		GunEarRingLowpass.onSourceUsed(this.source);
	}

	@Inject(method = "destroy", at = @At("HEAD"))
	private void weaponsmodaddon$earRingDestroy(CallbackInfo ci) {
		GunEarRingLowpass.forgetSource(this.source);
		GunBangBoost.forgetSource(this.source);
	}
}
