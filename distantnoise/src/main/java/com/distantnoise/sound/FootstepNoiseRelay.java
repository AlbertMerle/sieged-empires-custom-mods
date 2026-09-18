package com.distantnoise.sound;

import com.distantnoise.config.DistantNoiseConfig;
import com.distantnoise.network.DistantFootstepPayload;
import com.distantnoise.network.ModNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Server-side relay for extended-range mob footfalls, cave echoes, and surface sprint steps. */
public final class FootstepNoiseRelay {
	private FootstepNoiseRelay() {
	}

	public static void relayStomp(LivingEntity entity, BlockState steppedOn) {
		relay(entity, steppedOn, FootstepRelayKind.STOMP);
	}

	public static void relayRunning(LivingEntity entity, BlockState steppedOn) {
		relay(entity, steppedOn, FootstepRelayKind.RUNNING);
	}

	public static void relayCaveStep(Player player, BlockState steppedOn) {
		FootstepRelayKind kind = CavePlayerNoises.stepKind(player);
		relay(player, steppedOn, kind);
	}

	public static void relaySurfaceSprint(Player player, BlockState steppedOn) {
		relay(player, steppedOn, FootstepRelayKind.SURFACE_SPRINT);
	}

	public static void relayPlantGrassWalk(Player player, BlockState steppedOn) {
		relay(player, steppedOn, PlantGrassWalk.kindFor(player));
	}

	public static void relaySnake(LivingEntity entity, BlockState steppedOn) {
		relay(entity, steppedOn, FootstepRelayKind.SNAKE);
	}

	public static void relayHorseRun(LivingEntity entity, float volume, float pitch) {
		if (entity.level().isClientSide() || !(entity.level() instanceof ServerLevel level)) {
			return;
		}

		DistantNoiseConfig cfg = DistantNoiseConfig.get();
		if (!cfg.enabled || !cfg.horseSoundsEnabled || !cfg.footstepsEnabled) {
			return;
		}
		if (!HorseSounds.isHorse(entity)) {
			return;
		}

		FootstepProfile runProfile = new FootstepProfile(HorseSounds.runSoundId(), Math.max(0.35f, volume), pitch);
		broadcast(level, entity, runProfile, cfg.horse.maxVolumeRange, FootstepRelayKind.HORSE_RUN, false);

		if (HorseSounds.hasBodyArmor(entity)) {
			FootstepProfile armorProfile = new FootstepProfile(HorseSounds.armorJingleSoundId(), 0.85f, pitch);
			broadcast(level, entity, armorProfile, cfg.horseArmor.maxVolumeRange, FootstepRelayKind.HORSE_ARMOR, false);
		}
	}

	public static void relayCaveFall(Player player, SoundEvent event, float volume, float pitch) {
		if (player.level().isClientSide() || !(player.level() instanceof ServerLevel level)) {
			return;
		}

		DistantNoiseConfig cfg = DistantNoiseConfig.get();
		if (!CavePlayerNoises.shouldRelayFall(player, cfg)) {
			return;
		}

		Optional<FootstepProfile> profileOpt = CavePlayerNoises.fallProfile(event, volume, pitch);
		if (profileOpt.isEmpty()) {
			return;
		}
		broadcast(level, player, profileOpt.get(), cfg.caveFall.maxVolumeRange, FootstepRelayKind.CAVE_FALL, true);
	}

