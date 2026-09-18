package com.distantnoise.network;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Delays gun relay packets for listeners beyond view distance. */
public final class DistantSoundScheduler {
	private static final List<PendingSend> PENDING = new ArrayList<>();

	private DistantSoundScheduler() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(DistantSoundScheduler::tick);
	}

	public static void schedule(ServerLevel level, ServerPlayer player, DistantNoisePayload payload, int delayTicks) {
		if (delayTicks <= 0) {
			ServerPlayNetworking.send(player, payload);
			return;
		}
		synchronized (PENDING) {
			PENDING.add(new PendingSend(level, player.getUUID(), payload, delayTicks));
		}
	}

	private static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		synchronized (PENDING) {
			Iterator<PendingSend> it = PENDING.iterator();
			while (it.hasNext()) {
				PendingSend pending = it.next();
				pending.ticksLeft--;
				if (pending.ticksLeft > 0) {
					continue;
				}
				it.remove();
				ServerPlayer player = server.getPlayerList().getPlayer(pending.playerId);
				if (player == null || !player.isAlive()) {
					continue;
				}
				if (player.level() != pending.level) {
					continue;
				}
				ServerPlayNetworking.send(player, pending.payload);
			}
		}
	}

	private static final class PendingSend {
		final ServerLevel level;
		final java.util.UUID playerId;
		final DistantNoisePayload payload;
		int ticksLeft;

		PendingSend(ServerLevel level, java.util.UUID playerId, DistantNoisePayload payload, int ticksLeft) {
			this.level = level;
			this.playerId = playerId;
			this.payload = payload;
			this.ticksLeft = ticksLeft;
		}
	}
}
