# NeoOrigins 2.0 API Reference

Single landing page for the NeoOrigins datapack API. Every power type,
condition verb, action verb, and event key is listed below with a jump-link
to the per-topic detail doc.

- [Layer model](#layer-model)
- [Documents in this API](#documents-in-this-api)
- [Power types](#power-types) — 74 types
- [Condition verbs](#condition-verbs) — 74 conditions
- [Action verbs](#action-verbs) — 45 actions
- [Event keys](#event-keys) — 33 events
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
   a `type` — see the power-type index below — plus the type-specific
   fields.
4. **Composition DSL**: most 2.0 power types accept a `condition` (a
   predicate on the player) and either an `entity_action` (a side-effect)
   or an `event` key. Conditions and actions are themselves JSON objects
   with their own `type` and fields; they can be nested (`origins:and`,
   `origins:not`, `origins:or`) to express arbitrary logic.

A tick-driven gate like "take damage in the Nether, unless wearing Frost
Walker boots" is expressed as one power whose condition is an `origins:and`
of `origins:biome` and `origins:not(origins:enchantment)`. The 2.0 design
goal is that virtually any behaviour a pack wants is a composition of
verbs that already exist, rather than a bespoke power type.

The upstream layer — for cross-mod pack compatibility — is handled by
`LegacyPowerTypeAliases.java`, which translates `origins:` / `apace:` /
`apoli:` / `apugli:` types into the 2.0 vocabulary at load time. See
[MIGRATION.md](MIGRATION.md).

---

## Documents in this API

| Doc | What it covers |
|---|---|
| [POWER_TYPES.md](POWER_TYPES.md) | Full field table for every power type. Source of truth for allowable fields. |
| [CONDITIONS.md](CONDITIONS.md) | Every condition verb with fields and semantics. |
| [ACTIONS.md](ACTIONS.md) | Every action verb with fields and semantics. |
| [EVENTS.md](EVENTS.md) | Every event key the `action_on_event` power can listen on. |
| [MIGRATION.md](MIGRATION.md) | Legacy type → 2.0 type remap table, lossy translations, DSL gap catalog. |
| [COOKBOOK.md](COOKBOOK.md) | Recipe-oriented tutorial — 10 common patterns. |
| [PACK_FORMAT.md](PACK_FORMAT.md) | Directory layout, file-name conventions, JSON boilerplate. |
| [ARCHITECTURE.md](ARCHITECTURE.md) | How powers are loaded, dispatched, cached. For debugging. |
| [2_0_CHANGES.md](2_0_CHANGES.md) | Phase-by-phase log of the 2.0 consolidation. |

If a prose doc disagrees with the code, the code wins. Source of truth
paths are cross-linked in each detail doc.

---

## Power types

The `type` field on a power file selects one of these. All use the
`neoorigins:` namespace; see [Namespaces](#namespaces--prefixes) for how
legacy prefixes map.

Each row jumps to its section in [POWER_TYPES.md](POWER_TYPES.md).

### Generic composable (use these for new powers)

| Type | Summary |
|---|---|
| [`action_on_event`](POWER_TYPES.md#neooriginsaction_on_event) | Run an action (or modify a float) when an event fires. Workhorse event-listener. |
| [`action_on_hit`](POWER_TYPES.md#neooriginsaction_on_hit) | Run an action when the player deals damage. Filterable by target. |
| [`active_ability`](POWER_TYPES.md#neooriginsactive_ability) | Skill-key-triggered ability running one or more actions. Cooldown + hunger cost. |
| [`attribute_modifier`](POWER_TYPES.md#neooriginsattribute_modifier) | Add/multiply a player attribute. Optional tick-driven condition gate. |
| [`condition_passive`](POWER_TYPES.md#neooriginscondition_passive) | Run an action on an interval while a condition holds. Workhorse passive. |
| [`conditional`](POWER_TYPES.md#neooriginsconditional) | Gate another power on a static condition enum. |
| [`edible_item`](POWER_TYPES.md#neooriginsedible_item) | Make arbitrary items edible for this player. |
| [`modify_damage`](POWER_TYPES.md#neooriginsmodify_damage) | Scale damage the player deals or takes. Direction + type/tag filter. |
| [`persistent_effect`](POWER_TYPES.md#neooriginspersistent_effect) | Refresh a status effect every tick. Replaces legacy `status_effect`. |
| [`prevent_action`](POWER_TYPES.md#neooriginsprevent_action) | Cancel a specific player action (FALL_DAMAGE, DROWN, FIRE, SWIM, etc.). |
| [`toggle`](POWER_TYPES.md#neooriginstoggle) | Boolean state cycled by the skill key, readable via `origins:power_active`. |

### Movement & body

| Type | Summary |
|---|---|
| [`elytra_boost`](POWER_TYPES.md#neooriginselytra_boost) | Add forward thrust while elytra-flying. |
| [`enhanced_vision`](POWER_TYPES.md#neooriginsenhanced_vision) | Permanent brightness floor. Replaces legacy `night_vision`. |
| [`flight`](POWER_TYPES.md#neooriginsflight) | Creative-style flight. |
| [`phantom_form`](POWER_TYPES.md#neooriginsphantom_form) | Mayfly + noPhysics + no-fall — walk through walls. |
| [`size_scaling`](POWER_TYPES.md#neooriginssize_scaling) | Scale player dimensions. Optional reach-scaling. |
| [`wall_climbing`](POWER_TYPES.md#neooriginswall_climbing) | Scale walls like a spider. |
| [`water_breathing`](POWER_TYPES.md#neooriginswater_breathing) | Never loses air underwater. |
| [`cobweb_affinity`](POWER_TYPES.md#neooriginscobweb_affinity) | Web mobility + 10× cobweb break speed. |

### Minions & entities

| Type | Summary |
|---|---|
| [`summon_minion`](POWER_TYPES.md#neooriginssummon_minion) | Active ability that spawns a tracked mob with owner-aware AI. |
| [`tame_mob`](POWER_TYPES.md#neooriginstame_mob) | Active ability that rewrites a mob's AI to follow and defend the owner. |
| [`tamed_animal_boost`](POWER_TYPES.md#neooriginstamed_animal_boost) | Buff vanilla tamed animals (HP / speed). |
| [`tamed_potion_diffusal`](POWER_TYPES.md#neooriginstamed_potion_diffusal) | Potions drunk near tamed animals apply to them too. |
| [`entity_group`](POWER_TYPES.md#neooriginsentity_group) | Mark the player as part of an entity group (undead, arthropod). Affects effect applicability + enchantment bonuses. |
| [`entity_set`](POWER_TYPES.md#neooriginsentity_set) | Named UUID set per power id. Pair with `origins:in_set` / `add_to_set` / `remove_from_set`. |
| [`mobs_ignore_player`](POWER_TYPES.md#neooriginsmobs_ignore_player) | Mobs don't aggro. Retaliation window preserved. |
| [`scare_entities`](POWER_TYPES.md#neooriginsscare_entities) | Listed entity types flee from the player. |
| [`no_mob_spawns_nearby`](POWER_TYPES.md#neooriginsno_mob_spawns_nearby) | Cancel natural spawns in a radius. |
| [`twin_breeding`](POWER_TYPES.md#neooriginstwin_breeding) | Chance for breeding to yield two babies. |

### Combat

| Type | Summary |
|---|---|
| [`invulnerability`](POWER_TYPES.md#neooriginsinvulnerability) | Filter damage types the player is immune to. |
| [`projectile_immunity`](POWER_TYPES.md#neooriginsprojectile_immunity) | Filter projectile types the player is immune to. |
| [`effect_immunity`](POWER_TYPES.md#neooriginseffect_immunity) | Block specific status effects. |
| [`ender_gaze_immunity`](POWER_TYPES.md#neooriginsender_gaze_immunity) | Endermen don't aggro on eye contact. |
| [`ground_slam`](POWER_TYPES.md#neooriginsground_slam) | Radial AoE damage from falling. |
| [`tidal_wave`](POWER_TYPES.md#neooriginstidal_wave) | Active cone-shape knockback. |
| [`shadow_orb`](POWER_TYPES.md#neooriginsshadow_orb) | Place a stationary orb emitting Darkness + Blindness. |

### Mining, farming, crafting

| Type | Summary |
|---|---|
| [`break_speed_modifier`](POWER_TYPES.md#neooriginsbreak_speed_modifier) | Multiply block break speed with block/tool/condition filters. |
| [`underwater_mining_speed`](POWER_TYPES.md#neooriginsunderwater_mining_speed) | Remove underwater mining penalty. |
| [`tree_felling`](POWER_TYPES.md#neooriginstree_felling) | Chop connected logs in one swing. |
| [`crop_growth_accelerator`](POWER_TYPES.md#neoorigincrop_growth_accelerator) | Periodic tick boost to nearby crops. |
| [`crop_harvest_bonus`](POWER_TYPES.md#neooriginscrop_harvest_bonus) | +N drops per crop break. |
| [`quality_equipment`](POWER_TYPES.md#neooriginsquality_equipment) | Starting tool quality uplift. |
| [`craft_amount_bonus`](POWER_TYPES.md#neooriginscraft_amount_bonus) | Extra output on crafted items. |
| [`more_smoker_xp`](POWER_TYPES.md#neooriginsmore_smoker_xp) | Bonus XP from smoker. |

### Inventory & items

| Type | Summary |
|---|---|
| [`starting_equipment`](POWER_TYPES.md#neooriginsstarting_equipment) | Grant items on origin chosen. |
| [`keep_inventory`](POWER_TYPES.md#neooriginskeep_inventory) | Slot/item filter for inventory kept across death. |
| [`restrict_armor`](POWER_TYPES.md#neooriginsrestrict_armor) | Slot-scoped gate on wearable items. |
| [`item_magnetism`](POWER_TYPES.md#neooriginsitem_magnetism) | Pull item entities to the player. |
| [`hide_hud_bar`](POWER_TYPES.md#neooriginshide_hud_bar) | Hide the food or air bar. |

### Healing, hunger, survival

| Type | Summary |
|---|---|
| [`horde_regen`](POWER_TYPES.md#neooriginshorde_regen) | Regen scales with nearby allied mob count. |
| [`no_natural_regen`](POWER_TYPES.md#neooriginsno_natural_regen) | Kill food-driven regen; potion/beacon heals still work. |
| [`exhaustion_filter`](POWER_TYPES.md#neooriginsexhaustion_filter) | Selective hunger-drain modifier. |
| [`no_slowdown`](POWER_TYPES.md#neooriginsno_slowdown) | Immune to specific slowdown sources. |
| [`breath_in_fluid`](POWER_TYPES.md#neooriginsbreath_in_fluid) | Air supply drains in named fluids. |

### Respawn & lifecycle

| Type | Summary |
|---|---|
| [`modify_player_spawn`](POWER_TYPES.md#neooriginsmodify_player_spawn) | Per-power respawn override. Optional bed override. |

### Merchant & loot

| Type | Summary |
|---|---|
| [`trade_availability`](POWER_TYPES.md#neooriginstrade_availability) | Extend villager trade offers. |
| [`rare_wandering_loot`](POWER_TYPES.md#neooriginsrare_wandering_loot) | Bonus wandering-trader drops. |

### Command integration

| Type | Summary |
|---|---|
| [`command_pack`](POWER_TYPES.md#neooriginscommand_pack) | Package of `execute_command` actions behind a single power id. |

### Active abilities (specialised)

| Type | Summary |
|---|---|
| [`active_bolt`](POWER_TYPES.md#neooriginsactive_bolt) | Fires a wind-charge projectile. Legacy; prefer `active_ability + spawn_projectile`. |
| [`active_dash`](POWER_TYPES.md#neooriginsactive_dash) | Horizontal impulse. Legacy; see MIGRATION.md. |
| [`active_fireball`](POWER_TYPES.md#neooriginsactive_fireball) | Single small fireball. Legacy. |
| [`active_phase`](POWER_TYPES.md#neooriginsactive_phase) | Toggle movement state. Legacy. |
| [`active_place_block`](POWER_TYPES.md#neooriginsactive_place_block) | Raycast and place. Legacy; no DSL verb yet. |
| [`active_recall`](POWER_TYPES.md#neooriginsactive_recall) | Saved-position teleport. Legacy; stateful. |
| [`active_swap`](POWER_TYPES.md#neooriginsactive_swap) | Swap positions with target. |
| [`active_teleport`](POWER_TYPES.md#neooriginsactive_teleport) | Look-direction blink. Legacy; no DSL verb yet. |

### Deprecated & retired

Retired concrete types that still load via alias. See
[MIGRATION.md](MIGRATION.md) for the remap table.

| Type | Replaced by |
|---|---|
| `less_item_use_slowdown` | `attribute_modifier` + `origins:using_item` condition |
| `no_projectile_divergence` | `attribute_modifier` on `minecraft:projectile_accuracy` |
| `sneaky` / `stealth` | `mobs_ignore_player` + sneak gate |
| `tick_action` | `action_on_event` with `event: tick` |

---

## Condition verbs

Used in `condition` fields. All use the `origins:` namespace (the `apace:`
namespace is also accepted; they're aliases). Jumps go to
[CONDITIONS.md](CONDITIONS.md).

### Boolean combinators
`origins:and` • `origins:or` • `origins:not` • `origins:constant`

### Environment
`origins:biome` • `origins:dimension` • `origins:in_tag` • `origins:submerged_in` •
`origins:submerged_in_water` • `origins:in_water` • `origins:in_block` •
`origins:on_block` • `origins:block` • `origins:block_collision` •
`origins:on_ground` • `origins:on_fire` • `origins:in_rain` • `origins:temperature` •
`origins:weather` • `origins:brightness` • `origins:light_level` •
`origins:exposed_to_sky` • `origins:exposed_to_sun` • `origins:daytime` •
`origins:time_of_day` • `origins:moon_phase` • `origins:height` •
`origins:fluid_height` • `origins:distance`

### Player state
`origins:health` • `origins:relative_health` • `origins:food_level` •
`origins:armor_value` • `origins:xp_level` • `origins:xp_points` •
`origins:fall_distance` • `origins:fall_flying` • `origins:sneaking` •
`origins:sprinting` • `origins:swimming` • `origins:invisible` •
`origins:creative_flying` • `origins:moving` • `origins:passenger` •
`origins:using_item` • `origins:equipped_item` • `origins:enchantment` •
`origins:resource` • `origins:living` • `origins:exists` • `origins:ticking`

### Entity & damage
`origins:entity_type` • `origins:target_type` • `origins:target_group` •
`origins:can_see` • `origins:damage_type` • `origins:damage_tag` •
`origins:damage_name` • `origins:from_fire` • `origins:from_projectile` •
`origins:from_explosion`

### Power introspection
`origins:power_active` • `origins:power_type` • `origins:in_set`

### Advanced
`origins:nbt` • `origins:scoreboard` • `origins:command` • `origins:predicate` •
`origins:amount` • `origins:equal`

---

## Action verbs

Used in `entity_action` fields. All use the `origins:` namespace (the
`apace:` namespace is also accepted). Jumps go to [ACTIONS.md](ACTIONS.md).

### Combinators & control
`origins:and` • `origins:chance` • `origins:delay` • `origins:if_else` •
`origins:if_else_list` • `origins:nothing`

### Damage & healing
`origins:damage` • `origins:heal` • `origins:feed` • `origins:exhaust` •
`origins:change_resource`

### Effects
`origins:apply_effect` • `origins:clear_effect`

### Movement & position
`origins:add_velocity` • `origins:launch` • `origins:set_fall_distance` •
`origins:dismount` • `origins:throw_target`

### Items & inventory
`origins:give` • `origins:modify_food` • `origins:spawn_entity` •
`origins:spawn_projectile`

### World & environment
`origins:set_block` • `origins:set_on_fire` • `origins:extinguish` •
`origins:explode` • `origins:gain_air` • `origins:area_of_effect`

### Power control
`origins:grant_power` • `origins:revoke_power` • `origins:trigger_cooldown`

### Integration
`origins:execute_command` • `origins:play_sound` • `origins:swing_hand` •
`origins:emit_game_event`

---

## Event keys

Used in `action_on_event`'s `event` field. Case-insensitive.

### Core lifecycle & combat
`ATTACK` • `HIT_TAKEN` • `KILL` • `DEATH` • `BLOCK_BREAK` • `BLOCK_PLACE` •
`ITEM_USE` • `RESPAWN` • `TICK` • `DIMENSION_CHANGE` • `JUMP` • `PROJECTILE_HIT`

### Interactions
`BONEMEAL` • `FOOD_EATEN` • `BLOCK_USE` • `ENTITY_USE` • `ITEM_PICKUP` •
`ITEM_USE_FINISH`

### Origin & power lifecycle
`GAINED` • `LOST` • `CHOSEN` • `WAKE_UP` • `LAND`

### Modifiers (return a float, chain in registration order)
`MOD_EXHAUSTION` • `MOD_NATURAL_REGEN` • `MOD_ENCHANT_LEVEL` •
`MOD_HARVEST_DROPS` • `MOD_TELEPORT_RANGE` • `MOD_KNOCKBACK` •
`MOD_POTION_DURATION` • `MOD_ANVIL_COST` • `MOD_CRAFTED_FOOD_SATURATION` •
`MOD_BONEMEAL_EXTRA`

See [EVENTS.md](EVENTS.md) for each event's context record.

---

## Namespaces & prefixes

NeoOrigins accepts legacy prefixes for cross-mod pack compat.

| Prefix | Meaning | How it's resolved |
|---|---|---|
| `neoorigins:*` | Canonical 2.0 namespace. Use this for new packs. | Direct registry lookup. |
| `origins:*` | Upstream Apoli / vanilla Origins. | Translator in `OriginsCompatPowerLoader` maps to `neoorigins:*` or a DSL recipe. |
| `apace:*` | Apace mod variant. | Same translator as `origins:*`. |
| `apoli:*` | Upstream Apoli mod. | `LegacyPowerTypeAliases` remaps to 2.0 generics. |
| `apugli:*` | Apugli mod. | `LegacyPowerTypeAliases` remaps; Tier-1 aliases only (edible_item, action_on_jump, action_on_target_death). |

Running on a mixed pack: if two prefixes point at the same conceptual
power and the fields differ, the alias remap picks the intersection of
supported fields. Extra fields from the legacy type are dropped with a
one-time `[2.0-legacy]` log warning.

---

## JSON schemas

Machine-readable schemas live under [schema/](schema/). Point your IDE /
datapack validator at:

- `schema/power.schema.json` — for files under `data/*/origins/powers/`
- `schema/origin.schema.json` — for files under `data/*/origins/origins/`
- `schema/origin_layer.schema.json` — for files under `data/*/origins/origin_layers/`

Schemas are derived from the Java Config records and are authoritative
against what the loader accepts. If the schema disagrees with a prose
doc, the schema wins.

---

## Source-of-truth paths

When docs drift, these are the code files that won:

- Power registrations: `src/main/java/com/cyberday1/neoorigins/power/registry/PowerTypes.java`
- Power Config records: `src/main/java/com/cyberday1/neoorigins/power/builtin/*.java`
- Condition verbs: `src/main/java/com/cyberday1/neoorigins/compat/condition/ConditionParser.java`
- Action verbs: `src/main/java/com/cyberday1/neoorigins/compat/action/ActionParser.java`
- Event keys: `src/main/java/com/cyberday1/neoorigins/service/EventPowerIndex.java`
- Legacy aliases: `src/main/java/com/cyberday1/neoorigins/power/registry/LegacyPowerTypeAliases.java`
