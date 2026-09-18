package com.siegedempires.compat.shippyships.mixin;

import com.caesiusleo.shippyships.entities.cog.CogEntity;
import com.siegedempires.banner.BoatBannerManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cog overrides {@code getPassengerAttachmentPoint}; banner mast logic must run here too. */
@Mixin(CogEntity.class)
public abstract class CogEntityMixin {
	@Inject(method = "getPassengerAttachmentPoint", at = @At("HEAD"), cancellable = true)
	private void siegedempires$cogBannerAttachmentPoint(
			Entity passenger,
			EntityDimensions dimensions,
			float scale,
			CallbackInfoReturnable<Vec3> cir) {
		if (!BoatBannerManager.isBoatBanner(passenger)) {
			return;
		}
		CogEntity self = (CogEntity) (Object) this;
		cir.setReturnValue(BoatBannerManager.shipBannerAttachmentPoint(
				(AbstractBoat) self, self.getPitch(), self.getRoll(), self.getYRot()));
	}
}
