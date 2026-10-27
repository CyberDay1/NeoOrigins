---
title: "Active abilities"
parent: "Power Types"
grand_parent: "DSL Reference"
nav_order: 2
permalink: /powers/active
---

# Active abilities

Keybind abilities. Use `active_ability` for new powers; the specialised types after it predate it and do one fixed thing each.

Fields every power accepts (`name`, `hidden`, `power_condition`, ...) are on [Power basics](../concepts/power-basics.md). See also: [Keybinds](../concepts/keybinds.md), [Cooldowns & HUD](../concepts/cooldowns-and-hud.md), [Costs & upkeep](../concepts/costs-and-upkeep.md).

Types on this page: [`active_ability`](#neooriginsactive_ability) · [`command_pack`](#neooriginscommand_pack) · [`active_teleport`](#neooriginsactive_teleport) · [`active_dash`](#neooriginsactive_dash) · [`active_recall`](#neooriginsactive_recall) · [`personal_dimension`](#neooriginspersonal_dimension) · [`active_swap`](#neooriginsactive_swap) · [`active_fireball`](#neooriginsactive_fireball) · [`active_phase`](#neooriginsactive_phase) · [`active_bolt`](#neooriginsactive_bolt) · [`active_place_block`](#neooriginsactive_place_block) · [`ground_slam`](#neooriginsground_slam) · [`tidal_wave`](#neooriginstidal_wave) · [`shadow_orb`](#neooriginsshadow_orb).

---

## `neoorigins:active_ability`

Generic cooldown-gated active (keybind) ability. Part of the 2.0 consolidation: collapses `active_teleport`, `active_dash`, `active_launch`, `active_recall`, `active_swap`, `active_fireball`, `active_bolt`, `active_phase`, `active_place_block`, `healing_mist`, `repulse`, `active_aoe_effect`, and others into one type whose effect is described by an `entity_action` tree.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `cooldown_ticks` | int | no | `60` | Cooldown after each use. Ignored when `cooldown_resource` is set. |
| `cooldown_resource` | string | no | `""` | Id of a `neoorigins:variable` (or `neoorigins:resource`) counter whose **live value, in ticks**, is read at activation time and used as the cooldown length, overriding `cooldown_ticks`. Empty = fixed cooldown. Edit the counter with `change_resource`/`set_resource` to make a power's cooldown scale with game state (stacks consumed, upgrades bought, etc.). A missing counter falls back to its declared `start`; negative values clamp to 0 (instant reuse). |
| `hunger_cost` | int | no | `0` | Food points removed per use (1 shank = 2 points). Silently aborts if player has less food (cooldown not consumed). |
| `resource_cost` | string | no | `""` | Id of a `neoorigins:resource` power to debit per use. Empty = no resource cost. |
| `resource_cost_amount` | int | no | `0` | Amount drained from `resource_cost` per use. Silently aborts (cooldown not consumed) if the resource can't cover it. If resource bars are globally disabled in config, the cost is charged as hunger instead. |
| `entity_action` | EntityAction | no | noop | Action tree fired on use (typically `neoorigins:and { actions: [...] }`) |
| `condition` | EntityCondition | no | always-true | DSL gate: skips firing (and the cooldown) if false |
| `fail_action` | EntityAction | no | — | Run when an activation attempt is blocked by `condition`, e.g. a `tellraw` telling the player why. Not fired on cooldown or hunger/resource aborts; the blocked attempt never consumes the cooldown. |
| `cooldown_icon` | string | no | `""` | HUD cooldown icon (item id or `.png` texture path); see "Cooldown HUD fields" at the top of this page |
| `cooldown_countdown` | bool | no | `true` | Draw remaining seconds on the icon (needs `cooldown_icon`) |
| `always_show_icon` | bool | no | `false` | Keep the icon on the HUD even while idle (needs `cooldown_icon`; this is what keeps the icon visible under the default `COOLDOWNS_AND_TOGGLES` HUD mode, and has no additional effect under `ALL_ACTIVE_ABILITIES`) |
| `key` | int, string, or object | no | — | Hotkey the ability binds to instead of a `skill_1`..`skill_6` slot. A number `N` pins it to named-hotkey pool slot "Hotkey N" (1-64), and several powers sharing the same `N` all fire from that one key. A string is a keybind translation key used as-is (`"examplepack.key.leap"`), or a vanilla input key such as `"key.jump"`, which binds straight to that game control. An object takes `{"key": <number or string>, "continuous": true}` to fire every tick the key is held. Omit for the next free skill slot. See [Named keybinds](../concepts/keybinds.md). |

Each `active_ability` power maintains an **independent cooldown**. Multiple active abilities on the same origin do not share a cooldown counter: triggering one ability does not block another.

> **Named hotkey slots:** with no `key` field, `neoorigins:active_ability` takes
> one of the six hardcoded `key.neoorigins.skill_1`..`skill_6` controls. Declare
> a `key` and it binds to that instead and is kept out of the skill-slot roster,
> so the six stay free for the abilities that need them. This works on the native
> type directly: you do not have to fall back to an Apoli-style
> `origins:active_self` to get a labelled hotkey. See
> [Named keybinds](../concepts/keybinds.md).

Actions and conditions are compiled once at power-load time via `ActionParser` / `ConditionParser`; runtime only dispatches through the compiled closures.

Hunger gating is handled at the `AbstractActivePower` base class level: when `hunger_cost > 0`, the base checks and debits food before calling `execute`. Any power that extends `AbstractActivePower` and wires `hungerCost()` through its Codec inherits the behavior automatically.

**Example: launch the player upward**
```json
{
  "type": "neoorigins:active_ability",
  "cooldown_ticks": 80,
  "entity_action": {
    "type": "neoorigins:add_velocity",
    "y": 2.0
  },
  "name": "Leap",
  "description": "Launches the player upward."
}
```

**Example: cooldown driven by a counter.** Declare a hidden `neoorigins:variable` counter elsewhere in the origin (say `myorigin:dash_cooldown`, `start: 100`), then point the ability at it. Other powers can shorten or lengthen the reuse delay by writing to that counter, e.g. a passive that runs `set_resource myorigin:dash_cooldown 40` while sprinting, or a `change_resource` that adds 20 ticks per consecutive use:
```json
{
  "type": "neoorigins:active_ability",
  "cooldown_resource": "myorigin:dash_cooldown",
  "entity_action": {
    "type": "neoorigins:add_velocity",
    "x": 3.0
  },
  "name": "Adaptive Dash",
  "description": "Dash whose cooldown follows the dash_cooldown counter."
}
```
The counter's value is read live on every activation, so the same key can be tuned at runtime by any action. Because counters share the resource keyspace and are declared at power-load time, the cooldown reads correctly even on the very first use (it falls back to the counter's `start`).

