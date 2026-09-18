package com.siegedempires.mixin;

import com.siegedempires.banner.BoatBannerManager;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops boat-banner stands that are no longer riding their boat.
 * Targets {@link LivingEntity#tick} because {@code ArmorStand} does not override {@code tick} in 26.2.
 */
@Mixin(LivingEntity.class)
public class ArmorStandMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void siegedempires$discardDismountedBoatBanner(CallbackInfo ci) {
		BoatBannerManager.discardIfDismountedBoatBanner((LivingEntity) (Object) this);
	}
}
