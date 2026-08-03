---
title: Custom Classes
parent: "Origins & Content"
nav_order: 2
---

# Custom Classes

In NeoOrigins, a **class** is not a separate system. It's an ordinary
origin that lives in the special **`neoorigins:class` layer**. Every player
picks an origin (layer 1) *and* a class (layer 2); the class screen is the
second selection screen shown on first join.

Because a class is just an origin, everything in
[PACK_FORMAT.md](PACK_FORMAT.md) about Origin JSON and Power JSON applies
directly. This page covers only what's class-specific.

## The class layer

The built-in class layer is defined at
`data/neoorigins/origins/origin_layers/class.json` with `order: 2`. Classes
are intentionally passive: all built-in class powers are passive or
condition-gated, so **a class never consumes a keybind slot**. Keep custom
classes to passive/attribute/condition powers. Active (keybinded) powers in
the class layer are not the intended design and the class power list is
treated as always-on, not slotted.

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

Your class is appended to the existing list. All built-in classes are kept,
and you never have to maintain a copy of the mod's list. (Opt out of the
fold with `"standalone": true` if you deliberately want a separate screen.)

## Alternative: overriding the built-in layer

You *can* place a file at the exact built-in path
`data/neoorigins/origins/origin_layers/class.json`, but this **replaces the
list entirely**. You must re-list every built-in class you want to keep,
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
- `powers`: passive/condition powers only (see above).
- `upgrades`: optional advancement-driven promotion to another class; see
  the working `examples/class_tier_up/` datapack and the Upgrades section of
  [the examples README](../examples/README.md).

Naming convention: prefix the origin id and its powers with `class_`
(`class_alchemist`, `class_alchemist_resilience`). Not required by the code,
but it keeps packs consistent with the built-ins.

## Lang keys

Same derivation as any origin/power:

- `origins.<namespace>.<class_id>.name` / `.description`
- `power.<namespace>.<power_id>.name` / `.description`

Or use literal components (`{"text": "Alchemist"}`) directly in the JSON if
you don't want a resource/language pack. Handy for self-contained datapacks.

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

## Reopening the picker for arbitrary layers

The class picker can be reopened after the fact, and not just for the class
layer: the same mechanism works for any layer subset a pack defines. Two author
paths expose it:

- **Datapack action** [`neoorigins:open_layer_picker`](ACTIONS.md#neooriginsopen_layer_picker):
  give any power (item use, keybind, on-hit, …) a `layers` list to re-pick, with
  `commit_mode` (`deferred`/`immediate`), an XP `cost`, an optional `message` shown
  when the picker opens, and `consume_item` to spend the triggering item on commit.
  This lets a pack build its own re-pick items or powers for whatever layers it defines.
- **Admin command** `/origin gui <player> <layers>`: opens the picker scoped to one or
  more comma- or space-separated layers for a single target player (gamemaster permission).

## See also

- [PACK_FORMAT.md](PACK_FORMAT.md): Origin JSON, Power JSON, Layer JSON
- [`examples/class_tier_up/`](../examples/class_tier_up/): class promotion via advancement upgrades
- [`examples/custom_class/`](../examples/custom_class/): a complete copy-paste custom class
- [SUB_ORIGINS.md](SUB_ORIGINS.md): conditioned layer entries (also work in the class layer)
- [EVOLUTION.md](EVOLUTION.md): how essence evolution interacts with the class layer
