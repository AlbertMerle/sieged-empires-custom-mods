package com.distantnoise.sound;

import com.distantnoise.network.ModNetworking;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Server-side gun shot relay with per-tick dedup (C2S report + postShootingEffects). */
public final class GunShotRelay {
	private static final Map<UUID, Long> LAST_RELAY_TICK = new ConcurrentHashMap<>();

	private GunShotRelay() {
	}

	public static void relay(ServerPlayer shooter, DistantNoiseKind kind) {
		if (kind == null || !kind.isGun()) {
			return;
		}
		if (!(shooter.level() instanceof ServerLevel level)) {
			return;
		}
		long tick = level.getGameTime();
		Long previous = LAST_RELAY_TICK.put(shooter.getUUID(), tick);
		if (previous != null && previous == tick) {
			return;
		}
		ModNetworking.broadcastGunShot(
				level,
				kind,
				shooter.getX(),
				shooter.getY(),
				shooter.getZ(),
				shooter
		);
	}
}
