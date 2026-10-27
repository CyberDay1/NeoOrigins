---
title: "Core effect verbs"
parent: "Entity Actions"
grand_parent: "DSL Reference"
nav_order: 1
permalink: /actions/effects
---

# Core effect verbs

Effects, healing and damage, movement, sounds, items and XP, spawning entities, projectiles and areas, commands, blocks, powers, resources and cooldowns.

## `neoorigins:apply_effect`

Applies one or more mob effects to the target. Three author shapes are accepted, and **every** effect listed is applied, in declaration order:

- **Flat**: `"effect": "minecraft:speed"` with the flags on the root object.
- **Nested**: `"effect": { "effect": "minecraft:speed", "duration": 100 }`, Apoli's documented form; the id *and* the flags live inside the nested object.
- **List**: `"effects": [ { … }, { … } ]`, one spec per entry. Each entry takes the flat or nested shape. When `effects` is present the root-level `duration`/`amplifier`/flag fields are ignored. Set them per entry.

No `neoorigins:and` wrapper is needed for the multi-effect case. Entries whose effect id is unknown are skipped with a `[CompatB]` warning; if nothing resolves at all the action no-ops and warns.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effect` / `id` | resource id | yes (one of) | — | Mob effect id (e.g. `minecraft:regeneration`) |
| `duration` | int ticks | no | `200` | Effect duration |
| `amplifier` | int | no | `0` | Amplifier level (0 = level I) |
| `is_ambient` | bool | no | `false` | Ambient flag (suppresses some particles) |
| `show_particles` | bool | no | `true` | Render swirl particles |
| `show_icon` | bool | no | `true` | Show effect icon in HUD |
| `effects` | array of objects | alt | — | List form: each entry carries the same fields, and all entries are applied |

**Examples:**
```json
{ "type": "neoorigins:apply_effect", "effect": "minecraft:regeneration", "duration": 100, "amplifier": 1 }
```
```json
{
  "type": "neoorigins:apply_effect",
  "effects": [
    { "effect": "minecraft:speed", "duration": 200, "amplifier": 1 },
    { "effect": "minecraft:jump_boost", "duration": 200, "show_icon": false }
  ]
}
```

Effect ids are resolved at parse time; a missing registry entry logs a warning and that entry is skipped.

---

## `neoorigins:clear_effect`

Removes a specific mob effect, or every effect if none is specified.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effect` | resource id | no | — | Effect to remove. If omitted, all effects are cleared. |

**Example:**
```json
{ "type": "neoorigins:clear_effect", "effect": "minecraft:poison" }
```

---

## `neoorigins:heal`

Heals the target by the given amount (half-hearts).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `amount` | float | no | `1.0` | HP to restore (half-hearts) |

**Example:**
```json
{ "type": "neoorigins:heal", "amount": 4.0 }
```

---

## `neoorigins:damage`

Damages the target with a vanilla damage source.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `amount` | float | no | `1.0` | Damage to deal (half-hearts) |
| `damage_type` | string | no | — | Damage type id, resolved against the `damage_type` registry (e.g. `minecraft:freeze`). Takes priority over `source.name`, and reaches datapack-defined types the keyword list cannot. |
| `source.name` | string | no | `generic` | Damage source name. Supported: `fire`/`on_fire`/`in_fire`, `lava`, `magic`, `starve`, `drown`, `freeze`, `wither`. Anything else falls through to `generic`. |

**Example:**
```json
{ "type": "neoorigins:damage", "amount": 2.0, "source": { "name": "magic" } }
```

**Example: an explicit damage type**
```json
{ "type": "neoorigins:damage", "amount": 2.0, "damage_type": "minecraft:freeze" }
```

---

## `neoorigins:feed`

Adds food and saturation to the target's food data (same call as eating).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `food` | int | no | `1` | Food points to add |
| `saturation` | float | no | `0.0` | Saturation modifier |

**Example:**
```json
{ "type": "neoorigins:feed", "food": 6, "saturation": 0.6 }
```

---

## `neoorigins:exhaust`

Adds an exhaustion value to the target's food data (depletes saturation, eventually food).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `amount` | float | no | `1.0` | Exhaustion to add |

**Example:**
```json
{ "type": "neoorigins:exhaust", "amount": 3.0 }
```

---

## `neoorigins:set_on_fire`

Sets the target on fire for a fixed duration.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `ticks` | int | no | `20` | Fire duration in ticks |
| `duration` | int | no | — | Apoli alias for `ticks`, read only when `ticks` is absent |

**Example:**
```json
{ "type": "neoorigins:set_on_fire", "ticks": 100 }
```

---

## `neoorigins:extinguish`

Clears all fire ticks on the target. Takes no fields.

**Example:**
```json
{ "type": "neoorigins:extinguish" }
```

---

## `neoorigins:add_velocity`

Adds (or overwrites) velocity to the target. Distinguishes push vs. set via the `set` flag.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `x` | double | no | `0` | X component |
| `y` | double | no | `0` | Y component |
| `z` | double | no | `0` | Z component |
| `set` | bool | no | `false` | If true, replaces delta movement; otherwise adds via `push` |
| `space` | enum | no | `world` | Coordinate space for x/y/z: `world` (absolute axes), `local` (relative to look direction, +z = forward), `velocity` (relative to current movement direction), plus `_horizontal` variants that ignore the vertical component and `_normalized` variants that ignore the base vector's length (`local_horizontal`, `local_horizontal_normalized`, `velocity_normalized`, `velocity_horizontal`, `velocity_horizontal_normalized`) |

