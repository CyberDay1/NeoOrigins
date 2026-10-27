---
title: "Core & composable"
parent: "Power Types"
grand_parent: "DSL Reference"
nav_order: 1
permalink: /powers/core
---

# Core & composable

The general-purpose types most powers are built from: event listeners, timed passives, auras, attribute and damage modifiers, and the `multiple` container.

Fields every power accepts (`name`, `hidden`, `power_condition`, ...) are on [Power basics](../concepts/power-basics.md). See also: [Power basics](../concepts/power-basics.md), [Costs & upkeep](../concepts/costs-and-upkeep.md) (`effect_over_time` and the toggleable passives), [Events](../EVENTS.md).

Types on this page: [`simple`](#neooriginssimple) · [`multiple`](#neooriginsmultiple) · [`action_on_event`](#neooriginsaction_on_event) · [`condition_passive`](#neooriginscondition_passive) · [`effect_over_time`](#neooriginseffect_over_time) · [`persistent_effect`](#neooriginspersistent_effect) · [`attribute_modifier`](#neooriginsattribute_modifier) · [`modify_damage`](#neooriginsmodify_damage) · [`prevent_action`](#neooriginsprevent_action) · [`conditional`](#neooriginsconditional) · [`tick_action`](#neooriginstick_action).

---

## `neoorigins:simple`

Does nothing. A display-only marker power: the direct equivalent of `origins:simple` from the original Origins mod.

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

## `neoorigins:action_on_event`

The 2.0 generic event hook: fires an action and/or applies a float modifier when a named player event occurs. Replaces 20+ bespoke Origins-Classes hook powers (`better_enchanting`, `more_smoker_xp`, `crop_harvest_bonus`, `break_speed_modifier`, `natural_regen_modifier`, `action_on_kill`, `action_on_hit_taken`, `thorns_aura`, `knockback_modifier`, `hunger_drain_modifier`, `food_restriction`, …) with one configurable type.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `event` | string | yes | — | Event name (case-insensitive). See [EVENTS.md](../EVENTS.md) for the full list. |
| `condition` | EntityCondition | no | always-true | DSL gate: the event only fires when this is true |
| `entity_action` | EntityAction | no | noop | Side-effect run when the event fires |
| `modifier` | FloatModifier or list | no | identity | Float modifier applied to the event's numeric payload (for modifier-style events). Each entry is `{ "operation": ..., "value": ... }`; a list applies in order. `value` defaults to `0` (`amount` is not read). Operations (case-sensitive): `addition`, `add_base`, `add_base_early`, `add_total`, `add_total_early` add `value`; `multiplication`, `multiply_base_multiplicative`, `multiply_total_multiplicative` multiply by `value`; `multiply_base_additive`, `multiply_total_additive` multiply by `1 + value`; `set` / `set_total` replace with `value`; `min_total` caps at `value`; `max_total` floors at `value`. Any other operation, including `multiply_base` and `multiply_total`, is logged as `defaulted to identity` and leaves the payload unchanged. |
| `block_condition` | BlockCondition | no | — | Block-position gate for block events (`block_break`, `block_place`, `block_use`, `bonemeal`). Supports `block`/`id`, `in_tag`, `and`/`or` (aliases `all_of`/`any_of`), `block_state`, `height`, `adjacent`, and the positional `offset` wrapper (see below). On any other event a set `block_condition` never matches, so `entity_action` does not run there. The `modifier` path does not check it. |
| `hands` | string or list | no | — | Hand gate for interaction events (`block_use`, `entity_use`, `villager_interact`): only fire for the listed hands, `"main_hand"` and/or `"off_hand"`. Use `["main_hand"]` to stop a power double-firing (vanilla dispatches the right-click once per hand). Fails closed when the event carries no hand info; ignored on other events. |
| `hand` | string or list | no | — | Singular alias for `hands`, read only when `hands` is absent (Apoli's `action_on_block_use` spelling). |
| `item_condition` | ItemCondition | no | — | Item gate for item-carrying events (`item_use`, `item_use_finish`, `food_eaten`, `food_finished`, `item_pickup`, `block_use`, `entity_use`, `villager_interact`): only fire when the stack the event carries matches. That is the used, eaten or picked-up stack for the first five and the stack in the interacting hand for the rest, which is what Apoli's `item_condition` on `action_on_block_use` / `action_on_entity_use` translates to. Takes the same Apoli item-condition shapes as `equipped_item` (`id`/`tag`/`nbt`/`enchantment`, with `and`/`or`/`not`). Fails closed when set but the event carries no item, including on events that never carry one; the `modifier` path does not check it. |
| `effect` | id | no | — | `effect_applied` only: pre-dispatch filter on this exact effect id. |
| `effect_tag` | tag id | no | — | `effect_applied` only: pre-dispatch filter on this effect tag (leading `#` optional). OR-matched with `effect`. |
| `power` | id or list | no | — | `power_activated` only: pre-dispatch filter on the activated power's id (single id or array). Omit to fire on any activation. |
| `immunity_ticks` | int ≥ 0 | no | 0 | `effect_applied` only: after a successful cancel, grant this many ticks of full immunity to the same effect id before re-rolling. |
| `cooldown_ticks` | int ≥ 0 | no | 0 | After `entity_action` fires, suppress further firings of this power for this many ticks (20 = 1s). Tracked per player per power instance and persisted across respawn/relog. Only gates the action path: `modifier` chains are unaffected. |

**The `offset` block-condition wrapper.** Inside `block_condition`, `{ "type": "neoorigins:offset", "x": 0, "y": -2, "z": 0, "condition": { ... } }` evaluates its nested block condition at the event's block position shifted by the given block offsets — the Apoli structural wrapper for checks like "a basin two blocks below the block I clicked". Without a nested `condition` it matches all blocks (warned at load). The same wrapper works in `block_collision` and the `area_of_effect` block fan-out.

**Block-condition types.** Every field documented as a *BlockCondition* (`block_condition` here, in `block_collision`, `in_block`, `in_block_anywhere`, `near_block`, `on_block`, `prevent_sleep` and the `area_of_effect` block fan-out) accepts the same set of nested types:

| Type | Fields | Matches when |
|---|---|---|
| `block` | `block` (or legacy `id`) | The block is exactly that id. |
| `in_tag` | `tag` | The block is in that block tag (leading `#` optional). |
| `and` / `all_of` / `or` / `any_of` | `conditions` | All / at least one nested condition matches. |
| `offset` | `x`, `y`, `z`, `condition` | The nested condition matches at the shifted position. |
| `block_state` | `property`, plus `value`/`enum` or `comparison`+`compare_to` | The block carries that blockstate property and its value matches. A block without the property never matches. |
| `height` | `comparison` (default `>=`), `compare_to` | The *block's* own Y level compares true: `{ "comparison": "<=", "compare_to": 63 }` is "at or below sea level". |
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

**Event categories (see [EVENTS.md](../EVENTS.md) for the full list):**

- Lifecycle: `gained`, `lost`, `chosen`, `power_activated`, `respawn`, `death`, `dimension_change`, `advancement_earned`
- Combat: `attack`, `hit_taken`, `hit_dealt`, `kill`, `projectile_hit`, `mod_knockback`, `mod_fall_damage`
- Food: `food_eaten`, `food_finished`, `mod_exhaustion`, `mod_natural_regen`, `mod_food_nutrition`, `mod_crafted_food_saturation`
- Mining / crafting: `block_break`, `block_place`, `craft_item`, `smelt_item`, `enchant_item`, `anvil_repair`, `mod_craft_amount`, `mod_enchant_level`, `mod_harvest_drops`, `mod_anvil_cost`
- Economy / villagers: `trade_completed`, `villager_interact`, `mod_trade_price`
- Interaction / movement: `item_use`, `item_use_finish`, `item_pickup`, `block_use`, `entity_use`, `breed`, `tame`, `bonemeal`, `mod_bonemeal_extra`, `wake_up`, `land`, `jump`, `climb`, `tick`, `mod_teleport_range`, `mod_potion_duration`
- Status effects: `effect_applied`

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
    "operation": "multiplication",
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

## `neoorigins:condition_passive`

Generic condition-gated periodic action — "a passive with a trigger". Part of the 2.0 consolidation: collapses `biome_buff`, `damage_in_biome`, `damage_in_daylight`, `damage_in_water`, `burn_at_health_threshold`, `mobs_ignore_player`, `no_mob_spawns_nearby`, and `item_magnetism` into a single type. Also supersedes `tick_action` when `condition` is omitted.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `interval` | int | no | `20` | Tick interval between evaluations (clamped to ≥ 1) |
| `condition` | EntityCondition | no | always-true | DSL condition to test each interval |
| `entity_action` | EntityAction | no | noop | Action run against the player when condition is true |
| `else_action` | EntityAction | no | noop | Action run when condition is false |
| `toggleable` | bool | no | `false` | When true, binds a skill keybind that flips the periodic action on/off (chat feedback via `neoorigins.toggle.on`/`off`). While off the interval action never runs. |
| `default_off` | bool | no | `false` | Toggleable powers only: when true the power starts disabled so the player opts in via the keybind. |

Also takes the shared [toggle upkeep](../concepts/costs-and-upkeep.md#toggle-upkeep) fields (`hunger_cost`, `resource_cost`, `resource_cost_amount`, `upkeep_interval`, `upkeep_condition`).

See [EVENTS.md](../EVENTS.md) / the Apoli compat docs for the full condition and action DSL.

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

Sustained **aura** type: one `entity_action` tree pulsed on an `interval`, in one of two modes via the `activation` field. An aura is a constant effect (usually an `origins:area_of_effect` radiating from the holder). A **passive** aura is always on; an **active** aura is keybind-toggled. A passive aura can also be made **toggleable** (`toggleable: true`) to give it an on/off keybind. Active auras start **off** by default (opt-in); toggleable passives start **on**. Any of them may pay an **upkeep** each pulse (`hunger_cost`, `resource_cost`): a toggleable aura that can't pay switches itself off, and a plain passive aura skips the pulse, running neither `entity_action` nor `else_action`, and tries again on the next one. (For one-shot, cooldown-gated abilities use [`active_ability`](active.md#neooriginsactive_ability) instead; this type is specifically for auras.)

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `activation` | enum | no | `passive` | `passive` \| `active` (`toggle` accepted as an alias for `active`). `passive` pulses `entity_action` every `interval` while granted. `active` binds a keybind that toggles the aura on/off. Either mode may pay an upkeep. Unrecognised values fall back to `passive`. |
| `toggleable` | bool | no | `false` | Passive mode only: add an on/off keybind. A toggleable passive starts **on**. Active auras are always toggleable and start **off**; this flag is ignored for them. |
| `interval` | int | no | `20` | Ticks between aura pulses, and so between upkeep charges (clamped ≥ 1). |
| `condition` | EntityCondition | no | always-true | Gate: `entity_action` pulses each interval only while it passes, else `else_action`. The upkeep is charged regardless of this gate; use `upkeep_condition` to gate the cost. |
| `entity_action` | EntityAction | no | noop | Action pulsed on the player each interval, typically an `origins:area_of_effect` applying damage/effects to nearby entities. Inside an `area_of_effect`, `damage`, `heal`, `apply_effect`, `clear_effect` and `spawn_particles` run on **each affected entity**, so you can paint custom particles (and apply/strip status effects) on everyone the aura touches. |
| `else_action` | EntityAction | no | noop | Action pulsed each interval while `condition` is false. |
| `hunger_cost` | int | no | `0` | Upkeep: food points drained each pulse, in any mode. A toggleable aura switches itself off when the holder can't pay; a plain passive aura skips the pulse. `0` = free. |
| `resource_cost` | string | no | `""` | Upkeep: resource power id drained each pulse, in any mode; falls back to `hunger_cost` when resource bars are globally disabled. |
| `resource_cost_amount` | int | no | `0` | Upkeep: amount of `resource_cost` drained each pulse. Nothing is deducted unless the whole cost, hunger included, can be paid. |
| `upkeep_condition` | EntityCondition | no | always-true | Charge the upkeep only on pulses where this passes. A pulse whose charge is waived still runs. |
| `default_off` | bool | no | mode-dependent | Override the initial on/off state of a toggleable aura. Default: active → **off**, toggleable passive → **on**. Ignored for plain (non-toggleable) passive auras. |

See [EVENTS.md](../EVENTS.md) / the Apoli compat docs for the full condition and action DSL.

> **Custom particles & status effects on affected entities.** Put `spawn_particles` (and `apply_effect`/`damage`/`heal`/`clear_effect`) *inside* the `area_of_effect`'s `entity_action` to apply them to each entity the aura hits, at that entity's position. Put `spawn_particles` *outside* (a sibling of the `area_of_effect`) to draw on the holder instead. To apply a potion effect without its swirling vanilla particles, use `apply_effect` with `"show_particles": false`.

> **Filtering who the aura affects.** Set the `area_of_effect`'s `entity_condition` to control which entities are touched. Entity-general conditions filter **mobs and players** alike: `entity_type: minecraft:player` = players only; `entity_type: "#minecraft:skeletons"` = a tag group; `target_group` = a vanilla mob category; `health`/`relative_health`/`has_effect`/`on_fire`/`living`; and `and`/`or`/`not` of those (plus Apoli's `inverted: true`). For example, wrap `not { entity_type: minecraft:player }` to scorch hostile mobs without burning your allies. See [`area_of_effect`](../actions/meta.md#neooriginsarea_of_effect) for the full list.

**Example (active damage aura): toggle it on to scorch nearby mobs every second, draining 2 hunger per second to keep it up. The flame particles spawn on each burned entity (inside the `area_of_effect`), not on you:**
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

For a **plain passive** aura (always on, e.g. a constant self-healing field), set `"activation": "passive"`. Drop the cost fields to make it free, or keep them and it skips every pulse it can't pay for. For a **toggleable passive** (an on/off keybind, on by default), add `"toggleable": true`; with a cost, it switches off when it can't pay, like an active aura.

`effect_over_time` has no `upkeep_interval`: it charges once per pulse. The rest of the [toggle upkeep](../concepts/costs-and-upkeep.md#toggle-upkeep) rules apply as written, all-or-nothing charges included.

---

## `neoorigins:persistent_effect`

Generic condition-gated, toggleable status-effect stack. Part of the 2.0 consolidation: replaces `status_effect`, `stacking_status_effects`, `night_vision`, `glow`, `water_breathing`, `breath_in_fluid`, and the tag branch of `regen_in_fluid` with one type that applies an arbitrary list of mob effects whenever an optional `condition` (an EntityCondition DSL tree) is met.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effects` | list of EffectSpec | no | `[]` | Mob effects to apply. See below. May also be a single inline EffectSpec on the top-level object, which is read only when `effects` is absent or not an array. Not schema-required, but an empty list applies nothing. |
| `condition` | EntityCondition | no | always-true | DSL condition: effects only apply while it is true. Effects are cleared when it becomes false. |
| `toggleable` | bool | no | `true` | When true, this is an active-keybind power: pressing the key toggles effects on/off. When false, effects are always applied while condition is true. |
| `default_off` | bool | no | `false` | Toggleable powers only: when true, the power starts disabled, so effects stay off until the player first toggles it on. |
| `effect` | Identifier | no | — | Root-level single-effect shorthand, equivalent to a one-entry `effects` list. Read only when `effects` is absent. Alias `id`. |
| `amplifier` | int | no | — | Effect level for the root-level single-effect shorthand. With an `effects` array present it instead overrides the **first** effect's amplifier — winning even over one that entry sets itself — which is how a server admin retunes strength without editing the array. Later specs are unchanged, and unlike its three neighbours below it does not cascade. |
| `ambient` | bool | no | `true` | Root default, cascaded onto every `EffectSpec` that omits it. |
| `show_particles` | bool | no | `false` | Root default, cascaded onto every `EffectSpec` that omits it. |
| `show_icon` | bool | no | `true` | Root default, cascaded onto every `EffectSpec` that omits it. |

Also takes the shared [toggle upkeep](../concepts/costs-and-upkeep.md#toggle-upkeep) fields (`hunger_cost`, `resource_cost`, `resource_cost_amount`, `upkeep_interval`, `upkeep_condition`).

**`EffectSpec` object:**

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effect` | Identifier | yes | — | Effect ID, e.g. `minecraft:strength` (alias `id` also accepted). A spec with a missing or unknown id is dropped without a log. |
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
> needed in the JSON for this; see [`neoorigins:night_vision`](retired.md#neooriginsnight_vision).

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

Effects are granted with an infinite duration, so they never tick down or expire while the power is applying them. When `toggleable` is true (the default), the player can press their skill key to toggle effects on/off; set `toggleable: false` for effects that should always be active. A single effect can be specified inline on the top-level object (`effect`, `amplifier`, etc.) instead of using the `effects` list.

---

## `neoorigins:attribute_modifier`

Adds or multiplies a player attribute while the origin is active. Optionally gated on an environment condition, an equipped-item condition, or both (AND).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `attribute` | Identifier | yes | — | Attribute to modify, e.g. `minecraft:generic.movement_speed` |
| `amount` | double | yes | — | Amount to add or multiply. Ignored while `resource` is set, which supplies the value instead, so write `0`. |
| `resource` | Identifier | no | — | Power id of a `resource` power (native or Apoli-format) whose current value becomes this modifier's value. See below. |
| `operation` | string | no | `add_value` | `add_value`, `add_multiplied_base`, or `add_multiplied_total` (exact lowercase). Any other string, including Apoli's `addition` / `multiply_base`, is treated as `add_value` without a warning. |
| `condition` | string or EntityCondition | no | — | Environment gate: `in_water`, `on_land`, or `in_lava`, or any entity condition object. Tick-driven apply/remove. An unknown string logs a warning and the modifier stays on unconditionally. See the note below on what `on_land` counts. |
| `equipment_condition` | object | no | — | Equipment gate (see below). Tick-driven apply/remove. |
| `location_condition` | object | no | — | Location gate: dimension / biome / structure (see below). Tick-driven apply/remove. |

**`resource`: an attribute that follows a counter.** With `resource` set, the modifier's value is the current value of that resource power rather than `amount`, re-read every 5 ticks and re-applied whenever it has moved, so the attribute tracks the resource in both directions. This is Apoli's resource-driven modifier, and an imported `origins:modify_attribute` carrying a `resource` lands here. A resource nothing has written yet reads `0`, the same floor the [`resource` condition](../conditions/entity.md#neooriginsresource) compares against, so an attribute gains nothing until the counter starts. Name one resource power: a wildcard selector takes no single value, so it logs once and counts as `0`. The gates above still decide whether the modifier is attached at all; while it is, its value follows the resource.

```json
{
  "type": "neoorigins:attribute_modifier",
  "attribute": "minecraft:generic.max_health",
  "amount": 0,
  "resource": "mypack:soul_count",
  "operation": "add_value",
  "name": "Soul Vessel",
  "description": "Each soul you hold is another half heart."
}
```

Apoli can nest further modifiers over the resource value before it reaches the attribute. That is not supported: the attribute tracks the raw value, and the load warns when a pack nests them. To scale a counter, keep the scaled number in the resource itself.

**Operations:**
- `add_value`: flat addition to base value
- `add_multiplied_base`: adds `base * amount` (e.g. `-0.1` = 10% slower)
- `add_multiplied_total`: multiplies total after all other modifiers

**`equipment_condition` object:**

| Field | Type | Required | Description |
|---|---|---|---|
| `slot` | string | no | One of `mainhand`, `offhand`, `head`, `chest`, `legs`, `feet`, `body`. Defaults to `mainhand`. |
| `item` | Identifier | no | Exact item ID to match (e.g. `minecraft:iron_helmet`) |
| `tag` | Identifier | no | Item tag to match (e.g. `minecraft:helmets`) |

If both `item` and `tag` are given, either match satisfies the condition (OR). If neither is given, any non-empty stack in the slot counts as a match. When multiple of `condition` / `equipment_condition` / `location_condition` are set, **all** must hold for the modifier to apply.

**`location_condition` object:**

| Field | Type | Required | Description |
|---|---|---|---|
| `dimension` | Identifier | no | Match only in this dimension, e.g. `minecraft:the_end` |
| `biome` | Identifier | no | Match only in this specific biome, e.g. `minecraft:plains` |
| `biome_tag` | Identifier | no | Match only in biomes with this tag, e.g. `minecraft:is_forest` |
| `biomes` | list of Identifier | no | Match only in any of these biomes |
| `structure` | Identifier | no | Match only inside this structure, e.g. `minecraft:end_city` |
| `structure_tag` | Identifier | no | Match only inside structures with this tag, e.g. `minecraft:on_ocean_monument_maps` |

All fields are optional. `dimension`, `structure` and `structure_tag` combine with AND; the biome fields (`biome`, `biome_tag`, `biomes`) are OR'd with each other, so any one biome match satisfies the biome part. So `{ "dimension": "minecraft:the_end", "structure": "minecraft:end_city" }` is "only when standing inside an End City in The End." Structure membership is evaluated server-side via `ServerLevel.structureManager()`.

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
custom attributes added by [Iron's Spells 'n Spellbooks](../COMPATIBILITY.md#irons-spells-n-spellbooks)
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
| `sprint_food` | While sprinting, all hunger exhaustion is zeroed (not just the sprint cost) |
| `armor_equip` | Prevents wearing armor in any slot whose corresponding `head` / `chest` / `legs` / `feet` boolean is `true`. Items are ejected back to the inventory (or dropped if full). |
| `chestplate_equip` | Legacy alias of `armor_equip` with `chest: true`. Kept for back-compat with packs that pre-date `armor_equip`. |
| `eye_damage` | Accepted, but currently has no effect: nothing reads it |
| `water_damage` | Accepted, but currently has no effect: nothing reads it |
| `swim` | Prevents swimming (velocity-sinks the player in water) |
| `sleep` | Prevents sleeping (bed use returns a "no sleep" message). Clicking a vanilla bed still sets the respawn point before the sleep is refused. |
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

## `neoorigins:conditional`

Currently only one combination does anything: `condition: "climbing"` with an `inner_power` whose path contains `no_fall` cancels fall damage while the player is on a climbable block. The inner power itself is not gated: it runs unconditionally, and every other condition/inner-power combination has no effect. The inner power must be a separately defined power that is also listed in the origin's power list.

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

> The `inner_power` must also be listed in the origin's `powers` array for it to be registered.

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
