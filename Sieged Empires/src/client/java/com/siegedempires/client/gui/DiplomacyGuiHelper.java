package com.siegedempires.client.gui;

import net.minecraft.client.Minecraft;

public final class DiplomacyGuiHelper {
	private DiplomacyGuiHelper() {
	}

	public static void send(String action, String entityType, String entityName) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		String safeName = entityName.replace(" ", "_");
		minecraft.player.connection.sendCommand(
				"diplomacy " + action + " " + entityType + " " + safeName);
	}

	public static void acceptNotification(String inviteType, String entityType, String entityName) {
		String action = switch (inviteType) {
			case "trade" -> "tradeaccept";
			case "open_borders" -> "bordersaccept";
			case "peace" -> "peaceaccept";
			default -> "allyaccept";
		};
		send(action, entityType, entityName);
	}

	public static void declineNotification(String inviteType, String entityType, String entityName) {
		String action = switch (inviteType) {
			case "trade" -> "tradedecline";
			case "open_borders" -> "bordersdecline";
			case "peace" -> "peacedecline";
			default -> "allydecline";
		};
		send(action, entityType, entityName);
	}
}
