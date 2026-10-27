---
title: "Meta verbs"
parent: "Entity Actions"
grand_parent: "DSL Reference"
nav_order: 2
permalink: /actions/meta
---

# Meta verbs

Verbs that combine, branch, randomise, delay or relocate other actions, including `area_of_effect`.

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
| `actions` | array of action | no | `[]` | Actions to run in order. A single action object is also accepted; absent means the action does nothing |

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
| `actions` | array | no | `[]` | Each entry has `condition` + `action`. An entry whose `condition` is absent or not an object never matches. A lone entry object is accepted as a one-entry list |

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

Picks one action from a weighted list and runs it. Each `actions` entry is a **wrapper**, not an action: Apoli's `element` key holds the action (`action` is accepted as a synonym; `element` wins if both are present) and `weight` sets its relative chance. A bare action written directly as an entry has neither key, so that branch does nothing and a warning is logged.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `actions` | array of `{element, weight}` | yes | — | Candidate entries; one is picked weighted-randomly. `weight` is an int, default `1`. Weights are not validated: keep them positive, since a total of 0 makes the action fail when it runs |

**Example:**
```json
{ "type": "neoorigins:choice", "actions": [
  { "weight": 3, "element": { "type": "neoorigins:heal", "amount": 2.0 } },
  { "weight": 1, "element": { "type": "neoorigins:set_on_fire", "ticks": 40 } }
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

Positional wrapper: runs its inner `action` with the current context block position shifted by `x`/`y`/`z` blocks — Apoli's positional offset verb. When the dispatch context resolves a block position (`block_use` / `bonemeal` events, `raycast` block hits, `block_action_at`, the `area_of_effect` block fan-out), the shifted position is published as the context block for the inner action, so block verbs (`grow`, `transform_block`, `execute_command` with `~ ~ ~`) act on the shifted block. When no block context resolves, or all three offsets are 0, the inner action runs unchanged (transparent pass-through).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `action` | action | no | noop | Inner action to run at the offset block position |
| `x` | int | no | `0` | X offset in blocks (applied to the context block position) |
| `y` | int | no | `0` | Y offset in blocks (applied to the context block position) |
| `z` | int | no | `0` | Z offset in blocks (applied to the context block position) |

**Example: bonemeal the block two above the one you hit**
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

Iterates every living entity within the radius and runs `entity_action` against each. Mobs **and** players are hit when `entity_action` uses an *entity-general* verb: `damage`, `heal`, `apply_effect`, `clear_effect`, `set_on_fire`, `add_velocity`, `spawn_particles`, `shear`, `dye`, … (and an `and` of only those). A player-only verb (e.g. `launch`, `set_block`) still runs on player targets only and skips mobs. Mob targets are also subject to the friendly-fire protections in `gameplay.toml` (pets/minions/villagers/iron golems/animals).

`entity_condition` filters which entities are affected. When it uses an entity-general condition (`entity_type` incl. a `#tag` group, `in_tag`, `target_group`, `health`, `relative_health`, `has_effect`/`status_effect`, `on_fire`, `living`, `nbt`, `in_block_anywhere`, `equipped_item`, `constant`, and `and`/`or`/`not` of those), it filters **both mobs and players**, so an aura can restrict itself to *players only* (`entity_type: minecraft:player`), a tag group, a specific mob, etc. A player-only condition verb only gates player targets (mobs bypass it, as before).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | float | no | `16.0` for entities, `2.0` for the block sweep | Reach of the sweep. A value you write applies to both sweeps; the block sweep caps it at 16 |
| `shape` | string or object | no | `"sphere"` | `"cube"` skips the distance cull (plain AABB); anything else culls by squared distance. May also be an object: `{ "type": "cone", "angle": 60 }` additionally restricts the sphere to a cone of that full width (degrees) around the caster's look direction |
| `include_source` | bool | no | see below | Whether the source entity is included |
| `include_target` | bool | no | see below | Apoli spelling of `include_source`, read only when `include_source` is absent |
| `entity_action` | action | no | noop | Runs per affected entity (mobs + players for entity-general verbs) |
| `bientity_action` | bientity action | no | — | Apoli form: runs as (caster, affected entity). Takes precedence over `entity_action` |
| `entity_condition` | condition | no | always-true | Target filter; entity-general conditions filter mobs + players (see above) |
| `bientity_condition` | bientity condition | no | always-true | Apoli form: filter evaluated as (caster, affected entity) |
| `block_action` | action | no | — | Block fan-out (see below): runs at every block position in radius, each published as the context block |
| `block_condition` | block condition | no | matches all | Filter for the block fan-out: only positions matching it run `block_action`. Only used with `block_action` |

**Apoli `bientity_action` form.** Apoli's own `area_of_effect` names its per-target action `bientity_action` and its filter `bientity_condition`, both taking the (actor, target) pair, usually wrapped in `target_action` / `target_condition`. Both spellings are accepted. Two defaults differ between the forms: the caster is included by default with `entity_action` (long-standing behaviour) but **excluded** by default with `bientity_action`, matching Apoli. Otherwise a cone attack damages the player who cast it. A `bientity_condition` that cannot be evaluated against a non-player entity skips the entity fan-out entirely and is reported in the compat summary, since silently dropping an *exclusion* filter would hit bystanders it was written to spare.

**Block fan-out (Apoli `block_action` form).** When `block_action` is set, the action additionally sweeps every **block position** within `radius`, centered on the context block position when one resolves (raycast hit, `block_use`/`bonemeal` event, `block_action_at`) and on the source's feet otherwise. Each position is published as the context block, so nested block verbs (`grow`/`bonemeal`, `transform_block`, `offset`, `execute_command`) self-resolve. `block_condition` gates each position; `shape: "sphere"` culls by distance as for entities. With no `radius` written the block sweep covers 2 blocks (33 positions) rather than borrowing the entity sweep default of 16, which is 17,077 positions per activation. A `radius` you do write applies to both sweeps, and the block sweep caps it at 16. Apoli packs use small radii here (e.g. 2).

**Example: burn nearby mobs but never other players**
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

**Example: affect players only (e.g. a support aura)**
```json
{ "type": "neoorigins:area_of_effect",
  "radius": 8.0,
  "entity_condition": { "type": "neoorigins:entity_type", "entity_type": "minecraft:player" },
  "entity_action": { "type": "neoorigins:apply_effect", "effect": "minecraft:regeneration", "duration": 60 } }
```

**Example (block fan-out): bonemeal every grass block in a 2-block sphere**
```json
{ "type": "neoorigins:area_of_effect",
  "radius": 2.0,
  "shape": "sphere",
  "block_condition": { "type": "neoorigins:block", "block": "minecraft:grass_block" },
  "block_action": { "type": "neoorigins:grow" } }
```

**Example (a breath attack): a 60° cone in front of the caster, sparing players**
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
