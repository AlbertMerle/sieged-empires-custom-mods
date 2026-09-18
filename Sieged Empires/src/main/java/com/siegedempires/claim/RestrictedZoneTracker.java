package com.siegedempires.claim;

import com.siegedempires.data.TownDataManager;
import com.siegedempires.model.ChunkPosition;
import com.siegedempires.model.TownData;
import com.siegedempires.util.TitleHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Warns regular Citizens who stand in Restricted Zones with a flashing title,
 * and notifies the online Monarch once when a citizen enters.
 */
public final class RestrictedZoneTracker {
	private static final Map<UUID, ChunkKey> lastRestrictedChunkByPlayer = new HashMap<>();
	private static final Map<UUID, Integer> nextFlashTickByPlayer = new HashMap<>();
	/** Citizen UUIDs already announced for their current stay in a restricted chunk. */
	private static final Set<UUID> announcedEntry = new HashSet<>();

	private RestrictedZoneTracker() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RestrictedZoneTracker::onServerTick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clearPlayer(handler.getPlayer().getUUID()));
	}

	private static void onServerTick(MinecraftServer server) {
		int tick = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			checkPlayer(player, server, tick);
		}
	}

	private static void checkPlayer(ServerPlayer player, MinecraftServer server, int tick) {
		UUID playerId = player.getUUID();
		ChunkPos chunkPos = player.chunkPosition();
		String dimension = player.level().dimension().identifier().toString();
		TownData town = TownDataManager.getInstance().getTownAtChunk(chunkPos.x(), chunkPos.z(), dimension);
		ChunkPosition chunk = new ChunkPosition(chunkPos.x(), chunkPos.z(), dimension);

		boolean inRestricted = town != null
				&& town.getRestrictedChunks().contains(chunk)
				&& isRegularCitizenOf(town, playerId);

		ChunkKey currentKey = inRestricted ? new ChunkKey(town.getId(), chunk) : null;
		ChunkKey lastKey = lastRestrictedChunkByPlayer.get(playerId);

		if (!inRestricted) {
			if (lastKey != null) {
				TitleHelper.clearTitles(player);
				clearPlayer(playerId);
			}
			return;
		}

		boolean justEntered = !Objects.equals(currentKey, lastKey);
		lastRestrictedChunkByPlayer.put(playerId, currentKey);

		if (justEntered) {
			TitleHelper.showRestrictedZoneWarning(player);
			nextFlashTickByPlayer.put(playerId, tick + TitleHelper.RESTRICTED_FLASH_INTERVAL);
			if (announcedEntry.add(playerId)) {
				notifyMonarch(server, town);
			}
			return;
		}

		Integer nextFlash = nextFlashTickByPlayer.get(playerId);
		if (nextFlash != null && tick >= nextFlash) {
			TitleHelper.showRestrictedZoneWarning(player);
			nextFlashTickByPlayer.put(playerId, tick + TitleHelper.RESTRICTED_FLASH_INTERVAL);
		}
	}

	private static boolean isRegularCitizenOf(TownData town, UUID playerId) {
		if (playerId.equals(town.getMonarchUuid()) || town.isLord(playerId)) {
			return false;
		}
		String role = town.getMembers().get(playerId);
		if (role == null) {
			return false;
		}
		if ("Trusted Citizen".equals(role) || "Monarch".equals(role) || "Lord".equals(role)) {
			return false;
		}
		return true;
	}

	private static void notifyMonarch(MinecraftServer server, TownData town) {
		UUID monarchId = town.getMonarchUuid();
		if (monarchId == null) {
			return;
		}
		ServerPlayer monarch = server.getPlayerList().getPlayer(monarchId);
		if (monarch == null) {
			return;
		}
		monarch.sendSystemMessage(Component.literal("A Citizen has entered a restricted zone!"));
	}

	private static void clearPlayer(UUID playerId) {
		lastRestrictedChunkByPlayer.remove(playerId);
		nextFlashTickByPlayer.remove(playerId);
		announcedEntry.remove(playerId);
	}

	private record ChunkKey(String townId, ChunkPosition chunk) {
	}
}