**Example:**
```json
{ "type": "neoorigins:add_velocity", "y": 1.2, "set": false }
```

---

## `neoorigins:launch`

Shortcut for "launch straight up." Pushes the target vertically and sets `hurtMarked` so the client syncs.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `speed` | float | no | `1.0` | Upward speed |

**Example:**
```json
{ "type": "neoorigins:launch", "speed": 1.5 }
```

---

## `neoorigins:dismount`

Forces the target to stop riding its current vehicle. Takes no fields.

**Example:**
```json
{ "type": "neoorigins:dismount" }
```

---

## `neoorigins:mount`

Makes the player start riding a nearby entity. Searches a radius around the player and mounts the first alive, non-passenger entity found; with `entity_type` set, only entities of that type are considered. Server-side only.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_type` | resource id | no | — | Entity type id to mount; omit to mount any nearby entity |
| `radius` | float | no | `5.0` | Search radius around the player |

**Example:**
```json
{ "type": "neoorigins:mount", "entity_type": "minecraft:horse", "radius": 5.0 }
```

---

## `neoorigins:set_fall_distance`

Writes directly to the target's `fallDistance` field: useful to cancel imminent fall damage.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `fall_distance` | float | no | `0.0` | New fall distance |

**Example:**
```json
{ "type": "neoorigins:set_fall_distance", "fall_distance": 0.0 }
```

---

## `neoorigins:play_sound`

Plays a sound from the target's position.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `sound` | resource id | yes | — | Sound event id |
| `volume` | float | no | `1.0` | Volume |
| `pitch` | float | no | `1.0` | Pitch |

**Example:**
```json
{ "type": "neoorigins:play_sound", "sound": "minecraft:entity.player.levelup", "volume": 1.0, "pitch": 1.0 }
```

---

## `neoorigins:emit_game_event`

Emits a vanilla game event at the target's position (for sculk sensors, warden detection, etc.).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `event` / `game_event` | resource id | yes | — | Game event id (e.g. `minecraft:step`) |

**Example:**
```json
{ "type": "neoorigins:emit_game_event", "event": "minecraft:step" }
```

---

## `neoorigins:swing_hand`

Swings the target's main hand. Takes no fields. Off-hand is not supported at this time.

**Example:**
```json
{ "type": "neoorigins:swing_hand" }
```

---

## `neoorigins:give`

Gives an item to the target. If the inventory is full, the stack drops at their feet.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `stack.item` | resource id | yes | — | Item id |
| `stack.count` / `stack.amount` | int | no | `1` | Stack size. Apoli's `amount` wins when both are set |
| `item` | resource id | alt | — | Shorthand: if `stack` is absent, the root object is treated as the stack |
| `count` / `amount` | int | alt | `1` | Stack size in shorthand form (`amount` wins when both are set) |

**Example:**
```json
{ "type": "neoorigins:give", "stack": { "item": "minecraft:apple", "count": 3 } }
```

---

## `neoorigins:add_xp`

Grants experience to the target player as points and/or whole levels. Both fields default to 0, so set at least one. Player-only.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `points` | int | no | `0` | Experience points to grant |
| `levels` | int | no | `0` | Experience levels to grant |

**Example:**
```json
{ "type": "neoorigins:add_xp", "levels": 1, "points": 50 }
```

---

## `neoorigins:spawn_entity`

Spawns an entity at the target's feet. No orientation control: the entity faces world-default.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_type` | resource id | yes | — | Entity type id |
| `quantity` | int | no | `1` | Copies to spawn. A value below 1, or a non-numeric one, warns and spawns 1. With more than one, each copy is offset randomly by up to ±0.5 blocks on X and Z |

**Example:**
```json
{ "type": "neoorigins:spawn_entity", "entity_type": "minecraft:zombie" }
```

Server-side only; on client worlds the action silently no-ops.

---

## `neoorigins:spawn_projectile`

