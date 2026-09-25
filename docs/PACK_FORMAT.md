---
title: Pack Format
parent: "DSL Reference"
nav_order: 6
---

# NeoOrigins Pack Format Reference

Packs are folders, ZIPs, or JARs dropped into `config/originpacks/`. If that folder does not exist but an `originpacks/` folder exists in the game directory, the game-directory folder is used instead and a deprecation warning is logged. NeoOrigins registers the folder as both a data-pack and a resource-pack source, so a pack's `assets/` (lang files) load from there too. The same `data/` layout also works as an ordinary world datapack in `<world>/datapacks/`, which loads only `data/`. See [pack.mcmeta](#packmcmeta) for when the metadata file is needed and which format numbers to use.

---

## Directory Layout

```
your-pack/
  pack.mcmeta                                       # see pack.mcmeta below
  data/
    <namespace>/
      origins/
        origins/       <name>.json                 # origin definitions
        powers/        <name>.json                 # power definitions
        origin_layers/ <name>.json                 # layer definitions
        mob_origins/   <name>.json                 # mob origins (optional)
      neoorigins/
        morphs/        <name>.json                 # morph definitions (optional)
        entity_groups/ <name>.json                 # entity groups (optional)
      global_powers/   <name>.json                 # global power sets (optional)
  assets/
    <namespace>/
      lang/
        en_us.json                                  # translations (optional)
```

The `<namespace>` is your mod/pack ID (lowercase, no spaces). Choose something unique to avoid collisions with other packs.

The first three folders are all most packs need; the rest are separate registries
documented elsewhere:

| Folder | Contents | Reference |
|---|---|---|
| `origins/mob_origins/` | Origins granted to mobs rather than players | [MOB_ORIGINS.md](MOB_ORIGINS.md) |
| `neoorigins/morphs/` | Reusable morph definitions for `neoorigins:entity_model` | [POWER_TYPES.md](POWER_TYPES.md#morph-definitions) |
| `neoorigins/entity_groups/` | Entity groups for `neoorigins:entity_group` | [POWER_TYPES.md](POWER_TYPES.md#neooriginsentity_group) |
| `global_powers/` | Powers granted to every player regardless of origin | [GLOBAL_POWERS.md](GLOBAL_POWERS.md) |

**The original Origins mod layout is also accepted**, so a legacy pack loads without
being rearranged: `data/<namespace>/origins/`, `powers/` and `origin_layers/` are read
as origins, powers and layers respectively. Prefer the NeoOrigins layout above in new
packs: the shorter paths exist for compatibility, not as an alternative style.

---

## pack.mcmeta

Minecraft 26.1.2 uses data-pack format `101.1` (major `101`, minor `1`)
and resource-pack format `84` (the `pack_version` in the 26.1.2 game jar's
`version.json`).

```json
{
  "pack": {
    "description": "My NeoOrigins pack",
    "min_format": 101,
    "max_format": 101
  }
}
```

- A pack whose supported formats go above data format `81` (or resource format `64`)
  must declare both `min_format` and `max_format`. Each is a whole number or a
  `[major, minor]` pair. A whole-number `min_format` means `<major>.0` and a
  whole-number `max_format` means every minor version of that major, so
  `101` to `101` covers `101.1`.
- `pack_format` is optional here; if present it must lie between the two majors.
  `supported_formats` is rejected once `min_format` is above `81` (data) or `64`
  (resource).
- A pack that carries both `data/` and `assets/` and is also installed as a resource
  pack can declare `"min_format": 84, "max_format": 101` so it is compatible in
  both roles.
- A `pack.mcmeta` the game rejects (for example `"pack_format": 101` with no
  `min_format` / `max_format`) is logged as "Error reading pack metadata" and the pack
  is listed with unknown compatibility. A 1.21.1 pack that still declares
  `"pack_format": 48` parses, but is flagged as made for an older version.
- In `config/originpacks/` the file is optional: a pack without one is loaded with
  generated metadata that marks it compatible. A pack in `<world>/datapacks/` needs a
  `pack.mcmeta`, or the game skips it.

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
| `upgrades` | list | no | `[]` | Advancement-driven origin swaps, each `{"advancement": "<id>", "origin": "<id>", "announcement": "<translation key>"}` (`announcement` optional). When the player earns `advancement` while holding this origin, they are switched to `origin` on the same layer and sent `announcement`. An Origins-style entry with `condition` in place of `advancement` is also read: a bare advancement id, or an object with an `advancement` field, is used as the advancement; any other condition makes the upgrade never fire. |
| `unchoosable` | bool | no | `false` | Hides the origin from the picker. It can still be assigned by command. |
| `required_mods` | list of mod ids | no | `[]` | Load gate: the origin only loads (and only appears in the picker) when every listed mod is present. Used by the built-in Dragon Survival origins (`"required_mods": ["dragonsurvival"]`). |
| `spawn_location` | object | no | — | Relocates the player to a matching location on first origin pick and on bedless respawn. See [Spawn Location](#spawn-location). |
| `tier_powers` | list | no | `[]` | Evolution-tier power overlays (`{tier, add, remove}`). Full reference in [EVOLUTION.md](EVOLUTION.md#datapack-customization) and the [COOKBOOK](COOKBOOK.md#adding-evolution-tiers-to-an-origin). |
| `figura_model` | string | no | — | Opaque base model key for the Figura soft-dep integration. Only meaningful with the Figura mod installed. See [Figura Model Key](#figura-model-key) and [FIGURA.md](FIGURA.md). |
| `figura_models` | object | no | — | Advanced reactive model maps (`tiers` / `powers` / `capabilities` / `vocab`) for Figura. Opaque strings, soft-dep only. See [Figura Model Key](#figura-model-key) and [FIGURA.md](FIGURA.md). |

### Spawn Location

An origin can declare a `spawn_location` to drop the player at a matching spot
instead of the world spawn. It is resolved server-side and fires:

- once, immediately after the player picks the origin (picker or Orb of Origin), and
- on respawn when the player has **no** bed or respawn anchor, using the
  player's *primary* origin (the first origin, in sorted layer order, that
  declares a `spawn_location`).

For respawn control that fires on **every** death (optionally overriding the
bed/anchor), use the [`neoorigins:modify_player_spawn`](POWER_TYPES.md#neooriginsmodify_player_spawn)
power instead; `spawn_location` only covers first-join and bedless respawn.

```json
{
  "name": "origins.mypack.deep_one.name",
  "description": "origins.mypack.deep_one.description",
  "icon": "minecraft:heart_of_the_sea",
  "powers": ["mypack:gills"],
  "spawn_location": {
    "dimension": "minecraft:overworld",
    "biome_tag": "minecraft:is_ocean",
    "allow_water_surface": true,
    "min_y": 45,
    "max_y": 62
  }
}
```

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `dimension` | Identifier | no | — | Restrict placement to this dimension. |
| `biome` | Identifier | no | — | Restrict to this exact biome. |
| `biome_tag` | Identifier | no | — | Restrict to biomes in this tag. |
| `biomes` | list of Identifier | no | — | Restrict to any biome in this list. |
| `structure` | Identifier | no | — | Place at this structure. |
| `structure_tag` | Identifier | no | — | Place at any structure in this tag. |
| `allow_water_surface` | bool | no | `false` | Allow placement on a water surface. |
| `allow_ocean_floor` | bool | no | `false` | Allow placement on the ocean floor. |
| `min_y` / `max_y` | int | no | — | Clamp the vertical search range. |
| `can_see_sky` | bool | no | — | When set, only place where sky visibility matches (`true` = open sky, `false` = covered). |

`dimension` / `structure` / `structure_tag` combine with **AND**; `biome` /
`biome_tag` / `biomes` combine with **OR** (any biome match passes).

**Global kill switch.** All `spawn_location` teleports (built-in, datapack,
and compat origins alike) are gated by `[spawn_location] teleports_enabled`
in `config/neoorigins/gameplay.toml` (default `true`). Set it to `false` and
every origin spawns at the normal world spawn point instead. The built-in
ocean origins have an additional gate, `[ocean_origins] spawn_in_ocean`.

### Figura Model Key

An origin can declare model keys for the [Figura](FIGURA.md) soft-dependency: a
client-side custom-avatar mod whose Lua scripts can read the wearer's origin state
and swap models to match. These fields do nothing on their own (NeoOrigins never
interprets them): they are opaque strings handed to a Figura avatar author's Lua
script, and only matter when both Figura and an avatar that reads them are present.

- `figura_model` (string): the base model key, e.g. `"knight"`. Read from Lua as
  `neoorigins:getFiguraModel()`.
- `figura_models` (object): reactive maps for tier / power / capability driven model
  swaps, plus a vocab map for discovery. Keys and values are all opaque strings.

```json
{
  "name": "origins.mypack.knight.name",
  "description": "origins.mypack.knight.description",
  "icon": "minecraft:iron_chestplate",
  "powers": ["mypack:shield_wall"],
  "figura_model": "knight",
  "figura_models": {
    "tiers":        { "1": "knight_ascended", "2": "knight_apex" },
    "powers":       { "mypack:shield_wall": "knight_guard" },
    "capabilities": { "natural_glide": "knight_winged" },
    "vocab":        { "knight": "Knight Base", "knight_guard": "Shield Wall" }
  }
}
```

The full behaviour, resolution rules, and the complete list of `neoorigins:` Lua
methods an avatar author can call live in [FIGURA.md](FIGURA.md).

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
| `required_mods` | list of mod ids | no | `[]` | Load gate: the power only loads when every listed mod is present. Same semantics as the origin-level field. |

### Name/description resolution order

1. Explicit `name`/`description` field in the power JSON
2. Lang key derived from the power ID: `power.<namespace>.<path>.name` / `.description`
3. Fallback: a title-cased form of the ID path, e.g. `mypack:water_breathing` shows as `Water Breathing`. When the path has `/`, the first segment is dropped and the rest are joined with `: `, so `mypack:combat/slash` shows as `Slash`.

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
| `name` | string or component | no | layer ID path | Layer title |
| `origins` | list | no | `[]` | Origins available in this layer (plain IDs, or conditioned entries — see [Sub-Origins](SUB_ORIGINS.md)) |
| `allow_random` | bool | no | `false` | Show the Random button |
| `exclude_random` | list | no | `[]` | Origin IDs the Random roll never lands on, in the picker and in server-side random assignment. Still pickable directly. |
| `auto_choose` | bool | no | `false` | Accepted for Origins compatibility but currently has no effect: nothing reads it. |
| `hidden` | bool | no | `false` | Leave the layer out of the picker; it does not count toward completing the pick. An origin set on it another way (e.g. by command) still applies. |
| `enabled` | bool | no | `true` | Whether the layer is active at all |

### Adding origins to the built-in layer

To add your origins to the default NeoOrigins origin selector, list them under the `neoorigins:origin` layer ID by creating a layer file at:

```
data/neoorigins/origins/origin_layers/origin.json
```

By default this **merges additively** into the built-in layer; your `origins` array is appended (deduplicated by ID) to the shipped one. You don't have to re-list every built-in origin.

Alternatively, create your own layer with a different namespace and ID. Same-path layers (e.g. `mypack:origin`) auto-fold into `neoorigins:origin` unless you opt out with `"standalone": true`.

### Replacing or editing the built-in layer

To **fully replace** the built-in `neoorigins:origin` layer (e.g. you want to remove some of the shipped origins, or curate the list from scratch), add `"replace": true` to your override file. Every field present in your file then overwrites the built-in's corresponding field, including the `origins` array:

```json
{
  "replace": true,
  "order": 1,
  "name": "origins.layer.origin",
  "origins": [
    "mypack:merling",
    "mypack:pyromancer"
  ]
}
```

The same applies to `neoorigins:class` and any other layer ID you target. Without `"replace": true`, your file's fields are merged additively with the existing layer, useful for adding origins, but it means the built-in `origins` list is never removed.

> ⚠️ **Common pitfall**: dropping a `data/neoorigins/origins/origin_layers/origin.json` file with only your origins and expecting the built-in origins to disappear. Without `"replace": true` the built-ins stay because the merge is additive.

### Adding a class

Classes are origins in the special `neoorigins:class` layer. Adding one uses
the same same-path auto-merge shown above (any layer file whose path is
`class` folds into `neoorigins:class`). See **[CLASSES.md](CLASSES.md)** for
the full guide and a copy-paste example datapack.

### Conditioned sub-layers

Layers can contain origins that are only visible when the player has chosen a specific origin in a parent layer. This enables race/subrace trees. See [Sub-Origins](SUB_ORIGINS.md) for full documentation and examples.

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

For powers in subdirectories (e.g. `powers/combat/slash.json` → ID `mypack:combat/slash`), the key keeps the `/` as-is: `power.mypack.combat/slash.name`.

Only the `power.` keys are looked up automatically. An origin's `name` and `description` are required fields, so the `origins.` keys above are a naming convention: they apply because the origin JSON names them.

---

## Resource Bar HUD

Powers of type `neoorigins:resource` display a HUD bar. The appearance is configured via a `hud_render` object in the power JSON:

```json
{
  "type": "neoorigins:resource",
  "min": 0, "max": 100, "start_value": 100,
  "hud_render": {
    "label": "Mana",
    "color": "#5577FF"
  }
}
```

| Field | Type | Default | Description |
|---|---|---|---|
| `label` | string | `"Resource"` | Display name shown above the bar. |
| `color` | string (hex) | `"#55AAFF"` | Fill colour as `#RRGGBB`. |
| `animated` | string (preset id) | — | Animated bar FX preset to render instead of the flat fill, e.g. `"neoorigins:fire"`. Presets are resource-pack JSON under `assets/<namespace>/bar_fx/<name>.json`. See [POWER_TYPES.md → resource](POWER_TYPES.md#neooriginsresource) for the preset format and [animated_bar_artist_spec.md](animated_bar_artist_spec.md) for texture authoring. |

Bars automatically **hide when full** and reappear when the resource drops below max. A bar that starts full is the exception until it is first spent: it stays on screen from the start, so players can find it, and begins hiding at full only once its value has dropped. The client forgets that first spend on each join, respawn and origin change. Set `always_render` in `hud_render` to keep a bar visible all the time. Players can reposition bars via the **Edit HUD** keybind (unbound by default). Positions persist across sessions in `config/neoorigins/hud.json`.

---

## Origins Mod Compatibility

Packs originally written for the Fabric Origins mod can be dropped into `config/originpacks/` and will be translated automatically. See the README for which power types translate and which are skipped.

The translation pass runs on load and on `/reload`. A full log is written to `logs/neoorigins-compat.log`.

Packs written for 1.19/1.20 also carry syntax that the current game no longer accepts — plural data folders, the removed `minecraft:set_nbt` loot function, NBT paths that step through the deleted `tag` field. Those are repaired as the file is read, with no edit to the pack itself; see [Legacy Origins packs](COMPATIBILITY.md#legacy-origins-packs) for what is covered and what is not.
