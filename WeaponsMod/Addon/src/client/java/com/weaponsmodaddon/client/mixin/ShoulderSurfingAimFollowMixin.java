package com.weaponsmodaddon.client.mixin;

import com.github.exopandora.shouldersurfing.client.ShoulderSurfing;
import com.weaponsmodaddon.client.scope.ScopedMusketAimClient;
import com.weaponsmodaddon.gun.GunAimState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * With SS {@code STATIC} crosshair, look-at-crosshair only runs for {@code ADAPTIVE}/{@code DYNAMIC}
 * ({@code isAimingDecoupled}). That leaves shoulder-cam ADS firing parallel to the camera ray
 * from the eyes — crosshair ≠ impact.
 * <p>
 * Force look-following while our gun ADS / spear charge so SS aims the player at the camera
 * crosshair world point. Scoped muskets use temporary first person (no offset) and skip this.
 */
@Mixin(ShoulderSurfing.class)
public class ShoulderSurfingAimFollowMixin {

	@Inject(method = "computeIsLookFollowingCrosshairTarget", at = @At("HEAD"), cancellable = true)
	private static void weaponsmodaddon$forceLookFollowWhileAiming(
			Entity entity,
			boolean isAiming,
			CallbackInfoReturnable<Boolean> cir
	) {
		if (!(entity instanceof LivingEntity living)) {
			return;
		}
		if (!GunAimState.shouldLockBodyToLook(living)) {
			return;
		}
		if (ScopedMusketAimClient.isAimingScoped(living)) {
			return;
		}
		cir.setReturnValue(true);
	}
}
