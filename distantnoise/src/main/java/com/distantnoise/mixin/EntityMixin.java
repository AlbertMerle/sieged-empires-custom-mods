package com.distantnoise.mixin;

import com.distantnoise.config.DistantNoiseConfig;
import com.distantnoise.sound.CavePlayerNoises;
import com.distantnoise.sound.DeathNoiseRelay;
import com.distantnoise.sound.DeathSounds;
import com.distantnoise.sound.FootstepNoiseRelay;
import com.distantnoise.sound.HorseSounds;
import com.distantnoise.sound.HorseVocals;
import com.distantnoise.sound.PlantGrassWalk;
import com.distantnoise.sound.RunningFootsteps;
import com.distantnoise.sound.SurfaceSprintSteps;
import com.distantnoise.sound.VocalAnimals;
import com.distantnoise.sound.VocalNoiseRelay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace vanilla steps/vocals for relayed entities with distance-scaled sounds. */
@Mixin(Entity.class)
public class EntityMixin {
	@Inject(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at = @At("HEAD"), cancellable = true)
	private void distantnoise$vocalRelay(SoundEvent event, float volume, float pitch, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (self.level().isClientSide() || !(self instanceof LivingEntity living)) {
			return;
		}

		DistantNoiseConfig cfg = DistantNoiseConfig.get();
		if (!cfg.enabled) {
			return;
		}

		if (living instanceof ServerPlayer player
				&& CavePlayerNoises.isFallDamageSound(event)
				&& CavePlayerNoises.shouldRelayFall(player, cfg)) {
			ci.cancel();
			FootstepNoiseRelay.relayCaveFall(player, event, volume, pitch);
			return;
		}

		if (DeathSounds.isCustomDeath(event) && DeathSounds.enabled(cfg)) {
			ci.cancel();
			DeathNoiseRelay.relay(living, volume, pitch);
			return;
		}

		if (cfg.horseSoundsEnabled && HorseSounds.isHorse(living)) {
			if (HorseSounds.isRunSound(event)) {
				ci.cancel();
				FootstepNoiseRelay.relayHorseRun(living, volume, pitch);
				return;
			}
			if (HorseSounds.isNeighSound(event) || HorseSounds.isSnortSound(event)) {
				HorseVocals.relayExtra(living, event, volume, pitch);
				return;
			}
		}

		if (!cfg.vocalAnimals || !VocalAnimals.shouldRelayVocal(living, event)) {
			return;
		}

		ci.cancel();
		VocalNoiseRelay.relay(living, event, volume, pitch);
	}

	@Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
	private void distantnoise$stompRelay(BlockPos pos, BlockState state, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (self.level().isClientSide() || !(self instanceof LivingEntity living)) {
			return;
		}

		DistantNoiseConfig cfg = DistantNoiseConfig.get();
		if (!cfg.enabled) {
			return;
		}

		if (living instanceof Player player && CavePlayerNoises.shouldRelaySteps(player, cfg)) {
			ci.cancel();
			FootstepNoiseRelay.relayCaveStep(player, state);
			return;
		}

		// Footstep-driven (not tick): grasswalk when moving through plants / crops / leaf litter.
		if (living instanceof Player player && PlantGrassWalk.shouldRelay(player, state, cfg)) {
			ci.cancel();
			FootstepNoiseRelay.relayPlantGrassWalk(player, state);
			return;
		}

		if (living instanceof Player player && SurfaceSprintSteps.shouldRelay(player, cfg)) {
			ci.cancel();
			FootstepNoiseRelay.relaySurfaceSprint(player, state);
			return;
		}

		if (!cfg.footstepsEnabled) {
			return;
		}

		Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType());
		if (cfg.isSnakeEntity(typeId)) {
			ci.cancel();
			return;
		}

		if (cfg.isStompingEntity(typeId)) {
			ci.cancel();
			FootstepNoiseRelay.relayStomp(living, state);
			return;
		}

		if (cfg.runningFootstepsEnabled && RunningFootsteps.shouldRelay(living, cfg)) {
			ci.cancel();
			FootstepNoiseRelay.relayRunning(living, state);
		}
	}
}
