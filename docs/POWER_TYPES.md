# NeoOrigins Power Types Reference

All powers share two optional display fields:

| Field | Type | Description |
|---|---|---|
| `name` | string or `{"text":"..."}` / `{"translate":"..."}` | Display name shown in the origin selection screen. A plain string is treated as a translation key. |
| `description` | string or `{"text":"..."}` / `{"translate":"..."}` | Description shown below the power name. Same resolution rules as `name`. |

If neither field is present, NeoOrigins falls back to the lang key convention:
`power.<namespace>.<path>.name` / `power.<namespace>.<path>.description`

---

## `neoorigins:attribute_modifier`

Adds or multiplies a player attribute while the origin is active. Optionally gated on an environment condition, an equipped-item condition, or both (AND).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `attribute` | Identifier | yes | — | Attribute to modify, e.g. `minecraft:generic.movement_speed` |
| `amount` | double | yes | — | Amount to add or multiply |
| `operation` | string | no | `add_value` | `add_value`, `add_multiplied_base`, or `add_multiplied_total` |
| `condition` | string | no | — | Environment gate: `in_water`, `on_land`, or `in_lava`. Tick-driven apply/remove. |
| `equipment_condition` | object | no | — | Equipment gate (see below). Tick-driven apply/remove. |
| `location_condition` | object | no | — | Location gate — dimension / biome / structure (see below). Tick-driven apply/remove. |

**Operations:**
- `add_value` — flat addition to base value
- `add_multiplied_base` — adds `base * amount` (e.g. `-0.1` = 10% slower)
- `add_multiplied_total` — multiplies total after all other modifiers

**`equipment_condition` object:**

| Field | Type | Required | Description |
|---|---|---|---|
| `slot` | string | yes | One of `mainhand`, `offhand`, `head`, `chest`, `legs`, `feet`, `body` |
| `item` | Identifier | no | Exact item ID to match (e.g. `minecraft:iron_helmet`) |
| `tag` | Identifier | no | Item tag to match (e.g. `minecraft:helmets`) |

If both `item` and `tag` are given, either match satisfies the condition (OR). If neither is given, any non-empty stack in the slot counts as a match. When multiple of `condition` / `equipment_condition` / `location_condition` are set, **all** must hold for the modifier to apply.

**`location_condition` object:**

| Field | Type | Required | Description |
|---|---|---|---|
| `dimension` | Identifier | no | Match only in this dimension, e.g. `minecraft:the_end` |
| `biome` | Identifier | no | Match only in this specific biome, e.g. `minecraft:plains` |
| `biome_tag` | Identifier | no | Match only in biomes with this tag, e.g. `minecraft:is_forest` |
| `structure` | Identifier | no | Match only inside this structure, e.g. `minecraft:end_city` |
| `structure_tag` | Identifier | no | Match only inside structures with this tag, e.g. `minecraft:on_ocean_monument_maps` |

All fields are optional and combine with AND. So `{ "dimension": "minecraft:the_end", "structure": "minecraft:end_city" }` is "only when standing inside an End City in The End." Structure membership is evaluated server-side via `ServerLevel.structureManager()`.

**Example — 8 flat armor (unconditional):**
```json
{
  "type": "neoorigins:attribute_modifier",
  "attribute": "minecraft:generic.armor",
  "amount": 8.0,
  "operation": "add_value",
  "name": "Shell",
  "description": "Has permanent natural armor."
}
```

**Example — slower on land only:**
```json
{
  "type": "neoorigins:attribute_modifier",
  "attribute": "minecraft:generic.movement_speed",
  "amount": -0.25,
  "operation": "add_multiplied_base",
  "condition": "on_land",
  "name": "Landwalker",
  "description": "Moves slower while out of water."
}
```

**Example — +1 attack when wearing any helmet:**
```json
{
  "type": "neoorigins:attribute_modifier",
  "attribute": "minecraft:generic.attack_damage",
  "amount": 1.0,
  "equipment_condition": {
    "slot": "head",
    "tag": "minecraft:helmets"
  },
  "name": "Helm of Valor",
  "description": "Empowered while helmeted."
}
```

**Example — +2 armor only inside End Cities:**
```json
{
  "type": "neoorigins:attribute_modifier",
  "attribute": "minecraft:generic.armor",
  "amount": 2.0,
  "location_condition": {
    "dimension": "minecraft:the_end",
    "structure": "minecraft:end_city"
  },
  "name": "Void Ward",
  "description": "Armored while within the towers of the End."
}
```

**Useful attributes:**
- `minecraft:generic.movement_speed` — walk speed (base ≈ 0.1)
- `minecraft:generic.swimming_speed` — swim speed
- `minecraft:generic.armor` — armor points
- `minecraft:generic.armor_toughness` — armor toughness
- `minecraft:generic.attack_damage` — melee damage
- `minecraft:generic.attack_speed` — attack cooldown speed
- `minecraft:generic.max_health` — max HP
- `minecraft:generic.fall_damage_multiplier` — fall damage scale

---

## `neoorigins:status_effect`

> **Deprecated in 2.0** — this type is now an alias for `neoorigins:persistent_effect`. See [MIGRATION.md](MIGRATION.md).

Continuously applies a potion effect while the origin is active. The effect is refreshed every tick and removed when the origin is revoked.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effect` | Identifier | yes | — | Effect ID, e.g. `minecraft:strength` |
| `amplifier` | int | no | `0` | Effect level (0 = level I, 1 = level II, …) |
| `ambient` | bool | no | `true` | Whether the effect is ambient (reduced particle visibility) |
| `show_particles` | bool | no | `false` | Whether to show particles |

**Example — permanent Strength I:**
```json
{
  "type": "neoorigins:status_effect",
  "effect": "minecraft:strength",
  "amplifier": 0,
  "name": "Brute Strength",
  "description": "Permanently empowered by inner fire."
}
```

**Common effects:** `minecraft:strength`, `minecraft:speed`, `minecraft:haste`, `minecraft:regeneration`, `minecraft:resistance`, `minecraft:fire_resistance`, `minecraft:water_breathing`, `minecraft:night_vision`, `minecraft:jump_boost`, `minecraft:slow_falling`

---

## `neoorigins:prevent_action`

Prevents a specific harmful action or event from affecting the player.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `action` | string | yes | — | The action to prevent (see values below) |

**Action values:**

| Value | What it prevents |
|---|---|
| `fire` | All fire and lava damage |
| `fall_damage` | All fall damage |
| `drown` | Drowning damage |
| `freeze` | Freeze damage from powder snow |
| `sprint_food` | Sprinting no longer drains extra hunger |
| `chestplate_equip` | Prevents wearing chestplate armor |
| `eye_damage` | Prevents projectile hits to the eye |
| `water_damage` | Prevents water/rain contact damage |
| `swim` | Prevents swimming (velocity-sinks the player in water) |
| `sleep` | Prevents sleeping (bed use returns a "no sleep" message). Sleepless origins should pair this with `modify_player_spawn` if they still want bed interactions to set respawn. |

**Example — fire immunity:**
```json
{
  "type": "neoorigins:prevent_action",
  "action": "fire",
  "name": "Fire Immunity",
  "description": "Immune to fire and lava."
}
```

---

## `neoorigins:modify_damage`

Multiplies damage dealt or received, optionally filtered to a specific damage type and, for `direction: out`, restricted to a target entity group.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `direction` | string | no | `in` | `in` (damage received) or `out` (damage dealt) |
| `multiplier` | float | no | `1.0` | Damage multiplier (e.g. `2.0` = double, `0.5` = half) |
| `damage_type` | string | no | _(all types)_ | Optional vanilla damage type to filter, e.g. `drown`, `fire`, `fall` |
| `target_group` | string | no | _(any)_ | Outgoing only. Restrict to targets in this entity group: `undead`, `arthropod`, `illager`, `aquatic`. Resolved as the vanilla `minecraft:<group>` entity-type tag. |

**Example — double incoming water damage:**
```json
{
  "type": "neoorigins:modify_damage",
  "direction": "in",
  "multiplier": 2.5,
  "damage_type": "drown",
  "name": "Water Weakness",
  "description": "Takes extra damage from water."
}
```

**Example — bonus fire damage dealt:**
```json
{
  "type": "neoorigins:modify_damage",
  "direction": "out",
  "multiplier": 1.5,
  "damage_type": "fire",
  "name": "Fire Mastery",
  "description": "Deals extra fire damage."
}
```

**Example — +50% damage to undead:**
```json
{
  "type": "neoorigins:modify_damage",
  "direction": "out",
  "multiplier": 1.5,
  "target_group": "undead",
  "name": "Smite",
  "description": "Strikes the undead harder."
}
```

**Common damage types:** `fire`, `in_fire`, `lava`, `drown`, `fall`, `freeze`, `magic`, `wither`, `lightning_bolt`, `generic`

---

## `neoorigins:flight`

Grants the player creative-style free flight. The player can fly freely at any time without an elytra.

No additional fields beyond `name` and `description`.

**Example:**
```json
{
  "type": "neoorigins:flight",
  "name": "Natural Flight",
  "description": "Can fly freely without an elytra."
}
```

---

## `neoorigins:night_vision`

> **Deprecated in 2.0** — this type is now an alias for `neoorigins:persistent_effect`. See [MIGRATION.md](MIGRATION.md).

Grants permanent Night Vision at full strength (no ambient effect, no particles). The effect is refreshed every tick.

No additional fields beyond `name` and `description`.

**Example:**
```json
{
  "type": "neoorigins:night_vision",
  "name": "Dark Vision",
  "description": "Can see clearly in total darkness."
}
```

---

## `neoorigins:water_breathing`

Grants permanent Water Breathing. The player never loses air while underwater.

No additional fields beyond `name` and `description`.

**Example:**
```json
{
  "type": "neoorigins:water_breathing",
  "name": "Gills",
  "description": "Can breathe underwater indefinitely."
}
```

---

## `neoorigins:no_slowdown`

Prevents the player from being slowed down by specific blocks (cobwebs, berry bushes, etc.).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `block_tag` | string | no | _(all slowdown blocks)_ | Restrict immunity to blocks in this tag |

**Example — immune to all block slowdown:**
```json
{
  "type": "neoorigins:no_slowdown",
  "name": "Unimpeded",
  "description": "Not slowed by cobwebs or dense foliage."
}
```

---

## `neoorigins:wall_climbing`

Allows the player to cling to and climb any solid wall surface, similar to spiders. Fall damage is suppressed while climbing.

No additional fields beyond `name` and `description`.

**Example:**
```json
{
  "type": "neoorigins:wall_climbing",
  "name": "Wall Climbing",
  "description": "Can scale any solid surface."
}
```

---

## `neoorigins:elytra_boost`

Allows the player to activate elytra gliding without wearing an elytra. Pressing jump while falling initiates flight.

No additional fields beyond `name` and `description`.

**Example:**
```json
{
  "type": "neoorigins:elytra_boost",
  "name": "Elytra Boost",
  "description": "Can glide without equipping an elytra."
}
```

---

## `neoorigins:scare_entities`

Causes listed entity types to flee from the player on sight.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_types` | list of Identifier | no | `[]` | Entity types to scare, e.g. `["minecraft:creeper"]` |

