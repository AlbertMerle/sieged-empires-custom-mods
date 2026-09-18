package com.siegedempires.data;

import com.siegedempires.Siegedempires;
import com.siegedempires.model.PlayerMailData;
import com.siegedempires.model.StoredMailEntry;
import com.siegedempires.network.ModNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.UUID;

public final class PlayerMailManager {
	private static final String MAIL_FOLDER = "config/SiegedEmpires/Mail";
	private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder().setPrettyPrinting().create();

	private PlayerMailManager() {
	}

	public static void initialize() {
		try {
			Files.createDirectories(Paths.get(MAIL_FOLDER));
		} catch (IOException e) {
			Siegedempires.LOGGER.error("Failed to create mail folder", e);
		}
	}

	public static void addResponseMail(UUID playerUuid, String messageKey, String arg1, String arg2, String arg3) {
		addStoredMail(playerUuid, "response", messageKey, arg1, arg2, arg3, null, null, null);
	}

	public static void addStoredMail(UUID playerUuid, String type, String messageKey,
	                                 String arg1, String arg2, String arg3,
	                                 String entityType, String entityId, String senderName) {
		if (playerUuid == null) {
			return;
		}
		PlayerMailData data = load(playerUuid);
		StoredMailEntry entry = new StoredMailEntry(
				UUID.randomUUID().toString(),
				type,
				messageKey,
				arg1,
				arg2,
				arg3,
				entityType,
				entityId,
				senderName);
		data.getEntries().add(entry);
		save(playerUuid, data);
	}

	public static boolean dismissStoredMail(UUID playerUuid, String mailId) {
		PlayerMailData data = load(playerUuid);
		Iterator<StoredMailEntry> it = data.getEntries().iterator();
		while (it.hasNext()) {
			if (mailId.equals(it.next().id)) {
				it.remove();
				save(playerUuid, data);
				return true;
			}
		}
		return false;
	}

	public static PlayerMailData load(UUID playerUuid) {
		File file = mailFile(playerUuid);
		if (!file.exists()) {
			return new PlayerMailData();
		}
		try (FileReader reader = new FileReader(file)) {
			PlayerMailData data = GSON.fromJson(reader, PlayerMailData.class);
			return data != null ? data : new PlayerMailData();
		} catch (IOException e) {
			Siegedempires.LOGGER.error("Failed to load mail for " + playerUuid, e);
			return new PlayerMailData();
		}
	}

	public static void save(UUID playerUuid, PlayerMailData data) {
		File file = mailFile(playerUuid);
		try (FileWriter writer = new FileWriter(file)) {
			GSON.toJson(data, writer);
		} catch (IOException e) {
			Siegedempires.LOGGER.error("Failed to save mail for " + playerUuid, e);
		}
	}

	public static void refreshOnlinePlayer(ServerPlayer player) {
		if (player != null) {
			ModNetworking.sendMail(player);
		}
	}

	public static void refreshOnlinePlayer(MinecraftServer server, UUID playerUuid) {
		if (server == null || playerUuid == null) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
		if (player != null) {
			ModNetworking.sendMail(player);
		}
	}

	private static File mailFile(UUID playerUuid) {
		return new File(MAIL_FOLDER, playerUuid + ".json");
	}
}
