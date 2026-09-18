# VoxelMapSync — Viewport Sync + Broken-Tile Fix Brief

**Audience**: AI / developer working in the **VoxelMapSync source repo** (this document was written from the dedicated server workspace; source was not edited here).  
**Date**: 2026-08-27  
**Installed binary observed**: `mods/voxelmapsync-1.0.0.jar` (`voxelmapsync` 1.0.0, Fabric, MC `~26.2`)  
**Package root**: `com.voxmapsync`  
**Companion client mod**: VoxelMap `voxelmap-fabric-26.2-1.16.8` (`com.mamiyaotaru.voxelmap`)  
**Server world context**: Fabric dedicated server, Terralith/Tectonic, Chunky pregen square radius **15000** around spawn, VoxelMapSync bandwidth capped at **512 KiB/s** per player.

**Two operator problems (both must be fixed):**

1. **Missing tiles** — World Map stays empty for Chunky-pregenerated areas (viewport / demand sync).  
2. **Broken / “dirty” tiles** — map terrain that *does* arrive often looks awful (checkerboard voids, wrong surfaces, flat garbage, unlit patches). Prior attempts to fix this in the mod did not stick.

---

## 1. What the operator wants (acceptance criteria)

Players open VoxelMap’s **World Map** (`GuiPersistentMap`). Areas that look **empty** on that map should be filled **if and only if** the server already has that terrain on disk (Chunky / explored MCA), by converting and sending VoxelMap region cache data to the client.

Requirements:

1. **Viewport-aware**: Only (or primarily) sync regions the player is **currently trying to render** in the map menu (visible / near-visible tiles), not a blind dump of the entire server cache.
2. **Empty detection (client)**: If a visible map tile/region is empty / missing locally, treat it as a candidate to request.
3. **Existence check (server)**: For each requested region, if an MCA already exists under the dimension `region/` dir (Chunky or prior exploration), ensure it is in `voxelmapsync_cache`, then send it. **Do not generate new worldgen** just to fill the map.
4. **Bandwidth**: Stay within configured `maxBytesPerSecond` (production value **`524288` = 512 KiB/s** per player). Prefer prioritizing viewport regions under that budget.
5. **Clean tiles only**: Never convert, cache-replace, or send region zips built from **incomplete / in-progress** chunk data. Map tiles must match finished terrain (`minecraft:full`), not mid-Chunky junk.
6. **Keep working features**: Sieged Empires claim overlay, join hello hash negotiation, MCA→zip convert worker, existing payload framing.

Out of scope unless easy: web maps, Voxy LODs, changing Chunky.

---

## 2. Observed production symptoms

### 2.1 Players do not get “all chunks they should”

On this server, after Chunky has written large amounts of overworld MCA, the world map still shows blank regions that **already exist as `.mca` files** (or exist in `voxelmapsync_cache` but have not reached the client yet for the area being viewed).

### 2.2 Live evidence from server logs

Join / hello is **whole-cache enqueue**, not viewport:

```text
VoxelMapSync waiting for client hello from KingAlbert (3670 region(s) in cache)
VoxelMapSync hello from KingAlbert worldKey=147.135.31.41 known=256 queued=3493 pending=3591
VoxelMapSync hello from KingAlbert ... known=512 queued=0 pending=3591
... batches of known hashes ...
```

Earlier builds logged:

```text
VoxelMapSync join sync → KingAlbert (1815 region(s) queued)
VoxelMapSync hello from KingAlbert worldKey=... known=26 sent=1815
```

Convert worker is **background / dirty-MCA driven**, capped per cycle, not player-viewport driven:

```text
VoxelMapSync dim overworld: scanned 25 MCA, dirty 25, converted 98 (capped)
VoxelMapSync converted 98 region(s); broadcasting to 0 player(s) (cache total=4207)
```

Boot banner (current config):

```text
VoxelMapSync ready — cache=.../world/voxelmapsync_cache (4207 region(s) on disk), format=3,
interval=10s, maxRegions/cycle=98, maxBytes/s/player=524288
VoxelMapSync watching dim overworld regionDir=.../dimensions/minecraft/overworld/region
```

### 2.3 Disk reality on this server (2026-08-27 snapshot)