**Example — creepers flee from the player:**
```json
{
  "type": "neoorigins:scare_entities",
  "entity_types": ["minecraft:creeper", "minecraft:spider"],
  "name": "Predator Aura",
  "description": "Hostile arthropods flee on sight."
}
```

---

## `neoorigins:tick_action`

Runs a named action on a repeating interval while the origin is active.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `interval` | int | no | `20` | Ticks between action executions (20 ticks = 1 second) |
| `action_type` | string | no | `none` | Action to perform each interval. Currently: `teleport_on_damage`, `none` |

**Example — teleport to safety on damage every 2 seconds:**
```json
{
  "type": "neoorigins:tick_action",
  "interval": 40,
  "action_type": "teleport_on_damage",
  "name": "Dimensional Escape",
  "description": "Teleports away when taking damage."
}
```

---

## `neoorigins:conditional`

Wraps another power so it only applies when a movement condition is met. The inner power must be a separately defined power that is also listed in the origin's power list.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `condition` | string | no | `always` | `climbing`, `in_water`, `on_ground`, or `always` |
| `inner_power` | Identifier | yes | — | The power to conditionally enable |

**Example — no fall damage only while climbing:**
```json
{
  "type": "neoorigins:conditional",
  "condition": "climbing",
  "inner_power": "examplepack:specter_no_fall_base",
  "name": "Spider's Grip",
  "description": "Takes no fall damage while clinging to walls."
}
```

> The `inner_power` must also be listed in the origin's `powers` array for it to be registered. The conditional wrapper activates or suppresses its effect based on the condition.

---

## `neoorigins:phantom_form`

Applies a ghostly state to the player: permanent invisibility and/or removal of gravity.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `invisibility` | bool | no | `true` | Apply permanent Invisibility effect |
| `no_gravity` | bool | no | `true` | Disable gravity (player floats) |

**Example:**
```json
{
  "type": "neoorigins:phantom_form",
  "invisibility": true,
  "no_gravity": false,
  "name": "Phantom Form",
  "description": "Becomes invisible but still subject to gravity."
}
```

---

## `neoorigins:effect_immunity`

Prevents listed potion effects from being applied to the player. Uses NeoForge's `MobEffectEvent.Applicable` to cancel before application.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effects` | list of string | yes | — | Effect IDs to block, e.g. `["minecraft:poison"]` |

**Example:**
```json
{
  "type": "neoorigins:effect_immunity",
  "effects": ["minecraft:poison", "minecraft:wither"],
  "name": "Toxin Resistance",
  "description": "Immune to poison and wither effects."
}
```

---

## `neoorigins:glow`

> **Deprecated in 2.0** — this type is now an alias for `neoorigins:persistent_effect`. See [MIGRATION.md](MIGRATION.md).

Applies a permanent Glowing effect to the player (visible outline through walls for other players).

No additional fields beyond `name` and `description`.

**Example:**
```json
{
  "type": "neoorigins:glow",
  "name": "Bioluminescence",
  "description": "Emits a faint glow visible to others."
}
```

---

## `neoorigins:damage_in_daylight`

> **Deprecated in 2.0** — this type is now an alias for `neoorigins:condition_passive`. See [MIGRATION.md](MIGRATION.md).

Deals periodic damage to the player when they are in direct sunlight (sky-exposed, not in shade or water).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `damage` | float | no | `1.0` | Damage dealt per interval (half-hearts) |
| `interval_ticks` | int | no | `20` | How often damage is applied (ticks) |

**Example:**
```json
{
  "type": "neoorigins:damage_in_daylight",
  "damage": 1.0,
  "interval_ticks": 20,
  "name": "Sun Allergy",
  "description": "Burns in direct sunlight."
}
```

---

## `neoorigins:knockback_modifier`

> **Deprecated in 2.0** — this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](MIGRATION.md).

Multiplies knockback dealt or received.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `direction` | string | no | `out` | `out` (knockback dealt) or `in` (knockback received) |
| `multiplier` | float | no | `1.0` | Knockback multiplier |

**Example — halve knockback taken:**
```json
{
  "type": "neoorigins:knockback_modifier",
  "direction": "in",
  "multiplier": 0.5,
  "name": "Sturdy",
  "description": "Resistant to knockback."
}
```

---

## `neoorigins:xp_gain_modifier`

Multiplies experience points gained. Currently registered but inert — no NeoForge XP gain event exists in 21.11.38.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `multiplier` | float | no | `1.0` | XP gain multiplier |

**Example:**
```json
{
  "type": "neoorigins:xp_gain_modifier",
  "multiplier": 1.5,
  "name": "Quick Learner",
  "description": "Gains experience faster."
}
```

---

## `neoorigins:underwater_mining_speed`

Removes the normal mining speed penalty for being submerged in water. The player mines at full speed underwater.

No additional fields beyond `name` and `description`.

**Example:**
```json
{
  "type": "neoorigins:underwater_mining_speed",
  "name": "Aquatic Miner",
  "description": "Mines at full speed underwater."
}
```

---

## `neoorigins:hunger_drain_modifier`

> **Deprecated in 2.0** — this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](MIGRATION.md).

Multiplies the rate at which the player's hunger depletes.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `multiplier` | float | no | `1.0` | Hunger drain multiplier (`1.3` = 30% faster, `0.5` = half rate) |

**Example:**
```json
{
  "type": "neoorigins:hunger_drain_modifier",
  "multiplier": 1.3,
  "name": "High Metabolism",
  "description": "Gets hungry faster than normal."
}
```

---

## `neoorigins:natural_regen_modifier`

> **Deprecated in 2.0** — this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](MIGRATION.md).

Multiplies all healing the player receives via `LivingHealEvent`. **Note:** this is *not* limited to food-tick natural regen — it also scales Regeneration potion ticks, beacon regen, totem of undying, and any data-pack `origins:heal` actions. If you want to cancel food-tick regen specifically while leaving other heals intact, use `neoorigins:no_natural_regen` instead.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `multiplier` | float | no | `1.0` | Heal multiplier (`0.5` = half all heals, `2.0` = double all heals, `0.0` = block all heals) |

