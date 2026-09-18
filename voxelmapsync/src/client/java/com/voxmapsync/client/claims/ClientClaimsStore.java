package com.voxmapsync.client.claims;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;
import com.voxmapsync.VoxelMapSync;
import com.voxmapsync.claims.SiegedEmpiresClaimsBridge.ClaimTownDto;
import com.voxmapsync.config.SyncConfig;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ClientClaimsStore {
	private static final Gson GSON = new Gson();
	private static final Type LIST_TYPE = new TypeToken<List<ClaimTownDto>>() {}.getType();
	private static volatile List<ClaimTownDto> towns = List.of();
	private static volatile int chunkCount;

	private ClientClaimsStore() {
	}

	public static void apply(String json) {
		try {
			List<ClaimTownDto> parsed = GSON.fromJson(json, LIST_TYPE);
			if (parsed == null) {
				towns = List.of();
				chunkCount = 0;
			} else {
				int chunks = 0;
				for (ClaimTownDto town : parsed) {
					town.chunks = normalizeChunks(town.chunks);
					if (town.chunks != null) {
						chunks += town.chunks.size();
					}
				}
				towns = Collections.unmodifiableList(new ArrayList<>(parsed));
				chunkCount = chunks;
			}
			VoxelMapSync.LOGGER.info("VoxelMapSync claims overlay updated: {} town(s), {} chunk(s)",
					towns.size(), chunkCount);
		} catch (Exception e) {
			VoxelMapSync.LOGGER.warn("Bad claims payload", e);
		}
	}

	public static List<ClaimTownDto> towns() {
		return SyncConfig.claimsEnabled ? towns : List.of();
	}

	public static int chunkCount() {
		return SyncConfig.claimsEnabled ? chunkCount : 0;
	}

	/**
	 * Gson can deserialize nested arrays as {@link List} of {@link Number} instead of {@code int[]}.
	 */
	private static List<int[]> normalizeChunks(List<int[]> raw) {
		if (raw == null || raw.isEmpty()) {
			return List.of();
		}
		List<int[]> out = new ArrayList<>(raw.size());
		for (Object entry : raw) {
			int[] chunk = toChunk(entry);
			if (chunk != null) {
				out.add(chunk);
			}
		}
		return out;
	}

	private static int[] toChunk(Object entry) {
		if (entry instanceof int[] ints && ints.length >= 2) {
			return ints;
		}
		if (entry instanceof List<?> list && list.size() >= 2) {
			try {
				return new int[]{
						((Number) list.get(0)).intValue(),
						((Number) list.get(1)).intValue()
				};
			} catch (RuntimeException ignored) {
				return null;
			}
		}
		if (entry instanceof JsonArray arr && arr.size() >= 2) {
			try {
				return new int[]{
						arr.get(0).getAsInt(),
						arr.get(1).getAsInt()
				};
			} catch (RuntimeException ignored) {
				return null;
			}
		}
		return null;
	}
}
