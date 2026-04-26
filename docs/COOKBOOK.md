---
title: Cookbook
nav_order: 2
---

# NeoOrigins Pack-Builder Cookbook

A recipe-first tour of NeoOrigins 2.0 for datapack authors. The
[POWER_TYPES](POWER_TYPES.md), [CONDITIONS](CONDITIONS.md),
[ACTIONS](ACTIONS.md), and [EVENTS](EVENTS.md) docs are the reference —
this doc is the _how do I actually build a thing_ companion.

Contents:
- [Zero to origin in five minutes](#zero-to-origin-in-five-minutes)
- [Anatomy of a power file](#anatomy-of-a-power-file)
- [Recipes](#recipes) — 15 common patterns (incl. toggleable abilities)
- [Testing & debugging](#testing--debugging)
- [Common pitfalls](#common-pitfalls)
- [Where to go next](#where-to-go-next)

---

## Zero to origin in five minutes

The smallest viable origin needs three files:

```
data/
  mypack/
    origins/
      origin_layers/
        origin.json                 # which origins show in the picker
      origins/
        my_origin.json              # the origin itself
      powers/
        my_origin_scale.json        # one or more powers granted by the origin
```

**`origin_layers/origin.json`** — add your origin to the built-in picker layer.
Overriding `neoorigins:origin` (the main picker layer) merges your origins
into the vanilla tab instead of giving them their own. Only list the
`origin:` IDs you want visible; the built-in origins stay listed by the core
mod's own `origin_layers/origin.json` — layer JSONs merge by replacement of
the `origins` array, so copy the full list if you want to control ordering.

```json
{
  "origins": [
    "mypack:my_origin"
  ]
}
```

**`origins/my_origin.json`** — the origin definition the picker renders.

```json
{
  "name": "Stone Walker",
  "description": "Hardened from birth. Cannot drown but takes extra fire damage.",
  "impact": 1,
  "order": 50,
  "powers": [
    "mypack:my_origin_scale",
    "mypack:stone_walker_water_breath",
    "mypack:stone_walker_fire_weak"
  ],
  "icon": {
    "item": "minecraft:stone"
  }
}
```

**`powers/my_origin_scale.json`** — a size-scaling power. Keeps the origin
visually distinct.

```json
{
  "type": "neoorigins:size_scaling",
  "name": "Small",
  "description": "90% the size of a regular player.",
  "scale": 0.9,
  "modify_reach": false
}
```

Drop the datapack into `world/datapacks/` (or ship it as a mod resource),
`/reload`, open the origin picker, pick your origin. Done.

From here, everything is just adding more power JSONs and referencing them
in the origin's `powers` array.

---

## Anatomy of a power file

Every power file follows the same shape:

```json
{
  "type": "<the power type>",
  "name": "...",                    // shown in the origin picker
  "description": "...",             // shown in the origin picker
  "hidden": false,                  // optional — hide from the UI
  // ...type-specific fields...
}
```

The `type` field is the only mandatory part of the skeleton. The rest of
the JSON is whatever fields that power type accepts — see
[POWER_TYPES.md](POWER_TYPES.md) for the full per-type field catalogue.

**Three shapes of power**, in rough order of what you'll reach for:

1. **Always-on / passive** — things that are true while the origin is held.
   `attribute_modifier`, `persistent_effect`, `condition_passive`,
   `size_scaling`, `prevent_action`.
2. **Event-triggered** — things that run in response to something the
   player does. `action_on_event`, `action_on_hit`.
3. **Active ability** — things the player fires with a keybind.
   `active_ability` + `entity_action`.

Each of the 10 recipes below is one of these three shapes.

---

## Recipes

### 1. "Passive health boost" — +4 max HP

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

### 2. "Always has night vision"

```json
{
  "type": "neoorigins:enhanced_vision",
  "name": "Night Adapted",
  "description": "See clearly in the dark.",
  "exposure": 0.7
}
```

`enhanced_vision` is the 2.0 replacement for the legacy `night_vision` /
`glow` toggle. It sets a brightness floor without the intrusive full-white
overlay of the vanilla Night Vision effect.

### 3. "Carnivore — can only eat meat"

```json
{
  "type": "neoorigins:food_restriction",
  "name": "Carnivore",
  "description": "Can only eat meat.",
  "allowed_tags": ["minecraft:meat"]
}
```

For finer control, list items explicitly under `allowed_items`. Tags and
items union — the player can eat anything in either list.

### 4. "Fire weakness — +50% damage from fire"

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

### 5. "Slow fall from high places"

```json
{
  "type": "neoorigins:persistent_effect",
  "name": "Hollow Bones",
  "description": "Always falls slowly.",
  "effect": "minecraft:slow_falling",
  "amplifier": 0,
  "duration": 200,
  "show_icon": false
}
```

`persistent_effect` keeps the effect refreshed every tick. Set
`show_icon: false` so the effects HUD doesn't clutter.

### 6. "Heal in water"

```json
{
  "type": "neoorigins:condition_passive",
  "name": "Aquatic Recovery",
  "description": "Regenerates while submerged in water.",
  "condition": { "type": "neoorigins:submerged_in", "fluid": "minecraft:water" },
  "entity_action": { "type": "neoorigins:feed", "amount": 0.5 },
  "interval_ticks": 40
}
```

`condition_passive` is the workhorse 2.0 type — pair any condition with any
action, and it fires every `interval_ticks` when the condition holds.
Replaces the old `biome_buff`, `regen_in_fluid`, `burn_at_health_threshold`,
and several others.

### 7. "On kill: heal 2 hearts"

```json
{
  "type": "neoorigins:action_on_event",
  "name": "Blood Diet",
  "description": "Feed on the fallen.",
  "event": "kill",
  "entity_action": { "type": "neoorigins:feed", "amount": 4 }
}
```

See [EVENTS.md](EVENTS.md) for the full list of event keys. Common ones:
`kill`, `hit_taken`, `attack`, `jump`, `land`, `food_eaten`, `block_break`.

### 8. "Active ability — short dash forward"

```json
{
  "type": "neoorigins:active_ability",
  "name": "Dash",
  "description": "Press the skill key to dash forward.",
  "cooldown_ticks": 60,
  "hunger_cost": 1,
  "entity_action": {
    "type": "neoorigins:add_velocity",
    "x": 0,
    "y": 0.2,
    "z": 1.2,
    "set": false
  }
}
```

Active abilities bind to the primary skill keybind by default. Set
`key: "secondary"` for a second-slot binding (Y by default).

### 9. "Summon a minion"

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

Summons get their AI rewritten automatically — they won't attack the
summoner, and they fight back against whatever the summoner is fighting.
`death_damage` is the HP penalty when the minion falls in combat.

### 10. "Lose 1 HP every 2s while in daylight" (Vampire pattern)

```json
{
  "type": "neoorigins:condition_passive",
  "name": "Sun Allergy",
  "description": "Takes damage while exposed to daylight.",
  "condition": { "type": "neoorigins:exposed_to_sun" },
  "entity_action": { "type": "neoorigins:damage", "amount": 1, "source": "on_fire" },
  "interval_ticks": 40
}
```

`origins:exposed_to_sun` is the canonical day-exposure condition — it
checks both time-of-day and that the block column above the player lets
skylight through.

### 11. "Projectile that drowns enemies in an AoE at the impact point"

Fires a snowball-sized projectile; where it lands, every undead in a
5-block radius takes magic damage and gets heavy slowness for six seconds.
This is one power — an active ability whose action fires a projectile
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
      "entity_action": {
        "type": "neoorigins:if_else",
        "condition": { "type": "neoorigins:target_group", "group": "undead" },
        "if_action": {
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
}
```

**The wiring (key piece introduced in 2.0.0):**

- **`on_hit_action`** — any action, fired when the projectile lands. Stored
  on the projectile at spawn time, drained on impact.
- **Impact-centered AoE** — `area_of_effect` inside an `on_hit_action`
  automatically centers on the impact point, not the caster's position.
  Works because the projectile-impact dispatcher installs a
  `ProjectileHitContext` on the action-context holder before invoking
  your action; `area_of_effect` reads it and rebuilds its AABB.
- **Undead-only filter** — `origins:if_else` with `target_group: undead`
  wraps the damage+effect so normal mobs and players aren't caught.
- **Drown-tagged damage** — `source: { name: "drown" }` makes the hit read
  as drowning, with the correct sound and hit indicator.

**Tuning notes:**

- `radius: 5.0` is pragmatic. Smaller feels weak, larger hits from across
  the room.
- `duration: 120` = 6 seconds of Slowness II. Persistent enough to feel
  impactful, short enough that the next cooldown matters.
- `amount: 1.0` per hit, single-shot. For an attrition-style drowning,
  wrap in a `condition_passive` that fires at an interval while a marker
  entity exists at the impact point — more plumbing.

**For a fully custom projectile** (homing, chaining, trail effects), use a
custom entity type that extends `AbstractNeoProjectile` — see
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

### 12. "Black hole — pull everything nearby toward a point and damage the center"

Active ability that spawns a lingering gravity well at the caster's look
target. Pulls for two seconds, then expires.

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
  black hole wherever the projectile lands instead of the caster's feet —
  swap `entity_action` for:
  ```json
  { "type": "neoorigins:spawn_projectile",
    "entity_type": "neoorigins:magic_orb",
    "speed": 2.0,
    "on_hit_action": { "type": "neoorigins:spawn_black_hole", "radius": 6.0, "duration_ticks": 40 } }
  ```

### 13. "Tornado — fling entities in a cone"

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
  lifetime — enough to disorient without throwing them across the map.

### 14. "Lingering damage cloud at projectile impact"

Classic poison/acid cloud pattern — the projectile carries a cloud, the
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

### 15. Toggleable abilities (no keybind slot)

A `neoorigins:toggle` power is a **named boolean** stored per-player. It
doesn't appear on the skill bar and doesn't consume a keybind slot — its
only job is to be _read_ by other powers' `condition` field and _written_
by other powers' `entity_action`. This is the cleanest way to give pack
authors a stance, a stealth flag, a mark, or any "is this mode on right
now?" gate without burning a hotbar key.

The pattern is always three pieces:

1. **The flag itself** — a `neoorigins:toggle` power. Optional `default`
   sets the value reads see before the flag has ever been flipped.
2. **A way to read it** — `origins:power_active { power: "..." }` inside
   any other power's `condition`.
3. **A way to flip it** — `neoorigins:toggle { power: "..." }` inside any
   `entity_action` (active ability, action_on_hit, action_on_event, etc.).

#### Example A — Toggle-gated wall climbing

A passive that lets the player climb walls, but only while a separate
"climb mode" flag is on. The active ability flips the flag.

```json
// data/mypack/origins/powers/climb_mode.json — the flag
{
  "type": "neoorigins:toggle",
  "default": false,
  "name": "Climb Mode",
  "description": "While active, you can climb walls."
}
```

```json
// data/mypack/origins/powers/wall_climb.json — gated capability
{
  "type": "neoorigins:wall_climbing",
  "condition": {
    "type": "origins:power_active",
    "power": "mypack:climb_mode"
  },
  "name": "Spider Grip",
  "description": "Climb any wall while Climb Mode is on."
}
```

```json
// data/mypack/origins/powers/toggle_climb.json — the keybind
{
  "type": "neoorigins:active_ability",
  "cooldown_ticks": 10,
  "entity_action": {
    "type": "neoorigins:toggle",
    "power": "mypack:climb_mode"
  },
  "name": "Toggle Climb Mode",
  "description": "Press to toggle wall climbing on/off."
}
```

The wall-climb power does the work; the toggle is just a data flag.
Swap `wall_climbing` for any condition-gated power (attribute,
persistent_effect, etc.) and the same pattern works.

#### Example B — Stance switch (Hunter's Mark style)

A flag that's flipped on by attacking and back off after the next kill —
no keybind required. While the flag is on, the player gains
Strength I against all targets.

```json
// data/mypack/origins/powers/marked.json — the flag (default off)
{
  "type": "neoorigins:toggle",
  "default": false,
  "name": "Marked",
  "description": "You have a target in your sights."
}
```

```json
// data/mypack/origins/powers/mark_on_hit.json — flip on when hitting
{
  "type": "neoorigins:action_on_hit",
  "entity_action": {
    "type": "neoorigins:toggle",
    "power": "mypack:marked",
    "value": true
  },
  "name": "Hunter's Mark",
  "description": "Marks the next target in line."
}
```

```json
// data/mypack/origins/powers/mark_clear_on_kill.json — flip off on kill
{
  "type": "neoorigins:action_on_kill",
  "entity_action": {
    "type": "neoorigins:toggle",
    "power": "mypack:marked",
    "value": false
  }
}
```

```json
// data/mypack/origins/powers/marked_strength.json — gated buff
{
  "type": "neoorigins:persistent_effect",
  "condition": { "type": "origins:power_active", "power": "mypack:marked" },
  "effects": [
    { "effect": "minecraft:strength", "amplifier": 0 }
  ],
  "toggleable": false,
  "hidden": true,
  "name": "Marked Strength",
  "description": "Strength I while a target is marked."
}
```

Note `value: true` / `value: false` to set explicitly, vs. leaving
`value` off to flip whatever the current state is. Setting explicitly is
idempotent — useful for one-shot triggers like the `action_on_hit` above
that should always force the mark on regardless of prior state.

**Why use this over `AbstractTogglePower` subclasses (e.g. `flight`,
`item_magnetism`)?** Those occupy a keybind slot and are best for one
ability per origin. `neoorigins:toggle` is for fan-out: a single flag that
multiple powers gate on, or a flag flipped by something other than the
keybind (an event, a hit, a tick condition).

---

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
                   "tag": "mypack:eat_diamond" },
    "if_action": { "type": "neoorigins:apply_effect",
                   "effect": "minecraft:luck", "duration": 6000, "amplifier": 1 }
  }
}
```

Ship the item tag at `data/mypack/tags/item/eat_diamond.json`.

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

Ancient debris is hardcoded-excluded (vanilla parity — Fortune doesn't
affect netherite drops). Pack authors can narrow `target` to e.g.
`#minecraft:diamond_ores` for a diamond-only bonus.

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
condition works here — `neoorigins:thundering` for storm-blessed
equipment, `neoorigins:climbing` for scaling-specific buffs, etc.

---

### Sleepless origin with bed-anchored respawn

`prevent_action SLEEP` blocks sleep transitions. The
`SleepPreventionEvents` handler automatically sets the player's
respawn point at the bed before cancelling, so players still get the
anchor benefit — great for vampires, phantoms, wardens.

```json
{ "type": "neoorigins:prevent_action", "action": "SLEEP" }
```

Feedback message *"You don't need sleep — but you remember this place."*
fires on bed interaction.

---

### Elytra-style flight without an elytra (Phantom/Elytrian)

Grant gliding behavior — press jump while falling, spread wings like
a vanilla elytra user. No chest-slot requirement.

```json
{ "type": "neoorigins:natural_glide" }
```

Pair with `neoorigins:elytra_boost` for launch-speed amplification.
Contrast: `neoorigins:flight` is creative-mode-style hover (hold space
to ascend). `natural_glide` is pitch-based gliding, the real-elytra
feel.

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

### Warm near a campfire (near_block condition)

`neoorigins:near_block` scans a cubic radius for matching blocks and
fires when any are found. Accepts any combination of `block`/`blocks`/
`tag`/`tags` fields with logical OR.

```json
{ "type": "neoorigins:condition_passive",
  "condition": {
    "type": "neoorigins:near_block",
    "tags": ["minecraft:campfires", "#c:fire"],
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

Radius is capped at 8 to keep the per-tick scan cheap.

---

## Testing & debugging

### Hot-reload

`/reload` reloads all datapacks including power definitions. You don't
need to rejoin. If a power you just added isn't showing up, check the
server log for a `[CompatB]` or `[2.0-legacy]` warning — JSON parse errors
log once at reload.

### The deprecation log

Every legacy type emits a single one-shot warning at first use:

```
[2.0-legacy] power type 'neoorigins:thorns_aura' is deprecated — remap to 'neoorigins:action_on_event' (first seen on power 'mypack:spiky_skin')
```

Grep your logs for `[2.0-legacy]` to get your migration punch list. See
[MIGRATION.md](MIGRATION.md) for the full remap table.

### Forcing an origin for testing

Origin-picker state lives in the player data attachment. The fastest
iteration loop:

1. Give yourself a stack of `neoorigins:orb_of_origin`.
2. Use the orb to reroll. Each use opens the picker; the XP cost applies
   only when you commit a choice.
3. Pick, test, reroll, repeat.

### `/function` from an event

Chain any shell command out of a power using `execute_command`:

```json
{
  "type": "neoorigins:action_on_event",
  "event": "kill",
  "entity_action": {
    "type": "neoorigins:execute_command",
    "command": "function mypack:on_kill_callback"
  }
}
```

The command runs at server-level permissions (level 2), positioned at and
targeting the player.

---

## Common pitfalls

**"My power JSON parsed but nothing happens."**
Most often the `condition` you attached is evaluating false every tick.
Add a `origins:always_true` placeholder condition to confirm the wiring
works, then narrow it back down.

**"My size-scaling power makes me punch through walls."**
Set `modify_reach: false` on the `size_scaling` config. Reach scaling was
removed from size in v1.14 because it was confusing — it now only scales
when explicitly enabled.

**"My active ability triggers but the server disagrees with the client."**
`add_velocity` needs `hurtMarked` to survive the client's next physics
tick. The 2.0 alias handles this; if you're on the raw `spawn_projectile`
action, set `set: false` and keep the magnitude conservative (<1.5 per
axis).

**"My action_on_event fires twice on respawn."**
`onLogin` and `onRespawn` both default to calling `onGranted`. If your
event registers a handler in `onGranted`, the handler count doubles on
each respawn. Use `action_on_event` (which is idempotent by construction)
rather than registering listeners from a custom power.

**"My custom power type shows as unknown."**
The type field is case-sensitive and must be fully qualified:
`"neoorigins:persistent_effect"`, not `"persistent_effect"`. Check the
`[CompatB]` log if a type you expected to be registered isn't resolving.

**"Packets say UI sent but players see nothing."**
Most often: the client is running a much older version of NeoOrigins than
the server. The picker payload is versioned — mismatched mod versions
drop the payload on the client side.

---

## Where to go next

- **[POWER_TYPES.md](POWER_TYPES.md)** — every power type with full field
  tables. When a recipe here mentions a type, the details live there.
- **[CONDITIONS.md](CONDITIONS.md)** — the 60+ condition verbs you can
  use in `condition` fields.
- **[ACTIONS.md](ACTIONS.md)** — the 40+ action verbs you can use in
  `entity_action` fields.
- **[EVENTS.md](EVENTS.md)** — the event keys for `action_on_event`.
- **[MIGRATION.md](MIGRATION.md)** — if you're porting a pre-2.0 pack or
  an upstream Origins/Apoli/Apugli pack.
- **[ARCHITECTURE.md](ARCHITECTURE.md)** — for understanding how powers
  are resolved, dispatched, and cached (useful for pack-author debugging
  but not required reading).

The source of truth for every alias, verb, and event key is the Java code
under `src/main/java/com/cyberday1/neoorigins/` — if a doc drifts from the
code, the code wins. When in doubt, grep.
