---
title: "Retired ids"
parent: "Power Types"
grand_parent: "DSL Reference"
nav_order: 13
permalink: /powers/retired
---

# Retired ids

Ids that are no longer types of their own. Each still loads by remapping onto a live type, with a one-time `[2.0-legacy]` warning; use the replacement in new packs. The full remap table is in the [Migration Guide](../MIGRATION.md).

Fields every power accepts (`name`, `hidden`, `power_condition`, ...) are on [Power basics](../concepts/power-basics.md). See also: [Migration Guide](../MIGRATION.md).

Types on this page: [`action_on_hit_taken`](#neooriginsaction_on_hit_taken) · [`action_on_kill`](#neooriginsaction_on_kill) · [`action_over_time`](#neooriginsaction_over_time) · [`active_aoe_effect`](#neooriginsactive_aoe_effect) · [`active_launch`](#neooriginsactive_launch) · [`better_bone_meal`](#neooriginsbetter_bone_meal) · [`better_crafted_food`](#neooriginsbetter_crafted_food) · [`better_enchanting`](#neooriginsbetter_enchanting) · [`biome_buff`](#neooriginsbiome_buff) · [`burn_at_health_threshold`](#neooriginsburn_at_health_threshold) · [`damage_in_biome`](#neooriginsdamage_in_biome) · [`damage_in_daylight`](#neooriginsdamage_in_daylight) · [`damage_in_water`](#neooriginsdamage_in_water) · [`efficient_repairs`](#neooriginsefficient_repairs) · [`food_restriction`](#neooriginsfood_restriction) · [`glow`](#neooriginsglow) · [`healing_mist`](#neooriginshealing_mist) · [`hunger_drain_modifier`](#neooriginshunger_drain_modifier) · [`knockback_modifier`](#neooriginsknockback_modifier) · [`longer_potions`](#neooriginslonger_potions) · [`more_animal_loot`](#neooriginsmore_animal_loot) · [`natural_regen_modifier`](#neooriginsnatural_regen_modifier) · [`night_vision`](#neooriginsnight_vision) · [`regen_in_fluid`](#neooriginsregen_in_fluid) · [`repulse`](#neooriginsrepulse) · [`stacking_status_effects`](#neooriginsstacking_status_effects) · [`status_effect`](#neooriginsstatus_effect) · [`teleport_range_modifier`](#neooriginsteleport_range_modifier) · [`thorns_aura`](#neooriginsthorns_aura).

---

## `neoorigins:action_on_hit_taken`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](../MIGRATION.md).

Triggers an action each time the player takes damage.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `action` | string | no | `teleport` | Action to perform: `teleport` (random teleport, 16 blocks horizontal / 8 vertical), `ignite_attacker`, or `effect_on_attacker`. Any other value falls back to `teleport`. |
| `min_damage` | float | no | `0.0` | Only fires when incoming damage ≥ this |
| `chance` | float | no | `1.0` | Probability to fire, from 0.0 to 1.0 |
| `effect` | Identifier | no | — | Effect to apply to the attacker. Read only by `effect_on_attacker`. |
| `amplifier` | int | no | `0` | Effect level. Read only by `effect_on_attacker`. |
| `duration` | int | no | `100` | Effect duration in ticks for `effect_on_attacker`, or burn time for `ignite_attacker` — where it defaults to `60` instead. |

**Example: poison whoever hits you**
```json
{
  "type": "neoorigins:action_on_hit_taken",
  "action": "effect_on_attacker",
  "effect": "minecraft:poison",
  "amplifier": 1,
  "duration": 60,
  "name": "Toxic Blood",
  "description": "Poisons attackers who draw blood."
}
```

This type can only act on the attacker. To buff *yourself* when hit, use [`neoorigins:action_on_event`](core.md#neooriginsaction_on_event) with `"event": "hit_taken"` and an `entity_action`, which runs against the player.

---

## `neoorigins:action_on_kill`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](../MIGRATION.md).

Triggers an action each time the player kills a living entity.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `action` | string | no | `restore_health` | Action to perform: `restore_health`, `restore_hunger`, `grant_effect` |
| `amount` | float | no | `4.0` | Health or hunger to restore |
| `effect` | Identifier | no | — | Effect to grant (when `action` is `grant_effect`) |
| `amplifier` | int | no | `0` | Effect level |
| `duration` | int | no | `200` | Effect duration in ticks |

**Example: restore 1 heart on kill**
```json
{
  "type": "neoorigins:action_on_kill",
  "action": "restore_health",
  "amount": 2.0,
  "name": "Vampiric",
  "description": "Restores health by killing enemies."
}
```

---

## `neoorigins:action_over_time`

A straight alias for [`neoorigins:condition_passive`](core.md#neooriginscondition_passive) — same fields, same behaviour, remapped before any field is read. It exists because "run this action every N ticks" is the use the type is most often reached for, and `condition` is optional, so the name reads better when there is no gate at all. Prefer `condition_passive` in new packs; use this where an existing pack already spells it this way.

`neoorigins:tick_action` is **not** the type to reach for here: it ships no behaviour and dispatches nothing.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `interval` | int | no | `20` | Tick interval between runs, 20 = 1 second (clamped to ≥ 1) |
| `condition` | EntityCondition | no | always-true | DSL condition tested each interval |
| `entity_action` | EntityAction | no | noop | Action run against the player when the condition is true |
| `else_action` | EntityAction | no | noop | Action run when the condition is false |
| `toggleable` | bool | no | `false` | When true, binds a skill keybind that flips the periodic action on/off. While off the interval action never runs |
| `default_off` | bool | no | `false` | Toggleable only: when true the power starts disabled so the player opts in via the keybind |

Also takes the shared [toggle upkeep](../concepts/costs-and-upkeep.md#toggle-upkeep) fields (`hunger_cost`, `resource_cost`, `resource_cost_amount`, `upkeep_interval`, `upkeep_condition`).

Worked examples (periodic feed, periodic heal) are under [Periodic feed / heal](../COOKBOOK.md#periodic-feed--heal-via-neooriginsaction_over_time).

---

## `neoorigins:active_aoe_effect`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:active_ability`. See [MIGRATION.md](../MIGRATION.md).

Active ability that applies a mob effect to all living entities within a radius of the player.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effect` | Identifier | no | `minecraft:weakness` | Effect applied to everyone in range, e.g. `minecraft:slowness` |
| `amplifier` | int | no | `0` | Effect level minus one (`0` = level I) |
| `duration` | int | no | `200` | Duration of the applied effect in ticks (20 = 1s) |
| `duration_ticks` | int | no | — | Synonym for `duration`, accepted because packs mix the two spellings. Wins when both are set |
| `radius` | float | no | `8.0` | Range in blocks |
| `include_source` | bool | no | `false` | When true the caster is affected too. Leaving it false is what stops an offensive burst (instant damage, wither) from killing its own caster |
| `cooldown_ticks` | int | no | `60` | Cooldown in ticks. Ignored when `cooldown_resource` is set |
| `cooldown_resource` | string | no | `""` | Variable/resource power id whose live value (in ticks) becomes the cooldown length on each activation, overriding `cooldown_ticks`. Empty = fixed cooldown |
| `hunger_cost` | int | no | `0` | Food/exhaustion points consumed per successful activation |
| `resource_cost` | string | no | `""` | Resource power id whose value is spent on activation. Empty = no resource cost |
| `resource_cost_amount` | int | no | `0` | Amount of the `resource_cost` resource consumed per activation |
| `condition` | condition | no | always true | EntityCondition gating the activation. A blocked attempt spends no cooldown |
| `fail_action` | action | no | none | EntityAction run when an activation attempt is blocked by `condition`. Not fired on cooldown or cost aborts |
| `key` | int / string / object | no | none | Hotkey this active binds to instead of the next free skill slot. Same shape as [`active_ability`'s `key`](active.md#neooriginsactive_ability); see [Named keybinds](../concepts/keybinds.md) |

Also accepts the three shared cooldown HUD fields (`cooldown_icon`, `cooldown_countdown`, `always_show_icon`) described at the top of this file.

**Example: root all nearby mobs**
```json
{
  "type": "neoorigins:active_aoe_effect",
  "effect": "minecraft:slowness",
  "amplifier": 5,
  "duration_ticks": 80,
  "radius": 6.0,
  "cooldown_ticks": 200,
  "name": "Entangle",
  "description": "Roots all nearby creatures in place."
}
```

---

## `neoorigins:active_launch`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:active_ability`. See [MIGRATION.md](../MIGRATION.md).

Active ability that launches the player straight upward. Useful paired with `elytra_boost` or `flight` for vertical take-off.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` | float | no | `1.5` | Upward launch velocity |
| `cooldown_ticks` | int | no | `60` | Cooldown in ticks. Ignored when `cooldown_resource` is set |
| `cooldown_resource` | string | no | `""` | Variable/resource power id whose live value (in ticks) becomes the cooldown length on each activation, overriding `cooldown_ticks`. Empty = fixed cooldown |
| `hunger_cost` | int | no | `0` | Food/exhaustion points consumed per successful activation |
| `resource_cost` | string | no | `""` | Resource power id whose value is spent on activation. Empty = no resource cost |
| `resource_cost_amount` | int | no | `0` | Amount of the `resource_cost` resource consumed per activation |
| `condition` | condition | no | always true | EntityCondition gating the activation. A blocked attempt spends no cooldown |
| `fail_action` | action | no | none | EntityAction run when an activation attempt is blocked by `condition` — e.g. an `execute_command` tellraw explaining why. Not fired on cooldown or cost aborts |
| `key` | int / string / object | no | none | Hotkey this active binds to instead of the next free skill slot. Same shape as [`active_ability`'s `key`](active.md#neooriginsactive_ability); see [Named keybinds](../concepts/keybinds.md) |

Also accepts the three shared cooldown HUD fields (`cooldown_icon`, `cooldown_countdown`, `always_show_icon`) described at the top of this file.

**Example:**
```json
{
  "type": "neoorigins:active_launch",
  "power": 2.2,
  "cooldown_ticks": 80,
  "name": "Pounce Launch",
  "description": "Launches upward with powerful legs."
}
```

---

## `neoorigins:better_bone_meal`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](../MIGRATION.md).

Grants extra bone-meal growth applications per use (`mod_bonemeal_extra` event). Additive (`add_base`).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `extra_applications` | int | no | `1` | Additional bone-meal growth applications per use |

**Example:**
```json
{
  "type": "neoorigins:better_bone_meal",
  "extra_applications": 2,
  "name": "Green Thumb",
  "description": "Bone meal goes further."
}
```

---

## `neoorigins:better_crafted_food`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](../MIGRATION.md).

Adds a flat saturation bonus to food the player crafts (`mod_crafted_food_saturation` event). Additive (`add_base`).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `saturation_bonus` | float | no | `0.5` | Extra saturation added to crafted food |

**Example:**
```json
{
  "type": "neoorigins:better_crafted_food",
  "saturation_bonus": 1.0,
  "name": "Home Cooking",
  "description": "Food you make is more filling."
}
```

---

## `neoorigins:better_enchanting`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](../MIGRATION.md).

Adds a flat bonus to the enchantment power level available at the enchanting table (`mod_enchant_level` event). The bonus is additive (`add_base`), not a multiplier.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `bonus_levels` | int | no | `5` | Bonus enchanting levels added |

**Example:**
```json
{
  "type": "neoorigins:better_enchanting",
  "bonus_levels": 5,
  "name": "Arcane Affinity",
  "description": "Enchants as though surrounded by extra bookshelves."
}
```

---

## `neoorigins:biome_buff`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:condition_passive`. See [MIGRATION.md](../MIGRATION.md).

Applies a status effect while the player is standing in a biome matching the given biome tag.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `biome_tag` | Identifier | no | `""` | Biome tag, e.g. `minecraft:is_forest`. Not schema-required, but supply it in practice: the default is an empty tag, which matches no biome, so the buff never fires |
| `effect` | Identifier | no | `minecraft:regeneration` | Effect to apply, e.g. `minecraft:speed` |
| `amplifier` | int | no | `0` | Effect level |

**Example: Speed I in forests**
```json
{
  "type": "neoorigins:biome_buff",
  "biome_tag": "minecraft:is_forest",
  "effect": "minecraft:speed",
  "amplifier": 0,
  "name": "Forest Stride",
  "description": "Moves faster in forest biomes."
}
```

---

## `neoorigins:burn_at_health_threshold`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:condition_passive`. See [MIGRATION.md](../MIGRATION.md).

Ignites the player when their HP drops below a percentage of their maximum health.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `threshold_percent` | float | no | `0.25` | HP fraction that triggers ignition (e.g. `0.25` = below 25%) |
| `fire_ticks` | int | no | `60` | Duration the player is set on fire |

**Example:**
```json
{
  "type": "neoorigins:burn_at_health_threshold",
  "threshold_percent": 0.25,
  "fire_ticks": 60,
  "name": "Critical Combustion",
  "description": "Catches fire when near death."
}
```

---

## `neoorigins:damage_in_biome`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:condition_passive`. See [MIGRATION.md](../MIGRATION.md).

Deals periodic damage to the player while in a matching biome. Match by tag (`biome_tag`) or by an explicit list (`biomes`); damage fires once per second (the interval is fixed at 20 ticks).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `biome_tag` | Identifier | no* | — | Biome tag to match, e.g. `minecraft:is_nether`. Use this *or* `biomes`. |
| `biomes` | array of Identifier | no* | — | Explicit biome IDs; matches if the player is in any of them. Use this *or* `biome_tag`. |
| `damage_per_second` | float | no | `1.0` | Damage applied each second |
| `damage_type` | string | no | `generic` | Damage type used for the hit |

<sub>\* Provide either `biome_tag` or `biomes`. If both are omitted the condition matches no biome.</sub>

**Example:**
```json
{
  "type": "neoorigins:damage_in_biome",
  "biome_tag": "minecraft:is_nether",
  "damage_per_second": 1.0,
  "name": "Nether Intolerance",
  "description": "Takes damage in the Nether."
}
```

---

## `neoorigins:damage_in_daylight`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:condition_passive`. See [MIGRATION.md](../MIGRATION.md).

Deals periodic damage to the player when they are in direct sunlight (sky-exposed, not in shade or water). Fires once per second (the interval is fixed at 20 ticks). Damage and ignition can be combined; set `damage_per_second` to `0` for an ignite-only effect.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `damage_per_second` | float | no | `1.0` | Damage applied each second (set `0` to disable) |
| `ignite` | bool | no | `false` | Also set the player on fire each second |
| `fire_ticks` | int | no | `40` | Burn duration in ticks when `ignite` is true |

**Example:**
```json
{
  "type": "neoorigins:damage_in_daylight",
  "damage_per_second": 1.0,
  "name": "Sun Allergy",
  "description": "Burns in direct sunlight."
}
```

---

## `neoorigins:damage_in_water`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:condition_passive`. See [MIGRATION.md](../MIGRATION.md).

Deals periodic drown damage to the player while they are in water, and, when `include_rain` is enabled, while they are exposed to rain. Damage is applied once per second.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `damage_per_second` | float | no | `1.0` | Damage dealt each second (half-hearts). Set to `0` to disable damage entirely: the power still loads but substitutes a no-op so no hurt sound/animation fires. |
| `multiplier` | float | no | `1.0` | Scale factor applied **on top of** `damage_per_second`: the effective rate is `damage_per_second × multiplier`. Not a synonym: writing both applies both. Setting either to `0` disables the power. |
| `include_rain` | bool | no | `true` | When true, damage also applies while exposed to rain (`in_water` OR `in_rain`); when false, only while in water. |

Server owners can retune the water-damage rate without a datapack: the built-in `vampire_water_weakness` (and the other bundled water-weakness powers) expose a `multiplier` key in `config/neoorigins/power_overrides.toml`, keyed by power id, that overrides the effective damage. For example:
```toml
[power_overrides.vampire_water_weakness]
multiplier = 0.5
```

**Example:**
```json
{
  "type": "neoorigins:damage_in_water",
  "damage_per_second": 2.0,
  "include_rain": true,
  "name": "Hydrophobia",
  "description": "Burns when touched by water or rain."
}
```

---

## `neoorigins:efficient_repairs`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](../MIGRATION.md).

Multiplies the experience-level cost of anvil repairs (`mod_anvil_cost` event). Use a value below `1.0` to make repairs cheaper.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `cost_multiplier` | float | no | `1.0` | Multiplier applied to the anvil level cost |

**Example: half-price anvil repairs**
```json
{
  "type": "neoorigins:efficient_repairs",
  "cost_multiplier": 0.5,
  "name": "Master Smith",
  "description": "Anvil repairs cost half the levels."
}
```

---

## `neoorigins:food_restriction`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](../MIGRATION.md).

Restricts which foods the player can eat. Supports `blacklist` (cannot eat items matching the tag/item list) and `whitelist` (can only eat items matching). `item_tag` accepts a single string or a JSON array of tags and/or item IDs. Prefix entries with `#` for tags; bare strings match exact item IDs.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `mode` | string | no | `"blacklist"` | `"blacklist"` or `"whitelist"` |
| `item_tag` | string or array | no | — | Single tag/item or array of tags/items. Use `#` prefix for tags (e.g. `"#minecraft:meat"`), bare strings for item IDs (e.g. `"minecraft:spider_eye"`). Not schema-required, but supply this or `allowed_tags`: with neither, a blacklist restricts nothing and a whitelist forbids everything. |
| `allowed_tags` | string or array | no | — | Alias for `item_tag`, read only when `item_tag` is absent. Same single-or-array shape. |

**Example: meat-only diet (whitelist)**
```json
{
  "type": "neoorigins:food_restriction",
  "mode": "whitelist",
  "item_tag": "#minecraft:meat",
  "name": "Carnivore",
  "description": "Can only eat meat."
}
```

**Example: vegetarian with multiple blacklisted tags**
```json
{
  "type": "neoorigins:food_restriction",
  "mode": "blacklist",
  "item_tag": ["#minecraft:meat", "#c:foods/raw_meat"],
  "name": "Vegetarian",
  "description": "Cannot eat meat."
}
```

**Example: blacklist specific items and a tag**
```json
{
  "type": "neoorigins:food_restriction",
  "mode": "blacklist",
  "item_tag": ["#minecraft:meat", "minecraft:spider_eye", "minecraft:rotten_flesh"],
  "name": "Clean Eater",
  "description": "Cannot eat meat or unsanitary foods."
}
```

---

## `neoorigins:glow`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:persistent_effect`. See [MIGRATION.md](../MIGRATION.md).

Applies a permanent Glowing effect to the player (visible outline through walls for other players).

No additional fields beyond `name` and `description`.

**Example:**
```json
{
  "type": "neoorigins:glow",
  "name": "Bioluminescence",
  "description": "Emits a faint glow visible to others."
}
```

---

## `neoorigins:healing_mist`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:active_ability`. See [MIGRATION.md](../MIGRATION.md).

Cooldown-gated active ability that heals nearby players in a sphere around the caster. The AoE is players-only, matching the legacy filter.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `heal_amount` | float | no | `6.0` | Health restored to each affected player (half-hearts) |
| `radius` | float | no | `8.0` | Radius of the heal sphere (blocks) |
| `heal_self` | bool | no | `true` | When true the caster is also healed; when false only other players in range are healed |
| `cooldown_ticks` | int | no | `60` | Cooldown between uses in ticks (20 = 1s). Ignored when `cooldown_resource` is set |
| `cooldown_resource` | string | no | `""` | Variable/resource power id whose live value (in ticks) becomes the cooldown length on each activation, overriding `cooldown_ticks`. Empty = fixed cooldown |
| `hunger_cost` | int | no | `0` | Food/exhaustion points consumed per successful activation |
| `resource_cost` | string | no | `""` | Resource power id whose value is spent on activation. Empty = no resource cost |
| `resource_cost_amount` | int | no | `0` | Amount of the `resource_cost` resource consumed per activation |
| `condition` | condition | no | always true | EntityCondition gating the activation. A blocked attempt spends no cooldown |
| `fail_action` | action | no | none | EntityAction run when an activation attempt is blocked by `condition`. Not fired on cooldown or cost aborts |
| `key` | int / string / object | no | none | Hotkey this active binds to instead of the next free skill slot. Same shape as [`active_ability`'s `key`](active.md#neooriginsactive_ability); see [Named keybinds](../concepts/keybinds.md) |

Also accepts the three shared cooldown HUD fields (`cooldown_icon`, `cooldown_countdown`, `always_show_icon`) described at the top of this file.

**Example:**
```json
{
  "type": "neoorigins:healing_mist",
  "heal_amount": 6.0,
  "radius": 8.0,
  "name": "Healing Mist",
  "description": "Releases a restorative mist that heals nearby allies."
}
```

---

## `neoorigins:hunger_drain_modifier`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](../MIGRATION.md).

Multiplies the rate at which the player's hunger depletes.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `multiplier` | float | no | `1.0` | Hunger drain multiplier (`1.3` = 30% faster, `0.5` = half rate) |

**Example:**
```json
{
  "type": "neoorigins:hunger_drain_modifier",
  "multiplier": 1.3,
  "name": "High Metabolism",
  "description": "Gets hungry faster than normal."
}
```

---

## `neoorigins:knockback_modifier`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](../MIGRATION.md).

Multiplies the knockback the player *receives*. There is no outgoing form: the alias always emits `event: mod_knockback`, which fires on the knockback applied to the player.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `multiplier` | float | no | `1.0` | Knockback multiplier. `0` or less cancels the knockback outright. |

**Example: halve knockback taken**
```json
{
  "type": "neoorigins:knockback_modifier",
  "multiplier": 0.5,
  "name": "Sturdy",
  "description": "Resistant to knockback."
}
```

---

## `neoorigins:longer_potions`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](../MIGRATION.md).

Multiplies the duration of potion effects applied to the player (`mod_potion_duration` event).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `duration_multiplier` | float | no | `1.0` | Multiplier applied to potion effect duration |

**Example: potions last twice as long**
```json
{
  "type": "neoorigins:longer_potions",
  "duration_multiplier": 2.0,
  "name": "Slow Metabolism",
  "description": "Potions linger far longer."
}
```

---

## `neoorigins:more_animal_loot`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](../MIGRATION.md).

Multiplies the number of drops harvested from killed animals/mobs (`mod_harvest_drops` event).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `multiplier` | float | no | `1.0` | Multiplier applied to harvest drop count |

**Example:**
```json
{
  "type": "neoorigins:more_animal_loot",
  "multiplier": 2.0,
  "name": "Hunter's Bounty",
  "description": "Animals yield double drops."
}
```

---

## `neoorigins:natural_regen_modifier`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](../MIGRATION.md).

Multiplies all healing the player receives via `LivingHealEvent`. **Note:** this is *not* limited to food-tick natural regen; it also scales Regeneration potion ticks, beacon regen, totem of undying, and any data-pack `neoorigins:heal` actions. If you want to cancel food-tick regen specifically while leaving other heals intact, use `neoorigins:no_natural_regen` instead.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `multiplier` | float | no | `1.0` | Heal multiplier (`0.5` = half all heals, `2.0` = double all heals, `0.0` = block all heals) |

Server owners can retune the built-in slow-regen powers (`vampire_slow_regen`, `revenant_slow_regen`, `necromancer_slow_regen`) without a datapack: each exposes its `multiplier` key in `config/neoorigins/power_overrides.toml`, keyed by power id. For example:
```toml
[power_overrides.vampire_slow_regen]
multiplier = 0.6
```

**Example:**
```json
{
  "type": "neoorigins:natural_regen_modifier",
  "multiplier": 0.5,
  "name": "Slow Recovery",
  "description": "Heals at half the normal rate."
}
```

---

## `neoorigins:night_vision`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:persistent_effect`. See [MIGRATION.md](../MIGRATION.md).

Grants Night Vision at full strength (no ambient effect, no particles). The effect is refreshed every tick.

No additional fields beyond `name` and `description`.

### Player toggle

Night vision is **on by default** and can be switched off by the player with the
dedicated **Toggle Night Vision** keybind (default `K`), listed under *NeoOrigins*
in the vanilla Controls menu.

* It is **not** an ability slot. The key is entirely separate from Skill 1–6 and
  from `active_ability`'s `key` pinning, so night vision costs no slot and cannot
  be switched off by a stray skill keypress.
* The switch is **per player, not per power**. One press covers every
  `minecraft:night_vision` a player is receiving, so an origin with base, evolved
  and ascended night-vision powers still takes exactly one press. Powers that
  bundle night vision with other effects (the Conduit Power line: water breathing
  + night vision + haste) lose only the night vision; the rest keeps working.
* The state is stored on the player and **survives relog and death**. An origin
  reset does not clear it.
* The server owns the flag; the client only sends "flip it" and is told the
  result. The `disable_night_vision` server config in
  [CONTENT_CONFIG.md](../CONTENT_CONFIG.md) overrides it: where an admin has turned
  night vision off globally, the key cannot turn it back on.
* Pack authors need do nothing to opt in: any power applying
  `minecraft:night_vision` through `persistent_effect` (including via this alias)
  is covered automatically. There is no per-power opt-out field.

**Example:**
```json
{
  "type": "neoorigins:night_vision",
  "name": "Dark Vision",
  "description": "Can see clearly in total darkness."
}
```

---

## `neoorigins:regen_in_fluid`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:condition_passive`. See [MIGRATION.md](../MIGRATION.md).

Grants periodic health regeneration while submerged in the specified fluid. Healing fires once per second (the interval is fixed at 20 ticks).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `fluid` | string | no | `water` | Fluid to trigger in: `water` or `lava` |
| `amount_per_second` | float | no | `1.0` | Health restored each second |

**Example:**
```json
{
  "type": "neoorigins:regen_in_fluid",
  "fluid": "water",
  "amount_per_second": 1.0,
  "name": "Aquatic Regeneration",
  "description": "Regenerates health while underwater."
}
```

---

## `neoorigins:repulse`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:active_ability`. See [MIGRATION.md](../MIGRATION.md).

Cooldown-gated active ability that pushes nearby entities outward from the caster (including other players).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | float | no | `6.0` | Radius of the push (blocks) |
| `strength` | float | no | `1.0` | Outward push strength applied to each entity |
| `cooldown_ticks` | int | no | `60` | Cooldown between uses in ticks (20 = 1s). Ignored when `cooldown_resource` is set |
| `cooldown_resource` | string | no | `""` | Variable/resource power id whose live value (in ticks) becomes the cooldown length on each activation, overriding `cooldown_ticks`. Empty = fixed cooldown |
| `hunger_cost` | int | no | `0` | Food/exhaustion points consumed per successful activation |
| `resource_cost` | string | no | `""` | Resource power id whose value is spent on activation. Empty = no resource cost |
| `resource_cost_amount` | int | no | `0` | Amount of the `resource_cost` resource consumed per activation |
| `condition` | condition | no | always true | EntityCondition gating the activation. A blocked attempt spends no cooldown |
| `fail_action` | action | no | none | EntityAction run when an activation attempt is blocked by `condition`. Not fired on cooldown or cost aborts |
| `key` | int / string / object | no | none | Hotkey this active binds to instead of the next free skill slot. Same shape as [`active_ability`'s `key`](active.md#neooriginsactive_ability); see [Named keybinds](../concepts/keybinds.md) |

Also accepts the three shared cooldown HUD fields (`cooldown_icon`, `cooldown_countdown`, `always_show_icon`) described at the top of this file.

**Example:**
```json
{
  "type": "neoorigins:repulse",
  "radius": 6.0,
  "strength": 1.5,
  "name": "Force Repulse",
  "description": "Blasts nearby entities away."
}
```

---

## `neoorigins:stacking_status_effects`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:persistent_effect`. See [MIGRATION.md](../MIGRATION.md).

Continuously applies a list of potion effects while the origin is active. Unlike `status_effect` (single effect) this carries a full `effects` array, and unlike `persistent_effect` it is always-on: the alias forces `toggleable` off, so the effects cannot be keybind-toggled.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effects` | list of EffectSpec | no | `[]` | Mob effects to apply, passed through verbatim. Same per-entry shape as `persistent_effect`'s `EffectSpec` (`effect`/`id`, `amplifier`, `ambient`, `show_particles`, `show_icon`). Not schema-required, but an empty list applies nothing. |
| `amplifier` | int | no | — | Root-level override for the **first** entry's amplifier, inherited from `persistent_effect`. Lets `power_overrides` retune strength without editing the array; later entries are unchanged. |

Also takes the shared [toggle upkeep](../concepts/costs-and-upkeep.md#toggle-upkeep) fields (`hunger_cost`, `resource_cost`, `resource_cost_amount`, `upkeep_interval`, `upkeep_condition`).

**Example:**
```json
{
  "type": "neoorigins:stacking_status_effects",
  "effects": [
    { "effect": "minecraft:strength", "amplifier": 0 },
    { "effect": "minecraft:speed",    "amplifier": 1 }
  ],
  "name": "Battle Trance",
  "description": "Permanently empowered."
}
```

---

## `neoorigins:status_effect`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:persistent_effect`. See [MIGRATION.md](../MIGRATION.md).

Continuously applies a potion effect while the origin is active. The effect is refreshed every tick and removed when the origin is revoked.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effect` | Identifier | no | — | Effect ID, e.g. `minecraft:strength`. Not schema-required, but supply this or `effects`: with neither, the power applies nothing |
| `amplifier` | int | no | `0` | Effect level (0 = level I, 1 = level II, …) for the root-level `effect`. Alongside `effects` it does not cascade — it overrides the FIRST entry's amplifier, winning even over one that entry sets itself, and leaves later entries alone |
| `ambient` | bool | no | `true` | Whether the effect is ambient (reduced particle visibility). Cascades onto `effects` entries that omit it |
| `show_particles` | bool | no | `false` | Whether to show particles. Cascades onto `effects` entries that omit it |
| `show_icon` | bool | no | `true` | When false the effect's HUD status icon is hidden. Cascades onto `effects` entries that omit it |
| `effects` | list of EffectSpec | no | — | Multi-effect form, same per-entry shape as [`persistent_effect`](core.md#neooriginspersistent_effect)'s `EffectSpec`. Only `ambient`/`show_particles`/`show_icon` cascade from the root onto entries that omit their own: the root `effect` is read solely when `effects` is absent, and the root `amplifier` overrides just the first entry (see its row above) |
| `toggleable` | bool | no | `true` | When true the power binds a keybind that flips the effects on/off; off clears them |
| `default_off` | bool | no | `false` | Toggleable only: when true the effects START disabled so the player must opt in via the keybind |

Also takes the shared [toggle upkeep](../concepts/costs-and-upkeep.md#toggle-upkeep) fields (`hunger_cost`, `resource_cost`, `resource_cost_amount`, `upkeep_interval`, `upkeep_condition`).

**Example: permanent Strength I**
```json
{
  "type": "neoorigins:status_effect",
  "effect": "minecraft:strength",
  "amplifier": 0,
  "name": "Brute Strength",
  "description": "Permanently empowered by inner fire."
}
```

**Common effects:** `minecraft:strength`, `minecraft:speed`, `minecraft:haste`, `minecraft:regeneration`, `minecraft:resistance`, `minecraft:fire_resistance`, `minecraft:water_breathing`, `minecraft:night_vision`, `minecraft:jump_boost`, `minecraft:slow_falling`

---

## `neoorigins:teleport_range_modifier`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](../MIGRATION.md).

Multiplies the player's teleport range (e.g. ender-pearl throw distance) via the `mod_teleport_range` event.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `multiplier` | float | no | `2.0` | Multiplier applied to teleport range |

**Example:**
```json
{
  "type": "neoorigins:teleport_range_modifier",
  "multiplier": 2.0,
  "name": "Far Step",
  "description": "Teleports reach twice as far."
}
```

---

## `neoorigins:thorns_aura`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](../MIGRATION.md).

Reflects a portion of incoming melee damage back to the attacker.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `return_ratio` | float | no | `0.25` | Fraction of damage dealt back (`0.25` = 25%) |

**Example:**
```json
{
  "type": "neoorigins:thorns_aura",
  "return_ratio": 0.5,
  "name": "Thorny Hide",
  "description": "Reflects half of incoming melee damage."
}
```
