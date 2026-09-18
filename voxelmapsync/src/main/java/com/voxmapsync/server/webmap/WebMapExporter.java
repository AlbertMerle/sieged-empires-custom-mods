package com.voxmapsync.server.webmap;

import com.voxmapsync.VoxelMapSync;
import com.voxmapsync.config.SyncConfig;
import com.voxmapsync.server.mca.McaReader;
import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Manages export of web map tiles, towns.json, metadata.json, and the interactive viewer.
 */
public final class WebMapExporter {
	private static Path webmapDir;

	private WebMapExporter() {
	}

	public static void init() {
		if (!SyncConfig.webmapEnabled) {
			return;
		}
		try {
			Path dir = getWebmapDir();
			Files.createDirectories(dir.resolve("tiles").resolve("overworld").resolve("0"));
			Path townsFile = dir.resolve("towns.json");
			if (!Files.exists(townsFile)) {
				Files.writeString(townsFile, "[]", StandardCharsets.UTF_8);
			}
			writeMetadata();
			writeIndexHtml();
			VoxelMapSync.LOGGER.info("WebMap exporter initialized at {}", dir.toAbsolutePath());
		} catch (Exception e) {
			VoxelMapSync.LOGGER.error("Failed to initialize WebMap directory", e);
		}
	}

	public static Path getWebmapDir() {
		if (webmapDir == null) {
			String exportPathStr = SyncConfig.webmapExportPath;
			if (exportPathStr == null || exportPathStr.isBlank()) {
				exportPathStr = "config/voxelmapsync/webmap";
			}
			Path path = Path.of(exportPathStr);
			if (!path.isAbsolute()) {
				path = FabricLoader.getInstance().getGameDir().resolve(path);
			}
			webmapDir = path;
		}
		return webmapDir;
	}

	public static void exportTile(String dimension, int regionX, int regionZ, List<McaReader.ChunkSurface> chunks) {
		if (!SyncConfig.webmapEnabled || chunks == null || chunks.isEmpty()) {
			return;
		}
		try {
			String dimClean = sanitizeDim(dimension);
			Path dimDir = getWebmapDir().resolve("tiles").resolve(dimClean);
			Path zoom0Dir = dimDir.resolve("0");
			Files.createDirectories(zoom0Dir);

			BufferedImage tileImg = WebTileRenderer.renderTile(regionX, regionZ, chunks);
			Path tileFile = zoom0Dir.resolve(regionX + "_" + regionZ + ".png");
			ImageIO.write(tileImg, "PNG", tileFile.toFile());

			if (SyncConfig.webmapMaxZoomOut > 0) {
				WebPyramidDownscaler.updatePyramid(dimDir, regionX, regionZ, tileImg, SyncConfig.webmapMaxZoomOut);
			}
		} catch (Exception e) {
			VoxelMapSync.LOGGER.warn("Failed to export web tile {},{} dim={}", regionX, regionZ, dimension, e);
		}
	}

	public static boolean exportTileFromZip(String dimension, int regionX, int regionZ, byte[] zipBytes) {
		if (zipBytes == null || zipBytes.length == 0) {
			return false;
		}
		try {
			String dimClean = sanitizeDim(dimension);
			Path dimDir = getWebmapDir().resolve("tiles").resolve(dimClean);
			Path zoom0Dir = dimDir.resolve("0");
			Files.createDirectories(zoom0Dir);

			BufferedImage tileImg = WebTileRenderer.renderTileFromZip(zipBytes);
			if (tileImg == null) {
				return false;
			}
			Path tileFile = zoom0Dir.resolve(regionX + "_" + regionZ + ".png");
			ImageIO.write(tileImg, "PNG", tileFile.toFile());
			return true;
		} catch (Exception e) {
			VoxelMapSync.LOGGER.warn("Failed to export web tile from zip {},{} dim={}", regionX, regionZ, dimension, e);
			return false;
		}
	}

	public static boolean exportTileFromZipFile(String dimension, int regionX, int regionZ, Path zipPath) {
		if (zipPath == null || !Files.exists(zipPath)) {
			return false;
		}
		try {
			String dimClean = sanitizeDim(dimension);
			Path dimDir = getWebmapDir().resolve("tiles").resolve(dimClean);
			Path zoom0Dir = dimDir.resolve("0");
			Files.createDirectories(zoom0Dir);

			BufferedImage tileImg = WebTileRenderer.renderTileFromZipFile(zipPath);
			if (tileImg == null) {
				return false;
			}
			Path tileFile = zoom0Dir.resolve(regionX + "_" + regionZ + ".png");
			ImageIO.write(tileImg, "PNG", tileFile.toFile());

			if (SyncConfig.webmapMaxZoomOut > 0) {
				WebPyramidDownscaler.updatePyramid(dimDir, regionX, regionZ, tileImg, SyncConfig.webmapMaxZoomOut);
			}
			return true;
		} catch (Exception e) {
			VoxelMapSync.LOGGER.warn("Failed to export web tile from zip file {},{} dim={}", regionX, regionZ, dimension, e);
			return false;
		}
	}

