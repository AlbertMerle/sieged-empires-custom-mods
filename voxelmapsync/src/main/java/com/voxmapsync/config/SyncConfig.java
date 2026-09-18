package com.voxmapsync.config;

import com.voxmapsync.VoxelMapSync;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Server + shared defaults. User can edit {@code config/voxelmapsync.properties}.
 */
public final class SyncConfig {
	public static int syncIntervalSeconds = 10;
	/** MCA → VoxelMap cache work cap per convert cycle (server-side only). */
	public static int maxRegionsPerCycle = 98;
	/** Max complete regions sent to one player per second; 0 = disabled (use maxBytesPerSecond only). */
	public static int maxRegionsPerSecond = 0;
	public static int maxBytesPerSecond = 512 * 1024;
	public static boolean autoConvert = true;
	public static boolean syncOnJoin = true;
	public static boolean claimsEnabled = true;
	public static float claimFillOpacity = 0.45f;
	public static boolean showClaimLabels = true;
	public static boolean showClaimFlags = true;
	/** Min ms between claims resends to the same player during viewport map panning. */
	public static long claimsResendIntervalMs = 3000L;

	/** Accept only {@code minecraft:full} chunks when converting. */
	public static boolean strictFullStatusOnly = true;
	/** Ignore MCA files newer than this many seconds (Chunky mid-write settle). */
	public static int convertSettleSeconds = 60;
	/** Require at least this many full chunks in an MCA (of 1024) before converting. */
	public static int minFullChunksToConvert = 1024;
	/** Reject zip if sentinel empty height fraction exceeds this (0–1). */
	public static float maxSentinelFraction = 0.10f;
	/** Require at least this many explored chunks per VoxelMap region tile (max 256). */
	public static int minChunksPerRegion = 256;
	/** Min fraction of columns populated within each chunk surface (0–1). */
	public static float minColumnCoverageFraction = 0.75f;
	/** Reject tiles that are mostly flat water at sea level (Chunky placeholder junk). */
	public static boolean rejectFlatWaterGarbage = true;
	/** Fraction of explored columns at y=64 water to classify as flat-water garbage. */
	public static float flatWaterRejectFraction = 0.85f;
	/** If false, do not join-queue / keep index of cache zips with no backing MCA. */
	public static boolean syncOrphanCache = false;
	/** If true, delete orphan cache zips on server start. */
	public static boolean pruneOrphanCacheOnStart = false;
	/** Max cache zips read + quality-checked per batch during startup indexing (background worker). */
	public static int maxCacheLoadPerTick = 8;
	/**
	 * Admin background tasks ({@code /mapsync fix}, full render, webmap render): regions checked
	 * per batch before yielding on the worker thread.
	 */
	public static int backgroundTaskRegionsPerBatch = 8;
	/** Pause between admin background task batches (ms) — keeps disk/CPU load low while players are on. */
	public static int backgroundTaskBatchDelayMs = 250;
	/** Max MCA→cache conversions per admin background batch (heavy disk reads). */
	public static int backgroundTaskMaxConvertsPerBatch = 2;

	public static boolean viewportSyncEnabled = true;
	public static int viewportRequestIntervalMs = 750;
	/** Faster request cadence while the player is panning the world map. */
	public static int viewportPanRequestIntervalMs = 250;
	public static int viewportMarginRegions = 2;
	/**
	 * While the World Map is open, re-ask the server about partially explored tiles
	 * (local gaps) at most this often — fills holes when the server has a fuller zip.
	 */
	public static int viewportGapRecheckMs = 15000;
	public static int maxRegionsPerRequest = 32;
	/**
	 * Auto-enqueue missing regions within this Chebyshev distance of the player on hello.
	 * {@code 0} = all missing (distance-sorted). Default {@code 32} to avoid join floods.
	 */
	public static int joinSyncRadiusRegions = 8;
	public static boolean onDemandConvert = true;

