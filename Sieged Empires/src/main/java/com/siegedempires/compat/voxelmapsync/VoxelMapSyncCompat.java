package com.siegedempires.compat.voxelmapsync;

import com.siegedempires.Siegedempires;
import com.siegedempires.model.TownData;

/**
 * Soft-loaded when VoxelMapSync is present. Refreshes in-game VoxelMap claim overlays
 * when town borders change.
 */
public final class VoxelMapSyncCompat {
	private VoxelMapSyncCompat() {
	}

	public static void onTownChanged(TownData town) {
		requestRefresh();
	}

	public static void onTownRemoved(String townId) {
		requestRefresh();
	}

	private static void requestRefresh() {
		try {
			Class<?> serverClass = Class.forName("com.voxmapsync.server.MapSyncServer");
			serverClass.getMethod("requestClaimsRefresh").invoke(null);
		} catch (ClassNotFoundException ignored) {
			// VoxelMapSync not installed
		} catch (Throwable t) {
			Siegedempires.LOGGER.warn("VoxelMapSync claim refresh failed", t);
		}
	}
}
