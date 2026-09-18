package com.siegedempires.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.siegedempires.data.DiplomacyDataManager;
import com.siegedempires.data.EmpireDataManager;
import com.siegedempires.data.PendingCrownManager;
import com.siegedempires.data.PlayerMailManager;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.diplomacy.DiplomacyActions;
import com.siegedempires.model.DiplomacyRecord;
import com.siegedempires.model.EmpireData;
import com.siegedempires.model.PlayerMailData;
import com.siegedempires.model.StoredMailEntry;
import com.siegedempires.model.TownData;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MailBuilder {
	public static final String TYPE_TOWN_INVITE = "town_invite";
	public static final String TYPE_EMPIRE_INVITE = "empire_invite";
	public static final String TYPE_ALLY = "ally";
	public static final String TYPE_TRADE = "trade";
	public static final String TYPE_OPEN_BORDERS = "open_borders";
	public static final String TYPE_PEACE = "peace";
	public static final String TYPE_WAR = "war";
	public static final String TYPE_CROWN = "crown";
	public static final String TYPE_RESPONSE = "response";

	private static final Gson GSON = new GsonBuilder().create();

	private MailBuilder() {
	}

	public static MailData build(ServerPlayer player) {
		MailData data = new MailData();
		UUID playerUuid = player.getUUID();
		TownData playerTown = TownDataManager.getInstance().getPlayerTown(playerUuid);

		for (TownData town : TownDataManager.getInstance().getAllTowns()) {
			if (town.isInvited(playerUuid)) {
				MailData.MailEntry entry = baseEntry(TYPE_TOWN_INVITE, true,
						"mail.town_invite", "", "", "",
						DiplomacyRecord.TYPE_TOWN, town.getName(), town.getId());
				entry.senderName = town.getName();
				copyBanner(entry, DiplomacyRecord.TYPE_TOWN, town.getId());
				data.entries.add(entry);
			}
		}

		if (playerTown != null && playerTown.getMonarchUuid().equals(playerUuid)) {
			for (EmpireData empire : EmpireDataManager.getInstance().getAllEmpires()) {
				if (empire.isInvited(playerTown.getId())) {
					MailData.MailEntry entry = baseEntry(TYPE_EMPIRE_INVITE, true,
							"mail.empire_invite", "", "", "",
							DiplomacyRecord.TYPE_EMPIRE, empire.getName(), empire.getId());
					entry.senderName = empire.getName();
					copyBanner(entry, DiplomacyRecord.TYPE_EMPIRE, empire.getId());
					data.entries.add(entry);
				}
			}
		}

		if (playerTown != null && DiplomacyBuilder.canManageDiplomacy(player, playerTown)) {
			DiplomacyActions.ManagedEntity managed = DiplomacyActions.resolveForTown(playerTown);
			if (managed != null) {
				DiplomacyRecord record = DiplomacyDataManager.getInstance().getOrCreate(
						managed.entityType(), managed.entityId());
				addDiplomacyPending(data, TYPE_ALLY,
						"mail.alliance_request_town", "mail.alliance_request_empire",
						record.getPendingAllyInvites());
				addDiplomacyPending(data, TYPE_TRADE,
						"mail.trade_request_town", "mail.trade_request_empire",
						record.getPendingTradeRequests());
				addDiplomacyPending(data, TYPE_OPEN_BORDERS,
						"mail.open_borders_request_town", "mail.open_borders_request_empire",
						record.getPendingOpenBorderRequests());
				addDiplomacyPending(data, TYPE_PEACE,
						"mail.peace_request_town", "mail.peace_request_empire",
						record.getPendingPeaceRequests());
			}
		}

		PendingCrownManager.CrownPrompt crown = PendingCrownManager.get(playerUuid);
		if (crown != null && !crown.monarchAssigned()) {
			MailData.MailEntry entry = baseEntry(TYPE_CROWN, true,
					"mail.crown_request", crown.emperorName(), crown.wartownName(), "",
					DiplomacyRecord.TYPE_TOWN, crown.wartownName(), crown.wartownId());
			entry.senderName = crown.wartownName();
			copyBanner(entry, DiplomacyRecord.TYPE_TOWN, crown.wartownId());
			data.entries.add(entry);
		}

		PlayerMailData stored = PlayerMailManager.load(playerUuid);
		for (StoredMailEntry entry : stored.getEntries()) {
			String type = entry.type != null && !entry.type.isEmpty() ? entry.type : TYPE_RESPONSE;
			String entityType = entry.entityType != null && !entry.entityType.isEmpty()
					? entry.entityType : null;
			String entityId = entry.entityId != null && !entry.entityId.isEmpty()
					? entry.entityId : null;
			String sender = entry.senderName != null && !entry.senderName.isEmpty()
					? entry.senderName
					: (entry.arg1 != null ? entry.arg1 : "");
			MailData.MailEntry mail = baseEntry(type, false,
					entry.messageKey, entry.arg1, entry.arg2, entry.arg3,
					entityType, sender, entry.id);
			mail.senderName = sender;
			if (entityType != null && entityId != null) {
				copyBanner(mail, entityType, entityId);
			}
			data.entries.add(mail);
		}

		return data;
	}

	private static void addDiplomacyPending(MailData data, String type,
	                                          String townMessageKey, String empireMessageKey,
	                                          java.util.Set<String> refs) {
		if (refs == null) {
			return;
		}
		for (String ref : refs) {
			String[] parts = DiplomacyRecord.parseRef(ref);
			if (parts == null) {
				continue;
			}
			String name = resolveFactionName(parts[0], parts[1]);
			if (name == null) {
				continue;
			}
			boolean empire = DiplomacyRecord.TYPE_EMPIRE.equals(parts[0]);
			String messageKey = empire ? empireMessageKey : townMessageKey;
			MailData.MailEntry entry = baseEntry(type, true, messageKey, "", "", "",
					parts[0], name, ref);
			entry.senderName = name;
			copyBanner(entry, parts[0], parts[1]);
			data.entries.add(entry);
		}
	}

	private static String resolveFactionName(String entityType, String entityId) {
		if (DiplomacyRecord.TYPE_EMPIRE.equals(entityType)) {
			EmpireData empire = EmpireDataManager.getInstance().getEmpire(entityId);
			return empire != null ? empire.getName() : null;
		}
		TownData town = TownDataManager.getInstance().getTown(entityId);
		return town != null ? town.getName() : null;
	}

	private static void copyBanner(MailData.MailEntry entry, String entityType, String entityId) {
		if (DiplomacyRecord.TYPE_EMPIRE.equals(entityType)) {
			EmpireData empire = EmpireDataManager.getInstance().getEmpire(entityId);
			if (empire != null) {
				if (empire.getBannerPatterns() != null) {
					entry.bannerPatterns.addAll(empire.getBannerPatterns());
				}
				entry.bannerBaseColor = empire.getBannerBaseColor();
				entry.bannerPixels = empire.getBannerPixels();
			}
			return;
		}
		TownData town = TownDataManager.getInstance().getTown(entityId);
		if (town != null) {
			if (town.getBannerPatterns() != null) {
				entry.bannerPatterns.addAll(town.getBannerPatterns());
			}
			entry.bannerBaseColor = town.getBannerBaseColor();
			entry.bannerPixels = town.getBannerPixels();
		}
	}

	private static MailData.MailEntry baseEntry(String type, boolean actionable,
	                                            String messageKey, String arg1, String arg2, String arg3,
	                                            String entityType, String entityName, String targetId) {
		MailData.MailEntry entry = new MailData.MailEntry();
		entry.id = type + ":" + (targetId != null ? targetId : UUID.randomUUID());
		entry.type = type;
		entry.actionable = actionable;
		entry.messageKey = messageKey;
		entry.arg1 = arg1 == null ? "" : arg1;
		entry.arg2 = arg2 == null ? "" : arg2;
		entry.arg3 = arg3 == null ? "" : arg3;
		entry.entityType = entityType;
		entry.entityName = entityName;
		entry.targetId = targetId;
		entry.senderName = "";
		entry.bannerPatterns = new ArrayList<>();
		entry.bannerBaseColor = "";
		entry.bannerPixels = "";
		return entry;
	}

	public static String toJson(MailData data) {
		return GSON.toJson(data);
	}

	public static MailData fromJson(String json) {
		MailData data = GSON.fromJson(json, MailData.class);
		if (data == null) {
			return new MailData();
		}
		if (data.entries == null) {
			data.entries = new ArrayList<>();
		}
		for (MailData.MailEntry entry : data.entries) {
			if (entry.bannerPatterns == null) {
				entry.bannerPatterns = new ArrayList<>();
			}
			if (entry.bannerBaseColor == null) {
				entry.bannerBaseColor = "";
			}
			if (entry.bannerPixels == null) {
				entry.bannerPixels = "";
			}
			if (entry.senderName == null) {
				entry.senderName = entry.entityName != null ? entry.entityName : "";
			}
		}
		return data;
	}
}
