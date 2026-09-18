package com.siegedempires.mixin;

import com.siegedempires.banner.BoatBannerManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Claim / restore Shippy Ships banners when a player boards (vanilla boats ignored).
 * Ownership is stamped once and never replaced by later passengers.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "addPassenger", at = @At("TAIL"))
	private void siegedempires$claimBoatBannerOnBoard(Entity passenger, CallbackInfo ci) {
		if (!(passenger instanceof Player player)) {
			return;
		}
		Entity self = (Entity) (Object) this;
		if (self instanceof AbstractBoat boat) {
			BoatBannerManager.onPlayerEnter(boat, player);
		}
	}
}