**Example:**
```json
{
  "type": "neoorigins:natural_regen_modifier",
  "multiplier": 0.5,
  "name": "Slow Recovery",
  "description": "Heals at half the normal rate."
}
```

---

## `neoorigins:no_natural_regen`

Cancels vanilla food-based natural regeneration only. Both regen branches (saturation-based fast regen and food-level-≥18 slow regen) are skipped. Other heal sources — Regeneration potion, beacon, totem, `origins:heal` — still work normally. Pair with an alternate healing mechanic for "metabolism-less" origins like Automaton variants.

No fields. Presence of the power is the entire configuration.

**Example:**
```json
{
  "type": "neoorigins:no_natural_regen",
  "name": "No Pulse",
  "description": "Hunger doesn't restore HP. Only direct healing effects do."
}
```

---

## `neoorigins:food_restriction`

> **Deprecated in 2.0** — this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](MIGRATION.md).

Restricts which foods the player can eat to a specific item tag. Attempting to eat an item not in the tag cancels consumption.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `allowed_tag` | Identifier | yes | — | Item tag of foods the player is allowed to eat, e.g. `minecraft:meat` |

**Example — meat-only diet:**
```json
{
  "type": "neoorigins:food_restriction",
  "allowed_tag": "minecraft:meat",
  "name": "Carnivore",
  "description": "Can only eat meat."
}
```

---

## `neoorigins:item_magnetism`

Items on the ground within a radius are pulled toward the player automatically.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | float | no | `4.0` | Pull radius in blocks |

**Example:**
```json
{
  "type": "neoorigins:item_magnetism",
  "radius": 4.0,
  "name": "Item Attraction",
  "description": "Items on the ground are drawn toward you."
}
```

---

## `neoorigins:break_speed_modifier`

Multiplies the player's mining speed when breaking blocks in the specified tag. When no tag is provided, applies to all blocks.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `block_tag` | Identifier | no | _(all blocks)_ | Block tag to restrict the modifier to, e.g. `minecraft:stone` |
| `multiplier` | float | no | `1.0` | Speed multiplier (`2.0` = double speed) |

**Example — 2× speed on stone and deepslate:**
```json
{
  "type": "neoorigins:break_speed_modifier",
  "block_tag": "minecraft:stone",
  "multiplier": 2.0,
  "name": "Stone Affinity",
  "description": "Mines stone and deepslate twice as fast."
}
```

---

## `neoorigins:thorns_aura`

> **Deprecated in 2.0** — this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](MIGRATION.md).

Reflects a portion of incoming melee damage back to the attacker.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `reflect_ratio` | float | no | `0.5` | Fraction of damage dealt back (0.5 = 50%) |

**Example:**
```json
{
  "type": "neoorigins:thorns_aura",
  "reflect_ratio": 0.5,
  "name": "Thorny Hide",
  "description": "Reflects half of incoming melee damage."
}
```

---

## `neoorigins:projectile_immunity`

Prevents all incoming projectile damage (arrows, tridents, fireballs, etc.).

No additional fields beyond `name` and `description`.

**Example:**
```json
{
  "type": "neoorigins:projectile_immunity",
  "name": "Arrow Deflection",
  "description": "Immune to all projectile damage."
}
```

---

## `neoorigins:action_on_kill`

> **Deprecated in 2.0** — this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](MIGRATION.md).

Triggers an action each time the player kills a living entity.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `action` | string | no | `restore_health` | Action to perform: `restore_health`, `restore_hunger`, `grant_effect` |
| `amount` | float | no | `4.0` | Health or hunger to restore |
| `effect` | Identifier | no | — | Effect to grant (when `action` is `grant_effect`) |
| `amplifier` | int | no | `0` | Effect level |
| `duration` | int | no | `200` | Effect duration in ticks |

**Example — restore 1 heart on kill:**
```json
{
  "type": "neoorigins:action_on_kill",
  "action": "restore_health",
  "amount": 2.0,
  "name": "Vampiric",
  "description": "Restores health by killing enemies."
}
```

---

## `neoorigins:action_on_hit_taken`

> **Deprecated in 2.0** — this type is now an alias for `neoorigins:action_on_event`. See [MIGRATION.md](MIGRATION.md).

Triggers an action each time the player takes damage.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `action` | string | no | `teleport` | Action to perform: `teleport`, `restore_health`, `restore_hunger`, `grant_effect`, `ignite_attacker`, `effect_on_attacker` |
| `min_damage` | float | no | `0.0` | Only fires when incoming damage ≥ this |
| `chance` | float | no | `1.0` | Probability to fire, from 0.0 to 1.0 |
| `amount` | float | no | `2.0` | Health or hunger to restore (where applicable) |
| `effect` | Identifier | no | — | Effect to apply (for `grant_effect` / `effect_on_attacker`) |
| `amplifier` | int | no | `0` | Effect level |
| `duration` | int | no | `100` | Effect duration in ticks |

**Example — gain Speed II briefly when hit:**
```json
{
  "type": "neoorigins:action_on_hit_taken",
  "action": "grant_effect",
  "effect": "minecraft:speed",
  "amplifier": 1,
  "duration": 60,
  "name": "Combat Rush",
  "description": "Gains a burst of speed when struck."
}
```

---

## `neoorigins:action_on_hit`

Triggers an action each time the player deals damage to a living entity, optionally restricted by target entity group, target entity type, or damage type. The configured `action` may target the player (self) or the victim.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `action` | string | no | `restore_health` | One of `restore_health`, `restore_hunger`, `grant_effect` (self), `target_effect` (victim) |
| `amount` | float | no | `2.0` | Health or hunger amount (for the restore actions) |
| `effect` | Identifier | no | — | Effect to apply for `grant_effect` / `target_effect` |
| `duration` | int | no | `100` | Effect duration in ticks |
| `amplifier` | int | no | `0` | Effect level |
| `min_damage` | float | no | `0.0` | Only fires when outgoing damage ≥ this |
| `chance` | float | no | `1.0` | Probability to fire, from 0.0 to 1.0 |
| `target_group` | string | no | _(any)_ | Restrict to targets in group: `undead`, `arthropod`, `illager`, `aquatic` (vanilla `minecraft:<group>` entity-type tag) |
| `target_type` | Identifier | no | _(any)_ | Restrict to a specific entity type, e.g. `minecraft:zombie` |
| `damage_type` | string | no | _(all)_ | Restrict to a specific vanilla damage type, e.g. `mob_attack`, `magic` |

Filters are combined with AND: every configured filter must match for the action to fire. The `chance` roll happens last.

**Example — heal 0.5 hearts on striking any undead:**
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

**Example — mark the victim with Glowing when striking undead:**
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

**Example — 20% chance to gain Strength I on hitting a zombie, on melee only:**
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

---

## `neoorigins:regen_in_fluid`

> **Deprecated in 2.0** — this type is now an alias for `neoorigins:condition_passive`. See [MIGRATION.md](MIGRATION.md).

Grants periodic health regeneration while submerged in the specified fluid.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `fluid` | string | no | `water` | Fluid to trigger in: `water` or `lava` |
| `heal_amount` | float | no | `1.0` | Health restored per interval |
| `interval_ticks` | int | no | `40` | Ticks between heal pulses |

**Example:**
```json
{
  "type": "neoorigins:regen_in_fluid",
  "fluid": "water",
  "heal_amount": 1.0,
  "interval_ticks": 40,
  "name": "Aquatic Regeneration",
  "description": "Regenerates health while underwater."
}
```

---

## `neoorigins:breath_in_fluid`

Drains the player's air supply when submerged in the specified fluid. Useful for fire-themed origins that "drown" in water.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `fluid` | string | no | `water` | Fluid: `water` or `lava` |
| `drain_rate` | int | no | `20` | Air drained per tick while submerged |

**Example:**
```json
{
  "type": "neoorigins:breath_in_fluid",
  "fluid": "water",
  "drain_rate": 20,
  "name": "Hydrophobic",
  "description": "Cannot breathe in water."
}
```

---

## `neoorigins:biome_buff`

> **Deprecated in 2.0** — this type is now an alias for `neoorigins:condition_passive`. See [MIGRATION.md](MIGRATION.md).

Applies a status effect while the player is standing in a biome matching the given biome tag.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `biome_tag` | Identifier | yes | — | Biome tag, e.g. `minecraft:is_forest` |
| `effect` | Identifier | yes | — | Effect to apply, e.g. `minecraft:speed` |
| `amplifier` | int | no | `0` | Effect level |