Legacy active types (`active_teleport`, `active_dash`, etc.) remain registered during the deprecation window. The `migrateLegacyPowers` gradle task can rewrite pack JSON to this type; `LegacyPowerTypeAliases` covers unmigrated JSON once per-type field remappers are landed.

---

## `neoorigins:command_pack`

Active power that commands every tamed mob (via `tame_mob`) to attack the entity the player is looking at.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `range` | double | no | `32.0` | Max raycast distance to the target |
| `cooldown_ticks` | int | no | `40` | Cooldown after each use |

**Example:**
```json
{
  "type": "neoorigins:command_pack",
  "range": 32.0,
  "cooldown_ticks": 40,
  "name": "Sic 'Em",
  "description": "Orders your pack to attack your target."
}
```

---

## `neoorigins:active_teleport`

Active ability that teleports the player to the block they are looking at, up to a maximum distance. Plays enderman teleport sound and portal particles at departure and arrival.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `range` | float | no | `32.0` | Maximum teleport range in blocks |
| `cooldown_ticks` | int | no | `60` | Cooldown in ticks after each use |
| `mode` | string | no | `"target"` | `"target"` teleports to the looked-at spot; `"random"` makes up to 16 tries at a spot within ±`range` horizontally and ±`range`/8 vertically, accepting the first with two free blocks for the player. It does not check for a floor, so it can land the player in the air. |
| `hunger_cost` | int | no | `0` | Food points removed per use |

