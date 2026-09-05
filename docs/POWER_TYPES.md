---
title: Power Types
parent: "DSL Reference"
nav_order: 2
---

# NeoOrigins Power Types Reference

All powers share six optional top-level fields:

| Field | Type | Description |
|---|---|---|
| `name` | string or `{"text":"..."}` / `{"translate":"..."}` | Display name shown in the origin selection screen. A plain string is treated as a translation key. |
| `description` | string or `{"text":"..."}` / `{"translate":"..."}` | Description shown below the power name. Same resolution rules as `name`. |
| `hidden` | bool (default `false`) | When `true`, this power is excluded from the origin info panel. The mechanical effect still applies; only the display row is suppressed. Useful for purely-internal flag/glue powers (e.g. `neoorigins:toggle`, on-hit setters wired under a `multiple`). |
| `required_mods` | list of mod ids (default `[]`) | Load gate: the power only loads when every listed mod is present. Use it for content that targets an optional mod (e.g. the built-in Dragon Survival dragon forms carry `"required_mods": ["dragonsurvival"]`). Origins accept the same field. See [PACK_FORMAT.md](PACK_FORMAT.md). |
| `power_condition` | entity condition object | Runtime gate: the condition is re-evaluated against the power's holder every time the power would act, and the power only operates when the gate is satisfied. Works on every power type. See the universal power condition gate section in [CONDITIONS.md](CONDITIONS.md) for details and the condition format. |
| `power_condition_mode` | `"ALLOW"` or `"DENY"` (default `"DENY"`) | How `power_condition` gates: `ALLOW` = the power is active **while the condition is true**; `DENY` = the power is disabled while the condition is true. Case-insensitive. |

**`condition` as an alias.** On power types that don't have their own `condition` config field, a top-level `condition` is accepted as an alias for `power_condition` with mode `ALLOW` (so the power is active while the condition holds, the intuitive reading). Notes:

- Nine types claim `condition` for their own config and are excluded from the alias: `model_color`, `attribute_modifier`, `action_on_event`, `modify_damage`, `active_ability`, `persistent_effect`, `condition_passive`, `prevent_death`, `conditional`. On those, write `power_condition` for the whole-power gate.
- An explicit `power_condition_mode` is honored even when the gate comes in via the alias.
- If both `power_condition` and an aliased `condition` are present, `power_condition` wins and a warning is logged.
- Prefer `power_condition` in new packs; the alias exists so the common Apoli-style spelling doesn't get silently dropped.

**Action & condition fields take an object _or_ an array.** Wherever a field below holds an action (e.g. `entity_action`) or a condition (e.g. `condition`), you may pass either a single object or an array of them. Action arrays run in order (implicit `neoorigins:and`); condition arrays must all pass (implicit AND). An empty array no-ops for actions and is always-true for conditions. See [ACTIONS.md](ACTIONS.md) and [CONDITIONS.md](CONDITIONS.md).

If neither `name` nor `description` is present, NeoOrigins falls back to the lang key convention:
`power.<namespace>.<path>.name` / `power.<namespace>.<path>.description`

**Cooldown HUD fields (active powers).** Every cooldown-gated active (keybind) power type (`active_ability`, `active_teleport`, `active_dash`, `active_recall`, `active_swap`, `active_fireball`, `active_bolt`, `active_phase`, `active_place_block`, `ground_slam`, `tidal_wave`, `command_pack`, `elytra_boost`, `mount`, `shadow_orb`, `summon_minion`, `tame_mob`, `loot_pool_grant`) additionally accepts three optional HUD fields:

| Field | Type | Default | Description |
|---|---|---|---|
| `cooldown_icon` | string | `""` | HUD cooldown icon: an item id (e.g. `minecraft:ender_pearl`) rendered as the item, or a datapack texture path ending in `.png` (resolved under `assets/<namespace>/textures/`, e.g. `mypack:gui/fireball.png` → `assets/mypack/textures/gui/fireball.png`) drawn 16×16. When set, the HUD swaps that slot's cooldown bar for the icon with a clock-style radial sweep (dark fill over the not-yet-recharged arc, wiping clockwise from 12 o'clock). Empty keeps the plain bar. |
| `cooldown_countdown` | bool | `true` | Draw the remaining cooldown in whole seconds translucently on the icon. Only applies when `cooldown_icon` is set; players can suppress all countdown numbers with the `show_cooldown_countdown` client config switch and tune the text opacity with `cooldown_countdown_opacity`. |
| `always_show_icon` | bool | `false` | Keep this power's icon on the ability HUD cluster even while it is idle / off cooldown (full-bright, no sweep, no countdown). Only applies when `cooldown_icon` is set. **Note:** this is what makes an idle icon persist under the default client HUD mode (`hud_ability_display: COOLDOWNS_AND_TOGGLES`), which otherwise shows a non-toggle icon only while it is recharging. Under `ALL_ACTIVE_ABILITIES` every icon-bearing ability already keeps a slot, so the field has no additional effect there (players can also force all icons on with `always_show_ability_icons`). |

**Toggleable powers** (`flight`, `item_magnetism`, `no_mob_spawns_nearby`, `phantom_form`, `stealth`, `wraith_phase`, plus `persistent_effect` and `condition_passive` when authored with `"toggleable": true`, and `pose`, which is toggleable unless you set `"toggleable": false`) also accept `cooldown_icon` and `always_show_icon`. A toggle with an icon joins the HUD cluster: full-bright while toggled on, dimmed while off (no cooldown sweep).

Icon slots are labeled with the bound key's short name in the top-right corner; hovering an icon while a screen is open (chat, the HUD editor) shows the power's name and description. The `hud_ability_display` client config picks what the cluster shows besides live cooldowns: `COOLDOWNS_AND_TOGGLES` (default since 2.2.9: cooldown slots only while recharging, plus icon-bearing toggles) or `ALL_ACTIVE_ABILITIES` (every icon-bearing keybind ability keeps a persistent slot, full-bright while idle, sweep while recharging).

The cooldown cluster itself is draggable in the in-game HUD editor (same screen as resource bars); its position persists in `config/neoorigins/hud.json`.

The client-side switches mentioned above (`show_cooldown_countdown`, `cooldown_countdown_opacity`, `hud_ability_display`, `always_show_ability_icons`) live in `config/neoorigins/client.toml`. See [CLIENT_CONFIG.md](CLIENT_CONFIG.md) for the full list of per-client options.

---

## `neoorigins:simple`

Does nothing. A display-only marker power, the direct equivalent of `origins:simple` from the original Origins mod.

It has no gameplay effect, no capabilities, and no behavior. Its only purpose is to appear as an entry in the origin info panel so you can attach a `name` + `description` (the heading + body text every power carries) without also granting an ability. Use it for flavor text, lore lines, or to describe an effect that is implemented elsewhere (a mixin, datapack, command, or another mod).

No additional fields beyond `name` and `description`.

**Example:**
```json
{
  "type": "neoorigins:simple",
  "name": "Cold Blooded",
  "description": "You feel the chill of the deep more keenly than others."
}
```

> Imported `origins:simple` / `apace:simple` (and `origins:tooltip`) powers translate to this type automatically when no specific id-override applies, so their text still shows in the GUI.

---

## `neoorigins:multiple`

A container that bundles several powers into one. Every key in the JSON other than the shared display fields is treated as a **sub-power** — its own complete power object. At datapack load the container is flattened: each sub-power becomes a standalone power with the synthetic id `<container-namespace>:<container-path>/<subkey>`, and the origin's power list is rewritten to reference those synthetic ids instead of the container.

Use it when a single conceptual ability is really several powers working together (a passive attribute + an on-hit action + a keybind active), so the origin panel can collapse them under one name and description instead of listing each piece.

| Field | Type | Description |
|---|---|---|
| `name` / `description` | display fields | Shown once for the whole bundle. The origin selection screen collapses the sub-powers back under this single heading. |
| `<subkey>` | power object | Any other key is a sub-power. Its value is a full power object (any type, including a nested `neoorigins:multiple`). The key name becomes the last path segment of the sub-power's synthetic id. |

Sub-powers may be native `neoorigins:` powers or imported `origins:`/`apoli:` powers; each is loaded by the same pipeline it would use on its own. A sub-power can reference a sibling with the `*:*<subkey>` self-reference shorthand, which resolves to the sibling's synthetic id.

**Example: a passive flag wired to an on-hit action under one entry**
```json
{
  "type": "neoorigins:multiple",
  "name": "Venomous",
  "description": "Your strikes carry poison.",
  "poison_on_hit": {
    "type": "neoorigins:action_on_hit",
    "hidden": true,
    "entity_action": { "type": "neoorigins:apply_effect", "effect": "minecraft:poison", "duration": 60 }
  },
  "immune": {
    "type": "neoorigins:effect_immunity",
    "hidden": true,
    "effects": ["minecraft:poison"]
  }
}
```
This loads as two powers, `<ns>:<path>/poison_on_hit` and `<ns>:<path>/immune`, collapsed under the single "Venomous" entry.

> The imported `origins:multiple` / `apace:multiple` (and `apoli:multiple`) containers flatten through the exact same path, so existing Apoli packs keep working unchanged. `neoorigins:multiple` is the first-class, in-namespace spelling for new native packs.

---

## `neoorigins:attribute_modifier`

Adds or multiplies a player attribute while the origin is active. Optionally gated on an environment condition, an equipped-item condition, or both (AND).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `attribute` | Identifier | yes | — | Attribute to modify, e.g. `minecraft:generic.movement_speed` |
| `amount` | double | yes | — | Amount to add or multiply |
| `operation` | string | no | `add_value` | `add_value`, `add_multiplied_base`, or `add_multiplied_total` |
| `condition` | string | no | — | Environment gate: `in_water`, `on_land`, or `in_lava`. Tick-driven apply/remove. See the note below on what `on_land` counts. |
| `equipment_condition` | object | no | — | Equipment gate (see below). Tick-driven apply/remove. |
| `location_condition` | object | no | — | Location gate: dimension / biome / structure (see below). Tick-driven apply/remove. |

**Operations:**
- `add_value`: flat addition to base value
- `add_multiplied_base`: adds `base * amount` (e.g. `-0.1` = 10% slower)
- `add_multiplied_total`: multiplies total after all other modifiers

**`equipment_condition` object:**

| Field | Type | Required | Description |
|---|---|---|---|
| `slot` | string | yes | One of `mainhand`, `offhand`, `head`, `chest`, `legs`, `feet`, `body` |
| `item` | Identifier | no | Exact item ID to match (e.g. `minecraft:iron_helmet`) |
| `tag` | Identifier | no | Item tag to match (e.g. `minecraft:helmets`) |

If both `item` and `tag` are given, either match satisfies the condition (OR). If neither is given, any non-empty stack in the slot counts as a match. When multiple of `condition` / `equipment_condition` / `location_condition` are set, **all** must hold for the modifier to apply.

**`location_condition` object:**

| Field | Type | Required | Description |
|---|---|---|---|
| `dimension` | Identifier | no | Match only in this dimension, e.g. `minecraft:the_end` |
| `biome` | Identifier | no | Match only in this specific biome, e.g. `minecraft:plains` |
| `biome_tag` | Identifier | no | Match only in biomes with this tag, e.g. `minecraft:is_forest` |
| `structure` | Identifier | no | Match only inside this structure, e.g. `minecraft:end_city` |
| `structure_tag` | Identifier | no | Match only inside structures with this tag, e.g. `minecraft:on_ocean_monument_maps` |

All fields are optional and combine with AND. So `{ "dimension": "minecraft:the_end", "structure": "minecraft:end_city" }` is "only when standing inside an End City in The End." Structure membership is evaluated server-side via `ServerLevel.structureManager()`.

**Stacking across layers.** Each `attribute_modifier` power owns its own modifier id, so two powers touching the same attribute add up rather than replacing one another: an origin granting +6 max health and a class granting +4 leave the player at +10. Changing one layer re-grants only that layer and leaves every other layer's modifiers in place, so re-picking a class no longer costs the player their origin's health, armor or reach. Modifiers left behind by a power that no longer exists (JSON deleted or renamed) are swept at that same moment, so stale bonuses cannot accumulate either.

**Example: 8 flat armor (unconditional)**
```json
{
  "type": "neoorigins:attribute_modifier",
  "attribute": "minecraft:generic.armor",
  "amount": 8.0,
  "operation": "add_value",
  "name": "Shell",
  "description": "Has permanent natural armor."
}
```

**Example: slower on land only**
```json
{
  "type": "neoorigins:attribute_modifier",
  "attribute": "minecraft:generic.movement_speed",
  "amount": -0.25,
  "operation": "add_multiplied_base",
  "condition": "on_land",
  "name": "Landwalker",
  "description": "Moves slower while out of water."
}
```

> **`on_land` treats an active conduit as still being in water.** A player carrying
> Conduit Power does not count as on land, so a land penalty written this way lifts
> while they are in a conduit's range instead of punishing them inside their own
> base. `in_water` is the literal check and is unaffected, which means the two are
> not exact opposites: a player with Conduit Power on dry ground satisfies neither.
> Rain is not water for either gate — a player out in a storm is on land.

**Example: +1 attack when wearing any helmet**
```json
{
  "type": "neoorigins:attribute_modifier",
  "attribute": "minecraft:generic.attack_damage",
  "amount": 1.0,
  "equipment_condition": {
    "slot": "head",
    "tag": "minecraft:helmets"
  },
  "name": "Helm of Valor",
  "description": "Empowered while helmeted."
}
```

**Example: +2 armor only inside End Cities**
```json
{
  "type": "neoorigins:attribute_modifier",
  "attribute": "minecraft:generic.armor",
  "amount": 2.0,
  "location_condition": {
    "dimension": "minecraft:the_end",
    "structure": "minecraft:end_city"
  },
  "name": "Void Ward",
  "description": "Armored while within the towers of the End."
}
```

**Useful attributes:**
- `minecraft:movement_speed`: walk speed (base ≈ 0.1)
- `minecraft:water_movement_efficiency`: swim speed boost (0.0–1.0, stacks with Depth Strider)
- `minecraft:armor`: armor points
- `minecraft:armor_toughness`: armor toughness
- `minecraft:attack_damage`: melee damage
- `minecraft:attack_speed`: attack cooldown speed
- `minecraft:max_health`: max HP
- `minecraft:fall_damage_multiplier`: fall damage scale
- `minecraft:mining_efficiency`: mining speed bonus
- `minecraft:oxygen_bonus`: extends underwater air (like Respiration)

> **Note:** 1.21+ uses short-form IDs (`minecraft:movement_speed`). The legacy `minecraft:generic.movement_speed` format still parses via fallback.

**Iron's Spells 'n Spellbooks attributes:**

The `attribute` field resolves any registered attribute id, so it can target the
custom attributes added by [Iron's Spells 'n Spellbooks](COMPATIBILITY.md#irons-spells-n-spellbooks)
(`irons_spellbooks`). The eight ids a pack can modify:

| Attribute id | `add_value` does | Scale |
|---|---|---|
| `irons_spellbooks:max_mana` | Flat add to mana capacity | Flat points (`100.0` = +100 max mana) |
| `irons_spellbooks:mana_regen` | Mana regen multiplier | Base 1.0: `0.5` ≈ +50% regen |
| `irons_spellbooks:spell_power` | Overall spell power multiplier | Base 1.0: `0.25` ≈ +25% |
| `irons_spellbooks:spell_resist` | Incoming magic resistance | Base 1.0: `0.2` ≈ +20% resist |
| `irons_spellbooks:cooldown_reduction` | Spell cooldown reduction | Fraction 0–1 (`0.15` = 15%) |
| `irons_spellbooks:cast_time_reduction` | Cast-time reduction | Fraction 0–1 (`0.15` = 15%) |
| `irons_spellbooks:casting_movespeed` | Movement speed while casting | Movement-speed units |
| `irons_spellbooks:summon_damage` | Summoned-mob damage | Damage units |

The operation and scale differ per attribute: `max_mana` is a flat add;
`spell_power` / `mana_regen` / `spell_resist` sit on a base of `1.0`, so `add_value`
is a fractional bonus; `cooldown_reduction` / `cast_time_reduction` are 0-based
fractions in 0–1. Match Iron's own scaling. There are no per-school spell-power
attributes in 3.14.0; those eight are the complete list. Referencing one on a
server without Iron's installed logs one warning per grant and applies nothing (the
power still loads); gate the origin with `"required_mods": ["irons_spellbooks"]` if
it should only exist when Iron's is present.

---

## `neoorigins:status_effect`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:persistent_effect`. See [MIGRATION.md](MIGRATION.md).

Continuously applies a potion effect while the origin is active. The effect is refreshed every tick and removed when the origin is revoked.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effect` | Identifier | yes | — | Effect ID, e.g. `minecraft:strength` |
| `amplifier` | int | no | `0` | Effect level (0 = level I, 1 = level II, …) |
| `ambient` | bool | no | `true` | Whether the effect is ambient (reduced particle visibility) |
| `show_particles` | bool | no | `false` | Whether to show particles |

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

## `neoorigins:stacking_status_effects`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:persistent_effect`. See [MIGRATION.md](MIGRATION.md).

Continuously applies a list of potion effects while the origin is active. Unlike `status_effect` (single effect) this carries a full `effects` array, and unlike `persistent_effect` it is always-on: the alias forces `toggleable` off, so the effects cannot be keybind-toggled.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effects` | list of EffectSpec | yes | — | Mob effects to apply, passed through verbatim. Same per-entry shape as `persistent_effect`'s `EffectSpec` (`effect`/`id`, `amplifier`, `ambient`, `show_particles`, `show_icon`). |

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

## `neoorigins:prevent_action`

Prevents a specific harmful action or event from affecting the player.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `action` | string | yes | — | The action to prevent (see values below) |
| `active_when` | string | no | `"always"` | Stance gate for the prevention: `"always"`, `"sneaking"`, `"not_sneaking"`, `"on_ground"`, or `"not_on_ground"`. The action is only prevented while the condition holds. |
| `head` / `chest` / `legs` / `feet` | bool | no | `false` | Per-slot toggles for `armor_equip` only. When `true`, the matching armor slot rejects equip attempts (item snaps back to inventory, drops on the ground if full). |

**Action values:**

| Value | What it prevents |
|---|---|
| `fire` | All fire and lava damage |
| `fall_damage` | All fall damage |
| `drown` | Drowning damage |
| `freeze` | Freeze damage from powder snow |
| `sprint_food` | Sprinting no longer drains extra hunger |
| `armor_equip` | Prevents wearing armor in any slot whose corresponding `head` / `chest` / `legs` / `feet` boolean is `true`. Items are ejected back to the inventory (or dropped if full). |
| `chestplate_equip` | Legacy alias of `armor_equip` with `chest: true`. Kept for back-compat with packs that pre-date `armor_equip`. |
| `eye_damage` | Prevents projectile hits to the eye |
| `water_damage` | Prevents water/rain contact damage |
| `swim` | Prevents swimming (velocity-sinks the player in water) |
| `sleep` | Prevents sleeping (bed use returns a "no sleep" message). Sleepless origins should pair this with `modify_player_spawn` if they still want bed interactions to set respawn. |
| `elytra` | Prevents elytra flight (player is stopped from gliding each tick) |

**Example: fire immunity**
```json
{
  "type": "neoorigins:prevent_action",
  "action": "fire",
  "name": "Fire Immunity",
  "description": "Immune to fire and lava."
}
```

**Example: heavy-armor restriction (chest + legs only)**
```json
{
  "type": "neoorigins:prevent_action",
  "action": "armor_equip",
  "chest": true,
  "legs":  true,
  "name": "Light-Footed",
  "description": "Cannot wear chestplate or leggings."
}
```

---

## `neoorigins:modify_lava_speed`

Modifies the player's movement speed while submerged in lava. Uses the `NumericModifierRegistry` system consumed by `LivingEntityLavaSpeedMixin`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `operation` | string | no | `"addition"` | `"addition"`, `"multiply_base"`, or `"multiply_total"` |
| `value` | double | yes | — | Amount to add or multiply. Vanilla lava-swim factor is `0.02`; an addition of `0.04` (3× vanilla) feels like swimming in water. |

**Example: water-swim-pace lava swimming**
```json
{
  "type": "neoorigins:modify_lava_speed",
  "operation": "addition",
  "value": 0.04,
  "name": "Molten Stride",
  "description": "Swims through lava as easily as others swim through water."
}
```

---

## `neoorigins:modify_flight_speed`

Scales creative / hover flight speed: the vanilla `Abilities.flyingSpeed` value (base `0.05`) that the game reads while a player is flying. This is the in-mod equivalent of Pehkui's `pehkui:flight` scale, so packs no longer need a Pehkui dependency to retune flight speed. It composes with any flight source (the `creative_flight` / `flight` powers, vanilla creative, or another mod's mayfly) because it edits the shared flight-speed input rather than a specific flight mechanic.

It does **not** affect elytra / fall-flying gliding, which is physics-driven and never reads `flyingSpeed`, matching Pehkui's flight scale.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `operation` | string | no | `"multiply_base"` | `"addition"`, `"multiply_base"`, or `"multiply_total"` |
| `value` | double | yes | — | Amount applied to the `0.05` base. `multiply_base 1.0` doubles flight speed, `-0.5` halves it, `-0.25` ≈ Pehkui flight scale `0.75`. |

Multiple holders of this power stack the Apoli way (additions sum, then the multiplier deltas sum), exactly like `modify_lava_speed`.

**Example: Pehkui flight scale 0.75 equivalent**
```json
{
  "type": "neoorigins:modify_flight_speed",
  "operation": "multiply_base",
  "value": -0.25,
  "name": "Measured Flight",
  "description": "Hovers at three-quarters the usual flight speed."
}
```

---

## `neoorigins:modify_damage`

Multiplies damage dealt or received, optionally filtered to a specific damage type and, for `direction: out`, restricted to a target entity group.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `direction` | string | no | `in` | `in` (damage received) or `out` (damage dealt) |
| `multiplier` | float | no | `1.0` | Damage multiplier (e.g. `2.0` = double, `0.5` = half) |
| `damage_type` | string | no | _(all types)_ | Optional vanilla damage type to filter, e.g. `drown`, `fire`, `fall` |
| `target_group` | string | no | _(any)_ | Outgoing only. Restrict to targets in this entity group: `undead`, `arthropod`, `illager`, `aquatic`. Resolved as the vanilla `minecraft:<group>` entity-type tag. |
| `condition` | condition | no | _(always)_ | Only applies while this DSL condition passes. |
| `set_total` | float | no | — | Replaces the damage with this exact value after `multiplier` is applied. |
| `max_total` | float | no | — | Damage cap. |
| `min_total` | float | no | — | Damage floor. |

The three total fields are the Apoli `set_total` / `max_total` / `min_total` stages and run in that order, after the multiplier: `damage * multiplier` → replace with `set_total` → cap at `max_total` → raise to `min_total`. `max_total` and `min_total` still clamp a value that `set_total` just wrote, so a `set_total` of `100` alongside a `max_total` of `10` yields `10`. Omit a field to skip that stage.

**Example: double incoming water damage**
```json
{
  "type": "neoorigins:modify_damage",
  "direction": "in",
  "multiplier": 2.5,
  "damage_type": "drown",
  "name": "Water Weakness",
  "description": "Takes extra damage from water."
}
```

**Example: bonus fire damage dealt**
```json
{
  "type": "neoorigins:modify_damage",
  "direction": "out",
  "multiplier": 1.5,
  "damage_type": "fire",
  "name": "Fire Mastery",
  "description": "Deals extra fire damage."
}
```

**Example: +50% damage to undead**
```json
{
  "type": "neoorigins:modify_damage",
  "direction": "out",
  "multiplier": 1.5,
  "target_group": "undead",
  "name": "Smite",
  "description": "Strikes the undead harder."
}
```

**Common damage types:** `fire`, `in_fire`, `lava`, `drown`, `fall`, `freeze`, `magic`, `wither`, `lightning_bolt`, `fly_into_wall`, `generic`

**Matching:** damage types are matched against both the vanilla message ID (camelCase, e.g. `flyIntoWall`) and the registry key path (snake_case, e.g. `fly_into_wall`). Either convention works. Tag-based filters like `#minecraft:is_fire` match all damage types in that tag.

---

## `neoorigins:prevent_death`

Cancels the lethal blow instead of letting the player die. Faithful to Origins' `prevent_death`, with first-class condition and damage-type gating.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `condition` | object | no | _(always)_ | Standard entity condition; the power only saves the player while it is true. |
| `damage_types` | string | no | _(all)_ | Damage-type filter (comma-separated, `#tags`, msgIds, or registry keys). When set, only matching killing blows are prevented. |
| `invert` | bool | no | `false` | Flips `damage_types` into a blacklist: prevent all deaths _except_ the listed types. |
| `set_health` | float | no | `1.0` | Health the player is left at after a save (minimum 1). |
| `cooldown_ticks` | int | no | `0` | After a save, the power is inert for N ticks. `0` = unlimited (Origins behavior). |
| `entity_action` | object | no | — | Optional action run on the player each time a death is prevented. |

**Example: survive lethal fire once every 30 s, then drop to 4 hearts**
```json
{
  "type": "neoorigins:prevent_death",
  "damage_types": "#minecraft:is_fire,lava",
  "set_health": 8.0,
  "cooldown_ticks": 600,
  "name": "Fireproof Soul",
  "description": "Cannot burn to death — but only so often."
}
```

**Example: immortal except to the void, only while sneaking**
```json
{
  "type": "neoorigins:prevent_death",
  "damage_types": "fell_out_of_world",
  "invert": true,
  "condition": { "type": "neoorigins:sneaking" },
  "name": "Guarded Stance",
  "description": "Death cannot touch a braced body — the void still can."
}
```

**Caveat (same as Origins):** this only cancels the lethal event; it does not clear the damage source. For recurring damage-over-time (fire, lava, poison) pair it with a `condition` or an `entity_action` that removes the source, otherwise the player is re-killed on the next damage tick.

---

## `neoorigins:flight`

Elytra-style fall-flight launched from a **mid-air jump**, as a toggle. While the toggle is on, jumping again in mid-air spreads the player's wings and starts fall-flying without an equipped elytra; toggling the power off lands them. It is not creative hover: for that, see `neoorigins:creative_flight` below.

The launch is refused on the ground, in water, while riding, in spectator, and while already fall-flying.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `cooldown_icon` | string | no | `""` | HUD icon (joins the ability cluster); full-bright while toggled on, dimmed while off |
| `always_show_icon` | boolean | no | `false` | Keep the icon on the HUD even while toggled off |
| `render_elytra` | boolean | no | `false` | Whether an elytra is drawn on the player's back while flying. Flight works either way; this is cosmetic only. |
| `texture_location` | identifier | no | vanilla elytra | Custom texture for the drawn elytra. Only applies when `render_elytra` is true; the model stays the vanilla elytra. |

**Example:**
```json
{
  "type": "neoorigins:flight",
  "render_elytra": true,
  "cooldown_icon": "minecraft:elytra",
  "name": "Natural Flight",
  "description": "Jump again in mid-air to take to the sky, no elytra required."
}
```

As with `neoorigins:natural_glide`, the flight runs with an empty chest slot, so nothing is drawn on the player's back unless `render_elytra` is set. See the note under `natural_glide` for why that defaults off.

---

## `neoorigins:creative_flight`

True creative-style hover flight, as a **toggle**. Unlike `neoorigins:flight` and `natural_glide`, which are both elytra/fall-flying mechanics, this grants real `mayfly` hover: the player keeps solid block collision, normal visibility and gravity when not flying. Double-tap jump to take off, then jump to rise and sneak to descend, exactly like creative mode. Intended for "ride the sword" / levitating-cultivator fantasies.

The flight abilities are re-pushed to the client every tick to survive sync races. When the power is removed or toggled off, survival defaults are restored, but never for a creative or spectator player, so toggling off can't lock them out of their own game mode.

