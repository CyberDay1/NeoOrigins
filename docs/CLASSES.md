---
title: Custom Classes
parent: "Origins & Content"
nav_order: 2
---

# Custom Classes

In NeoOrigins, a **class** is not a separate system; it's an ordinary
origin that lives in the special **`neoorigins:class` layer**. Every player
picks an origin (layer 1) *and* a class (layer 2); the class screen is the
second selection screen shown on first join.

Because a class is just an origin, everything in
[PACK_FORMAT.md](PACK_FORMAT.md) about Origin JSON and Power JSON applies
directly. This page covers only what's class-specific.

## The class layer

The built-in class layer is defined at
`data/neoorigins/origins/origin_layers/class.json` with `order: 2`. Most
class powers are passive or condition-gated, but a class may also carry an
active power.

**A class gets one keybind slot.** The class layer does not use the six skill
slots that the origin layer does. Instead, the **first** active power in a
class's `powers` list is bound to the dedicated **Class Skill** key, shown as
`C` in the HUD when the key is unbound. Every later active power in the same
class is silently left unbound, so if you want two of them, only the first
one will ever fire.

The built-in *Step Assist* switch on the Explorer, Rogue and Scout is the
worked example: a hidden `neoorigins:toggle` holds the state, an
`neoorigins:active_ability` flips it, and the step-height
`attribute_modifier` is gated on the toggle.

That pattern (a hidden toggle plus an active that flips it) is the intended
way to give a class a switchable passive, because it keeps the passive itself
condition-gated while spending only the one class slot.

## Adding a class (recommended: additive layer file)

You do **not** need to edit the built-in `class.json`. Any layer file whose
**path** is `class` is automatically folded into `neoorigins:class`,
regardless of namespace. So ship your own:

`data/<yourpack>/origins/origin_layers/class.json`
```json
{
  "name": "origins.layer.class",
  "origins": [
    "yourpack:class_alchemist"
  ]
}
```

Your class is appended to the existing list: all built-in classes are kept,
and you never have to maintain a copy of the mod's list. (Opt out of the
fold with `"standalone": true` if you deliberately want a separate screen.)

## Alternative: overriding the built-in layer

You *can* place a file at the exact built-in path
`data/neoorigins/origins/origin_layers/class.json`, but this **replaces the
list entirely**: you must re-list every built-in class you want to keep,
and re-sync on every mod update. Prefer the additive method above unless you
specifically want to remove built-in classes (the `[classes]` config toggles
are usually the better tool for that).

## The class origin JSON

Identical to any origin (`data/<yourpack>/origins/origins/<id>.json`).
Conventions used by the built-ins:

```json
{
  "name": "origins.yourpack.class_alchemist.name",
  "description": "origins.yourpack.class_alchemist.description",
  "icon": "minecraft:brewing_stand",
  "impact": "none",
  "order": 21,
  "powers": [
    "yourpack:class_alchemist_resilience",
    "yourpack:class_alchemist_antidote"
  ],
  "upgrades": []
}
```

- `icon`: item shown in the class picker.
- `impact: "none"`. Classes don't carry an origin "impact" rating; always `none`.
- `order`: position in the class screen (built-ins occupy 1–20; use 21+ to
  append after them).
- `powers`: passive, condition-gated or attribute powers, plus at most one
  active power, which takes the Class Skill key (see above).
- `upgrades`: optional advancement-driven promotion to another class; see
  the working `examples/class_tier_up/` datapack and the Upgrades section of
  [the examples README](../examples/README.md).

Naming convention: prefix the origin id and its powers with `class_`
(`class_alchemist`, `class_alchemist_resilience`). Not required by the code,
but it keeps packs consistent with the built-ins.

## Stacking with the origin layer

A class and an origin are separate layers, and their powers **add together**;
neither replaces the other. A player who is a Golem (origin, 1.3x size) and a
Titan (class, 1.25x size) ends up 1.55x, and re-picking either layer leaves the
other layer's contribution alone. The same holds for
[`attribute_modifier`](POWER_TYPES.md#neooriginsattribute_modifier) bonuses:
health, armor and reach from the origin survive a class change.

Design classes on that assumption. If a class is meant to be an *alternative*
to something an origin already grants rather than an addition to it, express
that with a condition on the class power, not by expecting it to overwrite the
origin's.

## Lang keys

Same derivation as any origin/power:

- `origins.<namespace>.<class_id>.name` / `.description`
- `power.<namespace>.<power_id>.name` / `.description`

Or use literal components (`{"text": "Alchemist"}`) directly in the JSON if
you don't want a resource/language pack: handy for self-contained datapacks.

## Defaults and config

- **No class chosen:** if a player closes the picker with an origin but no
  class, NeoOrigins auto-assigns `neoorigins:class_nitwit` (a deliberate
  no-effect default) so starting equipment and pending grants still resolve.
- **Disabling built-ins:** the `[classes]` section in
  `config/neoorigins/content.toml` toggles each built-in class. Disabled
  classes are removed after data load (still assignable via
  `/neoorigins set`).
- **No classes at all:** if *every* class is disabled, the class selection
  screen is skipped entirely and only the origin layer is shown.

## See also

- [PACK_FORMAT.md](PACK_FORMAT.md): Origin JSON, Power JSON, Layer JSON
- [`neoorigins:open_layer_picker`](ACTIONS.md#neooriginsopen_layer_picker): reopen the
  picker for the class layer (or any other layer subset) from a power, the mechanism the
  Orb of Class is a preset of
- [`examples/class_tier_up/`](../examples/class_tier_up/): class promotion via advancement upgrades
- [`examples/custom_class/`](../examples/custom_class/): a complete copy-paste custom class
- [SUB_ORIGINS.md](SUB_ORIGINS.md): conditioned layer entries (also work in the class layer)
- [EVOLUTION.md](EVOLUTION.md): how essence evolution interacts with the class layer
