---
title: Keybinds
parent: Concepts
nav_order: 4
permalink: /concepts/keybinds
---

# Keybinds

How active and toggleable powers get a key: the six skill slots, the Class Skill key, and the named-keybind pool. The player-side key list is in [Client Config](../CLIENT_CONFIG.md#built-in-keybinds).

By default, active powers consume one of the six hardcoded skill slots
(`key.neoorigins.skill_1` … `key.neoorigins.skill_6`). For packs that ship more
than six active abilities, NeoOrigins exposes a **named-keybind pool**: the
pack declares its own translation key on the power, and the client surfaces
each declared key as a labelled hotkey in *Controls → Key Binds → NeoOrigins
(Hotkeys)*.

## Declaring on a power

Add a `key` field to any active power: the native `neoorigins:active_ability`,
or an Apoli-style `origins:active_self` / `origins:toggle`. The loader picks the
field up and registers the binding:

```json
{
  "type": "origins:active_self",
  "key": "examplepack.key.origins.1",
  "cooldown": 80,
  "entity_action": { "type": "neoorigins:add_velocity", "y": 1.5 },
  "name": "Leap"
}
```

`key` can also be an object form that carries the `continuous` flag:

```json
"key": { "key": "examplepack.key.origins.channel", "continuous": true }
```

- `key` is a free-form translation key string. Ship the human label as the
  matching entry in your pack's `lang/en_us.json` (e.g.
  `"examplepack.key.origins.1": "Phase Step"`).
- `continuous: false` (default) = the action fires once per press. `true` =
  the action fires every tick while the key is held, appropriate for
  hold-to-channel abilities; skip cooldowns for these.
