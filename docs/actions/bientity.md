---
title: "Caster & target actions"
parent: "Entity Actions"
grand_parent: "DSL Reference"
nav_order: 3
permalink: /actions/bientity
---

# Caster & target actions

Actions with two parties: the caster (actor) and the target.

Some call sites dispatch a **bientity action**: an action that runs against a *pair* of entities rather than one. The pair is always **(actor, target)**:

- **actor**: the caster (the player who owns the power). Always a player.
- **target**: the other entity in the interaction (the mob or player you hit, were hit by, or interacted with). May be a player **or** a non-player mob.

Bientity actions are how a single power can affect *both* sides of an interaction. The call sites that supply a bientity pair are:

- `action_on_hit.bientity_action`: actor = the attacker (you), target = the entity you hit
- `action_on_hit_taken.bientity_action`: actor = you, target = the entity that hit you
- `action_when_hit` / projectile on-hit (`action_on_hit` on a thrown/launched entity): actor = the launcher, target = the entity struck
- entity-interact powers: actor = you, target = the entity you interacted with

Both the caster and the target are available at the same time. To route an effect to one side or the other, wrap it in `actor_action` or `target_action`.

**The namespace matters in a bientity slot.** The pair forms below (`actor_action`, `target_action`, `and`, `chance`, `invert`, `if_else`, `damage`, `mount`) are selected only by an `origins:`, `apoli:` or `apace:` prefix (`nothing` by `origins:` / `apoli:`). Any other type there, including a `neoorigins:` one or a bare name, is parsed as an ordinary entity action and run on the **actor**, with the target published to the dispatch context. Context-reading verbs still reach the target that way (`neoorigins:target_action`, `neoorigins:actor_action`, the set verbs, `shear`, …), but `neoorigins:damage` hurts the actor rather than the target, `neoorigins:invert` does nothing, and `neoorigins:if_else` tests its condition against the actor. The inner `action` of `actor_action` / `target_action` is an ordinary entity action from this reference.

## `actor_action`

Runs the inner entity-action against the **caster** (the power holder). While it runs, the target is published to the dispatch context, so context-reading verbs (e.g. set verbs, `damage_target`-style sub-actions) can resolve the hit entity.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `action` | entity action | yes | noop | Action run against the actor (caster) |

**Example: heal yourself when you land a hit**
```json
{ "type": "apoli:actor_action", "action": { "type": "neoorigins:heal", "amount": 4.0 } }
```

## `target_action`

Runs the inner entity-action against the **target**: the entity on the other side of the interaction, resolved from the active dispatch context (the entity you hit / were hit by / killed / interacted with, or the entity a projectile struck). If no target resolves, the action is a no-op.

