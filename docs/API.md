# NeoOrigins 2.0 API Reference

Single landing page for the NeoOrigins datapack API. Every power type,
condition verb, action verb, and event key is listed below with a jump-link
to the per-topic detail doc.

- [Layer model](#layer-model)
- [Documents in this API](#documents-in-this-api)
- [Power types](#power-types): 120 types + 29 retired
- [Condition verbs](#condition-verbs): 94 conditions
- [Action verbs](#action-verbs): 85 actions
- [Event keys](#event-keys): 49 events
- [Named keybinds](#named-keybinds)
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

The upstream layer (for cross-mod pack compatibility) is handled by
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
| [COOKBOOK.md](COOKBOOK.md) | Recipe-oriented tutorial: 10 common patterns. |
| [PACK_FORMAT.md](PACK_FORMAT.md) | Directory layout, file-name conventions, JSON boilerplate. |
| [CONTENT_CONFIG.md](CONTENT_CONFIG.md) | Server `content.toml` toggles: global vision / resource-bar switches and per-origin / per-class enable flags. |
| [CLIENT_CONFIG.md](CLIENT_CONFIG.md) | Per-client `client.toml` options: UI theme, HUD layout, hotkey pool size. |
| [ARCHITECTURE.md](ARCHITECTURE.md) | How powers are loaded, dispatched, cached. For debugging. |
| [COMPATIBILITY.md](COMPATIBILITY.md) | Out-of-the-box mod integrations (Origins/Apoli, Curios, Ars Nouveau, KubeJS, JEI/REI, etc.). |

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
| [`effect_over_time`](POWER_TYPES.md#neooriginseffect_over_time) | Sustained aura pulsing an action on an interval. Passive (always on) or active (keybind + upkeep). |
| [`modify_damage`](POWER_TYPES.md#neooriginsmodify_damage) | Scale damage the player deals or takes. Direction + type/tag filter. |
| [`multiple`](POWER_TYPES.md#neooriginsmultiple) | Bundle several powers into one file. Sub-powers are flattened to their own ids at load. |
| [`persistent_effect`](POWER_TYPES.md#neooriginspersistent_effect) | Refresh a status effect every tick. Replaces legacy `status_effect`. |
| [`prevent_action`](POWER_TYPES.md#neooriginsprevent_action) | Cancel a specific player action (FALL_DAMAGE, DROWN, FIRE, SWIM, etc.). |
| [`resource`](POWER_TYPES.md#neooriginsresource) | Named persistent value with a HUD bar. Regeneration, threshold actions, `change_resource` compatible. |
| [`simple`](POWER_TYPES.md#neooriginssimple) | Does nothing. Display-only marker, the equivalent of `origins:simple`. |
| [`tick_action`](POWER_TYPES.md#neooriginstick_action) | Legacy no-op: it dispatches nothing. Use `condition_passive`, which takes the same `interval`. |
| [`toggle`](POWER_TYPES.md#neooriginstoggle) | Boolean state cycled by the skill key, readable via `neoorigins:power_active`. |
| [`variable`](POWER_TYPES.md#neooriginsvariable) | Hidden persistent integer counter. No bar, no regen: written only by actions. |

### Movement & body

| Type | Summary |
|---|---|
| [`bounce_on_land`](POWER_TYPES.md#neooriginsbounce_on_land) | Reflect fall impact back upward on landing, like a slime block. |
| [`creative_flight`](POWER_TYPES.md#neooriginscreative_flight) | True hover flight as a toggle. Keeps collision and gravity when not flying. |
| [`elytra_boost`](POWER_TYPES.md#neooriginselytra_boost) | Add forward thrust while elytra-flying. |
| [`elytra_flight`](POWER_TYPES.md#neooriginselytra_flight) | Elytra-style fall-flight with no elytra equipped. Can draw wings while gliding. |
| [`enhanced_vision`](POWER_TYPES.md#neooriginsenhanced_vision) | Permanent brightness floor. Replaces legacy `night_vision`. |
| [`flight`](POWER_TYPES.md#neooriginsflight) | Creative-style flight. |
| [`modify_flight_speed`](POWER_TYPES.md#neooriginsmodify_flight_speed) | Scale creative and hover flight speed, but not elytra gliding. In-mod replacement for Pehkui. |
| [`natural_glide`](POWER_TYPES.md#neooriginsnatural_glide) | Glide by pressing jump while falling, with no elytra equipped. |
| [`phantom_form`](POWER_TYPES.md#neooriginsphantom_form) | Mayfly + noPhysics + no-fall: walk through walls. |
| [`size_scaling`](POWER_TYPES.md#neooriginssize_scaling) | Scale player dimensions. Optional reach-scaling + flat reach bonus. |
| [`wall_climbing`](POWER_TYPES.md#neooriginswall_climbing) | Scale walls like a spider. |
| [`walk_on_fluid`](POWER_TYPES.md#neooriginswalk_on_fluid) | Walk on water, lava or both (default both), like a Strider. The surface stops being solid once you are submerged. |
| [`water_breathing`](POWER_TYPES.md#neooriginswater_breathing) | Never loses air underwater. |
| [`wraith_phase`](POWER_TYPES.md#neooriginswraith_phase) | Toggleable phasing through solid blocks: jump and sneak for vertical control. `always_on` makes it passive. |
| [`cobweb_affinity`](POWER_TYPES.md#neooriginscobweb_affinity) | Web mobility + 10× cobweb break speed. |
| [`burn`](POWER_TYPES.md#neooriginsburn) | Set the player on fire at a configurable interval. |
| [`ignore_water`](POWER_TYPES.md#neooriginsignore_water) | Full land speed in water + no current pushing. |
| [`ignore_fluid`](POWER_TYPES.md#neooriginsignore_fluid) | Totally ignore named fluids (incl. modded): no buoyancy, drag, push, drowning, burn, fog or swim pose. |
| [`lava_vision`](POWER_TYPES.md#neooriginslava_vision) | Increase vision distance in lava. |
| [`modify_lava_speed`](POWER_TYPES.md#neooriginsmodify_lava_speed) | Change movement speed while submerged in lava. |
| [`overlay`](POWER_TYPES.md#neooriginsoverlay) | Full-screen texture overlay with configurable opacity. |
| [`model_color`](POWER_TYPES.md#neooriginsmodel_color) | RGBA tint on the player model. |
| [`entity_model`](POWER_TYPES.md#neooriginsentity_model) | Replace the player's model with another entity's: a morph. Changes hitbox and voice with it. |
| [`invisibility`](POWER_TYPES.md#neooriginsinvisibility) | Turn the player invisible, optionally hiding worn armour too. |
| [`particle`](POWER_TYPES.md#neooriginsparticle) | Emit vanilla particles on the player at a fixed cadence. Server-side. |
| [`shader`](POWER_TYPES.md#neooriginsshader) | Post-processing shader on the player's view. |

### Minions & entities

| Type | Summary |
|---|---|
| [`summon_minion`](POWER_TYPES.md#neooriginssummon_minion) | Active ability that spawns a tracked mob with owner-aware AI. |
| [`tame_mob`](POWER_TYPES.md#neooriginstame_mob) | Active ability that rewrites a mob's AI to follow and defend the owner. |
| [`tamed_animal_boost`](POWER_TYPES.md#neooriginstamed_animal_boost) | Buff vanilla tamed animals (HP / speed). |
| [`tamed_potion_diffusal`](POWER_TYPES.md#neooriginstamed_potion_diffusal) | Potions drunk near tamed animals apply to them too. |
| [`entity_group`](POWER_TYPES.md#neooriginsentity_group) | Mark the player as part of a built-in or datapack-defined entity group. Affects effect/enchant interactions and which mobs ignore, hunt, or flee you. |
| [`entity_set`](POWER_TYPES.md#neooriginsentity_set) | Named UUID set per power id. Pair with `neoorigins:in_set` / `add_to_set` / `remove_from_set`. |
| [`mobs_ignore_player`](POWER_TYPES.md#neooriginsmobs_ignore_player) | Mobs don't aggro. Retaliation window preserved. |
| [`sneaky`](POWER_TYPES.md#neooriginssneaky) | Cut mob detection range via `detection_multiplier` (default 0.3): mobs only target the holder from much closer. |
| [`stealth`](POWER_TYPES.md#neooriginsstealth) | Invisibility after sneaking continuously for a threshold, clearing when you stop. Toggleable, so it claims a skill key. |
| [`mobs_target_player`](POWER_TYPES.md#neooriginsmobs_target_player) | The inverse: listed mobs, or every mob if none are listed, hunt the holder within range without needing line of sight. |
| [`mob_behavior`](POWER_TYPES.md#neooriginsmob_behavior) | Give a **mob origin** piglin-style aggression toward players or a named entity type. Vanilla AI stays intact. |
| [`attract_mobs`](POWER_TYPES.md#neooriginsattract_mobs) | Pull nearby mobs toward the holder, as though holding their favourite food. |
| [`scare_entities`](POWER_TYPES.md#neooriginsscare_entities) | Listed entity types flee from the player. |
| [`muffle_sound`](POWER_TYPES.md#neooriginsmuffle_sound) | Emit no game-event vibrations: sculk sensors and wardens stop noticing you. |
| [`mount`](POWER_TYPES.md#neooriginsmount) | Toggleable ability that seats the player on the entity they are looking at. Consent for player targets is server-config gated, and off by default. |
| [`no_mob_spawns_nearby`](POWER_TYPES.md#neooriginsno_mob_spawns_nearby) | Cancel natural spawns in a radius. |
| [`twin_breeding`](POWER_TYPES.md#neooriginstwin_breeding) | Chance for breeding to yield two babies. |

### Combat

| Type | Summary |
|---|---|
| [`invulnerability`](POWER_TYPES.md#neooriginsinvulnerability) | Filter damage types the player is immune to. |
| [`prevent_death`](POWER_TYPES.md#neooriginsprevent_death) | Cancel the lethal blow; condition / damage-type / cooldown gated. |
| [`projectile_immunity`](POWER_TYPES.md#neooriginsprojectile_immunity) | Filter projectile types the player is immune to. |
| [`effect_immunity`](POWER_TYPES.md#neooriginseffect_immunity) | Block specific status effects. |
| [`dodge_chance`](POWER_TYPES.md#neooriginsdodge_chance) | Percentage chance to dodge incoming damage. |
| [`no_projectile_divergence`](POWER_TYPES.md#neooriginsno_projectile_divergence) | Perfect accuracy on projectiles the player shoots: no random spread. |
| [`thorns_on_hit`](POWER_TYPES.md#neooriginsthorns_on_hit) | Passive thorns: attacker takes damage back. |
| [`light_level_effect`](POWER_TYPES.md#neooriginslight_level_effect) | Apply effect at/below a light level. |
| [`low_hp_threshold`](POWER_TYPES.md#neooriginslow_hp_threshold) | Apply effects when HP drops below a threshold. |
| [`ender_gaze_immunity`](POWER_TYPES.md#neooriginsender_gaze_immunity) | Endermen don't aggro on eye contact. |
| [`ground_slam`](POWER_TYPES.md#neooriginsground_slam) | Radial AoE damage from falling. |
| [`tidal_wave`](POWER_TYPES.md#neooriginstidal_wave) | Active cone-shape knockback. |
| [`shadow_orb`](POWER_TYPES.md#neooriginsshadow_orb) | Place a stationary orb emitting Darkness + Blindness. |

### Mining, farming, crafting

| Type | Summary |
|---|---|
| [`break_speed_modifier`](POWER_TYPES.md#neooriginsbreak_speed_modifier) | Multiply block break speed with block/tool/condition filters. |
| [`bare_hand_tool`](POWER_TYPES.md#neooriginsbare_hand_tool) | Empty hand mines as a named tool, for both drop eligibility and speed. |
| [`underwater_mining_speed`](POWER_TYPES.md#neooriginsunderwater_mining_speed) | Remove underwater mining penalty. |
| [`tree_felling`](POWER_TYPES.md#neooriginstree_felling) | Chop connected logs in one swing. |
| [`ultimine`](POWER_TYPES.md#neooriginsultimine) | Vein-mining via FTB Ultimine. Inert when that mod is absent. |
| [`fortune_when_effect`](POWER_TYPES.md#neooriginsfortune_when_effect) | Virtual Fortune level on a block tag (default `#c:ores`) while a named status effect is active. |
| [`crop_growth_accelerator`](POWER_TYPES.md#neooriginscrop_growth_accelerator) | Periodic tick boost to nearby crops. |
| [`crop_harvest_bonus`](POWER_TYPES.md#neooriginscrop_harvest_bonus) | +N drops per crop break. |
| [`quality_equipment`](POWER_TYPES.md#neooriginsquality_equipment) | Starting tool quality uplift. |
| [`craft_amount_bonus`](POWER_TYPES.md#neooriginscraft_amount_bonus) | Extra output on crafted items. |
| [`more_smoker_xp`](POWER_TYPES.md#neooriginsmore_smoker_xp) | Bonus XP from smoker. |

### Inventory & items

| Type | Summary |
|---|---|
| [`starting_equipment`](POWER_TYPES.md#neooriginsstarting_equipment) | Grant items on origin chosen. |
| [`extra_inventory`](POWER_TYPES.md#neooriginsextra_inventory) | Extra chest-style inventory on the skill key. `size` is in slots (9-54), persisted. |
| [`keep_inventory`](POWER_TYPES.md#neooriginskeep_inventory) | Slot/item filter for inventory kept across death. |
| [`restrict_armor`](POWER_TYPES.md#neooriginsrestrict_armor) | Slot-scoped gate on wearable items. |
| [`restrict_items`](POWER_TYPES.md#neooriginsrestrict_items) | Gate equipping and using items: `prevent_equip` and `prevent_use` are separate opt-ins. Allow-list or blacklist, any slot or hand. |
| [`prevent_item_damage`](POWER_TYPES.md#neooriginsprevent_item_damage) | Matching items never lose durability, on every durability path. |
| [`item_magnetism`](POWER_TYPES.md#neooriginsitem_magnetism) | Pull item entities to the player. |
| [`hide_hud_bar`](POWER_TYPES.md#neooriginshide_hud_bar) | Hide the food or air bar. |

### Healing, hunger, survival

| Type | Summary |
|---|---|
| [`horde_regen`](POWER_TYPES.md#neooriginshorde_regen) | Regen scales with nearby allied mob count. |
| [`no_natural_regen`](POWER_TYPES.md#neooriginsno_natural_regen) | Kill food-driven regen; potion/beacon heals still work. |
| [`exhaustion_filter`](POWER_TYPES.md#neooriginsexhaustion_filter) | Selective hunger-drain modifier. |
| [`modify_food_nutrition`](POWER_TYPES.md#neooriginsmodify_food_nutrition) | Override the hunger value of matching food. Saturation scales with it. |
| [`no_slowdown`](POWER_TYPES.md#neooriginsno_slowdown) | Immune to specific slowdown sources. |
| [`breath_in_fluid`](POWER_TYPES.md#neooriginsbreath_in_fluid) | Air supply drains in named fluids. |
| [`breath_out_of_fluid`](POWER_TYPES.md#neooriginsbreath_out_of_fluid) | Air drains while *out* of a named fluid: a fish out of water. |
| [`slime_moisture`](POWER_TYPES.md#neooriginsslime_moisture) | Moisture bar draining over time, faster when dry or on fire. Threshold effects. |
| [`slime_level_hp`](POWER_TYPES.md#neooriginsslime_level_hp) | Bonus max HP per XP level (default one per 10, capped), tracking your current level. |

### Respawn & lifecycle

| Type | Summary |
|---|---|
| [`modify_player_spawn`](POWER_TYPES.md#neooriginsmodify_player_spawn) | Per-power respawn override. Optional bed override. |
| [`slime_death_save`](POWER_TYPES.md#neooriginsslime_death_save) | Split instead of dying while moisture is high: teleport away at 2 hearts. |

### Merchant & loot

| Type | Summary |
|---|---|
| [`trade_availability`](POWER_TYPES.md#neooriginstrade_availability) | Extend villager trade offers. |
| [`rare_wandering_loot`](POWER_TYPES.md#neooriginsrare_wandering_loot) | Bonus wandering-trader drops. |
| [`kill_loot_drops`](POWER_TYPES.md#neooriginskill_loot_drops) | Extra drops on mobs the holder kills, through the real loot pipeline. |
| [`loot_pool_grant`](POWER_TYPES.md#neooriginsloot_pool_grant) | Active ability that rolls a loot table into the inventory. One-shot per `grant_id`, cleared by an origin reset. |

### Command integration

| Type | Summary |
|---|---|
| [`command_pack`](POWER_TYPES.md#neooriginscommand_pack) | Package of `execute_command` actions behind a single power id. |

### Scripting

Power types whose behaviour is supplied by a KubeJS script. See [KUBEJS.md](KUBEJS.md).

| Type | Summary |
|---|---|
| [`js_active`](POWER_TYPES.md#neooriginsjs_active) | Keybind ability whose `onUse` runs JS. Returning false consumes nothing. |
| [`js_custom`](POWER_TYPES.md#neooriginsjs_custom) | Passive power whose grant / revoke / tick hooks run JS. |

### Mod compatibility

Hooks into another mod. Each is an inert marker when that mod is absent. See
[COMPATIBILITY.md](COMPATIBILITY.md).

| Type | Summary |
|---|---|
| [`become_dragon`](POWER_TYPES.md#neooriginsbecome_dragon) | Dragon Survival: the holder is a dragon of the named species while the power is granted. |
| [`xeno_passive`](POWER_TYPES.md#neooriginsxeno_passive) | Aliens vs Predator: facehuggers stop treating the holder as a viable host. |

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

Retired concrete types that still load, remapped onto a live type. Existing packs
keep working unchanged; prefer the replacement in new ones. See
[MIGRATION.md](MIGRATION.md) for the full remap table.

| Type | Replaced by |
|---|---|
| [`action_on_hit_taken`](POWER_TYPES.md#neooriginsaction_on_hit_taken) | `action_on_event` |
| [`action_on_kill`](POWER_TYPES.md#neooriginsaction_on_kill) | `action_on_event` |
| [`active_aoe_effect`](POWER_TYPES.md#neooriginsactive_aoe_effect) | `active_ability` |
| [`active_launch`](POWER_TYPES.md#neooriginsactive_launch) | `active_ability` |
| [`better_bone_meal`](POWER_TYPES.md#neooriginsbetter_bone_meal) | `action_on_event` |
| [`better_crafted_food`](POWER_TYPES.md#neooriginsbetter_crafted_food) | `action_on_event` |
| [`better_enchanting`](POWER_TYPES.md#neooriginsbetter_enchanting) | `action_on_event` |
| [`biome_buff`](POWER_TYPES.md#neooriginsbiome_buff) | `condition_passive` |
| [`burn_at_health_threshold`](POWER_TYPES.md#neooriginsburn_at_health_threshold) | `condition_passive` |
| [`damage_in_biome`](POWER_TYPES.md#neooriginsdamage_in_biome) | `condition_passive` |
| [`damage_in_daylight`](POWER_TYPES.md#neooriginsdamage_in_daylight) | `condition_passive` |
| [`damage_in_water`](POWER_TYPES.md#neooriginsdamage_in_water) | `condition_passive` |
| [`efficient_repairs`](POWER_TYPES.md#neooriginsefficient_repairs) | `action_on_event` |
| [`food_restriction`](POWER_TYPES.md#neooriginsfood_restriction) | `action_on_event` |
| [`glow`](POWER_TYPES.md#neooriginsglow) | `persistent_effect` |
| [`healing_mist`](POWER_TYPES.md#neooriginshealing_mist) | `active_ability` |
| [`hunger_drain_modifier`](POWER_TYPES.md#neooriginshunger_drain_modifier) | `action_on_event` |
| [`knockback_modifier`](POWER_TYPES.md#neooriginsknockback_modifier) | `action_on_event` |
| [`less_item_use_slowdown`](POWER_TYPES.md#neooriginsless_item_use_slowdown) | `attribute_modifier` + `neoorigins:using_item` condition |
| [`longer_potions`](POWER_TYPES.md#neooriginslonger_potions) | `action_on_event` |
| [`more_animal_loot`](POWER_TYPES.md#neooriginsmore_animal_loot) | `action_on_event` |
| [`natural_regen_modifier`](POWER_TYPES.md#neooriginsnatural_regen_modifier) | `action_on_event` |
| [`night_vision`](POWER_TYPES.md#neooriginsnight_vision) | `persistent_effect` (or `enhanced_vision` for a brightness floor) |
| [`regen_in_fluid`](POWER_TYPES.md#neooriginsregen_in_fluid) | `condition_passive` |
| [`repulse`](POWER_TYPES.md#neooriginsrepulse) | `active_ability` |
| [`stacking_status_effects`](POWER_TYPES.md#neooriginsstacking_status_effects) | `persistent_effect` |
| [`status_effect`](POWER_TYPES.md#neooriginsstatus_effect) | `persistent_effect` |
| [`teleport_range_modifier`](POWER_TYPES.md#neooriginsteleport_range_modifier) | `action_on_event` |
| [`thorns_aura`](POWER_TYPES.md#neooriginsthorns_aura) | `action_on_event` |

---

## Condition verbs

Used in `condition` fields. All use the `neoorigins:` namespace (the `apace:`
namespace is also accepted; they're aliases). Jumps go to
[CONDITIONS.md](CONDITIONS.md).

### Boolean combinators
`neoorigins:and` • `neoorigins:or` • `neoorigins:not` • `neoorigins:constant` •
`neoorigins:always_active`

### Environment
`neoorigins:biome` • `neoorigins:dimension` • `neoorigins:in_tag` • `neoorigins:submerged_in` •
`neoorigins:submerged_in_water` • `neoorigins:in_water` • `neoorigins:in_block` •
`neoorigins:on_block` • `neoorigins:block` • `neoorigins:block_collision` •
`neoorigins:on_ground` • `neoorigins:on_fire` • `neoorigins:in_rain` • `neoorigins:temperature` •
`neoorigins:weather` • `neoorigins:brightness` • `neoorigins:light_level` •
`neoorigins:exposed_to_sky` • `neoorigins:exposed_to_sun` • `neoorigins:daytime` •
`neoorigins:time_of_day` • `neoorigins:moon_phase` • `neoorigins:height` •
`neoorigins:fluid_height` • `neoorigins:distance` • `neoorigins:near_entity` •
`neoorigins:nearby_entities` • `neoorigins:near_villager`

### Player state
`neoorigins:health` • `neoorigins:relative_health` • `neoorigins:food_level` •
`neoorigins:armor_value` • `neoorigins:xp_level` • `neoorigins:xp_points` •
`neoorigins:fall_distance` • `neoorigins:fall_flying` • `neoorigins:sneaking` •
`neoorigins:sprinting` • `neoorigins:swimming` • `neoorigins:invisible` •
`neoorigins:creative_flying` • `neoorigins:moving` • `neoorigins:passenger` •
`neoorigins:using_item` • `neoorigins:equipped_item` • `neoorigins:enchantment` •
`neoorigins:resource` • `neoorigins:living` • `neoorigins:exists` • `neoorigins:ticking`

### Entity & damage
`neoorigins:entity_type` • `neoorigins:target_type` • `neoorigins:target_group` •
`neoorigins:can_see` • `neoorigins:damage_type` • `neoorigins:damage_tag` •
`neoorigins:damage_name` • `neoorigins:from_fire` • `neoorigins:from_projectile` •
`neoorigins:from_explosion`

### Power introspection
`neoorigins:power_active` • `neoorigins:power_type` • `neoorigins:in_set`

### Advanced
`neoorigins:nbt` • `neoorigins:scoreboard` • `neoorigins:statistic` •
`neoorigins:command` • `neoorigins:predicate` •
`neoorigins:amount` • `neoorigins:equal`

---

## Action verbs

Used in `entity_action` fields. All use the `neoorigins:` namespace (the
`apace:` namespace is also accepted). Jumps go to [ACTIONS.md](ACTIONS.md).

### Combinators & control
`neoorigins:and` • `neoorigins:chance` • `neoorigins:delay` • `neoorigins:if_else` •
`neoorigins:if_else_list` • `neoorigins:nothing`

### Damage & healing
`neoorigins:damage` • `neoorigins:heal` • `neoorigins:feed` • `neoorigins:exhaust` •
`neoorigins:change_resource`

### Effects
`neoorigins:apply_effect` • `neoorigins:clear_effect`

### Movement & position
`neoorigins:add_velocity` • `neoorigins:launch` • `neoorigins:set_fall_distance` •
`neoorigins:dismount` • `neoorigins:throw_target`

### Items & inventory
`neoorigins:give` • `neoorigins:modify_food` • `neoorigins:spawn_entity` •
`neoorigins:spawn_projectile`

### World & environment
`neoorigins:set_block` • `neoorigins:set_on_fire` • `neoorigins:extinguish` •
`neoorigins:explode` • `neoorigins:gain_air` • `neoorigins:area_of_effect`

### Power control
`neoorigins:grant_power` • `neoorigins:revoke_power` • `neoorigins:trigger_cooldown`

### Integration
`neoorigins:execute_command` • `neoorigins:play_sound` • `neoorigins:swing_hand` •
`neoorigins:emit_game_event`

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

## Named keybinds

By default, active powers consume one of the six hardcoded skill slots
(`key.neoorigins.skill_1` … `key.neoorigins.skill_6`). For packs that ship more
than six active abilities, NeoOrigins exposes a **named-keybind pool**: the
pack declares its own translation key on the power, and the client surfaces
each declared key as a labelled hotkey in *Controls → Key Binds → NeoOrigins
(Hotkeys)*.

### Declaring on a power

Add a `key` field to an Apoli-style `origins:active_self` or `origins:toggle`
power JSON. The compat loader picks the field up and registers the binding:

```json
{
  "type": "origins:active_self",
  "key": "examplepack.key.origins.1",
  "cooldown": 80,
  "entity_action": { "type": "neoorigins:add_velocity", "y": 1.5 },
  "name": "Leap"
}
```

`key` can also be an object form that carries the `continuous` flag:

```json
"key": { "key": "examplepack.key.origins.channel", "continuous": true }
```

- `key` is a free-form translation key string. Ship the human label as the
  matching entry in your pack's `lang/en_us.json` (e.g.
  `"examplepack.key.origins.1": "Phase Step"`).
- `continuous: false` (default) = the action fires once per press. `true` =
  the action fires every tick while the key is held, appropriate for
  hold-to-channel abilities; skip cooldowns for these.
- Vanilla input keys (see [Vanilla input keys](#vanilla-input-keys) below) are
  *not* routed through the pool; they bind directly to a real game control and
  fire from server-side input polling, so they never consume a named-hotkey or
  skill slot.
- The native `neoorigins:active_ability` type does **not** use the pool;
  it always binds to one of the six built-in `skill_1`..`skill_6` slots.
  Use `origins:active_self` when you need a named hotkey.

### Numbered slots (`key: N`)

Instead of a translation-key string, `key` may be a **number**. `"key": 1`
targets named-hotkey pool slot 1 directly; it is shorthand for the canonical
translation key `key.neoorigins.hotkey.1` (1-indexed). The slot already carries
a built-in label ("Hotkey 01" … up to "Hotkey 64"), so you don't need to ship a
`lang` entry for it, and the modpack can pre-bind it via the
[`slot_defaults` client config](CLIENT_CONFIG.md#hotkeys).

```json
{
  "type": "origins:active_self",
  "key": 1,
  "cooldown": 80,
  "entity_action": { "type": "neoorigins:add_velocity", "y": 1.5 },
  "name": "Leap"
}
```

The object form accepts a number too, so a numbered slot can still be
continuous: `"key": { "key": 1, "continuous": true }`.

- **String vs. number.** A string `key` fills the next free pool slot in sorted
  order: stable across relogs, but which physical "Hotkey N" row it lands on
  depends on what else the pack declares. A numeric `key: N` is **deterministic**:
  it always pins to slot N, so the author controls exactly which row (and which
  `slot_defaults` default) a power gets. Reach for the number when you want a
  predictable layout; reach for the string when you just want "some free slot"
  and a custom label.
- Slot `N` must be within `pool_size` (default 32). A `key: N` above the pool
  size is logged and left dormant, same as string overflow. Raise
  [`pool_size`](CLIENT_CONFIG.md#hotkeys) to make room.

### One key, several powers

Multiple powers may declare the **same** `key`, whether a shared string or the
same `key: N`. They are not in conflict: the server keeps a list of bindings per
key and fans a single press out to **all** of them (each still gated by its own
`condition` and `cooldown`). This is how one keypress can drive several powers on
an origin at once, e.g. `"key": 1` on both a movement power and a particle
power fires both from one button. A player binds that one "Hotkey N" row a single
time in Controls and it triggers the whole group.

Source of truth: `power/keybind/PowerKeybindRegistry.java` (`BY_KEY` list +
`dispatch` fan-out) and `client/HotkeyAssignments.java` (the two-pass slot
assignment that pins numbered keys).

### Vanilla input keys

Instead of a translation key, a power's `key` field may name one of nine
**vanilla game controls**. The power then activates whenever the player uses
that control: no extra hotkey to bind, no pool slot consumed. This is ideal for
abilities that should feel native to an existing input (a sneak-toggle stance, a
jump-triggered dash, a use-key channel).

Supported keys:

| Key | Fires while… |
|---|---|
| `key.jump` | the jump key is held |
| `key.sneak` | the player is sneaking |
| `key.sprint` | the player is sprinting |
| `key.use` | the use / right-click key is held |
| `key.attack` | the attack / left-click key is held (or the player is swinging) |
| `key.forward` | pressing forward |
| `key.back` | pressing back |
| `key.left` | strafing left |
| `key.right` | strafing right |

```json
{
  "type": "origins:active_self",
  "key": "key.jump",
  "cooldown": 30,
  "condition": { "type": "origins:in_air" },
  "entity_action": { "type": "neoorigins:add_velocity", "y": 0.8 },
  "name": "Double Jump"
}
```

- The server polls the bound control's state every tick and fires the power's
  own action under its `condition` gate and `cooldown`. Non-continuous bindings
  are **edge-detected**: one activation per press, not once per tick. Set
  `"key": { "key": "key.use", "continuous": true }` for a hold-to-channel ability.
- `key.use`, `key.attack` and `key.jump` report their **true held state**:
  the client sends the real key state each tick (`VanillaKeyStatePayload`)
  rather than inferring it from swing animations or interaction packets, so
  hold-and-release timing is accurate.
- Because the activation runs through the power's normal gate, `fail_action`
  and cooldowns behave exactly as they do for named hotkeys.
- Works on `neoorigins:active_ability` as well as the Apoli-style
  `origins:active_self` / `origins:toggle` powers.

Source of truth: `power/keybind/PowerKeybindRegistry.java` (the
`case "key.*"` switch and `VANILLA_NATIVE` poller).

### Fail feedback (`fail_action`)

`origins:active_self`, `origins:toggle`, and `origins:launch` accept an
optional `fail_action` (a NeoOrigins extension; plain Apoli ignores it).
It runs when the player presses the power's key but the `condition` gate
fails, replacing the silent no-op with author-defined feedback:

```json
{
  "type": "origins:active_self",
  "key": "examplepack.key.origins.1",
  "cooldown": 80,
  "condition": { "type": "origins:on_block" },
  "entity_action": { "type": "neoorigins:add_velocity", "y": 1.5 },
  "fail_action": {
    "type": "neoorigins:execute_command",
    "command": "tellraw @s {\"text\":\"You must be on the ground to leap.\",\"color\":\"red\"}"
  },
  "name": "Leap"
}
```

- Works on every activation path: skill slots, named hotkeys, and vanilla
  input keys.
- Fires once per press. Held continuous keys are edge-detected so the
  feedback doesn't spam every tick.
- Only `condition` failures trigger it; a press blocked by an active
  cooldown stays silent (the HUD already shows the cooldown), and a
  blocked attempt never consumes the cooldown.
- The native `neoorigins:active_ability` type supports the same field
  (see [POWER_TYPES.md](POWER_TYPES.md#neooriginsactive_ability)).

### Hotkey-less actives (`disable_hotkey`)

`origins:active_self` accepts an optional boolean `disable_hotkey` (a
NeoOrigins extension; plain Apoli ignores it). When `true`, the power stays
a fully-fledged active ability (its `condition`, `cooldown`, and
`fail_action` all still apply), but it claims **no** skill slot or named
hotkey, so no key press can fire it. The only way to trigger it is the
[`neoorigins:activate_power`](ACTIONS.md#neooriginsactivate_power) action from
another power:

```json
{
  "type": "origins:active_self",
  "disable_hotkey": true,
  "cooldown": 200,
  "entity_action": { "type": "neoorigins:add_velocity", "y": 2.0 },
  "name": "Hidden Leap"
}
```

- Use this for abilities that should only ever be reached programmatically,
  e.g. a passive `condition` or `tick_action` power that fires
  `neoorigins:activate_power` at the hotkey-less power when its own trigger
  conditions are met.
- Omitting `key` on an `origins:active_self` normally drops the power into the
  next free `skill_1`..`skill_6` slot; `disable_hotkey: true` opts out of that
  entirely, freeing the slot for other powers.
- A `disable_hotkey` power with no `entity_action` does nothing; there's no
  hotkey *and* no action, so the power is inert.
- `disable_hotkey: true` takes precedence even if you also declare a `key`:
  the key is ignored and no slot or hotkey is bound. You don't have to remove
  `key` to make an existing active power hotkey-less.

### How the pool works

- At reload, the server collects every distinct `key` value from all loaded
  powers and broadcasts the sorted list to each client. Pool size is capped
  by `NeoOriginsConfig.HOTKEY_POOL_SIZE`.
- Each declared key is assigned an anonymous `Hotkey 01` … `Hotkey N` slot in
  stable order; a player who relogs sees the same key in the same slot as
  long as the pack hasn't changed.
- The player rebinds each slot to a physical key in vanilla controls. Press
  routes via `ActivatePowerByKeyPayload` back to the server, which fires the
  bound power's entity action under the original cooldown / condition gates.
- If another mod (keybindjs etc.) has already claimed the same key, the
  pool slot detects it and stands down to avoid double-binding.

### Display labels

The hotkey list in the controls menu shows your translation key's human
label. Built-in `skill_5` and `skill_6` slots use the same `key.neoorigins.skill_N`
naming as the original four; their labels are bundled in
`assets/neoorigins/lang/en_us.json`.

Source of truth: `power/keybind/PowerKeybindRegistry.java`,
`client/HotkeyAssignments.java`.

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

- `schema/power.schema.json`: for files under `data/*/origins/powers/`
- `schema/origin.schema.json`: for files under `data/*/origins/origins/`
- `schema/origin_layer.schema.json`: for files under `data/*/origins/origin_layers/`
- `schema/mob_origin.schema.json`: for files under `data/*/origins/mob_origins/`
- `schema/morph.schema.json`: for files under `data/*/neoorigins/morphs/`

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
- Condition verbs: `src/main/java/com/cyberday1/neoorigins/compat/condition/ConditionParser.java`
- Action verbs: `src/main/java/com/cyberday1/neoorigins/compat/action/ActionParser.java`
- Event keys: `src/main/java/com/cyberday1/neoorigins/service/EventPowerIndex.java`
- Legacy aliases: `src/main/java/com/cyberday1/neoorigins/power/registry/LegacyPowerTypeAliases.java`
