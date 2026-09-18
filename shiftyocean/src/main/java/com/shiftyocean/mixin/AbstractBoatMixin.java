package com.shiftyocean.mixin;

import com.shiftyocean.OceanCurrentHandler;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stacks ocean current onto boat/ship delta immediately before {@code move},
 * on the authoritative side (client while driven, server when empty). Player
 * paddle / Shippy sail input remains in the same delta and is not overwritten.
 */
@Mixin(AbstractBoat.class)
public abstract class AbstractBoatMixin {
	@Inject(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
					shift = At.Shift.BEFORE
			)
	)
	private void shiftyocean$beforeMove(CallbackInfo ci) {
		OceanCurrentHandler.applyAuthoritativeBoatCurrent((AbstractBoat) (Object) this);
	}
}
