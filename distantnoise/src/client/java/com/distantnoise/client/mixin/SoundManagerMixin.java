package com.distantnoise.client.mixin;

import com.distantnoise.config.DistantNoiseConfig;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla {@code entity.generic.explode} with the distantnoise custom explosion relay.
 */
@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
	private static final Identifier VANILLA_EXPLODE =
			Identifier.fromNamespaceAndPath("minecraft", "entity.generic.explode");

	@Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
	private void distantnoise$suppressVanillaExplode(SoundInstance instance, CallbackInfo ci) {
		if (!DistantNoiseConfig.get().enabled) {
			return;
		}
		if (VANILLA_EXPLODE.equals(instance.getIdentifier())) {
			ci.cancel();
		}
	}
}
