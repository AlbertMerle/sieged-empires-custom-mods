# Upstream references for Terrabiome

Cloned for development (not required at runtime — use the jars in `libs/` / server mods).

| Folder | Repo | Purpose |
|--------|------|---------|
| `Terralith/` | https://github.com/Stardust-Labs-MC/Terralith | Biome / parameter list datapack |
| `tectonic/` | https://github.com/Apollounknowndev/tectonic | Minecraft terrain shaping |
| `lithostitched/` | https://github.com/Apollounknowndev/lithostitched | Worldgen library (wrap/inject) |

Releases: https://github.com/Apollounknowndev/lithostitched/releases

**Do not use** https://github.com/tectonic-typesetting/tectonic — that is the TeX typesetting engine, not the Minecraft mod.

Refresh:

```bash
cd reference
git -C Terralith pull --ff-only
git -C tectonic pull --ff-only
git -C lithostitched pull --ff-only
```