Beyond `name` and `description`:

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `condition` | condition | no | always true | EntityCondition gating the flight, re-tested every tick |
| `cooldown_icon` | string | no | `""` | HUD icon resource path (joins the ability-icon cluster) |
| `always_show_icon` | bool | no | `false` | Keep the icon on the HUD even while toggled off |

**Requiring something of the flier.** With a `condition`, the flight is granted only while the condition passes, and — because it is re-tested every tick — a player who breaks it in mid-air has the ability stripped and falls. That drop is the point: it turns the condition into a real cost rather than a check made once at takeoff. Toggling on while the condition fails leaves the power armed but grounded; the flight begins the moment the condition is met. The built-in Sword Immortal uses this to bind flight to the blade in hand:

```json
{
  "type": "neoorigins:creative_flight",
  "condition": {
    "type": "neoorigins:equipped_item",
    "equipment_slot": "mainhand",
    "item_condition": { "tag": "minecraft:swords" }
  }
}
```

Pair a conditional flight with a fall-damage immunity (see `neoorigins:prevent_action`) if the drop should be survivable.

**Disabling it server-side.** `creative_flight` honors a top-level `enabled` flag (default `true`) that server owners can flip off in `config/neoorigins/power_overrides.toml`, keyed by the power id. When `enabled = false` the flight is stripped every tick and never re-granted; the origin keeps all its other powers. This is the intended way to ground a flying origin without editing datapacks; for example, the built-in Sword Immortal and Windwalker flights are:

```toml
[power_overrides.jianxian_riding_sword]
    enabled = false   # ground the Sword Immortal
[power_overrides.windwalker_riding_wind]
    enabled = false   # ground the Windwalker
```

**Example:**
```json
{
  "type": "neoorigins:creative_flight",
  "name": "Riding the Wind",
  "description": "Double-tap jump to take to the air and fly freely."
}
```

---

## `neoorigins:night_vision`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:persistent_effect`. See [MIGRATION.md](MIGRATION.md).

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
  [CONTENT_CONFIG.md](CONTENT_CONFIG.md) overrides it. Where an admin has turned
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

## `neoorigins:water_breathing`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:persistent_effect`. See [MIGRATION.md](MIGRATION.md).

Grants permanent Water Breathing. The player never loses air while underwater.

No additional fields beyond `name` and `description`.

**Example:**
```json
{
  "type": "neoorigins:water_breathing",
  "name": "Gills",
  "description": "Can breathe underwater indefinitely."
}
```

---

## `neoorigins:no_slowdown`

Prevents the player from being slowed by movement-impeding blocks: cobwebs,
sweet berry bushes, and powder snow (the stuck-velocity clamp), plus soul
sand and honey blocks (the reduced walk-speed factor).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `block_tag` | string | no | _(all slowdown blocks)_ | Restrict immunity to blocks in this tag |

With `block_tag` omitted the immunity is unconditional and predicted
client-side, so there's no rubberbanding when entering a web. Restricting
it to a tag keeps the check server-authoritative: a brief correction may
be visible.

**Example: immune to all block slowdown**
```json
{
  "type": "neoorigins:no_slowdown",
  "name": "Unimpeded",
  "description": "Not slowed by webs, soul sand, or dense foliage."
}
```

---

## `neoorigins:wall_climbing`

Allows the player to cling to and climb any solid wall surface, similar to spiders. Fall damage is suppressed while climbing.

No additional fields beyond `name` and `description`.

**Example:**
```json
{
  "type": "neoorigins:wall_climbing",
  "name": "Wall Climbing",
  "description": "Can scale any solid surface."
}
```

---

## `neoorigins:elytra_boost`

Allows the player to activate elytra gliding without wearing an elytra. Pressing jump while falling initiates flight.

No additional fields beyond `name` and `description`.

**Example:**
```json
{
  "type": "neoorigins:elytra_boost",
  "name": "Elytra Boost",
  "description": "Can glide without equipping an elytra."
}
```

---

## `neoorigins:scare_entities`

Causes listed entity types to flee from the player on sight.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_types` | list of Identifier | no | `[]` | Entity types to scare, e.g. `["minecraft:creeper"]` |
| `entity_blacklist` | string[] | no | `[]` | Entity ids (`"minecraft:elder_guardian"`) and tag refs (`"#mymod:fearless"`) this power never scares, even when they match `entity_types`. Checked on top of the built-in exclusions below. |

Boss-tier mobs (the Warden, Ender Dragon and Wither) are never scared, regardless of `entity_types`. Server operators can extend that exclusion to arbitrary mobs across all taming and scare powers at once via the `tame_scare_entity_blacklist` config list (see [Global taming/scare exclusions](#global-tamingscare-exclusions)). Excluded entities are simply skipped; no message is shown.

**Example: creepers flee from the player**
```json
{
  "type": "neoorigins:scare_entities",
  "entity_types": ["minecraft:creeper", "minecraft:spider"],
  "name": "Predator Aura",
  "description": "Hostile arthropods flee on sight."
}
```

---

## `neoorigins:tick_action`

Legacy no-op: it dispatches nothing. The type still loads and its fields still parse, so an existing pack keeps booting, but no action ever runs on the interval. Use [`neoorigins:condition_passive`](#neooriginscondition_passive) instead: it takes the same `interval` field and actually runs an `entity_action`, with an optional `condition` gating each run.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `interval` | int | no | `20` | Parsed and ignored: nothing is dispatched on the interval. |
| `action_type` | string | no | `none` | Parsed and ignored. `teleport_on_damage` is accepted by the codec but has no behaviour behind it. |

**Replacement: the same shape on `condition_passive`**
```json
{
  "type": "neoorigins:condition_passive",
  "interval": 40,
  "entity_action": {
    "type": "neoorigins:heal",
    "amount": 1.0
  },
  "name": "Slow Mend",
  "description": "Knits itself back together every two seconds."
}
```

---

## `neoorigins:conditional`

Wraps another power so it only applies when a movement condition is met. The inner power must be a separately defined power that is also listed in the origin's power list.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `condition` | string | no | `always` | `climbing`, `in_water`, `on_ground`, or `always` |
| `inner_power` | Identifier | yes | — | The power to conditionally enable |

**Example: no fall damage only while climbing**
```json
{
  "type": "neoorigins:conditional",
  "condition": "climbing",
  "inner_power": "examplepack:specter_no_fall_base",
  "name": "Spider's Grip",
  "description": "Takes no fall damage while clinging to walls."
}
```

> The `inner_power` must also be listed in the origin's `powers` array for it to be registered. The conditional wrapper activates or suppresses its effect based on the condition.

---

## `neoorigins:phantom_form`

Applies a ghostly state to the player: permanent invisibility and/or removal of gravity.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `invisibility` | bool | no | `true` | Apply permanent Invisibility effect |
| `no_gravity` | bool | no | `true` | Disable gravity (player floats) |

**Example:**
```json
{
  "type": "neoorigins:phantom_form",
  "invisibility": true,
  "no_gravity": false,
  "name": "Phantom Form",
  "description": "Becomes invisible but still subject to gravity."
}
```

---

## `neoorigins:invisibility`

Makes the player invisible. Unlike a plain invisibility status effect, this power can also hide worn armor for true invisibility (vanilla leaves armor visible on an invisible body). The invisibility rides the vanilla invisibility effect, so it is gated by the power's top-level `condition`: it applies only while the condition holds and lapses when it stops.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `render_armor` | bool | no | `true` | When `true`, worn armor stays visible (vanilla behaviour). When `false`, worn armor is also hidden client-side for true invisibility. |

**Example:**
```json
{
  "type": "neoorigins:invisibility",
  "render_armor": false,
  "name": "Vanish",
  "description": "Become completely invisible, armor and all."
}
```

This is the native type the Apoli compat layer maps `origins:invisibility` (and `apace:invisibility`) onto, carrying the `render_armor` field through.

---

## `neoorigins:prevent_entity_render`

Hides other entities from the holder. Every living entity matching `entity_condition` stops being drawn for this player only — everyone else still sees it normally, and the entity is otherwise untouched: it still moves, still attacks, and still collides. Only living entities are candidates, so boats, minecarts, item frames, dropped items and projectiles are never hidden. Pair it with a power that applies glowing to build a "you only see what you have revealed" origin.

The condition is evaluated on the server, which then tells the client which entities to skip. That is what lets verbs like `status_effect` and `nbt` work here, since a client never receives another entity's effects or full NBT.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_condition` | [condition](schema/condition.schema.json) | no | — | Tested against each nearby living entity; every one that passes becomes invisible to the holder. Omit it to hide every living entity. Because the subject is an arbitrary entity rather than the holder, only entity-general verbs apply: `entity_type`, `in_tag`, `target_group`, `living`, `on_fire`, `health`, `relative_health`, `status_effect` / `has_effect`, `nbt`, `constant`, and `and` / `or` / `not` over those. A condition using anything else is not refused — the power loads, warns, and hides nothing rather than hiding everything. Takes one condition object, or an array of them, which is read as all of them AND-combined. |

**Refresh rate and scope.** The hidden set is recomputed every 5 ticks, over the living entities within the server's view distance and no further than 256 blocks. An entity that starts or stops matching therefore appears or disappears up to a quarter of a second later. At most 1024 entities are hidden from one player at a time; past that, the remainder stay visible.

**Example: only glowing creatures are visible**
```json
{
  "type": "neoorigins:prevent_entity_render",
  "entity_condition": {
    "type": "neoorigins:and",
    "conditions": [
      { "type": "neoorigins:living" },
      { "type": "neoorigins:status_effect", "effect": "minecraft:glowing", "inverted": true }
    ]
  },
  "name": "Blind Sight",
  "description": "You see nothing but what you have marked."
}
```

This is the native type the Apoli compat layer maps `origins:prevent_entity_render` (and `apace:prevent_entity_render`) onto, carrying `entity_condition` through. Apoli's `bientity_condition` variant is not translated, because half of the pair it compares is the observer.

---

## `neoorigins:effect_immunity`

Prevents listed potion effects from being applied to the player. Uses NeoForge's `MobEffectEvent.Applicable` to cancel before application.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effects` | list of string | yes* | — | Effect IDs to block, e.g. `["minecraft:poison"]` (*optional when `inverted` is true) |
| `inverted` | bool | no | `false` | Flips the list into an exception list (Apoli semantics): immune to every effect **except** those listed. `true` with an empty list = immune to all effects. |

**Example:**
```json
{
  "type": "neoorigins:effect_immunity",
  "effects": ["minecraft:poison", "minecraft:wither"],
  "name": "Toxin Resistance",
  "description": "Immune to poison and wither effects."
}
```

**Example: immune to everything except regeneration**
```json
{
  "type": "neoorigins:effect_immunity",
  "effects": ["minecraft:regeneration"],
  "inverted": true
}
```

> Pair `inverted: true` with an empty `effects` list and a `power_condition` for a temporary blanket immunity (e.g. an immunity-shot buff gated on a resource).

---

## `neoorigins:glow`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:persistent_effect`. See [MIGRATION.md](MIGRATION.md).

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

## `neoorigins:damage_in_daylight`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:condition_passive`. See [MIGRATION.md](MIGRATION.md).

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

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:condition_passive`. See [MIGRATION.md](MIGRATION.md).

Deals periodic drown damage to the player while they are in water, and, when `include_rain` is enabled, while they are exposed to rain. Damage is applied once per second.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `damage_per_second` | float | no | `1.0` | Damage dealt each second (half-hearts). Set to `0` to disable damage entirely: the power still loads but substitutes a no-op so no hurt sound/animation fires. |
| `multiplier` | float | no | `1.0` | Scale factor applied **on top of** `damage_per_second` — the effective rate is `damage_per_second × multiplier`. Not a synonym: writing both applies both. Setting either to `0` disables the power. |
| `include_rain` | bool | no | `true` | When true, damage also applies while exposed to rain (`in_water` OR `in_rain`); when false, only while in water. |

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

## `neoorigins:knockback_modifier`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](MIGRATION.md).

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

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](MIGRATION.md).

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

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](MIGRATION.md).

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

## `neoorigins:efficient_repairs`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](MIGRATION.md).

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

## `neoorigins:better_enchanting`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](MIGRATION.md).

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

## `neoorigins:better_crafted_food`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](MIGRATION.md).

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

## `neoorigins:better_bone_meal`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](MIGRATION.md).

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

## `neoorigins:teleport_range_modifier`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](MIGRATION.md).

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

## `origins:modify_xp_gain`

Multiplies experience points gained. Wired to `PlayerXpEvent.XpChange` via the `NumericModifierRegistry`. Uses Apoli modifier math (`addition` / `multiply_base` / `multiply_total`).

> **Use `origins:modify_xp_gain`, not `neoorigins:xp_gain_modifier`.** There is no native `neoorigins:` XP-gain type; the only working implementation is this Apoli compat (Route B) verb. A power whose `type` is unregistered is dropped entirely at load (its name and description vanish from the picker too), so the `neoorigins:xp_gain_modifier` name in older docs never worked.

Takes an Apoli **modifier** (singular `modifier` object or plural `modifiers` array). Each entry is `{ "operation": ..., "value": ... }` (`value` may also be written `amount`).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `modifier` / `modifiers` | object / array | yes | — | Apoli modifier(s): `operation` (`addition`, `multiply_base`, `multiply_total`) + `value` |

**Example: gain 50% more XP**
```json
{
  "type": "origins:modify_xp_gain",
  "modifier": { "operation": "multiply_base", "value": 0.5 },
  "name": "Quick Learner",
  "description": "Gains experience faster."
}
```

---

## `origins:modify_fall_damage`

Scales incoming fall damage, optionally gated by a `condition`. This is an Apoli compat (Route B) verb; there is no native `neoorigins:` fall-damage type. It reuses the existing native fall-damage seam: the loader registers a `mod_fall_damage` modifier handler (the same hook `neoorigins:action_on_event` with `event: mod_fall_damage` uses), which chains onto the `LivingFallEvent` damage multiplier in `MovementPowerEvents.onLivingFall`. Because it chains, it stacks with feather-falling and other fall-damage modifiers.

Takes an Apoli **modifier** (singular `modifier` object or plural `modifiers` array). Each entry is `{ "operation": ..., "value": ... }` (`value` may also be written `amount`). The operation+value is applied through the same modifier math as the other compat verbs: `multiply_base_additive` with `value: -0.5` → `damage * (1 + -0.5)` (halved); `addition`/`add_base` adds a flat amount to the multiplier; `set_total` with `value: 0` hard-zeroes the multiplier (no fall damage). A `condition` that targets `fall_damage` written as a `conditioned_attribute` is auto-routed here (vanilla has no fall-damage *attribute*, so it would otherwise be dropped).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `modifier` / `modifiers` | object / array | yes | — | Apoli modifier(s): `operation` + `value` applied to the fall-damage multiplier |
| `condition` | object / array | no | always | Entity condition gating when the scale applies (e.g. only while sneaking) |

**Example: halved fall damage while sneaking**
```json
{
  "type": "origins:modify_fall_damage",
  "modifier": { "operation": "multiply_base_additive", "value": -0.5 },
  "condition": { "type": "origins:sneaking" },
  "name": "Soft Landing",
  "description": "Takes half fall damage while sneaking."
}
```

---

## `origins:modify_healing`

Scales health the holder regains. This is an Apoli compat (Route B) verb; there is no native `neoorigins:` healing-modifier type. It reuses the existing native seam: the loader registers a `mod_natural_regen` modifier handler, which `WorldPowerEvents.onLivingHeal` chains onto `LivingHealEvent`. That event fires for **all** healing, not just natural regeneration (potions, golden apples and `/heal` go through it too), which matches Apoli's contract for this type.

Takes an Apoli **modifier** (singular `modifier` object or plural `modifiers` array). This is a 1.16–1.18-vintage Origins type, so its packs use the attribute-style operation names: `addition` adds a flat amount, while `multiply_base` and `multiply_total` sum into `base + base * Σvalue`. A single `multiply_total` of `0.5` is therefore 1.5x healing, and `multiply_base` of `-0.5` is half healing.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `modifier` / `modifiers` | object / array | yes | — | Apoli modifier(s): `operation` + `value` applied to the heal amount |
| `condition` | object / array | no | always | Entity condition gating when the scale applies |

**Example: healing halved**
```json
{
  "type": "origins:modify_healing",
  "modifier": { "operation": "multiply_base", "value": -0.5 },
  "name": "Slower Regeneration",
  "description": "As a flower your healing capabilities are limited."
}
```

---

## `origins:modify_status_effect_duration`

Scales the duration of mob effects applied to the holder. Apoli compat (Route B), the duration-side sibling of `origins:modify_status_effect_amplifier`. The loader registers a `mod_potion_duration` modifier handler, which `CombatPowerEvents` chains onto `MobEffectEvent.Added`.

The seam collects a **multiplier**: its base is `1.0`, and the dispatch site applies the result to the effect instance's duration. So `multiply_total` with `value: 0.5` yields 1.5x and reads as "effects last 50% longer". Operation names are the same attribute-style set `modify_healing` uses. The scale applies to every effect added to the holder; there is no per-effect filter.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `modifier` / `modifiers` | object / array | yes | — | Apoli modifier(s) applied to the duration multiplier |
| `condition` | object / array | no | always | Entity condition gating when the scale applies |

**Example: beneficial effects last 50% longer**
```json
{
  "type": "origins:modify_status_effect_duration",
  "modifier": { "operation": "multiply_total", "value": 0.5 },
  "name": "Extended Potion Effects",
  "description": "Potion effects last longer for you."
}
```

---

## `origins:action_on_death`

Fires when the holder dies. Apoli compat (Route B), riding the native `death` event seam that `CombatPowerEvents.onLivingDeath` already dispatches.

`entity_action` runs on the dying holder. `bientity_action` runs with actor = the holder and target = the killer, Apoli's pairing for this type. The bientity half is skipped entirely when the death had no living attacker (fall damage, drowning, `/kill`) because there is no target to pair with, and running the action against the holder would invert its meaning.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_action` | object | no | — | Action run on the dying holder |
| `bientity_action` | object | no | — | Action run as (actor = holder, target = killer); skipped when there is no living killer |
| `condition` | object / array | no | always | Entity condition on the holder, tested at death time |

**Example: run a function as whoever killed you**
```json
{
  "type": "origins:action_on_death",
  "hidden": true,
  "bientity_action": {
    "type": "origins:target_action",
    "action": { "type": "origins:execute_command", "command": "function mypack:on_death" }
  }
}
```

---

## `origins:cooldown`

A readable cooldown timer. This is an Apoli compat (Route B) power; there is no native `neoorigins:` cooldown power type (native active powers carry their own `cooldown_ticks`). An Apoli cooldown power is a **countdown resource**: its value is `0` while ready and counts down from the armed duration, one per tick, while running. That makes it readable everywhere resources are: `neoorigins:resource` / `resource_level` conditions, `change_resource` / `set_resource` actions, and the HUD bar.

