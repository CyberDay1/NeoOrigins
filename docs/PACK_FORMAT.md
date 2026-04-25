---
title: Pack Format
nav_order: 3
---

# NeoOrigins Pack Format Reference

Packs are folders, ZIPs, or JARs dropped into `originpacks/` in the game directory. No `pack.mcmeta` is required, but it is good practice to include one.

---

## Directory Layout

```
your-pack/
  pack.mcmeta                                       (optional)
  data/
    <namespace>/
      origins/
        origins/       <name>.json                 # origin definitions
        powers/        <name>.json                 # power definitions
        origin_layers/ <name>.json                 # layer definitions
  assets/
    <namespace>/
      lang/
        en_us.json                                  # translations (optional)
```

The `<namespace>` is your mod/pack ID (lowercase, no spaces). Choose something unique to avoid collisions with other packs.

---

## Origin JSON

Path: `data/<namespace>/origins/origins/<id>.json`
Loaded as: `<namespace>:<id>`

```json
{
  "name": "origins.mypack.merling.name",
  "description": "origins.mypack.merling.description",
  "icon": "minecraft:cod",
  "impact": "medium",
  "order": 5,
  "powers": [
    "mypack:water_breathing",
    "mypack:swim_speed"
  ],
  "upgrades": []
}
```

### Fields

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `name` | string or component | yes | — | Display name. Plain string = translation key. `{"text":"..."}` = literal. |
| `description` | string or component | yes | — | Description text. Same resolution as `name`. |
| `icon` | Identifier | no | `minecraft:air` | Item to display as the origin icon |
| `impact` | string | no | `none` | `none`, `low`, `medium`, or `high` |
| `order` | int | no | `0` | Sort order in the selection screen (lower appears first) |
| `powers` | list of Identifier | no | `[]` | Powers granted by this origin |
| `upgrades` | list | no | `[]` | Reserved for future use |

---

## Power JSON

Path: `data/<namespace>/origins/powers/<id>.json`
Loaded as: `<namespace>:<id>`

Every power must have a `type` field. All other fields depend on the type.
See [POWER_TYPES.md](POWER_TYPES.md) for the full reference.

### Common fields (all types)

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `type` | Identifier | yes | — | Power type, e.g. `neoorigins:status_effect` |
| `name` | string or component | no | _(derived from ID)_ | Display name in the selection screen |
| `description` | string or component | no | _(derived from ID)_ | Description text |

### Name/description resolution order

1. Explicit `name`/`description` field in the power JSON
2. Lang key derived from the power ID: `power.<namespace>.<path>.name` / `.description`
3. Fallback: the power ID is shown as-is

**Recommendation:** Put names and descriptions in your lang file using the derived key convention. This keeps power JSONs clean and allows easy translation.

### Component format

`name` and `description` accept:
- `"power.mypack.my_power.name"` — treated as a translation key (most common)
- `{"text": "My Power"}` — literal string, not translatable
- `{"translate": "power.mypack.my_power.name"}` — explicit translation key

---

## Layer JSON

Path: `data/<namespace>/origins/origin_layers/<id>.json`
Loaded as: `<namespace>:<id>`

Layers are the selection groups shown to the player when they first join. Most packs should add their origins to the existing `neoorigins:origin` layer rather than creating a new one.

```json
{
  "order": 1,
  "name": "origins.layer.origin",
  "origins": [
    "mypack:merling",
    "mypack:pyromancer"
  ],
  "allow_random": true,
  "auto_choose": false,
  "hidden": false,
  "enabled": true
}
```

### Fields

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `order` | int | no | `0` | Layer display order |
| `name` | string or component | no | — | Layer title |
| `origins` | list of Identifier | yes | — | Origins available in this layer |
| `allow_random` | bool | no | `true` | Show the Random button |
| `auto_choose` | bool | no | `false` | Automatically pick an origin if only one is available |
| `hidden` | bool | no | `false` | Hide the layer from the screen (still applies its origin) |
| `enabled` | bool | no | `true` | Whether the layer is active at all |

### Adding origins to the built-in layer

To add your origins to the default NeoOrigins origin selector, list them under the `neoorigins:origin` layer ID by creating a layer file at:

```
data/neoorigins/origins/origin_layers/origin.json
```

This overwrites the built-in layer entirely, so be sure to include all existing origins you want to keep. Alternatively, create your own layer with a different namespace and ID.

---

## Lang File

Path: `assets/<namespace>/lang/en_us.json`

```json
{
  "origins.mypack.merling.name": "Merling",
  "origins.mypack.merling.description": "An aquatic being.",

  "power.mypack.water_breathing.name": "Gills",
  "power.mypack.water_breathing.description": "Breathes underwater."
}
```

### Key conventions

| Key pattern | Used for |
|---|---|
| `origins.<namespace>.<origin_id>.name` | Origin display name |
| `origins.<namespace>.<origin_id>.description` | Origin description |
| `power.<namespace>.<power_id>.name` | Power display name |
| `power.<namespace>.<power_id>.description` | Power description |

For powers in subdirectories (e.g. `powers/combat/slash.json` → ID `mypack:combat/slash`), replace `/` with `.` in the key: `power.mypack.combat/slash.name`.

---

## Origins Mod Compatibility

Packs originally written for the Fabric Origins mod can be dropped into `originpacks/` and will be translated automatically. See the README for which power types translate and which are skipped.

The translation pass runs on load and on `/reload`. A full log is written to `logs/neoorigins-compat.log`.