| Store | Count | Notes |
|-------|------:|-------|
| Overworld MCA `world/dimensions/minecraft/overworld/region/*.mca` | ~1942 | Chunky in progress (~51%, ~1.805M chunks, radius 15000 square) |
| VoxelMapSync cache `world/voxelmapsync_cache/overworld/{rx},{rz}.zip` | 4207 | format 3 zips (`data`/`key`/`biomes`/`control`) |
| MCA present but **no** matching cache zip | **~33** | Frontier Chunky writes; convert lag (some MCA age minutes–hours; one 0-byte MCA observed) |
| Cache zip with **no** matching MCA | ~2297 | Stale / orphaned relative to current MCA set — still pushable if present, but must not invent terrain |

Average compressed region zip ≈ **83 KiB** (min ~5 KiB, max ~164 KiB).  
At **512 KiB/s**, ≈ **6 regions/s**. Full 4207-region dump ≈ **350 MB ≈ 11+ minutes**, with **no guarantee nearby map tiles arrive first**.

### 2.4 Why this feels broken in the map menu

1. Client opens World Map and pans/zooms to Chunky-pregenerated land.
2. VoxelMap shows `EmptyCachedRegion` / empty `CachedRegion` for tiles with no local `*.zip`.
3. Server may already have that MCA (or even the cache zip), but:
   - join queue is huge and **not ordered by what the map is showing**;
   - convert worker may not have converted that MCA yet;
   - **there is no client→server “I need these map regions” packet**.
4. Bandwidth is spent on far/irrelevant pending regions while the visible map stays blank.

### 2.5 Broken / “dirty” looking tiles (second major bug)

Operator report: many tiles that *do* sync look awful — described as dirty/broken chunks. This is **not** the log word `dirty` alone (that only means “MCA mtime changed”); it is a **visual quality** failure of converted terrain.

#### Evidence from this server (2026-08-27)

**A. Converter deliberately accepts incomplete chunk statuses**

`McaReader` static `OK_STATUS` is:

```text
minecraft:features, minecraft:light, minecraft:spawn, minecraft:heightmaps, minecraft:full
(+ short forms: features, light, spawn, heightmaps, full)
```

It does **not** require `minecraft:full`. During Chunky, chunks spend a long time in earlier pipeline states. Accepting `features` / `heightmaps` / `spawn` / `light` produces surfaces that are wrong for the final world (bad heightmaps, underground ores as “surface”, missing foliage/ocean layers, unlit columns).

This looks like a prior attempt to “show more map during pregen.” It backfired: players get **junk tiles** instead of empty or correct ones. Format wipe `cache format -1 → 3` (seen in Aug 25 logs) also failed to solve quality because the convert rules stayed permissive.

**B. Chunky frontier MCAs are mostly incomplete — but cache zips still exist and get synced**

Examples of recently touched overworld MCA (Chunky frontier):

| MCA | Chunk statuses (approx) | Cache zip |
|-----|-------------------------|-----------|
| `r.-21.-15.mca` | structure_starts≈400, biomes/carvers/initialize_light/spawn mix — **not full** | `-21,-15.zip` exists (~70 KiB) |
| `r.-22.-21.mca` | structure_starts/biomes/carvers/initialize_light/spawn — **not full** | `-22,-21.zip` exists; converted ~minutes before latest MCA write |
| `r.22.21.mca` | structure_starts dominant — **not full** | `22,21.zip` = **flat water at y=64** garbage pattern |
| `r.18.21.mca` | light/carvers/biomes/structure_starts mix | `18,21.zip` present |
| `r.-21.-16.mca` | **1024× full** | cache mtime **~35h older** than MCA (reconvert backlog / missed) |

So the worker converts (or leaves stale) region zips while Chunky is still filling the MCA, then join-sync **sends those zips to players**.

**C. Cache quality census (`world/voxelmapsync_cache/overworld`, 4207 zips)**

Heuristic classification of zip `data` layers (height short @ layers 0–1, blockstate short @ 2–3, light @ 4):

| Class | Count | Meaning |
|-------|------:|---------|
| `varied_terrain` | ~2125 | Plausible mixed heights/blocks |
| `flat_water_y64` | ~2058 | Entire tile ≈ height 64 + `minecraft:water` + light 255 (often wrong / placeholder-looking) |
| `degenerate_flat` | ~19 | Nearly constant height/block |
| `mostly_sentinel_empty` | ~9 | Height `Short.MIN_VALUE` (−32768) + air + light 0 (VoxelMap empty/unlit look) |

Among zips **with** a matching MCA: still **~1075 flat_water_y64**. Among zips **without** any MCA (orphans): **~2297** total orphans including flat/sentinel junk — and join sync still queues them.

Tiny orphan example `2,-60.zip` (~4.7 KiB): **~98% sentinel empty** columns, light 0 — classic awful black/void tile if shown.