	public static void rebuildPyramids() {
		try {
			Path tilesDir = getWebmapDir().resolve("tiles");
			if (Files.isDirectory(tilesDir)) {
				try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(tilesDir)) {
					for (Path dimDir : stream) {
						if (Files.isDirectory(dimDir)) {
							WebPyramidDownscaler.rebuildFullPyramid(dimDir, SyncConfig.webmapMaxZoomOut);
						}
					}
				}
			}
			writeMetadata();
		} catch (Exception e) {
			VoxelMapSync.LOGGER.warn("Failed to rebuild web map pyramids", e);
		}
	}

	public static void exportTowns(String claimsJson) {
		if (!SyncConfig.webmapEnabled || !SyncConfig.webmapAutoExportTowns || claimsJson == null) {
			return;
		}
		try {
			Path townsFile = getWebmapDir().resolve("towns.json");
			Files.createDirectories(townsFile.getParent());
			Files.writeString(townsFile, claimsJson, StandardCharsets.UTF_8);
			writeMetadata();
		} catch (Exception e) {
			VoxelMapSync.LOGGER.warn("Failed to export towns.json for web map", e);
		}
	}

	private static void writeMetadata() {
		try {
			Path metaFile = getWebmapDir().resolve("metadata.json");
			String json = "{\n"
					+ "  \"worldSize\": " + SyncConfig.webmapWorldSize + ",\n"
					+ "  \"minZoom\": -" + SyncConfig.webmapMaxZoomOut + ",\n"
					+ "  \"maxZoom\": 0,\n"
					+ "  \"tileSize\": 256,\n"
					+ "  \"tileFormat\": \"png\",\n"
					+ "  \"dimensions\": [\"overworld\"],\n"
					+ "  \"updatedAt\": " + System.currentTimeMillis() + "\n"
					+ "}\n";
			Files.writeString(metaFile, json, StandardCharsets.UTF_8);
		} catch (Exception ignored) {
		}
	}

	private static String sanitizeDim(String dimension) {
		if (dimension == null) return "overworld";
		String d = dimension.replace("minecraft:", "").replace("minecraft~colon~", "").replace(":", "_").replace("~colon~", "_");
		return d.isBlank() ? "overworld" : d;
	}

	private static void writeIndexHtml() {
		try {
			Path indexPath = getWebmapDir().resolve("index.html");
			Files.writeString(indexPath, VIEWER_HTML, StandardCharsets.UTF_8);
		} catch (Exception e) {
			VoxelMapSync.LOGGER.warn("Failed to write web map index.html", e);
		}
	}

	private static final String VIEWER_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
	<meta charset="UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1.0">
	<title>Sieged Empires — World Map</title>
	<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
	<style>
		* { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
		body, html { width: 100%; height: 100%; overflow: hidden; background: #0c0d12; color: #e2e8f0; }
		#map { width: 100%; height: 100%; background: #08090c; }
		
		/* Top Header & Search */
		.header-panel {
			position: absolute; top: 16px; left: 16px; z-index: 1000;
			display: flex; gap: 10px; align-items: center;
		}
		.search-box {
			position: relative; width: 280px;
		}
		.search-input {
			width: 100%; height: 40px; padding: 0 14px;
			background: rgba(18, 22, 32, 0.88); backdrop-filter: blur(8px);
			border: 1px solid rgba(255, 255, 255, 0.15); border-radius: 8px;
			color: #fff; font-size: 14px; outline: none; transition: border-color 0.2s;
		}
		.search-input:focus { border-color: #eab308; box-shadow: 0 0 0 2px rgba(234, 179, 8, 0.25); }
		.search-results {
			position: absolute; top: 46px; left: 0; width: 100%; max-height: 280px; overflow-y: auto;
			background: rgba(15, 19, 28, 0.95); border: 1px solid rgba(255,255,255,0.15); border-radius: 8px;
			display: none; box-shadow: 0 10px 25px rgba(0,0,0,0.5);
		}
		.search-item {
			padding: 10px 14px; cursor: pointer; border-bottom: 1px solid rgba(255,255,255,0.05);
			display: flex; justify-content: space-between; align-items: center; font-size: 13px;
		}
		.search-item:hover { background: rgba(234, 179, 8, 0.15); color: #eab308; }
		.search-item .sub { font-size: 11px; opacity: 0.6; }

		/* Control Panel */
		.layer-controls {
			position: absolute; top: 16px; right: 16px; z-index: 1000;
			background: rgba(18, 22, 32, 0.88); backdrop-filter: blur(8px);
			border: 1px solid rgba(255, 255, 255, 0.15); border-radius: 8px;
			padding: 10px 14px; font-size: 13px; display: flex; flex-direction: column; gap: 8px;
		}
		.control-item { display: flex; align-items: center; gap: 8px; cursor: pointer; user-select: none; }
		.control-item input { accent-color: #eab308; cursor: pointer; }

		/* Bottom Status Bar */
		.coords-bar {
			position: absolute; bottom: 16px; right: 16px; z-index: 1000;
			background: rgba(18, 22, 32, 0.85); backdrop-filter: blur(6px);
			border: 1px solid rgba(255, 255, 255, 0.12); border-radius: 6px;
			padding: 6px 12px; font-size: 12px; font-family: monospace; color: #cbd5e1;
		}
		.brand-badge {
			position: absolute; bottom: 16px; left: 16px; z-index: 1000;
			background: rgba(18, 22, 32, 0.85); backdrop-filter: blur(6px);
			border: 1px solid rgba(255, 255, 255, 0.12); border-radius: 6px;
			padding: 6px 12px; font-size: 12px; color: #94a3b8; font-weight: 500;
		}
		.brand-badge span { color: #eab308; font-weight: 600; }

		/* Popup Custom Card */
		.leaflet-popup-content-wrapper {
			background: rgba(15, 20, 30, 0.95); backdrop-filter: blur(10px);
			border: 1px solid rgba(255,255,255,0.2); border-radius: 10px; color: #e2e8f0;
			box-shadow: 0 12px 30px rgba(0,0,0,0.6);
		}
		.leaflet-popup-tip { background: rgba(15, 20, 30, 0.95); }
		.town-card { padding: 4px; }
		.town-card-title { font-size: 16px; font-weight: 700; color: #facc15; margin-bottom: 2px; }
		.town-card-empire { font-size: 12px; color: #93c5fd; font-weight: 500; margin-bottom: 8px; }
		.town-card-row { display: flex; justify-content: space-between; font-size: 12px; padding: 3px 0; border-top: 1px solid rgba(255,255,255,0.08); }
		.town-card-row .label { opacity: 0.6; }
		.town-card-row .val { font-weight: 600; }

		/* Town label tooltip */
		.town-map-label {
			background: rgba(10, 13, 20, 0.75); border: 1px solid rgba(255,255,255,0.2);
			border-radius: 4px; color: #fff; font-size: 12px; font-weight: 600; padding: 2px 6px;
			box-shadow: 0 2px 6px rgba(0,0,0,0.4); pointer-events: none; text-align: center;
		}
	</style>
</head>
<body>
	<div id="map"></div>

	<div class="header-panel">
		<div class="search-box">
			<input type="text" id="searchInput" class="search-input" placeholder="Search town or X, Z...">
			<div id="searchResults" class="search-results"></div>
		</div>
	</div>

	<div class="layer-controls">
		<label class="control-item">
			<input type="checkbox" id="toggleClaims" checked> Town Claims
		</label>
		<label class="control-item">
			<input type="checkbox" id="toggleLabels" checked> Town Names
		</label>
		<label class="control-item">
			<input type="checkbox" id="toggleFlags" checked> Town Flags
		</label>
	</div>

	<div class="brand-badge"><span>Sieged Empires</span> World Map</div>
	<div id="coordsBar" class="coords-bar">X: 0 | Z: 0</div>

	<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
	<script>
		// Setup CRS.Simple map where 1 unit = 1 block at zoom 0
		const map = L.map('map', {
			crs: L.CRS.Simple,
			minZoom: -5,
			maxZoom: 2,
			zoomControl: false,
			attributionControl: false
		}).setView([0, 0], -3);

		L.control.zoom({ position: 'bottomleft' }).addTo(map);

		// Tile Layer mapping to /tiles/overworld/{z}/{x}_{z}.png
		const MCMapTileLayer = L.TileLayer.extend({
			getTileUrl: function(coords) {
				const z = coords.z;
				const x = coords.x;
				const y = coords.y; // Leaflet vertical index is inverted relative to standard Minecraft Z
				return `tiles/overworld/${z}/${x}_${y}.png`;
			}
		});

		const tileLayer = new MCMapTileLayer('', {
			tileSize: 256,
			noWrap: true,
			bounds: [[-15000, -15000], [15000, 15000]],
			minZoom: -5,
			maxZoom: 0,
			errorTileUrl: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII='
		}).addTo(map);

		// Town Layers
		const claimsGroup = L.layerGroup().addTo(map);
		const labelsGroup = L.layerGroup().addTo(map);
		const flagsGroup = L.layerGroup().addTo(map);

		let townsData = [];

		// Load metadata & towns
		fetch('metadata.json?' + Date.now()).then(r => r.json()).then(meta => {
			if (meta.minZoom) tileLayer.options.minZoom = meta.minZoom;
		}).catch(() => {});

		function loadTowns() {
			fetch('towns.json?' + Date.now()).then(r => r.json()).then(towns => {
				townsData = towns;
				renderTowns(towns);
			}).catch(() => {});
		}
		loadTowns();
		setInterval(loadTowns, 30000);

		function renderTowns(towns) {
			claimsGroup.clearLayers();
			labelsGroup.clearLayers();
			flagsGroup.clearLayers();

			towns.forEach(town => {
				if (!town.chunks || !town.chunks.length) return;
				if (town.dimension && town.dimension !== 'minecraft:overworld' && town.dimension !== 'overworld') return;

				const claimedSet = new Set(town.chunks.map(c => `${c[0]},${c[1]}`));
				const segments = [];
				let sumX = 0, sumZ = 0, count = 0;
				let minX = Infinity, minZ = Infinity, maxX = -Infinity, maxZ = -Infinity;

				// Draw each chunk fill & extract merged outer perimeter border segments
				const chunkPolys = [];
				town.chunks.forEach(([cx, cz]) => {
					const x1 = cx * 16;
					const z1 = cz * 16;
					const x2 = x1 + 16;
					const z2 = z1 + 16;

					sumX += x1 + 8;
					sumZ += z1 + 8;
					count++;

					minX = Math.min(minX, x1); minZ = Math.min(minZ, z1);
					maxX = Math.max(maxX, x2); maxZ = Math.max(maxZ, z2);

					// In Leaflet CRS.Simple: Lat = -Z, Lng = X
					chunkPolys.push([
						[-z1, x1], [-z1, x2], [-z2, x2], [-z2, x1]
					]);

					// Outer edges only
					if (!claimedSet.has(`${cx},${cz - 1}`)) segments.push([[-z1, x1], [-z1, x2]]);
					if (!claimedSet.has(`${cx},${cz + 1}`)) segments.push([[-z2, x1], [-z2, x2]]);
					if (!claimedSet.has(`${cx - 1},${cz}`)) segments.push([[-z1, x1], [-z2, x1]]);
					if (!claimedSet.has(`${cx + 1},${cz}`)) segments.push([[-z1, x2], [-z2, x2]]);
				});

				const colorHex = '#' + (town.color != null ? (town.color & 0xFFFFFF).toString(16).padStart(6, '0') : 'eab308');
				
				// Draw Fill
				const fillLayer = L.polygon(chunkPolys, {
					color: 'transparent',
					fillColor: colorHex,
					fillOpacity: 0.35,
					weight: 0
				}).addTo(claimsGroup);

				// Draw Merged Outer Border Lines
				const borderLayer = L.polyline(segments, {
					color: colorHex,
					weight: 2,
					opacity: 0.95
				}).addTo(claimsGroup);

				// Centroid in Minecraft coords
				const centerX = Math.round(sumX / count);
				const centerZ = Math.round(sumZ / count);
				const centerLatLng = [-centerZ, centerX];

				// Popup Card
				const popupContent = `
					<div class="town-card">
						<div class="town-card-title">${escapeHtml(town.name || 'Unknown Town')}</div>
						${town.empire ? `<div class="town-card-empire">👑 Empire of ${escapeHtml(town.empire)}</div>` : ''}
						<div class="town-card-row"><span class="label">Monarch</span><span class="val">${escapeHtml(town.monarch || 'None')}</span></div>
						<div class="town-card-row"><span class="label">Claimed Chunks</span><span class="val">${town.chunks.length} chunks</span></div>
						<div class="town-card-row"><span class="label">Area</span><span class="val">${(town.chunks.length * 256).toLocaleString()} m²</span></div>
						<div class="town-card-row"><span class="label">Location</span><span class="val">X: ${centerX}, Z: ${centerZ}</span></div>
					</div>
				`;
				fillLayer.bindPopup(popupContent);
				borderLayer.bindPopup(popupContent);

				// Town Name Label
				const labelMarker = L.marker(centerLatLng, {
					icon: L.divIcon({
						className: 'town-map-label',
						html: `<div>${escapeHtml(town.name || '')}</div>`,
						iconSize: [120, 24],
						iconAnchor: [60, 12]
					})
				}).addTo(labelsGroup);
				labelMarker.bindPopup(popupContent);

				// Town Banner Flag Icon (SVG marker)
				const flagIcon = L.divIcon({
					className: 'town-flag-icon',
					html: `
						<svg width="24" height="32" viewBox="0 0 24 32">
							<line x1="4" y1="2" x2="4" y2="30" stroke="#cbd5e1" stroke-width="2" />
							<rect x="5" y="4" width="16" height="20" fill="${colorHex}" stroke="#000" stroke-width="1" rx="1" />
							<polygon points="5,4 17,4 13,14 17,24 5,24" fill="rgba(255,255,255,0.25)" />
						</svg>
					`,
					iconSize: [24, 32],
					iconAnchor: [4, 30]
				});
				const flagMarker = L.marker(centerLatLng, { icon: flagIcon }).addTo(flagsGroup);
				flagMarker.bindPopup(popupContent);
			});
		}

		// Cursor Coordinates
		map.on('mousemove', e => {
			const x = Math.round(e.latlng.lng);
			const z = Math.round(-e.latlng.lat);
			document.getElementById('coordsBar').textContent = `X: ${x.toLocaleString()} | Z: ${z.toLocaleString()}`;
		});

		// Layer Toggles
		document.getElementById('toggleClaims').addEventListener('change', e => {
			if (e.target.checked) map.addLayer(claimsGroup); else map.removeLayer(claimsGroup);
		});
		document.getElementById('toggleLabels').addEventListener('change', e => {
			if (e.target.checked) map.addLayer(labelsGroup); else map.removeLayer(labelsGroup);
		});
		document.getElementById('toggleFlags').addEventListener('change', e => {
			if (e.target.checked) map.addLayer(flagsGroup); else map.removeLayer(flagsGroup);
		});

		// Search functionality
		const searchInput = document.getElementById('searchInput');
		const searchResults = document.getElementById('searchResults');

		searchInput.addEventListener('input', () => {
			const q = searchInput.value.trim().toLowerCase();
			if (!q) { searchResults.style.display = 'none'; return; }

			// Coordinates match (e.g. 100, -200 or /tp 100 64 -200)
			const coordMatch = q.match(/(-?\\d+)[,\\s]+(?:\\d+[,\\s]+)?(-?\\d+)/);
			const items = [];

			if (coordMatch) {
				const x = parseInt(coordMatch[1]);
				const z = parseInt(coordMatch[2]);
				items.push({ name: `Coordinates: X ${x}, Z ${z}`, sub: 'Teleport view', x, z });
			}

			townsData.forEach(t => {
				if (t.name && t.name.toLowerCase().includes(q) || (t.monarch && t.monarch.toLowerCase().includes(q)) || (t.empire && t.empire.toLowerCase().includes(q))) {
					let sumX = 0, sumZ = 0;
					t.chunks.forEach(c => { sumX += c[0] * 16 + 8; sumZ += c[1] * 16 + 8; });
					const cx = Math.round(sumX / t.chunks.length);
					const cz = Math.round(sumZ / t.chunks.length);
					items.push({ name: t.name, sub: t.empire ? `Empire of ${t.empire}` : `X: ${cx}, Z: ${cz}`, x: cx, z: cz });
				}
			});

			if (!items.length) { searchResults.style.display = 'none'; return; }

			searchResults.innerHTML = items.map((item, idx) => `
				<div class="search-item" data-idx="${idx}">
					<div><strong>${escapeHtml(item.name)}</strong></div>
					<div class="sub">${escapeHtml(item.sub)}</div>
				</div>
			`).join('');
			searchResults.style.display = 'block';

			searchResults.querySelectorAll('.search-item').forEach(el => {
				el.addEventListener('click', () => {
					const item = items[parseInt(el.dataset.idx)];
					map.flyTo([-item.z, item.x], 0, { duration: 1.2 });
					searchResults.style.display = 'none';
					searchInput.value = item.name;
				});
			});
		});

		document.addEventListener('click', e => {
			if (!searchInput.contains(e.target) && !searchResults.contains(e.target)) {
				searchResults.style.display = 'none';
			}
		});

		function escapeHtml(s) {
			return (s || '').replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
		}
	</script>
</body>
</html>
""";
}
