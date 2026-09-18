package com.voxmapsync.client;

import com.mamiyaotaru.voxelmap.persistent.CachedRegion;
import com.voxmapsync.VoxelMapSync;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * Soft-reload of VoxelMap cached regions after files are written.
 */
public final class VoxelMapReload {
	private VoxelMapReload() {
	}

	public static void invalidateRegion(String dimension, int regionX, int regionZ) {
		if (!FabricLoader.getInstance().isModLoaded("voxelmap")) {
			return;
		}
		try {
			Class<?> constants = Class.forName("com.mamiyaotaru.voxelmap.VoxelConstants");
			Object instance = constants.getMethod("getVoxelMapInstance").invoke(null);
			if (instance == null) {
				return;
			}
			Object persistentMap = instance.getClass().getMethod("getPersistentMap").invoke(instance);
			if (persistentMap == null) {
				return;
			}
			String key = regionX + "," + regionZ;
			Field regionsField = persistentMap.getClass().getDeclaredField("cachedRegions");
			Field poolField = persistentMap.getClass().getDeclaredField("cachedRegionsPool");
			regionsField.setAccessible(true);
			poolField.setAccessible(true);

			Object cached = regionsField.get(persistentMap);
			Object pooled = poolField.get(persistentMap);
			if (cached instanceof Map<?, ?> map) {
				CachedRegion region;
				synchronized (map) {
					Object removed = ((Map<Object, Object>) map).remove(key);
					region = removed instanceof CachedRegion cachedRegion ? cachedRegion : null;
				}
				if (region != null && region != CachedRegion.EMPTY_REGION) {
					if (pooled instanceof List<?> pool) {
						synchronized (pool) {
							pool.remove(region);
						}
					}
					region.cleanup();
				}
			}
			// PersistentMap.getRegions caches and returns the previous viewport array without
			// consulting cachedRegions when its bounds are unchanged. Reset that array so the
			// open World Map constructs and loads the replacement zip on its next render.
			persistentMap.getClass()
					.getMethod("getRegions", int.class, int.class, int.class, int.class)
					.invoke(persistentMap, 0, -1, 0, -1);
		} catch (Throwable t) {
			VoxelMapSync.LOGGER.debug("VoxelMap region invalidate skipped: {}", t.toString());
		}
	}
}
