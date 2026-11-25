---
title: "Meta conditions"
parent: "Conditions"
grand_parent: "DSL Reference"
nav_order: 1
permalink: /conditions/meta
---

# Meta conditions

Combinators, wrappers and constants: `and`, `or`, `not`, `actor_condition`, `target_condition` and `constant`.

## `neoorigins:and` (alias `neoorigins:all_of`)

Logical AND of nested conditions. `all_of` is Apoli 2.9+'s rename of `and`: imported packs using `origins:all_of` / `apoli:all_of` dispatch here unchanged.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `conditions` | array of condition objects | no | `[]` | Inner conditions; all must pass. Absent or empty → true |

**Example:**
```json
{ "type": "neoorigins:and", "conditions": [ {"type": "neoorigins:sneaking"}, {"type": "neoorigins:on_ground"} ] }
```

## `neoorigins:or` (alias `neoorigins:any_of`)

Logical OR of nested conditions. `any_of` is Apoli 2.9+'s rename of `or`: imported packs using `origins:any_of` / `apoli:any_of` dispatch here unchanged.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `conditions` | array of condition objects | no | `[]` | Inner conditions; at least one must pass. Absent or empty → false |

## `neoorigins:not`

Negates a single inner condition.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `condition` | condition object | yes | — | Condition to negate |

## `neoorigins:actor_condition`

Unwraps and evaluates the inner condition against the actor. In Apoli this filters the actor half of a bientity pair; here the inner condition is simply evaluated on the entity under test (the player). Fails closed when `condition` is missing.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `condition` | condition object | yes | — | Inner condition evaluated on the actor |

**Example:**
```json
{ "type": "neoorigins:actor_condition", "condition": { "type": "neoorigins:sneaking" } }
```

## `neoorigins:target_condition`

Evaluates the inner condition against the **target** of the event in progress instead of the entity under test. The target comes from the dispatch context, by the [target extraction rules](context.md#bientity-conditions): the entity you hit or that hit you, the entity you killed or interacted with, or the entity a projectile struck. With no target it is false, so `inverted: true` makes it true. Fails closed when `condition` is missing.

Use it where an action runs on the caster but should branch on the other entity: an `if_else` in `spawn_projectile`'s `on_hit_action`, a raycast `bientity_action`, or an `action_on_event` hit, kill or interact event. In a [bientity condition](context.md#bientity-conditions) slot it is the target half of the pair.

- On a **player** target, any condition works and is read on that player.
- On a **mob** target, only the [entity-general verbs](context.md#bientity-conditions) can read it. Any other inner condition is false for a mob, with a warning at load.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `condition` | condition object | yes | — | Inner condition evaluated on the context target |

**Example: heal what the orb hits, unless it is undead**
```json
"on_hit_action": {
  "type": "neoorigins:if_else",
  "condition": {
    "type": "neoorigins:target_condition",
    "condition": { "type": "neoorigins:entity_type", "entity_type": "#minecraft:undead" }
  },
  "if_action": { "type": "neoorigins:target_action", "action": { "type": "neoorigins:damage", "amount": 4 } },
  "else_action": { "type": "neoorigins:target_action", "action": { "type": "neoorigins:heal", "amount": 4 } }
}
```

## `neoorigins:constant`

Always-true or always-false literal.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `value` | boolean | no | `false` | Literal result. Missing field treated as `false`. |
