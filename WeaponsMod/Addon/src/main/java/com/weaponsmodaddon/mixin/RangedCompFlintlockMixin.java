package com.weaponsmodaddon.mixin;

import ckathode.weaponmod.item.RangedCompFlintlock;
import com.weaponsmodaddon.gun.GunHitscanFire;
import com.weaponsmodaddon.sound.GunFireEffects;
import com.weaponsmodaddon.sound.ModSounds;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replace vanilla explode flintlock fire with {@link ModSounds#FLINTLOCK_FIRE};
 * drop comparator click when reload finishes; no fire knockback.
 */
@Mixin(RangedCompFlintlock.class)
public abstract class RangedCompFlintlockMixin {

	@Inject(method = "effectShoot", at = @At("HEAD"), cancellable = true)
	private void weaponsmodaddon$customFlintlockFire(
			Level world,
			double x,
			double y,
			double z,
			float yaw,
			float pitch,
			CallbackInfo ci
	) {
		GunFireEffects.playBang(world, x, y, z, yaw, pitch, ModSounds.FLINTLOCK_FIRE);
		ci.cancel();
	}

	@Inject(method = "effectPlayer", at = @At("HEAD"), cancellable = true)
	private void weaponsmodaddon$noFlintlockRecoil(
			ItemStack itemstack,
			Player entityplayer,
			Level world,
			CallbackInfo ci
	) {
		ci.cancel();
	}

	@Inject(method = "effectReloadDone", at = @At("HEAD"), cancellable = true)
	private void weaponsmodaddon$noComparatorOnReloadDone(
			ItemStack itemstack,
			Level world,
			LivingEntity entityliving,
			CallbackInfo ci
	) {
		entityliving.swing(InteractionHand.MAIN_HAND);
		ci.cancel();
	}

	@Redirect(
			method = "fire",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"
			)
	)
	private boolean weaponsmodaddon$hitscanInsteadOfBullet(Level world, Entity entity) {
		return GunHitscanFire.redirectAddEntity(world, entity);
	}
}