	private static void relay(LivingEntity entity, BlockState steppedOn, FootstepRelayKind kind) {
		if (entity.level().isClientSide() || !(entity.level() instanceof ServerLevel level)) {
			return;
		}

		DistantNoiseConfig cfg = DistantNoiseConfig.get();
		if (!cfg.enabled) {
			return;
		}

		double maxRange;
		Optional<FootstepProfile> profileOpt;
		if (kind == FootstepRelayKind.STOMP) {
			if (!cfg.footstepsEnabled) {
				return;
			}
			var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
			Double dist = cfg.getStompingDistance(typeId);
			if (dist == null) {
				return;
			}
			maxRange = dist;
			profileOpt = StompingFootsteps.forEntity(entity, steppedOn);
		} else if (kind == FootstepRelayKind.RUNNING) {
			if (!cfg.footstepsEnabled || !cfg.runningFootstepsEnabled || !RunningFootsteps.shouldRelay(entity, cfg)) {
				return;
			}
			maxRange = cfg.running.maxVolumeRange;
			profileOpt = RunningFootsteps.profileFor(entity, steppedOn);
		} else if (kind == FootstepRelayKind.CAVE_WALK || kind == FootstepRelayKind.CAVE_SPRINT) {
			if (!(entity instanceof Player player) || !CavePlayerNoises.shouldRelaySteps(player, cfg)) {
				return;
			}
			maxRange = kind == FootstepRelayKind.CAVE_SPRINT ? cfg.caveSprint.maxVolumeRange : cfg.caveWalk.maxVolumeRange;
			profileOpt = CavePlayerNoises.stepProfile(steppedOn);
		} else if (kind == FootstepRelayKind.SURFACE_SPRINT) {
			if (!(entity instanceof Player player) || !SurfaceSprintSteps.shouldRelay(player, cfg)) {
				return;
			}
			maxRange = cfg.sprint.maxVolumeRange;
			profileOpt = SurfaceSprintSteps.stepProfile(steppedOn);
		} else if (kind == FootstepRelayKind.PLANT_WALK
				|| kind == FootstepRelayKind.PLANT_SPRINT
				|| kind == FootstepRelayKind.PLANT_SNEAK) {
			if (!(entity instanceof Player player) || !PlantGrassWalk.shouldRelay(player, steppedOn, cfg)) {
				return;
			}
			maxRange = switch (kind) {
				case PLANT_SPRINT -> cfg.plantSprint.maxVolumeRange;
				case PLANT_SNEAK -> cfg.plantSneak.maxVolumeRange;
				default -> cfg.plantWalk.maxVolumeRange;
			};
			profileOpt = PlantGrassWalk.profile(player);
		} else if (kind == FootstepRelayKind.SNAKE) {
			if (!cfg.snakeMovementEnabled || !SnakeMovement.shouldRelay(entity, cfg)) {
				return;
			}
			maxRange = cfg.snake.maxVolumeRange;
			profileOpt = SnakeMovement.profile(entity);
		} else if (kind == FootstepRelayKind.HORSE_RUN || kind == FootstepRelayKind.HORSE_ARMOR) {
			return;
		} else {
			return;
		}

		if (profileOpt.isEmpty()) {
			return;
		}
		FootstepProfile profile = profileOpt.get();
		if (!BuiltInRegistries.SOUND_EVENT.containsKey(profile.soundId())) {
			return;
		}

		// Players already hear their own client-side steps; mobs have no self listener.
		broadcast(level, entity, profile, maxRange, kind, false);
	}

	private static void broadcast(
			ServerLevel level,
			LivingEntity source,
			FootstepProfile profile,
			double maxRange,
			FootstepRelayKind kind,
			boolean includeSource
	) {
		long seed = level.getRandom().nextLong();
		DistantFootstepPayload payload = new DistantFootstepPayload(
				profile.soundId(),
				source.getX(),
				source.getY(),
				source.getZ(),
				profile.baseVolume(),
				profile.pitch(),
				seed,
				kind.id(),
				(float) maxRange
		);
		Vec3 origin = new Vec3(payload.x(), payload.y(), payload.z());

		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			if (!includeSource && player.getUUID().equals(source.getUUID())) {
				continue;
			}
			double dist = player.position().distanceTo(origin);
			if (dist > maxRange) {
				continue;
			}
			ModNetworking.sendFootstep(player, payload);
		}
	}
}
