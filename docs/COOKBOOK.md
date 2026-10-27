---
title: Cookbook
parent: Guides
nav_order: 3
---

# NeoOrigins Pack-Builder Cookbook

Working patterns to copy and adapt. Each recipe is a complete power file (or a small set of them). The reference pages hold every field: [Power Types](POWER_TYPES.md), [Entity Actions](ACTIONS.md), [Conditions](CONDITIONS.md) and [Events](EVENTS.md).

New to NeoOrigins? Start with [Getting started](guides/getting-started.md). Debugging a pack? See [Test and debug a pack](guides/testing.md).

Contents:

- [Passive stats and effects](#passive-stats-and-effects)
- [Hazards and weaknesses](#hazards-and-weaknesses)
- [Diet and food](#diet-and-food)
- [Combat triggers](#combat-triggers)
- [Active abilities and costs](#active-abilities-and-costs)
- [Projectiles and spells](#projectiles-and-spells)
- [Toggles](#toggles)
- [Mobs and mounts](#mobs-and-mounts)
- [Mining and movement](#mining-and-movement)
- [Origins, layers and respawn](#origins-layers-and-respawn)
- [Where to go next](#where-to-go-next)

---

## Passive stats and effects

<a id="1-passive-health-boost-4-max-hp"></a>
### Passive health boost: +4 max HP

```json
{
  "type": "neoorigins:attribute_modifier",
  "name": "Tough Skin",
  "description": "+2 hearts maximum health.",
  "attribute": "minecraft:max_health",
  "amount": 4,
  "operation": "add_value"
}
```

---

<a id="2-always-has-night-vision"></a>
### Always has night vision

```json
{
  "type": "neoorigins:enhanced_vision",
  "name": "Night Adapted",
  "description": "See clearly in the dark."
}
```

`enhanced_vision` sets a brightness floor without the intrusive full-white
overlay of the vanilla Night Vision effect. It takes no keybind slot; the
player's "Toggle Night Vision" key (K by default) switches it off and on. The
codec also accepts an `exposure` field (0.0-1.0), but the client currently
ignores it and applies a fixed floor.

---

<a id="5-slow-fall-from-high-places"></a>
### Slow fall from high places

```json
{
  "type": "neoorigins:persistent_effect",
  "name": "Hollow Bones",
  "description": "Always falls slowly.",
  "effect": "minecraft:slow_falling",
  "amplifier": 0,
  "show_icon": false,
  "toggleable": false
}
```

`persistent_effect` applies the effect at infinite duration and re-applies
it if it goes missing. Set `show_icon: false` so the effects HUD doesn't
clutter. Set `toggleable: false` so the player can't turn it off.

---

<a id="6-heal-in-water"></a>
### Heal in water

```json
{
  "type": "neoorigins:condition_passive",
  "name": "Aquatic Recovery",
  "description": "Regenerates while submerged in water.",
  "condition": { "type": "neoorigins:submerged_in", "fluid": "minecraft:water" },
  "entity_action": { "type": "neoorigins:heal", "amount": 0.5 },
  "interval": 40
}
```

`condition_passive` is the workhorse 2.0 type: pair any condition with any
action, and it fires every `interval` ticks when the condition holds.
Replaces the old `biome_buff`, `regen_in_fluid`, `burn_at_health_threshold`,
and several others.

---

### Moon-blessed armor (condition-gated attribute)

Attribute modifiers accept a `condition` that edge-triggers at ~5-tick
intervals. Pair with the new `neoorigins:night` condition for
"stronger after dark" flavor.

```json
{ "type": "neoorigins:attribute_modifier",
  "attribute": "minecraft:armor",
  "amount": 4.0, "operation": "add_value",
  "condition": { "type": "neoorigins:night" } }
```

The modifier is applied/removed automatically as daytime flips. Any
condition works here: `neoorigins:thundering` for storm-blessed
equipment, `neoorigins:climbing` for scaling-specific buffs, etc.

---

### Warm near a campfire (near_block condition)

`neoorigins:near_block` scans a cubic radius for matching blocks and
fires when any are found. Accepts any combination of `block`/`blocks`/
`tag`/`tags` fields with logical OR.

```json
{ "type": "neoorigins:condition_passive",
  "condition": {
    "type": "neoorigins:near_block",
    "tags": ["minecraft:campfires", "#minecraft:fire"],
    "radius": 5
  },
  "entity_action": {
    "type": "neoorigins:apply_effect",
    "effect": "minecraft:fire_resistance",
    "duration": 100, "amplifier": 0
  },
  "interval": 20
}
```

Radius defaults to 4 and is clamped to 1-16 to keep the scan cheap.

---

### Periodic feed / heal via `neoorigins:action_over_time`

NeoOrigins' built-in `neoorigins:tick_action` ships no behaviour at all: it dispatches nothing. For any periodic action (periodically restore hunger, heal a fixed amount, apply an effect, run an arbitrary entity-action), use `neoorigins:action_over_time`. It is an alias for [`neoorigins:condition_passive`](powers/core.md#neooriginscondition_passive) (identical fields), so an optional `condition` gates whether the action runs each interval.

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

`interval` is in ticks (20 = 1 second). The `entity_action` runs against the player. An optional `condition` (any `ConditionParser` verb, e.g. `neoorigins:exposed_to_sun`) gates each run. Any verb supported by `ActionParser` works (`neoorigins:apply_effect`, `neoorigins:damage`, `neoorigins:execute_command`, `neoorigins:if_else` for conditional wrapping, etc.).

---

## Hazards and weaknesses

<a id="4-fire-weakness-50-damage-from-fire"></a>
### Fire weakness: +50% damage from fire

```json
{
  "type": "neoorigins:modify_damage",
  "name": "Flammable",
  "description": "Takes +50% damage from fire.",
  "direction": "in",
  "damage_type": "#minecraft:is_fire",
  "multiplier": 1.5
}
```

Tag-based damage filters (`#minecraft:is_fire`) catch IN_FIRE + ON_FIRE +
LAVA + HOT_FLOOR + FIREBALL in one rule. Bare `damage_type` strings
(`"lava"`) match only that specific source.

---

<a id="10-lose-1-hp-every-2s-while-in-daylight-vampire-pattern"></a>
### Lose 1 HP every 2s while in daylight (Vampire pattern)

```json
{
  "type": "neoorigins:condition_passive",
  "name": "Sun Allergy",
  "description": "Takes damage while exposed to daylight.",
  "condition": { "type": "neoorigins:exposed_to_sun" },
  "entity_action": { "type": "neoorigins:damage", "amount": 1, "source": { "name": "on_fire" } },
  "interval": 40
}
```

`neoorigins:exposed_to_sun` is the canonical day-exposure condition: it
checks both time-of-day and that the block column above the player lets
skylight through.

---

## Diet and food

<a id="3-carnivore-can-only-eat-meat"></a>
### Carnivore: can only eat meat

```json
{
  "type": "neoorigins:food_restriction",
  "name": "Carnivore",
  "description": "Can only eat meat.",
  "mode": "whitelist",
  "item_tag": "#minecraft:meat"
}
```

For finer control, list several tags under `item_tag` as an array; the
player can eat anything in any listed tag. Every entry is read as an item
tag (the leading `#` is optional), so put individual items in a tag of
your own rather than listing bare item IDs.

---

<a id="3b-restricted-diet--make-non-food-items-edible-skeleton-pattern"></a>
### Restricted diet + make non-food items edible (Skeleton pattern)

Combine `food_restriction` to block normal food with `edible_item` to
make non-food items consumable. The Skeleton origin uses this pattern:
only bone meal, rotten flesh, and spider eyes are edible.

**Step 1. Restrict to a whitelist tag:**

Create the item tag at `data/mypack/tags/item/skeleton_foods.json`:
```json
{
  "replace": false,
  "values": [
    "minecraft:bone_meal",
    "minecraft:rotten_flesh",
    "minecraft:spider_eye"
  ]
}
```

**Step 2. Block all food except the whitelist:**
```json
{
  "type": "neoorigins:food_restriction",
  "mode": "whitelist",
  "item_tag": "mypack:skeleton_foods"
}
```

**Step 3. Make bone meal edible** (rotten flesh and spider eye are
already vanilla food items, but bone meal is not):
```json
{
  "type": "neoorigins:edible_item",
  "tags": ["mypack:skeleton_bone_meal"],
  "nutrition": 3,
  "saturation": 0.4,
  "always_edible": true
}
```

With the companion tag at `data/mypack/tags/item/skeleton_bone_meal.json`:
```json
{ "replace": false, "values": ["minecraft:bone_meal"] }
```

The origin JSON references both powers: `food_restriction` blocks
normal eating, `edible_item` grants the bone meal override. The
whitelist tag must include every food item the player can eat. Items
that `edible_item` makes edible and that have no vanilla food component
(bone meal here) never reach the `food_restriction` check, so listing
them is harmless but not required.

---

<a id="3c-food-restores-more-hunger-and-saturation"></a>
### Food restores more hunger and saturation

Use the dedicated `neoorigins:modify_food_nutrition` power type. It
**overrides** the nutrition of matching food to an absolute target value
(it is not a multiplier), and rescales saturation proportionally to the
food's original saturation-to-nutrition ratio. Filter to a single item with
`food_item` or to a group with `food_tag`; omit both to retune every food.
If AppleSkin is installed its tooltip and held-food preview show your value
rather than the vanilla one, so players can see the buff before they eat.

```json
{
  "type": "neoorigins:modify_food_nutrition",
  "name": "Hearty Bread",
  "description": "Bread fills more hunger; saturation scales with it.",
  "food_item": "minecraft:bread",
  "nutrition": 8
}
```

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `nutrition` | int | no | `1` | Target hunger value matching food is set to. Saturation rescales proportionally. |
| `food_item` | string | no | — | Restrict to this exact item id. |
| `food_tag` | string | no | — | Restrict to this item tag (leading `#` optional). |

To add a flat bonus on top of normal eating instead of overriding, pair
`neoorigins:modify_food` with a `food_finished` action. (`food_eaten` fires
when the player starts eating, before the food applies; use it to cancel an
eat, not to add to one.)

```json
{
  "type": "neoorigins:action_on_event",
  "name": "Well Fed",
  "description": "Gain +2 extra saturation whenever you eat.",
  "event": "food_finished",
  "entity_action": {
    "type": "neoorigins:modify_food",
    "food": 0,
    "saturation": 2.0
  }
}
```

---

### Eat stone for food, eat diamonds for Luck (Caveborn consumables)

Chained pattern: an `edible_item` power makes items consumable, and an
`action_on_event` power listens on `ITEM_USE_FINISH` to apply a bonus
when a matching item is eaten. Cross-reference using a custom item tag
in `food_item_in_tag`.

**Edibility side:**
```json
{ "type": "neoorigins:edible_item",
  "tags": ["mypack:eat_diamond"],
  "nutrition": 6, "saturation": 1.2, "always_edible": true }
```

**Bonus side:**
```json
{ "type": "neoorigins:action_on_event",
  "event": "item_use_finish",
  "entity_action": {
    "type": "neoorigins:if_else",
    "condition": { "type": "neoorigins:food_item_in_tag",
                   "tag": "#mypack:eat_diamond" },
    "if_action": { "type": "neoorigins:apply_effect",
                   "effect": "minecraft:luck", "duration": 6000, "amplifier": 1 }
  }
}
```

Ship the item tag at `data/mypack/tags/item/eat_diamond.json`.

---

## Combat triggers

<a id="7-on-kill-heal-2-hearts"></a>
### On kill: heal 2 hearts

```json
{
  "type": "neoorigins:action_on_event",
  "name": "Blood Diet",
  "description": "Feed on the fallen.",
  "event": "kill",
  "entity_action": { "type": "neoorigins:heal", "amount": 4 }
}
```

See [EVENTS.md](EVENTS.md) for the full list of event keys. Common ones:
`kill`, `hit_taken`, `attack`, `jump`, `land`, `food_eaten`, `block_break`.

---

### Composing power sets

Individual 2.0 power types are intentionally narrow so they can be combined. For a "rat"-style origin that marks small mobs it kills and gets a heal buff when attacking anything on the list:

- `neoorigins:entity_set` — declares the UUID set (e.g. `mypack:kill_list`)
- `neoorigins:action_on_event` with `event: kill`, `entity_action: { type: neoorigins:add_to_set, set: mypack:kill_list }` — appends victims to the set
- `neoorigins:condition_passive` with `condition: { type: origins:target_in_set, set: mypack:kill_list }` and `entity_action: { type: neoorigins:heal, amount: 0.5 }` — heals when attacking a marked target

Each piece is a separate power entry in the origin's `powers` array. The `entity_set` power carries no behaviour on its own: it's the shared name other powers read and write.

---

## Active abilities and costs

<a id="8-active-ability-short-dash-forward"></a>
### Active ability: short dash forward

```json
{
  "type": "neoorigins:active_ability",
  "name": "Dash",
  "description": "Press the skill key to dash forward.",
  "cooldown_ticks": 60,
  "hunger_cost": 1,
  "entity_action": {
    "type": "neoorigins:dash",
    "strength": 1.2,
    "allow_vertical": true
  }
}
```

Active abilities take skill slots in the order they appear in the origin's
`powers` array: the first on Skill 1 (V by default), the next on Skill 2 (G),
and so on. To bind one to a named hotkey instead, set `key` to a number N
(Hotkey N).

---

### Forward dash in look direction

The `neoorigins:dash` action projects a strength magnitude along the
player's look vector. Canonical V2 replacement for `active_dash`.

```json
{ "type": "neoorigins:active_ability",
  "cooldown_ticks": 60, "hunger_cost": 2,
  "entity_action": { "type": "neoorigins:dash",
                     "strength": 2.2,
                     "allow_vertical": true } }
```

Set `allow_vertical: false` for ground-lock dashes that ignore look
pitch. Sets `hurtMarked = true` internally so client prediction
doesn't discard the impulse.

---

<a id="21-ability-with-a-resource-cost"></a>
### Ability with a resource cost

Moved to its own page: [Give an ability a cost](guides/costs.md).

---

## Projectiles and spells

<a id="11-projectile-that-drowns-enemies-in-an-aoe-at-the-impact-point"></a>
### Projectile that drowns enemies in an AoE at the impact point

Fires a snowball-sized projectile; where it lands, every undead in a
5-block radius takes magic damage and gets heavy slowness for six seconds.
This is one power: an active ability whose action fires a projectile
with an `on_hit_action` attached.

```json
{
  "type": "neoorigins:active_ability",
  "name": "Drowning Bolt",
  "description": "Fires a bolt that drowns enemies where it lands.",
  "cooldown_ticks": 200,
  "hunger_cost": 3,
  "entity_action": {
    "type": "neoorigins:spawn_projectile",
    "entity_type": "minecraft:snowball",
    "speed": 1.8,
    "inaccuracy": 0.2,
    "on_hit_action": {
      "type": "neoorigins:area_of_effect",
      "radius": 5.0,
      "include_source": false,
      "entity_condition": { "type": "neoorigins:target_group", "group": "undead" },
      "entity_action": {
        "type": "neoorigins:and",
        "actions": [
          { "type": "neoorigins:apply_effect",
            "effect": "minecraft:slowness",
            "duration": 120,
            "amplifier": 2,
            "show_icon": true },
          { "type": "neoorigins:damage",
            "amount": 1.0,
            "source": { "name": "drown" } }
        ]
      }
    }
  }
}
```

**The wiring (key piece introduced in 2.0.0):**

- **`on_hit_action`**: any action, fired when the projectile lands. Stored
  on the projectile at spawn time, drained on impact.
- **Impact-centered AoE**: `area_of_effect` inside an `on_hit_action`
  automatically centers on the impact point, not the caster's position.
  Works because the projectile-impact dispatcher installs a
  `ProjectileHitContext` on the action-context holder before invoking
  your action; `area_of_effect` reads it and rebuilds its AABB.
- **Undead-only filter**: `entity_condition` with `target_group: undead`
  filters each caught entity (mobs and players alike) before the
  damage+effect runs, so normal mobs and players aren't caught. Put the
  filter here, not in an `if_else` inside `entity_action`: `if_else` has no
  mob-target form, so an `if_else` there only ever runs on caught players.
- **Drown-tagged damage**: `source: { name: "drown" }` makes the hit read
  as drowning, with the correct sound and hit indicator.

**Tuning notes:**

- `radius: 5.0` is pragmatic. Smaller feels weak, larger hits from across
  the room.
- `duration: 120` = 6 seconds of Slowness II. Persistent enough to feel
  impactful, short enough that the next cooldown matters.
- `amount: 1.0` per hit, single-shot. For an attrition-style drowning,
  wrap in a `condition_passive` that fires at an interval while a marker
  entity exists at the impact point (more plumbing).

**For a fully custom projectile** (homing, chaining, trail effects), use a
custom entity type that extends `AbstractNeoProjectile`. See
[JAVA_API.md](JAVA_API.md#custom-projectiles). The reference
`neoorigins:homing_projectile` entity ships with the mod and demonstrates
the pattern; pack authors reference it directly:

```json
{
  "type": "neoorigins:spawn_projectile",
  "entity_type": "neoorigins:homing_projectile",
  "speed": 1.2,
  "on_hit_action": { "type": "neoorigins:heal", "amount": 2 }
}
```

The Java subclass handles per-tick AI (in this case, steering toward
the nearest living entity); the DSL `on_hit_action` still fires
independently on impact, so custom projectiles integrate cleanly with
the rest of the 2.0 action pipeline.

---

<a id="12-black-hole-pull-everything-nearby-toward-a-point-and-damage-the-center"></a>
### Black hole: pull everything nearby toward a point and damage the center

Active ability that spawns a lingering gravity well at the caster's
position. Pulls for two seconds, then expires.

```json
{
  "type": "neoorigins:active_ability",
  "name": "Singularity",
  "description": "Collapse space at your feet for a moment.",
  "cooldown_ticks": 400,
  "hunger_cost": 6,
  "entity_action": {
    "type": "neoorigins:spawn_black_hole",
    "radius": 8.0,
    "duration_ticks": 40,
    "pull_strength": 2.5,
    "damage_per_tick": 2.0,
    "effect_type": "sonic"
  }
}
```

**Tuning notes:**
- `duration_ticks: 40` = 2 seconds. Longer reads as overpowered fast.
- Combine with an `on_hit_action` on a `spawn_projectile` to drop the
  black hole wherever the projectile lands instead of the caster's feet.
  swap `entity_action` for:
  ```json
  { "type": "neoorigins:spawn_projectile",
    "entity_type": "neoorigins:magic_orb",
    "speed": 2.0,
    "on_hit_action": { "type": "neoorigins:spawn_black_hole", "radius": 6.0, "duration_ticks": 40 } }
  ```

---

<a id="13-tornado-fling-entities-in-a-cone"></a>
### Tornado: fling entities in a cone

```json
{
  "type": "neoorigins:active_ability",
  "name": "Whirlwind",
  "description": "Summon a tornado where you're looking.",
  "cooldown_ticks": 300,
  "hunger_cost": 5,
  "entity_action": {
    "type": "neoorigins:spawn_projectile",
    "entity_type": "neoorigins:magic_orb",
    "speed": 1.5,
    "effect_type": "frost",
    "on_hit_action": {
      "type": "neoorigins:spawn_tornado",
      "radius": 6.0,
      "duration_ticks": 80,
      "pull_strength": 1.5,
      "lift_strength": 0.8,
      "spin_strength": 0.8,
      "damage_per_interval": 1.5
    }
  }
}
```

**The wiring:**
- Projectile carries the tornado to wherever the caster aims.
- `on_hit_action` drops a `spawn_tornado` at the impact point (the
  projectile-hit context auto-rebases the spawn to the hit location).
- `lift_strength: 0.8` lifts targets ~2 blocks over the tornado's
  lifetime, enough to disorient without throwing them across the map.

---

<a id="14-lingering-damage-cloud-at-projectile-impact"></a>
### Lingering damage cloud at projectile impact

Classic poison/acid cloud pattern: the projectile carries a cloud, the
cloud ticks its action on an interval until it expires.

```json
{
  "type": "neoorigins:active_ability",
  "name": "Spore Bolt",
  "description": "Fires a spore that lingers where it lands.",
  "cooldown_ticks": 200,
  "hunger_cost": 3,
  "entity_action": {
    "type": "neoorigins:spawn_projectile",
    "entity_type": "neoorigins:magic_orb",
    "speed": 1.8,
    "effect_type": "spore",
    "on_hit_action": {
      "type": "neoorigins:spawn_lingering_area",
      "radius": 4.0,
      "duration_ticks": 120,
      "interval_ticks": 20,
      "particle_type": "minecraft:spore_blossom_air",
      "entity_action": {
        "type": "neoorigins:area_of_effect",
        "radius": 4.0,
        "include_source": false,
        "entity_action": { "type": "neoorigins:apply_effect",
          "effect": "minecraft:poison", "duration": 60, "amplifier": 1 }
      }
    }
  }
}
```

The nested `area_of_effect` inside the cloud's `entity_action` centers
on the cloud's position (not the caster's), because the lingering-area
tick passes the cloud's location to the action context holder.

---

<a id="17-arcane-bolt-a-straight-flying-spell-projectile-no-gravity"></a>
### Arcane bolt: a straight-flying spell projectile (no gravity)

Recipe #11 fires a projectile that arcs under gravity. Set
`no_gravity: true` and it flies dead straight along your aim like a laser
bolt, and the 2.1 visual fields let you style the orb without any Java.
Here it's a cyan sphere with a soul-fire trail that deals magic damage on
hit.

```json
{
  "type": "neoorigins:active_ability",
  "name": "Arcane Bolt",
  "description": "Fire a straight bolt of arcane energy.",
  "cooldown_ticks": 40,
  "hunger_cost": 2,
  "entity_action": {
    "type": "neoorigins:spawn_projectile",
    "entity_type": "neoorigins:magic_orb",
    "speed": 2.2,
    "no_gravity": true,
    "orb_color": "#30E0FF",
    "glow_color": "#1060FF",
    "shape": "sphere",
    "size": 0.35,
    "trail_particle": "minecraft:soul_fire_flame",
    "on_hit_action": {
      "type": "neoorigins:target_action",
      "action": {
        "type": "neoorigins:damage",
        "amount": 6.0,
        "source": { "name": "magic" }
      }
    }
  }
}
```

`on_hit_action` runs on the caster, so the damage is wrapped in
`target_action`, which retargets it onto the entity the bolt hit (and does
nothing on a block hit). A bare `damage` here would hurt the caster.

`no_gravity` works on **any** projectile entity (vanilla `minecraft:arrow`
included), not just the magic orb; it sets the vanilla no-gravity flag so
drag still applies but the projectile never drops.

---

<a id="18-elemental-cast-one-line-fireball--wind-bolt"></a>
### Elemental cast: one-line fireball / wind bolt

For a quick spell without wiring up `spawn_projectile` yourself, the
dedicated active cast powers fire a styled projectile on the skill key.
`active_fireball` lobs small fireballs; `active_bolt` throws a wind charge.

```json
{
  "type": "neoorigins:active_fireball",
  "name": "Fire Cast",
  "description": "Hurl a small fireball.",
  "speed": 1.6,
  "cooldown_ticks": 80
}
```

Swap to `"type": "neoorigins:active_bolt"` for a knockback wind charge
(`speed` default 1.2, `cooldown_ticks` default 80). Both take the next free
skill slot, like any active ability (see recipe #8).

---

## Toggles

<a id="15-toggleable-abilities-no-keybind-slot"></a>
### Toggleable abilities (no keybind slot)

Moved to its own page: [Make a toggle](guides/toggles.md).

---

## Mobs and mounts

<a id="9-summon-a-minion"></a>
### Summon a minion

```json
{
  "type": "neoorigins:summon_minion",
  "name": "Raise Skeleton",
  "description": "Call a skeleton guardian.",
  "mob_type": "minecraft:skeleton",
  "max_count": 2,
  "cooldown_ticks": 400,
  "hunger_cost": 6,
  "despawn_ticks": 12000,
  "death_damage": 2.0,
  "mainhand": "minecraft:bow",
  "head": "minecraft:iron_helmet"
}
```

Summons get their AI rewritten automatically: they won't attack the
summoner, and they fight back against whatever the summoner is fighting.
`death_damage` is the HP penalty when the minion falls in combat.

---

<a id="19-hunting-predator-conditional-mob-aggression-mob-origins"></a>
### Hunting predator: conditional mob aggression (mob origins)

`mob_behavior` rewrites a mob origin's AI so the mob hunts players. With
`aggression: "conditional"` the mob only turns hostile when **all**
`hostile_when` conditions hold against the prospective target: here, only
players who are in daylight and *not* sneaking. It still retaliates when
struck (`retaliate` defaults true) and calls nearby packmates.

```json
{
  "type": "neoorigins:mob_behavior",
  "aggression": "conditional",
  "aggro_range": 24.0,
  "anger_linger_ticks": 300,
  "call_for_help": true,
  "hostile_when": [
    { "type": "neoorigins:exposed_to_sun" },
    { "type": "neoorigins:not",
      "condition": { "type": "neoorigins:sneaking" } }
  ]
}
```

Set `aggression: "hostile"` to always hunt (skip `hostile_when`), or leave
it `"neutral"` to keep vanilla AI and only fight back when hit.
`target_type` lets the mob hunt another entity type instead of players.

---

<a id="20-beast-rider-mount-entities-on-demand"></a>
### Beast rider: mount entities on demand

`mount` raycasts for an entity in front of you and seats you on it. Great
for a tamer/druid origin: ride mobs (and optionally players) with one key.

```json
{
  "type": "neoorigins:mount",
  "name": "Mount Beast",
  "description": "Leap onto the creature you're looking at.",
  "range": 6.0,
  "cooldown_ticks": 60,
  "allow_mobs": true,
  "allow_players": false,
  "mount_position": "shoulder"
}
```

`block_bosses` (default true) keeps players off the Ender Dragon and Wither.
`mount_position: "shoulder"` offsets the rider to one side instead of
sitting centered on top.

---

## Mining and movement

### Bare-hand stone mining (Caveborn pattern)

Make the player's empty hand behave as a vanilla tool at any tier,
enabling proper drops and mining speed without holding an actual item.

```json
{ "type": "neoorigins:bare_hand_tool", "tool": "minecraft:stone_pickaxe" }
```

Stack multiple instances to emulate several tool types simultaneously
(pickaxe + axe + shovel). Config accepts any vanilla tool item ID; the
runtime reads the item's tool component to determine correct-for-drops
and break speed. Only fires while the main hand is empty.

---

### Fortune-from-effect (Mining Fortune gated by a buff)

Combine with the recipe above: while the player has `minecraft:luck`
(granted by the consumable), ore blocks drop as if mined with a real
Fortune pickaxe. Vanilla `ApplyBonusCount.ORE_DROPS` math, so the
distribution matches a Fortune enchantment exactly.

```json
{ "type": "neoorigins:fortune_when_effect",
  "effect": "minecraft:luck", "level": 2, "target": "#c:ores" }
```

Ancient debris is hardcoded-excluded (vanilla parity: Fortune doesn't
affect netherite drops). Pack authors can narrow `target` to e.g.
`#minecraft:diamond_ores` for a diamond-only bonus.

---

### Elytra-style flight without an elytra (Phantom/Elytrian)

Grant gliding behavior: press jump while falling, spread wings like
a vanilla elytra user. No chest-slot requirement.

```json
{ "type": "neoorigins:natural_glide" }
```

Pair with `neoorigins:elytra_boost` for launch-speed amplification.
Contrast: `neoorigins:flight` is a toggle power (it takes a skill slot)
that, while on, launches the same elytra-style flight from a jump in
mid-air. `natural_glide` needs no toggle; it is pitch-based gliding, the
real-elytra feel.

---

## Origins, layers and respawn

<a id="16-reroll-my-custom-layer-wiring-up-a-spare-orb"></a>
### Reroll my custom layer: wiring up a spare orb

NeoOrigins ships six orb items. Two have hardcoded behaviour:
`neoorigins:orb_of_origin` rerolls the origin layer and
`neoorigins:orb_of_class` rerolls the class layer. The other four
(`gold_orb`, `pink_orb`, `purple_orb`, `teal_orb`) are deliberately
inert. Right-clicking one does nothing on its own. They exist so a pack
can bind its own meaning to them without registering an item, and they
have no crafting recipe for the same reason: the pack that claims an orb
decides what it costs.

> **1.21.1 vs 26.x:** a shaped recipe's `key` entries are objects on 1.21.1 (`"G": { "item": "minecraft:gold_ingot" }`) and bare ingredient strings on 26.x (`"G": "minecraft:gold_ingot"`).

All six are in the `#neoorigins:orbs` item tag.

The walkthrough below takes `neoorigins:gold_orb` and turns it into a
reroll token for a custom layer.

**Step 1: the custom layer.**

`data/mypack/origins/origin_layers/bloodline.json`:
```json
{
  "order": 3,
  "name": "mypack.layer.bloodline",
  "origins": [
    "mypack:bloodline_ashborn",
    "mypack:bloodline_tidecaller",
    "mypack:bloodline_stonekin"
  ],
  "allow_random": true,
  "auto_choose": false,
  "hidden": false,
  "enabled": true
}
```

The layer id is derived from the file path, so this layer is
`mypack:bloodline`. That is the id the orb has to name.

**Step 2: the power that binds the orb.**

`data/mypack/origins/powers/gold_orb_reroll.json`:
```json
{
  "type": "neoorigins:action_on_event",
  "name": "Bloodline Orb",
  "description": "Right-click a gold orb to re-pick your bloodline.",
  "hidden": true,
  "event": "item_use",
  "item_condition": { "item": "neoorigins:gold_orb" },
  "entity_action": {
    "type": "neoorigins:open_layer_picker",
    "layers": ["mypack:bloodline"],
    "commit_mode": "deferred",
    "cost": 3,
    "message": "Choose a new bloodline",
    "consume_item": true
  }
}
```

Three fields carry the whole behaviour:

- `item_condition` scopes the handler to the gold orb. Leave it off and
  the power fires on *every* item use: snowballs, ender pearls, food,
  everything. This is the single most common way to get this wrong.
- `layers` names the layers to clear and re-show. Everything not listed
  stays chosen. List more than one to reroll several at once.
- `consume_item` removes one orb from the main hand when the pick
  commits. Creative players keep it.

`commit_mode: "deferred"` means closing the picker without choosing is a
free cancel and restores the previous bloodline; the 3 XP levels are
charged only on commit. Use `"immediate"` if you want the cost paid up
front and a cancelled pick to leave the layer empty (the layer's
auto-default then fills it). Full field table in
[Core effect verbs](actions/effects.md#neooriginsopen_layer_picker).

**Step 3: grant the power.**

A power file on its own does nothing: something has to hand the power to
the player. An orb should work no matter what origin the holder picked,
so grant it globally rather than attaching it to one origin.

`data/mypack/global_powers/orbs.json`:
```json
{
  "entity_types": ["minecraft:player"],
  "powers": ["mypack:gold_orb_reroll"]
}
```

Global sets are granted on login and re-reconciled on `/reload`. See
[GLOBAL_POWERS.md](GLOBAL_POWERS.md). If you would rather the orb only
work for certain origins, list `mypack:gold_orb_reroll` in those origins'
`powers` array instead and skip this file.

This is the step that gets missed most often. The orb is craftable, the
power JSON parses clean, nothing happens on right-click, and the log is
silent: nobody holds the power.

**Step 4: the crafting recipe.**

`data/mypack/recipe/gold_orb.json`:
```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": [
    "GRG",
    "RER",
    "GRG"
  ],
  "key": {
    "G": "minecraft:gold_ingot",
    "R": "minecraft:redstone",
    "E": "minecraft:ender_eye"
  },
  "result": {
    "id": "neoorigins:gold_orb",
    "count": 1
  }
}
```

Two things to watch on the recipe:

- The folder is `recipe`, singular. `recipes` is the pre-1.21 spelling
  and a file left there loads nothing, silently.
- The `key` entry shape changed between Minecraft versions. On 26.1 and
  later each key maps to a bare ingredient string:
  `"G": "minecraft:gold_ingot"`, as above. On 1.21.1 it is an object:
  `"G": { "item": "minecraft:gold_ingot" }`. Compare
  `data/neoorigins/recipe/orb_of_class.json` in the jar for the version
  you are building against.

To hand the orb out instead of crafting it, skip step 4 and use
`/give @s neoorigins:gold_orb`, a loot table, or a villager trade. Steps
1-3 are what make the orb do anything; the recipe is only how the player
gets one.

**Binding all four spare orbs to different layers.** Write one power per
orb, each with its own `item_condition` and `layers`. They can live on
the same origin or on a global power set; the handler only cares that
the player has the power and is holding the right orb.

---

### Sleepless origin with bed-anchored respawn

`prevent_action SLEEP` blocks sleep transitions. The
`SleepPreventionEvents` handler automatically sets the player's
respawn point at the bed before cancelling, so players still get the
anchor benefit — great for vampires, phantoms, wardens.

```json
{ "type": "neoorigins:prevent_action", "action": "SLEEP" }
```

Feedback message *"You don't need sleep, but you remember this place."*
fires on bed interaction.

---

## Where to go next

- **[POWER_TYPES.md](POWER_TYPES.md)**: every power type with full field
  tables. When a recipe here mentions a type, the details live there.
- **[CONDITIONS.md](CONDITIONS.md)**: the 90+ condition verbs you can
  use in `condition` fields.
- **[ACTIONS.md](ACTIONS.md)**: the 60+ action verbs you can use in
  `entity_action` fields.
- **[EVENTS.md](EVENTS.md)**: the event keys for `action_on_event`.
- **[MIGRATION.md](MIGRATION.md)**: if you're porting a pre-2.0 pack or
  an upstream Origins/Apoli/Apugli pack.
- **[ARCHITECTURE.md](ARCHITECTURE.md)**: for understanding how powers
  are resolved, dispatched, and cached (useful for pack-author debugging
  but not required reading).

The source of truth for every alias, verb, and event key is the Java code
under `src/main/java/com/cyberday1/neoorigins/`. If a doc drifts from the
code, the code wins. When in doubt, grep.

---

## Moved from this page

These sections now live on other pages.

<div id="no-anchor-stub"></div>

- <a id="zero-to-origin-in-five-minutes"></a>[Zero to origin in five minutes](guides/getting-started.md#zero-to-origin-in-five-minutes)
- <a id="anatomy-of-a-power-file"></a>[Anatomy of a power file](guides/getting-started.md#anatomy-of-a-power-file)
- <a id="recipes"></a>Recipes: merged into the sections above.
- <a id="example-a-toggle-gated-wall-climbing"></a>[Example A: Toggle-gated wall climbing](guides/toggles.md#example-a-toggle-gated-wall-climbing)
- <a id="example-b-stance-switch-hunters-mark-style"></a>[Example B: Stance switch (Hunter's Mark style)](guides/toggles.md#example-b-stance-switch-hunters-mark-style)
- <a id="new-in-22"></a>New in 2.2: merged into the sections above.
- <a id="essence-evolution-tier-progression"></a>[Essence Evolution (tier progression)](EVOLUTION.md)
- <a id="tiers"></a>[Tiers](EVOLUTION.md)
- <a id="how-it-works"></a>[How it works](EVOLUTION.md#how-it-works)
- <a id="commands"></a>[Commands](EVOLUTION.md#commands)
- <a id="adding-evolution-tiers-to-an-origin"></a>[Adding evolution tiers to an origin](EVOLUTION.md#adding-evolution-tiers-to-an-origin)
- <a id="built-in-evolution-powers"></a>[Built-in evolution powers](EVOLUTION.md#built-in-evolution-powers)
- <a id="built-in-mechanics"></a>[Built-in mechanics](concepts/built-in-mechanics.md)
- <a id="undead-potion-reversal"></a>[Undead potion reversal](concepts/built-in-mechanics.md#undead-potion-reversal)
- <a id="aquatic-origin-shared-powers"></a>[Aquatic origin shared powers](concepts/built-in-mechanics.md#aquatic-origin-shared-powers)
- <a id="suppression"></a>[Suppression](concepts/built-in-mechanics.md#suppression)
- <a id="testing--debugging"></a>[Testing & debugging](guides/testing.md)
- <a id="hot-reload"></a>[Hot-reload](guides/testing.md#hot-reload)
- <a id="the-deprecation-log"></a>[The deprecation log](guides/testing.md#the-deprecation-log)
- <a id="forcing-an-origin-for-testing"></a>[Forcing an origin for testing](guides/testing.md#forcing-an-origin-for-testing)
- <a id="function-from-an-event"></a>[`/function` from an event](guides/testing.md#function-from-an-event)
- <a id="common-pitfalls"></a>[Common pitfalls](guides/testing.md#common-pitfalls)
