package com.siegedempires.mail;

import com.siegedempires.data.EmpireDataManager;
import com.siegedempires.data.PlayerMailManager;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.diplomacy.DiplomacyActions;
import com.siegedempires.model.DiplomacyRecord;
import com.siegedempires.model.EmpireData;
import com.siegedempires.model.TownData;
import com.siegedempires.network.DiplomacyBuilder;
import com.siegedempires.network.MailBuilder;
import com.siegedempires.network.ModNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class MailNotifications {
	private MailNotifications() {
	}

	public static void townInviteResponse(MinecraftServer server, UUID monarchUuid,
	                                      String playerName, String townName, boolean accepted) {
		if (monarchUuid == null) {
			return;
		}
		String key = accepted ? "mail.response.town_accept" : "mail.response.town_decline";
		PlayerMailManager.addResponseMail(monarchUuid, key, playerName, townName, null);
		PlayerMailManager.refreshOnlinePlayer(server, monarchUuid);
	}

	public static void empireInviteResponse(MinecraftServer server, UUID emperorUuid,
	                                        String townName, String empireName, boolean accepted) {
		if (emperorUuid == null) {
			return;
		}
		String key = accepted ? "mail.response.empire_accept" : "mail.response.empire_decline";
		PlayerMailManager.addResponseMail(emperorUuid, key, townName, empireName, null);
		PlayerMailManager.refreshOnlinePlayer(server, emperorUuid);
	}

	public static void crownResponse(MinecraftServer server, UUID emperorUuid,
	                                 String playerName, String wartownName, boolean accepted) {
		if (emperorUuid == null) {
			return;
		}
		String key = accepted ? "mail.response.crown_accept" : "mail.response.crown_decline";
		PlayerMailManager.addResponseMail(emperorUuid, key, playerName, wartownName, null);
		PlayerMailManager.refreshOnlinePlayer(server, emperorUuid);
	}

	public static void diplomacyResponse(MinecraftServer server,
	                                     DiplomacyActions.ManagedEntity sender,
	                                     DiplomacyActions.ManagedEntity responder,
	                                     String requestKind, boolean accepted) {
		if (server == null || sender == null || responder == null) {
			return;
		}
		String key = accepted ? "mail.response.diplomacy_accept" : "mail.response.diplomacy_decline";
		String kindKey = "mail.kind." + requestKind;
		for (ServerPlayer manager : server.getPlayerList().getPlayers()) {
			if (!DiplomacyBuilder.canManageDiplomacy(manager,
					TownDataManager.getInstance().getPlayerTown(manager.getUUID()))) {
				continue;
			}
			DiplomacyActions.ManagedEntity managed = DiplomacyActions.resolveForPlayer(manager);
			if (managed != null && managed.ref().equals(sender.ref())) {
				PlayerMailManager.addResponseMail(manager.getUUID(), key,
						responder.entityName(), kindKey, null);
				ModNetworking.sendMail(manager);
			}
		}
	}

	/** Notify the target faction's monarch/emperor that war was declared on them. */
	public static void warDeclared(MinecraftServer server,
	                               DiplomacyActions.ManagedEntity declarer,
	                               DiplomacyActions.ManagedEntity target) {
		if (server == null || declarer == null || target == null) {
			return;
		}
		UUID managerUuid = managerUuid(target);
		if (managerUuid == null) {
			return;
		}
		boolean empire = DiplomacyRecord.TYPE_EMPIRE.equals(declarer.entityType());
		String messageKey = empire ? "mail.war_declared_empire" : "mail.war_declared_town";
		PlayerMailManager.addStoredMail(managerUuid, MailBuilder.TYPE_WAR, messageKey,
				"", "", "",
				declarer.entityType(), declarer.entityId(), declarer.entityName());
		PlayerMailManager.refreshOnlinePlayer(server, managerUuid);
	}

	private static UUID managerUuid(DiplomacyActions.ManagedEntity faction) {
		if (DiplomacyRecord.TYPE_EMPIRE.equals(faction.entityType())) {
			EmpireData empire = EmpireDataManager.getInstance().getEmpire(faction.entityId());
			return empire != null ? empire.getEmperorUuid() : null;
		}
		TownData town = TownDataManager.getInstance().getTown(faction.entityId());
		return town != null ? town.getMonarchUuid() : null;
	}

	public static void refreshPlayer(ServerPlayer player) {
		PlayerMailManager.refreshOnlinePlayer(player);
	}
}
