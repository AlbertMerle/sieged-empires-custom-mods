package com.siegedempires.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.siegedempires.config.ModSettings;
import com.siegedempires.invasion.InvasionManager;
import com.siegedempires.data.DiplomacyDataManager;
import com.siegedempires.data.EmpireDataManager;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.diplomacy.DiplomacyActions;
import com.siegedempires.model.DiplomacyRecord;
import com.siegedempires.model.EmpireData;
import com.siegedempires.model.TownData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class DiplomacyBuilder {
	private static final Gson GSON = new GsonBuilder().create();

	private DiplomacyBuilder() {
	}

	public static DiplomacyData build(ServerPlayer player) {
		DiplomacyData data = new DiplomacyData();
		TownData town = TownDataManager.getInstance().getPlayerTown(player.getUUID());
		if (town == null) {
			return data;
		}

		DiplomacyActions.ManagedEntity managed = DiplomacyActions.resolveForTown(town);
		if (managed == null) {
			return data;
		}

		data.entityType = managed.entityType();
		data.entityId = managed.entityId();
		data.entityName = managed.entityName();
		data.townName = town.getName();
		if (town.getEmpireId() != null && !town.getEmpireId().isEmpty()) {
			EmpireData empire = EmpireDataManager.getInstance().getEmpire(town.getEmpireId());
			if (empire != null) {
				data.empireName = empire.getName();
			}
		}
		copyBanner(data, managed.entityType(), managed.entityId());
		data.canManage = canManageDiplomacy(player, town);
		data.invasionMinOnlinePlayers = ModSettings.get().invasionMinOnlinePlayers;
		data.hasActiveInvasion = InvasionManager.hasActiveInvasionFor(managed.entityType(), managed.entityId());

		DiplomacyRecord record = DiplomacyDataManager.getInstance().getOrCreate(managed.entityType(), managed.entityId());
		MinecraftServer server = player.level().getServer();
		data.allies = resolveFactions(record.getAllies(), record, server);
		data.enemies = resolveFactions(record.getEnemies(), record, server);
		data.notifications = buildNotifications(record, server);
		return data;
	}

	public static boolean canManageDiplomacy(ServerPlayer player, TownData town) {
		if (town == null) {
			return false;
		}
		if (town.getEmpireId() == null || town.getEmpireId().isEmpty()) {
			return town.getMonarchUuid().equals(player.getUUID());
		}
		EmpireData empire = EmpireDataManager.getInstance().getEmpire(town.getEmpireId());
		return empire != null && empire.getEmperorUuid().equals(player.getUUID());
	}

	private static void copyBanner(DiplomacyData data, String entityType, String entityId) {
		if (DiplomacyRecord.TYPE_EMPIRE.equals(entityType)) {
			EmpireData empire = EmpireDataManager.getInstance().getEmpire(entityId);
			if (empire != null) {
				if (empire.getBannerPatterns() != null) {
					data.bannerPatterns.addAll(empire.getBannerPatterns());
				}
				data.bannerBaseColor = empire.getBannerBaseColor();
				data.bannerPixels = empire.getBannerPixels();
			}
			return;
		}
		TownData town = TownDataManager.getInstance().getTown(entityId);
		if (town != null) {
			if (town.getBannerPatterns() != null) {
				data.bannerPatterns.addAll(town.getBannerPatterns());
			}
			data.bannerBaseColor = town.getBannerBaseColor();
			data.bannerPixels = town.getBannerPixels();
		}
	}

	private static java.util.List<DiplomacyData.FactionInfo> resolveFactions(Set<String> refs,
	                                                                        DiplomacyRecord selfRecord,
	                                                                        MinecraftServer server) {
		java.util.List<DiplomacyData.FactionInfo> factions = new ArrayList<>();
		if (refs == null) {
			return factions;
		}
		for (String ref : refs) {
			DiplomacyData.FactionInfo info = resolveFaction(ref, selfRecord, server);
			if (info != null) {
				factions.add(info);
			}
		}
		factions.sort(Comparator.comparing(f -> f.name.toLowerCase()));
		return factions;
	}

	private static DiplomacyData.FactionInfo resolveFaction(String ref, DiplomacyRecord selfRecord,
	                                                        MinecraftServer server) {
		String[] parts = DiplomacyRecord.parseRef(ref);
		if (parts == null) {
			return null;
		}

		DiplomacyData.FactionInfo info = new DiplomacyData.FactionInfo();
		info.entityType = parts[0];
		info.entityId = parts[1];

		if (DiplomacyRecord.TYPE_EMPIRE.equals(parts[0])) {
			EmpireData empire = EmpireDataManager.getInstance().getEmpire(parts[1]);
			if (empire == null) {
				return null;
			}
			info.name = empire.getName();
			if (empire.getBannerPatterns() != null) {
				info.bannerPatterns.addAll(empire.getBannerPatterns());
			}
			info.bannerBaseColor = empire.getBannerBaseColor();
			info.bannerPixels = empire.getBannerPixels();
		} else {
			TownData town = TownDataManager.getInstance().getTown(parts[1]);
			if (town == null) {
				return null;
			}
			info.name = town.getName();
			if (town.getEmpireId() != null && !town.getEmpireId().isEmpty()) {
				EmpireData empire = EmpireDataManager.getInstance().getEmpire(town.getEmpireId());
				if (empire != null) {
					if (empire.getBannerPatterns() != null) {
						info.bannerPatterns.addAll(empire.getBannerPatterns());
					}
					info.bannerBaseColor = empire.getBannerBaseColor();
					info.bannerPixels = empire.getBannerPixels();
				}
			} else {
				if (town.getBannerPatterns() != null) {
					info.bannerPatterns.addAll(town.getBannerPatterns());
				}
				info.bannerBaseColor = town.getBannerBaseColor();
				info.bannerPixels = town.getBannerPixels();
			}
		}

		info.onlineCount = countOnline(info.entityType, info.entityId, server);
		if (selfRecord != null) {
			info.hasTrade = selfRecord.getAllyTrade().contains(ref);
			info.hasOpenBorders = selfRecord.getAllyOpenBorders().contains(ref);
		}
		return info;
	}

	private static java.util.List<DiplomacyData.PendingNotification> buildNotifications(DiplomacyRecord record,
	                                                                                    MinecraftServer server) {
		java.util.List<DiplomacyData.PendingNotification> notifications = new ArrayList<>();
		for (String ref : record.getPendingAllyInvites()) {
			addNotification(notifications, DiplomacyActions.INVITE_ALLY, ref);
		}
		for (String ref : record.getPendingTradeRequests()) {
			addNotification(notifications, DiplomacyActions.INVITE_TRADE, ref);
		}
		for (String ref : record.getPendingOpenBorderRequests()) {
			addNotification(notifications, DiplomacyActions.INVITE_OPEN_BORDERS, ref);
		}
		for (String ref : record.getPendingPeaceRequests()) {
			addNotification(notifications, DiplomacyActions.INVITE_PEACE, ref);
		}
		return notifications;
	}

	private static void addNotification(java.util.List<DiplomacyData.PendingNotification> notifications,
	                                    String inviteType, String ref) {
		DiplomacyData.FactionInfo faction = resolveFaction(ref, null, null);
		if (faction == null) {
			return;
		}
		DiplomacyData.PendingNotification notification = new DiplomacyData.PendingNotification();
		notification.inviteType = inviteType;
		notification.entityType = faction.entityType;
		notification.entityId = faction.entityId;
		notification.name = faction.name;
		notification.bannerPatterns = faction.bannerPatterns;
		notification.bannerBaseColor = faction.bannerBaseColor;
		notification.bannerPixels = faction.bannerPixels;
		notifications.add(notification);
	}

	public static int countOnline(String entityType, String entityId, MinecraftServer server) {
		if (server == null) {
			return 0;
		}
		Set<UUID> counted = new HashSet<>();
		int online = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!counted.add(player.getUUID())) {
				continue;
			}
			if (belongsToFaction(player.getUUID(), entityType, entityId)) {
				online++;
			}
		}
		return online;
	}

	private static boolean belongsToFaction(UUID playerId, String entityType, String entityId) {
		if (DiplomacyRecord.TYPE_EMPIRE.equals(entityType)) {
			EmpireData empire = EmpireDataManager.getInstance().getEmpire(entityId);
			if (empire != null && empire.getEmperorUuid().equals(playerId)) {
				return true;
			}
			TownData town = TownDataManager.getInstance().getPlayerTown(playerId);
			return town != null && entityId.equals(town.getEmpireId());
		}
		TownData town = TownDataManager.getInstance().getPlayerTown(playerId);
		return town != null && entityId.equals(town.getId());
	}

	public static String toJson(DiplomacyData data) {
		return GSON.toJson(data);
	}

	public static DiplomacyData fromJson(String json) {
		DiplomacyData data = GSON.fromJson(json, DiplomacyData.class);
		return data != null ? data : new DiplomacyData();
	}
}
