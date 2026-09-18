package com.siegedempires.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.siegedempires.banner.BoatBannerManager;
import com.siegedempires.boat.VanillaBoatHealth;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBoat.class)
public abstract class AbstractBoatMixin {
	@Shadow
	protected abstract double rideHeight(EntityDimensions dimensions);

	@Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
	private void siegedempires$canAddPassenger(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
		AbstractBoat self = (AbstractBoat) (Object) this;
		if (self.isEyeInFluid(FluidTags.WATER)) {
			cir.setReturnValue(false);
			return;
		}
		if (BoatBannerManager.isBoatBanner(passenger)) {
			cir.setReturnValue(!BoatBannerManager.hasBoatBanner(self));
			return;
		}
		cir.setReturnValue(BoatBannerManager.nonBannerPassengerCount(self) < 2);
	}

	@Inject(method = "getMaxPassengers", at = @At("HEAD"), cancellable = true)
	private void siegedempires$getMaxPassengers(CallbackInfoReturnable<Integer> cir) {
		AbstractBoat self = (AbstractBoat) (Object) this;
		cir.setReturnValue(2 + (BoatBannerManager.hasBoatBanner(self) ? 1 : 0));
	}

	@Inject(method = "getPassengerAttachmentPoint", at = @At("HEAD"), cancellable = true)
	private void siegedempires$bannerAttachment(
			Entity passenger,
			EntityDimensions dimensions,
			float scale,
			CallbackInfoReturnable<Vec3> cir) {
		if (!BoatBannerManager.isBoatBanner(passenger)) {
			return;
		}
		AbstractBoat self = (AbstractBoat) (Object) this;
		cir.setReturnValue(BoatBannerManager.boatBannerAttachmentPoint(self, (float) this.rideHeight(dimensions)));
	}

	@Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
	private void siegedempires$skipBannerController(CallbackInfoReturnable<LivingEntity> cir) {
		AbstractBoat self = (AbstractBoat) (Object) this;
		for (Entity passenger : self.getPassengers()) {
			if (BoatBannerManager.isBoatBanner(passenger)) {
				continue;
			}
			if (passenger instanceof LivingEntity living) {
				cir.setReturnValue(living);
				return;
			}
		}
		cir.setReturnValue(null);
	}

	/**
	 * Solo riders (plus a banner) should use the centered seat, not the rear offset
	 * that vanilla applies whenever {@code passengers.size() > 1}.
	 */
	@ModifyExpressionValue(
			method = "getPassengerAttachmentPoint",
			at = @At(value = "INVOKE", target = "Ljava/util/List;size()I")
	)
	private int siegedempires$ignoreBannerInSeatCount(int original) {
		AbstractBoat self = (AbstractBoat) (Object) this;
		return BoatBannerManager.nonBannerPassengerCount(self);
	}

	@WrapOperation(
			method = "getPassengerAttachmentPoint",
			at = @At(value = "INVOKE", target = "Ljava/util/List;indexOf(Ljava/lang/Object;)I")
	)
	private int siegedempires$bannerAwareSeatIndex(java.util.List<?> passengers, Object passenger, Operation<Integer> original) {
		if (!(passenger instanceof Entity entity) || BoatBannerManager.isBoatBanner(entity)) {
			return original.call(passengers, passenger);
		}
		int index = 0;
		for (Object entry : passengers) {
			if (!(entry instanceof Entity other) || BoatBannerManager.isBoatBanner(other)) {
				continue;
			}
			if (other == entity) {
				return index;
			}
			index++;
		}
		return original.call(passengers, passenger);
	}

	@Inject(method = "remove", at = @At("HEAD"))
	private void siegedempires$discardBannersOnRemove(Entity.RemovalReason reason, CallbackInfo ci) {
		AbstractBoat self = (AbstractBoat) (Object) this;
		BoatBannerManager.discardBoatBanners(self);
		VanillaBoatHealth.clearStormTimer(self.getUUID());
	}

	/**
	 * Shippy Ships: restore the visual flag from persisted claim data if the armor
	 * stand is missing. Vanilla boats: strip any leftover banner stands (flags are
	 * ships-only). Does not claim ownership.
	 */
	@Inject(method = "tick", at = @At("TAIL"))
	private void siegedempires$restoreBannerVisual(CallbackInfo ci) {
		AbstractBoat self = (AbstractBoat) (Object) this;
		VanillaBoatHealth.tickStormDamage(self);
		if (self.level().isClientSide()) {
			return;
		}
		if (VanillaBoatHealth.isVanillaBoat(self)) {
			if (BoatBannerManager.hasBoatBanner(self)) {
				BoatBannerManager.discardBoatBanners(self);
			}
			return;
		}
		if (!BoatBannerManager.hasBannerClaim(self) || BoatBannerManager.hasBoatBanner(self)) {
			return;
		}
		if (self.tickCount % 20 != 0) {
			return;
		}
		BoatBannerManager.ensureBannerVisual(self);
	}
}
