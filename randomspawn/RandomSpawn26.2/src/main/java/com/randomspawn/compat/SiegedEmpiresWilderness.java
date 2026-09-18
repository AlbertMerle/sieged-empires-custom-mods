package com.randomspawn.compat;

import com.siegedempires.data.TownDataManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;

/**
 * Optional Sieged Empires integration — rejects spawn positions inside town claims.
 */
public final class SiegedEmpiresWilderness {
	private SiegedEmpiresWilderness() {
	}

	public static boolean isAvailable() {
		return FabricLoader.getInstance().isModLoaded("siegedempires");
	}

	public static boolean isWilderness(ServerLevel world, int blockX, int blockZ) {
		if (!isAvailable()) {
			return true;
		}
		String dimension = world.dimension().identifier().toString();
		int chunkX = blockX >> 4;
		int chunkZ = blockZ >> 4;
		return TownDataManager.getInstance().getTownAtChunk(chunkX, chunkZ, dimension) == null;
	}
}
