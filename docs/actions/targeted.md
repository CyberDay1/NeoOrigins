---
title: "Targeted, block & integration verbs"
parent: "Entity Actions"
grand_parent: "DSL Reference"
nav_order: 4
permalink: /actions/targeted
---

# Targeted, block & integration verbs

Entity sets and toggle flags, event cancelling, attacker reactions, teleports and swaps, item handling, block-target verbs, raycasts, spells and morph verbs.

These verbs are new in 2.0 and read from `ActionContextHolder`, the service that publishes the current dispatch context while `EventPowerIndex` walks handlers. They no-op outside a compatible context.

## `neoorigins:add_to_set`

Adds the current bientity target's UUID to a named entity-set on the actor player. The backing sets power relationship tracking (who I've tagged, who I'm tracking, etc.).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `set` | string | yes | — | Set name |

**Gotcha (bientity-only):** requires an active `HitTakenContext`, `KillContext`, `EntityInteractContext`, or `ProjectileHitContext` carrying a `LivingEntity` target. Silently no-ops otherwise.

**Example:**
```json
{ "type": "neoorigins:add_to_set", "set": "tagged_enemies" }
```

---

## `neoorigins:remove_from_set`

Removes the current bientity target's UUID from a named entity-set. Same context requirements as `add_to_set`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `set` | string | yes | — | Set name |

**Example:**
```json
{ "type": "neoorigins:remove_from_set", "set": "tagged_enemies" }
```

---

## `neoorigins:toggle`

