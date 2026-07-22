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
| `required_mods` | list of mod ids | no | `[]` | Load gate: the origin only loads (and only appears in the picker) when every listed mod is present. Used by the built-in Dragon Survival origins (`"required_mods": ["dragonsurvival"]`). |
| `spawn_location` | object | no | — | Relocates the player to a matching location on first origin pick and on bedless respawn. See [Spawn Location](#spawn-location). |
| `tier_powers` | list | no | `[]` | Evolution-tier power overlays (`{tier, add, remove}`). Full reference in [EVOLUTION.md](EVOLUTION.md#datapack-customization) and the [COOKBOOK](COOKBOOK.md#adding-evolution-tiers-to-an-origin). |
| `figura_model` | string | no | — | Opaque base model key for the Figura soft-dep integration. Only meaningful with the Figura mod installed. See [Figura Model Key](#figura-model-key) and [FIGURA.md](FIGURA.md). |
| `figura_models` | object | no | — | Advanced reactive model maps (`tiers` / `powers` / `capabilities` / `vocab`) for Figura. Opaque strings, soft-dep only. See [Figura Model Key](#figura-model-key) and [FIGURA.md](FIGURA.md). |

### Spawn Location

An origin can declare a `spawn_location` to drop the player at a matching spot
instead of the world spawn. It is resolved server-side and fires:

- once, immediately after the player picks the origin (picker or Orb of Origin), and
- on respawn when the player has **no** bed or respawn anchor — using the
  player's *primary* origin (the first origin, in sorted layer order, that
  declares a `spawn_location`).

For respawn control that fires on **every** death (optionally overriding the
bed/anchor), use the [`neoorigins:modify_player_spawn`](POWER_TYPES.md#neooriginsmodify_player_spawn)
power instead — `spawn_location` only covers first-join and bedless respawn.

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

**Global kill switch.** All `spawn_location` teleports — built-in, datapack,
and compat origins alike — are gated by `[spawn_location] teleports_enabled`
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
| `origins` | list | yes | — | Origins available in this layer (plain IDs, or conditioned entries — see [Sub-Origins](SUB_ORIGINS.md)) |
| `allow_random` | bool | no | `true` | Show the Random button |
| `auto_choose` | bool | no | `false` | Automatically pick an origin if only one is available |
| `hidden` | bool | no | `false` | Hide the layer from the screen (still applies its origin) |
| `enabled` | bool | no | `true` | Whether the layer is active at all |

### Adding origins to the built-in layer

To add your origins to the default NeoOrigins origin selector, list them under the `neoorigins:origin` layer ID by creating a layer file at:

```
data/neoorigins/origins/origin_layers/origin.json
```

By default this **merges additively** into the built-in layer — your `origins` array is appended (deduplicated by ID) to the shipped one. You don't have to re-list every built-in origin.

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

The same applies to `neoorigins:class` and any other layer ID you target. Without `"replace": true`, your file's fields are merged additively with the existing layer — useful for adding origins, but it means the built-in `origins` list is never removed.

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

For powers in subdirectories (e.g. `powers/combat/slash.json` → ID `mypack:combat/slash`), replace `/` with `.` in the key: `power.mypack.combat/slash.name`.

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
| `animated` | string (preset id) | — | Animated bar FX preset to render instead of the flat fill, e.g. `"neoorigins:fire"`. Presets are resource-pack JSON under `assets/<namespace>/bar_fx/<name>.json` — see [POWER_TYPES.md → resource](POWER_TYPES.md#neooriginsresource) for the preset format and [animated_bar_artist_spec.md](animated_bar_artist_spec.md) for texture authoring. |

Bars automatically **hide when full** and reappear when the resource drops below max. Players can reposition bars via the **Edit HUD** keybind (unbound by default). Positions persist across sessions in `config/neoorigins/hud.json`.

---

## Origins Mod Compatibility

Packs originally written for the Fabric Origins mod can be dropped into `originpacks/` and will be translated automatically. See the README for which power types translate and which are skipped.

The translation pass runs on load and on `/reload`. A full log is written to `logs/neoorigins-compat.log`.
