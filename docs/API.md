---
title: API Overview
parent: "DSL Reference"
nav_order: 1
---


# NeoOrigins 2.0 API Reference

Single landing page for the NeoOrigins datapack API. Every power type,
condition verb, action verb, and event key is listed below with a jump-link
to the per-topic detail doc.

- [Layer model](#layer-model)
- [Documents in this API](#documents-in-this-api)
- [Power types](#power-types): 120 registered types plus the `multiple` container, and 29 retired ids
- [Condition verbs](#condition-verbs): 108 conditions
- [Action verbs](#action-verbs): 94 actions
- [Event keys](#event-keys): 51 events
- [Named keybinds](concepts/keybinds.md)
- [Active theme datapack file](#active-theme-datapack-file)
- [Namespaces & prefixes](#namespaces--prefixes)
- [JSON schemas](#json-schemas)

---

## Layer model

A NeoOrigins pack is composed of four layers, each authored in JSON:

1. **Origin layers** (`origins/origin_layers/*.json`) control which origins
   show in the picker. Override `neoorigins:origin` to add your origins to
   the main picker tab.
2. **Origins** (`origins/origins/*.json`) define each selectable origin:
   name, description, icon, impact, and the list of powers it grants.
3. **Powers** (`origins/powers/*.json`) are the mechanical units. Each has
   a `type` (see the power-type index below) plus the type-specific
   fields.
4. **Composition DSL**: most 2.0 power types accept a `condition` (a
   predicate on the player) and either an `entity_action` (a side-effect)
   or an `event` key. Conditions and actions are themselves JSON objects
   with their own `type` and fields; they can be nested (`neoorigins:and`,
   `neoorigins:not`, `neoorigins:or`) to express arbitrary logic.

A tick-driven gate like "take damage in the Nether, unless wearing Frost
Walker boots" is expressed as one power whose condition is an `neoorigins:and`
of `neoorigins:biome` and `neoorigins:not(origins:enchantment)`. The 2.0 design
goal is that virtually any behaviour a pack wants is a composition of
verbs that already exist, rather than a bespoke power type.

The upstream layer (for cross-mod pack compatibility) is handled by the
compat loader: `origins:` and `apace:` types are translated into the 2.0
vocabulary by `OriginsPowerTranslator` / `OriginsCompatPowerLoader`, while
`apoli:` and `apugli:` spellings are canonicalized to `origins:` first.
`LegacyPowerTypeAliases` is a narrower remap table: retired `neoorigins:`
ids plus a few cross-mod entries. See [MIGRATION.md](MIGRATION.md) and the
[Namespaces](#namespaces--prefixes) table.

---

## Documents in this API

| Doc | What it covers |
|---|---|
| [POWER_TYPES.md](POWER_TYPES.md) | Full field table for every power type. Source of truth for allowable fields. |
| [CONDITIONS.md](CONDITIONS.md) | Every condition verb with fields and semantics. |
| [ACTIONS.md](ACTIONS.md) | Every action verb with fields and semantics. |
| [EVENTS.md](EVENTS.md) | Every event key the `action_on_event` power can listen on. |
| [MIGRATION.md](MIGRATION.md) | Legacy type → 2.0 type remap table, lossy translations, DSL gap catalog. |
| [COOKBOOK.md](COOKBOOK.md) | Recipe-oriented tutorial: a five-minute starter origin, then worked recipes for common patterns. |
| [PACK_FORMAT.md](PACK_FORMAT.md) | Directory layout, file-name conventions, JSON boilerplate. |
| [CONTENT_CONFIG.md](CONTENT_CONFIG.md) | Server `content.toml` toggles: global vision / resource-bar switches and per-origin / per-class enable flags. |
| [CLIENT_CONFIG.md](CLIENT_CONFIG.md) | Per-client `client.toml` options: UI theme, HUD layout, hotkey pool size. |
| [GAMEPLAY_CONFIG.md](GAMEPLAY_CONFIG.md) | `gameplay.toml` options: Orb of Origins costs, random assignment, evolution kill counts, ocean-origin survival, sun damage, mount consent, friendly fire, armor classes. |
| [ADMIN_CONFIG.md](ADMIN_CONFIG.md) | `admin.toml` policy: command-power blacklist, dimension restrictions, compat origin filtering, taming exclusions, debug flags. |
| [ARCHITECTURE.md](ARCHITECTURE.md) | How powers are loaded, dispatched, cached. For debugging. |
| [COMPATIBILITY.md](COMPATIBILITY.md) | Out-of-the-box mod integrations (Origins/Apoli, Curios, Ars Nouveau, KubeJS, JEI/REI, etc.). |

If a prose doc disagrees with the code, the code wins. Source of truth
paths are cross-linked in each detail doc.

---

## Power types

The type index, grouped by category with a one-line summary per type, is on [Power Types](POWER_TYPES.md). Each category has its own page under `powers/`.

> **1.21.1 vs 26.x:** `model_color` draws nothing on 26.x; `js_active` and `js_custom` are registered on 1.21.1 and 26.1 only; `ultimine` is an inert marker on 26.2 and 26.3.

---

## Condition verbs

Used in `condition` fields. A bare verb name defaults to `neoorigins:`; any other
namespace (`origins:`, `apace:`, `apoli:`, `apugli:`, ...) is rewritten to
`neoorigins:` with a one-time `[2.0-legacy]` warning. An unknown verb fails
closed. Each entry jumps to [CONDITIONS.md](CONDITIONS.md).

Alias spellings (dispatch to the same verb, not counted above): `riding` → `passenger`, `fire` → `on_fire`, `food` → `food_level`, `name` → `damage_name`, `replaceable` → `replacable`, `resource_level` → `resource`, `covered_by_block` → `cover`, `block_in_radius` → `near_block`, `all_of` → `and`, `any_of` → `or`.

### Boolean combinators
[`neoorigins:and`](conditions/meta.md#neooriginsand-alias-neooriginsall_of) • [`neoorigins:or`](conditions/meta.md#neooriginsor-alias-neooriginsany_of) • [`neoorigins:not`](conditions/meta.md#neooriginsnot) • [`neoorigins:constant`](conditions/meta.md#neooriginsconstant) •
[`neoorigins:always_active`](conditions/entity.md#neooriginsalways_active)

### Environment
[`neoorigins:biome`](conditions/entity.md#neooriginsbiome) • [`neoorigins:dimension`](conditions/entity.md#neooriginsdimension) • [`neoorigins:in_tag`](conditions/entity.md#neooriginsin_tag) • [`neoorigins:submerged_in`](conditions/entity.md#neooriginssubmerged_in) •
[`neoorigins:submerged_in_water`](conditions/entity.md#neooriginssubmerged_in_water) • [`neoorigins:in_water`](conditions/entity.md#neooriginsin_water) • [`neoorigins:in_block`](conditions/entity.md#neooriginsin_block) •
[`neoorigins:on_block`](conditions/entity.md#neooriginson_block) • [`neoorigins:block`](conditions/entity.md#neooriginsblock) • [`neoorigins:block_collision`](conditions/entity.md#neooriginsblock_collision) •
[`neoorigins:on_ground`](conditions/entity.md#neooriginson_ground) • [`neoorigins:on_fire`](conditions/entity.md#neooriginson_fire-alias-neooriginsfire) • [`neoorigins:in_rain`](conditions/entity.md#neooriginsin_rain) • [`neoorigins:temperature`](conditions/entity.md#neooriginstemperature) •
[`neoorigins:weather`](conditions/entity.md#neooriginsweather) • [`neoorigins:brightness`](conditions/entity.md#neooriginsbrightness) • [`neoorigins:light_level`](conditions/entity.md#neooriginslight_level) •
[`neoorigins:exposed_to_sky`](conditions/entity.md#neooriginsexposed_to_sky) • [`neoorigins:exposed_to_sun`](conditions/entity.md#neooriginsexposed_to_sun) • [`neoorigins:daytime`](conditions/entity.md#neooriginsdaytime) •
[`neoorigins:time_of_day`](conditions/entity.md#neooriginstime_of_day) • [`neoorigins:moon_phase`](conditions/entity.md#neooriginsmoon_phase) • [`neoorigins:height`](conditions/entity.md#neooriginsheight) •
[`neoorigins:fluid_height`](conditions/entity.md#neooriginsfluid_height) • [`neoorigins:distance`](conditions/context.md#neooriginsdistance) • [`neoorigins:near_entity`](conditions/entity.md#neooriginsnear_entity) •
[`neoorigins:nearby_entities`](conditions/entity.md#neooriginsnearby_entities) • [`neoorigins:near_villager`](conditions/entity.md#neooriginsnear_villager) •
[`neoorigins:near_block`](conditions/entity.md#neooriginsnear_block) • [`neoorigins:in_block_anywhere`](conditions/entity.md#neooriginsin_block_anywhere) •
[`neoorigins:distance_from_coordinates`](conditions/entity.md#neooriginsdistance_from_coordinates) • [`neoorigins:cover`](conditions/entity.md#neooriginscover-alias-neooriginscovered_by_block) •
[`neoorigins:hardness`](conditions/entity.md#neooriginshardness) • [`neoorigins:replacable`](conditions/entity.md#neooriginsreplacable-alias-neooriginsreplaceable) • [`neoorigins:night`](conditions/entity.md#neooriginsnight) •
[`neoorigins:thundering`](conditions/entity.md#neooriginsthundering)

### Player state
[`neoorigins:health`](conditions/entity.md#neooriginshealth) • [`neoorigins:relative_health`](conditions/entity.md#neooriginsrelative_health) • [`neoorigins:food_level`](conditions/entity.md#neooriginsfood_level-alias-neooriginsfood) •
[`neoorigins:armor_value`](conditions/entity.md#neooriginsarmor_value) • [`neoorigins:xp_level`](conditions/entity.md#neooriginsxp_level) • [`neoorigins:xp_points`](conditions/entity.md#neooriginsxp_points) •
[`neoorigins:fall_distance`](conditions/entity.md#neooriginsfall_distance) • [`neoorigins:fall_flying`](conditions/entity.md#neooriginsfall_flying) • [`neoorigins:sneaking`](conditions/entity.md#neooriginssneaking) •
[`neoorigins:sprinting`](conditions/entity.md#neooriginssprinting) • [`neoorigins:swimming`](conditions/entity.md#neooriginsswimming) • [`neoorigins:invisible`](conditions/entity.md#neooriginsinvisible) •
[`neoorigins:creative_flying`](conditions/entity.md#neooriginscreative_flying) • [`neoorigins:moving`](conditions/entity.md#neooriginsmoving) • [`neoorigins:passenger`](conditions/entity.md#neooriginspassenger-alias-neooriginsriding) •
[`neoorigins:using_item`](conditions/entity.md#neooriginsusing_item) • [`neoorigins:equipped_item`](conditions/entity.md#neooriginsequipped_item) • [`neoorigins:enchantment`](conditions/entity.md#neooriginsenchantment) •
[`neoorigins:resource`](conditions/entity.md#neooriginsresource) • [`neoorigins:living`](conditions/entity.md#neooriginsliving) • [`neoorigins:exists`](conditions/entity.md#neooriginsexists) • [`neoorigins:ticking`](conditions/entity.md#neooriginsticking) •
[`neoorigins:advancement`](conditions/entity.md#neooriginsadvancement) • [`neoorigins:air`](conditions/entity.md#neooriginsair) • [`neoorigins:body_temperature`](conditions/entity.md#neooriginsbody_temperature) •
[`neoorigins:climbing`](conditions/entity.md#neooriginsclimbing) • [`neoorigins:climbing_gate`](conditions/entity.md#neooriginsclimbing_gate) •
[`neoorigins:collided_horizontally`](conditions/entity.md#neooriginscollided_horizontally) • [`neoorigins:creative_mode`](conditions/entity.md#neooriginscreative_mode) •
[`neoorigins:has_effect`](conditions/entity.md#neooriginshas_effect) • [`neoorigins:status_effect`](conditions/entity.md#neooriginsstatus_effect) • [`neoorigins:inventory`](conditions/entity.md#neooriginsinventory) •
[`neoorigins:saturation_level`](conditions/entity.md#neooriginssaturation_level) • [`neoorigins:using_effective_tool`](conditions/entity.md#neooriginsusing_effective_tool) •
[`neoorigins:xp_levels`](conditions/entity.md#neooriginsxp_levels)

### Food context
[`neoorigins:food_item_id`](conditions/context.md#neooriginsfood_item_id) • [`neoorigins:food_item_in_tag`](conditions/context.md#neooriginsfood_item_in_tag) •
[`neoorigins:food_item_in_config_list`](conditions/context.md#neooriginsfood_item_in_config_list)

### Entity & damage
[`neoorigins:entity_type`](conditions/entity.md#neooriginsentity_type) • [`neoorigins:target_type`](conditions/context.md#neooriginstarget_type) • [`neoorigins:target_group`](conditions/context.md#neooriginstarget_group) •
[`neoorigins:can_see`](conditions/context.md#neooriginscan_see) • [`neoorigins:damage_type`](conditions/context.md#neooriginsdamage_type) • [`neoorigins:damage_tag`](conditions/context.md#neooriginsdamage_tag) •
[`neoorigins:damage_name`](conditions/context.md#neooriginsdamage_name-alias-neooriginsname) • [`neoorigins:from_fire`](conditions/context.md#neooriginsfrom_fire) • [`neoorigins:from_projectile`](conditions/context.md#neooriginsfrom_projectile) •
[`neoorigins:from_explosion`](conditions/context.md#neooriginsfrom_explosion) • [`neoorigins:actor_condition`](conditions/meta.md#neooriginsactor_condition) •
[`neoorigins:hit_dealt_amount`](conditions/context.md#neooriginshit_dealt_amount) • [`neoorigins:hit_taken_amount`](conditions/context.md#neooriginshit_taken_amount) •
[`neoorigins:out_of_combat`](conditions/entity.md#neooriginsout_of_combat)

### Power introspection
[`neoorigins:power_active`](conditions/entity.md#neooriginspower_active) • [`neoorigins:power_type`](conditions/entity.md#neooriginspower_type) • [`neoorigins:in_set`](conditions/context.md#neooriginsin_set) •
[`neoorigins:power`](conditions/entity.md#neooriginspower) • [`neoorigins:origin`](conditions/entity.md#neooriginsorigin) • [`neoorigins:cooldown`](conditions/entity.md#neooriginscooldown) •
[`neoorigins:no_minions_alive`](conditions/context.md#neooriginsno_minions_alive)

### Advanced
[`neoorigins:nbt`](conditions/entity.md#neooriginsnbt) • [`neoorigins:scoreboard`](conditions/entity.md#neooriginsscoreboard) • [`neoorigins:statistic`](conditions/entity.md#neooriginsstatistic) •
[`neoorigins:command`](conditions/entity.md#neooriginscommand) • [`neoorigins:predicate`](conditions/entity.md#neooriginspredicate) •
[`neoorigins:amount`](conditions/entity.md#neooriginsamount) • [`neoorigins:equal`](conditions/context.md#neooriginsequal) • [`neoorigins:config_flag`](conditions/entity.md#neooriginsconfig_flag)

---

## Action verbs

Used in `entity_action` fields. Namespace handling is the same as for
conditions (bare name → `neoorigins:`; other namespaces rewritten with a
one-time warning). Each entry jumps to [ACTIONS.md](ACTIONS.md).

Two caveats. In a **bientity** action slot, pair behaviour (actor/target
wrappers, `and`, `chance`, `invert`, `damage`, `mount`, `if_else`) only exists
for the literal `origins:` / `apoli:` / `apace:` spellings; any other type,
including a `neoorigins:` one, is parsed as a plain entity action and runs on
the **actor**, with the target published as context. And the entity-action
`neoorigins:invert` is registered but is a **no-op** (it parses to
`EntityAction.noop()`); only the bientity `origins:invert` swaps actor and target.

Alias spellings (dispatch to the same verb, not counted above): `all_of` → `and`, `bonemeal` → `grow`, `modify_resource` → `change_resource`, `fire_projectile` → `spawn_projectile`, `command` → `execute_command`, `spawn_sword_rain` → `spawn_projectile_rain`.

### Combinators & control
[`neoorigins:and`](actions/meta.md#neooriginsand-alias-neooriginsall_of) • [`neoorigins:chance`](actions/meta.md#neooriginschance) • [`neoorigins:delay`](actions/meta.md#neooriginsdelay) • [`neoorigins:if_else`](actions/meta.md#neooriginsif_else) •
[`neoorigins:if_else_list`](actions/meta.md#neooriginsif_else_list) • [`neoorigins:nothing`](actions/meta.md#neooriginsnothing) • [`neoorigins:choice`](actions/meta.md#neooriginschoice) •
[`neoorigins:invert`](actions/bientity.md#invert) (no-op; see above) • [`neoorigins:cancel_event`](actions/targeted.md#neooriginscancel_event)

### Wrappers & targeting
[`neoorigins:actor_action`](actions/bientity.md#actor_action) • [`neoorigins:target_action`](actions/bientity.md#target_action) • [`neoorigins:riding_action`](actions/bientity.md#riding_action) •
[`neoorigins:passenger_action`](actions/bientity.md#passenger_action) • [`neoorigins:equipped_item_action`](actions/targeted.md#neooriginsequipped_item_action) •
[`neoorigins:block_action_at`](actions/meta.md#neooriginsblock_action_at) • [`neoorigins:block_target_action`](actions/targeted.md#neooriginsblock_target_action) •
[`neoorigins:selector_action`](actions/bientity.md#selector_action) • [`neoorigins:raycast`](actions/targeted.md#neooriginsraycast) • [`neoorigins:offset`](actions/meta.md#neooriginsoffset)

### Damage & healing
[`neoorigins:damage`](actions/effects.md#neooriginsdamage) • [`neoorigins:heal`](actions/effects.md#neooriginsheal) • [`neoorigins:feed`](actions/effects.md#neooriginsfeed) • [`neoorigins:exhaust`](actions/effects.md#neooriginsexhaust) •
[`neoorigins:change_resource`](actions/effects.md#neooriginschange_resource) • [`neoorigins:damage_attacker`](actions/targeted.md#neooriginsdamage_attacker) •
[`neoorigins:ignite_attacker`](actions/targeted.md#neooriginsignite_attacker) • [`neoorigins:effect_on_attacker`](actions/targeted.md#neooriginseffect_on_attacker)

### Effects
[`neoorigins:apply_effect`](actions/effects.md#neooriginsapply_effect) • [`neoorigins:clear_effect`](actions/effects.md#neooriginsclear_effect) •
[`neoorigins:spawn_effect_cloud`](actions/effects.md#neooriginsspawn_effect_cloud) • [`neoorigins:spawn_lingering_area`](actions/effects.md#neooriginsspawn_lingering_area) •
[`neoorigins:modify_temperature`](actions/effects.md#neooriginsmodify_temperature)

### Movement & position
[`neoorigins:add_velocity`](actions/effects.md#neooriginsadd_velocity) • [`neoorigins:launch`](actions/effects.md#neooriginslaunch) • [`neoorigins:set_fall_distance`](actions/effects.md#neooriginsset_fall_distance) •
[`neoorigins:dismount`](actions/effects.md#neooriginsdismount) • [`neoorigins:throw_target`](actions/targeted.md#neooriginsthrow_target) • [`neoorigins:dash`](actions/targeted.md#neooriginsdash) •
[`neoorigins:mount`](actions/effects.md#neooriginsmount) • [`neoorigins:random_teleport`](actions/targeted.md#neooriginsrandom_teleport) • [`neoorigins:swap_positions`](actions/targeted.md#neooriginsswap_positions) •
[`neoorigins:swap_with_entity`](actions/targeted.md#neooriginsswap_with_entity) • [`neoorigins:teleport_target_to_self`](actions/targeted.md#neooriginsteleport_target_to_self) •
[`neoorigins:teleport_to_marker`](actions/targeted.md#neooriginsteleport_to_marker) • [`neoorigins:teleport_to_target`](actions/targeted.md#neooriginsteleport_to_target)

### Items & inventory
[`neoorigins:give`](actions/effects.md#neooriginsgive) • [`neoorigins:modify_food`](actions/effects.md#neooriginsmodify_food) • [`neoorigins:spawn_entity`](actions/effects.md#neooriginsspawn_entity) •
[`neoorigins:spawn_projectile`](actions/effects.md#neooriginsspawn_projectile) • [`neoorigins:drop_inventory`](actions/targeted.md#neooriginsdrop_inventory) •
[`neoorigins:drop_items`](actions/effects.md#neooriginsdrop_items) • [`neoorigins:force_drop`](actions/targeted.md#neooriginsforce_drop) • [`neoorigins:steal_item`](actions/targeted.md#neooriginssteal_item) •
[`neoorigins:modify_inventory`](actions/targeted.md#neooriginsmodify_inventory) • [`neoorigins:dye`](actions/targeted.md#neooriginsdye) • [`neoorigins:shear`](actions/targeted.md#neooriginsshear) •
[`neoorigins:add_xp`](actions/effects.md#neooriginsadd_xp) • [`neoorigins:crafting_table`](actions/targeted.md#neooriginscrafting_table)

### World & environment
[`neoorigins:set_block`](actions/effects.md#neooriginsset_block) • [`neoorigins:set_on_fire`](actions/effects.md#neooriginsset_on_fire) • [`neoorigins:extinguish`](actions/effects.md#neooriginsextinguish) •
[`neoorigins:explode`](actions/effects.md#neooriginsexplode) • [`neoorigins:gain_air`](actions/effects.md#neooriginsgain_air) • [`neoorigins:area_of_effect`](actions/meta.md#neooriginsarea_of_effect) •
[`neoorigins:grow`](actions/targeted.md#neooriginsgrow-alias-neooriginsbonemeal) • [`neoorigins:till`](actions/targeted.md#neooriginstill) • [`neoorigins:path`](actions/targeted.md#neooriginspath) • [`neoorigins:strip`](actions/targeted.md#neooriginsstrip) •
[`neoorigins:transform_block`](actions/targeted.md#neooriginstransform_block) • [`neoorigins:spawn_black_hole`](actions/effects.md#neooriginsspawn_black_hole) •
[`neoorigins:spawn_tornado`](actions/effects.md#neooriginsspawn_tornado) • [`neoorigins:spawn_projectile_rain`](actions/effects.md#neooriginsspawn_projectile_rain) •
[`neoorigins:spawn_telegraph`](actions/effects.md#neooriginsspawn_telegraph) • [`neoorigins:spawn_particles`](actions/effects.md#neooriginsspawn_particles)

### Power control
[`neoorigins:grant_power`](actions/effects.md#neooriginsgrant_power) • [`neoorigins:revoke_power`](actions/effects.md#neooriginsrevoke_power) • [`neoorigins:trigger_cooldown`](actions/effects.md#neooriginstrigger_cooldown) •
[`neoorigins:activate_power`](actions/effects.md#neooriginsactivate_power) • [`neoorigins:toggle`](actions/targeted.md#neooriginstoggle) • [`neoorigins:set_resource`](actions/effects.md#neooriginsset_resource) •
[`neoorigins:open_layer_picker`](actions/effects.md#neooriginsopen_layer_picker)

### Entities & sets
[`neoorigins:tame_target`](actions/targeted.md#neooriginstame_target) • [`neoorigins:chain_to_nearest`](actions/targeted.md#neooriginschain_to_nearest) •
[`neoorigins:pull_entities`](actions/targeted.md#neooriginspull_entities) • [`neoorigins:add_to_set`](actions/targeted.md#neooriginsadd_to_set) •
[`neoorigins:remove_from_set`](actions/targeted.md#neooriginsremove_from_set) • [`neoorigins:morph_entity_event`](actions/targeted.md#neooriginsmorph_entity_event) •
[`neoorigins:trigger_morph_animation`](actions/targeted.md#neooriginstrigger_morph_animation)

### Integration
[`neoorigins:execute_command`](actions/effects.md#neooriginsexecute_command) • [`neoorigins:play_sound`](actions/effects.md#neooriginsplay_sound) • [`neoorigins:swing_hand`](actions/effects.md#neooriginsswing_hand) •
[`neoorigins:emit_game_event`](actions/effects.md#neooriginsemit_game_event) •
[`neoorigins:cast_spell`](actions/targeted.md#neooriginscast_spell) • [`neoorigins:cast_iron_spell`](actions/targeted.md#neooriginscast_iron_spell)

---

## Event keys

Used in `action_on_event`'s `event` field. Case-insensitive. The compat
spelling `item_use_start` also resolves (to `ITEM_USE`); see
[EVENTS.md](EVENTS.md#compat-spellings). Each entry jumps to its section in
[EVENTS.md](EVENTS.md).

### Core lifecycle & combat
[`ATTACK`](EVENTS.md#attack) • [`HIT_TAKEN`](EVENTS.md#hit_taken) • [`HIT_DEALT`](EVENTS.md#hit_dealt) • [`KILL`](EVENTS.md#kill) • [`DEATH`](EVENTS.md#death) • [`BLOCK_BREAK`](EVENTS.md#block_break) •
[`BLOCK_PLACE`](EVENTS.md#block_place) • [`ITEM_USE`](EVENTS.md#item_use) • [`RESPAWN`](EVENTS.md#respawn) • [`TICK`](EVENTS.md#tick) • [`DIMENSION_CHANGE`](EVENTS.md#dimension_change) •
[`JUMP`](EVENTS.md#jump) • [`CLIMB`](EVENTS.md#climb) • [`PROJECTILE_HIT`](EVENTS.md#projectile_hit) • [`EFFECT_APPLIED`](EVENTS.md#effect_applied)

### Interactions
[`BONEMEAL`](EVENTS.md#bonemeal) • [`FOOD_EATEN`](EVENTS.md#food_eaten) • [`FOOD_FINISHED`](EVENTS.md#food_finished) • [`BLOCK_USE`](EVENTS.md#block_use) • [`ENTITY_USE`](EVENTS.md#entity_use) •
[`ITEM_PICKUP`](EVENTS.md#item_pickup) • [`ITEM_USE_FINISH`](EVENTS.md#item_use_finish) • [`VILLAGER_INTERACT`](EVENTS.md#villager_interact) • [`TRADE_COMPLETED`](EVENTS.md#trade_completed) •
[`BREED`](EVENTS.md#breed) • [`TAME`](EVENTS.md#tame) • [`CRAFT_ITEM`](EVENTS.md#craft_item) • [`SMELT_ITEM`](EVENTS.md#smelt_item) • [`ENCHANT_ITEM`](EVENTS.md#enchant_item) •
[`ANVIL_REPAIR`](EVENTS.md#anvil_repair) • [`ADVANCEMENT_EARNED`](EVENTS.md#advancement_earned)

### Origin & power lifecycle
[`GAINED`](EVENTS.md#gained) • [`LOST`](EVENTS.md#lost) • [`CHOSEN`](EVENTS.md#chosen) • [`WAKE_UP`](EVENTS.md#wake_up) • [`LAND`](EVENTS.md#land) • [`POWER_ACTIVATED`](EVENTS.md#power_activated)

### Modifiers (return a float, chain in registration order)
[`MOD_EXHAUSTION`](EVENTS.md#mod_exhaustion) • [`MOD_NATURAL_REGEN`](EVENTS.md#mod_natural_regen) • [`MOD_ENCHANT_LEVEL`](EVENTS.md#mod_enchant_level) •
[`MOD_HARVEST_DROPS`](EVENTS.md#mod_harvest_drops) • [`MOD_TELEPORT_RANGE`](EVENTS.md#mod_teleport_range) • [`MOD_KNOCKBACK`](EVENTS.md#mod_knockback) •
[`MOD_POTION_DURATION`](EVENTS.md#mod_potion_duration) • [`MOD_ANVIL_COST`](EVENTS.md#mod_anvil_cost) • [`MOD_CRAFTED_FOOD_SATURATION`](EVENTS.md#mod_crafted_food_saturation) •
[`MOD_BONEMEAL_EXTRA`](EVENTS.md#mod_bonemeal_extra) • [`MOD_FALL_DAMAGE`](EVENTS.md#mod_fall_damage) • [`MOD_TRADE_PRICE`](EVENTS.md#mod_trade_price) •
[`MOD_CRAFT_AMOUNT`](EVENTS.md#mod_craft_amount) • [`MOD_FOOD_NUTRITION`](EVENTS.md#mod_food_nutrition)

See [EVENTS.md](EVENTS.md) for each event's context record.

---

## Named keybinds

Moved to [Keybinds](concepts/keybinds.md): the skill slots, the named-keybind pool, numbered slots, vanilla input keys, `fail_action` and `disable_hotkey`.

---

## Active theme datapack file

The UI theme used by the origin selection / info screens is selectable per
world via a datapack file. Drop the following into any pack:

```
data/<namespace>/neoorigins/active_theme.json
```

```json
{ "theme": "examplepack:dark_woods" }
```

The server reads every `active_theme.json` on world load + each `/reload`
and broadcasts the selection to every client at login. When multiple packs
each declare an `active_theme.json`, the one loaded **last** wins and a
warning is logged listing every contributor. A per-client override lives
at `config/neoorigins/client.toml` (`[ui] theme_override = "<ns>:<id>"`)
and beats the datapack file when set to a loaded id.

For the theme JSON schema (`assets/<ns>/ui_themes/<id>.json`) and the
authoring quickstart, see [THEMING.md](THEMING.md). A copy-and-edit pack
skeleton lives at [`docs/theme-template/`](theme-template).

---

## Namespaces & prefixes

NeoOrigins accepts legacy prefixes for cross-mod pack compat.

| Prefix | Meaning | How it's resolved |
|---|---|---|
| `neoorigins:*` | Canonical 2.0 namespace. Use this for new packs. | Direct registry lookup. |
| `origins:*` | Upstream Apoli / vanilla Origins. | Translator in `OriginsCompatPowerLoader` maps to `neoorigins:*` or a DSL recipe. |
| `apace:*` | Apace mod variant. | Same translator as `origins:*`. |
| `apoli:*` | Upstream Apoli mod. | Canonicalized to `origins:*` and fed through the same translator; `LegacyPowerTypeAliases` covers only the odd cross-mod id (e.g. `apoli:edible_item`). |
| `apugli:*` | Apugli mod. | Canonicalized to `origins:*` like `apoli:*` and fed through the same translator. `LegacyPowerTypeAliases` also covers `apugli:edible_item`, `apugli:action_on_jump` and `apugli:action_on_target_death`. |

A remapped type logs one `[2.0-legacy]` warning per old type id per boot,
naming the replacement. The remap rewrites only the fields it knows about;
the replacement type's parser then reads the result, so a field neither
the remap nor the new type reads has no effect.

---

## JSON schemas

Machine-readable schemas live under [schema/](schema/). Point your IDE /
datapack validator at:

- `schema/power.schema.json`: for files under `data/*/origins/powers/`
- `schema/origin.schema.json`: for files under `data/*/origins/origins/`
- `schema/origin_layer.schema.json`: for files under `data/*/origins/origin_layers/`
- `schema/mob_origin.schema.json`: for files under `data/*/origins/mob_origins/`
- `schema/morph.schema.json`: for files under `data/*/neoorigins/morphs/`
- `schema/condition.schema.json`, `schema/action.schema.json`,
  `schema/block_condition.schema.json`, `schema/item_condition.schema.json`,
  `schema/item_action.schema.json`: the nested condition / action objects

Schemas are derived from the Java Config records and are authoritative
against what the loader accepts. If the schema disagrees with a prose
doc, the schema wins.

---

## Capability system

Powers declare capability tags via `capabilities(Config)`. These tags are synced to the client and used by client-predicted mixins (e.g. `"wall_climb"`, `"wall_phase"`, `"no_physics"`, `"flight"`).

A **player-aware variant** `capabilities(ServerPlayer, Config)` is available for capabilities that depend on runtime state (conditions, resource levels, etc.). Default delegates to the static variant. Used by `model_color` to conditionally emit the color capability based on a condition field.

---

## Mod integration (Java)

For other mods that need to read or change a player's origins programmatically.

**Where origins live.** A player's per-layer selection is stored on the `neoorigins:origin_data` data attachment (`PlayerOriginData`, accessed via `player.getData(OriginAttachments.originData())`). It persists only the `layer → origin` map plus a little bookkeeping; **a player's powers are *not* stored**. The active power set is derived at runtime from each assigned origin's definition (`ActiveOriginService`), so writing the attachment NBT directly does **not** apply or remove any powers.

**Changing origins cleanly.** Because powers are derived, swapping an origin requires running the revoke/grant lifecycle (`onRevoked` / `onGranted`, attribute-modifier cleanup, event-handler teardown, client sync). Two entry points on `com.cyberday1.neoorigins.service.ActiveOriginService`:

- `applyOriginPowers(player, layerId, oldOriginId, newOriginId)` transitions a **single** layer: revokes `oldOriginId`'s powers, grants `newOriginId`'s. Pass `null` for either id to grant-only / revoke-only. This is what `/neoorigins set` uses.
- `reapplyOrigins(player, Map<layerId, originId>)` replaces a player's **entire** origin selection in one clean call: tears down all current powers, overwrites the layer map, regrants, restores server-global powers, and syncs the client. Intended for profile / loadout mods (e.g. Switchy) that restore a saved origin set. Call this instead of writing the attachment NBT directly to avoid leftover powers from the previous profile. An empty map clears the player to no origins.

Both run the full lifecycle, so the client HUD, keybinds, and attribute state stay consistent. After either call you do not need a separate sync.

---

## Source-of-truth paths

When docs drift, these are the code files that won:

- Power registrations: `src/main/java/com/cyberday1/neoorigins/power/registry/PowerTypes.java`
- Power Config records: `src/main/java/com/cyberday1/neoorigins/power/builtin/*.java`
- Condition verbs: `src/main/java/com/cyberday1/neoorigins/compat/condition/BuiltinConditions.java` (registered descriptors; `ConditionParser.java` dispatches to them)
- Action verbs: `src/main/java/com/cyberday1/neoorigins/compat/action/BuiltinActions.java` (registered descriptors; `ActionParser.java` dispatches to them)
- Event keys: `src/main/java/com/cyberday1/neoorigins/service/EventPowerIndex.java`
- Legacy aliases: `src/main/java/com/cyberday1/neoorigins/power/registry/LegacyPowerTypeAliases.java`

---

## Moved from this page

These sections now live on other pages.

<div id="no-anchor-stub"></div>

- <a id="generic-composable-use-these-for-new-powers"></a>[Generic composable (use these for new powers)](POWER_TYPES.md)
- <a id="movement--body"></a>[Movement & body](POWER_TYPES.md)
- <a id="minions--entities"></a>[Minions & entities](POWER_TYPES.md)
- <a id="combat"></a>[Combat](POWER_TYPES.md)
- <a id="mining-farming-crafting"></a>[Mining, farming, crafting](POWER_TYPES.md)
- <a id="inventory--items"></a>[Inventory & items](POWER_TYPES.md)
- <a id="healing-hunger-survival"></a>[Healing, hunger, survival](POWER_TYPES.md)
- <a id="respawn--lifecycle"></a>[Respawn & lifecycle](POWER_TYPES.md)
- <a id="merchant--loot"></a>[Merchant & loot](POWER_TYPES.md)
- <a id="command-integration"></a>[Command integration](POWER_TYPES.md)
- <a id="scripting"></a>[Scripting](POWER_TYPES.md)
- <a id="mod-compatibility"></a>[Mod compatibility](POWER_TYPES.md)
- <a id="active-abilities-specialised"></a>[Active abilities (specialised)](POWER_TYPES.md)
- <a id="deprecated--retired"></a>[Deprecated & retired](POWER_TYPES.md)
- <a id="declaring-on-a-power"></a>[Declaring on a power](concepts/keybinds.md#declaring-on-a-power)
- <a id="numbered-slots-key-n"></a>[Numbered slots (`key: N`)](concepts/keybinds.md#numbered-slots-key-n)
- <a id="one-key-several-powers"></a>[One key, several powers](concepts/keybinds.md#one-key-several-powers)
- <a id="vanilla-input-keys"></a>[Vanilla input keys](concepts/keybinds.md#vanilla-input-keys)
- <a id="fail-feedback-fail_action"></a>[Fail feedback (`fail_action`)](concepts/keybinds.md#fail-feedback-fail_action)
- <a id="hotkey-less-actives-disable_hotkey"></a>[Hotkey-less actives (`disable_hotkey`)](concepts/keybinds.md#hotkey-less-actives-disable_hotkey)
- <a id="how-the-pool-works"></a>[How the pool works](concepts/keybinds.md#how-the-pool-works)
- <a id="display-labels"></a>[Display labels](concepts/keybinds.md#display-labels)
