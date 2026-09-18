# Terrabiome (`terratonicbiomes`)

Fabric 26.2 mod: **Z-axis latitude biome placement** for Terralith + Tectonic overworlds.

## Stack with Terralith + Tectonic

Terrabiome **owns surface-land biome placement** (mixin on `MultiNoiseBiomeSource`). It uses Terralith's biome definitions/features but does not accept Terralith's land choice. It does **not** change continentalness, erosion, depth, weirdness, or Tectonic terrain.

Datapack priority is forced to:

1. **Terralith** (lowest of the three) — biome library for this mod to use  
2. **Tectonic** (`tectonic:tectonic`) — full say over land / density / height  
3. **Terrabiome** (`terratonicbiomes:biomes`) — marker pack on top; Java performs direct catalog placement

Vanilla Fabric auto-enables packs alphabetically, which put `terralith` *above* `tectonic:tectonic` and made Tectonic look like it never applied. `PackRepositoryMixin` + `DatapackStack` fix that, force-enable Tectonic when the mod is present, and register the Terrabiome builtin pack as `ALWAYS_ENABLED`.

Look for log line: `Datapack stack (low→high): …` (`log-datapack-stack` in `config/terrabiome.json`, default true).

## What it does

1. **Latitude temperature** — remaps multi-noise temperature into five equal Z bands (south = +Z): Freezing, Cold, Temperate, Warm, Hot. Tectonic density unchanged.
2. **Direct zone placement (`enforce-zone-biomes`, default true)** — Terrabiome chooses **land** directly from the per-zone catalog in `ZONE_BIOMES.md` / `ZoneBiomeCatalog.java`. Deterministic jittered-Voronoi regions use `minLandBiomeSize`; Terralith's one-shot result only classifies ocean, river, beach, or land. Terrain shape is untouched.
3. **Hot wet/dry mix** — in Hot only, large wet/dry patches (~50/50) via humidity remap + mismatch correction.
4. **Minimum land regions** — constant-time jittered-Voronoi land regions use `minLandBiomeSize`; no neighboring biome generation or recursive climate sampling.
5. **Plains ↔ meadow** — north of Temperate|Warm cutoff: plains → meadow; south: meadow → plains.
6. **Land over ocean** — re-query with land-biased continentalness when inland would get ocean (does not rewrite real ocean).
7. **No skylands** — skyland biomes + floating island features cancelled.
8. **Cold mountain override** — south of Z≈0: cold peaks → windswept_hills (stony_peaks in Hot).
9. **Warm swamp ban** — swamp/mangrove/orchid_swamp only in Warm + Hot.
10. **Northern beach rules** — north of Z −3000 (Cold + Freezing): beaches → nearest land; north of ≈ −8000: `snowy_beach` instead.
11. **Allowed structures** — whitelist (default mineshafts + amethyst geodes).
12. **High-altitude highlands (Y ≥ 200)** — erosion classifies mountain vs plateau. **Mountains always get peak biomes** (`jagged_peaks` / `frozen_peaks` cool; `stony_peaks` / volcanic Warm–Hot). **Plateaus get forests/highlands**, never peaks.

**Surface Y rules:** Below `surface-biome-min-y` (default **45**) only **vanilla** cave biomes may spawn — rates in `cave-biomes` (`1.0` = vanilla, `0.1` ≈ 1/10); Terralith caves always banned. At and above Y 45 only zone-whitelist surface **land** biomes — no cave biomes (`CaveBiomeBan` + depth bias + whitelist).

Terrain height (Tectonic) is **not** changed.

See **[ZONE_BIOMES.md](./ZONE_BIOMES.md)** for the full allowed-land-biome list per zone.

## Config

