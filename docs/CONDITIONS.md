# NeoOrigins 2.0 Condition Reference

Conditions evaluate to true/false against an entity (usually the power's owning player). They gate power activation, `action_on_event` triggers, `conditional` wrappers, and bientity interactions. Any power can also be gated as a whole via the top-level `power_condition` / `power_condition_mode` fields — see the common-fields table in [POWER_TYPES.md](POWER_TYPES.md).

**Canonical namespace:** `neoorigins:*` is the preferred form for new packs. Legacy `neoorigins:*` and `apace:*` prefixes still work but log a one-shot `[2.0-legacy]` deprecation warning. Bare type names (e.g. `"type": "and"`) are auto-prefixed with `neoorigins:`. Section headers below still show the traditional `neoorigins:*` names for familiarity with upstream documentation; the JSON examples use the canonical `neoorigins:*` form.

**Fail-closed semantics:** a malformed or unsupported condition logs a warning and returns `false` rather than throwing. Bientity / damage / food conditions that require a dispatch context also return `false` when evaluated outside that context.

**Object or array.** Every condition-valued field (`condition`, …) accepts either a single condition object **or** an array. An array is combined as logical AND (an implicit `neoorigins:and`) — every element must pass. An empty array is treated as always-true. *(Previously a bare array in these fields silently no-opped.)*

**Universal `inverted` field:** every condition supports a top-level `"inverted": true` flag that flips its result. Compatible with the Apoli/Origins convention used by upstream packs.

```json
// "true when the player is NOT swimming"
{ "type": "neoorigins:swimming", "inverted": true }

// inside an AND, etc.
{ "type": "neoorigins:and", "conditions": [
  { "type": "neoorigins:creative_flying" },
  { "type": "neoorigins:on_fire", "inverted": true }
]}
```

The `inverted` flag is also honored on nested conditions inside `and` / `or` / `not` wrappers — every recursive parse pass checks for it.

**Common comparison fields:** most numeric conditions accept `"comparison"` (one of `==`, `!=`, `<`, `<=`, `>`, `>=`) and `"compare_to"` (number). Defaults to `">="` and `0` unless stated otherwise.

**Item conditions** — the separate verb set that matches against an `ItemStack` (used inside `equipped_item` / `inventory` entity conditions, event-power `item_condition` filters, and `if_else` item actions) is documented in the [Item conditions section of ACTIONS.md](ACTIONS.md#item-conditions).

---

# Meta conditions

## `neoorigins:and` (alias `neoorigins:all_of`)

Logical AND of nested conditions. `all_of` is Apoli 2.9+'s rename of `and`: imported packs using `origins:all_of` / `apoli:all_of` dispatch here unchanged.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `conditions` | array of condition objects | yes | `[]` | Inner conditions; all must pass |

**Example:**
```json
{ "type": "neoorigins:and", "conditions": [ {"type": "neoorigins:sneaking"}, {"type": "neoorigins:on_ground"} ] }
```

## `neoorigins:or` (alias `neoorigins:any_of`)

Logical OR of nested conditions. `any_of` is Apoli 2.9+'s rename of `or`: imported packs using `origins:any_of` / `apoli:any_of` dispatch here unchanged.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `conditions` | array of condition objects | yes | `[]` | Inner conditions; at least one must pass |

## `neoorigins:not`

Negates a single inner condition.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `condition` | condition object | yes | — | Condition to negate |

## `neoorigins:actor_condition`

Unwraps and evaluates the inner condition against the actor. In Apoli this filters the actor half of a bientity pair; here the inner condition is simply evaluated on the entity under test (the player). Fails closed when `condition` is missing.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `condition` | condition object | yes | — | Inner condition evaluated on the actor |

**Example:**
```json
{ "type": "neoorigins:actor_condition", "condition": { "type": "neoorigins:sneaking" } }
```

## `neoorigins:constant`

Always-true or always-false literal.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `value` | boolean | no | `false` | Literal result. Missing field treated as `false`. |

---

# Entity conditions

These evaluate against a single entity — usually the power's owning player.

## `neoorigins:sneaking`

True while shift is held. No fields.

## `neoorigins:sprinting`

True while sprinting. No fields.

## `neoorigins:on_ground`

True while standing on a block. No fields.

## `neoorigins:in_water`

True while touching water. No fields.

## `neoorigins:swimming`

True while the swim animation is active. No fields.

## `neoorigins:submerged_in_water`

True while the eye position is inside water. No fields.

## `neoorigins:fall_flying`

True while elytra-gliding. No fields.

## `neoorigins:invisible`

True while the invisible flag is set. No fields.

## `neoorigins:moving`

True while horizontal delta-movement is nonzero. No fields.

## `neoorigins:in_rain`

True when rain is falling at the entity's block position with sky access (server-side only); returns false while mounted. If **Vampires Need Umbrellas** is installed, holding an umbrella in either hand or a Curios/Accessories slot blocks the rain entirely (mirroring `exposed_to_sun`), so rain/water-damage origins like Wet Fur and True Hydrophobia stop hurting while you carry one.

## `neoorigins:daytime`

True for in-game times 0–12999. No fields.

## `neoorigins:exposed_to_sky`

True when the sky is visible from the entity's block position (server-side only).

## `neoorigins:exposed_to_sun`

True during daytime (time 0–11999) with sky access and no rain. Includes helmet protection (damageable helmets absorb the burn at the cost of durability). Helmets in the `neoorigins:sun_permeable` item tag (open/mesh helmets — `minecraft:chainmail_helmet` by default) do **not** shade the player; add modded see-through helmets to that tag via a datapack. Helmet protection as a whole can be disabled with the `[sun_damage] helmet_protection` config. If **Vampires Need Umbrellas** is installed, holding an umbrella in either hand or a Curios/Accessories slot blocks sun damage entirely (checked before helmets).

## `neoorigins:on_fire` (alias `neoorigins:fire`)

True while the entity is on fire. No fields.

## `neoorigins:passenger` (alias `neoorigins:riding`)

True while the entity is a passenger of something. No fields.

## `neoorigins:using_item`

True while the entity is actively using an item (eating, drawing bow, etc.). No fields.

## `neoorigins:ticking`

True unless the entity has been removed. No fields.

## `neoorigins:exists`

True when the entity is non-null and not removed. No fields.

## `neoorigins:living`

True when `isAlive()` returns true. No fields.

## `neoorigins:creative_flying`

True when creative flight is engaged. No fields.

## `neoorigins:creative_mode`

True when the player is in creative or spectator gamemode. No fields. Apoli-derivative packs (Medieval Origins Revival etc.) use this to gate resource drains so they don't run for creative players — `medievalorigins:creative_mode` dispatches here via the namespace fallback.

## `neoorigins:block_collision`

True when the entity's bounding box — shifted by `offset_x`/`offset_y`/`offset_z` — intersects at least one matching block. Apoli semantics: the offsets are multipliers of the box's **own dimensions** (X and Z scale by the entity's width, Y by its height), so `"offset_x": 0.01` nudges the box 1% of the entity's width. With a `block_condition`, at least one intersected block must match it; without one, any block with a real collision shape counts.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `offset_x` | float | no | `0.0` | Box X shift as a multiple of the entity's width |
| `offset_y` | float | no | `0.0` | Box Y shift as a multiple of the entity's height |
| `offset_z` | float | no | `0.0` | Box Z shift as a multiple of the entity's width |
| `block_condition` | block condition | no | any collidable block | Block filter; supports `block`/`id`, `in_tag`, `and`/`or`, and the `offset` wrapper |

**Example — touching an iron-tagged block on either side:**
```json
{
  "type": "neoorigins:block_collision",
  "offset_x": 0.01,
  "block_condition": { "type": "neoorigins:in_tag", "tag": "fairy:iron" }
}
```

## `neoorigins:collided_horizontally`

True when the entity ran into a wall this tick — a thin wrapper over vanilla `Entity.horizontalCollision`, mirroring Apoli's condition of the same name. Used by conditioned climbing powers (e.g. Origins++) that should only engage while the player is pressed against a surface.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| (no fields) | — | — | — | Marker condition. |

## `neoorigins:replacable` (alias `neoorigins:replaceable`)

True when the block at the entity's block position is replaceable (air, short grass, snow layers, etc. — vanilla `canBeReplaced()`). Apoli-parity condition; the misspelled `replacable` is the canonical Apoli name, and the corrected `replaceable` spelling is accepted as a synonym. No fields.

**Example:**
```json
{ "type": "neoorigins:replacable" }
```

## `neoorigins:health`

Numeric comparison against current health.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | number | no | `0.0` | Health threshold |

## `neoorigins:relative_health`

Numeric comparison against `health / maxHealth` (0.0–1.0).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | number | no | `0.0` | Ratio threshold |

## `neoorigins:food_level` (alias `neoorigins:food`)

Numeric comparison against food level (0–20).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | number | no | `0.0` | Hunger threshold |

## `neoorigins:saturation_level`

Numeric comparison against the player's food saturation level.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | number | no | `0.0` | Saturation threshold |

**Example:**
```json
{ "type": "neoorigins:saturation_level", "comparison": "<", "compare_to": 2.0 }
```

## `neoorigins:fall_distance`

Numeric comparison against the entity's `fallDistance` field.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | number | no | `0.0` | Distance in blocks |

## `neoorigins:height`

Numeric comparison against the entity's Y coordinate.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | number | no | `0.0` | World Y threshold |

## `neoorigins:distance_from_coordinates`

Numeric comparison against the entity's distance from a reference point (`world_origin` at 0,0,0 or `world_spawn`), with selectable distance metric and per-axis exclusion.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `"=="` | Comparison operator |
| `compare_to` | number | no | `0.0` | Distance threshold (required in practice) |
| `reference` | string | no | `"world_origin"` | Reference point: `world_origin` (0,0,0) or `world_spawn` |
| `offset` | object | no | — | Per-axis `{x,y,z}` offset added to the reference point |
| `shape` | string | no | `"cube"` | Distance metric: `cube` (Chebyshev), `star` (Manhattan), `sphere` (Euclidean) |
| `ignore_x` | bool | no | `false` | Exclude the X axis from the distance |
| `ignore_y` | bool | no | `false` | Exclude the Y axis from the distance |
| `ignore_z` | bool | no | `false` | Exclude the Z axis from the distance |
| `result_on_the_wrong_dimension` | number | no | — | Distance value substituted when off the reference's dimension (absent → use real distance) |

**Example — within 100 blocks of world spawn (horizontal only):**
```json
{ "type": "neoorigins:distance_from_coordinates",
  "reference": "world_spawn",
  "shape": "sphere",
  "ignore_y": true,
  "comparison": "<=",
  "compare_to": 100 }
```

## `neoorigins:armor_value`

Numeric comparison against the entity's armor value.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | number | no | `0.0` | Armor points |

## `neoorigins:amount`

Generic numeric wrapper. **Standalone fallback compares against current health** — context-dependent, retained for pack compatibility.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | number | no | `0.0` | Threshold |

## `neoorigins:xp_level`

Numeric comparison against `experienceLevel`. Also registered as `neoorigins:xp_levels` (same fields, same behavior — both are canonical types, not aliases).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | int | no | `0` | Level threshold |

## `neoorigins:xp_levels`

Numeric comparison against the entity's experience level. A synonym of [`neoorigins:xp_level`](#neooriginsxp_level) (same fields, same behaviour — both are canonical types).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | int | no | `0` | Experience-level threshold |

**Example:**
```json
{ "type": "neoorigins:xp_levels", "comparison": ">=", "compare_to": 30 }
```

## `neoorigins:xp_points`

Numeric comparison against `totalExperience`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | int | no | `0` | Points threshold |

## `neoorigins:air`

Numeric comparison against the entity's air supply (0–300; full air is 300, drowning starts at 0).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | number | no | `0.0` | Air-supply threshold (0–300) |

**Example:**
```json
{ "type": "neoorigins:air", "comparison": "<", "compare_to": 60 }
```

## `neoorigins:dimension`

Checks current dimension ID.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `dimension` | resource location | no | — | e.g. `"minecraft:overworld"`; always-true when absent |

## `neoorigins:biome`

Checks biome at the entity's block position. Precedence: `biome` > `tag`/`biome_tag` > `condition`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `biome` | resource location | no | — | Exact biome ID |
| `tag` | resource location | no | — | Biome tag (used when `biome` absent) |
| `biome_tag` | resource location | no | — | Synonym for `tag` (used by several built-in JSONs) |
| `condition` | object | no | — | Nested biome sub-condition (used when no ID/tag present) |

Always-true when none of the fields are present.

**Nested `condition`:** only the `temperature` sub-type is supported — it compares the biome's base temperature using the usual `comparison`/`compare_to` fields. Any other sub-condition type logs a warning and fails closed (use biome tags instead).

**Example:**
```json
{ "type": "neoorigins:biome",
  "condition": { "type": "neoorigins:temperature", "comparison": "<", "compare_to": 0.15 } }
```

## `neoorigins:in_tag`

Biome tag check (equivalent to `neoorigins:biome` with `tag`).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `tag` | resource location | no | — | Biome tag ID; always-true when absent |

## `neoorigins:submerged_in`

Fluid-membership check at the entity's eye position.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `fluid` | string | no | `""` | `"minecraft:water"`, `"minecraft:lava"`, or anything else (matches either) |

## `neoorigins:fluid_height`

Numeric comparison against the submersion height for a given fluid tag.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `fluid` | string | no | `""` | `"minecraft:water"` or `"minecraft:lava"`; otherwise compares against `0` |
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | number | no | `0.0` | Height threshold |

## `neoorigins:temperature`

Numeric comparison against the biome's base temperature.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | number | no | `0.0` | Temperature threshold |

## `neoorigins:body_temperature`

Numeric comparison against a player's **[Cold Sweat](https://modrinth.com/mod/cold-sweat)** (`cold_sweat`) temperature reading. Integration condition: gate the power with `"required_mods": ["cold_sweat"]`. When Cold Sweat is absent the condition **fails closed** (always false) and logs a warning.

Distinct from `neoorigins:temperature` above (which reads the biome's base temperature). Cold Sweat's `core` (body) temp runs roughly **−100** (freezing death) … **+100** (burning death), 0 = neutral; `base`/`world` use the ambient scale; resistance/dampening traits sit in a ~0..1 range.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `trait` | enum | no | `core` | Which trait to read: `core`, `base`, `world`, `heat_resistance`, `cold_resistance`, `heat_dampening`, `cold_dampening`, `freezing_point`, `burning_point`, `rate` |
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | number | no | `0` | Threshold the reading is compared against |

**Example — true once the player is dangerously hot:**
```json
{ "type": "neoorigins:body_temperature", "trait": "core", "comparison": ">=", "compare_to": 50 }
```

## `neoorigins:light_level`

Numeric comparison against ambient light at the entity's block position. Also registered as `neoorigins:brightness` (same fields, same behavior — both are canonical types, not aliases).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `light_type` | string | no | `"any"` | `"sky"`, `"block"`, or `"any"` |
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | int | no | `0` | Light threshold (0–15) |

Omitting `light_type` (or any value other than `"sky"`/`"block"`) samples the max local brightness — the higher of the sky and block light layers.

## `neoorigins:brightness`

Numeric comparison against ambient light at the entity's block position. A synonym of [`neoorigins:light_level`](#neooriginslight_level) (same fields, same behaviour — both are canonical types).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `light_type` | string | no | `"any"` | Which light layer to sample: `"sky"`, `"block"`, or `"any"` (max local brightness) |
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | int | no | `0` | Light threshold (0–15) |

**Example:**
```json
{ "type": "neoorigins:brightness", "comparison": "<=", "compare_to": 7 }
```

## `neoorigins:time_of_day`

Numeric comparison against `level.getDayTime() % 24000`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | long | no | `0` | Tick within day |

## `neoorigins:weather`

Checks the current weather state against one of `clear`, `rain`, `raining`, `thunder`, or `thundering` — `rain`/`raining` and `thunder`/`thundering` are synonyms, matched case-insensitively. `clear` means neither raining nor thundering; `rain` means raining but not thundering; `thunder` matches any thunderstorm. **Unusual:** accepts either `"state"` or `"value"`; with both absent the state defaults to `"clear"`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `state` | string | no | `"clear"` | `"clear"`, `"rain"`/`"raining"`, or `"thunder"`/`"thundering"` (case-insensitive) |
| `value` | string | no | — | Synonym for `state` (read when `state` absent) |

## `neoorigins:moon_phase`

Numeric comparison against the moon phase index 0–7. **Default comparison is `"=="`**.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `"=="` | Comparison operator |
| `compare_to` | int | no | `0` | Phase index |

## `neoorigins:on_block`

True when the entity is on ground and the block directly below matches the nested block condition. When `block_condition` is omitted entirely, the condition is just "standing on any block" (plain `onGround()` check).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `block_condition` | object | no | — | Nested block condition (`block`/`id`, `in_tag`, or `and`/`or` combinator); absent → on any ground |
| `block_condition.id` | resource location | no | — | Block ID to match (also accepts `block`) |

## `neoorigins:block`

Block check at the entity's current position — accepts either a nested `block_condition` object or the same fields at the top level.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `block_condition` | object | no | — | Optional wrapper |
| `block` / `id` | resource location | no | — | Exact block ID |
| `tag` | resource location | no | — | Block tag (used when ID absent) |

**Unusual:** accepts `block` or `id` for the block ID, and will read fields at top level if `block_condition` is omitted.

## `neoorigins:in_block` (alias `neoorigins:in_block_anywhere`)

Block check at the entity's current position via an optional wrapper.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `block_condition` | object | no | — | Wrapper |
| `block_condition.block` / `block_condition.id` | resource location | no | — | Block ID |

Always-true when the wrapper is absent or no ID is present (fail-open — use `neoorigins:not` + a specific block condition if you need strict gating).

## `neoorigins:hardness`

Numeric comparison against a block's destroy hardness. **Context-dependent:** inside a raycast `block_action` dispatch it tests the raycast-hit block; otherwise it falls back to the block the entity is looking at (20-block pick). False when no block is found. Used by spell-break-style powers (e.g. Mage `spell_break`).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | number | no | `0.0` | Block hardness threshold |

**Example:**
```json
{ "type": "neoorigins:hardness", "comparison": "<=", "compare_to": 1.5 }
```

## `neoorigins:entity_type`

Checks entity type against a resource location. For a player entity this is always `minecraft:player`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_type` | resource location | no | — | Entity type ID |
| `type_id` | resource location | no | — | Alias for `entity_type` |

Always-true when both fields are absent.

## `neoorigins:equipped_item`

Inspects an item in a given equipment slot.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `equipment_slot` | string | no | `"mainhand"` | `"head"`, `"chest"`, `"legs"`, `"feet"`, `"offhand"`, `"mainhand"`, or `"accessory"` |
| `slot_type` | string | no | — | Only used when `equipment_slot` is `"accessory"`: narrows to a named accessory/curio slot type (e.g. `ring`, `belt`, `hands`); absent → any accessory slot |
| `item_condition` | object | no | — | Nested condition (see below) |

**Unusual:** `item_condition` has its own internal shape. Accepts any of:
- `{ "id": "minecraft:stick" }` — exact item ID
- `{ "tag": "c:tools/pickaxe" }` — item tag
- `{ "type": "neoorigins:empty" }` — slot is empty (also `apace:empty`)
- `{ "ingredient": { "item": "..." } }` or `{ "ingredient": { "tag": "..." } }` — ingredient-style wrapper

Always-true when `item_condition` is absent.

**Accessory slots:** `"accessory"` inspects worn trinkets from Curios and/or Accessories (Wisp Forest), aggregating both. Each is a soft dependency — with neither installed the `accessory` slot matches nothing. When `item_condition` is present it passes if *any* equipped accessory stack matches; absent, it is a presence check (any accessory equipped). This integration is **1.21.1 only** — Accessories has no 26.1 build.

## `neoorigins:inventory`

Counts inventory contents matching an item condition and compares the count against a threshold. The default comparison (`>` `0`) reads as "has at least one match".

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `inventory_types` | list of string | no | `["inventory"]` | Inventories to scan: `inventory` (the player's main inventory, including hotbar, armor and offhand slots) and/or `ender_chest` |
| `process_mode` | string | no | `"stacks"` | `"stacks"` counts matching stacks; `"items"` sums their stack counts |
| `item_condition` | item condition | no | any non-empty stack | Per-stack predicate — the full item-condition vocabulary (see [ACTIONS.md](ACTIONS.md#item-conditions)) |
| `comparison` | string | no | `">"` | Comparison operator against the count |
| `compare_to` | int | no | `0` | Count threshold |

Apoli's `power` field (counting slots inside an `apoli:inventory` power's virtual container) is **not supported** — a condition using it fails closed with a load warning.

**Example — carrying at least 16 bones in total:**
```json
{
  "type": "neoorigins:inventory",
  "process_mode": "items",
  "item_condition": { "type": "neoorigins:ingredient", "item": "minecraft:bone" },
  "comparison": ">=",
  "compare_to": 16
}
```

## `neoorigins:enchantment`

Checks the max level of a given enchantment across all equipment slots.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `enchantment` | resource location | no | — | Enchantment ID; always-true when absent |
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | int | no | `1` | Level threshold |

## `neoorigins:resource`

Numeric comparison against a named resource power's stored value.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `resource` | resource location | yes | — | Power ID storing the resource |
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | int | no | `0` | Threshold |

> ⚠️ `resource` must be the **full** namespaced power ID (e.g. `mypack:thorns/resource`). Unlike `power_active`, the `*:` / `*:*` self-reference wildcard is **not** resolved here — a reference containing `*` silently reads as `0` (and is warned about at load). The same applies to the `change_resource` / `set_resource` actions.

## `neoorigins:resource_level`

Synonym of [`neoorigins:resource`](#neooriginsresource) — same factory and fields (`resource`, `comparison`, `compare_to`), accepted so Apoli-format packs using `resource_level` dispatch directly. The same full-power-id requirement (no `*` wildcard) applies.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `resource` | resource location | yes | — | Power ID storing the resource |
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | int | no | `0` | Threshold |

**Example:**
```json
{ "type": "neoorigins:resource_level", "resource": "mypack:mana", "comparison": ">=", "compare_to": 20 }
```

## `neoorigins:power`

True when the entity has the given power granted — either through any of its chosen origins' power lists or as a dynamic grant. Distinct from `power_active` (which tests whether a toggle power is currently on) and `power_type` (which matches by power *type* ID rather than power ID).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` | resource location | yes | — | Power ID to look for; always-false when absent |

**Example:**
```json
{ "type": "neoorigins:power", "power": "mypack:night_vision" }
```

## `neoorigins:power_active`

Whether a named toggle power is currently active (toggled on) on this entity. Works with:

- **`neoorigins:toggle`** powers — resolves the `default` field if the toggle has never been flipped.
- **Native toggle powers** — `wraith_phase`, `flight`, `phantom_form`, and any other power that extends the internal toggle system (skill-key toggled powers).

Use this to gate other powers so they only tick while a specific toggle is on. For example, apply debuffs only while phasing is active, or drain a resource only while flight is toggled on.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` | resource location | yes | — | Power ID to check (e.g. `"mypack:wraith_phase"`, `"mypack:my_toggle"`) |

```json
{
  "type": "neoorigins:condition_passive",
  "name": "Phasing Hunger",
  "description": "Phasing drains your hunger.",
  "hidden": true,
  "interval": 20,
  "condition": { "type": "neoorigins:power_active", "power": "mypack:wraith_phase" },
  "entity_action": { "type": "neoorigins:exhaust", "amount": 1.0 }
}
```

Combine with `neoorigins:and` to add extra gates:

```json
"condition": {
  "type": "neoorigins:and",
  "conditions": [
    { "type": "neoorigins:power_active", "power": "mypack:wraith_phase" },
    { "type": "neoorigins:near_block", "tag": "mypack:soul-repellent", "radius": 6 }
  ]
}
```

## `neoorigins:power_type`

Whether any power granted to this entity has a matching `type` ID.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power_type` | resource location | yes | — | Power type ID (e.g. `neoorigins:toggle`) |
| `id` | resource location | no | — | Alias for `power_type` |

Bare type names (no `:`) are auto-prefixed with `neoorigins:`.

## `neoorigins:origin`

True when the player currently has the named origin, optionally scoped to a single layer. Mirrors Apoli's `origins:origin` entity condition — use it to gate a power or condition on which origin the player is.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `origin` | resource location | yes | — | Origin id the player must currently have (e.g. `neoorigins:merling`). |
| `layer` | resource location | no | any layer | Restrict the match to a single origin layer; omit to match the origin in any layer. |

**Example — only while playing the Merling origin:**
```json
{ "type": "neoorigins:origin", "origin": "neoorigins:merling" }
```

## `neoorigins:cooldown`

**Inverted polarity:** true when the named power is **not** on cooldown (i.e. ready to use). Use `"inverted": true` to test "is on cooldown" instead. Always-true when `power` is absent.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` | resource location | no | — | Power ID whose cooldown to test; always-true when absent |

**Example:**
```json
{ "type": "neoorigins:cooldown", "power": "mypack:fireball" }
```

## `neoorigins:nbt`

Simplified NBT presence check: true when the entity's persistent data contains a given top-level key.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `nbt` | string | no | — | Compound key name; always-true when absent |

## `neoorigins:scoreboard`

Numeric comparison against the player's score on a named objective.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `objective` | string | yes | — | Scoreboard objective name; always-false when absent |
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | int | no | `0` | Score threshold |

## `neoorigins:command`

Runs an arbitrary server command with suppressed output and returns true if no exception was thrown.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `command` | string | yes | — | Command text (no leading slash); always-false when blank |

**Unusual:** this does *not* test exit code. It returns true unless the command threw — check the feasibility of any JSON-condition written this way carefully.

## `neoorigins:advancement`

True when the player has completed the given advancement (server-side only; unknown advancement IDs return false).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `advancement` | resource location | yes | — | Advancement ID the player must have completed; always-false when absent |

**Example:**
```json
{ "type": "neoorigins:advancement", "advancement": "minecraft:story/enter_the_nether" }
```

## `neoorigins:predicate`

Meta-wrapper around vanilla Minecraft predicates. **Unusual:** dispatches on `predicate_type`, and the inner `predicate` object is codec-parsed once at load.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `predicate_type` | string | yes | — | One of `biome`, `block_state`, `entity_properties`, `fluid_state`, `item`, `location`, `damage` |
| `predicate` | object | yes | — | Inner JSON in that predicate's vanilla codec |

Supported `predicate_type` values and the check they perform:
- `biome` — biome at the entity's block position; inner shape `{ "biomes": ["..."], "tag": "..." }` (at least one required)
- `block_state` — vanilla `BlockPredicate` at the entity's block position
- `entity_properties` — vanilla `EntityPredicate` against this entity
- `fluid_state` — vanilla `FluidPredicate` at the entity's block position
- `item` — vanilla `ItemPredicate` against the entity's mainhand
- `location` — vanilla `LocationPredicate` at the entity's position
- `damage` — **fails closed** (requires damage-source context; use action-on-hit hooks instead)

## `neoorigins:config_flag`

True when the named server config flag is currently enabled. Pack authors use this to gate powers on server-operator toggleable settings (e.g. letting admins disable diet restrictions or dry-out mechanics globally).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `key` | string | no | `""` | Config flag key. Unknown keys log a warning and default to `true` (fail-open). |

**Supported keys:**
- `"ocean_origins.fish_diet_required"` — whether ocean origins enforce fish-only diet
- `"ocean_origins.dries_out"` — whether ocean origins take dry-out damage on land

**Example — only enforce fish diet when the server has it enabled:**
```json
{ "type": "neoorigins:config_flag", "key": "ocean_origins.fish_diet_required" }
```

---

# Context-aware conditions

These read from `ActionContextHolder` populated during an action dispatch. They fail closed outside a matching context.

## `neoorigins:hit_taken_amount`

Numeric comparison against the hit damage currently being taken. Requires an active `HitTakenContext`. Used by `action_on_hit_taken` to re-express `min_damage`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | number | no | `0.0` | Damage threshold |

## `neoorigins:hit_dealt_amount`

Numeric comparison against the most recent damage the player **dealt** to a target. Requires an active `HitDealtContext` — the attacker-side mirror of `hit_taken_amount`, populated when the player damages a living entity (player-vs-mob included). Hook it from an `action_on_event` power with `"event": "hit_dealt"`. Inside that dispatch a bientity `target_action` resolves the victim that was struck.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | number | no | `0.0` | Dealt-damage threshold |

## `neoorigins:food_item_in_tag`

True if the item being eaten in the current `FOOD_EATEN` dispatch matches a tag or item ID. Requires an active `FoodContext`. Used by `food_restriction` aliases.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `tag` | string | no | — | Item tag (prefix with `#`, e.g. `"#minecraft:meat"`) or bare item ID (e.g. `"minecraft:spider_eye"`). Always-false when absent. |

**Tag vs item:** a leading `#` denotes a tag lookup; without `#` the value is matched as an exact item ID.

### Built-in diet tags

NeoOrigins ships these item tags for its diet-restricted origins, under `data/neoorigins/tags/item/`. All are declared `"replace": false`, so a datapack can append to any of them without redefining the list. Cross-mod entries are marked `required: false`, so they no-op when the other mod is absent.

| Tag | Diet / origin | Contents |
|---|---|---|
| `#neoorigins:fish_foods` | Pescivore (ocean/aquatic origins) | cod, salmon, tropical fish, pufferfish, cooked cod, cooked salmon; plus optional `#c:foods/raw_fish`, `#c:foods/cooked_fish`, and Aquaculture fish fillet + sushi |
| `#neoorigins:meat_foods` | Carnivore | beef, porkchop, mutton, chicken, rabbit (raw + cooked each), rabbit stew, rotten flesh; plus optional `#c:foods/raw_meat`, `#c:foods/cooked_meat`, and Aquaculture turtle soup |
| `#neoorigins:vampire_foods` | Blood diet (Vampire) | beef, porkchop, chicken, mutton, rabbit, rotten flesh, spider eye |
| `#neoorigins:skeleton_foods` | Boneless diet (Skeleton, base) | bone meal, rotten flesh, spider eye |
| `#neoorigins:skeleton_evolved_foods` | Boneless diet (Skeleton, evolved) | skeleton base list + all cooked meats and cooked fish (beef, porkchop, chicken, mutton, rabbit, cod, salmon) |

The fish diet additionally reads a server config allowlist: the `neoorigins:aquatic_fish_diet` power accepts either the `#neoorigins:fish_foods` tag **or** any entry in `ocean_origins.extra_fish_foods` (see `food_item_in_config_list` below), and only enforces the restriction when the `ocean_origins.fish_diet_required` flag is on.

## `neoorigins:food_item_id`

True if the item being eaten in the current `FOOD_EATEN` dispatch is an exact item-ID match. Requires an active `FoodContext`; returns `false` outside that context. Sibling of `food_item_in_tag` — use this when you need per-item bonuses (e.g. raw cod gets one bonus, cooked cod gets a different one).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `id` | resource location | yes | — | Exact item ID to match (e.g. `"minecraft:cod"`). Unknown IDs fail closed. |

**Example — bonus saturation when eating raw cod:**
```json
{
  "condition": { "type": "neoorigins:food_item_id", "id": "minecraft:cod" },
  "action": { "type": "neoorigins:modify_food", "food": 3, "saturation": 5.6 }
}
```

**`food_item_id` vs `food_item_in_tag`:** `food_item_id` matches a single exact item. `food_item_in_tag` matches by tag (or bare ID), so it can cover many items in one rule. Use `food_item_id` when you need different behavior per individual food item.

## `neoorigins:food_item_in_config_list`

True if the item being eaten in the current `FOOD_EATEN` dispatch matches any entry in a named server config list. Requires an active `FoodContext`; returns `false` outside that context, and `false` for an unknown key. Each list entry is either a bare item ID or a `#`-prefixed tag ref, matched the same way as `food_item_in_tag`.

This lets a server owner extend a diet without editing a datapack: the ocean-origin fish diet reads `ocean_origins.extra_fish_foods` so modded fish (Aquaculture, Hybrid Aquatic, etc.) can be whitelisted from config.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `key` | string | yes | — | Config list key to read (e.g. `"ocean_origins.extra_fish_foods"`). Unknown keys evaluate false. |

**Supported keys:**
- `"ocean_origins.extra_fish_foods"` — extra items/tags ocean origins may eat, additive to the `neoorigins:fish_foods` tag.

**Example — allow the eat when it is a fish tag OR a config-listed item:**
```json
{
  "type": "neoorigins:any_of",
  "conditions": [
    { "type": "neoorigins:food_item_in_tag", "tag": "#neoorigins:fish_foods" },
    { "type": "neoorigins:food_item_in_config_list", "key": "ocean_origins.extra_fish_foods" }
  ]
}
```

---

# Bientity conditions

These evaluate against a pair — the entity under test (actor) plus a target pulled from the active dispatch context. They fail closed outside a bientity context.

**Target extraction rules** (from `ActionContextHolder.get()`):
- `HitTakenContext` — target = the damage source entity (if it's a `LivingEntity`)
- `KillContext` — target = the killed entity
- `EntityInteractContext` — target = the interacted entity
- `ProjectileHitContext` — target = the entity hit (if any)
- otherwise — null, condition returns false

## `neoorigins:distance`

Numeric comparison against 3D distance from actor to target. **Default comparison is `"<="`** (unlike most numeric conditions).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `"<="` | Comparison operator |
| `compare_to` | number | no | `0.0` | Distance in blocks |

## `neoorigins:can_see`

True when the actor has line-of-sight to the target. No fields.

## `neoorigins:equal`

True when target UUID == actor UUID. No fields.

## `neoorigins:target_type`

Checks target's entity-type ID or tag. **Unusual:** a leading `#` denotes a tag.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_type` | string | yes | — | e.g. `"minecraft:zombie"` or `"#minecraft:undead"`; always-false when blank |

## `neoorigins:target_group`

Checks whether the target's entity type is in a vanilla mob-category tag under the `minecraft:` namespace.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `group` | string | yes | — | Tag path under `minecraft:` (e.g. `"raiders"`); always-false when blank |

## `neoorigins:in_set` (alias `neoorigins:in_set`)

2.0 entity-set membership: true when the target's UUID is in the actor's named entity-set. Pack authors should namespace `set` keys to avoid collision.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `set` | string | yes | — | Entity-set key (e.g. `"mypack:kill_streak"`) |

## `neoorigins:no_minions_alive`

True when the player has zero living tracked minions of the given key.
Used for "loner" gates — e.g., the Monster Tamer's Lone Weakness applies
only when no tamed mobs are alive. Evaluates to true if the player has
never summoned a minion as well (count of 0).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `key` | string | no | `"tamer:tamed"` | MinionTracker key to count. Defaults to the `TameMobPower` key. Summoned-mob keys are the entity type string, e.g. `"minecraft:skeleton"` for `SummonMinionPower`. |

---

# Damage conditions

Read the `DamageSource` from `HitTakenContext`. Fail closed outside that context.

## `neoorigins:from_fire`

True when the damage source is in the `minecraft:is_fire` damage-type tag. No fields.

## `neoorigins:from_projectile`

True when the damage source is in the `minecraft:is_projectile` damage-type tag. No fields.

## `neoorigins:from_explosion`

True when the damage source is in the `minecraft:is_explosion` damage-type tag. No fields.

## `neoorigins:damage_type`

Matches the damage source against a specific damage-type resource key.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `damage_type` | resource location | yes | — | Damage-type ID; always-false when blank |

## `neoorigins:damage_tag`

Matches the damage source against a damage-type tag. Leading `#` is stripped if present.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `tag` | resource location | yes | — | Damage-type tag (optional `#` prefix); always-false when blank |

## `neoorigins:damage_name` (alias `neoorigins:name`)

Case-insensitive match against the damage source's message ID (e.g. `"lava"`, `"cactus"`).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `name` | string | yes | — | Damage message ID; always-false when blank |

## `neoorigins:night`

True while the level's in-game time is past 13000 ticks (nightfall) and before the day reset. Logical inverse of `neoorigins:daytime`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| (no fields) | — | — | — | Marker condition. |

## `neoorigins:thundering`

True when there's an active thunderstorm **and** rain is falling at the player's position (biome supports rain). Stricter than vanilla's global `isThundering()` — won't fire in dry biomes even during a global thunderstorm.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| (no fields) | — | — | — | Marker condition. |

## `neoorigins:has_effect`

True when the player has the specified MobEffect active. Useful for gating passives on consumable-applied buffs (mirrors the `FortuneWhenEffectPower` gate pattern for DSL authors).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effect` | resource location | yes | — | MobEffect ID (e.g. `minecraft:luck`, `minecraft:haste`) |

## `neoorigins:status_effect`

True when the player has the specified MobEffect active within an amplifier range. Apoli-parity sibling of `has_effect`: `has_effect` is presence-only, while `status_effect` can additionally gate on the effect's amplifier.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effect` | resource location | yes | — | MobEffect ID; always-false when absent |
| `amplifier` | int | no | — | Exact amplifier to require (sets both min and max; overrides the fields below) |
| `min_amplifier` | int | no | `-1` | Lowest acceptable amplifier (`-1` = any) |
| `max_amplifier` | int | no | unbounded | Highest acceptable amplifier |

**Example — Strength II or higher:**
```json
{ "type": "neoorigins:status_effect", "effect": "minecraft:strength", "min_amplifier": 1 }
```

## `neoorigins:climbing`

True when the player is currently on a climbable block (vanilla ladder, vine, or any block with the wall-climb capability from origins like Arachnid).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| (no fields) | — | — | — | Marker condition. |

## `neoorigins:climbing_gate`

Internal state machine that drives conditioned `origins:climbing` powers. It is emitted by the compat translator when a climbing power carries a `condition` / `hold_condition`, so it is not normally hand-authored — documented for completeness and for authors debugging converted Apoli packs. It tracks per-player climb state: climbing starts while `condition` holds and, when `allow_holding`, keeps going while the player is airborne until `hold_condition` fails or they touch the ground.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `condition` | entity condition | no | always active | Condition that activates climbing; absent → always active. |
| `hold_condition` | entity condition | no | hold until touchdown | Condition that keeps an active climb going after `condition` stops (evaluated while airborne); absent → hold until the player lands. |
| `allow_holding` | boolean | no | `true` | Whether an active climb may persist after `condition` stops. |

## `neoorigins:out_of_combat`

True when at least `ticks` ticks have elapsed since the player last took damage. Backed by `CombatTracker` which timestamps damage hits in `CombatPowerEvents.onLivingDamage` (including cancelled damage — getting hit by invulnerable armor still counts as being attacked). Forgotten on logout.

Useful for gating rest / regen / out-of-combat-only buffs.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `ticks` | int | no | `100` | Ticks since last damage hit. 100 = 5 seconds. |

**Example — regen only while out of combat:**
```json
{ "type": "neoorigins:out_of_combat", "ticks": 100 }
```

Typically combined with `near_block` or `biome` in an `neoorigins:and` so the buff only applies when both safe *and* in the right spot (campfire, village, bed area, etc.).

## `neoorigins:cover` (alias `neoorigins:covered_by_block`)

True when the column directly above the player contains a non-air block within `distance` blocks — the "standing under cover" check (inverse of seeing the sky overhead, scoped to a single column). Apoli-parity pair; MoR Wood Elf uses `medievalorigins:cover`, which dispatches here via the namespace fallback.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `distance` | int | no | `8` | How many blocks above the player to scan (minimum 1) |

## `neoorigins:near_block`

**Aliases:** `origins:block_in_radius`, `apace:block_in_radius`

True when any matching block is within a cubic radius of the player. Accepts any combination of single IDs, ID lists, single tags, and tag lists — a block matches if it appears in ANY of the provided blocks/tags (logical OR).

Intended for ambient proximity buffs (campfire warmth, lava-side speed, water-near regen). Capped at radius 8 to avoid expensive per-tick scans.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `block` | resource location | no | — | Single block ID to match |
| `blocks` | list of resource location | no | `[]` | Additional block IDs to match |
| `tag` | block tag | no | — | Single block tag (with or without leading `#`) |
| `tags` | list of block tag | no | `[]` | Additional block tags |
| `block_condition` | object | no | — | Origins-format nested block condition (`in_tag` or `block` type) |
| `radius` | int (1–8) | no | `4` | Cubic radius to scan around the player |

At least one of `block`/`blocks`/`tag`/`tags`/`block_condition` must be non-empty.

**Example — warm near any fire source:**
```json
{ "type": "neoorigins:near_block",
  "tags": ["minecraft:campfires", "#c:fire"],
  "radius": 5 }
```

## `neoorigins:block_in_radius`

Synonym of [`neoorigins:near_block`](#neooriginsnear_block) — same factory and fields, accepted so Origins-format packs using `block_in_radius` dispatch directly. Also accepts the Origins `block_condition` (nested `in_tag`/`block`) shape.

**Example:**
```json
{ "type": "neoorigins:block_in_radius", "block": "minecraft:campfire", "radius": 5 }
```

## `neoorigins:near_entity`

True when at least one entity of the given type (or entity tag) is within `distance` blocks of the player. Uses an AABB broad scan followed by Euclidean (spherical) distance filtering for accuracy. Distance capped at 64 blocks to avoid expensive per-tick scans.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_type` | resource location or `#tag` | yes | — | Entity type id (e.g. `"minecraft:creeper"`) or entity tag with `#` prefix (e.g. `"#minecraft:undead"`). |
| `distance` | double (1–64) | no | `8.0` | Radius in blocks. |

**Example — buff when near wolves:**
```json
{ "type": "neoorigins:near_entity",
  "entity_type": "minecraft:wolf",
  "distance": 10 }
```

**Example — debuff when undead are nearby:**
```json
{ "type": "neoorigins:near_entity",
  "entity_type": "#minecraft:undead",
  "distance": 16 }
```