Spawns a projectile from the target's eye height, aimed along their look vector. `neoorigins:fire_projectile` is accepted as a legacy Apoli alias for the canonical `spawn_projectile`. Non-projectile entity types fall back to a linear velocity shove along look.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_type` / `projectile` | resource id | yes | — | Entity type id |
| `speed` | float | no | `1.5` | Launch speed |
| `inaccuracy` / `divergence` | float | no | `0.0` | Random spread (`divergence` is the Apoli name; `inaccuracy` wins when both are set) |
| `vertical_offset` | float | no | `0.0` | Added to the spawn Y (relative to eye height) |
| `effect_type` | string | no | `""` | Colour key from `VfxEffectTypes`. Sets **defaults** for `orb_color`/`glow_color`/`shape`/`trail_particle`; explicit fields below override it. |
| `orb_color` | `[r,g,b]` or `"#RRGGBB"` | no | effect_type colour | Core orb colour. RGB array (0–255) or hex string. |
| `glow_color` | `[r,g,b]` or `"#RRGGBB"` | no | `orb_color` | Outer-glow colour. RGB array or hex string. |
| `size` | float | no | `0.3` | Core quad scale. |
| `glow_size` | float | no | `0.7` | Glow base scale (pulse layered on top). |
| `glow_alpha` | int 0–255 | no | `140` | Glow halo opacity. |
| `shape` | enum | no | `cross` / effect_type default | One of `cross` / `cube` / `ring` / `sphere`. |
| `trail_particle` | resource id | no | effect_type default | Vanilla particle id for the flight trail (e.g. `minecraft:witch`). |
| `count` | int | no | `1` plain / `2` orb | For plain projectiles: number spawned in one fire (shotgun spread when combined with `inaccuracy`), default `1`. For the magic orb: trail particles per tick (always one orb), default `2`. |
| `spread` | float | no | `0.05` | Trail particle position spread. |
| `trail_speed` | float | no | `0.0` | Trail particle speed/velocity. Also accepted under the Apoli legacy name `speed_particle`. |
| `no_gravity` | bool | no | `false` | When `true` the projectile ignores gravity and flies straight along its launch vector (drag still applies). Works for any projectile entity, not just the magic orb. |
| `tag` | string | no | — | SNBT compound merged onto each spawned projectile (e.g. `"{pickup:1b}"` so fired arrows can be picked up). An unparseable tag warns and is ignored |
| `projectile_action` | object | no | — | Entity action applied to the spawned projectile itself (actor = projectile), immediately on launch. Object only. On a non-living projectile only `set_on_fire`, `extinguish`, `add_velocity`, `execute_command` / `command`, `and` and `nothing` apply; other verbs are skipped |
| `on_hit_action` | object | no | — | Action fired when the projectile impacts. `area_of_effect` inside this auto-rebases to the impact point. Object only (an array is ignored) |

**Example: magic-orb with impact-AoE**
```json
{ "type": "neoorigins:spawn_projectile",
  "entity_type": "neoorigins:magic_orb",
  "speed": 1.8,
  "effect_type": "poison",
  "on_hit_action": {
    "type": "neoorigins:area_of_effect",
    "radius": 4.0,
    "entity_action": { "type": "neoorigins:apply_effect",
      "effect": "minecraft:poison", "duration": 100, "amplifier": 1 }
  } }
```

**Example: fully data-driven visuals (green sphere, purple trail)**
```json
{ "type": "neoorigins:spawn_projectile",
  "entity_type": "neoorigins:magic_orb",
  "orb_color": [60, 220, 90],
  "glow_color": "#8030FF",
  "shape": "sphere",
  "size": 0.35,
  "glow_alpha": 160,
  "trail_particle": "minecraft:witch" }
```
`effect_type` and the explicit fields compose: `effect_type` fills any field
you leave out, and any field you set wins. See
[CUSTOM_PROJECTILES.md](../CUSTOM_PROJECTILES.md) for the full visual model.

---

## `neoorigins:fire_projectile`

