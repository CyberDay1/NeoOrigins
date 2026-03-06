# NeoOrigins Example Pack

A ready-to-install example that demonstrates every built-in NeoOrigins power type.

## Installation

Drop this folder (or zip it first) into the `originpacks/` directory in your Minecraft game folder.

## What's included

### Origins

| Origin | Powers demonstrated |
|---|---|
| **Pyromancer** | `prevent_action` (fire), `night_vision`, `status_effect`, `modify_damage` (out), `modify_damage` (in) |
| **Golem** | `attribute_modifier` (armor + speed), `prevent_action` (fall), `effect_immunity`, `no_slowdown`, `scare_entities` |
| **Specter** | `flight`, `elytra_boost`, `phantom_form`, `water_breathing`, `prevent_action` (sprint food), `wall_climbing`, `scare_entities`, `tick_action`, `conditional` |

All 15 built-in power types are represented across the three origins.

## File layout

```
example-pack/
  pack.mcmeta
  data/examplepack/origins/
    origins/         pyromancer.json, golem.json, specter.json
    powers/          one file per power (16 total)
    origin_layers/   examplepack.json   (separate layer from built-in origins)
  assets/examplepack/lang/
    en_us.json       all display names and descriptions
```

## Notes

- The Specter origin includes `specter_no_fall_base` and `specter_conditional_fall` as a pair to demonstrate `neoorigins:conditional`. Both must be listed in the origin's `powers` array.
- All display text lives in `assets/examplepack/lang/en_us.json` using the standard key convention (`power.<namespace>.<power_id>.name`). The power JSONs themselves reference these keys via the `name`/`description` fields.
- See [docs/POWER_TYPES.md](../docs/POWER_TYPES.md) for the complete field reference for each type.
- See [docs/PACK_FORMAT.md](../docs/PACK_FORMAT.md) for the origin/power/layer JSON schema.