**D. Race: convert while MCA is being written**

`autoConvert=true` + Chunky `pattern=region` means MCA files are rewritten continuously. Ops already observed VoxelMapSync-Worker contending on `.mca` (STOP mid-convert stalled Chunky). Torn/partial reads → corrupt surfaces → hashed → broadcast. Even a later full rewrite may not repair clients quickly because:

- `maxRegionsPerCycle=98` cannot keep up with thousands of dirty MCAs;
- `mcaMtimes` can mark a region “done” after converting a **partial** snapshot, then lag behind further Chunky writes;
- clients already applied the bad hash until a newer hash arrives (and bandwidth is busy dumping the whole cache).

**E. How VoxelMap renders “awful”**

VoxelMap client (`CachedRegion`) has `isChunkEmptyOrUnlit`, empty regions, and rejects wrong-sized data. Sent tiles with light 0, sentinel heights, or nonsense block keys look like dirty/broken map art. Iron/coal/deepslate ores appearing as top surface in some zip keys is consistent with sampling incomplete heightmaps / non-full sections.

#### Root cause (broken tiles) in one sentence

**VoxelMapSync converts and ships region zips as soon as an MCA is “dirty,” using non-`full` chunk statuses and empty fillers, during live Chunky pregen — so players receive unfinished or stale map data that looks broken.**

---

## 3. How VoxelMapSync 1.0.0 actually works today

Reverse-engineered from the installed JAR + logs (class names are exact).

### 3.1 High-level architecture

```
[Chunky / exploration]
        │ writes
        ▼
  dimension region/*.mca
        │ VoxelMapSync-Worker (autoConvert)
        ▼
  world/voxelmapsync_cache/<dim>/<rx>,<rz>.zip   (VoxelMap format 3)
        │ tickSendQueues @ maxBytesPerSecond
        ▼
  RegionDataPayload (S2C, possibly multi-part)
        │
        ▼
  ClientCacheApplier → writes client VoxelMap cache zip
        │
        ▼
  VoxelMapReload.invalidateRegion → World Map can show terrain
```

Claims are a separate S2C `ClaimsPayload` path; `GuiPersistentMapMixin` only draws claim overlays today.

### 3.2 Important classes

| Class | Role |
|-------|------|
| `com.voxmapsync.VoxelMapSync` | Mod init; join hook |
| `com.voxmapsync.config.SyncConfig` | `config/voxelmapsync.properties` |
| `com.voxmapsync.server.MapSyncServer` | Convert worker, cache index, per-player queues, send loop |
| `MapSyncServer$PlayerSyncState` | `known`, `pending` Deque, `pendingKeys`, send counters |
| `MapSyncServer$RegionRecord` | `dimension`, `regionX`, `regionZ`, `hash`, `zip` bytes/path |
| `com.voxmapsync.server.mca.McaReader` | MCA → surface samples (`ChunkSurface`) |
| `com.voxmapsync.server.VoxelMapZipWriter` | Surface list → VoxelMap region zip (`format:3`) |
| `com.voxmapsync.network.ModNetworking` | Registers payloads; **only serverbound is ClientHello** |
| `ClientHelloPayload` | `worldKey` + `knownHashes` map (batched; `MAX_KNOWN_ENTRIES`) |
| `RegionDataPayload` | `dimension, regionX/Z, contentHash, partIndex/partCount, data` |
| `ClaimsPayload` | Sieged Empires overlay JSON |
| `com.voxmapsync.client.VoxelMapSyncClient` | Hello batches on join; receives region parts / claims |
| `ClientCacheApplier` | Reassembles parts, writes zips under VoxelMap cache dir, tracks known hashes |
| `VoxelMapReload` | Reflectively clears VoxelMap `cachedRegions` entry |
| `GuiPersistentMapMixin` | Injects into `GuiPersistentMap.extractRenderState` → **claims only** |

### 3.3 Config knobs (production values)

File: `config/voxelmapsync.properties`

| Key | Prod value | Meaning |
|-----|------------|---------|
| `syncIntervalSeconds` | `10` | Convert / claims tick cadence |
| `maxRegionsPerCycle` | `98` | Cap on MCA→cache converts per worker cycle |
| `maxRegionsPerSecond` | `0` | `0` = disabled region-count cap (bytes cap still applies) |
| `maxBytesPerSecond` | `524288` | **512 KiB/s per player** — must remain respected |
| `autoConvert` | `true` | Background dirty-MCA conversion (CPU heavy during Chunky) |
| `syncOnJoin` | `true` | After hello, enqueue all missing cache regions |
| `claimsEnabled` / claim UI flags | `true` / … | Unrelated to terrain holes |

