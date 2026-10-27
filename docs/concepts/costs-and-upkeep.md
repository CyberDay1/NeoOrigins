---
title: Costs & upkeep
parent: Concepts
nav_order: 2
permalink: /concepts/costs-and-upkeep
---

# Costs & upkeep

A power can charge the player in two ways:

- **Per use:** paid once each time a keybind ability fires.
- **Upkeep:** paid on a beat while a power stays on.

Both use the same keys: `hunger_cost` (food points; one shank is 2), `resource_cost` (the id of a [`neoorigins:resource`](../powers/state.md#neooriginsresource) power) and `resource_cost_amount`. For a worked walkthrough, see [Give an ability a cost](../guides/costs.md).

## Who reads the cost keys

`active_ability` (plus the retired types that load as it: `active_launch`, `active_aoe_effect`, `healing_mist`, `repulse`) and `tame_mob` charge `hunger_cost` / `resource_cost` / `resource_cost_amount` once per use. Every toggle type and `effect_over_time` read the same keys as an upkeep, charged while the power is on: see [Toggle upkeep](#toggle-upkeep). `extra_inventory` charges them as an upkeep while its menu is open. Every other type, including `active_teleport`, `active_phase`, `active_place_block`, `ground_slam`, `tidal_wave`, `shadow_orb` and `summon_minion`, ignores both resource keys silently: the power loads and costs nothing from the resource. To charge one of those, gate it on a `neoorigins:resource` condition and spend with `neoorigins:change_resource` (see [Give an ability a cost](../guides/costs.md)).

## Per-use costs

`active_ability` checks the cost before it fires. If the player can't pay, the key does nothing and no cooldown starts; see the `hunger_cost` and `resource_cost_amount` rows in [`active_ability`](../powers/active.md#neooriginsactive_ability). `tame_mob` charges once per mob tamed, not once per activation; see [`tame_mob`](../powers/mobs.md#neooriginstame_mob).

## Upkeep while a power is on

<a id="toggle-upkeep"></a>
<!-- shared-fields: upkeep_interval -->
Every toggle type (`flight`, `creative_flight`, `item_magnetism`, `no_mob_spawns_nearby`, `phantom_form`, `stealth`, `wraith_phase`, `pose`, `persistent_effect` and `condition_passive`, plus the aliases that load as those last two) accepts an optional upkeep: a hunger and/or resource cost charged at intervals while the power is on. Every field is optional and the defaults are free, so a toggle without them behaves exactly as before. `extra_inventory` takes the same `hunger_cost`, `resource_cost`, `resource_cost_amount` and `upkeep_interval` (but not `upkeep_condition`) and charges them while its menu is open: see [its table](../powers/items.md#neooriginsextra_inventory).

| Field | Type | Default | Description |
|---|---|---|---|
| `hunger_cost` | int | `0` | Food points charged per charge. Saturation is spent first, as sprinting spends it. |
| `resource_cost` | string | `""` | Id of a `neoorigins:resource` power to drain. While resource bars are disabled server-wide (`disable_resource_bars`), the amount is charged as hunger instead. |
| `resource_cost_amount` | int | `0` | Amount of `resource_cost` drained per charge. |
| `upkeep_interval` | int | `20` | Ticks between charges, clamped to at least 1. |
| `upkeep_condition` | condition | always true | Charge only on charge ticks where this passes. A tick where it fails costs nothing, and the power keeps running. |

How the upkeep is charged:

- **On a fixed beat.** Charges fall on every `upkeep_interval`-th tick of the player's own tick counter while the power is on (and its `power_condition`, if any, holds). Switching a toggle on is free and the first charge comes on the next beat, so flipping it off and on again can never charge twice.
- **All or nothing.** If the player can't cover the whole charge, hunger and resource together, nothing is deducted.
- **A toggle that can't pay switches off,** with the same red message a keypress gives, and its effect is torn down as if the key had been pressed. Switch it back on once the bar has refilled; it shuts off again at the next charge it can't pay.
- **A power with no toggle lapses instead.** `condition_passive`, `persistent_effect` and `pose` can be authored with `"toggleable": false`; with nothing to switch off, a missed charge stops the effect (periodic actions are skipped, effects are cleared, the pose is released) until a later charge it can pay, or one where `upkeep_condition` fails. `wraith_phase` with `"always_on": true` ignores the upkeep fields.
- **`upkeep_condition` is not the power's own `condition`.** On `creative_flight`, `condition` decides whether the player may fly at all, and the upkeep is charged whenever the toggle is on, airborne or not. Set `"upkeep_condition": {"type": "neoorigins:creative_flying"}` to charge only while the player is actually flying (see the example under [`creative_flight`](../powers/movement.md#neooriginscreative_flight)).
- **Keyless toggles stay where they are.** A toggle with an upkeep is never switched back on at login: see [Toggles and keys at login](toggles.md#toggles-and-keys-at-login).

`effect_over_time` takes `hunger_cost`, `resource_cost`, `resource_cost_amount` and `upkeep_condition` too, but charges once per pulse rather than on an `upkeep_interval`: see [its table](../powers/core.md#neooriginseffect_over_time).

## Resource bars switched off

While `disable_resource_bars` is on in [`content.toml`](../CONTENT_CONFIG.md#global-toggles), every `resource_cost` is charged as hunger instead, per use and as upkeep alike.

## Types with no cost keys

A type that ignores the cost keys can still be charged by hand: gate it on a [`neoorigins:resource`](../conditions/entity.md#neooriginsresource) condition and spend from an [`action_on_event`](../powers/core.md#neooriginsaction_on_event) listening to `power_activated`. The full pattern is in [Give an ability a cost](../guides/costs.md#types-that-dont-read-resource_cost).
