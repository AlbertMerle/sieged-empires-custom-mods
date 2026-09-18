package com.distantnoise.mixin;

import com.distantnoise.config.DistantNoiseConfig;
import com.distantnoise.sound.CaveEnclosure;
import com.distantnoise.sound.DeathSounds;
import com.distantnoise.sound.FootstepNoiseRelay;
import com.distantnoise.sound.GrassRustling;
import com.distantnoise.sound.SnakeMovement;
import com.distantnoise.sound.VocalAnimals;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Unique
	private boolean distantnoise$grassRustling;

	@Unique
	private boolean distantnoise$grassRustlingFleeing;

	@Unique
	private double distantnoise$snakeMoveDist;

	@Unique
	private double distantnoise$lastSnakeX;

	@Unique
	private double distantnoise$lastSnakeY;

	@Unique
	private double distantnoise$lastSnakeZ;

	@Unique
	private boolean distantnoise$snakePosInitialized;

	@Inject(method = "tick", at = @At("TAIL"))
	private void distantnoise$tickGrassRustling(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.level().isClientSide()) {
			return;
		}

		DistantNoiseConfig cfg = DistantNoiseConfig.get();

		if (cfg.enabled && cfg.snakeMovementEnabled && SnakeMovement.isSnake(self, cfg)) {
			distantnoise$tickSnake(self, cfg);
		}

		if (self.tickCount % 10 != 0) {
			return;
		}

		if (!cfg.enabled || !cfg.grassRussling || !VocalAnimals.isVocalAnimal(self)) {
			if (distantnoise$grassRustling) {
				distantnoise$grassRustling = false;
				GrassRustling.stop(self);
			}
			return;
		}

		boolean shouldRustle = GrassRustling.shouldRustle(self);
		if (!shouldRustle) {
			if (distantnoise$grassRustling) {
				distantnoise$grassRustling = false;
				GrassRustling.stop(self);
			}
			return;
		}

		boolean fleeing = GrassRustling.isFleeing(self);
		if (distantnoise$grassRustling && fleeing == distantnoise$grassRustlingFleeing) {
			return;
		}

		if (distantnoise$grassRustling) {
			GrassRustling.stop(self);
		}
		distantnoise$grassRustling = true;
		distantnoise$grassRustlingFleeing = fleeing;
		GrassRustling.start(self, fleeing);
	}

	@Unique
	private void distantnoise$tickSnake(LivingEntity self, DistantNoiseConfig cfg) {
		double currentX = self.getX();
		double currentY = self.getY();
		double currentZ = self.getZ();

		if (!distantnoise$snakePosInitialized) {
			distantnoise$lastSnakeX = currentX;
			distantnoise$lastSnakeY = currentY;
			distantnoise$lastSnakeZ = currentZ;
			distantnoise$snakePosInitialized = true;
			return;
		}

		double dx = currentX - distantnoise$lastSnakeX;
		double dy = currentY - distantnoise$lastSnakeY;
		double dz = currentZ - distantnoise$lastSnakeZ;

		distantnoise$lastSnakeX = currentX;
		distantnoise$lastSnakeY = currentY;
		distantnoise$lastSnakeZ = currentZ;

		if (self.isPassenger()) {
			distantnoise$snakeMoveDist = 0.0;
			return;
		}

		double distSq = dx * dx + dz * dz;
		if (distSq > 25.0) {
			distantnoise$snakeMoveDist = 0.0;
			return;
		}

		if (distSq > 1.0E-4) {
			distantnoise$snakeMoveDist += Math.sqrt(distSq);
			if (distantnoise$snakeMoveDist >= SnakeMovement.STEP_DISTANCE) {
				distantnoise$snakeMoveDist = 0.0;
				var belowPos = self.getOnPosLegacy();
				var blockState = self.level().getBlockState(belowPos);
				FootstepNoiseRelay.relaySnake(self, blockState);
			}
		}
	}

	@Inject(method = "getDeathSound", at = @At("RETURN"), cancellable = true)
	private void distantnoise$customDeathSound(CallbackInfoReturnable<SoundEvent> cir) {
		if (DeathSounds.enabled(DistantNoiseConfig.get())) {
			cir.setReturnValue(DeathSounds.deathEvent());
		}
	}

	@Inject(method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", at = @At("HEAD"))
	private void distantnoise$stopGrassRustlingOnRemove(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		CaveEnclosure.clear(self.getUUID());
		if (distantnoise$grassRustling) {
			distantnoise$grassRustling = false;
			GrassRustling.stop(self);
		}
	}
}
