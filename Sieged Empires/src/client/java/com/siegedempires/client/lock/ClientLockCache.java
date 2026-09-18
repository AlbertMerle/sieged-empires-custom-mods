package com.siegedempires.client.lock;

import net.minecraft.core.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of locked block positions for overlay rendering.
 * Populated by LockSyncPayload from the server.
 */
public final class ClientLockCache {
    private static final Map<String, Set<BlockPos>> lockedPositions = new ConcurrentHashMap<>();

    public static void clear() {
        lockedPositions.clear();
    }

    public static void setDimension(String dimension, List<String> posStrings) {
        Set<BlockPos> set = ConcurrentHashMap.newKeySet();
        for (String s : posStrings) {
            String[] parts = s.split(",");
            if (parts.length == 3) {
                try {
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    set.add(new BlockPos(x, y, z));
                } catch (NumberFormatException ignored) {}
            }
        }
        lockedPositions.put(dimension, set);
    }

    public static void addPosition(String dimension, BlockPos pos) {
        lockedPositions.computeIfAbsent(dimension, k -> ConcurrentHashMap.newKeySet()).add(pos);
    }

    public static void removePosition(String dimension, BlockPos pos) {
        var set = lockedPositions.get(dimension);
        if (set != null) set.remove(pos);
    }

	public static boolean isLocked(String dimension, BlockPos pos) {
		var set = lockedPositions.get(dimension);
		return set != null && set.contains(pos);
	}

	/** Snapshot of locked positions for a dimension (empty if none). */
	public static Set<BlockPos> getLockedPositions(String dimension) {
		var set = lockedPositions.get(dimension);
		return set == null || set.isEmpty() ? Set.of() : Set.copyOf(set);
	}

	/**
	 * Update cache from server sync JSON.
	 * JSON format: { "dimension_id": ["x,y,z", ...], ... }
	 */
    @SuppressWarnings("unchecked")
    public static void updateFromJson(String json) {
        if (json == null || json.isEmpty() || json.equals("{}")) {
            lockedPositions.clear();
            return;
        }
        try {
            var gson = new com.google.gson.Gson();
            Map<String, List<String>> data = gson.fromJson(json, Map.class);
            lockedPositions.clear();
            if (data != null) {
                for (var entry : data.entrySet()) {
                    setDimension(entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception e) {
            // Malformed JSON - ignore
        }
    }
}