---
title: Actions
nav_order: 8
---

# NeoOrigins 2.0 Entity Action Reference

Entity actions run against an entity target (usually the player who owns the power, or a bientity target depending on the call site). They're the side-effect half of the DSL — conditions filter, actions mutate.

**Canonical namespace:** `neoorigins:*` is the preferred form for new packs. Legacy `origins:*` and `apace:*` prefixes still work but log a one-shot `[2.0-legacy]` deprecation warning. Bare type names like `"type": "heal"` are auto-prefixed to `neoorigins:heal`. Section headers below still show the traditional `origins:*` names for familiarity with upstream docs; JSON examples use `neoorigins:*`.

**Call sites that dispatch actions:**
- `action_on_event.entity_action` — runs against the event's actor (player)
- `action_on_hit.entity_action` / `action_on_hit_taken.entity_action` — bientity (actor + target)
- `conditional.inner_action` — gated by the wrapping condition
- Nested inside meta verbs (`if_else`, `if_else_list`, `and`, `chance`, `delay`, `area_of_effect`)

On any parse error or unknown `type`, the action silently degrades to a no-op and logs a warning tagged `[CompatB]`.

---

# Core effect verbs

## `origins:apply_effect`

Applies a mob effect to the target. Accepts either a single inline effect spec or an `effects` array (only the first element is read — Apoli's multi-effect form is simulated by wrapping in `origins:and`).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effect` / `id` | resource id | yes (one of) | — | Mob effect id (e.g. `minecraft:regeneration`) |
| `duration` | int ticks | no | `200` | Effect duration |
| `amplifier` | int | no | `0` | Amplifier level (0 = level I) |
| `is_ambient` | bool | no | `false` | Ambient flag (suppresses some particles) |
| `show_particles` | bool | no | `true` | Render swirl particles |
| `show_icon` | bool | no | `true` | Show effect icon in HUD |
| `effects[0]` | object | alt | — | Alternative: array form where `effects[0]` carries the same fields |

**Example:**
```json
{ "type": "neoorigins:apply_effect", "effect": "minecraft:regeneration", "duration": 100, "amplifier": 1 }
```

Unknown effect ids are resolved at parse time; a missing registry entry logs a warning and no-ops.

---

## `origins:clear_effect`

Removes a specific mob effect, or every effect if none is specified.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effect` | resource id | no | — | Effect to remove. If omitted, all effects are cleared. |

**Example:**
```json
{ "type": "neoorigins:clear_effect", "effect": "minecraft:poison" }
```

---

## `origins:heal`

Heals the target by the given amount (half-hearts).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `amount` | float | no | `1.0` | HP to restore (half-hearts) |

**Example:**
```json
{ "type": "neoorigins:heal", "amount": 4.0 }
```

---

## `origins:damage`

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

## `origins:feed`

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

## `origins:exhaust`

Adds an exhaustion value to the target's food data (depletes saturation, eventually food).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `amount` | float | no | `1.0` | Exhaustion to add |

**Example:**
```json
{ "type": "neoorigins:exhaust", "amount": 3.0 }
```

---

## `origins:set_on_fire`

Sets the target on fire for a fixed duration.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `ticks` | int | no | `20` | Fire duration in ticks |

**Example:**
```json
{ "type": "neoorigins:set_on_fire", "ticks": 100 }
```

---

## `origins:extinguish`

Clears all fire ticks on the target. Takes no fields.

**Example:**
```json
{ "type": "neoorigins:extinguish" }
```

---

## `origins:add_velocity`

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

## `origins:launch`

Shortcut for "launch straight up." Pushes the target vertically and sets `hurtMarked` so the client syncs.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `speed` | float | no | `1.0` | Upward speed |

**Example:**
```json
{ "type": "neoorigins:launch", "speed": 1.5 }
```

---

## `origins:dismount`

Forces the target to stop riding its current vehicle. Takes no fields.

**Example:**
```json
{ "type": "neoorigins:dismount" }
```

---

## `origins:set_fall_distance`

Writes directly to the target's `fallDistance` field — useful to cancel imminent fall damage.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `fall_distance` | float | no | `0.0` | New fall distance |

**Example:**
```json
{ "type": "neoorigins:set_fall_distance", "fall_distance": 0.0 }
```

---

## `origins:play_sound`

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

## `origins:emit_game_event`

Emits a vanilla game event at the target's position (for sculk sensors, warden detection, etc.).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `event` / `game_event` | resource id | yes | — | Game event id (e.g. `minecraft:step`) |

**Example:**
```json
{ "type": "neoorigins:emit_game_event", "event": "minecraft:step" }
```

---

## `origins:swing_hand`

Swings the target's main hand. Takes no fields — off-hand is not supported at this time.

**Example:**
```json
{ "type": "neoorigins:swing_hand" }
```

---

## `origins:give`

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

## `origins:spawn_entity`

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

## `origins:spawn_projectile`

Spawns a projectile from the target's eye height, aimed along their look vector. Aliased to `neoorigins:spawn_projectile`. Non-projectile entity types fall back to a linear velocity shove along look.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_type` / `projectile` | resource id | yes | — | Entity type id |
| `speed` | float | no | `1.5` | Launch speed |
| `inaccuracy` | float | no | `0.0` | Random spread |
| `vertical_offset` | float | no | `0.0` | Added to the spawn Y (relative to eye height) |
| `effect_type` | string | no | `""` | Colour key from `VfxEffectTypes` (applied to `neoorigins:magic_orb` and custom VFX projectiles) |
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

---

## `origins:execute_command`

Runs a server command at permission level 2 (vanilla's function-permission-level default). Works for non-op players — mirrors upstream Origins behaviour. Output is suppressed.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `command` | string | yes | — | Command line (no leading slash) |

**Example:**
```json
{ "type": "neoorigins:execute_command", "command": "effect give @s minecraft:glowing 10 0" }
```

Runs only if the target is on a server (`player.level().getServer() != null`).

---

## `origins:set_block`

Replaces the block at the target's feet (their `blockPosition`) with the given block's default state.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `block` | resource id | yes | — | Block id |

**Example:**
```json
{ "type": "neoorigins:set_block", "block": "minecraft:cobweb" }
```

---

## `origins:modify_food`

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

## `origins:grant_power`

Dynamically grants a power to the target. Tracks dynamic grants separately from origin-granted powers so later `revoke_power` calls don't strip origin-granted ones. Fires `PowerGrantedEvent` and syncs to the client.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` / `power_id` | resource id | yes | — | Power id |

**Example:**
```json
{ "type": "neoorigins:grant_power", "power": "examplepack:super_jump" }
```

---

## `origins:revoke_power`

Removes a previously `grant_power`ed power. No-op if the power was granted by an origin (not dynamic).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` / `power_id` | resource id | yes | — | Power id |

**Example:**
```json
{ "type": "neoorigins:revoke_power", "power": "examplepack:super_jump" }
```

---

## `origins:explode`

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

## `origins:gain_air`

Restores air supply (bubbles), clamped to `getMaxAirSupply()`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `amount` | int | no | `10` | Air ticks to add |

**Example:**
```json
{ "type": "neoorigins:gain_air", "amount": 40 }
```

---

## `origins:change_resource`

Mutates a `resource` power's stored integer. The resource state lives on a player attachment, keyed by power id.

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

---

## `origins:trigger_cooldown`

Manually places a power on cooldown. Used when an ability's fire path is custom but should still show the HUD cooldown bar.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` | resource id | yes | — | Power id to cool down |
| `cooldown` | int ticks | no | `20` | Cooldown duration |

**Example:**
```json
{ "type": "neoorigins:trigger_cooldown", "power": "examplepack:fireball", "cooldown": 40 }
```

---

# Meta verbs

## `origins:nothing`

Explicit no-op. Useful as the default branch of `if_else` or for placeholder authoring. Takes no fields.

**Example:**
```json
{ "type": "neoorigins:nothing" }
```

---

## `origins:and`

Runs a sequence of actions in order against the same target.

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

## `origins:if_else`

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

## `origins:if_else_list`

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

## `origins:chance`

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

## `origins:delay`

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

## `origins:area_of_effect`

Iterates every `ServerPlayer` within the radius and runs `entity_action` against each. **Gotcha:** non-player living entities are skipped — `EntityAction` is typed on `ServerPlayer`, so AoE cannot target mobs in the current compat layer.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | float | no | `16.0` | Radius |
| `shape` | string | no | `"sphere"` | `"sphere"` culls by squared distance; any other string skips the distance cull (behaves like a cube/AABB) |
| `include_source` | bool | no | `true` | Whether the source player is included |
| `entity_action` | action | no | noop | Runs per target |
| `entity_condition` | condition | no | always-true | Target filter |

**Example:**
```json
{ "type": "neoorigins:area_of_effect",
  "radius": 8.0,
  "shape": "sphere",
  "include_source": false,
  "entity_action": { "type": "neoorigins:set_on_fire", "ticks": 40 } }
```

---

# Set / capability verbs (2.0)

These verbs are new in 2.0 and read from `ActionContextHolder` — the service that publishes the current dispatch context while `EventPowerIndex` walks handlers. They no-op outside a compatible context.

## `neoorigins:add_to_set`

Adds the current bientity target's UUID to a named entity-set on the actor player. The backing sets power relationship tracking (who I've tagged, who I'm tracking, etc.). Aliased as `origins:add_to_set`.

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

Removes the current bientity target's UUID from a named entity-set. Same context requirements as `add_to_set`. Aliased as `origins:remove_from_set`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `set` | string | yes | — | Set name |

**Example:**
```json
{ "type": "neoorigins:remove_from_set", "set": "tagged_enemies" }
```

---

## `neoorigins:toggle`

Flips or sets a named toggle state on the target. Used by 2.0's `toggle` alias family. If `value` is given it's set explicitly; otherwise the current state is flipped (default starting value: false).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` | string | yes | — | Toggle key (usually the power id) |
| `value` | bool | no | — | If present, set to this value; otherwise toggle |

**Example:**
```json
{ "type": "neoorigins:toggle", "power": "examplepack:flight_toggle" }
```

---

## `neoorigins:cancel_event`

Cancels the currently dispatched event. Used internally by the `food_restriction` alias to reject food consumption.

**Gotcha — context-only:** works only when the current `ActionContextHolder` value is a `FoodContext` (wraps the underlying `ICancellableEvent`) or is itself an `ICancellableEvent`. No-op elsewhere.

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
