---
title: Migration
nav_order: 4
---

# NeoOrigins 2.0 Migration Guide

Reference for pack authors updating JSON from pre-2.0 types to the 2.0 generic / composable types.

## What changed

Pre-2.0 NeoOrigins had **88 hardcoded PowerType classes** — one per behaviour. 2.0 collapses these into **~25 generic types** that compose via DSL (condition + action + event).

For **existing packs**: nothing to change. Legacy type IDs still work via the alias table. On first load of a legacy type, the log prints a one-time `[2.0-legacy] power type '<old>' is deprecated — remap to '<new>'` warning so you know where to migrate when you next touch the pack.

For **new / updated packs**: author against the 2.0 generic types directly. They're more expressive, cheaper to maintain, and won't be deprecated in 3.0.

The source of truth for every alias is `src/main/java/com/cyberday1/neoorigins/power/registry/LegacyPowerTypeAliases.java`. Each `register(old, new, remap)` call below corresponds to a row here; if this document drifts from the code, the code wins.

---

# The deprecation log line

Every aliased legacy type emits exactly one warning per boot:

```
[2.0-legacy] power type 'neoorigins:thorns_aura' is deprecated — remap to 'neoorigins:action_on_event' (first seen on power 'examplepack:spiky_skin')
```

Grep your logs for `[2.0-legacy]` to get the migration punch list. **Deprecated types will be removed no earlier than NeoOrigins 4.0** — you have time.

Aliases fire only once the legacy Java class is physically deleted. While the legacy class still exists in the registry (see the `// <type> retired in 2.0` comments in `PowerTypes.java`), the alias is dormant and the legacy behaviour keeps running. This lets us stage the collapse without breaking in-flight packs.

---

# Collapse table

Grouped by target generic type. Each row reflects the actual remap lambda in `LegacyPowerTypeAliases.java` — the "After" column shows exactly what the JSON is rewritten to before `PowerTypes.get(...)` dispatches to the generic type.

## → `neoorigins:action_on_event`

Fourteen legacy types collapse into this single event-listener.

### `neoorigins:action_on_kill`

Before:
```json
{
  "type": "neoorigins:action_on_kill",
  "action": "restore_health",
  "amount": 4.0
}
```
After:
```json
{
  "type": "neoorigins:action_on_event",
  "event": "kill",
  "entity_action": { "type": "origins:heal", "amount": 4.0 }
}
```
`action` also accepts `"restore_hunger"` (→ `origins:feed { food: <amount> }`) and `"grant_effect"` (→ `origins:apply_effect { effect, duration, amplifier }`).

### `neoorigins:action_on_hit_taken`

Before:
```json
{
  "type": "neoorigins:action_on_hit_taken",
  "action": "teleport",
  "chance": 0.5,
  "min_damage": 2.0
}
```
After (chance + min_damage compose `origins:chance` + `origins:if_else`):
```json
{
  "type": "neoorigins:action_on_event",
  "event": "hit_taken",
  "entity_action": {
    "type": "origins:if_else",
    "condition": {
      "type": "neoorigins:hit_taken_amount",
      "comparison": ">=",
      "compare_to": 2.0
    },
    "if_action": {
      "type": "origins:chance",
      "chance": 0.5,
      "action": {
        "type": "neoorigins:random_teleport",
        "horizontal_range": 16.0,
        "vertical_range": 8.0
      }
    }
  }
}
```
`action` also accepts `"ignite_attacker"` (→ `neoorigins:ignite_attacker { ticks }`) and `"effect_on_attacker"` (→ `neoorigins:effect_on_attacker { effect, duration, amplifier }`).

### `neoorigins:thorns_aura`

Before:
```json
{ "type": "neoorigins:thorns_aura", "return_ratio": 0.25 }
```
After:
```json
{
  "type": "neoorigins:action_on_event",
  "event": "hit_taken",
  "entity_action": {
    "type": "neoorigins:damage_attacker",
    "amount_ratio": 0.25,
    "source": { "name": "magic" }
  }
}
```

