package com.voxmapsync.client;

import com.voxmapsync.VoxelMapSync;
import com.voxmapsync.config.SyncConfig;
import com.voxmapsync.network.RegionDataPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Assembles multipart region payloads and writes them into VoxelMap's cache folder.
 * Applies and VoxelMap reloads are rate-limited so join/viewport sync cannot freeze the client.
 */
public final class ClientCacheApplier {
	private static final Pattern ZIP_NAME = Pattern.compile("^(-?\\d+),(-?\\d+)\\.zip$");
	private static final String MANIFEST_FILE = ".voxelmapsync_manifest.properties";
	private static final Map<String, PartBuffer> PARTS = new ConcurrentHashMap<>();
	private static final Map<String, String> KNOWN = new ConcurrentHashMap<>();
	/** Region keys waiting to be applied. Payloads are coalesced so repeated viewport
	 * force-resends cannot build a queue of identical disk writes. */
	private static final Deque<String> APPLY_QUEUE = new ArrayDeque<>();
	private static final Map<String, PendingApply> PENDING_APPLIES = new ConcurrentHashMap<>();
	/** Region invalidations are likewise unique; reloading the same VoxelMap tile repeatedly
	 * is expensive and was a major source of map-screen hitching. */
	private static final Deque<String> RELOAD_QUEUE = new ArrayDeque<>();
	private static final Map<String, ReloadJob> PENDING_RELOADS = new ConcurrentHashMap<>();
	private static final AtomicInteger WRITE_COUNT = new AtomicInteger();
	private static final AtomicBoolean SCANNING = new AtomicBoolean(false);
	private static final ExecutorService WRITE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "VoxelMapSync-Writer");
		t.setDaemon(true);
		return t;
	});

	private static Properties manifest = new Properties();
	private static Path manifestPath;
	private static int applyBudget;
	private static long lastApplyBudgetResetMs;
	private static boolean manifestDirty;
	private static long lastManifestSaveMs;

	private ClientCacheApplier() {
	}

	public static void resetSession() {
		PARTS.clear();
		KNOWN.clear();
		APPLY_QUEUE.clear();
		PENDING_APPLIES.clear();
		RELOAD_QUEUE.clear();
		PENDING_RELOADS.clear();
		WRITE_COUNT.set(0);
		applyBudget = 0;
		lastApplyBudgetResetMs = 0;
		manifestDirty = false;
		lastManifestSaveMs = 0;
		manifest = new Properties();
		manifestPath = null;
		ViewportRegionRequester.resetSession();
		ClientMapSession.resetSession();
		if (SyncConfig.clientUploadEnabled) {
			ClientRegionUploader.resetSession();
		}
	}

	public static int applyQueueDepth() {
		synchronized (APPLY_QUEUE) {
			return PENDING_APPLIES.size();
		}
	}

	/** Called every client tick — drains apply budget and spreads VoxelMap reloads. */
	public static void tick() {
		long now = System.currentTimeMillis();
		if (now - lastApplyBudgetResetMs >= 1000) {
			applyBudget = SyncConfig.tilesPerSecond;
			lastApplyBudgetResetMs = now;
		}
		submitPendingApplies();
		drainReloadQueue();
		flushManifestIfDue();
	}

	/**
	 * On join: optionally load the hash manifest and send hello immediately, then verify
	 * local cache in the background without blocking the first hello.
	 */
	public static void prepareJoin(Runnable onHelloReady) {
		if (SyncConfig.clientFastJoinHello) {
			int loaded = loadManifestIntoKnown();
			VoxelMapSync.LOGGER.info("VoxelMapSync manifest loaded — {} region(s) known locally", loaded);
			if (onHelloReady != null) {
				onHelloReady.run();
			}
			startBackgroundCacheVerify(onHelloReady);
			return;
		}
		loadKnownHashesFromDisk(onHelloReady);
	}

	/** Known region hashes to send on join (optionally limited to nearby tiles). */
	public static Map<String, String> helloHashes() {
		Map<String, String> known = knownHashes();
		int radius = SyncConfig.clientHelloRadiusRegions;
		if (radius <= 0) {
			return known;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return known;
		}
		String playerDim = dimensionStorageName(mc);
		int playerRegionX = (int) Math.floor(mc.player.getX() / 256.0);
		int playerRegionZ = (int) Math.floor(mc.player.getZ() / 256.0);
		Map<String, String> filtered = new java.util.LinkedHashMap<>();
		for (Map.Entry<String, String> entry : known.entrySet()) {
			String key = entry.getKey();
			int sep = key.indexOf('|');
			if (sep < 0) {
				continue;
			}
			if (!playerDim.equals(key.substring(0, sep))) {
				continue;
			}
			int comma = key.indexOf(',', sep + 1);
			if (comma < 0) {
				continue;
			}
			try {
				int rx = Integer.parseInt(key.substring(sep + 1, comma));
				int rz = Integer.parseInt(key.substring(comma + 1));
				int dist = Math.max(Math.abs(rx - playerRegionX), Math.abs(rz - playerRegionZ));
				if (dist <= radius) {
					filtered.put(key, entry.getValue());
				}
			} catch (NumberFormatException ignored) {
			}
		}
		return filtered;
	}

	private static String dimensionStorageName(Minecraft mc) {
		var id = mc.level.dimension().identifier();
		return "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
	}

	/** Path where VoxelMap reads/writes a region zip for the current world. */
	public static Path cacheFilePath(String dimension, int regionX, int regionZ) {
		return cacheFile(dimension, regionX, regionZ);
	}

	public static boolean hasHash(String key) {
		return key != null && KNOWN.containsKey(key);
	}

	/** Drop a known hash so join/hello state cannot block a viewport refill for an empty tile. */
	public static void clearHash(String key) {
		if (key != null) {
			KNOWN.remove(key);
		}
	}

	/** Scan local VoxelMap cache on a background thread, then run {@code onComplete} on the client thread. */
	public static void loadKnownHashesFromDisk(Runnable onComplete) {
		if (!SCANNING.compareAndSet(false, true)) {
			if (onComplete != null) {
				Minecraft.getInstance().execute(onComplete);
			}
			return;
		}
		Thread scan = new Thread(() -> {
			int loaded = 0;
			try {
				Path worldCache = resolveWorldCacheDir();
				if (worldCache != null && Files.isDirectory(worldCache)) {
					loaded = scanCacheDir(worldCache, Integer.MAX_VALUE);
					saveManifest();
				}
			} catch (Exception e) {
				VoxelMapSync.LOGGER.warn("VoxelMapSync cache scan failed", e);
			} finally {
				SCANNING.set(false);
				VoxelMapSync.LOGGER.info("VoxelMapSync cache scan done — {} region(s) known locally", loaded);
				if (onComplete != null) {
					Minecraft.getInstance().execute(onComplete);
				}
			}
		}, "VoxelMapSync-CacheScan");
		scan.setDaemon(true);
		scan.start();
	}

	private static void startBackgroundCacheVerify(Runnable onComplete) {
		if (!SyncConfig.clientBackgroundCacheVerify) {
			return;
		}
		if (!SCANNING.compareAndSet(false, true)) {
			return;
		}
		Thread scan = new Thread(() -> {
			int loaded = 0;
			try {
				Path worldCache = resolveWorldCacheDir();
				if (worldCache != null && Files.isDirectory(worldCache)) {
					int budget = SyncConfig.clientCacheScanMaxPerSecond > 0
							? SyncConfig.clientCacheScanMaxPerSecond
							: Integer.MAX_VALUE;
					loaded = scanCacheDir(worldCache, budget);
					saveManifest();
				}
			} catch (Exception e) {
				VoxelMapSync.LOGGER.warn("VoxelMapSync background cache verify failed", e);
			} finally {
				SCANNING.set(false);
				if (loaded > 0) {
					VoxelMapSync.LOGGER.info("VoxelMapSync cache verify updated {} region hash(es)", loaded);
					if (onComplete != null) {
						Minecraft.getInstance().execute(onComplete);
					}
				}
			}
		}, "VoxelMapSync-CacheVerify");
		scan.setDaemon(true);
		scan.start();
	}

	public static String currentWorldKey() {
		String fromVoxel = voxelMapWorldKey();
		if (fromVoxel != null && !fromVoxel.isBlank()) {
			return fromVoxel;
		}
		Minecraft mc = Minecraft.getInstance();
		ServerData server = mc.getCurrentServer();
		if (server != null && server.ip != null && !server.ip.isBlank()) {
			return scrubNameFile(server.ip.toLowerCase(Locale.ROOT));
		}
		if (mc.getConnection() != null && mc.getConnection().getConnection() != null) {
			try {
				var addr = mc.getConnection().getConnection().getRemoteAddress();
				if (addr instanceof java.net.InetSocketAddress inet) {
					return scrubNameFile((inet.getHostString() + ":" + inet.getPort()).toLowerCase(Locale.ROOT));
				}
			} catch (Exception ignored) {
			}
		}
		return "unknown";
	}

	public static Map<String, String> knownHashes() {
		return Map.copyOf(KNOWN);
	}

	public static void onRegionPart(RegionDataPayload payload) {
		String key = payload.dimension() + "|" + payload.regionX() + "," + payload.regionZ();
		PartBuffer buffer = PARTS.computeIfAbsent(key, k -> new PartBuffer(payload.partCount(), payload.contentHash()));
		if (!buffer.hash.equals(payload.contentHash()) || buffer.parts.length != payload.partCount()) {
			PARTS.put(key, buffer = new PartBuffer(payload.partCount(), payload.contentHash()));
		}
		int index = payload.partIndex();
		if (index < 0 || index >= buffer.parts.length) {
			VoxelMapSync.LOGGER.warn("Bad region part index {} for {}", index, key);
			return;
		}
		if (buffer.parts[index] == null) {
			buffer.received++;
		}
		buffer.parts[index] = payload.data();
		if (buffer.received < buffer.parts.length) {
			return;
		}
		PARTS.remove(key);
		int total = 0;
		for (byte[] part : buffer.parts) {
			if (part == null) {
				VoxelMapSync.LOGGER.warn("Incomplete region {}", key);
				return;
			}
			total += part.length;
		}
		byte[] zip = new byte[total];
		int offset = 0;
		for (byte[] part : buffer.parts) {
			System.arraycopy(part, 0, zip, offset, part.length);
			offset += part.length;
		}
		// Receiving a region is authoritative. The server deliberately force-resends
		// viewport holes and /mapsync rerender results, so a matching manifest hash does
		// not prove that VoxelMap's on-disk or in-memory tile is usable.
		enqueueApply(payload.dimension(), payload.regionX(), payload.regionZ(), payload.contentHash(), zip);
	}

	private static void enqueueApply(String dim, int regionX, int regionZ, String hash, byte[] zip) {
		String key = dim + "|" + regionX + "," + regionZ;
		PendingApply replacement = new PendingApply(dim, regionX, regionZ, hash, zip);
		synchronized (APPLY_QUEUE) {
			if (PENDING_APPLIES.put(key, replacement) == null) {
				APPLY_QUEUE.addLast(key);
			}
		}
	}

	private static void submitPendingApplies() {
		while (applyBudget > 0) {
			String key;
			PendingApply job;
			synchronized (APPLY_QUEUE) {
				key = APPLY_QUEUE.pollFirst();
				job = key != null ? PENDING_APPLIES.remove(key) : null;
			}
			if (job == null) {
				if (key == null) {
					return;
				}
				continue;
			}
			applyBudget--;
			WRITE_EXECUTOR.execute(() -> applyRegion(job));
		}
	}

	private static void applyRegion(PendingApply job) {
		String key = job.dimension + "|" + job.regionX + "," + job.regionZ;
		try {
			Path out = cacheFile(job.dimension, job.regionX, job.regionZ);
			Files.createDirectories(out.getParent());
			Files.write(out, job.zip);
			KNOWN.put(key, job.hash);
			noteManifestEntry(key, job.hash, out);
			markManifestDirty();
			if (SyncConfig.clientUploadEnabled) {
				ClientRegionUploader.noteServerHash(key, job.hash);
			}
			int n = WRITE_COUNT.incrementAndGet();
			if (n <= 5 || n % 50 == 0) {
				VoxelMapSync.LOGGER.info("Wrote VoxelMap region {} ({} bytes) → {}", key, job.zip.length, out);
			} else {
				VoxelMapSync.LOGGER.debug("Wrote VoxelMap region {} ({} bytes)", key, job.zip.length);
			}
			enqueueReload(new ReloadJob(job.dimension, job.regionX, job.regionZ));
		} catch (IOException e) {
			VoxelMapSync.LOGGER.warn("Failed to write region {}", key, e);
		}
	}

	private static void enqueueReload(ReloadJob job) {
		String key = job.dimension + "|" + job.regionX + "," + job.regionZ;
		synchronized (RELOAD_QUEUE) {
			if (PENDING_RELOADS.putIfAbsent(key, job) == null) {
				RELOAD_QUEUE.addLast(key);
			}
		}
	}

	private static void drainReloadQueue() {
		if (SyncConfig.clientDeferReloadUntilMapOpen && !ClientMapSession.isWorldMapOpen()) {
			return;
		}
		int limit = SyncConfig.clientReloadMaxPerTick;
		for (int i = 0; i < limit; i++) {
			String key;
			ReloadJob job;
			synchronized (RELOAD_QUEUE) {
				key = RELOAD_QUEUE.pollFirst();
				job = key != null ? PENDING_RELOADS.remove(key) : null;
			}
			if (job == null) {
				if (key == null) {
					return;
				}
				continue;
			}
			VoxelMapReload.invalidateRegion(job.dimension, job.regionX, job.regionZ);
		}
	}

	private static Path cacheFile(String dimension, int regionX, int regionZ) {
		Path worldCache = resolveWorldCacheDir();
		if (worldCache == null) {
			worldCache = Minecraft.getInstance().gameDirectory.toPath()
					.resolve("voxelmap").resolve("cache").resolve(currentWorldKey());
		}
		String dimPath = scrubNameFile(dimension);
		return worldCache.resolve(dimPath).resolve(regionX + "," + regionZ + ".zip");
	}

	private static Path resolveWorldCacheDir() {
		Path worldCache = voxelMapWorldCacheDir();
		if (worldCache == null) {
			worldCache = Minecraft.getInstance().gameDirectory.toPath()
					.resolve("voxelmap").resolve("cache").resolve(currentWorldKey());
		}
		String subworld = voxelMapSubworldName();
		if (subworld != null && !subworld.isBlank()) {
			worldCache = worldCache.resolve(scrubNameFile(subworld));
		}
		return worldCache;
	}

	private static int loadManifestIntoKnown() {
		Path worldCache = resolveWorldCacheDir();
		if (worldCache == null) {
			return 0;
		}
		manifestPath = worldCache.resolve(MANIFEST_FILE);
		manifest = new Properties();
		if (Files.isRegularFile(manifestPath)) {
			try (InputStream in = Files.newInputStream(manifestPath)) {
				manifest.load(in);
			} catch (IOException e) {
				VoxelMapSync.LOGGER.debug("VoxelMapSync manifest read failed: {}", e.toString());
			}
		}
		int loaded = 0;
		for (String key : manifest.stringPropertyNames()) {
			String value = manifest.getProperty(key);
			if (value == null || value.isBlank()) {
				continue;
			}
			String hash = value.split("\\|", 2)[0];
			if (!hash.isBlank()) {
				KNOWN.put(key, hash);
				loaded++;
			}
		}
		return loaded;
	}

	private static void noteManifestEntry(String key, String hash, Path zipPath) {
		try {
			long mtime = Files.getLastModifiedTime(zipPath).toMillis();
			long size = Files.size(zipPath);
			manifest.setProperty(key, hash + "|" + mtime + "|" + size);
		} catch (IOException e) {
			manifest.setProperty(key, hash);
		}
	}

	private static void markManifestDirty() {
		manifestDirty = true;
	}

	private static void flushManifestIfDue() {
		if (!manifestDirty) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastManifestSaveMs < SyncConfig.clientManifestSaveIntervalMs) {
			return;
		}
		saveManifest();
	}

	private static void saveManifest() {
		if (manifestPath == null) {
			Path worldCache = resolveWorldCacheDir();
			if (worldCache == null) {
				return;
			}
			manifestPath = worldCache.resolve(MANIFEST_FILE);
		}
		try {
			Files.createDirectories(manifestPath.getParent());
			try (OutputStream out = Files.newOutputStream(manifestPath)) {
				manifest.store(out, "VoxelMapSync local region hashes");
			}
			manifestDirty = false;
			lastManifestSaveMs = System.currentTimeMillis();
		} catch (IOException e) {
			VoxelMapSync.LOGGER.debug("VoxelMapSync manifest write failed: {}", e.toString());
		}
	}

	/** Same rules as VoxelMap TextUtils.scrubNameFile. */
	static String scrubNameFile(String input) {
		if (input == null) {
			return "";
		}
		return input
				.replace("<", "~less~")
				.replace(">", "~greater~")
				.replace(":", "~colon~")
				.replace("\"", "~quote~")
				.replace("/", "~slash~")
				.replace("\\", "~backslash~")
				.replace("|", "~pipe~")
				.replace("?", "~question~")
				.replace("*", "~star~");
	}

	private static String voxelMapWorldKey() {
		try {
			Class<?> constants = Class.forName("com.mamiyaotaru.voxelmap.VoxelConstants");
			Object instance = constants.getMethod("getVoxelMapInstance").invoke(null);
			if (instance == null) {
				return null;
			}
			Object wm = instance.getClass().getMethod("getWaypointManager").invoke(instance);
			String worldName = (String) wm.getClass().getMethod("getCurrentWorldName").invoke(wm);
			if (worldName == null || worldName.isBlank()) {
				return null;
			}
			return scrubNameFile(worldName);
		} catch (Throwable t) {
			return null;
		}
	}

	private static Path voxelMapWorldCacheDir() {
		try {
			Class<?> constants = Class.forName("com.mamiyaotaru.voxelmap.VoxelConstants");
			Object instance = constants.getMethod("getVoxelMapInstance").invoke(null);
			if (instance == null) {
				return null;
			}
			Object dataStore = instance.getClass().getMethod("getDataStore").invoke(instance);
			File dir = (File) dataStore.getClass().getMethod("getWorldCacheDir").invoke(dataStore);
			return dir != null ? dir.toPath() : null;
		} catch (Throwable t) {
			return null;
		}
	}

	private static String voxelMapSubworldName() {
		try {
			Class<?> constants = Class.forName("com.mamiyaotaru.voxelmap.VoxelConstants");
			Object instance = constants.getMethod("getVoxelMapInstance").invoke(null);
			if (instance == null) {
				return "";
			}
			Object wm = instance.getClass().getMethod("getWaypointManager").invoke(instance);
			String sub = (String) wm.getClass().getMethod("getCurrentSubworldDescriptor", boolean.class).invoke(wm, false);
			return sub != null ? sub : "";
		} catch (Throwable t) {
			return "";
		}
	}

	private static int scanCacheDir(Path worldCache, int maxHashes) throws IOException {
		if (manifestPath == null) {
			manifestPath = worldCache.resolve(MANIFEST_FILE);
		}
		int loaded = 0;
		int hashed = 0;
		try (var dims = Files.list(worldCache)) {
			for (Path dimDir : dims.filter(Files::isDirectory).toList()) {
				String dim = dimDir.getFileName().toString();
				try (var zips = Files.newDirectoryStream(dimDir, "*.zip")) {
					for (Path zipPath : zips) {
						Matcher matcher = ZIP_NAME.matcher(zipPath.getFileName().toString());
						if (!matcher.matches()) {
							continue;
						}
						int regionX = Integer.parseInt(matcher.group(1));
						int regionZ = Integer.parseInt(matcher.group(2));
						String key = dim + "|" + regionX + "," + regionZ;
						String hash = hashZipIfNeeded(key, zipPath, maxHashes, hashed);
						if (hash == null) {
							continue;
						}
						if (!hash.equals(KNOWN.get(key))) {
							KNOWN.put(key, hash);
							loaded++;
						}
						hashed++;
					}
				}
			}
		}
		return loaded;
	}

	private static String hashZipIfNeeded(String key, Path zipPath, int maxHashes, int hashedSoFar) throws IOException {
		long size = Files.size(zipPath);
		if (size <= 0) {
			return null;
		}
		FileTime mtime = Files.getLastModifiedTime(zipPath);
		String cached = manifest.getProperty(key);
		if (cached != null && !cached.isBlank()) {
			String[] parts = cached.split("\\|");
			if (parts.length >= 3) {
				try {
					long cachedMtime = Long.parseLong(parts[1]);
					long cachedSize = Long.parseLong(parts[2]);
					if (cachedMtime == mtime.toMillis() && cachedSize == size) {
						return parts[0];
					}
				} catch (NumberFormatException ignored) {
				}
			} else if (parts.length == 1 && KNOWN.containsKey(key) && parts[0].equals(KNOWN.get(key))) {
				return parts[0];
			}
		}
		if (maxHashes != Integer.MAX_VALUE && hashedSoFar >= maxHashes) {
			return KNOWN.get(key);
		}
		byte[] zip = Files.readAllBytes(zipPath);
		String hash = sha256(zip);
		noteManifestEntry(key, hash, zipPath);
		return hash;
	}

	private static String sha256(byte[] data) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(data));
		} catch (Exception e) {
			return Integer.toHexString(data.length);
		}
	}

	private static final class PartBuffer {
		final byte[][] parts;
		final String hash;
		int received;

		PartBuffer(int count, String hash) {
			this.parts = new byte[count][];
			this.hash = hash;
		}
	}

	private static final class PendingApply {
		final String dimension;
		final int regionX;
		final int regionZ;
		final String hash;
		final byte[] zip;

		PendingApply(String dimension, int regionX, int regionZ, String hash, byte[] zip) {
			this.dimension = dimension;
			this.regionX = regionX;
			this.regionZ = regionZ;
			this.hash = hash;
			this.zip = zip;
		}
	}

	private static final class ReloadJob {
		final String dimension;
		final int regionX;
		final int regionZ;

		ReloadJob(String dimension, int regionX, int regionZ) {
			this.dimension = dimension;
			this.regionX = regionX;
			this.regionZ = regionZ;
		}
	}
}
