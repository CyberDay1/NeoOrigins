# NeoOrigins

A modern, ground-up reimplementation of the Origins experience for **NeoForge (MC 26.1)**.

---

## Features

- **21 built-in origins** — Human, Merling, Avian, Blazeling, Elytrian, Enderian, Arachnid, Shulk, Phantom, Feline, Golem, Caveborn, Draconic, Sylvan, Tiny, Revenant, Abyssal, Voidwalker, Stoneguard, Verdant, Umbral
- **51 built-in power types** — attribute modifiers, status effects, flight, wall climbing, damage modification, active abilities, biome effects, starting equipment, and more
- **Hot-reload** — `/reload` rebuilds all origins and powers without restarting the server
- **Origins pack compatibility** — drop existing Origins mod content packs into `originpacks/` and they load automatically
- **Data-driven** — all origins and powers defined in JSON; fully overridable via datapacks

---

## Built-in Origins

| Origin | Impact | Icon | Strengths | Weaknesses |
|---|---|---|---|---|
| **Human** | none | `minecraft:apple` | No special drawbacks | No special abilities |
| **Merling** | medium | `minecraft:prismarine_shard` | Water breathing, aquatic speed boost, no drowning | Slowed on land |
| **Avian** | low | `minecraft:feather` | Slow fall, no fall damage, no sprint-hunger | — |
| **Blazeling** | medium | `minecraft:blaze_powder` | Fire immunity, night vision | Water and rain cause damage |
| **Elytrian** | medium | `minecraft:elytra` | Elytra glide without equipping elytra, free flight | — |
| **Enderian** | low | `minecraft:ender_pearl` | No eye damage from projectiles | Water and rain cause damage |
| **Arachnid** | low | `minecraft:string` | Wall climbing, no fall damage while climbing | — |
| **Shulk** | medium | `minecraft:shulker_shell` | +4 natural armor | −30% movement speed |
| **Phantom** | medium | `minecraft:phantom_membrane` | Permanent invisibility, no gravity | — |
| **Feline** | medium | `minecraft:raw_salmon` | No fall damage, night vision, +15% speed, creepers ignore, directional launch | Water 1.5× damage, +30% hunger drain |
| **Golem** | high | `minecraft:iron_ingot` | +4 armor, knockback resistance, 1.3× size, poison/wither immunity | −30% speed, fire 1.8× damage |
| **Caveborn** | medium | `minecraft:torch` | Night vision, no fall damage, 2× break speed on stone/deepslate | Burns in direct sunlight, 0.9× size |
| **Draconic** | high | `minecraft:dragon_breath` | Fire immunity, slow fall, elytra flight, fireball ability, scares small mobs, 1.2× size, +2 attack | Water 2× damage, +50% hunger drain |
| **Sylvan** | low | `minecraft:oak_sapling` | Mobs ignore, regen in water, forest speed buff, crop growth accelerator, root AoE ability | Damage in nether/desert biomes, plant-only diet |
| **Tiny** | medium | `minecraft:poppy` | 0.5× size, wall climbing, +20% speed, no fall damage, item magnetism | −2 attack damage, 1.8× hunger drain |
| **Revenant** | medium | `minecraft:rotten_flesh` | Undead entity group (poison immune), water breathing, night vision, phase through walls, purple bolt | Burns in daylight, 50% slower natural regen |
| **Abyssal** | high | `minecraft:heart_of_the_sea` | Water breathing, night vision, thorns aura, underwater mining, regen in water, starts with enchanted trident, scares ocean mobs | Burns in daylight, −20% land speed |
| **Voidwalker** | medium | `minecraft:ender_pearl` | Night vision, mobs ignore, phase through walls (hunger cost), short-range teleport | Water damage, no sprint-hunger drain |
| **Stoneguard** | medium | `minecraft:stone` | +4 natural armor, thorns aura, knockback resistance, place glowstone on cooldown, 2× stone mining, suppresses mob spawns nearby | −30% movement speed |
| **Verdant** | low | `minecraft:grass_block` | Mobs ignore, no fall damage, no sprint-hunger, bonus drops from crops and logs, regen in forests | Damage in nether/desert biomes |
| **Umbral** | medium | `minecraft:coal` | Night vision, place shadow orbs (Darkness 28-block radius, max 4), dash ability, projectile immunity | Burns in direct sunlight, no sprint-hunger drain |

