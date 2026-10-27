---
title: Make a toggle
parent: Guides
nav_order: 5
permalink: /guides/toggles
---

# Make a toggle

Many power types are already toggleable: `flight`, `stealth`, `persistent_effect` with `"toggleable": true` and others take a key and switch on and off by themselves (see [Toggles](../concepts/toggles.md)). This page covers the other case: a mode, stance or mark that several powers share, built from a [`neoorigins:toggle`](../powers/state.md#neooriginstoggle) flag.

A `neoorigins:toggle` power is a **named boolean** stored per-player. It
doesn't appear on the skill bar and doesn't consume a keybind slot; its
only job is to be _read_ by other powers' `condition` field and _written_
by other powers' `entity_action`. This is the cleanest way to give pack
authors a stance, a stealth flag, a mark, or any "is this mode on right
now?" gate without burning a hotbar key.

The pattern is always three pieces:

1. **The flag itself**: a `neoorigins:toggle` power. Optional `default`
   sets the value reads see before the flag has ever been flipped. Set
   `"hidden": true` to keep the flag off the origin info panel, almost
   always what you want for an internal flag.
2. **A way to read it**: `neoorigins:power_active { power: "..." }` inside
   any other power's `condition`.
3. **A way to flip it**: `neoorigins:toggle { power: "..." }` inside any
   `entity_action` (active ability, action_on_hit, action_on_event, etc.).
   These glue powers also typically want `"hidden": true` since the
   player only needs to see the user-facing rollup.

### Example A: Toggle-gated wall climbing

A passive that lets the player climb walls, but only while a separate
"climb mode" flag is on. The active ability flips the flag.

```json
// data/mypack/origins/powers/climb_mode.json — the flag (visible so the player sees it cycle)
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
    "type": "neoorigins:power_active",
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

### Example B: Stance switch (Hunter's Mark style)

A flag that's flipped on by attacking and back off after the next kill.
No keybind required. While the flag is on, the player gains
Strength I against all targets.

```json
// data/mypack/origins/powers/marked.json — the internal flag
{
  "type": "neoorigins:toggle",
  "default": false,
  "hidden": true
}
```

```json
// data/mypack/origins/powers/mark_on_hit.json — flip on when hitting (visible: this is the user-facing description)
{
  "type": "neoorigins:action_on_hit",
  "entity_action": {
    "type": "neoorigins:actor_action",
    "action": {
      "type": "neoorigins:toggle",
      "power": "mypack:marked",
      "value": true
    }
  },
  "name": "Hunter's Mark",
  "description": "Marks the next target in line."
}
```

```json
// data/mypack/origins/powers/mark_clear_on_kill.json — internal: flip off on kill
{
  "type": "neoorigins:action_on_event",
  "event": "kill",
  "hidden": true,
  "entity_action": {
    "type": "neoorigins:toggle",
    "power": "mypack:marked",
    "value": false
  }
}
```

`action_on_hit` runs a bare `entity_action` against the entity you hit, so
the toggle above is wrapped in `actor_action` to flip the attacker's own
flag. Unwrapped, it would do nothing on a mob and flip the victim's flag on
a player.

Use `action_on_event` with `"event": "kill"` here, not the legacy
`neoorigins:action_on_kill`. That alias builds its own `entity_action` from
`action`/`amount`/`effect` and overwrites whatever the file supplied, so a
toggle written under it is dropped and the power heals instead.

```json
// data/mypack/origins/powers/marked_strength.json — gated buff
{
  "type": "neoorigins:persistent_effect",
  "condition": { "type": "neoorigins:power_active", "power": "mypack:marked" },
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
idempotent, useful for one-shot triggers like the `action_on_hit` above
that should always force the mark on regardless of prior state.

**Why use this over `AbstractTogglePower` subclasses (e.g. `flight`,
`item_magnetism`)?** Those occupy a keybind slot and are best for one
ability per origin. `neoorigins:toggle` is for fan-out: a single flag that
multiple powers gate on, or a flag flipped by something other than the
keybind (an event, a hit, a tick condition).