Arming: the [`neoorigins:trigger_cooldown`](ACTIONS.md#neooriginstrigger_cooldown) action sets the resource to the power's registered `cooldown` duration. Its `power` field may be the full power id, a legacy slash id, or a wildcard glob like `*:*_timer`; the glob is matched against the player's resource keys at runtime and every matching cooldown power is armed. The canonical Apoli pattern (inside an `origins:multiple`, where the `*:*_timer` self-reference in a *condition* resolves to the sibling timer's synthetic id at expansion): fire an ability, `trigger_cooldown` the timer, then gate the ability on the timer resource being `0`. A running countdown persists across relogs.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `cooldown` | int ticks | no | `1` | Countdown duration armed by `trigger_cooldown` (clamped to ≥ 1) |
| `hidden` | bool | no | `false` | Hide the HUD bar |
| `hud_render` | object | no | — | Same shape as the `neoorigins:resource` HUD block: `sprite_location`, `bar_index` / `icon_index` (default 0 when the block is present), `should_render` (false → hidden), `condition` (entity condition gating bar visibility) |

**Example: a 10-second ability timer, read back as a resource**
```json
{
  "type": "origins:cooldown",
  "cooldown": 200,
  "hud_render": { "should_render": false }
}
```

---

## `neoorigins:underwater_mining_speed`

Removes the normal mining speed penalty for being submerged in water. The player mines at full speed underwater.

No additional fields beyond `name` and `description`.

**Example:**
```json
{
  "type": "neoorigins:underwater_mining_speed",
  "name": "Aquatic Miner",
  "description": "Mines at full speed underwater."
}
```

---

## `neoorigins:hunger_drain_modifier`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](MIGRATION.md).

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

## `neoorigins:natural_regen_modifier`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](MIGRATION.md).

Multiplies all healing the player receives via `LivingHealEvent`. **Note:** this is *not* limited to food-tick natural regen; it also scales Regeneration potion ticks, beacon regen, totem of undying, and any data-pack `neoorigins:heal` actions. If you want to cancel food-tick regen specifically while leaving other heals intact, use `neoorigins:no_natural_regen` instead.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `multiplier` | float | no | `1.0` | Heal multiplier (`0.5` = half all heals, `2.0` = double all heals, `0.0` = block all heals) |

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

## `neoorigins:no_natural_regen`

Cancels vanilla food-based natural regeneration only. Both regen branches (saturation-based fast regen and food-level-≥18 slow regen) are skipped. Other heal sources (Regeneration potion, beacon, totem, `neoorigins:heal`) still work normally. Pair with an alternate healing mechanic for "metabolism-less" origins like Automaton variants.

No fields. Presence of the power is the entire configuration.

**Example:**
```json
{
  "type": "neoorigins:no_natural_regen",
  "name": "No Pulse",
  "description": "Hunger doesn't restore HP. Only direct healing effects do."
}
```

---

## `neoorigins:food_restriction`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](MIGRATION.md).

Restricts which foods the player can eat. Supports `blacklist` (cannot eat items matching the tag/item list) and `whitelist` (can only eat items matching). `item_tag` accepts a single string or a JSON array of tags and/or item IDs. Prefix entries with `#` for tags; bare strings match exact item IDs.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `mode` | string | no | `"blacklist"` | `"blacklist"` or `"whitelist"` |
| `item_tag` | string or array | yes | — | Single tag/item or array of tags/items. Use `#` prefix for tags (e.g. `"#minecraft:meat"`), bare strings for item IDs (e.g. `"minecraft:spider_eye"`). |

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

## `neoorigins:item_magnetism`

Items on the ground within a radius are pulled toward the player automatically.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | float | no | `4.0` | Pull radius in blocks |

**Example:**
```json
{
  "type": "neoorigins:item_magnetism",
  "radius": 4.0,
  "name": "Item Attraction",
  "description": "Items on the ground are drawn toward you."
}
```

---

## `neoorigins:break_speed_modifier`

Multiplies the player's mining speed when breaking blocks in the specified tag. When no tag is provided, applies to all blocks.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `block_tag` | Identifier | no | _(all blocks)_ | Block tag to restrict the modifier to, e.g. `minecraft:stone` |
| `multiplier` | float | no | `2.0` | Speed multiplier (`2.0` = double speed) |

**Example: 2× speed on stone and deepslate**
```json
{
  "type": "neoorigins:break_speed_modifier",
  "block_tag": "minecraft:stone",
  "multiplier": 2.0,
  "name": "Stone Affinity",
  "description": "Mines stone and deepslate twice as fast."
}
```

---

## `neoorigins:thorns_aura`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](MIGRATION.md).

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

---

## `neoorigins:projectile_immunity`

Prevents all incoming projectile damage (arrows, tridents, fireballs, etc.).

No additional fields beyond `name` and `description`.

**Example:**
```json
{
  "type": "neoorigins:projectile_immunity",
  "name": "Arrow Deflection",
  "description": "Immune to all projectile damage."
}
```

---

## `neoorigins:action_on_kill`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](MIGRATION.md).

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

## `neoorigins:action_on_hit_taken`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](MIGRATION.md).

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

This type can only act on the attacker. To buff *yourself* when hit, use [`neoorigins:action_on_event`](#neooriginsaction_on_event) with `"event": "hit_taken"` and an `entity_action`, which runs against the player.

---

## `neoorigins:action_on_hit`

Triggers an action each time the player deals damage to any living entity: mobs, animals, and other players. Optionally restricted by target entity group, target entity type, or damage type. There are two ways to express the effect: the simple flat `action` string (self or victim), or a full Apoli-style `bientity_action` (alias `entity_action`) that runs against the (attacker, victim) pair. Both may be present — the flat `action` runs first, then the parsed bientity action.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `action` | string | no | `restore_health` | One of `restore_health`, `restore_hunger`, `grant_effect` (self), `target_effect` (victim) |
| `amount` | float | no | `2.0` | Health or hunger amount (for the restore actions) |
| `effect` | Identifier | no | — | Effect to apply for `grant_effect` / `target_effect` |
| `duration` | int | no | `100` | Effect duration in ticks |
| `amplifier` | int | no | `0` | Effect level |
| `min_damage` | float | no | `0.0` | Only fires when outgoing damage ≥ this |
| `chance` | float | no | `1.0` | Probability to fire, from 0.0 to 1.0 |
| `target_group` | string | no | _(any)_ | Restrict to targets in group: `undead`, `arthropod`, `illager`, `aquatic` (vanilla `minecraft:<group>` entity-type tag) |
| `target_type` | Identifier | no | _(any)_ | Restrict to a specific entity type, e.g. `minecraft:zombie` |
| `damage_type` | string | no | _(all)_ | Restrict to a specific vanilla damage type, e.g. `mob_attack`, `magic` |
| `bientity_action` | entity action (object or array) | no | noop | Parsed action run against the pair: actor = the attacker (you), target = the entity you hit. A bare verb (e.g. `apply_effect`) runs against the **victim**; wrap in `actor_action` / `target_action` to route explicitly. See [ACTIONS.md: bientity actions](ACTIONS.md#caster--target-bientity-actions). |
| `entity_action` | entity action (object or array) | no | noop | Alias for `bientity_action`: the field name a pack copying the ACTIONS.md example uses. |

Filters are combined with AND: every configured filter must match for the action to fire. The `chance` roll happens last. The same filter gates (`min_damage`, `damage_type`, `target_group`, `target_type`, `chance`) govern the `bientity_action` too.

**Example: heal 0.5 hearts on striking any undead**
```json
{
  "type": "neoorigins:action_on_hit",
  "action": "restore_health",
  "amount": 1.0,
  "target_group": "undead",
  "name": "Smite Vigor",
  "description": "Drains life from the undead with each blow."
}
```

**Example: mark the victim with Glowing when striking undead**
```json
{
  "type": "neoorigins:action_on_hit",
  "action": "target_effect",
  "effect": "minecraft:glowing",
  "duration": 60,
  "target_group": "undead",
  "name": "Holy Mark",
  "description": "Hit undead are illuminated briefly."
}
```

**Example: 20% chance to gain Strength I on hitting a zombie, on melee only**
```json
{
  "type": "neoorigins:action_on_hit",
  "action": "grant_effect",
  "effect": "minecraft:strength",
  "duration": 60,
  "chance": 0.2,
  "damage_type": "mob_attack",
  "target_type": "minecraft:zombie",
  "name": "Adrenal Surge",
  "description": "Sometimes empowered when you melee a zombie."
}
```

**Example: poison whatever you hit (bientity form)**
```json
{
  "type": "neoorigins:action_on_hit",
  "hidden": true,
  "entity_action": { "type": "neoorigins:apply_effect", "effect": "minecraft:poison", "duration": 60 },
  "name": "Venom Strike",
  "description": "Your blows leave a lingering poison."
}
```
The bare `apply_effect` runs against the victim: hitting a mob poisons it. To affect yourself instead, wrap it in `actor_action`; to be explicit about targeting the victim, wrap it in `target_action`.

---

## `neoorigins:regen_in_fluid`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:condition_passive`. See [MIGRATION.md](MIGRATION.md).

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

## `neoorigins:breath_in_fluid`

Drains the player's air supply when their eyes are submerged in the specified fluid. Useful for fire-themed origins that "drown" in water. Surfacing (head above water) restores air normally via vanilla breathing. While submerged, fully overrides vanilla's air management (suppresses both vanilla drowning and water-breathing refill), so the configured drain rate is the sole authority on how fast air depletes. Respiration enchantment extends survival time. Drown damage (2 HP/sec) applies once air is exhausted.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `fluid` | string | no | `water` | Fluid: `water` or `lava` |
| `air_loss_per_second` | int | no | — | Intuitive form: how many air points are lost per second. Higher = faster drain. Internally converted to ticks via `20 / value`. |
| `drain_interval_ticks` | int | no | — | Ticks between each air drain. Higher = slower drain. Equivalent to the legacy `drain_rate`. |
| `drain_rate` | int | no | `20` | Legacy alias for `drain_interval_ticks`. Resolution priority: `air_loss_per_second` > `drain_interval_ticks` > `drain_rate` > default 20 (one decrement per second). |

**Example:**
```json
{
  "type": "neoorigins:breath_in_fluid",
  "fluid": "water",
  "air_loss_per_second": 1,
  "name": "Hydrophobic",
  "description": "Cannot breathe in water."
}
```

---

## `neoorigins:breath_out_of_fluid`

Drains the player's air supply while they are **not** submerged in the specified fluid: a fish out of water. Once the air supply reaches 0, vanilla drown damage applies. For the water form, rain, a bubble column and standing in a water cauldron all count as being in the fluid. Water Breathing and Conduit Power effects pause the drain, and drinking a water bottle restores half the air bar. Vanilla only grants Conduit Power to a player who is already in water or rain, which would put it out of reach of the one situation this power creates; the mod lifts that restriction for players holding this power, so an active conduit reaches them anywhere in its normal radius and the air bar refills while they stand there. Compatible with Create's Copper Backtank (worn in chestplate slot, consumes pressurized air to pause drain). Respiration enchantment extends land time using the same probability curve vanilla uses underwater. `fluid: lava` is the bare check: none of these reprieves reach it, only being in lava itself.

The drain rate is per-power. Set one of the three fields below and that power drains at your rate; omit all three and the power falls back to the global `ocean_origins.drain_rate_ticks` config, which is how the four built-in `*_dries_out` powers are written. If a player holds more than one of these powers, the tightest interval wins.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `fluid` | string | no | `water` | Fluid the player must stay in: `water` or `lava` |
| `air_loss_per_second` | int | no | config | Highest priority: air points lost per **second** (higher = faster drain) |
| `drain_interval_ticks` | int | no | config | Ticks between each 1-point drain (higher = slower drain; 20 = 1s) |
| `drain_rate` | int | no | config | Legacy alias for `drain_interval_ticks`, lowest priority |

Air starts at 300, so land time in seconds is roughly `(300 × drain_interval_ticks) / 20`: `1` gives 15s (vanilla cod parity), `2` gives 30s, `4` gives a minute.

**Count bars, not bubbles.** The interval is ticks per single air **point**, and the HUD draws 300 air as 10 bubbles, so one bubble is 30 points. At `1` a bubble lasts 1.5s and the whole bar 15s; at `10` a bubble lasts a full 15s and the bar 150s. Both figures have been reported as bugs by players reading the bubble as the unit, so it is worth checking which one you are timing. If your bar empties far slower than the table says, check `config/neoorigins/gameplay.toml`: `ModConfigSpec` writes that file once and never re-defaults it, so a world that was played before the default changed from `10` to `1` still holds the old value. As of 2.2.24 the mod corrects that one case itself on the first start and stamps the file with a `config_version`, after which the setting is yours: put it back to `10` and it stays there.

**Example: aquatic origin that drowns on land**
```json
{
  "type": "neoorigins:breath_out_of_fluid",
  "fluid": "water",
  "name": "Dries Out",
  "description": "Suffocates when not in water."
}
```

> Pair with `neoorigins:water_breathing` so the player never loses air underwater while the bubble row depletes on land.

---

## `neoorigins:biome_buff`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:condition_passive`. See [MIGRATION.md](MIGRATION.md).

Applies a status effect while the player is standing in a biome matching the given biome tag.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `biome_tag` | Identifier | yes | — | Biome tag, e.g. `minecraft:is_forest` |
| `effect` | Identifier | yes | — | Effect to apply, e.g. `minecraft:speed` |
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

## `neoorigins:damage_in_biome`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:condition_passive`. See [MIGRATION.md](MIGRATION.md).

Deals periodic damage to the player while in a matching biome. Match by tag (`biome_tag`) or by an explicit list (`biomes`); damage fires once per second (the interval is fixed at 20 ticks).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `biome_tag` | Identifier | no* | — | Biome tag to match, e.g. `minecraft:is_nether`. Use this *or* `biomes`. |
| `biomes` | array of Identifier | no* | — | Explicit biome IDs; matches if the player is in any of them. Use this *or* `biome_tag`. |
| `damage_per_second` | float | no | `1.0` | Damage applied each second |
| `damage_type` | string | no | `generic` | Damage type used for the hit |

<sub>* Provide either `biome_tag` or `biomes`. If both are omitted the condition matches no biome.</sub>

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

## `neoorigins:burn_at_health_threshold`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:condition_passive`. See [MIGRATION.md](MIGRATION.md).

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

## `neoorigins:mobs_ignore_player`

Causes specific mob types to ignore the player. By default a retaliation
window applies: once the player hits the mob, the mob may target back
briefly. Set `passive: true` to make the ignore unconditional.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_types` | list of Identifier or `#tag` | no | `[]` | Entity types that will ignore the player. Accepts raw ids (`"minecraft:zombie"`) and tag references (`"#minecraft:skeletons"`). When empty, every mob ignores. |
| `entity_blacklist` | string[] | no | `[]` | Entity ids and tag refs this power never affects; they target the player normally even when `entity_types` matches (including the empty match-all case). Checked on top of the built-in exclusions below. |
| `passive` | bool | no | `false` | When true, the ignore is unconditional: even attacking the mob does not provoke retaliation. |

Boss-tier mobs (the Warden, Ender Dragon and Wither) never ignore the player, regardless of `entity_types` (an empty match-all list won't make the Warden docile). Server operators can extend that exclusion via the `tame_scare_entity_blacklist` config list (see [Global taming/scare exclusions](#global-tamingscare-exclusions)).

**Example: only creepers ignore (with retaliation)**
```json
{
  "type": "neoorigins:mobs_ignore_player",
  "entity_types": ["minecraft:creeper"],
  "name": "Creeper Affinity",
  "description": "Creepers ignore you unless attacked."
}
```

**Example: unprovokable peace with every skeleton (tag + passive)**
```json
{
  "type": "neoorigins:mobs_ignore_player",
  "entity_types": ["#minecraft:skeletons"],
  "passive": true,
  "name": "Bonewalker",
  "description": "Skeletons never see you as a threat, no matter what you do."
}
```

> This is distinct from `scare_entities` (which makes mobs flee). `mobs_ignore_player` makes mobs neutral; `scare_entities` makes them run away.

---

## `neoorigins:mobs_target_player`

The inverse of `mobs_ignore_player`: listed mob types proactively hunt a player who has this power, the way wolves hunt a vanilla skeleton. Nothing fires when a mob is not targeting the player, so this can not be a reactive event: instead each mob is given a targeting goal on spawn that acquires the nearest matching power-holder within `range`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_types` | list of Identifier or `#tag` | no | `[]` | Entity types that hunt the player. Accepts raw ids (`"minecraft:wolf"`) and tag references (`"#minecraft:wolves"`). When empty, every mob hunts the player. |
| `entity_blacklist` | string[] | no | `[]` | Entity ids and tag refs this power never affects; they never hunt the player even when `entity_types` matches (including the empty match-all case). Checked on top of the built-in exclusions below. |
| `range` | double | no | `16.0` | How far (in blocks) a matching mob can be and still start hunting the player. |

Boss-tier mobs (the Warden, Ender Dragon and Wither) never hunt the player, regardless of `entity_types`. Server operators can extend that exclusion via the `tame_scare_entity_blacklist` config list (see [Global taming/scare exclusions](#global-tamingscare-exclusions)).

**Example: a skeleton-type origin that wolves hunt**
```json
{
  "type": "neoorigins:mobs_target_player",
  "entity_types": ["minecraft:wolf"],
  "range": 20.0,
  "name": "Bonewalker's Curse",
  "description": "Wolves hunt you the way they hunt skeletons."
}
```

> Pairs naturally with `mobs_ignore_player`: use `mobs_ignore_player` for the mobs that leave a skeleton alone, and `mobs_target_player` for the wolves that chase it.

---

## `neoorigins:no_mob_spawns_nearby`

Suppresses mob spawns within a radius of the player. Toggleable: the player can turn the aura on/off via their skill keybind.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | int | no | `24` | Radius in blocks within which spawns are suppressed. Note: vanilla already blocks `MONSTER`-category natural spawns within 24 blocks of any player (`NaturalSpawner`); use a value above 24 to extend the safe zone meaningfully. |
| `categories` | list of string | no | `["monster"]` | Spawn categories to suppress: `monster`, `creature`, `ambient`, `water_creature`, or `all` |

**Example: suppress hostile spawns in a 36-block radius**
```json
{
  "type": "neoorigins:no_mob_spawns_nearby",
  "radius": 36,
  "categories": ["monster"],
  "name": "Warding Presence",
  "description": "Hostile mobs don't spawn within 36 blocks. Toggle with skill key."
}
```

---

## `neoorigins:entity_group`

Treats the player as a member of a mob group, so game mechanics that key off a creature's type act on the player. A player can't be added to vanilla's real entity-type tags, so membership is simulated by intercepting the relevant hooks: effect immunity, instant heal/harm inversion, enchant vulnerability, and mob targeting.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `group` | string | no | `undefined` | Group id. A bare name (no namespace) resolves to `neoorigins:<name>`. `undefined` means no classification. |

**Example:**
```json
{
  "type": "neoorigins:entity_group",
  "group": "undead",
  "name": "Undead Nature",
  "description": "Treated as an undead creature — immune to poison, healed by harm."
}
```

### Built-in groups

These are registered in code and work with zero setup:

| Group | Effect |
|---|---|
| `neoorigins:undead` | Immune to Poison and Regeneration; Instant Health harms and Instant Damage heals; takes extra damage from Smite. |
| `neoorigins:arthropod` | Takes extra damage from Bane of Arthropods (plus the vanilla slowness-on-hit). |
| `neoorigins:water` | Takes extra damage from Impaling. |
| `neoorigins:illager` | Raiders (anything in `#minecraft:raiders`) won't target the player; village-spawned iron golems hunt the player (player-built golems stay loyal); villagers and wandering traders flee the player. |
| `neoorigins:piglin` | Piglins and piglin brutes never target the player. Unlike vanilla this is unconditional: no gold armor required. |
| `neoorigins:skeleton` | The `undead` kit (Poison/Regeneration immunity, inverted instant effects, Smite vulnerability) plus wolves hunt the player the way they hunt vanilla skeletons, and the player burns in daylight like a vanilla skeleton (honours the same `[sun_damage]` helmet/umbrella rules as `exposed_to_sun`). |

Referencing an unknown group id (one with no built-in default and no datapack file) logs a one-time warning and does nothing, rather than silently no-op-ing.

### Custom groups (datapack)

A datapack can mint new groups, or override a built-in, with a JSON file at `data/<namespace>/neoorigins/entity_groups/<name>.json`. A file whose id matches a built-in (e.g. `data/neoorigins/neoorigins/entity_groups/undead.json`) replaces that built-in. Every field is optional:

| Field | Type | Default | Effect |
|---|---|---|---|
| `immune_effects` | list of effect ids | `[]` | These status effects can't apply to the player. |
| `invert_instant_effects` | bool | `false` | Instant Health harms and Instant Damage heals (undead behaviour). |
| `vulnerable_enchants` | list of enchant ids | `[]` | For each listed enchant, the attacker's weapon level adds bonus damage (`level × 2.5` per enchant, summed). Listing `minecraft:bane_of_arthropods` also applies the vanilla slowness-on-hit. |
| `ignored_by` | list of entity ids and/or `#tags` | `[]` | Mobs matching these entries never target the player. |
| `targeted_by` | list of entity ids and/or `#tags` | `[]` | Mobs matching these entries proactively hunt the player, within ~16 blocks. Player-built iron golems are exempt even when `minecraft:iron_golem` is listed; only village-spawned golems hunt. |
| `feared_by` | list of entity ids and/or `#tags` | `[]` | Mobs matching these entries flee the player, within ~8 blocks. |
| `burns_in_sunlight` | bool | `false` | The player catches fire in daylight like a vanilla skeleton, honouring the same `[sun_damage]` config (helmet protection, `neoorigins:sun_permeable` helmets, umbrella) as the `exposed_to_sun` condition. |

**Example: `data/mypack/neoorigins/entity_groups/cursed.json`**
```json
{
  "immune_effects": ["minecraft:poison", "minecraft:wither"],
  "invert_instant_effects": true,
  "vulnerable_enchants": ["minecraft:smite"],
  "ignored_by": ["#minecraft:raiders", "minecraft:zombie"],
  "targeted_by": ["minecraft:iron_golem"],
  "feared_by": ["minecraft:villager", "minecraft:wandering_trader"]
}
```

An origin then opts in with `{"type": "neoorigins:entity_group", "group": "mypack:cursed"}`.

---

## `neoorigins:active_teleport`

Active ability that teleports the player to the block they are looking at, up to a maximum distance. Plays enderman teleport sound and portal particles at departure and arrival.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `range` | float | no | `32.0` | Maximum teleport range in blocks |
| `cooldown_ticks` | int | no | `60` | Cooldown in ticks after each use |
| `hunger_cost` | int | no | `0` | Food points removed per use |

**Example:**
```json
{
  "type": "neoorigins:active_teleport",
  "range": 32.0,
  "cooldown_ticks": 60,
  "hunger_cost": 2,
  "name": "Blink",
  "description": "Teleports to where you're looking."
}
```

---

## `neoorigins:active_dash`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:active_ability`. See [MIGRATION.md](MIGRATION.md).

Active ability that launches the player in their look direction.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` | float | no | `1.5` | Launch velocity |
| `cooldown_ticks` | int | no | `40` | Cooldown in ticks |
| `allow_vertical` | bool | no | `false` | Whether to include vertical component from look direction |
| `damage` | float | no | `0` | Flat damage dealt to entities along the dash path. The damage sweep runs when either this or `weapon_damage_scale` is above `0` |
| `damage_radius` | float | no | `2.0` | Radius of the capsule swept along the dash path |
| `weapon_damage_scale` | float | no | `0` | Adds a fraction of the held weapon's attack damage on top of `damage` (e.g. `1.0` = full weapon damage) |
| `condition` | condition | no | always true | EntityCondition gating the dash. While it fails the keypress does nothing and no cooldown is spent |

**Example:**
```json
{
  "type": "neoorigins:active_dash",
  "power": 1.5,
  "cooldown_ticks": 40,
  "allow_vertical": true,
  "name": "Pounce",
  "description": "Dashes in the direction you're looking."
}
```

---

## `neoorigins:active_launch`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:active_ability`. See [MIGRATION.md](MIGRATION.md).

Active ability that launches the player straight upward. Useful paired with `elytra_boost` or `flight` for vertical take-off.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` | float | no | `1.5` | Upward launch velocity |
| `cooldown_ticks` | int | no | `60` | Cooldown in ticks |

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

## `neoorigins:healing_mist`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:active_ability`. See [MIGRATION.md](MIGRATION.md).

Cooldown-gated active ability that heals nearby players in a sphere around the caster. The AoE is players-only, matching the legacy filter.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `heal_amount` | float | no | `6.0` | Health restored to each affected player (half-hearts) |
| `radius` | float | no | `8.0` | Radius of the heal sphere (blocks) |
| `heal_self` | bool | no | `true` | When true the caster is also healed; when false only other players in range are healed |

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

## `neoorigins:repulse`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:active_ability`. See [MIGRATION.md](MIGRATION.md).

Cooldown-gated active ability that pushes nearby entities outward from the caster (including other players).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | float | no | `6.0` | Radius of the push (blocks) |
| `strength` | float | no | `1.0` | Outward push strength applied to each entity |

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

## `neoorigins:active_recall`

Active ability that teleports the player to their bed or respawn point. Falls back to world spawn if none is set.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `cooldown_ticks` | int | no | `600` | Cooldown in ticks |

**Example:**
```json
{
  "type": "neoorigins:active_recall",
  "cooldown_ticks": 600,
  "name": "Home Recall",
  "description": "Teleports to your respawn point."
}
```

---

## `neoorigins:active_swap`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:active_ability`. See [MIGRATION.md](MIGRATION.md).

Active ability that swaps positions with the entity the player is looking at.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `range` | float | no | `20.0` | Maximum range to target an entity |
| `cooldown_ticks` | int | no | `80` | Cooldown in ticks |

**Example:**
```json
{
  "type": "neoorigins:active_swap",
  "range": 16.0,
  "cooldown_ticks": 80,
  "name": "Swap",
  "description": "Swap positions with your target."
}
```

---

## `neoorigins:active_fireball`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:active_ability`. See [MIGRATION.md](MIGRATION.md).

Active ability that shoots a small fireball in the player's look direction. The fireball explodes on impact and ignites the area.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `speed` | float | no | `1.5` | Projectile speed multiplier |
| `cooldown_ticks` | int | no | `100` | Cooldown in ticks |

**Example:**
```json
{
  "type": "neoorigins:active_fireball",
  "speed": 1.5,
  "cooldown_ticks": 80,
  "name": "Ember Shot",
  "description": "Spits a fireball in your look direction."
}
```

---

## `neoorigins:crop_growth_accelerator`

Passively accelerates the growth of nearby crops by randomly applying a bonemeal tick to eligible blocks at a set interval.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | int | no | `4` | Scan radius in blocks |
| `tick_interval` | int | no | `40` | Ticks between growth attempts |
| `growths_per_interval` | int | no | `1` | Number of crops to accelerate per interval |

**Example:**
```json
{
  "type": "neoorigins:crop_growth_accelerator",
  "radius": 4,
  "tick_interval": 40,
  "growths_per_interval": 2,
  "name": "Verdant Touch",
  "description": "Crops nearby grow faster."
}
```

---

## `neoorigins:active_aoe_effect`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:active_ability`. See [MIGRATION.md](MIGRATION.md).

Active ability that applies a mob effect to all living entities within a radius of the player.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effect` | Identifier | yes | — | Effect to apply, e.g. `minecraft:slowness` |
| `amplifier` | int | no | `0` | Effect level |
| `duration_ticks` | int | no | `100` | Duration of the applied effect |
| `radius` | float | no | `8.0` | Range in blocks |
| `cooldown_ticks` | int | no | `60` | Cooldown in ticks |

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

## `neoorigins:active_phase`

Active ability that phases the player through a solid wall in their look direction. Scans forward for an air gap on the far side of a wall and teleports there. An optional hunger cost is deducted per use.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `max_depth` | int | no | `16` | Maximum solid-block scan depth in blocks |
| `cooldown_ticks` | int | no | `40` | Cooldown in ticks |
| `hunger_cost` | int | no | `0` | Food points removed per use (2 = 1 shank) |

**Example:**
```json
{
  "type": "neoorigins:active_phase",
  "max_depth": 10,
  "cooldown_ticks": 80,
  "hunger_cost": 3,
  "name": "Phase Step",
  "description": "Pass through walls at the cost of hunger."
}
```

---

## `neoorigins:active_bolt`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:active_ability`. See [MIGRATION.md](MIGRATION.md).

Active ability that shoots a dragon-fire bolt (purple ender-breath projectile) in the player's look direction. On impact, creates an area of dragon's breath that deals damage over time.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `speed` | float | no | `1.2` | Projectile speed multiplier |
| `cooldown_ticks` | int | no | `80` | Cooldown in ticks |

**Example:**
```json
{
  "type": "neoorigins:active_bolt",
  "speed": 1.2,
  "cooldown_ticks": 80,
  "name": "Void Bolt",
  "description": "Fires a bolt of corrosive dragon's breath."
}
```

---

## `neoorigins:starting_equipment`

Grants the player one or more items (optionally with enchantments) once on first origin assignment. Tracks grant state per-player so the items are not re-granted on respawn.

There are two shapes. The **singular** shape sets `item` at the root and grants one stack. The **plural** shape sets `stacks` and grants several. Pick one: they do not combine. See [Singular vs plural](#singular-vs-plural) below.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `grant_id` | string | yes | — | Unique ID tracked so the bundle is granted only once per player. Dedups the whole power, not individual stacks. |
| `stacks` | list | no | `[]` | Plural shape: array of stack entries, each taking the same per-stack fields as the singular shape (`item`, `count`, `enchantments`, `legacy_tag`, `components`). Granted in array order. |
| `item` | Identifier | no | `""` | Singular shape: item to grant, e.g. `minecraft:trident`. Optional only when `stacks` is provided; one of the two must be present. |
| `count` | int | no | `1` | Singular shape: stack count |
| `enchantments` | list | no | `[]` | Singular shape: list of `{"id": "...", "level": N}` enchantment entries |
| `legacy_tag` | string | no | `""` | SNBT string for legacy NBT data (Potion, display.Name, etc.). Recognised keys are translated to data components; unrecognised keys go to `minecraft:custom_data`. |
| `components` | string | no | `""` | SNBT string representing a `DataComponentPatch`. Supports any registered data component (vanilla or modded). Parsed with registry context at grant time. |

**Example: enchanted trident**
```json
{
  "type": "neoorigins:starting_equipment",
  "grant_id": "abyssal_trident",
  "item": "minecraft:trident",
  "enchantments": [
    {"id": "minecraft:mending", "level": 1},
    {"id": "minecraft:unbreaking", "level": 3},
    {"id": "minecraft:riptide", "level": 3}
  ],
  "name": "Deep-Sea Armament",
  "description": "Begins life with an enchanted trident."
}
```

**Example: modded item with custom data components (Iron's Spellbooks)**
```json
{
  "type": "neoorigins:starting_equipment",
  "grant_id": "mage_spellbook",
  "item": "irons_spellbooks:iron_spell_book",
  "components": "{\"irons_spellbooks:spell_container\":{data:[{id:\"irons_spellbooks:firebolt\",index:1,level:1}],maxSpells:5,mustEquip:1b,spellWheel:1b}}",
  "name": "Arcane Tome",
  "description": "Begins life with a pre-inscribed spell book."
}
```

**Example: a multi-item starting kit**
```json
{
  "type": "neoorigins:starting_equipment",
  "grant_id": "ranger_kit",
  "stacks": [
    {
      "item": "minecraft:bow",
      "enchantments": [{"id": "minecraft:power", "level": 2}]
    },
    {"item": "minecraft:arrow", "count": 32},
    {"item": "minecraft:leather_boots"}
  ],
  "name": "Ranger's Kit",
  "description": "Begins life with a bow, arrows and boots."
}
```

### Singular vs plural

`stacks` does not add to the root `item` — it **replaces** it. When `stacks` is non-empty the root `item`, `count`, `enchantments`, `legacy_tag` and `components` are all ignored, so a power declaring both silently drops the root item. Put every item in `stacks`, or use the singular shape alone.

One `grant_id` covers the whole bundle. Two consequences worth planning around:

- The bundle is marked granted if **at least one** stack went in. If one item id is a registry miss — a typo, or a mod that is not installed — the rest still arrive and the bundle is sealed, so correcting the id later does not deliver the missing item to players who already have the bundle. Give a genuinely optional item its own power with its own `grant_id`.
- If **every** id fails, the bundle is deliberately left un-deduped, so fixing the typo and running `/reload` retries it.

A stack with a blank `item` is skipped with a warning rather than failing the power, so an unfilled row left behind in the in-game editor is tolerated.

---

## `neoorigins:active_place_block`

Active ability that places a specific block at the surface the player is looking at, up to a maximum range.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `block_id` | Identifier | no | `minecraft:glowstone` | Block to place |
| `max_distance` | float | no | `5.0` | Maximum targeting range in blocks |
| `cooldown_ticks` | int | no | `100` | Cooldown in ticks |
| `hunger_cost` | int | no | `0` | Food points removed per use |

**Example:**
```json
{
  "type": "neoorigins:active_place_block",
  "block_id": "minecraft:glowstone",
  "max_distance": 5.0,
  "cooldown_ticks": 100,
  "name": "Stone Touch",
  "description": "Places a glowstone block where you're looking."
}
```

---

## `neoorigins:crop_harvest_bonus`

Passively grants extra item drops when the player harvests a fully-grown crop or breaks a log. The bonus drops are identical copies of the block's normal drops.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `extra_drops` | int | no | `1` | Number of extra drop copies per break |

**Example:**
```json
{
  "type": "neoorigins:crop_harvest_bonus",
  "extra_drops": 1,
  "name": "Bountiful Harvest",
  "description": "Crops and trees yield extra resources."
}
```

---

## `neoorigins:shadow_orb`

Active ability that places a persistent shadow orb at the player's position. Each orb applies Darkness to all nearby entities at a set interval. The player can maintain up to `max_orbs` orbs at once; placing a new one when at the cap removes the oldest. Orbs are cleared when the origin is revoked.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `max_orbs` | int | no | `4` | Maximum number of simultaneous orbs |
| `radius` | float | no | `28.0` | Effect radius per orb in blocks |
| `cooldown_ticks` | int | no | `100` | Cooldown between placements |
| `tick_interval` | int | no | `20` | Ticks between Darkness pulses per orb |
| `hunger_cost` | int | no | `0` | Food points removed per placement |

**Example:**
```json
{
  "type": "neoorigins:shadow_orb",
  "max_orbs": 4,
  "radius": 28.0,
  "cooldown_ticks": 100,
  "tick_interval": 20,
  "name": "Shadow Anchor",
  "description": "Places orbs that shroud the area in darkness."
}
```

---

## `neoorigins:persistent_effect`

Generic condition-gated, toggleable status-effect stack. Part of the 2.0 consolidation: replaces `status_effect`, `stacking_status_effects`, `night_vision`, `glow`, `water_breathing`, `breath_in_fluid`, and the tag branch of `regen_in_fluid` with one type that applies an arbitrary list of mob effects whenever an optional `condition` (an EntityCondition DSL tree) is met.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effects` | list of EffectSpec | yes | — | Mob effects to apply. See below. May also be a single inline EffectSpec on the top-level object. |
| `condition` | EntityCondition | no | always-true | DSL condition: effects only apply while it is true. Effects are cleared when it becomes false. |
| `toggleable` | bool | no | `true` | When true, this is an active-keybind power: pressing the key toggles effects on/off. When false, effects are always applied while condition is true. |
| `default_off` | bool | no | `false` | Toggleable powers only: when true, the power starts disabled, so effects stay off until the player first toggles it on. |
| `amplifier` | int | no | — | Root-level override for the **first** effect's amplifier (lets server admins retune strength without editing the `effects` array). Per-effect amplifiers on later specs are unchanged. |

**`EffectSpec` object:**

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effect` | Identifier | yes | — | Effect ID, e.g. `minecraft:strength` (alias `id` also accepted) |
| `amplifier` | int | no | `0` | Effect level |
| `ambient` | bool | no | `true` | Ambient particles (low visibility) |
| `show_particles` | bool | no | `false` | Whether to show particles |
| `show_icon` | bool | no | `true` | Whether to show the HUD icon |

> **Root cascade:** `show_icon`, `show_particles`, and `ambient` placed at the power root (alongside `type`/`effects`) cascade as defaults onto every nested `EffectSpec` that does not declare its own value. This lets you set HUD/particle visibility once for the whole stack instead of repeating it per effect.

> **`minecraft:night_vision` is special-cased.** A spec granting night vision is
> skipped while either the `disable_night_vision` server config is set or the
> player has switched night vision off with the dedicated **Toggle Night Vision**
> keybind (default `K`). The gate is per effect, not per power, so a stack of
> water breathing + night vision + haste keeps applying the other two. Nothing is
> needed in the JSON for this. See [`neoorigins:night_vision`](#neooriginsnight_vision).

**Example: permanent always-on Weakness II**
```json
{
  "type": "neoorigins:persistent_effect",
  "effect": "minecraft:weakness",
  "amplifier": 1,
  "toggleable": false,
  "name": "Frail",
  "description": "Permanently weakened."
}
```

**Example: Strength + Haste while in water (conditional, not toggleable)**
```json
{
  "type": "neoorigins:persistent_effect",
  "toggleable": false,
  "condition": { "type": "neoorigins:in_water" },
  "effects": [
    { "effect": "minecraft:strength", "amplifier": 0 },
    { "effect": "minecraft:haste",    "amplifier": 1 }
  ],
  "name": "Tidecaller",
  "description": "Empowered while immersed in water."
}
```

Effects are applied with **infinite duration** (`MobEffectInstance.INFINITE_DURATION`), so they never tick down or expire on their own. They are cleared when the `condition` becomes false, when the power is toggled off, or when the origin is revoked, and re-applied when the condition becomes true again. When `toggleable` is true (the default), the player can press their skill key to toggle effects on/off. Set `toggleable: false` for effects that should always be active, or `default_off: true` to start a toggleable power in the off state. A single effect can be specified inline on the top-level object (`effect`, `amplifier`, etc.) instead of using the `effects` list.

---

## `neoorigins:modify_food_nutrition`

Overrides the nutrition (hunger) value of food the player eats. Matching food gives exactly the configured number of hunger points regardless of its original value. Saturation is scaled proportionally. Use `food_item` or `food_tag` to filter which foods are affected; if neither is set, ALL food is affected.

The override is applied at the moment of eating rather than written onto the item, so the item's own food data is never changed. That is invisible in play but it matters to anything that reads the stack: with AppleSkin installed, NeoOrigins feeds the adjusted figures to AppleSkin's tooltip and held-food preview so they agree with what eating will actually give. See [COMPATIBILITY.md](COMPATIBILITY.md). Other mods that read `DataComponents.FOOD` off the stack directly will still see the vanilla value.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `nutrition` | int | no | `1` | Fixed hunger points matching food gives |
| `food_item` | Identifier | no | — | Only affect this specific item (e.g. `minecraft:sweet_berries`) |
| `food_tag` | string | no | — | Only affect items in this tag (e.g. `#minecraft:meat`). Supports `#` prefix. |

**Example: all food gives 1 hunger point**
```json
{
  "type": "neoorigins:modify_food_nutrition",
  "nutrition": 1,
  "name": "Picky Eater",
  "description": "Gains almost no nutrition from food."
}
```

**Example: meat gives 8 hunger points**
```json
{
  "type": "neoorigins:modify_food_nutrition",
  "nutrition": 8,
  "food_tag": "#minecraft:meat",
  "name": "Carnivore",
  "description": "Thrives on meat."
}
```

---

## `neoorigins:condition_passive`

Generic condition-gated periodic action: "a passive with a trigger". Part of the 2.0 consolidation: collapses `biome_buff`, `damage_in_biome`, `damage_in_daylight`, `damage_in_water`, `burn_at_health_threshold`, `mobs_ignore_player`, `no_mob_spawns_nearby`, and `item_magnetism` into a single type. Also supersedes `tick_action` when `condition` is omitted.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `interval` | int | no | `20` | Tick interval between evaluations (clamped to ≥ 1) |
| `condition` | EntityCondition | no | always-true | DSL condition to test each interval |
| `entity_action` | EntityAction | no | noop | Action run against the player when condition is true |
| `else_action` | EntityAction | no | noop | Action run when condition is false |
| `toggleable` | bool | no | `false` | When true, binds a skill keybind that flips the periodic action on/off (chat feedback via `neoorigins.toggle.on`/`off`). While off the interval action never runs. |
| `default_off` | bool | no | `false` | Toggleable powers only: when true the power starts disabled so the player opts in via the keybind. |

See [EVENTS.md](EVENTS.md) / the Apoli compat docs for the full condition and action DSL.

**Example: take 1 damage every 2 seconds in the Nether**
```json
{
  "type": "neoorigins:condition_passive",
  "interval": 40,
  "condition": {
    "type": "neoorigins:in_tag",
    "tag": "minecraft:is_nether"
  },
  "entity_action": {
    "type": "neoorigins:damage",
    "amount": 1.0,
    "source": "fire"
  },
  "name": "Nether Intolerance",
  "description": "Burns while in the Nether."
}
```

---

## `neoorigins:effect_over_time`

Sustained **aura** type: one `entity_action` tree pulsed on an `interval`, in one of two modes via the `activation` field. An aura is a constant effect (usually an `origins:area_of_effect` radiating from the holder); a **passive** aura is always on with no cost, while an **active** aura is keybind-toggled and drains an upkeep each interval to stay up, switching itself off when you can't pay. A passive aura can also be made **toggleable** (`toggleable: true`), still free, but with an on/off keybind. Active auras start **off** by default (opt-in); toggleable passives start **on**. (For one-shot, cooldown-gated abilities use [`active_ability`](#neooriginsactive_ability) instead; this type is specifically for auras.)

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `activation` | enum | no | `passive` | `passive` \| `active` (`toggle` accepted as an alias for `active`). `passive` pulses `entity_action` every `interval` while granted, with no upkeep. `active` binds a keybind that toggles the aura on/off; while on it pulses every interval AND pays the upkeep. Unrecognised values fall back to `passive`. |
| `toggleable` | bool | no | `false` | Passive mode only: add a free on/off keybind (no upkeep). A toggleable passive starts **on**. Active auras are always toggleable and start **off**; this flag is ignored for them. |
| `interval` | int | no | `20` | Ticks between aura pulses and, in active mode, between upkeep charges (clamped ≥ 1). |
| `condition` | EntityCondition | no | always-true | Gate: `entity_action` pulses each interval only while it passes, else `else_action`. In active mode the upkeep is still charged while the aura is on, regardless of this gate. |
| `entity_action` | EntityAction | no | noop | Action pulsed on the player each interval, typically an `origins:area_of_effect` applying damage/effects to nearby entities. Inside an `area_of_effect`, `damage`, `heal`, `apply_effect`, `clear_effect` and `spawn_particles` run on **each affected entity**, so you can paint custom particles (and apply/strip status effects) on everyone the aura touches. |
| `else_action` | EntityAction | no | noop | Action pulsed each interval while `condition` is false. |
| `hunger_cost` | int | no | `0` | Active upkeep: food points drained each interval; the aura auto-disables when the holder can't pay. `0` = a free on/off toggle. Ignored in passive mode. |
| `resource_cost` | string | no | `""` | Active upkeep: resource power id drained each interval; falls back to `hunger_cost` when resource bars are globally disabled. Ignored in passive mode. |
| `resource_cost_amount` | int | no | `0` | Active upkeep: amount of `resource_cost` drained each interval. |
| `default_off` | bool | no | mode-dependent | Override the initial on/off state of a toggleable aura. Default: active → **off**, toggleable passive → **on**. Ignored for plain (non-toggleable) passive auras. |

See [EVENTS.md](EVENTS.md) / the Apoli compat docs for the full condition and action DSL.

> **Custom particles & status effects on affected entities.** Put `spawn_particles` (and `apply_effect`/`damage`/`heal`/`clear_effect`) *inside* the `area_of_effect`'s `entity_action` to apply them to each entity the aura hits, at that entity's position. Put `spawn_particles` *outside* (a sibling of the `area_of_effect`) to draw on the holder instead. To apply a potion effect without its swirling vanilla particles, use `apply_effect` with `"show_particles": false`.

> **Filtering who the aura affects.** Set the `area_of_effect`'s `entity_condition` to control which entities are touched. Entity-general conditions filter **mobs and players** alike: `entity_type: minecraft:player` = players only; `entity_type: "#minecraft:skeletons"` = a tag group; `target_group` = a vanilla mob category; `health`/`relative_health`/`has_effect`/`on_fire`/`living`; and `and`/`or`/`not` of those (plus Apoli's `inverted: true`). For example, wrap `not { entity_type: minecraft:player }` to scorch hostile mobs without burning your allies. See [`area_of_effect`](ACTIONS.md#neooriginsarea_of_effect) for the full list.

**Example (active damage aura): toggle it on to scorch nearby mobs every second, draining 2 hunger per second to keep it up. The flame particles spawn on each burned entity (inside the `area_of_effect`), not on you**
```json
{
  "type": "neoorigins:effect_over_time",
  "activation": "active",
  "interval": 20,
  "hunger_cost": 2,
  "entity_action": {
    "type": "origins:area_of_effect",
    "radius": 6,
    "shape": "sphere",
    "include_source": false,
    "entity_action": {
      "type": "origins:and",
      "actions": [
        {
          "type": "origins:damage",
          "amount": 3.0,
          "damage_type": "minecraft:on_fire"
        },
        {
          "type": "origins:spawn_particles",
          "particle": "minecraft:flame",
          "count": 12,
          "speed": 0.02,
          "offset_y": 1.0,
          "spread": { "x": 0.4, "y": 0.6, "z": 0.4 }
        }
      ]
    }
  },
  "cooldown_icon": "minecraft:blaze_powder",
  "name": "Searing Aura",
  "description": "A ring of flame that burns nearby foes while it drains your stamina."
}
```

For a **plain passive** aura (always on, no cost, e.g. a constant self-healing field), set `"activation": "passive"` and drop the cost fields. For a **toggleable passive** (free, but with an on/off keybind, on by default), add `"toggleable": true`.

---

## `neoorigins:action_on_event`

The 2.0 generic event hook: fires an action and/or applies a float modifier when a named player event occurs. Replaces 20+ bespoke Origins-Classes hook powers (`better_enchanting`, `more_smoker_xp`, `crop_harvest_bonus`, `break_speed_modifier`, `natural_regen_modifier`, `action_on_kill`, `action_on_hit_taken`, `thorns_aura`, `knockback_modifier`, `hunger_drain_modifier`, `food_restriction`, …) with one configurable type.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `event` | string | yes | — | Event name (case-insensitive). See [EVENTS.md](EVENTS.md) for the full list. |
| `condition` | EntityCondition | no | always-true | DSL gate: the event only fires when this is true |
| `entity_action` | EntityAction | no | noop | Side-effect run when the event fires |
| `modifier` | FloatModifier or list | no | identity | Float modifier applied to the event's numeric payload (for modifier-style events) |
| `block_condition` | BlockCondition | no | — | Block-position gate for block events (`block_break`, `block_place`, `block_use`, `bonemeal`). Supports `block`/`id`, `in_tag`, `and`/`or` (aliases `all_of`/`any_of`), `block_state`, `height`, `adjacent`, and the positional `offset` wrapper (see below). Ignored on other events. |
| `hands` | string or list | no | — | Hand gate for interaction events (`block_use`, `entity_use`, `villager_interact`): only fire for the listed hands, `"main_hand"` and/or `"off_hand"`. Use `["main_hand"]` to stop a power double-firing (vanilla dispatches the right-click once per hand). Fails closed when the event carries no hand info; ignored on other events. |
| `hand` | string or list | no | — | Singular alias for `hands`, read only when `hands` is absent (Apoli's `action_on_block_use` spelling). |
| `item_condition` | ItemCondition | no | — | Item gate for item-carrying events (`item_use`, `item_use_finish`, `block_use`, `entity_use`, `villager_interact`): only fire when the stack the event carries matches. That is the used stack for the first two and the stack in the interacting hand for the rest, which is what Apoli's `item_condition` on `action_on_block_use` / `action_on_entity_use` translates to. Takes the same Apoli item-condition shapes as `equipped_item` (`id`/`tag`/`nbt`/`enchantment`, with `and`/`or`/`not`). Fails closed when set but the event carries no item; ignored on events that never carry one. |
| `effect` | id | no | — | `effect_applied` only: pre-dispatch filter on this exact effect id. |
| `effect_tag` | tag id | no | — | `effect_applied` only: pre-dispatch filter on this effect tag (leading `#` optional). OR-matched with `effect`. |
| `immunity_ticks` | int ≥ 0 | no | 0 | `effect_applied` only: after a successful cancel, grant this many ticks of full immunity to the same effect id before re-rolling. |
| `power` | id or list | no | — | `power_activated` only: pre-dispatch filter on the activated power's id (single id or array). Omit to fire on any activation. |
| `cooldown_ticks` | int ≥ 0 | no | 0 | After `entity_action` fires, suppress further firings of this power for this many ticks (20 = 1s). Tracked per player per power instance and persisted across respawn/relog. Only gates the action path: `modifier` chains are unaffected. |

**The `offset` block-condition wrapper.** Inside `block_condition`, `{ "type": "neoorigins:offset", "x": 0, "y": -2, "z": 0, "condition": { ... } }` evaluates its nested block condition at the event's block position shifted by the given block offsets, the Apoli structural wrapper for checks like "a basin two blocks below the block I clicked". Without a nested `condition` it matches all blocks (warned at load). The same wrapper works in `block_collision` and the `area_of_effect` block fan-out.

**Block-condition types.** Every field documented as a *BlockCondition* (`block_condition` here, in `block_collision`, `in_block`, `in_block_anywhere`, `near_block`, `on_block`, `prevent_sleep` and the `area_of_effect` block fan-out) accepts the same set of nested types:

| Type | Fields | Matches when |
|---|---|---|
| `block` | `block` (or legacy `id`) | The block is exactly that id. |
| `in_tag` | `tag` | The block is in that block tag (leading `#` optional). |
| `and` / `all_of` / `or` / `any_of` | `conditions` | All / at least one nested condition matches. |
| `offset` | `x`, `y`, `z`, `condition` | The nested condition matches at the shifted position. |
| `block_state` | `property`, plus `value`/`enum` or `comparison`+`compare_to` | The block carries that blockstate property and its value matches. A block without the property never matches. |
| `height` | `comparison` (default `>=`), `compare_to` | The *block's* own Y level compares true; `{ "comparison": "<=", "compare_to": 63 }` is "at or below sea level". |
| `adjacent` | `adjacent_condition`, `comparison` (default `>=`), `compare_to` (default 1) | The number of the six face-neighbours satisfying `adjacent_condition` compares true. |

Any node also honours `"inverted": true`, which negates just that node.

```json
{ "type": "neoorigins:adjacent",
  "adjacent_condition": { "type": "neoorigins:in_tag", "tag": "minecraft:ice" },
  "comparison": "<=",
  "compare_to": 2 }
```

```json
{ "type": "neoorigins:block_state", "property": "waterlogged", "value": true }
```

**Event categories (see [EVENTS.md](EVENTS.md) for the full list):**

- Lifecycle: `GAINED`, `LOST`, `CHOSEN`, `POWER_ACTIVATED`, `RESPAWN`, `DEATH`, `DIMENSION_CHANGE`, `ADVANCEMENT_EARNED`
- Combat: `ATTACK`, `HIT_TAKEN`, `KILL`, `PROJECTILE_HIT`, `MOD_KNOCKBACK`
- Food: `FOOD_EATEN`, `FOOD_FINISHED`, `MOD_EXHAUSTION`, `MOD_NATURAL_REGEN`, `MOD_CRAFTED_FOOD_SATURATION`
- Mining / blocks: `BLOCK_BREAK`, `BLOCK_PLACE`, `BLOCK_USE`, `BONEMEAL`, `MOD_HARVEST_DROPS`, `MOD_BONEMEAL_EXTRA`
- Crafting / stations: `CRAFT_ITEM`, `SMELT_ITEM`, `ENCHANT_ITEM`, `ANVIL_REPAIR`, `MOD_CRAFT_AMOUNT`, `MOD_ENCHANT_LEVEL`, `MOD_ANVIL_COST`
- Trading: `TRADE_COMPLETED`, `VILLAGER_INTERACT`, `MOD_TRADE_PRICE`
- Animals: `BREED`, `TAME`
- Items / interaction: `ITEM_USE`, `ITEM_USE_FINISH`, `ITEM_PICKUP`, `ENTITY_USE`
- Movement: `JUMP`, `LAND`, `CLIMB`, `WAKE_UP`, `TICK`, `MOD_TELEPORT_RANGE`, `MOD_FALL_DAMAGE`
- Other modifiers: `MOD_POTION_DURATION`
- Status effects: `EFFECT_APPLIED`

For action-style events set `entity_action`; for modifier-style events set `modifier`. A single power may declare both: the action path fires on `dispatch` sites and the modifier path chains on `dispatchModifier` sites.

**`neoorigins:cancel_event` support.** Using `cancel_event` as the `entity_action` vetoes the underlying game event, but only where NeoForge exposes a cancellable event:

- **Cancellable**: `death` (the lethal blow is undone; if the player is still at 0 HP they are left at 1 HP, totem-style; pair with a `condition` or a healing `entity_action`, otherwise the next damage tick kills them again), `kill` (spares the victim, same 1-HP patch), `hit_taken` (negates the incoming damage), `attack` (stops the swing before damage), `land` (negates fall damage), `projectile_hit` (negates the impact), `item_use`, `food_eaten` (blocks the use/eat), `effect_applied` (blocks the effect), `block_break`, `block_place`, `block_use`, `entity_use`, `villager_interact`, `breed`, `tame`, `bonemeal`.
- **Not cancellable** (the NeoForge event fires after the fact or cannot be cancelled; `cancel_event` is a silent no-op): `jump`, `item_use_finish`, `food_finished`, `item_pickup`, `craft_item`, `smelt_item`, `enchant_item`, `anvil_repair`, `trade_completed`, `advancement_earned`, `wake_up`, `respawn`, `dimension_change`, `climb`, `tick`, `gained`, `lost`, `chosen`, `power_activated`, and all `mod_*` modifier events.

**Example: heal 1 heart on kill only while holding a wooden sword**
```json
{
  "type": "neoorigins:action_on_event",
  "event": "kill",
  "condition": {
    "type": "neoorigins:equipped_item",
    "equipment_slot": "mainhand",
    "item_condition": { "id": "minecraft:wooden_sword" }
  },
  "entity_action": {
    "type": "neoorigins:heal",
    "amount": 2.0
  },
  "name": "Wooden Vampire",
  "description": "Regain health on kills, but only with a wooden sword."
}
```

**Example: 30% faster natural regen**
```json
{
  "type": "neoorigins:action_on_event",
  "event": "mod_natural_regen",
  "modifier": {
    "operation": "multiply_base",
    "value": 1.3
  }
}
```

**Example: 90% probabilistic resistance to a third-party infection effect, with 2s of full immunity after each cleanse**
```json
{
  "type": "neoorigins:action_on_event",
  "event": "effect_applied",
  "effect": "spore:mycelium_ef",
  "entity_action": {
    "type": "neoorigins:chance",
    "chance": 0.9,
    "action": { "type": "neoorigins:cancel_event" }
  },
  "immunity_ticks": 40
}
```

> Imported `origins:action_on_block_use` and `origins:bonemeal` powers translate to this type automatically (events `block_use` and `bonemeal`), the same way `origins:action_on_block_break` and `origins:action_on_entity_use` already do. The translation carries `condition`, `block_condition` and the hand filter over directly; `block_action` and `entity_action` both run as the event's `entity_action` (block actions self-resolve the clicked/bonemealed block from the event context), and an Apoli `action_result` of `SUCCESS`/`CONSUME`/`CONSUME_PARTIAL`/`FAIL` appends a `cancel_event` so the interaction outcome matches.

---

## `neoorigins:invulnerability`

Native invulnerability power: cancels incoming damage whose source matches any configured filter. If every filter list is empty, blocks all incoming damage. Replaces the lossy 1.x translation that only covered `FIRE`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `damage_types` | list of string | no | `[]` | Damage type IDs (e.g. `minecraft:fall`), matched by ID |
| `damage_tags` | list of string | no | `[]` | Damage type tag IDs (e.g. `minecraft:is_fire`) |
| `msg_ids` | list of string | no | `[]` | Vanilla damage msgId strings (e.g. `inFire`, `fall`), covers loose Origins-style names |

Filters combine with OR: a damage source is cancelled if it matches any entry in any list. If all three lists are empty, all damage is cancelled (matching Origins' behaviour when `damage_condition` is omitted).

**Example: fire and lava immunity**
```json
{
  "type": "neoorigins:invulnerability",
  "damage_tags": ["minecraft:is_fire"],
  "name": "Inferno Blood",
  "description": "Immune to fire and lava."
}
```

**Example: block everything**
```json
{
  "type": "neoorigins:invulnerability",
  "name": "Adamant",
  "description": "Cannot be harmed."
}
```

---

## `neoorigins:size_scaling`

Scales the player's visual and collision size via the `minecraft:generic.scale` attribute. Optionally also scales block/entity interaction reach. Mirrors the scale to Pehkui when the mod is present so cross-mod queries see a consistent value.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `scale` | float | no | `1.0` | Target scale multiplier (`0.5` = half size, `2.0` = double) |
| `modify_reach` | bool | no | `true` | Also adjust block/entity interaction reach proportionally |
| `reach_bonus` | float | no | `0.0` | Flat reach (blocks) added to **both** block and entity interaction range, on top of any `modify_reach` scaling |

**Example: half-size origin that keeps usable reach**
```json
{
  "type": "neoorigins:size_scaling",
  "scale": 0.5,
  "modify_reach": false,
  "reach_bonus": 0.5,
  "name": "Tiny",
  "description": "Half-sized, but reach is kept near normal."
}
```

The scale attribute uses `ADD_VALUE` against a base of `1.0` (so delta = `scale - 1.0`); proportional reach (`modify_reach`) uses `ADD_MULTIPLIED_BASE` so reach tracks visual size, while `reach_bonus` uses a flat `ADD_VALUE` on both ranges. Missing attributes (older MC or NeoForge versions) log a single warning and the power silently skips that attribute.

For the bundled size origins, `scale`, `modify_reach` and `reach_bonus` are all exposed per-origin in `config/neoorigins/power_overrides.toml`, so server owners can retune a shrunk origin's reach without a datapack; small origins (`inchling_size`, `tiny_size`) ship with `modify_reach: false` and a positive `reach_bonus` so they stay playable out of the box.

**Size stacks across layers.** Every `size_scaling` power owns its own modifier ids, so an origin scaling to `1.3` and a class scaling to `1.25` leave the player at `1.55`, not at whichever layer was picked last. Re-picking one layer removes only that layer's contribution and leaves the rest standing, in either order. Reach modifiers follow the same rule. If you want two layers to be mutually exclusive rather than cumulative, gate them with conditions; do not rely on one overwriting the other.

Deleting or renaming a size power's JSON is still safe: its leftover modifiers are swept the next time the player's powers change, so a power that no longer exists cannot strand someone at the wrong size.

---

## `neoorigins:pose`

Holds the player's body in a chosen pose for as long as the power is on: prone (crawling), sneak height, or upright. Vanilla recomputes the pose every tick and would fight a one-off change, so this is a power rather than an action — it re-asserts the pose while the origin says so and releases it the moment it stops.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `pose` | string | **yes** | — | `standing`, `crouching` or `swimming` (case-insensitive). Any other value makes the whole power fail to load, with an error naming the three it accepts: there is no fallback to `standing`, so a typo is loud rather than a power that silently does nothing |
| `toggleable` | bool | no | `true` | Binds a keybind that flips the pose on and off |
| `default_off` | bool | no | `false` | Toggleable powers only: the pose starts released, so the player opts in via the keybind |
| `enabled` | bool | no | `true` | Kill switch: when false the power stays attached but holds no pose |
| `cooldown_icon` | string | no | `""` | Item id or `.png` path shown on the HUD ability cluster: full-bright while on, dimmed while off |
| `always_show_icon` | bool | no | `false` | Keep the icon on the cluster while the power is idle |

**Example: a burrowing origin that crawls on command**
```json
{
  "type": "neoorigins:pose",
  "pose": "swimming",
  "toggleable": true,
  "default_off": true,
  "cooldown_icon": "minecraft:dirt",
  "name": "Burrow",
  "description": "Drop prone to squeeze through a one-block gap."
}
```

`swimming` is the crawl pose: a 0.6x0.6 body with the eye at 0.4. **The pose is not clearance-checked**, so it fits wherever its box fits, including gaps the player could not otherwise enter — a crawling origin can pass under a one-block ceiling anywhere, not only where vanilla would let it. That is deliberate; narrow it with a `condition` if your pack wants it narrower. Releasing the pose hands the player back to vanilla, which *does* check clearance and settles on whatever fits where they are standing.

**What the pose does and does not change.** It sets the hitbox and the animation, and it costs speed: vanilla scales movement input by the `minecraft:sneaking_speed` attribute whenever the player is crouched or crawling, and that test reads the pose, so a forced `crouching` or a forced `swimming` on land walks at sneak pace. It does **not** set the sneak flag, so there is no ledge-stop and no quiet footsteps — hold `pose: crouching` and the player still walks off edges. It also does **not** grant swimming: the swim propulsion in `travel` runs off the separate swimming flag, which vanilla sets from sprinting in water and never from a pose, so a forced `swimming` out of water is a crawl and nothing more.

Two `pose` powers active at once resolve smallest-first (`swimming`, then `crouching`, then `standing`), so the winning pose always fits wherever the loser would have. The top-level `condition` gates the pose like any other passive: while it fails the player is released to their normal pose, and picks it back up when the condition returns.

`sleeping`, `fall_flying` and `spin_attack` are vanilla poses too, but each carries behaviour the pose alone does not deliver (a bed, an elytra, a trident), so they are not offered here. For gliding, use `neoorigins:elytra_flight` or `neoorigins:natural_glide`.

---

## `neoorigins:entity_set`

Pure data-holder power. Its presence in a player's active power set declares that the player participates in a named UUID set. Actual set storage lives on `PlayerOriginData`; the `neoorigins:in_set` / `neoorigins:add_to_set` / `neoorigins:remove_from_set` verbs read and mutate it.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `name` | string | no | `""` | Name of the UUID set this power declares. Conventionally namespaced like `mypack:kill_streak`. |

**Example:**
```json
{
  "type": "neoorigins:entity_set",
  "name": "mypack:hunted",
  "description": "Tracks entities marked as hunted by this origin."
}
```

The colon in `name` is allowed and carries no mechanical meaning; it's a soft convention for avoiding collisions between packs. Pair this with `action_on_event` (to add entries) and `condition_passive` or direct DSL predicates (to query membership).

---

## `neoorigins:enhanced_vision`

> **Status (2.2.x):** active again. On 26.x the brightness boost is applied
> through the lightmap render state (`LightmapRenderStateExtractor` mixin) at
> higher priority, so it survives other mods (Alex's Caves, etc.) that touch
> the same pipeline. A handful of built-in powers (Avian keen sight, the
> Archer / Cleric / Miner class sights) use `enhanced_vision`; most origin
> night vision remains on `neoorigins:night_vision`.

Passive low-light vision: emits an `enhanced_vision` capability tag and scales the client brightness curve directly via a `LightTexture` mixin. Unlike the full `minecraft:night_vision` status effect, there's no screen tint, HUD icon, or max-brightness ramp at end of duration, just exposure-style compensation.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `exposure` | float (0.0–1.0) | no | `0.7` | Target brightness scalar (advisory: the client mixin currently hardcodes 0.7; a real Night Vision effect still wins where stronger) |

**Example:**
```json
{
  "type": "neoorigins:enhanced_vision",
  "exposure": 0.7,
  "name": "Cat Eyes",
  "description": "Sees clearly in dim light without the green tint."
}
```

All exposure work happens on the logical client; the server only publishes the capability tag. If per-origin variance is needed later, the value will be wired through a client-synced power-config payload.

The **Toggle Night Vision** keybind (default `K`) switches this off too, so the
key means the same thing whichever mechanism an origin uses. Like the status-effect
path it starts on, so nothing changes for a player who never presses it. No
ability slot is claimed either way: the dedicated key sits outside the skill-slot
system entirely. See [`neoorigins:night_vision`](#neooriginsnight_vision).

---

## `neoorigins:edible_item`

Makes arbitrary items consumable on right-click. A matching item is instantly consumed: the configured nutrition/saturation is applied, one is removed from the stack, and an `action_on_event.ITEM_USE_FINISH` dispatch fires with the stack as context. Bypasses vanilla FoodProperties so pack authors can declare "Merling eats raw fish" or "Phantom eats rotten flesh for full food" without replacing the item's data components.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `items` | list of Identifier | no | `[]` | Exact item IDs that qualify |
| `tags` | list of Identifier | no | `[]` | Item tag IDs that qualify |
| `nutrition` | int | no | `4` | Food points restored (1 shank = 2) |
| `saturation` | float | no | `0.3` | Saturation restored |
| `always_edible` | bool | no | `true` | If true, can be eaten at full hunger |
| `consume_sound` | Identifier | no | _(none)_ | Optional sound ID played on consume |

At least one of `items` or `tags` should be non-empty, otherwise nothing will ever match. Matching is inclusive: an item qualifies if it appears in either list.

**Example: Merling eats raw fish at full food**
```json
{
  "type": "neoorigins:edible_item",
  "tags": ["minecraft:fishes"],
  "nutrition": 4,
  "saturation": 0.4,
  "always_edible": true,
  "name": "Pescivore",
  "description": "Can eat raw fish at any time."
}
```

### Caveborn diet tags

The Caveborn's seven eating powers each read one shipped item tag, so a datapack can extend any of them without overriding the power. A tag file lives under the namespace of the tag it extends, not your own, so declare `data/neoorigins/tags/item/<name>.json` inside your pack with `"replace": false` and append your own items.

| Tag | Backing power | Nutrition / saturation |
|---|---|---|
| `neoorigins:caveborn_eat_stone` | *Stone Eater* | 2 / 0.4 |
| `neoorigins:caveborn_eat_copper` | *Copper Palate* | 3 / 0.6 |
| `neoorigins:caveborn_eat_iron` | *Iron Palate* | 4 / 0.8 |
| `neoorigins:caveborn_eat_gold` | *Gilded Palate* | 5 / 1.0 |
| `neoorigins:caveborn_eat_diamond` | *Diamond Palate* | 6 / 1.2 |
| `neoorigins:caveborn_eat_emerald` | *Emerald Palate* | 7 / 1.4 |
| `neoorigins:caveborn_eat_netherite` | *Netherite Palate* | 8 / 1.6 |

Nutrition is a property of the power, not of the tag, so adding an item to `caveborn_eat_stone` makes it edible at stone's values. To grant a different amount, write your own `edible_item` power instead.

Every one of these powers is `always_edible: false`, so none of them go down on a full hunger bar.

The six metal and gem tags hold the raw and refined forms of their material — ingots, nuggets, gems, raw metal and raw metal blocks, and since 2.2.25 the ores themselves, including deepslate variants, nether gold ore and ancient debris. Not every form exists for every material: copper has no nugget, diamond is the gem plus its two ores, emerald is the gem plus its two ores, and netherite is the ingot, scrap and ancient debris. `caveborn_eat_stone` is the exception and carries no ore at all: it holds the ten vanilla raw stones (cobblestone, stone, granite, diorite, andesite, tuff, deepslate, cobbled deepslate, basalt, blackstone).

Copper and emerald also carry a bonus power that fires on finishing the meal: `caveborn_copper_bonus` grants Water Breathing I for 60 seconds, and `caveborn_emerald_bonus` grants Fire Resistance for 5 minutes. Each meal additionally drives a hidden `model_color` tint for as long as its effect runs; those six tints are individually switchable from `power_overrides.toml`.

Worth knowing for the ore and stone entries: both are placeable blocks, so aiming at a surface places one as normal. Eating one means aiming where it cannot be placed — the same rule the stone diet has always followed.

---

## `neoorigins:restrict_armor`

Prevents certain items from being equipped in certain slots. When a matching item is placed in a restricted slot, the power ejects it back to the player's inventory (or drops it to the ground if inventory is full).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `restrictions` | list of SlotRestriction | no | `[]` | Per-slot filters. An empty list disables the power. |

**`SlotRestriction` object:**

| Field | Type | Required | Description |
|---|---|---|---|
| `slot` | string | yes | One of `mainhand`, `offhand`, `head`, `chest`, `legs`, `feet`, `body` |
| `item` | Identifier | no | Exact item ID to restrict |
| `tag` | Identifier | no | Item tag to restrict |

If neither `item` nor `tag` is set on a restriction entry, any non-empty stack in that slot is restricted.

**Example: no iron or diamond chestplates**
```json
{
  "type": "neoorigins:restrict_armor",
  "restrictions": [
    { "slot": "chest", "item": "minecraft:iron_chestplate" },
    { "slot": "chest", "item": "minecraft:diamond_chestplate" }
  ],
  "name": "Light Armor Only",
  "description": "Cannot wear iron or diamond chestplates."
}
```

### Armor classes

NeoOrigins ships two item tags that categorize vanilla armor into **light** and **heavy** classes. Use these with `restrict_armor` to broadly gate what an origin can wear without listing every item individually.

| Tag | Contents |
|---|---|
| `neoorigins:light_armor` | Leather, Chainmail (all slots) |
| `neoorigins:heavy_armor` | Iron, Gold, Diamond, Netherite (all slots) |

**Example: restrict an origin to light armor only**
```json
{
  "type": "neoorigins:restrict_armor",
  "restrictions": [
    { "slot": "head",  "tag": "neoorigins:heavy_armor" },
    { "slot": "chest", "tag": "neoorigins:heavy_armor" },
    { "slot": "legs",  "tag": "neoorigins:heavy_armor" },
    { "slot": "feet",  "tag": "neoorigins:heavy_armor" }
  ],
  "name": "Light Armor Only",
  "description": "Cannot wear heavy armor."
}
```

**Adding modded armor to a class:**

Modpack authors can extend the classes in two ways:

1. **Datapack**: add entries to the `neoorigins:heavy_armor` or `neoorigins:light_armor` item tags via a higher-priority datapack.

2. **Config**: add item IDs or `#tags` to the `[armor_classes]` section in `config/neoorigins/gameplay.toml`.

```toml
[armor_classes]
    # Items/tags added here supplement the neoorigins:heavy_armor tag
    heavy_armor = ["modid:steel_chestplate", "#modid:titanium_armor"]
    # Items/tags added here supplement the neoorigins:light_armor tag
    light_armor = ["modid:cloth_robe"]
```

Config entries are checked alongside the tags; items in either source count as that armor class.

---

## `neoorigins:restrict_items`

One modular, multipurpose gate over **equipping** and **using** items. A single flexible power that covers both blacklist and allow-list semantics for any item matched by a reusable item condition, across any equipment slot and/or hand. Unlike `restrict_armor` (equip-only), this can also cancel item *use*, and, done properly, that means stopping a totem of undying from saving the holder (vanilla consumes the totem before the death event, so this is enforced via a mixin into `checkTotemDeathProtection`).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `item_condition` | item condition | no | matches all | Apoli-style item condition (`id` / `tag` / `nbt` / `enchantment` / `empty`, with `and`/`or`/`not` composition) selecting which stacks the gate matches. Omit to match every stack. |
| `slots` | list of strings | no | `[]` (all) | EquipmentSlot names the *equip* gate applies to: `mainhand`, `offhand`, `head`, `chest`, `legs`, `feet`, `body`. Empty = all slots. |
| `hands` | list of strings | no | `[]` (both) | InteractionHand names the *use* gate applies to: `mainhand`, `offhand`. Empty = both hands. |
| `prevent_equip` | boolean | no | `false` | When true, equipping a matched item into a gated slot is rejected (ejected back to the inventory). |
| `prevent_use` | boolean | no | `false` | When true, *using* a matched item (right-click / raise: shields, totems, bows, food, ...) in a gated hand is cancelled, including stopping a totem of undying from popping. |
| `deny` | boolean | no | `true` | `true` = blacklist: matched items are forbidden. `false` = allow-list: ONLY matched items are permitted for the gated action; everything else is forbidden. |
| `condition` | EntityCondition | no | always true | Optional whole-power gate: the power is inert unless it passes. |

The single decision is `match → forbidden`: in blacklist mode a matched item is forbidden; in allow-list mode an *un*matched item is forbidden. The same decision drives equip, use, and the totem mixin, so they can never disagree.

**Example: a "fragile" origin can't use shields and can't be saved by totems**
```json
{
  "type": "neoorigins:restrict_items",
  "item_condition": {
    "type": "neoorigins:or",
    "conditions": [
      { "item": "minecraft:shield" },
      { "item": "minecraft:totem_of_undying" }
    ]
  },
  "prevent_use": true,
  "deny": true,
  "name": "Fragile",
  "description": "Cannot raise a shield, and a totem of undying will not save you."
}
```

**Example (allow-list): can only ever hold a wooden sword in the main hand**
```json
{
  "type": "neoorigins:restrict_items",
  "item_condition": { "item": "minecraft:wooden_sword" },
  "slots": ["mainhand"],
  "prevent_equip": true,
  "deny": false,
  "name": "Pacifist's Vow",
  "description": "Refuses to wield anything but a wooden sword."
}
```

---

## `neoorigins:keep_inventory`

On death, selectively retain inventory items matching the power's filters. Matching items are removed from drops and restored on respawn into the exact slot they were taken from, trinket slots included: armour comes back worn, the hotbar keeps its layout, and a full inventory can no longer push the leftovers onto the ground.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `slots` | list of string | no | `["*"]` | Slot categories to keep from: `hotbar`, `main`, `armor`, `offhand`, `all` (alias `*`). `main` also includes `hotbar`. Also accepts trinket slots: `curio`, `accessory`, or `trinket` cover every Curios slot, or name a specific slot id like `ring` or `necklace`. |
| `items` | list of Identifier | no | `[]` | Specific items to keep |
| `tags` | list of Identifier | no | `[]` | Item tags to keep |

If both `items` and `tags` are empty, every item in the configured slots is kept.

**Curios:** trinket slots are covered when the [Curios](https://www.curseforge.com/minecraft/mc-mods/curios) mod is installed; without it these slot tokens simply match nothing. The wildcard `*`/`all` includes trinkets too.

A kept trinket is re-equipped into its original slot on respawn. Curios rebuilds its slot counts on its own schedule after a respawn, so if the slot isn't writable yet the trinket is held and retried for three seconds rather than given up on. Only once that window is spent does it fall back to the regular inventory, and to the ground only if the inventory is full as well. Logging out mid-retry settles it the same way instead of losing it.

**Example: always keep tools**
```json
{
  "type": "neoorigins:keep_inventory",
  "slots": ["main", "hotbar", "offhand"],
  "tags": ["minecraft:pickaxes", "minecraft:swords", "minecraft:axes", "minecraft:shovels"],
  "name": "Tool Preservation",
  "description": "Tools are never dropped on death."
}
```

---

## `neoorigins:modify_player_spawn`

Overrides the player's respawn location to a power-configured target. Unlike the origin's `spawn_location` (first-join and bed-less respawn only), this power fires on every respawn. Optionally also overrides the bed/respawn-anchor spawn point.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `location` | LocationCondition | yes | — | Target location descriptor: `dimension` / `biome` / `biome_tag` / `structure` / `structure_tag`. See `attribute_modifier`'s `location_condition` for the shape. |
| `override_bed` | bool | no | `false` | When true, also overrides bed/respawn-anchor spawn. When false, respects a valid bed/anchor and only fires if none is set. |

**Example: always respawn in an End city**
```json
{
  "type": "neoorigins:modify_player_spawn",
  "location": {
    "dimension": "minecraft:the_end",
    "structure": "minecraft:end_city"
  },
  "override_bed": true,
  "name": "Void-Bound",
  "description": "Always respawns in the nearest End city."
}
```

---

## `neoorigins:toggle`

A bare, stateless boolean power: purely a data-holder whose state lives in `CompatAttachments.toggleState()` keyed by the registered id. Unlike `active_ability`, `toggle` does not consume a keybind slot; it's a named boolean other powers gate on.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `default` | bool | no | `false` | Value reads see before the toggle has ever been flipped on this player. Set `true` for "on until flipped off." |

Read the current value with `neoorigins:power_active { power: "mypack:my_toggle" }`. Flip it with `neoorigins:toggle { power: "mypack:my_toggle" }` (optionally `value: true/false` to set explicitly).

**Example:**
```json
{
  "type": "neoorigins:toggle",
  "default": false,
  "hidden": true,
  "name": "Hunter's Mark",
  "description": "Internal flag gated by other powers."
}
```

`hidden: true` keeps the toggle out of the origin info panel, recommended for any purely-internal flag a player doesn't need to see listed.

See [COOKBOOK.md → Toggleable abilities (no keybind slot)](COOKBOOK.md#15-toggleable-abilities-no-keybind-slot) for full recipes.

---

## `neoorigins:active_ability`

Generic cooldown-gated active (keybind) ability. Part of the 2.0 consolidation: collapses `active_teleport`, `active_dash`, `active_launch`, `active_recall`, `active_swap`, `active_fireball`, `active_bolt`, `active_phase`, `active_place_block`, `healing_mist`, `repulse`, `active_aoe_effect`, and others into one type whose effect is described by an `entity_action` tree.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `cooldown_ticks` | int | no | `60` | Cooldown after each use. Ignored when `cooldown_resource` is set. |
| `cooldown_resource` | string | no | `""` | Id of a `neoorigins:variable` (or `neoorigins:resource`) counter whose **live value, in ticks**, is read at activation time and used as the cooldown length, overriding `cooldown_ticks`. Empty = fixed cooldown. Edit the counter with `change_resource`/`set_resource` to make a power's cooldown scale with game state (stacks consumed, upgrades bought, etc.). A missing counter falls back to its declared `start`; negative values clamp to 0 (instant reuse). |
| `hunger_cost` | int | no | `0` | Food points removed per use (1 shank = 2 points). Silently aborts if player has less food (cooldown not consumed). |
| `resource_cost` | string | no | `""` | Id of a `neoorigins:resource` power to debit per use. Empty = no resource cost. |
| `resource_cost_amount` | int | no | `0` | Amount drained from `resource_cost` per use. Silently aborts (cooldown not consumed) if the resource can't cover it. If resource bars are globally disabled in config, the cost is charged as hunger instead. |
| `entity_action` | EntityAction | no | noop | Action tree fired on use (typically `neoorigins:and { actions: [...] }`) |
| `condition` | EntityCondition | no | always-true | DSL gate: skips firing (and the cooldown) if false |
| `cooldown_icon` | string | no | `""` | HUD cooldown icon (item id or `.png` texture path), see "Cooldown HUD fields" at the top of this page |
| `cooldown_countdown` | bool | no | `true` | Draw remaining seconds on the icon (needs `cooldown_icon`) |
| `always_show_icon` | bool | no | `false` | Keep the icon on the HUD even while idle (needs `cooldown_icon`; this is what keeps the icon visible under the default `COOLDOWNS_AND_TOGGLES` HUD mode, and has no additional effect under `ALL_ACTIVE_ABILITIES`) |

Each `active_ability` power maintains an **independent cooldown**. Multiple active abilities on the same origin do not share a cooldown counter; triggering one ability does not block another.

Actions and conditions are compiled once at power-load time via `ActionParser` / `ConditionParser`; runtime only dispatches through the compiled closures.

Hunger gating is handled at the `AbstractActivePower` base class level: when `hunger_cost > 0`, the base checks and debits food before calling `execute`. Any power that extends `AbstractActivePower` and wires `hungerCost()` through its Codec inherits the behavior automatically.

**Example: launch the player upward**
```json
{
  "type": "neoorigins:active_ability",
  "cooldown_ticks": 80,
  "entity_action": {
    "type": "neoorigins:add_velocity",
    "y": 2.0
  },
  "name": "Leap",
  "description": "Launches the player upward."
}
```

**Example: cooldown driven by a counter.** Declare a hidden `neoorigins:variable` counter elsewhere in the origin (say `myorigin:dash_cooldown`, `start: 100`), then point the ability at it. Other powers can shorten or lengthen the reuse delay by writing to that counter, e.g. a passive that runs `set_resource myorigin:dash_cooldown 40` while sprinting, or a `change_resource` that adds 20 ticks per consecutive use:
```json
{
  "type": "neoorigins:active_ability",
  "cooldown_resource": "myorigin:dash_cooldown",
  "entity_action": {
    "type": "neoorigins:add_velocity",
    "x": 3.0
  },
  "name": "Adaptive Dash",
  "description": "Dash whose cooldown follows the dash_cooldown counter."
}
```
The counter's value is read live on every activation, so the same key can be tuned at runtime by any action. Because counters share the resource keyspace and are declared at power-load time, the cooldown reads correctly even on the very first use (it falls back to the counter's `start`).

Legacy active types (`active_teleport`, `active_dash`, etc.) remain registered during the deprecation window. The `migrateLegacyPowers` gradle task can rewrite pack JSON to this type; `LegacyPowerTypeAliases` covers unmigrated JSON once per-type field remappers are landed.

---

## `neoorigins:ground_slam`

Active AoE slam: damages and knocks back every entity within radius.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `damage` | float | no | `6.0` | Damage dealt to each entity |
| `knockback_strength` | float | no | `1.5` | Radial knockback strength |
| `radius` | double | no | `6.0` | Effect radius in blocks |
| `cooldown_ticks` | int | no | `120` | Cooldown after each use |

**Example:**
```json
{
  "type": "neoorigins:ground_slam",
  "damage": 6.0,
  "radius": 6.0,
  "cooldown_ticks": 120,
  "name": "Tectonic Slam",
  "description": "Crushes nearby enemies with a shockwave."
}
```

---

## `neoorigins:tidal_wave`

Cone-shaped water blast in the player's look direction. Knocks back and damages entities caught in the wave.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `damage` | float | no | `4.0` | Damage per hit |
| `knockback_strength` | float | no | `2.0` | Forward knockback strength |
| `range` | double | no | `8.0` | Maximum wave distance |
| `cone_angle` | double | no | `60.0` | Total cone angle in degrees (30° to each side of look axis) |
| `cooldown_ticks` | int | no | `100` | Cooldown after each use |
| `hunger_cost` | int | no | `0` | Food points removed per use |

**Example:**
```json
{
  "type": "neoorigins:tidal_wave",
  "damage": 4.0,
  "range": 8.0,
  "cone_angle": 60.0,
  "cooldown_ticks": 100,
  "name": "Tidal Wave",
  "description": "Blasts enemies with a cone of water."
}
```

---

## `neoorigins:summon_minion`

Active power that summons a mob near the player. Summoned mobs are tracked with caps, despawn timers, and death-damage feedback. Equipment can be configured per slot; all equipment drop chances are zeroed so summoned mobs never drop loot.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `mob_type` | Identifier | yes | — | Entity type to summon, e.g. `minecraft:zombie` |
| `max_count` | int | no | `3` | Maximum concurrent minions per player per mob_type |
| `quantity` | int | no | `1` | Minions summoned per activation, capped by remaining `max_count` headroom |
| `cooldown_ticks` | int | no | `200` | Cooldown after each summon |
| `hunger_cost` | int | no | `4` | Food points consumed per summon |
| `despawn_ticks` | int | no | `18000` | Lifespan after spawn (15 min default) |
| `death_damage` | float | no | `1.0` | Damage taken by the owner when a minion dies |
| `head` / `chest` / `legs` / `feet` / `mainhand` / `offhand` | Identifier _or_ object | no | _(none or iron helmet for head)_ | Equipment per slot. Either a bare item id (`"minecraft:iron_sword"`) or an object `{"item": "...", "enchantments": [{"id": "...", "level": N}]}` to enchant the piece |
| `mount` | Identifier | no | — | Entity type of an optional mount; each minion rides its own copy (e.g. a piglin riding a hoglin) |
| `attributes` | object[] | no | `[]` | Attribute modifiers applied to each summoned mob at spawn. Each entry is `{"attribute": "...", "amount": N, "operation": "..."}` |

If `head` is unset, the minion gets an iron helmet by default (sun protection for undead). All drop chances are set to 0.

**Multiple minions:** set `quantity` to summon several per activation (still capped by `max_count`). Hunger is charged once per activation regardless of how many spawn.

**Enchanted equipment:** any equipment slot accepts the object form `{"item": "...", "enchantments": [...]}` in place of a bare item id. Each enchantment entry is `{"id": "minecraft:sharpness", "level": 3}` (`level` defaults to 1). Unknown enchantment ids are skipped silently. The string form is unchanged, so existing configs keep working.

**Attribute modifiers:** `attributes` is a list of modifiers applied to every summoned mob. Each entry takes `attribute` (e.g. `minecraft:generic.max_health`, `minecraft:generic.attack_damage`, `minecraft:generic.movement_speed`), a numeric `amount`, and an `operation` of `add_value` (flat), `add_multiplied_base`, or `add_multiplied_total` (default `add_value`). Attribute ids resolve with `generic.`/`player.` prefix tolerance, so the same JSON is portable across the 1.21.1 and 26.1 builds. A raised `max_health` spawns the mob at full HP.

**Mounts:** when `mount` is set, every summoned minion is seated on a freshly spawned copy of that entity using a forced ride, so cross-type stacks like a piglin on a hoglin work. The mount is tracked alongside its rider: it counts against the same `mob_type` cap, drops no loot, and despawns with the rider.

**Brain-driven mobs (piglins, hoglins):** these decide aggression through the Brain/memory system rather than goal selectors, so vanilla checks like "the player isn't wearing gold" would otherwise make a summoned piglin turn hostile. Summoned brain mobs are pacified at spawn and re-pacified every tick: their attack-target/anger memories toward the owner are cleared and piglins are made immune to zombification, so they stay loyal to their summoner.

**Example: zombie minion with sword**
```json
{
  "type": "neoorigins:summon_minion",
  "mob_type": "minecraft:zombie",
  "max_count": 3,
  "cooldown_ticks": 200,
  "hunger_cost": 4,
  "mainhand": "minecraft:iron_sword",
  "name": "Raise Dead",
  "description": "Summons a zombie minion to fight for you."
}
```

**Example: pack of piglins riding hoglins**
```json
{
  "type": "neoorigins:summon_minion",
  "mob_type": "minecraft:piglin",
  "mount": "minecraft:hoglin",
  "quantity": 2,
  "max_count": 4,
  "cooldown_ticks": 400,
  "hunger_cost": 6,
  "mainhand": "minecraft:golden_sword",
  "name": "Piglin Cavalry",
  "description": "Summons mounted piglin riders that fight for you."
}
```

**Example: enchanted, buffed iron golem**
```json
{
  "type": "neoorigins:summon_minion",
  "mob_type": "minecraft:iron_golem",
  "max_count": 1,
  "cooldown_ticks": 600,
  "hunger_cost": 8,
  "mainhand": {
    "item": "minecraft:netherite_sword",
    "enchantments": [
      { "id": "minecraft:sharpness", "level": 5 },
      { "id": "minecraft:fire_aspect", "level": 2 }
    ]
  },
  "attributes": [
    { "attribute": "minecraft:generic.max_health", "amount": 40, "operation": "add_value" },
    { "attribute": "minecraft:generic.attack_damage", "amount": 0.5, "operation": "add_multiplied_total" }
  ],
  "name": "Forge Guardian",
  "description": "Summons a heavily armed, reinforced iron golem."
}
```

---

## `neoorigins:tame_mob`

Active power that tames a hostile mob the player is looking at. The mob's AI is rewritten to follow the player and target whatever recently hurt the owner. Tamed mobs are tracked via MinionTracker.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `range` | double | no | `16.0` | Max raycast distance to a target |
| `max_tamed` | int | no | `4` | Maximum concurrently-tamed mobs per player |
| `cooldown_ticks` | int | no | `200` | Cooldown after each use |
| `hunger_cost` | int | no | `3` | Food points consumed per tame |
| `despawn_ticks` | int | no | `36000` | Lifespan of each tamed mob (30 min default) |
| `death_damage` | float | no | `0.5` | Damage taken by owner when a tamed mob dies |
| `hostile_only` | bool | no | `true` | If `true`, only hostile mobs (implementing `Enemy`) can be tamed, the Monster Tamer default. Set to `false` to allow taming any non-player `Mob` (animals, golems, villagers, etc.). |
| `entity_blacklist` | string[] | no | `[]` | Entity ids (`"minecraft:warden"`) and tag refs (`"#mymod:untameable"`) this power can never tame. Checked on top of the built-in boss exclusion. |
| `targeting` | string | no | `"raycast"` | `"raycast"` tames the single mob you are looking at; `"area"` tames every eligible mob within `range`, nearest-first, up to the remaining `max_tamed` slots. Case-insensitive; anything else falls back to `raycast`. |
| `entity_whitelist` | string[] | no | `[]` | **Area mode only.** Entity ids (`"minecraft:zombie"`) and tag refs (`"#mymod:tameable"`) the sweep is restricted to. Empty (default) = any mob (still subject to `hostile_only` and the blacklists). Ignored in raycast mode. |
| `resource_cost` | string | no | `""` | Optional `neoorigins:resource` power id spent **per mob tamed**. Empty = no resource cost (falls back to `hunger_cost`). When resource bars are globally disabled this amount is charged as hunger instead. |
| `resource_cost_amount` | int | no | `0` | Amount of the `resource_cost` resource spent per mob tamed. |

### Targeting modes and per-mob cost

With the default `targeting: "raycast"` the power tames the one mob under the player's crosshair, showing the existing per-failure actionbar messages (`not_hostile` / `boss` / `blacklisted` / `no_target`).

Set `targeting: "area"` to tame every eligible mob inside `range` in one activation. Candidates are gathered nearest-first and capped at the remaining `max_tamed` slots; `hostile_only`, `entity_blacklist`, the boss-tier set and the global config blacklist all still apply, and `entity_whitelist` (if non-empty) further restricts which mobs qualify. A successful multi-tame shows "Tamed N mobs!".

Cost is charged **once per mob tamed**, not once per activation. When `resource_cost`/`resource_cost_amount` are set and resource bars are enabled, each tame spends that much of the named resource; otherwise it spends `hunger_cost` food points (or, when a resource is configured but resource bars are globally disabled, `resource_cost_amount` food points). Taming is greedy: it tames mobs one at a time until either the slots run out or the player can no longer afford one more, then stops. If candidates existed but none could be afforded, it shows "Not enough power to tame!" and consumes no cooldown.

Target must be a non-player `Mob`. With the default `hostile_only: true`, only mobs implementing `Enemy` qualify (villagers, animals, and passive mobs won't tame); set `hostile_only: false` to drop that restriction. Boss-tier mobs (the Warden, Ender Dragon and Wither) are always rejected, as is anything that fails the `canUsePortal` boss check; `entity_blacklist` lets a pack extend that exclusion to arbitrary mobs, and server operators can do the same for all taming and scare powers at once via the `tame_scare_entity_blacklist` config list (see [Global taming/scare exclusions](#global-tamingscare-exclusions)). A blocked tame shows the "That creature cannot be tamed!" actionbar message.

### Persistence

Tamed mobs behave like vanilla pets: the tame is stored on the mob itself (owner, despawn duration and death-damage ride the entity's saved data), so it survives everything short of the mob dying or `despawn_ticks` running out. Concretely:

- **Tamer dies**: pets survive and are recalled to the tamer when they respawn, crossing dimensions if needed.
- **Tamer logs out**: pets stay in the world, idle and non-hostile, and resume following/defending the moment the tamer logs back in. No re-tame needed.
- **Server restarts / chunk unloads**: the pet's loyalty AI is rebuilt automatically whenever its chunk loads again.

The `max_tamed` cap and death-damage backlash stay correct across all of the above. Losing the taming power (an origin change) discards the player's loaded pets outright; that is the one way to revoke a tame. Summoned minions (`summon_minion`) deliberately do NOT get this treatment: they remain session-bound and vanish with their summoner's death or logout.

**Example (blacklist):**
```json
{
  "type": "neoorigins:tame_mob",
  "entity_blacklist": ["minecraft:elder_guardian", "#mymod:untameable"]
}
```

**Example:**
```json
{
  "type": "neoorigins:tame_mob",
  "range": 16.0,
  "max_tamed": 4,
  "cooldown_ticks": 200,
  "name": "Beastmaster",
  "description": "Tames hostile mobs to your will."
}
```

### Global taming/scare exclusions

All taming and scare powers (`tame_mob`, `scare_entities` and `mobs_ignore_player`) share one exclusion rule. An entity is excluded when **any** of these hold:

1. **Boss-tier**: the Warden, Ender Dragon and Wither. Hardcoded; cannot be overridden.
2. **Global config blacklist**: the `tame_scare_entity_blacklist` list in the `[entity_exclusions]` section of `config/neoorigins/admin.toml`. Lets a server or pack operator extend the exclusion to arbitrary mobs across every taming and scare power at once:

```toml
[entity_exclusions]
    # Entity ids and #tags that can never be tamed, scared,
    # or made to ignore a player by any power. Default: []
    tame_scare_entity_blacklist = ["minecraft:elder_guardian", "#mymod:untouchable"]
```

3. **Per-power `entity_blacklist`**: the optional JSON field on the individual power.

Excluded entities can't be tamed (`tame_mob` shows its actionbar message) and aren't affected by `scare_entities` or `mobs_ignore_player` (silently skipped; they flee or target the player exactly as vanilla dictates).

---

## `neoorigins:command_pack`

Active power that commands every tamed mob (via `tame_mob`) to attack the entity the player is looking at.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `range` | double | no | `32.0` | Max raycast distance to the target |
| `cooldown_ticks` | int | no | `40` | Cooldown after each use |

**Example:**
```json
{
  "type": "neoorigins:command_pack",
  "range": 32.0,
  "cooldown_ticks": 40,
  "name": "Sic 'Em",
  "description": "Orders your pack to attack your target."
}
```

---

## `neoorigins:horde_regen`

Passively regenerates health on all tamed mobs (via `tame_mob`) on an interval. Healing only applies when the mob hasn't taken damage recently.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `heal_amount` | float | no | `1.0` | HP restored per interval |
| `interval_ticks` | int | no | `120` | Ticks between heal pulses |
| `combat_cooldown_ticks` | int | no | `100` | Minimum ticks since last damage before a mob heals |

**Example:**
```json
{
  "type": "neoorigins:horde_regen",
  "heal_amount": 1.0,
  "interval_ticks": 120,
  "name": "Pack Mender",
  "description": "Your tamed mobs regenerate out of combat."
}
```

---

## `neoorigins:mount`

Active keybind power that raycasts for the living entity in front of the player and seats the player on it. Press the skill key with a target in range to mount; press again while riding to dismount. Mobs are mounted immediately; **player** targets require consent according to the server's configured consent mode (`MountConsentManager`). Boss mobs and already-ridden entities are rejected, and the rider is dismounted automatically if the power is revoked. Good for tamer/druid origins that ride mobs (or other players) on demand.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `range` | double | no | `5.0` | Max distance in blocks to raycast for the entity to mount. |
| `cooldown_ticks` | int | no | `100` | Ticks before the mount ability can be reused (100 = 5s). |
| `hunger_cost` | int | no | `0` | Hunger consumed each time an entity is mounted. |
| `allow_players` | bool | no | `true` | Whether this power can mount other players (subject to consent). |
| `allow_mobs` | bool | no | `true` | Whether this power can mount mobs. |
| `block_bosses` | bool | no | `true` | Prevent mounting boss mobs like the Ender Dragon or Wither. |
| `mount_position` | enum | no | `centered` | Where the rider sits: `centered` (on top) or `shoulder` (offset to one side). |

**Example:**
```json
{
  "type": "neoorigins:mount",
  "range": 6.0,
  "cooldown_ticks": 60,
  "allow_players": false,
  "mount_position": "shoulder",
  "name": "Mount Beast",
  "description": "Leap onto the creature you're looking at."
}
```

---

## `neoorigins:mob_behavior`

Rewrites a **mob origin's** AI so the mob hunts players (or another entity type). Unlike the player-facing tame/mount powers, this is applied to a mob origin and controls how that mob acquires and holds targets. On grant, a vanilla `NearestAttackableTargetGoal` (and, when `retaliate` is set, a `HurtByTargetGoal`) is added to the mob's target selector; both are stripped again on revoke, leaving the rest of the mob's vanilla AI intact. With `aggression: "conditional"` it behaves piglin-style: the mob only turns hostile toward a player while every `hostile_when` condition holds (the conditions are evaluated against the prospective player target, not the mob).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `aggression` | enum | no | `neutral` | `neutral` = vanilla AI unchanged (only `retaliate` applies); `hostile` = always target the target type on sight; `conditional` = target a player only while every `hostile_when` condition holds. |
| `hostile_when` | array | no | `[]` | Conditions (AND-ed) tested against the **prospective player target** (e.g. "player not wearing gold"), not the mob. Only used when `aggression: "conditional"`; an empty list behaves like `hostile`. |
| `retaliate` | bool | no | `true` | Also fight back against whatever damaged the mob (vanilla hurt-by-target). |
| `anger_linger_ticks` | int | no | `200` | Keep the target this many ticks after the trigger stops holding, so the mob calms down gradually (200 = 10s). |
| `aggro_range` | double | no | `16.0` | Max distance to acquire a target. |
| `target_type` | resource id | no | players | Entity type id to be hostile toward; omitted = players. Conditions only apply to player targets. |
| `call_for_help` | bool | no | `false` | When retaliating, alert nearby same-type mobs (vanilla pack aggro). |

**Example: hostile to players only in daylight while not sneaking**
```json
{
  "type": "neoorigins:mob_behavior",
  "aggression": "conditional",
  "aggro_range": 24.0,
  "anger_linger_ticks": 300,
  "call_for_help": true,
  "hostile_when": [
    { "type": "neoorigins:exposed_to_sun" },
    { "type": "neoorigins:not", "condition": { "type": "neoorigins:sneaking" } }
  ]
}
```

---

## `neoorigins:exhaustion_filter`

Filters out specific vanilla exhaustion sources so they don't drain the player's hunger. Handled via `PlayerTickEvent.Pre`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `sources` | list of string | no | `["sprint"]` | Exhaustion sources to cancel, e.g. `sprint`, `mining` |

**Example:**
```json
{
  "type": "neoorigins:exhaustion_filter",
  "sources": ["sprint"],
  "name": "Tireless",
  "description": "Sprinting no longer drains hunger."
}
```

---

## `neoorigins:twin_breeding`

On vanilla breeding, spawns a second baby with the configured probability.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `chance` | float | no | `1.0` | Probability (0.0–1.0) of spawning a twin |

**Example:**
```json
{
  "type": "neoorigins:twin_breeding",
  "chance": 0.5,
  "name": "Shepherd",
  "description": "Animals bred near you sometimes produce twins."
}
```

---

## `neoorigins:less_item_use_slowdown`

> **Deprecated in 2.0**: this type is now an alias for `neoorigins:attribute_modifier`. See [MIGRATION.md](MIGRATION.md).

Reduces movement slowdown while using items (bow, shield, etc.). Applies a transient `generic.movement_speed` modifier while `isUsingItem()` is true and the held item matches.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `item_type` | string | no | `any` | `any`, `bow`, `shield`, or any substring matched against the held item's ID |
| `speed_multiplier` | float | no | `0.5` | `ADD_MULTIPLIED_BASE` value: `0.5` = +50% walk speed while using |

**Example: full-speed archery**
```json
{
  "type": "neoorigins:less_item_use_slowdown",
  "item_type": "bow",
  "speed_multiplier": 0.8,
  "name": "Strider Archer",
  "description": "Barely slowed while drawing a bow."
}
```

---

## `neoorigins:prevent_item_damage`

Stops items from losing durability while the holder has the power. Unlike handing out an unbreakable item at spawn, this is a trait of the origin: any matching tool, weapon, or armor the player uses simply never takes durability damage. Implemented via a `ItemStack.hurtAndBreak` mixin, so it covers every durability path (mining, attacking, blocking, casting).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `items` | array | no | `[]` | Item ids or `#tags` spared from durability loss. Empty list (default) protects **every** damageable item the holder uses. |

**Example: unbreaking everything**
```json
{
  "type": "neoorigins:prevent_item_damage",
  "name": "Tireless Hands",
  "description": "Your gear never wears down."
}
```

**Example: only tools and bows**
```json
{
  "type": "neoorigins:prevent_item_damage",
  "items": ["#minecraft:pickaxes", "#minecraft:axes", "minecraft:bow"],
  "name": "Curator",
  "description": "Your tools and bow never break."
}
```

---

## `neoorigins:attract_mobs`

Pulls nearby mobs toward the holder each tick, as though the player were holding the mob's favourite food. Drawn mobs simply path to the player; they are not tamed and do not turn hostile. With no `entity_types` filter only animals (the vanilla follows-food set) are pulled; supplying ids/tags widens or replaces that to any matching mob.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | number | no | `8.0` | Blocks around the holder within which mobs are pulled in |
| `speed` | number | no | `1.0` | Movement-speed multiplier mobs path toward the holder at |
| `entity_types` | array | no | `[]` | Entity ids or `#tags` drawn in; empty (default) pulls only animals |
| `entity_blacklist` | array | no | `[]` | Entity ids or `#tags` excluded from attraction even when `entity_types` (or the animal default) would select them |

**Example: pied piper of animals**
```json
{
  "type": "neoorigins:attract_mobs",
  "radius": 12,
  "name": "Beast Whisperer",
  "description": "Nearby animals follow you as if you held their favourite food."
}
```

---

## `neoorigins:no_projectile_divergence`

Removes projectile divergence (perfect accuracy) for projectiles shot by the player. Handled via `EntityJoinLevelEvent`.

No fields beyond `name` / `description`.

**Example:**
```json
{
  "type": "neoorigins:no_projectile_divergence",
  "name": "Dead Eye",
  "description": "Arrows and projectiles fly true."
}
```

---

## `neoorigins:quality_equipment`

Equipment the player crafts or upgrades at a smithing table receives bonus attributes. Tools mine faster, weapons hit harder, armor is tougher, and everything lasts longer.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `bonus_mining_speed` | double | no | `0.25` | Mining speed multiplier for tools (+25%) |
| `bonus_attack_damage` | double | no | `0.20` | Attack damage multiplier for weapons (+20%) |
| `bonus_armor_toughness` | double | no | `1.0` | Flat armor toughness bonus for armor pieces |
| `durability_multiplier` | double | no | `0.10` | Max durability increase for all damageable items (+10%) |
| `intercept_menus` | array | no | `[]` | Extra menu-type ids whose result slot also grants the buff when the finished item is taken. |

**Working with modded workstations:** the buff normally rides the vanilla crafting and smithing paths. `intercept_menus` opts additional container screens in by menu-type id, so an item taken out of, say, a mod's own smithing anvil is buffed the same way:

```json
"intercept_menus": [
  "overgeared:smithing_anvil_menu",
  "overgeared:stone_smithing_anvil_menu"
]
```

Ids that no loaded mod registers simply never match, so listing a menu from a mod the player does not have is harmless: no `required_mods` guard is needed just for this field.

**What gets buffed:**
- **Tools** (pickaxe, axe, shovel, hoe, shears): +mining speed via `MINING_EFFICIENCY` attribute
- **Weapons** (sword, axe, trident): +attack damage via `ATTACK_DAMAGE` attribute
- **Armor** (helmet, chest, legs, boots): +armor toughness via `ARMOR_TOUGHNESS` attribute
- **All damageable items**: +max durability via `MAX_DAMAGE` component

**Example:**
```json
{
  "type": "neoorigins:quality_equipment",
  "name": "Quality Craftsmanship",
  "description": "Tools mine faster, weapons hit harder, armor is tougher, and everything lasts longer."
}
```

Bonuses are applied at craft/smelt time via attribute modifiers stored on the item. Items already carrying the quality modifier are not double-buffed.

Smithing-table upgrades are part of the buffed surface: when a quality item is upgraded (e.g. diamond → netherite), the attribute snapshot is rebuilt from the upgraded item's own base stats and the durability bonus is recomputed against the upgraded item's base durability, so the bonus scales with the new material instead of carrying the old item's stale values.

---

## `neoorigins:more_smoker_xp`

Grants bonus nutrition and saturation to food cooked in a smoker or furnace by the player. Applied at smelt-finish time.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `multiplier` | float | no | `2.0` | Nutrition/saturation bonus multiplier |

---

## `neoorigins:trade_availability`

Periodically resets villager trade uses for every villager within a radius of the player.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `scan_interval` | int | no | `40` | Ticks between scans |
| `radius` | double | no | `8.0` | Scan radius in blocks |

**Example:**
```json
{
  "type": "neoorigins:trade_availability",
  "scan_interval": 40,
  "radius": 8.0,
  "name": "Charismatic",
  "description": "Villagers near you reset their trades."
}
```

---

## `neoorigins:rare_wandering_loot`

Adds rare items to the wandering-trader pool. This is a **global effect** hooked via `WandererTradesEvent` at mod init; the power's presence on any player enables it worldwide.

No fields beyond `name` / `description`.

**Example:**
```json
{
  "type": "neoorigins:rare_wandering_loot",
  "name": "Curio Magnet",
  "description": "Wandering traders bring rarer wares while you're around."
}
```

---

## `neoorigins:sneaky`

Reduces mob detection range: hostile mobs only target the player when significantly closer than normal. Handled via `LivingChangeTargetEvent`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `detection_multiplier` | double | no | `0.3` | Detection range multiplier (`0.3` = mobs see you at 30% of their normal range) |

**Example:**
```json
{
  "type": "neoorigins:sneaky",
  "detection_multiplier": 0.3,
  "name": "Shadow Tread",
  "description": "Mobs notice you from much closer than usual."
}
```

---

## `neoorigins:muffle_sound`

Suppresses the game-event vibrations the player emits, so sculk sensors, calibrated sculk sensors and wardens stop detecting their footsteps, item use, block interactions and the like. Handled via NeoForge's cancelable `VanillaGameEvent`: when the cause is a player holding this power, the emission is canceled before any `GameEventListener` (sculk / warden) receives it.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `strength` | double | no | `1.0` | Fraction (`0.0`-`1.0`) of emitted vibrations suppressed. `1.0` = fully silent; `0.0` = no muffling; `0.5` = roughly half the player's vibrations are dropped. |

**Example:**
```json
{
  "type": "neoorigins:muffle_sound",
  "strength": 1.0,
  "name": "Silent Step",
  "description": "Sculk sensors and the Warden can no longer hear you."
}
```

---

## `neoorigins:stealth`

After sneaking continuously for a threshold number of ticks, the player gains Invisibility. The effect clears when sneaking stops. Toggleable off via keybind.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `activation_ticks` | int | no | `200` | Sneak time required before invisibility kicks in (10 s default) |

**Example:**
```json
{
  "type": "neoorigins:stealth",
  "activation_ticks": 100,
  "name": "Vanish",
  "description": "Sneak for 5 seconds to turn invisible."
}
```

---

## `neoorigins:tree_felling`

When the player breaks a log, BFS/DFS upward to break all connected logs. Skipped while sneaking so pack authors can still harvest single logs. Handled via `BlockEvent.BreakEvent`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `max_blocks` | int | no | `64` | Maximum connected logs to break per activation |

**Example:**
```json
{
  "type": "neoorigins:tree_felling",
  "max_blocks": 64,
  "name": "Arboreal Sense",
  "description": "Fells an entire tree when you chop its base. Sneak to break single logs."
}
```

---

## `neoorigins:ultimine`

Grants [FTB Ultimine](https://www.curseforge.com/minecraft/mc-mods/ftb-ultimine) vein-mining to the holder. Soft dependency on `ftbultimine`: when that mod is absent this power is an inert marker and does nothing.

When FTB Ultimine is installed, NeoOrigins registers a restriction handler that permits vein-mining only for players with an **active** `neoorigins:ultimine` power and denies it for everyone else. Because FTB Ultimine treats restriction handlers as an AND-gate (the first handler that disallows wins), adding this power to any origin turns vein-mining into an origin-gated ability: only that origin's holders may ultimine.

The integration is completely dormant unless a loaded pack defines a `neoorigins:ultimine` power. While no pack uses it, FTB Ultimine behaves exactly as vanilla (the AND-gate handler stays out of the way for everyone). Once at least one `ultimine` power is loaded, vein-mining is restricted to players who hold an ultimine power. This keeps the soft dependency truly opt-in: installing both mods together changes nothing until a pack adds the power.

This power carries no config. FTB Ultimine's restriction hook is a coarse allow/deny permission check with no setter for the per-player block limit, the require-tool toggle, or the mining shape, so those are **not** exposed here. The block count, tool requirement, cooldown, exhaustion/XP cost, and shape all follow FTB Ultimine's own server config (and any FTB Ranks / attribute overrides). Modeling fields the API cannot honour would be misleading, so the power is deliberately config-light: "while this power is active you may vein-mine."

**Example:**
```json
{
  "type": "neoorigins:ultimine",
  "name": "Excavator",
  "description": "Hold the ultimine key and break a block to vein-mine the whole vein. Limits follow the server's FTB Ultimine config."
}
```

---

## `neoorigins:craft_amount_bonus`

Grants bonus items when crafting a specific output (e.g., more planks per log). Hooks `PlayerEvent.ItemCraftedEvent` directly, so bonuses only trigger on genuine crafting operations.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `output_item` | Identifier | no | `minecraft:oak_planks` | Item to watch for |
| `bonus_count` | int | no | `4` | Maximum extra copies granted per craft |

**Example: quadruple planks from logs**
```json
{
  "type": "neoorigins:craft_amount_bonus",
  "output_item": "minecraft:oak_planks",
  "bonus_count": 4,
  "name": "Lumberwright",
  "description": "Gets extra planks when crafting from logs."
}
```

The bonus fires once per craft event; shift-clicking triggers one event per output stack.

---

## `neoorigins:tamed_animal_boost`

Boosts stats (max health, movement speed) on every tamed animal owned by the player within a radius. Applies transient attribute modifiers with fixed IDs; modifiers are removed when the power is revoked.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `health_bonus` | float | no | `4.0` | `ADD_VALUE` bonus on max health |
| `speed_bonus` | float | no | `0.1` | `ADD_VALUE` bonus on movement speed |
| `radius` | double | no | `32.0` | Scan radius |

**Example:**
```json
{
  "type": "neoorigins:tamed_animal_boost",
  "health_bonus": 4.0,
  "speed_bonus": 0.1,
  "radius": 32.0,
  "name": "Kinship",
  "description": "Your tamed animals are hardier and faster."
}
```

---

## `neoorigins:tamed_potion_diffusal`

When the player receives a positive mob effect, it's also applied to nearby tamed animals owned by the player. Handled via `MobEffectEvent.Added`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | double | no | `16.0` | Scan radius for nearby tamed animals |

**Example:**
```json
{
  "type": "neoorigins:tamed_potion_diffusal",
  "radius": 16.0,
  "name": "Shared Fortitude",
  "description": "Positive potion effects also buff your tamed animals."
}
```

---

# Pack-author patterns

Recipes that compose existing power types to cover use cases the built-in types don't expose directly. Most rely on the Apoli compat surface (`neoorigins:`-prefixed types handled by `OriginsCompatPowerLoader`).

## Periodic feed / heal via `neoorigins:action_over_time`

NeoOrigins' built-in `neoorigins:tick_action` ships no behaviour at all: it dispatches nothing. For any periodic action (periodically restore hunger, heal a fixed amount, apply an effect, run an arbitrary entity-action), use `neoorigins:action_over_time`. It is an alias for [`neoorigins:condition_passive`](#neooriginscondition_passive) (identical fields), so an optional `condition` gates whether the action runs each interval.

**Periodic hunger restoration** (e.g. for an origin that doesn't eat conventionally):
```json
{
  "type": "neoorigins:action_over_time",
  "interval": 40,
  "entity_action": {
    "type": "neoorigins:feed",
    "food": 1,
    "saturation": 0.2
  }
}
```

**Periodic healing** (independent of food / `natural_regen_modifier`):
```json
{
  "type": "neoorigins:action_over_time",
  "interval": 60,
  "entity_action": {
    "type": "neoorigins:heal",
    "amount": 0.5
  }
}
```

`interval` is in ticks (20 = 1 second). The `entity_action` runs against the player. An optional `condition` (any `ConditionParser` verb, e.g. `neoorigins:exposed_to_sun`) gates each run. Any verb supported by `ActionParser` works (`neoorigins:apply_effect`, `neoorigins:damage`, `neoorigins:execute_command`, `neoorigins:if_else` for conditional wrapping, etc.).

## `neoorigins:cobweb_affinity`

Spider-like mobility through cobwebs. Emits the `cobweb_affinity` capability tag; `EntityMakeStuckInBlockMixin` reads it to suppress the usual slowdown inside cobwebs, and `MovementPowerEvents.onBreakSpeed` reads it to multiply cobweb break speed by 10×.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| (no fields) | — | — | — | Marker power. |

**Example:**
```json
{
  "type": "neoorigins:cobweb_affinity",
  "name": "Spider Affinity",
  "description": "Move and break cobwebs easily."
}
```

---

## `neoorigins:hide_hud_bar`

Hides a HUD bar while the power is active. Emits `hide_hunger_bar` or `hide_air_bar` capability tags; `GuiHudBarsMixin` reads them to cancel the matching render call. Governed server-side by the `hide_hud_bars` common config (default true); if disabled, the power registers but the HUD still renders.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `bar` | string | no | `"hunger"` | Which bar to hide. `"hunger"` / `"food"` hide the hunger bar; `"air"` / `"oxygen"` / `"breath"` hide the air bar. Any other value is a no-op. |

**Example:**
```json
{
  "type": "neoorigins:hide_hud_bar",
  "bar": "air",
  "name": "No Need to Breathe",
  "hidden": true
}
```

Typically paired with `neoorigins:water_breathing` or the Automaton pattern so the hidden bar is also being suppressed mechanically; hiding a bar that still ticks is disorienting.

---

## `neoorigins:particle`

Spawns vanilla particles on the player at a fixed cadence. Server-side `ServerLevel.sendParticles` packetizes to nearby clients, so this works on dedicated servers without a client-side mixin.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `particle` | string \| object | yes | — | Registry id of any `SimpleParticleType` (e.g. `"minecraft:end_rod"`). For parameterized particles use the object form: `{ "type": "minecraft:dust", "color": [1.0, 0.85, 0.2], "scale": 0.6 }` |
| `frequency` | int | no | `8` | Emit every N server ticks. Lower = denser. |
| `count` | int | no | `1` | Particles per emission. |
| `spread` | `[x, y, z]` floats | no | `[0.25, 0.5, 0.25]` | Random offset spread per particle. |
| `offset` | `[x, y, z]` floats | no | `[0.0, 1.0, 0.0]` | Origin offset from player feet (defaults to ~chest height). |
| `speed` | float | no | `0.0` | Vanilla "speed" parameter: passes through to `sendParticles`. Most particle types use this as initial-velocity scale; some ignore it. |
| `condition` | EntityCondition | no | always-true | Optional gate evaluated each emission tick. |

The Origins/Apoli `particle` power type is auto-translated to this; packs that already use `origins:particle`, `apoli:particle`, `apace:particle`, or `apugli:particle` work without modification (`frequency` and `particle` fields map 1:1).

**Example: ambient sparkle aura**
```json
{
  "type": "neoorigins:particle",
  "particle": "minecraft:end_rod",
  "frequency": 6,
  "count": 1,
  "spread": [0.4, 0.6, 0.4],
  "name": "Blessed Aura",
  "description": "Faint sparkles trail you wherever you walk."
}
```

**Example: colored gold dust, water-only**
```json
{
  "type": "neoorigins:particle",
  "particle": { "type": "minecraft:dust", "color": [1.0, 0.85, 0.2], "scale": 0.6 },
  "frequency": 10,
  "count": 2,
  "condition": { "type": "neoorigins:in_water" },
  "name": "Gilded Wake"
}
```

Sparkle-aesthetic vanilla particle picks: `minecraft:end_rod` (cleanest white twinkle), `minecraft:firework` (bright sparks, heavier), `minecraft:enchant` (swirling glyphs), `minecraft:wax_on` (puff burst), `minecraft:scrape` (copper sparks), `minecraft:totem_of_undying` (green/gold festive), `minecraft:glow` (Allay-style soft dots), `minecraft:nautilus` (subtle blue specs).

---

## `neoorigins:ender_gaze_immunity`

Endermen do not aggro when the player looks at them. Emits the `ender_gaze_immunity` capability tag; an Enderman targeting mixin reads it to skip the usual line-of-sight check.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| (no fields) | — | — | — | Marker power. |

**Example:**
```json
{
  "type": "neoorigins:ender_gaze_immunity",
  "name": "Void Gaze",
  "description": "Endermen ignore your gaze."
}
```

---

## `neoorigins:xeno_passive`

Soft-dependency compat with the Aliens vs Predator mod (`avp_alien`). Emits the `xeno_passive` capability tag, which an `@Pseudo` mixin reads from inside AvP's `AlienPredicates.isHost` to short-circuit host eligibility: facehuggers (and the ovomorph hatch-desire / parasite-attachment logic gated off the same predicate) stop treating the holder as a viable host, as if already infected or immune.

This only governs the facehugger host gate. The companion "xenomorphs ignore me entirely" behaviour is delivered separately via a `mobs_ignore_player` power scoped to the `#avp_alien:aliens` / `#avp_alien:parasites` tags. When AvP is not installed the capability simply goes unread and this power is an inert marker.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| (no fields) | — | — | — | Marker power. |

**Example:**
```json
{
  "type": "neoorigins:xeno_passive",
  "name": "Tainted Blood",
  "description": "Facehuggers find you an unfit host."
}
```

---

## `neoorigins:natural_glide`

Grants elytra-style gliding without needing to equip an elytra. Press jump while falling to start fall-flying, exactly as if the player were wearing one.

Emits the `natural_glide` capability tag. The `PlayerStartFallFlyingMixin` reads it at the head of `Player.tryToStartFallFlying` and bypasses the standard chest-slot elytra check, calling `startFallFlying()` directly.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `render_elytra` | boolean | no | `false` | Whether an elytra is drawn on the player's back while fall-flying. Flight works either way; this is cosmetic only. Defaults `false`: this power glides with no visible wings unless you ask for them. |
| `texture_location` | identifier | no | vanilla elytra | Custom texture for the drawn elytra, e.g. `mymod:textures/entity/my_wings.png`. Only applies when `render_elytra` is true; the model stays the vanilla elytra (texture swap only). Omit for the vanilla elytra texture. |

**Example: Phantom spectral wings**
```json
{
  "type": "neoorigins:natural_glide",
  "render_elytra": true,
  "name": "Spectral Wings",
  "description": "Glide like an elytra user, no item required. Press jump while falling to spread your wings."
}
```

Preconditions match vanilla: not on ground, not already fall-flying, not in water, not levitating. Pair with `neoorigins:elytra_boost` for a full glide + launch-boost kit.

**Why wings need asking for.** The glide runs with an empty chest slot, and vanilla's elytra render layer keys off the equipped item, so without `render_elytra` the player flies with nothing on their back. The field defaults `false` here rather than `true` so packs authored against the wingless behaviour keep it, including origins that already supply their own wing model. `neoorigins:elytra_flight` is the same mechanic with the default flipped on.

Contrast with `neoorigins:flight`: both are pitch-based elytra gliding, but `flight` is a toggle launched by a mid-air jump, while `natural_glide` is always available and starts from a fall. For creative-mode hover, see `neoorigins:creative_flight`.

---

## `neoorigins:elytra_flight`

The native mirror of `apoli:elytra_flight`: grants elytra-style fall-flight without an equipped elytra, and optionally draws an elytra on the player's back while gliding.

Flight itself reuses natural glide: this power emits the `natural_glide` capability, so it drives the exact same activation path (press jump while falling to spread your wings). Everything `neoorigins:natural_glide` does, `elytra_flight` does too; it just adds the cosmetic wing render on top.

The wings are purely cosmetic: gliding works the same whether or not an elytra is drawn. When drawn, the model is always the vanilla elytra; `texture_location` only swaps the texture on that model. If the player is wearing a real equipped elytra, vanilla renders it and this power stays out of the way (no double wings).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `render_elytra` | boolean | no | `true` | Whether an elytra is drawn on the player's back while fall-flying. Flight works either way; this is cosmetic only. Set `false` to glide with no visible wings. |
| `texture_location` | identifier | no | vanilla elytra | Custom texture for the drawn elytra, e.g. `mymod:textures/entity/my_wings.png`. Only applies when `render_elytra` is true; the model stays the vanilla elytra (texture swap only). Omit for the vanilla elytra texture. |

**Example: spectral wings you can see**
```json
{
  "type": "neoorigins:elytra_flight",
  "render_elytra": true,
  "texture_location": "neoorigins:textures/entity/spectral_wings.png",
  "name": "Spectral Wings",
  "description": "Glide like an elytra user — no item required — with shimmering wings on your back."
}
```

**Example: invisible glide (no wings drawn)**
```json
{
  "type": "neoorigins:elytra_flight",
  "render_elytra": false,
  "name": "Phantom Drift",
  "description": "Glide with no visible wings."
}
```

If `render_elytra` is false, this is behaviourally identical to `neoorigins:natural_glide`. Pair either with `neoorigins:elytra_boost` for a full glide + launch-boost kit.

---

## `neoorigins:bare_hand_tool`

Makes the player's empty hand behave like a specific vanilla tool for block-break purposes: tool-tier drop eligibility **and** break speed both match the configured tool item. Point at any tool item ID and the runtime looks up its tool component to determine which blocks qualify and at what speed.

Emits a capability tag of the form `bare_hand_tool:<tool_id>` (e.g. `bare_hand_tool:minecraft:stone_pickaxe`): the tool ID is encoded in the tag so the client-side break-speed predictor and the server-side harvest check can both reach the answer without extra sync state. Wired via `BareHandToolEvents` on the NeoForge event bus (`PlayerEvent.HarvestCheck` + `PlayerEvent.BreakSpeed`). No mixins required.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `tool` | Identifier | no | `minecraft:stone_pickaxe` | Any vanilla tool item ID. Determines both break-speed and drop-eligibility. |

**Example: Caveborn mines like a stone pickaxe with bare hands**
```json
{
  "type": "neoorigins:bare_hand_tool",
  "tool": "minecraft:stone_pickaxe",
  "name": "Stone Fists",
  "description": "Bare hands mine like a stone pickaxe — break ores and stone without tools."
}
```

An origin can stack multiple instances to emulate several tool types simultaneously (e.g. a miner + lumberjack hybrid with both a pickaxe and an axe instance). When the player's hand is empty the event handler iterates all active `bare_hand_tool` capabilities and picks the first that can correctly harvest the target; different tool types don't conflict.

Only fires when the main hand is empty. Holding any item delegates to vanilla behaviour normally.

---

## `neoorigins:fortune_when_effect`

Applies a virtual Fortune-level drop multiplier whenever a configured `MobEffect` is active on the player. The vanilla `ApplyBonusCount.ORE_DROPS` formula is used, giving the same rolling distribution as a real Fortune N pickaxe (`count × (max(0, random(level + 2) - 1) + 1)`).

Deliberately generic — any origin can emulate an enchantment-like buff by pairing this with any MobEffect (e.g. Caveborn's Mining Fortune is gated by `minecraft:luck` granted via eating diamond). Wired via `FortuneEffectEvents` subscribing to `BlockDropsEvent` server-side.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effect` | Identifier | no | `minecraft:luck` | The gating MobEffect ID. Bonus only applies while this effect is active on the player. |
| `level` | int | no | `2` | Virtual Fortune level to roll. Higher = more extra drops. |
| `target` | string | no | `#c:ores` | Block tag the bonus applies to. Use `#tagname` syntax. Defaults to the NeoForge common ores tag; pack authors can narrow to a vanilla sub-tag like `#minecraft:diamond_ores` or a custom tag. |

**Vanilla parity:** `minecraft:ancient_debris` is hardcoded-excluded because netherite is the single vanilla ore that ignores Fortune. Every other vanilla ore (iron, gold, copper, coal, diamond, emerald, lapis, redstone, nether_gold, nether_quartz) is covered by `#c:ores` and will roll the bonus normally.

**Example: Caveborn Mining Fortune (gated by Luck from eating diamond)**
```json
{
  "type": "neoorigins:fortune_when_effect",
  "effect": "minecraft:luck",
  "level": 2,
  "target": "#c:ores",
  "name": "Mining Fortune",
  "description": "While Luck is active, ore blocks drop as if mined with Fortune II."
}
```

Stacking isn't additive: only the first matching power fires per break. Author variants as separate powers with different effect gates rather than expecting them to compound.

---

## `neoorigins:dodge_chance`

Percentage chance to completely dodge incoming damage. When triggered, the damage event is cancelled entirely. Applied via `CombatPowerEvents`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `chance` | float | no | `0.15` | Dodge probability (0.0–1.0). 0.15 = 15% dodge. |

**Example:**
```json
{
  "type": "neoorigins:dodge_chance",
  "chance": 0.2,
  "name": "Evasion",
  "description": "20% chance to dodge incoming attacks."
}
```

---

## `neoorigins:thorns_on_hit`

Passive thorns — when the player takes melee damage, the attacker takes damage back. Optionally sets the attacker on fire. Applied via `CombatPowerEvents`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `damage` | float | no | `2.0` | Thorns damage dealt to the attacker. |
| `fire_ticks` | int | no | `0` | Fire ticks applied to the attacker (0 = no fire). |

**Example:**
```json
{
  "type": "neoorigins:thorns_on_hit",
  "damage": 3.0,
  "fire_ticks": 40,
  "name": "Ember Thorns",
  "description": "Melee attackers take 3 damage and catch fire."
}
```

---

## `neoorigins:light_level_effect`

Applies a status effect when the player is at or below a certain light level. Removes the effect when they move to brighter light.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `max_light_level` | int | no | `4` | Light level threshold (inclusive). |
| `effect` | resource location | yes | — | Status effect to apply. |
| `amplifier` | int | no | `0` | Effect amplifier. |
| `ambient` | boolean | no | `true` | Ambient effect flag. |
| `show_particles` | boolean | no | `false` | Show effect particles. |
| `show_icon` | boolean | no | `false` | Show effect icon on HUD. |

**Example: invisibility in darkness**
```json
{
  "type": "neoorigins:light_level_effect",
  "max_light_level": 4,
  "effect": "minecraft:invisibility",
  "name": "Shadow Meld",
  "description": "Become invisible in darkness."
}
```

---

## `neoorigins:low_hp_threshold`

Applies one or more status effects when the player's HP drops below a percentage threshold. Effects are removed when HP rises above the threshold.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `threshold` | float | no | `0.5` | HP fraction (0.0–1.0). 0.5 = below 50%. |
| `effects` | list of `{ effect, amplifier }` | yes | — | Effects to apply while below threshold. |

**Example: berserker rage below 25% HP**
```json
{
  "type": "neoorigins:low_hp_threshold",
  "threshold": 0.25,
  "effects": [
    { "effect": "minecraft:strength", "amplifier": 1 },
    { "effect": "minecraft:speed", "amplifier": 0 }
  ],
  "name": "Death's Embrace",
  "description": "Gain Strength II and Speed below 25% HP."
}
```

---

## `neoorigins:burn`

Sets the player on fire at a configurable interval. Used for origins that are perpetually burning or catch fire under certain conditions (pair with a `condition` on the power JSON to gate on daylight, biome, etc.).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `interval` | int | no | `20` | Ticks between fire applications. |
| `burn_duration` | int | no | `100` | Fire duration in ticks per application. |

Origins compat: translates `origins:burn`.

**Example: smoulder in sunlight**
```json
{
  "type": "neoorigins:burn",
  "interval": 40,
  "burn_duration": 60,
  "condition": { "type": "neoorigins:exposed_to_sun" },
  "name": "Sun Scorch",
  "description": "Burns when exposed to direct sunlight."
}
```

---

## `neoorigins:walk_on_fluid`

Allows the player to walk on the surface of a fluid (water, lava, or both), using the same vanilla mechanic as Striders (`LivingEntity.canStandOnFluid`). The player can still dive by jumping into the fluid.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `fluid` | string | no | `both` | Which fluid to walk on: `water`, `lava`, or `both` |

**Example: walk on water**
```json
{
  "type": "neoorigins:walk_on_fluid",
  "fluid": "water",
  "name": "Water Walking",
  "description": "Can walk on water surfaces."
}
```

Origins compat: translates `origins:walk_on_fluid`.

---

## `neoorigins:extra_inventory`

Gives the player an extra inventory opened via the skill keybind. Uses vanilla's chest UI, dynamically sized to fit the configured slot count (1-6 rows). Contents are persisted across sessions.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `size` | int | no | `9` | Number of slots (rounded up to nearest row of 9, max 54 = 6 rows) |
| `drop_on_death` | bool | no | `false` | Whether to drop contents on death |
| `title` | string | no | `""` | Literal title drawn on the container screen. Blank/absent uses the translatable `container.neoorigins.extra_inventory`. Plain text, not a JSON text component. |

**Example: 27-slot extra inventory**
```json
{
  "type": "neoorigins:extra_inventory",
  "size": 27,
  "name": "Shulker Inventory",
  "description": "Press your skill key to open an extra inventory."
}
```

Origins compat: translates `origins:inventory` / `origins:shulker_inventory`.

---

## `neoorigins:ignore_water`

Makes the player unaffected by water: full movement speed in water (via `water_movement_efficiency` attribute) and immune to water-current pushing (via `EntityIgnoreWaterMixin`). Emits the `ignore_water` capability tag.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| (no fields) | — | — | — | Marker power. |

Origins compat: translates `origins:ignore_water`.

**Example:**
```json
{
  "type": "neoorigins:ignore_water",
  "name": "Hydrophobic",
  "description": "Water doesn't slow you down or push you around."
}
```

---

## `neoorigins:ignore_fluid`

Makes the player **totally** ignore one or more fluids. Where `ignore_water` only removes water's speed penalty and current pushing, this power removes the fluid from the player's world entirely: no buoyancy, no drag, no current push, no drowning, no lava burn, no fog or screen overlay, no swim pose.

It does that by intercepting fluid **detection** rather than each individual effect. `EntityFluidInteractionIgnoreFluidMixin` hands back an empty fluid state for ignored fluids inside `EntityFluidInteraction.update`, the single body scan that feeds buoyancy, drag, current push, `isInWater()`/`isInLava()`, `lavaHurt()`, the swim pose, drowning and the air bar. `CameraIgnoreFluidMixin` does the same for `Camera.getFluidInCamera`, which decides fog and the underwater distortion without ever consulting the entity. `BlockStateBaseIgnoreFluidMixin` cancels `entityInside` for the fluid's own block, which is how a mod-authored fluid applies its own damage or effects.

Because every vanilla fluid behaviour reads those same values, they all fall away together, and so do most third-party mods, since they query the same methods.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `fluid` | string or array | no | — | Fluid to ignore: a fluid id (`"minecraft:lava"`), a fluid tag (`"#c:milk"`), or an array of either |
| `fluids` | array | no | — | Plural spelling of `fluid`. Both keys are read and their entries merged |

Naming any fluid of a fluid type ignores the whole type, so `"minecraft:water"` also covers `minecraft:flowing_water` — that is what makes poured buckets work. Entries without a namespace get `minecraft:` (so `"lava"` means `"minecraft:lava"`). If neither key is given the power defaults to water and lava, so a marker-only entry is never a silent no-op. Unknown or misspelled ids are dropped quietly rather than failing the datapack load: the power simply ignores nothing for that entry.

Emits the `ignore_fluid` capability tag plus one `ignore_fluid:<id>` tag per entry, so the client and server agree without any extra sync.

**Modded fluids on this version.** The field shape is deliberately identical across every branch of the mod, so a datapack naming a modded fluid loads and validates here exactly as it does on 1.21.1. The physics do not follow. This version of Minecraft tracks entity fluid interaction only for fluids in the `minecraft:water` and `minecraft:lava` fluid tags, so an entry naming a fluid outside those two tags is accepted, syncs its capability tag, and drives nothing on the movement side. The `entityInside` guard still applies, which means a modded fluid's own damage or effects are still cancelled even though its buoyancy and drag are not. Write the pack for 1.21.1 and it will not break here; just do not expect a modded fluid to stop pushing you.

**Not covered.** A mod that runs its own `level.getFluidState(pos)` check per tick — rather than asking the entity whether it is in a fluid — is doing its own detection and cannot be intercepted from here; it will still see the fluid. The same goes for anything keyed off the block state rather than the fluid state (a mod checking `state.is(Blocks.LAVA)` directly). Fluid **rendering** is untouched: the fluid is still drawn normally, you simply pass through it as if it were air.

One deliberate side effect: the `entityInside` guard is keyed on the block's fluid state, so a *waterlogged* block's own `entityInside` behaviour is suppressed too while you ignore water. In practice that means things like bubble columns stop pushing you, which is the intended reading of "water does not affect me".

`ignore_water` is unchanged and still supported; use it when you only want water's movement penalty gone, and this power when you want the fluid to stop existing for the player.

**Example: swim through lava unharmed**
```json
{
  "type": "neoorigins:ignore_fluid",
  "fluid": "minecraft:lava",
  "name": "Magma Born",
  "description": "Lava does not slow, push, burn or blind you."
}
```

**Example: ignore several fluids, including a modded one**
```json
{
  "type": "neoorigins:ignore_fluid",
  "fluids": ["minecraft:water", "minecraft:lava", "create:honey"],
  "name": "Untouchable",
  "description": "Liquids simply don't register."
}
```

---

## `neoorigins:overlay`

Renders a full-screen texture overlay on the player's HUD. Client-side only: the server emits a capability tag encoding the texture path and strength; `VisualEffectsHandler` on the client reads it and draws the overlay.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `texture` | resource location | yes | — | Overlay texture (e.g. `"minecraft:textures/misc/pumpkinblur.png"`). |
| `strength` | float | no | `1.0` | Opacity (0.0 = invisible, 1.0 = fully opaque). |

Origins compat: translates `origins:overlay`.

**Example: dim vignette**
```json
{
  "type": "neoorigins:overlay",
  "texture": "minecraft:textures/misc/pumpkinblur.png",
  "strength": 0.3,
  "name": "Tunnel Vision",
  "description": "Your peripheral vision is dimmed."
}
```

---

## `neoorigins:model_color`

Tints the player model with an RGBA colour. Client-side rendering applies a colour multiply via `RenderSystem.setShaderColor` during the player render pass.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `red` | float | no | `1.0` | Red channel (0.0–1.0). |
| `green` | float | no | `1.0` | Green channel (0.0–1.0). |
| `blue` | float | no | `1.0` | Blue channel (0.0–1.0). |
| `alpha` | float | no | `1.0` | Alpha channel (0.0–1.0). |
| `condition` | object | no | — | EntityCondition that gates when the tint is applied. When absent, the tint is always active. When present, the colour only shows while the condition evaluates true. |
| `enabled` | bool | no | `true` | Kill switch: when false the power stays attached but never tints. Lets a server owner drop one tint from `power_overrides.toml` without editing the datapack. |

When two or more `model_color` powers are active at once, the channels are averaged rather than one winning: two tints produce their midpoint, three their mean. A conditioned tint is re-evaluated once a second, so it appears and clears as the condition flips rather than only on login or toggle.

Origins compat: translates `origins:model_color`.

**Example: ghostly blue tint**
```json
{
  "type": "neoorigins:model_color",
  "red": 0.6,
  "green": 0.7,
  "blue": 1.0,
  "alpha": 0.8,
  "name": "Spectral Hue",
  "description": "Your form shimmers with a faint blue glow."
}
```

**Example: red glow at low health**
```json
{
  "type": "neoorigins:model_color",
  "red": 0.9, "green": 0.2, "blue": 0.2, "alpha": 0.7,
  "condition": { "type": "neoorigins:health", "comparison": "<=", "compare_to": 6 },
  "name": "Blood Rage",
  "description": "Your body glows red when near death."
}
```

---

## `neoorigins:entity_model`

Overrides the player's rendered model with another entity's model: a "morph". The server resolves the power's config into a single morph description and broadcasts it to every client tracking the player, which renders a cached stand-in entity of that type in place of the player, for both the morphed player (third-person) and everyone who can see them. A morph changes how the player sounds and how big they are as well as how they look.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `morph` | string | no | — | Named morph definition to use, e.g. `neoorigins:sheep`. A bare name resolves in the `neoorigins` namespace. See [Morph definitions](#morph-definitions) below. |
| `entity_type` | string | no | — | Entity id whose model replaces the player's, e.g. `minecraft:slime`. It also supplies the morph's voice and collision box. |
| `nbt` | object | no | — | Partial NBT applied to the stand-in to pick a variant, e.g. `{"Color": 14}` for a red sheep. |
| `scale` | number | no | `1.0` | Uniform scale of the morph model, and of its collision box along with it. |
| `hitbox` | boolean | no | `true` | Whether the player collides at `entity_type`'s size instead of their own. See [Morph hitbox](#morph-hitbox) below. |
| `render_held_item` | boolean | no | `true` | Whether the player's held items are drawn on the morph, so others can see what they're carrying. A morph with hands holds the item; one without floats it in front of the body instead. |
| `render_armor` | boolean | no | `true` | Whether the player's worn armour is drawn on the morph. Only takes effect on morphs whose entity renders armour at all: zombies, skeletons and piglins do; sheep, slimes and villagers do not. |
| `first_person` | enum | no | `item` | What the morphed player sees in their own view: `item` draws the held item without the vanilla arm, `arm` also draws the morph's own arm, `hidden` draws nothing. See [First-person view](#first-person-view) below. |
| `arm` | string | no | — | Name of the bone to draw as the morph's arm in `arm` mode. See [First-person view](#first-person-view) below. |
| `skin` | object | no | — | Reskin the player instead of replacing their model. See [Skin morphs](#skin-morphs) below. |
| `entity_sounds` | boolean | no | `true` | Whether the player borrows the voice of `entity_type`. See [Morph sounds](#morph-sounds) below. |
| `sounds` | object | no | — | Explicit sound overrides, layered over that voice. See [Morph sounds](#morph-sounds) below. |

Every field is optional because a morph can be described two ways: inline, or by referencing a named definition with `morph`. When both are present the inline fields win, so a pack can reuse a definition and tweak one detail. A power with neither leaves the player looking like themselves.

### First-person view

Everyone else sees the morph. The morphed player mostly sees their own hands, and those hands are the one part of the player a morph cannot simply replace: vanilla draws a player arm there, and a slime has no arm to put in its place. `first_person` picks which of the three honest answers you want:

| Value | What the player sees |
|---|---|
| `item` | The held item, floating, with no arm behind it. The default: it works for every morph, because it never needs the morph to have an arm. |
| `arm` | The morph's own arm when the hand is empty, and the held item when it isn't, the same rule vanilla uses for the player's arm. |
| `hidden` | Nothing. Right for a morph that shouldn't have visible hands at all. |

`arm` is worth setting for humanoid morphs (a zombie, a skeleton, a piglin, a villager) where the arm exists and reads correctly. On a morph with no arm bone it costs nothing: the view falls back to the `item` behaviour rather than showing something wrong.

The bone is found automatically. Humanoid mobs are recognised outright, and other models are searched for a bone named `right_arm` or `left_arm` (and the usual variants: `rightArm`, `arm_right`, `right_hand`, or a plain `arm`). Set `arm` only when that picks the wrong bone or finds nothing, and set it to the bone's name as the model defines it. Nothing stops you naming a bone that isn't an arm; a morph that should raise a wing or a claw into view is a legitimate use. A name the model doesn't have logs a warning once and falls back to auto-detection, so a typo shows up in the log rather than silently emptying the view.

The arm is drawn where a *player's* arm would be, because that is where the hand belongs on screen. A morph whose arm is much longer or shorter than a player's will look it: that's the cost of borrowing another mob's geometry, and it's why the default doesn't do this.

```json
{
  "type": "neoorigins:entity_model",
  "entity_type": "minecraft:zombie",
  "first_person": "arm"
}
```

### Skin morphs

`entity_type` replaces the player's model with a mob's. `skin` does something different and often more useful: it keeps the player's own model and swaps the textures drawn on it. Because the player is still being rendered as a player, arms, animations, armour, held items and first-person view all keep working with nothing extra to configure. Reach for `skin` for anything humanoid, and `entity_type` when you actually want a different shape.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `texture` | string | no | — | Body texture, as an *asset id*. |
| `cape` | string | no | — | Cape texture. |
| `elytra` | string | no | — | Elytra texture. Vanilla falls back to the cape texture when unset. |
| `model` | enum | no | — | Arm width: `slim` (three-pixel arms) or `wide` (four). |

Textures are addressed as **asset ids, not file paths**: `neoorigins:morph/fox` loads `assets/neoorigins/textures/morph/fox.png`. The file must be a 64×64 player skin; a mob texture will not map onto the player model.

Every key is layered over the player's real skin rather than replacing it wholesale, so a `skin` block that only sets `cape` leaves the body alone, and one that only sets `texture` doesn't strip a player's real cape. Leaving `model` unset keeps whatever arm width the player already had, which is what a texture-only reskin normally wants; set it when your texture was drawn for a specific arm width.

```json
{
  "type": "neoorigins:entity_model",
  "skin": {
    "texture": "neoorigins:morph/frostborn",
    "model": "slim"
  },
  "name": "Frostborn",
  "description": "Your skin pales to frost-bitten blue."
}
```

### Morph sounds

A morph is audible as well as visible. Naming an `entity_type` also hands the player that mob's voice: its hurt sound, its death sound, its landing sounds and its swimming sounds. A morphed creeper hisses when it takes a hit; a morphed slime squelches when it lands. Nothing has to be configured for this; it is what `entity_type` does now.

Set `entity_sounds` to `false` to look like the mob and still sound like yourself.

`sounds` overrides individual sounds on top of that. It is the way to give a voice to a morph that has no `entity_type` at all (a `skin` morph), to use a sound your own pack ships, or to change just one of the sounds a mob would otherwise supply.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `hurt` | string | no | — | Played when the player takes damage. |
| `death` | string | no | — | Played once when the player dies. |
| `fall_small` | string | no | — | Landing from a short fall (four blocks or less). |
| `fall_big` | string | no | — | Landing from a long fall. |
| `swim` | string | no | — | Looping sound while swimming. |
| `splash` | string | no | — | Entering or leaving water. |
| `splash_high_speed` | string | no | — | The faster splash used when hitting water hard. |

Every value is a **sound event id**: `minecraft:entity.fox.hurt`, not a path to an `.ogg`. Custom sounds need an entry in your pack's `sounds.json` the same as anywhere else. An id that isn't registered is reported once in the log and then ignored, so a typo costs you one sound rather than silencing the player.

Like `skin`, each key is layered rather than wholesale: a `sounds` block that only sets `hurt` leaves the rest of the mob's voice alone, and `fall_small` and `fall_big` resolve independently of each other.

```json
{
  "type": "neoorigins:entity_model",
  "morph": "creeper",
  "sounds": {
    "death": "mypack:morph.creeper.unmaking"
  },
  "name": "Creeping Form",
  "description": "You hiss when struck, and go out with a sound of your own."
}
```

Two sounds deliberately do not change. **Step sounds** come from the block underfoot, not from the entity, so morphing into a chicken does not make you sound like one walking. **Ambient sounds** (a zombie's groan, a cat's meow) have no player equivalent to hook, so a morph never idles aloud.

### Morph hitbox

A morph is tangible, not just a picture. Naming an `entity_type` gives the player that mob's collision box: a small slime fits through a one-block gap, a spider walks under a slab, an iron golem no longer does. Eye height follows the box, so a short morph genuinely sees from lower down and a tall one sees over a fence. `scale` resizes the box along with the model, so the two never disagree.

The size is read from the morph target with its variant NBT already applied, which means `{"Size": 0}` on a slime gives you the small slime's box, not the default one. `neoorigins:size_scaling` still applies on top, so a scaled-up player morphed into a slime collides as a scaled-up slime.

Set `hitbox` to `false` for a look-only morph: the player keeps their own box and eye height, and only the model changes.

```json
{
  "type": "neoorigins:entity_model",
  "entity_type": "minecraft:slime",
  "nbt": { "Size": 0 },
  "name": "Slimeling",
  "description": "Small enough to slip through a gap a person cannot."
}
```

**Growing into a wall.** A morph that would make the player *bigger* than the space they are standing in is held back rather than forced: the player keeps their own size, and the morph takes hold the moment they step somewhere it fits. Nothing is cancelled and nothing teleports. So crouching into a one-block gap as a small slime works, and a power that would grow you back while you are still in that gap simply waits until you are out of it. The same check runs while a morph is already applied, so a player walled in mid-morph shrinks back to their own size instead of suffocating.

A morph target far larger than a player (an ender dragon, a ravager) is not rejected, but in practice it will rarely find room, so expect it to spend most of its time held back. Very large morphs are better authored with `hitbox` set to `false`.

### Morph definitions

`morph` refers to a reusable definition: the same entity type, variant NBT and scale used across several origins, written once. The built-ins are `slime`, `magma_cube`, `sheep`, `cat`, `villager`, `creeper`, `zombie`, `skeleton`, `enderman` and `spider`.

Define your own (or replace a built-in) at `data/<ns>/neoorigins/morphs/<name>.json`, using the same fields as above minus `morph` itself:

```json
{
  "entity_type": "minecraft:cat",
  "nbt": { "variant": "minecraft:siamese" },
  "scale": 0.9,
  "first_person": "hidden"
}
```

A datapack file always overrides the built-in of the same id. An unknown `morph` id logs a warning once and is ignored, rather than silently rendering nothing.

Notes:

- **The hitbox does change**, unless you set `hitbox` to `false`. See [Morph hitbox](#morph-hitbox). It composes with `neoorigins:size_scaling` rather than fighting it: that power writes the vanilla `minecraft:scale` attribute, which is applied on top of the morph's own size.
- **Sounds do change**, unless you set `entity_sounds` to `false`. See [Morph sounds](#morph-sounds).
- **Variants come from `nbt`**, so any variant the entity itself reads from save data works with no per-mob support needed: sheep colour, cat and villager types, slime size, and so on. The `Team` key is ignored — a render stand-in has no business joining a scoreboard team.
- **Held items and armour** carry over to the morph unless you turn them off. Where the morph's model draws equipment itself (zombies, skeletons, piglins, anything humanoid), the items go into its real hands and armour slots and animate with it. Where it does not (a slime has no hand bone), the held item floats just above and in front of the body instead. `render_armor` therefore has no visible effect on a morph whose entity never draws armour.
- **Player state carries across.** Invisibility, the red damage flash, the freezing/powder-snow overlay, the death topple, and the swing and use animations all mirror the player onto the morph, so a morphed player still reads as hurt, frozen or dying at a glance.
- **Nameplate** is preserved: the player's display name still renders above the morph under the same visibility rules vanilla uses.
- An entity type that can't be rendered (unknown id, no registered renderer, players) is reported once in the log and the player keeps their normal model, rather than turning invisible.

**Example: slime morph, using the built-in definition**
```json
{
  "type": "neoorigins:entity_model",
  "morph": "slime",
  "name": "Slime Form",
  "description": "You appear as a slime to yourself and others."
}
```

**Example: a big red sheep, defined entirely inline**
```json
{
  "type": "neoorigins:entity_model",
  "entity_type": "minecraft:sheep",
  "nbt": { "Color": 14 },
  "scale": 1.4,
  "first_person": "hidden",
  "name": "Ram Form",
  "description": "You take on the shape of a great red ram."
}
```

---

## `neoorigins:bounce_on_land`

Reflects the player's downward impact velocity back upward on landing, mimicking a slime block: a slime-morphed player springs off the ground after a fall. The bounce fires only on the airborne→ground transition, using the impact speed captured the tick before landing (by the landing tick the player's own velocity has already been collision-clamped to ~0). The new velocity is pushed to the client through the same packet path vanilla uses for server-applied knockback, so the launch feels responsive.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `restitution` | number | no | `0.8` | Fraction of downward impact velocity reflected back up on landing; `<1` damps each successive bounce so the player settles. |
| `min_velocity` | number | no | `0.3` | Minimum downward speed (blocks/tick) needed to trigger a bounce, so walking and small steps don't micro-bounce. |
| `max_velocity` | number | no | `1.6` | Cap on the upward launch speed (blocks/tick) so terminal-velocity falls don't fling the player absurdly high. |

Notes:

- **Sneaking suppresses the bounce**: matching slime-block behavior and giving players a deliberate way to stop bouncing.
- Pair with `neoorigins:prevent_action` (`"action": "fall_damage"`) so the impact driving the bounce doesn't also hurt.

**Example: springy slime body**
```json
{
  "type": "neoorigins:bounce_on_land",
  "restitution": 0.8,
  "name": "Springy Body",
  "description": "Like a slime, you rebound off the ground after a fall. Hold sneak to stay put."
}
```

---

## `neoorigins:become_dragon`

Soft-compat hook for the [Dragon Survival](https://www.curseforge.com/minecraft/mc-mods/dragon-survival) mod: while the power is granted, the holder is a Dragon Survival dragon of the configured species; revoking it reverts them to human form. The power is a pure hook — Dragon Survival supplies all of the resulting traits, growth, abilities and hunters. The form is re-applied on login and respawn, so it survives relog and death.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `species` | string | yes | — | Dragon Survival species id, e.g. `dragonsurvival:cave_dragon`. |
| `stage` | string | no | `dragonsurvival:newborn` | Starting growth stage. Blank or unresolvable ids fall back to the species default. |

Notes:

- **Inert without Dragon Survival.** The power no-ops when the mod is absent. Always pair it (and the origin that grants it) with `"required_mods": ["dragonsurvival"]` so the content never loads, or appears in the picker, without the target mod. The three built-in dragon origins (Cave, Forest, Sea Dragon) follow this pattern.
- The bridge binds to Dragon Survival's internals reflectively (the mod exposes no addon API): if a future Dragon Survival release renames those internals, the power logs one warning and stops transforming players rather than crashing. See [COMPATIBILITY.md](COMPATIBILITY.md).

**Example: cave dragon form**
```json
{
  "type": "neoorigins:become_dragon",
  "species": "dragonsurvival:cave_dragon",
  "stage": "dragonsurvival:newborn",
  "required_mods": ["dragonsurvival"],
  "name": "Cave Dragon Form",
  "description": "Take the shape of a cave dragon."
}
```

---

## `neoorigins:lava_vision`

Increases the player's vision distance while the camera is submerged in lava by pushing back the lava fog planes. Holders also lose the first-person burning-screen fire overlay: the power is meant for fire-immune origins, where the flame animation is noise. Client-side rendering is handled by `VisualEffectsHandler` via `ViewportEvent.RenderFog` and `RenderBlockScreenEffectEvent`.

The planes can be set two ways. `strength` scales vanilla's own values, while `start` and `end` replace them outright, in blocks. An absolute field always wins for the plane it names; a plane with no absolute value falls back to the multiplier.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `strength` | float | no | `3.0` | Fog distance multiplier (higher = further vision in lava). Ignored for whichever plane `start` or `end` sets. |
| `start` | float | no | — | Absolute fog start, in blocks: the distance at which fog begins. |
| `end` | float | no | — | Absolute fog end, in blocks: effectively how far you can see through lava. |

Note that `strength` is relative to values that vanilla itself varies, so the same multiplier gives different distances depending on status effects: without fire resistance the lava fog runs 0.25 to 1.0 blocks, with it 0.0 to 5.0. Use `start`/`end` when you want a distance you can count on.

Origins compat: `origins:lava_vision` maps `s` to `start` and `v` to `end`. Both are absolute distances upstream too, so the common `{"s": 0, "v": 15}` spelling carries over unchanged and means "see 15 blocks through lava".

**Example:**
```json
{
  "type": "neoorigins:lava_vision",
  "strength": 5.0,
  "name": "Magma Sight",
  "description": "See clearly through molten rock."
}
```

**Example: an exact sight distance, independent of fire resistance**
```json
{
  "type": "neoorigins:lava_vision",
  "start": 0.0,
  "end": 15.0,
  "name": "Magma Sight",
  "description": "See fifteen blocks through molten rock."
}
```

---

## `neoorigins:shader`

Applies a post-processing shader to the player's view via `GameRenderer.loadEffect()`. Origins-style full paths (e.g. `minecraft:shaders/post/desaturate.json`) are automatically normalised to the MC resource location format.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `shader` | resource location | yes | — | Shader id (e.g. `"minecraft:desaturate"`, `"minecraft:spider"`). |

Origins compat: translates `origins:shader`.

**Example: desaturated phantom view**
```json
{
  "type": "neoorigins:shader",
  "shader": "minecraft:desaturate",
  "name": "Phantom Eyes",
  "description": "The world appears washed of colour."
}
```

---

## `neoorigins:wraith_phase`

Toggleable spectral phasing. When active the player walks through solid blocks horizontally. While inside a solid block, jump and sneak give vertical control (jump = up, shift = down): that movement is velocity, and the power never grants flight. Holding shift on the surface phases downward into the ground. Certain blocks cannot be phased through.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `blocked_blocks` | list of string | no | `["minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:bedrock"]` | Block IDs that cannot be phased through. |
| `exhaustion_per_tick` | float | no | `0.15` | Hunger drain per tick while inside solid blocks. |
| `always_on` | bool | no | `false` | When `true`, the power is passive (always active, no toggle, no skill key slot). Configurable per tier in the mod config. |

Emits the `wall_phase` capability while active (toggled on or always_on).

**Example: base wraith phase**
```json
{
  "type": "neoorigins:wraith_phase",
  "blocked_blocks": ["minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:bedrock"],
  "exhaustion_per_tick": 0.15
}
```

**Example: apex tier (only bedrock blocks, minimal drain)**
```json
{
  "type": "neoorigins:wraith_phase",
  "blocked_blocks": ["minecraft:bedrock"],
  "exhaustion_per_tick": 0.075
}
```

---

## `neoorigins:resource`

A named, persistent, HUD-visible resource bar. Values are stored per-player and synced to the client for rendering. Supports regeneration with conditions, threshold actions when min/max are hit, and compatibility with the Origins `change_resource` / `resource` condition system.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `min` | int | no | `0` | Minimum resource value |
| `max` | int | no | `100` | Maximum resource value |
| `start_value` | int | no | `max` | Initial value when granted |
| `regen_rate` | int | no | `0` | Amount regenerated per interval (0 = no regen) |
| `regen_interval` | int | no | `20` | Ticks between regen ticks |
| `regen_condition` | EntityCondition | no | always-true | Condition for when regeneration occurs |
| `min_action` | EntityAction | no | noop | Action triggered each tick while resource is at minimum |
| `max_action` | EntityAction | no | noop | Action triggered each tick while resource is at maximum |
| `hud_render` | object | no | — | HUD display settings (see below) |
| `hidden` | bool | no | `false` | Whether to hide the bar from the HUD |
| `backing` | string | no | `""` | Optional external pool that backs the bar's value instead of the internal store. Only `irons_spellbooks:mana` is supported (see below); when set, `min`/`max` are ignored and the bar auto-scales to Iron's live max mana. Empty = internally stored. |

**`backing`: bind the bar to Iron's Spells mana**

Set `"backing": "irons_spellbooks:mana"` to make the bar's value **read from and write to the player's [Iron's Spells 'n Spellbooks](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks) mana pool** rather than NeoOrigins' own per-player store. The mana pool stays authoritative:

- **Reads** (the HUD bar, the `resource` condition, `resource_cost` gating) observe the live mana value.
- **Writes** are **additive-only**: `change_resource` adds its `change` amount (positive to grant, negative to drain), `regen_rate` adds per interval, and `resource_cost` deductions subtract. All of these add a delta to the pool; NeoOrigins never overwrites mana absolutely (that would fight Iron's own regen and casting bookkeeping).
- **Absolute sets are ignored.** `set_resource` and `change_resource` with `"operation": "set"` are no-ops on a mana-backed bar (logged once). Use additive `change_resource` instead.
- **`min`/`max` are optional and ignored: the bar auto-scales.** A mana-backed bar uses `min = 0` and `max =` Iron's **live** max mana, which is the `MAX_MANA` attribute (it moves with gear, level, and effects), so you don't declare a static scale. Iron's own mana bar reads the same attribute, so the two bars fill identically. Any author `min`/`max` on a mana-backed power is accepted but has no effect (they still matter for ordinary, non-backed resources). A drain is floor-clamped so mana never goes below 0.
- **The dynamic max is pushed live.** Because the max can change mid-game (e.g. a gear swap that grants more max mana), the periodic value sync carries the current max, so the HUD re-scales without waiting for a full re-sync.
- **Iron's Spells is a soft dependency.** If it isn't installed, the bar reads empty and writes do nothing (logged once); it does **not** fall back to an internal value. Gate the origin/power with `"required_mods": ["irons_spellbooks"]` if the bar should only exist when Iron's is present.

Because Iron's changes mana out from under the game (regen, spellcasting), a mana-backed bar re-syncs its value (and live max) to the client every 10 ticks so the HUD tracks the pool live.

```json
{
  "type": "neoorigins:resource",
  "backing": "irons_spellbooks:mana",
  "hud_render": {
    "label": "Mana",
    "color": "#55AAFF"
  },
  "name": "Arcane Reserve",
  "description": "Your Iron's Spells mana, shown as an origin resource bar."
}
```

> `min`/`max` are omitted above on purpose: a mana-backed bar auto-scales to Iron's live max mana.

**`hud_render` object:**

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `label` | string | no | `"Resource"` | Display label on the HUD bar |
| `color` | string | no | `"#55AAFF"` | Bar color in `#RRGGBB` or `#AARRGGBB` hex format |
| `should_render` | bool | no | `true` | Origins compat: when false, hides the bar |
| `always_render` | bool | no | `false` | Keep the bar on-screen even when full. By default the HUD hides a full bar (Apoli convention), so a regenerating meter that sits at max is invisible until it's spent; set this to keep it always visible. |
| `animated` | string | no | — | Id of an animated bar FX preset, e.g. `"neoorigins:fire"`. When set and the preset is loaded, the bar fill renders as an animated texture strip instead of the flat `color`. |
| `tint` | string | no | — | Hex color (`#RRGGBB` or `#AARRGGBB`) multiplied over the animated preset art, so one texture strip can be recolored per power. Ignored when `animated` is unset. |

**Animated bar FX presets:**

Presets are resource-pack JSON under `assets/<namespace>/bar_fx/<name>.json`, looked up by the `animated` id. Because the bar render is entirely client-side, the preset and its texture ship in a resource pack: only the preset id travels in the power JSON; clients without the preset fall back to the flat `color` fill.

```json
{
  "texture": "neoorigins:textures/gui/bar_fx/fire.png",
  "mode": "scroll",
  "tile_width": 213,
  "tile_height": 16,
  "scroll_speed": 24,
  "track_color": "#AA2B0900",
  "level_color": "#B3551500"
}
```

| Field | Type | Default | Description |
|---|---|---|---|
| `texture` | resource location | required | The strip texture to scroll across the bar. |
| `mode` | string | `"scroll"` | Animation mode; only `scroll` is supported today. |
| `tile_width` / `tile_height` | int | `64` / `8` | Source texture dimensions in texels. |
| `scroll_speed` | float | `24` | On-screen pixels per second the strip drifts left. |
| `track_color` | hex color | `#AA000000` | ARGB backing drawn under the empty remainder of the bar. |
| `level_color` | hex color | `track_color` | ARGB backing under the filled portion: pick a brighter tone so the current level reads through transparent gaps in the texture. |

See [animated_bar_artist_spec.md](animated_bar_artist_spec.md) for texture-authoring guidance.

**Example: mana bar that regens while not in combat**
```json
{
  "type": "neoorigins:resource",
  "min": 0,
  "max": 100,
  "start_value": 100,
  "regen_rate": 1,
  "regen_interval": 20,
  "regen_condition": { "type": "neoorigins:out_of_combat" },
  "hud_render": {
    "label": "Mana",
    "color": "#55AAFF"
  },
  "name": "Mana Pool",
  "description": "Magical energy that regenerates outside of combat."
}
```

---

## `neoorigins:variable`

A named, persistent, **always-hidden** integer counter — a "local variable" for your origin. Unlike [`neoorigins:resource`](#neooriginsresource), a variable has no HUD bar, no regeneration, and no per-tick cost: it only changes when an action explicitly writes to it. It's the lightweight way to track state (combos, charges, kill counts, stages) and gate other abilities on it.

The counter is stored per-player and saved across login sessions. Its storage key is the **power's own id**, so it shares the same keyspace as resources: read it with the [`resource` condition](CONDITIONS.md) and write it with [`change_resource` / `set_resource`](ACTIONS.md), exactly as you would a resource. Because two powers can never share an id, a variable can never collide with a resource of the same name.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `start` | int | no | `0` | Value seeded when the power is granted. Also the value reads fall back to before the first write. |
| `min` | int | no | unbounded | Lower clamp applied to additive (`change_resource`) writes. Omit for an unbounded counter. |
| `max` | int | no | unbounded | Upper clamp applied to additive (`change_resource`) writes. Omit for an unbounded counter. |

Declare more than one by adding a `neoorigins:variable` power for each counter. Because a read resolves the declared `start` value even before the power's own seed runs (declarations are registered when powers load), the counter reads correctly no matter where it sits in the origin's power list, but listing your variable powers first keeps intent clear ("declared at the start of the power stack").

**Example: a combo counter that powers a finisher**
```json
{
  "type": "neoorigins:variable",
  "start": 0,
  "min": 0,
  "max": 5,
  "name": "Combo"
}
```
Increment it on hit (via an `action_on_event` / `change_resource` of `{ "resource": "<this power's id>", "change": 1 }`), gate the finisher ability behind a `resource` condition (`comparison: ">=", compare_to: 5`), and reset it with `set_resource` once the finisher fires.

---

## `neoorigins:slime_moisture`

Custom resource bar (0.0–1.0 float) that drains passively over time, faster in dry biomes (desert, badlands, savanna) and much faster when on fire. Replenished by standing in water or rain. Triggers threshold effects: Regeneration above 75%, armor penalty below 10%, and damage-over-time at 0%.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `drain_per_tick` | float | no | `0.0004` | Base moisture drain per tick |
| `dry_biome_drain_multiplier` | float | no | `3.0` | Drain multiplier in desert/badlands/savanna biomes |
| `fire_drain_multiplier` | float | no | `10.0` | Drain multiplier when player is on fire |
| `water_refill_per_tick` | float | no | `0.005` | Moisture gained per tick in water or rain |
| `regen_threshold` | float | no | `0.75` | Moisture level above which Regeneration I is applied |
| `armor_penalty_threshold` | float | no | `0.10` | Below this, -4 armor is applied |
| `dot_threshold` | float | no | `0.0` | Below this, damage-over-time triggers |
| `dot_damage` | float | no | `1.0` | Damage per interval when below dot_threshold |
| `dot_interval` | int | no | `40` | Ticks between damage ticks |

**Example:**
```json
{
  "type": "neoorigins:slime_moisture",
  "name": "Moisture",
  "description": "Must stay hydrated. Dries out faster in hot biomes."
}
```

---

## `neoorigins:slime_death_save`

Death prevention mechanic for slime-themed origins. When the player would die with moisture above the threshold, they "split" instead: teleported to a random location, max HP reduced to 2 hearts, which gradually recovers over time.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `moisture_threshold` | float | no | `0.75` | Moisture level required to trigger the save |
| `teleport_distance` | int | no | `50` | Horizontal blocks to teleport |
| `teleport_y_range` | int | no | `10` | Random Y offset range (±) |
| `split_max_hp` | float | no | `4.0` | Max HP while "split" (2 hearts) |
| `recovery_ticks` | int | no | `2400` | Ticks for HP to recover to normal (default: 120 seconds) |

**Example:**
```json
{
  "type": "neoorigins:slime_death_save",
  "name": "Slime Split",
  "description": "Instead of dying, splits and teleports away — but at 2 hearts."
}
```

> Requires a companion `slime_moisture` power to provide the moisture value. Without it, the death save never triggers (moisture defaults to 1.0 and never changes).

---

## `neoorigins:slime_level_hp`

Grants bonus max HP based on the player's experience level. The bonus tracks your current level: it is recomputed from the level you have right now, so it falls when you lose levels and comes back when you earn them again.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `levels_per_hp` | int | no | `10` | Experience levels needed per +1 max HP |
| `max_bonus_hp` | int | no | `20` | Maximum HP bonus cap |

**Example: +1 HP every 10 levels, capped at +20**
```json
{
  "type": "neoorigins:slime_level_hp",
  "levels_per_hp": 10,
  "max_bonus_hp": 20,
  "name": "Growth",
  "description": "Grows tougher with experience."
}
```

---

## Composing power sets

Individual 2.0 power types are intentionally narrow so they can be combined. For a "rat"-style origin that marks small mobs it kills and gets a heal buff when attacking anything on the list:

- `neoorigins:entity_set` — declares the UUID set (e.g. `mypack:kill_list`)
- `neoorigins:action_on_event` with `event: kill`, `entity_action: { type: neoorigins:add_to_set, set: mypack:kill_list }` — appends victims to the set
- `neoorigins:condition_passive` with `condition: { type: origins:target_in_set, set: mypack:kill_list }` and `entity_action: { type: neoorigins:heal, amount: 0.5 }` — heals when attacking a marked target

Each piece is a separate power entry in the origin's `powers` array. The `entity_set` power carries no behaviour on its own: it's the shared name other powers read and write.

---

## `neoorigins:loot_pool_grant`

Active power that rolls a vanilla loot table on activation and grants every rolled stack to the player. Lets pack authors deliver weighted, conditional, function-driven rewards through the full vanilla loot infrastructure instead of a flat item list, and (when FTB Quests is installed) reuse the same loot table as a quest reward via a tag-marker. The grant fires once per `grant_id`: after it lands, further activations with the same id do nothing until an origin reset clears the record.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `grant_id` | string | yes | — | Unique id tracked so the bundle is granted only once per player. Shares the `starting_equipment` grant attachment so an Orb of Origin / `/origin reset` clears both. |
| `loot_table` | resource-loc | yes | — | The vanilla loot table to roll. Reuses the full vanilla loot infrastructure (weighted entries, conditions, functions, modifiers). |
| `rolls` | int | no | `1` | Times the table is rolled per activation. |
| `bonus_rolls` | int | no | `0` | Extra rolls added to `rolls`. Mirrors vanilla loot-pool naming. |
| `active` | string | no | `""` | Optional display-only translation key advertising which keybind slot the power expects (e.g. `key.use_skill_1`). |
| `cooldown` | int | no | `0` | Cooldown in ticks between activations (`20` = 1s). |

Overflow that does not fit in the inventory is dropped at the player's feet; no stacks are silently lost. Empty rolls (a table that returns no items) do not consume the `grant_id`, so the author can fix the table and the player can re-activate.

### Worked example: wood starter pack

`data/neoorigins/loot_tables/rewards/wood_starter.json`:
```json
{
  "type": "minecraft:gift",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        { "type": "minecraft:item", "name": "minecraft:oak_log", "weight": 3,
          "functions": [{ "function": "minecraft:set_count", "count": { "min": 8, "max": 16 } }] },
        { "type": "minecraft:item", "name": "minecraft:birch_log", "weight": 1,
          "functions": [{ "function": "minecraft:set_count", "count": { "min": 8, "max": 16 } }] }
      ]
    },
    {
      "rolls": 1,
      "entries": [
        { "type": "minecraft:item", "name": "minecraft:stone_axe" }
      ]
    }
  ]
}
```

`data/neoorigins/origins/powers/lumberjack_starter_pack.json`:
```json
{
  "type": "neoorigins:loot_pool_grant",
  "name": "Starter Pack",
  "description": "Rolls a weighted bundle of logs plus a stone axe on use.",
  "grant_id": "lumberjack:starter_pack_v1",
  "loot_table": "neoorigins:rewards/wood_starter",
  "rolls": 1,
  "bonus_rolls": 0,
  "active": "key.use_skill_1",
  "cooldown": 0
}
```

### FTB Quests soft-compat

When `ftbquests` is on the mod list, NeoOrigins listens for FTBQ's `QuestCompletedEvent`. Any quest tagged

```
neoorigins_loot_pool_grant:<loot_table_id>
```

routes through the same `LootPoolGrantPower#fireLootPoolGrant` pipeline on completion: the completing player receives the rolled stacks, with dedup keyed on `ftbq:<quest_id>:<table_id>`. Authors get vanilla loot-table reuse for both origin powers and quest rewards without any hard FTBQ dependency.

This is a soft-compat layer: it is **not** an FTBQ `RewardType` registration (which would require Provider-API hooks that vary across FTBQ minor versions). The tag-marker path is the supported integration; a `RewardType` upgrade is reserved for v2.2 once that API stabilises.


---

## `neoorigins:kill_loot_drops`

Passive power that layers extra item drops onto the vanilla loot of any mob the holder kills: the bonus items appear as real drops from the mob's corpse, flowing through the loot pipeline (drop events, other loot-modifying mods) rather than being pushed straight into the inventory. It is implemented as a NeoForge global loot modifier keyed on the killer (the mirror image of the mob-origin drop hook), so it fires only on entity-death loot tables, never on chests, blocks, or fishing.

One power can touch many mob loot tables at once: list an entry per mob type in `drops`, or match a whole category from a single entry with `entity_tag` / `entity_types`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `drops` | array | no | `[]` | The drop rules. Each entry rolls independently against the killed mob; an entry with no valid target or an unknown `item` is skipped with a warning at load time. Absent or empty is not a load error: the power simply drops nothing, so it is worth setting. |

Each entry in `drops` takes exactly one target form plus an item:

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_type` | resource-loc | one of | — | A single exact mob type, e.g. `minecraft:zombie`. |
| `entity_tag` | resource-loc | one of | — | An entity-type tag, e.g. `minecraft:skeletons`, matches every type in the tag. |
| `entity_types` | array | one of | — | An explicit list of exact mob types. |
| `item` | resource-loc | yes | — | The item to drop. An unknown id skips the entry. |
| `chance` | float | no | `1.0` | Probability in `[0,1]` that this entry drops on a matching kill. |
| `count` | int | no | `1` | Stack size dropped (minimum 1). |

Exactly one of `entity_type` / `entity_tag` / `entity_types` must be set per entry. The drop only rolls when the killer is a player who holds the power: mob-on-mob and environmental deaths are ignored. A player may hold several `kill_loot_drops` powers at once (e.g. one per origin tier); their rule lists are concatenated, and revoking one leaves the others intact.

### Worked example: Head Hunter

`data/neoorigins/origins/powers/head_hunter_drops.json`:
```json
{
  "type": "neoorigins:kill_loot_drops",
  "name": "Head Hunter",
  "description": "5% chance to drop the matching head when you kill a zombie, creeper, skeleton or piglin.",
  "drops": [
    { "entity_type": "minecraft:zombie",   "item": "minecraft:zombie_head",    "chance": 0.05 },
    { "entity_type": "minecraft:creeper",  "item": "minecraft:creeper_head",   "chance": 0.05 },
    { "entity_type": "minecraft:skeleton", "item": "minecraft:skeleton_skull", "chance": 0.05 },
    { "entity_type": "minecraft:piglin",   "item": "minecraft:piglin_head",    "chance": 0.05 }
  ]
}
```

The same idea, hooking every undead type from a single entry:
```json
{ "entity_tag": "minecraft:undead", "item": "minecraft:bone", "chance": 0.25 }
```

### Activation

The hook is a global loot modifier, so it has to be switched on by a carrier file: unlike `mob_origin_drops`, the mod does not auto-generate one for you. Ship these two files alongside the power (the Head Hunter example includes both):

`data/neoforge/loot_modifiers/global_loot_modifiers.json`:
```json
{
  "replace": false,
  "entries": [ "neoorigins:kill_loot_drops" ]
}
```

`data/neoorigins/loot_modifiers/kill_loot_drops.json`:
```json
{
  "type": "neoorigins:kill_loot_drops",
  "conditions": []
}
```

`replace: false` makes NeoForge merge the entry list additively across packs, so this coexists with the mod's own `mob_origin_drops` carrier and any other pack's modifiers. The per-power `drops` live in the power file, not here: this carrier is a data-free on-switch, so one carrier covers every `kill_loot_drops` power in the pack.

---

# KubeJS bridge powers

These two types delegate power behavior to JavaScript handlers registered from a KubeJS `startup_scripts/` file. They only function when KubeJS is on the mod list: the power JSON references a `js_id`, and the script registers the matching handler via the `NeoOrigins.*` bindings. If no handler is registered for the id, the power is inert.

They are registered on 1.21.1 and, from 2.2.24, on 26.1. They are absent from the 26.2 build because KubeJS itself has no 26.2 release to build against, and a power file naming an unregistered type is dropped whole at load. See [KUBEJS.md](KUBEJS.md).

## `neoorigins:js_custom`

A passive power whose lifecycle hooks run JS. Register the handler with `NeoOrigins.registerPower(id, {onGranted, onRevoked, onTick})`; any hook the JS object doesn't supply defaults to a no-op.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `js_id` | string | yes | — | ID of the JS handler registered via `NeoOrigins.registerPower` |

```json
{
  "type": "neoorigins:js_custom",
  "name": "Scripted Aura",
  "description": "Behavior supplied by the pack's startup script.",
  "js_id": "mypack:aura"
}
```

```js
// startup_scripts/powers.js
NeoOrigins.registerPower('mypack:aura', {
    onTick: player => { /* runs each power tick */ }
})
```

## `neoorigins:js_active`

A keybind-activated power whose `onUse` runs JS. Register with `NeoOrigins.registerActivePower(id, {onUse, onGranted, onRevoked})`. `onUse(player)` must return a boolean: `true` consumes the cooldown and hunger cost, `false` is a no-op (nothing is consumed). Cooldown and hunger cost behave exactly like every other active power.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `js_id` | string | yes | — | ID of the JS handler registered via `NeoOrigins.registerActivePower` |
| `cooldown_ticks` | int | no | `20` | Cooldown between uses in ticks (20 = 1s), consumed only when `onUse` returns `true` |
| `hunger_cost` | int | no | `0` | Food/exhaustion points consumed on a successful activation (`onUse` returns `true`) |

```json
{
  "type": "neoorigins:js_active",
  "name": "Scripted Blink",
  "description": "Teleports via the pack's startup script.",
  "js_id": "mypack:blink",
  "cooldown_ticks": 100
}
```

```js
// startup_scripts/powers.js
NeoOrigins.registerActivePower('mypack:blink', {
    onUse: player => {
        // return true to consume cooldown + hunger, false to no-op
        return true
    }
})
```