- Vanilla input keys (see [Vanilla input keys](#vanilla-input-keys) below) are
  *not* routed through the pool; they bind directly to a real game control and
  fire from server-side input polling, so they never consume a named-hotkey or
  skill slot.
- The native `neoorigins:active_ability` uses the pool too. Give it the same
  `key` field and it binds to a named hotkey and drops out of the skill-slot
  roster; leave `key` off and it takes the next free `skill_1`..`skill_6` slot
  as before. There is no need to rewrite it as `origins:active_self`. It reads
  the same three forms as everything else here: the mod's own
  `neoorigins:windwalker_cloud_steps` is a native `active_ability` carrying
  `"key": "key.jump"`, the vanilla-key form covered by the bullet above.
- The native `neoorigins:toggle` is the exception, because it is not an active
  power: it holds a boolean for other powers to read and has no activation to
  fire, so a `key` on it is ignored. Put the key on an `active_ability` whose
  `entity_action` flips the toggle.

## Numbered slots (`key: N`)

Instead of a translation-key string, `key` may be a **number**. `"key": 1`
targets named-hotkey pool slot 1 directly; it is shorthand for the canonical
translation key `key.neoorigins.hotkey.1` (1-indexed). The slot already carries
a built-in label ("Hotkey 01" … up to "Hotkey 64"), so you don't need to ship a
`lang` entry for it, and the modpack can pre-bind it via the
[`slot_defaults` client config](../CLIENT_CONFIG.md#hotkeys).

```json
{
  "type": "origins:active_self",
  "key": 1,
  "cooldown": 80,
  "entity_action": { "type": "neoorigins:add_velocity", "y": 1.5 },
  "name": "Leap"
}
```

The object form accepts a number too, so a numbered slot can still be
continuous: `"key": { "key": 1, "continuous": true }`.

- **String vs. number.** A string `key` fills the next free pool slot in sorted
  order: stable across relogs, but which physical "Hotkey N" row it lands on
  depends on what else the pack declares. A numeric `key: N` is **deterministic**:
  it always pins to slot N, so the author controls exactly which row (and which
  `slot_defaults` default) a power gets. Reach for the number when you want a
  predictable layout; reach for the string when you just want "some free slot"
  and a custom label.
- Slot `N` must be within `pool_size` (default 32). A `key: N` above the pool
  size is logged and left dormant, same as string overflow. Raise
  [`pool_size`](../CLIENT_CONFIG.md#hotkeys) to make room.

## One key, several powers

Multiple powers may declare the **same** `key`, whether a shared string or the
same `key: N`. They are not in conflict: the server keeps a list of bindings per
key and fans a single press out to **all** of them (each still gated by its own
`condition` and `cooldown`). This is how one keypress can drive several powers on
an origin at once, e.g. `"key": 1` on both a movement power and a particle
power fires both from one button. A player binds that one "Hotkey N" row a single
time in Controls and it triggers the whole group.

Source of truth: `power/keybind/PowerKeybindRegistry.java` (`BY_KEY` list +
`dispatch` fan-out) and `client/HotkeyAssignments.java` (the two-pass slot
assignment that pins numbered keys).

## Vanilla input keys

Instead of a translation key, a power's `key` field may name one of nine
**vanilla game controls**. The power then activates whenever the player uses
that control: no extra hotkey to bind, no pool slot consumed. This is ideal for
abilities that should feel native to an existing input (a sneak-toggle stance, a
jump-triggered dash, a use-key channel).

Supported keys:

| Key | Fires while… |
|---|---|
| `key.jump` | the jump key is held |
| `key.sneak` | the player is sneaking |
| `key.sprint` | the player is sprinting |
| `key.use` | the use / right-click key is held |
| `key.attack` | the attack / left-click key is held (or the player is swinging) |
| `key.forward` | pressing forward |
| `key.back` | pressing back |
| `key.left` | strafing left |
| `key.right` | strafing right |

```json
{
  "type": "origins:active_self",
  "key": "key.jump",
  "cooldown": 30,
  "condition": { "type": "origins:in_air" },
  "entity_action": { "type": "neoorigins:add_velocity", "y": 0.8 },
  "name": "Double Jump"
}
```

- The server polls the bound control's state every tick and fires the power's
  own action under its `condition` gate and `cooldown`. Non-continuous bindings
  are **edge-detected**: one activation per press, not once per tick. Set
  `"key": { "key": "key.use", "continuous": true }` for a hold-to-channel ability.
- `key.use`, `key.attack` and `key.jump` report their **true held state**:
  the client sends the real key state each tick (`VanillaKeyStatePayload`)
  rather than inferring it from swing animations or interaction packets, so
  hold-and-release timing is accurate.
- Because the activation runs through the power's normal gate, `fail_action`
  and cooldowns behave exactly as they do for named hotkeys.
- Works on `neoorigins:active_ability` as well as the Apoli-style
  `origins:active_self` / `origins:toggle` powers.

Source of truth: `power/keybind/PowerKeybindRegistry.java` (the
`case "key.*"` switch and `VANILLA_NATIVE` poller).

## Fail feedback (`fail_action`)

`origins:active_self`, `origins:toggle`, and `origins:launch` accept an
optional `fail_action` (a NeoOrigins extension; plain Apoli ignores it).
It runs when the player presses the power's key but the `condition` gate
fails, replacing the silent no-op with author-defined feedback:

```json
{
  "type": "origins:active_self",
  "key": "examplepack.key.origins.1",
  "cooldown": 80,
  "condition": { "type": "origins:on_block" },
  "entity_action": { "type": "neoorigins:add_velocity", "y": 1.5 },
  "fail_action": {
    "type": "neoorigins:execute_command",
    "command": "tellraw @s {\"text\":\"You must be on the ground to leap.\",\"color\":\"red\"}"
  },
  "name": "Leap"
}
```

- Works on every activation path: skill slots, named hotkeys, and vanilla
  input keys.
- Fires once per press. Held continuous keys are edge-detected so the
  feedback doesn't spam every tick.
- Only `condition` failures trigger it; a press blocked by an active
  cooldown stays silent (the HUD already shows the cooldown), and a
  blocked attempt never consumes the cooldown.
- The native `neoorigins:active_ability` type supports the same field
  (see [Active abilities](../powers/active.md#neooriginsactive_ability)).

## Hotkey-less actives (`disable_hotkey`)

`origins:active_self` accepts an optional boolean `disable_hotkey` (a
NeoOrigins extension; plain Apoli ignores it). When `true`, the power stays
a fully-fledged active ability (its `condition`, `cooldown`, and
`fail_action` all still apply), but it claims **no** skill slot or named
hotkey, so no key press can fire it. The only way to trigger it is the
[`neoorigins:activate_power`](../actions/effects.md#neooriginsactivate_power) action from
another power:

```json
{
  "type": "origins:active_self",
  "disable_hotkey": true,
  "cooldown": 200,
  "entity_action": { "type": "neoorigins:add_velocity", "y": 2.0 },
  "name": "Hidden Leap"
}
```

- Use this for abilities that should only ever be reached programmatically,
  e.g. a passive `condition_passive` power that fires
  `neoorigins:activate_power` at the hotkey-less power when its own trigger
  conditions are met.
- Omitting `key` on an `origins:active_self` normally drops the power into the
  next free `skill_1`..`skill_6` slot; `disable_hotkey: true` opts out of that
  entirely, freeing the slot for other powers.
- A `disable_hotkey` power with no `entity_action` does nothing; there's no
  hotkey *and* no action, so the power is inert.
- `disable_hotkey: true` takes precedence even if you also declare a `key`:
  the key is ignored and no slot or hotkey is bound. You don't have to remove
  `key` to make an existing active power hotkey-less.

## How the pool works

- At reload, the server collects every distinct `key` value from all loaded
  powers and broadcasts the sorted list to each client. Pool size is the
  client's `[hotkeys] pool_size` (default 32, range 1-256;
  `NeoOriginsClientConfig.HOTKEY_POOL_SIZE`). Slots 1-64 ship a "Hotkey N"
  label; higher slots work but show the raw translation key.
- Each declared key is assigned an anonymous `Hotkey 01` … `Hotkey N` slot in
  stable order; a player who relogs sees the same key in the same slot as
  long as the pack hasn't changed.
- The player rebinds each slot to a physical key in vanilla controls. Press
  routes via `ActivatePowerByKeyPayload` back to the server, which fires the
  bound power's entity action under the original cooldown / condition gates.
- If keybindjs is loaded and already registers a mapping with the same
  translation key, that mapping is used and no pool slot is spent on it.

## Display labels

The hotkey list in the controls menu shows your translation key's human
label. Built-in `skill_5` and `skill_6` slots use the same `key.neoorigins.skill_N`
naming as the original four; their labels are bundled in
`assets/neoorigins/lang/en_us.json`.

Source of truth: `power/keybind/PowerKeybindRegistry.java`,
`client/HotkeyAssignments.java`.
