package com.siegedempires.util;

import com.siegedempires.banner.BannerManager;
import com.siegedempires.model.TownData;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import java.util.UUID;

public final class PlayerPrefixManager {
	private PlayerPrefixManager() {
	}

	public static void refresh(ServerPlayer player) {
		if (player == null || player.level() == null) {
			return;
		}
		PlayerList playerList = player.level().getServer().getPlayerList();
		playerList.broadcastAll(new ClientboundPlayerInfoUpdatePacket(
				ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
				player
		));
	}

	public static void refreshForTown(TownData town) {
		if (town == null || town.getMembers() == null) {
			return;
		}
		MinecraftServer server = BannerManager.getServer();
		if (server == null) {
			return;
		}
		for (UUID memberId : town.getMembers().keySet()) {
			ServerPlayer online = server.getPlayerList().getPlayer(memberId);
			if (online != null) {
				refresh(online);
			}
		}
	}

	public static void refreshMember(TownData town, UUID memberId) {
		refreshPlayer(memberId);
	}

	public static void refreshPlayer(UUID playerId) {
		if (playerId == null) {
			return;
		}
		MinecraftServer server = BannerManager.getServer();
		if (server == null) {
			return;
		}
		ServerPlayer online = server.getPlayerList().getPlayer(playerId);
		if (online != null) {
			refresh(online);
		}
	}
}
