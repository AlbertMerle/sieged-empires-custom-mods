package com.siegedempires.mail;

import com.siegedempires.data.EmpireDataManager;
import com.siegedempires.data.PendingCrownManager;
import com.siegedempires.data.PlayerMailManager;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.diplomacy.DiplomacyActions;
import com.siegedempires.model.EmpireData;
import com.siegedempires.model.TownData;
import com.siegedempires.network.MailBuilder;
import com.siegedempires.network.ModNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class MailActions {
	private MailActions() {
	}

	public static String accept(ServerPlayer player, String mailType, String targetId,
	                            String entityType, String entityName) {
		return switch (mailType) {
			case MailBuilder.TYPE_TOWN_INVITE -> acceptTownInvite(player, targetId);
			case MailBuilder.TYPE_EMPIRE_INVITE -> acceptEmpireInvite(player, targetId);
			case MailBuilder.TYPE_ALLY -> runDiplomacy(player, "allyaccept", entityType, entityName);
			case MailBuilder.TYPE_TRADE -> runDiplomacy(player, "tradeaccept", entityType, entityName);
			case MailBuilder.TYPE_OPEN_BORDERS -> runDiplomacy(player, "bordersaccept", entityType, entityName);
			case MailBuilder.TYPE_PEACE -> runDiplomacy(player, "peaceaccept", entityType, entityName);
			case MailBuilder.TYPE_CROWN -> "Open the mail and choose Duke or Duchess!";
			default -> "Unknown mail type!";
		};
	}

	public static String decline(ServerPlayer player, String mailType, String targetId,
	                             String entityType, String entityName) {
		return switch (mailType) {
			case MailBuilder.TYPE_TOWN_INVITE -> declineTownInvite(player, targetId);
			case MailBuilder.TYPE_EMPIRE_INVITE -> declineEmpireInvite(player, targetId);
			case MailBuilder.TYPE_ALLY -> runDiplomacy(player, "allydecline", entityType, entityName);
			case MailBuilder.TYPE_TRADE -> runDiplomacy(player, "tradedecline", entityType, entityName);
			case MailBuilder.TYPE_OPEN_BORDERS -> runDiplomacy(player, "bordersdecline", entityType, entityName);
			case MailBuilder.TYPE_PEACE -> runDiplomacy(player, "peacedecline", entityType, entityName);
			case MailBuilder.TYPE_CROWN -> declineCrown(player);
			default -> "Unknown mail type!";
		};
	}

	public static String dismiss(ServerPlayer player, String mailId) {
		if (mailId == null || mailId.isEmpty()) {
			return "Mail not found!";
		}
		// Stored response entries use the raw id; derived entries include a type prefix.
		String storedId = mailId.contains(":") ? mailId.substring(mailId.indexOf(':') + 1) : mailId;
		if (!PlayerMailManager.dismissStoredMail(player.getUUID(), storedId)) {
			return "Mail not found!";
		}
		ModNetworking.sendMail(player);
		return null;
	}

	private static String acceptTownInvite(ServerPlayer player, String townId) {
		TownData town = TownDataManager.getInstance().getTown(townId);
		if (town == null) {
			return "That town no longer exists!";
		}
		if (!town.isInvited(player.getUUID())) {
			return "You do not have an invite to that town!";
		}
		String townName = town.getName();
		UUID monarchUuid = town.getMonarchUuid();
		if (TownDataManager.getInstance().joinTown(player, townName)) {
			MailNotifications.townInviteResponse(player.level().getServer(), monarchUuid,
					player.getName().getString(), townName, true);
			ModNetworking.sendMail(player);
			return null;
		}
		return "Could not join that town!";
	}

	private static String declineTownInvite(ServerPlayer player, String townId) {
		TownData town = TownDataManager.getInstance().getTown(townId);
		if (town == null) {
			return "That town no longer exists!";
		}
		if (!town.isInvited(player.getUUID())) {
			return "You do not have an invite to that town!";
		}
		town.getInvitedPlayers().remove(player.getUUID());
		TownDataManager.getInstance().saveTown(town);
		MailNotifications.townInviteResponse(player.level().getServer(), town.getMonarchUuid(),
				player.getName().getString(), town.getName(), false);
		ModNetworking.sendMail(player);
		return null;
	}

	private static String acceptEmpireInvite(ServerPlayer player, String empireId) {
		TownData town = TownDataManager.getInstance().getPlayerTown(player.getUUID());
		if (town == null || !town.getMonarchUuid().equals(player.getUUID())) {
			return "Only your town monarch can accept an empire invite!";
		}
		EmpireData empire = EmpireDataManager.getInstance().getEmpire(empireId);
		if (empire == null) {
			return "That empire no longer exists!";
		}
		if (!empire.isInvited(town.getId())) {
			return "Your town does not have an invite to that empire!";
		}
		String error = EmpireDataManager.getInstance().joinEmpire(empireId, town);
		if (error != null) {
			return error;
		}
		MailNotifications.empireInviteResponse(player.level().getServer(), empire.getEmperorUuid(),
				town.getName(), empire.getName(), true);
		ModNetworking.sendMail(player);
		return null;
	}

	private static String declineEmpireInvite(ServerPlayer player, String empireId) {
		TownData town = TownDataManager.getInstance().getPlayerTown(player.getUUID());
		if (town == null || !town.getMonarchUuid().equals(player.getUUID())) {
			return "Only your town monarch can decline an empire invite!";
		}
		EmpireData empire = EmpireDataManager.getInstance().getEmpire(empireId);
		if (empire == null) {
			return "That empire no longer exists!";
		}
		if (!empire.isInvited(town.getId())) {
			return "Your town does not have an invite to that empire!";
		}
		empire.getInvitedTowns().remove(town.getId());
		EmpireDataManager.getInstance().saveEmpire(empire);
		MailNotifications.empireInviteResponse(player.level().getServer(), empire.getEmperorUuid(),
				town.getName(), empire.getName(), false);
		ModNetworking.sendMail(player);
		return null;
	}

	private static String declineCrown(ServerPlayer player) {
		PendingCrownManager.CrownPrompt prompt = PendingCrownManager.clear(player.getUUID());
		if (prompt == null) {
			return "You do not have a pending crown offer!";
		}
		MailNotifications.crownResponse(player.level().getServer(), prompt.emperorUuid(),
				player.getName().getString(), prompt.wartownName(), false);
		ModNetworking.sendMail(player);
		return null;
	}

	private static String runDiplomacy(ServerPlayer player, String action, String entityType, String entityName) {
		String safeName = entityName.replace("_", " ");
		String result = switch (action) {
			case "allyaccept" -> DiplomacyActions.acceptAlliance(player, entityType, safeName);
			case "allydecline" -> DiplomacyActions.declineAlliance(player, entityType, safeName);
			case "tradeaccept" -> DiplomacyActions.acceptTrade(player, entityType, safeName);
			case "tradedecline" -> DiplomacyActions.declineTrade(player, entityType, safeName);
			case "bordersaccept" -> DiplomacyActions.acceptOpenBorders(player, entityType, safeName);
			case "bordersdecline" -> DiplomacyActions.declineOpenBorders(player, entityType, safeName);
			case "peaceaccept" -> DiplomacyActions.acceptPeace(player, entityType, safeName);
			case "peacedecline" -> DiplomacyActions.declinePeace(player, entityType, safeName);
			default -> "Unknown action!";
		};
		if (result == null) {
			ModNetworking.sendMail(player);
		}
		return result;
	}
}
