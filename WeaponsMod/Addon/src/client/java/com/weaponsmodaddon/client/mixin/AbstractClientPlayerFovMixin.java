package com.weaponsmodaddon.client.mixin;

import com.weaponsmodaddon.client.scope.ScopedMusketAimClient;
import com.weaponsmodaddon.gun.GunAimState;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scoped muskets keep staged zoom. Regular gun aim uses WeaponMod bow-style FOV.
 * Spear/javelin charge gets the same 15% bow-like zoom curve.
 */
@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerFovMixin {

	@Inject(method = "getFieldOfViewModifier", at = @At("HEAD"), cancellable = true)
	private void weaponsmodaddon$scopedMusketFov(boolean firstPerson, float effectScale,
			CallbackInfoReturnable<Float> cir) {
		if (!firstPerson) {
			return;
		}
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		if (ScopedMusketAimClient.isAimingScoped(self)) {
			cir.setReturnValue(ScopedMusketAimClient.fovModifier());
		}
	}

	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void weaponsmodaddon$spearBowFov(boolean firstPerson, float effectScale,
			CallbackInfoReturnable<Float> cir) {
		if (!firstPerson) {
			return;
		}
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		if (ScopedMusketAimClient.isAimingScoped(self)) {
			return;
		}
		if (!GunAimState.isChargingSpearOrJavelin(self)) {
			return;
		}
		float charge = self.getTicksUsingItem() / 20.0F;
		if (charge > 1.0F) {
			charge = 1.0F;
		} else {
			charge *= charge;
		}
		cir.setReturnValue(cir.getReturnValueF() * (1.0F - charge * 0.15F));
	}
}
