package com.weaponsmodaddon.mixin;

import ckathode.weaponmod.WeaponModConfig;
import me.shedaniel.autoconfig.AutoConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link WeaponModConfig#init()} is not idempotent. Loom can run our {@code main} entrypoint
 * before {@code weaponmod}'s despite {@code depends}, so we may register the config early —
 * then WeaponMod's own {@code init()} must no-op instead of throwing.
 */
@Mixin(WeaponModConfig.class)
public abstract class WeaponModConfigMixin {
	@Inject(method = "init", at = @At("HEAD"), cancellable = true)
	private static void weaponsmodaddon$skipIfAlreadyRegistered(CallbackInfo ci) {
		try {
			AutoConfig.getConfigHolder(WeaponModConfig.class);
			ci.cancel();
		} catch (RuntimeException ignored) {
			// Not registered yet — let WeaponMod (or our bootstrap) register it.
		}
	}
}