**Example:**
```json
{
  "type": "neoorigins:active_teleport",
  "range": 32.0,
  "cooldown_ticks": 60,
  "hunger_cost": 2,
  "name": "Blink",
  "description": "Teleports to where you're looking."
}
```

---

## `neoorigins:active_dash`

Active ability that launches the player in their look direction. With the optional damage fields it also sweeps a capsule along the dash path, hitting every entity it passes through: a charging shoulder-barge or blade dash.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` | float | no | `1.5` | Launch velocity |
| `cooldown_ticks` | int | no | `40` | Cooldown in ticks |
| `allow_vertical` | bool | no | `false` | Whether to include vertical component from look direction |
| `damage` | float | no | `0` | Flat damage dealt to entities along the dash path. The damage sweep runs when either this or `weapon_damage_scale` is above `0` |
| `set_velocity` | bool | no | `false` | When true the dash replaces the player's velocity outright; when false it is added to whatever they already had |
| `damage_radius` | float | no | `2.0` | Radius of the capsule swept along the dash path |
| `weapon_damage_scale` | float | no | `0` | Adds this fraction of the player's attack-damage attribute (the base 1 plus the held weapon and any other bonuses) on top of `damage` (e.g. `1.0` = the whole attribute value) |
| `condition` | condition | no | always true | EntityCondition gating the dash. While it fails the keypress does nothing and no cooldown is spent |

**Example:**
```json
{
  "type": "neoorigins:active_dash",
  "power": 1.5,
  "cooldown_ticks": 40,
  "allow_vertical": true,
  "damage": 4.0,
  "damage_radius": 2.0,
  "weapon_damage_scale": 1.0,
  "name": "Pounce",
  "description": "Dashes in the direction you're looking, savaging anything in the way."
}
```

---

## `neoorigins:active_recall`

Active ability that teleports the player to their bed or respawn point. Falls back to world spawn if none is set.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `cooldown_ticks` | int | no | `600` | Cooldown in ticks |

**Example:**
```json
{
  "type": "neoorigins:active_recall",
  "cooldown_ticks": 600,
  "name": "Home Recall",
  "description": "Teleports to your respawn point."
}
```

---

## `neoorigins:personal_dimension`

**\<Experimental\>** New in 2.2.32: its fields and behaviour may still change in a later release.

Active ability that moves the player in and out of their own box in the `neoorigins:personal_space` dimension. Pressing the key outside the dimension saves the exact spot the player is standing in (dimension, position and facing) and sends them to the centre of their box floor. Pressing it again inside the dimension sends them back to that spot. Only entering starts the cooldown and charges the cost; leaving is always free.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `size` | int | no | `12` | Usable interior of the box: `size` × `size` wide and `size` tall (1 to 64) |
| `cooldown_ticks` | int | no | `1200` | Cooldown in ticks after entering (1 minute); leaving has none |
| `xp_levels_cost` | int | no | `0` | Experience levels taken on each entry; `0` is free |

**Example:**
```json
{
  "type": "neoorigins:personal_dimension",
  "size": 16,
  "cooldown_ticks": 1200,
  "xp_levels_cost": 3,
  "cooldown_icon": "minecraft:ender_chest",
  "name": "Pocket Room",
  "description": "Step into a room of your own, and back out to where you stood."
}
```

**The box.** Each player has one box, shared by every `personal_dimension` power they hold. It is built the first time they enter: a bedrock floor, with barrier walls and a barrier ceiling, all outside the usable space, so the outside of the box is `size` + 2 blocks across. Boxes stand 1024 blocks apart, so no two players' boxes meet. Whatever the player leaves inside stays there between visits. If `size` is raised later, the box grows on the next entry and keeps its contents; if it is lowered, the box keeps its larger size and nothing is removed. Every entry also puts back any floor, wall or ceiling block that is missing, in case another mod removed one. Bedrock and barriers cannot be broken in survival, pushed by pistons or destroyed by explosions.

**Leaving.** The key sends the player back to the saved spot without checking that it is safe. Leaving any other way, by dying, a portal, `/tp` or another mod's teleport, forgets the saved spot. A player in the dimension with no saved spot, for example one brought in by another mod, is sent to their respawn point by the key, or to world spawn if that respawn point is inside the dimension. Logging out inside keeps the player and the saved spot; they log back in inside. Losing the power while inside, through an origin change, an Orb of Origin or a revoke, sends the player back to the saved spot (or their respawn point) straight away.

**Cost.** A player without enough levels for `xp_levels_cost` stays where they are and no cooldown starts. Players in creative pay nothing.

**Other players.** Nothing in the power brings anyone else in, but other mods' teleports can. Anyone in the dimension is an ordinary player there. The key does nothing for a player without the power, and for one with it, it acts on their own saved spot.

**The dimension.** `neoorigins:personal_space` is an empty void with no terrain and no weather, lit by fixed midday sky light. Death inside follows the usual rules: the player respawns at their spawn point and their items drop in the box. No spawn point can be set inside: beds and respawn anchors do nothing there (no sleeping and no explosions), and `/spawnpoint` pointing into the dimension has no effect. The world spawns no mobs there on its own: natural, chunk-generation, structure, patrol, reinforcement and event spawns (raids, wandering traders, sieges) are all refused. Mobs a player brings about still appear: spawners and trial spawners, spawn eggs, breeding, buckets, dispensers, commands, conversions and summons. No built-in origin or class uses this power.

---

## `neoorigins:active_swap`

Active ability that swaps positions with the entity the player is looking at: the nearest living entity on the look ray (hitboxes padded by 0.3). There is no line-of-sight check, so it targets through walls.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `range` | float | no | `20.0` | Maximum range to target an entity |
| `cooldown_ticks` | int | no | `80` | Cooldown in ticks |

**Example:**
```json
{
  "type": "neoorigins:active_swap",
  "range": 20.0,
  "cooldown_ticks": 80,
  "name": "Swap",
  "description": "Swap positions with your target."
}
```

---

## `neoorigins:active_fireball`

Active ability that shoots a spread of 3 to 4 small (blaze-style) fireballs in the player's look direction. Each one sets what it hits on fire; they do not explode.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `speed` | float | no | `1.5` | Projectile speed multiplier |
| `cooldown_ticks` | int | no | `100` | Cooldown in ticks |

**Example:**
```json
{
  "type": "neoorigins:active_fireball",
  "speed": 1.5,
  "cooldown_ticks": 80,
  "name": "Ember Shot",
  "description": "Spits a spray of fireballs in your look direction."
}
```

---

## `neoorigins:active_phase`

Active ability that phases the player through a solid wall in their look direction. Scans forward in 0.5-block steps from half eye height for a spot with a passable block, clear space above and a solid floor below, and teleports there. If none is found within `max_depth` nothing happens and no cooldown is spent. An optional hunger cost is deducted per use.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `max_depth` | int | no | `16` | Maximum solid-block scan depth in blocks |
| `cooldown_ticks` | int | no | `40` | Cooldown in ticks |
| `hunger_cost` | int | no | `0` | Food points removed per use (2 = 1 shank) |

**Example:**
```json
{
  "type": "neoorigins:active_phase",
  "max_depth": 10,
  "cooldown_ticks": 80,
  "hunger_cost": 3,
  "name": "Phase Step",
  "description": "Pass through walls at the cost of hunger."
}
```

---

## `neoorigins:active_bolt`

Active ability that shoots a wind charge in the player's look direction. On impact it makes the vanilla wind burst, knocking back nearby entities.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `speed` | float | no | `1.2` | Projectile speed multiplier |
| `cooldown_ticks` | int | no | `80` | Cooldown in ticks |

**Example:**
```json
{
  "type": "neoorigins:active_bolt",
  "speed": 1.2,
  "cooldown_ticks": 80,
  "name": "Gale Bolt",
  "description": "Fires a wind charge that blasts nearby creatures back."
}
```

---

## `neoorigins:active_place_block`

Active ability that places a specific block at the surface the player is looking at, up to a maximum range.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `block_id` | Identifier | no | `minecraft:glowstone` | Block to place |
| `max_distance` | float | no | `5.0` | Maximum targeting range in blocks |
| `cooldown_ticks` | int | no | `100` | Cooldown in ticks |
| `hunger_cost` | int | no | `0` | Food points removed per use |

**Example:**
```json
{
  "type": "neoorigins:active_place_block",
  "block_id": "minecraft:glowstone",
  "max_distance": 5.0,
  "cooldown_ticks": 100,
  "name": "Stone Touch",
  "description": "Places a glowstone block where you're looking."
}
```

---

## `neoorigins:ground_slam`

Active AoE slam: damages and knocks back every living entity in a box of `radius` around the player, including allies and pets. If nothing is in range, no cooldown is spent.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `damage` | float | no | `6.0` | Damage dealt to each entity |
| `knockback_strength` | float | no | `1.5` | Radial knockback strength |
| `radius` | double | no | `6.0` | Effect radius in blocks |
| `cooldown_ticks` | int | no | `120` | Cooldown after each use |

**Example:**
```json
{
  "type": "neoorigins:ground_slam",
  "damage": 6.0,
  "radius": 6.0,
  "cooldown_ticks": 120,
  "name": "Tectonic Slam",
  "description": "Crushes nearby enemies with a shockwave."
}
```

---

## `neoorigins:tidal_wave`

Cone-shaped water blast in the player's look direction. Knocks back and damages entities caught in the wave.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `damage` | float | no | `4.0` | Damage per hit |
| `knockback_strength` | float | no | `2.0` | Forward knockback strength |
| `range` | double | no | `8.0` | Maximum wave distance |
| `cone_angle` | double | no | `60.0` | Total cone angle in degrees (30° to each side of look axis) |
| `cooldown_ticks` | int | no | `100` | Cooldown after each use |
| `hunger_cost` | int | no | `0` | Food points removed per use |

**Example:**
```json
{
  "type": "neoorigins:tidal_wave",
  "damage": 4.0,
  "range": 8.0,
  "cone_angle": 60.0,
  "cooldown_ticks": 100,
  "name": "Tidal Wave",
  "description": "Blasts enemies with a cone of water."
}
```

---

## `neoorigins:shadow_orb`

Active ability that places a persistent shadow orb at the player's position. Each orb applies Darkness and Blindness to nearby entities at a set interval, skipping the caster and their team allies. Orbs only pulse while their owner is in the same dimension, and placing an orb in a new dimension clears the old ones. The player can maintain up to `max_orbs` orbs at once; placing a new one when at the cap removes the oldest. Orbs are cleared when the origin is revoked.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `max_orbs` | int | no | `4` | Maximum number of simultaneous orbs |
| `radius` | float | no | `28.0` | Effect radius per orb in blocks |
| `cooldown_ticks` | int | no | `100` | Cooldown between placements |
| `tick_interval` | int | no | `20` | Ticks between pulses per orb |
| `hunger_cost` | int | no | `0` | Food points removed per placement |

**Example:**
```json
{
  "type": "neoorigins:shadow_orb",
  "max_orbs": 4,
  "radius": 28.0,
  "cooldown_ticks": 100,
  "tick_interval": 20,
  "name": "Shadow Anchor",
  "description": "Places orbs that shroud the area in darkness."
}
```
