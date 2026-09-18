package com.siegedempires.data;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which wartown an emperor is remotely managing in the Manage Wartown GUI.
 * Cleared when the client closes the screen or disconnects.
 */
public final class WartownManagementContext {
	private static final Map<UUID, String> ACTIVE = new ConcurrentHashMap<>();

	private WartownManagementContext() {
	}

	public static void set(UUID playerUuid, String wartownId) {
		if (playerUuid == null || wartownId == null || wartownId.isEmpty()) {
			return;
		}
		ACTIVE.put(playerUuid, wartownId);
	}

	public static String get(UUID playerUuid) {
		return playerUuid == null ? null : ACTIVE.get(playerUuid);
	}

	public static void clear(UUID playerUuid) {
		if (playerUuid != null) {
			ACTIVE.remove(playerUuid);
		}
	}
}
