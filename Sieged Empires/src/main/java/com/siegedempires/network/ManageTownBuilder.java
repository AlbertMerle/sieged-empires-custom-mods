package com.siegedempires.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.siegedempires.data.EmpireDataManager;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.model.TownData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class ManageTownBuilder {
	private static final Gson GSON = new GsonBuilder().create();

	private ManageTownBuilder() {
	}

	public static ManageTownData build(ServerPlayer player, MinecraftServer server) {
		ManageTownData data = new ManageTownData();
		if (server == null) {
			return data;
		}
		TownData town = TownDataManager.getInstance().getPlayerTown(player.getUUID());
		if (town == null) {
			return data;
		}

		boolean isMonarch = town.getMonarchUuid().equals(player.getUUID());
		boolean isLord = town.isLord(player.getUUID());
		if (!isMonarch && !isLord) {
			return data;
		}

		data.isMonarch = isMonarch;
		data.isLord = isLord;
		data.townPublic = town.isTownPublic();
		data.townName = town.getName();
		data.claimedChunks = town.getChunkCount();
		data.bannerPatterns = town.getBannerPatterns() != null
				? new ArrayList<>(town.getBannerPatterns())
				: new ArrayList<>();
		data.bannerBaseColor = town.getBannerBaseColor();
		data.bannerPixels = town.getBannerPixels();

		// Empire info
		data.empireId = town.getEmpireId();
		if (town.getEmpireId() != null && !town.getEmpireId().isEmpty()) {
			var empire = EmpireDataManager.getInstance().getEmpire(town.getEmpireId());
			if (empire != null) {
				data.empireName = empire.getName();
				data.isEmperor = empire.getEmperorUuid().equals(player.getUUID());
			}
		}

		for (var entry : town.getMembers().entrySet()) {
			UUID memberUuid = entry.getKey();
			ManageTownData.MemberInfo info = new ManageTownData.MemberInfo();
			info.uuid = memberUuid.toString();
			info.role = entry.getValue();
			info.name = town.getMemberName(memberUuid);
			data.members.add(info);
		}

		data.members.sort(Comparator.comparing(member -> member.name.toLowerCase()));

		for (ServerPlayer online : server.getPlayerList().getPlayers()) {
			if (online.getUUID().equals(player.getUUID())) {
				continue;
			}
			data.onlinePlayers.add(online.getName().getString());
		}

		data.onlinePlayers.sort(String.CASE_INSENSITIVE_ORDER);
		return data;
	}

	public static String toJson(ManageTownData data) {
		return GSON.toJson(data);
	}

	public static ManageTownData fromJson(String json) {
		ManageTownData data = GSON.fromJson(json, ManageTownData.class);
		return data != null ? data : new ManageTownData();
	}

	public static ManageTownData buildForWartown(ServerPlayer player, MinecraftServer server, String wartownId) {
		ManageTownData data = new ManageTownData();
		if (server == null || wartownId == null || wartownId.isEmpty()) {
			return data;
		}
		TownData wartown = TownDataManager.getInstance().getTown(wartownId);
		if (wartown == null || !wartown.isWarTown()) {
			return data;
		}
		var empire = EmpireDataManager.getInstance().getEmpire(wartown.getEmpireId());
		if (empire == null || !empire.getEmperorUuid().equals(player.getUUID())) {
			return data;
		}

		data.wartownId = wartownId;
		data.isMonarch = true;
		data.isLord = false;
		data.townPublic = wartown.isTownPublic();
		data.townName = wartown.getName();
		data.claimedChunks = wartown.getChunkCount();
		data.bannerPatterns = wartown.getBannerPatterns() != null
				? new ArrayList<>(wartown.getBannerPatterns())
				: new ArrayList<>();
		data.bannerBaseColor = wartown.getBannerBaseColor();
		data.bannerPixels = wartown.getBannerPixels();
		data.empireId = wartown.getEmpireId();
		data.empireName = empire.getName();
		data.isEmperor = true;
		data.empirePlayers = TownDataManager.getInstance().collectEmpirePlayers(empire.getId());

		for (var entry : wartown.getMembers().entrySet()) {
			UUID memberUuid = entry.getKey();
			ManageTownData.MemberInfo info = new ManageTownData.MemberInfo();
			info.uuid = memberUuid.toString();
			info.role = entry.getValue();
			info.name = wartown.getMemberName(memberUuid);
			data.members.add(info);
		}
		data.members.sort(Comparator.comparing(member -> member.name.toLowerCase()));

		for (ServerPlayer online : server.getPlayerList().getPlayers()) {
			if (online.getUUID().equals(player.getUUID())) {
				continue;
			}
			data.onlinePlayers.add(online.getName().getString());
		}
		data.onlinePlayers.sort(String.CASE_INSENSITIVE_ORDER);
		return data;
	}
}
