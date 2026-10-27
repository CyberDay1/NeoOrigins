---
title: "Scripting & mod integration"
parent: "Power Types"
grand_parent: "DSL Reference"
nav_order: 11
permalink: /powers/integration
---

# Scripting & mod integration

Types driven by a KubeJS script or by another mod. A mod-backed type does nothing when its mod is absent; see [Mod Compatibility](../COMPATIBILITY.md).

Fields every power accepts (`name`, `hidden`, `power_condition`, ...) are on [Power basics](../concepts/power-basics.md). See also: [KubeJS Integration](../KUBEJS.md), [Mod Compatibility](../COMPATIBILITY.md).

Types on this page: [`become_dragon`](#neooriginsbecome_dragon) · [`xeno_passive`](#neooriginsxeno_passive).

---

## KubeJS bridge powers

`neoorigins:js_custom` and `neoorigins:js_active` do not exist on this version. KubeJS has no 26.3 release to build against, so the 26.3 build registers neither type, and a power file that names either one is dropped at load. They are available on the 1.21.1 and 26.1 builds; see [KUBEJS.md](../KUBEJS.md).

> **1.21.1 vs 26.x:** `js_custom` and `js_active` are registered on 1.21.1, and on 26.1 from 2.2.24. KubeJS has no 26.2 or 26.3 build to compile against, so neither type exists there and a power file naming one is dropped at load.

---

## `neoorigins:become_dragon`

Soft-compat hook for the [Dragon Survival](https://www.curseforge.com/minecraft/mc-mods/dragon-survival) mod: while the power is granted, the holder is a Dragon Survival dragon of the configured species; revoking it reverts them to human form. The power is a pure hook — Dragon Survival supplies all of the resulting traits, growth, abilities and hunters. The form is re-applied on login and respawn, so it survives relog and death.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `species` | string | yes | — | Dragon Survival species id, e.g. `dragonsurvival:cave_dragon`. |
| `stage` | string | no | `dragonsurvival:newborn` | Starting growth stage. Blank or unresolvable ids fall back to the species default. |

Notes:

- **Inert without Dragon Survival.** The power no-ops when the mod is absent. Always pair it (and the origin that grants it) with `"required_mods": ["dragonsurvival"]` so the content never loads, or appears in the picker, without the target mod. The three built-in dragon origins (Cave, Forest, Sea Dragon) follow this pattern.
- The bridge binds to Dragon Survival's internals reflectively (the mod exposes no addon API): if a future Dragon Survival release renames those internals, the power logs one warning and stops transforming players rather than crashing. See [COMPATIBILITY.md](../COMPATIBILITY.md).

**Example: cave dragon form**
```json
{
  "type": "neoorigins:become_dragon",
  "species": "dragonsurvival:cave_dragon",
  "stage": "dragonsurvival:newborn",
  "required_mods": ["dragonsurvival"],
  "name": "Cave Dragon Form",
  "description": "Take the shape of a cave dragon."
}
```

---

## `neoorigins:xeno_passive`

Soft-dependency compat with the Aliens vs Predator mod (`avp_alien`). Emits the `xeno_passive` capability tag, which an `@Pseudo` mixin reads from inside AvP's `AlienPredicates.isHost` to short-circuit host eligibility: facehuggers (and the ovomorph hatch-desire / parasite-attachment logic gated off the same predicate) stop treating the holder as a viable host, as if already infected or immune.

This only governs the facehugger host gate. The companion "xenomorphs ignore me entirely" behaviour is delivered separately via a `mobs_ignore_player` power scoped to the `#avp_alien:aliens` / `#avp_alien:parasites` tags. When AvP is not installed the capability simply goes unread and this power is an inert marker.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| (no fields) | — | — | — | Marker power. |

**Example:**
```json
{
  "type": "neoorigins:xeno_passive",
  "name": "Tainted Blood",
  "description": "Facehuggers find you an unfit host."
}
```
