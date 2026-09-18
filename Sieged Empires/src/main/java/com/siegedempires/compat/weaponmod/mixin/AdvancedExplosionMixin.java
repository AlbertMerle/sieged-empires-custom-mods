package com.siegedempires.compat.weaponmod.mixin;

import com.siegedempires.lock.LockExplosionHandler;
import com.siegedempires.permission.ExplosionProtection;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WeaponsMod uses {@code AdvancedExplosion} instead of vanilla {@code ServerExplosion.explode()},
 * so town protection must hook its detonation path separately.
 */
@Mixin(targets = "ckathode.weaponmod.AdvancedExplosion", remap = false)
public abstract class AdvancedExplosionMixin {

	@Shadow(remap = false)
	public Vec3 center;

	@Shadow(remap = false)
	public ServerLevel serverLevel;

	@Shadow(remap = false)
	public ObjectArrayList<BlockPos> toBlow;

	@Inject(method = "doBlockExplosion", at = @At("HEAD"), cancellable = true, remap = false)
	private void siegedempires$protectTownBlocks(CallbackInfo ci) {
		if (ExplosionProtection.isExplosionSuppressedAt(serverLevel, center)) {
			ci.cancel();
			return;
		}
		ExplosionProtection.filterProtectedBlocks(serverLevel, toBlow);
		LockExplosionHandler.onBlocksExploded(serverLevel, toBlow);
	}

	@Inject(method = "doEntityExplosion", at = @At("HEAD"), cancellable = true, remap = false)
	private void siegedempires$suppressPeacefulTownEntityBlast(CallbackInfo ci) {
		if (ExplosionProtection.isExplosionSuppressedAt(serverLevel, center)) {
			ci.cancel();
		}
	}

	@Redirect(method = "doEntityExplosion(Lnet/minecraft/world/damagesource/DamageSource;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"),
			remap = false)
	private boolean siegedempires$protectTownEntities(Entity entity, ServerLevel level, DamageSource source, float amount) {
		if (!ExplosionProtection.shouldDamageEntity(level, center, entity)) {
			return false;
		}
		return entity.hurtServer(level, source, amount);
	}

	@Inject(method = "doFlaming", at = @At("HEAD"), cancellable = true, remap = false)
	private void siegedempires$protectTownFire(CallbackInfo ci) {
		if (ExplosionProtection.isExplosionSuppressedAt(serverLevel, center)) {
			ci.cancel();
			return;
		}
		ExplosionProtection.filterProtectedBlocks(serverLevel, toBlow);
	}

	@Inject(method = "doParticleExplosion", at = @At("HEAD"), cancellable = true, remap = false)
	private void siegedempires$suppressPeacefulTownEffects(CallbackInfo ci) {
		if (ExplosionProtection.isExplosionSuppressedAt(serverLevel, center)) {
			ci.cancel();
		}
	}
}
