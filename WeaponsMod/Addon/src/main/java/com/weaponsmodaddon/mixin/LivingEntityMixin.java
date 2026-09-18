package com.weaponsmodaddon.mixin;

import com.weaponsmodaddon.ModItemTags;
import com.weaponsmodaddon.config.AddonConfig;
import com.weaponsmodaddon.damage.ProjectileDamage;
import com.weaponsmodaddon.gun.GunAimState;
import com.weaponsmodaddon.gun.GunReload;
import com.weaponsmodaddon.headshot.HeadshotContext;
import com.weaponsmodaddon.headshot.MeleeHeadshot;
import com.weaponsmodaddon.spear.SpearChargeThrow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Only while gun ADS / spear charging: zero head/body lag so the player faces the crosshair.
 * Holding without aiming uses vanilla body rotation.
 * <p>
 * Also applies pending player headshot damage / blindness from {@link HeadshotContext},
 * reload slowdown, and unload-on-interrupt for sticky gun reload.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

	@Inject(method = "getMaxHeadRotationRelativeToBody", at = @At("HEAD"), cancellable = true)
	private void weaponsmodaddon$aimBodyLock(CallbackInfoReturnable<Float> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (GunAimState.shouldLockBodyToLook(self)) {
			cir.setReturnValue(0.0F);
		}
	}

	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float weaponsmodaddon$projectileDamage(float damage, ServerLevel level, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		float modified = damage;
		if (HeadshotContext.isPendingFor(self)) {
			modified = HeadshotContext.applyAndModifyDamage(self, modified);
		} else {
			modified = MeleeHeadshot.apply(modified, self, source);
		}
		AddonConfig cfg = AddonConfig.get();
		if (cfg.doubleDamage
				&& cfg.doubleDamageEnabled.matches(self, false)
				&& ProjectileDamage.isGunOrArrow(source)) {
			modified *= 2.0f;
		}
		return modified;
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void weaponsmodaddon$reloadSlowdown(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof Player player) {
			GunReload.tickSlowdown(player);
			// Drop javelin/spear charge if player leaves stand/sneak (crawl, swim, etc.).
			if (player.isUsingItem()) {
				ItemStack useItem = player.getUseItem();
				if ((useItem.is(ModItemTags.SPEARS) || useItem.is(ModItemTags.JAVELINS))
						&& !SpearChargeThrow.canCharge(player)) {
					player.stopUsingItem();
				}
			}
		}
	}

	/**
	 * Hotbar swap / item change calls {@code stopUsingItem} without {@code releaseUsing}.
	 * Force incomplete gun reloads back to unloaded.
	 */
	@Inject(method = "stopUsingItem", at = @At("HEAD"))
	private void weaponsmodaddon$cancelIncompleteGunReload(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.isUsingItem()) {
			return;
		}
		ItemStack useItem = self.getUseItem();
		GunReload.onStopUsing(self, useItem);
	}
}
