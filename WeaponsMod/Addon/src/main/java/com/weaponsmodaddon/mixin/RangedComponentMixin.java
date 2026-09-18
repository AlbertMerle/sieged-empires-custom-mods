package com.weaponsmodaddon.mixin;

import ckathode.weaponmod.ReloadHelper;
import ckathode.weaponmod.item.RangedComponent;
import com.weaponsmodaddon.config.AddonConfig;
import com.weaponsmodaddon.gun.GunAimState;
import com.weaponsmodaddon.gun.GunHitscanFire;
import com.weaponsmodaddon.gun.GunReload;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RangedComponent.class)
public abstract class RangedComponentMixin {

	@Inject(method = "getReloadDuration", at = @At("HEAD"), cancellable = true)
	private void weaponsmodaddon$configReloadDuration(
			ItemStack stack,
			CallbackInfoReturnable<Integer> cir
	) {
		int ticks = AddonConfig.get().reloadTicksFor(stack);
		if (ticks >= 0) {
			cir.setReturnValue(ticks);
		}
	}

	/**
	 * Sticky reload: once WeaponMod reaches {@code STATE_RELOADED}, promote to ready and
	 * end use so releasing RMB is not required (and so the player does not enter ADS).
	 */
	@Inject(method = "onUsingTick", at = @At("RETURN"))
	private void weaponsmodaddon$finishStickyReload(
			Level level,
			LivingEntity livingEntity,
			ItemStack stack,
			int remainingUseDuration,
			CallbackInfo ci
	) {
		GunReload.finishReloadIfNeeded(livingEntity, stack);
	}

	@Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
	private void weaponsmodaddon$aimReleaseNoAutoFire(
			ItemStack stack,
			Level level,
			LivingEntity entity,
			int timeLeft,
			CallbackInfoReturnable<Boolean> cir
	) {
		if (!GunAimState.isGunStack(stack)) {
			return;
		}
		if (!RangedComponent.isReloaded(stack)) {
			return;
		}
		if (!RangedComponent.isReadyToFire(stack)) {
			return;
		}

		RangedComponent self = (RangedComponent) (Object) this;
		if (GunAimState.consumeFireRequest(entity)) {
			if (self.hasAmmoAndConsume(stack, level, entity)) {
				if (entity instanceof net.minecraft.server.level.ServerPlayer sp && GunHitscanFire.usesHitscan(stack)) {
					GunHitscanFire.fire(sp, stack, self, sp.getYRot(), sp.getXRot(), timeLeft);
				} else {
					self.fire(stack, level, entity, timeLeft);
				}
			}
			RangedComponent.setReloadState(stack, ReloadHelper.ReloadState.STATE_NONE);
			cir.setReturnValue(true);
			return;
		}

		if (entity instanceof Player) {
			GunAimState.markSkipFireAnim(entity);
		}
		cir.setReturnValue(true);
	}
}
