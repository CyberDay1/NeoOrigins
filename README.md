# NeoOrigins

A modern, ground-up reimplementation of the Origins experience for **NeoForge**.

Supports **MC 26.1** (Java 25) and **MC 1.21.1** (Java 21).

📖 **Pack-author docs**: [cyberday1.github.io/NeoOrigins](https://cyberday1.github.io/NeoOrigins/) — full reference for all 74 power types, 74 conditions, 45 actions, and 33 events.

---

## What's new in 2.0

NeoOrigins 2.0 collapses 88 bespoke Java power-type classes into ~25 generic types driven by a JSON action / condition / event DSL. Pack authors now get a stable, schema-validated authoring surface; existing Origins-mod packs keep loading verbatim via transparent legacy aliases.

Highlights this release:

- **Aquatic overhaul** — Abyssal / Merling / Kraken / Siren rebuilt around a real dry-out mechanic with master configs for drain rate and drown damage. New "Pescivore + Raw Adapted" diet (raw cod and salmon nourish like cooked). Spawn placement now prefers ocean floor / water surface for aquatic origins.
- **Mod compat** — `LightTexture` and water-vision mixins now apply at higher priority so they survive other mods (Alex's Caves, etc.) that touch the same vanilla pipelines.
- **Dedicated-server stable** on both 1.21.1 and 26.1 (singleplayer-tested through alpha.27 hid six classes of dist crash; all fixed).
- **New `throw_target` action** — raycast the entity under your crosshair and hurl them away + upward.
- **74 power types, 74 conditions, 45 actions, 33 events** — `food_item_id` condition and `FOOD_FINISHED` event are new this release.

See `docs/2_0_CHANGES.md` for the full Phase 1–8 architecture write-up.

---

## Features

- **46 built-in origins** across two layers — choose an origin *and* a class
- **20 built-in classes** — Warrior, Archer, Miner, Beastmaster, Explorer, Sentinel, Herbalist, Scout, Berserker, Titan, Rogue, Lumberjack, Blacksmith, Cook, Merchant, Cleric, Nitwit, **Fisher**, **Mason**, **Paladin**
- **130+ power types** — attribute modifiers (with optional environment, condition, or equipment-slot gating), status/persistent effects, creative flight + natural elytra glide (no-item), wall climbing, bare-hand-as-tool (any vanilla tool at any tier), damage modification, on-hit/on-kill actions, active abilities (with hunger gating), biome effects, summon minions, tame hostile mobs, Fortune-from-effect loot multipliers, gravity wells, elemental magic, toggleable passives, HUD-hide powers, and more
- **Random origin mode** — server config to randomly assign origins on first join or every death
- **Cooldown HUD overlay** — shows active ability cooldown bars above the hotbar
- **Origin info screen** — press O to view your current origin and class details
- **JEI / REI integration** — info panel for Orb of Origin
- **Hot-reload** — `/reload` rebuilds all origins and powers without restarting the server
- **Origins pack compatibility** — drop existing Origins mod content packs into `originpacks/` and they load automatically
- **Data-driven** — all origins and powers defined in JSON; fully overridable via datapacks
- **Advancement-based origin upgrades** — any origin can declare upgrade paths in its JSON; when the player earns the advancement, their origin swaps automatically (see [examples/](examples/))
- **Epic Fight compatibility** — sized origins maintain correct scale when Epic Fight takes over rendering in combat mode
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
| **Stoneguard** | medium | +3 armor, thorns, knockback resist, glowstone placement, 2× mining speed, suppresses mob spawns | −10% speed |
| **Verdant** | low | Mobs ignore, no fall damage, no sprint-hunger, bonus harvest drops, forest regen | Nether damage |
| **Umbral** | medium | Night vision, shadow orbs (Darkness AoE), shadow dash, projectile immunity | Burns in sunlight |
| **Inchling** | medium | 0.25× size, wall climbing, no fall damage, +15% speed, 50% less hunger | −5 hearts |
| **Sporeling** | medium | Spore cloud AoE, poison immunity, night vision, mushroom biome regen, +4 armor | Burns in sunlight, −10% speed |
| **Frostborn** | medium | Frost nova AoE, cold biome buff, ice walk, +4 armor, freeze immunity | Fire weakness, Nether damage |
| **Strider** | medium | Fire immunity, Nether speed buff, lava regen, night vision, +6 armor | Water weakness, overworld slowness |
| **Siren** | medium | All mobs ignore, water breathing, fast swimming, night vision, water regen | −15% land speed, −2 hearts |
| **Piglin** | high | Nether Strength II, piglin friendship, fire immune, +2 attack, night vision | Overworld weakness |
| **Hiveling** | high | Flight, venomous sting, crop growth, 0.5× size, arthropod | −3 hearts, +50% hunger |
| **Cinderborn** | high | Fire immune, fireball, +6 armor, lava regen, Nether strength, scare mobs, night vision | Water weakness |
| **Sculkborn** | high | Sonic shriek, darkness AoE, +8 armor, knockback resist, projectile immune, night vision | Burns in daylight, −15% speed, −2 hearts |
| **Enderite** | high | Teleport 32 blocks, phase through walls, slow fall, +3 attack, scare endermites, night vision | Water damage, daylight weakness |
| **Necromancer** | high | Summon wither skeletons + skeletons, undead, night vision | Burns in daylight, −3 hearts, 40% regen |
| **Gorgon** | high | Petrifying gaze AoE, +2 attack, +4 armor, knockback resist, 1.1× size | −20% speed, +30% hunger |
| **Automaton** | high | +8 armor, no drowning, no hunger, potion resistance, night vision, knockback resist | −20% speed, rigid joints |
| **Kraken** | high | Water breathing, aquatic speed, tentacle strike, thorns, night vision, guardian summon, 1.3× size | Burns in daylight |
| **Warden** | high | Sonic boom, echolocation (glow), +4 attack, +10 armor, 1.15× size, night vision | Burns in daylight, −30% speed |
| **Dwarf** | medium | 0.5× size, +4 armor, night vision, +25% mining speed, −25% hunger drain | −15% speed, reduced reach |
| **Breeze** | high | Wind charge, wind dash, slow falling, Jump Boost II, +15% speed, 0.9× size | −4 hearts |
| **Vampire** | high | Undead, +2 attack, +15% speed, night vision | Burns in sunlight, raw meat diet, 40% regen, water weakness |
| **Monster Tamer** | high | Tame hostile mobs (max 4), command pack to attack, pack regen | −25% melee damage, +50% hunger |
| **Earth Mage** | high | Ground slam AoE, stone wall placement, +50% mining, −50% knockback | Can't swim, +50% fall damage |
| **Water Mage** | high | Tidal wave cone, healing mist AoE, water breathing, swim speed, water regen | Desert/badlands damage, −25% melee |
| **Fire Mage** | high | Fireball volley, inferno burst AoE, flame cloak, fire immunity | Water damage, +50% hunger |
| **Air Mage** | high | Wind charge, updraft launch, no fall damage, slow fall, +20% speed | −4 hearts, +50% knockback taken |
| **Gravity Mage** | high | Gravity well vortex (pull + damage), repulse blast, slow fall, no fall damage | +75% knockback taken, −25% melee |
| **Darkness Mage** | high | Shadow orbs (Darkness AoE), shadow step teleport, night vision, stealth | Burns in sunlight, −30% mining speed |

---

## Built-in Classes

Classes are a second selection layer — every player picks both an origin and a class. All class powers are passive or condition-gated — **zero keybind slots consumed** by any class.

| Class | Description |
|---|---|
| **Warrior** | +1 attack, 30% KB resist, +2 armor, +2 HP, immune weakness |
| **Archer** | Perfect projectile accuracy, +15% speed, enhanced vision, poison-on-hit vs arthropods, starting bow + 16 arrows |
| **Miner** | +50% break speed, −30% hunger drain, bare-hand stone pickaxe, enhanced vision, +2 HP |
| **Beastmaster** | Potion effects shared with tamed animals, +50% potion duration |
| **Explorer** | Starts with compass, clock, maps; −40% hunger drain; no fall damage; +0.5 step height; campfire regen (out of combat) |
| **Sentinel** | +4 armor, 25% thorns, 20% KB resist, immune weakness/slowness, +2 HP, +20% KB resist when sneaking |
| **Herbalist** | Accelerates nearby crop growth, +50% crop harvest, poison immunity, +1 bonemeal application, starting seeds + bone meal |
| **Scout** | Night vision, +20% speed, no fall damage, +0.5 step height, starting bread |
| **Berserker** | +3 attack, +50% hunger drain, +2 damage when HP≤50%, −2 armor, +0.2 KB resist |
| **Titan** | 1.25× size, +2 hearts, extended reach, +1 attack, +0.2 KB resist, −10% speed |
| **Rogue** | Hidden nameplate, invisibility while sneaking, backstab 2× damage, 0.5× fall damage, +0.5 step height |
| **Lumberjack** | One-hit tree felling, +2 extra planks per craft, bare-hand iron axe, starting iron axe (Unbreaking II) |
| **Blacksmith** | 0.5× anvil cost, Unbreaking I auto-applied to tools/armor, bare-hand stone pickaxe, 0.5× fire damage, starting 4 iron ingots |
| **Cook** | Crafted food is more nourishing, bonus XP from smokers, immune hunger/nausea, 1.25× potion duration, starting iron sword (cook's knife) |
| **Merchant** | Better villager trades, rare wandering trader loot, starting 8 emeralds, 1.15× potion duration, +1 luck |
| **Cleric** | +5 enchant levels, 2× potion duration, weakness-on-hit vs undead, enhanced vision, starting writable book |
| **Nitwit** | No special abilities — for the purist |
| **Fisher** ⭐ | +1 luck in water, +15% swim speed, 0.5× drown damage, starting rod (Luck of the Sea I + Lure I), night vision underwater |
| **Mason** ⭐ | Bare-hand stone pickaxe, +1 armor, 1.25× break speed, starting stone pickaxe (Efficiency I), +1 block placement reach |
| **Paladin** ⭐ | Weakness-on-hit vs undead, +2 armor, regen near beacons, starting iron sword (Smite I), wither immunity |

⭐ = new

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

## Origin Spawn Locations

Any origin can declare a `spawn_location` that the mod will teleport the player to:

1. **Immediately when they pick the origin** (via the selection screen or an Orb of Origin), and
2. **On death when they have no bed or respawn anchor set** — instead of world spawn.

```json
{
  "name": "origins.mypack.void_knight.name",
  "description": "origins.mypack.void_knight.description",
  "icon": "minecraft:end_crystal",
  "powers": [ "mypack:void_knight_flight" ],
  "spawn_location": {
    "dimension": "minecraft:the_end",
    "structure": "minecraft:end_city"
  }
}
```

| Field | Type | Description |
|---|---|---|
| `dimension` | Identifier | Target dimension (player is switched to this level) |
| `biome` | Identifier | Find a position inside this biome ID |
| `biome_tag` | Identifier | Find a position inside a biome with this tag |
| `structure` | Identifier | Find a position inside this structure |
| `structure_tag` | Identifier | Find a position inside a structure with this tag |
| `allow_water_surface` | boolean | Default `false`. If no dry land column is found, accept the topmost water column — player spawns feet-in-water, head above. |
| `allow_ocean_floor` | boolean | Default `false`. If no dry land column is found, accept the seabed — player spawns submerged on the floor (needs water breathing to survive). |

All fields are optional; fields combine with AND (dimension narrows the search, then structure/biome pins down the position within that dimension). Structure match takes precedence over biome when both are specified. The finder sweeps a 5×5 column area around the hit and scans top-down for the first `(solid, air, air)` column — logical height is respected, so Nether spawns stay below the bedrock ceiling. When that strict land pass finds nothing, `allow_ocean_floor` tries `(solid, water, water)`, then `allow_water_surface` tries the water surface. Search radius is 6400 blocks from that dimension's world spawn; if nothing matches at all, the origin selection/respawn proceeds without teleport (with a warning in the log).

On a respawn **with** a set bed or respawn anchor, vanilla behavior applies — `spawn_location` is only used when there's no respawn point to honor.

The same dimension/biome/structure fields can gate a `neoorigins:attribute_modifier` power effect as a `location_condition` — see [docs/POWER_TYPES.md](docs/POWER_TYPES.md#neooriginsattribute_modifier). (`allow_water_surface` / `allow_ocean_floor` are ignored in the gate path — they only influence the spawn finder.)

---

## Advancement-Based Origin Upgrades

Any origin can declare upgrade paths that fire when the player earns specific advancements. This is fully datapack-driven — no Java code required.

Add an `upgrades` list to any origin JSON:

```json
{
  "name": "...",
  "powers": ["..."],
  "upgrades": [
    {
      "advancement": "minecraft:story/enter_the_nether",
      "origin": "neoorigins:strider",
      "announcement": "mypack.upgrade.strider"
    }
  ]
}
```

- **Per-layer**: the same advancement can drive different swaps on different layers (origin + class)
- **Chainable**: each intermediate origin defines its own `upgrades` to the next stage
- **announcement** is optional — a translation key sent as a system message on upgrade

See the [examples/](examples/) folder for working datapacks demonstrating simple upgrades, multi-stage chains, and class-layer promotions.

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
