---
title: "Combat & damage"
parent: "Power Types"
grand_parent: "DSL Reference"
nav_order: 6
permalink: /powers/combat
---

# Combat & damage

Immunities, damage reactions, dodges and on-hit effects.

Fields every power accepts (`name`, `hidden`, `power_condition`, ...) are on [Power basics](../concepts/power-basics.md). See also: [Power basics](../concepts/power-basics.md), [Events](../EVENTS.md).

Types on this page: [`invulnerability`](#neooriginsinvulnerability) · [`prevent_death`](#neooriginsprevent_death) · [`projectile_immunity`](#neooriginsprojectile_immunity) · [`projectile_vulnerability`](#neooriginsprojectile_vulnerability) · [`effect_immunity`](#neooriginseffect_immunity) · [`dodge_chance`](#neooriginsdodge_chance) · [`no_projectile_divergence`](#neooriginsno_projectile_divergence) · [`thorns_on_hit`](#neooriginsthorns_on_hit) · [`action_on_hit`](#neooriginsaction_on_hit) · [`self_action_on_hit`](#neooriginsself_action_on_hit) · [`light_level_effect`](#neooriginslight_level_effect) · [`low_hp_threshold`](#neooriginslow_hp_threshold) · [`ender_gaze_immunity`](#neooriginsender_gaze_immunity) · [`burn`](#neooriginsburn).

---

## `neoorigins:invulnerability`

Native invulnerability power: cancels incoming damage whose source matches any configured filter. If every filter list is empty, blocks all incoming damage. Replaces the lossy 1.x translation that only covered `FIRE`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `damage_types` | list of string | no | `[]` | Damage type IDs (e.g. `minecraft:fall`): matched by ID |
| `damage_tags` | list of string | no | `[]` | Damage type tag IDs (e.g. `minecraft:is_fire`) |
| `msg_ids` | list of string | no | `[]` | Vanilla damage msgId strings (e.g. `inFire`, `fall`): covers loose Origins-style names |

Filters combine with OR: a damage source is cancelled if it matches any entry in any list. If all three lists are empty, all damage is cancelled (matching Origins' behaviour when `damage_condition` is omitted).

**Example: fire and lava immunity**
```json
{
  "type": "neoorigins:invulnerability",
  "damage_tags": ["minecraft:is_fire"],
  "name": "Inferno Blood",
  "description": "Immune to fire and lava."
}
```

**Example: block everything**
```json
{
  "type": "neoorigins:invulnerability",
  "name": "Adamant",
  "description": "Cannot be harmed."
}
```

---

## `neoorigins:prevent_death`

Cancels the lethal blow instead of letting the player die. Faithful to Origins' `prevent_death`, with first-class condition and damage-type gating.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `condition` | object | no | _(always)_ | Standard entity condition; the power only saves the player while it is true. |
| `damage_types` | string | no | _(all)_ | Damage-type filter (comma-separated, `#tags`, msgIds, or registry keys). When set, only matching killing blows are prevented. |
| `invert` | bool | no | `false` | Flips `damage_types` into a blacklist: prevent all deaths _except_ the listed types. |
| `set_health` | float | no | `1.0` | Health the player is left at after a save (minimum 1). |
| `cooldown_ticks` | int | no | `0` | After a save, the power is inert for N ticks. `0` = unlimited (Origins behavior). |
| `entity_action` | object | no | — | Optional action run on the player each time a death is prevented. |

**Example: survive lethal fire once every 30 s, then drop to 4 hearts**
```json
{
  "type": "neoorigins:prevent_death",
  "damage_types": "#minecraft:is_fire,lava",
  "set_health": 8.0,
  "cooldown_ticks": 600,
  "name": "Fireproof Soul",
  "description": "Cannot burn to death — but only so often."
}
```

**Example: immortal except to the void, only while sneaking**
```json
{
  "type": "neoorigins:prevent_death",
  "damage_types": "fell_out_of_world",
  "invert": true,
  "condition": { "type": "neoorigins:sneaking" },
  "name": "Guarded Stance",
  "description": "Death cannot touch a braced body — the void still can."
}
```

**Caveat (same as Origins):** this only cancels the lethal event; it does not clear the damage source. For recurring damage-over-time (fire, lava, poison) pair it with a `condition` or an `entity_action` that removes the source, otherwise the player is re-killed on the next damage tick.

---

## `neoorigins:projectile_immunity`

Negates incoming projectile damage (arrows, tridents, fireballs, etc.). Which projectiles are blocked, and how reliably, are both configurable; the defaults block every arrow.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `projectile_types` | list of strings | no | `["arrow"]` | Projectiles blocked: `arrow` (every arrow-type projectile, which includes thrown tridents), `fireball`, `trident`, `all`, or an entity id. |
| `chance` | double (0.0–1.0) | no | `1.0` | Probability an incoming matching projectile is negated. |
| `teleport` | bool | no | `false` | When true a successful dodge triggers a short random teleport. |
| `teleport_range` | int | no | `16` | Max teleport distance in blocks on a dodge, when `teleport` is enabled. |

**Example:**
```json
{
  "type": "neoorigins:projectile_immunity",
  "projectile_types": ["all"],
  "name": "Arrow Deflection",
  "description": "Immune to all projectile damage."
}
```

---

## `neoorigins:projectile_vulnerability`

The holder takes extra damage from the listed projectiles, including ones that normally deal a player no damage at all, such as snowballs and eggs. The bonus is added to the projectile's own damage when it has some, and dealt on its own, credited to the projectile's thrower, when it has none. Origins' Blazeborn takes 3 from snowballs this way.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `projectiles` | list of string | yes | — | Projectile entity ids or `#tags`, e.g. `minecraft:snowball`. |
| `amount` | float | no | `3.0` | Extra damage per hit. |

**Example:**
```json
{
  "type": "neoorigins:projectile_vulnerability",
  "projectiles": ["minecraft:snowball"],
  "amount": 3.0,
  "name": "Melting Point",
  "description": "Snowballs hurt you."
}
```

---

## `neoorigins:effect_immunity`

Prevents listed potion effects from being applied to the player. Uses NeoForge's `MobEffectEvent.Applicable` to cancel before application.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effects` | list of string | no | `[]` | Effect IDs to block, e.g. `["minecraft:poison"]`. Supply it unless `inverted` is true: an empty list on its own grants no immunity at all |
| `inverted` | bool | no | `false` | Flips the list into an exception list (Apoli semantics): immune to every effect **except** those listed. `true` with an empty list = immune to all effects. |

**Example:**
```json
{
  "type": "neoorigins:effect_immunity",
  "effects": ["minecraft:poison", "minecraft:wither"],
  "name": "Toxin Resistance",
  "description": "Immune to poison and wither effects."
}
```

**Example: immune to everything except regeneration**
```json
{
  "type": "neoorigins:effect_immunity",
  "effects": ["minecraft:regeneration"],
  "inverted": true
}
```

> Pair `inverted: true` with an empty `effects` list and a `power_condition` for a temporary blanket immunity (e.g. an immunity-shot buff gated on a resource).

---

## `neoorigins:dodge_chance`

Percentage chance to completely dodge incoming damage. When triggered, the damage event is cancelled entirely. Applied via `CombatPowerEvents`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `chance` | float | no | `0.15` | Dodge probability (0.0–1.0). 0.15 = 15% dodge. |

**Example:**
```json
{
  "type": "neoorigins:dodge_chance",
  "chance": 0.2,
  "name": "Evasion",
  "description": "20% chance to dodge incoming attacks."
}
```

---

## `neoorigins:no_projectile_divergence`

Removes projectile divergence (perfect accuracy) for projectiles shot by the player. Every projectile the player owns is re-aimed along the look direction at its original speed when it spawns, so multishot volleys collapse into one line and projectiles fired by other powers are redirected too. Handled via `EntityJoinLevelEvent`.

No fields beyond `name` / `description`.

**Example:**
```json
{
  "type": "neoorigins:no_projectile_divergence",
  "name": "Dead Eye",
  "description": "Arrows and projectiles fly true."
}
```

---

## `neoorigins:thorns_on_hit`

Passive thorns: when the player takes damage from a living attacker (including the owner of a projectile), the attacker takes damage back. Optionally sets the attacker on fire. Applied via `CombatPowerEvents`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `damage` | float | no | `2.0` | Thorns damage dealt to the attacker. |
| `fire_ticks` | int | no | `0` | Fire ticks applied to the attacker (0 = no fire). |

**Example:**
```json
{
  "type": "neoorigins:thorns_on_hit",
  "damage": 3.0,
  "fire_ticks": 40,
  "name": "Ember Thorns",
  "description": "Melee attackers take 3 damage and catch fire."
}
```

---

## `neoorigins:action_on_hit`

Triggers an action each time the player deals damage to any living entity: mobs, animals, and other players. Optionally restricted by target entity group, target entity type, or damage type. There are two ways to express the effect: the simple flat `action` string (self or victim), or a full Apoli-style `bientity_action` (alias `entity_action`) that runs against the (attacker, victim) pair. Both may be present — the flat `action` runs first, then the parsed bientity action.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `action` | string | no | `restore_health` (`none` when `entity_action` or `bientity_action` is set) | One of `restore_health`, `restore_hunger`, `grant_effect` (self), `target_effect` (victim), or `none` / `nothing` |
| `amount` | float | no | `2.0` | Health or hunger amount (for the restore actions) |
| `effect` | Identifier | no | — | Effect to apply for `grant_effect` / `target_effect` |
| `duration` | int | no | `100` | Effect duration in ticks |
| `amplifier` | int | no | `0` | Effect level |
| `min_damage` | float | no | `0.0` | Only fires when outgoing damage ≥ this |
| `chance` | float | no | `1.0` | Probability to fire, from 0.0 to 1.0 |
| `target_group` | string | no | _(any)_ | Restrict to targets in group: `undead`, `arthropod`, `illager`, `aquatic` (vanilla `minecraft:<group>` entity-type tag) |
| `target_type` | Identifier | no | _(any)_ | Restrict to a specific entity type, e.g. `minecraft:zombie` |
| `damage_type` | string | no | _(all)_ | Restrict to a specific vanilla damage type, e.g. `mob_attack`, `magic` |
| `bientity_action` | entity action (object or array) | no | noop | Parsed action run against the pair: actor = the attacker (you), target = the entity you hit. A bare verb (e.g. `apply_effect`) runs against the **victim**; wrap in `actor_action` / `target_action` to route explicitly. See [Caster & target actions: bientity actions](../actions/bientity.md). |
| `entity_action` | entity action (object or array) | no | noop | Alias for `bientity_action`: the field name a pack copying the ACTIONS.md example uses. |

Filters are combined with AND: every configured filter must match for the action to fire. The `chance` roll happens last. The same filter gates (`min_damage`, `damage_type`, `target_group`, `target_type`, `chance`) govern the `bientity_action` too.

**Example: heal 0.5 hearts on striking any undead**
```json
{
  "type": "neoorigins:action_on_hit",
  "action": "restore_health",
  "amount": 1.0,
  "target_group": "undead",
  "name": "Smite Vigor",
  "description": "Drains life from the undead with each blow."
}
```

**Example: mark the victim with Glowing when striking undead**
```json
{
  "type": "neoorigins:action_on_hit",
  "action": "target_effect",
  "effect": "minecraft:glowing",
  "duration": 60,
  "target_group": "undead",
  "name": "Holy Mark",
  "description": "Hit undead are illuminated briefly."
}
```

**Example: 20% chance to gain Strength I on hitting a zombie, on melee only**
```json
{
  "type": "neoorigins:action_on_hit",
  "action": "grant_effect",
  "effect": "minecraft:strength",
  "duration": 60,
  "chance": 0.2,
  "damage_type": "mob_attack",
  "target_type": "minecraft:zombie",
  "name": "Adrenal Surge",
  "description": "Sometimes empowered when you melee a zombie."
}
```

**Example: poison whatever you hit (bientity form)**
```json
{
  "type": "neoorigins:action_on_hit",
  "hidden": true,
  "entity_action": { "type": "neoorigins:apply_effect", "effect": "minecraft:poison", "duration": 60 },
  "name": "Venom Strike",
  "description": "Your blows leave a lingering poison."
}
```
The bare `apply_effect` runs against the victim: hitting a mob poisons it. To affect yourself instead, wrap it in `actor_action`; to be explicit about targeting the victim, wrap it in `target_action`.

---

## `neoorigins:self_action_on_hit`

Fires when the holder deals damage to a living entity. Apoli compat (Route B), riding the same player-as-attacker seam in `CombatPowerEvents.onLivingDamage` that the native [`action_on_hit`](#neooriginsaction_on_hit) uses; the victim is passed straight through, so a paired action resolves against it instead of silently doing nothing.

`entity_action` runs on the holder. `bientity_action` runs with actor = the holder and target = the entity that was hit. Both may be present: the holder's action runs first.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_action` | object / array | no | — | Action run on the holder |
| `bientity_action` | object / array | no | — | Action run as (actor = holder, target = the entity hit). A bare verb runs against the target; wrap in `actor_action` / `target_action` to route explicitly. See [Caster & target actions: bientity actions](../actions/bientity.md). |
| `cooldown` | int | no | `0` | Ticks before the power can fire again. `0` fires on every hit. |
| `condition` | object / array | no | always | Entity condition on the holder, tested at hit time |

Every spelling lands here: `neoorigins:self_action_on_hit` (recognized since 2.2.6), `origins:` / `apace:` / `apoli:` / `apugli:self_action_on_hit`, and `origins:action_on_hit` / `apace:action_on_hit`. Mind the namespace on that last pair: `neoorigins:action_on_hit` is the *native* type with the filter surface, while `origins:action_on_hit` is Apoli's and loads as this one.

The type carries no `min_damage`, `chance` or victim filters: every hit fires it, subject only to `cooldown` and the holder-side `condition`. For a damage threshold, a chance roll or target-group / target-type gates, write the native [`neoorigins:action_on_hit`](#neooriginsaction_on_hit) instead.

**Example: lifesteal that poisons what you hit**
```json
{
  "type": "neoorigins:self_action_on_hit",
  "cooldown": 20,
  "entity_action": { "type": "neoorigins:heal", "amount": 1.0 },
  "bientity_action": { "type": "neoorigins:apply_effect", "effect": "minecraft:poison", "duration": 60 },
  "name": "Sanguine Edge",
  "description": "Your blows draw life and leave venom."
}
```

---

## `neoorigins:light_level_effect`

Applies a status effect when the player is at or below a certain light level. Removes the effect when they move to brighter light, including the same effect from any other source (a potion, a beacon).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `max_light_level` | int | no | `4` | Light level threshold (inclusive). |
| `effect` | resource location | yes | — | Status effect to apply. |
| `amplifier` | int | no | `0` | Effect amplifier. |
| `ambient` | boolean | no | `true` | Ambient effect flag. |
| `show_particles` | boolean | no | `false` | Show effect particles. |
| `show_icon` | boolean | no | `false` | Show effect icon on HUD. |

**Example: invisibility in darkness**
```json
{
  "type": "neoorigins:light_level_effect",
  "max_light_level": 4,
  "effect": "minecraft:invisibility",
  "name": "Shadow Meld",
  "description": "Become invisible in darkness."
}
```

---

## `neoorigins:low_hp_threshold`

Applies one or more status effects when the player's HP drops below a percentage threshold. Effects are removed when HP rises above the threshold, including the same effects from any other source (a potion, a beacon).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `threshold` | float | no | `0.5` | HP fraction (0.0–1.0). 0.5 = below 50%. |
| `effects` | list of `{ effect, amplifier }` | yes | — | Effects to apply while below threshold. |

**Example: berserker rage below 25% HP**
```json
{
  "type": "neoorigins:low_hp_threshold",
  "threshold": 0.25,
  "effects": [
    { "effect": "minecraft:strength", "amplifier": 1 },
    { "effect": "minecraft:speed", "amplifier": 0 }
  ],
  "name": "Death's Embrace",
  "description": "Gain Strength II and Speed below 25% HP."
}
```

---

## `neoorigins:ender_gaze_immunity`

Endermen do not aggro when the player looks at them. Emits the `ender_gaze` capability tag: `EnderManLookMixin` reads it and makes the Enderman's is-looking-at-me check return false.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| (no fields) | — | — | — | Marker power. |

**Example:**
```json
{
  "type": "neoorigins:ender_gaze_immunity",
  "name": "Void Gaze",
  "description": "Endermen ignore your gaze."
}
```

---

## `neoorigins:burn`

Sets the player on fire at a configurable interval. Used for origins that are perpetually burning or catch fire under certain conditions (pair with a `condition` on the power JSON to gate on daylight, biome, etc.).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `interval` | int | no | `20` | Ticks between fire applications. |
| `burn_duration` | int | no | `100` | Fire duration in ticks per application. |

Origins compat: translates `origins:burn`.

**Example: smoulder in sunlight**
```json
{
  "type": "neoorigins:burn",
  "interval": 40,
  "burn_duration": 60,
  "condition": { "type": "neoorigins:exposed_to_sun" },
  "name": "Sun Scorch",
  "description": "Burns when exposed to direct sunlight."
}
```
