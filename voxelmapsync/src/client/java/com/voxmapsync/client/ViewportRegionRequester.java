package com.voxmapsync.client;

import com.mamiyaotaru.voxelmap.persistent.CachedRegion;
import com.mamiyaotaru.voxelmap.persistent.CompressibleMapData;
import com.mamiyaotaru.voxelmap.persistent.EmptyCachedRegion;
import com.mamiyaotaru.voxelmap.persistent.GuiPersistentMap;
import com.voxmapsync.VoxelMapSync;
import com.voxmapsync.WorldBorderRegions;
import com.voxmapsync.config.SyncConfig;
import com.voxmapsync.network.RegionRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.border.WorldBorder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * While the VoxelMap World Map is open, request empty/missing/partial region tiles from the server.
 * Requests follow the visible map viewport (plus margin), never beyond the world border.
 */
public final class ViewportRegionRequester {
	private static final int CHUNKS_PER_SIDE = 16;
	private static final int BLOCKS_PER_CHUNK = 16;

	private static long lastRequestMs;
	private static long clientSeq;
	private static int lastCenterRegionX = Integer.MIN_VALUE;
	private static int lastCenterRegionZ = Integer.MIN_VALUE;
	/** Last time we asked the server about a gappy (already-hashed) region — backoff to avoid spam. */
	private static final Map<String, Long> gapRecheckAtMs = new ConcurrentHashMap<>();
	private static Field regionsField;
	private static boolean fieldsResolved;
	private static boolean fieldsFailed;

	private ViewportRegionRequester() {
	}

	public static void tick(GuiPersistentMap screen) {
		if (!SyncConfig.viewportSyncEnabled) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.getConnection() == null) {
			return;
		}
		if (!ClientPlayNetworking.canSend(RegionRequestPayload.TYPE)) {
			return;
		}
		if (!resolveFields()) {
			return;
		}

		MapViewportMath.RegionBounds bounds = MapViewportMath.regionBounds(screen, SyncConfig.viewportMarginRegions);
		if (bounds == null) {
			return;
		}

		int centerRegionX = (bounds.left() + bounds.right()) / 2;
		int centerRegionZ = (bounds.top() + bounds.bottom()) / 2;
		long now = System.currentTimeMillis();
		boolean panned = centerRegionX != lastCenterRegionX || centerRegionZ != lastCenterRegionZ;
		int minInterval = panned
				? SyncConfig.viewportPanRequestIntervalMs
				: SyncConfig.viewportRequestIntervalMs;
		// Under apply backpressure, slow requests — do not stop entirely while blanks remain.
		if (ClientCacheApplier.applyQueueDepth() >= SyncConfig.clientApplyBackpressureThreshold) {
			minInterval = Math.max(minInterval, Math.max(2000, SyncConfig.viewportRequestIntervalMs * 2));
		}
		if (now - lastRequestMs < minInterval) {
			return;
		}

