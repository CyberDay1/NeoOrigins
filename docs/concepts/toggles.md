---
title: Toggles
parent: Concepts
nav_order: 3
permalink: /concepts/toggles
---

# Toggles

Two different things are called a toggle:

- A **toggleable power** is a power the player switches on and off with a key, such as `flight` or `stealth`. It holds a skill key, can show an icon on the HUD, and can charge an [upkeep](costs-and-upkeep.md#toggle-upkeep) while it is on.
- A **[`neoorigins:toggle`](../powers/state.md#neooriginstoggle)** is a named true/false flag with no key of its own. Other powers read it with the [`power_active`](../conditions/entity.md#neooriginspower_active) condition and flip it with the [`neoorigins:toggle`](../actions/targeted.md#neooriginstoggle) action. [Make a toggle](../guides/toggles.md) builds one step by step.

## Toggleable powers

<!-- shared-fields: cooldown_icon, always_show_icon -->
**Toggleable powers** (`flight`, `creative_flight`, `item_magnetism`, `no_mob_spawns_nearby`, `phantom_form`, `stealth`, `wraith_phase`, plus `persistent_effect`, `condition_passive` and `effect_over_time` when authored with `"toggleable": true` — `effect_over_time` is also toggleable under `"activation": "active"` — and `pose`, which is toggleable unless you set `"toggleable": false`) also accept `cooldown_icon` and `always_show_icon`. A toggle with an icon joins the HUD cluster: full-bright while toggled on, dimmed while off (no cooldown sweep).

## Chat messages

<!-- shared-fields: toggle_messages -->
**Every toggle type** (`flight`, `creative_flight`, `item_magnetism`, `no_mob_spawns_nearby`, `phantom_form`, `stealth`, `wraith_phase`, `pose`, `persistent_effect`, `condition_passive` and `effect_over_time`) prints a green "on" or red "off" line in chat when its key flips it, and the same red line when an [upkeep](costs-and-upkeep.md#toggle-upkeep) it can't pay switches it off. Set `toggle_messages` to `false` to keep that toggle silent: the key still works and the HUD icon still brightens and dims, only the chat line is dropped.

| Field | Type | Default | Description |
|---|---|---|---|
| `toggle_messages` | bool | `true` | `false` sends no chat message when the toggle switches on or off, by keypress or by a missed upkeep charge. |

```json
{
  "type": "neoorigins:stealth",
  "toggle_messages": false
}
```

## Toggles and keys at login

**A toggle no key can reach comes back on at login.** Toggle state is saved with the player, so a toggle switched off stays off across a relog, which is only safe while some key can switch it back. Outside the class layer the six skill keys go to the player's first six active powers; the class layer has the single Class Skill key, held by its first active power (see [CLASSES.md](../CLASSES.md)); and a power with its own `key` binds to that instead (see [Keybinds](keybinds.md)). A toggleable power left over after those slots is still granted, but the player has no way to flip it, so at every login NeoOrigins clears its off state and it runs as switched on. That includes a toggle authored with `default_off`, and an `effect_over_time` in `"activation": "active"`, which would otherwise start off. There are two exceptions. A player under the Suppression effect, which switches toggles off on purpose, keeps the reset waiting for a login without it. And a toggle that charges an [upkeep](costs-and-upkeep.md#toggle-upkeep) is never switched on at login, since the player would have no key to stop the cost: it keeps whatever state it had, and if it is on, it pays like any other toggle until it can't, then switches itself off and stays off. That rules out an on/off loop on every relog.

Under the Suppression effect no toggle can be switched on, and one that is already on is forced off: see [Suppression](built-in-mechanics.md#suppression).
