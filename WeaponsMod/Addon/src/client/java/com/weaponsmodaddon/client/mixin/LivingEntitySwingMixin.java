package com.weaponsmodaddon.client.mixin;

import com.weaponsmodaddon.gun.GunAimState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppress vanilla arm swing while gun ADS / on gun fire so it does not override the
 * Blockbench fire clip.
 */
@Mixin(LivingEntity.class)
public class LivingEntitySwingMixin {

	@Inject(method = "swing(Lnet/minecraft/world/InteractionHand;)V", at = @At("HEAD"), cancellable = true)
	private void weaponsmodaddon$noSwingDuringGunAds(InteractionHand hand, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self instanceof LocalPlayer)) {
			return;
		}
		if (GunAimState.isAimingReadyGun(self)) {
			ci.cancel();
		}
	}
}
