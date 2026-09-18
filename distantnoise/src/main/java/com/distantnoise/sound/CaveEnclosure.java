package com.distantnoise.sound;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side stand-in for “Sound Physics would reverb here”: enclosed, low-sky-light space.
 * Result is cached briefly so footstep spam does not raycast every step.
 */
public final class CaveEnclosure {
	private static final int CACHE_TICKS = 20;
	private static final int RAY_DISTANCE = 12;
	private static final int MIN_SOLID_HITS = 5;
	private static final int MAX_SKY_LIGHT = 7;

	private static final Direction[] HORIZONTAL = {
			Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
	};

	private static final Map<UUID, CacheEntry> CACHE = new ConcurrentHashMap<>();

	private CaveEnclosure() {
	}

	public static boolean isEchoingCave(LivingEntity entity) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return false;
		}
		if (entity.isSpectator() || entity.isInWater() || entity.isInLava()) {
			return false;
		}

		UUID id = entity.getUUID();
		int tick = entity.tickCount;
		CacheEntry cached = CACHE.get(id);
		if (cached != null && tick - cached.checkedAtTick < CACHE_TICKS) {
			return cached.echoing;
		}

		boolean echoing = evaluate(level, entity);
		CACHE.put(id, new CacheEntry(tick, echoing));
		if (CACHE.size() > 256) {
			CACHE.entrySet().removeIf(e -> tick - e.getValue().checkedAtTick > CACHE_TICKS * 8);
		}
		return echoing;
	}

	public static void clear(UUID entityId) {
		CACHE.remove(entityId);
	}

	private static boolean evaluate(ServerLevel level, LivingEntity entity) {
		BlockPos feet = entity.blockPosition();
		BlockPos sample = feet.above();

		if (level.dimensionType().hasSkyLight()) {
			int sky = level.getBrightness(LightLayer.SKY, sample);
			if (sky > MAX_SKY_LIGHT) {
				return false;
			}
			if (level.canSeeSky(sample) && sky > 0) {
				return false;
			}
		}

		Vec3 origin = entity.position().add(0.0, entity.getEyeHeight() * 0.5, 0.0);
		int hits = 0;

		// Ceiling is required — open pits / deep surface holes without a roof do not echo.
		if (hitsSolid(level, entity, origin, Direction.UP, RAY_DISTANCE + 4)) {
			hits++;
		} else {
			return false;
		}

		if (hitsSolid(level, entity, origin, Direction.DOWN, 4)) {
			hits++;
		}

		for (Direction dir : HORIZONTAL) {
			if (hitsSolid(level, entity, origin, dir, RAY_DISTANCE)) {
				hits++;
			}
		}

		// Diagonals catch narrow tunnels that miss cardinals.
		hits += diagonalHit(level, entity, origin, 1, 0, 1);
		hits += diagonalHit(level, entity, origin, 1, 0, -1);
		hits += diagonalHit(level, entity, origin, -1, 0, 1);
		hits += diagonalHit(level, entity, origin, -1, 0, -1);

		return hits >= MIN_SOLID_HITS;
	}

	private static int diagonalHit(ServerLevel level, LivingEntity entity, Vec3 origin, double x, double y, double z) {
		Vec3 dir = new Vec3(x, y, z).normalize();
		Vec3 to = origin.add(dir.scale(RAY_DISTANCE));
		return clipHits(level, entity, origin, to) ? 1 : 0;
	}

	private static boolean hitsSolid(
			ServerLevel level,
			LivingEntity entity,
			Vec3 origin,
			Direction direction,
			int distance
	) {
		Vec3 to = origin.add(
				direction.getStepX() * distance,
				direction.getStepY() * distance,
				direction.getStepZ() * distance
		);
		return clipHits(level, entity, origin, to);
	}

	private static boolean clipHits(ServerLevel level, LivingEntity entity, Vec3 from, Vec3 to) {
		BlockHitResult hit = level.clip(new ClipContext(
				from,
				to,
				ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE,
				entity
		));
		return hit.getType() == HitResult.Type.BLOCK;
	}

	private record CacheEntry(int checkedAtTick, boolean echoing) {
	}
}
