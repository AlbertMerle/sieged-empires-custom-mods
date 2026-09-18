package com.weaponsmodaddon.mixin;

import ckathode.weaponmod.entity.projectile.EntityBlunderShot;
import ckathode.weaponmod.entity.projectile.EntityJavelin;
import ckathode.weaponmod.entity.projectile.EntityMortarShell;
import ckathode.weaponmod.entity.projectile.EntityMusketBullet;
import ckathode.weaponmod.entity.projectile.EntityProjectile;
import ckathode.weaponmod.entity.projectile.EntitySpear;
import com.weaponsmodaddon.headshot.HeadshotContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks head hits for gun projectiles and thrown spears / javelins.
 */
@Mixin(EntityProjectile.class)
public abstract class EntityProjectileHeadshotMixin {

	@Inject(method = "onHit", at = @At("HEAD"))
	private void weaponsmodaddon$prepareGunHeadshot(HitResult result, CallbackInfo ci) {
		if (!(result instanceof EntityHitResult entityHit)) {
			return;
		}
		Object self = this;
		if (self instanceof EntityMusketBullet
				|| self instanceof EntityBlunderShot
				|| self instanceof EntityMortarShell) {
			HeadshotContext.prepare(entityHit, HeadshotContext.Kind.GUN);
		} else if (self instanceof EntitySpear || self instanceof EntityJavelin) {
			HeadshotContext.prepare(entityHit, HeadshotContext.Kind.SPEAR);
		}
	}

	@Inject(method = "onHit", at = @At("RETURN"))
	private void weaponsmodaddon$clearGunHeadshot(HitResult result, CallbackInfo ci) {
		HeadshotContext.clear();
	}
}