	/** Accept client-uploaded VoxelMap tiles when players explore ahead of MCA convert. */
	public static boolean clientUploadEnabled = false;
	/** How often the client scans nearby regions for local changes to upload (ms). */
	public static int clientUploadScanIntervalMs = 5000;
	/** Chebyshev radius around the player for proactive client upload scans. */
	public static int clientUploadRadiusRegions = 2;
	/** Max bytes per second the client may upload to the server. */
	public static int maxClientUploadBytesPerSecond = 256 * 1024;
	/** How often the server asks nearby players to upload stale tiles (seconds). */
	public static int clientUploadRequestIntervalSeconds = 15;
	/** Chebyshev radius for server-initiated upload requests around each player. */
	public static int clientUploadRequestRadiusRegions = 3;

	/** Max region zips written to disk per second on the client (spread VoxelMap reload cost). */
	public static int tilesPerSecond = 16;
	/** Max VoxelMap region invalidations per client tick. */
	public static int clientReloadMaxPerTick = 2;
	/**
	 * While the client apply queue is at least this deep, pause viewport region requests
	 * so downloads do not outrun disk writes and map reloads.
	 */
	public static int clientApplyBackpressureThreshold = 3;
	/** Max cache zip files hashed per second during background verify (0 = unlimited). */
	public static int clientCacheScanMaxPerSecond = 40;
	/**
	 * Send client hello from the on-disk hash manifest immediately on join instead of
	 * blocking until every local zip is re-hashed.
	 */
	public static boolean clientFastJoinHello = true;
	/**
	 * On join hello, only report hashes within this Chebyshev radius of the player.
	 * {@code 0} = report all (can flood the network with large local caches).
	 */
	public static int clientHelloRadiusRegions = 16;
	/** After join, re-hash local zips in the background to refresh the manifest. */
	public static boolean clientBackgroundCacheVerify = false;
	/** Min ms between manifest disk writes while applying regions. */
	public static int clientManifestSaveIntervalMs = 5000;
	/**
	 * Only invalidate VoxelMap in-memory tiles when the World Map is open.
	 * Files are still written immediately; map picks them up when opened.
	 */
	public static boolean clientDeferReloadUntilMapOpen = true;
	/**
	 * While the World Map is open, re-request partially explored tiles with local gaps.
	 * Disabled by default — gap scan is expensive (256 chunk probes per candidate tile).
	 */
	public static boolean viewportGapFillEnabled = false;

	/** Webmap tile export settings */
	public static boolean webmapEnabled = true;
	public static String webmapExportPath = "config/voxelmapsync/webmap";
	public static int webmapMaxZoomOut = 5;
	public static int webmapWorldSize = 30000;
	public static boolean webmapAutoExportTowns = true;

