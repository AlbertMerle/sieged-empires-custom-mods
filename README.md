# sieged-empires-custom-mods

Monorepo of Sieged Empires custom Fabric mod **sources**, mirroring the workspace `mods/` layout (minus excluded folders).

## Included

| Folder | Notes |
|--------|--------|
| `CropLite/` | CropLite |
| `Sieged Empires/` | siegedempires base mod |
| `WeaponsMod/` | WeaponsModLegacy + Addon workspace |
| `distantnoise/` | Distantnoise |
| `moremeat/` | More Meat & Milk |
| `randomspawn/` | RandomSpawn |
| `servertracker/` | ServerTracker (server-only audit) |
| `shiftyocean/` | Shiftyocean |
| `sieged-music/` | Sieged Music |
| `terrabiome/` | Terratonic Biomes |
| `voxelmapsync/` | VoxelMapSync |

## Excluded on purpose

- `distantentities/`
- `VoxelMap/` (upstream ARR — not published here)
- `voxelserver/` (local test server tree)
- Per-mod `aidata/` agent-memory folders
- Gradle caches (`.gradle/`), `build/`, `run/`
- Nested `.git/`
- `shiftyocean/libs/physics-mod-pro-*.jar` — proprietary + over GitHub’s 100 MB file limit; obtain separately if needed to compile

## Build

Each mod is its own Gradle/Loom project. From a mod folder (or nested `Addon/` / `RandomSpawn26.2/` as applicable):

```bash
./gradlew build
```

