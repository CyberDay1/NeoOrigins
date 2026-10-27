---
title: Cooldowns & HUD
parent: Concepts
nav_order: 5
permalink: /concepts/cooldowns-and-hud
---

# Cooldowns & HUD

Every keybind ability keeps its own cooldown, set by its type's cooldown field (`cooldown_ticks` on [`active_ability`](../powers/active.md#neooriginsactive_ability)); using one ability never blocks another. `active_ability` can also read its cooldown length from a counter with `cooldown_resource`, and any action can start a cooldown with [`neoorigins:trigger_cooldown`](../actions/effects.md#neooriginstrigger_cooldown).

## Cooldown icons

<!-- shared-fields: cooldown_icon, cooldown_countdown, always_show_icon -->
**Cooldown HUD fields (active powers).** Every cooldown-gated active (keybind) power type (`active_ability`, `active_teleport`, `active_dash`, `active_recall`, `personal_dimension`, `active_swap`, `active_fireball`, `active_bolt`, `active_phase`, `active_place_block`, `ground_slam`, `tidal_wave`, `command_pack`, `elytra_boost`, `mount`, `shadow_orb`, `summon_minion`, `tame_mob`, `js_active`, `loot_pool_grant`) additionally accepts three optional HUD fields:

> **1.21.1 vs 26.x:** 26.2 and 26.3 have no `js_active` at all, since KubeJS has no build for either.

| Field | Type | Default | Description |
|---|---|---|---|
| `cooldown_icon` | string | `""` | HUD cooldown icon: an item id (e.g. `minecraft:ender_pearl`) rendered as the item, or a datapack texture path ending in `.png` (resolved under `assets/<namespace>/textures/`, e.g. `mypack:gui/fireball.png` → `assets/mypack/textures/gui/fireball.png`) drawn 16×16. When set, the HUD swaps that slot's cooldown bar for the icon with a clock-style radial sweep (dark fill over the not-yet-recharged arc, wiping clockwise from 12 o'clock). Empty keeps the plain bar. |
| `cooldown_countdown` | bool | `true` | Draw the remaining cooldown in whole seconds translucently on the icon. Only applies when `cooldown_icon` is set; players can suppress all countdown numbers with the `show_cooldown_countdown` client config switch and tune the text opacity with `cooldown_countdown_opacity`. |
| `always_show_icon` | bool | `false` | Keep this power's icon on the ability HUD cluster even while it is idle / off cooldown (full-bright, no sweep, no countdown). Only applies when `cooldown_icon` is set. **Note:** this is what makes an idle icon persist under the default client HUD mode (`hud_ability_display: COOLDOWNS_AND_TOGGLES`), which otherwise shows a non-toggle icon only while it is recharging. Under `ALL_ACTIVE_ABILITIES` every icon-bearing ability already keeps a slot, so the field has no additional effect there (players can also force all icons on with `always_show_ability_icons`). |

Icon slots are labeled with the bound key's short name in the top-right corner; hovering an icon while a screen is open (chat, the HUD editor) shows the power's name and description. The `hud_ability_display` client config picks what the cluster shows besides live cooldowns: `COOLDOWNS_AND_TOGGLES` (default since 2.2.9: cooldown slots only while recharging, plus icon-bearing toggles) or `ALL_ACTIVE_ABILITIES` (every icon-bearing keybind ability keeps a persistent slot, full-bright while idle, sweep while recharging).

The cooldown cluster itself is draggable in the in-game HUD editor (same screen as resource bars); its position persists in `config/neoorigins/hud.json`.

The client-side switches mentioned above (`show_cooldown_countdown`, `cooldown_countdown_opacity`, `hud_ability_display`, `always_show_ability_icons`) live in `config/neoorigins/client.toml`. See [CLIENT_CONFIG.md](../CLIENT_CONFIG.md) for the full list of per-client options.
