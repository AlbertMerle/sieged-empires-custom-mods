package com.distantnoise.network;

import com.distantnoise.config.DistantNoiseConfig;
import com.distantnoise.sound.DistantNoiseKind;
import com.distantnoise.sound.GunShotRelay;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Registers S2C payload and broadcasts distant-noise events to players in range.
 */
public final class ModNetworking {
	private ModNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(DistantNoisePayload.TYPE, DistantNoisePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DistantFootstepPayload.TYPE, DistantFootstepPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DistantVocalPayload.TYPE, DistantVocalPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(GrassRustleStartPayload.TYPE, GrassRustleStartPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(GrassRustleStopPayload.TYPE, GrassRustleStopPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(GunShotReportPayload.TYPE, GunShotReportPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(GunShotReportPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> handleGunShotReport(player, payload));
		});
		DistantSoundScheduler.register();
	}

	private static void handleGunShotReport(ServerPlayer player, GunShotReportPayload payload) {
		GunShotRelay.relay(player, DistantNoiseKind.byId(payload.kindId()));
	}

	public static void broadcast(ServerLevel level, DistantNoiseKind kind, double x, double y, double z) {
		broadcast(level, kind, x, y, z, null);
	}

	/** Gun relay: skips the shooter (they already hear the local bang). */
	public static void broadcastGunShot(
			ServerLevel level,
			DistantNoiseKind kind,
			double x,
			double y,
			double z,
			ServerPlayer shooter
	) {
		broadcast(level, kind, x, y, z, shooter);
	}

	private static void broadcast(
			ServerLevel level,
			DistantNoiseKind kind,
			double x,
			double y,
			double z,
			ServerPlayer shooter
	) {
		DistantNoiseConfig cfg = DistantNoiseConfig.get();
		if (!cfg.enabled) {
			return;
		}
		double near = cfg.nearCutoff;
		long seed = level.getRandom().nextLong();
		DistantNoisePayload payload = new DistantNoisePayload(kind, x, y, z, seed);
		Vec3 origin = new Vec3(x, y, z);
		boolean gunKind = kind.isGun();
		boolean explosionKind = kind.isExplosion();
		double radius = explosionKind ? cfg.explosion.maxVolumeRange : cfg.radius;
		int delayTicks = gunKind ? Math.max(0, cfg.gunDelayBeyondViewTicks) : 0;
		MinecraftServer server = level.getServer();
		double viewBlocks = server.getPlayerList().getViewDistance() * 16.0;

		// Every online player in this dimension — full radius scan (not view-distance limited).
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.level() != level) {
				continue;
			}
			if (player.isSpectator()) {
				continue;
			}
			if (shooter != null && player.getUUID().equals(shooter.getUUID())) {
				continue;
			}
			double dist = player.position().distanceTo(origin);
			if (gunKind || explosionKind) {
				if (dist > radius) {
					continue;
				}
			} else if (dist <= near || dist > radius) {
				continue;
			}
			if (delayTicks > 0 && dist > viewBlocks) {
				DistantSoundScheduler.schedule(level, player, payload, delayTicks);
			} else {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	public static void sendFootstep(ServerPlayer player, DistantFootstepPayload payload) {
		ServerPlayNetworking.send(player, payload);
	}

	public static void sendVocal(ServerPlayer player, DistantVocalPayload payload) {
		ServerPlayNetworking.send(player, payload);
	}

	public static void sendGrassRustleStart(ServerPlayer player, GrassRustleStartPayload payload) {
		ServerPlayNetworking.send(player, payload);
	}

	public static void sendGrassRustleStop(ServerPlayer player, GrassRustleStopPayload payload) {
		ServerPlayNetworking.send(player, payload);
	}
}
