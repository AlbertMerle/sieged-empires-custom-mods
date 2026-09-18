package com.siegedempires.util;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-wide chat announcements. Only town/empire creation and war declarations
 * should appear in chat from this mod.
 */
public final class ChatAnnouncements {
	private ChatAnnouncements() {
	}

	public static void broadcast(MinecraftServer server, Component message) {
		if (server == null || message == null) {
			return;
		}
		server.getPlayerList().broadcastSystemMessage(message, false);
	}

	public static void broadcast(ServerPlayer player, Component message) {
		if (player == null) {
			return;
		}
		broadcast(player.level().getServer(), message);
	}

	public static void townOrNationCreated(ServerPlayer player, String type, String name) {
		broadcast(player, Component.literal("Created " + type + " of " + name + "!"));
	}

	public static void empireCreated(ServerPlayer player, String empireName, String title) {
		broadcast(player, Component.literal(
				"Created the Empire of " + empireName + "! You are the " + title + "."));
	}

	public static void warDeclared(ServerPlayer player, String declarerName, String targetName) {
		broadcast(player, Component.literal(declarerName + " has declared war on " + targetName + "!"));
	}
}
