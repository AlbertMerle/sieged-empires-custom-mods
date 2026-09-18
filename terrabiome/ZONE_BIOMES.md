# Terrabiome — allowed **land** biomes per latitude zone

Terrabiome **owns overworld land biome placement**. Terralith supplies biomes to the library; Tectonic shapes terrain (continentalness, erosion, depth, weirdness are never changed). Multi-noise only *suggests* a biome; `enforce-zone-biomes` (default **true**) replaces **land** picks outside the lists below.

**Oceans / rivers are not zoned.** They are left as multi-noise places them (important for temperature mods — oceans stay ~0.5). **Beaches** are post-filtered to a Warm/Hot coastal strip only (`CoastalBeachFilter`). Land-over-ocean bleed (`preferLandOverOcean`) still replaces ocean *on land*; it does not rewrite real deep ocean.

**Surface Y rules (active):** Below **Y 45** only **vanilla** cave biomes may spawn (rates in `cave-biomes`; Terralith caves banned). At and above **Y 45** only whitelisted surface **land** biomes per zone — **no cave biomes**. Config: `surface-biome-bounds`, `surface-biome-min-y` (default 45), `cave-biomes`.

| Zone | Z (default `world-size` 30000) | Count |
|------|--------------------------------|------:|
| Freezing | Z ≤ −9000 | 12 |
| Cold | −9000 … −3000 | 8 |
| Temperate | −3000 … +3000 | 10 |
| Warm | +3000 … +9000 | 12 |
| Hot | Z ≥ +9000 | 20 |

**Globally banned** (never spawn at surface land): Terralith alpha islands, skylands, `mushroom_fields`, `hot_shrubland`, `windswept_forest`, and **all cave/underground biomes** (vanilla lush/dripstone/deep_dark/sulfur + Terralith `cave/*` — see `CaveBiomeBan.java`). Below Y 45, Terralith caves stay banned; vanilla caves use `cave-biomes` rates.

**Post-filters** (still apply after whitelist): cold peaks → hills/stony south of Z≈0 (skipped at Y≥200 in Freezing/Cold/Temperate so snow stays); warm swamps Warm/Hot only; plains↔meadow at Temperate|Warm; **coastal beaches** Warm/Hot only (1–12 blocks inland + short ocean bleed; nowhere else); skyland feature cancel; small-land absorb (`minLandBiomeSize` 64 chunks); **Y≥200 Warm/Hot** highland shift via erosion form → cooler zone biomes (never snow).

**Terralith shortlist (2026-09-06):** only the Terralith ids below are allowed; all other Terralith surface land biomes are rewritten. Vanilla land lists are unchanged.

---

## Freezing (12 biomes)

### Vanilla (7)

- `minecraft:frozen_peaks` — temp -0.7, downfall 0.9
- `minecraft:grove` — temp -0.2, downfall 0.8
- `minecraft:ice_spikes` — temp 0, downfall 0.5
- `minecraft:jagged_peaks` — temp -0.7, downfall 0.9
- `minecraft:snowy_plains` — temp 0, downfall 0.5
- `minecraft:snowy_slopes` — temp -0.3, downfall 0.9
- `minecraft:snowy_taiga` — temp -0.5, downfall 0.4

### Terralith (5)

- `terralith:alpine_grove` — temp -0.2, downfall 0.8
- `terralith:birch_taiga` — temp 0.22, downfall 0.2
- `terralith:ice_marsh` — temp 0.14, downfall 0.9 — cold swamp
- `terralith:scarlet_mountains` — temp 0.1, downfall 0.4
- `terralith:snowy_maple_forest` — temp 0.1, downfall 0.2

---

## Cold (8 biomes)

### Vanilla (5)

- `minecraft:old_growth_birch_forest` — temp 0.6, downfall 0.6 — also Temperate
- `minecraft:old_growth_pine_taiga` — temp 0.3, downfall 0.8
- `minecraft:old_growth_spruce_taiga` — temp 0.25, downfall 0.8
- `minecraft:taiga` — temp 0.25, downfall 0.8
- `minecraft:windswept_gravelly_hills` — temp 0.2, downfall 0.3

### Terralith (3)

- `terralith:alpine_highlands` — temp 0.45, downfall 0.1
- `terralith:caldera` — temp 0.45, downfall 0.8
- `terralith:yellowstone` — temp 0.24775, downfall 0.8

