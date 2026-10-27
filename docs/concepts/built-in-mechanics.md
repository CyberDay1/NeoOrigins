---
title: Built-in mechanics
parent: Concepts
nav_order: 6
permalink: /concepts/built-in-mechanics
---

# Built-in mechanics

Behaviour the mod applies on its own, without a power file asking for it.

## Undead potion reversal

Origins holding a `neoorigins:entity_group` power with `"group": "undead"`
(Revenant, Necromancer, Skeleton, Vampire) automatically receive
vanilla-style undead potion interactions:

- **Poison**: immune (no effect)
- **Regeneration**: immune (no effect)
- **Instant Health**: deals damage instead of healing (vanilla formula `6 × 2^amplifier`)
- **Instant Damage**: heals instead of dealing damage (vanilla formula `4 × 2^amplifier`)
- **Food healing**: works normally (natural regen from saturation is not blocked)

This comes with the built-in `undead` group itself, so
`{ "type": "neoorigins:entity_group", "group": "undead" }` is the only power
JSON needed.

---

## Aquatic origin shared powers

All built-in ocean origins (Merling, Siren, Kraken, Abyssal) share these
passive powers in addition to their unique abilities:

| Power | Effect |
|---|---|
| `neoorigins:aquatic_underwater_mining` | Mines at full speed while submerged (Aqua Affinity) |
| `neoorigins:aquatic_depth_strider` | +0.15 water movement efficiency (Natural Swimmer) |
| `neoorigins:aquatic_swim_speed` | +0.25 `neoforge:swim_speed` (Open Water) |
| `neoorigins:aquatic_buoyancy` | -0.08 `minecraft:gravity` while submerged, cancelling in-water sink (Neutral Buoyancy) |
| `neoorigins:aquatic_fish_diet` | Fish-only diet (gated by `ocean_origins.fish_diet_required`; extend the allowed items via `ocean_origins.extra_fish_foods`) |
| `neoorigins:aquatic_fish_diet_bonus` | Raw cod/salmon as nourishing as cooked |

Custom aquatic origins can reference any of these shared powers by ID.

**Why two swim-speed attributes?** `minecraft:water_movement_efficiency` is
halved by vanilla whenever the swimmer is not standing on a block, so a power
built on it alone is at its weakest exactly where an ocean origin spends its
time: mid-water, clear of the seafloor. `neoforge:swim_speed` is applied
unconditionally in the same movement pass, so it is the one that still pays out
in open water, and it also feeds the vertical swim impulse that keeps a swimmer
from sinking. Use `water_movement_efficiency` for a bottom-walking or shallows
origin and `swim_speed` for a true open-water one; the built-in ocean origins
carry both.

**Moisture system:** ocean origins with `breath_out_of_fluid` replenish
moisture from water blocks, rain, bubble columns, and water cauldrons, and
Water Breathing pauses the drain for as long as it lasts. An active conduit
holds them too, and it reaches them on dry land inside its normal radius, which
makes one worth building as the dry heart of an ocean base. With Create
installed, a charged backtank worn in the chest slot holds the drain off as
well, spending one air a second, the rate it costs underwater.

---

## Suppression

`neoorigins:suppression` is a status effect the mod registers for you to hang a
boss mechanic, a debuff zone, or a PvP counter on. While a player holds it,
every active, keybind, and toggle power refuses to fire. A key press or a click
in the power GUI gets an action bar message and a short sound, so an input that
does nothing always comes with a reason. Refusals the player did not ask for are
silent: `neoorigins:activate_power` and continuous (held-key) bindings can fire
every tick, and announcing those would flood the action bar. The amplifier
carries no meaning and is reserved for later use.

Passive powers are deliberately out of scope, because you can already gate one
yourself. Put a `power_condition` on the passive that tests for the effect and
leave the mode at its `DENY` default, and the power switches off for exactly as
long as Suppression is held.

```json
{
  "type": "neoorigins:night_vision",
  "power_condition": {
    "type": "neoorigins:has_effect",
    "effect": "neoorigins:suppression"
  }
}
```

A toggle power that is already on when the effect lands is forced off rather
than left on and locked. Locking it on would strand the player
mid-transformation, with the one key that undoes it refused and nothing on
screen to explain why.

Delivery is `/effect` and the `neoorigins:apply_effect` action, and nothing else.
There is no brewing recipe, no tipped arrow, and no potion item by design: a
craftable ability lock is a balance decision that belongs to your pack, and
shipping the recipe would make that decision for every world.