### 3.4 Current sync algorithm (why it fails the product goal)

**Server start**

1. Open/create `world/voxelmapsync_cache`.
2. `maybeInvalidateCacheFormat()` — if format ≠ 3, wipe cache.
3. `loadExistingCache()` — index all `dim/{rx},{rz}.zip` into `regions` map.
4. Start `VoxelMapSync-Worker` thread; on interval call `runConvertCycle` → `convertDimension`.
5. Each server tick: `tickSendQueues()` + optional `broadcastClaims()`.

**Convert cycle (`convertDimension`)**

1. List `*.mca` in dimension region dir.
2. Compare mtimes via `mcaMtimes`; mark dirty.
3. For dirty MCA: `McaReader` extracts chunk surfaces; `VoxelMapZipWriter.writeRegion` builds zip; SHA-256 hash; store under cache; add to `newlyConverted`.
4. Cap conversions with `maxRegionsPerCycle` (“converted N (capped)”).
5. `enqueueRegions` for online players / broadcast.

**Player join**

1. `onPlayerJoin`: if `syncOnJoin`, log waiting for hello with full cache size.
2. Client scans local VoxelMap cache (`ClientCacheApplier.scanCacheDir` / `loadKnownHashesFromDisk`), sends **batched** `ClientHelloPayload`s (`known` climbing 256, 512, …).
3. `onClientHello` merges `knownHashes`, then **`enqueueMissingRegions(state)`** — queues **every** server cache region the client lacks / has wrong hash.
4. `tickSendQueues` drains `pending` FIFO under `maxBytesPerSecond` (and optional `maxRegionsPerSecond`), via `sendRegion` → multi-part `RegionDataPayload`.

**Critical gaps vs desired behavior**

| Gap | Today | Needed |
|-----|-------|--------|
| Viewport awareness | None | Client reports visible/empty map regions |
| Demand sync | Only push from join + convert broadcast | Pull/request for map viewport |
| MCA not yet in cache | Wait for dirty convert cycle (can lag Chunky by many regions) | On-demand convert when player requests an existing MCA |
| Queue priority | FIFO over entire missing set | Prefer viewport / nearby; demote background fill |
| Empty map detection | Not used for networking | Use VoxelMap empty/missing region state |
| Serverbound API | Only `client_hello` | Add region/viewport request payload |

`GuiPersistentMapMixin` already touches the World Map render path — perfect extension point — but currently only calls `ClaimOverlayRenderer.drawInMapSpace`.

### 3.5 Units: regions, not Minecraft “chunks”

VoxelMap persistent cache and VoxelMapSync both work in **Anvil region** coordinates:

- 1 region = 32×32 chunks = 512×512 blocks  
- Files: `r.{rx}.{rz}.mca` ↔ `{rx},{rz}.zip`

When the user says “chunks,” implement as **map regions / region tiles** (and optionally finer later). Do **not** stream raw MCA chunks to the client.

### 3.6 VoxelMap client hooks to use

From `voxelmap-fabric-26.2-1.16.8.jar` (do not modify VoxelMap; mixin/reflect):

| Symbol | Use |
|--------|-----|
| `com.mamiyaotaru.voxelmap.persistent.GuiPersistentMap` | World Map screen; fields `regions`, helpers `guiToMap` / `mapToGui`, zoom state |
| `CachedRegion` | Per-tile cache object; `isEmpty`, load state |
| `EmptyCachedRegion` | Placeholder when no data |
| `PersistentMap.cachedRegions` | Live map; already invalidated by `VoxelMapReload` |
| `VoxelConstants.getVoxelMapInstance().getPersistentMap()` | Entry point already used by sync client |

Recommended approach: while `GuiPersistentMap` is open (and optionally periodically while map textures are refreshing), compute the set of **regionX/regionZ** intersecting the current view frustum on the map, classify each as:

- has valid local zip / non-empty cached region → skip  
- empty / missing / unloadable → **request**

---

## 4. Root cause summary (for the fix AI)

### 4.1 Missing tiles

VoxelMapSync was designed as a **server push cache replicator**:

1. Convert all dirty MCA → server-side VoxelMap zips in the background.  
2. On join, tell the client about **everything** it doesn’t have.  
3. Drip-feed under 512 KiB/s.

