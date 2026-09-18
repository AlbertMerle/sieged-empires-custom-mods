# RandomSpawn (Fabric 26.2)

Server-side Fabric mod that teleports players to a random dry-land location within a configurable coordinate range.

Ported from [ThebigTijn/RandomSpawn](https://github.com/ThebigTijn/RandomSpawn) (1.21.1) to Minecraft 26.2.

## Features

- Random spawn on first join and/or respawn (when the player has no bed/respawn anchor)
- Configurable X/Z bounds
- Skips oceans and any position with water or lava at feet, head, or ground
- When **Sieged Empires** is present, skips chunks claimed by a town (wilderness only)

## Config

Created at `config/Randomspawn.properties` on first run:

```
MinX=-1000
MaxX=1000
MinZ=-1000
MaxZ=1000
SpawnOnFirstJoin=true
SpawnOnRespawn=true
```

## Build

```bash
./gradlew build
```

The jar will be in `build/libs/`.
