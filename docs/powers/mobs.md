---
title: "Mobs, minions & taming"
parent: "Power Types"
grand_parent: "DSL Reference"
nav_order: 7
permalink: /powers/mobs
---

# Mobs, minions & taming

Minions, taming, mob aggression and fear, stealth, entity groups and mounts.

Fields every power accepts (`name`, `hidden`, `power_condition`, ...) are on [Power basics](../concepts/power-basics.md). See also: [Keybinds](../concepts/keybinds.md), [Toggles](../concepts/toggles.md), [Costs & upkeep](../concepts/costs-and-upkeep.md).

Types on this page: [`summon_minion`](#neooriginssummon_minion) · [`tame_mob`](#neooriginstame_mob) · [`tamed_animal_boost`](#neooriginstamed_animal_boost) · [`tamed_potion_diffusal`](#neooriginstamed_potion_diffusal) · [`entity_group`](#neooriginsentity_group) · [`mobs_ignore_player`](#neooriginsmobs_ignore_player) · [`mobs_target_player`](#neooriginsmobs_target_player) · [`mob_behavior`](#neooriginsmob_behavior) · [`attract_mobs`](#neooriginsattract_mobs) · [`scare_entities`](#neooriginsscare_entities) · [`sneaky`](#neooriginssneaky) · [`stealth`](#neooriginsstealth) · [`muffle_sound`](#neooriginsmuffle_sound) · [`mount`](#neooriginsmount) · [`no_mob_spawns_nearby`](#neooriginsno_mob_spawns_nearby) · [`twin_breeding`](#neooriginstwin_breeding) · [`horde_regen`](#neooriginshorde_regen).

---

## `neoorigins:summon_minion`

Active power that summons a mob near the player. Summoned mobs are tracked with caps, despawn timers, and death-damage feedback. Equipment can be configured per slot; all equipment drop chances are zeroed so summoned mobs never drop loot.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `mob_type` | Identifier | yes | — | Entity type to summon, e.g. `minecraft:zombie` |
| `max_count` | int | no | `3` | Maximum concurrent minions per player per mob_type |
| `quantity` | int | no | `1` | Minions summoned per activation, capped by remaining `max_count` headroom |
| `cooldown_ticks` | int | no | `200` | Cooldown after each summon |
| `hunger_cost` | int | no | `4` | Food points consumed per summon |
| `despawn_ticks` | int | no | `18000` | Lifespan after spawn (15 min default) |
| `death_damage` | float | no | `1.0` | Damage taken by the owner when a minion dies |
| `head` / `chest` / `legs` / `feet` / `mainhand` / `offhand` | Identifier _or_ object | no | _(none or iron helmet for head)_ | Equipment per slot. Either a bare item id (`"minecraft:iron_sword"`) or an object `{"item": "...", "enchantments": [{"id": "...", "level": N}]}` to enchant the piece |
| `mount` | Identifier | no | — | Entity type of an optional mount; each minion rides its own copy (e.g. a piglin riding a hoglin) |
| `attributes` | object[] | no | `[]` | Attribute modifiers applied to each summoned mob at spawn. Each entry is `{"attribute": "...", "amount": N, "operation": "..."}` |

If `head` is unset, the minion gets an iron helmet by default (sun protection for undead). All drop chances are set to 0.

**Multiple minions:** set `quantity` to summon several per activation (still capped by `max_count`). Hunger is charged once per activation regardless of how many spawn.

**Enchanted equipment:** any equipment slot accepts the object form `{"item": "...", "enchantments": [...]}` in place of a bare item id. Each enchantment entry is `{"id": "minecraft:sharpness", "level": 3}` (`level` defaults to 1). Unknown enchantment ids are skipped silently. The string form is unchanged, so existing configs keep working.

**Attribute modifiers:** `attributes` is a list of modifiers applied to every summoned mob. Each entry takes `attribute` (e.g. `minecraft:generic.max_health`, `minecraft:generic.attack_damage`, `minecraft:generic.movement_speed`), a numeric `amount`, and an `operation` of `add_value` (flat), `add_multiplied_base`, or `add_multiplied_total` (default `add_value`). Attribute ids resolve with `generic.`/`player.` prefix tolerance, so the same JSON is portable across the 1.21.1 and 26.1 builds. A raised `max_health` spawns the mob at full HP.

**Mounts:** when `mount` is set, every summoned minion is seated on a freshly spawned copy of that entity using a forced ride, so cross-type stacks like a piglin on a hoglin work. The mount is tracked alongside its rider: it counts against the same `mob_type` cap, drops no loot, and despawns with the rider.

**Brain-driven mobs (piglins, hoglins):** these decide aggression through the Brain/memory system rather than goal selectors, so vanilla checks like "the player isn't wearing gold" would otherwise make a summoned piglin turn hostile. Summoned brain mobs are pacified at spawn and re-pacified every tick: their attack-target/anger memories toward the owner are cleared and piglins are made immune to zombification, so they stay loyal to their summoner.

**Example: zombie minion with sword**
```json
{
  "type": "neoorigins:summon_minion",
  "mob_type": "minecraft:zombie",
  "max_count": 3,
  "cooldown_ticks": 200,
  "hunger_cost": 4,
  "mainhand": "minecraft:iron_sword",
  "name": "Raise Dead",
  "description": "Summons a zombie minion to fight for you."
}
```

**Example: pack of piglins riding hoglins**
```json
{
  "type": "neoorigins:summon_minion",
  "mob_type": "minecraft:piglin",
  "mount": "minecraft:hoglin",
  "quantity": 2,
  "max_count": 4,
  "cooldown_ticks": 400,
  "hunger_cost": 6,
  "mainhand": "minecraft:golden_sword",
  "name": "Piglin Cavalry",
  "description": "Summons mounted piglin riders that fight for you."
}
```

**Example: enchanted, buffed iron golem**
```json
{
  "type": "neoorigins:summon_minion",
  "mob_type": "minecraft:iron_golem",
  "max_count": 1,
  "cooldown_ticks": 600,
  "hunger_cost": 8,
  "mainhand": {
    "item": "minecraft:netherite_sword",
    "enchantments": [
      { "id": "minecraft:sharpness", "level": 5 },
      { "id": "minecraft:fire_aspect", "level": 2 }
    ]
  },
  "attributes": [
    { "attribute": "minecraft:generic.max_health", "amount": 40, "operation": "add_value" },
    { "attribute": "minecraft:generic.attack_damage", "amount": 0.5, "operation": "add_multiplied_total" }
  ],
  "name": "Forge Guardian",
  "description": "Summons a heavily armed, reinforced iron golem."
}
```

---

## `neoorigins:tame_mob`

Active power that tames a hostile mob the player is looking at. The mob's AI is rewritten to follow the player, target whatever recently hurt the owner, and join the owner's own attacks. Tamed mobs are tracked via MinionTracker.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `range` | double | no | `16.0` | Max raycast distance to a target |
| `max_tamed` | int | no | `4` | Maximum concurrently-tamed mobs per player |
| `cooldown_ticks` | int | no | `200` | Cooldown after each use |
| `hunger_cost` | int | no | `3` | Food points consumed per tame |
| `despawn_ticks` | int | no | `36000` | Lifespan of each tamed mob (30 min default) |
| `death_damage` | float | no | `0.5` | Damage taken by owner when a tamed mob dies |
| `hostile_only` | bool | no | `true` | If `true`, only hostile mobs (implementing `Enemy`) can be tamed, the Monster Tamer default. Set to `false` to allow taming any non-player `Mob` (animals, golems, villagers, etc.). |
| `entity_blacklist` | string[] | no | `[]` | Entity ids (`"minecraft:warden"`) and tag refs (`"#mymod:untameable"`) this power can never tame. Checked on top of the built-in boss exclusion. |
| `targeting` | string | no | `"raycast"` | `"raycast"` tames the single mob you are looking at; `"area"` tames every eligible mob within `range`, nearest-first, up to the remaining `max_tamed` slots. Case-insensitive; anything else falls back to `raycast`. |
| `entity_whitelist` | string[] | no | `[]` | **Area mode only.** Entity ids (`"minecraft:zombie"`) and tag refs (`"#mymod:tameable"`) the sweep is restricted to. Empty (default) = any mob (still subject to `hostile_only` and the blacklists). Ignored in raycast mode. |
| `resource_cost` | string | no | `""` | Optional `neoorigins:resource` power id spent **per mob tamed**. Empty = no resource cost (falls back to `hunger_cost`). When resource bars are globally disabled this amount is charged as hunger instead. |
| `resource_cost_amount` | int | no | `0` | Amount of the `resource_cost` resource spent per mob tamed. |

### Targeting modes and per-mob cost

With the default `targeting: "raycast"` the power tames the one mob under the player's crosshair, showing the existing per-failure actionbar messages (`not_hostile` / `boss` / `blacklisted` / `no_target`).

Set `targeting: "area"` to tame every eligible mob inside `range` in one activation. Candidates are gathered nearest-first and capped at the remaining `max_tamed` slots; `hostile_only`, `entity_blacklist`, the boss-tier set and the global config blacklist all still apply, and `entity_whitelist` (if non-empty) further restricts which mobs qualify. A successful multi-tame shows "Tamed N mobs!".

Cost is charged **once per mob tamed**, not once per activation. When `resource_cost`/`resource_cost_amount` are set and resource bars are enabled, each tame spends that much of the named resource; otherwise it spends `hunger_cost` food points (or, when a resource is configured but resource bars are globally disabled, `resource_cost_amount` food points). Taming is greedy: it tames mobs one at a time until either the slots run out or the player can no longer afford one more, then stops. If candidates existed but none could be afforded, it shows "Not enough power to tame!" and consumes no cooldown.

Target must be a non-player `Mob`. With the default `hostile_only: true`, only mobs implementing `Enemy` qualify (villagers, animals, and passive mobs won't tame); set `hostile_only: false` to drop that restriction. Boss-tier mobs (the Warden, Ender Dragon and Wither) are always rejected, as is anything that fails the `canUsePortal` boss check; `entity_blacklist` lets a pack extend that exclusion to arbitrary mobs, and server operators can do the same for all taming and scare powers at once via the `tame_scare_entity_blacklist` config list (see [Global taming/scare exclusions](#global-tamingscare-exclusions)). A blocked tame shows the "That creature cannot be tamed!" actionbar message ("Cannot tame boss mobs!" for a boss).

### Persistence

Tamed mobs behave like vanilla pets: the tame is stored on the mob itself (owner, despawn duration and death-damage ride the entity's saved data), so it survives everything short of the mob dying or `despawn_ticks` running out. Concretely:

- **Tamer dies**: pets survive and are recalled to the tamer when they respawn, crossing dimensions if needed.
- **Tamer logs out**: pets stay in the world, idle and non-hostile, and resume following/defending the moment the tamer logs back in. No re-tame needed.
- **Server restarts / chunk unloads**: the pet's loyalty AI is rebuilt automatically whenever its chunk loads again.

The `max_tamed` cap and death-damage backlash stay correct across all of the above. Losing the taming power (an origin change) discards the player's loaded pets outright; that is the one way to revoke a tame. Summoned minions (`summon_minion`) deliberately do NOT get this treatment: they remain session-bound and vanish with their summoner's death or logout.

**Example (blacklist):**
```json
{
  "type": "neoorigins:tame_mob",
  "entity_blacklist": ["minecraft:elder_guardian", "#mymod:untameable"]
}
```

**Example:**
```json
{
  "type": "neoorigins:tame_mob",
  "range": 16.0,
  "max_tamed": 4,
  "cooldown_ticks": 200,
  "name": "Beastmaster",
  "description": "Tames hostile mobs to your will."
}
```

### Global taming/scare exclusions

All taming and scare powers (`tame_mob`, `scare_entities` and `mobs_ignore_player`) share one exclusion rule. An entity is excluded when **any** of these hold:

1. **Boss-tier**: the Warden, Ender Dragon and Wither. Hardcoded; cannot be overridden.
2. **Global config blacklist**: the `tame_scare_entity_blacklist` list in the `[entity_exclusions]` section of `config/neoorigins/admin.toml`. Lets a server or pack operator extend the exclusion to arbitrary mobs across every taming and scare power at once:

```toml
[entity_exclusions]
    # Entity ids and #tags that can never be tamed, scared,
    # or made to ignore a player by any power. Default: []
    tame_scare_entity_blacklist = ["minecraft:elder_guardian", "#mymod:untouchable"]
```

3. **Per-power `entity_blacklist`**: the optional JSON field on the individual power.

Excluded entities can't be tamed (`tame_mob` shows its actionbar message) and aren't affected by `scare_entities` or `mobs_ignore_player` (silently skipped; they flee or target the player exactly as vanilla dictates).

---

## `neoorigins:tamed_animal_boost`

Boosts stats (max health, movement speed) on every tamed animal owned by the player within a radius. Every 60 ticks it adds permanent attribute modifiers with fixed ids (saved on the animal) and heals the animal to full the first time. Revoking the power only strips animals inside the radius at that moment.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `health_bonus` | float | no | `4.0` | `ADD_VALUE` bonus on max health |
| `speed_bonus` | float | no | `0.1` | `ADD_VALUE` bonus on movement speed |
| `radius` | double | no | `32.0` | Scan radius |

**Example:**
```json
{
  "type": "neoorigins:tamed_animal_boost",
  "health_bonus": 4.0,
  "speed_bonus": 0.1,
  "radius": 32.0,
  "name": "Kinship",
  "description": "Your tamed animals are hardier and faster."
}
```

---

## `neoorigins:tamed_potion_diffusal`

When the player receives a positive mob effect, it's also applied to nearby tamed animals owned by the player. Handled via `MobEffectEvent.Added`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | double | no | `16.0` | Scan radius for nearby tamed animals |

**Example:**
```json
{
  "type": "neoorigins:tamed_potion_diffusal",
  "radius": 16.0,
  "name": "Shared Fortitude",
  "description": "Positive potion effects also buff your tamed animals."
}
```

---

## `neoorigins:entity_group`

Treats the player as a member of a mob group, so game mechanics that key off a creature's type act on the player. A player can't be added to vanilla's real entity-type tags, so membership is simulated by intercepting the relevant hooks: effect immunity, instant heal/harm inversion, enchant vulnerability, and mob targeting.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `group` | string | no | `undefined` | Group id. A bare name (no namespace) resolves to `neoorigins:<name>`. `undefined` means no classification. |

**Example:**
```json
{
  "type": "neoorigins:entity_group",
  "group": "undead",
  "name": "Undead Nature",
  "description": "Treated as an undead creature — immune to poison, healed by harm."
}
```

### Built-in groups

These are registered in code and work with zero setup:

| Group | Effect |
|---|---|
| `neoorigins:undead` | Immune to Poison and Regeneration; Instant Health harms and Instant Damage heals; takes extra damage from Smite. |
| `neoorigins:arthropod` | Takes extra damage from Bane of Arthropods (plus the vanilla slowness-on-hit). |
| `neoorigins:water` | Takes extra damage from Impaling. |
| `neoorigins:illager` | Raiders (anything in `#minecraft:raiders`) won't target the player; village-spawned iron golems hunt the player (player-built golems stay loyal); villagers and wandering traders flee the player. |
| `neoorigins:piglin` | Piglins and piglin brutes never target the player. Unlike vanilla this is unconditional: no gold armor required. |
| `neoorigins:skeleton` | The `undead` kit (Poison/Regeneration immunity, inverted instant effects, Smite vulnerability) plus wolves hunt the player the way they hunt vanilla skeletons, and the player burns in daylight like a vanilla skeleton (honours the same `[sun_damage]` helmet/umbrella rules as `exposed_to_sun`). |

Referencing an unknown group id (one with no built-in default and no datapack file) logs a one-time warning and does nothing, rather than silently no-op-ing.

### Custom groups (datapack)

A datapack can mint new groups, or override a built-in, with a JSON file at `data/<namespace>/neoorigins/entity_groups/<name>.json`. A file whose id matches a built-in (e.g. `data/neoorigins/neoorigins/entity_groups/undead.json`) replaces that built-in. Every field is optional:

| Field | Type | Default | Effect |
|---|---|---|---|
| `immune_effects` | list of effect ids | `[]` | These status effects can't apply to the player. |
| `invert_instant_effects` | bool | `false` | Instant Health harms and Instant Damage heals (undead behaviour). |
| `vulnerable_enchants` | list of enchant ids | `[]` | For each listed enchant, the attacker's weapon level adds bonus damage (`level × 2.5` per enchant, summed). Listing `minecraft:bane_of_arthropods` also applies the vanilla slowness-on-hit. |
| `ignored_by` | list of entity ids and/or `#tags` | `[]` | Mobs matching these entries never target the player. |
| `targeted_by` | list of entity ids and/or `#tags` | `[]` | Mobs matching these entries proactively hunt the player, within ~16 blocks. Player-built iron golems are exempt even when `minecraft:iron_golem` is listed; only village-spawned golems hunt. |
| `feared_by` | list of entity ids and/or `#tags` | `[]` | Mobs matching these entries flee the player, within ~8 blocks. |
| `burns_in_sunlight` | bool | `false` | The player catches fire in daylight like a vanilla skeleton, honouring the same `[sun_damage]` config (helmet protection, `neoorigins:sun_permeable` helmets, umbrella) as the `exposed_to_sun` condition. |

**Example: `data/mypack/neoorigins/entity_groups/cursed.json`**
```json
{
  "immune_effects": ["minecraft:poison", "minecraft:wither"],
  "invert_instant_effects": true,
  "vulnerable_enchants": ["minecraft:smite"],
  "ignored_by": ["#minecraft:raiders", "minecraft:zombie"],
  "targeted_by": ["minecraft:iron_golem"],
  "feared_by": ["minecraft:villager", "minecraft:wandering_trader"]
}
```

An origin then opts in with `{"type": "neoorigins:entity_group", "group": "mypack:cursed"}`.

---

## `neoorigins:mobs_ignore_player`

Causes specific mob types to ignore the player. By default a retaliation
window applies: once the player hits the mob, the mob may target back
briefly. Set `passive: true` to make the ignore unconditional.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_types` | list of Identifier or `#tag` | no | `[]` | Entity types that will ignore the player. Accepts raw ids (`"minecraft:zombie"`) and tag references (`"#minecraft:skeletons"`). When empty, every mob ignores. |
| `entity_blacklist` | string[] | no | `[]` | Entity ids and tag refs this power never affects; they target the player normally even when `entity_types` matches (including the empty match-all case). Checked on top of the built-in exclusions below. |
| `passive` | bool | no | `false` | When true, the ignore is unconditional: even attacking the mob does not provoke retaliation. |

Boss-tier mobs (the Warden, Ender Dragon and Wither) never ignore the player, regardless of `entity_types` (an empty match-all list won't make the Warden docile). Server operators can extend that exclusion via the `tame_scare_entity_blacklist` config list (see [Global taming/scare exclusions](#global-tamingscare-exclusions)).

**Example: only creepers ignore (with retaliation)**
```json
{
  "type": "neoorigins:mobs_ignore_player",
  "entity_types": ["minecraft:creeper"],
  "name": "Creeper Affinity",
  "description": "Creepers ignore you unless attacked."
}
```

**Example: unprovokable peace with every skeleton (tag + passive)**
```json
{
  "type": "neoorigins:mobs_ignore_player",
  "entity_types": ["#minecraft:skeletons"],
  "passive": true,
  "name": "Bonewalker",
  "description": "Skeletons never see you as a threat, no matter what you do."
}
```

> This is distinct from `scare_entities` (which makes mobs flee). `mobs_ignore_player` makes mobs neutral; `scare_entities` makes them run away.

---

## `neoorigins:mobs_target_player`

The inverse of `mobs_ignore_player`: listed mob types proactively hunt a player who has this power, the way wolves hunt a vanilla skeleton. Nothing fires when a mob is not targeting the player, so this can not be a reactive event: instead each mob is given a targeting goal on spawn that acquires the nearest matching power-holder within `range`.

> **1.21.1 vs 26.x:** on 1.21.1 the targeting goal searches a fixed 16 blocks, so a `range` above 16 has no extra effect; on 26.x the search is capped by the mob's own follow range instead.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_types` | list of Identifier or `#tag` | no | `[]` | Entity types that hunt the player. Accepts raw ids (`"minecraft:wolf"`) and tag references (`"#minecraft:wolves"`). When empty, every mob hunts the player. |
| `entity_blacklist` | string[] | no | `[]` | Entity ids and tag refs this power never affects; they never hunt the player even when `entity_types` matches (including the empty match-all case). Checked on top of the built-in exclusions below. |
| `range` | double | no | `16.0` | How far (in blocks) a matching mob can be and still start hunting the player. Also capped by the mob's own follow range, so values above that have no extra effect. |

Boss-tier mobs (the Warden, Ender Dragon and Wither) never hunt the player, regardless of `entity_types`. Server operators can extend that exclusion via the `tame_scare_entity_blacklist` config list (see [Global taming/scare exclusions](#global-tamingscare-exclusions)).

**Example: a skeleton-type origin that wolves hunt**
```json
{
  "type": "neoorigins:mobs_target_player",
  "entity_types": ["minecraft:wolf"],
  "range": 20.0,
  "name": "Bonewalker's Curse",
  "description": "Wolves hunt you the way they hunt skeletons."
}
```

> Pairs naturally with `mobs_ignore_player`: use `mobs_ignore_player` for the mobs that leave a skeleton alone, and `mobs_target_player` for the wolves that chase it.

---

## `neoorigins:mob_behavior`

Rewrites a **mob origin's** AI so the mob hunts players (or another entity type). Unlike the player-facing tame/mount powers, this is applied to a mob origin and controls how that mob acquires and holds targets. On grant, a vanilla `NearestAttackableTargetGoal` (and, when `retaliate` is set, a `HurtByTargetGoal`) is added to the mob's target selector; both are stripped again on revoke, leaving the rest of the mob's vanilla AI intact. With `aggression: "conditional"` it behaves piglin-style: the mob only turns hostile toward a player while every `hostile_when` condition holds (the conditions are evaluated against the prospective player target, not the mob).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `aggression` | enum | no | `neutral` | `neutral` = vanilla AI unchanged (only `retaliate` applies); `hostile` = always target the target type on sight; `conditional` = target a player only while every `hostile_when` condition holds. |
| `hostile_when` | array | no | `[]` | Conditions (AND-ed) tested against the **prospective player target** (e.g. "player not wearing gold"), not the mob. Only used when `aggression: "conditional"`; an empty list behaves like `hostile`. |
| `retaliate` | bool | no | `true` | Also fight back against whatever damaged the mob (vanilla hurt-by-target). |
| `anger_linger_ticks` | int | no | `200` | Keep the target this many ticks after the trigger stops holding, so the mob calms down gradually (200 = 10s). |
| `aggro_range` | double | no | `16.0` | Max distance to acquire a target. |
| `target_type` | resource id | no | players | Entity type id to be hostile toward; omitted = players. Conditions only apply to player targets. |
| `call_for_help` | bool | no | `false` | When retaliating, alert nearby same-type mobs (vanilla pack aggro). |

**Example: hostile to players only in daylight while not sneaking**
```json
{
  "type": "neoorigins:mob_behavior",
  "aggression": "conditional",
  "aggro_range": 24.0,
  "anger_linger_ticks": 300,
  "call_for_help": true,
  "hostile_when": [
    { "type": "neoorigins:exposed_to_sun" },
    { "type": "neoorigins:not", "condition": { "type": "neoorigins:sneaking" } }
  ]
}
```

---

## `neoorigins:attract_mobs`

Pulls nearby mobs toward the holder each tick, as though the player were holding the mob's favourite food. Drawn mobs simply path to the player; they are not tamed and do not turn hostile. With no `entity_types` filter only animals (the vanilla follows-food set) are pulled; supplying ids/tags widens or replaces that to any matching pathfinding mob. Bosses and the `tame_scare_entity_blacklist` config list are always excluded. Mobs re-path every 10 ticks.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | number | no | `8.0` | Blocks around the holder within which mobs are pulled in |
| `speed` | number | no | `1.0` | Movement-speed multiplier mobs path toward the holder at |
| `entity_types` | array | no | `[]` | Entity ids or `#tags` drawn in; empty (default) pulls only animals |
| `entity_blacklist` | array | no | `[]` | Entity ids or `#tags` excluded from attraction even when `entity_types` (or the animal default) would select them |

**Example: pied piper of animals**
```json
{
  "type": "neoorigins:attract_mobs",
  "radius": 12,
  "name": "Beast Whisperer",
  "description": "Nearby animals follow you as if you held their favourite food."
}
```

---

## `neoorigins:scare_entities`

Causes listed entity types to flee from the player. Every 5 ticks, matching mobs within a fixed 8-block box flee; no line of sight is needed. Matching mobs also cannot target the player unless the player hits them first. With `entity_types` empty nothing is scared.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_types` | list of Identifier | no | `[]` | Entity types to scare, e.g. `["minecraft:creeper"]` |
| `entity_blacklist` | string[] | no | `[]` | Entity ids (`"minecraft:elder_guardian"`) and tag refs (`"#mymod:fearless"`) this power never scares, even when they match `entity_types`. Checked on top of the built-in exclusions below. |

Boss-tier mobs (the Warden, Ender Dragon and Wither) are never scared, regardless of `entity_types`. Server operators can extend that exclusion to arbitrary mobs across all taming and scare powers at once via the `tame_scare_entity_blacklist` config list (see [Global taming/scare exclusions](#global-tamingscare-exclusions)). Excluded entities are simply skipped; no message is shown.

**Example: creepers flee from the player**
```json
{
  "type": "neoorigins:scare_entities",
  "entity_types": ["minecraft:creeper", "minecraft:spider"],
  "name": "Predator Aura",
  "description": "Hostile arthropods flee on sight."
}
```

---

## `neoorigins:sneaky`

Reduces mob detection range: any mob only targets the player when closer than its follow range times `detection_multiplier` (the lowest one wins if several powers apply). A mob that is already chasing keeps chasing. Handled via `LivingChangeTargetEvent`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `detection_multiplier` | double | no | `0.3` | Detection range multiplier (`0.3` = mobs see you at 30% of their normal range) |

**Example:**
```json
{
  "type": "neoorigins:sneaky",
  "detection_multiplier": 0.3,
  "name": "Shadow Tread",
  "description": "Mobs notice you from much closer than usual."
}
```

---

## `neoorigins:stealth`

After sneaking continuously for a threshold number of ticks, the player gains Invisibility, renewed every tick for as long as they keep sneaking. The effect is two seconds long and is not removed when they stand up, so it runs out within two seconds of sneaking stopping. While the holder has Invisibility from any source, mobs cannot pick them as a target. Toggleable off via keybind.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `activation_ticks` | int | no | `200` | Sneak time required before invisibility kicks in (10 s default) |

Also takes the shared [toggle upkeep](../concepts/costs-and-upkeep.md#toggle-upkeep) fields (`hunger_cost`, `resource_cost`, `resource_cost_amount`, `upkeep_interval`, `upkeep_condition`).

**Example:**
```json
{
  "type": "neoorigins:stealth",
  "activation_ticks": 100,
  "name": "Vanish",
  "description": "Sneak for 5 seconds to turn invisible."
}
```

---

## `neoorigins:muffle_sound`

Suppresses the game-event vibrations the player emits, so sculk sensors, calibrated sculk sensors and wardens stop detecting their footsteps, item use, block interactions and the like. Handled via NeoForge's cancelable `VanillaGameEvent`: when the cause is a player holding this power, the emission is canceled before any `GameEventListener` (sculk / warden) receives it.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `strength` | double | no | `1.0` | Fraction (`0.0`-`1.0`) of emitted vibrations suppressed. `1.0` = fully silent; `0.0` = no muffling; `0.5` = roughly half the player's vibrations are dropped. |

**Example:**
```json
{
  "type": "neoorigins:muffle_sound",
  "strength": 1.0,
  "name": "Silent Step",
  "description": "Sculk sensors and the Warden can no longer hear you."
}
```

---

## `neoorigins:mount`

Active keybind power that raycasts for the living entity in front of the player and seats the player on it. Press the skill key with a target in range to mount; press again while riding to dismount. Mobs are mounted immediately; **player** targets require consent according to the server's configured consent mode (`MountConsentManager`). Boss mobs and already-ridden entities are rejected, and the rider is dismounted automatically if the power is revoked. Good for tamer/druid origins that ride mobs (or other players) on demand.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `range` | double | no | `5.0` | Max distance in blocks to raycast for the entity to mount. |
| `cooldown_ticks` | int | no | `100` | Ticks before the key works again (100 = 5s). Dismounting also starts it, and while it runs the key cannot dismount either. |
| `hunger_cost` | int | no | `0` | Hunger consumed per key press that does something: mounting, dismounting, or sending a player-mount request. |
| `allow_players` | bool | no | `true` | Whether this power can mount other players (subject to consent). |
| `allow_mobs` | bool | no | `true` | Whether this power can mount mobs. |
| `block_bosses` | bool | no | `true` | Prevent mounting boss mobs like the Ender Dragon or Wither. |
| `mount_position` | enum | no | `centered` | Where the rider sits: `centered` (on top) or `shoulder` (offset to one side). |

**Example:**
```json
{
  "type": "neoorigins:mount",
  "range": 6.0,
  "cooldown_ticks": 60,
  "allow_players": false,
  "mount_position": "shoulder",
  "name": "Mount Beast",
  "description": "Leap onto the creature you're looking at."
}
```

---

## `neoorigins:no_mob_spawns_nearby`

Suppresses mob spawns within a radius of the player. Toggleable: the player can turn the aura on/off via their skill keybind.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | int | no | `24` | Radius in blocks within which spawns are suppressed. Note: vanilla already blocks `MONSTER`-category natural spawns within 24 blocks of any player (`NaturalSpawner`); use a value above 24 to extend the safe zone meaningfully. |
| `categories` | list of string | no | `["monster"]` | Spawn categories to suppress: `monster`, `creature`, `ambient`, `water_creature`, or `all` |

Also takes the shared [toggle upkeep](../concepts/costs-and-upkeep.md#toggle-upkeep) fields (`hunger_cost`, `resource_cost`, `resource_cost_amount`, `upkeep_interval`, `upkeep_condition`).

**Example: suppress hostile spawns in a 36-block radius**
```json
{
  "type": "neoorigins:no_mob_spawns_nearby",
  "radius": 36,
  "categories": ["monster"],
  "name": "Warding Presence",
  "description": "Hostile mobs don't spawn within 36 blocks. Toggle with skill key."
}
```

---

## `neoorigins:twin_breeding`

On vanilla breeding, spawns a second baby with the configured probability. Only the player credited with the breeding triggers it.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `chance` | float | no | `1.0` | Probability (0.0–1.0) of spawning a twin |

**Example:**
```json
{
  "type": "neoorigins:twin_breeding",
  "chance": 0.5,
  "name": "Shepherd",
  "description": "Animals bred near you sometimes produce twins."
}
```

---

## `neoorigins:horde_regen`

Passively regenerates health on all tamed mobs (via `tame_mob`) on an interval. Healing only applies when the mob hasn't taken damage recently.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `heal_amount` | float | no | `1.0` | HP restored per interval |
| `interval_ticks` | int | no | `120` | Ticks between heal pulses |
| `combat_cooldown_ticks` | int | no | `100` | Minimum ticks since the mob was last hurt by another mob before it heals. Other damage (fire, falls, players) does not pause healing. |

**Example:**
```json
{
  "type": "neoorigins:horde_regen",
  "heal_amount": 1.0,
  "interval_ticks": 120,
  "name": "Pack Mender",
  "description": "Your tamed mobs regenerate out of combat."
}
```