Legacy Apoli alias for [`neoorigins:spawn_projectile`](#neooriginsspawn_projectile): identical fields and behaviour. Use `spawn_projectile` in new packs.

**Example:**
```json
{ "type": "neoorigins:fire_projectile", "entity_type": "minecraft:arrow", "speed": 2.0 }
```

---

## `neoorigins:spawn_effect_cloud`

Spawns a vanilla area-effect cloud at the target that applies a mob effect to entities passing through it. Omit `effect` for an empty (visual-only) cloud.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effect` | resource id or object | no | — | Mob effect id (bare string), or object `{effect, duration, amplifier}`. Omit for an empty cloud |
| `duration` | int ticks | no | `200` | Cloud + effect duration |
| `amplifier` | int | no | `0` | Effect amplifier level |
| `radius` | float | no | `3.0` | Cloud radius in blocks |
| `wait_time` | int ticks | no | `10` | Ticks before the cloud starts applying its effect |

**Example:**
```json
{ "type": "neoorigins:spawn_effect_cloud", "effect": "minecraft:poison", "duration": 200, "radius": 4.0 }
```

---

## `neoorigins:spawn_particles`

Broadcasts particles from the target's position via the server, so all nearby clients see them. Simple (data-less) particle types only: data-bearing particles (`dust`/`block`/`item`) can't be reached from a plain id and no-op with a warning.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `particle` | resource id | no | `minecraft:poof` | Particle id (simple particles only) |
| `count` | int | no | `1` | Number of particles |
| `speed` | float | no | `0.0` | Particle speed / extra-data scalar |
| `offset_y` | float | no | `0.0` | Vertical offset from the player's feet |
| `spread` | object | no | — | `{x,y,z}` per-axis gaussian spread radius |
| `force` | bool | no | `false` | Apoli force-render flag (accepted; the server always broadcasts) |

**Example:**
```json
{ "type": "neoorigins:spawn_particles", "particle": "minecraft:flame", "count": 20, "offset_y": 1.0, "spread": { "x": 0.5, "y": 0.5, "z": 0.5 } }
```

---

## `neoorigins:spawn_lingering_area`

Spawns a stationary AoE entity that emits particles and, every N ticks, runs a stored action against the caster. Great for ground-marker auras, poison clouds, lingering rune effects.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | float | no | `3.0` | Horizontal radius (synched to client) |
| `duration_ticks` | int | no | `100` | Lifetime |
| `interval_ticks` | int | no | `20` | How often `entity_action` runs |
| `effect_type` | string | no | `""` | Colour key for future renderer hooks |
| `particle_type` | string | no | `minecraft:witch` | Particle emitted every 2 ticks |
| `entity_action` | object | no | — | Action fired against caster each interval; pair with `area_of_effect` inside to hit entities in radius |

Position: impact point when invoked from `on_hit_action`, else caster's feet.

**Example: poison cloud at projectile impact**
```json
{ "type": "neoorigins:spawn_lingering_area",
  "radius": 4.0,
  "duration_ticks": 120,
  "interval_ticks": 20,
  "particle_type": "minecraft:witch",
  "entity_action": {
    "type": "neoorigins:area_of_effect",
    "radius": 4.0,
    "entity_action": { "type": "neoorigins:apply_effect",
      "effect": "minecraft:poison", "duration": 60, "amplifier": 1 }
  } }
```

---

## `neoorigins:spawn_black_hole`

Spawns a gravity-well entity that pulls nearby entities toward its center and damages anything in the inner radius every 10 ticks.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | float | no | `6.0` | Outer pull radius. Inner damage radius is 30% of this. |
| `duration_ticks` | int | no | `100` | Lifetime |
| `pull_strength` | float | no | `1.5` | Inward force multiplier |
| `damage_per_tick` | float | no | `2.0` | Damage per 10-tick interval in inner radius |
| `effect_type` | string | no | `""` | Colour key for renderer |

**Example:**
```json
{ "type": "neoorigins:spawn_black_hole",
  "radius": 8.0,
  "duration_ticks": 100,
  "pull_strength": 2.0,
  "damage_per_tick": 3.0 }
```

---

## `neoorigins:spawn_tornado`

Spawns a tornado that pulls entities inward, lifts them upward, and spins them tangentially.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | float | no | `5.0` | Horizontal influence radius |
| `duration_ticks` | int | no | `100` | Lifetime |
| `pull_strength` | float | no | `1.0` | Inward force |
| `lift_strength` | float | no | `0.5` | Upward force |
| `spin_strength` | float | no | `0.5` | Tangential spin force |
| `damage_per_interval` | float | no | `2.0` | Damage every `damage_interval_ticks` (set to 0 to disable) |
| `damage_interval_ticks` | int | no | `10` | How often damage fires |
| `move_speed` | float | no | `0.2` | Blocks/tick the funnel drifts forward along the caster's facing (0 = stationary) |
| `gravity` | bool | no | `false` | When true the funnel falls under gravity until its base hits the ground, so a tornado spawned in mid-air drops while it drifts forward |
| `impact_action` | action / array | no | — | Composable action run on each damage-interval tick against every entity caught in the funnel's inner radius. When set it **replaces** the built-in `damage_per_interval`. An array is wrapped in `neoorigins:and`. |
| `effect_type` | string | no | `""` | Colour key |

**Example:**
```json
{ "type": "neoorigins:spawn_tornado",
  "radius": 6.0,
  "duration_ticks": 80,
  "pull_strength": 1.5,
  "lift_strength": 0.8,
  "spin_strength": 0.8,
  "damage_per_interval": 1.5 }
```

**Composable payload:** drop the flat damage and run any action against each caught entity every interval (e.g. fling them upward)
```json
{ "type": "neoorigins:spawn_tornado",
  "radius": 6.0,
  "duration_ticks": 120,
  "gravity": true,
  "impact_action": { "type": "neoorigins:add_velocity", "y": 0.6, "set": false } }
```

---

## `neoorigins:spawn_projectile_rain`

Rains a storm of projectiles that fall from the sky across a disk, each landing at a random moment over the duration so the storm reads as a meteor shower rather than one instant pop. A ground telegraph plays during the lead-in before anything falls. Server-side the entity owns the staggered damage: as each projectile lands it hits living entities near that ground point (caster excluded) and launches them, so the damage scales naturally with how exposed a victim is.

By default the falling object is a choreographed spectral-sword visual (`model: "sword"`); set `model` to pick a different baked mesh, or `projectile` to rain a **real** entity that falls under physics (see below).

Also registered under the legacy alias **`neoorigins:spawn_sword_rain`** (same action) so packs written before it was generalised keep working.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | float | no | `6.0` | Radius of the disk the projectiles rain across |
| `duration_ticks` | int | no | `70` | How long the storm lingers before fading |
| `count` | int | no | `16` | Number of projectiles in the storm. Legacy alias: `sword_count` |
| `damage_per_impact` | float | no | `4.0` | Flat damage each landing projectile deals to foes near it. Legacy alias: `damage_per_sword` |
| `knockup` | float | no | `0.5` | Upward velocity applied to struck foes |
| `impact_radius` | float | no | `2.0` | Horizontal radius of each projectile's hit |
| `telegraph_ticks` | int | no | `14` | Lead-in ticks the ground telegraph shows before the first projectile lands. `0` = no lead-in |
| `weapon_damage_scale` | float | no | `0.0` | Per-impact bonus = this × the caster's attack-damage attribute (folds in the held weapon), captured at cast time. `0` = flat damage |
| `impact_action` | action | no | — | Composable action run at each landing point against entities within `impact_radius`. When set it **replaces** the built-in damage/knockup; omit to keep the default damage. Accepts a single action object or an array (run in order) |
| `model` | string | no | `"sword"` | Which baked-mesh model the client renders for the falling object. Built-in: `"sword"` (spectral blade). Unknown ids fall back to `"sword"`. Ignored when `projectile` is set |
| `projectile` | string | no | — | **Real-projectile mode.** A registered entity-type id (e.g. `minecraft:arrow`, `minecraft:trident`, `minecraft:snowball`) to rain instead of the choreographed baked-mesh blade. Each scatter point spawns this **actual** entity from the sky and lets it fall under real physics. `impact_action` becomes the projectile's on-hit (caster-owned projectiles only). Unknown/unset keeps the spectral-blade visual |
| `projectile_speed` | float | no | `1.0` | Initial downward launch speed of each rained entity; gravity then accelerates it. Only used when `projectile` is set |
| `spawn_height` | float | no | `18.0` | Blocks above each scatter point the entity spawns from. Only used when `projectile` is set |
| `tag` | string | no | — | SNBT compound merged onto each rained entity (e.g. `"{pickup:1b}"` so fired arrows can be picked up). Only used when `projectile` is set |
| `follow_terrain` | bool | no | `true` | Each blade/projectile lands on the surface Y under its own scatter point (`MOTION_BLOCKING` heightmap), so the storm follows hills/stairs instead of all dropping on one flat plane. Set `false` to lock every drop to the storm center's Y |
| `origin` | string | no | `"self"` | Where the storm centers: `"self"` (around caster), `"look"` (what the caster aims at), or `"impact"` (projectile hit point, when cast from an on-hit context). Inside an on-hit context `"self"` also centres on the hit point; outside one `"impact"` centres on the caster |
| `effect_type` | string | no | `""` | Colour key |

In **real-projectile mode** the rain keeps owning the scatter pattern, the staggered launch schedule and the lead-in telegraph, but each blade is swapped for a genuine entity that flies and hits on its own, so you can rain any projectile you could otherwise `spawn_projectile`/summon (arrows, tridents, snowballs, fireballs, …). The client skips the fake-blade render so the visuals don't double up.

**Example:** a heavy spectral-sword storm centered on what the caster is looking at, scaling with the held weapon, that applies Glowing instead of the built-in damage
```json
{ "type": "neoorigins:spawn_projectile_rain",
  "origin": "look",
  "radius": 8.0,
  "duration_ticks": 100,
  "count": 40,
  "weapon_damage_scale": 0.4,
  "impact_action": {
    "type": "neoorigins:apply_effect",
    "effect": "minecraft:glowing",
    "duration": 60
  } }
```

**Example:** rain real arrows from the sky that fall under physics and stick where they land, scattered over the duration
```json
{ "type": "neoorigins:spawn_projectile_rain",
  "origin": "look",
  "radius": 7.0,
  "duration_ticks": 80,
  "count": 30,
  "projectile": "minecraft:arrow",
  "projectile_speed": 1.5,
  "tag": "{pickup:1b}" }
```

**Example:** *throw a sword, and where it lands becomes the storm's centre.* This composes two primitives: [`spawn_projectile`](#neooriginsspawn_projectile) hurls a `neoorigins:thrown_sword` (a spinning spectral blade that flies under physics) along the caster's aim, and its `on_hit_action` fires `spawn_projectile_rain` with `origin: "impact"`, so the rain centres on the blade's landing point rather than the caster:
```json
{ "type": "neoorigins:spawn_projectile",
  "entity_type": "neoorigins:thrown_sword",
  "speed": 1.6,
  "on_hit_action": {
    "type": "neoorigins:spawn_projectile_rain",
    "origin": "impact",
    "radius": 11,
    "count": 56,
    "duration_ticks": 200,
    "telegraph_ticks": 14,
    "damage_per_impact": 4,
    "weapon_damage_scale": 0.4,
    "knockup": 0.6,
    "impact_radius": 2.5
  } }
```

---

## `neoorigins:spawn_sword_rain`

Legacy alias for [`neoorigins:spawn_projectile_rain`](#neooriginsspawn_projectile_rain): same action and fields, kept so packs written before the verb was generalised keep working. The legacy field names `sword_count` (→ `count`) and `damage_per_sword` (→ `damage_per_impact`) are accepted. Prefer `spawn_projectile_rain` in new packs.

**Example:**
```json
{ "type": "neoorigins:spawn_sword_rain", "radius": 6.0, "sword_count": 16, "damage_per_sword": 4.0 }
```

---

## `neoorigins:spawn_telegraph`

Spawns a particle-only ground danger marker: a static outer ring marking the full footprint plus a reticle ring that contracts toward the centre over the wind-up, then optionally fires a composable action when it expires. Use it as a reusable "marked, dodgeable zone that pays off at the end". The telegraph and the payoff are composed in the datapack rather than hard-wired together. The marker has no model and needs no client assets.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | float | no | `3.0` | Radius of the marked danger zone |
| `duration_ticks` | int | no | `20` | Wind-up length: ticks the reticle takes to contract before it expires |
| `on_expire` | action | no | — | Composable action run once when the wind-up ends, against entities within `radius` (caster excluded). Omit for a pure dodge cue with no payoff. Accepts a single action object or an array (run in order) |
| `origin` | string | no | `"self"` | Where the marker centers: `"self"` (at caster), `"look"` (what the caster aims at), or `"impact"` (projectile hit point). Same resolution as `spawn_projectile_rain`: inside an on-hit context `"self"` also uses the hit point |
| `effect_type` | string | no | `""` | Colour key |

**Example:** a 1-second marker on what the caster aims at that detonates for damage when it expires
```json
{ "type": "neoorigins:spawn_telegraph",
  "origin": "look",
  "radius": 4.0,
  "duration_ticks": 20,
  "on_expire": {
    "type": "neoorigins:damage",
    "amount": 10.0
  } }
```

---

## `neoorigins:execute_command`

Runs a server command at permission level 2 (vanilla's function-permission-level default). Works for non-op players. Mirrors upstream Origins behaviour. Output is suppressed.

Also accepted under the alias `command` (Apoli's verb name, same field shape), so `apoli:command` / `origins:command` actions in imported packs dispatch here directly. The alias is the same factory: the server-config command blacklist (`command_power_blacklist` under `[command_powers]`) applies identically to both spellings.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `command` | string | yes | — | Command line (no leading slash) |

**Examples:**
```json
{ "type": "neoorigins:execute_command", "command": "effect give @s minecraft:glowing 10 0" }
```

A text component is itself JSON, so its quotes must be escaped inside the `command` string:
```json
{ "type": "neoorigins:execute_command", "command": "tellraw @s {\"text\":\"Triggered\"}" }
```

Written as `tellraw @s {"Triggered"}`, the same command does not parse, because `{"Triggered"}` is not a valid text component.

Runs only on the server side, and a blank `command` does nothing.

A command that does not parse is logged with the id of the power that ran it (or only the site, when no power is known) and the game's own reason for refusing it, once for each combination of power, site and command text. Because output is suppressed, that log line is the only report a syntax error produces: the action itself runs nothing and raises nothing.

Up to 2.2.29 no such line was written: a command that did not parse was dropped with nothing in the log.

### Position resolution

The command source is positioned at the **player by default**. When the dispatch context carries a block position, the source is repositioned at that **block's centre**, so `~ ~ ~` resolves to it: `block_break` / `block_place` events, `block_use` / `bonemeal` interactions, a `raycast` block hit, and the block published by `block_action_at`, `offset` or the `area_of_effect` block fan-out. This lets pack authors write the standard "drop loot at the block" recipe without manual coord lookup:

```json
{
  "type": "neoorigins:action_on_event",
  "event": "block_break",
  "block_condition": { "type": "neoorigins:block", "id": "minecraft:stone" },
  "entity_action": {
    "type": "neoorigins:execute_command",
    "command": "loot spawn ~ ~ ~ loot mypack:generic/stone_drops"
  }
}
```

For other contexts (`hit_taken`, `kill`, `tick`, a projectile hit, etc.) the source stays at the player.

---

## `neoorigins:command`

Apoli-verb alias for [`neoorigins:execute_command`](#neooriginsexecute_command): same factory, same `command` field, same level-2 permission and command blacklist. Lets `apoli:command` / `origins:command` actions in imported packs dispatch directly.

**Example:**
```json
{ "type": "neoorigins:command", "command": "say hello" }
```

---

## `neoorigins:drop_items`

Drops one or more item stacks at the dispatch position. Inline alternative to authoring a vanilla loot table: pack authors who want "5% chance to drop a diamond when you break stone" don't need a separate `data/.../loot_table/...json` file.

Position resolution mirrors `execute_command`: drops at the dispatch BlockPos for block events, at the player for everything else.

### Top-level fields

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `items` | array | yes | — | List of drop entries |
| `mode` | string | no | `"each"` | `"each"` (per-entry independent rolls) or `"one_of"` (weighted single pick; `"single"` and `"weighted"` are synonyms). Case-insensitive; any other value behaves as `"each"` |
| `rolls` | int | no | `1` | Only used in `"one_of"` mode: repeat the pick with replacement |

### Per-entry fields

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `item` | resource id | yes | — | Item registry id |
| `count` | int OR `[min, max]` | no | `1` | Exact count or inclusive range |
| `chance` | float (0.0–1.0) | no | `1.0` | Per-entry roll. Only used in `"each"` mode |
| `weight` | int | no | `1` | Selection weight. Only used in `"one_of"` mode |

### Modes

**`each`** (default): every entry rolls its own `chance` independently. Multiple drops possible per trigger. Mirrors a multi-pool vanilla loot table where every pool has a `random_chance` condition:

```json
{
  "type": "neoorigins:drop_items",
  "items": [
    { "item": "minecraft:diamond", "count": 1,      "chance": 0.05 },
    { "item": "minecraft:emerald", "count": [1, 3], "chance": 0.10 }
  ]
}
```

**`one_of`**: exactly one entry is picked, weighted by each entry's `weight`. The picked entry's `count` range still rolls. Mirrors a single vanilla loot pool with `rolls: 1`:

```json
{
  "type": "neoorigins:drop_items",
  "mode": "one_of",
  "items": [
    { "item": "minecraft:diamond",    "weight": 1,  "count": [1, 2] },
    { "item": "minecraft:emerald",    "weight": 4,  "count": [2, 5] },
    { "item": "minecraft:gold_ingot", "weight": 10, "count": 1      }
  ]
}
```

Total weight 1+4+10 = 15. Diamond 6.7%, emerald 27%, gold 67%. The picked type rolls its own count range.

Use `rolls: N` to repeat the pick (with replacement). The same item type can win multiple rolls.

### Notes

- Unknown item ids log a one-shot warning at parse time and skip that entry.
- Dropped items get vanilla's default pickup delay of 10 ticks (0.5 s).
- For random_count without `chance`, omit `chance` (defaults to 1.0 = always).
- For 80% chance to drop a single item, just `{ "item": "...", "chance": 0.8 }`.

---

## `neoorigins:set_block`

Replaces a block with the given block's default state. When the action runs inside a block context — a `block_use` or bonemeal interaction, a projectile or raycast block hit, or a `block_action_at` / `offset` wrapper — it replaces the block that context resolved to. With no block context active it falls back to the block at the target's feet (their `blockPosition`).

It is also a block-target verb, so it can be named directly inside `block_target_action` alongside `strip`, `till`, `path`, `grow` and `transform_block`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `block` | resource id | yes | — | Block id |
| `keep` | bool | no | `false` | Only place when the target position is already air |

**Example:**
```json
{ "type": "neoorigins:set_block", "block": "minecraft:cobweb" }
```

---

## `neoorigins:modify_food`

Mutates the target's food/saturation levels. **Gotcha:** upstream Apoli's `modify_food` is contextual to an item-use hook. This port applies the delta as a one-shot adjustment because our action context has no item-stack reference.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `food` / `food_component_food` | int | no | `0` | Food delta, clamped to [0,20] |
| `saturation` / `food_component_saturation` | float | no | `0.0` | Saturation delta, clamped to [0, newFood] |

**Example:**
```json
{ "type": "neoorigins:modify_food", "food": 4, "saturation": 0.4 }
```

---

## `neoorigins:grant_power`

Dynamically grants a power to the target. Tracks dynamic grants separately from origin-granted powers so later `revoke_power` calls don't strip origin-granted ones. Fires `PowerGrantedEvent` and syncs to the client.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` / `power_id` | resource id | yes | — | Power id |

**Example:**
```json
{ "type": "neoorigins:grant_power", "power": "examplepack:super_jump" }
```

---

## `neoorigins:revoke_power`

Removes a previously `grant_power`ed power. No-op if the power was granted by an origin (not dynamic).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` / `power_id` | resource id | yes | — | Power id |

**Example:**
```json
{ "type": "neoorigins:revoke_power", "power": "examplepack:super_jump" }
```

---

## `neoorigins:activate_power`

Triggers another power's activation exactly as if the player pressed its key: the target power's `condition`, `cooldown`, and `fail_action` all apply. The player must currently have the target power (origin-granted or dynamic). Reaches skill-slot actives (`active_self`, `toggle`, `launch`) and named-hotkey powers; powers driven purely by vanilla input state (`key.sneak`, `key.use`, …) have no activation entry point and cannot be reached. Recursive activation (a power activating itself, directly or through a cycle) is detected and blocked with a log warning.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` | resource id | yes | — | Power id to activate |

**Example:**
```json
{ "type": "neoorigins:activate_power", "power": "examplepack:fireball" }
```

---

## `neoorigins:open_layer_picker`

Reopens the origin selection screen scoped to an author-specified subset of layers, so a pack can offer a "re-pick these layers" item or power for any layer set (generalizes the Orb of Class). The scoped layers are cleared (their powers revoked) and the picker re-shows them; every other layer stays chosen. Runs server-side.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `layers` | array of resource ids | yes | — | Layer ids to reopen the picker for. Unknown or hidden layers are skipped; if none are valid the action does nothing |
| `commit_mode` | enum | no | `"deferred"` | `deferred`: `cost` is charged on the first pick and closing the picker is a free cancel that restores the prior origins. `immediate`: `cost` is charged up front and closing the picker leaves the layers empty (the auto-default fills them, e.g. class → nitwit) |
| `cost` | int | no | `0` | XP levels charged when the player commits a pick (deferred) or immediately (immediate). 0 = free |
| `message` | string | no | — | Chat line shown to the player when the picker opens. A translation key resolves against the active language; a plain string is shown as-is |
| `consume_item` | bool | no | `false` | When true, one of the player's main-hand item (the item that triggered this action, e.g. a datapack orb) is removed when the pick commits. Creative players keep the item. Pair with an item-use trigger: on a keybind it would consume whatever is held |

**Example:**
```json
{ "type": "neoorigins:open_layer_picker", "layers": ["neoorigins:class"], "commit_mode": "deferred", "cost": 3, "message": "Choose a new class", "consume_item": true }
```

The `/neoorigins gui <player> <layers>` command (also reachable as `/origin gui`) is the admin equivalent; it takes one or more comma- or space-separated layer ids (e.g. `/neoorigins gui Steve neoorigins:origin,neoorigins:class`).

For a full worked example (binding one of the four inert orb items to a custom layer, granting the power, and adding a crafting recipe), see [COOKBOOK.md § 16](../COOKBOOK.md#reroll-my-custom-layer-wiring-up-a-spare-orb).

---

## `neoorigins:explode`

Creates an explosion centred on the target.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` | float | no | `3.0` | Explosion radius/strength |
| `destruction_type` | string | no | `"none"` | Absent or `"none"` leaves blocks intact; any other value (e.g. `"break"`) lets the explosion break blocks |
| `create_fire` | bool | no | `false` | Leave fire behind |

**Example:**
```json
{ "type": "neoorigins:explode", "power": 4.0, "destruction_type": "break", "create_fire": false }
```

Server-side only.

The explosion is always created at the player, whatever position the surrounding action resolved. At most four are created per player per tick, counted across every action that asks for one; the fifth and later in the same tick are skipped without a message. They all land on the same player, and hurt immunity after the first absorbs most of what the rest would deal, so the extras cost server time without adding effect.

---

## `neoorigins:gain_air`

Restores air supply (bubbles), clamped to `getMaxAirSupply()`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `amount` | int | no | `10` | Air ticks to add |

**Example:**
```json
{ "type": "neoorigins:gain_air", "amount": 40 }
```

---

## `neoorigins:change_resource`

Mutates a `resource` power's stored integer. The resource state lives on a player attachment, keyed by power id. Also accepted under the Apoli legacy alias `modify_resource` (same field shape).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `resource` | resource id | yes | — | Power id owning the resource |
| `operation` | string | no | `"add"` | `"add"` or `"set"`. Apoli's `set_total` and `set_value` also set; every other value (`add_base`, `addition`, `multiply_*`, unknown strings) adds |
| `change` | int | no | `0` | Value to add or set. Read as an integer: a fractional value is truncated |
| `modifier` | object | no | — | Apoli form `{ "operation": …, "amount": … }` (`value` is accepted for `amount`). When present it replaces the root `operation` / `change` |

**Example:**
```json
{ "type": "neoorigins:change_resource", "resource": "examplepack:mana", "operation": "add", "change": -5 }
```

`add` clamps the result to the resource's own `min` / `max` (or a declared variable's bounds); `set` writes the value unclamped. On a resource backed by Iron's Spells mana only `add` applies; `set` is ignored with a one-time log line.

> ⚠️ `resource` is normally the **full** namespaced power id. A `resource` containing `*` is a glob (`*` matches any run of characters) over the resource keys already stored on the player: `add` / `set` apply to every matching key, and nothing happens while no key matches yet, so the matching resource power must have been granted first. Inside an `origins:multiple` sub-power, `*:*` self-references are resolved against the parent power before this runs. The same rules apply to `set_resource` and the `neoorigins:resource` condition.

---

## `neoorigins:modify_resource`

Apoli legacy alias for [`neoorigins:change_resource`](#neooriginschange_resource): same factory and field shape (`resource`, `operation`, `change`). Apoli's nested `modifier` object (`{operation, amount}`, or `value` for `amount`) is accepted in place of the root `operation` / `change`. Prefer `change_resource` in new packs.

**Example:**
```json
{ "type": "neoorigins:modify_resource", "resource": "examplepack:mana", "change": 10 }
```

---

## `neoorigins:modify_temperature`

Changes a player's temperature via **[Cold Sweat](https://modrinth.com/mod/cold-sweat)** (`cold_sweat`). This is an integration action: it only does anything when Cold Sweat is installed. Gate any power that uses it with the top-level `"required_mods": ["cold_sweat"]` field so the power doesn't even load without the mod. Without that gate the action degrades to a logged no-op when Cold Sweat is absent.

Cold Sweat's `core` (body) temperature runs on a scale of roughly **−100** (freezing death) … **+100** (burning death), with **0** neutral. The `base`/`world` traits use the ambient scale; the resistance/dampening traits sit in a ~0..1 range. Positive `amount` warms, negative cools.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `trait` | enum | no | `core` | Which trait to modify: `core`, `base`, `world`, `heat_resistance`, `cold_resistance`, `heat_dampening`, `cold_dampening`, `freezing_point`, `burning_point`, `rate` |
| `amount` | number | yes | — | How much to change by (positive warms, negative cools). ±1 is a small `core` nudge; ±100 spans the survivable band. Resistance/dampening traits want ~0..1 |
| `operation` | enum | no | `add` | `add` treats `amount` as a delta; `set` overwrites the trait to `amount` outright |

**Example: warm the player up when an ability fires**
```json
{
  "type": "neoorigins:active_ability",
  "key": "key.neoorigins.warmth",
  "cooldown_ticks": 100,
  "entity_action": {
    "type": "neoorigins:modify_temperature",
    "trait": "core",
    "amount": 40,
    "operation": "add"
  }
}
```

---

## `neoorigins:set_resource`

Assigns a `resource` power's stored integer to a fixed value (the `set`-only sibling of `change_resource`). Same attachment-backed state and the same `resource` rules, including `*` globs over stored keys. The value is written unclamped; on a resource backed by Iron's Spells mana it is ignored.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `resource` | resource id | yes | — | Power id owning the resource |
| `value` / `change` | int | no | `0` | Value to assign (`change` is an accepted alias) |

**Example:**
```json
{ "type": "neoorigins:set_resource", "resource": "examplepack:mana", "value": 0 }
```

---

## `neoorigins:trigger_cooldown`

Manually places a power on cooldown. Used when an ability's fire path is custom but should still show the HUD cooldown bar.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` | resource id | yes | — | Power id to cool down |
| `cooldown` | int ticks | no | `20` | Cooldown duration |

**Example:**
```json
{ "type": "neoorigins:trigger_cooldown", "power": "examplepack:fireball", "cooldown": 40 }
```

> **Arming imported `origins:cooldown` powers.** This action also arms imported Apoli cooldown powers (see [`origins:cooldown`](../powers/apoli-compat.md#originscooldown)): any resource key matching `power` (wildcard globs like `*:*_timer` included) that belongs to a cooldown power is set to that power's registered duration, starting its countdown. The `cooldown` field is ignored on that path; the duration comes from the cooldown power's own JSON.