That design **does not** answer: “The player is staring at empty tiles at map coordinates X that Chunky already generated — fill those **now**.”

So holes appear because:

- **A.** Visible tiles are not prioritized (or even requested).  
- **B.** Convert lag leaves MCA without cache zips on the frontier.  
- **C.** Bandwidth is saturated by thousands of non-visible pending regions.  
- **D.** No protocol exists for the map UI to ask for specific regions.

### 4.2 Broken / dirty tiles

The convert path optimizes for **coverage during pregen**, not **correctness**:

- **E.** `OK_STATUS` includes `features` / `heightmaps` / `spawn` / `light` — unfinished chunks become map art.  
- **F.** `autoConvert` runs on every MCA mtime bump while Chunky is mid-region → partial zips + torn reads.  
- **G.** Missing chunk slots in a region become empty/sentinel/unlit columns (`emptyData` / `emptyHeights`) → checkerboard voids.  
- **H.** Bad zips are hashed and **broadcast / join-queued** like good ones; orphans (cache without MCA) are also synced.  
- **I.** Reconvert to true `full` terrain lags (`maxRegionsPerCycle`) so bad tiles persist on clients for a long time.  
- **J.** Prior mitigations (format wipe to 3, widening OK_STATUS) did not enforce “only finished terrain.”

---

## 5. Required fix design

### 5.0 Fix broken / dirty tiles first (quality gates)

Do this even if viewport sync is deferred — otherwise demand-sync will **prioritize shipping junk faster**.

#### 5.0.1 Strict chunk status (mandatory)

In `McaReader.parseSurface` / `OK_STATUS`:

- **Accept only** `minecraft:full` (and maybe `full` legacy short form).  
- **Remove** `features`, `heightmaps`, `spawn`, `light` from OK_STATUS.  
- Those statuses were the smoking gun for awful mid-pregen tiles.

#### 5.0.2 Do not convert unstable MCA files

Before `readSurfaces` / `writeRegion` on an MCA:

1. **mtime settle debounce** — e.g. ignore MCA until `now - mtime >= convertSettleSeconds` (suggest **30–120s**, config). Prevents converting while Chunky is mid-write.  
2. **Occupancy / completeness gate** — e.g. require `presentChunks == 1024` **or** (`presentChunks >= N` AND every present chunk is `full`). Prefer waiting for full regions when Chunky uses `pattern=region`.  
3. **If no usable full surfaces** — skip convert; **do not** replace an existing good cache zip with a sparser/emptier one; log at debug.  
4. **Never send** a newly built zip that fails quality checks (below).

#### 5.0.3 Quality checks on produced zip / surface list

Reject (don’t write / don’t enqueue) if:

- Usable full chunk surfaces below threshold (e.g. `< 256` of 1024 unless region intentionally sparse ocean — better: require all *present* slots full and present≥1).  
- Sentinel height fraction (`-32768`) above e.g. **10–25%** for a “complete” region.  
- Optional: light layer almost all 0 while heights valid → treat as unlit junk.

#### 5.0.4 Cache replace policy

When reconverting:

- If new zip is **lower quality** than existing (fewer full surfaces, higher sentinel fraction), **keep old zip** unless MCA proves old zip is for a different generation (rare).  
- Always update when new zip is from a **strictly fuller** full-status convert.

#### 5.0.5 Stop syncing orphan / junk cache

- On load / enqueue: if `r.rx.rz.mca` missing or size 0, **do not** join-queue that zip (or quarantine). Orphans on this server (~2297) pollute bandwidth and visuals.  
- Optional one-shot: admin command / config `pruneOrphanCache=true` to delete cache zips with no MCA (operator can run after reviewing).

#### 5.0.6 Reconvert backlog after becoming full

- When an MCA transitions to all-`full` (or settle passes), **force dirty** even if a partial zip exists.  
- Prioritize reconvert of regions that currently have low-quality cached zips.  
- During Chunky, prefer **quality over coverage**: better empty map than broken map.

#### 5.0.7 Why earlier fixes failed (tell the source AI)

Likely previous attempts:

- Wiping cache via format bump (`format -1 → 3`) — resets files but **same convert rules** refill junk during Chunky.  
- Widening `OK_STATUS` — increases coverage, **increases** awful tiles.  
- Raising `maxRegionsPerCycle` — converts junk faster, still junk.

Correct approach is **strict full-only + settle + don’t ship orphans**, not “convert more aggressively.”

### 5.1 New network payload (serverbound)

Add something like:

```text
voxelmapsync:region_request   (name flexible; keep id namespace voxelmapsync)
```

