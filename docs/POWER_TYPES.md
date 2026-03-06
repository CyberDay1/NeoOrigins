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

Permanently adds or multiplies a player attribute while the origin is active.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `attribute` | Identifier | yes | — | Attribute to modify, e.g. `minecraft:generic.movement_speed` |
| `amount` | double | yes | — | Amount to add or multiply |
| `operation` | string | no | `add_value` | `add_value`, `add_multiplied_base`, or `add_multiplied_total` |

**Operations:**
- `add_value` — flat addition to base value
- `add_multiplied_base` — adds `base * amount` (e.g. `-0.1` = 10% slower)
- `add_multiplied_total` — multiplies total after all other modifiers

**Example — 8 flat armor:**
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

Multiplies damage dealt or received, optionally filtered to a specific damage type.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `direction` | string | no | `in` | `in` (damage received) or `out` (damage dealt) |
| `multiplier` | float | no | `1.0` | Damage multiplier (e.g. `2.0` = double, `0.5` = half) |
| `damage_type` | string | no | _(all types)_ | Optional vanilla damage type to filter, e.g. `drown`, `fire`, `fall` |

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
