---
title: Conditions
parent: "DSL Reference"
nav_order: 4
has_children: true
---

# NeoOrigins 2.0 Condition Reference

Conditions evaluate to true/false against an entity (usually the power's owning player). They gate power activation, `action_on_event` triggers, `conditional` wrappers, bientity interactions, and (as of v2.0.20) **any power type** via the universal condition gate.

**Canonical namespace:** `neoorigins:*` is the preferred form for new packs. Any other namespace (`origins:`, `apace:`, `apoli:`, `medievalorigins:`, …) is rewritten to `neoorigins:` plus the same name, and logs a one-shot `[2.0-legacy]` deprecation warning per distinct type. Bare type names (e.g. `"type": "and"`) are auto-prefixed with `neoorigins:`. Section headers and JSON examples below use the canonical `neoorigins:*` form.

**Fail-closed semantics:** a malformed or unsupported condition logs a warning and returns `false` rather than throwing. Bientity / damage / food conditions that require a dispatch context also return `false` when evaluated outside that context.

**Object or array.** A power's own condition field (`condition`, `regen_condition`, …) accepts either a single condition object **or** an array. An array is combined as logical AND (an implicit `neoorigins:and`): every element must pass. An empty array is treated as always-true. *(Previously a bare array in these fields silently no-opped.)* Inside a condition, a single-condition field (`not`'s `condition`, `actor_condition`'s `condition`, …) must be an object; the `conditions` list of `and`/`or` accepts an array or a single object.

**Universal `inverted` field:** every condition supports a top-level `"inverted": true` flag that flips its result. Compatible with the Apoli/Origins convention used by upstream packs.

# Universal power condition gate (v2.0.20+)

Every power type (passive, toggle, active, persistent effect, compat) supports an optional top-level `power_condition` field and `power_condition_mode` field. This allows any power to be gated or blocked by entity conditions without needing per-type condition support.

> **Note:** The field is named `power_condition` (not `condition`) to avoid colliding with power types that already use `condition` in their own config (e.g. `condition_passive`, `action_on_event`, `attribute_modifier`). On types that do **not** claim `condition` for their own config, a top-level `condition` is accepted as an alias for `power_condition` with mode `allow`. See the common-fields table in [POWER_TYPES.md](POWER_TYPES.md) for the exact alias rules.

| Field | Type | Default | Description |
|---|---|---|---|
| `power_condition` | condition object | _(none)_ | Entity condition using the full DSL documented below |
| `power_condition_mode` | `"deny"` or `"allow"` | `"deny"` | How the condition result maps to power operation |

**Modes:**
- `"deny"` (default): the power is **disabled** when the condition evaluates to true. Use this to express "this power doesn't work when X".
- `"allow"`: the power **only operates** when the condition evaluates to true. Use this to express "this power only works when X".

**What gets gated:** `onTick`, `onActivated` (keybind), `onHit`, `onKill`, and capability checks are all suppressed when the condition is not satisfied. Lifecycle methods (`onGranted`, `onRevoked`, `onLogin`, `onRespawn`) are never gated, so powers can still set up and tear down correctly.

**Examples:**

Block wraith phase when in water AND exposed to sun:
```json
{
  "type": "neoorigins:wraith_phase",
  "blocked_blocks": ["minecraft:obsidian"],
  "power_condition": {
    "type": "neoorigins:and",
    "conditions": [
      { "type": "neoorigins:in_water" },
      { "type": "neoorigins:exposed_to_sun" }
    ]
  },
  "power_condition_mode": "deny"
}
```

Flight only works in the Nether:
```json
{
  "type": "neoorigins:flight",
  "power_condition": { "type": "neoorigins:dimension", "dimension": "minecraft:the_nether" },
  "power_condition_mode": "allow"
}
```

Active teleport blocked while sneaking:
```json
{
  "type": "neoorigins:active_teleport",
  "range": 32,
  "cooldown_ticks": 60,
  "power_condition": { "type": "neoorigins:sneaking" },
  "power_condition_mode": "deny"
}
```

```json
// "true when the player is NOT swimming"
{ "type": "neoorigins:swimming", "inverted": true }

// inside an AND, etc.
{ "type": "neoorigins:and", "conditions": [
  { "type": "neoorigins:creative_flying" },
  { "type": "neoorigins:on_fire", "inverted": true }
]}
```

The `inverted` flag is also honored on nested conditions inside `and` / `or` / `not` wrappers: every recursive parse pass checks for it.

**Common comparison fields:** most numeric conditions accept `"comparison"` (one of `==`, `!=`, `<`, `<=`, `>`, `>=`) and `"compare_to"` (number). Defaults to `">="` and `0` unless stated otherwise.

**Item conditions**, the separate verb set that matches against an `ItemStack` (used inside `equipped_item` / `inventory` entity conditions, event-power `item_condition` filters, and `if_else` item actions), is documented in the [Item conditions](conditions/item.md).

---

## Pages

- <a id="meta-conditions"></a>[Meta conditions](conditions/meta.md): Combinators and constants: `and`, `or`, `not`, `actor_condition` and `constant`.
- <a id="entity-conditions"></a><a id="more-entity-conditions"></a>[Entity conditions](conditions/entity.md): Conditions on an entity, usually the player: environment, state, equipment, resources and powers.
- <a id="context-aware-conditions"></a><a id="bientity-conditions"></a><a id="damage-conditions"></a>[Context, bientity & damage conditions](conditions/context.md): Conditions that need a dispatch context: the event in progress, a second entity, or a damage source. Outside that context they return false.

<div id="no-anchor-stub"></div>

## Every entry

| Entry | Page |
|---|---|
| <a id="neooriginsand-alias-neooriginsall_of"></a>[`neoorigins:and` (alias `neoorigins:all_of`)](conditions/meta.md#neooriginsand-alias-neooriginsall_of) | [Meta conditions](conditions/meta.md) |
| <a id="neooriginsor-alias-neooriginsany_of"></a>[`neoorigins:or` (alias `neoorigins:any_of`)](conditions/meta.md#neooriginsor-alias-neooriginsany_of) | [Meta conditions](conditions/meta.md) |
| <a id="neooriginsnot"></a>[`neoorigins:not`](conditions/meta.md#neooriginsnot) | [Meta conditions](conditions/meta.md) |
| <a id="neooriginsactor_condition"></a>[`neoorigins:actor_condition`](conditions/meta.md#neooriginsactor_condition) | [Meta conditions](conditions/meta.md) |
| <a id="neooriginsconstant"></a>[`neoorigins:constant`](conditions/meta.md#neooriginsconstant) | [Meta conditions](conditions/meta.md) |
| <a id="neooriginssneaking"></a>[`neoorigins:sneaking`](conditions/entity.md#neooriginssneaking) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginssprinting"></a>[`neoorigins:sprinting`](conditions/entity.md#neooriginssprinting) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginson_ground"></a>[`neoorigins:on_ground`](conditions/entity.md#neooriginson_ground) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsin_water"></a>[`neoorigins:in_water`](conditions/entity.md#neooriginsin_water) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsswimming"></a>[`neoorigins:swimming`](conditions/entity.md#neooriginsswimming) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginssubmerged_in_water"></a>[`neoorigins:submerged_in_water`](conditions/entity.md#neooriginssubmerged_in_water) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsfall_flying"></a>[`neoorigins:fall_flying`](conditions/entity.md#neooriginsfall_flying) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsinvisible"></a>[`neoorigins:invisible`](conditions/entity.md#neooriginsinvisible) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsmoving"></a>[`neoorigins:moving`](conditions/entity.md#neooriginsmoving) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsin_rain"></a>[`neoorigins:in_rain`](conditions/entity.md#neooriginsin_rain) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsdaytime"></a>[`neoorigins:daytime`](conditions/entity.md#neooriginsdaytime) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsexposed_to_sky"></a>[`neoorigins:exposed_to_sky`](conditions/entity.md#neooriginsexposed_to_sky) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsexposed_to_sun"></a><a id="umbrella-items"></a>[`neoorigins:exposed_to_sun`](conditions/entity.md#neooriginsexposed_to_sun) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginson_fire-alias-neooriginsfire"></a>[`neoorigins:on_fire` (alias `neoorigins:fire`)](conditions/entity.md#neooriginson_fire-alias-neooriginsfire) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginspassenger-alias-neooriginsriding"></a>[`neoorigins:passenger` (alias `neoorigins:riding`)](conditions/entity.md#neooriginspassenger-alias-neooriginsriding) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsusing_item"></a>[`neoorigins:using_item`](conditions/entity.md#neooriginsusing_item) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsusing_effective_tool"></a>[`neoorigins:using_effective_tool`](conditions/entity.md#neooriginsusing_effective_tool) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsticking"></a>[`neoorigins:ticking`](conditions/entity.md#neooriginsticking) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsexists"></a>[`neoorigins:exists`](conditions/entity.md#neooriginsexists) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsliving"></a>[`neoorigins:living`](conditions/entity.md#neooriginsliving) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsalways_active"></a>[`neoorigins:always_active`](conditions/entity.md#neooriginsalways_active) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginscreative_flying"></a>[`neoorigins:creative_flying`](conditions/entity.md#neooriginscreative_flying) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginscreative_mode"></a>[`neoorigins:creative_mode`](conditions/entity.md#neooriginscreative_mode) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsblock_collision"></a>[`neoorigins:block_collision`](conditions/entity.md#neooriginsblock_collision) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginscollided_horizontally"></a>[`neoorigins:collided_horizontally`](conditions/entity.md#neooriginscollided_horizontally) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsreplacable-alias-neooriginsreplaceable"></a>[`neoorigins:replacable` (alias `neoorigins:replaceable`)](conditions/entity.md#neooriginsreplacable-alias-neooriginsreplaceable) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginshealth"></a>[`neoorigins:health`](conditions/entity.md#neooriginshealth) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsrelative_health"></a>[`neoorigins:relative_health`](conditions/entity.md#neooriginsrelative_health) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsfood_level-alias-neooriginsfood"></a>[`neoorigins:food_level` (alias `neoorigins:food`)](conditions/entity.md#neooriginsfood_level-alias-neooriginsfood) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginssaturation_level"></a>[`neoorigins:saturation_level`](conditions/entity.md#neooriginssaturation_level) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsfall_distance"></a>[`neoorigins:fall_distance`](conditions/entity.md#neooriginsfall_distance) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsheight"></a>[`neoorigins:height`](conditions/entity.md#neooriginsheight) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsdistance_from_coordinates"></a>[`neoorigins:distance_from_coordinates`](conditions/entity.md#neooriginsdistance_from_coordinates) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsarmor_value"></a>[`neoorigins:armor_value`](conditions/entity.md#neooriginsarmor_value) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsamount"></a>[`neoorigins:amount`](conditions/entity.md#neooriginsamount) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsxp_level"></a>[`neoorigins:xp_level`](conditions/entity.md#neooriginsxp_level) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsxp_levels"></a>[`neoorigins:xp_levels`](conditions/entity.md#neooriginsxp_levels) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsxp_points"></a>[`neoorigins:xp_points`](conditions/entity.md#neooriginsxp_points) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsair"></a>[`neoorigins:air`](conditions/entity.md#neooriginsair) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsdimension"></a>[`neoorigins:dimension`](conditions/entity.md#neooriginsdimension) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsbiome"></a><a id="nested-condition-the-biome-sub-grammar"></a>[`neoorigins:biome`](conditions/entity.md#neooriginsbiome) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsin_tag"></a>[`neoorigins:in_tag`](conditions/entity.md#neooriginsin_tag) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginssubmerged_in"></a>[`neoorigins:submerged_in`](conditions/entity.md#neooriginssubmerged_in) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsfluid_height"></a>[`neoorigins:fluid_height`](conditions/entity.md#neooriginsfluid_height) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginstemperature"></a>[`neoorigins:temperature`](conditions/entity.md#neooriginstemperature) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsbody_temperature"></a>[`neoorigins:body_temperature`](conditions/entity.md#neooriginsbody_temperature) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginslight_level"></a>[`neoorigins:light_level`](conditions/entity.md#neooriginslight_level) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsbrightness"></a>[`neoorigins:brightness`](conditions/entity.md#neooriginsbrightness) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginstime_of_day"></a>[`neoorigins:time_of_day`](conditions/entity.md#neooriginstime_of_day) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsweather"></a>[`neoorigins:weather`](conditions/entity.md#neooriginsweather) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsmoon_phase"></a>[`neoorigins:moon_phase`](conditions/entity.md#neooriginsmoon_phase) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginson_block"></a>[`neoorigins:on_block`](conditions/entity.md#neooriginson_block) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsblock"></a>[`neoorigins:block`](conditions/entity.md#neooriginsblock) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsin_block"></a>[`neoorigins:in_block`](conditions/entity.md#neooriginsin_block) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsin_block_anywhere"></a><a id="when-a-block-condition-cannot-be-read"></a>[`neoorigins:in_block_anywhere`](conditions/entity.md#neooriginsin_block_anywhere) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginshardness"></a>[`neoorigins:hardness`](conditions/entity.md#neooriginshardness) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsentity_type"></a>[`neoorigins:entity_type`](conditions/entity.md#neooriginsentity_type) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsequipped_item"></a>[`neoorigins:equipped_item`](conditions/entity.md#neooriginsequipped_item) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsinventory"></a>[`neoorigins:inventory`](conditions/entity.md#neooriginsinventory) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsenchantment"></a>[`neoorigins:enchantment`](conditions/entity.md#neooriginsenchantment) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsresource"></a>[`neoorigins:resource`](conditions/entity.md#neooriginsresource) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsresource_level"></a>[`neoorigins:resource_level`](conditions/entity.md#neooriginsresource_level) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginspower"></a>[`neoorigins:power`](conditions/entity.md#neooriginspower) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginspower_active"></a>[`neoorigins:power_active`](conditions/entity.md#neooriginspower_active) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginspower_type"></a>[`neoorigins:power_type`](conditions/entity.md#neooriginspower_type) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsorigin"></a>[`neoorigins:origin`](conditions/entity.md#neooriginsorigin) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginscooldown"></a>[`neoorigins:cooldown`](conditions/entity.md#neooriginscooldown) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsnbt"></a>[`neoorigins:nbt`](conditions/entity.md#neooriginsnbt) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsscoreboard"></a>[`neoorigins:scoreboard`](conditions/entity.md#neooriginsscoreboard) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsstatistic"></a>[`neoorigins:statistic`](conditions/entity.md#neooriginsstatistic) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginscommand"></a>[`neoorigins:command`](conditions/entity.md#neooriginscommand) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsadvancement"></a>[`neoorigins:advancement`](conditions/entity.md#neooriginsadvancement) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginspredicate"></a>[`neoorigins:predicate`](conditions/entity.md#neooriginspredicate) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsconfig_flag"></a>[`neoorigins:config_flag`](conditions/entity.md#neooriginsconfig_flag) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginshit_taken_amount"></a>[`neoorigins:hit_taken_amount`](conditions/context.md#neooriginshit_taken_amount) | [Context, bientity & damage conditions](conditions/context.md) |
| <a id="neooriginshit_dealt_amount"></a>[`neoorigins:hit_dealt_amount`](conditions/context.md#neooriginshit_dealt_amount) | [Context, bientity & damage conditions](conditions/context.md) |
| <a id="neooriginsfood_item_in_tag"></a><a id="built-in-diet-tags"></a>[`neoorigins:food_item_in_tag`](conditions/context.md#neooriginsfood_item_in_tag) | [Context, bientity & damage conditions](conditions/context.md) |
| <a id="neooriginsfood_item_id"></a>[`neoorigins:food_item_id`](conditions/context.md#neooriginsfood_item_id) | [Context, bientity & damage conditions](conditions/context.md) |
| <a id="neooriginsfood_item_in_config_list"></a>[`neoorigins:food_item_in_config_list`](conditions/context.md#neooriginsfood_item_in_config_list) | [Context, bientity & damage conditions](conditions/context.md) |
| <a id="neooriginsdistance"></a>[`neoorigins:distance`](conditions/context.md#neooriginsdistance) | [Context, bientity & damage conditions](conditions/context.md) |
| <a id="neooriginscan_see"></a>[`neoorigins:can_see`](conditions/context.md#neooriginscan_see) | [Context, bientity & damage conditions](conditions/context.md) |
| <a id="neooriginsequal"></a>[`neoorigins:equal`](conditions/context.md#neooriginsequal) | [Context, bientity & damage conditions](conditions/context.md) |
| <a id="neooriginstarget_type"></a>[`neoorigins:target_type`](conditions/context.md#neooriginstarget_type) | [Context, bientity & damage conditions](conditions/context.md) |
| <a id="neooriginstarget_group"></a>[`neoorigins:target_group`](conditions/context.md#neooriginstarget_group) | [Context, bientity & damage conditions](conditions/context.md) |
| <a id="neooriginsin_set"></a>[`neoorigins:in_set`](conditions/context.md#neooriginsin_set) | [Context, bientity & damage conditions](conditions/context.md) |
| <a id="neooriginsno_minions_alive"></a>[`neoorigins:no_minions_alive`](conditions/context.md#neooriginsno_minions_alive) | [Context, bientity & damage conditions](conditions/context.md) |
| <a id="neooriginsfrom_fire"></a>[`neoorigins:from_fire`](conditions/context.md#neooriginsfrom_fire) | [Context, bientity & damage conditions](conditions/context.md) |
| <a id="neooriginsfrom_projectile"></a>[`neoorigins:from_projectile`](conditions/context.md#neooriginsfrom_projectile) | [Context, bientity & damage conditions](conditions/context.md) |
| <a id="neooriginsfrom_explosion"></a>[`neoorigins:from_explosion`](conditions/context.md#neooriginsfrom_explosion) | [Context, bientity & damage conditions](conditions/context.md) |
| <a id="neooriginsdamage_type"></a>[`neoorigins:damage_type`](conditions/context.md#neooriginsdamage_type) | [Context, bientity & damage conditions](conditions/context.md) |
| <a id="neooriginsdamage_tag"></a>[`neoorigins:damage_tag`](conditions/context.md#neooriginsdamage_tag) | [Context, bientity & damage conditions](conditions/context.md) |
| <a id="neooriginsdamage_name-alias-neooriginsname"></a>[`neoorigins:damage_name` (alias `neoorigins:name`)](conditions/context.md#neooriginsdamage_name-alias-neooriginsname) | [Context, bientity & damage conditions](conditions/context.md) |
| <a id="neooriginsnight"></a>[`neoorigins:night`](conditions/entity.md#neooriginsnight) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsthundering"></a>[`neoorigins:thundering`](conditions/entity.md#neooriginsthundering) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginshas_effect"></a>[`neoorigins:has_effect`](conditions/entity.md#neooriginshas_effect) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsstatus_effect"></a>[`neoorigins:status_effect`](conditions/entity.md#neooriginsstatus_effect) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsclimbing"></a>[`neoorigins:climbing`](conditions/entity.md#neooriginsclimbing) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsclimbing_gate"></a>[`neoorigins:climbing_gate`](conditions/entity.md#neooriginsclimbing_gate) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsout_of_combat"></a>[`neoorigins:out_of_combat`](conditions/entity.md#neooriginsout_of_combat) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginscover-alias-neooriginscovered_by_block"></a>[`neoorigins:cover` (alias `neoorigins:covered_by_block`)](conditions/entity.md#neooriginscover-alias-neooriginscovered_by_block) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsnear_block"></a>[`neoorigins:near_block`](conditions/entity.md#neooriginsnear_block) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsblock_in_radius"></a>[`neoorigins:block_in_radius`](conditions/entity.md#neooriginsblock_in_radius) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsnear_entity"></a>[`neoorigins:near_entity`](conditions/entity.md#neooriginsnear_entity) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsnearby_entities"></a>[`neoorigins:nearby_entities`](conditions/entity.md#neooriginsnearby_entities) | [Entity conditions](conditions/entity.md) |
| <a id="neooriginsnear_villager"></a>[`neoorigins:near_villager`](conditions/entity.md#neooriginsnear_villager) | [Entity conditions](conditions/entity.md) |
