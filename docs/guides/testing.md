---
title: Test and debug a pack
parent: Guides
nav_order: 6
permalink: /guides/testing
---

# Test and debug a pack

## Hot-reload

`/reload` reloads all datapacks including power definitions. You don't
need to rejoin. If a power you just added isn't showing up, check the
server log for a `[CompatB]` or `[2.0-legacy]` warning. JSON parse errors
log once at reload.

## The deprecation log

Every legacy type emits a single one-shot warning at first use:

```
[2.0-legacy] power type 'neoorigins:thorns_aura' is deprecated, remap to 'neoorigins:action_on_event' (first seen on power 'mypack:spiky_skin')
```

Grep your logs for `[2.0-legacy]` to get your migration punch list. See
[MIGRATION.md](../MIGRATION.md) for the full remap table.

## Forcing an origin for testing

Origin-picker state lives in the player data attachment. The fastest
iteration loop:

1. Give yourself a stack of `neoorigins:orb_of_origin`.
2. Use the orb to reroll. Each use opens the picker; the XP cost applies
   only when you commit a choice.
3. Pick, test, reroll, repeat.

## `/function` from an event

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

## Common pitfalls

**"My power JSON parsed but nothing happens."**
Most often the `condition` you attached is evaluating false every tick.
Swap in `{ "type": "neoorigins:constant", "value": true }` as a
placeholder condition to confirm the wiring works, then narrow it back
down. (There is no `always_true` condition; an unknown condition type
evaluates to false.)

**"My size-scaling power makes me punch through walls."**
`modify_reach` defaults to `true`, so reach tracks body size unless you turn
it off: set `modify_reach: false` on the `size_scaling` config. To claw back
a flat amount of reach for a shrunk origin without re-enabling proportional
scaling, set `reach_bonus` (in blocks) instead.

**"My origin and my class both resize me, but only one of them takes effect."**
They stack. A 1.3x origin plus a 1.25x class puts the player at 1.55x, in
either pick order, and re-picking one layer leaves the other alone. If the
last-picked layer wins instead, the build predates the cross-layer stacking
fix. `attribute_modifier` bonuses stack across layers the same way.

**"My active ability triggers but the server disagrees with the client."**
A velocity change needs `hurtMarked` to survive the client's next physics
tick. `add_velocity` and `dash` both set it for you; if the motion still
snaps back, keep the magnitude conservative (<1.5 per axis). `add_velocity`
adds to the current motion by default; `set: true` replaces it instead.

**"My action_on_event fires twice on respawn."**
`onLogin` and `onRespawn` both default to calling `onGranted`. If your
event registers a handler in `onGranted`, the handler count doubles on
each respawn. Use `action_on_event` (which is idempotent by construction)
rather than registering listeners from a custom power.

**"My custom power type shows as unknown."**
The type field is case-sensitive and must be fully qualified:
`"neoorigins:persistent_effect"`, not `"persistent_effect"` (a bare id
resolves to the `minecraft:` namespace). The log names the offender:
`Unknown power type '<type>' for power <id>`, with a hint when the path is
really an action or condition.

**"Packets say UI sent but players see nothing."**
Most often: the client is running a much older version of NeoOrigins than
the server. The picker payload is versioned: mismatched mod versions
drop the payload on the client side.