### `neoorigins:food_restriction`

Before (blacklist):
```json
{
  "type": "neoorigins:food_restriction",
  "mode": "blacklist",
  "item_tag": "minecraft:fishes"
}
```
After:
```json
{
  "type": "neoorigins:action_on_event",
  "event": "food_eaten",
  "entity_action": {
    "type": "origins:if_else",
    "condition": {
      "type": "neoorigins:food_item_in_tag",
      "tag": "minecraft:fishes"
    },
    "if_action": { "type": "neoorigins:cancel_event" }
  }
}
```
For `"mode": "whitelist"`, the `condition` is wrapped in `origins:not`.

### `neoorigins:hunger_drain_modifier`

Before:
```json
{ "type": "neoorigins:hunger_drain_modifier", "multiplier": 1.3 }
```
After:
```json
{
  "type": "neoorigins:action_on_event",
  "event": "mod_exhaustion",
  "modifier": { "operation": "multiplication", "value": 1.3 }
}
```

### `neoorigins:natural_regen_modifier`

Before:
```json
{ "type": "neoorigins:natural_regen_modifier", "multiplier": 0.5 }
```
After:
```json
{
  "type": "neoorigins:action_on_event",
  "event": "mod_natural_regen",
  "modifier": { "operation": "multiplication", "value": 0.5 }
}
```

### `neoorigins:knockback_modifier`

Before:
```json
{ "type": "neoorigins:knockback_modifier", "multiplier": 0.25 }
```
After:
```json
{
  "type": "neoorigins:action_on_event",
  "event": "mod_knockback",
  "modifier": { "operation": "multiplication", "value": 0.25 }
}
```

### `neoorigins:longer_potions`

Before:
```json
{ "type": "neoorigins:longer_potions", "duration_multiplier": 1.5 }
```
After:
```json
{
  "type": "neoorigins:action_on_event",
  "event": "mod_potion_duration",
  "modifier": { "operation": "multiplication", "value": 1.5 }
}
```

### `neoorigins:teleport_range_modifier`

Before:
```json
{ "type": "neoorigins:teleport_range_modifier", "multiplier": 2.0 }
```
After:
```json
{
  "type": "neoorigins:action_on_event",
  "event": "mod_teleport_range",
  "modifier": { "operation": "multiplication", "value": 2.0 }
}
```

### `neoorigins:more_animal_loot`

Before:
```json
{ "type": "neoorigins:more_animal_loot", "multiplier": 2.0 }
```
After:
```json
{
  "type": "neoorigins:action_on_event",
  "event": "mod_harvest_drops",
  "modifier": { "operation": "multiplication", "value": 2.0 }
}
```

### `neoorigins:efficient_repairs`

Before:
```json
{ "type": "neoorigins:efficient_repairs", "cost_multiplier": 0.5 }
```
After:
```json
{
  "type": "neoorigins:action_on_event",
  "event": "mod_anvil_cost",
  "modifier": { "operation": "multiplication", "value": 0.5 }
}
```

### `neoorigins:better_enchanting`

Before:
```json
{ "type": "neoorigins:better_enchanting", "bonus_levels": 5 }
```
After (note: `add_base`, not `multiplication`):
```json
{
  "type": "neoorigins:action_on_event",
  "event": "mod_enchant_level",
  "modifier": { "operation": "add_base", "value": 5.0 }
}
```

### `neoorigins:better_crafted_food`

Before:
```json
{ "type": "neoorigins:better_crafted_food", "saturation_bonus": 0.5 }
```
After:
```json
{
  "type": "neoorigins:action_on_event",
  "event": "mod_crafted_food_saturation",
  "modifier": { "operation": "add_base", "value": 0.5 }
}
```

### `neoorigins:better_bone_meal`

