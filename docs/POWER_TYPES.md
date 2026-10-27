---
title: Power Types
parent: "DSL Reference"
nav_order: 2
has_children: true
---

# NeoOrigins Power Types Reference

Every power type, grouped by what it does. Each row links to the type's full field table.

Start with [Power basics](concepts/power-basics.md) for the fields every power accepts. Cross-cutting rules have their own pages: [Costs & upkeep](concepts/costs-and-upkeep.md), [Toggles](concepts/toggles.md), [Keybinds](concepts/keybinds.md) and [Cooldowns & HUD](concepts/cooldowns-and-hud.md).

<div id="no-anchor-stub"></div>

## Core & composable

[Open the Core & composable page](powers/core.md)

| Type | Summary |
|---|---|
| <a id="neooriginssimple"></a>[`simple`](powers/core.md#neooriginssimple) | Does nothing. Display-only marker, the equivalent of `origins:simple`. |
| <a id="neooriginsmultiple"></a>[`multiple`](powers/core.md#neooriginsmultiple) | Bundle several powers into one file. Sub-powers are flattened to their own ids at load. |
| <a id="neooriginsaction_on_event"></a>[`action_on_event`](powers/core.md#neooriginsaction_on_event) | Run an action (or modify a float) when an event fires. Workhorse event-listener. |
| <a id="neooriginscondition_passive"></a>[`condition_passive`](powers/core.md#neooriginscondition_passive) | Run an action on an interval while a condition holds. Workhorse passive. |
| <a id="neooriginseffect_over_time"></a>[`effect_over_time`](powers/core.md#neooriginseffect_over_time) | Sustained aura pulsing an action on an interval. Passive (always on) or active (keybind + upkeep). |
| <a id="neooriginspersistent_effect"></a>[`persistent_effect`](powers/core.md#neooriginspersistent_effect) | Refresh a status effect every tick. Replaces legacy `status_effect`. |
| <a id="neooriginsattribute_modifier"></a>[`attribute_modifier`](powers/core.md#neooriginsattribute_modifier) | Add/multiply a player attribute. Optional tick-driven condition gate. |
| <a id="neooriginsmodify_damage"></a>[`modify_damage`](powers/core.md#neooriginsmodify_damage) | Scale damage the player deals or takes. Direction + type/tag filter. |
| <a id="neooriginsprevent_action"></a>[`prevent_action`](powers/core.md#neooriginsprevent_action) | Cancel a specific player action (FALL_DAMAGE, DROWN, FIRE, SWIM, etc.). |
| <a id="neooriginsconditional"></a>[`conditional`](powers/core.md#neooriginsconditional) | Gate another power on a static condition enum. |
| <a id="neooriginstick_action"></a>[`tick_action`](powers/core.md#neooriginstick_action) | Legacy no-op: it dispatches nothing. Use `condition_passive`, which takes the same `interval`. |

## Active abilities

[Open the Active abilities page](powers/active.md)

| Type | Summary |
|---|---|
| <a id="neooriginsactive_ability"></a>[`active_ability`](powers/active.md#neooriginsactive_ability) | Skill-key-triggered ability running one or more actions. Cooldown + hunger cost. |
| <a id="neooriginscommand_pack"></a>[`command_pack`](powers/active.md#neooriginscommand_pack) | Package of `execute_command` actions behind a single power id. |
| <a id="neooriginsactive_teleport"></a>[`active_teleport`](powers/active.md#neooriginsactive_teleport) | Look-direction blink. Legacy; no DSL verb yet. |
| <a id="neooriginsactive_dash"></a>[`active_dash`](powers/active.md#neooriginsactive_dash) | Horizontal impulse. Legacy; see MIGRATION.md. |
| <a id="neooriginsactive_recall"></a>[`active_recall`](powers/active.md#neooriginsactive_recall) | Saved-position teleport. Legacy; stateful. |
| <a id="neooriginspersonal_dimension"></a>[`personal_dimension`](powers/active.md#neooriginspersonal_dimension) | **\<Experimental\>** Key in and out of the player's own box in the `neoorigins:personal_space` dimension. Cooldown and optional XP cost on entering. |
| <a id="neooriginsactive_swap"></a>[`active_swap`](powers/active.md#neooriginsactive_swap) | Swap positions with target. |
| <a id="neooriginsactive_fireball"></a>[`active_fireball`](powers/active.md#neooriginsactive_fireball) | Single small fireball. Legacy. |
| <a id="neooriginsactive_phase"></a>[`active_phase`](powers/active.md#neooriginsactive_phase) | Toggle movement state. Legacy. |
| <a id="neooriginsactive_bolt"></a>[`active_bolt`](powers/active.md#neooriginsactive_bolt) | Fires a wind-charge projectile. Legacy; prefer `active_ability + spawn_projectile`. |
| <a id="neooriginsactive_place_block"></a>[`active_place_block`](powers/active.md#neooriginsactive_place_block) | Raycast and place. Legacy; no DSL verb yet. |
| <a id="neooriginsground_slam"></a>[`ground_slam`](powers/active.md#neooriginsground_slam) | Radial AoE damage from falling. |
| <a id="neooriginstidal_wave"></a>[`tidal_wave`](powers/active.md#neooriginstidal_wave) | Active cone-shape knockback. |
| <a id="neooriginsshadow_orb"></a>[`shadow_orb`](powers/active.md#neooriginsshadow_orb) | Place a stationary orb emitting Darkness + Blindness. |

## Resources, toggles & state

[Open the Resources, toggles & state page](powers/state.md)

| Type | Summary |
|---|---|
| <a id="neooriginsresource"></a>[`resource`](powers/state.md#neooriginsresource) | Named persistent value with a HUD bar. Regeneration, threshold actions, `change_resource` compatible. |
| <a id="neooriginsvariable"></a>[`variable`](powers/state.md#neooriginsvariable) | Hidden persistent integer counter. No bar, no regen: written only by actions. |
| <a id="neooriginstoggle"></a>[`toggle`](powers/state.md#neooriginstoggle) | Boolean state cycled by the skill key, readable via `neoorigins:power_active`. |
| <a id="neooriginsentity_set"></a>[`entity_set`](powers/state.md#neooriginsentity_set) | Named UUID set per power id. Pair with `neoorigins:in_set` / `add_to_set` / `remove_from_set`. |

## Movement & body

[Open the Movement & body page](powers/movement.md)

| Type | Summary |
|---|---|
| <a id="neooriginsflight"></a>[`flight`](powers/movement.md#neooriginsflight) | Creative-style flight. |
| <a id="neooriginscreative_flight"></a>[`creative_flight`](powers/movement.md#neooriginscreative_flight) | True hover flight as a toggle. Keeps collision and gravity when not flying. |
| <a id="neooriginselytra_boost"></a>[`elytra_boost`](powers/movement.md#neooriginselytra_boost) | Add forward thrust while elytra-flying. |
| <a id="neooriginselytra_flight"></a>[`elytra_flight`](powers/movement.md#neooriginselytra_flight) | Elytra-style fall-flight with no elytra equipped. Can draw wings while gliding. |
| <a id="neooriginsnatural_glide"></a>[`natural_glide`](powers/movement.md#neooriginsnatural_glide) | Glide by pressing jump while falling, with no elytra equipped. |
| <a id="neooriginswall_climbing"></a>[`wall_climbing`](powers/movement.md#neooriginswall_climbing) | Scale walls like a spider. |
| <a id="neooriginsmodify_flight_speed"></a>[`modify_flight_speed`](powers/movement.md#neooriginsmodify_flight_speed) | Scale creative and hover flight speed, but not elytra gliding. In-mod replacement for Pehkui. |
| <a id="neooriginsmodify_lava_speed"></a>[`modify_lava_speed`](powers/movement.md#neooriginsmodify_lava_speed) | Change movement speed while submerged in lava. |
| <a id="neooriginsno_slowdown"></a>[`no_slowdown`](powers/movement.md#neooriginsno_slowdown) | Immune to specific slowdown sources. |
| <a id="neooriginsphantom_form"></a>[`phantom_form`](powers/movement.md#neooriginsphantom_form) | Mayfly + noPhysics + no-fall: walk through walls. |
| <a id="neooriginswraith_phase"></a>[`wraith_phase`](powers/movement.md#neooriginswraith_phase) | Toggleable phasing through solid blocks: jump and sneak for vertical control. `always_on` makes it passive. |
| <a id="neooriginssize_scaling"></a>[`size_scaling`](powers/movement.md#neooriginssize_scaling) | Scale player dimensions. Optional reach-scaling + flat reach bonus. |
| <a id="neooriginspose"></a>[`pose`](powers/movement.md#neooriginspose) | Hold the player's body in a chosen pose (prone, sneak height, upright) while the power is on. |
| <a id="neooriginswalk_on_fluid"></a>[`walk_on_fluid`](powers/movement.md#neooriginswalk_on_fluid) | Walk on water, lava or both (default both), like a Strider. The surface stops being solid once you are submerged. |
| <a id="neooriginsignore_water"></a>[`ignore_water`](powers/movement.md#neooriginsignore_water) | Full land speed in water + no current pushing. |
| <a id="neooriginsignore_fluid"></a>[`ignore_fluid`](powers/movement.md#neooriginsignore_fluid) | Totally ignore named fluids (incl. modded): no buoyancy, drag, push, drowning, burn, fog or swim pose. |
| <a id="neooriginslike_air"></a>[`like_air`](powers/movement.md#neooriginslike_air) | Carry walking-speed modifiers into airborne control, as Origins' Avian does. |
| <a id="neooriginscobweb_affinity"></a>[`cobweb_affinity`](powers/movement.md#neooriginscobweb_affinity) | Web mobility + 10× cobweb break speed. |
| <a id="neooriginsbounce_on_land"></a>[`bounce_on_land`](powers/movement.md#neooriginsbounce_on_land) | Reflect fall impact back upward on landing, like a slime block. |
| <a id="neooriginswater_breathing"></a>[`water_breathing`](powers/movement.md#neooriginswater_breathing) | Never loses air underwater. |
| <a id="neooriginsbreath_in_fluid"></a>[`breath_in_fluid`](powers/movement.md#neooriginsbreath_in_fluid) | Air supply drains in named fluids. |
| <a id="neooriginsbreath_out_of_fluid"></a>[`breath_out_of_fluid`](powers/movement.md#neooriginsbreath_out_of_fluid) | Air drains while *out* of a named fluid: a fish out of water. |
| <a id="neooriginsconduit_power_on_land"></a>[`conduit_power_on_land`](powers/movement.md#neooriginsconduit_power_on_land) | Let an active conduit reach the holder out of water or rain. |

## Vision, rendering & HUD

[Open the Vision, rendering & HUD page](powers/visuals.md)

| Type | Summary |
|---|---|
| <a id="neooriginsenhanced_vision"></a>[`enhanced_vision`](powers/visuals.md#neooriginsenhanced_vision) | Permanent brightness boost applied client-side (no potion effect or HUD icon), switched with the player's "Toggle Night Vision" key. The retired `night_vision` id remaps to `persistent_effect`, not to this. |
| <a id="neooriginslava_vision"></a>[`lava_vision`](powers/visuals.md#neooriginslava_vision) | Increase vision distance in lava. |
| <a id="neooriginsoverlay"></a>[`overlay`](powers/visuals.md#neooriginsoverlay) | Full-screen texture overlay with configurable opacity. |
| <a id="neooriginsshader"></a>[`shader`](powers/visuals.md#neooriginsshader) | Post-processing shader on the player's view. |
| <a id="neooriginsmodel_color"></a>[`model_color`](powers/visuals.md#neooriginsmodel_color) | RGBA tint on the player model. Not drawn on 26.1, 26.2 or 26.3: the power still loads and syncs, but there is no tint render pass. |
| <a id="neooriginsentity_model"></a><a id="first-person-view"></a><a id="skin-morphs"></a><a id="morph-sounds"></a><a id="morph-hitbox"></a><a id="morph-definitions"></a>[`entity_model`](powers/visuals.md#neooriginsentity_model) | Replace the player's model with another entity's: a morph. Changes hitbox and voice with it. |
| <a id="neooriginsinvisibility"></a>[`invisibility`](powers/visuals.md#neooriginsinvisibility) | Turn the player invisible, optionally hiding worn armour too. |
| <a id="neooriginsprevent_entity_render"></a>[`prevent_entity_render`](powers/visuals.md#neooriginsprevent_entity_render) | Hide matching living entities from this player only; they still act and collide. |
| <a id="neooriginsentity_glow"></a>[`entity_glow`](powers/visuals.md#neooriginsentity_glow) | Outline matching living entities for the holder alone, as a Spectral Arrow marks a target. |
| <a id="neooriginsparticle"></a>[`particle`](powers/visuals.md#neooriginsparticle) | Emit vanilla particles on the player at a fixed cadence. Server-side. |
| <a id="neooriginshide_hud_bar"></a>[`hide_hud_bar`](powers/visuals.md#neooriginshide_hud_bar) | Hide the food or air bar. |

## Combat & damage

[Open the Combat & damage page](powers/combat.md)

| Type | Summary |
|---|---|
| <a id="neooriginsinvulnerability"></a>[`invulnerability`](powers/combat.md#neooriginsinvulnerability) | Filter damage types the player is immune to. |
| <a id="neooriginsprevent_death"></a>[`prevent_death`](powers/combat.md#neooriginsprevent_death) | Cancel the lethal blow; condition / damage-type / cooldown gated. |
| <a id="neooriginsprojectile_immunity"></a>[`projectile_immunity`](powers/combat.md#neooriginsprojectile_immunity) | Filter projectile types the player is immune to. |
| <a id="neooriginsprojectile_vulnerability"></a>[`projectile_vulnerability`](powers/combat.md#neooriginsprojectile_vulnerability) | Take extra damage from listed projectiles, even ones that deal none. |
| <a id="neooriginseffect_immunity"></a>[`effect_immunity`](powers/combat.md#neooriginseffect_immunity) | Block specific status effects. |
| <a id="neooriginsdodge_chance"></a>[`dodge_chance`](powers/combat.md#neooriginsdodge_chance) | Percentage chance to dodge incoming damage. |
| <a id="neooriginsno_projectile_divergence"></a>[`no_projectile_divergence`](powers/combat.md#neooriginsno_projectile_divergence) | Perfect accuracy on projectiles the player shoots: no random spread. |
| <a id="neooriginsthorns_on_hit"></a>[`thorns_on_hit`](powers/combat.md#neooriginsthorns_on_hit) | Passive thorns: attacker takes damage back. |
| <a id="neooriginsaction_on_hit"></a>[`action_on_hit`](powers/combat.md#neooriginsaction_on_hit) | Run an action when the player deals damage. Filterable by target. |
| <a id="neooriginsself_action_on_hit"></a>[`self_action_on_hit`](powers/combat.md#neooriginsself_action_on_hit) | Apoli-shaped on-hit hook: an action on the holder, a paired action on the victim. Cooldown-gated. |
| <a id="neooriginslight_level_effect"></a>[`light_level_effect`](powers/combat.md#neooriginslight_level_effect) | Apply effect at/below a light level. |
| <a id="neooriginslow_hp_threshold"></a>[`low_hp_threshold`](powers/combat.md#neooriginslow_hp_threshold) | Apply effects when HP drops below a threshold. |
| <a id="neooriginsender_gaze_immunity"></a>[`ender_gaze_immunity`](powers/combat.md#neooriginsender_gaze_immunity) | Endermen don't aggro on eye contact. |
| <a id="neooriginsburn"></a>[`burn`](powers/combat.md#neooriginsburn) | Set the player on fire at a configurable interval. |

## Mobs, minions & taming

[Open the Mobs, minions & taming page](powers/mobs.md)

| Type | Summary |
|---|---|
| <a id="neooriginssummon_minion"></a>[`summon_minion`](powers/mobs.md#neooriginssummon_minion) | Active ability that spawns a tracked mob with owner-aware AI. |
| <a id="neooriginstame_mob"></a><a id="targeting-modes-and-per-mob-cost"></a><a id="persistence"></a><a id="global-tamingscare-exclusions"></a>[`tame_mob`](powers/mobs.md#neooriginstame_mob) | Active ability that rewrites a mob's AI to follow and defend the owner. |
| <a id="neooriginstamed_animal_boost"></a>[`tamed_animal_boost`](powers/mobs.md#neooriginstamed_animal_boost) | Buff vanilla tamed animals (HP / speed). |
| <a id="neooriginstamed_potion_diffusal"></a>[`tamed_potion_diffusal`](powers/mobs.md#neooriginstamed_potion_diffusal) | Potions drunk near tamed animals apply to them too. |
| <a id="neooriginsentity_group"></a><a id="built-in-groups"></a><a id="custom-groups-datapack"></a>[`entity_group`](powers/mobs.md#neooriginsentity_group) | Mark the player as part of a built-in or datapack-defined entity group. Affects effect/enchant interactions and which mobs ignore, hunt, or flee you. |
| <a id="neooriginsmobs_ignore_player"></a>[`mobs_ignore_player`](powers/mobs.md#neooriginsmobs_ignore_player) | Mobs don't aggro. Retaliation window preserved. |
| <a id="neooriginsmobs_target_player"></a>[`mobs_target_player`](powers/mobs.md#neooriginsmobs_target_player) | The inverse: listed mobs, or every mob if none are listed, hunt the holder within range without needing line of sight. |
| <a id="neooriginsmob_behavior"></a>[`mob_behavior`](powers/mobs.md#neooriginsmob_behavior) | Give a **mob origin** piglin-style aggression toward players or a named entity type. Vanilla AI stays intact. |
| <a id="neooriginsattract_mobs"></a>[`attract_mobs`](powers/mobs.md#neooriginsattract_mobs) | Pull nearby mobs toward the holder, as though holding their favourite food. |
| <a id="neooriginsscare_entities"></a>[`scare_entities`](powers/mobs.md#neooriginsscare_entities) | Listed entity types flee from the player. |
| <a id="neooriginssneaky"></a>[`sneaky`](powers/mobs.md#neooriginssneaky) | Cut mob detection range via `detection_multiplier` (default 0.3): mobs only target the holder from much closer. |
| <a id="neooriginsstealth"></a>[`stealth`](powers/mobs.md#neooriginsstealth) | Invisibility after sneaking continuously for a threshold, clearing when you stop. Toggleable, so it claims a skill key. |
| <a id="neooriginsmuffle_sound"></a>[`muffle_sound`](powers/mobs.md#neooriginsmuffle_sound) | Emit no game-event vibrations: sculk sensors and wardens stop noticing you. |
| <a id="neooriginsmount"></a>[`mount`](powers/mobs.md#neooriginsmount) | Toggleable ability that seats the player on the entity they are looking at. Consent for player targets is server-config gated, and off by default. |
| <a id="neooriginsno_mob_spawns_nearby"></a>[`no_mob_spawns_nearby`](powers/mobs.md#neooriginsno_mob_spawns_nearby) | Cancel natural spawns in a radius. |
| <a id="neooriginstwin_breeding"></a>[`twin_breeding`](powers/mobs.md#neooriginstwin_breeding) | Chance for breeding to yield two babies. |
| <a id="neooriginshorde_regen"></a>[`horde_regen`](powers/mobs.md#neooriginshorde_regen) | Regen scales with nearby allied mob count. |

## Health, hunger & survival

[Open the Health, hunger & survival page](powers/survival.md)

| Type | Summary |
|---|---|
| <a id="neooriginsno_natural_regen"></a>[`no_natural_regen`](powers/survival.md#neooriginsno_natural_regen) | Kill food-driven regen; potion/beacon heals still work. |
| <a id="neooriginsexhaustion_filter"></a>[`exhaustion_filter`](powers/survival.md#neooriginsexhaustion_filter) | Selective hunger-drain modifier. |
| <a id="neooriginsmodify_food_nutrition"></a>[`modify_food_nutrition`](powers/survival.md#neooriginsmodify_food_nutrition) | Override the hunger value of matching food. Saturation scales with it. |
| <a id="neooriginsedible_item"></a><a id="tiers"></a><a id="caveborn-diet-tags"></a>[`edible_item`](powers/survival.md#neooriginsedible_item) | Make arbitrary items edible for this player. |
| <a id="neooriginsslime_moisture"></a>[`slime_moisture`](powers/survival.md#neooriginsslime_moisture) | Moisture bar draining over time, faster when dry or on fire. Threshold effects. |
| <a id="neooriginsslime_level_hp"></a>[`slime_level_hp`](powers/survival.md#neooriginsslime_level_hp) | Bonus max HP per XP level (default one per 10, capped), tracking your current level. |
| <a id="neooriginsslime_death_save"></a>[`slime_death_save`](powers/survival.md#neooriginsslime_death_save) | Split instead of dying while moisture is high: teleport away at 2 hearts. |
| <a id="neooriginsmodify_player_spawn"></a>[`modify_player_spawn`](powers/survival.md#neooriginsmodify_player_spawn) | Per-power respawn override. Optional bed override. |
| <a id="neooriginskeep_inventory"></a>[`keep_inventory`](powers/survival.md#neooriginskeep_inventory) | Slot/item filter for inventory kept across death. |

## Items & inventory

[Open the Items & inventory page](powers/items.md)

| Type | Summary |
|---|---|
| <a id="neooriginsstarting_equipment"></a><a id="singular-vs-plural"></a>[`starting_equipment`](powers/items.md#neooriginsstarting_equipment) | Grant items on origin chosen. |
| <a id="neooriginsextra_inventory"></a>[`extra_inventory`](powers/items.md#neooriginsextra_inventory) | Extra chest-style inventory on the skill key. `size` is in slots (9-54), persisted. |
| <a id="neooriginsrestrict_armor"></a><a id="armor-classes"></a>[`restrict_armor`](powers/items.md#neooriginsrestrict_armor) | Slot-scoped gate on wearable items. |
| <a id="neooriginsrestrict_items"></a>[`restrict_items`](powers/items.md#neooriginsrestrict_items) | Gate equipping and using items: `prevent_equip` and `prevent_use` are separate opt-ins. Allow-list or blacklist, any slot or hand. |
| <a id="neooriginsprevent_item_damage"></a>[`prevent_item_damage`](powers/items.md#neooriginsprevent_item_damage) | Matching items never lose durability, on every durability path. |
| <a id="neooriginsitem_magnetism"></a>[`item_magnetism`](powers/items.md#neooriginsitem_magnetism) | Pull item entities to the player. |
| <a id="neooriginsless_item_use_slowdown"></a>[`less_item_use_slowdown`](powers/items.md#neooriginsless_item_use_slowdown) | Speed modifier while using an item (`item_type`: `any` / `bow` / `shield` / id substring). Still registered; hidden from the creator's type picker. For new packs, `attribute_modifier` + the `neoorigins:using_item` condition covers the `any` case. |

## Mining, farming, crafting & trade

[Open the Mining, farming, crafting & trade page](powers/work.md)

| Type | Summary |
|---|---|
| <a id="neooriginsbreak_speed_modifier"></a>[`break_speed_modifier`](powers/work.md#neooriginsbreak_speed_modifier) | Multiply block break speed with block/tool/condition filters. |
| <a id="neooriginsbare_hand_tool"></a>[`bare_hand_tool`](powers/work.md#neooriginsbare_hand_tool) | Empty hand mines as a named tool, for both drop eligibility and speed. |
| <a id="neooriginsunderwater_mining_speed"></a>[`underwater_mining_speed`](powers/work.md#neooriginsunderwater_mining_speed) | Remove underwater mining penalty. |
| <a id="neooriginstree_felling"></a>[`tree_felling`](powers/work.md#neooriginstree_felling) | Chop connected logs in one swing. |
| <a id="neooriginsultimine"></a>[`ultimine`](powers/work.md#neooriginsultimine) | Vein-mining via FTB Ultimine. **Inert on this line:** the type is registered, but the 26.2 build contains no FTB Ultimine integration (`compat/ftbultimine` is absent), so it does nothing even with FTB Ultimine installed. |
| <a id="neooriginsfortune_when_effect"></a>[`fortune_when_effect`](powers/work.md#neooriginsfortune_when_effect) | Virtual Fortune level on a block tag (default `#c:ores`) while a named status effect is active. |
| <a id="neooriginscrop_growth_accelerator"></a>[`crop_growth_accelerator`](powers/work.md#neooriginscrop_growth_accelerator) | Periodic tick boost to nearby crops. |
| <a id="neooriginscrop_harvest_bonus"></a>[`crop_harvest_bonus`](powers/work.md#neooriginscrop_harvest_bonus) | +N drops per crop break. |
| <a id="neooriginsquality_equipment"></a>[`quality_equipment`](powers/work.md#neooriginsquality_equipment) | Starting tool quality uplift. |
| <a id="neooriginscraft_amount_bonus"></a>[`craft_amount_bonus`](powers/work.md#neooriginscraft_amount_bonus) | Extra output on crafted items. |
| <a id="neooriginsmore_smoker_xp"></a>[`more_smoker_xp`](powers/work.md#neooriginsmore_smoker_xp) | Bonus XP from smoker. |
| <a id="neooriginstrade_availability"></a>[`trade_availability`](powers/work.md#neooriginstrade_availability) | Extend villager trade offers. |
| <a id="neooriginsrare_wandering_loot"></a>[`rare_wandering_loot`](powers/work.md#neooriginsrare_wandering_loot) | Bonus wandering-trader drops. |
| <a id="neooriginskill_loot_drops"></a><a id="worked-example-head-hunter"></a><a id="activation"></a>[`kill_loot_drops`](powers/work.md#neooriginskill_loot_drops) | Extra drops on mobs the holder kills, through the real loot pipeline. |
| <a id="neooriginsloot_pool_grant"></a><a id="worked-example-wood-starter-pack"></a><a id="ftb-quests-soft-compat"></a>[`loot_pool_grant`](powers/work.md#neooriginsloot_pool_grant) | Active ability that rolls a loot table into the inventory. One-shot per `grant_id`, cleared by an origin reset. |

## Scripting & mod integration

[Open the Scripting & mod integration page](powers/integration.md)

| Type | Summary |
|---|---|
| <a id="neooriginsbecome_dragon"></a>[`become_dragon`](powers/integration.md#neooriginsbecome_dragon) | Dragon Survival: the holder is a dragon of the named species while the power is granted. |
| <a id="neooriginsxeno_passive"></a>[`xeno_passive`](powers/integration.md#neooriginsxeno_passive) | Aliens vs Predator: facehuggers stop treating the holder as a viable host. |

## Apoli compat types

[Open the Apoli compat types page](powers/apoli-compat.md)

| Type | Summary |
|---|---|
| <a id="originsmodify_xp_gain"></a>[`origins:modify_xp_gain`](powers/apoli-compat.md#originsmodify_xp_gain) | Multiplies experience points gained. |
| <a id="originsmodify_fall_damage"></a>[`origins:modify_fall_damage`](powers/apoli-compat.md#originsmodify_fall_damage) | Scales incoming fall damage, optionally gated by a `condition`. |
| <a id="originsmodify_healing"></a>[`origins:modify_healing`](powers/apoli-compat.md#originsmodify_healing) | Scales health the holder regains. |
| <a id="originsmodify_status_effect_duration"></a>[`origins:modify_status_effect_duration`](powers/apoli-compat.md#originsmodify_status_effect_duration) | Scales the duration of mob effects applied to the holder. |
| <a id="originsaction_on_death"></a>[`origins:action_on_death`](powers/apoli-compat.md#originsaction_on_death) | Fires when the holder dies. |
| <a id="apugliaction_on_jump"></a>[`apugli:action_on_jump`](powers/apoli-compat.md#apugliaction_on_jump) | Fires when the holder jumps. |
| <a id="apugliaction_on_target_death"></a>[`apugli:action_on_target_death`](powers/apoli-compat.md#apugliaction_on_target_death) | Fires when the holder lands the killing blow on another entity. |
| <a id="originscooldown"></a>[`origins:cooldown`](powers/apoli-compat.md#originscooldown) | A readable cooldown timer. |

## Retired ids

[Open the Retired ids page](powers/retired.md)

| Type | Summary |
|---|---|
| <a id="neooriginsaction_on_hit_taken"></a>[`action_on_hit_taken`](powers/retired.md#neooriginsaction_on_hit_taken) | Replaced by `action_on_event` |
| <a id="neooriginsaction_on_kill"></a>[`action_on_kill`](powers/retired.md#neooriginsaction_on_kill) | Replaced by `action_on_event` |
| <a id="neooriginsaction_over_time"></a>[`action_over_time`](powers/retired.md#neooriginsaction_over_time) | Replaced by `condition_passive` |
| <a id="neooriginsactive_aoe_effect"></a>[`active_aoe_effect`](powers/retired.md#neooriginsactive_aoe_effect) | Replaced by `active_ability` |
| <a id="neooriginsactive_launch"></a>[`active_launch`](powers/retired.md#neooriginsactive_launch) | Replaced by `active_ability` |
| <a id="neooriginsbetter_bone_meal"></a>[`better_bone_meal`](powers/retired.md#neooriginsbetter_bone_meal) | Replaced by `action_on_event` |
| <a id="neooriginsbetter_crafted_food"></a>[`better_crafted_food`](powers/retired.md#neooriginsbetter_crafted_food) | Replaced by `action_on_event` |
| <a id="neooriginsbetter_enchanting"></a>[`better_enchanting`](powers/retired.md#neooriginsbetter_enchanting) | Replaced by `action_on_event` |
| <a id="neooriginsbiome_buff"></a>[`biome_buff`](powers/retired.md#neooriginsbiome_buff) | Replaced by `condition_passive` |
| <a id="neooriginsburn_at_health_threshold"></a>[`burn_at_health_threshold`](powers/retired.md#neooriginsburn_at_health_threshold) | Replaced by `condition_passive` |
| <a id="neooriginsdamage_in_biome"></a>[`damage_in_biome`](powers/retired.md#neooriginsdamage_in_biome) | Replaced by `condition_passive` |
| <a id="neooriginsdamage_in_daylight"></a>[`damage_in_daylight`](powers/retired.md#neooriginsdamage_in_daylight) | Replaced by `condition_passive` |
| <a id="neooriginsdamage_in_water"></a>[`damage_in_water`](powers/retired.md#neooriginsdamage_in_water) | Replaced by `condition_passive` |
| <a id="neooriginsefficient_repairs"></a>[`efficient_repairs`](powers/retired.md#neooriginsefficient_repairs) | Replaced by `action_on_event` |
| <a id="neooriginsfood_restriction"></a>[`food_restriction`](powers/retired.md#neooriginsfood_restriction) | Replaced by `action_on_event` |
| <a id="neooriginsglow"></a>[`glow`](powers/retired.md#neooriginsglow) | Replaced by `persistent_effect` |
| <a id="neooriginshealing_mist"></a>[`healing_mist`](powers/retired.md#neooriginshealing_mist) | Replaced by `active_ability` |
| <a id="neooriginshunger_drain_modifier"></a>[`hunger_drain_modifier`](powers/retired.md#neooriginshunger_drain_modifier) | Replaced by `action_on_event` |
| <a id="neooriginsknockback_modifier"></a>[`knockback_modifier`](powers/retired.md#neooriginsknockback_modifier) | Replaced by `action_on_event` |
| <a id="neooriginslonger_potions"></a>[`longer_potions`](powers/retired.md#neooriginslonger_potions) | Replaced by `action_on_event` |
| <a id="neooriginsmore_animal_loot"></a>[`more_animal_loot`](powers/retired.md#neooriginsmore_animal_loot) | Replaced by `action_on_event` |
| <a id="neooriginsnatural_regen_modifier"></a>[`natural_regen_modifier`](powers/retired.md#neooriginsnatural_regen_modifier) | Replaced by `action_on_event` |
| <a id="neooriginsnight_vision"></a><a id="player-toggle"></a>[`night_vision`](powers/retired.md#neooriginsnight_vision) | Replaced by `persistent_effect` (or `enhanced_vision` for a brightness floor) |
| <a id="neooriginsregen_in_fluid"></a>[`regen_in_fluid`](powers/retired.md#neooriginsregen_in_fluid) | Replaced by `condition_passive` |
| <a id="neooriginsrepulse"></a>[`repulse`](powers/retired.md#neooriginsrepulse) | Replaced by `active_ability` |
| <a id="neooriginsstacking_status_effects"></a>[`stacking_status_effects`](powers/retired.md#neooriginsstacking_status_effects) | Replaced by `persistent_effect` |
| <a id="neooriginsstatus_effect"></a>[`status_effect`](powers/retired.md#neooriginsstatus_effect) | Replaced by `persistent_effect` |
| <a id="neooriginsteleport_range_modifier"></a>[`teleport_range_modifier`](powers/retired.md#neooriginsteleport_range_modifier) | Replaced by `action_on_event` |
| <a id="neooriginsthorns_aura"></a>[`thorns_aura`](powers/retired.md#neooriginsthorns_aura) | Replaced by `action_on_event` |

---

## Moved from this page

These sections now live on other pages.

<div id="no-anchor-stub"></div>

- <a id="toggle-upkeep"></a>[Toggle upkeep](concepts/costs-and-upkeep.md#toggle-upkeep)
- <a id="pack-author-patterns"></a>[Pack-author patterns](COOKBOOK.md)
- <a id="periodic-feed--heal-via-neooriginsaction_over_time"></a>[Periodic feed / heal via `neoorigins:action_over_time`](COOKBOOK.md#periodic-feed--heal-via-neooriginsaction_over_time)
- <a id="composing-power-sets"></a>[Composing power sets](COOKBOOK.md#composing-power-sets)
- <a id="kubejs-bridge-powers"></a>[KubeJS bridge powers](powers/integration.md#kubejs-bridge-powers)
