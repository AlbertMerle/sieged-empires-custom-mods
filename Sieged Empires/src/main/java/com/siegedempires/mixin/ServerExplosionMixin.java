package com.siegedempires.mixin;

import com.siegedempires.lock.LockExplosionHandler;
import com.siegedempires.permission.ExplosionProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Protects town claims from explosions until an invasion is active, and drops
 * locks on any blocks that still get destroyed during a siege.
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {

	@Shadow
	@Final
	private ServerLevel level;

	@Inject(method = "explode", at = @At("HEAD"), cancellable = true)
	private void siegedempires$suppressPeacefulTownExplosions(CallbackInfoReturnable<Integer> cir) {
		ServerExplosion self = (ServerExplosion) (Object) this;
		if (ExplosionProtection.isExplosionSuppressedAt(this.level, self.center())) {
			cir.setReturnValue(0);
		}
	}

	@Redirect(method = "hurtEntities", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
	private boolean siegedempires$protectTownEntities(Entity entity, ServerLevel level, DamageSource source, float amount) {
		ServerExplosion self = (ServerExplosion) (Object) this;
		if (!ExplosionProtection.shouldDamageEntity(level, self.center(), entity)) {
			return false;
		}
		return entity.hurtServer(level, source, amount);
	}

	@Inject(method = "interactWithBlocks", at = @At("HEAD"))
	private void siegedempires$protectTownsAndDropLocks(List<BlockPos> targetBlocks, CallbackInfo ci) {
		ExplosionProtection.filterProtectedBlocks(this.level, targetBlocks);
		LockExplosionHandler.onBlocksExploded(this.level, targetBlocks);
	}

	@Inject(method = "createFire", at = @At("HEAD"))
	private void siegedempires$protectTownsFromFire(List<BlockPos> targetBlocks, CallbackInfo ci) {
		ExplosionProtection.filterProtectedBlocks(this.level, targetBlocks);
	}
}
