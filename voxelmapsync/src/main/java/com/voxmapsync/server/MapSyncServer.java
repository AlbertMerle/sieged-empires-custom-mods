package com.voxmapsync.server;

import com.voxmapsync.VoxelMapSync;
import com.voxmapsync.WorldBorderRegions;
import com.voxmapsync.claims.SiegedEmpiresClaimsBridge;
import com.voxmapsync.config.SyncConfig;
import com.voxmapsync.network.ClaimsPayload;
import com.voxmapsync.network.ClientHelloPayload;
import com.voxmapsync.network.RegionDataPayload;
import com.voxmapsync.network.RegionRequestPayload;
import com.voxmapsync.network.RegionUploadPayload;
import com.voxmapsync.network.RegionUploadRequestPayload;
import com.voxmapsync.server.mca.McaReader;
import com.voxmapsync.server.webmap.WebMapExporter;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class MapSyncServer {
	private static final Pattern MCA_NAME = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)\\.mca$");
	private static final Pattern ZIP_NAME = Pattern.compile("^(-?\\d+),(-?\\d+)\\.zip$");
	private static final int PART_SIZE = 24 * 1024;

	private static final long CACHE_LOAD_BATCH_DELAY_MS = 50L;

	private static MinecraftServer server;
	private static Path cacheRoot;
	private static ScheduledExecutorService worker;
	private static volatile Future<?> fullRenderTask;
	private static volatile Future<?> fixTask;
	private static volatile Future<?> webRenderTask;
	private static final Set<Future<?>> regionRenderTasks = ConcurrentHashMap.newKeySet();
	private static final AtomicBoolean cacheReady = new AtomicBoolean(false);
	private static final AtomicBoolean converting = new AtomicBoolean(false);
	private static final AtomicBoolean fullRenderRunning = new AtomicBoolean(false);
	private static final AtomicBoolean fixRunning = new AtomicBoolean(false);
	private static final AtomicBoolean webRenderRunning = new AtomicBoolean(false);
	private static final AtomicBoolean cancelRequested = new AtomicBoolean(false);
	private static final Map<String, Long> mcaMtimes = new ConcurrentHashMap<>();
	private static final Map<String, RegionRecord> regions = new ConcurrentHashMap<>();
	private static final Deque<PriorityConvertJob> priorityConvertQueue = new ArrayDeque<>();
	private static final Set<String> priorityConvertKeys = ConcurrentHashMap.newKeySet();
	/** MCA jobKey → earliest time another priority convert may be queued (failed convert backoff). */
	private static final Map<String, Long> priorityConvertCooldownUntilMs = new ConcurrentHashMap<>();
	private static final long PRIORITY_CONVERT_FAIL_COOLDOWN_MS = 30_000L;
	private static String lastClaimsJson = "[]";
	private static String lastClaimsHash = "";
	private static volatile boolean initialClaimsBroadcastPending;
	private static final Map<UUID, Long> lastClaimsSentToPlayerMs = new ConcurrentHashMap<>();
	private static final Map<UUID, PlayerSyncState> playerStates = new ConcurrentHashMap<>();
	private static final Map<String, UploadPartBuffer> incomingUploads = new ConcurrentHashMap<>();
	private static int uploadRequestTickCounter;
	private static final Deque<PendingCacheZip> cacheLoadQueue = new ArrayDeque<>();
	private static volatile boolean cacheLoadDiscoveryDone;
	private static volatile int cacheLoadTotal;
	private static volatile int cacheLoadScanned;
	private static volatile long cacheLoadStartedAt;
	private static final AtomicBoolean cacheLoadFinishing = new AtomicBoolean(false);
	private static final AtomicBoolean backgroundMaintenanceRunning = new AtomicBoolean(false);

	private MapSyncServer() {
	}

	public static void onServerStarted(MinecraftServer started) {
		server = started;
		cacheReady.set(false);
		cacheRoot = started.getWorldPath(LevelResource.ROOT).resolve("voxelmapsync_cache");
		try {
			Files.createDirectories(cacheRoot);
		} catch (IOException e) {
			VoxelMapSync.LOGGER.error("Failed to create cache dir {}", cacheRoot, e);
		}
		maybeInvalidateCacheFormat();
		worker = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "VoxelMapSync-Worker");
			t.setDaemon(true);
			return t;
		});
		VoxelMapSync.LOGGER.info(
				"VoxelMapSync starting — discovering cache at {} (load {} zip(s)/batch, background worker continues while server paused), format={}, interval={}s, maxRegions/cycle={}, maxBytes/s/player={}, settle={}s, fullOnly={}, joinRadius={}",
				cacheRoot.toAbsolutePath(), SyncConfig.maxCacheLoadPerTick,
				VoxelMapZipWriter.FORMAT_VERSION,
				SyncConfig.syncIntervalSeconds, SyncConfig.maxRegionsPerCycle, SyncConfig.maxBytesPerSecond,
				SyncConfig.convertSettleSeconds, SyncConfig.strictFullStatusOnly, SyncConfig.joinSyncRadiusRegions);
		for (ServerLevel level : started.getAllLevels()) {
			Path regionDir = resolveRegionDir(level);
			VoxelMapSync.LOGGER.info(
					"VoxelMapSync watching dim {} regionDir={}",
					dimensionStorageName(level.dimension()),
					regionDir != null && Files.isDirectory(regionDir) ? regionDir.toAbsolutePath() : "(missing)");
		}
		WebMapExporter.init();
		worker.execute(MapSyncServer::discoverCacheFiles);
		initialClaimsBroadcastPending = true;
	}

	private static void discoverCacheFiles() {
		cacheLoadStartedAt = System.currentTimeMillis();
		cacheLoadScanned = 0;
		cacheLoadTotal = 0;
		synchronized (cacheLoadQueue) {
			cacheLoadQueue.clear();
		}
		if (cacheRoot == null || !Files.isDirectory(cacheRoot)) {
			cacheLoadDiscoveryDone = true;
			scheduleCacheLoadComplete();
			return;
		}
		try (Stream<Path> dims = Files.list(cacheRoot)) {
			for (Path dimDir : dims.filter(Files::isDirectory).toList()) {
				String dim = dimDir.getFileName().toString();
				try (DirectoryStream<Path> zips = Files.newDirectoryStream(dimDir, "*.zip")) {
					for (Path zipPath : zips) {
						Matcher matcher = ZIP_NAME.matcher(zipPath.getFileName().toString());
						if (!matcher.matches()) {
							continue;
						}
						int regionX = Integer.parseInt(matcher.group(1));
						int regionZ = Integer.parseInt(matcher.group(2));
						synchronized (cacheLoadQueue) {
							cacheLoadQueue.addLast(new PendingCacheZip(dim, regionX, regionZ, zipPath));
						}
					}
				}
			}
		} catch (IOException e) {
			VoxelMapSync.LOGGER.warn("Failed to discover VoxelMapSync cache files", e);
		}
		synchronized (cacheLoadQueue) {
			cacheLoadTotal = cacheLoadQueue.size();
		}
		cacheLoadDiscoveryDone = true;
		if (cacheLoadTotal == 0) {
			scheduleCacheLoadComplete();
			return;
		}
		VoxelMapSync.LOGGER.info(
				"VoxelMapSync cache discovery done — {} zip(s) queued, indexing {} per batch on background worker",
				cacheLoadTotal, SyncConfig.maxCacheLoadPerTick);
		worker.execute(MapSyncServer::runCacheLoadLoop);
	}

	/** Indexes startup cache zips on the worker thread (not gated on server ticks / pause-when-empty). */
	private static void runCacheLoadLoop() {
		try {
			while (!cacheReady.get() && !Thread.currentThread().isInterrupted()) {
				synchronized (cacheLoadQueue) {
					if (cacheLoadQueue.isEmpty()) {
						scheduleCacheLoadComplete();
						return;
					}
				}
				processCacheLoadBatch();
				if (cacheReady.get()) {
					return;
				}
				synchronized (cacheLoadQueue) {
					if (cacheLoadQueue.isEmpty()) {
						return;
					}
				}
				Thread.sleep(CACHE_LOAD_BATCH_DELAY_MS);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static void processCacheLoadBatch() {
		int budget = SyncConfig.maxCacheLoadPerTick;
		int processed = 0;
		while (processed < budget) {
			PendingCacheZip entry;
			synchronized (cacheLoadQueue) {
				entry = cacheLoadQueue.pollFirst();
			}
			if (entry == null) {
				break;
			}
			indexOneCacheZip(entry);
			cacheLoadScanned++;
			processed++;
			if (cacheLoadScanned % 250 == 0) {
				VoxelMapSync.LOGGER.info(
						"VoxelMapSync cache load progress: {}/{} zip(s), {} region(s) indexed…",
						cacheLoadScanned, cacheLoadTotal, regions.size());
			}
		}
		boolean queueEmpty;
		synchronized (cacheLoadQueue) {
			queueEmpty = cacheLoadQueue.isEmpty();
		}
		if (queueEmpty) {
			scheduleCacheLoadComplete();
		}
	}

	private static boolean indexOneCacheZip(PendingCacheZip entry) {
		try {
			byte[] zip = Files.readAllBytes(entry.path());
			if (zip.length == 0) {
				return false;
			}
			RegionQuality.Assessment quality = RegionQuality.assessZip(zip);
			if (!quality.ok()) {
				VoxelMapSync.LOGGER.info(
						"VoxelMapSync skip stale cache {},{} reason={} ({})",
						entry.regionX(), entry.regionZ(), quality.reason(), quality.detail());
				try {
					Files.deleteIfExists(entry.path());
				} catch (IOException ignored) {
				}
				return false;
			}
			float sentinel = VoxelMapZipReader.sentinelFraction(zip);
			int explored = VoxelMapZipReader.exploredChunkCount(zip);
			String hash = sha256(zip);
			String key = entry.dim() + "|" + entry.regionX() + "," + entry.regionZ();
			regions.put(key, new RegionRecord(entry.dim(), entry.regionX(), entry.regionZ(), hash, zip, explored, sentinel));
			return true;
		} catch (IOException e) {
			VoxelMapSync.LOGGER.warn("VoxelMapSync failed to read cache zip {}", entry.path(), e);
			return false;
		}
	}

	private static void scheduleCacheLoadComplete() {
		if (cacheReady.get() || worker == null) {
			return;
		}
		if (!cacheLoadFinishing.compareAndSet(false, true)) {
			return;
		}
		worker.execute(() -> {
			try {
				int pruned = pruneOrphanCache();
				MinecraftServer srv = server;
				if (srv == null) {
					return;
				}
				srv.execute(() -> completeCacheLoad(pruned));
			} catch (Exception e) {
				cacheLoadFinishing.set(false);
				VoxelMapSync.LOGGER.error("VoxelMapSync cache load failed during finalize", e);
				MinecraftServer srv = server;
				if (srv != null) {
					srv.execute(() -> {
						cacheReady.set(true);
						VoxelMapSync.LOGGER.warn(
								"VoxelMapSync cache load failed — sync will use partial index until restart");
					});
				}
			}
		});
	}

	private static void completeCacheLoad(int pruned) {
		if (!cacheReady.compareAndSet(false, true)) {
			return;
		}
		long elapsed = cacheLoadStartedAt > 0 ? System.currentTimeMillis() - cacheLoadStartedAt : 0;
		VoxelMapSync.LOGGER.info(
				"VoxelMapSync cache ready — {} region(s) indexed{} ({}/{} zip(s) scanned in {}ms)",
				regions.size(),
				pruned > 0 ? (", pruned " + pruned + " orphan(s)") : "",
				cacheLoadScanned, cacheLoadTotal, elapsed);
		finishCacheLoad(pruned);
		startBackgroundMaintenance();
	}

	/** Schedule one maintenance pass at a time without permanently occupying the only worker. */
	private static void startBackgroundMaintenance() {
		if (worker == null || server == null) {
			return;
		}
		if (!backgroundMaintenanceRunning.compareAndSet(false, true)) {
			return;
		}
		final long intervalMs = Math.max(5_000L, (long) SyncConfig.syncIntervalSeconds * 1000L);
		worker.scheduleWithFixedDelay(() -> {
			try {
				// /mapsync stop also cancels a maintenance pass in progress. Consume the
				// cancellation on the next scheduled pass so normal automatic sync resumes.
				if (cancelRequested.compareAndSet(true, false)) {
					return;
				}
				if (cacheReady.get() && SyncConfig.autoConvert && !isAdminBackgroundTaskRunning()) {
					runConvertCycle(false);
					MinecraftServer srv = server;
					if (srv != null) {
						srv.execute(MapSyncServer::broadcastClaims);
					}
				}
			} catch (Throwable t) {
				VoxelMapSync.LOGGER.error("VoxelMapSync background maintenance failed", t);
			}
		}, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
	}

	private static boolean isAdminBackgroundTaskRunning() {
		return fixRunning.get() || fullRenderRunning.get() || webRenderRunning.get();
	}

	/**
	 * Yield the worker thread between admin task batches so disk/CPU load stays low and the
	 * server thread is not flooded with progress updates.
	 */
	private static void yieldBackgroundTaskBatch(int convertsThisBatch) throws InterruptedException {
		// There is no gameplay to protect while the server is empty. The old unconditional
		// 250 ms sleep after every two MCAs added roughly 26 minutes to a 12,500-MCA
		// rerender even though all work was already isolated on the background worker.
		if (server == null || server.getPlayerList().getPlayerCount() == 0) {
			Thread.yield();
			return;
		}
		int delayMs = SyncConfig.backgroundTaskBatchDelayMs;
		if (convertsThisBatch > 0) {
			// Spread conversions evenly instead of doing a burst of heavy MCA reads and then
			// sleeping. Same configured throughput, much smoother ticks for online players.
			delayMs = Math.max(1, delayMs / SyncConfig.backgroundTaskMaxConvertsPerBatch);
		}
		if (delayMs <= 0) {
			return;
		}
		Thread.sleep(delayMs);
	}

	private static boolean shouldYieldBackgroundBatch(int regionsThisBatch, int convertsThisBatch) {
		return regionsThisBatch >= SyncConfig.backgroundTaskRegionsPerBatch
				|| convertsThisBatch >= (hasOnlinePlayers()
						? 1
						: SyncConfig.backgroundTaskMaxConvertsPerBatch);
	}

	private static boolean hasOnlinePlayers() {
		return server != null && server.getPlayerList().getPlayerCount() > 0;
	}

	private static void convertOneRegionLocked(
			ServerLevel level,
			int regionX,
			int regionZ,
			List<RegionRecord> newlyConverted,
			boolean ignoreSettle,
			boolean forceOverwrite
	) throws IOException, InterruptedException {
		acquireConvertLock();
		try {
			convertOneRegion(level, regionX, regionZ, newlyConverted, ignoreSettle, forceOverwrite);
		} finally {
			converting.set(false);
		}
	}

	private static boolean convertOneMcaLocked(
			ServerLevel level,
			int mcaX,
			int mcaZ,
			List<RegionRecord> newlyConverted,
			boolean ignoreSettle,
			boolean forceOverwrite
	) throws IOException, InterruptedException {
		acquireConvertLock();
		try {
			return convertOneMca(level, mcaX, mcaZ, newlyConverted, ignoreSettle, forceOverwrite);
		} finally {
			converting.set(false);
		}
	}

	private static void finishCacheLoad(int pruned) {
		if (!SyncConfig.syncOnJoin || server == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			PlayerSyncState state = playerStates.get(player.getUUID());
			if (state == null) {
				continue;
			}
			int queued = enqueueMissingRegions(player, state);
			if (queued > 0) {
				VoxelMapSync.LOGGER.info(
						"VoxelMapSync post-cache join fill for {} queued={} pending={}",
						player.getGameProfile().name(), queued, state.pending.size());
			}
		}
	}

	private static String cacheStatusLabel() {
		if (cacheReady.get()) {
			return "ready, regions=" + regions.size();
		}
		if (!cacheLoadDiscoveryDone) {
			return "discovering…";
		}
		if (cacheLoadTotal <= 0) {
			return "loading…";
		}
		return "loading " + cacheLoadScanned + "/" + cacheLoadTotal + " (" + regions.size() + " indexed)";
	}

	public static boolean isCacheReady() {
		return cacheReady.get();
	}

	public static void onServerStopping(MinecraftServer stopping) {
		if (MapSyncProgress.isActive()) {
			MapSyncProgress.finish(stopping, true);
		}
		if (worker != null) {
			worker.shutdownNow();
			worker = null;
		}
		fullRenderTask = null;
		fixTask = null;
		webRenderTask = null;
		regionRenderTasks.clear();
		cacheReady.set(false);
		cacheLoadDiscoveryDone = false;
		cacheLoadTotal = 0;
		cacheLoadScanned = 0;
		cacheLoadFinishing.set(false);
		backgroundMaintenanceRunning.set(false);
		synchronized (cacheLoadQueue) {
			cacheLoadQueue.clear();
		}
		server = null;
		fullRenderRunning.set(false);
		fixRunning.set(false);
		webRenderRunning.set(false);
		cancelRequested.set(false);
		synchronized (priorityConvertQueue) {
			priorityConvertQueue.clear();
			priorityConvertKeys.clear();
		}
		priorityConvertCooldownUntilMs.clear();
	}

	public static void onServerTick(MinecraftServer ticking) {
		if (initialClaimsBroadcastPending) {
			initialClaimsBroadcastPending = false;
			broadcastClaims();
		}
		tickSendQueues();
		tickClientUploadRequests(ticking);
	}

	public static void onPlayerDisconnect(ServerPlayer player) {
		MapSyncProgress.onPlayerDisconnect(player);
		playerStates.remove(player.getUUID());
		lastClaimsSentToPlayerMs.remove(player.getUUID());
	}

	public static void onPlayerJoin(ServerPlayer player) {
		MapSyncProgress.onPlayerJoin(player);
		if (SyncConfig.claimsEnabled) {
			sendClaimsToPlayer(player, true);
		}
		if (SyncConfig.syncOnJoin) {
			VoxelMapSync.LOGGER.info(
					"VoxelMapSync waiting for client hello from {} ({} region(s) in cache)",
					player.getGameProfile().name(), regions.size());
		}
	}

	public static void onClientHello(ServerPlayer player, ClientHelloPayload payload) {
		PlayerSyncState state = playerStates.computeIfAbsent(player.getUUID(), ignored -> new PlayerSyncState());
		Map<String, String> clientHashes = payload.knownHashes();
		state.clientReported.putAll(clientHashes);
		state.known.putAll(clientHashes);
		requestUploadsFromClient(player, clientHashes);
		int queued = cacheReady.get() ? enqueueMissingRegions(player, state) : 0;
		VoxelMapSync.LOGGER.info(
				"VoxelMapSync hello from {} worldKey={} known={} queued={} pending={}{}",
				player.getGameProfile().name(), payload.worldKey(), state.known.size(), queued, state.pending.size(),
				cacheReady.get() ? "" : " (cache still loading — join fill runs when ready)");
		if (SyncConfig.claimsEnabled) {
			sendClaimsToPlayer(player, true);
		}
	}

	public static void onRegionRequest(ServerPlayer player, RegionRequestPayload payload) {
		if (SyncConfig.claimsEnabled) {
			sendClaimsToPlayer(player, false);
		}
		if (!SyncConfig.viewportSyncEnabled || server == null) {
			return;
		}
		PlayerSyncState state = playerStates.computeIfAbsent(player.getUUID(), ignored -> new PlayerSyncState());
		if (payload.clientSeq() > 0 && payload.clientSeq() < state.lastRequestSeq) {
			return;
		}
		state.lastRequestSeq = Math.max(state.lastRequestSeq, payload.clientSeq());

		String dim = normalizeDimension(payload.dimension());
		int[] xs = payload.regionXs();
		int[] zs = payload.regionZs();
		int count = Math.min(xs.length, zs.length);
		count = Math.min(count, SyncConfig.maxRegionsPerRequest);

		int queuedPriority = 0;
		int convertQueued = 0;
		int skippedNoMca = 0;
		int skippedOrphan = 0;
		int skippedBorder = 0;
		int skippedConvertCooldown = 0;
		Set<String> mcaJobs = new HashSet<>();

		for (int i = 0; i < count; i++) {
			int regionX = xs[i];
			int regionZ = zs[i];
			ServerLevel level = findLevel(dim);
			if (level != null && !WorldBorderRegions.overlaps(level.getWorldBorder(), regionX, regionZ)) {
				skippedBorder++;
				continue;
			}
			String key = dim + "|" + regionX + "," + regionZ;
			RegionRecord existing = regions.get(key);
			if (existing != null) {
				if (!SyncConfig.syncOrphanCache && !hasBackingMca(dim, regionX, regionZ)) {
					skippedOrphan++;
					continue;
				}
				// Viewport requests mean the client still shows blank/gappy tiles — always
				// force-resend even if we already marked known on a prior send (apply lag).
				if (enqueueRegions(state, List.of(existing), true, true) > 0) {
					queuedPriority++;
				}
				continue;
			}
			if (!SyncConfig.onDemandConvert) {
				continue;
			}
			int mcaX = Math.floorDiv(regionX, 2);
			int mcaZ = Math.floorDiv(regionZ, 2);
			Path mca = resolveMcaPath(dim, mcaX, mcaZ);
			if (mca == null || !Files.isRegularFile(mca)) {
				skippedNoMca++;
				VoxelMapSync.LOGGER.debug(
						"VoxelMapSync skip request r.{}.{} — no MCA (not pregenerated)", mcaX, mcaZ);
				continue;
			}
			try {
				if (Files.size(mca) <= 0) {
					skippedNoMca++;
					continue;
				}
			} catch (IOException e) {
				continue;
			}
			String jobKey = dim + "|r." + mcaX + "." + mcaZ;
			if (mcaJobs.add(jobKey)) {
				if (isPriorityConvertCoolingDown(jobKey)) {
					skippedConvertCooldown++;
					continue;
				}
				queuePriorityConvert(dim, mcaX, mcaZ, player.getUUID());
				convertQueued++;
			}
		}

		VoxelMapSync.LOGGER.info(
				"VoxelMapSync viewport request from {} dim={} center={},{} count={} queuedPriority={} convertQueued={} skippedNoMca={} skippedOrphan={} skippedBorder={} skippedConvertCooldown={}",
				player.getGameProfile().name(), dim, payload.centerRegionX(), payload.centerRegionZ(),
				count, queuedPriority, convertQueued, skippedNoMca, skippedOrphan, skippedBorder,
				skippedConvertCooldown);

		if (convertQueued > 0 && worker != null) {
			worker.execute(() -> runConvertCycle(false));
		}
	}

	public static void onRegionUpload(ServerPlayer player, RegionUploadPayload payload) {
		if (!SyncConfig.clientUploadEnabled || server == null || cacheRoot == null) {
			return;
		}
		String dim = normalizeDimension(payload.dimension());
		String key = dim + "|" + payload.regionX() + "," + payload.regionZ();
		UploadPartBuffer buffer = incomingUploads.computeIfAbsent(
				player.getUUID() + "|" + key,
				ignored -> new UploadPartBuffer(payload.partCount(), payload.contentHash()));
		if (!buffer.hash.equals(payload.contentHash()) || buffer.parts.length != payload.partCount()) {
			incomingUploads.put(player.getUUID() + "|" + key,
					buffer = new UploadPartBuffer(payload.partCount(), payload.contentHash()));
		}
		int index = payload.partIndex();
		if (index < 0 || index >= buffer.parts.length) {
			return;
		}
		if (buffer.parts[index] == null) {
			buffer.received++;
		}
		buffer.parts[index] = payload.data();
		if (buffer.received < buffer.parts.length) {
			return;
		}
		incomingUploads.remove(player.getUUID() + "|" + key);
		int total = 0;
		for (byte[] part : buffer.parts) {
			if (part == null) {
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
		if (!buffer.hash.equals(sha256(zip))) {
			VoxelMapSync.LOGGER.warn("VoxelMapSync upload hash mismatch from {} for {}", player.getGameProfile().name(), key);
			return;
		}
		applyClientUpload(player, dim, payload.regionX(), payload.regionZ(), zip, buffer.hash);
	}

	private static void applyClientUpload(
			ServerPlayer player,
			String dim,
			int regionX,
			int regionZ,
			byte[] zip,
			String hash
	) {
		if (!VoxelMapZipReader.isValidRegionZip(zip)) {
			VoxelMapSync.LOGGER.debug("VoxelMapSync reject invalid upload {} from {}", regionKey(dim, regionX, regionZ), player.getGameProfile().name());
			return;
		}
		RegionQuality.Assessment quality = RegionQuality.assessZip(zip);
		if (!quality.ok()) {
			VoxelMapSync.LOGGER.debug(
					"VoxelMapSync reject low-quality client upload {},{} reason={} ({}) from {}",
					regionX, regionZ, quality.reason(), quality.detail(), player.getGameProfile().name());
			return;
		}
		float sentinel = VoxelMapZipReader.sentinelFraction(zip);
		int exploredChunks = VoxelMapZipReader.exploredChunkCount(zip);
		String key = regionKey(dim, regionX, regionZ);
		RegionRecord previous = regions.get(key);
		if (previous != null && hash.equals(previous.hash)) {
			return;
		}
		if (previous != null && !isBetterClientUpload(exploredChunks, sentinel, previous)) {
			VoxelMapSync.LOGGER.debug(
					"VoxelMapSync keep existing cache {},{} (client upload not better, sentinel={} vs {})",
					regionX, regionZ, sentinel, previous.sentinelFraction);
			return;
		}
		try {
			Path out = cacheRoot.resolve(dim).resolve(regionX + "," + regionZ + ".zip");
			Files.createDirectories(out.getParent());
			VoxelMapZipWriter.writeToFile(out, zip);
		} catch (IOException e) {
			VoxelMapSync.LOGGER.warn("VoxelMapSync failed to write client upload {}", key, e);
			return;
		}
		RegionRecord record = new RegionRecord(dim, regionX, regionZ, hash, zip, exploredChunks, sentinel);
		regions.put(key, record);
		WebMapExporter.exportTileFromZip(dim, regionX, regionZ, zip);
		PlayerSyncState state = playerStates.computeIfAbsent(player.getUUID(), ignored -> new PlayerSyncState());
		state.known.put(key, hash);
		state.clientReported.put(key, hash);
		int broadcast = 0;
		for (ServerPlayer online : server.getPlayerList().getPlayers()) {
			if (online.getUUID().equals(player.getUUID())) {
				continue;
			}
			PlayerSyncState other = playerStates.computeIfAbsent(online.getUUID(), ignored -> new PlayerSyncState());
			if (enqueueRegions(other, List.of(record), true, false) > 0) {
				broadcast++;
			}
		}
		VoxelMapSync.LOGGER.info(
				"VoxelMapSync client upload accepted {},{} from {} ({} bytes, sentinel={}, exploredChunks={}, broadcast={})",
				regionX, regionZ, player.getGameProfile().name(), zip.length, sentinel, exploredChunks, broadcast);
	}

	private static boolean isBetterClientUpload(int exploredChunks, float sentinel, RegionRecord previous) {
		if (previous.fullChunks <= 0 && previous.sentinelFraction >= 0.99f) {
			return true;
		}
		if (exploredChunks > previous.fullChunks) {
			return true;
		}
		if (sentinel + 0.01f < previous.sentinelFraction) {
			return true;
		}
		// Same or slightly better coverage — accept live client-rendered terrain over MCA.
		return exploredChunks >= previous.fullChunks && sentinel <= previous.sentinelFraction + 0.01f;
	}

	private static void requestUploadsFromClient(ServerPlayer player, Map<String, String> clientHashes) {
		if (!SyncConfig.clientUploadEnabled || clientHashes.isEmpty()) {
			return;
		}
		String playerDim = dimensionStorageName(player.level().dimension());
		List<Integer> xs = new ArrayList<>();
		List<Integer> zs = new ArrayList<>();
		List<String> serverHashes = new ArrayList<>();
		for (Map.Entry<String, String> entry : clientHashes.entrySet()) {
			String key = entry.getKey();
			String clientHash = entry.getValue();
			if (clientHash == null || clientHash.isBlank()) {
				continue;
			}
			int sep = key.indexOf('|');
			if (sep <= 0 || sep >= key.length() - 1) {
				continue;
			}
			String dim = normalizeDimension(key.substring(0, sep));
			if (!dim.equals(playerDim)) {
				continue;
			}
			String coords = key.substring(sep + 1);
			int comma = coords.indexOf(',');
			if (comma <= 0) {
				continue;
			}
			int regionX;
			int regionZ;
			try {
				regionX = Integer.parseInt(coords.substring(0, comma));
				regionZ = Integer.parseInt(coords.substring(comma + 1));
			} catch (NumberFormatException e) {
				continue;
			}
			RegionRecord serverRegion = regions.get(key);
			String serverHash = serverRegion != null ? serverRegion.hash : "";
			if (serverRegion != null && serverRegion.hash.equals(clientHash)) {
				continue;
			}
			xs.add(regionX);
			zs.add(regionZ);
			serverHashes.add(serverHash);
			if (xs.size() >= RegionUploadRequestPayload.MAX_REGIONS) {
				break;
			}
		}
		sendUploadRequest(player, playerDim, xs, zs, serverHashes);
	}

	private static void tickClientUploadRequests(MinecraftServer ticking) {
		if (!SyncConfig.clientUploadEnabled || server == null) {
			return;
		}
		uploadRequestTickCounter++;
		int intervalTicks = SyncConfig.clientUploadRequestIntervalSeconds * 20;
		if (uploadRequestTickCounter < intervalTicks) {
			return;
		}
		uploadRequestTickCounter = 0;
		for (ServerPlayer player : ticking.getPlayerList().getPlayers()) {
			requestNearbyUploads(player);
		}
	}

	private static void requestNearbyUploads(ServerPlayer player) {
		if (!SyncConfig.clientUploadEnabled) {
			return;
		}
		String dim = dimensionStorageName(player.level().dimension());
		int centerX = (int) Math.floor(player.getX() / 256.0);
		int centerZ = (int) Math.floor(player.getZ() / 256.0);
		int radius = SyncConfig.clientUploadRequestRadiusRegions;
		PlayerSyncState state = playerStates.get(player.getUUID());
		List<Integer> xs = new ArrayList<>();
		List<Integer> zs = new ArrayList<>();
		List<String> serverHashes = new ArrayList<>();
		for (int dz = -radius; dz <= radius; dz++) {
			for (int dx = -radius; dx <= radius; dx++) {
				int regionX = centerX + dx;
				int regionZ = centerZ + dz;
				String key = dim + "|" + regionX + "," + regionZ;
				RegionRecord serverRegion = regions.get(key);
				String serverHash = serverRegion != null ? serverRegion.hash : "";
				String clientHash = state != null ? state.clientReported.get(key) : null;
				if (clientHash != null && clientHash.equals(serverHash)) {
					continue;
				}
				xs.add(regionX);
				zs.add(regionZ);
				serverHashes.add(serverHash);
				if (xs.size() >= RegionUploadRequestPayload.MAX_REGIONS) {
					sendUploadRequest(player, dim, xs, zs, serverHashes);
					xs = new ArrayList<>();
					zs = new ArrayList<>();
					serverHashes = new ArrayList<>();
				}
			}
		}
		if (!xs.isEmpty()) {
			sendUploadRequest(player, dim, xs, zs, serverHashes);
		}
	}

	private static void sendUploadRequest(
			ServerPlayer player,
			String dim,
			List<Integer> xs,
			List<Integer> zs,
			List<String> serverHashes
	) {
		if (xs.isEmpty()) {
			return;
		}
		RegionUploadRequestPayload payload = new RegionUploadRequestPayload(
				dim,
				xs.stream().mapToInt(Integer::intValue).toArray(),
				zs.stream().mapToInt(Integer::intValue).toArray(),
				serverHashes.toArray(String[]::new));
		ServerPlayNetworking.send(player, payload);
	}

	private static String regionKey(String dim, int regionX, int regionZ) {
		return dim + "|" + regionX + "," + regionZ;
	}

	private static void queuePriorityConvert(String dim, int mcaX, int mcaZ, UUID requester) {
		String jobKey = dim + "|r." + mcaX + "." + mcaZ;
		if (isPriorityConvertCoolingDown(jobKey)) {
			return;
		}
		synchronized (priorityConvertQueue) {
			if (!priorityConvertKeys.add(jobKey)) {
				return;
			}
			priorityConvertQueue.addLast(new PriorityConvertJob(dim, mcaX, mcaZ, requester, jobKey));
		}
	}

	private static boolean isPriorityConvertCoolingDown(String jobKey) {
		Long until = priorityConvertCooldownUntilMs.get(jobKey);
		if (until == null) {
			return false;
		}
		if (System.currentTimeMillis() >= until) {
			priorityConvertCooldownUntilMs.remove(jobKey, until);
			return false;
		}
		return true;
	}

	private static void markPriorityConvertFailed(String jobKey, String reason) {
		priorityConvertCooldownUntilMs.put(jobKey, System.currentTimeMillis() + PRIORITY_CONVERT_FAIL_COOLDOWN_MS);
		VoxelMapSync.LOGGER.info(
				"VoxelMapSync on-demand convert rejected {} reason={} (cooldown {}s)",
				jobKey, reason, PRIORITY_CONVERT_FAIL_COOLDOWN_MS / 1000L);
	}

	private static int loadExistingCache() {
		if (cacheRoot == null || !Files.isDirectory(cacheRoot)) {
			return 0;
		}
		int loaded = 0;
		try (Stream<Path> dims = Files.list(cacheRoot)) {
			for (Path dimDir : dims.filter(Files::isDirectory).toList()) {
				String dim = dimDir.getFileName().toString();
				try (DirectoryStream<Path> zips = Files.newDirectoryStream(dimDir, "*.zip")) {
					for (Path zipPath : zips) {
						Matcher matcher = ZIP_NAME.matcher(zipPath.getFileName().toString());
						if (!matcher.matches()) {
							continue;
						}
						int regionX = Integer.parseInt(matcher.group(1));
						int regionZ = Integer.parseInt(matcher.group(2));
						if (indexOneCacheZip(new PendingCacheZip(dim, regionX, regionZ, zipPath))) {
							loaded++;
						}
					}
				}
			}
		} catch (IOException e) {
			VoxelMapSync.LOGGER.warn("Failed to load existing VoxelMapSync cache", e);
		}
		return loaded;
	}

	/** Drop (and optionally delete) cache zips with no backing MCA. */
	private static int pruneOrphanCache() {
		if (server == null || SyncConfig.syncOrphanCache && !SyncConfig.pruneOrphanCacheOnStart) {
			return 0;
		}
		int pruned = 0;
		List<String> toRemove = new ArrayList<>();
		for (Map.Entry<String, RegionRecord> entry : regions.entrySet()) {
			RegionRecord record = entry.getValue();
			if (hasBackingMca(record.dimension, record.regionX, record.regionZ)) {
				continue;
			}
			toRemove.add(entry.getKey());
			if (SyncConfig.pruneOrphanCacheOnStart && cacheRoot != null) {
				Path zipPath = cacheRoot.resolve(record.dimension).resolve(record.regionX + "," + record.regionZ + ".zip");
				try {
					Files.deleteIfExists(zipPath);
					VoxelMapSync.LOGGER.debug("VoxelMapSync prune orphan {}", zipPath.getFileName());
				} catch (IOException ignored) {
				}
			}
			pruned++;
		}
		if (!SyncConfig.syncOrphanCache) {
			for (String key : toRemove) {
				regions.remove(key);
			}
		}
		if (pruned > 0) {
			VoxelMapSync.LOGGER.info("VoxelMapSync orphan prune: {} cache zip(s) without MCA", pruned);
		}
		return pruned;
	}

	/**
	 * Cache summary for {@code /mapsync} status.
	 */
	public static String statusSummary() {
		int pendingPlayers = 0;
		int pendingRegions = 0;
		for (PlayerSyncState state : playerStates.values()) {
			pendingPlayers++;
			synchronized (state) {
				pendingRegions += state.pending.size();
			}
		}
		return "VoxelMapSync — cache="
				+ cacheStatusLabel()
				+ ", format=" + VoxelMapZipWriter.FORMAT_VERSION
				+ ", minChunks/region=" + SyncConfig.minChunksPerRegion
				+ ", fullRender=" + (fullRenderRunning.get() ? "running" : "idle")
				+ ", fix=" + (fixRunning.get() ? "running" : "idle")
				+ ", webRender=" + (webRenderRunning.get() ? "running" : "idle")
				+ ", convertBusy=" + converting.get()
				+ ", playersTracked=" + pendingPlayers
				+ ", pendingSends=" + pendingRegions
				+ (cacheRoot != null ? (", cacheDir=" + cacheRoot.toAbsolutePath()) : "");
	}

	/**
	 * Scan all MCAs and existing cache zips; report quality reject counts (debug).
	 */
	public static String auditSummary() {
		if (server == null) {
			return "VoxelMapSync not ready";
		}
		int mcaTotal = 0;
		int mcaFull = 0;
		int mcaRejected = 0;
		int cacheOk = 0;
		int cacheBad = 0;
		java.util.Map<RegionQuality.RejectReason, Integer> rejectCounts = new java.util.EnumMap<>(RegionQuality.RejectReason.class);

		for (ServerLevel level : server.getAllLevels()) {
			Path regionDir = resolveRegionDir(level);
			if (regionDir == null || !Files.isDirectory(regionDir)) {
				continue;
			}
			int minY = level.getMinY();
			int height = level.getHeight();
			try (DirectoryStream<Path> mcas = Files.newDirectoryStream(regionDir, "r.*.mca")) {
				for (Path mca : mcas) {
					mcaTotal++;
					Matcher m = MCA_NAME.matcher(mca.getFileName().toString());
					if (!m.matches()) {
						continue;
					}
					int mcaX = Integer.parseInt(m.group(1));
					int mcaZ = Integer.parseInt(m.group(2));
					try (McaReader reader = McaReader.open(mca, minY, height, SyncConfig.strictFullStatusOnly)) {
						McaReader.ReadResult read = reader.readSurfacesDetailed(mcaX, mcaZ);
						if (read.fullChunks() >= SyncConfig.minFullChunksToConvert) {
							mcaFull++;
						}
						RegionQuality.Assessment q = RegionQuality.assessSurfaces(read.surfaces());
						if (!q.ok()) {
							mcaRejected++;
							rejectCounts.merge(q.reason(), 1, Integer::sum);
						}
					} catch (IOException e) {
						mcaRejected++;
						rejectCounts.merge(RegionQuality.RejectReason.INVALID_ZIP, 1, Integer::sum);
					}
				}
			} catch (IOException e) {
				return "Audit failed reading region dir: " + e.getMessage();
			}
		}

		for (RegionRecord record : regions.values()) {
			RegionQuality.Assessment q = RegionQuality.assessZip(record.zip);
			if (q.ok()) {
				cacheOk++;
			} else {
				cacheBad++;
				rejectCounts.merge(q.reason(), 1, Integer::sum);
			}
		}

		StringBuilder sb = new StringBuilder();
		sb.append("VoxelMapSync audit — MCAs=").append(mcaTotal)
				.append(" full=").append(mcaFull)
				.append(" rejected=").append(mcaRejected)
				.append(" | cache ok=").append(cacheOk)
				.append(" bad=").append(cacheBad);
		for (var entry : rejectCounts.entrySet()) {
			if (entry.getKey() != RegionQuality.RejectReason.NONE) {
				sb.append(" | ").append(entry.getKey()).append("=").append(entry.getValue());
			}
		}
		return sb.toString();
	}

	public static boolean isFullRenderRunning() {
		return fullRenderRunning.get();
	}

	public static boolean isFixRunning() {
		return fixRunning.get();
	}

	public static boolean isWebRenderRunning() {
		return webRenderRunning.get();
	}

	/**
	 * Stops all active full rendering, rerendering, and web map rendering processes,
	 * clears priority queues, and cancels pending player send buffers.
	 */
	public static void stopAll(java.util.function.Consumer<String> feedback) {
		cancelRequested.set(true);
		int cancelledTasks = 0;
		cancelledTasks += cancelTask(fullRenderTask);
		cancelledTasks += cancelTask(fixTask);
		cancelledTasks += cancelTask(webRenderTask);
		for (Future<?> task : regionRenderTasks) {
			cancelledTasks += cancelTask(task);
		}
		regionRenderTasks.removeIf(task -> task.isDone() || task.isCancelled());
		if (server != null && MapSyncProgress.isActive()) {
			MapSyncProgress.finish(server, true);
		}
		boolean wasRunning = fullRenderRunning.get() || fixRunning.get() || webRenderRunning.get();
		fullRenderRunning.set(false);
		fixRunning.set(false);
		webRenderRunning.set(false);
		synchronized (priorityConvertQueue) {
			priorityConvertQueue.clear();
			priorityConvertKeys.clear();
		}
		priorityConvertCooldownUntilMs.clear();
		int clearedPlayers = 0;
		for (PlayerSyncState state : playerStates.values()) {
			synchronized (state) {
				state.pending.clear();
				state.pendingKeys.clear();
				state.sendRegionKey = null;
				state.sendRegionHash = null;
				state.sendPartIndex = 0;
			}
			clearedPlayers++;
		}
		if (wasRunning) {
			feedback.accept("Stopped VoxelMapSync render / fix / webmap work; cancelled "
					+ cancelledTasks + " worker task(s) and cleared all player/convert queues.");
		} else {
			feedback.accept("Cancelled " + cancelledTasks + " queued worker task(s), cleared priority converts, "
					+ "and reset send queues for " + clearedPlayers + " player(s).");
		}
	}

	private static int cancelTask(Future<?> task) {
		return task != null && !task.isDone() && task.cancel(true) ? 1 : 0;
	}

	/**
	 * Admin {@code /mapsync render}: scan MCA files and convert finished regions into cache
	 * (skips tiles already up-to-date unless quality improves).
	 */
	public static boolean startFullRender(java.util.function.Consumer<String> feedback) {
		return startFullRender(feedback, false);
	}

	/**
	 * Admin {@code /mapsync rerender}: re-convert every MCA that passes quality gates,
	 * overwrite existing cache zips, and force-resend to online players (overrides local
	 * discovery) under {@link SyncConfig#maxBytesPerSecond}.
	 */
	public static boolean startFullRerender(java.util.function.Consumer<String> feedback) {
		return startFullRender(feedback, true);
	}

	/**
	 * Admin {@code /mapsync fix}: within the world border, convert missing or low-quality cache
	 * tiles from MCA and queue them to online players (does not touch regions outside the border).
	 */
	public static boolean startFix(java.util.function.Consumer<String> feedback) {
		if (server == null || worker == null || cacheRoot == null) {
			feedback.accept("VoxelMapSync is not ready (server not started).");
			return false;
		}
		if (webRenderRunning.get()) {
			feedback.accept("A web map render/rerender is currently running. Please wait or use /mapsync stop.");
			return false;
		}
		if (fullRenderRunning.get()) {
			feedback.accept("A /mapsync render or rerender is currently running. Please wait or use /mapsync stop.");
			return false;
		}
		if (!fixRunning.compareAndSet(false, true)) {
			feedback.accept("A /mapsync fix is already running.");
			return false;
		}
		MinecraftServer srv = server;
		fixTask = worker.submit(() -> {
			cancelRequested.set(false);
			try {
				runFix(msg -> srv.execute(() -> feedback.accept(msg)));
			} catch (Exception e) {
				if (!cancelRequested.get() && !Thread.currentThread().isInterrupted()) {
					VoxelMapSync.LOGGER.error("Border fix failed", e);
					srv.execute(() -> feedback.accept("Fix failed: " + e.getMessage()));
				}
			} finally {
				fixRunning.set(false);
			}
		});
		return true;
	}

	/**
	 * Admin {@code /mapsync render region}: convert the command sender's current VoxelMap
	 * region from MCA into cache and web map tiles when quality improves or the tile is new.
	 */
	public static boolean startPlayerRegionRender(
			ServerPlayer player,
			java.util.function.Consumer<String> feedback
	) {
		return startPlayerRegionRender(player, false, feedback);
	}

	/**
	 * Admin {@code /mapsync rerender region}: force re-convert the sender's current region,
	 * overwrite cache + web map PNG, and force-resend to online players.
	 */
	public static boolean startPlayerRegionRerender(
			ServerPlayer player,
			java.util.function.Consumer<String> feedback
	) {
		return startPlayerRegionRender(player, true, feedback);
	}

	private static boolean startPlayerRegionRender(
			ServerPlayer player,
			boolean force,
			java.util.function.Consumer<String> feedback
	) {
		if (server == null || worker == null || cacheRoot == null) {
			feedback.accept("VoxelMapSync is not ready (server not started).");
			return false;
		}
		ServerLevel level = (ServerLevel) player.level();
		int regionX = (int) Math.floor(player.getX() / 256.0);
		int regionZ = (int) Math.floor(player.getZ() / 256.0);
		MinecraftServer srv = server;
		String label = force ? "rerender" : "render";
		Future<?> task = worker.submit(() -> {
			cancelRequested.set(false);
			try {
				runPlayerRegionRender(player, level, regionX, regionZ, force,
						msg -> srv.execute(() -> feedback.accept(msg)));
			} catch (Exception e) {
				if (!cancelRequested.get() && !Thread.currentThread().isInterrupted()) {
					VoxelMapSync.LOGGER.error("Region {} failed for {},{}", label, regionX, regionZ, e);
					srv.execute(() -> feedback.accept("Region " + label + " failed: " + e.getMessage()));
				}
			}
		});
		regionRenderTasks.removeIf(done -> done.isDone() || done.isCancelled());
		regionRenderTasks.add(task);
		return true;
	}

	private static boolean startFullRender(java.util.function.Consumer<String> feedback, boolean force) {
		if (server == null || worker == null || cacheRoot == null) {
			feedback.accept("VoxelMapSync is not ready (server not started).");
			return false;
		}
		if (webRenderRunning.get()) {
			feedback.accept("A web map render/rerender is currently running. Please wait or use /mapsync stop.");
			return false;
		}
		if (fixRunning.get()) {
			feedback.accept("A /mapsync fix is currently running. Please wait or use /mapsync stop.");
			return false;
		}
		if (!fullRenderRunning.compareAndSet(false, true)) {
			feedback.accept("A /mapsync render or rerender is already running.");
			return false;
		}
		MinecraftServer srv = server;
		String label = force ? "rerender" : "render";
		fullRenderTask = worker.submit(() -> {
			cancelRequested.set(false);
			try {
				runFullRender(msg -> srv.execute(() -> feedback.accept(msg)), force);
			} catch (Exception e) {
				if (!cancelRequested.get() && !Thread.currentThread().isInterrupted()) {
					VoxelMapSync.LOGGER.error("Full {} failed", label, e);
					srv.execute(() -> feedback.accept("Full " + label + " failed: " + e.getMessage()));
				}
			} finally {
				fullRenderRunning.set(false);
			}
		});
		return true;
	}

	/**
	 * Admin {@code /mapsync render webmap}: generate web map tiles from existing VoxelMapSync cache and MCA.
	 */
	public static boolean startWebmapRender(java.util.function.Consumer<String> feedback) {
		return startWebmapRender(feedback, false);
	}

	/**
	 * Admin {@code /mapsync rerender webmap}: force re-generate all web map tiles and pyramids.
	 */
	public static boolean startWebmapRerender(java.util.function.Consumer<String> feedback) {
		return startWebmapRender(feedback, true);
	}

	private static boolean startWebmapRender(java.util.function.Consumer<String> feedback, boolean force) {
		if (server == null || worker == null || cacheRoot == null) {
			feedback.accept("VoxelMapSync is not ready (server not started).");
			return false;
		}
		if (fullRenderRunning.get()) {
			feedback.accept("A /mapsync render or rerender is currently running. Please wait or use /mapsync stop.");
			return false;
		}
		if (fixRunning.get()) {
			feedback.accept("A /mapsync fix is currently running. Please wait or use /mapsync stop.");
			return false;
		}
		if (!webRenderRunning.compareAndSet(false, true)) {
			feedback.accept("A web map render/rerender is already running.");
			return false;
		}
		MinecraftServer srv = server;
		String label = force ? "rerender" : "render";
		webRenderTask = worker.submit(() -> {
			cancelRequested.set(false);
			try {
				runWebmapRender(msg -> srv.execute(() -> feedback.accept(msg)), force);
			} catch (Exception e) {
				if (!cancelRequested.get() && !Thread.currentThread().isInterrupted()) {
					VoxelMapSync.LOGGER.error("Web map {} failed", label, e);
					srv.execute(() -> feedback.accept("Web map " + label + " failed: " + e.getMessage()));
				}
			} finally {
				webRenderRunning.set(false);
			}
		});
		return true;
	}

	private static void acquireConvertLock() throws InterruptedException {
		int waits = 0;
		while (!converting.compareAndSet(false, true)) {
			if (waits++ > 300) { // ~60s
				throw new IllegalStateException("Timed out waiting for convert worker");
			}
			Thread.sleep(200);
		}
	}

	/**
	 * Convert every present MCA (skips settle debounce; keeps full-status quality gates).
	 *
	 * @param force if true, overwrite existing cache even when unchanged and force-resend to players
	 */
	private static void runFullRender(java.util.function.Consumer<String> feedback, boolean force) throws IOException, InterruptedException {
		if (server == null) {
			feedback.accept("Server gone; aborting " + (force ? "rerender" : "render") + ".");
			return;
		}
		String progressLabel = force ? "Full rerender" : "Full render";
		feedback.accept(force
				? "Rerender: scanning all world region/*.mca and forcing cache overwrite "
						+ "(low-priority background — batched)…"
				: "Scanning world region/*.mca for VoxelMapSync render (low-priority background — batched)…");
		int mcaSeen = 0;
		int mcaOk = 0;
		int mcaIncomplete = 0;
		int mcaFailed = 0;
		int zipAdded = 0;

		List<ServerLevel> levels = new ArrayList<>();
		server.getAllLevels().forEach(levels::add);
		List<List<int[]>> allCoords = new ArrayList<>();
		int totalMcas = 0;
		for (ServerLevel level : levels) {
			Path regionDir = resolveRegionDir(level);
			if (regionDir == null || !Files.isDirectory(regionDir)) {
				allCoords.add(List.of());
				continue;
			}
			List<int[]> coords = new ArrayList<>();
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(regionDir, "*.mca")) {
				for (Path mca : stream) {
					if (Files.size(mca) <= 0) {
						continue;
					}
					Matcher matcher = MCA_NAME.matcher(mca.getFileName().toString());
					if (!matcher.matches()) {
						continue;
					}
					coords.add(new int[]{
							Integer.parseInt(matcher.group(1)),
							Integer.parseInt(matcher.group(2))
					});
				}
			}
			coords.sort(Comparator.comparingInt((int[] c) -> Math.max(Math.abs(c[0]), Math.abs(c[1])))
					.thenComparingInt(c -> c[0])
					.thenComparingInt(c -> c[1]));
			allCoords.add(coords);
			totalMcas += coords.size();
		}

		MapSyncProgress.start(server, progressLabel, Math.max(1, totalMcas));
		int globalSeen = 0;
		int batchRegions = 0;
		int batchConverts = 0;

		try {
			for (int dimIndex = 0; dimIndex < levels.size(); dimIndex++) {
				if (cancelRequested.get()) {
					feedback.accept("Render stopped by admin.");
					break;
				}
				ServerLevel level = levels.get(dimIndex);
				Path regionDir = resolveRegionDir(level);
				String dim = dimensionStorageName(level.dimension());
				List<int[]> coords = allCoords.get(dimIndex);
				if (regionDir == null || !Files.isDirectory(regionDir)) {
					feedback.accept("Dim " + dim + ": no region directory.");
					continue;
				}

				int dimSeen = 0;
				int dimOk = 0;
				feedback.accept("Dim " + dim + ": " + coords.size() + " MCA file(s) — "
						+ (force ? "rerendering…" : "converting…"));
				for (int[] coord : coords) {
					if (cancelRequested.get()) {
						feedback.accept("Render stopped by admin.");
						break;
					}
					dimSeen++;
					mcaSeen++;
					globalSeen++;
					batchRegions++;
					List<RegionRecord> convertedThisMca = new ArrayList<>(4);
					boolean processed;
					try {
						processed = convertOneMcaLocked(level, coord[0], coord[1], convertedThisMca, true, force);
					} catch (IOException e) {
						mcaFailed++;
						VoxelMapSync.LOGGER.warn("Full {} MCA r.{}.{} failed: {}",
								force ? "rerender" : "render", coord[0], coord[1], e.toString());
						MapSyncProgress.advance(server, globalSeen);
						if (shouldYieldBackgroundBatch(batchRegions, batchConverts)) {
							yieldBackgroundTaskBatch(batchConverts);
							batchRegions = 0;
							batchConverts = 0;
						}
						continue;
					}
					if (!processed) {
						mcaFailed++;
						MapSyncProgress.advance(server, globalSeen);
						if (shouldYieldBackgroundBatch(batchRegions, batchConverts)) {
							yieldBackgroundTaskBatch(batchConverts);
							batchRegions = 0;
							batchConverts = 0;
						}
						continue;
					}
					batchConverts++;
					String mcaKey = dim + "|r." + coord[0] + "." + coord[1];
					Path mca = regionDir.resolve("r." + coord[0] + "." + coord[1] + ".mca");
					try {
						mcaMtimes.put(mcaKey, Files.getLastModifiedTime(mca).toMillis());
					} catch (IOException ignored) {
					}
					int added = convertedThisMca.size();
					if (added > 0) {
						dimOk++;
						mcaOk++;
						zipAdded += added;
						queueRegionsForOnlinePlayers(convertedThisMca, force);
					} else {
						mcaIncomplete++;
					}
					MapSyncProgress.advance(server, globalSeen);
					int pct = totalMcas > 0 ? (globalSeen * 100) / totalMcas : 100;
					if (dimSeen % 25 == 0 || dimSeen == coords.size()) {
						feedback.accept("Dim " + dim + ": " + dimSeen + "/" + coords.size()
								+ " MCA (" + pct + "%, tiles this dim=" + dimOk + ", cache total=" + regions.size() + ")");
					}
					if (shouldYieldBackgroundBatch(batchRegions, batchConverts)) {
						yieldBackgroundTaskBatch(batchConverts);
						batchRegions = 0;
						batchConverts = 0;
					}
				}
			}

			if (!cancelRequested.get()) {
				List<RegionRecord> rescanConverted = new ArrayList<>();
				int rescanAdded = rescanEmptyRegions(rescanConverted, Integer.MAX_VALUE, true, force);
				if (rescanAdded > 0) {
					feedback.accept("Empty-region rescan wrote " + rescanAdded + " missing tile(s).");
					queueRegionsForOnlinePlayers(rescanConverted, force);
				}
			}

			String summary = (cancelRequested.get() ? "Render stopped" : (force ? "Full rerender done" : "Full render done"))
					+ " — MCA scanned=" + mcaSeen
					+ ", converted=" + mcaOk
					+ ", skipped/unchanged=" + mcaIncomplete
					+ ", failed=" + mcaFailed
					+ ", VoxelMap tiles written=" + zipAdded
					+ ", cache total=" + regions.size();
			VoxelMapSync.LOGGER.info("VoxelMapSync {}", summary);
			feedback.accept(summary);
		} finally {
			MapSyncProgress.finish(server, cancelRequested.get());
		}
	}

	/**
	 * Publish admin-render results as each MCA finishes instead of retaining every tile until
	 * the entire world scan completes. This makes the server cache authoritative during a long
	 * rerender: current players receive each replacement promptly, while players joining later
	 * receive already-converted records through the normal hello comparison.
	 */
	private static void queueRegionsForOnlinePlayers(List<RegionRecord> converted, boolean forceResend) {
		MinecraftServer srv = server;
		if (srv == null || converted.isEmpty() || srv.getPlayerList().getPlayerCount() == 0) {
			return;
		}
		List<RegionRecord> snapshot = List.copyOf(converted);
		srv.execute(() -> {
			if (server != srv || cancelRequested.get()) {
				return;
			}
			for (ServerPlayer player : srv.getPlayerList().getPlayers()) {
				PlayerSyncState state = playerStates.computeIfAbsent(player.getUUID(), ignored -> new PlayerSyncState());
				enqueueRegions(state, snapshot, true, forceResend);
			}
		});
	}

	/**
	 * Scan every VoxelMap region that overlaps the dimension world border and convert missing or
	 * low-quality cache tiles from MCA. Players receive tiles through viewport sync when panning.
	 */
	private static void runFix(java.util.function.Consumer<String> feedback) throws IOException, InterruptedException {
		if (server == null) {
			feedback.accept("Server gone; aborting fix.");
			return;
		}
		feedback.accept("Fix: scanning regions inside the world border for missing or broken map tiles "
				+ "(low-priority background — "
				+ SyncConfig.backgroundTaskRegionsPerBatch + " regions / "
				+ SyncConfig.backgroundTaskMaxConvertsPerBatch + " convert(s) per batch, "
				+ SyncConfig.backgroundTaskBatchDelayMs + "ms pause)…");

		List<ServerLevel> levels = new ArrayList<>();
		server.getAllLevels().forEach(levels::add);

		List<BorderScanDim> scans = new ArrayList<>();
		int totalRegions = 0;
		for (ServerLevel level : levels) {
			String dim = dimensionStorageName(level.dimension());
			WorldBorder border = level.getWorldBorder();
			List<int[]> coords = collectBorderRegionCoords(border);
			if (coords.isEmpty()) {
				continue;
			}
			scans.add(new BorderScanDim(level, dim, border, coords));
			totalRegions += coords.size();
		}
		if (scans.isEmpty()) {
			feedback.accept("Fix: no regions overlap the world border.");
			return;
		}

		List<RegionRecord> newlyConverted = new ArrayList<>();
		int scanned = 0;
		int converted = 0;
		int alreadyOk = 0;
		int noMca = 0;
		int convertFailed = 0;
		int batchRegions = 0;
		int batchConverts = 0;

		MapSyncProgress.start(server, "Border fix", Math.max(1, totalRegions));
		try {
			for (BorderScanDim scan : scans) {
				if (cancelRequested.get()) {
					feedback.accept("Fix stopped by admin.");
					break;
				}
				WorldBorder border = scan.border();
				feedback.accept("Dim " + scan.dim() + ": border "
						+ (int) border.getMinX() + "…" + (int) border.getMaxX()
						+ " × " + (int) border.getMinZ() + "…" + (int) border.getMaxZ()
						+ " — " + scan.coords().size() + " region(s) to check.");
				for (int[] coord : scan.coords()) {
					if (cancelRequested.get()) {
						feedback.accept("Fix stopped by admin.");
						break;
					}
					scanned++;
					batchRegions++;
					int regionX = coord[0];
					int regionZ = coord[1];
					String dimPath = scan.dim();

					if (!hasBackingMca(dimPath, regionX, regionZ)) {
						noMca++;
						MapSyncProgress.advance(server, scanned);
						if (shouldYieldBackgroundBatch(batchRegions, batchConverts)) {
							yieldBackgroundTaskBatch(batchConverts);
							batchRegions = 0;
							batchConverts = 0;
						}
						continue;
					}
					if (!needsRegionConvert(dimPath, regionX, regionZ, false)) {
						alreadyOk++;
						MapSyncProgress.advance(server, scanned);
						if (shouldYieldBackgroundBatch(batchRegions, batchConverts)) {
							yieldBackgroundTaskBatch(batchConverts);
							batchRegions = 0;
							batchConverts = 0;
						}
						continue;
					}
					int before = newlyConverted.size();
					convertOneRegionLocked(scan.level(), regionX, regionZ, newlyConverted, true, false);
					batchConverts++;
					if (newlyConverted.size() > before) {
						converted++;
					} else {
						convertFailed++;
					}
					MapSyncProgress.advance(server, scanned);
					if (scanned % 100 == 0 || scanned == totalRegions) {
						int pct = totalRegions > 0 ? (scanned * 100) / totalRegions : 100;
						feedback.accept("Fix progress: " + scanned + "/" + totalRegions
								+ " (" + pct + "%, converted=" + converted + ", already ok=" + alreadyOk + ")");
					}
					if (shouldYieldBackgroundBatch(batchRegions, batchConverts)) {
						yieldBackgroundTaskBatch(batchConverts);
						batchRegions = 0;
						batchConverts = 0;
					}
				}
			}

			String summary = (cancelRequested.get() ? "Fix stopped" : "Fix done")
					+ " — border regions scanned=" + scanned
					+ ", converted=" + converted
					+ ", already ok=" + alreadyOk
					+ ", no MCA=" + noMca
					+ ", convert failed/skipped=" + convertFailed
					+ ", cache total=" + regions.size();
			if (!cancelRequested.get() && converted > 0) {
				feedback.accept("Server cache repaired — players load new tiles via viewport sync when they pan the world map.");
			}
			VoxelMapSync.LOGGER.info("VoxelMapSync {}", summary);
			feedback.accept(summary);
		} finally {
			MapSyncProgress.finish(server, cancelRequested.get());
		}
	}

	private static List<int[]> collectBorderRegionCoords(WorldBorder border) {
		int minRegionX = (int) Math.floor(border.getMinX() / 256.0);
		int maxRegionX = (int) Math.floor(border.getMaxX() / 256.0);
		int minRegionZ = (int) Math.floor(border.getMinZ() / 256.0);
		int maxRegionZ = (int) Math.floor(border.getMaxZ() / 256.0);
		List<int[]> coords = new ArrayList<>();
		for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
			for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
				if (WorldBorderRegions.overlaps(border, regionX, regionZ)) {
					coords.add(new int[]{regionX, regionZ});
				}
			}
		}
		return coords;
	}

	private record BorderScanDim(ServerLevel level, String dim, WorldBorder border, List<int[]> coords) {
	}

	private static void runWebmapRender(java.util.function.Consumer<String> feedback, boolean force) throws IOException, InterruptedException {
		if (server == null) {
			feedback.accept("Server gone; aborting web map " + (force ? "rerender" : "render") + ".");
			return;
		}
		WebMapExporter.init();
		broadcastClaims();

		String progressLabel = force ? "Web map rerender" : "Web map render";
		feedback.accept(force
				? "Webmap Rerender: scanning cache & MCA to force-regenerate all web map tiles…"
				: "Webmap Render: scanning cache & MCA for missing/updated web map tiles…");

		int tilesRendered = 0;
		int tilesSkipped = 0;
		int tilesFailed = 0;

		Path webmapDir = WebMapExporter.getWebmapDir();

		List<ServerLevel> levels = new ArrayList<>();
		server.getAllLevels().forEach(levels::add);
		List<String> dimCleanNames = new ArrayList<>();
		List<List<int[]>> allCoords = new ArrayList<>();
		List<Path> dimCacheDirs = new ArrayList<>();
		List<Path> dimRegionDirs = new ArrayList<>();
		List<Path> zoom0Dirs = new ArrayList<>();
		int totalRegions = 0;

		for (ServerLevel level : levels) {
			if (cancelRequested.get()) {
				break;
			}
			String dimRaw = dimensionStorageName(level.dimension());
			String dimClean = dimRaw.replace("minecraft:", "").replace("minecraft~colon~", "").replace(":", "_").replace("~colon~", "_");
			if (dimClean.isBlank()) {
				dimClean = "overworld";
			}

			Path dimCacheDir = cacheRoot.resolve(dimRaw);
			Path dimRegionDir = resolveRegionDir(level);
			Path zoom0Dir = webmapDir.resolve("tiles").resolve(dimClean).resolve("0");
			Files.createDirectories(zoom0Dir);

			Set<Long> coordSet = new HashSet<>();
			if (Files.isDirectory(dimCacheDir)) {
				try (DirectoryStream<Path> stream = Files.newDirectoryStream(dimCacheDir, "*.zip")) {
					for (Path zipFile : stream) {
						Matcher matcher = ZIP_NAME.matcher(zipFile.getFileName().toString());
						if (matcher.matches()) {
							int rx = Integer.parseInt(matcher.group(1));
							int rz = Integer.parseInt(matcher.group(2));
							coordSet.add((((long) rx) << 32) | (rz & 0xFFFFFFFFL));
						}
					}
				}
			}
			if (dimRegionDir != null && Files.isDirectory(dimRegionDir)) {
				try (DirectoryStream<Path> stream = Files.newDirectoryStream(dimRegionDir, "*.mca")) {
					for (Path mcaFile : stream) {
						if (Files.size(mcaFile) <= 0) {
							continue;
						}
						Matcher matcher = MCA_NAME.matcher(mcaFile.getFileName().toString());
						if (matcher.matches()) {
							int rx = Integer.parseInt(matcher.group(1));
							int rz = Integer.parseInt(matcher.group(2));
							coordSet.add((((long) rx) << 32) | (rz & 0xFFFFFFFFL));
						}
					}
				}
			}

			List<int[]> coords = new ArrayList<>();
			for (long key : coordSet) {
				int rx = (int) (key >> 32);
				int rz = (int) key;
				coords.add(new int[]{rx, rz});
			}
			coords.sort(Comparator.comparingInt((int[] c) -> Math.max(Math.abs(c[0]), Math.abs(c[1])))
					.thenComparingInt(c -> c[0])
					.thenComparingInt(c -> c[1]));

			dimCleanNames.add(dimClean);
			allCoords.add(coords);
			dimCacheDirs.add(dimCacheDir);
			dimRegionDirs.add(dimRegionDir);
			zoom0Dirs.add(zoom0Dir);
			totalRegions += coords.size();
		}

		boolean willRebuildPyramid = !cancelRequested.get();
		int totalSteps = Math.max(1, totalRegions + (willRebuildPyramid ? 1 : 0));
		MapSyncProgress.start(server, progressLabel, totalSteps);
		int globalSeen = 0;
		int batchRegions = 0;
		int batchConverts = 0;

		try {
			for (int dimIndex = 0; dimIndex < levels.size(); dimIndex++) {
				if (cancelRequested.get()) {
					feedback.accept("Web map render stopped by admin.");
					break;
				}
				ServerLevel level = levels.get(dimIndex);
				String dimClean = dimCleanNames.get(dimIndex);
				List<int[]> coords = allCoords.get(dimIndex);
				Path dimCacheDir = dimCacheDirs.get(dimIndex);
				Path dimRegionDir = dimRegionDirs.get(dimIndex);
				Path zoom0Dir = zoom0Dirs.get(dimIndex);

				feedback.accept("Dim " + dimClean + ": " + coords.size() + " region(s) found.");

				int dimSeen = 0;
				int dimRendered = 0;
				for (int[] coord : coords) {
					if (cancelRequested.get()) {
						feedback.accept("Web map render stopped by admin.");
						break;
					}
					dimSeen++;
					globalSeen++;
					batchRegions++;
					int rx = coord[0];
					int rz = coord[1];
					Path pngFile = zoom0Dir.resolve(rx + "_" + rz + ".png");
					Path zipFile = dimCacheDir != null ? dimCacheDir.resolve(rx + "," + rz + ".zip") : null;
					Path mcaFile = dimRegionDir != null ? dimRegionDir.resolve("r." + rx + "." + rz + ".mca") : null;

					if (!force && Files.exists(pngFile)) {
						long pngMtime = Files.getLastModifiedTime(pngFile).toMillis();
						long sourceMtime = 0;
						if (zipFile != null && Files.exists(zipFile)) {
							sourceMtime = Math.max(sourceMtime, Files.getLastModifiedTime(zipFile).toMillis());
						}
						if (mcaFile != null && Files.exists(mcaFile)) {
							sourceMtime = Math.max(sourceMtime, Files.getLastModifiedTime(mcaFile).toMillis());
						}
						if (pngMtime >= sourceMtime && sourceMtime > 0) {
							tilesSkipped++;
							MapSyncProgress.advance(server, globalSeen);
							if (shouldYieldBackgroundBatch(batchRegions, batchConverts)) {
								yieldBackgroundTaskBatch(batchConverts);
								batchRegions = 0;
								batchConverts = 0;
							}
							continue;
						}
					}

					boolean ok = false;
					if (zipFile != null && Files.exists(zipFile)) {
						ok = WebMapExporter.exportTileFromZipFile(dimClean, rx, rz, zipFile);
					}
					if (!ok && mcaFile != null && Files.exists(mcaFile)) {
						batchConverts++;
						try (McaReader reader = McaReader.open(mcaFile, level.getMinY(), level.getHeight(), SyncConfig.strictFullStatusOnly)) {
							List<McaReader.ChunkSurface> chunks = reader.readSurfaces(rx, rz);
							if (!chunks.isEmpty()) {
								WebMapExporter.exportTile(dimClean, rx, rz, chunks);
								ok = true;
							}
						} catch (Exception e) {
							VoxelMapSync.LOGGER.warn("Failed to render web tile from MCA {},{} dim={}", rx, rz, dimClean, e);
						}
					}

					if (ok) {
						dimRendered++;
						tilesRendered++;
					} else {
						tilesFailed++;
					}

					MapSyncProgress.advance(server, globalSeen);
					int pct = totalRegions > 0 ? (globalSeen * 100) / totalSteps : 0;
					if (dimSeen % 25 == 0 || dimSeen == coords.size()) {
						feedback.accept("Dim " + dimClean + ": " + dimSeen + "/" + coords.size()
								+ " (" + pct + "%, rendered=" + dimRendered + ", skipped=" + tilesSkipped + ")");
					}
					if (shouldYieldBackgroundBatch(batchRegions, batchConverts)) {
						yieldBackgroundTaskBatch(batchConverts);
						batchRegions = 0;
						batchConverts = 0;
					}
				}
			}

			if (!cancelRequested.get() && (tilesRendered > 0 || force)) {
				feedback.accept("Rebuilding web map pyramid overviews (zooms -1 to -" + SyncConfig.webmapMaxZoomOut + ")…");
				globalSeen++;
				MapSyncProgress.advance(server, globalSeen);
				WebMapExporter.rebuildPyramids();
			}

			String summary = (cancelRequested.get() ? "Web map render stopped" : ((force ? "Web map rerender" : "Web map render") + " finished"))
					+ " — rendered=" + tilesRendered
					+ ", skipped=" + tilesSkipped
					+ ", failed/empty=" + tilesFailed
					+ " (webmap at " + webmapDir.toAbsolutePath() + "/index.html)";
			VoxelMapSync.LOGGER.info("VoxelMapSync {}", summary);
			feedback.accept(summary);
		} finally {
			MapSyncProgress.finish(server, cancelRequested.get());
		}
	}

	private static void runConvertCycle(boolean full) {
		if (server == null || cancelRequested.get() || Thread.currentThread().isInterrupted()
				|| !converting.compareAndSet(false, true)) {
			return;
		}
		try {
			List<RegionRecord> newlyConverted = new ArrayList<>();
			int converted = drainPriorityConverts(newlyConverted);
			if (converted < SyncConfig.maxRegionsPerCycle || full) {
				for (ServerLevel level : server.getAllLevels()) {
					converted += convertDimension(level, full, newlyConverted, SyncConfig.maxRegionsPerCycle - converted);
					if (converted >= SyncConfig.maxRegionsPerCycle && !full) {
						break;
					}
				}
			}
			int rescanAdded = rescanEmptyRegions(
					newlyConverted,
					Math.max(0, SyncConfig.maxRegionsPerCycle - converted),
					false,
					false);
			if (rescanAdded > 0) {
				VoxelMapSync.LOGGER.info(
						"VoxelMapSync empty-region rescan converted {} tile(s) (cache total={})",
						rescanAdded, regions.size());
				converted += rescanAdded;
			}
			if (!newlyConverted.isEmpty()) {
				VoxelMapSync.LOGGER.info(
						"VoxelMapSync converted {} region(s); broadcasting to {} player(s) (cache total={})",
						newlyConverted.size(), server.getPlayerList().getPlayers().size(), regions.size());
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					PlayerSyncState state = playerStates.computeIfAbsent(player.getUUID(), ignored -> new PlayerSyncState());
					// Fresh converts go to the front so they beat leftover join-fill FIFO
					enqueueRegions(state, newlyConverted, true);
				}
			} else {
				VoxelMapSync.LOGGER.debug(
						"VoxelMapSync convert cycle: nothing new to send (work={}, cache total={})",
						converted, regions.size());
			}
		} catch (Exception e) {
			VoxelMapSync.LOGGER.error("Convert cycle failed", e);
		} finally {
			converting.set(false);
		}
	}

	private static int drainPriorityConverts(List<RegionRecord> newlyConverted) throws IOException {
		int done = 0;
		while (done < SyncConfig.maxRegionsPerCycle
				&& !cancelRequested.get()
				&& !Thread.currentThread().isInterrupted()) {
			PriorityConvertJob job;
			synchronized (priorityConvertQueue) {
				job = priorityConvertQueue.pollFirst();
				if (job != null) {
					priorityConvertKeys.remove(job.key);
				}
			}
			if (job == null) {
				break;
			}
			ServerLevel level = findLevel(job.dim);
			if (level == null) {
				markPriorityConvertFailed(job.key, "missing_dimension");
				continue;
			}
			int before = newlyConverted.size();
			// Viewport/on-demand: player is staring at a hole — skip settle (background autoConvert only).
			boolean ok = convertOneMca(level, job.mcaX, job.mcaZ, newlyConverted, true, false);
			if (!ok) {
				markPriorityConvertFailed(job.key, diagnosePriorityConvertFailure(job));
				done++;
				continue;
			}
			String mcaKey = job.dim + "|r." + job.mcaX + "." + job.mcaZ;
			Path mca = resolveMcaPath(job.dim, job.mcaX, job.mcaZ);
			if (mca != null) {
				try {
					mcaMtimes.put(mcaKey, Files.getLastModifiedTime(mca).toMillis());
				} catch (IOException ignored) {
					// keep trying next cycle if mtime unread
				}
			}
			int added = newlyConverted.size() - before;
			done += added > 0 ? added : 1;
			if (added > 0) {
				priorityConvertCooldownUntilMs.remove(job.key);
				VoxelMapSync.LOGGER.info(
						"VoxelMapSync on-demand convert r.{}.{} for dim {} (mca→cache, {} tile(s))",
						job.mcaX, job.mcaZ, job.dim, added);
				enqueuePriorityConvertToRequester(job, newlyConverted.subList(before, newlyConverted.size()));
			} else {
				markPriorityConvertFailed(job.key, diagnosePriorityConvertFailure(job));
			}
		}
		return done;
	}

	private static void enqueuePriorityConvertToRequester(PriorityConvertJob job, List<RegionRecord> tiles) {
		if (server == null || tiles.isEmpty() || job.requester == null) {
			return;
		}
		ServerPlayer requester = server.getPlayerList().getPlayer(job.requester);
		if (requester == null) {
			return;
		}
		PlayerSyncState state = playerStates.computeIfAbsent(job.requester, ignored -> new PlayerSyncState());
		enqueueRegions(state, tiles, true, true);
	}

	/** Best-effort reject reason for INFO logs when priority MCA→cache produces no tiles. */
	private static String diagnosePriorityConvertFailure(PriorityConvertJob job) {
		Path mca = resolveMcaPath(job.dim, job.mcaX, job.mcaZ);
		if (mca == null || !Files.isRegularFile(mca)) {
			return "no_mca";
		}
		try {
			if (Files.size(mca) <= 0) {
				return "empty_mca";
			}
		} catch (IOException e) {
			return "mca_io";
		}
		ServerLevel level = findLevel(job.dim);
		if (level == null) {
			return "missing_dimension";
		}
		try (McaReader reader = McaReader.open(mca, level.getMinY(), level.getHeight(), SyncConfig.strictFullStatusOnly)) {
			McaReader.ReadResult read = reader.readSurfacesDetailed(job.mcaX, job.mcaZ);
			if (read.surfaces().isEmpty()) {
				return "empty_surfaces(fullChunks=" + read.fullChunks() + ")";
			}
			for (int ox = 0; ox < 2; ox++) {
				for (int oz = 0; oz < 2; oz++) {
					int regionX = job.mcaX * 2 + ox;
					int regionZ = job.mcaZ * 2 + oz;
					List<McaReader.ChunkSurface> subset = filterRegionSubset(read.surfaces(), regionX, regionZ);
					if (subset.isEmpty()) {
						continue;
					}
					RegionQuality.Assessment q = RegionQuality.assessRegionSubset(subset, regionX, regionZ);
					if (!q.ok()) {
						return q.reason().name().toLowerCase() + "(" + q.detail() + ")";
					}
				}
			}
			return "no_tiles_written(fullChunks=" + read.fullChunks() + ")";
		} catch (IOException e) {
			return "mca_read:" + e.getClass().getSimpleName();
		}
	}

	private static int convertDimension(ServerLevel level, boolean full, List<RegionRecord> newlyConverted, int budget)
			throws IOException {
		if (budget <= 0) {
			return 0;
		}
		Path regionDir = resolveRegionDir(level);
		if (regionDir == null || !Files.isDirectory(regionDir)) {
			return 0;
		}
		String dimPath = dimensionStorageName(level.dimension());
		int done = 0;
		int mcaSeen = 0;
		int mcaDirty = 0;
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(regionDir, "*.mca")) {
			for (Path mca : stream) {
				if (cancelRequested.get() || Thread.currentThread().isInterrupted()) {
					return done;
				}
				if (Files.size(mca) <= 0) {
					continue;
				}
				Matcher matcher = MCA_NAME.matcher(mca.getFileName().toString());
				if (!matcher.matches()) {
					continue;
				}
				mcaSeen++;
				int mcaX = Integer.parseInt(matcher.group(1));
				int mcaZ = Integer.parseInt(matcher.group(2));
				String mcaKey = dimPath + "|r." + mcaX + "." + mcaZ;
				long mtime = Files.getLastModifiedTime(mca).toMillis();
				Long prev = mcaMtimes.get(mcaKey);
				if (!full && prev != null && prev == mtime) {
					continue;
				}
				mcaDirty++;

				long ageMs = System.currentTimeMillis() - mtime;
				if (!full && SyncConfig.convertSettleSeconds > 0
						&& ageMs < SyncConfig.convertSettleSeconds * 1000L) {
					VoxelMapSync.LOGGER.debug(
							"VoxelMapSync skip convert (settle) {} age={}ms", mca.getFileName(), ageMs);
					continue;
				}

				int before = newlyConverted.size();
				boolean processed = convertOneMca(level, mcaX, mcaZ, newlyConverted, false, false);
				if (processed) {
					int added = newlyConverted.size() - before;
					// Do not lock MCA mtime until every quadrant with chunk data has a good cache tile.
					if (added > 0 || allMcaRegionsSatisfied(dimPath, mcaX, mcaZ, mca)) {
						mcaMtimes.put(mcaKey, mtime);
					}
					done += added;
					if (done >= budget && !full) {
						VoxelMapSync.LOGGER.info(
								"VoxelMapSync dim {}: scanned {} MCA, dirty {}, converted {} (capped)",
								dimPath, mcaSeen, mcaDirty, done);
						return done;
					}
				}
				// false = settle / missing / IO — do NOT lock mtime or we never retry settled MCAs
			}
		}
		if (mcaDirty > 0 || done > 0) {
			VoxelMapSync.LOGGER.info(
					"VoxelMapSync dim {}: scanned {} MCA, dirty {}, converted {}",
					dimPath, mcaSeen, mcaDirty, done);
		}
		return done;
	}

	/**
	 * Convert one Anvil MCA into up to four VoxelMap region zips (16×16 chunks each).
	 * Applies quality gates; does not replace a better existing cache with a worse zip
	 * unless {@code forceOverwrite} is set.
	 *
	 * @param ignoreSettle if true, skip mtime settle debounce (admin full render)
	 * @param forceOverwrite if true, always rewrite cache and queue for players even if unchanged
	 * @return true if the MCA was processed (converted or deliberately skipped after checks)
	 */
	private static boolean convertOneMca(
			ServerLevel level,
			int mcaX,
			int mcaZ,
			List<RegionRecord> newlyConverted,
			boolean ignoreSettle,
			boolean forceOverwrite
	) throws IOException {
		Path regionDir = resolveRegionDir(level);
		if (regionDir == null) {
			return false;
		}
		Path mca = regionDir.resolve("r." + mcaX + "." + mcaZ + ".mca");
		if (!Files.isRegularFile(mca) || Files.size(mca) <= 0) {
			return false;
		}

		if (!ignoreSettle && SyncConfig.convertSettleSeconds > 0) {
			long ageMs = System.currentTimeMillis() - Files.getLastModifiedTime(mca).toMillis();
			if (ageMs < SyncConfig.convertSettleSeconds * 1000L) {
				VoxelMapSync.LOGGER.debug("VoxelMapSync skip convert (settle) {}", mca.getFileName());
				return false;
			}
		}

		String dimPath = dimensionStorageName(level.dimension());
		int minY = level.getMinY();
		int height = level.getHeight();
		McaReader.ReadResult read;
		try (McaReader reader = McaReader.open(mca, minY, height, SyncConfig.strictFullStatusOnly)) {
			read = reader.readSurfacesDetailed(mcaX, mcaZ);
		} catch (IOException e) {
			VoxelMapSync.LOGGER.warn("Failed reading MCA {}: {}", mca.getFileName(), e.toString());
			return false;
		}

		if (read.surfaces().isEmpty()) {
			VoxelMapSync.LOGGER.debug("MCA {} had no usable chunk surfaces", mca.getFileName());
			return true;
		}

		List<McaReader.ChunkSurface> surfaces = read.surfaces();
		for (int ox = 0; ox < 2; ox++) {
			for (int oz = 0; oz < 2; oz++) {
				int regionX = mcaX * 2 + ox;
				int regionZ = mcaZ * 2 + oz;
				List<McaReader.ChunkSurface> subset = filterRegionSubset(surfaces, regionX, regionZ);
				if (subset.isEmpty()) {
					continue;
				}
				processRegionSubset(dimPath, regionX, regionZ, subset, newlyConverted, forceOverwrite);
			}
		}
		return true;
	}

	/**
	 * Convert one VoxelMap region (16×16 chunks) from its backing MCA.
	 *
	 * @return true if the MCA existed and was processed (converted or rejected after checks)
	 */
	private static boolean convertOneRegion(
			ServerLevel level,
			int regionX,
			int regionZ,
			List<RegionRecord> newlyConverted,
			boolean ignoreSettle,
			boolean forceOverwrite
	) throws IOException {
		Path regionDir = resolveRegionDir(level);
		if (regionDir == null) {
			return false;
		}
		int mcaX = Math.floorDiv(regionX, 2);
		int mcaZ = Math.floorDiv(regionZ, 2);
		Path mca = regionDir.resolve("r." + mcaX + "." + mcaZ + ".mca");
		if (!Files.isRegularFile(mca) || Files.size(mca) <= 0) {
			return false;
		}

		if (!ignoreSettle && SyncConfig.convertSettleSeconds > 0) {
			long ageMs = System.currentTimeMillis() - Files.getLastModifiedTime(mca).toMillis();
			if (ageMs < SyncConfig.convertSettleSeconds * 1000L) {
				VoxelMapSync.LOGGER.debug("VoxelMapSync skip convert (settle) {}", mca.getFileName());
				return false;
			}
		}

		String dimPath = dimensionStorageName(level.dimension());
		McaReader.ReadResult read;
		try (McaReader reader = McaReader.open(mca, level.getMinY(), level.getHeight(), SyncConfig.strictFullStatusOnly)) {
			read = reader.readSurfacesDetailed(mcaX, mcaZ);
		} catch (IOException e) {
			VoxelMapSync.LOGGER.warn("Failed reading MCA {}: {}", mca.getFileName(), e.toString());
			return false;
		}

		List<McaReader.ChunkSurface> subset = filterRegionSubset(read.surfaces(), regionX, regionZ);
		if (subset.isEmpty()) {
			VoxelMapSync.LOGGER.debug("VoxelMapSync region {},{} had no usable chunk surfaces", regionX, regionZ);
			return true;
		}

		boolean converted = processRegionSubset(dimPath, regionX, regionZ, subset, newlyConverted, forceOverwrite);
		if (converted) {
			String mcaKey = dimPath + "|r." + mcaX + "." + mcaZ;
			mcaMtimes.put(mcaKey, Files.getLastModifiedTime(mca).toMillis());
		}
		return true;
	}

	/**
	 * Second pass after each convert/render scan: retry VoxelMap regions that have backing MCA
	 * data but no cache zip yet (or a zip that fails quality gates).
	 *
	 * @return number of newly written region tiles
	 */
	private static int rescanEmptyRegions(
			List<RegionRecord> newlyConverted,
			int budget,
			boolean ignoreSettle,
			boolean forceOverwrite
	) throws IOException {
		if (budget <= 0 || server == null) {
			return 0;
		}
		int written = 0;
		for (ServerLevel level : server.getAllLevels()) {
			Path regionDir = resolveRegionDir(level);
			if (regionDir == null || !Files.isDirectory(regionDir)) {
				continue;
			}
			String dimPath = dimensionStorageName(level.dimension());
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(regionDir, "*.mca")) {
				for (Path mca : stream) {
					if (cancelRequested.get() || Thread.currentThread().isInterrupted()) {
						return written;
					}
					if (written >= budget) {
						return written;
					}
					if (Files.size(mca) <= 0) {
						continue;
					}
					Matcher matcher = MCA_NAME.matcher(mca.getFileName().toString());
					if (!matcher.matches()) {
						continue;
					}
					int mcaX = Integer.parseInt(matcher.group(1));
					int mcaZ = Integer.parseInt(matcher.group(2));
					for (int ox = 0; ox < 2; ox++) {
						for (int oz = 0; oz < 2; oz++) {
							if (written >= budget) {
								return written;
							}
							int regionX = mcaX * 2 + ox;
							int regionZ = mcaZ * 2 + oz;
							if (!needsRegionConvert(dimPath, regionX, regionZ, forceOverwrite)) {
								continue;
							}
							int before = newlyConverted.size();
							convertOneRegion(level, regionX, regionZ, newlyConverted, ignoreSettle, forceOverwrite);
							if (newlyConverted.size() > before) {
								written++;
								VoxelMapSync.LOGGER.info(
										"VoxelMapSync empty-region rescan converted {},{} dim {}",
										regionX, regionZ, dimPath);
							}
						}
					}
				}
			}
		}
		return written;
	}

	private static boolean needsRegionConvert(String dimPath, int regionX, int regionZ, boolean force) {
		if (force) {
			return hasBackingMca(dimPath, regionX, regionZ);
		}
		if (!hasBackingMca(dimPath, regionX, regionZ)) {
			return false;
		}
		String key = dimPath + "|" + regionX + "," + regionZ;
		RegionRecord existing = regions.get(key);
		if (existing == null) {
			return true;
		}
		Path zipPath = cacheRoot.resolve(dimPath).resolve(regionX + "," + regionZ + ".zip");
		if (!Files.isRegularFile(zipPath)) {
			return true;
		}
		return !RegionQuality.assessZip(existing.zip).ok();
	}

	private static boolean allMcaRegionsSatisfied(String dimPath, int mcaX, int mcaZ, Path mca) throws IOException {
		ServerLevel level = findLevel(dimPath);
		if (level == null) {
			return false;
		}
		try (McaReader reader = McaReader.open(mca, level.getMinY(), level.getHeight(), SyncConfig.strictFullStatusOnly)) {
			List<McaReader.ChunkSurface> surfaces = reader.readSurfaces(mcaX, mcaZ);
			for (int ox = 0; ox < 2; ox++) {
				for (int oz = 0; oz < 2; oz++) {
					int regionX = mcaX * 2 + ox;
					int regionZ = mcaZ * 2 + oz;
					List<McaReader.ChunkSurface> subset = filterRegionSubset(surfaces, regionX, regionZ);
					if (subset.isEmpty()) {
						continue;
					}
					if (needsRegionConvert(dimPath, regionX, regionZ, false)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private static List<McaReader.ChunkSurface> filterRegionSubset(
			List<McaReader.ChunkSurface> surfaces,
			int regionX,
			int regionZ
	) {
		List<McaReader.ChunkSurface> subset = new ArrayList<>();
		for (McaReader.ChunkSurface surface : surfaces) {
			int lx = surface.chunkX() - regionX * 16;
			int lz = surface.chunkZ() - regionZ * 16;
			if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
				subset.add(surface);
			}
		}
		return subset;
	}

	/**
	 * @return true if a cache zip was written for this region
	 */
	private static boolean processRegionSubset(
			String dimPath,
			int regionX,
			int regionZ,
			List<McaReader.ChunkSurface> subset,
			List<RegionRecord> newlyConverted,
			boolean forceOverwrite
	) throws IOException {
		RegionQuality.Assessment subsetQuality = RegionQuality.assessRegionSubset(subset, regionX, regionZ);
		if (!subsetQuality.ok()) {
			VoxelMapSync.LOGGER.debug(
					"VoxelMapSync reject low-quality zip {},{} reason={} ({})",
					regionX, regionZ, subsetQuality.reason(), subsetQuality.detail());
			return false;
		}
		float sentinel = McaReader.sentinelFraction(subset);
		if (sentinel > SyncConfig.maxSentinelFraction) {
			VoxelMapSync.LOGGER.debug(
					"VoxelMapSync reject low-quality zip {},{} sentinel={}",
					regionX, regionZ, sentinel);
			return false;
		}
		byte[] zip = VoxelMapZipWriter.writeRegion(regionX, regionZ, subset);
		RegionQuality.Assessment zipQuality = RegionQuality.assessZip(zip);
		if (!zipQuality.ok()) {
			VoxelMapSync.LOGGER.debug(
					"VoxelMapSync reject written zip {},{} reason={} ({})",
					regionX, regionZ, zipQuality.reason(), zipQuality.detail());
			return false;
		}
		String hash = sha256(zip);
		String key = dimPath + "|" + regionX + "," + regionZ;
		RegionRecord previous = regions.get(key);
		int fullChunks = subset.size();
		if (!forceOverwrite && previous != null && isWorseQuality(fullChunks, sentinel, previous)) {
			VoxelMapSync.LOGGER.debug(
					"VoxelMapSync keep existing cache {},{} (new worse quality)",
					regionX, regionZ);
			return false;
		}
		Path out = cacheRoot.resolve(dimPath).resolve(regionX + "," + regionZ + ".zip");
		VoxelMapZipWriter.writeToFile(out, zip);
		WebMapExporter.exportTile(dimPath, regionX, regionZ, subset);
		RegionRecord record = new RegionRecord(dimPath, regionX, regionZ, hash, zip, fullChunks, sentinel);
		regions.put(key, record);
		if (forceOverwrite || previous == null || !hash.equals(previous.hash)) {
			newlyConverted.add(record);
		}
		return true;
	}

	private static void runPlayerRegionRender(
			ServerPlayer player,
			ServerLevel level,
			int regionX,
			int regionZ,
			boolean force,
			java.util.function.Consumer<String> feedback
	) throws IOException, InterruptedException {
		if (server == null) {
			feedback.accept("Server gone; aborting region " + (force ? "rerender" : "render") + ".");
			return;
		}
		String dimPath = dimensionStorageName(level.dimension());
		String progressLabel = (force ? "Region rerender " : "Region render ") + regionX + "," + regionZ;
		feedback.accept((force ? "Rerendering" : "Rendering") + " region " + regionX + "," + regionZ
				+ " (" + dimPath + ") from your position…");

		MapSyncProgress.start(server, progressLabel, 1);
		try {
			List<RegionRecord> newlyConverted = new ArrayList<>();
			int before = newlyConverted.size();
			convertOneRegionLocked(level, regionX, regionZ, newlyConverted, true, force);
			boolean cacheUpdated = newlyConverted.size() > before;
			MapSyncProgress.advance(server, 1);

			if (force && !cacheUpdated) {
				Path zipFile = cacheRoot.resolve(dimPath).resolve(regionX + "," + regionZ + ".zip");
				RegionRecord existing = regions.get(regionKey(dimPath, regionX, regionZ));
				if (existing != null && Files.exists(zipFile)) {
					WebMapExporter.init();
					if (WebMapExporter.exportTileFromZipFile(dimPath, regionX, regionZ, zipFile)) {
						newlyConverted.add(existing);
						feedback.accept("Web map tile refreshed from existing cache (MCA convert skipped).");
					}
				}
			}

			if (!newlyConverted.isEmpty()) {
				int players = 0;
				for (ServerPlayer online : server.getPlayerList().getPlayers()) {
					PlayerSyncState state = playerStates.computeIfAbsent(online.getUUID(), ignored -> new PlayerSyncState());
					enqueueRegions(state, newlyConverted, true, force);
					players++;
				}
				feedback.accept("Queued region " + regionX + "," + regionZ + " to " + players + " player(s)"
						+ (force ? " (force overwrite local discovery)" : "") + ".");
			}

			String summary;
			if (!newlyConverted.isEmpty()) {
				summary = "Region " + regionX + "," + regionZ + " "
						+ (force ? "rerendered" : "rendered")
						+ " — VoxelMap cache + web map updated.";
			} else {
				summary = "Region " + regionX + "," + regionZ
						+ ": no update (missing MCA, quality gate rejected, or already up to date).";
			}
			VoxelMapSync.LOGGER.info("VoxelMapSync region {} by {}: {}", force ? "rerender" : "render",
					player.getGameProfile().name(), summary);
			feedback.accept(summary);
		} finally {
			MapSyncProgress.finish(server, false);
		}
	}

	private static boolean isWorseQuality(int newFullChunks, float newSentinel, RegionRecord previous) {
		if (previous.fullChunks <= 0) {
			return false;
		}
		if (newFullChunks < previous.fullChunks) {
			return true;
		}
		return newFullChunks == previous.fullChunks && newSentinel > previous.sentinelFraction + 0.01f;
	}

	private static int enqueueMissingRegions(ServerPlayer player, PlayerSyncState state) {
		if (regions.isEmpty()) {
			return 0;
		}
		int playerRegionX = (int) Math.floor(player.getX() / 256.0);
		int playerRegionZ = (int) Math.floor(player.getZ() / 256.0);
		String playerDim = dimensionStorageName(player.level().dimension());

		List<RegionRecord> candidates = new ArrayList<>();
		for (RegionRecord region : regions.values()) {
			String key = regionKey(region);
			if (region.hash.equals(state.known.get(key))) {
				continue;
			}
			if (!SyncConfig.syncOrphanCache && !hasBackingMca(region.dimension, region.regionX, region.regionZ)) {
				continue;
			}
			if (SyncConfig.joinSyncRadiusRegions > 0) {
				if (!region.dimension.equals(playerDim)) {
					continue;
				}
				int dist = Math.max(Math.abs(region.regionX - playerRegionX), Math.abs(region.regionZ - playerRegionZ));
				if (dist > SyncConfig.joinSyncRadiusRegions) {
					continue;
				}
			}
			candidates.add(region);
		}
		candidates.sort(Comparator.comparingInt(r ->
				Math.max(Math.abs(r.regionX - playerRegionX), Math.abs(r.regionZ - playerRegionZ))));

		int queued = 0;
		for (RegionRecord region : candidates) {
			if (enqueueRegions(state, List.of(region), false, false) > 0) {
				queued++;
			}
		}
		return queued;
	}

	private static int enqueueRegions(PlayerSyncState state, List<RegionRecord> toSend, boolean priority) {
		return enqueueRegions(state, toSend, priority, false);
	}

	/**
	 * @param forceResend if true, clear known hash and (re)queue so the client zip is overwritten
	 *                    even when the content hash matches — used by {@code /mapsync rerender}
	 */
	private static int enqueueRegions(
			PlayerSyncState state,
			List<RegionRecord> toSend,
			boolean priority,
			boolean forceResend
	) {
		int queued = 0;
		synchronized (state) {
			for (RegionRecord region : toSend) {
				String key = regionKey(region);
				if (forceResend) {
					state.known.remove(key);
				} else if (region.hash.equals(state.known.get(key))) {
					continue;
				}
				if (state.pendingKeys.contains(key)) {
					moveToFront(state, key, region);
					queued++;
					continue;
				}
				if (priority) {
					state.pending.addFirst(region);
				} else {
					state.pending.addLast(region);
				}
				state.pendingKeys.add(key);
				queued++;
			}
		}
		return queued;
	}

	private static boolean enqueuePriority(PlayerSyncState state, RegionRecord region) {
		return enqueueRegions(state, List.of(region), true, false) > 0
				|| bumpExisting(state, region);
	}

	private static boolean bumpExisting(PlayerSyncState state, RegionRecord region) {
		String key = regionKey(region);
		synchronized (state) {
			if (!state.pendingKeys.contains(key)) {
				return false;
			}
			if (region.hash.equals(state.known.get(key))) {
				return false;
			}
			moveToFront(state, key, region);
			return true;
		}
	}

	private static void moveToFront(PlayerSyncState state, String key, RegionRecord region) {
		Iterator<RegionRecord> it = state.pending.iterator();
		while (it.hasNext()) {
			RegionRecord existing = it.next();
			if (regionKey(existing).equals(key)) {
				it.remove();
				break;
			}
		}
		state.pending.addFirst(region);
	}

	private static void tickSendQueues() {
		if (server == null || playerStates.isEmpty()) {
			return;
		}
		boolean regionCapEnabled = SyncConfig.maxRegionsPerSecond > 0;
		boolean newSecond = regionCapEnabled && server.getTickCount() % 20 == 0;
		int budgetPerTick = Math.max(4096, SyncConfig.maxBytesPerSecond / 20);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			PlayerSyncState state = playerStates.get(player.getUUID());
			if (state == null) {
				continue;
			}
			synchronized (state) {
				if (state.pending.isEmpty()) {
					clearSendProgress(state);
					continue;
				}
				if (newSecond) {
					state.regionsSentThisSecond = 0;
				}
				int budget = budgetPerTick;
				while (budget > 0 && !state.pending.isEmpty()) {
					if (regionCapEnabled && state.regionsSentThisSecond >= SyncConfig.maxRegionsPerSecond) {
						break;
					}
					RegionRecord region = state.pending.peekFirst();
					String key = regionKey(region);
					if (!key.equals(state.sendRegionKey) || !region.hash.equals(state.sendRegionHash)) {
						state.sendRegionKey = key;
						state.sendRegionHash = region.hash;
						state.sendPartIndex = 0;
					}
					int parts = Math.max(1, (region.zip.length + PART_SIZE - 1) / PART_SIZE);
					boolean sentPartThisTick = false;
					while (state.sendPartIndex < parts) {
						int from = state.sendPartIndex * PART_SIZE;
						int to = Math.min(region.zip.length, from + PART_SIZE);
						int partCost = (to - from) + 96;
						// Always send at least one part per region attempt so an oversized
						// head can never stall the queue (old bug: break when zip > budget
						// && pending.size() > 1 left pending forever for ~83 KiB avg zips).
						if (sentPartThisTick && partCost > budget) {
							break;
						}
						byte[] slice = new byte[to - from];
						System.arraycopy(region.zip, from, slice, 0, slice.length);
						ServerPlayNetworking.send(player, new RegionDataPayload(
								region.dimension, region.regionX, region.regionZ, region.hash,
								state.sendPartIndex, parts, slice));
						state.sendPartIndex++;
						budget -= partCost;
						sentPartThisTick = true;
						if (budget <= 0 && state.sendPartIndex < parts) {
							break;
						}
					}
					if (state.sendPartIndex < parts) {
						break;
					}
					state.pending.removeFirst();
					state.pendingKeys.remove(key);
					state.known.put(key, region.hash);
					state.sentRegions++;
					state.regionsSentThisSecond++;
					clearSendProgress(state);
					if (state.sentRegions <= 5 || state.sentRegions % 25 == 0) {
						VoxelMapSync.LOGGER.info(
								"VoxelMapSync sent region {} to {} ({} bytes, {} parts, pending={}, totalSent={})",
								key, player.getGameProfile().name(), region.zip.length, parts,
								state.pending.size(), state.sentRegions);
					}
				}
			}
		}
	}

	private static void clearSendProgress(PlayerSyncState state) {
		state.sendRegionKey = null;
		state.sendRegionHash = null;
		state.sendPartIndex = 0;
	}

	private static String regionKey(RegionRecord region) {
		return region.dimension + "|" + region.regionX + "," + region.regionZ;
	}

	/**
	 * Called by Sieged Empires (soft compat) when town claims change.
	 */
	public static void requestClaimsRefresh() {
		broadcastClaims();
	}

	private static boolean refreshClaimsSnapshot() {
		String json = SiegedEmpiresClaimsBridge.buildClaimsJson();
		String hash = sha256(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		if (hash.equals(lastClaimsHash)) {
			return false;
		}
		lastClaimsJson = json;
		lastClaimsHash = hash;
		if (SyncConfig.webmapAutoExportTowns) {
			WebMapExporter.exportTowns(json);
		}
		int townCount = countTownsInClaimsJson(json);
		VoxelMapSync.LOGGER.info("VoxelMapSync claims snapshot updated — {} town(s), {} bytes",
				townCount, json.length());
		return true;
	}

	private static int countTownsInClaimsJson(String json) {
		if (json == null || json.isBlank() || "[]".equals(json.trim())) {
			return 0;
		}
		int count = 0;
		for (int i = 0; i < json.length(); i++) {
			if (json.charAt(i) == '{') {
				count++;
			}
		}
		return count;
	}

	private static void sendClaimsToPlayer(ServerPlayer player, boolean force) {
		if (!SyncConfig.claimsEnabled || server == null || player == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (!force) {
			Long lastSent = lastClaimsSentToPlayerMs.get(player.getUUID());
			if (lastSent != null && now - lastSent < SyncConfig.claimsResendIntervalMs) {
				return;
			}
		}
		refreshClaimsSnapshot();
		ServerPlayNetworking.send(player, new ClaimsPayload(lastClaimsJson));
		lastClaimsSentToPlayerMs.put(player.getUUID(), now);
		VoxelMapSync.LOGGER.debug("Sent claims snapshot to {} ({} bytes)",
				player.getGameProfile().name(), lastClaimsJson.length());
	}

	private static void broadcastClaims() {
		if (!SyncConfig.claimsEnabled || server == null) {
			return;
		}
		if (!refreshClaimsSnapshot()) {
			return;
		}
		if ("[]".equals(lastClaimsJson.trim()) && SiegedEmpiresClaimsBridge.isAvailable()) {
			VoxelMapSync.LOGGER.debug("Claims snapshot is empty — Sieged Empires present but no towns indexed yet");
		}
		ClaimsPayload payload = new ClaimsPayload(lastClaimsJson);
		long now = System.currentTimeMillis();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, payload);
			lastClaimsSentToPlayerMs.put(player.getUUID(), now);
		}
		VoxelMapSync.LOGGER.debug("Broadcast claims snapshot ({} bytes)", lastClaimsJson.length());
	}

	private static void maybeInvalidateCacheFormat() {
		if (cacheRoot == null) {
			return;
		}
		Path marker = cacheRoot.resolve(".format");
		int existing = -1;
		if (Files.isRegularFile(marker)) {
			try {
				existing = Integer.parseInt(Files.readString(marker).trim());
			} catch (Exception ignored) {
			}
		}
		if (existing == VoxelMapZipWriter.FORMAT_VERSION) {
			return;
		}
		VoxelMapSync.LOGGER.info(
				"VoxelMapSync cache format {} → {} — clearing {}",
				existing, VoxelMapZipWriter.FORMAT_VERSION, cacheRoot.toAbsolutePath());
		try {
			if (Files.isDirectory(cacheRoot)) {
				try (Stream<Path> walk = Files.walk(cacheRoot)) {
					walk.sorted(java.util.Comparator.reverseOrder())
							.filter(p -> !p.equals(cacheRoot))
							.forEach(p -> {
								try {
									Files.deleteIfExists(p);
								} catch (IOException ignored) {
								}
							});
				}
			}
			Files.createDirectories(cacheRoot);
			Files.writeString(marker, Integer.toString(VoxelMapZipWriter.FORMAT_VERSION));
		} catch (IOException e) {
			VoxelMapSync.LOGGER.warn("Failed to reset VoxelMapSync cache", e);
		}
		regions.clear();
		mcaMtimes.clear();
	}

	private static boolean hasBackingMca(String dim, int regionX, int regionZ) {
		int mcaX = Math.floorDiv(regionX, 2);
		int mcaZ = Math.floorDiv(regionZ, 2);
		Path mca = resolveMcaPath(dim, mcaX, mcaZ);
		if (mca == null || !Files.isRegularFile(mca)) {
			return false;
		}
		try {
			return Files.size(mca) > 0;
		} catch (IOException e) {
			return false;
		}
	}

	private static Path resolveMcaPath(String dim, int mcaX, int mcaZ) {
		ServerLevel level = findLevel(dim);
		if (level == null) {
			return null;
		}
		Path regionDir = resolveRegionDir(level);
		if (regionDir == null) {
			return null;
		}
		return regionDir.resolve("r." + mcaX + "." + mcaZ + ".mca");
	}

	private static ServerLevel findLevel(String dimStorageName) {
		if (server == null) {
			return null;
		}
		String want = normalizeDimension(dimStorageName);
		for (ServerLevel level : server.getAllLevels()) {
			if (dimensionStorageName(level.dimension()).equals(want)) {
				return level;
			}
		}
		return null;
	}

	/** Match server cache / payload dimension keys. */
	static String normalizeDimension(String raw) {
		if (raw == null || raw.isBlank()) {
			return "overworld";
		}
		String scrubbed = raw.replace("~colon~", ":");
		if (scrubbed.startsWith("minecraft:")) {
			return scrubbed.substring("minecraft:".length());
		}
		return scrubbed;
	}

	private static Path resolveRegionDir(ServerLevel level) {
		Path root = level.getServer().getWorldPath(LevelResource.ROOT);
		ResourceKey<Level> dim = level.dimension();
		if (dim.equals(Level.OVERWORLD)) {
			Path modern = root.resolve("dimensions").resolve(dim.identifier().getNamespace()).resolve(dim.identifier().getPath()).resolve("region");
			if (Files.isDirectory(modern)) {
				return modern;
			}
			return root.resolve("region");
		}
		Path modern = root.resolve("dimensions").resolve(dim.identifier().getNamespace()).resolve(dim.identifier().getPath()).resolve("region");
		if (Files.isDirectory(modern)) {
			return modern;
		}
		if (dim.equals(Level.NETHER)) {
			return root.resolve("DIM-1").resolve("region");
		}
		if (dim.equals(Level.END)) {
			return root.resolve("DIM1").resolve("region");
		}
		return modern;
	}

	private static String dimensionStorageName(ResourceKey<Level> dim) {
		var id = dim.identifier();
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

	private record RegionRecord(
			String dimension,
			int regionX,
			int regionZ,
			String hash,
			byte[] zip,
			int fullChunks,
			float sentinelFraction
	) {
	}

	private record PriorityConvertJob(String dim, int mcaX, int mcaZ, UUID requester, String key) {
	}

	private record PendingCacheZip(String dim, int regionX, int regionZ, Path path) {
	}

	private static final class PlayerSyncState {
		final Map<String, String> known = new ConcurrentHashMap<>();
		/** Hashes the client reported on hello / after upload — not overwritten by outbound sends. */
		final Map<String, String> clientReported = new ConcurrentHashMap<>();
		final Deque<RegionRecord> pending = new ArrayDeque<>();
		final Set<String> pendingKeys = ConcurrentHashMap.newKeySet();
		int sentRegions;
		int regionsSentThisSecond;
		long lastRequestSeq;
		/** Multipart drip: which region/part is in flight across ticks. */
		String sendRegionKey;
		String sendRegionHash;
		int sendPartIndex;
	}

	private static final class UploadPartBuffer {
		final byte[][] parts;
		final String hash;
		int received;

		UploadPartBuffer(int count, String hash) {
			this.parts = new byte[count][];
			this.hash = hash;
		}
	}
}
