package com.voxmapsync.client;

import com.voxmapsync.VoxelMapSync;
import com.voxmapsync.config.SyncConfig;
import com.voxmapsync.network.RegionUploadPayload;
import com.voxmapsync.network.RegionUploadRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Uploads locally explored VoxelMap region zips to the server when the server asks
 * or when the client detects tiles newer than the last successful upload.
 */
public final class ClientRegionUploader {
	private static final int PART_SIZE = 24 * 1024;

	private static final Deque<UploadJob> pending = new ArrayDeque<>();
	private static final Set<String> pendingKeys = ConcurrentHashMap.newKeySet();
	/** Last hash successfully uploaded per region key. */
	private static final Map<String, String> uploadedHashes = new ConcurrentHashMap<>();
	/** Server hash we were told about (from download or upload request). */
	private static final Map<String, String> serverHashes = new ConcurrentHashMap<>();
	private static long lastScanMs;
	private static long uploadBudgetBytes;
	private static long lastBudgetResetMs;

	private ClientRegionUploader() {
	}

	public static void resetSession() {
		pending.clear();
		pendingKeys.clear();
		uploadedHashes.clear();
		serverHashes.clear();
		lastScanMs = 0;
		uploadBudgetBytes = 0;
		lastBudgetResetMs = 0;
	}

	public static void noteServerHash(String key, String hash) {
		if (key != null && hash != null && !hash.isBlank()) {
			serverHashes.put(key, hash);
		}
	}

	public static void onUploadRequest(RegionUploadRequestPayload payload) {
		if (!SyncConfig.clientUploadEnabled) {
			return;
		}
		String dim = payload.dimension();
		int[] xs = payload.regionXs();
		int[] zs = payload.regionZs();
		String[] hashes = payload.serverHashes();
		int count = Math.min(xs.length, Math.min(zs.length, hashes.length));
		for (int i = 0; i < count; i++) {
			String key = dim + "|" + xs[i] + "," + zs[i];
			String serverHash = hashes[i] != null ? hashes[i] : "";
			if (!serverHash.isBlank()) {
				serverHashes.put(key, serverHash);
			}
			maybeQueueUpload(dim, xs[i], zs[i], serverHash, false);
		}
	}

	public static void tick() {
		if (!SyncConfig.clientUploadEnabled) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.getConnection() == null) {
			return;
		}
		if (!ClientPlayNetworking.canSend(RegionUploadPayload.TYPE)) {
			return;
		}

		if (ClientCacheApplier.applyQueueDepth() >= SyncConfig.clientApplyBackpressureThreshold) {
			return;
		}

		long now = System.currentTimeMillis();
		if (now - lastScanMs >= SyncConfig.clientUploadScanIntervalMs) {
			lastScanMs = now;
			scanNearPlayer(mc);
		}

		if (now - lastBudgetResetMs >= 1000) {
			uploadBudgetBytes = SyncConfig.maxClientUploadBytesPerSecond;
			lastBudgetResetMs = now;
		}
		drainUploadQueue();
	}

	private static void scanNearPlayer(Minecraft mc) {
		String dim = dimensionStorageName(mc);
		int regionX = (int) Math.floor(mc.player.getX() / 256.0);
		int regionZ = (int) Math.floor(mc.player.getZ() / 256.0);
		int radius = SyncConfig.clientUploadRadiusRegions;
		for (int dz = -radius; dz <= radius; dz++) {
			for (int dx = -radius; dx <= radius; dx++) {
				int rx = regionX + dx;
				int rz = regionZ + dz;
				String key = dim + "|" + rx + "," + rz;
				String serverHash = serverHashes.getOrDefault(key, "");
				maybeQueueUpload(dim, rx, rz, serverHash, false);
			}
		}
	}

	private static void maybeQueueUpload(String dim, int regionX, int regionZ, String serverHash, boolean priority) {
		String key = dim + "|" + regionX + "," + regionZ;
		if (pendingKeys.contains(key)) {
			return;
		}
		Path zipPath = ClientCacheApplier.cacheFilePath(dim, regionX, regionZ);
		if (zipPath == null || !Files.isRegularFile(zipPath)) {
			return;
		}
		byte[] zip;
		try {
			zip = Files.readAllBytes(zipPath);
		} catch (IOException e) {
			return;
		}
		if (zip.length == 0 || !isWorthUploading(zip)) {
			return;
		}
		String localHash = sha256(zip);
		if (localHash.equals(uploadedHashes.get(key))) {
			return;
		}
		if (!serverHash.isBlank() && localHash.equals(serverHash)) {
			uploadedHashes.put(key, localHash);
			return;
		}
		UploadJob job = new UploadJob(dim, regionX, regionZ, localHash, zip);
		if (priority) {
			pending.addFirst(job);
		} else {
			pending.addLast(job);
		}
		pendingKeys.add(key);
	}

	private static boolean isWorthUploading(byte[] zip) {
		return com.voxmapsync.server.RegionQuality.assessZip(zip).ok();
	}

	private static void drainUploadQueue() {
		while (!pending.isEmpty() && uploadBudgetBytes > 0) {
			UploadJob job = pending.peekFirst();
			if (job == null) {
				break;
			}
			int parts = Math.max(1, (job.zip.length + PART_SIZE - 1) / PART_SIZE);
			while (job.partIndex < parts && uploadBudgetBytes > 0) {
				int from = job.partIndex * PART_SIZE;
				int to = Math.min(job.zip.length, from + PART_SIZE);
				int cost = (to - from) + 96;
				if (job.partIndex > 0 && cost > uploadBudgetBytes) {
					return;
				}
				byte[] slice = new byte[to - from];
				System.arraycopy(job.zip, from, slice, 0, slice.length);
				ClientPlayNetworking.send(new RegionUploadPayload(
						job.dimension, job.regionX, job.regionZ, job.hash,
						job.partIndex, parts, slice));
				job.partIndex++;
				uploadBudgetBytes -= cost;
			}
			if (job.partIndex < parts) {
				return;
			}
			pending.removeFirst();
			String key = job.dimension + "|" + job.regionX + "," + job.regionZ;
			pendingKeys.remove(key);
			uploadedHashes.put(key, job.hash);
			serverHashes.put(key, job.hash);
			VoxelMapSync.LOGGER.debug("VoxelMapSync uploaded region {} ({} bytes)", key, job.zip.length);
		}
	}

	private static String dimensionStorageName(Minecraft mc) {
		var id = mc.level.dimension().identifier();
		return "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
	}

	private static String sha256(byte[] data) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(data));
		} catch (Exception e) {
			return Integer.toHexString(data.length);
		}
	}

	private static final class UploadJob {
		final String dimension;
		final int regionX;
		final int regionZ;
		final String hash;
		final byte[] zip;
		int partIndex;

		UploadJob(String dimension, int regionX, int regionZ, String hash, byte[] zip) {
			this.dimension = dimension;
			this.regionX = regionX;
			this.regionZ = regionZ;
			this.hash = hash;
			this.zip = zip;
		}
	}
}
