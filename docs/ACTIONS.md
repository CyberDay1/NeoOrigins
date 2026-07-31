# NeoOrigins 2.0 Entity Action Reference

Entity actions run against an entity target (usually the player who owns the power, or a bientity target depending on the call site). They're the side-effect half of the DSL — conditions filter, actions mutate.

**Canonical namespace:** `neoorigins:*` is the preferred form for new packs. Legacy `origins:*` and `apace:*` prefixes still work but log a one-shot `[2.0-legacy]` deprecation warning. Bare type names like `"type": "heal"` are auto-prefixed to `neoorigins:heal`. Section headers below still show the traditional `origins:*` names for familiarity with upstream docs; JSON examples use `neoorigins:*`.

**Call sites that dispatch actions:**
- `action_on_event.entity_action` — runs against the event's actor (player)
- `action_on_hit.entity_action` / `action_on_hit_taken.entity_action` — bientity (actor + target)
- `conditional.inner_action` — gated by the wrapping condition
- Nested inside meta verbs (`if_else`, `if_else_list`, `and`, `chance`, `delay`, `area_of_effect`)

**Object or array.** Every entity-action field — `entity_action`, `else_action`, `fail_action`, and the nested actions of the meta verbs (`if_else`, `if_else_list`, `chance`, `delay`, `choice`, `area_of_effect`, `raycast`'s `block_action`/`bientity_action`, etc.) — accepts either a single action object **or** an array of them, run in order (an implicit `neoorigins:and`). An empty array, or entries that aren't objects, no-op. *(Previously a bare array in these fields silently did nothing; wrapping in `neoorigins:and` was required.)* The **item-action** verbs (the `(item)` section below) and `block_target_action`'s `action` remain object-only — sequence those with `neoorigins:and (item)`.

On any parse error or unknown `type`, the action silently degrades to a no-op and logs a warning tagged `[CompatB]`.

---

# Core effect verbs

## `neoorigins:apply_effect`

Applies one or more mob effects to the target. Three author shapes are accepted, and **every** effect listed is applied, in declaration order:

- **Flat** — `"effect": "minecraft:speed"` with the flags on the root object.
- **Nested** — `"effect": { "effect": "minecraft:speed", "duration": 100 }`, Apoli's documented form; the id *and* the flags live inside the nested object.
- **List** — `"effects": [ { … }, { … } ]`, one spec per entry. Each entry takes the flat or nested shape. When `effects` is present the root-level `duration`/`amplifier`/flag fields are ignored — set them per entry.

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
| `source.name` | string | no | `generic` | Damage source name. Supported: `fire`/`on_fire`/`in_fire`, `lava`, `magic`, `starve`, `drown`, `freeze`, `wither`. Anything else falls through to `generic`. |

**Example:**
```json
{ "type": "neoorigins:damage", "amount": 2.0, "source": { "name": "magic" } }
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

Writes directly to the target's `fallDistance` field — useful to cancel imminent fall damage.

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

Swings the target's main hand. Takes no fields — off-hand is not supported at this time.

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
| `stack.count` | int | no | `1` | Stack size |
| `item` | resource id | alt | — | Shorthand: if `stack` is absent, the root object is treated as the stack |
| `count` | int | alt | `1` | Stack size in shorthand form |

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

Spawns an entity at the target's feet. No orientation control — the entity faces world-default.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_type` | resource id | yes | — | Entity type id |

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
| `inaccuracy` | float | no | `0.0` | Random spread |
| `vertical_offset` | float | no | `0.0` | Added to the spawn Y (relative to eye height) |
| `effect_type` | string | no | `""` | Colour key from `VfxEffectTypes`. Sets **defaults** for `orb_color`/`glow_color`/`shape`/`trail_particle`; explicit fields below override it. |
| `orb_color` | `[r,g,b]` or `"#RRGGBB"` | no | effect_type colour | Core orb colour. RGB array (0–255) or hex string. |
| `glow_color` | `[r,g,b]` or `"#RRGGBB"` | no | `orb_color` | Outer-glow colour. RGB array or hex string. |
| `size` | float | no | `0.3` | Core quad scale. |
| `glow_size` | float | no | `0.7` | Glow base scale (pulse layered on top). |
| `glow_alpha` | int 0–255 | no | `140` | Glow halo opacity. |
| `shape` | enum | no | `cross` / effect_type default | One of `cross` / `cube` / `ring` / `sphere`. |
| `trail_particle` | resource id | no | effect_type default | Vanilla particle id for the flight trail (e.g. `minecraft:witch`). |
| `count` | int | no | `1` | For plain projectiles: number spawned in one fire (shotgun spread when combined with `inaccuracy`). For the magic orb: trail particles per tick (always one orb). |
| `spread` | float | no | `0.05` | Trail particle position spread. |
| `trail_speed` | float | no | `0.0` | Trail particle speed/velocity. Also accepted under the Apoli legacy name `speed_particle`. |
| `no_gravity` | bool | no | `false` | When `true` the projectile ignores gravity and flies straight along its launch vector (drag still applies). Works for any projectile entity, not just the magic orb. |
| `projectile_action` | object | no | — | Entity action applied to the spawned projectile itself (actor = projectile), immediately on launch. |
| `on_hit_action` | object | no | — | Action fired when the projectile impacts. `area_of_effect` inside this auto-rebases to the impact point. |

**Example — magic-orb with impact-AoE:**
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

**Example — fully data-driven visuals (green sphere, purple trail):**
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
[CUSTOM_PROJECTILES.md](CUSTOM_PROJECTILES.md) for the full visual model.

---

## `neoorigins:fire_projectile`

Legacy Apoli alias for [`neoorigins:spawn_projectile`](#neooriginsspawn_projectile) — identical fields and behaviour. Use `spawn_projectile` in new packs.

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

**Example — poison cloud at projectile impact:**
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

**Composable payload** — drop the flat damage and run any action against each caught entity every interval (e.g. fling them upward):
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
| `weapon_damage_scale` | float | no | `0.0` | Per-impact bonus = this × the caster's attack-damage attribute (folds in the held weapon), captured at cast time. `0` = flat damage |
| `impact_action` | action | no | — | Composable action run at each landing point against entities within `impact_radius`. When set it **replaces** the built-in damage/knockup; omit to keep the default damage. Accepts a single action object or an array (run in order) |
| `model` | string | no | `"sword"` | Which baked-mesh model the client renders for the falling object. Built-in: `"sword"` (spectral blade). Unknown ids fall back to `"sword"`. Ignored when `projectile` is set |
| `projectile` | string | no | — | **Real-projectile mode.** A registered entity-type id (e.g. `minecraft:arrow`, `minecraft:trident`, `minecraft:snowball`) to rain instead of the choreographed baked-mesh blade. Each scatter point spawns this **actual** entity from the sky and lets it fall under real physics. `impact_action` becomes the projectile's on-hit (caster-owned projectiles only). Unknown/unset keeps the spectral-blade visual |
| `projectile_speed` | float | no | `1.0` | Initial downward launch speed of each rained entity; gravity then accelerates it. Only used when `projectile` is set |
| `spawn_height` | float | no | `18.0` | Blocks above each scatter point the entity spawns from. Only used when `projectile` is set |
| `tag` | string | no | — | SNBT compound merged onto each rained entity (e.g. `"{pickup:1b}"` so fired arrows can be picked up). Only used when `projectile` is set |
| `follow_terrain` | bool | no | `true` | Each blade/projectile lands on the surface Y under its own scatter point (`MOTION_BLOCKING` heightmap), so the storm follows hills/stairs instead of all dropping on one flat plane. Set `false` to lock every drop to the storm center's Y |
| `origin` | string | no | `"self"` | Where the storm centers: `"self"` (around caster), `"look"` (what the caster aims at), or `"impact"` (projectile hit point, when cast from an on-hit context) |
| `effect_type` | string | no | `""` | Colour key |

In **real-projectile mode** the rain keeps owning the scatter pattern, the staggered launch schedule and the lead-in telegraph, but each blade is swapped for a genuine entity that flies and hits on its own — so you can rain any projectile you could otherwise `spawn_projectile`/summon (arrows, tridents, snowballs, fireballs, …). The client skips the fake-blade render so the visuals don't double up.

**Example** — a heavy spectral-sword storm centered on what the caster is looking at, scaling with the held weapon, that applies Glowing instead of the built-in damage:
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

**Example** — rain real arrows from the sky that fall under physics and stick where they land, scattered over the duration:
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

**Example** — *throw a sword, and where it lands becomes the storm's centre.* This composes two primitives: [`spawn_projectile`](#neooriginsspawn_projectile) hurls a `neoorigins:thrown_sword` (a spinning spectral blade that flies under physics) along the caster's aim, and its `on_hit_action` fires `spawn_projectile_rain` with `origin: "impact"`, so the rain centres on the blade's landing point rather than the caster:
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

Legacy alias for [`neoorigins:spawn_projectile_rain`](#neooriginsspawn_projectile_rain) — same action and fields, kept so packs written before the verb was generalised keep working. The legacy field names `sword_count` (→ `count`) and `damage_per_sword` (→ `damage_per_impact`) are accepted. Prefer `spawn_projectile_rain` in new packs.

**Example:**
```json
{ "type": "neoorigins:spawn_sword_rain", "radius": 6.0, "sword_count": 16, "damage_per_sword": 4.0 }
```

---

## `neoorigins:spawn_telegraph`

Spawns a particle-only ground danger marker: a static outer ring marking the full footprint plus a reticle ring that contracts toward the centre over the wind-up, then optionally fires a composable action when it expires. Use it as a reusable "marked, dodgeable zone that pays off at the end" — the telegraph and the payoff are composed in the datapack rather than hard-wired together. The marker has no model and needs no client assets.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | float | no | `3.0` | Radius of the marked danger zone |
| `duration_ticks` | int | no | `20` | Wind-up length: ticks the reticle takes to contract before it expires |
| `on_expire` | action | no | — | Composable action run once when the wind-up ends, against entities within `radius` (caster excluded). Omit for a pure dodge cue with no payoff. Accepts a single action object or an array (run in order) |
| `origin` | string | no | `"self"` | Where the marker centers: `"self"` (at caster), `"look"` (what the caster aims at), or `"impact"` (projectile hit point) |
| `effect_type` | string | no | `""` | Colour key |

**Example** — a 1-second marker on what the caster aims at that detonates for damage when it expires:
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

Runs a server command at permission level 2 (vanilla's function-permission-level default). Works for non-op players — mirrors upstream Origins behaviour. Output is suppressed.

Also accepted under the alias `command` (Apoli's verb name, same field shape), so `apoli:command` / `origins:command` actions in imported packs dispatch here directly. The alias is the same factory: the server-config command blacklist (`command_power_blacklist` under `[command_powers]`) applies identically to both spellings.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `command` | string | yes | — | Command line (no leading slash) |

**Example:**
```json
{ "type": "neoorigins:execute_command", "command": "effect give @s minecraft:glowing 10 0" }
```

---

## `neoorigins:command`

Apoli-verb alias for [`neoorigins:execute_command`](#neooriginsexecute_command) — same factory, same `command` field, same level-2 permission and command blacklist. Lets `apoli:command` / `origins:command` actions in imported packs dispatch directly.

**Example:**
```json
{ "type": "neoorigins:command", "command": "say hello" }
```

Runs only if the target is on a server (`player.level().getServer() != null`).

### Position resolution

The command source is positioned at the **player by default**. When the
dispatch context is a block-shaped event (`block_break`, `block_place`,
`block_use`), the command source is repositioned at the **block's centre**
instead — so `~ ~ ~` resolves to the broken/placed/used block. Matches
Apoli's `block_action` pattern; lets pack authors write the standard
"drop loot at the block" recipe without manual coord lookup:

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

For non-block events (`hit_taken`, `kill`, `tick`, etc.) the source
stays at the player.

---

## `neoorigins:drop_items`

Drops one or more item stacks at the dispatch position. Inline alternative to authoring a vanilla loot table — pack authors who want "5% chance to drop a diamond when you break stone" don't need a separate `data/.../loot_table/...json` file.

Position resolution mirrors `execute_command`: drops at the dispatch BlockPos for block events, at the player for everything else.

### Top-level fields

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `items` | array | yes | — | List of drop entries |
| `mode` | string | no | `"each"` | `"each"` (per-entry independent rolls) or `"one_of"` (weighted single pick) |
| `rolls` | int | no | `1` | Only used in `"one_of"` mode — repeat the pick with replacement |

### Per-entry fields

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `item` | resource id | yes | — | Item registry id |
| `count` | int OR `[min, max]` | no | `1` | Exact count or inclusive range |
| `chance` | float (0.0–1.0) | no | `1.0` | Per-entry roll. Only used in `"each"` mode |
| `weight` | int | no | `1` | Selection weight. Only used in `"one_of"` mode |

### Modes

**`each`** (default) — every entry rolls its own `chance` independently. Multiple drops possible per trigger. Mirrors a multi-pool vanilla loot table where every pool has a `random_chance` condition:

```json
{
  "type": "neoorigins:drop_items",
  "items": [
    { "item": "minecraft:diamond", "count": 1,      "chance": 0.05 },
    { "item": "minecraft:emerald", "count": [1, 3], "chance": 0.10 }
  ]
}
```

**`one_of`** — exactly one entry is picked, weighted by each entry's `weight`. The picked entry's `count` range still rolls. Mirrors a single vanilla loot pool with `rolls: 1`:

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

Use `rolls: N` to repeat the pick (with replacement) — same item type can win multiple rolls.

### Notes

- Unknown item ids log a one-shot warning at parse time and skip that entry.
- Dropped items have default pickup delay (no 0.5s wait), so the triggering player can pick them up immediately.
- For random_count without `chance`, omit `chance` (defaults to 1.0 = always).
- For 80% chance to drop a single item, just `{ "item": "...", "chance": 0.8 }`.

---

## `neoorigins:set_block`

Replaces the block at the target's feet (their `blockPosition`) with the given block's default state.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `block` | resource id | yes | — | Block id |

**Example:**
```json
{ "type": "neoorigins:set_block", "block": "minecraft:cobweb" }
```

---

## `neoorigins:modify_food`

Mutates the target's food/saturation levels. **Gotcha:** upstream Apoli's `modify_food` is contextual to an item-use hook — this port applies the delta as a one-shot adjustment because our action context has no item-stack reference.

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

Triggers another power's activation exactly as if the player pressed its key — the target power's `condition`, `cooldown`, and `fail_action` all apply. The player must currently have the target power (origin-granted or dynamic). Reaches skill-slot actives (`active_self`, `toggle`, `launch`) and named-hotkey powers; powers driven purely by vanilla input state (`key.sneak`, `key.use`, …) have no activation entry point and cannot be reached. Recursive activation (a power activating itself, directly or through a cycle) is detected and blocked with a log warning.

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
| `consume_item` | bool | no | `false` | When true, one of the player's main-hand item (the item that triggered this action, e.g. a datapack orb) is removed when the pick commits. Creative players keep the item. Pair with an item-use trigger — on a keybind it would consume whatever is held |

**Example:**
```json
{ "type": "neoorigins:open_layer_picker", "layers": ["neoorigins:class"], "commit_mode": "deferred", "cost": 3, "message": "Choose a new class", "consume_item": true }
```

The `/origin gui <player> <layers>` command is the admin equivalent; it takes one or more comma- or space-separated layer ids (e.g. `/origin gui Steve neoorigins:origin,neoorigins:class`).

---

## `neoorigins:explode`

Creates an explosion centred on the target.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` | float | no | `3.0` | Explosion radius/strength |
| `destruction_type` | string | no | `"break"` | If set to `"none"`, no blocks break; any other value (or absent) lets blocks break |
| `create_fire` | bool | no | `false` | Leave fire behind |

**Example:**
```json
{ "type": "neoorigins:explode", "power": 4.0, "destruction_type": "break", "create_fire": false }
```

Server-side only.

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
| `operation` | string | no | `"add"` | `"add"` (default) or `"set"`. Unknown values fall through to add. |
| `change` | int | no | `0` | Value to add or set |

**Example:**
```json
{ "type": "neoorigins:change_resource", "resource": "examplepack:mana", "operation": "add", "change": -5 }
```

Clamped to `[Integer.MIN_VALUE, Integer.MAX_VALUE]` on add.

> ⚠️ `resource` must be the **full** namespaced power id. The `*:` / `*:*` self-reference wildcard is **not** resolved for resources (only `power_active` and `origins:multiple` sub-powers support it) — a reference containing `*` targets a non-existent key and is warned about at load. This applies equally to `set_resource` and the `neoorigins:resource` condition.

---

## `neoorigins:modify_resource`

Apoli legacy alias for [`neoorigins:change_resource`](#neooriginschange_resource) — same factory and field shape (`resource`, `operation`, `change`). The `change` field also accepts Apoli's nested `modifier` `{operation, amount}` form. Prefer `change_resource` in new packs.

**Example:**
```json
{ "type": "neoorigins:modify_resource", "resource": "examplepack:mana", "change": 10 }
```

---

## `neoorigins:modify_temperature`

Changes a player's temperature via **[Cold Sweat](https://modrinth.com/mod/cold-sweat)** (`cold_sweat`). This is an integration action: it only does anything when Cold Sweat is installed. Gate any power that uses it with the top-level `"required_mods": ["cold_sweat"]` field so the power doesn't even load without the mod — without that gate the action degrades to a logged no-op when Cold Sweat is absent.

Cold Sweat's `core` (body) temperature runs on a scale of roughly **−100** (freezing death) … **+100** (burning death), with **0** neutral. The `base`/`world` traits use the ambient scale; the resistance/dampening traits sit in a ~0..1 range. Positive `amount` warms, negative cools.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `trait` | enum | no | `core` | Which trait to modify: `core`, `base`, `world`, `heat_resistance`, `cold_resistance`, `heat_dampening`, `cold_dampening`, `freezing_point`, `burning_point`, `rate` |
| `amount` | number | yes | — | How much to change by (positive warms, negative cools). ±1 is a small `core` nudge; ±100 spans the survivable band. Resistance/dampening traits want ~0..1 |
| `operation` | enum | no | `add` | `add` treats `amount` as a delta; `set` overwrites the trait to `amount` outright |

**Example — warm the player up when an ability fires:**
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

Assigns a `resource` power's stored integer to a fixed value (the `set`-only sibling of `change_resource`). Same attachment-backed state and the same full-power-id requirement (no `*` wildcard).

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

> **Arming imported `origins:cooldown` powers.** This action also arms imported Apoli cooldown powers (see [`origins:cooldown`](POWER_TYPES.md#originscooldown)): any resource key matching `power` — wildcard globs like `*:*_timer` included — that belongs to a cooldown power is set to that power's registered duration, starting its countdown. The `cooldown` field is ignored on that path; the duration comes from the cooldown power's own JSON.

---

# Meta verbs

## `neoorigins:nothing`

Explicit no-op. Useful as the default branch of `if_else` or for placeholder authoring. Takes no fields.

**Example:**
```json
{ "type": "neoorigins:nothing" }
```

---

## `neoorigins:and` (alias `neoorigins:all_of`)

Runs a sequence of actions in order against the same target. `all_of` is Apoli 2.9+'s rename of the `and` meta action: imported packs using `origins:all_of` / `apoli:all_of` dispatch here unchanged (there is no `any_of` action).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `actions` | array of action | yes | `[]` | Actions to run in order |

**Example:**
```json
{ "type": "neoorigins:and", "actions": [
  { "type": "neoorigins:heal", "amount": 2.0 },
  { "type": "neoorigins:play_sound", "sound": "minecraft:entity.player.levelup" }
] }
```

---

## `neoorigins:if_else`

Conditional dispatch. If `condition` is absent or not an object, it's treated as always-false (`CompatPolicy.FALSE_CONDITION`), so the `else_action` runs.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `condition` | entity condition | no | FALSE | Guard |
| `if_action` | action | no | noop | Runs when condition passes |
| `else_action` | action | no | noop | Runs when condition fails |

**Example:**
```json
{ "type": "neoorigins:if_else",
  "condition": { "type": "neoorigins:submerged_in", "fluid": "minecraft:water" },
  "if_action": { "type": "neoorigins:gain_air", "amount": 40 },
  "else_action": { "type": "neoorigins:nothing" } }
```

---

## `neoorigins:if_else_list`

First-match-wins chain of `(condition, action)` pairs. Stops after the first matching branch.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `actions` | array | yes | `[]` | Each entry has `condition` + `action` |

**Example:**
```json
{ "type": "neoorigins:if_else_list", "actions": [
  { "condition": { "type": "neoorigins:in_rain" }, "action": { "type": "neoorigins:heal", "amount": 2 } },
  { "condition": { "type": "neoorigins:daytime" }, "action": { "type": "neoorigins:set_on_fire", "ticks": 40 } }
] }
```

---

## `neoorigins:chance`

Probabilistic dispatch. Uses the target's RNG source.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `chance` | float [0,1] | no | `0.5` | Probability of running the inner action |
| `action` | action | no | noop | Inner action |

**Example:**
```json
{ "type": "neoorigins:chance", "chance": 0.2, "action": { "type": "neoorigins:play_sound", "sound": "minecraft:entity.cat.ambient" } }
```

---

## `neoorigins:choice`

Picks one action from a weighted list and runs it. Each entry pairs an `action` with a `weight`; one is selected weighted-randomly per dispatch.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `actions` | array of `{action, weight}` | yes | — | Candidate entries; one is picked weighted-randomly |

**Example:**
```json
{ "type": "neoorigins:choice", "actions": [
  { "weight": 3, "action": { "type": "neoorigins:heal", "amount": 2.0 } },
  { "weight": 1, "action": { "type": "neoorigins:set_on_fire", "ticks": 40 } }
] }
```

---

## `neoorigins:delay`

Schedules the inner action to run N ticks in the future via `CompatTickScheduler`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `ticks` | int | no | `1` | Tick delay |
| `action` | action | no | noop | Action to fire at `currentTick + ticks` |

**Example:**
```json
{ "type": "neoorigins:delay", "ticks": 40, "action": { "type": "neoorigins:extinguish" } }
```

Server-side only.

---

## `neoorigins:offset`

Positional wrapper: runs its inner `action` with the current context block position shifted by `x`/`y`/`z` blocks — Apoli's positional offset verb. When the dispatch context resolves a block position (`block_use` / `bonemeal` events, `raycast` block hits, `block_action_at`, the `area_of_effect` block fan-out), the shifted position is published as the context block for the inner action, so block verbs (`grow`, `transform_block`, `execute_command` with `~ ~ ~`) act on the shifted block. When no block context resolves — or all three offsets are 0 — the inner action runs unchanged (transparent pass-through).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `action` | action | no | noop | Inner action to run at the offset block position |
| `x` | int | no | `0` | X offset in blocks (applied to the context block position) |
| `y` | int | no | `0` | Y offset in blocks (applied to the context block position) |
| `z` | int | no | `0` | Z offset in blocks (applied to the context block position) |

**Example — bonemeal the block two above the one you hit:**
```json
{ "type": "neoorigins:offset", "y": 2, "action": { "type": "neoorigins:grow" } }
```

---

## `neoorigins:block_action_at`

Runs `block_action` at the entity's current block position, publishing that block position to the dispatch context so nested verbs (e.g. `execute_command` with `~ ~ ~`) resolve to the block centre.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `block_action` | action | no | noop | Action run at the entity's block position |

**Example:**
```json
{ "type": "neoorigins:block_action_at", "block_action": { "type": "neoorigins:set_block", "block": "minecraft:torch" } }
```

---

## `neoorigins:area_of_effect`

Iterates every living entity within the radius and runs `entity_action` against each. Mobs **and** players are hit when `entity_action` uses an *entity-general* verb — `damage`, `heal`, `apply_effect`, `clear_effect`, `set_on_fire`, `add_velocity`, `spawn_particles`, `shear`, `dye`, … (and an `and` of only those). A player-only verb (e.g. `launch`, `set_block`) still runs on player targets only and skips mobs. Mob targets are also subject to the friendly-fire protections in `gameplay.toml` (pets/minions/villagers/iron golems/animals).

`entity_condition` filters which entities are affected. When it uses an entity-general condition — `entity_type` (incl. a `#tag` group), `target_group`, `health`, `relative_health`, `has_effect`/`status_effect`, `on_fire`, `living`, and `and`/`or`/`not` of those — it filters **both mobs and players**, so an aura can restrict itself to *players only* (`entity_type: minecraft:player`), a tag group, a specific mob, etc. A player-only condition verb only gates player targets (mobs bypass it, as before).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | float | no | `16.0` | Radius |
| `shape` | string or object | no | `"sphere"` | `"cube"` skips the distance cull (plain AABB); anything else culls by squared distance. May also be an object — `{ "type": "cone", "angle": 60 }` additionally restricts the sphere to a cone of that full width (degrees) around the caster's look direction |
| `include_source` | bool | no | see below | Whether the source entity is included |
| `include_target` | bool | no | `false` | Apoli spelling of `include_source` |
| `entity_action` | action | no | noop | Runs per affected entity (mobs + players for entity-general verbs) |
| `bientity_action` | bientity action | no | — | Apoli form: runs as (caster, affected entity). Takes precedence over `entity_action` |
| `entity_condition` | condition | no | always-true | Target filter; entity-general conditions filter mobs + players (see above) |
| `bientity_condition` | bientity condition | no | always-true | Apoli form: filter evaluated as (caster, affected entity) |
| `block_action` | action | no | — | Block fan-out (see below): runs at every block position in radius, each published as the context block |
| `block_condition` | block condition | no | matches all | Filter for the block fan-out — only positions matching it run `block_action`. Only used with `block_action` |

**Apoli `bientity_action` form.** Apoli's own `area_of_effect` names its per-target action `bientity_action` and its filter `bientity_condition`, both taking the (actor, target) pair — usually wrapped in `target_action` / `target_condition`. Both spellings are accepted. Two defaults differ between the forms: the caster is included by default with `entity_action` (long-standing behaviour) but **excluded** by default with `bientity_action`, matching Apoli — otherwise a cone attack damages the player who cast it. A `bientity_condition` that cannot be evaluated against a non-player entity skips the entity fan-out entirely and is reported in the compat summary, since silently dropping an *exclusion* filter would hit bystanders it was written to spare.

**Block fan-out (Apoli `block_action` form).** When `block_action` is set, the action additionally sweeps every **block position** within `radius`, centered on the context block position when one resolves (raycast hit, `block_use`/`bonemeal` event, `block_action_at`) and on the source's feet otherwise. Each position is published as the context block, so nested block verbs (`grow`/`bonemeal`, `transform_block`, `offset`, `execute_command`) self-resolve. `block_condition` gates each position; `shape: "sphere"` culls by distance as for entities. The block sweep radius is capped at 16 — Apoli packs use small radii here (e.g. 2).

**Example — burn nearby mobs but never other players:**
```json
{ "type": "neoorigins:area_of_effect",
  "radius": 8.0,
  "shape": "sphere",
  "include_source": false,
  "entity_condition": {
    "type": "neoorigins:not",
    "condition": { "type": "neoorigins:entity_type", "entity_type": "minecraft:player" }
  },
  "entity_action": { "type": "neoorigins:set_on_fire", "ticks": 40 } }
```

**Example — affect players only (e.g. a support aura):**
```json
{ "type": "neoorigins:area_of_effect",
  "radius": 8.0,
  "entity_condition": { "type": "neoorigins:entity_type", "entity_type": "minecraft:player" },
  "entity_action": { "type": "neoorigins:apply_effect", "effect": "minecraft:regeneration", "duration": 60 } }
```

**Example — block fan-out: bonemeal every grass block in a 2-block sphere:**
```json
{ "type": "neoorigins:area_of_effect",
  "radius": 2.0,
  "shape": "sphere",
  "block_condition": { "type": "neoorigins:block", "block": "minecraft:grass_block" },
  "block_action": { "type": "neoorigins:grow" } }
```

**Example — a breath attack: a 60° cone in front of the caster, sparing players:**
```json
{ "type": "neoorigins:area_of_effect",
  "radius": 5.0,
  "shape": { "type": "cone", "angle": 60 },
  "bientity_condition": {
    "type": "neoorigins:target_condition",
    "condition": { "type": "neoorigins:entity_type", "entity_type": "minecraft:player", "inverted": true }
  },
  "bientity_action": {
    "type": "neoorigins:target_action",
    "action": { "type": "neoorigins:damage", "amount": 4 }
  } }
```

---

# Caster & target (bientity actions)

Some call sites dispatch a **bientity action** — an action that runs against a *pair* of entities rather than one. The pair is always **(actor, target)**:

- **actor** — the caster: the player who owns the power. Always a player.
- **target** — the other entity in the interaction: the mob or player you hit, were hit by, or interacted with. May be a player **or** a non-player mob.

Bientity actions are how a single power can affect *both* sides of an interaction. The call sites that supply a bientity pair are:

- `action_on_hit.bientity_action` — actor = the attacker (you), target = the entity you hit
- `action_on_hit_taken.bientity_action` — actor = you, target = the entity that hit you
- `action_when_hit` / projectile on-hit (`action_on_hit` on a thrown/launched entity) — actor = the launcher, target = the entity struck
- entity-interact powers — actor = you, target = the entity you interacted with

Both the caster and the target are available at the same time. To route an effect to one side or the other, wrap it in `actor_action` or `target_action`.

These wrapper verbs are Apoli-namespaced (`origins:` / `apoli:` / `apace:` prefixes are all accepted); the inner `action` they wrap is an ordinary entity-action from this reference.

## `actor_action`

Runs the inner entity-action against the **caster** (the power holder). While it runs, the target is published to the dispatch context, so context-reading verbs (e.g. set verbs, `damage_target`-style sub-actions) can resolve the hit entity.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `action` | entity action | yes | noop | Action run against the actor (caster) |

**Example — heal yourself when you land a hit:**
```json
{ "type": "apoli:actor_action", "action": { "type": "neoorigins:heal", "amount": 4.0 } }
```

## `target_action`

Runs the inner entity-action against the **target** — the entity on the other side of the interaction, resolved from the active dispatch context (the entity you hit / were hit by / killed / interacted with, or the entity a projectile struck). If no target resolves, the action is a no-op.

- When the target is a **player**, the full entity-action surface runs on it (PvP-style scenarios). The actor is published to the dispatch context while it runs.
- When the target is a **non-player mob**, the entity-general verbs run directly on the mob: `apply_effect`, `clear_effect`, `damage`, `heal`, `set_on_fire`, `extinguish`, `add_velocity`, `play_sound`, `set_fall_distance`, `dismount`, `swing_hand`, `nothing`. Verbs that depend on player-only systems (powers, resources, XP, food, inventory, command execution) only apply when the target is a player.

This verb also works directly as a `neoorigins:`-namespaced entity-action (e.g. inside a projectile `on_hit_action`), where it reads the same context target rather than being a transparent pass-through to the holder.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `action` | entity action | yes | noop | Action run against the target entity |

**Example — set the entity you hit on fire and poison it (works on mobs and players alike):**
```json
{ "type": "apoli:target_action", "action": { "type": "neoorigins:and", "actions": [
  { "type": "neoorigins:set_on_fire", "ticks": 60 },
  { "type": "neoorigins:apply_effect", "effect": "minecraft:poison", "duration": 100 }
] } }
```

## `invert`

Swaps actor and target, then runs the inner bientity action against the swapped pair. Because the actor slot must be a player, the swap only takes effect when the original target is itself a player.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `action` | bientity action | no | noop | Action run with actor/target swapped |

## `if_else` (bientity)

Branches on a **bientity condition** and runs one of two bientity actions against the same pair. This is the pair-level twin of the entity-action [`if_else`](#neooriginsif_else); the difference that matters is the condition, which is read as a bientity condition (`target_condition`, `actor_condition`, `can_see`, `constant`, and the combinators) rather than as a holder-only condition.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `condition` | bientity condition | no | false | Gate evaluated as (actor, target) |
| `if_action` | bientity action | no | noop | Run when the condition is true |
| `else_action` | bientity action | no | noop | Run when it is false |

A condition that cannot be compiled as a bientity condition is reported in the compat summary and evaluates false, so the `else_action` branch runs.

**Example — mark the entity you hit, but only once:**
```json
{ "type": "apoli:if_else",
  "condition": { "type": "apoli:target_condition",
                 "condition": { "type": "apoli:nbt", "nbt": "{Tags:[\"marked\"]}" } },
  "if_action": { "type": "apoli:target_action", "action": { "type": "neoorigins:damage", "amount": 4.0 } },
  "else_action": { "type": "apoli:target_action", "action": { "type": "neoorigins:execute_command", "command": "tag @s add marked" } } }
```

## `riding_action`

Runs the inner action against the entity the holder is **riding** (its vehicle), but only when that vehicle is a player. If the holder isn't riding a player, it no-ops.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `action` / `entity_action` | entity action | no | noop | Action run on the ridden entity (`entity_action` is an accepted alias) |

**Example:**
```json
{ "type": "neoorigins:riding_action", "action": { "type": "neoorigins:heal", "amount": 2.0 } }
```

## `passenger_action`

Runs the inner action against every **passenger** of the holder. Only `ServerPlayer` passengers are affected; non-player passengers are skipped.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `action` / `entity_action` | entity action | no | noop | Action run on each player passenger (`entity_action` is an accepted alias) |

**Example:**
```json
{ "type": "neoorigins:passenger_action", "action": { "type": "neoorigins:apply_effect", "effect": "minecraft:speed", "duration": 100 } }
```

## `selector_action`

Resolves a vanilla entity selector relative to the holder's command source, then runs `bientity_action` once per selected entity. Each selected entity is published as the action's source (its origin + rotation), so a nested `spawn_projectile` / `fire_projectile` fires **from** that entity. `sort` / `limit` / `tag` predicates inside the selector string are honoured by vanilla's parser.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `selector` | string | yes | — | Vanilla entity selector (e.g. `@e[type=area_effect_cloud,tag=foo,limit=2,sort=nearest]`) |
| `bientity_action` / `entity_action` | action | yes | — | Action run once per selected entity (`entity_action` is an accepted alias) |

**Example:**
```json
{ "type": "neoorigins:selector_action",
  "selector": "@e[type=minecraft:area_effect_cloud,tag=volley,limit=4]",
  "bientity_action": { "type": "neoorigins:fire_projectile", "entity_type": "minecraft:arrow", "speed": 2.0 } }
```

## `and` / `chance` (bientity form)

`and` runs a list of bientity actions in order against the same (actor, target) pair; `chance` runs one with a probability (uses the actor's RNG). Both mirror their entity-action counterparts but operate on the pair.

```json
{ "type": "apoli:and", "actions": [
  { "type": "apoli:actor_action",  "action": { "type": "neoorigins:heal", "amount": 2.0 } },
  { "type": "apoli:target_action", "action": { "type": "neoorigins:damage", "amount": 4.0 } }
] }
```

## `damage` (bientity form)

Directly damages the target, attributed to the actor. Supports `amount` and an optional `damage_type` (defaults to `minecraft:generic`).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `amount` | float | no | `1.0` | Damage in half-hearts |
| `damage_type` | resource id | no | `minecraft:generic` | Registered damage type; falls back to a player-attack source if unresolved |

---

# Set / capability verbs (2.0)

These verbs are new in 2.0 and read from `ActionContextHolder` — the service that publishes the current dispatch context while `EventPowerIndex` walks handlers. They no-op outside a compatible context.

## `neoorigins:add_to_set`

Adds the current bientity target's UUID to a named entity-set on the actor player. The backing sets power relationship tracking (who I've tagged, who I'm tracking, etc.). Aliased as `neoorigins:add_to_set`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `set` | string | yes | — | Set name |

**Gotcha — bientity-only:** requires an active `HitTakenContext`, `KillContext`, `EntityInteractContext`, or `ProjectileHitContext` carrying a `LivingEntity` target. Silently no-ops otherwise.

**Example:**
```json
{ "type": "neoorigins:add_to_set", "set": "tagged_enemies" }
```

---

## `neoorigins:remove_from_set`

Removes the current bientity target's UUID from a named entity-set. Same context requirements as `add_to_set`. Aliased as `neoorigins:remove_from_set`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `set` | string | yes | — | Set name |

**Example:**
```json
{ "type": "neoorigins:remove_from_set", "set": "tagged_enemies" }
```

---

## `neoorigins:toggle`

Flips or sets a named toggle state on the target. Used by 2.0's `toggle` alias family. If `value` is given it's set explicitly; otherwise the current state is flipped (resolving the registered [`neoorigins:toggle`](POWER_TYPES.md#neooriginstoggle) power's `default` field as the starting value).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` | string | yes | — | Toggle key (usually the power id) |
| `value` | bool | no | — | If present, set to this value; otherwise flip |

**Example:**
```json
{ "type": "neoorigins:toggle", "power": "examplepack:flight_toggle" }
```

See [COOKBOOK.md → Toggleable abilities (no keybind slot)](COOKBOOK.md#toggleable-abilities-no-keybind-slot) for full recipes.

---

## `neoorigins:cancel_event`

Cancels the currently dispatched event. Used internally by the `food_restriction` alias to reject food consumption.

**Gotcha — context-only:** works only when the current `ActionContextHolder` value carries a cancellable event. That covers `food_eaten` (`FoodContext`), `effect_applied` (`EffectAppliedContext`), `entity_use` / `villager_interact` / `breed` / `tame` (`EntityInteractContext`), `block_use` / `bonemeal` (`BlockInteractContext`), and any dispatch whose context is itself an `ICancellableEvent` (e.g. `block_place`). No-op elsewhere — post-hoc events like `food_finished`, `trade_completed` or `kill` cannot be cancelled.

**Example:**
```json
{ "type": "neoorigins:cancel_event" }
```

---

## `neoorigins:damage_attacker`

Hurts the attacker recorded in the current `HitTakenContext`. Used by the `thorns_aura` alias.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `amount` | float | no | `2.0` | Fixed damage |
| `amount_ratio` | float | no | — | If present, damage = `max(0.5, incomingDamage * ratio)`. Overrides `amount`. |
| `source.name` | string | no | `magic` | Damage source. Supported: `fire`/`on_fire`/`in_fire`, `lava`, `magic`, `generic`. Unknown values fall through to `magic`. |

**Gotcha — hit-taken only:** requires an active `HitTakenContext` whose attacker is a `LivingEntity`. No-op elsewhere.

**Example:**
```json
{ "type": "neoorigins:damage_attacker", "amount_ratio": 0.5, "source": { "name": "magic" } }
```

---

## `neoorigins:ignite_attacker`

Sets the current `HitTakenContext` attacker on fire. **Gotcha:** hit-taken context only.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `ticks` | int | no | `60` | Fire duration |

**Example:**
```json
{ "type": "neoorigins:ignite_attacker", "ticks": 100 }
```

---

## `neoorigins:effect_on_attacker`

Applies a mob effect to the current `HitTakenContext` attacker. **Gotcha:** hit-taken context only, and the attacker must be a `LivingEntity`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effect` | resource id | yes | — | Effect id |
| `duration` | int | no | `100` | Effect duration |
| `amplifier` | int | no | `0` | Amplifier level |

**Example:**
```json
{ "type": "neoorigins:effect_on_attacker", "effect": "minecraft:weakness", "duration": 100, "amplifier": 0 }
```

---

## `neoorigins:random_teleport`

Random-teleports the target within a bounded box. Retries up to `attempts` times, requiring 2-block air clearance at the destination.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `horizontal_range` / `range` | double | no | `16.0` | Half-width of the XZ search box |
| `vertical_range` | double | no | `8.0` | Half-height of the Y search range |
| `attempts` | int | no | `16` | Number of candidate positions to try |

Server-side only. Silently gives up if no viable spot is found in `attempts` tries.

**Example:**
```json
{ "type": "neoorigins:random_teleport", "horizontal_range": 8.0, "vertical_range": 4.0, "attempts": 32 }
```

---

## `neoorigins:chain_to_nearest`

Pulls the actor toward the nearest matching living entity within `radius`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | float | no | `16.0` | Search radius |
| `speed` | float | no | `1.0` | Launch speed along the vector |
| `target_condition` | condition | no | always-true | Filter applied to `ServerPlayer` candidates only (non-players are accepted as-is) |

**Example:**
```json
{ "type": "neoorigins:chain_to_nearest", "radius": 12.0, "speed": 1.2 }
```

---

## `neoorigins:pull_entities`

Pulls nearby living entities toward the actor — inverse of `chain_to_nearest`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | float | no | `8.0` | Search radius |
| `strength` | float | no | `0.5` | Pull strength (velocity magnitude) |
| `include_players` | bool | no | `true` | Include other players |
| `entity_condition` | condition | no | always-true | Filter (applied to `ServerPlayer` candidates only) |

**Example:**
```json
{ "type": "neoorigins:pull_entities", "radius": 6.0, "strength": 0.8, "include_players": false }
```

---

## `neoorigins:throw_target`

Hurls the single living entity directly under the actor's crosshair away from the actor and upward. Unlike `pull_entities` (radius AOE) this is a precise raycast pick — the entity must be visible along the look ray within `max_distance`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `force` | float | no | `1.5` | Horizontal velocity impulse magnitude (knockback strength) |
| `vertical_lift` | float | no | `0.5` | Upward velocity component, applied independently of `force` so packs can tune throw arc separately from horizontal range |
| `max_distance` | float | no | `5.0` | Raycast range from the actor's eye — targets beyond this are ignored |

Direction is the XZ vector from actor to target, so the throw is always purely "away horizontally + up". When the target's XZ position equals the actor's (rare — overhead pickup), the actor's look-yaw is used as the fallback horizontal direction.

The action sets `target.hurtMarked = true` after pushing so client-side prediction picks up the velocity change immediately (otherwise the visible knockback can lag a packet).

**Approximate empirical range** (vanilla LivingEntity drag, no obstacles):
- `force: 1.0` → target travels ~3–4 blocks
- `force: 2.0` → ~7–9 blocks
- `force: 3.0` → ~13–15 blocks

Not strictly linear — vanilla drag is non-trivial.

**Example — light shove:**
```json
{ "type": "neoorigins:throw_target", "force": 1.2, "vertical_lift": 0.4, "max_distance": 4.0 }
```

**Example — heavy hurl:**
```json
{ "type": "neoorigins:throw_target", "force": 2.5, "vertical_lift": 0.9, "max_distance": 6.0 }
```

Typically wrapped in `active_ability` for a cooldown + hunger cost.

---

## `neoorigins:tame_target`

Tames a mob as a side-effect of an action, using the same taming behaviour as the [`tame_mob`](POWER_TYPES.md) active power — the tamed mob follows and defends the actor, is marked persistent, and is tracked for despawn + death-damage. This is the **action** form: use it inside an `entity_action` field (an `action_on_event` with `event: "attack"`, a `raycast` `bientity_action`, an `active_ability`, etc.) rather than as a standalone power.

The target is resolved in two steps:
1. **Dispatch context first** — if the action fires from an interaction that carries a target entity (the mob you hit, the entity a `raycast`'s `bientity_action` struck, a projectile's `on_hit_action` target), that entity is tamed.
2. **Crosshair fallback** — when no context target resolves, the mob under the actor's crosshair within `max_distance` is used.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `max_tamed` | int | no | `4` | Max tamed mobs alive at once. Shared with `tame_mob` through the same tracker, so they count toward one combined cap. Once reached the action no-ops. |
| `max_distance` | float | no | `5.0` | Crosshair raycast range, used **only** when no context target resolves |
| `despawn_ticks` | int | no | `36000` | Ticks the tamed mob lasts before despawning (36000 = 30 min) |
| `death_damage` | float | no | `0.5` | Damage dealt to the actor when the tamed mob dies (half-hearts) |
| `hostile_only` | bool | no | `true` | When true, only mobs implementing `Enemy` can be tamed; set false to allow any non-player `Mob` |
| `entity_blacklist` | array | no | `[]` | Entity ids (`"minecraft:warden"`) and tag refs (`"#mymod:untameable"`) this action can never tame |

The Warden, Ender Dragon and Wither (plus the server's `tame_scare_entity_blacklist` config list) are always excluded regardless of `entity_blacklist`. A capped, no-target, or excluded activation is a **silent no-op** — unlike `tame_mob`, this action does not send actionbar feedback, since it fires from events rather than a deliberate keybind.

Tamed mobs are persistent like vanilla pets: they survive the tamer's death (recalled to them on respawn), the tamer logging out, and full server restarts — the tame is stored on the mob itself and its loyalty AI is rebuilt whenever it loads. See [Persistence](POWER_TYPES.md#persistence) under `tame_mob` for details.

**Example — tame the mob you hit, on attack:**
```json
{
  "type": "neoorigins:action_on_event",
  "event": "attack",
  "entity_action": { "type": "neoorigins:tame_target", "hostile_only": false }
}
```

**Example — crosshair tame, no fallback distance change:**
```json
{ "type": "neoorigins:tame_target", "max_tamed": 2, "entity_blacklist": ["minecraft:creeper"] }
```

---

## `neoorigins:dash`

Applies a forward impulse in the direction the player is currently facing. Unlike `add_velocity` (which uses fixed x/y/z), `dash` reads the player's look vector and projects `strength` along it — so looking up-forward causes a diagonal upward dash, horizontal look causes a flat dash, etc.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `strength` | float | no | `1.5` | Velocity magnitude along the look vector |
| `allow_vertical` | bool | no | `true` | When `false`, pins the dash to horizontal (ignores look Y component) |

Sets `hurtMarked = true` internally so the client doesn't discard the server-authoritative velocity change on the next movement packet — same guarantee as `add_velocity`.

**Example — cat pounce (2.2 strength, vertical allowed):**
```json
{ "type": "neoorigins:dash", "strength": 2.2, "allow_vertical": true }
```

**Example — shadow dash (ground-level only):**
```json
{ "type": "neoorigins:dash", "strength": 2.0, "allow_vertical": false }
```

Preferred canonical replacement for the legacy `active_dash` type when paired with `active_ability`.

---

## `neoorigins:swap_with_entity`

Swaps positions and facing with the nearest matching living entity within `radius`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | float | no | `16.0` | Search radius |
| `target_condition` | condition | no | always-true | Filter (applied to `ServerPlayer` candidates only) |

**Example:**
```json
{ "type": "neoorigins:swap_with_entity", "radius": 10.0 }
```

---

## `neoorigins:swap_positions`

Atomically swaps the actor's and the **context target's** full transform (position, yaw, pitch). The target is the entity on the other side of the interaction (the mob/player hit, hit-by, killed, interacted-with, or struck by a projectile), not a radius search. Both transforms are snapshotted before either entity moves, so the two never collapse to one point. No-op if no target resolves.

This is a dual-actor verb — pair it with a bientity context (e.g. a projectile `on_hit_action`).

No fields.

**Example — a projectile that swaps you with whatever it hits:**
```json
{ "type": "neoorigins:swap_positions" }
```

---

## `neoorigins:teleport_to_target`

Moves the **actor** to the context target's position and facing. No-op if no target resolves. No fields.

**Example:**
```json
{ "type": "neoorigins:teleport_to_target" }
```

---

## `neoorigins:teleport_target_to_self`

Moves the **context target** to the actor's position and facing. No-op if no target resolves. No fields.

**Example:**
```json
{ "type": "neoorigins:teleport_target_to_self" }
```

---

## `neoorigins:shear`

Shears the **context target** the way vanilla or modded shears would — sheep drop wool and go bald, mooshrooms convert to cows and drop their mushroom (or flower), snow golems lose their pumpkin, bogged and modded shearables behave correctly. Uses the NeoForge `IShearable` seam so any registered shearable is covered. No-op if no target resolves or the target isn't currently shearable (e.g. an already-sheared sheep or a non-shearable mob).

This is a dual-actor verb — pair it with a bientity context (e.g. a projectile `on_hit_action`) or wrap it in `target_action` to hit an arbitrary mob target.

No fields.

**Example — a projectile that shears whatever it hits:**
```json
{ "type": "neoorigins:shear" }
```

---

## `neoorigins:dye`

Sets the colour of a dyeable **context target**. On 1.21.1 this dyes a sheep's wool colour; mobs with no public colour setter (wolf/cat collars) no-op cleanly. No-op if no target resolves, the target is non-dyeable, or `color` is an unknown dye name.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `color` | string | no | — | Dye colour name (e.g. `"red"`, `"light_blue"`). Unknown names no-op. |

**Example — dye the sheep you hit red:**
```json
{ "type": "neoorigins:dye", "color": "red" }
```

---

## `neoorigins:force_drop`

Makes the **context target** drop the item in a named equipment slot as an item entity, then clears that slot. No-op if no target resolves or the slot is empty.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `slot` | string | no | `mainhand` | Equipment slot: `mainhand` / `offhand` / `head` / `chest` / `legs` / `feet`. Unknown names fall back to `mainhand`. |

**Example — disarm the mob you hit:**
```json
{ "type": "neoorigins:force_drop", "slot": "mainhand" }
```

---

## `neoorigins:steal_item`

Like `force_drop`, but transfers the item from the **context target's** named slot to the **actor** (the power holder): added to the actor's inventory, or dropped at the actor if the inventory is full. No-op if no target resolves or the slot is empty.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `slot` | string | no | `mainhand` | Equipment slot: `mainhand` / `offhand` / `head` / `chest` / `legs` / `feet`. Unknown names fall back to `mainhand`. |

**Example — steal the weapon out of the hand of whatever you hit:**
```json
{ "type": "neoorigins:steal_item", "slot": "mainhand" }
```

---

## `neoorigins:drop_inventory`

Drops items from the target player's vanilla inventory. By default it scatters every slot's contents; restrict by slot list and/or an item condition. Only the `inventory` container is supported.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `inventory_type` | string | no | `"inventory"` | Only `inventory` (the vanilla player inventory) is supported; `power` inventories no-op |
| `slots` | array of string | no | — | Restrict to these vanilla/Apoli slot names (e.g. `weapon.mainhand`, `weapon.offhand`, `armor.head`). Omit or leave empty to scan every slot |
| `item_condition` | item condition | no | — | Only drop stacks matching this condition. Omit to drop everything |
| `throw_randomly` | bool | no | `true` | Scatter the dropped items |
| `retain_ownership` | bool | no | `false` | Tag drops with the thrower for pickup priority |

**Example — drop just the held weapon:**
```json
{ "type": "neoorigins:drop_inventory", "slots": ["weapon.mainhand"] }
```

---

## Block-target verbs

These act on the **block on the other side of the interaction** — the block a projectile or raycast impacted — rather than on an entity. They resolve the impacted block from the active dispatch context (a projectile `on_hit_action` that lands on a block, or a `raycast` `block_action`), so you can write them directly as an `on_hit_action` / `block_action` and they self-resolve the hit block. Each no-ops cleanly when no block resolves or the block isn't applicable. To run one against a specific resolved block context explicitly, wrap it in [`block_target_action`](#neoorigins-block_target_action).

## `neoorigins:strip`

Axe-strips the **context block** — logs and wood (all vanilla wood families, including crimson/warped stems and hyphae, and bamboo blocks) become their stripped variant, preserving the pillar axis. No-op if the block has no strip mapping.

No fields.

**Example — a projectile that strips logs it hits:**
```json
{ "type": "neoorigins:strip" }
```

## `neoorigins:till`

Hoe-tills the **context block** — grass / dirt / coarse-dirt / rooted-dirt / dirt-path become farmland (coarse dirt becomes plain dirt), but only when the block directly above is air (the vanilla hoe rule). No-op otherwise.

No fields.

**Example:**
```json
{ "type": "neoorigins:till" }
```

## `neoorigins:path`

Shovels the **context block** into a dirt path — grass / dirt / podzol / mycelium / coarse-dirt / rooted-dirt, only when the block directly above is air (the vanilla shovel rule). No-op otherwise.

No fields.

**Example:**
```json
{ "type": "neoorigins:path" }
```

## `neoorigins:grow` (alias `neoorigins:bonemeal`)

Applies one bonemeal-style growth tick to the **context block** if it's a bonemealable block ready to grow (crops, saplings, grass, etc.), with the green growth particles. No-op when the block isn't bonemealable or isn't valid for growth right now. `bonemeal` is the Apoli block-action name for the same verb, accepted so imported packs (e.g. an `area_of_effect` block fan-out authoring `origins:bonemeal`) dispatch here directly.

No fields.

**Example — a projectile that fertilises crops it hits:**
```json
{ "type": "neoorigins:grow" }
```

## `neoorigins:transform_block`

The generic primitive: sets the **context block** to `to`, optionally only when it currently matches `from`. Use this as the fallback for any block state swap not covered by the verbs above. No-op when `to` is missing/unknown or the `from` guard fails.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `from` | string | no | — | Optional block-id guard; only transform when the impacted block matches (e.g. `"minecraft:stone"`). |
| `to` | string | yes | — | Block id to set the impacted block to (e.g. `"minecraft:gold_block"`). |

**Example — turn the stone you hit into gold:**
```json
{ "type": "neoorigins:transform_block", "from": "minecraft:stone", "to": "minecraft:gold_block" }
```

## `neoorigins:block_target_action`

The block-side analogue of [`target_action`](#target_action): resolves the impacted block from the active dispatch context and runs the inner block-target verb against it. No-op when no block context resolves or the inner action isn't a block-target verb (`strip` / `till` / `path` / `grow` / `transform_block`). The actor (the power holder) is the one running the power.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `action` | object | no | — | Inner block-target verb to run on the resolved context block. |

**Example — a projectile that strips the block it hits, via the wrapper:**
```json
{ "type": "neoorigins:block_target_action", "action": { "type": "neoorigins:strip" } }
```

---

## `neoorigins:teleport_to_marker`

Teleports the target to an absolute position or by a relative offset. The "marker" naming is aspirational — named-marker lookup is not yet wired; only absolute `position` and `dx`/`dy`/`dz` offsets are honoured.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `position.x` / `.y` / `.z` | double | no | — | Absolute target (if `position` is present, offsets are ignored) |
| `dx` / `dy` / `dz` | double | no | `0` | Relative offset from current position |

**Example:**
```json
{ "type": "neoorigins:teleport_to_marker", "dx": 0.0, "dy": 16.0, "dz": 0.0 }
```

---

## `neoorigins:equipped_item_action`

Runs an item action on the stack in a given equipment slot. Delegates per-stack verbs to `ItemActionParser`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `equipment_slot` | string | no | `"mainhand"` | `"head"`, `"chest"`, `"legs"`, `"feet"`, `"offhand"`, or `"mainhand"` |
| `item_action` | object | yes | — | Item action to run (see Item Actions below) |

**Example — damage the held item by 5:**
```json
{ "type": "neoorigins:equipped_item_action",
  "equipment_slot": "mainhand",
  "item_action": { "type": "neoorigins:damage", "amount": 5 } }
```

---

## `neoorigins:modify_inventory`

Iterates inventory slots and runs an item action on each matching stack.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `item_action` | object | yes | — | Item action to run per matching stack |
| `item_condition` | object | no | — | Item condition to filter stacks (see Item Conditions below); all stacks if absent |
| `slot` | string | no | — | Restrict to a single slot: `mainhand`, `offhand`, `head`, `chest`, `legs`, `feet`, or a raw inventory index; iterates all slots if absent |

**Example — consume all rotten flesh in inventory:**
```json
{ "type": "neoorigins:modify_inventory",
  "item_condition": { "type": "neoorigins:ingredient", "item": "minecraft:rotten_flesh" },
  "item_action": { "type": "neoorigins:consume" } }
```

---

## `neoorigins:raycast`

Performs a block and/or entity raycast from the player's eye position along their look vector. Runs nested actions at the hit position.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `distance` | double | no | `10.0` | Max raycast distance |
| `block` | bool | no | `true` | Include block hits |
| `entity` | bool | no | `false` | Include entity hits |
| `fluid_handling` | enum | no | `none` | How fluids are treated: `none` / `source_only` / `any` |
| `shape_type` | enum | no | `visual` | Block shape used for the trace: `visual` or `collider` |
| `block_action` | object | no | — | Action to run at the hit block position (dispatched with `RaycastBlockContext` so `~ ~ ~` resolves to the hit block) |
| `bientity_action` | object | no | — | Action to run when an entity is hit (actor = caster, target = hit entity) |
| `miss_action` | object | no | — | Action to run when nothing is hit within range |
| `before_action` | object | no | — | Entity action run once before the ray is cast, regardless of hit outcome (e.g. consume a reagent) |
| `command_along_ray` | string | no | — | Command run at each step along the ray, hit or miss. Bare `/particle <id> ~ ~ ~` commands are force-rendered so trails stay visible past the client particle setting |
| `command_step` | double | no | `1.0` | Block increment between `command_along_ray` executions |
| `command_at_hit` | string | no | — | Command run at the precise impact point when the ray hits a block or entity (same force-rendering as `command_along_ray`) |

**Example — execute command at the looked-at block:**
```json
{ "type": "neoorigins:raycast",
  "distance": 32.0,
  "entity": false,
  "block_action": {
    "type": "neoorigins:execute_command",
    "command": "particle minecraft:flame ~ ~ ~ 0.5 0.5 0.5 0.1 20"
  } }
```

---

## `neoorigins:cast_spell`

Casts an inline, author-baked **[Build A Spell](https://modrinth.com/mod/buildaspell)** (`buildaspell`) spell as the player. This is an integration action: it only does anything when Build A Spell is installed. Gate any power that uses it with the top-level `"required_mods": ["buildaspell"]` field so the power doesn't even load without BaS — without that gate the action degrades to a logged no-op when BaS is absent.

The spell is assembled once at datapack-load time and reused on every dispatch. Cost is charged on **your** NeoOrigins power (resource / hunger / cooldown), so the BaS mana pool is never touched — but BaS's own per-component enable/disable config toggles are still honoured.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `delivery` | enum | yes | — | One of Build A Spell's 5 delivery methods: `rune`, `sight`, `self`, `cast`, `tracking` |
| `components` | array | yes | — | The ordered component list (effect / modifier / `compat:*` ids). See ordering below |

### Component order is EFFECT-FIRST

`components` is a single ordered list, not a split of effects and modifiers. **A modifier binds to the effect that precedes it**, so each effect id is followed by the modifier ids that apply to it. A modifier appearing *before the first effect is silently dropped*.

```
EFFECT-FIRST (correct):  ["damage", "increased_power", "increased_power", "lightning", "chain"]
                          ^ damage, doubly powered          ^ lightning, chained
```

Any id that isn't a known effect, a known modifier, or a `compat:`-prefixed compat effect causes the whole spell to fail to build (logged, naming the bad id) and the power does nothing. The list is capped at 30 components and a duplicate non-stackable modifier is dropped — the same limits a player hits in the in-game builder.

> **Easiest authoring path:** build the spell in the in-game Build A Spell editor, hit its **"Export to Power"** button, and paste. The button emits the `components` list in the correct effect-first order; hand-writing it is the error-prone path.

**Example — a doubly-powered, chained lightning bolt on an active ability:**
```json
{
  "type": "neoorigins:active_ability",
  "key": "key.neoorigins.spell",
  "cooldown_ticks": 40,
  "entity_action": {
    "type": "neoorigins:cast_spell",
    "delivery": "cast",
    "components": ["damage", "increased_power", "increased_power", "lightning", "chain"]
  }
}
```
(The inner `cast_spell` block is a plain `entity_action` — lift it into `action_on_event` / `condition_passive` for a passive trigger.)

---

## `neoorigins:cast_iron_spell`

Casts a spell from **[Iron's Spells 'n Spellbooks](https://modrinth.com/mod/irons-spells-n-spellbooks)** (`irons_spellbooks`) as the player. This is an integration action: it only does anything when Iron's Spells is installed. Gate any power that uses it with the top-level `"required_mods": ["irons_spellbooks"]` field so the power doesn't even load without the mod — without that gate the action degrades to a logged no-op when Iron's Spells is absent.

By default cost is charged on **your** NeoOrigins power (resource / cooldown) and the Iron's mana pool is left untouched (`consume_mana: false`). Set `consume_mana: true` to draw from and gate on the player's Iron's mana instead — an under-funded cast is refused (logged no-op); creative players bypass the check.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `spell` | string | yes | — | The Iron's spell id, e.g. `irons_spellbooks:fireball`, `irons_spellbooks:magic_missile`, `irons_spellbooks:heal`, `irons_spellbooks:teleport`, `irons_spellbooks:chain_lightning`. Unknown ids resolve to a logged no-op |
| `level` | int | no | `1` | Spell level (power). Clamped to at least 1 and to the spell's own max level |
| `consume_mana` | bool | no | `false` | Draw from + gate on the player's Iron's mana pool. Creative bypasses the gate |
| `trigger_cooldown` | bool | no | `true` | Apply the spell's Iron's cooldown after casting |
| `mode` | enum | no | `instant` | `instant` fires the effect directly in one shot (best for keybind / on-hit triggers). `channel` runs the full animated cast — INSTANT spells complete inline, LONG / CONTINUOUS spells channel over time |

**Example — a level-2 fireball on an active ability:**
```json
{
  "type": "neoorigins:active_ability",
  "key": "key.neoorigins.spell",
  "cooldown_ticks": 60,
  "entity_action": {
    "type": "neoorigins:cast_iron_spell",
    "spell": "irons_spellbooks:fireball",
    "level": 2
  }
}
```

---

## `neoorigins:crafting_table`

Opens a 3×3 crafting menu for the target player, anchored at the player's position (so any recipe needing the table works). Takes no fields. Player-only.

**Example:**
```json
{ "type": "neoorigins:crafting_table" }
```

---

## `neoorigins:kubejs_callback`

Invokes a KubeJS-registered callback by id, handing it the dispatch context. Use it to run custom script logic from a power. Requires the registered callback to exist; no-ops otherwise.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `id` | string | yes | — | Id of the KubeJS-registered callback to invoke |

**Example:**
```json
{ "type": "neoorigins:kubejs_callback", "id": "examplepack:on_dash" }
```

---

# Item actions

Item actions operate on a single `ItemStack` and are used inside `equipped_item_action`, `modify_inventory`, and other per-stack contexts. They use the `ItemActionParser` verb set.

## `neoorigins:merge_nbt`

Merges SNBT data into the stack's data components via `LegacyTagToComponents`. Pre-1.21 SNBT keys (Potion, Enchantments, Damage, Unbreakable, CustomModelData, display.Name/Lore) are auto-translated to modern data components.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `nbt` | string (SNBT) | yes | — | SNBT to merge |

## `neoorigins:consume`

Removes the stack entirely (sets count to 0). No fields.

## `neoorigins:damage`

Damages the stack's durability.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `amount` | int | no | `1` | Durability to remove |

## `neoorigins:set_count`

Sets the stack's count.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `count` | int | no | `1` | New stack size |

## `neoorigins:and` (item)

Runs multiple item actions in sequence.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `actions` | array of item action | yes | — | Actions to run in order |

## `neoorigins:if_else` (item)

Conditional item action dispatch.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `condition` | item condition | no | FALSE | Guard |
| `if_action` | item action | no | noop | Runs when condition passes |
| `else_action` | item action | no | noop | Runs when condition fails |

---

# Item conditions

Item conditions evaluate to true/false against an `ItemStack`. Used inside `equipped_item` and `inventory` entity conditions, event-power `item_condition` filters, and `if_else` item actions. They use the `ItemConditionParser` verb set. All item conditions support the universal `"inverted": true` flag.

## `neoorigins:empty`

True when the stack is empty. No fields.

## `neoorigins:nbt`

True when the stack's NBT contains the given SNBT compound (subset match: every expected key must exist with an equal-or-containing value; list elements match if any actual element contains them).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `nbt` | string (SNBT) | yes | — | SNBT subtree to match against |

> **Legacy component-view bridge.** Apoli packs wrote this condition against the pre-1.21 item NBT layout, which 1.21 replaced with data components. The stack's components are therefore projected back into a legacy-shaped view — `Potion`, `CustomPotionColor`, `CustomPotionEffects`, `Enchantments`/`StoredEnchantments`, `display.Name`/`display.Lore`, `Damage`, `RepairCost`, `Unbreakable`, `CustomModelData` — merged over `minecraft:custom_data`, and the subtree match runs against that view. So `{ "nbt": "{Potion:\"minecraft:swiftness\"}" }` still matches a swiftness potion, and scalar types mirror the 1.20.1 save format exactly (enchantment `lvl` is a short: write `5s`).

**Example:**
```json
{ "type": "neoorigins:nbt", "nbt": "{Potion:\"minecraft:swiftness\"}" }
```

## `neoorigins:enchantment`

True when the stack has the given enchantment at or above a minimum level.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `enchantment` | resource id | yes | — | Enchantment ID |
| `min_level` | int | no | `1` | Minimum level |

## `neoorigins:ingredient`

True when the stack matches a vanilla ingredient (item ID or tag).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `item` | resource id | no | — | Exact item ID |
| `tag` | resource id | no | — | Item tag |

Bare item ID strings (no `type` field) also match via the ingredient fallback path.

The Origins wrapper form nests the item/tag under an `ingredient` key, and vanilla's union (array) form is accepted there too — it matches when *any* entry matches:

```json
{
  "type": "neoorigins:ingredient",
  "ingredient": [
    { "tag": "origins:ignore_diet" },
    { "tag": "origins:vegetarian" }
  ]
}
```

## `neoorigins:amount`

Numeric comparison against the stack count.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | int | no | `1` | Stack count threshold |

**Example:**
```json
{ "type": "neoorigins:amount", "comparison": ">=", "compare_to": 16 }
```

## `neoorigins:name`

True when the stack's display name (hover name: custom name if renamed, else the default item name) exactly equals the given text. False on empty stacks.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `name` | string | yes | — | Exact display-name text; always-false when absent |

**Example:**
```json
{ "type": "neoorigins:name", "name": "Excalibur" }
```

## `neoorigins:food`

True when the item has a food component (anything edible). No fields.

**Example:**
```json
{ "type": "neoorigins:food" }
```

## `neoorigins:and` / `neoorigins:or` / `neoorigins:not` (item)

Standard boolean combinators, same shape as entity conditions but operating on `ItemStack`. `all_of` / `any_of` are accepted as aliases of `and` / `or` (the Apoli 2.9+ renames), matching the entity-side conditions.
