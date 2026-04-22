# NeoOrigins 2.0 Entity Action Reference

Entity actions run against an entity target (usually the player who owns the power, or a bientity target depending on the call site). They're the side-effect half of the DSL — conditions filter, actions mutate.

**Namespace tolerance:** every action accepts `origins:`, `apace:`, and often `neoorigins:` prefixes. Bare type names like `"type": "heal"` are auto-prefixed to `origins:heal` at parse time, so pre-namespace Origins JSON and loose community packs load without edits.

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
{ "type": "origins:apply_effect", "effect": "minecraft:regeneration", "duration": 100, "amplifier": 1 }
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
{ "type": "origins:clear_effect", "effect": "minecraft:poison" }
```

---

## `origins:heal`

Heals the target by the given amount (half-hearts).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `amount` | float | no | `1.0` | HP to restore (half-hearts) |

**Example:**
```json
{ "type": "origins:heal", "amount": 4.0 }
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
{ "type": "origins:damage", "amount": 2.0, "source": { "name": "magic" } }
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
{ "type": "origins:feed", "food": 6, "saturation": 0.6 }
```

---

## `origins:exhaust`

Adds an exhaustion value to the target's food data (depletes saturation, eventually food).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `amount` | float | no | `1.0` | Exhaustion to add |

**Example:**
```json
{ "type": "origins:exhaust", "amount": 3.0 }
```

---

## `origins:set_on_fire`

Sets the target on fire for a fixed duration.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `ticks` | int | no | `20` | Fire duration in ticks |

**Example:**
```json
{ "type": "origins:set_on_fire", "ticks": 100 }
```

---

## `origins:extinguish`

Clears all fire ticks on the target. Takes no fields.

**Example:**
```json
{ "type": "origins:extinguish" }
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
{ "type": "origins:add_velocity", "y": 1.2, "set": false }
```

---

## `origins:launch`

Shortcut for "launch straight up." Pushes the target vertically and sets `hurtMarked` so the client syncs.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `speed` | float | no | `1.0` | Upward speed |

**Example:**
```json
{ "type": "origins:launch", "speed": 1.5 }
```

---

## `origins:dismount`

Forces the target to stop riding its current vehicle. Takes no fields.

**Example:**
```json
{ "type": "origins:dismount" }
```

---

## `origins:set_fall_distance`

Writes directly to the target's `fallDistance` field — useful to cancel imminent fall damage.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `fall_distance` | float | no | `0.0` | New fall distance |

**Example:**
```json
{ "type": "origins:set_fall_distance", "fall_distance": 0.0 }
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
{ "type": "origins:play_sound", "sound": "minecraft:entity.player.levelup", "volume": 1.0, "pitch": 1.0 }
```

---

## `origins:emit_game_event`

Emits a vanilla game event at the target's position (for sculk sensors, warden detection, etc.).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `event` / `game_event` | resource id | yes | — | Game event id (e.g. `minecraft:step`) |

**Example:**
```json
{ "type": "origins:emit_game_event", "event": "minecraft:step" }
```

---

## `origins:swing_hand`

Swings the target's main hand. Takes no fields — off-hand is not supported at this time.

**Example:**
```json
{ "type": "origins:swing_hand" }
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
{ "type": "origins:give", "stack": { "item": "minecraft:apple", "count": 3 } }
```

---

## `origins:spawn_entity`

Spawns an entity at the target's feet. No orientation control — the entity faces world-default.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_type` | resource id | yes | — | Entity type id |

**Example:**
```json
{ "type": "origins:spawn_entity", "entity_type": "minecraft:zombie" }
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

**Example:**
```json
{ "type": "origins:spawn_projectile", "entity_type": "minecraft:arrow", "speed": 2.0 }
```

---

## `origins:execute_command`

Runs a server command at permission level 2 (vanilla's function-permission-level default). Works for non-op players — mirrors upstream Origins behaviour. Output is suppressed.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `command` | string | yes | — | Command line (no leading slash) |

**Example:**
```json
{ "type": "origins:execute_command", "command": "effect give @s minecraft:glowing 10 0" }
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
{ "type": "origins:set_block", "block": "minecraft:cobweb" }
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
{ "type": "origins:modify_food", "food": 4, "saturation": 0.4 }
```

---

## `origins:grant_power`

Dynamically grants a power to the target. Tracks dynamic grants separately from origin-granted powers so later `revoke_power` calls don't strip origin-granted ones. Fires `PowerGrantedEvent` and syncs to the client.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` / `power_id` | resource id | yes | — | Power id |

**Example:**
```json
{ "type": "origins:grant_power", "power": "examplepack:super_jump" }
```

---

## `origins:revoke_power`

Removes a previously `grant_power`ed power. No-op if the power was granted by an origin (not dynamic).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` / `power_id` | resource id | yes | — | Power id |

**Example:**
```json
{ "type": "origins:revoke_power", "power": "examplepack:super_jump" }
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
{ "type": "origins:explode", "power": 4.0, "destruction_type": "break", "create_fire": false }
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
{ "type": "origins:gain_air", "amount": 40 }
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
{ "type": "origins:change_resource", "resource": "examplepack:mana", "operation": "add", "change": -5 }
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
{ "type": "origins:trigger_cooldown", "power": "examplepack:fireball", "cooldown": 40 }
```

---

# Meta verbs

## `origins:nothing`

Explicit no-op. Useful as the default branch of `if_else` or for placeholder authoring. Takes no fields.

**Example:**
```json
{ "type": "origins:nothing" }
```

---

## `origins:and`

Runs a sequence of actions in order against the same target.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `actions` | array of action | yes | `[]` | Actions to run in order |

**Example:**
```json
{ "type": "origins:and", "actions": [
  { "type": "origins:heal", "amount": 2.0 },
  { "type": "origins:play_sound", "sound": "minecraft:entity.player.levelup" }
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
{ "type": "origins:if_else",
  "condition": { "type": "origins:submerged_in", "fluid": "minecraft:water" },
  "if_action": { "type": "origins:gain_air", "amount": 40 },
  "else_action": { "type": "origins:nothing" } }
```

---

## `origins:if_else_list`

First-match-wins chain of `(condition, action)` pairs. Stops after the first matching branch.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `actions` | array | yes | `[]` | Each entry has `condition` + `action` |

**Example:**
```json
{ "type": "origins:if_else_list", "actions": [
  { "condition": { "type": "origins:in_rain" }, "action": { "type": "origins:heal", "amount": 2 } },
  { "condition": { "type": "origins:daytime" }, "action": { "type": "origins:set_on_fire", "ticks": 40 } }
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
{ "type": "origins:chance", "chance": 0.2, "action": { "type": "origins:play_sound", "sound": "minecraft:entity.cat.ambient" } }
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
{ "type": "origins:delay", "ticks": 40, "action": { "type": "origins:extinguish" } }
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
{ "type": "origins:area_of_effect",
  "radius": 8.0,
  "shape": "sphere",
  "include_source": false,
  "entity_action": { "type": "origins:set_on_fire", "ticks": 40 } }
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