Before:
```json
{ "type": "neoorigins:better_bone_meal", "extra_applications": 1 }
```
After:
```json
{
  "type": "neoorigins:action_on_event",
  "event": "mod_bonemeal_extra",
  "modifier": { "operation": "add_base", "value": 1.0 }
}
```

## → `neoorigins:persistent_effect`

Collapses status-effect variants into a single list-of-effects shape.

### `neoorigins:status_effect`

Pass-through: the legacy JSON shape already matches `persistent_effect`'s top-level fallback parse. Just change the `type`. Toggleable stays `true` by default (matching `AbstractTogglePower` semantics).

### `neoorigins:stacking_status_effects`

Effects list passes through verbatim. The remap forces `"toggleable": false` since `persistent_effect` defaults to toggleable-on.

Before:
```json
{
  "type": "neoorigins:stacking_status_effects",
  "effects": [
    { "effect": "minecraft:speed", "amplifier": 1 },
    { "effect": "minecraft:strength", "amplifier": 0 }
  ]
}
```
After:
```json
{
  "type": "neoorigins:persistent_effect",
  "toggleable": false,
  "effects": [
    { "effect": "minecraft:speed", "amplifier": 1 },
    { "effect": "minecraft:strength", "amplifier": 0 }
  ]
}
```

### `neoorigins:night_vision`

Before:
```json
{ "type": "neoorigins:night_vision" }
```
After:
```json
{
  "type": "neoorigins:persistent_effect",
  "effects": [
    {
      "effect": "minecraft:night_vision",
      "amplifier": 0,
      "ambient": true,
      "show_particles": false,
      "show_icon": false
    }
  ]
}
```

### `neoorigins:glow`

Same shape as `night_vision` but with `"effect": "minecraft:glowing"` and `"show_icon": true`.

### `neoorigins:water_breathing`

Before:
```json
{ "type": "neoorigins:water_breathing" }
```
After (gated by `origins:in_water`, not toggleable, icon hidden):
```json
{
  "type": "neoorigins:persistent_effect",
  "toggleable": false,
  "condition": { "type": "origins:in_water" },
  "effects": [
    {
      "effect": "minecraft:water_breathing",
      "amplifier": 0,
      "ambient": true,
      "show_particles": false,
      "show_icon": false
    }
  ]
}
```

## → `neoorigins:condition_passive`

Tick-based condition + action pairs. Default `interval` is 20 ticks.

### `neoorigins:biome_buff`

Before:
```json
{
  "type": "neoorigins:biome_buff",
  "biome_tag": "minecraft:is_forest",
  "effect": "minecraft:regeneration",
  "amplifier": 0
}
```
After:
```json
{
  "type": "neoorigins:condition_passive",
  "condition": { "type": "origins:biome", "tag": "minecraft:is_forest" },
  "entity_action": {
    "type": "origins:apply_effect",
    "effect": "minecraft:regeneration",
    "duration": 300,
    "amplifier": 0
  },
  "interval": 20
}
```

### `neoorigins:damage_in_biome`

Before:
```json
{
  "type": "neoorigins:damage_in_biome",
  "biome_tag": "minecraft:is_ocean",
  "damage_per_second": 1.0,
  "damage_type": "generic"
}
```
After:
```json
{
  "type": "neoorigins:condition_passive",
  "condition": { "type": "origins:biome", "tag": "minecraft:is_ocean" },
  "entity_action": {
    "type": "origins:damage",
    "amount": 1.0,
    "source": { "name": "generic" }
  },
  "interval": 20
}
```

### `neoorigins:damage_in_daylight`

Before:
```json
{
  "type": "neoorigins:damage_in_daylight",
  "damage_per_second": 1.0,
  "ignite": false
}
```
After (composed with `origins:and` + `origins:not`):
```json
{
  "type": "neoorigins:condition_passive",
  "condition": {
    "type": "origins:and",
    "conditions": [
      { "type": "origins:exposed_to_sun" },
      { "type": "origins:not", "condition": { "type": "origins:in_water" } },
      { "type": "origins:not", "condition": { "type": "origins:on_fire" } }
    ]
  },
  "entity_action": {
    "type": "origins:damage",
    "amount": 1.0,
    "source": { "name": "in_fire" }
  },
  "interval": 20
}
```
When `"ignite": true`, `entity_action` becomes `{ "type": "origins:set_on_fire", "ticks": 40 }` instead.

