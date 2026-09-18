package com.siegedempires.client.mixin;

import com.siegedempires.banner.BannerHelper;
import com.siegedempires.banner.BoatBannerManager;
import com.siegedempires.client.banner.BannerVisibility;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Boat/ship mast flags and war flags are tiny armor stands; vanilla
 * {@code shouldRenderAtSqrDistance} caps them around ~250 blocks, which is
 * short of high simulation distances. Keep them visible for the full sim range.
 */
@Mixin(ArmorStand.class)
public class ArmorStandBannerVisibilityMixin {
	@Inject(method = "shouldRenderAtSqrDistance", at = @At("HEAD"), cancellable = true)
	private void siegedempires$bannerSimDistance(double distance, CallbackInfoReturnable<Boolean> cir) {
		ArmorStand self = (ArmorStand) (Object) this;
		if (!isExtendedBannerStand(self)) {
			return;
		}
		cir.setReturnValue(distance < BannerVisibility.maxDistanceSqr());
	}

	private static boolean isExtendedBannerStand(ArmorStand stand) {
		if (BoatBannerManager.isBoatBanner(stand)) {
			return true;
		}
		ItemStack head = stand.getItemBySlot(EquipmentSlot.HEAD);
		return BannerHelper.isWarBanner(head);
	}
}
