# WeaponMod musket / flintlock fire sounds (for review)

WeaponMod does **not** ship its own gun `.ogg` files. Firing reuses vanilla Minecraft sounds.

## What plays when you fire

| Weapon | Sound event | Vanilla files | Volume | Pitch formula |
|--------|-------------|---------------|--------|---------------|
| **Musket** (incl. bayonet / scoped) | `minecraft:entity.generic.explode` | `explode1–4.ogg` | 3.0 | `1.0 / (rand*0.4 + 0.7)` ≈ **0.71–1.43** |
| **Musket** (same shot, layered) | `minecraft:entity.lightning_bolt.thunder` | `thunder1–3.ogg` | 3.0 | `1.0 / (rand*0.4 + 0.4)` ≈ **0.71–2.50** |
| **Flintlock** | `minecraft:entity.generic.explode` only | `explode1–4.ogg` | 3.0 | same as musket explode |

Source (WeaponMod):
- `ckathode.weaponmod.item.RangedCompMusket.effectShoot`
- `ckathode.weaponmod.item.RangedCompFlintlock.effectShoot`

## Folder layout

```
sounds/
  README.md                 ← this file
  SOURCE.md                 ← sound-event → file map from sounds.json
  musket_fire/              ← explode1–4 + thunder1–3 (both layers)
  flintlock_fire/           ← explode1–4 only
  _source_vanilla/          ← flat dump of every unique .ogg used
```

Listen here, then decide keep / drop / replace. Replacing in-game later would mean either a resource pack remapping those vanilla events (affects all explosions/thunder) or addon custom SoundEvents for guns only.

## Not included (related but not “fire”)

Reload / chamber click uses `minecraft:block.comparator.click` (`RangedCompMusket` / `RangedCompFlintlock` / `RangedComponent`). Ask if you want those extracted too.