**Example — Speed I in forests:**
```json
{
  "type": "neoorigins:biome_buff",
  "biome_tag": "minecraft:is_forest",
  "effect": "minecraft:speed",
  "amplifier": 0,
  "name": "Forest Stride",
  "description": "Moves faster in forest biomes."
}
```

---

## `neoorigins:damage_in_biome`

> **Deprecated in 2.0** — this type is now an alias for `neoorigins:condition_passive`. See [MIGRATION.md](MIGRATION.md).

Deals periodic damage to the player while in a biome matching the given tag.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `biome_tag` | Identifier | yes | — | Biome tag to match, e.g. `minecraft:is_nether` |
| `damage` | float | no | `1.0` | Damage per interval |
| `interval_ticks` | int | no | `40` | Ticks between damage pulses |

**Example:**
```json
{
  "type": "neoorigins:damage_in_biome",
  "biome_tag": "minecraft:is_nether",
  "damage": 1.0,
  "interval_ticks": 40,
  "name": "Nether Intolerance",
  "description": "Takes damage in the Nether."
}
```

---

## `neoorigins:burn_at_health_threshold`

> **Deprecated in 2.0** — this type is now an alias for `neoorigins:condition_passive`. See [MIGRATION.md](MIGRATION.md).

Ignites the player when their HP drops below a percentage of their maximum health.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `threshold_percent` | float | no | `0.25` | HP fraction that triggers ignition (e.g. `0.25` = below 25%) |
| `fire_ticks` | int | no | `60` | Duration the player is set on fire |

**Example:**
```json
{
  "type": "neoorigins:burn_at_health_threshold",
  "threshold_percent": 0.25,
  "fire_ticks": 60,
  "name": "Critical Combustion",
  "description": "Catches fire when near death."
}
```

---

## `neoorigins:mobs_ignore_player`

Causes specific mob types to passively ignore the player unless provoked.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_types` | list of Identifier | no | `[]` | Entity types that will ignore the player. When empty, all hostile mobs ignore. |

**Example — only creepers ignore:**
```json
{
  "type": "neoorigins:mobs_ignore_player",
  "entity_types": ["minecraft:creeper"],
  "name": "Creeper Affinity",
  "description": "Creepers ignore you unless attacked."
}
```

> This is distinct from `scare_entities` (which makes mobs flee). `mobs_ignore_player` makes mobs neutral; `scare_entities` makes them run away.

---

## `neoorigins:no_mob_spawns_nearby`

Suppresses hostile mob spawns within a radius of the player.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | float | no | `16.0` | Radius in blocks within which spawns are suppressed |

**Example:**
```json
{
  "type": "neoorigins:no_mob_spawns_nearby",
  "radius": 16.0,
  "name": "Warden's Presence",
  "description": "Hostile mobs don't spawn nearby."
}
```

---

## `neoorigins:entity_group`

Changes the player's entity group, making them treated as a different creature class by game mechanics. Undead players become immune to poison and wither but take extra damage from smite.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `group` | string | no | `undead` | Entity group: `undead`, `arthropod`, `illager`, `water`, `default` |

**Example:**
```json
{
  "type": "neoorigins:entity_group",
  "group": "undead",
  "name": "Undead Nature",
  "description": "Treated as an undead creature — immune to poison and wither."
}
```

---

## `neoorigins:active_teleport`

Active ability that teleports the player to the block they are looking at, up to a maximum distance.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `max_distance` | float | no | `32.0` | Maximum teleport range in blocks |
| `cooldown_ticks` | int | no | `60` | Cooldown in ticks after each use |
| `hunger_cost` | int | no | `0` | Food points removed per use |

**Example:**
```json
{
  "type": "neoorigins:active_teleport",
  "max_distance": 32.0,
  "cooldown_ticks": 60,
  "hunger_cost": 2,
  "name": "Blink",
  "description": "Teleports to where you're looking."
}
```

---

## `neoorigins:active_dash`

Active ability that launches the player in their look direction.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` | float | no | `1.5` | Launch velocity |
| `cooldown_ticks` | int | no | `40` | Cooldown in ticks |
| `allow_vertical` | bool | no | `false` | Whether to include vertical component from look direction |

**Example:**
```json
{
  "type": "neoorigins:active_dash",
  "power": 1.5,
  "cooldown_ticks": 40,
  "allow_vertical": true,
  "name": "Pounce",
  "description": "Dashes in the direction you're looking."
}
```

---

## `neoorigins:active_launch`

> **Deprecated in 2.0** — this type is now an alias for `neoorigins:active_ability`. See [MIGRATION.md](MIGRATION.md).