Suggested fields:

```java
record RegionRequestPayload(
  String dimension,          // e.g. "minecraft:overworld"
  int centerRegionX,         // optional but useful for server-side priority
  int centerRegionZ,
  int[] regionXs,            // parallel arrays or list of RegionCoord
  int[] regionZs,
  long clientSeq             // optional: ignore stale requests
)
```

Constraints:

- Cap list size per packet (e.g. 64–256 regions) to avoid abuse / huge packets.  
- Client may send every N ticks / on pan debounce while World Map is open.  
- Deduplicate on server per player (`pendingKeys` already exists — reuse).

Register in `ModNetworking` alongside `ClientHelloPayload` (currently the **only** serverbound type).

### 5.2 Client: detect empty visible map regions

Extend mixin coverage beyond claims:

**Option A (preferred):** Expand `GuiPersistentMapMixin` (or sibling mixin) on `GuiPersistentMap` tick/render:

1. If screen not current / level null → return.  
2. Compute visible world AABB from GUI via existing `guiToMap` (or equivalent field access).  
3. Convert block bounds → region bounds (`block >> 9` for region coord, since 512 = 2^9).  
4. For each region in view (+ 1 region margin):  
   - Look up in `PersistentMap.cachedRegions` / `GuiPersistentMap.regions`.  
   - If instance of `EmptyCachedRegion` OR `isEmpty()` OR no local hash in `ClientCacheApplier.knownHashes` → add to request set.  
5. Batch-send `RegionRequestPayload` (debounce ~0.5–1.0s; don’t spam every frame).

**Option B:** Mixin `PersistentMap` when it assigns `EmptyCachedRegion` for a coordinate while World Map is open — more invasive.

Also: after `ClientCacheApplier` finishes writing a zip, keep calling `VoxelMapReload.invalidateRegion` (already present) so the open map refreshes without reconnect.

### 5.3 Server: handle request with MCA existence check

In `MapSyncServer`, new handler `onRegionRequest(player, payload)`:

For each requested `(dimension, rx, rz)`:

1. **If** `regions` cache already has `RegionRecord` with zip bytes → `enqueueFront` / high-priority enqueue (see §5.4).  
2. **Else if** MCA file exists at `resolveRegionDir(level)/r.rx.rz.mca` and `size > 0`:  
   - Only if quality gates in §5.0 pass (full chunks + settle).  
   - **On-demand convert** that single MCA (reuse `McaReader` + `VoxelMapZipWriter.writeRegion`).  
   - Insert into cache index.  
   - Enqueue high-priority to that player.  
   - Do **not** call worldgen / `ServerChunkCache` getChunk to generate.  
   - If MCA exists but is still incomplete → **do not** send a partial zip; leave empty until full (or send only if a prior *good* full zip exists).  
3. **Else** (no MCA): skip silently (true void / not pregenerated). Optionally log at debug.

Concurrency notes from this server’s ops history:

- Convert worker already contends with Chunky on MCA files (STOP on worker stalled Chunky).  
- On-demand convert should use the **same worker queue** or a short lock around MCA read, not unbounded parallel readers on hot Chunky frontiers.  
- Prefer: push request into a `priorityConvertQueue`; worker drains those **before** general dirty scan.

### 5.4 Queue priority under 512 KiB/s

`PlayerSyncState.pending` is a `Deque` — use it:

1. **Viewport / explicit requests** → `addFirst` (or separate `priorityPending` drained first).  
2. **Background join fill / convert broadcast** → `addLast`.  
3. `tickSendQueues` unchanged in byte accounting: still stop when `bytesThisSecond >= maxBytesPerSecond`.  
4. Optional: when a new viewport request arrives, **reorder** existing pending so requested keys move to front (don’t duplicate — `pendingKeys` already dedupes).  
5. Optional config: `viewportOnlySync=true` to disable whole-cache join enqueue once viewport sync works (or keep join sync but distance-sort from player region).

**Do not raise** `maxBytesPerSecond` above server config; operator wants 512 KiB/s.

Rough capacity: ~6 avg regions/s ≈ fill a ~7×7 region window in ~8s if prioritized — acceptable UX.

### 5.5 Join path changes (recommended)

Keep hello hash negotiation (good bandwidth saver when client already has data).

Change `enqueueMissingRegions`:

- **Minimum**: sort missing regions by Chebyshev/Euclidean distance to player’s current region before enqueue.  
- **Better**: only auto-enqueue within radius R (config `joinSyncRadiusRegions`, e.g. 8–16); rely on viewport requests for the rest.  
- **Best with map-open UX**: join sync radius small; World Map requests drive the rest.

