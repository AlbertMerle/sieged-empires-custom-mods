package com.distantnoise.mixin.weaponmod;

import ckathode.weaponmod.AdvancedExplosion;
import com.distantnoise.sound.DistantNoiseKind;
import com.distantnoise.sound.WeaponModNoiseRelay;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** WeaponsMod custom advanced explosions (mortar shell crater, dynamite, cannonball, warhammer). */
@Mixin(AdvancedExplosion.class)
public class AdvancedExplosionMixin {
	@Inject(method = "doEntityExplosion(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
	private void distantnoise$afterAdvancedExplosion(DamageSource damagesource, CallbackInfo ci) {
		AdvancedExplosion self = (AdvancedExplosion) (Object) this;
		WeaponModNoiseRelay.relay(
				self.serverLevel,
				DistantNoiseKind.TNT,
				self.center.x,
				self.center.y,
				self.center.z
		);
	}
}
