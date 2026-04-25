# NeoOrigins Example Pack

A working datapack that showcases common NeoOrigins 2.0 idioms. Use it as
a reference when building your own pack, or install it as-is for eight
playable origins.

> This pack is **not** exhaustive — it covers the high-traffic power
> types and composition patterns. For the complete catalogue see
> [docs/POWER_TYPES.md](../docs/POWER_TYPES.md) and [docs/API.md](../docs/API.md).

## Installation

Drop this folder (or zip it first) into the `originpacks/` directory in
your Minecraft game folder.

## Origins included

| Origin | Showcases |
|---|---|
| **Pyromancer** | `prevent_action` (fire), `persistent_effect` (night vision), `modify_damage` (in / out), `attribute_modifier` (strength) |
| **Golem** | `attribute_modifier` (armor + speed), `prevent_action` (fall), `effect_immunity`, `no_slowdown`, `scare_entities` |
| **Specter** | `flight`, `elytra_boost`, `phantom_form`, `water_breathing`, `wall_climbing`, `scare_entities`, `conditional` gate |
| **Paladin** | `attribute_modifier` with `equipment_condition` (helmet-gated), `action_on_hit` (self-heal vs undead), `action_on_hit` (apply-effect to target) |
| **Void Knight** | `spawn_location` to a specific structure (End City on pick + bed-less respawn), `attribute_modifier` with `location_condition` (End-only armor) |
| **Netherborn** | `spawn_location` to the Nether with a structure filter (fortress), **stacked `starting_equipment` powers** (Fire Aspect sword + 8 porkchops), `condition_passive` biome aura |
| **Tidecaller** | `spawn_location` with `biome_tag` + `allow_ocean_floor`, `starting_equipment` (Loyalty trident), `condition_passive` water aura |
| **Hearth Keeper** | Pure `starting_equipment` showcase — three stacked grants (bread, seeds, saplings) with no custom spawn, plus `crop_growth_accelerator` |

## Spawn-location + starting-equipment patterns

Three origins demonstrate the origin-level spawn override and the
starting-equipment power:

### Origin-level `spawn_location`

Put this at the top level of an origin JSON (not inside a power). When
the player picks the origin (and again on respawn if they have no bed),
the game teleports them to a location matching the filter. All fields
are optional and combine with AND.

```json
{
  "name": "origins.examplepack.netherborn.name",
  "icon": "minecraft:netherrack",
  "impact": "medium",
  "powers": [ "examplepack:netherborn_starting_sword" ],
  "spawn_location": {
    "dimension": "minecraft:the_nether",
    "structure": "minecraft:fortress"
  }
}
```

| Field | Purpose |
|---|---|
| `dimension` | Dimension ID — e.g. `"minecraft:the_nether"`, `"minecraft:the_end"` |
| `biome` | Specific biome ID |
| `biome_tag` | Biome tag (matches any biome carrying the tag) |
| `structure` | Structure ID — teleport to the nearest matching structure |
| `structure_tag` | Structure tag |
| `allow_water_surface` | If true, counts the water surface as valid spawn ground |
| `allow_ocean_floor` | If true, spawns on the ocean floor (Tidecaller uses this) |

See `void_knight.json`, `netherborn.json`, `tidecaller.json` for three
shapes of this field.

### `starting_equipment` power

One power grants one item (optionally with enchantments + count). To give
multiple items, stack multiple `starting_equipment` powers — each needs a
unique `grant_id` so the grant-ledger can tell them apart on re-picks.

```json
{
  "type": "neoorigins:starting_equipment",
  "grant_id": "examplepack:netherborn_starting_sword",
  "item": "minecraft:stone_sword",
  "count": 1,
  "enchantments": [
    { "enchantment": "minecraft:fire_aspect", "level": 1 }
  ]
}
```

Hearth Keeper stacks three of these (bread × 8, wheat seeds × 16, oak
saplings × 4) to give the player a small traveling kit at spawn. Items
are granted only when the picker **commits** — a rolled-back or
cancelled origin pick doesn't consume the grant.

## File layout

```
example-pack/
  pack.mcmeta
  data/examplepack/origins/
    origins/         pyromancer.json, golem.json, specter.json, paladin.json,
                     void_knight.json, netherborn.json, tidecaller.json, hearth_keeper.json
    powers/          one file per power (~40)
    origin_layers/   examplepack.json   (adds all eight to a separate tab)
  assets/examplepack/lang/
    en_us.json       all display names and descriptions
```

## Notes

- All power JSONs in this pack use the 2.0 `neoorigins:*` verb namespace
  inside conditions and actions. Legacy `origins:*` still works but emits
  a `[2.0-legacy]` deprecation warning — see
  [docs/MIGRATION.md](../docs/MIGRATION.md).
- Specter bundles `specter_no_fall_base` + `specter_conditional_fall` to
  demonstrate the `neoorigins:conditional` gate pattern.
- Display text lives in `assets/examplepack/lang/en_us.json` using the
  `power.<namespace>.<power_id>.name` convention. Power JSONs reference
  those keys via `name` / `description` fields.
- See [docs/COOKBOOK.md](../docs/COOKBOOK.md) for recipe-oriented patterns,
  [docs/POWER_TYPES.md](../docs/POWER_TYPES.md) for the full field
  reference, and [docs/API.md](../docs/API.md) for the flat verb index.
