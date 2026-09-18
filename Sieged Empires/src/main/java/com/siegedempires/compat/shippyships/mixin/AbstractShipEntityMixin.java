package com.siegedempires.compat.shippyships.mixin;

import com.caesiusleo.ingeniumapi.api.ShipMountable;
import com.caesiusleo.shippyships.AbstractShipEntity;
import com.caesiusleo.shippyships.entities.caravel.CaravelEntity;
import com.siegedempires.banner.BoatBannerManager;
import com.siegedempires.config.ModSettings;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Town/empire banner support for Shippy Ships vessels.
 * Ships override boat passenger APIs, so vanilla {@code AbstractBoat} mixins do not apply.
 * Ownership is claimed only when a player boards (see {@link BoatBannerManager#onPlayerEnter}).
 * Also sets absolute full-sail max speed (open sail remains a 0–100% scale of that cap).
 */
@Mixin(AbstractShipEntity.class)
public abstract class AbstractShipEntityMixin {
	/**
	 * Shippy Ships: {@code currentSpeed} caps at {@code getMaxSpeed() * (openSail/100) * weather}.
	 * Replace {@code getMaxSpeed} with configured BPS/20 so full sail hits a fixed top speed
	 * (wood/crew bonuses from the original formula no longer change the cap).
	 */
	@Inject(method = "getMaxSpeed", at = @At("HEAD"), cancellable = true)
	private void siegedempires$shipFullSailMaxSpeed(CallbackInfoReturnable<Float> cir) {
		AbstractShipEntity self = (AbstractShipEntity) (Object) this;
		ModSettings settings = ModSettings.get();
		float bps = self instanceof CaravelEntity
				? settings.shippyCaravelFullSailBps
				: settings.shippySailboatCogFullSailBps;
		cir.setReturnValue(bps / 20.0F);
	}

	/**
	 * Restore the visual flag from persisted claim data if the armor stand is missing.
	 */
	@Inject(method = "tick", at = @At("TAIL"))
	private void siegedempires$restoreShipBannerVisual(CallbackInfo ci) {
		AbstractShipEntity self = (AbstractShipEntity) (Object) this;
		if (self.level().isClientSide() || self.getConstructionStatus()) {
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

	@Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
	private void siegedempires$allowShipBanner(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
		if (!BoatBannerManager.isBoatBanner(passenger)) {
			return;
		}
		AbstractShipEntity self = (AbstractShipEntity) (Object) this;
		cir.setReturnValue(!self.getConstructionStatus() && !BoatBannerManager.hasBoatBanner(self));
	}

	/**
	 * Keep the banner from consuming a crew seat for real passengers.
	 */
	@Inject(method = "canAddPassenger", at = @At("RETURN"), cancellable = true)
	private void siegedempires$ignoreBannerInCrewCount(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() || BoatBannerManager.isBoatBanner(passenger) || passenger instanceof ShipMountable) {
			return;
		}
		AbstractShipEntity self = (AbstractShipEntity) (Object) this;
		if (self.getConstructionStatus() || !BoatBannerManager.hasBoatBanner(self)) {
			return;
		}
		int crew = 0;
		for (Entity riding : self.getPassengers()) {
			if (BoatBannerManager.isBoatBanner(riding) || riding instanceof ShipMountable) {
				continue;
			}
			crew++;
		}
		if (crew < self.getMaxCrewSlot()) {
			cir.setReturnValue(true);
		}
	}

	/**
	 * Pin the banner at local {@code (0, y, 0)} mast height (F3+B hitbox-aligned),
	 * rotated by ship pitch/roll/yaw. Also hooks attachment-point lookup used by
	 * {@code Entity#positionRider} on client and server.
	 */
	@Inject(method = "getPassengerAttachmentPoint", at = @At("HEAD"), cancellable = true)
	private void siegedempires$shipBannerAttachmentPoint(
			Entity passenger,
			EntityDimensions dimensions,
			float scale,
			CallbackInfoReturnable<Vec3> cir) {
		if (!BoatBannerManager.isBoatBanner(passenger)) {
			return;
		}
		AbstractShipEntity self = (AbstractShipEntity) (Object) this;
		cir.setReturnValue(BoatBannerManager.shipBannerAttachmentPoint(
				(AbstractBoat) self, self.getPitch(), self.getRoll(), self.getYRot()));
	}

	/**
	 * Pin the banner at mast height each tick. Cancels ship seating logic.
	 */
	@Inject(method = "positionRider", at = @At("HEAD"), cancellable = true)
	private void siegedempires$positionShipBanner(Entity passenger, Entity.MoveFunction moveFunction, CallbackInfo ci) {
		if (!BoatBannerManager.isBoatBanner(passenger)) {
			return;
		}
		AbstractShipEntity self = (AbstractShipEntity) (Object) this;
		if (!self.hasPassenger(passenger)) {
			return;
		}
		Vec3 offset = BoatBannerManager.shipBannerAttachmentPoint(
				(AbstractBoat) self, self.getPitch(), self.getRoll(), self.getYRot());
		moveFunction.accept(
				passenger,
				self.getX() + offset.x,
				self.getY() + offset.y,
				self.getZ() + offset.z);
		passenger.setYRot(self.getYRot());
		passenger.setYHeadRot(self.getYRot());
		ci.cancel();
	}
}
