# WeaponsMod Addon

Fabric companion for **Balkon's WeaponMod: Legacy** (`weaponmod`).

## Layout

| Path | Purpose |
|------|---------|
| `WeaponsMod/WeaponsModLegacy/` | Upstream source (Architectury multi-loader, branch `26.2`) |
| `WeaponsMod/Addon/` | This addon — mechanics + animation tweaks |
| `libs/` | Local jars for compile + Loom run (WeaponMod + Architectury + Cloth Config) |
| `run/mods/` | Same jars for `./gradlew runClient` (gitignored under `run/`) |

## Build

```bash
cd WeaponsMod/Addon
./gradlew build
```

Jar: `build/libs/weaponsmodaddon-1.0.0.jar`  
Copy to `client/mods/` and `server/mods/` after changes.

## Dev client

```bash
cd WeaponsMod/Addon
./gradlew runClient
```

Loads this addon from source plus jars in `libs/` / `run/mods/` (`weaponmod`, Architectury, Cloth Config). Do **not** put `weaponsmodaddon-*.jar` in `run/mods/` (Loom already loads the project).

If `run/mods/` is missing after a clean clone, copy from `libs/`:

```bash
mkdir -p run/mods && cp libs/*.jar run/mods/
```

## Runtime deps

- `weaponmod` (required)
- Architectury API (`architectury`) — required by WeaponMod itself
- Cloth Config — required by WeaponMod itself

## Where to work

- **Mechanics** — `src/main/` + `weaponsmodaddon.mixins.json`
- **Animations / render** — `src/client/` + `weaponsmodaddon.client.mixins.json`
