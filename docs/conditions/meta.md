---
title: "Meta conditions"
parent: "Conditions"
grand_parent: "DSL Reference"
nav_order: 1
permalink: /conditions/meta
---

# Meta conditions

Combinators and constants: `and`, `or`, `not`, `actor_condition` and `constant`.

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

## `neoorigins:constant`

Always-true or always-false literal.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `value` | boolean | no | `false` | Literal result. Missing field treated as `false`. |