---

## Installation

1. Install [NeoForge 26.1.0.1-beta](https://neoforged.net) for Minecraft 26.1
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

NeoOrigins runs two translation passes over Origins-format JSON at load time.

**Route A — direct type mapping** (static translation to a NeoOrigins equivalent):

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

**Route B — compat power engine** (compiled into live event-driven behaviour at load time):

| Origins type | What it does |
|---|---|
| `origins:active_self` | Full active ability with cooldown; action types: `origins:apply_effect`, `origins:add_velocity`, `origins:teleport`, `origins:change_resource`, `origins:if_else`, `origins:and` |
| `origins:toggle` | Toggled active power with optional cooldown |
| `origins:resource` | Integer resource bar with min/max; `change_resource` action writes to it |
| `origins:conditioned_attribute` | Attribute modifier gated on a condition (biome, fluid, sky, block proximity, etc.) |
| `origins:conditioned_status_effect` | Status effect gated on a condition |
| `origins:action_on_being_hit` | Triggers an action when the player takes damage |
| `origins:action_on_hit` | Triggers an action when the player deals damage |

### What is skipped

The following types have no equivalent in NeoOrigins and are **silently skipped** — the rest of the origin still loads:

- `origins:overlay`, `origins:shader`, `origins:particle`, `origins:model_color` — visual/rendering effects
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
[SKIP] merling:swim_speed  (origins:swim_speed — no equivalent)
---------------------------------------------------------
Summary: 2 passed, 1 failed, 1 skipped
```

### Line types

| Prefix | Meaning |
|--------|---------|
| `[PASS]` | Power translated and loaded successfully |
| `[FAIL]` | Power type should translate but encountered a missing field or unsupported sub-type. The power is skipped; the rest of the origin loads normally |
| `[SKIP]` | Power type is known to be unsupported in this version. Not a bug — expected for visual effects and unimplemented variants |

**A high SKIP count is normal** for packs that use visual or rendering powers. The origin will still appear in the selection screen; only the untranslatable powers are absent.

**A FAIL line** means something unexpected went wrong. The power ID and reason are shown. Check whether the pack targets a very old Origins version with a different JSON schema.

### Per-namespace load diagnostics

For addon and datapack authors debugging load issues, NeoOrigins can log a per-namespace power count breakdown after each reload. This is disabled by default. To enable it, open `config/neoorigins-common.toml` and set:

```toml
# Log per-namespace power counts after each data reload.
# Useful for addon and datapack authors debugging load issues.
debug_power_loading = true
```

With this enabled, the game log will show an entry like:

```
[NeoOrigins] Loaded 87 powers
  [DEBUG] powers: neoorigins  x20
  [DEBUG] powers: origins_pack_name  x67
```

### Power descriptions from external packs

Power descriptions in the origin selection screen are resolved in this order:

1. Explicit `name`/`description` fields in the power JSON (NeoOrigins native format, or packs that embed them)
2. Translation keys derived from the power ID — `power.<namespace>.<path>.name` / `.description` — matching the Fabric Origins convention

Most well-maintained packs (Origins++, etc.) follow convention 2 and will display correctly. Packs that store descriptions in a non-standard location or use a different key format may show only the power ID as a fallback. Check `logs/neoorigins-compat.log` to confirm which powers loaded, then verify the pack's lang file uses the expected key pattern.

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

Requires Java 25.

---

## Releases

Tagged releases are built automatically via GitHub Actions. Each release on the [Releases page](https://github.com/CyberDay1/NeoOrigins/releases) includes the mod JAR built against the versions listed in its release notes.

---

Originial Origin Mod 

https://www.curseforge.com/minecraft/mc-mods/origins
https://github.com/apace100/origins-fabric

## License

MIT — see [LICENSE](LICENSE)