### `neoorigins:damage_in_water`

Before:
```json
{
  "type": "neoorigins:damage_in_water",
  "damage_per_second": 1.0,
  "include_rain": true
}
```
After (rain included via `origins:or`):
```json
{
  "type": "neoorigins:condition_passive",
  "condition": {
    "type": "origins:or",
    "conditions": [
      { "type": "origins:in_water" },
      { "type": "origins:in_rain" }
    ]
  },
  "entity_action": {
    "type": "origins:damage",
    "amount": 1.0,
    "source": { "name": "magic" }
  },
  "interval": 20
}
```
With `"include_rain": false`, the `condition` is just `{ "type": "origins:in_water" }`.

### `neoorigins:burn_at_health_threshold`

Before:
```json
{
  "type": "neoorigins:burn_at_health_threshold",
  "threshold_percent": 0.25,
  "fire_ticks": 60
}
```
After:
```json
{
  "type": "neoorigins:condition_passive",
  "condition": {
    "type": "origins:relative_health",
    "comparison": "<=",
    "compare_to": 0.25
  },
  "entity_action": { "type": "origins:set_on_fire", "ticks": 60 },
  "interval": 20
}
```

### `neoorigins:regen_in_fluid`

Before (water):
```json
{
  "type": "neoorigins:regen_in_fluid",
  "fluid": "water",
  "amount_per_second": 1.0
}
```
After:
```json
{
  "type": "neoorigins:condition_passive",
  "condition": { "type": "origins:in_water" },
  "entity_action": { "type": "origins:heal", "amount": 1.0 },
  "interval": 20
}
```
For `"fluid": "lava"`, `condition` is `{ "type": "origins:submerged_in", "fluid": "minecraft:lava" }`.

## → `neoorigins:attribute_modifier`

Consolidated attribute modifier with optional condition / equipment_condition / location_condition gates (these fields pre-date 2.0 — see v1.13 release notes).

### `neoorigins:less_item_use_slowdown`

Before:
```json
{
  "type": "neoorigins:less_item_use_slowdown",
  "speed_multiplier": 0.5,
  "item_type": "any"
}
```
After:
```json
{
  "type": "neoorigins:attribute_modifier",
  "attribute": "minecraft:movement_speed",
  "amount": 0.5,
  "operation": "add_multiplied_base",
  "condition": { "type": "origins:using_item" }
}
```
**Lossy:** the legacy `item_type` filter (`"bow"` / `"shield"`) is dropped — the alias applies to any item-use. A `_migration_note` field is written into the JSON at load time warning the pack author. If you need the filter, stay on the legacy class until a future DSL verb lands.

## → `neoorigins:active_ability`

Active-ability DSL collapses eight legacy active types onto `entity_action`.

### `neoorigins:active_launch`

Before:
```json
{ "type": "neoorigins:active_launch", "power": 1.5 }
```
After:
```json
{
  "type": "neoorigins:active_ability",
  "entity_action": { "type": "origins:add_velocity", "y": 1.5 }
}
```

### `neoorigins:active_dash`

Before:
```json
{ "type": "neoorigins:active_dash", "strength": 1.2 }
```
After (lossy — horizontal impulse approximates look-direction dash):
```json
{
  "type": "neoorigins:active_ability",
  "entity_action": {
    "type": "neoorigins:pull_entities",
    "radius": 0,
    "strength": -1.2
  }
}
```

### `neoorigins:repulse`

Before:
```json
{ "type": "neoorigins:repulse", "radius": 6.0, "strength": 1.0 }
```
After:
```json
{
  "type": "neoorigins:active_ability",
  "entity_action": {
    "type": "neoorigins:pull_entities",
    "radius": 6.0,
    "strength": -1.0,
    "include_players": true
  }
}
```

