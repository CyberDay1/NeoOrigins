# NeoOrigins

A modern, ground-up reimplementation of the Origins experience for **NeoForge**.

Supports **MC 26.1** (Java 25) and **MC 1.21.1** (Java 21).

---

## Features

- **39 built-in origins** across two layers — choose an origin *and* a class
- **17 built-in classes** — Warrior, Archer, Miner, Beastmaster, Explorer, Sentinel, Herbalist, Scout, Berserker, Titan, Rogue, Lumberjack, Blacksmith, Cook, Merchant, Cleric, Nitwit
- **116 power types** — attribute modifiers, status effects, flight, wall climbing, damage modification, active abilities, biome effects, summon minions, toggle powers, and more
- **Random origin mode** — server config to randomly assign origins on first join or every death
- **Cooldown HUD overlay** — shows active ability cooldown bars above the hotbar
- **Origin info screen** — press O to view your current origin and class details
- **JEI / REI integration** — info panel for Orb of Origin
- **Hot-reload** — `/reload` rebuilds all origins and powers without restarting the server
- **Origins pack compatibility** — drop existing Origins mod content packs into `originpacks/` and they load automatically
- **Data-driven** — all origins and powers defined in JSON; fully overridable via datapacks
- **Per-origin config toggles** — disable any built-in origin or class in `neoorigins-common.toml`
- **Dimension restrictions** — disable specific powers in specific dimensions via config

---

## Built-in Origins

| Origin | Impact | Strengths | Weaknesses |
|---|---|---|---|
| **Human** | none | No special drawbacks | No special abilities |
| **Merling** | medium | Water breathing, aquatic speed, no drowning | Slowed on land |
| **Avian** | low | Slow fall, no fall damage, 75% less exhaustion | — |
| **Blazeling** | medium | Fire immunity, night vision | Water damage |
| **Elytrian** | medium | Free elytra flight, elytra boost | — |
| **Enderian** | low | Eye damage immunity | Water damage |
| **Arachnid** | low | Wall climbing, no fall damage while climbing | — |
| **Shulk** | medium | +4 natural armor | −30% movement speed |
| **Phantom** | medium | Invisibility, no gravity, night vision | — |
| **Feline** | medium | No fall damage, night vision, speed, pounce, mobs ignore | Water damage, +30% hunger |
| **Golem** | high | +6 armor, knockback resist, 1.3× size, poison/wither immune | −25% speed, fire weakness |
| **Caveborn** | medium | Night vision, no fall damage, 2× mining speed, 0.85× size | Burns in sunlight |
| **Sylvan** | low | Mobs ignore, water regen, forest speed, crop growth, root AoE | Nether damage |
| **Draconic** | high | Fire immune, flight, fireball, scare mobs, +2 attack, 1.2× size | Water damage, +50% hunger |
| **Revenant** | medium | Undead, water breathing, night vision, phase, spectral bolt | Burns in daylight, 40% regen |
| **Tiny** | medium | 0.5× size, wall climbing, +20% speed, no fall damage, item magnet | −2 attack, +80% hunger |
| **Abyssal** | high | Water breathing, night vision, thorns, underwater mining, water regen, trident, guardian summon | Burns in daylight, −10% land speed |
| **Voidwalker** | medium | Night vision, mobs ignore, phase, teleport | Water damage |
| **Stoneguard** | medium | +3 armor, thorns, knockback resist, glowstone placement, 3× stone mining, suppresses mob spawns | −10% speed |
| **Verdant** | low | Mobs ignore, no fall damage, no sprint-hunger, bonus harvest drops, forest regen | Nether damage |
| **Umbral** | medium | Night vision, shadow orbs (Darkness AoE), shadow dash, projectile immunity | Burns in sunlight |
| **Inchling** | medium | 0.25× size, wall climbing, no fall damage, +15% speed, 50% less hunger | −5 hearts |
| **Sporeling** | medium | Spore cloud AoE, poison immunity, night vision, mushroom biome regen, +4 armor | Burns in sunlight, −10% speed |
| **Frostborn** | medium | Frost nova AoE, cold biome buff, ice walk, +4 armor, freeze immunity | Fire weakness, Nether damage |
| **Strider** | medium | Fire immunity, Nether speed buff, lava regen, night vision, +6 armor | Water weakness, overworld slowness |
| **Siren** | medium | All mobs ignore, water breathing, fast swimming, night vision, water regen | −15% land speed, −2 hearts |
| **Piglin** | high | Nether Strength II, piglin friendship, fire immune, +2 attack, night vision | Overworld weakness |
| **Hiveling** | high | Flight, venomous sting, crop growth, 0.6× size, arthropod | −3 hearts, +50% hunger |
| **Cinderborn** | high | Fire immune, fireball, +6 armor, lava regen, Nether strength, scare mobs, night vision | Water weakness |
| **Sculkborn** | high | Sonic shriek, darkness AoE, +8 armor, knockback resist, projectile immune, night vision | Burns in daylight, −15% speed, −2 hearts |
| **Enderite** | high | Teleport 32 blocks, phase through walls, slow fall, +3 attack, scare endermites, night vision | Water damage, daylight weakness |
| **Necromancer** | high | Summon wither skeletons + skeletons, undead, night vision | Burns in daylight, −3 hearts, 40% regen |
| **Gorgon** | high | Petrifying gaze AoE, +2 attack, +4 armor, knockback resist, 1.1× size | −20% speed, +30% hunger |
| **Automaton** | high | +8 armor, no drowning, no hunger, potion resistance, night vision, knockback resist | −20% speed, rigid joints |
| **Kraken** | high | Water breathing, aquatic speed, tentacle strike, thorns, night vision, guardian summon, 1.3× size | Burns in daylight |
| **Warden** | high | Sonic boom, echolocation (glow), +4 attack, +10 armor, 1.15× size, night vision | Burns in daylight, −30% speed |
| **Dwarf** | medium | 0.8× size, +4 armor, night vision, +25% mining speed, −25% hunger drain | −15% speed, reduced reach |
| **Breeze** | high | Wind charge, wind dash, slow falling, Jump Boost II, +15% speed, 0.9× size | −4 hearts |
| **Vampire** | high | Undead, +2 attack, +15% speed, night vision | Burns in sunlight, raw meat diet, 40% regen, water weakness |

