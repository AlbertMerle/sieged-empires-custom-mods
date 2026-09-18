package com.siegedempires.mixin;

import com.siegedempires.permission.ExplosionProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TntBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents TNT from being primed inside town claims until an invasion is active.
 */
@Mixin(TntBlock.class)
public abstract class TntBlockMixin {

	@Inject(method = "prime(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
	private static void siegedempires$blockPeacefulTownTnt(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (level instanceof ServerLevel serverLevel && ExplosionProtection.isBlockProtected(serverLevel, pos)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "prime(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;)Z",
			at = @At("HEAD"), cancellable = true)
	private static void siegedempires$blockPeacefulTownTntWithOwner(Level level, BlockPos pos, LivingEntity owner,
	                                                                  CallbackInfoReturnable<Boolean> cir) {
		if (level instanceof ServerLevel serverLevel && ExplosionProtection.isBlockProtected(serverLevel, pos)) {
			cir.setReturnValue(false);
		}
	}
}