### `neoorigins:active_aoe_effect`

Before:
```json
{
  "type": "neoorigins:active_aoe_effect",
  "radius": 8.0,
  "effect": "minecraft:weakness",
  "duration": 200,
  "amplifier": 0
}
```
After:
```json
{
  "type": "neoorigins:active_ability",
  "entity_action": {
    "type": "origins:area_of_effect",
    "radius": 8.0,
    "entity_action": {
      "type": "origins:apply_effect",
      "effect": "minecraft:weakness",
      "duration": 200,
      "amplifier": 0
    }
  }
}
```

### `neoorigins:active_swap`

Before:
```json
{ "type": "neoorigins:active_swap", "range": 20.0 }
```
After (swaps with NEAREST entity in radius — legacy used a look-direction raycast):
```json
{
  "type": "neoorigins:active_ability",
  "entity_action": { "type": "neoorigins:swap_with_entity", "radius": 20.0 }
}
```

### `neoorigins:active_fireball`

Before:
```json
{ "type": "neoorigins:active_fireball", "speed": 1.5 }
```
After (lossy — legacy fired 3–4 fireballs with spread; alias fires one):
```json
{
  "type": "neoorigins:active_ability",
  "entity_action": {
    "type": "origins:spawn_projectile",
    "entity_type": "minecraft:small_fireball",
    "speed": 1.5
  }
}
```

### `neoorigins:active_bolt`

Before:
```json
{ "type": "neoorigins:active_bolt", "speed": 1.2 }
```
After:
```json
{
  "type": "neoorigins:active_ability",
  "entity_action": {
    "type": "origins:spawn_projectile",
    "entity_type": "minecraft:wind_charge",
    "speed": 1.2
  }
}
```

### `neoorigins:healing_mist`

Before:
```json
{
  "type": "neoorigins:healing_mist",
  "heal_amount": 6.0,
  "radius": 8.0,
  "heal_self": true
}
```
After:
```json
{
  "type": "neoorigins:active_ability",
  "entity_action": {
    "type": "origins:area_of_effect",
    "radius": 8.0,
    "include_source": true,
    "entity_action": { "type": "origins:heal", "amount": 6.0 }
  }
}
```

---

# Cross-mod compat aliases

Foreign-namespace equivalents for packs extracted from Apugli (abandoned at 2.11.0+1.20.4) and legacy Apoli. These bypass the `origins:` / `apace:` translator because `isOriginsFormat` doesn't match their prefixes.

### `apugli:edible_item` / `apoli:edible_item` → `neoorigins:edible_item`

The remap hoists singular `item` → `items[]`, singular `tag` → `tags[]`, and flattens `food_component.{nutrition, saturation_modifier, always_edible}` up to the root.

Before:
```json
{
  "type": "apugli:edible_item",
  "item": "minecraft:rotten_flesh",
  "food_component": {
    "nutrition": 4,
    "saturation_modifier": 0.2,
    "always_edible": true
  }
}
```
After:
```json
{
  "type": "neoorigins:edible_item",
  "items": ["minecraft:rotten_flesh"],
  "nutrition": 4,
  "saturation": 0.2,
  "always_edible": true
}
```
Apugli-only fields `effect`, `action`, `return_stack` are dropped (same policy as the origins/apace translator).

### `apugli:action_on_jump` → `action_on_event { event: jump }`

Thin event-key injection; `entity_action` passes through verbatim.

### `apugli:action_on_target_death` → `action_on_event { event: kill }`

Thin event-key injection; `entity_action` passes through verbatim.

---

# Power types NOT retired

These legacy types kept their standalone classes because the DSL can't express them yet (too stateful, not an event trigger, or no matching verb exists). They still work, just aren't collapsed.