		try {
			String dim = dimensionStorageName(mc);
			WorldBorder border = mc.level.getWorldBorder();
			List<Integer> xs = new ArrayList<>();
			List<Integer> zs = new ArrayList<>();
			int max = SyncConfig.maxRegionsPerRequest;

			Map<Long, CachedRegion> visible = indexVisibleRegions(screen);

			List<int[]> coords = new ArrayList<>();
			for (int rz = bounds.top(); rz <= bounds.bottom(); rz++) {
				for (int rx = bounds.left(); rx <= bounds.right(); rx++) {
					if (!WorldBorderRegions.overlaps(border, rx, rz)) {
						continue;
					}
					coords.add(new int[]{rx, rz});
				}
			}
			coords.sort((a, b) -> {
				int da = Math.max(Math.abs(a[0] - centerRegionX), Math.abs(a[1] - centerRegionZ));
				int db = Math.max(Math.abs(b[0] - centerRegionX), Math.abs(b[1] - centerRegionZ));
				return Integer.compare(da, db);
			});

			for (int[] coord : coords) {
				if (xs.size() >= max) {
					break;
				}
				int rx = coord[0];
				int rz = coord[1];
				if (!needsRequest(dim, rx, rz, visible.get(pack(rx, rz)))) {
					continue;
				}
				xs.add(rx);
				zs.add(rz);
			}

			lastCenterRegionX = centerRegionX;
			lastCenterRegionZ = centerRegionZ;
			lastRequestMs = now;

			if (xs.isEmpty()) {
				return;
			}

			int[] regionXs = xs.stream().mapToInt(Integer::intValue).toArray();
			int[] regionZs = zs.stream().mapToInt(Integer::intValue).toArray();
			clientSeq++;
			RegionRequestPayload payload = new RegionRequestPayload(
					dim, centerRegionX, centerRegionZ, regionXs, regionZs, clientSeq);
			ClientPlayNetworking.send(payload);
			VoxelMapSync.LOGGER.debug(
					"VoxelMapSync viewport request dim={} center={},{} count={} panned={}",
					dim, centerRegionX, centerRegionZ, regionXs.length, panned);
		} catch (ReflectiveOperationException e) {
			VoxelMapSync.LOGGER.debug("Viewport request failed: {}", e.toString());
		}
	}

	/**
	 * Request when local VoxelMap tile is missing/empty, has no sync hash yet, or has
	 * unexplored gaps inside a previously explored square (server may have the full tile).
	 */
	private static boolean needsRequest(String dim, int rx, int rz, CachedRegion region) {
		String key = dim + "|" + rx + "," + rz;
		if (region == null || region instanceof EmptyCachedRegion || region.isEmpty()) {
			gapRecheckAtMs.remove(key);
			// Hash may exist from hello/send while VoxelMap still shows empty — clear so
			// server known-skip + client hello do not permanently stall the fill.
			ClientCacheApplier.clearHash(key);
			return true;
		}
		if (!ClientCacheApplier.hasHash(key)) {
			gapRecheckAtMs.remove(key);
			return true;
		}
		if (!SyncConfig.viewportGapFillEnabled) {
			return false;
		}
		if (!hasUnexploredGaps(region)) {
			gapRecheckAtMs.remove(key);
			return false;
		}
		long now = System.currentTimeMillis();
		Long nextAllowed = gapRecheckAtMs.get(key);
		if (nextAllowed != null && now < nextAllowed) {
			return false;
		}
		gapRecheckAtMs.put(key, now + SyncConfig.viewportGapRecheckMs);
		pruneGapRecheckMap(now);
		return true;
	}

	private static boolean hasUnexploredGaps(CachedRegion region) {
		if (!region.isLoaded()) {
			return false;
		}
		CompressibleMapData data = region.getMapData();
		if (data == null) {
			return false;
		}
		try {
			for (int cx = 0; cx < CHUNKS_PER_SIDE; cx++) {
				for (int cz = 0; cz < CHUNKS_PER_SIDE; cz++) {
					int x = cx * BLOCKS_PER_CHUNK;
					int z = cz * BLOCKS_PER_CHUNK;
					if (data.getHeight(x, z) == Short.MIN_VALUE && data.getLight(x, z) == 0) {
						return true;
					}
				}
			}
			return false;
		} catch (RuntimeException e) {
			VoxelMapSync.LOGGER.debug("Gap scan failed for {},{}: {}", region.getX(), region.getZ(), e.toString());
			return false;
		}
	}

	private static void pruneGapRecheckMap(long now) {
		if (gapRecheckAtMs.size() < 512) {
			return;
		}
		Iterator<Map.Entry<String, Long>> it = gapRecheckAtMs.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Long> e = it.next();
			if (e.getValue() < now) {
				it.remove();
			}
		}
	}

	private static Map<Long, CachedRegion> indexVisibleRegions(GuiPersistentMap screen)
			throws ReflectiveOperationException {
		Map<Long, CachedRegion> map = new HashMap<>();
		Object raw = regionsField.get(screen);
		if (!(raw instanceof CachedRegion[] regions)) {
			return map;
		}
		for (CachedRegion region : regions) {
			if (region == null) {
				continue;
			}
			map.put(pack(region.getX(), region.getZ()), region);
		}
		return map;
	}

	private static long pack(int x, int z) {
		return (((long) x) << 32) ^ (z & 0xffffffffL);
	}

	public static void resetSession() {
		lastRequestMs = 0;
		clientSeq = 0;
		lastCenterRegionX = Integer.MIN_VALUE;
		lastCenterRegionZ = Integer.MIN_VALUE;
		gapRecheckAtMs.clear();
	}

	private static String dimensionStorageName(Minecraft mc) {
		var id = mc.level.dimension().identifier();
		return "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
	}

	private static boolean resolveFields() {
		if (fieldsResolved) {
			return true;
		}
		if (fieldsFailed) {
			return false;
		}
		try {
			regionsField = GuiPersistentMap.class.getDeclaredField("regions");
			regionsField.setAccessible(true);
			fieldsResolved = true;
			return true;
		} catch (ReflectiveOperationException e) {
			fieldsFailed = true;
			VoxelMapSync.LOGGER.warn("VoxelMapSync viewport sync disabled — GuiPersistentMap.regions missing: {}", e.toString());
			return false;
		}
	}
}
