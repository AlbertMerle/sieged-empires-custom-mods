package com.distantnoise.sound;

import com.distantnoise.config.DistantNoiseConfig;
import com.distantnoise.network.GrassRustleStartPayload;
import com.distantnoise.network.GrassRustleStopPayload;
import com.distantnoise.network.ModNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Server-side grass / plant rustle while animals move through vegetation. */
public final class GrassRustling {
	/** Matches converted {@code grass_russling.ogg} length (seconds). */
	public static final float CLIP_SECONDS = 30.16f;
	private static final double MOVE_THRESHOLD_SQR = 1.0E-5;

	private GrassRustling() {
	}

	public static boolean shouldRustle(LivingEntity entity) {
		return isMoving(entity) && PlantVegetation.isTouchingRustlePlant(entity);
	}

	public static boolean isFleeing(LivingEntity entity) {
		return RunningFootsteps.isFleeing(entity, DistantNoiseConfig.get());
	}

	public static DistantNoiseConfig.RelayRangeSettings rangeFor(LivingEntity entity) {
		DistantNoiseConfig cfg = DistantNoiseConfig.get();
		return isFleeing(entity) ? cfg.russlingFlee : cfg.russlingWalk;
	}

	public static void start(LivingEntity entity, boolean fleeing) {
		if (entity.level().isClientSide() || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		float offset = entity.getRandom().nextFloat() * CLIP_SECONDS;
		GrassRustleStartPayload payload = new GrassRustleStartPayload(entity.getId(), offset, fleeing);
		broadcastStart(level, entity, payload, rangeFor(entity));
	}

	public static void stop(LivingEntity entity) {
		if (entity.level().isClientSide() || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		broadcastStop(level, entity, new GrassRustleStopPayload(entity.getId()));
	}

	private static void broadcastStart(
			ServerLevel level,
			LivingEntity entity,
			GrassRustleStartPayload payload,
			DistantNoiseConfig.RelayRangeSettings range
	) {
		double hearRange = range.maxVolumeRange;
		Vec3 origin = entity.position();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			if (player.position().distanceTo(origin) > hearRange) {
				continue;
			}
			ModNetworking.sendGrassRustleStart(player, payload);
		}
	}

	private static void broadcastStop(ServerLevel level, LivingEntity entity, GrassRustleStopPayload payload) {
		double hearRange = maxBroadcastRange();
		Vec3 origin = entity.position();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			if (player.position().distanceTo(origin) > hearRange) {
				continue;
			}
			ModNetworking.sendGrassRustleStop(player, payload);
		}
	}

	private static double maxBroadcastRange() {
		DistantNoiseConfig cfg = DistantNoiseConfig.get();
		return Math.max(cfg.russlingWalk.maxVolumeRange, cfg.russlingFlee.maxVolumeRange);
	}

	private static boolean isMoving(LivingEntity entity) {
		Vec3 delta = entity.getDeltaMovement();
		if (delta.horizontalDistanceSqr() > MOVE_THRESHOLD_SQR) {
			return true;
		}
		return entity.getX() != entity.xOld || entity.getZ() != entity.zOld;
	}
}