Flips or sets a named toggle state on the target. Used by 2.0's `toggle` alias family. If `value` is given it's set explicitly; otherwise the current state is flipped (resolving the registered [`neoorigins:toggle`](../powers/state.md#neooriginstoggle) power's `default` field as the starting value).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `power` | string | yes | — | Toggle key (usually the power id) |
| `value` | bool | no | — | If present, set to this value; otherwise flip |

**Example:**
```json
{ "type": "neoorigins:toggle", "power": "examplepack:flight_toggle" }
```

See [Make a toggle → Toggleable abilities (no keybind slot)](../guides/toggles.md) for full recipes.

---

## `neoorigins:cancel_event`

Cancels the currently dispatched event. Used internally by the `food_restriction` alias to reject food consumption.

**Gotcha (context-only):** works only when the current `ActionContextHolder` value carries a cancellable event. That covers `food_eaten` (`FoodContext`), `effect_applied` (`EffectAppliedContext`), `entity_use` / `villager_interact` / `breed` / `tame` (`EntityInteractContext`), `block_use` / `bonemeal` (`BlockInteractContext`), `hit_taken` (`HitTakenContext`: negates the hit), `kill` (`KillContext`: the victim is spared), `projectile_hit` (`ProjectileHitContext`), and any dispatch whose context is itself an `ICancellableEvent` (e.g. `block_place`). No-op for any other context.

**Example:**
```json
{ "type": "neoorigins:cancel_event" }
```

---

## `neoorigins:damage_attacker`

Hurts the attacker recorded in the current `HitTakenContext`. Used by the `thorns_aura` alias.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `amount` | float | no | `2.0` | Fixed damage |
| `amount_ratio` | float | no | — | If present, damage = `max(0.5, incomingDamage * ratio)`. Overrides `amount`. |
| `source.name` | string | no | `magic` | Damage source. Supported: `fire`/`on_fire`/`in_fire`, `lava`, `magic`, `generic`. Unknown values fall through to `magic`. |

**Gotcha (hit-taken only):** requires an active `HitTakenContext` whose attacker is a `LivingEntity`. No-op elsewhere.

**Example:**
```json
{ "type": "neoorigins:damage_attacker", "amount_ratio": 0.5, "source": { "name": "magic" } }
```

---

## `neoorigins:ignite_attacker`

Sets the current `HitTakenContext` attacker on fire. **Gotcha:** hit-taken context only.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `ticks` | int | no | `60` | Fire duration |

**Example:**
```json
{ "type": "neoorigins:ignite_attacker", "ticks": 100 }
```

---

## `neoorigins:effect_on_attacker`

Applies a mob effect to the current `HitTakenContext` attacker. **Gotcha:** hit-taken context only, and the attacker must be a `LivingEntity`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effect` | resource id | yes | — | Effect id |
| `duration` | int | no | `100` | Effect duration |
| `amplifier` | int | no | `0` | Amplifier level |

**Example:**
```json
{ "type": "neoorigins:effect_on_attacker", "effect": "minecraft:weakness", "duration": 100, "amplifier": 0 }
```

---

## `neoorigins:random_teleport`

Random-teleports the target within a bounded box. Retries up to `attempts` times, requiring 2-block air clearance at the destination.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `horizontal_range` / `range` | double | no | `16.0` | Half-width of the XZ search box |
| `vertical_range` | double | no | `8.0` | Half-height of the Y search range |
| `attempts` | int | no | `16` | Number of candidate positions to try |

Server-side only. Silently gives up if no viable spot is found in `attempts` tries.

**Example:**
```json
{ "type": "neoorigins:random_teleport", "horizontal_range": 8.0, "vertical_range": 4.0, "attempts": 32 }
```

---

## `neoorigins:chain_to_nearest`

Pulls the actor toward the nearest matching living entity within `radius`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | float | no | `16.0` | Search radius |
| `speed` | float | no | `1.0` | Launch speed along the vector |
| `target_condition` | condition | no | always-true | Filter applied to `ServerPlayer` candidates only (non-players are accepted as-is) |

**Example:**
```json
{ "type": "neoorigins:chain_to_nearest", "radius": 12.0, "speed": 1.2 }
```

---

## `neoorigins:pull_entities`

Pulls nearby living entities toward the actor: inverse of `chain_to_nearest`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | float | no | `8.0` | Search radius |
| `strength` | float | no | `0.5` | Pull strength (velocity magnitude) |
| `include_players` | bool | no | `true` | Include other players |
| `entity_condition` | condition | no | always-true | Filter (applied to `ServerPlayer` candidates only) |

**Example:**
```json
{ "type": "neoorigins:pull_entities", "radius": 6.0, "strength": 0.8, "include_players": false }
```

---

## `neoorigins:throw_target`

Hurls the single living entity directly under the actor's crosshair away from the actor and upward. Unlike `pull_entities` (radius AOE) this is a precise raycast pick: the entity must be visible along the look ray within `max_distance`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `force` | float | no | `1.5` | Horizontal velocity impulse magnitude (knockback strength) |
| `vertical_lift` | float | no | `0.5` | Upward velocity component, applied independently of `force` so packs can tune throw arc separately from horizontal range |
| `max_distance` | float | no | `5.0` | Raycast range from the actor's eye: targets beyond this are ignored |

Direction is the XZ vector from actor to target, so the throw is always purely "away horizontally + up". When the target's XZ position equals the actor's (rare: overhead pickup), the actor's look-yaw is used as the fallback horizontal direction.

The action sets `target.hurtMarked = true` after pushing so client-side prediction picks up the velocity change immediately (otherwise the visible knockback can lag a packet).

**Approximate empirical range** (vanilla LivingEntity drag, no obstacles):
- `force: 1.0` → target travels ~3–4 blocks
- `force: 2.0` → ~7–9 blocks
- `force: 3.0` → ~13–15 blocks

Not strictly linear: vanilla drag is non-trivial.

**Example: light shove**
```json
{ "type": "neoorigins:throw_target", "force": 1.2, "vertical_lift": 0.4, "max_distance": 4.0 }
```

**Example: heavy hurl**
```json
{ "type": "neoorigins:throw_target", "force": 2.5, "vertical_lift": 0.9, "max_distance": 6.0 }
```

Typically wrapped in `active_ability` for a cooldown + hunger cost.

---

## `neoorigins:tame_target`

Tames a mob as a side-effect of an action, using the same taming behaviour as the [`tame_mob`](../POWER_TYPES.md) active power — the tamed mob follows and defends the actor, is marked persistent, and is tracked for despawn + death-damage. This is the **action** form: use it inside an `entity_action` field (an `action_on_event` with `event: "attack"`, a `raycast` `bientity_action`, an `active_ability`, etc.) rather than as a standalone power.

The target is resolved in two steps:
1. **Dispatch context first**: if the action fires from an interaction that carries a target entity (the mob you hit, the entity a `raycast`'s `bientity_action` struck, a projectile's `on_hit_action` target), that entity is tamed.
2. **Crosshair fallback**: when no context target resolves, the mob under the actor's crosshair within `max_distance` is used.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `max_tamed` | int | no | `4` | Max tamed mobs alive at once. Shared with `tame_mob` through the same tracker, so they count toward one combined cap. Once reached the action no-ops. |
| `max_distance` | float | no | `5.0` | Crosshair raycast range, used **only** when no context target resolves |
| `despawn_ticks` | int | no | `36000` | Ticks the tamed mob lasts before despawning (36000 = 30 min) |
| `death_damage` | float | no | `0.5` | Damage dealt to the actor when the tamed mob dies (half-hearts) |
| `hostile_only` | bool | no | `true` | When true, only mobs implementing `Enemy` can be tamed; set false to allow any non-player `Mob` |
| `entity_blacklist` | array | no | `[]` | Entity ids (`"minecraft:warden"`) and tag refs (`"#mymod:untameable"`) this action can never tame |

The Warden, Ender Dragon and Wither (plus the server's `tame_scare_entity_blacklist` config list) are always excluded regardless of `entity_blacklist`. A capped, no-target, or excluded activation is a **silent no-op**; unlike `tame_mob`, this action does not send actionbar feedback, since it fires from events rather than a deliberate keybind.

Tamed mobs are persistent like vanilla pets: they survive the tamer's death (recalled to them on respawn), the tamer logging out, and full server restarts; the tame is stored on the mob itself and its loyalty AI is rebuilt whenever it loads. See [Persistence](../powers/mobs.md#persistence) under `tame_mob` for details.

**Example: tame the mob you hit, on attack**
```json
{
  "type": "neoorigins:action_on_event",
  "event": "attack",
  "entity_action": { "type": "neoorigins:tame_target", "hostile_only": false }
}
```

**Example: crosshair tame, no fallback distance change**
```json
{ "type": "neoorigins:tame_target", "max_tamed": 2, "entity_blacklist": ["minecraft:creeper"] }
```

---

## `neoorigins:dash`

Applies a forward impulse in the direction the player is currently facing. Unlike `add_velocity` (which uses fixed x/y/z), `dash` reads the player's look vector and projects `strength` along it, so looking up-forward causes a diagonal upward dash, horizontal look causes a flat dash, etc.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `strength` | float | no | `1.5` | Velocity magnitude along the look vector |
| `allow_vertical` | bool | no | `true` | When `false`, pins the dash to horizontal (ignores look Y component) |
| `set_velocity` | bool | no | `false` | When `true`, replaces delta movement with the dash vector instead of pushing additively |

Sets `hurtMarked = true` internally so the client doesn't discard the server-authoritative velocity change on the next movement packet: same guarantee as `add_velocity`.

**Example: cat pounce (2.2 strength, vertical allowed)**
```json
{ "type": "neoorigins:dash", "strength": 2.2, "allow_vertical": true }
```

**Example: shadow dash (ground-level only)**
```json
{ "type": "neoorigins:dash", "strength": 2.0, "allow_vertical": false }
```

Preferred canonical replacement for the legacy `active_dash` type when paired with `active_ability`.

---

## `neoorigins:swap_with_entity`

Swaps positions and facing with the nearest matching living entity within `radius`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | float | no | `16.0` | Search radius |
| `target_condition` | condition | no | always-true | Filter (applied to `ServerPlayer` candidates only) |

**Example:**
```json
{ "type": "neoorigins:swap_with_entity", "radius": 10.0 }
```

---

## `neoorigins:swap_positions`

Atomically swaps the actor's and the **context target's** full transform (position, yaw, pitch). The target is the entity on the other side of the interaction (the mob/player hit, hit-by, killed, interacted-with, or struck by a projectile), not a radius search. Both transforms are snapshotted before either entity moves, so the two never collapse to one point. No-op if no target resolves.

This is a dual-actor verb. Pair it with a bientity context (e.g. a projectile `on_hit_action`).

No fields.

**Example: a projectile that swaps you with whatever it hits**
```json
{ "type": "neoorigins:swap_positions" }
```

---

## `neoorigins:teleport_to_target`

Moves the **actor** to the context target's position and facing. No-op if no target resolves. No fields.

**Example:**
```json
{ "type": "neoorigins:teleport_to_target" }
```

---

## `neoorigins:teleport_target_to_self`

Moves the **context target** to the actor's position and facing. No-op if no target resolves. No fields.

**Example:**
```json
{ "type": "neoorigins:teleport_target_to_self" }
```

---

## `neoorigins:shear`

Shears the **context target** the way vanilla or modded shears would: sheep drop wool and go bald, mooshrooms convert to cows and drop their mushroom (or flower), snow golems lose their pumpkin, bogged and modded shearables behave correctly. Uses the NeoForge `IShearable` seam so any registered shearable is covered. No-op if no target resolves or the target isn't currently shearable (e.g. an already-sheared sheep or a non-shearable mob).

This is a dual-actor verb. Pair it with a bientity context (e.g. a projectile `on_hit_action`) or wrap it in `target_action` to hit an arbitrary mob target.

No fields.

**Example: a projectile that shears whatever it hits**
```json
{ "type": "neoorigins:shear" }
```

---

## `neoorigins:dye`

Sets the colour of a dyeable **context target**. This dyes a sheep's wool colour; mobs with no public colour setter (wolf/cat collars) no-op cleanly. No-op if no target resolves, the target is non-dyeable, or `color` is an unknown dye name.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `color` | string | no | — | Dye colour name (e.g. `"red"`, `"light_blue"`). Unknown names no-op. |

**Example: dye the sheep you hit red**
```json
{ "type": "neoorigins:dye", "color": "red" }
```

---

## `neoorigins:force_drop`

Makes the **context target** drop the item in a named equipment slot as an item entity, then clears that slot. No-op if no target resolves or the slot is empty.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `slot` | string | no | `mainhand` | Equipment slot: `mainhand` / `offhand` / `head` / `chest` / `legs` / `feet`. Unknown names fall back to `mainhand`. |

**Example: disarm the mob you hit**
```json
{ "type": "neoorigins:force_drop", "slot": "mainhand" }
```

---

## `neoorigins:steal_item`

Like `force_drop`, but transfers the item from the **context target's** named slot to the **actor** (the power holder): added to the actor's inventory, or dropped at the actor if the inventory is full. No-op if no target resolves or the slot is empty.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `slot` | string | no | `mainhand` | Equipment slot: `mainhand` / `offhand` / `head` / `chest` / `legs` / `feet`. Unknown names fall back to `mainhand`. |

**Example: steal the weapon out of the hand of whatever you hit**
```json
{ "type": "neoorigins:steal_item", "slot": "mainhand" }
```

---

## `neoorigins:drop_inventory`

Drops items from the target player's vanilla inventory. By default it scatters every slot's contents; restrict by slot list and/or an item condition. Only the `inventory` container is supported.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `inventory_type` | string | no | `"inventory"` | Only `inventory` (the vanilla player inventory) is supported; `power` inventories no-op |
| `slots` | array of string | no | — | Restrict to these vanilla/Apoli slot names (e.g. `weapon.mainhand`, `weapon.offhand`, `armor.head`). Omit or leave empty to scan every slot |
| `item_condition` | item condition | no | — | Only drop stacks matching this condition. Omit to drop everything |
| `throw_randomly` | bool | no | `true` | Scatter the dropped items |
| `retain_ownership` | bool | no | `false` | Tag drops with the thrower for pickup priority |

**Example: drop just the held weapon**
```json
{ "type": "neoorigins:drop_inventory", "slots": ["weapon.mainhand"] }
```

---

## Block-target verbs

These act on the **block on the other side of the interaction** (the block a projectile or raycast impacted) rather than on an entity. They resolve the impacted block from the active dispatch context (a projectile `on_hit_action` that lands on a block, a `raycast` `block_action`, a `block_use` / `bonemeal` interaction, or the block published by `block_action_at`, `offset` or the `area_of_effect` block fan-out), so you can write them directly as an `on_hit_action` / `block_action` and they self-resolve the hit block. Each no-ops cleanly when no block resolves or the block isn't applicable. To run one against a specific resolved block context explicitly, wrap it in [`block_target_action`](#neooriginsblock_target_action).

## `neoorigins:strip`

Axe-strips the **context block**: logs and wood (all vanilla wood families, including crimson/warped stems and hyphae, and bamboo blocks) become their stripped variant, preserving the pillar axis. No-op if the block has no strip mapping.

No fields.

**Example: a projectile that strips logs it hits**
```json
{ "type": "neoorigins:strip" }
```

## `neoorigins:till`

Hoe-tills the **context block**: grass / dirt / coarse-dirt / rooted-dirt / dirt-path become farmland (coarse dirt becomes plain dirt), but only when the block directly above is air (the vanilla hoe rule). No-op otherwise.

No fields.

**Example:**
```json
{ "type": "neoorigins:till" }
```

## `neoorigins:path`

Shovels the **context block** into a dirt path: grass / dirt / podzol / mycelium / coarse-dirt / rooted-dirt, only when the block directly above is air (the vanilla shovel rule). No-op otherwise.

No fields.

**Example:**
```json
{ "type": "neoorigins:path" }
```

## `neoorigins:grow` (alias `neoorigins:bonemeal`)

Applies one bonemeal-style growth tick to the **context block** if it's a bonemealable block ready to grow (crops, saplings, grass, etc.), with the green growth particles. No-op when the block isn't bonemealable or isn't valid for growth right now. `bonemeal` is the Apoli block-action name for the same verb, accepted so imported packs (e.g. an `area_of_effect` block fan-out authoring `origins:bonemeal`) dispatch here directly.

No fields.

**Example: a projectile that fertilises crops it hits**
```json
{ "type": "neoorigins:grow" }
```

## `neoorigins:transform_block`

The generic primitive: sets the **context block** to `to`, optionally only when it currently matches `from`. Use this as the fallback for any block state swap not covered by the verbs above. No-op when `to` is missing/unknown or the `from` guard fails.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `from` | string | no | — | Optional block-id guard; only transform when the impacted block matches (e.g. `"minecraft:stone"`). |
| `to` | string | yes | — | Block id to set the impacted block to (e.g. `"minecraft:gold_block"`). |

**Example: turn the stone you hit into gold**
```json
{ "type": "neoorigins:transform_block", "from": "minecraft:stone", "to": "minecraft:gold_block" }
```

## `neoorigins:block_target_action`

The block-side analogue of [`target_action`](bientity.md#target_action): resolves the impacted block from the active dispatch context and runs the inner block-target verb against it. No-op when no block context resolves or the inner action isn't a block-target verb (`strip` / `till` / `path` / `grow` or `bonemeal` / `transform_block` / `set_block`). The actor (the power holder) is the one running the power.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `action` | object | no | — | Inner block-target verb to run on the resolved context block. Object only |

**Example: a projectile that strips the block it hits, via the wrapper**
```json
{ "type": "neoorigins:block_target_action", "action": { "type": "neoorigins:strip" } }
```

---

## `neoorigins:teleport_to_marker`

Teleports the target to an absolute position or by a relative offset. The "marker" naming is aspirational: named-marker lookup is not yet wired; only absolute `position` and `dx`/`dy`/`dz` offsets are honoured.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `position.x` / `.y` / `.z` | double | no | — | Absolute target (if `position` is present, offsets are ignored). All three are required inside `position`; a missing one makes the action a no-op |
| `dx` / `dy` / `dz` | double | no | `0` | Relative offset from current position |

**Example:**
```json
{ "type": "neoorigins:teleport_to_marker", "dx": 0.0, "dy": 16.0, "dz": 0.0 }
```

---

## `neoorigins:equipped_item_action`

Runs an item action on the stack in a given equipment slot. Delegates per-stack verbs to `ItemActionParser`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `equipment_slot` | string | no | `"mainhand"` | `"head"`, `"chest"`, `"legs"`, `"feet"`, `"offhand"`, or `"mainhand"` (case-insensitive). Any other value warns and the action does nothing |
| `item_action` | object | yes | — | Item action to run (see Item Actions below). The older key `action` is read when `item_action` is absent. Object only |
| `reset_repair_cost` | bool | no | `false` | Handed down to a `remove_enchantment` item action: also clear the stack's anvil repair cost |

**Example: damage the held item by 5**
```json
{ "type": "neoorigins:equipped_item_action",
  "equipment_slot": "mainhand",
  "item_action": { "type": "neoorigins:damage", "amount": 5 } }
```

---

## `neoorigins:modify_inventory`

Iterates inventory slots and runs an item action on each matching stack.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `item_action` | object | yes | — | Item action to run per matching stack |
| `item_condition` | object | no | — | Item condition to filter stacks (see Item Conditions below); all stacks if absent |
| `slot` | string | no | — | Restrict to a single slot: `mainhand`, `offhand`, `head`, `chest`, `legs`, `feet`, or a raw inventory index; iterates all slots if absent. An unknown name warns and the action does nothing |
| `process_mode` | string | no | `"items"` | `"items"` counts every item of a processed stack toward `limit`; `"stacks"` counts each stack as 1 |
| `limit` | int | no | `0` | Stop once this many items / stacks have been processed; `0` = no limit. Checked before each stack, so the stack that crosses the limit is still processed whole |

**Example: consume all rotten flesh in inventory**
```json
{ "type": "neoorigins:modify_inventory",
  "item_condition": { "type": "neoorigins:ingredient", "item": "minecraft:rotten_flesh" },
  "item_action": { "type": "neoorigins:consume" } }
```

---

## `neoorigins:raycast`

Performs a block and/or entity raycast from the player's eye position along their look vector. Runs nested actions at the hit position.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `distance` | double | no | `10.0` | Max raycast distance |
| `block` | bool | no | `true` | Include block hits |
| `entity` | bool | no | `false` | Include entity hits |
| `fluid_handling` | enum | no | `none` | How fluids are treated: `none` / `source_only` / `any` |
| `shape_type` | enum | no | `visual` | Block shape used for the trace: `visual` or `collider` |
| `block_action` | object | no | — | Action to run at the hit block position (dispatched with `RaycastBlockContext` so `~ ~ ~` resolves to the hit block) |
| `bientity_action` | object | no | — | Entity action run on the **caster** when a living entity is hit, with the hit entity published as the context target: wrap target-side effects in `target_action` (a bare `origins:damage` here hurts the caster). Entity hits are checked before blocks |
| `miss_action` | object | no | — | Action to run when nothing is hit within range |
| `before_action` | object | no | — | Entity action run once before the ray is cast, regardless of hit outcome (e.g. consume a reagent) |
| `command_along_ray` | string | no | — | Command run at each step along the ray, hit or miss. Bare `/particle <id> ~ ~ ~` commands are force-rendered so trails stay visible past the client particle setting |
| `command_step` | double | no | `1.0` | Block increment between `command_along_ray` executions |
| `command_at_hit` | string | no | — | Command run at the precise impact point when the ray hits a block or entity (same force-rendering as `command_along_ray`) |

**Example: execute command at the looked-at block**
```json
{ "type": "neoorigins:raycast",
  "distance": 32.0,
  "entity": false,
  "block_action": {
    "type": "neoorigins:execute_command",
    "command": "particle minecraft:flame ~ ~ ~ 0.5 0.5 0.5 0.1 20"
  } }
```

---

## `neoorigins:cast_spell`

Casts an inline, author-baked **[Build A Spell](https://modrinth.com/mod/buildaspell)** (`buildaspell`) spell as the player. This is an integration action: it only does anything when Build A Spell is installed. Gate any power that uses it with the top-level `"required_mods": ["buildaspell"]` field so the power doesn't even load without BaS. Without that gate the action degrades to a logged no-op when BaS is absent.

The spell is assembled once at datapack-load time and reused on every dispatch. Cost is charged on **your** NeoOrigins power (resource / hunger / cooldown), so the BaS mana pool is never touched, but BaS's own per-component enable/disable config toggles are still honoured.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `delivery` | enum | yes | — | One of Build A Spell's 5 delivery methods: `rune`, `sight`, `self`, `cast`, `tracking` |
| `components` | array | yes | — | The ordered component list (effect / modifier / `compat:*` ids). See ordering below |

### Component order is EFFECT-FIRST

`components` is a single ordered list, not a split of effects and modifiers. **A modifier binds to the effect that precedes it**, so each effect id is followed by the modifier ids that apply to it. A modifier appearing *before the first effect is silently dropped*.

```
EFFECT-FIRST (correct):  ["damage", "increased_power", "increased_power", "lightning", "chain"]
                          ^ damage, doubly powered          ^ lightning, chained
```

Any id that isn't a known effect, a known modifier, or a `compat:`-prefixed compat effect causes the whole spell to fail to build (logged, naming the bad id) and the power does nothing. The list is capped at 30 components and a duplicate non-stackable modifier is dropped: the same limits a player hits in the in-game builder.

> **Easiest authoring path:** build the spell in the in-game Build A Spell editor, hit its **"Export to Power"** button, and paste. The button emits the `components` list in the correct effect-first order; hand-writing it is the error-prone path.

**Example: a doubly-powered, chained lightning bolt on an active ability**
```json
{
  "type": "neoorigins:active_ability",
  "key": "key.neoorigins.spell",
  "cooldown_ticks": 40,
  "entity_action": {
    "type": "neoorigins:cast_spell",
    "delivery": "cast",
    "components": ["damage", "increased_power", "increased_power", "lightning", "chain"]
  }
}
```
(The inner `cast_spell` block is a plain `entity_action`; lift it into `action_on_event` / `condition_passive` for a passive trigger.)

---

## `neoorigins:cast_iron_spell`

Casts a spell from **[Iron's Spells 'n Spellbooks](https://modrinth.com/mod/irons-spells-n-spellbooks)** (`irons_spellbooks`) as the player. This is an integration action: it only does anything when Iron's Spells is installed. Gate any power that uses it with the top-level `"required_mods": ["irons_spellbooks"]` field so the power doesn't even load without the mod. Without that gate the action degrades to a logged no-op when Iron's Spells is absent.

By default cost is charged on **your** NeoOrigins power (resource / cooldown) and the Iron's mana pool is left untouched (`consume_mana: false`). Set `consume_mana: true` to draw from and gate on the player's Iron's mana instead: an under-funded cast is refused (logged no-op); creative players bypass the check.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `spell` | string | yes | — | The Iron's spell id, e.g. `irons_spellbooks:fireball`, `irons_spellbooks:magic_missile`, `irons_spellbooks:heal`, `irons_spellbooks:teleport`, `irons_spellbooks:chain_lightning`. Unknown ids resolve to a logged no-op |
| `level` | int | no | `1` | Spell level (power). Clamped to at least 1 and to the spell's own max level |
| `consume_mana` | bool | no | `false` | Draw from + gate on the player's Iron's mana pool. Creative bypasses the gate |
| `trigger_cooldown` | bool | no | `true` | Apply the spell's Iron's cooldown after casting |
| `mode` | enum | no | `instant` | `instant` fires the effect directly in one shot (best for keybind / on-hit triggers). `channel` runs the full animated cast: INSTANT spells complete inline, LONG / CONTINUOUS spells channel over time |

**Example: a level-2 fireball on an active ability**
```json
{
  "type": "neoorigins:active_ability",
  "key": "key.neoorigins.spell",
  "cooldown_ticks": 60,
  "entity_action": {
    "type": "neoorigins:cast_iron_spell",
    "spell": "irons_spellbooks:fireball",
    "level": 2
  }
}
```

---

## `neoorigins:crafting_table`

Opens a 3×3 crafting menu for the target player, anchored at the player's position (so any recipe needing the table works). Takes no fields. Player-only.

**Example:**
```json
{ "type": "neoorigins:crafting_table" }
```

---

## `neoorigins:kubejs_callback`

Invokes a KubeJS-registered callback by id, handing it the acting player. Use it to run custom script logic from a power. With no callback registered for the id — KubeJS absent, or the script has not run yet — the action is silently dropped rather than erroring. See [KUBEJS.md](../KUBEJS.md).

> **1.21.1 vs 26.x:** available on 1.21.1 and 26.1; 26.2 and 26.3 have no KubeJS integration, so the action does not exist there.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `id` | string | yes | — | Id the script passed to `NeoOrigins.registerCallback` |

**Example:**
```json
{ "type": "neoorigins:kubejs_callback", "id": "examplepack:on_dash" }
```

---

## `neoorigins:trigger_morph_animation`

Plays (or stops) a named animation on the model a player is currently morphed into with `neoorigins:entity_model`. Requires GeckoLib; no-ops silently without it, and no-ops silently when the player is not currently rendering as a morph model — a morph power carrying only non-model tweaks leaves nothing for this verb to animate.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `animation` | string | yes | — | Triggerable animation name registered by the morph mob's own author |
| `controller` | string | no | — | Animation controller to search; omit the field to search every controller on the model. Omit it, do not set it to `""`: an empty string is passed through as a controller name, and no model has a controller called `""`, so the animation silently never plays |
| `stop` | boolean | no | `false` | Stop the triggered animation instead of starting it |

**Example:**
```json
{ "type": "neoorigins:trigger_morph_animation", "animation": "attack", "controller": "controller" }
```

**You cannot invent animation names.** GeckoLib only plays names the mob's author explicitly registered as triggerable, by calling `triggerableAnim("name", …)` when they built the model's controllers. Every other animation in the mob's `.animation.json` is driven by that mob's own code and is not reachable this way. An unregistered name is not an error — the call simply does nothing, with no log line — so if nothing happens, the name is almost certainly not on the mob's triggerable list. There is no way for a pack to discover that list from JSON; it comes from the mod's source or its author.

Morphing into a vanilla mob does nothing here: vanilla models are not GeckoLib models. This verb is for morphs into GeckoLib-animated modded mobs.

The animation runs on every client that can see the morphed player, not just their own.

Only GeckoLib is supported today. AzureLib is shaped the same way and could be added; Citadel is not, because its animations are numeric ids that need a per-tick pump the morph does not run.

---

## `neoorigins:morph_entity_event`

Runs an entity event on the model a player is morphed into — the same hook the server normally uses to tell clients "this mob just did a thing": an iron golem's attack swing, a villager's angry particles, a wolf shaking off water. It is what makes a morph react like the real mob instead of only sharing its silhouette. No-ops silently when the player is not currently rendering as a morph model.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `event` | enum | no | — | Named vanilla event (see below) |
| `id` | integer | no | — | Raw event byte, −128…127, for modded mobs with their own; ignored when `event` is set |

**Examples:**
```json
{ "type": "neoorigins:morph_entity_event", "event": "villager_angry" }
```
```json
{ "type": "neoorigins:morph_entity_event", "id": 20 }
```

Give one of `event` or `id`. `event` accepts Minecraft's own entity-event constant names, lowercased — `jump`, `start_attacking`, `stop_attacking`, `taming_failed`, `taming_succeeded`, `shake_wetness`, `use_item_complete`, `eat_grass`, `offer_flower`, `love_hearts`, `villager_angry`, `villager_happy`, `witch_hat_magic`, `zombie_converting`, `fireworks_explode`, `in_love_hearts`, `squid_anim_synch`, `silverfish_merge_anim`, `guardian_attack_sound`, `attack_blocked`, `shield_disabled`, `fishing_rod_reel_in`, `armorstand_wobble`, `stop_offer_flower`, `talisman_activate`, `dolphin_looking_for_treasure`, `ravager_stunned`, `trusting_failed`, `trusting_succeeded`, `villager_sweat`, `fox_eat`, `teleport`, `mainhand_break`, `offhand_break`, `head_break`, `chest_break`, `legs_break`, `feet_break`, `body_break`, `honey_slide`, `honey_jump`, `swap_hands`, `cancel_shake_wetness`, `start_ram`, `end_ram`, `poof`, `tendrils_shiver`, `sonic_charge`, `sniffer_digging_sound`, `armadillo_peek`, plus the `reduced_debug_info`/`full_debug_info`/`permission_level_*` set. The list is read from Minecraft's own table, so it tracks the game version rather than a hand-copied snapshot; the `event` enum in `action.schema.json` is the authoritative spelling for the version you are on. `id` is the escape hatch for modded mobs, which are free to define bytes of their own.

Which bytes a given mob actually reacts to is that mob's business — most ignore most of them. Sending an event a mob does not handle is harmless and does nothing. A name that is not on the list is a different matter: the action is dropped at load with a warning naming the accepted values, and an `id` alongside it does not rescue it, because `id` is only read when `event` is absent.

The event runs on every client that can see the morphed player, not just their own.

**Safety limits.** Two values are refused, both with a warning in the log, and neither is a balance decision:

* **Byte 3 (`death`) is rejected** and is not in the `event` list. On a `LivingEntity` it calls `die()`, which fires a client-side death event for an entity that was never added to the world — any mod listening for client-side deaths would see a phantom kill it cannot make sense of. Morphs already topple on real death without this, because the renderer mirrors the player's death state onto the model.
* **`id` outside −128…127 is rejected**, because the value is a byte and anything larger would silently wrap into a different event.

There is no hurt event. Vanilla removed byte 2 in 1.19.4 and moved the damage flash to its own packet, and morphs already flash red from the player's own hurt timer.

**Known limits.** These are consequences of the morph being a display-only stand-in that never runs the mob's `tick()`, and are documented rather than worked around:

* **Some events are silent.** Events whose vanilla code plays the sound through the entity itself produce no sound on a morph: that path asks the level to play the sound for everyone except one player, and on a client the level only honours it when that one player is the local player, which it never is here. An iron golem's attack is one of these. Events that instead ask the level for a sound at a position do work, an armadillo's peek among them. There is no flag to tell the two apart from JSON — try it and listen.
* **Timer-driven events freeze mid-pose.** Events that set a countdown the mob's own tick is supposed to drain — an iron golem's attack swing, a sheep's eating dip, a ravager's stun — set the countdown and nothing ever decrements it, so the morph holds that pose rather than playing through it. Particle and sound events are unaffected.
* **Particle events that read movement streak.** A few events spawn particles along the line between the entity's previous and current position; `teleport` is the one to watch. The morph never records a previous position, so that line is drawn from the world origin and the particles trail in across the map.
