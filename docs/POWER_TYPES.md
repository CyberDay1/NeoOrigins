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
| `radius` | float | no | `6.0` | Pull radius in blocks |

**Example:**
```json
{
  "type": "neoorigins:item_magnetism",
  "radius": 6.0,
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

**Example:**
```json
{
  "type": "neoorigins:active_teleport",
  "max_distance": 32.0,
  "cooldown_ticks": 60,
  "name": "Blink",
  "description": "Teleports to where you're looking."
}
```

---

## `neoorigins:active_dash`

Active ability that launches the player in their look direction.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` | float | no | `1.8` | Launch velocity |
| `cooldown_ticks` | int | no | `60` | Cooldown in ticks |
| `allow_vertical` | bool | no | `false` | Whether to include vertical component from look direction |

**Example:**
```json
{
  "type": "neoorigins:active_dash",
  "power": 1.8,
  "cooldown_ticks": 60,
  "allow_vertical": true,
  "name": "Pounce",
  "description": "Dashes in the direction you're looking."
}
```

---

## `neoorigins:active_launch`

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
| `cooldown_ticks` | int | no | `60` | Cooldown between placements |
| `tick_interval` | int | no | `40` | Ticks between Darkness pulses per orb |

**Example:**
```json
{
  "type": "neoorigins:shadow_orb",
  "max_orbs": 4,
  "radius": 28.0,
  "cooldown_ticks": 60,
  "tick_interval": 40,
  "name": "Shadow Anchor",
  "description": "Places orbs that shroud the area in darkness."
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
  "type": "origins:action_over_time",
  "interval": 40,
  "entity_action": {
    "type": "origins:feed",
    "food": 1,
    "saturation": 0.2
  }
}
```

**Periodic healing** (independent of food / `natural_regen_modifier`):
```json
{
  "type": "origins:action_over_time",
  "interval": 60,
  "entity_action": {
    "type": "origins:heal",
    "amount": 0.5
  }
}
```

`interval` is in ticks (20 = 1 second). The `entity_action` runs against the player. Any verb supported by `ActionParser` works (`origins:apply_effect`, `origins:damage`, `origins:execute_command`, `origins:if_else` for conditional wrapping, etc.).
