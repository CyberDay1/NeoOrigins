# NeoOrigins 2.0 Condition Reference

Conditions evaluate to true/false against an entity (usually the power's owning player). They gate power activation, `action_on_event` triggers, `conditional` wrappers, and bientity interactions.

**Canonical namespace:** `neoorigins:*` is the preferred form for new packs. Legacy `neoorigins:*` and `apace:*` prefixes still work but log a one-shot `[2.0-legacy]` deprecation warning. Bare type names (e.g. `"type": "and"`) are auto-prefixed with `neoorigins:`. Section headers below still show the traditional `neoorigins:*` names for familiarity with upstream documentation; the JSON examples use the canonical `neoorigins:*` form.

**Fail-closed semantics:** a malformed or unsupported condition logs a warning and returns `false` rather than throwing. Bientity / damage / food conditions that require a dispatch context also return `false` when evaluated outside that context.

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

---

# Meta conditions

## `neoorigins:and`

Logical AND of nested conditions.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `conditions` | array of condition objects | yes | `[]` | Inner conditions; all must pass |

**Example:**
```json
{ "type": "neoorigins:and", "conditions": [ {"type": "neoorigins:sneaking"}, {"type": "neoorigins:on_ground"} ] }
```

## `neoorigins:or`

Logical OR of nested conditions.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `conditions` | array of condition objects | yes | `[]` | Inner conditions; at least one must pass |

## `neoorigins:not`

Negates a single inner condition.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `condition` | condition object | yes | — | Condition to negate |

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

True when rain is falling at the entity's block position (server-side only).

## `neoorigins:daytime`

True for in-game times 0–12999. No fields.

## `neoorigins:exposed_to_sky`

True when the sky is visible from the entity's block position (server-side only).

## `neoorigins:exposed_to_sun`

True between time 6001–11999 with sky access and no rain.

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

## `neoorigins:block_collision`

Always true. Placeholder/stub for parity with Apoli. No fields.

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

Numeric comparison against `experienceLevel`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | int | no | `0` | Level threshold |

## `neoorigins:xp_points`

Numeric comparison against `totalExperience`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | int | no | `0` | Points threshold |

## `neoorigins:dimension`

Checks current dimension ID.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `dimension` | resource location | no | — | e.g. `"minecraft:overworld"`; always-true when absent |

## `neoorigins:biome`

Checks biome at the entity's block position.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `biome` | resource location | no | — | Exact biome ID |
| `tag` | resource location | no | — | Biome tag (used when `biome` absent) |

Always-true when neither field is present.

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

## `neoorigins:light_level` (alias `neoorigins:brightness`)

Numeric comparison against ambient light at the entity's block position.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `light_type` | string | no | `""` | `"sky"`, `"block"`, or blank for combined max |
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | int | no | `0` | Light threshold |

## `neoorigins:time_of_day`

Numeric comparison against `level.getDayTime() % 24000`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | long | no | `0` | Tick within day |

## `neoorigins:weather`

Checks the current weather state. **Unusual:** accepts either `"state"` or `"value"`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `state` | string | no | — | `"clear"`, `"rain"`/`"raining"`, or `"thunder"`/`"thundering"` |
| `value` | string | no | `"clear"` | Fallback if `state` absent |

## `neoorigins:moon_phase`

Numeric comparison against the moon phase index 0–7. **Default comparison is `"=="`**.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `"=="` | Comparison operator |
| `compare_to` | int | no | `0` | Phase index |

## `neoorigins:on_block`

True when the entity is on ground and the block directly below matches an ID.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `block_condition` | object | yes | — | Nested block condition |
| `block_condition.id` | resource location | yes | — | Block ID to match |

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
| `equipment_slot` | string | no | `"mainhand"` | `"head"`, `"chest"`, `"legs"`, `"feet"`, `"offhand"`, or `"mainhand"` |
| `item_condition` | object | no | — | Nested condition (see below) |

**Unusual:** `item_condition` has its own internal shape. Accepts any of:
- `{ "id": "minecraft:stick" }` — exact item ID
- `{ "tag": "c:tools/pickaxe" }` — item tag
- `{ "type": "neoorigins:empty" }` — slot is empty (also `apace:empty`)
- `{ "ingredient": { "item": "..." } }` or `{ "ingredient": { "tag": "..." } }` — ingredient-style wrapper

Always-true when `item_condition` is absent.

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

## `neoorigins:power_active`

Whether a named toggle power is currently active on this entity. Resolves the registered [`neoorigins:toggle`](POWER_TYPES.md#neooriginstoggle) power's `default` field if the toggle has never been flipped on this player.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` | resource location | yes | — | Power ID |

See [COOKBOOK.md → Toggleable abilities (no keybind slot)](COOKBOOK.md#toggleable-abilities-no-keybind-slot) for full recipes.

## `neoorigins:power_type`

Whether any power granted to this entity has a matching `type` ID.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power_type` | resource location | yes | — | Power type ID (e.g. `neoorigins:toggle`) |
| `id` | resource location | no | — | Alias for `power_type` |

Bare type names (no `:`) are auto-prefixed with `neoorigins:`.

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

---

# Context-aware conditions

These read from `ActionContextHolder` populated during an action dispatch. They fail closed outside a matching context.

## `neoorigins:hit_taken_amount`

Numeric comparison against the hit damage currently being taken. Requires an active `HitTakenContext`. Used by `action_on_hit_taken` to re-express `min_damage`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | number | no | `0.0` | Damage threshold |

## `neoorigins:food_item_in_tag`

True if the item being eaten in the current `FOOD_EATEN` dispatch matches a tag or item ID. Requires an active `FoodContext`. Used by `food_restriction` aliases.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `tag` | string | no | — | Item tag (prefix with `#`, e.g. `"#minecraft:meat"`) or bare item ID (e.g. `"minecraft:spider_eye"`). Always-false when absent. |

**Tag vs item:** a leading `#` denotes a tag lookup; without `#` the value is matched as an exact item ID.

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

## `neoorigins:climbing`

True when the player is currently on a climbable block (vanilla ladder, vine, or any block with the wall-climb capability from origins like Arachnid).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| (no fields) | — | — | — | Marker condition. |

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

## `neoorigins:near_block`

True when any matching block is within a cubic radius of the player. Accepts any combination of single IDs, ID lists, single tags, and tag lists — a block matches if it appears in ANY of the provided blocks/tags (logical OR).

Intended for ambient proximity buffs (campfire warmth, lava-side speed, water-near regen). Capped at radius 8 to avoid expensive per-tick scans.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `block` | resource location | no | — | Single block ID to match |
| `blocks` | list of resource location | no | `[]` | Additional block IDs to match |
| `tag` | block tag | no | — | Single block tag (with or without leading `#`) |
| `tags` | list of block tag | no | `[]` | Additional block tags |
| `radius` | int (1–8) | no | `4` | Cubic radius to scan around the player |

At least one of `block`/`blocks`/`tag`/`tags` must be non-empty.

**Example — warm near any fire source:**
```json
{ "type": "neoorigins:near_block",
  "tags": ["minecraft:campfires", "#c:fire"],
  "radius": 5 }
```
