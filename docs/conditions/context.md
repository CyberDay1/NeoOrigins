---
title: "Context, bientity & damage conditions"
parent: "Conditions"
grand_parent: "DSL Reference"
nav_order: 3
permalink: /conditions/context
---

# Context, bientity & damage conditions

Conditions that need a dispatch context: the event in progress, a second entity, or a damage source. Outside that context they return false.

These read from `ActionContextHolder` populated during an action dispatch. They fail closed outside a matching context.

## `neoorigins:hit_taken_amount`

Numeric comparison against the hit damage currently being taken. Requires an active `HitTakenContext`. Used by `action_on_hit_taken` to re-express `min_damage`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | number | no | `0.0` | Damage threshold |

## `neoorigins:hit_dealt_amount`

Numeric comparison against the most recent damage the player **dealt** to a target. Requires an active `HitDealtContext`, the attacker-side mirror of `hit_taken_amount`, populated when the player damages a living entity (player-vs-mob included). Hook it from an `action_on_event` power with `"event": "hit_dealt"`. Inside that dispatch a bientity `target_action` resolves the victim that was struck.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | number | no | `0.0` | Dealt-damage threshold |

## `neoorigins:food_item_in_tag`

True if the item being eaten in the current `FOOD_EATEN` dispatch matches a tag or item ID. Requires an active `FoodContext`. Used by `food_restriction` aliases.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `tag` | string | no | — | Item tag (prefix with `#`, e.g. `"#minecraft:meat"`) or bare item ID (e.g. `"minecraft:spider_eye"`). Always-false when absent. |

**Tag vs item:** a leading `#` denotes a tag lookup; without `#` the value is matched as an exact item ID.

### Built-in diet tags

NeoOrigins ships these item tags for its diet-restricted origins, under `data/neoorigins/tags/item/`. All are declared `"replace": false`, so a datapack can append to any of them without redefining the list. Cross-mod entries are marked `required: false`, so they no-op when the other mod is absent.

| Tag | Diet / origin | Contents |
|---|---|---|
| `#neoorigins:fish_foods` | Pescivore (ocean/aquatic origins) | cod, salmon, tropical fish, pufferfish, cooked cod, cooked salmon; plus optional `#c:foods/raw_fish`, `#c:foods/cooked_fish`, Aquaculture fish fillet + sushi, and the fish of mods that skip the conventional tags: Alex's Mobs raw/cooked catfish, blobfish, flying fish and cosmic cod, and Ocean's Delight fugu slice |
| `#neoorigins:meat_foods` | Carnivore and vegetarian (`origins:carnivore` / `origins:vegetarian`, read inverted) | beef, porkchop, mutton, chicken, rabbit (raw + cooked each), rabbit stew, rotten flesh; plus optional `#c:foods/raw_meat`, `#c:foods/cooked_meat`, Aquaculture turtle soup, and **all of `#neoorigins:fish_foods`** |
| `#neoorigins:vampire_foods` | Blood diet (Vampire) | beef, porkchop, chicken, mutton, rabbit, rotten flesh, spider eye |
| `#neoorigins:skeleton_foods` | Boneless diet (Skeleton, base) | bone meal, rotten flesh, spider eye |
| `#neoorigins:skeleton_evolved_foods` | Boneless diet (Skeleton, evolved) | skeleton base list + all cooked meats and cooked fish (beef, porkchop, chicken, mutton, rabbit, cod, salmon) |

The fish diet additionally reads a server config allowlist: the `neoorigins:aquatic_fish_diet` power accepts either the `#neoorigins:fish_foods` tag **or** any entry in `ocean_origins.extra_fish_foods` (see `food_item_in_config_list` below), and only enforces the restriction when the `ocean_origins.fish_diet_required` flag is on.

Fish counts as meat: `meat_foods` nests `fish_foods` (as `required: false`, so a broken fish tag cannot take the meat tag down with it). A carnivore may therefore eat any fish, and a vegetarian none. Two consequences for pack authors: a datapack that replaces `fish_foods` changes the carnivore and vegetarian diets too, and the `ocean_origins.extra_fish_foods` list counts as meat too, so a modded fish whitelisted there feeds a carnivore without a datapack edit. The other `ocean_origins.*` options reach the ocean diet only.

## `neoorigins:food_item_id`

True if the item being eaten in the current `FOOD_EATEN` dispatch is an exact item-ID match. Requires an active `FoodContext`; returns `false` outside that context. Sibling of `food_item_in_tag`. Use this when you need per-item bonuses (e.g. raw cod gets one bonus, cooked cod gets a different one).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `id` | resource location | yes | — | Exact item ID to match (e.g. `"minecraft:cod"`). Unknown IDs fail closed. |

**Example: bonus saturation when eating raw cod**
```json
{
  "condition": { "type": "neoorigins:food_item_id", "id": "minecraft:cod" },
  "action": { "type": "neoorigins:modify_food", "food": 3, "saturation": 5.6 }
}
```

**`food_item_id` vs `food_item_in_tag`:** `food_item_id` matches a single exact item. `food_item_in_tag` matches by tag (or bare ID), so it can cover many items in one rule. Use `food_item_id` when you need different behavior per individual food item.

## `neoorigins:food_item_in_config_list`

True if the item being eaten in the current `FOOD_EATEN` dispatch matches any entry in a named server config list. Requires an active `FoodContext`; returns `false` outside that context, and `false` for an unknown key. Each list entry is either a bare item ID or a `#`-prefixed tag ref, matched the same way as `food_item_in_tag`.

