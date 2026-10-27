# NeoOrigins 2.0 Entity Action Reference

Entity actions run against an entity target (usually the player who owns the power, or a bientity target depending on the call site). They're the side-effect half of the DSL: conditions filter, actions mutate.

**Canonical namespace:** `neoorigins:*` is the preferred form for new packs. Any other namespace (`origins:`, `apoli:`, `apace:`, or another Apoli derivative's, such as `medievalorigins:`) is rewritten to `neoorigins:` with the same path and logs a one-shot `[2.0-legacy]` deprecation warning. Bare type names like `"type": "heal"` are auto-prefixed to `neoorigins:heal`. The one place the namespace changes behaviour is a bientity slot: see [Caster & target](actions/bientity.md).

**Call sites that dispatch actions:**
- `action_on_event.entity_action`: runs against the event's actor (player)
- `action_on_hit.entity_action` / `action_on_hit_taken.entity_action`: bientity (actor + target)
- `conditional.inner_action`: gated by the wrapping condition
- Nested inside meta verbs (`if_else`, `if_else_list`, `and`, `chance`, `delay`, `area_of_effect`)

**Object or array.** The action fields of the native powers (`entity_action`, `else_action`, `fail_action`) and most nested action fields of the verbs below accept either a single action object **or** an array of them, run in order (an implicit `neoorigins:and`): `if_else`, `if_else_list`, `chance`, `delay`, `choice`, `offset`, `block_action_at`, `actor_action`, `riding_action`, `passenger_action`, `selector_action`, `area_of_effect` (`entity_action` / `bientity_action` / `block_action`), `raycast` (`block_action` / `bientity_action` / `miss_action` / `before_action`), `spawn_lingering_area`, the `impact_action` of `spawn_tornado` and `spawn_projectile_rain`, and `spawn_telegraph`'s `on_expire`. An empty array, or entries that aren't objects, no-op. `and`'s own `actions` also accepts a lone object.

These stay **object-only**, and an array there silently does nothing: `target_action`'s `action`, `block_target_action`'s `action`, `spawn_projectile`'s `on_hit_action` and `projectile_action`, the fields of the Apoli-prefixed bientity forms (`actor_action`, `target_action`, `chance`, `invert`, `if_else`), and the item-action fields (`item_action` on `equipped_item_action` / `modify_inventory`, and the branches of `if_else (item)`). Wrap a sequence in `neoorigins:and` (or `neoorigins:and (item)`).

On any parse error or unknown `type`, the action degrades to a no-op. Outside a datapack load this logs a `[CompatB]` warning; during a load the per-action line drops to debug level and the verb is counted in the compat summary instead.

---

## Pages

- <a id="core-effect-verbs"></a>[Core effect verbs](actions/effects.md): Effects, healing and damage, movement, sounds, items and XP, spawning entities, projectiles and areas, commands, blocks, powers, resources and cooldowns.
- <a id="meta-verbs"></a>[Meta verbs](actions/meta.md): Verbs that combine, branch, randomise, delay or relocate other actions, including `area_of_effect`.
- <a id="caster--target-bientity-actions"></a>[Caster & target actions](actions/bientity.md): Actions with two parties: the caster (actor) and the target.
- <a id="set--capability-verbs-20"></a>[Targeted, block & integration verbs](actions/targeted.md): Entity sets and toggle flags, event cancelling, attacker reactions, teleports and swaps, item handling, block-target verbs, raycasts, spells and morph verbs.
- <a id="item-actions"></a>[Item actions](actions/item.md): Actions that run on an item stack.
- <a id="item-conditions"></a>[Item conditions](conditions/item.md): Conditions on an item stack, used by item filters such as `equipped_item`.

<div id="no-anchor-stub"></div>

## Every entry

| Entry | Page |
|---|---|
| <a id="neooriginsapply_effect"></a>[`neoorigins:apply_effect`](actions/effects.md#neooriginsapply_effect) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsclear_effect"></a>[`neoorigins:clear_effect`](actions/effects.md#neooriginsclear_effect) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsheal"></a>[`neoorigins:heal`](actions/effects.md#neooriginsheal) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsdamage"></a>[`neoorigins:damage`](actions/effects.md#neooriginsdamage) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsfeed"></a>[`neoorigins:feed`](actions/effects.md#neooriginsfeed) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsexhaust"></a>[`neoorigins:exhaust`](actions/effects.md#neooriginsexhaust) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsset_on_fire"></a>[`neoorigins:set_on_fire`](actions/effects.md#neooriginsset_on_fire) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsextinguish"></a>[`neoorigins:extinguish`](actions/effects.md#neooriginsextinguish) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsadd_velocity"></a>[`neoorigins:add_velocity`](actions/effects.md#neooriginsadd_velocity) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginslaunch"></a>[`neoorigins:launch`](actions/effects.md#neooriginslaunch) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsdismount"></a>[`neoorigins:dismount`](actions/effects.md#neooriginsdismount) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsmount"></a>[`neoorigins:mount`](actions/effects.md#neooriginsmount) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsset_fall_distance"></a>[`neoorigins:set_fall_distance`](actions/effects.md#neooriginsset_fall_distance) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsplay_sound"></a>[`neoorigins:play_sound`](actions/effects.md#neooriginsplay_sound) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsemit_game_event"></a>[`neoorigins:emit_game_event`](actions/effects.md#neooriginsemit_game_event) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsswing_hand"></a>[`neoorigins:swing_hand`](actions/effects.md#neooriginsswing_hand) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsgive"></a>[`neoorigins:give`](actions/effects.md#neooriginsgive) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsadd_xp"></a>[`neoorigins:add_xp`](actions/effects.md#neooriginsadd_xp) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsspawn_entity"></a>[`neoorigins:spawn_entity`](actions/effects.md#neooriginsspawn_entity) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsspawn_projectile"></a>[`neoorigins:spawn_projectile`](actions/effects.md#neooriginsspawn_projectile) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsfire_projectile"></a>[`neoorigins:fire_projectile`](actions/effects.md#neooriginsfire_projectile) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsspawn_effect_cloud"></a>[`neoorigins:spawn_effect_cloud`](actions/effects.md#neooriginsspawn_effect_cloud) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsspawn_particles"></a>[`neoorigins:spawn_particles`](actions/effects.md#neooriginsspawn_particles) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsspawn_lingering_area"></a>[`neoorigins:spawn_lingering_area`](actions/effects.md#neooriginsspawn_lingering_area) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsspawn_black_hole"></a>[`neoorigins:spawn_black_hole`](actions/effects.md#neooriginsspawn_black_hole) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsspawn_tornado"></a>[`neoorigins:spawn_tornado`](actions/effects.md#neooriginsspawn_tornado) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsspawn_projectile_rain"></a>[`neoorigins:spawn_projectile_rain`](actions/effects.md#neooriginsspawn_projectile_rain) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsspawn_sword_rain"></a>[`neoorigins:spawn_sword_rain`](actions/effects.md#neooriginsspawn_sword_rain) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsspawn_telegraph"></a>[`neoorigins:spawn_telegraph`](actions/effects.md#neooriginsspawn_telegraph) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsexecute_command"></a><a id="position-resolution"></a>[`neoorigins:execute_command`](actions/effects.md#neooriginsexecute_command) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginscommand"></a>[`neoorigins:command`](actions/effects.md#neooriginscommand) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsdrop_items"></a><a id="top-level-fields"></a><a id="per-entry-fields"></a><a id="modes"></a><a id="notes"></a>[`neoorigins:drop_items`](actions/effects.md#neooriginsdrop_items) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsset_block"></a>[`neoorigins:set_block`](actions/effects.md#neooriginsset_block) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsmodify_food"></a>[`neoorigins:modify_food`](actions/effects.md#neooriginsmodify_food) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsgrant_power"></a>[`neoorigins:grant_power`](actions/effects.md#neooriginsgrant_power) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsrevoke_power"></a>[`neoorigins:revoke_power`](actions/effects.md#neooriginsrevoke_power) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsactivate_power"></a>[`neoorigins:activate_power`](actions/effects.md#neooriginsactivate_power) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsopen_layer_picker"></a>[`neoorigins:open_layer_picker`](actions/effects.md#neooriginsopen_layer_picker) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsexplode"></a>[`neoorigins:explode`](actions/effects.md#neooriginsexplode) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsgain_air"></a>[`neoorigins:gain_air`](actions/effects.md#neooriginsgain_air) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginschange_resource"></a>[`neoorigins:change_resource`](actions/effects.md#neooriginschange_resource) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsmodify_resource"></a>[`neoorigins:modify_resource`](actions/effects.md#neooriginsmodify_resource) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsmodify_temperature"></a>[`neoorigins:modify_temperature`](actions/effects.md#neooriginsmodify_temperature) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsset_resource"></a>[`neoorigins:set_resource`](actions/effects.md#neooriginsset_resource) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginstrigger_cooldown"></a>[`neoorigins:trigger_cooldown`](actions/effects.md#neooriginstrigger_cooldown) | [Core effect verbs](actions/effects.md) |
| <a id="neooriginsnothing"></a>[`neoorigins:nothing`](actions/meta.md#neooriginsnothing) | [Meta verbs](actions/meta.md) |
| <a id="neooriginsand-alias-neooriginsall_of"></a>[`neoorigins:and` (alias `neoorigins:all_of`)](actions/meta.md#neooriginsand-alias-neooriginsall_of) | [Meta verbs](actions/meta.md) |
| <a id="neooriginsif_else"></a>[`neoorigins:if_else`](actions/meta.md#neooriginsif_else) | [Meta verbs](actions/meta.md) |
| <a id="neooriginsif_else_list"></a>[`neoorigins:if_else_list`](actions/meta.md#neooriginsif_else_list) | [Meta verbs](actions/meta.md) |
| <a id="neooriginschance"></a>[`neoorigins:chance`](actions/meta.md#neooriginschance) | [Meta verbs](actions/meta.md) |
| <a id="neooriginschoice"></a>[`neoorigins:choice`](actions/meta.md#neooriginschoice) | [Meta verbs](actions/meta.md) |
| <a id="neooriginsdelay"></a>[`neoorigins:delay`](actions/meta.md#neooriginsdelay) | [Meta verbs](actions/meta.md) |
| <a id="neooriginsoffset"></a>[`neoorigins:offset`](actions/meta.md#neooriginsoffset) | [Meta verbs](actions/meta.md) |
| <a id="neooriginsblock_action_at"></a>[`neoorigins:block_action_at`](actions/meta.md#neooriginsblock_action_at) | [Meta verbs](actions/meta.md) |
| <a id="neooriginsarea_of_effect"></a>[`neoorigins:area_of_effect`](actions/meta.md#neooriginsarea_of_effect) | [Meta verbs](actions/meta.md) |
| <a id="actor_action"></a>[`actor_action`](actions/bientity.md#actor_action) | [Caster & target actions](actions/bientity.md) |
| <a id="target_action"></a>[`target_action`](actions/bientity.md#target_action) | [Caster & target actions](actions/bientity.md) |
| <a id="invert"></a>[`invert`](actions/bientity.md#invert) | [Caster & target actions](actions/bientity.md) |
| <a id="if_else-bientity"></a>[`if_else` (bientity)](actions/bientity.md#if_else-bientity) | [Caster & target actions](actions/bientity.md) |
| <a id="riding_action"></a>[`riding_action`](actions/bientity.md#riding_action) | [Caster & target actions](actions/bientity.md) |
| <a id="passenger_action"></a>[`passenger_action`](actions/bientity.md#passenger_action) | [Caster & target actions](actions/bientity.md) |
| <a id="selector_action"></a>[`selector_action`](actions/bientity.md#selector_action) | [Caster & target actions](actions/bientity.md) |
| <a id="and--chance-bientity-form"></a>[`and` / `chance` (bientity form)](actions/bientity.md#and--chance-bientity-form) | [Caster & target actions](actions/bientity.md) |
| <a id="mount-bientity-form"></a>[`mount` (bientity form)](actions/bientity.md#mount-bientity-form) | [Caster & target actions](actions/bientity.md) |
| <a id="damage-bientity-form"></a>[`damage` (bientity form)](actions/bientity.md#damage-bientity-form) | [Caster & target actions](actions/bientity.md) |
| <a id="neooriginsadd_to_set"></a>[`neoorigins:add_to_set`](actions/targeted.md#neooriginsadd_to_set) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginsremove_from_set"></a>[`neoorigins:remove_from_set`](actions/targeted.md#neooriginsremove_from_set) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginstoggle"></a>[`neoorigins:toggle`](actions/targeted.md#neooriginstoggle) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginscancel_event"></a>[`neoorigins:cancel_event`](actions/targeted.md#neooriginscancel_event) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginsdamage_attacker"></a>[`neoorigins:damage_attacker`](actions/targeted.md#neooriginsdamage_attacker) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginsignite_attacker"></a>[`neoorigins:ignite_attacker`](actions/targeted.md#neooriginsignite_attacker) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginseffect_on_attacker"></a>[`neoorigins:effect_on_attacker`](actions/targeted.md#neooriginseffect_on_attacker) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginsrandom_teleport"></a>[`neoorigins:random_teleport`](actions/targeted.md#neooriginsrandom_teleport) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginschain_to_nearest"></a>[`neoorigins:chain_to_nearest`](actions/targeted.md#neooriginschain_to_nearest) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginspull_entities"></a>[`neoorigins:pull_entities`](actions/targeted.md#neooriginspull_entities) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginsthrow_target"></a>[`neoorigins:throw_target`](actions/targeted.md#neooriginsthrow_target) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginstame_target"></a>[`neoorigins:tame_target`](actions/targeted.md#neooriginstame_target) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginsdash"></a>[`neoorigins:dash`](actions/targeted.md#neooriginsdash) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginsswap_with_entity"></a>[`neoorigins:swap_with_entity`](actions/targeted.md#neooriginsswap_with_entity) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginsswap_positions"></a>[`neoorigins:swap_positions`](actions/targeted.md#neooriginsswap_positions) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginsteleport_to_target"></a>[`neoorigins:teleport_to_target`](actions/targeted.md#neooriginsteleport_to_target) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginsteleport_target_to_self"></a>[`neoorigins:teleport_target_to_self`](actions/targeted.md#neooriginsteleport_target_to_self) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginsshear"></a>[`neoorigins:shear`](actions/targeted.md#neooriginsshear) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginsdye"></a>[`neoorigins:dye`](actions/targeted.md#neooriginsdye) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginsforce_drop"></a>[`neoorigins:force_drop`](actions/targeted.md#neooriginsforce_drop) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginssteal_item"></a>[`neoorigins:steal_item`](actions/targeted.md#neooriginssteal_item) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginsdrop_inventory"></a>[`neoorigins:drop_inventory`](actions/targeted.md#neooriginsdrop_inventory) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="block-target-verbs"></a>[Block-target verbs](actions/targeted.md#block-target-verbs) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginsstrip"></a>[`neoorigins:strip`](actions/targeted.md#neooriginsstrip) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginstill"></a>[`neoorigins:till`](actions/targeted.md#neooriginstill) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginspath"></a>[`neoorigins:path`](actions/targeted.md#neooriginspath) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginsgrow-alias-neooriginsbonemeal"></a>[`neoorigins:grow` (alias `neoorigins:bonemeal`)](actions/targeted.md#neooriginsgrow-alias-neooriginsbonemeal) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginstransform_block"></a>[`neoorigins:transform_block`](actions/targeted.md#neooriginstransform_block) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginsblock_target_action"></a>[`neoorigins:block_target_action`](actions/targeted.md#neooriginsblock_target_action) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginsteleport_to_marker"></a>[`neoorigins:teleport_to_marker`](actions/targeted.md#neooriginsteleport_to_marker) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginsequipped_item_action"></a>[`neoorigins:equipped_item_action`](actions/targeted.md#neooriginsequipped_item_action) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginsmodify_inventory"></a>[`neoorigins:modify_inventory`](actions/targeted.md#neooriginsmodify_inventory) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginsraycast"></a>[`neoorigins:raycast`](actions/targeted.md#neooriginsraycast) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginscast_spell"></a><a id="component-order-is-effect-first"></a>[`neoorigins:cast_spell`](actions/targeted.md#neooriginscast_spell) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginscast_iron_spell"></a>[`neoorigins:cast_iron_spell`](actions/targeted.md#neooriginscast_iron_spell) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginscrafting_table"></a>[`neoorigins:crafting_table`](actions/targeted.md#neooriginscrafting_table) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginskubejs_callback"></a>[`neoorigins:kubejs_callback`](actions/targeted.md#neooriginskubejs_callback) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginstrigger_morph_animation"></a>[`neoorigins:trigger_morph_animation`](actions/targeted.md#neooriginstrigger_morph_animation) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginsmorph_entity_event"></a>[`neoorigins:morph_entity_event`](actions/targeted.md#neooriginsmorph_entity_event) | [Targeted, block & integration verbs](actions/targeted.md) |
| <a id="neooriginsmerge_nbt"></a>[`neoorigins:merge_nbt`](actions/item.md#neooriginsmerge_nbt) | [Item actions](actions/item.md) |
| <a id="neooriginsconsume"></a>[`neoorigins:consume`](actions/item.md#neooriginsconsume) | [Item actions](actions/item.md) |
| <a id="neooriginsdamage-1"></a>[`neoorigins:damage`](actions/item.md#neooriginsdamage) | [Item actions](actions/item.md) |
| <a id="neooriginsset_count"></a>[`neoorigins:set_count`](actions/item.md#neooriginsset_count) | [Item actions](actions/item.md) |
| <a id="neooriginsremove_enchantment"></a>[`neoorigins:remove_enchantment`](actions/item.md#neooriginsremove_enchantment) | [Item actions](actions/item.md) |
| <a id="neooriginsand-item"></a>[`neoorigins:and` (item)](actions/item.md#neooriginsand-item) | [Item actions](actions/item.md) |
| <a id="neooriginsif_else-item"></a>[`neoorigins:if_else` (item)](actions/item.md#neooriginsif_else-item) | [Item actions](actions/item.md) |
| <a id="neooriginsempty"></a>[`neoorigins:empty`](conditions/item.md#neooriginsempty) | [Item conditions](conditions/item.md) |
| <a id="neooriginsnbt"></a>[`neoorigins:nbt`](conditions/item.md#neooriginsnbt) | [Item conditions](conditions/item.md) |
| <a id="neooriginsenchantment"></a>[`neoorigins:enchantment`](conditions/item.md#neooriginsenchantment) | [Item conditions](conditions/item.md) |
| <a id="neooriginsingredient"></a>[`neoorigins:ingredient`](conditions/item.md#neooriginsingredient) | [Item conditions](conditions/item.md) |
| <a id="neooriginsamount"></a>[`neoorigins:amount`](conditions/item.md#neooriginsamount) | [Item conditions](conditions/item.md) |
| <a id="neooriginsname"></a>[`neoorigins:name`](conditions/item.md#neooriginsname) | [Item conditions](conditions/item.md) |
| <a id="neooriginsfood"></a>[`neoorigins:food`](conditions/item.md#neooriginsfood) | [Item conditions](conditions/item.md) |
| <a id="neooriginsmeat"></a>[`neoorigins:meat`](conditions/item.md#neooriginsmeat) | [Item conditions](conditions/item.md) |
| <a id="neooriginsarmor_value"></a>[`neoorigins:armor_value`](conditions/item.md#neooriginsarmor_value) | [Item conditions](conditions/item.md) |
| <a id="neooriginsharvest_level"></a>[`neoorigins:harvest_level`](conditions/item.md#neooriginsharvest_level) | [Item conditions](conditions/item.md) |
| <a id="neooriginsdurability"></a>[`neoorigins:durability`](conditions/item.md#neooriginsdurability) | [Item conditions](conditions/item.md) |
| <a id="neooriginsconstant-item"></a>[`neoorigins:constant` (item)](conditions/item.md#neooriginsconstant-item) | [Item conditions](conditions/item.md) |
| <a id="neooriginsand--neooriginsor--neooriginsnot-item"></a>[`neoorigins:and` / `neoorigins:or` / `neoorigins:not` (item)](conditions/item.md#neooriginsand--neooriginsor--neooriginsnot-item) | [Item conditions](conditions/item.md) |