	private SyncConfig() {
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("voxelmapsync.properties");
		Properties props = new Properties();
		if (Files.exists(path)) {
			try (InputStream in = Files.newInputStream(path)) {
				props.load(in);
			} catch (IOException e) {
				VoxelMapSync.LOGGER.warn("Failed to read {}", path, e);
			}
		}

		syncIntervalSeconds = Math.max(5, parseInt(props, "syncIntervalSeconds", syncIntervalSeconds));
		maxRegionsPerCycle = Math.max(1, parseInt(props, "maxRegionsPerCycle", maxRegionsPerCycle));
		maxRegionsPerSecond = Math.max(0, parseInt(props, "maxRegionsPerSecond", maxRegionsPerSecond));
		maxBytesPerSecond = Math.max(64 * 1024, parseInt(props, "maxBytesPerSecond", maxBytesPerSecond));
		autoConvert = parseBool(props, "autoConvert", autoConvert);
		syncOnJoin = parseBool(props, "syncOnJoin", syncOnJoin);
		claimsEnabled = parseBool(props, "claimsEnabled", claimsEnabled);
		claimFillOpacity = Math.clamp(parseFloat(props, "claimFillOpacity", claimFillOpacity), 0.05f, 0.9f);
		showClaimLabels = parseBool(props, "showClaimLabels", showClaimLabels);
		showClaimFlags = parseBool(props, "showClaimFlags", showClaimFlags);
		claimsResendIntervalMs = Math.max(500L, parseLong(props, "claimsResendIntervalMs", claimsResendIntervalMs));

		strictFullStatusOnly = parseBool(props, "strictFullStatusOnly", strictFullStatusOnly);
		convertSettleSeconds = Math.max(0, parseInt(props, "convertSettleSeconds", convertSettleSeconds));
		minFullChunksToConvert = Math.clamp(parseInt(props, "minFullChunksToConvert", minFullChunksToConvert), 1, 1024);
		maxSentinelFraction = Math.clamp(parseFloat(props, "maxSentinelFraction", maxSentinelFraction), 0f, 1f);
		minChunksPerRegion = Math.clamp(parseInt(props, "minChunksPerRegion", minChunksPerRegion), 1, 256);
		minColumnCoverageFraction = Math.clamp(parseFloat(props, "minColumnCoverageFraction", minColumnCoverageFraction), 0.5f, 1f);
		rejectFlatWaterGarbage = parseBool(props, "rejectFlatWaterGarbage", rejectFlatWaterGarbage);
		flatWaterRejectFraction = Math.clamp(parseFloat(props, "flatWaterRejectFraction", flatWaterRejectFraction), 0.5f, 1f);
		syncOrphanCache = parseBool(props, "syncOrphanCache", syncOrphanCache);
		pruneOrphanCacheOnStart = parseBool(props, "pruneOrphanCacheOnStart", pruneOrphanCacheOnStart);
		maxCacheLoadPerTick = Math.max(1, parseInt(props, "maxCacheLoadPerTick", maxCacheLoadPerTick));
		backgroundTaskRegionsPerBatch = Math.max(1, parseInt(props, "backgroundTaskRegionsPerBatch", backgroundTaskRegionsPerBatch));
		backgroundTaskBatchDelayMs = Math.max(0, parseInt(props, "backgroundTaskBatchDelayMs", backgroundTaskBatchDelayMs));
		backgroundTaskMaxConvertsPerBatch = Math.max(1, parseInt(props, "backgroundTaskMaxConvertsPerBatch", backgroundTaskMaxConvertsPerBatch));

		viewportSyncEnabled = parseBool(props, "viewportSyncEnabled", viewportSyncEnabled);
		viewportRequestIntervalMs = Math.max(200, parseInt(props, "viewportRequestIntervalMs", viewportRequestIntervalMs));
		viewportPanRequestIntervalMs = Math.max(100, parseInt(props, "viewportPanRequestIntervalMs", viewportPanRequestIntervalMs));
		viewportMarginRegions = Math.max(0, parseInt(props, "viewportMarginRegions", viewportMarginRegions));
		viewportGapRecheckMs = Math.max(1000, parseInt(props, "viewportGapRecheckMs", viewportGapRecheckMs));
		maxRegionsPerRequest = Math.clamp(parseInt(props, "maxRegionsPerRequest", maxRegionsPerRequest), 1, 256);
		joinSyncRadiusRegions = Math.max(0, parseInt(props, "joinSyncRadiusRegions", joinSyncRadiusRegions));
		onDemandConvert = parseBool(props, "onDemandConvert", onDemandConvert);

		clientUploadEnabled = parseBool(props, "clientUploadEnabled", clientUploadEnabled);
		clientUploadScanIntervalMs = Math.max(1000, parseInt(props, "clientUploadScanIntervalMs", clientUploadScanIntervalMs));
		clientUploadRadiusRegions = Math.max(0, parseInt(props, "clientUploadRadiusRegions", clientUploadRadiusRegions));
		maxClientUploadBytesPerSecond = Math.max(32 * 1024, parseInt(props, "maxClientUploadBytesPerSecond", maxClientUploadBytesPerSecond));
		clientUploadRequestIntervalSeconds = Math.max(5, parseInt(props, "clientUploadRequestIntervalSeconds", clientUploadRequestIntervalSeconds));
		clientUploadRequestRadiusRegions = Math.max(0, parseInt(props, "clientUploadRequestRadiusRegions", clientUploadRequestRadiusRegions));

		tilesPerSecond = Math.max(1, parseInt(props, "tiles-per-second", tilesPerSecond));
		clientReloadMaxPerTick = Math.max(1, parseInt(props, "clientReloadMaxPerTick", clientReloadMaxPerTick));
		clientApplyBackpressureThreshold = Math.max(1, parseInt(props, "clientApplyBackpressureThreshold", clientApplyBackpressureThreshold));
		clientCacheScanMaxPerSecond = Math.max(0, parseInt(props, "clientCacheScanMaxPerSecond", clientCacheScanMaxPerSecond));
		clientFastJoinHello = parseBool(props, "clientFastJoinHello", clientFastJoinHello);
		clientHelloRadiusRegions = Math.max(0, parseInt(props, "clientHelloRadiusRegions", clientHelloRadiusRegions));
		clientBackgroundCacheVerify = parseBool(props, "clientBackgroundCacheVerify", clientBackgroundCacheVerify);
		clientManifestSaveIntervalMs = Math.max(500, parseInt(props, "clientManifestSaveIntervalMs", clientManifestSaveIntervalMs));
		clientDeferReloadUntilMapOpen = parseBool(props, "clientDeferReloadUntilMapOpen", clientDeferReloadUntilMapOpen);
		viewportGapFillEnabled = parseBool(props, "viewportGapFillEnabled", viewportGapFillEnabled);

		webmapEnabled = parseBool(props, "webmapEnabled", webmapEnabled);
		webmapExportPath = parseString(props, "webmapExportPath", webmapExportPath);
		webmapMaxZoomOut = Math.clamp(parseInt(props, "webmapMaxZoomOut", webmapMaxZoomOut), 1, 8);
		webmapWorldSize = Math.max(1000, parseInt(props, "webmapWorldSize", webmapWorldSize));
		webmapAutoExportTowns = parseBool(props, "webmapAutoExportTowns", webmapAutoExportTowns);

		save(path);
	}