**Active abilities (stateful / specialised):**
- `neoorigins:active_teleport` — no look-direction teleport verb
- `neoorigins:active_recall` — stateful saved position
- `neoorigins:active_place_block` — no raycast-and-place verb
- `neoorigins:shadow_orb` — stateful orb with tick loop
- `neoorigins:ground_slam` — AoE on mobs; `area_of_effect` is players-only
- `neoorigins:tidal_wave` — cone shape not modelled in `area_of_effect`
- `neoorigins:active_phase` — movement state toggle, not an active ability

**Non-tick-based condition interceptors:**
- `neoorigins:mobs_ignore_player` — `LivingChangeTargetEvent` interceptor
- `neoorigins:no_mob_spawns_nearby` — `MobSpawnEvent.FinalizeSpawn` interceptor
- `neoorigins:item_magnetism` — item-entity pull, no DSL verb yet
- `neoorigins:breath_in_fluid` — air-supply drain, no DSL verb yet

**Attribute-modifier candidates held back:**
- `neoorigins:break_speed_modifier` — `PlayerEvent.BreakSpeed` fires client-side only in current NeoForge
- `neoorigins:underwater_mining_speed` — same BreakSpeed constraint
- `neoorigins:no_slowdown` — unwired holder pending a slowdown-source DSL

**Persistent-effect candidates held back:**
- `neoorigins:effect_immunity` — migrates under a different generic in a later phase

See the carve-out comments in `LegacyPowerTypeAliases.registerActiveAbilityAliases`, `registerConditionPassiveAliases`, `registerAttributeModifierAliases`, and `registerPersistentEffectAliases` for the authoritative list. Phase 7+ may add raycast / cone / mob-AoE verbs and shrink this list further.

---

# Verb namespace migration — `origins:` / `apace:` → `neoorigins:`

In 2.0.0 the canonical DSL verb namespace is `neoorigins:`. The legacy
`origins:` and `apace:` prefixes still parse — every verb your pack uses
today will keep working — but they emit a one-shot `[2.0-legacy]` warning
at first use:

```
[2.0-legacy] DSL verb 'origins:damage' is deprecated — use 'neoorigins:damage'
```

Grep your logs for `[2.0-legacy]` to get the migration punch list. **Verbs
under the legacy prefixes will be removed no earlier than NeoOrigins 3.0**
— you have the full 2.x lifecycle to migrate.

## Why

Pre-2.0, the parser was an Apoli-compat layer. Verb names lived under
`origins:` because that's what upstream Apoli used. When 2.0 introduced
its own power types (`neoorigins:condition_passive`, `neoorigins:modify_damage`,
...) the DSL verbs they *composed with* stayed on `origins:*`, producing
the awkward mix of namespaces inside a single power JSON:

```jsonc
// Pre-2.0 style — mixed namespaces
{
  "type": "neoorigins:condition_passive",
  "condition": { "type": "origins:biome", "tag": "minecraft:is_nether" },
  "entity_action": { "type": "origins:damage", "amount": 1.0 },
  "interval": 20
}
```

The canonicalization fixes this. Power types, condition verbs, and action
verbs all live under the same namespace:

```jsonc
// 2.0.0 canonical
{
  "type": "neoorigins:condition_passive",
  "condition": { "type": "neoorigins:biome", "tag": "minecraft:is_nether" },
  "entity_action": { "type": "neoorigins:damage", "amount": 1.0 },
  "interval": 20
}
```

## How to migrate

For a quick pass, run a find-replace over your pack's JSON:

```bash
# On every power JSON:
sed -i 's/"type": *"origins:/"type": "neoorigins:/g' path/to/powers/*.json
sed -i 's/"type": *"apace:/"type": "neoorigins:/g' path/to/powers/*.json
```

The verb names are identical — only the namespace changes. No field
shapes were changed as part of this canonicalization.

Bare verb names (`"type": "and"`) still work and are auto-prefixed; the
autoprefix target moved from `origins:` → `neoorigins:` for the 2.0
release. If your pack relies on bare names and needed them resolved as
`origins:*` for some reason, write them out explicitly.

