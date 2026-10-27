---
title: Give an ability a cost
parent: Guides
nav_order: 4
permalink: /guides/costs
---

# Give an ability a cost

Three pieces: a bar to spend from, an ability that spends it, and
optionally a toggle that drains it while it is on.

## The bar

A `neoorigins:resource` shows on the HUD and refills on its
own:

```json
// data/mypack/origins/powers/mana.json
{
  "type": "neoorigins:resource",
  "name": "Mana",
  "min": 0, "max": 100, "start_value": 100,
  "regen_rate": 2, "regen_interval": 20,
  "hud_render": { "label": "Mana", "color": "#55AAFF" }
}
```

## An ability that spends it

`active_ability` reads `resource_cost` and
`resource_cost_amount`. With less than 25 mana the key does nothing, and
no cooldown starts:

```json
// data/mypack/origins/powers/surge.json
{
  "type": "neoorigins:active_ability",
  "name": "Surge",
  "cooldown_ticks": 40,
  "resource_cost": "mypack:mana",
  "resource_cost_amount": 25,
  "entity_action": { "type": "neoorigins:dash", "strength": 1.5 }
}
```

## Types that don't read `resource_cost`

Most other actives
(`active_teleport`, `active_phase`, `ground_slam`, ...) ignore it. Charge
those by hand: gate the power on a `neoorigins:resource` condition, and
spend from a `power_activated` listener, which only fires when the ability
actually went off:

```json
// data/mypack/origins/powers/blink.json
{
  "type": "neoorigins:active_teleport",
  "range": 24,
  "cooldown_ticks": 60,
  "power_condition": {
    "type": "neoorigins:resource",
    "resource": "mypack:mana",
    "comparison": ">=",
    "compare_to": 30
  },
  "power_condition_mode": "ALLOW"
}
```

```json
// data/mypack/origins/powers/blink_cost.json
{
  "type": "neoorigins:action_on_event",
  "event": "power_activated",
  "power": "mypack:blink",
  "hidden": true,
  "entity_action": {
    "type": "neoorigins:change_resource",
    "resource": "mypack:mana",
    "operation": "add",
    "change": -30
  }
}
```

Keep `compare_to` and `change` in step. `change_resource` clamps at the
bar's `min`, so a cost larger than the gate would empty the bar rather
than refuse the ability.

## A toggle that drains while it is on

Every toggle type takes the same
`hunger_cost` / `resource_cost` / `resource_cost_amount` keys as an
upkeep, charged every `upkeep_interval` ticks. When the bar can't cover a
charge, the toggle switches itself off:

```json
// data/mypack/origins/powers/levitate.json
{
  "type": "neoorigins:creative_flight",
  "resource_cost": "mypack:mana",
  "resource_cost_amount": 3,
  "upkeep_interval": 20,
  "upkeep_condition": { "type": "neoorigins:creative_flying" }
}
```

The `upkeep_condition` charges only while the player is actually flying.
Full rules, including how a toggle with no key behaves:
[Toggle upkeep](../concepts/costs-and-upkeep.md#toggle-upkeep).

## An aura that drains while it runs

[`effect_over_time`](../powers/core.md#neooriginseffect_over_time) charges its upkeep once per pulse, in every mode, rather than on an `upkeep_interval`. An active (keybind) aura that can't pay switches itself off; a plain passive aura skips that pulse and tries again on the next one. This passive healing field costs 1 food point (saturation first) every 2 seconds, and stops healing once food and saturation are both empty:

```json
// data/mypack/origins/powers/mending_field.json
{
  "type": "neoorigins:effect_over_time",
  "activation": "passive",
  "interval": 40,
  "hunger_cost": 1,
  "entity_action": { "type": "neoorigins:heal", "amount": 1 }
}
```

The full rules (all-or-nothing charges, `upkeep_condition`, keyless toggles) are in [Costs & upkeep](../concepts/costs-and-upkeep.md).

## Which pattern to use

| You want | Use |
|---|---|
| A one-off cost per key press | `resource_cost` / `hunger_cost` on [`active_ability`](../powers/active.md#neooriginsactive_ability) |
| A cost on a type that has no cost keys | The resource condition plus `change_resource` pattern above |
| A cost while a toggle stays on | The [toggle upkeep](../concepts/costs-and-upkeep.md#toggle-upkeep) keys |
| A cost per aura pulse | The cost keys on `effect_over_time` |