This lets a server owner extend a diet without editing a datapack: the ocean-origin fish diet reads `ocean_origins.extra_fish_foods` so modded fish (Aquaculture, Hybrid Aquatic, etc.) can be whitelisted from config.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `key` | string | yes | — | Config list key to read (e.g. `"ocean_origins.extra_fish_foods"`). Unknown keys evaluate false. |

**Supported keys:**
- `"ocean_origins.extra_fish_foods"`: extra items/tags ocean origins may eat, additive to the `neoorigins:fish_foods` tag.

**Example: allow the eat when it is a fish tag OR a config-listed item**
```json
{
  "type": "neoorigins:any_of",
  "conditions": [
    { "type": "neoorigins:food_item_in_tag", "tag": "#neoorigins:fish_foods" },
    { "type": "neoorigins:food_item_in_config_list", "key": "ocean_origins.extra_fish_foods" }
  ]
}
```

---

# Bientity conditions

These evaluate against a pair: the entity under test (actor) plus a target pulled from the active dispatch context. They fail closed outside a bientity context.

**Target extraction rules** (from `ActionContextHolder.get()`):
- `HitTakenContext`: target = the damage source entity (if it's a `LivingEntity`)
- `HitDealtContext`: target = the entity the player damaged
- `KillContext`: target = the killed entity
- `EntityInteractContext`: target = the interacted entity
- `ProjectileHitContext`: target = the entity hit (if any)
- otherwise: null, condition returns false

**Apoli `bientity_condition` fields.** Where a field is typed *bientity condition* (`area_of_effect.bientity_condition`, `prevent_entity_use.bientity_condition`, `entity_glow.bientity_condition`, the bientity `if_else` gate, `nearby_entities.bientity_condition`), the pair is handed in directly rather than pulled from the dispatch context. Those slots accept `target_condition` and `actor_condition` (each wrapping a condition in `condition`, evaluated against that side of the pair), `can_see`, `distance` (actor to target, same fields as below), `constant`, and the combinators `and`/`all_of`, `or`/`any_of`, `not`; `inverted` is honoured on every node. Anything else cannot be compiled, and each call site documents what it does about that.

`actor_condition` takes any ordinary condition. The target may be any entity, so `target_condition` takes only the verbs that can read a non-player: `living`, `on_fire`/`fire`, `entity_type`/`target_type` (`entity_type` or `type_id`, an ID or a `#tag`), `in_tag` (an entity-type tag), `target_group`, `health`, `relative_health`, `has_effect`/`status_effect` (presence only; amplifier fields are ignored), `nbt`, `in_block_anywhere` (every block the target's hitbox overlaps, same fields as the entity condition), `constant`, and `and`/`all_of`, `or`/`any_of`, `not` over those. A target that is not a living entity never passes a `target_condition`.

## `neoorigins:distance`

Numeric comparison against 3D distance from actor to target. **Default comparison is `"<="`** (unlike most numeric conditions).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `"<="` | Comparison operator |
| `compare_to` | number | no | `0.0` | Distance in blocks |

## `neoorigins:can_see`

True when the actor has line-of-sight to the target. No fields.

## `neoorigins:equal`

True when target UUID == actor UUID. No fields.

## `neoorigins:target_type`

Checks target's entity-type ID or tag. **Unusual:** a leading `#` denotes a tag.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_type` | string | yes | — | e.g. `"minecraft:zombie"` or `"#minecraft:undead"`; always-false when blank |

## `neoorigins:target_group`

Checks whether the target's entity type is in a vanilla mob-category tag under the `minecraft:` namespace.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `group` | string | yes | — | Tag path under `minecraft:` (e.g. `"raiders"`); always-false when blank |

## `neoorigins:in_set`

2.0 entity-set membership: true when the target's UUID is in the actor's named entity-set. Pack authors should namespace `set` keys to avoid collision.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `set` | string | yes | — | Entity-set key (e.g. `"mypack:kill_streak"`) |

## `neoorigins:no_minions_alive`

True when the player has zero living tracked minions of the given key.
Used for "loner" gates, e.g., the Monster Tamer's Lone Weakness applies
only when no tamed mobs are alive. Evaluates to true if the player has
never summoned a minion as well (count of 0).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `key` | string | no | `"tamer:tamed"` | MinionTracker key to count. Defaults to the `TameMobPower` key. Summoned-mob keys are the entity type string, e.g. `"minecraft:skeleton"` for `SummonMinionPower`. |

---

# Damage conditions

Read the `DamageSource` from `HitTakenContext`. Fail closed outside that context.

## `neoorigins:from_fire`

True when the damage source is in the `minecraft:is_fire` damage-type tag. No fields.

## `neoorigins:from_projectile`

True when the damage source is in the `minecraft:is_projectile` damage-type tag. No fields.

## `neoorigins:from_explosion`

True when the damage source is in the `minecraft:is_explosion` damage-type tag. No fields.

## `neoorigins:damage_type`

Matches the damage source against a specific damage-type resource key.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `damage_type` | resource location | yes | — | Damage-type ID; always-false when blank |

## `neoorigins:damage_tag`

Matches the damage source against a damage-type tag. Leading `#` is stripped if present.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `tag` | resource location | yes | — | Damage-type tag (optional `#` prefix); always-false when blank |

## `neoorigins:damage_name` (alias `neoorigins:name`)

Case-insensitive match against the damage source's message ID (e.g. `"lava"`, `"cactus"`).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `name` | string | yes | — | Damage message ID; always-false when blank |
