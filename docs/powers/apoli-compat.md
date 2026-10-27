---
title: "Apoli compat types"
parent: "Power Types"
grand_parent: "DSL Reference"
nav_order: 12
permalink: /powers/apoli-compat
---

# Apoli compat types

`origins:` and `apugli:` types run by the Apoli compat layer, for packs written for the original Origins mod. Some have no native `neoorigins:` equivalent; each section says which to use.

Fields every power accepts (`name`, `hidden`, `power_condition`, ...) are on [Power basics](../concepts/power-basics.md). See also: [Mod Compatibility](../COMPATIBILITY.md), [Migration Guide](../MIGRATION.md).

Types on this page: [`origins:modify_xp_gain`](#originsmodify_xp_gain) · [`origins:modify_fall_damage`](#originsmodify_fall_damage) · [`origins:modify_healing`](#originsmodify_healing) · [`origins:modify_status_effect_duration`](#originsmodify_status_effect_duration) · [`origins:action_on_death`](#originsaction_on_death) · [`apugli:action_on_jump`](#apugliaction_on_jump) · [`apugli:action_on_target_death`](#apugliaction_on_target_death) · [`origins:cooldown`](#originscooldown).

---

## `origins:modify_xp_gain`

Multiplies experience points gained. Wired to `PlayerXpEvent.XpChange` via the `NumericModifierRegistry`. Uses Apoli modifier math (`addition` / `multiply_base` / `multiply_total`).

> **Use `origins:modify_xp_gain`, not `neoorigins:xp_gain_modifier`.** There is no native `neoorigins:` XP-gain type; the only working implementation is this Apoli compat (Route B) verb. A power whose `type` is unregistered is dropped entirely at load (its name and description vanish from the picker too), so the `neoorigins:xp_gain_modifier` name in older docs never worked.

Takes an Apoli **modifier** (singular `modifier` object or plural `modifiers` array). Each entry is `{ "operation": ..., "value": ... }` (`value` may also be written `amount`).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `modifier` / `modifiers` | object / array | no | — | Apoli modifier(s): `operation` (`addition`, `multiply_base`, `multiply_total`; the two multiply operations are identical, and any other string is treated as `addition`) + `value`. Not schema-required, but supply one: with neither key the loader logs `missing modifier/modifiers` and drops the power entirely. There is no `condition` gate on this type |

**Example: gain 50% more XP**
```json
{
  "type": "origins:modify_xp_gain",
  "modifier": { "operation": "multiply_base", "value": 0.5 },
  "name": "Quick Learner",
  "description": "Gains experience faster."
}
```

The modifier applies once, as written. Up to 2.2.27 it was added again on every respawn and every login, so the `+50%` above became `+100%` after one death and kept growing until `/reload` or a restart. If you lowered a value to compensate, put it back.

---

## `origins:modify_fall_damage`

Scales incoming fall damage, optionally gated by a `condition`. This is an Apoli compat (Route B) verb; there is no native `neoorigins:` fall-damage type. It reuses the existing native fall-damage seam: the loader registers a `mod_fall_damage` modifier handler (the same hook `neoorigins:action_on_event` with `event: mod_fall_damage` uses), which chains onto the `LivingFallEvent` damage multiplier in `MovementPowerEvents.onLivingFall`. Because it chains, it stacks with feather-falling and other fall-damage modifiers.

Takes an Apoli **modifier** (singular `modifier` object or plural `modifiers` array). Each entry is `{ "operation": ..., "value": ... }` (`value` may also be written `amount`). The operation+value is applied through the same modifier math as the other compat verbs: `multiply_base_additive` with `value: -0.5` → `damage * (1 + -0.5)` (halved); `addition`/`add_base` adds a flat amount to the multiplier; `set_total` with `value: 0` hard-zeroes the multiplier (no fall damage). A `condition` that targets `fall_damage` written as a `conditioned_attribute` is auto-routed here (vanilla has no fall-damage *attribute*, so it would otherwise be dropped).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `modifier` / `modifiers` | object / array | no | — | Apoli modifier(s): `operation` + `value` applied to the fall-damage multiplier. Not schema-required, but supply one: with neither key the loader logs `missing modifier/modifiers` and drops the power entirely |
| `condition` | object / array | no | always | Entity condition gating when the scale applies (e.g. only while sneaking) |

**Example: halved fall damage while sneaking**
```json
{
  "type": "origins:modify_fall_damage",
  "modifier": { "operation": "multiply_base_additive", "value": -0.5 },
  "condition": { "type": "origins:sneaking" },
  "name": "Soft Landing",
  "description": "Takes half fall damage while sneaking."
}
```

---

## `origins:modify_healing`

Scales health the holder regains. This is an Apoli compat (Route B) verb; there is no native `neoorigins:` healing-modifier type. It reuses the existing native seam: the loader registers a `mod_natural_regen` modifier handler, which `WorldPowerEvents.onLivingHeal` chains onto `LivingHealEvent`. That event fires for **all** healing, not just natural regeneration (potions, golden apples and `/heal` go through it too), which matches Apoli's contract for this type.

Takes an Apoli **modifier** (singular `modifier` object or plural `modifiers` array). This is a 1.16–1.18-vintage Origins type, so its packs use the attribute-style operation names: `addition` adds a flat amount, while `multiply_base` and `multiply_total` sum into `base + base * Σvalue`. A single `multiply_total` of `0.5` is therefore 1.5x healing, and `multiply_base` of `-0.5` is half healing.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `modifier` / `modifiers` | object / array | no | — | Apoli modifier(s): `operation` + `value` applied to the heal amount. Not schema-required, but supply one: with neither key the loader logs `missing 'modifier'/'modifiers'` and drops the power entirely |
| `condition` | object / array | no | always | Entity condition gating when the scale applies |

**Example: healing halved**
```json
{
  "type": "origins:modify_healing",
  "modifier": { "operation": "multiply_base", "value": -0.5 },
  "name": "Slower Regeneration",
  "description": "As a flower your healing capabilities are limited."
}
```

---

## `origins:modify_status_effect_duration`

Scales the duration of mob effects applied to the holder. Apoli compat (Route B), the duration-side sibling of `origins:modify_status_effect_amplifier`. The loader registers a `mod_potion_duration` modifier handler, which `CombatPowerEvents` chains onto `MobEffectEvent.Added`.

The seam collects a **multiplier**: its base is `1.0`, and the dispatch site applies the result to the effect instance's duration. So `multiply_total` with `value: 0.5` yields 1.5x and reads as "effects last 50% longer". Operation names are the same attribute-style set `modify_healing` uses. The scale applies to every effect added to the holder; there is no per-effect filter.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `modifier` / `modifiers` | object / array | no | — | Apoli modifier(s) applied to the duration multiplier. Not schema-required, but supply one: with neither key the loader logs `missing 'modifier'/'modifiers'` and drops the power entirely |
| `condition` | object / array | no | always | Entity condition gating when the scale applies |

**Example: beneficial effects last 50% longer**
```json
{
  "type": "origins:modify_status_effect_duration",
  "modifier": { "operation": "multiply_total", "value": 0.5 },
  "name": "Extended Potion Effects",
  "description": "Potion effects last longer for you."
}
```

---

## `origins:action_on_death`

Fires when the holder dies. Apoli compat (Route B), riding the native `death` event seam that `CombatPowerEvents.onLivingDeath` already dispatches.

`entity_action` runs on the dying holder. `bientity_action` runs with actor = the holder and target = the killer, Apoli's pairing for this type. The bientity half is skipped entirely when the death had no living attacker (fall damage, drowning, `/kill`) because there is no target to pair with, and running the action against the holder would invert its meaning.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_action` | object | no | — | Action run on the dying holder |
| `bientity_action` | object | no | — | Action run as (actor = holder, target = killer); skipped when there is no living killer |
| `condition` | object / array | no | always | Entity condition on the holder, tested at death time |

**Example: run a function as whoever killed you**
```json
{
  "type": "origins:action_on_death",
  "hidden": true,
  "bientity_action": {
    "type": "origins:target_action",
    "action": { "type": "origins:execute_command", "command": "function mypack:on_death" }
  }
}
```

---

## `apugli:action_on_jump`

Fires when the holder jumps. Apugli compat: the loader remaps it onto `neoorigins:action_on_event` with `event` pinned to `jump`, so the whole `jump` seam and every action in [ACTIONS.md](../ACTIONS.md) is available. Apugli was abandoned at 2.11.0+1.20.4, so this id only appears in packs shipped against it — new packs should write `action_on_event` directly.

Because `event` is pinned, the filter fields that only mean something for other events (`block_condition`, `hands`/`hand`, `item_condition`, `effect`, `effect_tag`, `power`, `immunity_ticks`) are not offered here, and neither is `modifier`: the jump seam dispatches the action path only, so a modifier chain would never be consulted.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_action` | EntityAction | no | noop | Action run on the holder when they jump |
| `condition` | EntityCondition | no | always-true | Gate: the jump only fires the action while this is true |
| `cooldown_ticks` | int ≥ 0 | no | `0` | After the action fires, suppress further firings for this many ticks (20 = 1s) |

**Example: cost a little hunger per jump**
```json
{
  "type": "apugli:action_on_jump",
  "entity_action": { "type": "origins:exhaust", "amount": 1.0 }
}
```

---

## `apugli:action_on_target_death`

Fires when the holder lands the killing blow on another entity. Apugli compat: remapped onto `neoorigins:action_on_event` with `event` pinned to `kill`. Same deprecation note as `action_on_jump`.

The action runs on the **killer**, never the victim. Apugli's bi-entity spelling has no counterpart here for that reason: the `kill` hook carries only the holder, so a `bientity_action` would be read by nothing and is deliberately not offered. Field set is identical to `action_on_jump`, and for the same reason.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_action` | EntityAction | no | noop | Action run on the holder when they get a kill |
| `condition` | EntityCondition | no | always-true | Gate: the kill only fires the action while this is true |
| `cooldown_ticks` | int ≥ 0 | no | `0` | After the action fires, suppress further firings for this many ticks (20 = 1s) |

**Example: heal a heart per kill**
```json
{
  "type": "apugli:action_on_target_death",
  "entity_action": { "type": "origins:heal", "amount": 2.0 }
}
```

---

## `origins:cooldown`

A readable cooldown timer. This is an Apoli compat (Route B) power; there is no native `neoorigins:` cooldown power type (native active powers carry their own `cooldown_ticks`). An Apoli cooldown power is a **countdown resource**: its value is `0` while ready and counts down from the armed duration, one per tick, while running. That makes it readable everywhere resources are: `neoorigins:resource` / `resource_level` conditions, `change_resource` / `set_resource` actions, and the HUD bar.

Arming: the [`neoorigins:trigger_cooldown`](../actions/effects.md#neooriginstrigger_cooldown) action sets the resource to the power's registered `cooldown` duration. Its `power` field may be the full power id, a legacy slash id, or a wildcard glob like `*:*_timer`; the glob is matched against the player's resource keys at runtime and every matching cooldown power is armed. The canonical Apoli pattern (inside an `origins:multiple`, where the `*:*_timer` self-reference in a *condition* resolves to the sibling timer's synthetic id at expansion): fire an ability, `trigger_cooldown` the timer, then gate the ability on the timer resource being `0`. A running countdown persists across relogs.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `cooldown` | int ticks | no | `1` | Countdown duration armed by `trigger_cooldown` (clamped to ≥ 1) |
| `hidden` | bool | no | `false` | Hide the HUD bar |
| `hud_render` | object | no | — | Same shape as the `neoorigins:resource` HUD block: `sprite_location`, `bar_index` / `icon_index` (default 0 when the block is present), `should_render` (false → hidden), `condition` (entity condition gating bar visibility) |

The duration and bar settings are shared by every holder of the power, and losing the power only clears that player's own timer. Up to 2.2.27, one player losing it also stripped the registered duration from everyone else until the next `/reload` or restart.

**Example: a 10-second ability timer, read back as a resource**
```json
{
  "type": "origins:cooldown",
  "cooldown": 200,
  "hud_render": { "should_render": false }
}
```