Log lines to add for debugging:

```text
VoxelMapSync viewport request from {player} dim={} center={},{} count={} queuedPriority={}
VoxelMapSync on-demand convert r.{x}.{z} for {player} (mca→cache)
VoxelMapSync skip request r.{x}.{z} — no MCA (not pregenerated)
```

### 5.6 What NOT to do

- Do not generate chunks via server chunk loading/worldgen to “fill the map.”  
- Do not remove `maxBytesPerSecond` enforcement.  
- Do not require modifying VoxelMap’s own JAR (mixin/reflect only).  
- Do not send raw `.mca` to clients.  
- Do not clear the entire client cache on each request.  
- Do not assume 1 MCA dirty scan catches up under Chunky — viewport on-demand convert is mandatory for frontier holes.  
- **Do not** “fix” broken tiles by adding more statuses to `OK_STATUS` or by wiping format again without strict full-only gates.  
- **Do not** broadcast/replace cache from mid-Chunky partial MCA reads.  
- **Do not** join-sync orphan zips with no backing MCA.

---

## 6. Suggested implementation checklist (source repo)

### Protocol / networking

- [ ] Add `RegionRequestPayload` (or equivalent) + codec + Fabric registration.  
- [ ] Register server receiver → `MapSyncServer.onRegionRequest`.  
- [ ] Keep `ClientHelloPayload` / `RegionDataPayload` / `ClaimsPayload` intact.

### Convert quality (broken tiles)

- [ ] Narrow `OK_STATUS` to **`minecraft:full` only**.  
- [ ] Add MCA mtime settle debounce before convert.  
- [ ] Skip / don’t replace cache when surfaces are partial or sentinel-heavy.  
- [ ] Don’t enqueue orphan cache (no MCA).  
- [ ] Force reconvert when region becomes fully `full` after a partial zip.  
- [ ] Add logs: `skip convert (incomplete status)`, `skip convert (settle)`, `reject low-quality zip`, `prune orphan`.

### Client

- [ ] While World Map open, compute visible region set.  
- [ ] Detect empty/missing via `EmptyCachedRegion` / `isEmpty` / missing known hash.  
- [ ] Debounced send of region requests.  
- [ ] Ensure applied regions invalidate VoxelMap cache so the open GUI updates.  
- [ ] Keep claim overlay mixin working.

### Server sync

- [ ] Priority pending queue for viewport requests.  
- [ ] On-demand MCA→zip convert only when §5.0 gates pass.  
- [ ] Distance-sort or radius-limit join enqueue.  
- [ ] Share convert path with worker; avoid MCA lock stampedes during Chunky.  
- [ ] Logging for request / convert / skip.

### Config (optional new keys)

```properties
# Quality / anti-junk (critical)
strictFullStatusOnly=true
convertSettleSeconds=60
minFullChunksToConvert=1024
maxSentinelFraction=0.10
syncOrphanCache=false
pruneOrphanCacheOnStart=false

# Viewport (missing tiles)
viewportSyncEnabled=true
viewportRequestIntervalMs=750
viewportMarginRegions=1
maxRegionsPerRequest=128
joinSyncRadiusRegions=12
onDemandConvert=true

# existing:
maxBytesPerSecond=524288
autoConvert=true
```

### Tests / verification

**Missing tiles**

1. Pregen (or copy) a far MCA the client has never visited; wait until chunks are `full`.  
2. Join with empty client VoxelMap cache.  
3. Open World Map, pan to that area **before** background sync would reach it.  
4. Expect: request logs → on-demand convert (if needed) → region_data under 512 KiB/s → tile fills without exploring in-person.  
5. Pan to never-generated void beyond Chunky radius → stays empty; debug skip log.

**Broken tiles**

6. While Chunky is actively writing an MCA, confirm convert **does not** produce/send a new zip for that region (settle / incomplete skip logs).  
7. After that MCA reaches 1024× `full`, confirm a reconvert runs and clients receive a hash update that looks correct (not flat water / checkerboard / ore-as-surface).  
8. Confirm join sync does not send orphan zips (no MCA).  
9. Spot-check: open World Map over frontier — no black sentinel patches or “dirty” half-generated tiles.  
10. Claims overlay still draws.  
11. With Chunky running, on-demand/full convert doesn’t deadlock region IO.

---

## 7. Concrete “edit these places” map

