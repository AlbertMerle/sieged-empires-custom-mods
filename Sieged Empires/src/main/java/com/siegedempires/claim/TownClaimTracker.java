package com.siegedempires.claim;

import com.siegedempires.data.TownDataManager;
import com.siegedempires.model.TownData;
import com.siegedempires.util.TitleHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class TownClaimTracker {
	private static final Map<UUID, String> lastTownIdByPlayer = new HashMap<>();

	private TownClaimTracker() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TownClaimTracker::onServerTick);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			lastTownIdByPlayer.put(player.getUUID(), getTownIdAt(player));
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			lastTownIdByPlayer.remove(handler.getPlayer().getUUID());
		});
	}

	private static void onServerTick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			checkPlayer(player);
		}
	}

	private static void checkPlayer(ServerPlayer player) {
		String currentTownId = getTownIdAt(player);
		UUID playerId = player.getUUID();
		String lastTownId = lastTownIdByPlayer.get(playerId);

		if (!lastTownIdByPlayer.containsKey(playerId)) {
			lastTownIdByPlayer.put(playerId, currentTownId);
			return;
		}

		if (Objects.equals(currentTownId, lastTownId)) {
			return;
		}

		TownDataManager townManager = TownDataManager.getInstance();

		if (currentTownId != null) {
			// Entering a town (from wilderness or another town): show that town only.
			TownData enteredTown = townManager.getTown(currentTownId);
			if (enteredTown != null) {
				TitleHelper.showEnterTownTitle(player, enteredTown);
			}
		} else if (lastTownId != null) {
			// Leaving into wilderness: show leave title.
			TownData leftTown = townManager.getTown(lastTownId);
			if (leftTown != null) {
				TitleHelper.showLeaveTownTitle(player, leftTown);
			}
		}

		lastTownIdByPlayer.put(playerId, currentTownId);
	}

	private static String getTownIdAt(ServerPlayer player) {
		ChunkPos chunkPos = player.chunkPosition();
		String dimension = player.level().dimension().identifier().toString();
		TownData town = TownDataManager.getInstance().getTownAtChunk(chunkPos.x(), chunkPos.z(), dimension);
		return town != null ? town.getId() : null;
	}
}
