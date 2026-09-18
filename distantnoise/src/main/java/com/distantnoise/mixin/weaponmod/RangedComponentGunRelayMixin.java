package com.distantnoise.mixin.weaponmod;

import ckathode.weaponmod.item.RangedComponent;
import com.distantnoise.sound.DistantNoiseKind;
import com.distantnoise.sound.GunKindResolver;
import com.distantnoise.sound.GunShotRelay;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Central hook for every WeaponMod gun fire (musket, flintlock, blunderbuss, mortar,
 * crossbow, blowgun, …). Runs on {@code postShootingEffects} before {@code effectShoot},
 * so WeaponsMod Addon cancelling {@code effectShoot} cannot block the relay.
 */
@Mixin(RangedComponent.class)
public class RangedComponentGunRelayMixin {
	@Inject(method = "postShootingEffects", at = @At("HEAD"))
	private void distantnoise$relayGunShot(
			ItemStack stack,
			LivingEntity entityLiving,
			Level world,
			CallbackInfo ci
	) {
		if (!(entityLiving instanceof ServerPlayer shooter)) {
			return;
		}
		RangedComponent self = (RangedComponent) (Object) this;
		DistantNoiseKind kind = GunKindResolver.from(self);
		if (kind != null) {
			GunShotRelay.relay(shooter, kind);
		}
	}
}
