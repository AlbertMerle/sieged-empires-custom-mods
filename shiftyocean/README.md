# Shiftyocean

Fabric 26.2 mod: ocean currents shove swimming **players** and configured **boats/ships** during rain/thunder.

## Behavior

| Weather | Default player push |
|---------|---------------------|
| Clear | `0` blocks/s |
| Rain | `0.4` blocks/s |
| Thunderstorm | `1.0` blocks/s |

- Players: full BPS, additive X/Z shift (stacks with swim input)
- Enabled entities (boats, Shippy Ships, …): **15%** of player BPS + weather yaw drift (rain **8°/s**, thunder **12°/s**)
- Driven boats/ships keep paddle/sail control; storm current is **added** on top (including while a player is driving)
- Must be in water in an ocean biome: `#minecraft:is_ocean` / `#minecraft:is_deep_ocean`, `#c:is_ocean` (and deep/shallow), `#shiftyocean:oceans` (every vanilla ocean plus optional Terralith `deep_warm_ocean`), or any biome id whose path is `ocean` / `*_ocean` (frozen, cold, lukewarm, warm, deep variants)
- Players riding a boat/ship are not shoved as passengers (the vehicle gets the current)
- **General current** (any heading on 360°, including NE/SW mixes of X and Z) re-rolls every **7 minutes**
- Short-interval “gust” every **10 ticks**: **70%** the general heading, **30%** a different random 360° direction

## Config

Created on first load: `config/shiftyocean.json`

```json
{
  "enabled": true,
  "clearSpeedBps": 0.0,
  "rainSpeedBps": 0.4,
  "thunderSpeedBps": 1.0,
  "directionChangeTicks": 10,
  "generalDirectionChangeMinutes": 7.0,
  "generalDirectionBias": 0.7,
  "entitySpeedScale": 0.15,
  "entityYawDriftRainDegreesPerSecond": 8.0,
  "entityYawDriftThunderDegreesPerSecond": 12.0,
  "enabled-entities": [
    "#minecraft:boat",
    "minecraft:oak_chest_boat",
    "minecraft:spruce_chest_boat",
    "minecraft:birch_chest_boat",
    "minecraft:jungle_chest_boat",
    "minecraft:acacia_chest_boat",
    "minecraft:cherry_chest_boat",
    "minecraft:dark_oak_chest_boat",
    "minecraft:pale_oak_chest_boat",
    "minecraft:mangrove_chest_boat",
    "minecraft:bamboo_chest_raft",
    "shippy-ships:oak_sailboat",
    "shippy-ships:oak_cog",
    "shippy-ships:oak_caravel",
    "… all 10 woods × sailboat/cog/caravel (30 ids)"
  ]
}
```

Soft-suggests **Shippy Ships** (`suggests.shippy-ships`); jars in `libs/` are compileOnly + localRuntime for local runs. Loom also loads `run/mods/` (copy of those jars plus Physics Mod for testing).

Defaults list every Shippy vessel id (not only `shippy-ships:*`). Wildcard still works if you prefer it.

`enabled-entities` accepts:

| Entry | Meaning |
|-------|---------|
| `minecraft:oak_boat` | Exact entity id |
| `#minecraft:boat` | Entity-type tag (all wood boats + bamboo raft) |
| `shippy-ships:*` | Every entity in that mod namespace |
| `shippy-ships:oak_sailboat` | One Shippy vessel |

## Build

```bash
./gradlew build
```

Copy `build/libs/shiftyocean-1.0.0.jar` to `client/mods/` and `server/mods/`.
