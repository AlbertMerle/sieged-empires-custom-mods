# VoxelMapSync

Fabric companion addon for **stock VoxelMap** (does not redistribute a modified VoxelMap).

## What it does

1. **Universal terrain map** — Server scans Chunky/explored `region/*.mca` every **10 seconds** (configurable), converts dirty areas into VoxelMap-compatible `.zip` cache regions, and syncs them to clients on join + after each convert cycle.
2. **Sieged Empires claims** — Soft-deps on `siegedempires`. Broadcasts town claim chunks + flag colors + names; client draws fills/borders/labels on the VoxelMap world map.
3. **Web Map Tile & Claims Exporter** — Converts terrain into lightweight 256×256 PNG tiles and multi-level quadtree pyramid overviews (`config/voxelmapsync/webmap/tiles/`) with live `towns.json`, `metadata.json`, and an interactive Leaflet.js web viewer for browsers.

## Install

- Client: `voxelmap` + `voxelmapsync`
- Server: `voxelmap` (settings) + `voxelmapsync` (+ `siegedempires` for claims)

Config (auto-created): `config/voxelmapsync.properties`

| Key | Default | Meaning |
|-----|---------|---------|
| `syncIntervalSeconds` | `10` | Convert/broadcast interval (min 5; not every tick) |
| `maxRegionsPerCycle` | `98` | Server MCA→cache convert cap per cycle (not player send rate) |
| `maxRegionsPerSecond` | `0` | Optional per-player region count cap; `0` = off (use bytes only) |
| `maxBytesPerSecond` | `524288` | Per-player send cap (512 KiB/s) on join/delta sync |
| `autoConvert` | `true` | MCA → cache on interval |
| `syncOnJoin` | `true` | Push known regions when a player joins |
| `claimsEnabled` | `true` | Sieged Empires overlay |
| `claimFillOpacity` | `0.45` | Fill alpha |
| `showClaimLabels` | `true` | Town/empire name labels scaled to claim size |
| `showClaimFlags` | `true` | Town banner flag at claim centroid |
| `tiles-per-second` | `16` | Client cache tiles written per second; lower this if map updates cause client hitches |
| `webmapEnabled` | `true` | Export web map tiles and data to `config/voxelmapsync/webmap` |
| `webmapExportPath` | `config/voxelmapsync/webmap` | Directory for tiles, towns.json, metadata.json, and index.html |
| `webmapMaxZoomOut` | `5` | Quadtree pyramid overview levels (-1 to -5, covering 30k world) |
| `webmapWorldSize` | `30000` | World width/height in blocks |
| `webmapAutoExportTowns` | `true` | Auto-export `towns.json` on claim changes |

Client cache written to: VoxelMap's own `voxelmap/cache/<scrubbed-server-ip>/…` (must use VoxelMap `scrubNameFile`, e.g. `localhost~colon~25565`)  
Server cache: `<world>/voxelmapsync_cache/`  
Web map output: `config/voxelmapsync/webmap/`

## Commands (op / permission level 2+)

| Command | Effect |
|---------|--------|
| `/mapsync` or `/mapsync status` | Cache size, full render/web render status, convert busy flag, pending sends |
| `/mapsync render` | Scan **all** dimension `region/*.mca` files and convert finished terrain into `voxelmapsync_cache` (background; skips settle; quality gates kept). New/updated tiles are priority-queued to online players under `maxBytesPerSecond`. |
| `/mapsync rerender` | Same scan, but **force-overwrites** every existing cache tile that still passes quality gates and **force-resends each tile as soon as its MCA finishes** (not only after the whole-world scan) to online players, still capped at `maxBytesPerSecond`. Players joining during/after the scan receive the authoritative converted cache through normal join sync. Runs without artificial batch sleeps when nobody is online; with players online it spaces MCA conversions evenly to avoid tick spikes. Client applies/reloads are coalesced per region and invalidate VoxelMap's region map, pool, and cached viewport so replacement zips appear even while the World Map stays open. |
| `/mapsync render webmap` | Renders Web Map PNG tiles from existing VoxelMapSync cache and MCA (skips already rendered up-to-date PNG tiles) and builds pyramid overviews. |
| `/mapsync rerender webmap` | **Force re-renders all** Web Map PNG tiles from VoxelMapSync cache and MCA (overwrites existing PNGs) and rebuilds full pyramid overview downscales. |
| `/mapsync stop` | **Cancels active and queued** full render, rerender, fix, region render, and webmap tasks; interrupts pyramid generation; clears priority converts and every player's pending multipart send buffer. Automatic interval sync skips the interrupted pass and resumes on a later interval. |

Build: `./gradlew build` → `build/libs/voxelmapsync-1.0.0.jar`
