---
title: Power basics
parent: Concepts
nav_order: 1
permalink: /concepts/power-basics
---

# Power basics

A power file is one JSON object with a `type` (see [Power Types](../POWER_TYPES.md)) plus that type's own fields. The fields on this page work on every type.

All powers share six optional top-level fields:

| Field | Type | Description |
|---|---|---|
| `name` | string or `{"text":"..."}` / `{"translate":"..."}` | Display name shown in the origin selection screen. A plain string is treated as a translation key. |
| `description` | string or `{"text":"..."}` / `{"translate":"..."}` | Description shown below the power name. Same resolution rules as `name`. |
| `hidden` | bool (default `false`) | When `true`, this power is excluded from the origin info panel. The mechanical effect still applies; only the display row is suppressed. Useful for purely-internal flag/glue powers (e.g. `neoorigins:toggle`, on-hit setters wired under a `multiple`). |
| `required_mods` | list of mod ids (default `[]`) | Load gate: the power only loads when every listed mod is present. Use it for content that targets an optional mod (e.g. the built-in Dragon Survival dragon forms carry `"required_mods": ["dragonsurvival"]`). Origins accept the same field; see [PACK_FORMAT.md](../PACK_FORMAT.md). |
| `power_condition` | entity condition object | Runtime gate: the condition is re-evaluated against the power's holder every time the power would act, and the power only operates when the gate is satisfied. Works on every power type. See [CONDITIONS.md](../CONDITIONS.md) for the condition format. |
| `power_condition_mode` | `"ALLOW"` or `"DENY"` (default `"DENY"`) | How `power_condition` gates: `ALLOW` = the power is active **while the condition is true**; `DENY` = the power is disabled while the condition is true. Case-insensitive. |

**`condition` as an alias.** On power types that don't have their own `condition` config field, a top-level `condition` is accepted as an alias for `power_condition` with mode `ALLOW` (so the power is active while the condition holds, the intuitive reading). Notes:

<!-- shared-fields: condition -->
- These types claim `condition` for their own config and are excluded from the alias: `model_color`, `attribute_modifier`, `action_on_event`, `modify_damage`, `active_ability`, `active_dash`, `persistent_effect`, `condition_passive`, `effect_over_time`, `creative_flight`, `restrict_items`, `prevent_death`, `conditional`, `particle`. On those, write `power_condition` for the whole-power gate.
- An explicit `power_condition_mode` is honored even when the gate comes in via the alias.
- If both `power_condition` and an aliased `condition` are present, `power_condition` wins and a warning is logged.
- Prefer `power_condition` in new packs; the alias exists so the common Apoli-style spelling doesn't get silently dropped.

<!-- shared-fields: enabled -->
**`enabled`: the per-power kill switch.** `condition_passive`, `creative_flight`, `model_color`, `persistent_effect`, `pose` and `water_breathing` accept an optional top-level `enabled` (bool, default `true`). When `false` the power stays attached to the origin but does nothing — no effects, no flight, no periodic action. It exists mainly so a server owner can drop one power out of an origin from `config/neoorigins/power_overrides.toml` without editing the datapack, but a datapack may write it directly.

**Action & condition fields take an object _or_ an array.** Wherever a field below holds an action (e.g. `entity_action`) or a condition (e.g. `condition`), you may pass either a single object or an array of them. Action arrays run in order (implicit `neoorigins:and`); condition arrays must all pass (implicit AND). An empty array no-ops for actions and is always-true for conditions. See [ACTIONS.md](../ACTIONS.md) and [CONDITIONS.md](../CONDITIONS.md).

If neither `name` nor `description` is present, NeoOrigins falls back to the lang key convention:
`power.<namespace>.<path>.name` / `power.<namespace>.<path>.description`

## Related

- [Costs & upkeep](costs-and-upkeep.md): which types charge hunger or a resource, and when.
- [Toggles](toggles.md) and [Keybinds](keybinds.md): how a power gets a key and an on/off state.
- [Cooldowns & HUD](cooldowns-and-hud.md): cooldown icons and the ability HUD.
- [Conditions](../CONDITIONS.md): the condition format used by `power_condition` and `condition`.