---

## Built-in Classes

Classes are a second selection layer — every player picks both an origin and a class.

| Class | Description |
|---|---|
| **Warrior** | +1 attack damage, 30% knockback resistance |
| **Archer** | Perfect projectile accuracy, +15% speed |
| **Miner** | +50% break speed, −30% hunger drain |
| **Beastmaster** | Potion effects shared with tamed animals, +50% potion duration |
| **Explorer** | Starts with compass, clock, maps; −40% hunger drain |
| **Sentinel** | +4 armor, 25% thorns, 20% knockback resistance |
| **Herbalist** | Accelerates nearby crop growth, +50% crop harvest, poison immunity |
| **Scout** | Night vision, +20% speed, no fall damage |
| **Berserker** | +3 attack damage, +50% hunger drain |
| **Titan** | 1.25× size, +2 hearts, extended reach |
| **Rogue** | Hidden nameplate, invisibility after 10s sneaking, backstab 2× damage |
| **Lumberjack** | One-hit tree felling, +2 extra planks per craft |
| **Blacksmith** | Crafted equipment has bonus attributes, efficient anvil repairs |
| **Cook** | Crafted food is more nourishing, bonus XP from smokers |
| **Merchant** | Better villager trades, rare wandering trader loot |
| **Cleric** | Longer potion durations, bonus healing |
| **Nitwit** | No special abilities — for the purist |

---

## Configuration

Edit `config/neoorigins-common.toml`:

```toml
# Disable specific origins or classes
[origins]
human = true
merling = true
# ... set any to false to remove from selection

[classes]
class_warrior = true
# ... set any to false to remove from selection

# Random origin assignment
[random_assignment]
# DISABLED / FIRST_JOIN / EVERY_DEATH
mode = "DISABLED"
# Number of rerolls allowed (0 = none, -1 = unlimited)
rerolls = 0

# Per-power dimension restrictions
[dimension_restrictions]
rules = [
    # "neoorigins:elytrian_flight = minecraft:the_nether, minecraft:the_end"
]
```

---

## Installation

1. Install [NeoForge](https://neoforged.net) for your Minecraft version (26.1 or 1.21.1)
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
| `origins:active_self` | Full active ability with cooldown |
| `origins:toggle` | Toggled active power with optional cooldown |
| `origins:resource` | Integer resource bar with min/max |
| `origins:conditioned_attribute` | Attribute modifier gated on a condition |
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

Requires Java 25 (MC 26.1 branch) or Java 21 (1.21.1 branch).

---

## Credits

Original Origins mod:

- https://www.curseforge.com/minecraft/mc-mods/origins
- https://github.com/apace100/origins-fabric

## License

MIT — see [LICENSE](LICENSE)