	private static void save(Path path) {
		Properties props = new Properties();
		props.setProperty("syncIntervalSeconds", Integer.toString(syncIntervalSeconds));
		props.setProperty("maxRegionsPerCycle", Integer.toString(maxRegionsPerCycle));
		props.setProperty("maxRegionsPerSecond", Integer.toString(maxRegionsPerSecond));
		props.setProperty("maxBytesPerSecond", Integer.toString(maxBytesPerSecond));
		props.setProperty("autoConvert", Boolean.toString(autoConvert));
		props.setProperty("syncOnJoin", Boolean.toString(syncOnJoin));
		props.setProperty("claimsEnabled", Boolean.toString(claimsEnabled));
		props.setProperty("claimFillOpacity", Float.toString(claimFillOpacity));
		props.setProperty("showClaimLabels", Boolean.toString(showClaimLabels));
		props.setProperty("showClaimFlags", Boolean.toString(showClaimFlags));
		props.setProperty("claimsResendIntervalMs", Long.toString(claimsResendIntervalMs));
		props.setProperty("strictFullStatusOnly", Boolean.toString(strictFullStatusOnly));
		props.setProperty("convertSettleSeconds", Integer.toString(convertSettleSeconds));
		props.setProperty("minFullChunksToConvert", Integer.toString(minFullChunksToConvert));
		props.setProperty("maxSentinelFraction", Float.toString(maxSentinelFraction));
		props.setProperty("minChunksPerRegion", Integer.toString(minChunksPerRegion));
		props.setProperty("minColumnCoverageFraction", Float.toString(minColumnCoverageFraction));
		props.setProperty("rejectFlatWaterGarbage", Boolean.toString(rejectFlatWaterGarbage));
		props.setProperty("flatWaterRejectFraction", Float.toString(flatWaterRejectFraction));
		props.setProperty("syncOrphanCache", Boolean.toString(syncOrphanCache));
		props.setProperty("pruneOrphanCacheOnStart", Boolean.toString(pruneOrphanCacheOnStart));
		props.setProperty("maxCacheLoadPerTick", Integer.toString(maxCacheLoadPerTick));
		props.setProperty("backgroundTaskRegionsPerBatch", Integer.toString(backgroundTaskRegionsPerBatch));
		props.setProperty("backgroundTaskBatchDelayMs", Integer.toString(backgroundTaskBatchDelayMs));
		props.setProperty("backgroundTaskMaxConvertsPerBatch", Integer.toString(backgroundTaskMaxConvertsPerBatch));
		props.setProperty("viewportSyncEnabled", Boolean.toString(viewportSyncEnabled));
		props.setProperty("viewportRequestIntervalMs", Integer.toString(viewportRequestIntervalMs));
		props.setProperty("viewportPanRequestIntervalMs", Integer.toString(viewportPanRequestIntervalMs));
		props.setProperty("viewportMarginRegions", Integer.toString(viewportMarginRegions));
		props.setProperty("viewportGapRecheckMs", Integer.toString(viewportGapRecheckMs));
		props.setProperty("maxRegionsPerRequest", Integer.toString(maxRegionsPerRequest));
		props.setProperty("joinSyncRadiusRegions", Integer.toString(joinSyncRadiusRegions));
		props.setProperty("onDemandConvert", Boolean.toString(onDemandConvert));
		props.setProperty("clientUploadEnabled", Boolean.toString(clientUploadEnabled));
		props.setProperty("clientUploadScanIntervalMs", Integer.toString(clientUploadScanIntervalMs));
		props.setProperty("clientUploadRadiusRegions", Integer.toString(clientUploadRadiusRegions));
		props.setProperty("maxClientUploadBytesPerSecond", Integer.toString(maxClientUploadBytesPerSecond));
		props.setProperty("clientUploadRequestIntervalSeconds", Integer.toString(clientUploadRequestIntervalSeconds));
		props.setProperty("clientUploadRequestRadiusRegions", Integer.toString(clientUploadRequestRadiusRegions));
		props.setProperty("tiles-per-second", Integer.toString(tilesPerSecond));
		props.setProperty("clientReloadMaxPerTick", Integer.toString(clientReloadMaxPerTick));
		props.setProperty("clientApplyBackpressureThreshold", Integer.toString(clientApplyBackpressureThreshold));
		props.setProperty("clientCacheScanMaxPerSecond", Integer.toString(clientCacheScanMaxPerSecond));
		props.setProperty("clientFastJoinHello", Boolean.toString(clientFastJoinHello));
		props.setProperty("clientHelloRadiusRegions", Integer.toString(clientHelloRadiusRegions));
		props.setProperty("clientBackgroundCacheVerify", Boolean.toString(clientBackgroundCacheVerify));
		props.setProperty("clientManifestSaveIntervalMs", Integer.toString(clientManifestSaveIntervalMs));
		props.setProperty("clientDeferReloadUntilMapOpen", Boolean.toString(clientDeferReloadUntilMapOpen));
		props.setProperty("viewportGapFillEnabled", Boolean.toString(viewportGapFillEnabled));
		props.setProperty("webmapEnabled", Boolean.toString(webmapEnabled));
		props.setProperty("webmapExportPath", webmapExportPath);
		props.setProperty("webmapMaxZoomOut", Integer.toString(webmapMaxZoomOut));
		props.setProperty("webmapWorldSize", Integer.toString(webmapWorldSize));
		props.setProperty("webmapAutoExportTowns", Boolean.toString(webmapAutoExportTowns));
		try {
			Files.createDirectories(path.getParent());
			try (OutputStream out = Files.newOutputStream(path)) {
				props.store(out, "VoxelMapSync — universal VoxelMap cache + Sieged Empires claims");
			}
		} catch (IOException e) {
			VoxelMapSync.LOGGER.warn("Failed to write {}", path, e);
		}
	}

	private static int parseInt(Properties props, String key, int def) {
		String raw = props.getProperty(key);
		if (raw == null || raw.isBlank()) {
			return def;
		}
		try {
			return Integer.parseInt(raw.trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static long parseLong(Properties props, String key, long def) {
		String raw = props.getProperty(key);
		if (raw == null || raw.isBlank()) {
			return def;
		}
		try {
			return Long.parseLong(raw.trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static float parseFloat(Properties props, String key, float def) {
		String raw = props.getProperty(key);
		if (raw == null || raw.isBlank()) {
			return def;
		}
		try {
			return Float.parseFloat(raw.trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static boolean parseBool(Properties props, String key, boolean def) {
		String raw = props.getProperty(key);
		if (raw == null || raw.isBlank()) {
			return def;
		}
		return Boolean.parseBoolean(raw.trim());
	}

	private static String parseString(Properties props, String key, String def) {
		String raw = props.getProperty(key);
		if (raw == null || raw.isBlank()) {
			return def;
		}
		return raw.trim();
	}
}