## Verbs that were already `neoorigins:*`

A dozen or so verbs introduced fresh in 2.0 were already namespaced
correctly: `neoorigins:in_set`, `neoorigins:no_minions_alive`,
`neoorigins:hit_taken_amount`, `neoorigins:food_item_in_tag`,
`neoorigins:toggle`, `neoorigins:add_to_set`, `neoorigins:remove_from_set`,
`neoorigins:chain_to_nearest`, `neoorigins:pull_entities`,
`neoorigins:swap_with_entity`, `neoorigins:teleport_to_marker`,
`neoorigins:damage_attacker`, `neoorigins:ignite_attacker`,
`neoorigins:effect_on_attacker`, `neoorigins:random_teleport`,
`neoorigins:cancel_event`, `neoorigins:spawn_projectile`.

These were unchanged.

---

# Lossy translations — aliases that drop fields

A handful of aliases simplify the legacy behaviour because no 2.0 DSL verb
matches the legacy 1:1. They still work, but a pack that actually relied on
the dropped field will behave differently after the alias kicks in. Keep the
legacy type declaration (i.e. let the legacy class stay registered) if any
of these matter for your pack.

| Legacy type | What's dropped | Workaround / roadmap |
|---|---|---|
| `active_dash` | Look-direction projection — the alias lands a horizontal impulse; the legacy class computed the dash vector from the player's look axis. | Needs a `look_direction` vector DSL verb. Targeted for Phase 7. Until then, keep the legacy class or expect roughly horizontal dashes. |
| `active_fireball` | Multi-shot spread — legacy fired 3–4 small fireballs with a random spread cone. The alias shoots one. | No multi-shot DSL verb yet. Legacy class still registered; keep it for shotgun behaviour. |
| `less_item_use_slowdown` | Item-type filter (`item_type: bow` / `shield` / `any`). Alias applies to any item-use. | No item-type condition verb yet. Packs filtering by tool keep the legacy class. |

Aliases that inject a `_migration_note` field into the rewritten JSON will
also have this flagged in the runtime log the first time they fire.

---

# DSL gaps worth knowing about

Runtime hooks and verb shapes that don't exist in 2.0 yet — flagged in
`LegacyPowerTypeAliases.java` as carve-outs. Each one blocks one or more
alias collapses until it lands.

| Missing verb / hook | Blocks | Notes |
|---|---|---|
| Look-direction vector | `active_dash` full port, any future `look_impulse` | Needs a `direction_source: look` field on `add_velocity`. |
| Multi-shot projectile | `active_fireball` shotgun, future `spray` powers | Either loop `spawn_projectile` × N with a spread seed, or a dedicated `spawn_projectile_spread` verb. |
| Raycast-and-place block | `active_place_block` | Needs a block-placement action with a raycast source. |
| Cone-shape AoE | `tidal_wave` | Existing `area_of_effect` is radial only; needs an angled/cone variant. |
| AoE on mobs (not players) | `ground_slam` | `area_of_effect` targets players only by default; needs a mob-target mode. |
| Item-type condition | `less_item_use_slowdown` filter | Predicate on the item stack the player is currently using. |
| Slowdown-source condition | `no_slowdown` | Need to tell movement-slowdown from tick-slowdown vs item-use slowdown. |
| Air-supply modifier action | `breath_in_fluid` | Either a tick-driven attribute or a dedicated air-drain action. |
| Item-entity pull action | `item_magnetism` | Needs an entity-attraction action with item-entity filter. |
| Server-authoritative `PlayerEvent.BreakSpeed` | `break_speed_modifier`, `underwater_mining_speed` collapses | BreakSpeed fires client-only in current NeoForge; packs use the attribute fallback in the concrete class. |

---

# Questions

If you hit a legacy type not documented here, check `LegacyPowerTypeAliases.java` — it's the source of truth. Or open an issue.