Active ability that launches the player straight upward. Useful paired with `elytra_boost` or `flight` for vertical take-off.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` | float | no | `1.8` | Upward launch velocity |
| `cooldown_ticks` | int | no | `60` | Cooldown in ticks |

**Example:**
```json
{
  "type": "neoorigins:active_launch",
  "power": 2.2,
  "cooldown_ticks": 80,
  "name": "Pounce Launch",
  "description": "Launches upward with powerful legs."
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

## `neoorigins:active_swap`

Active ability that swaps positions with the entity the player is looking at.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `max_distance` | float | no | `16.0` | Maximum range to target an entity |
| `cooldown_ticks` | int | no | `80` | Cooldown in ticks |

**Example:**
```json
{
  "type": "neoorigins:active_swap",
  "max_distance": 16.0,
  "cooldown_ticks": 80,
  "name": "Swap",
  "description": "Swap positions with your target."
}
```

---

## `neoorigins:active_fireball`

Active ability that shoots a small fireball in the player's look direction. The fireball explodes on impact and ignites the area.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `speed` | float | no | `1.5` | Projectile speed multiplier |
| `cooldown_ticks` | int | no | `80` | Cooldown in ticks |

**Example:**
```json
{
  "type": "neoorigins:active_fireball",
  "speed": 1.5,
  "cooldown_ticks": 80,
  "name": "Ember Shot",
  "description": "Spits a fireball in your look direction."
}
```

---

## `neoorigins:crop_growth_accelerator`

Passively accelerates the growth of nearby crops by randomly applying a bonemeal tick to eligible blocks at a set interval.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | int | no | `4` | Scan radius in blocks |
| `tick_interval` | int | no | `40` | Ticks between growth attempts |
| `growths_per_interval` | int | no | `1` | Number of crops to accelerate per interval |

**Example:**
```json
{
  "type": "neoorigins:crop_growth_accelerator",
  "radius": 4,
  "tick_interval": 40,
  "growths_per_interval": 2,
  "name": "Verdant Touch",
  "description": "Crops nearby grow faster."
}
```

---

## `neoorigins:active_aoe_effect`

> **Deprecated in 2.0** — this type is now an alias for `neoorigins:active_ability`. See [MIGRATION.md](MIGRATION.md).

Active ability that applies a mob effect to all living entities within a radius of the player.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effect` | Identifier | yes | — | Effect to apply, e.g. `minecraft:slowness` |
| `amplifier` | int | no | `0` | Effect level |
| `duration_ticks` | int | no | `100` | Duration of the applied effect |
| `radius` | float | no | `8.0` | Range in blocks |
| `cooldown_ticks` | int | no | `200` | Cooldown in ticks |

**Example — root all nearby mobs:**
```json
{
  "type": "neoorigins:active_aoe_effect",
  "effect": "minecraft:slowness",
  "amplifier": 5,
  "duration_ticks": 80,
  "radius": 6.0,
  "cooldown_ticks": 200,
  "name": "Entangle",
  "description": "Roots all nearby creatures in place."
}
```

---

## `neoorigins:active_phase`

Active ability that phases the player through a solid wall in their look direction. Scans forward for an air gap on the far side of a wall and teleports there. An optional hunger cost is deducted per use.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `max_depth` | int | no | `8` | Maximum solid-block scan depth in blocks |
| `cooldown_ticks` | int | no | `80` | Cooldown in ticks |
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

Active ability that shoots a dragon-fire bolt (purple ender-breath projectile) in the player's look direction. On impact, creates an area of dragon's breath that deals damage over time.

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
  "name": "Void Bolt",
  "description": "Fires a bolt of corrosive dragon's breath."
}
```

---

## `neoorigins:starting_equipment`

Grants the player a specific item (optionally with enchantments) once on first origin assignment. Tracks grant state per-player so the item is not re-granted on respawn.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `grant_id` | string | yes | — | Unique ID used to track whether the item has been granted |
| `item` | Identifier | yes | — | Item to grant, e.g. `minecraft:trident` |
| `count` | int | no | `1` | Stack count |
| `enchantments` | list | no | `[]` | List of `{"id": "...", "level": N}` enchantment entries |

**Example — enchanted trident:**
```json
{
  "type": "neoorigins:starting_equipment",
  "grant_id": "abyssal_trident",
  "item": "minecraft:trident",
  "enchantments": [
    {"id": "minecraft:mending", "level": 1},
    {"id": "minecraft:unbreaking", "level": 3},
    {"id": "minecraft:riptide", "level": 3}
  ],
  "name": "Deep-Sea Armament",
  "description": "Begins life with an enchanted trident."
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

## `neoorigins:crop_harvest_bonus`

Passively grants extra item drops when the player harvests a fully-grown crop or breaks a log. The bonus drops are identical copies of the block's normal drops.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `extra_drops` | int | no | `1` | Number of extra drop copies per break |

**Example:**
```json
{
  "type": "neoorigins:crop_harvest_bonus",
  "extra_drops": 1,
  "name": "Bountiful Harvest",
  "description": "Crops and trees yield extra resources."
}
```

---

## `neoorigins:shadow_orb`

Active ability that places a persistent shadow orb at the player's position. Each orb applies Darkness to all nearby entities at a set interval. The player can maintain up to `max_orbs` orbs at once; placing a new one when at the cap removes the oldest. Orbs are cleared when the origin is revoked.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `max_orbs` | int | no | `4` | Maximum number of simultaneous orbs |
| `radius` | float | no | `28.0` | Effect radius per orb in blocks |
| `cooldown_ticks` | int | no | `100` | Cooldown between placements |
| `tick_interval` | int | no | `20` | Ticks between Darkness pulses per orb |
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

---

## `neoorigins:persistent_effect`

Generic condition-gated, toggleable status-effect stack. Part of the 2.0 consolidation: replaces `status_effect`, `stacking_status_effects`, `night_vision`, `glow`, `water_breathing`, `breath_in_fluid`, and the tag branch of `regen_in_fluid` with one type that applies an arbitrary list of mob effects whenever an optional `condition` (an EntityCondition DSL tree) is met.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effects` | list of EffectSpec | yes | — | Mob effects to apply. See below. May also be a single inline EffectSpec on the top-level object. |
| `condition` | EntityCondition | no | always-true | DSL condition — effects only apply while it is true. Effects are cleared when it becomes false. |
| `refresh_interval` | int | no | `300` | Ticks each applied effect is (re)granted for. Must be ≥ 1. |
| `toggleable` | bool | no | `true` | When true, this is an active-keybind power: pressing the key toggles effects on/off. When false, effects are always applied while condition is true. |

**`EffectSpec` object:**

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effect` | Identifier | yes | — | Effect ID, e.g. `minecraft:strength` (alias `id` also accepted) |
| `amplifier` | int | no | `0` | Effect level |
| `ambient` | bool | no | `true` | Ambient particles (low visibility) |
| `show_particles` | bool | no | `false` | Whether to show particles |
| `show_icon` | bool | no | `true` | Whether to show the HUD icon |

**Example — permanent Strength + Haste while in water:**
```json
{
  "type": "neoorigins:persistent_effect",
  "toggleable": false,
  "condition": { "type": "neoorigins:in_water" },
  "effects": [
    { "effect": "minecraft:strength", "amplifier": 0 },
    { "effect": "minecraft:haste",    "amplifier": 1 }
  ],
  "name": "Tidecaller",
  "description": "Empowered while immersed in water."
}
```

Effects are re-applied every tick (bounded to `refresh_interval / 2` before re-grant), so amplifier upgrades or duration refreshes from other sources won't win over this power. When `toggleable` is true and the player toggles it off, the effects are removed and will not be re-applied until toggled on.

---

## `neoorigins:condition_passive`

Generic condition-gated periodic action — "a passive with a trigger". Part of the 2.0 consolidation: collapses `biome_buff`, `damage_in_biome`, `damage_in_daylight`, `damage_in_water`, `burn_at_health_threshold`, `mobs_ignore_player`, `no_mob_spawns_nearby`, and `item_magnetism` into a single type. Also supersedes `tick_action` when `condition` is omitted.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `interval` | int | no | `20` | Tick interval between evaluations (clamped to ≥ 1) |
| `condition` | EntityCondition | no | always-true | DSL condition to test each interval |
| `entity_action` | EntityAction | no | noop | Action run against the player when condition is true |
| `else_action` | EntityAction | no | noop | Action run when condition is false |

See [EVENTS.md](EVENTS.md) / the Apoli compat docs for the full condition and action DSL.

**Example — take 1 damage every 2 seconds in the Nether:**
```json
{
  "type": "neoorigins:condition_passive",
  "interval": 40,
  "condition": {
    "type": "neoorigins:in_tag",
    "tag": "minecraft:is_nether"
  },
  "entity_action": {
    "type": "neoorigins:damage",
    "amount": 1.0,
    "source": "fire"
  },
  "name": "Nether Intolerance",
  "description": "Burns while in the Nether."
}
```

---

## `neoorigins:action_on_event`

The 2.0 generic event hook — fires an action and/or applies a float modifier when a named player event occurs. Replaces 20+ bespoke Origins-Classes hook powers (`better_enchanting`, `more_smoker_xp`, `crop_harvest_bonus`, `break_speed_modifier`, `natural_regen_modifier`, `action_on_kill`, `action_on_hit_taken`, `thorns_aura`, `knockback_modifier`, `hunger_drain_modifier`, `food_restriction`, …) with one configurable type.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `event` | string | yes | — | Event name (case-insensitive). See [EVENTS.md](EVENTS.md) for the full list. |
| `condition` | EntityCondition | no | always-true | DSL gate — the event only fires when this is true |
| `entity_action` | EntityAction | no | noop | Side-effect run when the event fires |
| `modifier` | FloatModifier or list | no | identity | Float modifier applied to the event's numeric payload (for modifier-style events) |

**Event categories (see [EVENTS.md](EVENTS.md) for the full list):**

- Lifecycle: `GAINED`, `REVOKED`, `RESPAWN`, `GAMEMODE_CHANGE`
- Combat: `KILL`, `HIT_TAKEN`, `DAMAGE_DEALT`, `MOD_KNOCKBACK`, `MOD_THORNS`
- Food: `FOOD_EATEN`, `MOD_FOOD_NUTRITION`, `MOD_EXHAUSTION`, `MOD_NATURAL_REGEN`
- Mining / crafting: `BLOCK_BREAK`, `CRAFT_ITEM`, `ITEM_USE_FINISH`, `MOD_BREAK_SPEED`, `MOD_CRAFT_COUNT`
- XP / economy: `XP_GAINED`, `MOD_XP_GAIN`, `TRADE_COMPLETE`, `MOD_BONEMEAL_GROWTH`
- Interaction: `BLOCK_INTERACT`, `ENTITY_INTERACT`, `RIGHT_CLICK_ITEM`

For action-style events set `entity_action`; for modifier-style events set `modifier`. A single power may declare both — the action path fires on `dispatch` sites and the modifier path chains on `dispatchModifier` sites.

**Example — heal 1 heart on kill only while holding a wooden sword:**
```json
{
  "type": "neoorigins:action_on_event",
  "event": "kill",
  "condition": {
    "type": "neoorigins:equipped_item",
    "slot": "mainhand",
    "item": "minecraft:wooden_sword"
  },
  "entity_action": {
    "type": "neoorigins:heal",
    "amount": 2.0
  },
  "name": "Wooden Vampire",
  "description": "Regain health on kills, but only with a wooden sword."
}
```

**Example — 30% faster natural regen:**
```json
{
  "type": "neoorigins:action_on_event",
  "event": "mod_natural_regen",
  "modifier": {
    "operation": "multiply_base",
    "value": 1.3
  }
}
```

---

## `neoorigins:invulnerability`

Native invulnerability power — cancels incoming damage whose source matches any configured filter. If every filter list is empty, blocks all incoming damage. Replaces the lossy 1.x translation that only covered `FIRE`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `damage_types` | list of string | no | `[]` | Damage type IDs (e.g. `minecraft:fall`) — matched by ID |
| `damage_tags` | list of string | no | `[]` | Damage type tag IDs (e.g. `minecraft:is_fire`) |
| `msg_ids` | list of string | no | `[]` | Vanilla damage msgId strings (e.g. `inFire`, `fall`) — covers loose Origins-style names |

Filters combine with OR: a damage source is cancelled if it matches any entry in any list. If all three lists are empty, all damage is cancelled (matching Origins' behaviour when `damage_condition` is omitted).

**Example — fire and lava immunity:**
```json
{
  "type": "neoorigins:invulnerability",
  "damage_tags": ["minecraft:is_fire"],
  "name": "Inferno Blood",
  "description": "Immune to fire and lava."
}
```

**Example — block everything:**
```json
{
  "type": "neoorigins:invulnerability",
  "name": "Adamant",
  "description": "Cannot be harmed."
}
```

---

## `neoorigins:size_scaling`

Scales the player's visual and collision size via the `minecraft:generic.scale` attribute. Optionally also scales block/entity interaction reach. Mirrors the scale to Pehkui when the mod is present so cross-mod queries see a consistent value.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `scale` | float | no | `1.0` | Target scale multiplier (`0.5` = half size, `2.0` = double) |
| `modify_reach` | bool | no | `true` | Also adjust block/entity interaction reach proportionally |

**Example — half-size origin:**
```json
{
  "type": "neoorigins:size_scaling",
  "scale": 0.5,
  "modify_reach": true,
  "name": "Tiny",
  "description": "Half-sized, with proportionally shorter reach."
}
```

The scale attribute uses `ADD_VALUE` against a base of `1.0` (so delta = `scale - 1.0`); reach attributes use `ADD_MULTIPLIED_BASE` so reach tracks visual size. Missing attributes (older MC or NeoForge versions) log a single warning and the power silently skips that attribute.

---

## `neoorigins:entity_set`

Pure data-holder power. Its presence in a player's active power set declares that the player participates in a named UUID set. Actual set storage lives on `PlayerOriginData`; the `origins:in_set` / `neoorigins:add_to_set` / `neoorigins:remove_from_set` verbs read and mutate it.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `name` | string | no | `""` | Name of the UUID set this power declares. Conventionally namespaced like `mypack:kill_streak`. |

**Example:**
```json
{
  "type": "neoorigins:entity_set",
  "name": "mypack:hunted",
  "name_text": "Marked Prey",
  "description": "Tracks entities marked as hunted by this origin."
}
```

The colon in `name` is allowed and carries no mechanical meaning — it's a soft convention for avoiding collisions between packs. Pair this with `action_on_event` (to add entries) and `condition_passive` or direct DSL predicates (to query membership).

---

## `neoorigins:enhanced_vision`

Passive low-light vision: emits an `enhanced_vision` capability tag and scales the client brightness curve directly via a `LightTexture` mixin. Unlike the full `minecraft:night_vision` status effect, there's no screen tint, HUD icon, or max-brightness ramp at end of duration — just exposure-style compensation.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `exposure` | float (0.0–1.0) | no | `0.7` | Target brightness scalar (advisory in v1 — the client mixin currently hardcodes 0.7) |

**Example:**
```json
{
  "type": "neoorigins:enhanced_vision",
  "exposure": 0.7,
  "name": "Cat Eyes",
  "description": "Sees clearly in dim light without the green tint."
}
```

All exposure work happens on the logical client; the server only publishes the capability tag. If per-origin variance is needed later, the value will be wired through a client-synced power-config payload.

---

## `neoorigins:edible_item`

Makes arbitrary items consumable on right-click. A matching item is instantly consumed: the configured nutrition/saturation is applied, one is removed from the stack, and an `action_on_event.ITEM_USE_FINISH` dispatch fires with the stack as context. Bypasses vanilla FoodProperties so pack authors can declare "Merling eats raw fish" or "Phantom eats rotten flesh for full food" without replacing the item's data components.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `items` | list of Identifier | no | `[]` | Exact item IDs that qualify |
| `tags` | list of Identifier | no | `[]` | Item tag IDs that qualify |
| `nutrition` | int | no | `4` | Food points restored (1 shank = 2) |
| `saturation` | float | no | `0.3` | Saturation restored |
| `always_edible` | bool | no | `true` | If true, can be eaten at full hunger |
| `consume_sound` | Identifier | no | _(none)_ | Optional sound ID played on consume |

At least one of `items` or `tags` should be non-empty, otherwise nothing will ever match. Matching is inclusive — an item qualifies if it appears in either list.

**Example — Merling eats raw fish at full food:**
```json
{
  "type": "neoorigins:edible_item",
  "tags": ["minecraft:fishes"],
  "nutrition": 4,
  "saturation": 0.4,
  "always_edible": true,
  "name": "Pescivore",
  "description": "Can eat raw fish at any time."
}
```

---

## `neoorigins:restrict_armor`

Prevents certain items from being equipped in certain slots. When a matching item is placed in a restricted slot, the power ejects it back to the player's inventory (or drops it to the ground if inventory is full).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `restrictions` | list of SlotRestriction | no | `[]` | Per-slot filters. An empty list disables the power. |

**`SlotRestriction` object:**

| Field | Type | Required | Description |
|---|---|---|---|
| `slot` | string | yes | One of `mainhand`, `offhand`, `head`, `chest`, `legs`, `feet`, `body` |
| `item` | Identifier | no | Exact item ID to restrict |
| `tag` | Identifier | no | Item tag to restrict |

If neither `item` nor `tag` is set on a restriction entry, any non-empty stack in that slot is restricted.

**Example — no iron or diamond chestplates:**
```json
{
  "type": "neoorigins:restrict_armor",
  "restrictions": [
    { "slot": "chest", "item": "minecraft:iron_chestplate" },
    { "slot": "chest", "item": "minecraft:diamond_chestplate" }
  ],
  "name": "Light Armor Only",
  "description": "Cannot wear iron or diamond chestplates."
}
```

---

## `neoorigins:keep_inventory`

On death, selectively retain inventory items matching the power's filters. Matching items are removed from drops and restored to the player's inventory on respawn.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `slots` | list of string | no | `["*"]` | Slot categories to keep from: `hotbar`, `main`, `armor`, `offhand`, `all` (alias `*`). `main` also includes `hotbar`. |
| `items` | list of Identifier | no | `[]` | Specific items to keep |
| `tags` | list of Identifier | no | `[]` | Item tags to keep |

If both `items` and `tags` are empty, every item in the configured slots is kept.

**Example — always keep tools:**
```json
{
  "type": "neoorigins:keep_inventory",
  "slots": ["main", "hotbar", "offhand"],
  "tags": ["minecraft:pickaxes", "minecraft:swords", "minecraft:axes", "minecraft:shovels"],
  "name": "Tool Preservation",
  "description": "Tools are never dropped on death."
}
```

---

## `neoorigins:modify_player_spawn`

Overrides the player's respawn location to a power-configured target. Unlike the origin's `spawn_location` (first-join and bed-less respawn only), this power fires on every respawn. Optionally also overrides the bed/respawn-anchor spawn point.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `location` | LocationCondition | yes | — | Target location descriptor — `dimension` / `biome` / `biome_tag` / `structure` / `structure_tag`. See `attribute_modifier`'s `location_condition` for the shape. |
| `override_bed` | bool | no | `false` | When true, also overrides bed/respawn-anchor spawn. When false, respects a valid bed/anchor and only fires if none is set. |

**Example — always respawn in an End city:**
```json
{
  "type": "neoorigins:modify_player_spawn",
  "location": {
    "dimension": "minecraft:the_end",
    "structure": "minecraft:end_city"
  },
  "override_bed": true,
  "name": "Void-Bound",
  "description": "Always respawns in the nearest End city."
}
```

---

## `neoorigins:toggle`

A bare, stateless boolean power — purely a data-holder whose state lives in `CompatAttachments.toggleState()` keyed by the registered id. Unlike `active_ability`, `toggle` does not consume a keybind slot; it's a named boolean other powers gate on.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `default` | bool | no | `false` | Value reads see before the toggle has ever been flipped on this player. Set `true` for "on until flipped off." |

Read the current value with `origins:power_active { power: "mypack:my_toggle" }`. Flip it with `neoorigins:toggle { power: "mypack:my_toggle" }` (optionally `value: true/false` to set explicitly).

**Example:**
```json
{
  "type": "neoorigins:toggle",
  "default": false,
  "name": "Hunter's Mark",
  "description": "Internal flag gated by other powers."
}
```

See [COOKBOOK.md → Toggleable abilities (no keybind slot)](COOKBOOK.md#toggleable-abilities-no-keybind-slot) for full recipes.

---

## `neoorigins:active_ability`

Generic cooldown-gated active (keybind) ability. Part of the 2.0 consolidation — collapses `active_teleport`, `active_dash`, `active_launch`, `active_recall`, `active_swap`, `active_fireball`, `active_bolt`, `active_phase`, `active_place_block`, `healing_mist`, `repulse`, `active_aoe_effect`, and others into one type whose effect is described by an `entity_action` tree.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `cooldown_ticks` | int | no | `60` | Cooldown after each use |
| `hunger_cost` | int | no | `0` | Food points removed per use (1 shank = 2 points). Silently aborts if player has less food (cooldown not consumed). |
| `entity_action` | EntityAction | yes | noop | Action tree fired on use (typically `origins:and { actions: [...] }`) |
| `condition` | EntityCondition | no | always-true | DSL gate — skips firing (and the cooldown) if false |

Actions and conditions are compiled once at power-load time via `ActionParser` / `ConditionParser`; runtime only dispatches through the compiled closures.

Hunger gating is handled at the `AbstractActivePower` base class level — when `hunger_cost > 0`, the base checks and debits food before calling `execute`. Any power that extends `AbstractActivePower` and wires `hungerCost()` through its Codec inherits the behavior automatically.

**Example — launch the player upward:**
```json
{
  "type": "neoorigins:active_ability",
  "cooldown_ticks": 80,
  "entity_action": {
    "type": "neoorigins:add_velocity",
    "y": 2.0,
    "client": true,
    "server": true
  },
  "name": "Leap",
  "description": "Launches the player upward."
}
```

Legacy active types (`active_teleport`, `active_dash`, etc.) remain registered during the deprecation window. The `migrateLegacyPowers` gradle task can rewrite pack JSON to this type; `LegacyPowerTypeAliases` covers unmigrated JSON once per-type field remappers are landed.

---

## `neoorigins:ground_slam`

Active AoE slam — damages and knocks back every entity within radius.

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

## `neoorigins:summon_minion`

Active power that summons a mob near the player. Summoned mobs are tracked with caps, despawn timers, and death-damage feedback. Equipment can be configured per slot; all equipment drop chances are zeroed so summoned mobs never drop loot.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `mob_type` | Identifier | yes | — | Entity type to summon, e.g. `minecraft:zombie` |
| `max_count` | int | no | `3` | Maximum concurrent minions per player per mob_type |
| `cooldown_ticks` | int | no | `200` | Cooldown after each summon |
| `hunger_cost` | int | no | `4` | Food points consumed per summon |
| `despawn_ticks` | int | no | `18000` | Lifespan after spawn (15 min default) |
| `death_damage` | float | no | `1.0` | Damage taken by the owner when a minion dies |
| `head` / `chest` / `legs` / `feet` / `mainhand` / `offhand` | Identifier | no | _(none or iron helmet for head)_ | Equipment per slot |

If `head` is unset, the minion gets an iron helmet by default (sun protection for undead). All drop chances are set to 0.

**Example — zombie minion with sword:**
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

---

## `neoorigins:tame_mob`

Active power that tames a hostile mob the player is looking at. The mob's AI is rewritten to follow the player and target whatever recently hurt the owner. Tamed mobs are tracked via MinionTracker.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `range` | double | no | `16.0` | Max raycast distance to a target |
| `max_tamed` | int | no | `4` | Maximum concurrently-tamed mobs per player |
| `cooldown_ticks` | int | no | `200` | Cooldown after each use |
| `hunger_cost` | int | no | `3` | Food points consumed per tame |
| `despawn_ticks` | int | no | `36000` | Lifespan of each tamed mob (30 min default) |
| `death_damage` | float | no | `0.5` | Damage taken by owner when a tamed mob dies |

Target must be a hostile `Mob` that implements `Enemy` (so villagers, animals, and passive mobs won't qualify). Bosses are rejected via a `canUsePortal` check.

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

## `neoorigins:horde_regen`

Passively regenerates health on all tamed mobs (via `tame_mob`) on an interval. Healing only applies when the mob hasn't taken damage recently.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `heal_amount` | float | no | `1.0` | HP restored per interval |
| `interval_ticks` | int | no | `120` | Ticks between heal pulses |
| `combat_cooldown_ticks` | int | no | `100` | Minimum ticks since last damage before a mob heals |

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

---

## `neoorigins:exhaustion_filter`

Filters out specific vanilla exhaustion sources so they don't drain the player's hunger. Handled via `PlayerTickEvent.Pre`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `sources` | list of string | no | `["sprint"]` | Exhaustion sources to cancel, e.g. `sprint`, `mining` |

**Example:**
```json
{
  "type": "neoorigins:exhaustion_filter",
  "sources": ["sprint"],
  "name": "Tireless",
  "description": "Sprinting no longer drains hunger."
}
```

---

## `neoorigins:twin_breeding`

On vanilla breeding, spawns a second baby with the configured probability.

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

## `neoorigins:less_item_use_slowdown`

Reduces movement slowdown while using items (bow, shield, etc.). Applies a transient `generic.movement_speed` modifier while `isUsingItem()` is true and the held item matches.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `item_type` | string | no | `any` | `any`, `bow`, `shield`, or any substring matched against the held item's ID |
| `speed_multiplier` | float | no | `0.5` | `ADD_MULTIPLIED_BASE` value — `0.5` = +50% walk speed while using |

**Example — full-speed archery:**
```json
{
  "type": "neoorigins:less_item_use_slowdown",
  "item_type": "bow",
  "speed_multiplier": 0.8,
  "name": "Strider Archer",
  "description": "Barely slowed while drawing a bow."
}
```

---

## `neoorigins:no_projectile_divergence`

Removes projectile divergence (perfect accuracy) for projectiles shot by the player. Handled via `EntityJoinLevelEvent`.

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

## `neoorigins:quality_equipment`

Adds Unbreaking to newly crafted tools and armor by monitoring the player's inventory and enchanting eligible items as they appear.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `unbreaking_level` | int | no | `1` | Level of Unbreaking to apply |

**Example:**
```json
{
  "type": "neoorigins:quality_equipment",
  "unbreaking_level": 1,
  "name": "Craftsman's Touch",
  "description": "Tools you craft gain Unbreaking I."
}
```

Scans every 5 ticks. Items already carrying Unbreaking at any level are skipped so this doesn't stack up.

---

## `neoorigins:more_smoker_xp`

Intended to grant bonus XP from smoker cooking. **Currently inert** — no furnace XP event exists in NeoForge 21.11.38. Registered so pack JSON referencing it doesn't fail.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `multiplier` | float | no | `2.0` | Planned XP multiplier (not yet applied) |

---

## `neoorigins:trade_availability`

Periodically resets villager trade uses for every villager within a radius of the player.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `scan_interval` | int | no | `40` | Ticks between scans |
| `radius` | double | no | `8.0` | Scan radius in blocks |

**Example:**
```json
{
  "type": "neoorigins:trade_availability",
  "scan_interval": 40,
  "radius": 8.0,
  "name": "Charismatic",
  "description": "Villagers near you reset their trades."
}
```

---

## `neoorigins:rare_wandering_loot`

Adds rare items to the wandering-trader pool. This is a **global effect** hooked via `WandererTradesEvent` at mod init; the power's presence on any player enables it worldwide.

No fields beyond `name` / `description`.

**Example:**
```json
{
  "type": "neoorigins:rare_wandering_loot",
  "name": "Curio Magnet",
  "description": "Wandering traders bring rarer wares while you're around."
}
```

---

## `neoorigins:sneaky`

Reduces mob detection range — hostile mobs only target the player when significantly closer than normal. Handled via `LivingChangeTargetEvent`.

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

After sneaking continuously for a threshold number of ticks, the player gains Invisibility. The effect clears when sneaking stops. Toggleable off via keybind.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `activation_ticks` | int | no | `200` | Sneak time required before invisibility kicks in (10 s default) |

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

## `neoorigins:tree_felling`

When the player breaks a log, BFS/DFS upward to break all connected logs. Skipped while sneaking so pack authors can still harvest single logs. Handled via `BlockEvent.BreakEvent`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `max_blocks` | int | no | `64` | Maximum connected logs to break per activation |

**Example:**
```json
{
  "type": "neoorigins:tree_felling",
  "max_blocks": 64,
  "name": "Arboreal Sense",
  "description": "Fells an entire tree when you chop its base. Sneak to break single logs."
}
```

---

## `neoorigins:craft_amount_bonus`

Grants bonus items when crafting a specific output (e.g., more planks per log). Uses tick-based inventory monitoring because NeoForge exposes no `ItemCraftedEvent`; tracks total count of the target item while a container is open and grants `bonus_count` extra on each increase.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `output_item` | Identifier | no | `minecraft:oak_planks` | Item to watch for |
| `bonus_count` | int | no | `4` | Maximum extra copies granted per craft |

**Example — quadruple planks from logs:**
```json
{
  "type": "neoorigins:craft_amount_bonus",
  "output_item": "minecraft:oak_planks",
  "bonus_count": 4,
  "name": "Lumberwright",
  "description": "Gets extra planks when crafting from logs."
}
```

The bonus is capped at `min(diff, bonus_count)` so shift-clicking a full log doesn't multiply out.

---

## `neoorigins:tamed_animal_boost`

Boosts stats (max health, movement speed) on every tamed animal owned by the player within a radius. Applies permanent attribute modifiers with fixed IDs, so reloading or re-granting is idempotent.

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

# Pack-author patterns

Recipes that compose existing power types to cover use cases the built-in types don't expose directly. Most rely on the Apoli compat surface (`origins:`-prefixed types handled by `OriginsCompatPowerLoader`).

## Periodic feed / heal via `origins:action_over_time`

NeoOrigins' built-in `neoorigins:tick_action` only ships a hardcoded `TELEPORT_ON_DAMAGE` behaviour. For any other periodic action — periodically restore hunger, heal a fixed amount, apply an effect, run an arbitrary entity-action — use `origins:action_over_time` from the Apoli compat layer.

**Periodic hunger restoration** (e.g. for an origin that doesn't eat conventionally):
```json
{
  "type": "neoorigins:action_over_time",
  "interval": 40,
  "entity_action": {
    "type": "neoorigins:feed",
    "food": 1,
    "saturation": 0.2
  }
}
```

**Periodic healing** (independent of food / `natural_regen_modifier`):
```json
{
  "type": "neoorigins:action_over_time",
  "interval": 60,
  "entity_action": {
    "type": "neoorigins:heal",
    "amount": 0.5
  }
}
```

`interval` is in ticks (20 = 1 second). The `entity_action` runs against the player. Any verb supported by `ActionParser` works (`origins:apply_effect`, `origins:damage`, `origins:execute_command`, `origins:if_else` for conditional wrapping, etc.).

## `neoorigins:cobweb_affinity`

Spider-like mobility through cobwebs. Emits the `cobweb_affinity` capability tag — `EntityMakeStuckInBlockMixin` reads it to suppress the usual slowdown inside cobwebs, and `MovementPowerEvents.onBreakSpeed` reads it to multiply cobweb break speed by 10×.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| (no fields) | — | — | — | Marker power. |

**Example:**
```json
{
  "type": "neoorigins:cobweb_affinity",
  "name": "Spider Affinity",
  "description": "Move and break cobwebs easily."
}
```

---

## `neoorigins:hide_hud_bar`

Hides a HUD bar while the power is active. Emits `hide_hunger_bar` or `hide_air_bar` capability tags — `GuiHudBarsMixin` reads them to cancel the matching render call. Governed server-side by the `hide_hud_bars` common config (default true); if disabled, the power registers but the HUD still renders.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `bar` | string | no | `"hunger"` | Which bar to hide. `"hunger"` / `"food"` hide the hunger bar; `"air"` / `"oxygen"` / `"breath"` hide the air bar. Any other value is a no-op. |

**Example:**
```json
{
  "type": "neoorigins:hide_hud_bar",
  "bar": "air",
  "name": "No Need to Breathe",
  "hidden": true
}
```

Typically paired with `neoorigins:water_breathing` or the Automaton pattern so the hidden bar is also being suppressed mechanically — hiding a bar that still ticks is disorienting.

---

## `neoorigins:ender_gaze_immunity`

Endermen do not aggro when the player looks at them. Emits the `ender_gaze_immunity` capability tag — an Enderman targeting mixin reads it to skip the usual line-of-sight check.

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

## `neoorigins:natural_glide`

Grants elytra-style gliding without needing to equip an elytra. Press jump while falling to start fall-flying, exactly as if the player were wearing one.

Emits the `natural_glide` capability tag. The `PlayerStartFallFlyingMixin` reads it at the head of `Player.tryToStartFallFlying` and bypasses the standard chest-slot elytra check, calling `startFallFlying()` directly.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| (no fields) | — | — | — | Marker power. |

**Example — Phantom spectral wings:**
```json
{
  "type": "neoorigins:natural_glide",
  "name": "Spectral Wings",
  "description": "Glide like an elytra user — no item required. Press jump while falling to spread your wings."
}
```

Preconditions match vanilla: not on ground, not already fall-flying, not in water, not levitating. Pair with `neoorigins:elytra_boost` for a full glide + launch-boost kit.

Contrast with `neoorigins:flight` — flight is creative-mode-style (hold space to ascend, fly freely). `natural_glide` is pitch-based gliding like a real elytra user.

---

## `neoorigins:bare_hand_tool`

Makes the player's empty hand behave like a specific vanilla tool for block-break purposes — tool-tier drop eligibility **and** break speed both match the configured tool item. Point at any tool item ID and the runtime looks up its tool component to determine which blocks qualify and at what speed.

Emits a capability tag of the form `bare_hand_tool:<tool_id>` (e.g. `bare_hand_tool:minecraft:stone_pickaxe`) — the tool ID is encoded in the tag so the client-side break-speed predictor and the server-side harvest check can both reach the answer without extra sync state. Wired via `BareHandToolEvents` on the NeoForge event bus (`PlayerEvent.HarvestCheck` + `PlayerEvent.BreakSpeed`). No mixins required.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `tool` | Identifier | no | `minecraft:stone_pickaxe` | Any vanilla tool item ID. Determines both break-speed and drop-eligibility. |

**Example — Caveborn mines like a stone pickaxe with bare hands:**
```json
{
  "type": "neoorigins:bare_hand_tool",
  "tool": "minecraft:stone_pickaxe",
  "name": "Stone Fists",
  "description": "Bare hands mine like a stone pickaxe — break ores and stone without tools."
}
```

An origin can stack multiple instances to emulate several tool types simultaneously (e.g. a miner + lumberjack hybrid with both a pickaxe and an axe instance). When the player's hand is empty the event handler iterates all active `bare_hand_tool` capabilities and picks the first that can correctly harvest the target — different tool types don't conflict.

Only fires when the main hand is empty. Holding any item delegates to vanilla behaviour normally.

---

## `neoorigins:fortune_when_effect`

Applies a virtual Fortune-level drop multiplier whenever a configured `MobEffect` is active on the player. The vanilla `ApplyBonusCount.ORE_DROPS` formula is used, giving the same rolling distribution as a real Fortune N pickaxe (`count × (max(0, random(level + 2) - 1) + 1)`).

Deliberately generic — any origin can emulate an enchantment-like buff by pairing this with any MobEffect (e.g. Caveborn's Mining Fortune is gated by `minecraft:luck` granted via eating diamond). Wired via `FortuneEffectEvents` subscribing to `BlockDropsEvent` server-side.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effect` | Identifier | no | `minecraft:luck` | The gating MobEffect ID. Bonus only applies while this effect is active on the player. |
| `level` | int | no | `2` | Virtual Fortune level to roll. Higher = more extra drops. |
| `target` | string | no | `#c:ores` | Block tag the bonus applies to. Use `#tagname` syntax. Defaults to the NeoForge common ores tag; pack authors can narrow to a vanilla sub-tag like `#minecraft:diamond_ores` or a custom tag. |

**Vanilla parity:** `minecraft:ancient_debris` is hardcoded-excluded because netherite is the single vanilla ore that ignores Fortune. Every other vanilla ore (iron, gold, copper, coal, diamond, emerald, lapis, redstone, nether_gold, nether_quartz) is covered by `#c:ores` and will roll the bonus normally.

**Example — Caveborn Mining Fortune (gated by Luck from eating diamond):**
```json
{
  "type": "neoorigins:fortune_when_effect",
  "effect": "minecraft:luck",
  "level": 2,
  "target": "#c:ores",
  "name": "Mining Fortune",
  "description": "While Luck is active, ore blocks drop as if mined with Fortune II."
}
```

Stacking isn't additive — only the first matching power fires per break. Author variants as separate powers with different effect gates rather than expecting them to compound.

---

## Composing power sets

Individual 2.0 power types are intentionally narrow so they can be combined. For a "rat"-style origin that marks small mobs it kills and gets a heal buff when attacking anything on the list:

- `neoorigins:entity_set` — declares the UUID set (e.g. `mypack:kill_list`)
- `neoorigins:action_on_event` with `event: kill`, `entity_action: { type: neoorigins:add_to_set, set: mypack:kill_list }` — appends victims to the set
- `neoorigins:condition_passive` with `condition: { type: origins:target_in_set, set: mypack:kill_list }` and `entity_action: { type: origins:heal, amount: 0.5 }` — heals when attacking a marked target

Each piece is a separate power entry in the origin's `powers` array. The `entity_set` power carries no behaviour on its own — it's the shared name other powers read and write.