`config/terrabiome.json` (written on first launch). Key fields: `enabled`, `allowed-structures`, **`world-size`**, **`enforce-zone-biomes`** (default true — hard per-zone whitelist), display min/max per band, `swapPlainsMeadow`, `preferLandOverOcean`, `disableSkylands`, `hotDesertJungleMix`, `overrideColdMountains`, `coldMountainCutoffZ`, `banSwampsNorthOfWarm`, `absorbSmallLandBiomes`, `minLandBiomeSize`, **`high-altitude-zone-shift`** / **`high-altitude-min-y`**, boundary wobble keys. Legacy `zoneBiomeOverrides` only applies when `enforce-zone-biomes` is false.

### `world-size`

North–south climate diameter in blocks. Five equal zones (Freezing → Cold → Temperate → Warm → Hot). Temperate is centered on Z=0.

| `world-size` | Zone width | Temperate | Default cutoffs |
|--------------|------------|-----------|-----------------|
| **30000** (default) | 6000 | −3000…+3000 | −9000 / −3000 / +3000 / +9000 |
| 60000 | 12000 | −6000…+6000 | −18000 / −6000 / +6000 / +18000 |

Beyond ±`world-size`/2, Freezing continues north and Hot continues south. Restart after changing; already-generated chunks keep old biomes.

### Hot wet / dry mix

| Key | Default | Meaning |
|-----|---------|---------|
| `hotDesertJungleMix` | `true` | Large wet/dry patches in Hot + humidity remap + mismatch correction |
| `hot-wet-land-fraction` | `0.5` | Target fraction of Hot land that is wet (jungle/swamp); dry = 1 − this |
| `hotMixPatchScale` | `768` | Patch noise cell size in blocks (larger = more spaced-out regions) |
| `hotMixPatchSeed` | `91337` | Seed for wet/dry patch layout |
| `hotDesertHumidityMax` | `-0.15` | Dry patches map humidity into `[-1, this]` |
| `hotJungleHumidityMin` | `0.30` | Wet patches map humidity into `[this, 1]` (jungle/bamboo/swamp) |

### Minimum land biome size

There is no vanilla/Terralith “min biome size” knob. Terrabiome approximates it:

| Key | Default | Meaning |
|-----|---------|---------|
| `absorbSmallLandBiomes` | `true` | Use `minLandBiomeSize` as the direct-placement region scale |
| `minLandBiomeSize` | `64` | Approximate jittered-Voronoi region scale in **chunks**; set absorb to `false` for one-chunk placement cells |

Oceans, rivers, and beaches are never absorbed and never used as replacements.

### Coastal beaches (Warm/Hot strip only)

| Key | Default | Meaning |
|-----|---------|---------|
| `coastal-beach-rules` | `true` | Thin beach strip on Warm/Hot coasts only |
| `beach-inland-min-blocks` | `1` | Inland start of strip (blocks from ocean) |
| `beach-inland-max-blocks` | `12` | Inland end of strip |
| `beach-ocean-bleed-blocks` | `4` | How far beach may paint into ocean |

Freezing/Cold/Temperate never keep beaches (including `snowy_beach`). Rivers are never rewritten. Warm/Hot coast width is derived in constant time from continentalness and these block-width settings; it no longer samples neighboring chunks.

### Cold mountain override

| Key | Default | Meaning |
|-----|---------|---------|
| `overrideColdMountains` | `true` | Replace cold peaks south of the cutoff |
| `coldMountainCutoffZ` | `0` | Middle of Temperate; cold peaks only north of this (wobbled + blend) |

South of the cutoff: `frozen_peaks` / `jagged_peaks` / `snowy_slopes` → `windswept_hills`. In Hot (Warm\|Hot cutoff, same blend): those → `stony_peaks`. Does not change mountain height.

### Warm swamp ban

| Key | Default | Meaning |
|-----|---------|---------|
| `banSwampsNorthOfWarm` | `true` | No `swamp` / `mangrove_swamp` / `orchid_swamp` in Temperate or north |

`ice_marsh` is not affected. Toggle off to allow warm swamps everywhere humidity places them.

### Northern beach rules