---

## Temperate (10 biomes)

### Vanilla (9)

- `minecraft:birch_forest` — temp 0.6, downfall 0.6
- `minecraft:cherry_grove` — temp 0.5, downfall 0.8
- `minecraft:dark_forest` — temp 0.7, downfall 0.8 — also Warm
- `minecraft:flower_forest` — temp 0.7, downfall 0.8
- `minecraft:forest` — temp 0.7, downfall 0.8
- `minecraft:meadow` — temp 0.5, downfall 0.8
- `minecraft:old_growth_birch_forest` — temp 0.6, downfall 0.6 — also Cold
- `minecraft:pale_garden` — temp 0.7, downfall 0.8
- `minecraft:windswept_hills` — temp 0.2, downfall 0.3 — cold-mtn replace south of Z≈0

### Terralith (1)

- `terralith:moonlight_grove` — temp 0.7, downfall 0.8 — also Warm

---

## Warm (12 biomes)

### Vanilla (6)

- `minecraft:bamboo_jungle` — temp 0.95, downfall 0.9 — also Hot wet
- `minecraft:dark_forest` — temp 0.7, downfall 0.8 — also Temperate
- `minecraft:mangrove_swamp` — temp 0.8, downfall 0.9 — warm swamp
- `minecraft:plains` — temp 0.8, downfall 0.4
- `minecraft:sunflower_plains` — temp 0.8, downfall 0.4
- `minecraft:swamp` — temp 0.8, downfall 0.9 — warm swamp

### Terralith (6)

- `terralith:jungle_mountains` — temp 0.95, downfall 0.9 — also Hot wet
- `terralith:mirage_isles` — temp 0.7, downfall 0.8
- `terralith:moonlight_grove` — temp 0.7, downfall 0.8 — also Temperate
- `terralith:orchid_swamp` — temp 0.8, downfall 0.9 — warm swamp; also Hot wet
- `terralith:temperate_highlands` — temp 0.5, downfall 0.2
- `terralith:tropical_jungle` — temp 0.95, downfall 0.9 — also Hot wet

---

## Hot (20 biomes)

Hot land is split into **dry** (desert, mesa, savanna, badlands, volcanic peaks) and **wet** (jungle, bamboo, swamp) classes. With `hotDesertJungleMix` (default on), large patches south of Z ≥ +9000 are split by `hot-wet-land-fraction` (default **0.5** = equal wet vs dry land area) via equal-probability noise cells; the zone enforcer only picks biomes from the matching class in each patch.

### Dry — vanilla (8)

- `minecraft:badlands` — temp 2, downfall 0
- `minecraft:desert` — temp 2, downfall 0
- `minecraft:eroded_badlands` — temp 2, downfall 0
- `minecraft:savanna` — temp 1.2, downfall 0
- `minecraft:savanna_plateau` — temp 1, downfall 0
- `minecraft:stony_peaks` — temp 1, downfall 0.3
- `minecraft:windswept_savanna` — temp 2.0, downfall 0.0
- `minecraft:wooded_badlands` — temp 2.0, downfall 0.0

### Dry — Terralith (4)

- `terralith:brushland` — temp 1.2, downfall 0.2
- `terralith:desert_oasis` — temp 2, downfall 0
- `terralith:lush_desert` — temp 2, downfall 0
- `terralith:sandstone_valley` — temp 2, downfall 0

### Wet — vanilla (5)

- `minecraft:bamboo_jungle` — temp 0.95, downfall 0.9 — also Warm
- `minecraft:jungle` — temp 0.95, downfall 0.9
- `minecraft:mangrove_swamp` — temp 0.8, downfall 0.9 — warm swamp
- `minecraft:sparse_jungle` — temp 0.95, downfall 0.8
- `minecraft:swamp` — temp 0.8, downfall 0.9 — warm swamp

### Wet — Terralith (3)

- `terralith:jungle_mountains` — temp 0.95, downfall 0.9 — also Warm
- `terralith:orchid_swamp` — temp 0.8, downfall 0.9 — warm swamp; also Warm
- `terralith:tropical_jungle` — temp 0.95, downfall 0.9 — also Warm

---

Source of truth in code: `ZoneBiomeCatalog.java` (land-only; jungle_mountains / orchid_swamp / tropical_jungle / moonlight_grove / bamboo / dark_forest / old_growth_birch may count in two zones).