| Area | File (source names from JAR) | Change |
|------|------------------------------|--------|
| Config | `SyncConfig.java` | New viewport / on-demand / join-radius knobs |
| Net IDs | `ModNetworking.java`, new payload class | Serverbound region request |
| Server core | `MapSyncServer.java` | `onRegionRequest`, priority enqueue, on-demand convert, join sort/radius |
| Player state | `MapSyncServer.PlayerSyncState` | Optional `priorityPending` or use `Deque.addFirst` |
| Convert | `McaReader.java`, `MapSyncServer.convertDimension` | **Strict full-only**; settle debounce; quality reject; extract `convertOneMca` |
| Client net | `VoxelMapSyncClient.java` | Send requests; reuse receive path |
| Client apply | `ClientCacheApplier.java` | Already writes zips — ensure invalidate after viewport fills |
| World Map mixin | `GuiPersistentMapMixin.java` (+ maybe new mixin) | Empty-visible detection + request scheduler |
| Mixins JSON | `voxelmapsync.client.mixins.json` | Register any new mixins |

---

## 8. Environment specifics this fix must respect

- **MC / loader**: Minecraft 26.2, Fabric Loader 0.19.3, Java 25.  
- **Dimension path quirk**: overworld regions live at  
  `world/dimensions/minecraft/overworld/region`  
  (already handled by `resolveRegionDir` / watch logs).  
- **Cache path**: `world/voxelmapsync_cache/<dimName>/`.  
- **World key** clients send: IP-like `worldKey` (e.g. `147.135.31.41`) — keep hello behavior.  
- **Chunky**: pattern `region`, square radius 15000; MCA appears before VoxelMapSync convert finishes — this is the main “empty but exists” case.  
- **Ops note**: `autoConvert=true` during Chunky is CPU-heavy (~13% of process in one profile) and contends on MCA; viewport on-demand should be **targeted**, not a second full-scan hammer.

---

## 9. Success definition (operator-facing)

When a player opens the World Map and looks at terrain Chunky already finished (`full`):

- Empty tiles in view are requested within ~1s.  
- They receive correct VoxelMap region data soon after, **without walking there**.  
- Transfers stay ≤ **512 KiB/s** per player.  
- Unexplored / non-pregenerated areas stay empty.  
- **No broken/dirty tiles** from in-progress Chunky regions — unfinished areas stay empty until convert quality gates pass.  
- Claims overlay and normal exploration caching still work.

---

## 10. Appendix — useful log fingerprints

```text
VoxelMapSync ready — terrain sync every 10s, claims=true
VoxelMapSync ready — cache=... format=3, interval=10s, maxRegions/cycle=98, maxBytes/s/player=524288
VoxelMapSync watching dim overworld regionDir=...
VoxelMapSync waiting for client hello from {} ({} region(s) in cache)
VoxelMapSync hello from {} worldKey={} known={} queued={} pending={}
VoxelMapSync dim {}: scanned {} MCA, dirty {}, converted {} (capped)
VoxelMapSync converted {} region(s); broadcasting to {} player(s) (cache total={})
MCA {} had no usable chunk surfaces
```

Payload IDs (from class strings): `client_hello`, `region_data`, plus claims id; **add** e.g. `region_request`.

Cache zip members: `data`, `key`, `biomes`, `control` with `format:3`.

---

## 11. One-paragraph brief for the coding agent

> VoxelMapSync 1.0.0 has two bugs. (1) **Missing tiles**: it only push-replicates `voxelmapsync_cache` after join hello and background MCA convert — never asks what the World Map is showing — so bandwidth (~512 KiB/s, ~11 min for full cache) is wasted on FIFO regions while visible tiles stay empty. Fix with GuiPersistentMap empty detection + serverbound region requests + priority enqueue + on-demand convert of existing MCA. (2) **Broken/dirty tiles**: `McaReader.OK_STATUS` accepts `features`/`heightmaps`/`spawn`/`light` (not only `full`), and `autoConvert` runs on every dirty MCA while Chunky is mid-write, producing and broadcasting partial/sentinel/flat junk zips (this server: ~49% of cache classified flat-water-y64; frontier MCAs incomplete yet still have synced zips; ~2297 orphan zips without MCA). Prior format wipes / wider OK_STATUS made coverage worse, not better. Fix by **strict `minecraft:full` only**, mtime settle debounce, reject low-quality/partial zips, don’t replace good cache with worse, don’t sync orphans, then reconvert when regions become fully complete — and only then feed viewport demand sync so you don’t prioritize shipping junk.
