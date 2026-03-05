# NeoOrigins

A modern, ground-up reimplementation of the Origins experience for **NeoForge 1.21.1**. Built without porting the original mod — no tech debt, no deprecated APIs, no compatibility shims baked into the core. Just clean NeoForge architecture with first-class support for the modern toolchain.

---

## Features

- **9 built-in origins** — Human, Merling, Avian, Blazeling, Elytrian, Enderian, Arachnid, Shulk, Phantom
- **14 built-in power types** — attribute modifiers, status effects, flight, wall climbing, damage modification, and more
- **Hot-reload** — `/reload` rebuilds all origins and powers without restarting the server
- **Origins pack compatibility** — drop existing Origins mod content packs into `originpacks/` and they load automatically
- **Data-driven** — all origins and powers defined in JSON; fully overridable via datapacks

---

## Installation

1. Install [NeoForge 21.11.38-beta](https://neoforged.net) for Minecraft 1.21.1
2. Drop `neoorigins-<version>.jar` into your `mods/` folder
3. Launch — the `originpacks/` folder is created automatically in your game directory

---

## Origins Pack Compatibility (`originpacks/`)

NeoOrigins can load content from existing Origins mod packs without any modification. On first launch, an `originpacks/` folder is created in your game directory (next to `mods/` and `config/`).

**Supported pack formats:**
| Format | How to install |
|--------|---------------|
| `.jar` (Origins mod JAR) | Drop directly into `originpacks/` |
| `.zip` (datapack) | Drop directly into `originpacks/` |
| Folder | Drop the unpacked folder into `originpacks/` |

Packs are scanned at world load and on `/reload`. No `pack.mcmeta` is required.

### What translates automatically

NeoOrigins runs a translation pass over all Origins-format JSON at load time. The following power types are translated to their NeoOrigins equivalents:

| Origins type | Result |
|---|---|
| `origins:attribute` | `neoorigins:attribute_modifier` |
| `origins:elytra_flight` / `origins:creative_flight` | `neoorigins:flight` |
| `origins:night_vision` | `neoorigins:night_vision` |
| `origins:water_breathing` | `neoorigins:water_breathing` |
| `origins:stacking_status_effect` / `origins:status_effect` | `neoorigins:status_effect` |
| `origins:effect_immunity` | `neoorigins:effect_immunity` |
| `origins:modify_damage_taken` / `origins:modify_damage_dealt` | `neoorigins:modify_damage` |
| `origins:invulnerability` | `neoorigins:prevent_action` (fire, approximate) |
| `origins:disable_regen` | `neoorigins:prevent_action` (sprint food) |
| `origins:slow_falling` | `neoorigins:prevent_action` (fall damage) |
| `origins:walk_speed` | `neoorigins:attribute_modifier` (movement speed) |
| `origins:multiple` | Expanded to individual sub-powers |
| `origins:active_self` (apply_effect only) | `neoorigins:status_effect` |

### What is skipped

Some Origins power types require a full condition/action engine that is not present in NeoOrigins 1.x. These are **silently skipped** — the rest of the origin still loads:

- `origins:resource`, `origins:toggle` — stateful resource bars and toggles
- `origins:action_on_hit`, `origins:action_on_being_hit`, `origins:action_on_kill` — event-triggered actions
- `origins:conditioned_attribute`, `origins:conditioned_status_effect` — condition-gated effects
- `origins:overlay`, `origins:shader`, `origins:particle`, `origins:model_color` — visual effects
- `origins:lava_vision`, `origins:swim_speed`, `origins:air_acceleration` — movement/vision variants
- `origins:keep_inventory`, `origins:ignore_water`, `origins:climbing` — misc behaviours
- `origins:phasing`, `origins:burn`, `origins:fire_projectile`, `origins:exhaust` — interaction effects

A full compat log is written to `logs/neoorigins-compat.log` every time origins load so you can see exactly what translated and what did not.

---

## Reading the Compat Log

After each world load or `/reload`, open `logs/neoorigins-compat.log` in your game directory.

```
NeoOrigins Compat Translation Log — 2026-03-05T14:23:01
---------------------------------------------------------
[PASS] merling:water_breathing  (origins:water_breathing -> neoorigins:water_breathing)
[PASS] avian:origins/slow_fall  (origins:multiple sub-power)
[FAIL] coppergolem:abilities/heal  (origins:active_self: unsupported action type origins:if_else_list)
[SKIP] merling:swim_speed  (origins:swim_speed — no equivalent in Route A, requires Route B)
---------------------------------------------------------
Summary: 2 passed, 1 failed, 1 skipped
```

### Line types

| Prefix | Meaning |
|--------|---------|
| `[PASS]` | Power translated and loaded successfully |
| `[FAIL]` | Power type should translate but encountered a missing field or unsupported sub-type. The power is skipped; the rest of the origin loads normally |
| `[SKIP]` | Power type is known to be unsupported in this version. Not a bug — expected for resource bars, toggles, and visual effects |

**A high SKIP count is normal** for packs that use complex conditional powers. The origin will still appear in the selection screen; only the untranslatable powers are absent.

**A FAIL line** means something unexpected went wrong. The power ID and reason are shown. Check whether the pack targets a very old Origins version with a different JSON schema.

---

## Writing Your Own Origins

Place JSON files in your datapack under:

```
data/<namespace>/origins/origins/<name>.json   # origin definitions
data/<namespace>/origins/powers/<name>.json    # power definitions
data/<namespace>/origins/origin_layers/<name>.json  # layer definitions
```

NeoOrigins format example:

```json
{
  "name": { "text": "Merling" },
  "description": { "text": "Adapted to life underwater." },
  "icon": "minecraft:prismarine_shard",
  "impact": "medium",
  "powers": ["neoorigins:merling_water_breathing", "neoorigins:merling_aquatic_speed"]
}
```

For Origins-mod-compatible path layout (`data/<ns>/origins/`, `data/<ns>/powers/`, `data/<ns>/origin_layers/`) the translation pass runs automatically.

---

## Building from Source

```bash
git clone https://github.com/CyberDay1/NeoOrigins.git
cd NeoOrigins
./gradlew build
# Output: build/libs/neoorigins-<version>.jar
```

Requires Java 21.

---

## Releases

Tagged releases are built automatically via GitHub Actions. Each release on the [Releases page](https://github.com/CyberDay1/NeoOrigins/releases) includes the mod JAR built against the versions listed in its release notes.

---

## License

MIT — see [LICENSE](LICENSE)
