package com.weaponsmodaddon.mixin;

import ckathode.weaponmod.WMRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link WMRegistries#init()} is not idempotent (DeferredRegister throws on second register).
 * Loom can run our {@code main} before {@code weaponmod}, and we must register attributes
 * before constructing scoped muskets — then WeaponMod's later {@code init()} must no-op.
 */
@Mixin(WMRegistries.class)
public abstract class WMRegistriesMixin {
	@Inject(method = "init", at = @At("HEAD"), cancellable = true)
	private static void weaponsmodaddon$skipIfAlreadyRegistered(CallbackInfo ci) {
		if (WMRegistries.RELOAD_TIME.isPresent()) {
			ci.cancel();
		}
	}
}
