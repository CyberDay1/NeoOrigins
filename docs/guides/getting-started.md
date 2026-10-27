---
title: Getting started
parent: Guides
nav_order: 2
permalink: /guides/getting-started
---

# Getting started

Build a working origin from an empty folder, then learn the parts of a power file. When this page names a power type, its fields are on [Power Types](../POWER_TYPES.md); the fields every power shares are on [Power basics](../concepts/power-basics.md).

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

**`origin_layers/origin.json`**: add your origin to the built-in picker layer.
Overriding `neoorigins:origin` (the main picker layer) merges your origins
into the vanilla tab instead of giving them their own. Only list the IDs you
are adding: layer JSONs merge **additively** by default, so your `origins`
array is appended to the shipped one (deduplicated by ID) and the built-in
origins stay listed by the core mod's own `origin_layers/origin.json`. You
never have to re-list a built-in.

Position in the array is not display order. The picker sorts by each origin's
own `order` field (ascending, alphabetical tie-break) by default, under the
`MANUAL` sort, so set `order` in `origins/my_origin.json` to place
your entry. If you want to remove or curate the built-ins rather than add to
them, set `"replace": true` on your layer file: every field you supply then
overwrites the built-in's, `origins` included. See
[PACK_FORMAT](../PACK_FORMAT.md#adding-origins-to-the-built-in-layer) for the
full merge rules.

```json
{
  "origins": [
    "mypack:my_origin"
  ]
}
```

**`origins/my_origin.json`**: the origin definition the picker renders.

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

**`powers/my_origin_scale.json`**: a size-scaling power. Keeps the origin
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
the JSON is whatever fields that power type accepts. See
[POWER_TYPES.md](../POWER_TYPES.md) for the full per-type field catalogue.

**Three shapes of power**, in rough order of what you'll reach for:

1. **Always-on / passive**: things that are true while the origin is held.
   `attribute_modifier`, `persistent_effect`, `condition_passive`,
   `size_scaling`, `prevent_action`.
2. **Event-triggered**: things that run in response to something the
   player does. `action_on_event`, `action_on_hit`.
3. **Active ability**: things the player fires with a keybind.
   `active_ability` + `entity_action`.

Most recipes below are one of these three shapes.

## Next steps

- [Cookbook](../COOKBOOK.md): working patterns to copy.
- [Give an ability a cost](costs.md) and [Make a toggle](toggles.md).
- [Test and debug a pack](testing.md).
