package com.weaponsmodaddon.client.mixin;

import com.weaponsmodaddon.client.gun.GunAimClient;
import com.weaponsmodaddon.gun.GunReload;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sticky ADS / sticky reload: releasing the use key does not stop using a ready gun
 * or cancel an in-progress reload (including the brief {@code STATE_RELOADED} finish).
 * Fire is left-click; right-click again untoggles aim ({@link GunAimClient}).
 * Hotbar swap still cancels via {@code LivingEntity.stopUsingItem}.
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

	@Inject(method = "releaseUsingItem", at = @At("HEAD"), cancellable = true)
	private void weaponsmodaddon$stickyGunAim(Player player, CallbackInfo ci) {
		if (!(player instanceof LocalPlayer local)) {
			return;
		}
		if (GunAimClient.shouldHoldAim(local) || GunReload.isReloading(local)) {
			ci.cancel();
		}
	}
}
