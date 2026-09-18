package com.shiftyocean;

import com.shiftyocean.config.ShiftyoceanConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Server-driven ocean currents.
 * <p>
 * Players swimming in any ocean biome (vanilla + Terralith + untagged
 * {@code *_ocean} ids) get a full-BPS additive
 * X/Z shift via {@link ServerPlayer#teleportRelative} (client position packets
 * would otherwise wipe a plain {@code move()}).
 * <p>
 * Boats/ships ({@link AbstractBoat}) get
 * {@link ShiftyoceanConfig#entitySpeedScale} of that BPS plus yaw drift inside
 * their authoritative {@code tick} (mixin), so player paddle/sail input stacks
 * with the current — including while a player is driving. Empty boats are
 * server-authoritative; ridden boats are client-authoritative.
 */
public final class OceanCurrentHandler {
	private static final double TICKS_PER_SECOND = 20.0;
	private static final double TWO_PI = Math.PI * 2.0;
	/** Mixes for deterministic RNG (gameTime slots — no world seed, so client boats match). */
	private static final long MIX_GENERAL_ANGLE = 0x9E3779B97F4A7C15L;
	private static final long MIX_GUST_BIAS = 0xBF58476D1CE4E5B9L;
	private static final long MIX_GUST_ANGLE = 0x94D049BB133111EBL;
	private static final long MIX_YAW_SIGN = 0xC2B2AE3D27D4EB4FL;

	private OceanCurrentHandler() {
	}

	public static void onServerTick(MinecraftServer server) {
		ShiftyoceanConfig cfg = ShiftyoceanConfig.get();
		if (!cfg.enabled) {
			return;
		}

		for (ServerLevel level : server.getAllLevels()) {
			tickPlayers(level, cfg);
			tickNonBoatEntities(level, cfg);
		}
	}

	/**
	 * Called from {@code AbstractBoat.tick} immediately before {@code move}, on
	 * whichever side is local-instance authoritative (client when driven, server
	 * when empty). Adds current velocity on top of paddle/float delta.
	 */
	public static void applyAuthoritativeBoatCurrent(AbstractBoat boat) {
		ShiftyoceanConfig cfg = ShiftyoceanConfig.get();
		if (!cfg.enabled) {
			return;
		}
		if (!cfg.isEnabledEntity(boat.getType())) {
			return;
		}
		if (!shouldAffectEntity(boat)) {
			return;
		}

		Level level = boat.level();
		double playerBps = speedForWeather(level, cfg);
		if (playerBps <= 0.0) {
			return;
		}

		double entityBps = playerBps * cfg.entitySpeedScale;
		if (entityBps > 0.0) {
			Vec3 step = directionFor(level, cfg).scale(entityBps / TICKS_PER_SECOND);
			boat.addDeltaMovement(step);
		}

		float yawStep = yawStepDegrees(level, cfg);
		if (yawStep != 0.0f) {
			boat.setYRot(boat.getYRot() + yawStep);
			boat.setYBodyRot(boat.getYRot());
		}
	}

	private static void tickPlayers(ServerLevel level, ShiftyoceanConfig cfg) {
		double playerBps = speedForWeather(level, cfg);
		if (playerBps <= 0.0) {
			return;
		}

		Vec3 playerStep = directionFor(level, cfg).scale(playerBps / TICKS_PER_SECOND);
		for (ServerPlayer player : level.players()) {
			if (!shouldAffectPlayer(player)) {
				continue;
			}
			// Relative teleport is synced through ServerGamePacketListenerImpl so
			// the shift survives client move packets (unlike move(SELF)).
			player.teleportRelative(playerStep.x, 0.0, playerStep.z);
		}
	}

	/**
	 * Enabled entities that are not {@link AbstractBoat} (mixin covers boats/ships).
	 * Only when this side is authoritative — ridden client-controlled vehicles are
	 * skipped on the server.
	 */
	private static void tickNonBoatEntities(ServerLevel level, ShiftyoceanConfig cfg) {
		double playerBps = speedForWeather(level, cfg);
		if (playerBps <= 0.0) {
			return;
		}

		double entityBps = playerBps * cfg.entitySpeedScale;
		float yawStep = yawStepDegrees(level, cfg);
		if (entityBps <= 0.0 && yawStep == 0.0f) {
			return;
		}

		Vec3 entityStep = directionFor(level, cfg).scale(entityBps / TICKS_PER_SECOND);
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof ServerPlayer || entity instanceof AbstractBoat) {
				continue;
			}
			if (!cfg.isEnabledEntity(entity.getType())) {
				continue;
			}
			if (!entity.isLocalInstanceAuthoritative()) {
				continue;
			}
			if (!shouldAffectEntity(entity)) {
				continue;
			}
			if (entityBps > 0.0) {
				entity.teleportRelative(entityStep.x, 0.0, entityStep.z);
			}
			if (yawStep != 0.0f) {
				entity.setYRot(entity.getYRot() + yawStep);
				entity.setYBodyRot(entity.getYRot());
			}
		}
	}

	private static double speedForWeather(Level level, ShiftyoceanConfig cfg) {
		if (level.isThundering()) {
			return cfg.thunderSpeedBps;
		}
		if (level.isRaining()) {
			return cfg.rainSpeedBps;
		}
		return cfg.clearSpeedBps;
	}

	/**
	 * Short-interval heading shared by everyone in the level: usually the
	 * general current ({@link ShiftyoceanConfig#generalDirectionBias}), else a
	 * random 360° gust. General heading itself re-rolls every
	 * {@link ShiftyoceanConfig#generalDirectionChangeMinutes}.
	 */
	private static Vec3 directionFor(Level level, ShiftyoceanConfig cfg) {
		Vec3 general = generalDirection(level, cfg);
		int gustInterval = Math.max(1, cfg.directionChangeTicks);
		Random biasRng = slotRandom(level, gustInterval, MIX_GUST_BIAS);
		if (biasRng.nextDouble() < cfg.generalDirectionBias) {
			return general;
		}
		return headingFromAngle(slotRandom(level, gustInterval, MIX_GUST_ANGLE).nextDouble() * TWO_PI);
	}

	/** Unit XZ vector for the 7-minute global current (any angle, not NESW-only). */
	private static Vec3 generalDirection(Level level, ShiftyoceanConfig cfg) {
		int interval = cfg.generalDirectionChangeTicks();
		double angle = slotRandom(level, interval, MIX_GENERAL_ANGLE).nextDouble() * TWO_PI;
		return headingFromAngle(angle);
	}

	/** Angle 0 = North (−Z), π/2 = East (+X). Length 1. */
	private static Vec3 headingFromAngle(double angleRadians) {
		return new Vec3(Math.sin(angleRadians), 0.0, -Math.cos(angleRadians));
	}

	private static Random slotRandom(Level level, int intervalTicks, long mix) {
		long slot = level.getGameTime() / Math.max(1, intervalTicks);
		long seed = slot * mix ^ (level.dimension().hashCode() * 31L);
		return new Random(seed);
	}

	/**
	 * Degrees of yaw to apply this tick. Sign (+/−) re-rolls with the same
	 * cadence as current direction so boats slowly weave. Rate depends on weather.
	 */
	private static float yawStepDegrees(Level level, ShiftyoceanConfig cfg) {
		double yawPerSecond = yawDriftForWeather(level, cfg);
		if (yawPerSecond <= 0.0) {
			return 0.0f;
		}
		Random random = slotRandom(level, Math.max(1, cfg.directionChangeTicks), MIX_YAW_SIGN);
		float sign = random.nextBoolean() ? 1.0f : -1.0f;
		return (float) (sign * yawPerSecond / TICKS_PER_SECOND);
	}

	private static double yawDriftForWeather(Level level, ShiftyoceanConfig cfg) {
		if (level.isThundering()) {
			return cfg.entityYawDriftThunderDegreesPerSecond;
		}
		if (level.isRaining()) {
			return cfg.entityYawDriftRainDegreesPerSecond;
		}
		return 0.0;
	}

	private static boolean shouldAffectPlayer(ServerPlayer player) {
		if (player.isSpectator() || player.getAbilities().flying) {
			return false;
		}
		// Riding: vehicle (boat/ship) carries the current; don't shove the passenger.
		if (player.isPassenger()) {
			return false;
		}
		if (!player.isInWater()) {
			return false;
		}
		return isOceanBiome(player);
	}

	private static boolean shouldAffectEntity(Entity entity) {
		if (!entity.isAlive()) {
			return false;
		}
		// Boats/ships often float with wasTouchingWater false; also accept water at/below feet.
		if (!isWaterborne(entity)) {
			return false;
		}
		return isOceanBiome(entity);
	}

	private static boolean isOceanBiome(Entity entity) {
		return OceanBiomes.isOcean(entity.level().getBiome(entity.blockPosition()));
	}

	private static boolean isWaterborne(Entity entity) {
		if (entity.isInWater()) {
			return true;
		}
		BlockPos pos = entity.blockPosition();
		return entity.level().getFluidState(pos).is(FluidTags.WATER)
				|| entity.level().getFluidState(pos.below()).is(FluidTags.WATER);
	}
}
