package com.distantnoise.client.mixin;

import com.distantnoise.client.sound.GrassRustleSoundInstance;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.lwjgl.openal.AL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
	@Shadow
	private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

	@Unique
	private static final Set<SoundInstance> distantnoise$offsetApplied =
			Collections.newSetFromMap(new IdentityHashMap<>());

	@Inject(method = "tickInGameSound", at = @At("TAIL"))
	private void distantnoise$seekGrassRustle(CallbackInfo ci) {
		for (Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> entry : instanceToChannel.entrySet()) {
			SoundInstance instance = entry.getKey();
			if (!(instance instanceof GrassRustleSoundInstance grass)) {
				continue;
			}
			if (distantnoise$offsetApplied.contains(instance)) {
				continue;
			}
			float offset = grass.getStartOffsetSeconds();
			if (offset <= 0.0f) {
				distantnoise$offsetApplied.add(instance);
				continue;
			}
			entry.getValue().execute(channel -> AL11.alSourcef(
					((ChannelAccessor) channel).distantnoise$getSource(),
					AL11.AL_SEC_OFFSET,
					offset
			));
			distantnoise$offsetApplied.add(instance);
		}
		distantnoise$offsetApplied.removeIf(instance -> !instanceToChannel.containsKey(instance));
	}
}