- When the target is a **player**, the full entity-action surface runs on it (PvP-style scenarios). The actor is published to the dispatch context while it runs.
- When the target is a **non-player mob**, the entity-general verbs run directly on the mob: `apply_effect`, `clear_effect`, `damage`, `heal`, `set_on_fire`, `extinguish`, `add_velocity`, `play_sound`, `spawn_particles`, `set_fall_distance`, `dismount`, `swing_hand`, `shear`, `dye`, `force_drop`, `steal_item`, `execute_command`, `nothing`, and an `and` made only of these. On a mob target `damage` reads `amount` and `source.name` only: `damage_type` is ignored, and an unnamed source is attributed to the actor. It also honours `weapon_damage_scale` (that fraction of the actor's attack-damage attribute is added). `add_velocity` ignores `space` on a mob. `execute_command` runs as the mob, so `@s` is the mob rather than the holder: output suppressed, permission level 2, and the same legacy-command rewrite, blocked-command list and unparseable-command logging as every other command site. Verbs that depend on player-only systems (powers, resources, XP, food, inventory) only apply when the target is a player.

This verb also works directly as a `neoorigins:`-namespaced entity-action (e.g. inside a projectile `on_hit_action`), where it reads the same context target rather than being a transparent pass-through to the holder.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `action` | entity action | yes | noop | Action run against the target entity. Object only |

**Example: set the entity you hit on fire and poison it (works on mobs and players alike)**
```json
{ "type": "apoli:target_action", "action": { "type": "neoorigins:and", "actions": [
  { "type": "neoorigins:set_on_fire", "ticks": 60 },
  { "type": "neoorigins:apply_effect", "effect": "minecraft:poison", "duration": 100 }
] } }
```

## `invert`

Swaps actor and target, then runs the inner bientity action against the swapped pair. Because the actor slot must be a player, the swap only takes effect when the original target is itself a player. The `neoorigins:invert` entity action is a no-op.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `action` | bientity action | no | noop | Action run with actor/target swapped |

## `if_else` (bientity)

Branches on a **bientity condition** and runs one of two bientity actions against the same pair. This is the pair-level twin of the entity-action [`if_else`](meta.md#neooriginsif_else); the difference that matters is the condition, which is read as a bientity condition (`target_condition`, `actor_condition`, `can_see`, `constant`, and the combinators) rather than as a holder-only condition.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `condition` | bientity condition | no | false | Gate evaluated as (actor, target) |
| `if_action` | bientity action | no | noop | Run when the condition is true |
| `else_action` | bientity action | no | noop | Run when it is false |

A condition that cannot be compiled as a bientity condition is reported in the compat summary and evaluates false, so the `else_action` branch runs.

**Example: mark the entity you hit, but only once**
```json
{ "type": "apoli:if_else",
  "condition": { "type": "apoli:target_condition",
                 "condition": { "type": "apoli:nbt", "nbt": "{Tags:[\"marked\"]}" } },
  "if_action": { "type": "apoli:target_action", "action": { "type": "neoorigins:damage", "amount": 4.0 } },
  "else_action": { "type": "apoli:target_action", "action": { "type": "neoorigins:execute_command", "command": "tag @s add marked" } } }
```

## `riding_action`

Runs the inner action against the entity the holder is **riding** (its vehicle), but only when that vehicle is a player. If the holder isn't riding a player, it no-ops.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `action` / `entity_action` | entity action | no | noop | Action run on the ridden entity (`entity_action` is an accepted alias) |

**Example:**
```json
{ "type": "neoorigins:riding_action", "action": { "type": "neoorigins:heal", "amount": 2.0 } }
```

## `passenger_action`

Runs the inner action against every **passenger** of the holder. Only `ServerPlayer` passengers are affected; non-player passengers are skipped.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `action` / `entity_action` | entity action | no | noop | Action run on each player passenger (`entity_action` is an accepted alias) |

**Example:**
```json
{ "type": "neoorigins:passenger_action", "action": { "type": "neoorigins:apply_effect", "effect": "minecraft:speed", "duration": 100 } }
```

## `selector_action`

Resolves a vanilla entity selector relative to the holder's command source, then runs `bientity_action` once per selected entity. Each selected entity is published as the action's source (its origin + rotation), so a nested `spawn_projectile` / `fire_projectile` fires **from** that entity. `sort` / `limit` / `tag` predicates inside the selector string are honoured by vanilla's parser.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `selector` | string | yes | — | Vanilla entity selector (e.g. `@e[type=area_effect_cloud,tag=foo,limit=2,sort=nearest]`) |
| `bientity_action` / `entity_action` | action | yes | — | Action run once per selected entity (`entity_action` is an accepted alias) |

**Example:**
```json
{ "type": "neoorigins:selector_action",
  "selector": "@e[type=minecraft:area_effect_cloud,tag=volley,limit=4]",
  "bientity_action": { "type": "neoorigins:fire_projectile", "entity_type": "minecraft:arrow", "speed": 2.0 } }
```

## `and` / `chance` (bientity form)

`and` runs a list of bientity actions in order against the same (actor, target) pair; `chance` runs its `action` with probability `chance` (default `0.5`, actor's RNG) and its optional `fail_action` otherwise. Both mirror their entity-action counterparts but operate on the pair.

```json
{ "type": "apoli:and", "actions": [
  { "type": "apoli:actor_action",  "action": { "type": "neoorigins:heal", "amount": 2.0 } },
  { "type": "apoli:target_action", "action": { "type": "neoorigins:damage", "amount": 4.0 } }
] }
```

## `mount` (bientity form)

The actor starts riding the target. No fields. Distinct from the entity action [`neoorigins:mount`](effects.md#neooriginsmount), which mounts the nearest entity.

## `damage` (bientity form)

Directly damages the target, attributed to the actor. Supports `amount` and an optional `damage_type` (defaults to `minecraft:generic`).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `amount` | float | no | `1.0` | Damage in half-hearts |
| `damage_type` | resource id | no | `minecraft:generic` | Registered damage type; falls back to a player-attack source if unresolved |
