package com.voxmapsync.client;

/**
 * Tracks whether the VoxelMap World Map screen is open (reload deferral).
 */
public final class ClientMapSession {
	private static volatile boolean worldMapOpen;

	private ClientMapSession() {
	}

	public static boolean isWorldMapOpen() {
		return worldMapOpen;
	}

	public static void setWorldMapOpen(boolean open) {
		worldMapOpen = open;
	}

	public static void resetSession() {
		worldMapOpen = false;
	}
}