Removed — replaced by **Coastal beaches** above (Warm/Hot strip only).

### High-altitude highlands (`high-altitude-zone-shift`)

| Key | Default | Meaning |
|-----|---------|---------|
| `high-altitude-zone-shift` | `true` | Enable Y≥200 highland rules |
| `high-altitude-min-y` | `200` | Start height for these rules |
| `high-altitude-mountain-erosion-max` | `-0.22` | Erosion ≤ this → mountain form |
| `high-altitude-plateau-erosion-max` | `0.45` | Else erosion ≤ this → plateau; above → no shift |
| `high-altitude-peak-weirdness-min` | `0.55` | \|weirdness\| above this with mild erosion → mountain |

**Freezing / Cold / Temperate:** snowy peak biomes stay on mountains (`jagged_peaks` / `frozen_peaks` / …).  
**Warm / Hot mountains:** always non-snow peaks (`stony_peaks`, volcanic peaks, spires) — never forest.  
**Any climate plateaus:** forests / meadows / highlands — never peaks.

Detection uses multi-noise **erosion** and **weirdness** (correlated with Tectonic shape) — not a heightmap plateaus scan.

### Zone whitelist (`enforce-zone-biomes`)

| Key | Default | Meaning |
|-----|---------|---------|
| `enforce-zone-biomes` | `true` | Choose every **land** biome directly from the per-zone catalog (`ZONE_BIOMES.md`). The underlying pick classifies water/coast only. |

Set `false` to fall back to legacy partial `zoneBiomeOverrides` rules only.

See `ZONE_BIOMES.md` for the resulting per-zone lists.

### `allowed-structures`

Whitelist. Empty = nothing spawns. Default:

```json
"allowed-structures": [
  "mineshafts",
  "amethyst_geodes"
]
```

Accepted names: short (`villages`, `mineshafts`, `mage_tower`), full ids (`minecraft:mineshaft`, `terralith:mage_tower`), or a structure-set id (`minecraft:villages`, `terralith:mage`). Amethyst geodes are features, not structures, but are gated by this same list. Use `"*"` or `"all"` to allow every structure.

Does **not** change already-generated chunks. Disable the old `worldgen_structure_ban` datapack — this config replaces it.

### Cave biomes (`cave-biomes`)

Below `surface-biome-min-y` (default **45**): Terralith caves never spawn. Vanilla caves use keep rates vs multi-noise picks:

```json
"cave-biomes": {
  "minecraft:lush_caves": 1.0,
  "minecraft:dripstone_caves": 1.0,
  "minecraft:deep_dark": 1.0,
  "minecraft:sulfur_caves": 1.0
}
```

| Rate | Meaning |
|------|---------|
| `1.0` | Vanilla (keep every multi-noise pick) |
| `0.1` | ~1/10 of vanilla picks |
| `0` | Never |

Short keys (`sulfur_caves`) work too. At/above Y 45 all caves are banned regardless of rate.

## References (cloned under `reference/`)

| Project | URL | Note |
|---------|-----|------|
| Terralith | https://github.com/Stardust-Labs-MC/Terralith | datapack-style biomes |
| Tectonic | https://github.com/Apollounknowndev/tectonic | **Minecraft** terrain mod |
| Lithostitched | https://github.com/Apollounknowndev/lithostitched | library |

**Wrong link:** `https://github.com/tectonic-typesetting/tectonic` is the TeX engine, not the Minecraft mod.

Runtime jars mirrored in `libs/` from `server-enviornment/mods/`.

## Build / deploy

```bash
./gradlew build
cp build/libs/terrabiome-1.0.0.jar ../server-enviornment/mods/
# also client/mods + server/mods as needed
```

**Disable** `datapacks/worldgen2` on the world — do not run both.

## Requires

- Minecraft 26.2 + Fabric API
- Terralith + Tectonic + Lithostitched on the server (soft-suggested)
- New chunks / new world after install
