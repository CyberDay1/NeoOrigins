# NeoOrigins

A modern, ground-up reimplementation of the Origins experience for **NeoForge**.

Supports **MC 1.21.1** (Java 21), **MC 26.1** and **MC 26.2** (both Java 25).

📖 **Pack-author docs**: [cyberday1.github.io/NeoOrigins](https://cyberday1.github.io/NeoOrigins/) (full reference for all 122 power types, 108 entity conditions, 95 entity actions, and 51 events).

🛠️ **Web editor**: [cyberday1.github.io/NeoOrigins/editor](https://cyberday1.github.io/NeoOrigins/editor/) (build origin and mob-origin JSON in the browser, no install needed).

---

## What's new in 2.0

NeoOrigins 2.0 collapses 88 bespoke Java power-type classes into ~25 generic types driven by a JSON action / condition / event DSL. Pack authors now get a stable, schema-validated authoring surface; existing Origins-mod packs keep loading verbatim via transparent legacy aliases.

Highlights this release:

- **Aquatic overhaul**: Abyssal / Merling / Kraken / Siren rebuilt around a real dry-out mechanic with master configs for drain rate and drown damage. New "Pescivore + Raw Adapted" diet (raw cod and salmon nourish like cooked). Spawn placement now prefers ocean floor / water surface for aquatic origins.
- **Mod compat**: `LightTexture` and water-vision mixins now apply at higher priority so they survive other mods (Alex's Caves, etc.) that touch the same vanilla pipelines.
- **Dedicated-server stable** on both 1.21.1 and 26.1 (singleplayer-tested through alpha.27 hid six classes of dist crash; all fixed).
- **New `throw_target` action**: raycast the entity under your crosshair and hurl them away + upward.
- **122 power types, 108 entity conditions, 95 entity actions, 51 events**. The DSL surface has kept growing since 2.0: visual/interaction power types (burn, ignore_water, overlay, model_color, lava_vision, shader), `near_entity` and friends, and near-complete Origins compat coverage.

---

## Features

- **58 built-in origins** (55, plus three Dragon Survival origins that only load with that mod) across two layers: choose an origin *and* a class
- **20 built-in classes**: Warrior, Archer, Miner, Beastmaster, Explorer, Sentinel, Herbalist, Scout, Berserker, Titan, Rogue, Lumberjack, Blacksmith, Cook, Merchant, Cleric, Nitwit, **Fisher**, **Mason**, **Paladin**
- **122 power types**: attribute modifiers (with optional environment, condition, or equipment-slot gating), status/persistent effects, creative flight + natural elytra glide (no-item), wall climbing, bare-hand-as-tool (any vanilla tool at any tier), damage modification, on-hit/on-kill actions, active abilities (with hunger gating), biome effects, summon minions, tame hostile mobs, Fortune-from-effect loot multipliers, gravity wells, elemental magic, toggleable passives, HUD-hide powers, and more
- **Random origin mode**: server config to randomly assign origins on first join or every death
- **Cooldown HUD overlay**: shows active ability cooldown bars above the hotbar
- **Animated resource bars**: a resource power can name an animated FX preset (`"animated": "neoorigins:fire"` in its `hud_render` block) and the bar fill renders as a scrolling texture strip instead of a flat colour. Presets are resource-pack JSON under `assets/<ns>/bar_fx/`. See [docs/PACK_FORMAT.md](docs/PACK_FORMAT.md#resource-bar-hud)
- **Dragon Survival integration**: three built-in dragon origins (Cave / Forest / Sea) turn the player into a Dragon Survival dragon via the `become_dragon` power; content gated behind `required_mods` only loads when the target mod is installed. See [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md)
- **Origin info screen**: press O to view your current origin and class details
- **Named-keybind pool**: packs can declare more than six active abilities; each `"key"` translation id on a power gets a labelled hotkey slot the player can rebind from *Controls → Key Binds → NeoOrigins (Hotkeys)*. See [docs/API.md#named-keybinds](docs/API.md#named-keybinds)
- **Night-vision toggle**: press K to switch off origin-granted night vision when you'd rather have the dark back; it starts on, toggles only your own view, and persists across relogs. Rebindable in *Controls → Key Binds → NeoOrigins*, alongside Skill 1-6 (V/G/N/B) and Class Skill (H). See [docs/CLIENT_CONFIG.md#built-in-keybinds](docs/CLIENT_CONFIG.md#built-in-keybinds)
- **Skinnable UI**: origin selection / info screens read colours, panel texture, font and 9-slice insets from a datapack-and-resourcepack theme. Ships with `neoorigins:parchment` (bundled Newsreader OFL font); addon packs can register their own theme and activate it via `data/<ns>/neoorigins/active_theme.json`. See [docs/THEMING.md](docs/THEMING.md) and the copy-and-edit skeleton at [`docs/theme-template/`](docs/theme-template)
- **JEI / REI integration**: JEI shows an info panel for the Orb of Origin; REI lists the Orb of Origin as an entry
- **Hot-reload**: `/reload` rebuilds all origins and powers without restarting the server
- **Origins pack compatibility**: drop existing Origins mod content packs into `config/originpacks/` and they load automatically
- **Data-driven**: all origins and powers defined in JSON; fully overridable via datapacks
- **Advancement-based origin upgrades**: any origin can declare upgrade paths in its JSON; when the player earns the advancement, their origin swaps automatically (see [examples/](examples/))
- **Epic Fight compatibility**: sized origins maintain correct scale when Epic Fight takes over rendering in combat mode
- **Per-origin config toggles**: disable any built-in origin (except the three dragon origins) or class in `config/neoorigins/content.toml`
- **Dimension restrictions**: disable specific powers in specific dimensions via config

---

## Mob Origin System (2.1)

Pack authors can author origins **for mobs** the same way they author origins for
players. A mob origin is a JSON file under `data/<ns>/origins/mob_origins/<id>.json`
that bundles powers, weighted spawn rules, drops, and behavior (applied to any
non-player `LivingEntity`).

- **Weighted spawn rules**: apply origins to natural mob spawns with biome,
  structure, dimension, time-of-day, Y-range and light-range filters
- **Per-origin drops**: additive or replace, with both per-item independent-chance
  and weighted-pool strategies, layered onto vanilla loot via a global loot modifier
- **`neoorigins:mob_behavior` power**: configurable, piglin-style aggression
  (neutral / hostile / conditional) using the existing entity-condition DSL
- **Spawn-egg minting**: `/neoorigins mob egg <origin>` produces a vanilla spawn
  egg that spawns the mob with the origin pre-attached; right-clicking it on a
  spawner reconfigures the spawner too
- **Mob Origin Creator GUI**: tabbed in-game authoring tool (Identity / Powers /
  Spawn Rules / Drops / JSON Preview). Open with `/neoorigins mob editor` (permission level
  2) or bind the *Open Mob Origin Creator* keybind in Controls → Key Binds →
  NeoOrigins
- **Portable output**: written to `<world>/datapacks/neoorigins_custom/`; copy the
  folder to another instance with NeoOrigins installed and it keeps working

See [`docs/MOB_ORIGINS.md`](docs/MOB_ORIGINS.md) for the pack-author reference.

---

## Global Power Sets

Grant powers to entities **without an origin assignment**: NeoOrigins' port of
Apoli's `apoli:global` feature. A global power set is a JSON file under
`data/<ns>/global_powers/<id>.json` that lists `powers` and an optional
`entity_types` filter (mixing literal ids and `#tags`); when the filter is absent
the powers apply to every player and mob.

- **Players**: granted on login and re-reconciled on `/reload`; persisted like
  any other dynamically-granted power
- **Mobs**: mob-applicable powers applied at spawn (`FinalizeSpawnEvent`)
- **`order`**: optional load/apply ordering (lower applies first)

See [`docs/GLOBAL_POWERS.md`](docs/GLOBAL_POWERS.md) for the pack-author reference.

---

## Built-in Origins

| Origin | Impact | Strengths | Weaknesses |
|---|---|---|---|
| **Human** | none | No special drawbacks | No special abilities |
| **Merling** | medium | Water breathing, aquatic speed, night vision, underwater mining | Slowed on land (−10%), dries out on land |
| **Avian** | low | Toggleable slow fall, no fall damage, 75% less exhaustion, feather hop, keen sight | −1 heart |
| **Blazeling** | high | Fire immunity, night vision, +3 hearts, firebolt, Nether speed | Water damage, +25% hunger |
| **Elytrian** | medium | Elytra glide without an elytra, elytra boost, no fall or wall-impact damage | −2 hearts, can't wear heavy armor |
| **Enderian** | low | Endermen ignore your gaze, teleport, 50% chance to dodge projectiles by teleporting | Water damage |
| **Arachnid** | medium | Wall climbing, cobweb affinity, poison bite, cobweb shot, arthropod | None |
| **Shulk** | high | Resistance I, levitation burst AoE, shell retreat (random teleport), levitation immunity | −25% movement speed |
| **Phantom** | high | Elytra glide, elytra boost, night vision, water breathing, no fall damage, Resistance I at night, heal on kill | Burns in sunlight, −2 hearts, can't sleep |
| **Feline** | medium | No fall damage, night vision, +10% speed, pounce, creepers ignore you | Water damage, +30% hunger |
| **Golem** | high | Resistance I, 80% knockback resist, 1.3× size, poison/wither/hunger immune, ground slam | −25% speed, 1.8× fire damage |
| **Caveborn** | medium | Night vision, no fall damage, 2× mining speed, 0.85× size | Burns in sunlight |
| **Sylvan** | low | Mobs ignore, water regen, forest speed, crop growth, root AoE | Nether damage |
| **Draconic** | high | Fire immune, elytra-style flight, fireball volley, scare mobs, +2 attack, 1.2× size, no fall damage | Water damage, +50% hunger |
| **Revenant** | medium | Undead, water breathing, night vision, phase, spectral bolt | Burns in daylight, 40% regen |
| **Tiny** | medium | 0.5× size, wall climbing, +20% speed, no fall damage, item magnet | −2 attack, +80% hunger |
| **Abyssal** | high | Water breathing, night vision, thorns, underwater mining, +2 hearts, starting trident, guardian summon | −10% land speed, dries out on land |
| **Voidwalker** | medium | Night vision, mobs ignore, phase, teleport | Water damage |
| **Stoneguard** | medium | +3 hearts, thorns, knockback resist, glowstone placement, 2× mining speed, suppresses mob spawns | −10% speed |
| **Verdant** | low | Mobs ignore, no fall damage, no sprint-hunger, bonus harvest drops, forest regen | Nether damage |
| **Umbral** | medium | Night vision, shadow orbs (Darkness AoE), shadow dash, arrow immunity, no sprint hunger | Burns in sunlight |
| **Inchling** | medium | 0.25× size, wall climbing, no fall damage, +15% speed, 50% less hunger | −5 hearts |
| **Sporeling** | medium | Spore cloud AoE, poison immunity, night vision, mushroom biome regen, Resistance I | Burns in sunlight, −10% speed |
| **Frostborn** | medium | Frost nova AoE, Strength in taiga biomes, ice placement, Resistance I, freeze immunity | 2× burning damage, Nether damage |
| **Strider** | high | Fire immunity, Nether Speed II, lava regen, night vision, Resistance I, stampede dash | Water weakness, overworld slowness |
| **Siren** | medium | All mobs ignore, water breathing, fast swimming, night vision, underwater mining | −15% land speed, −2 hearts, dries out on land |
| **Piglin** | high | Nether Strength II, piglin friendship, +2 attack, night vision | Overworld weakness, Soul Sand Valley damage |
| **Hiveling** | high | Elytra glide, venomous sting, crop growth, 0.6× size, +40% jump, arthropod | −3 hearts, +50% hunger |
| **Cinderborn** | high | Fire immune, fireball, Resistance I, lava regen, Nether strength, scare mobs, night vision | Water weakness |
| **Sculkborn** | high | Sonic shriek, darkness AoE, Resistance I, knockback resist, arrow immune, night vision, vibration sense | Burns in daylight, −15% speed, −2 hearts |
| **Enderite** | high | Teleport 32 blocks, phase through walls, slow fall, +3 attack, scare endermites, night vision | Water damage, daylight weakness |
| **Necromancer** | high | Summon wither skeletons + skeletons, undead, night vision | Burns in daylight, −3 hearts, 40% regen |
| **Gorgon** | high | Petrifying gaze AoE, +4 attack, Resistance I, knockback resist, 1.15× size | −20% speed, +50% hunger |
| **Automaton** | high | Resistance I, no drowning, no hunger, poison immune, night vision, knockback resist | −15% speed, 25% natural regen, immune to Regeneration and Instant Health |
| **Kraken** | high | Water breathing, aquatic speed, tentacle lash, ink shot, Resistance I, guardian summon, 1.3× size | Burns in daylight, −30% land speed, dries out on land |
| **Warden** | high | Sonic boom, echolocation (glow), +4 attack, Resistance II, 1.15× size, night vision | Burns in daylight, −30% speed |
| **Dwarf** | medium | 0.8× size, Resistance I, night vision, +25% mining speed, −25% hunger drain, starting iron pickaxe | −15% speed, reduced reach |
| **Breeze** | high | Wind charge, wind dash, slow falling, Jump Boost II, +15% speed, 0.9× size | −4 hearts |
| **Vampire** | high | Undead, +2 attack, +15% speed, night vision | Burns in sunlight, raw meat diet, 40% regen, water weakness |
| **Monster Tamer** | high | Tame hostile mobs (max 4), command pack to attack, pack regen | −25% damage dealt while no tamed mob is alive, +50% hunger |
| **Earth Mage** | high | Ground slam AoE, stone wall placement, +50% mining, −50% knockback | Can't swim, +50% fall damage |
| **Water Mage** | high | Tidal wave cone, healing mist AoE, water breathing, swim speed, water regen | Desert/badlands damage, −25% damage dealt |
| **Fire Mage** | high | Fireball volley, inferno burst AoE, flame cloak, fire immunity | Water damage, +50% hunger |
| **Air Mage** | high | Wind charge, whirlwind, updraft launch, no fall damage, slow fall, +20% speed | −2 hearts, +50% knockback taken |
| **Gravity Mage** | high | Gravity well vortex (pull + damage), repulse blast, toggleable levitation, no fall damage | +75% knockback taken, −25% damage dealt |
| **Darkness Mage** | high | Shadow orbs (Darkness AoE), shadow step teleport, night vision, stealth | Burns in sunlight, −30% mining speed |
| **Wraith** | high | Toggleable phasing through solid blocks (not obsidian, crying obsidian or bedrock), water breathing | Burns in sunlight, phasing drains hunger, +75% hunger |
| **Skeleton** | high | Undead, water breathing, +50% arrow damage, +20% speed, +30% jump | −3 hearts, burns in sunlight, diet limited to bone meal, rotten flesh and spider eyes |
| **Slime** | high | No fall damage, bounces on landing, Regeneration above 75% moisture, splits instead of dying above 75% moisture, +1 max HP per 10 levels (up to +20) | Moisture drains out of water (faster in dry biomes or on fire): −4 armor below 10%, damage at 0% |
| **Asura** | high | Bloodrage: Strength and Speed I/II/III at 66%/40%/25% health, knockback immune at 25% health, slam, frenzy (Strength II, Speed II, Resistance I) | −3 hearts |
| **Windwalker** | medium | No fall damage, two mid-air jumps, higher jumps, wall climbing, gale dash, +20% speed, typhoon | None |
| **Qi Cultivator** | medium | Qi meter (refills faster while sneaking), Regeneration while sneaking, vibrating palm (Qi projectile), hardened skin (Resistance II) | None |
| **Golden Body** | medium | +8 armor, 60% knockback resist, 4 thorns damage to attackers, Golden Bell (Resistance V for 5s) | −20% speed |
| **Iron Monk** | medium | Guard stance (90% less damage taken while stamina lasts), palm strike AoE, 40% knockback resist | Slowness I while guarding, guarding drains stamina |
| **Sword Immortal** | high | With a sword in hand: +4 attack, +1 attack speed, sword qi, flight, flickering slash, ten thousand swords rain; no fall damage | Abilities need a sword in the main hand |
| **Cave Dragon*** | high | Become a Dragon Survival cave dragon; DS supplies traits, growth and abilities | Managed by Dragon Survival |
| **Forest Dragon*** | high | Become a Dragon Survival forest dragon; DS supplies traits, growth and abilities | Managed by Dragon Survival |
| **Sea Dragon*** | high | Become a Dragon Survival sea dragon; DS supplies traits, growth and abilities | Managed by Dragon Survival |

\* The dragon origins require the [Dragon Survival](https://www.curseforge.com/minecraft/mc-mods/dragon-survival) mod: they declare `"required_mods": ["dragonsurvival"]` and only load (and only appear in the picker) when it is installed.

---

## Built-in Classes

Classes are a second selection layer: every player picks both an origin and a class. Class powers never take one of the origin skill slots (Skill 1-6). A class's first active power is fired by the separate Class Skill key (H): the built-in Explorer, Rogue and Scout use it to switch their step assist on and off.

Pack authors can add their own classes. See [docs/CLASSES.md](docs/CLASSES.md).

| Class | Description |
|---|---|
| **Warrior** | +1 attack, 30% KB resist, +2 armor, +2 HP, immune weakness |
| **Archer** | Perfect projectile accuracy, +15% speed, enhanced vision, Slowness-on-hit vs arthropods, starting bow (Power I) + 16 arrows |
| **Miner** | +50% break speed, −30% hunger drain, bare-hand stone pickaxe, enhanced vision, +2 HP |
| **Beastmaster** | Potion effects shared with tamed animals, +50% potion duration |
| **Explorer** | Starts with compass, clock, maps; −40% hunger drain; no fall damage; +0.5 step height; campfire regen (out of combat) |
| **Sentinel** | +4 armor, 25% thorns, 40% KB resist, immune weakness/slowness, +2 HP, +35% KB resist when sneaking |
| **Herbalist** | Accelerates nearby crop growth, +1 extra drop from mature crops and logs, poison immunity, +1 bonemeal application, starting seeds + bone meal |
| **Scout** | Night vision, +20% speed, no fall damage, +0.5 step height, starting bread |
| **Berserker** | +3 attack, +50% hunger drain, +2 damage when HP≤50%, −2 armor, +0.2 KB resist |
| **Titan** | 1.25× size, +2 hearts, extended reach, +1 attack, +0.2 KB resist, −10% speed |
| **Rogue** | Mobs notice you only within 30% of their follow range, invisibility after sneaking for 10 seconds, +2 attack damage while sneaking, 0.5× fall damage, +0.5 step height (switched on the Class Skill key) |
| **Lumberjack** | One-hit tree felling, +2 extra oak planks when crafting oak planks, bare-hand iron axe, starting iron axe (Unbreaking II) |
| **Blacksmith** | 0.5× anvil cost, crafted/smithed gear gets +10% durability plus +25% mining speed (tools), +20% damage (weapons) or +1 toughness (armor), bare-hand stone pickaxe, 0.5× fire damage, starting 4 iron ingots |
| **Cook** | Crafted and smelted food gets +1 nutrition and extra saturation (smelted food +2 more nutrition), immune hunger/nausea, 1.25× potion duration, starting iron sword (cook's knife) |
| **Merchant** | Restocks trades of villagers within 8 blocks, rare wandering trader loot, starting emerald, 1.15× potion duration, +1 luck |
| **Cleric** | +5 enchant levels, 2× potion duration, weakness-on-hit vs undead, enhanced vision, starting writable book |
| **Nitwit** | No special abilities: for the purist |
| **Fisher** ⭐ | +1 luck in water, +15% speed in water, 0.5× drown damage, starting rod (Luck of the Sea I + Lure I), night vision underwater |
| **Mason** ⭐ | Bare-hand stone pickaxe, +1 armor, 1.25× break speed, starting stone pickaxe (Efficiency I), +1 block placement reach |
| **Paladin** ⭐ | Weakness-on-hit vs undead, +2 armor, regen near beacons, starting iron sword (Smite I), wither immunity |

⭐ = new

---

## Configuration

Configs live in the `config/neoorigins/` folder (since 2.2.2; legacy
`neoorigins-common.toml` / `neoorigins-server.toml` / `neoorigins-client.toml`
files are migrated automatically on first launch, values intact):

- `neoorigins/gameplay.toml`: orb of origins, auto-human, skip initial selection, random assignment, evolution, spawn location teleports, ocean origins, sun damage, mount consent, friendly fire, armor classes, cooldowns
- `neoorigins/admin.toml`: command blacklist, command access, dimension restrictions, entity exclusions, compat filtering, debug flags
- `neoorigins/power_overrides.toml`: per-power parameter overrides
- `neoorigins/content.toml`: origin/class enable toggles + resource-bar, night-vision and enhanced-vision disables (SERVER config, auto-synced to clients; per-world override via `<world>/serverconfig/neoorigins/content.toml`)
- `neoorigins/client.toml`: client-only options (UI theme and picker, HUD, hotkey pool, cross-mod compat)
- `neoorigins/hud.json`: saved HUD bar/cluster positions

```toml
# config/neoorigins/content.toml — disable specific origins or classes
[origins]
human = true
merling = true
# ... set any to false to remove from selection

[classes]
class_warrior = true
# ... set any to false to remove from selection

# config/neoorigins/gameplay.toml — random origin assignment
[random_assignment]
# DISABLED / FIRST_JOIN / EVERY_DEATH
mode = "DISABLED"
# Number of rerolls allowed (0 = none, -1 = unlimited)
rerolls = 0

# config/neoorigins/admin.toml — per-power dimension restrictions
[dimension_restrictions]
rules = [
    # "neoorigins:elytrian_flight = minecraft:the_nether, minecraft:the_end"
]

# config/neoorigins/admin.toml — command access
[commands]
# Allow non-OP players to run /neoorigins get <player> (OPs always can)
public_origin_get = true

# config/neoorigins/admin.toml — global taming/scare exclusions
[entity_exclusions]
# Entity ids / #tags that can never be tamed, scared, or made to ignore a
# player by any power (the Warden, Ender Dragon and Wither always are)
tame_scare_entity_blacklist = []
```

---

## Installation

1. Install [NeoForge](https://neoforged.net) for your Minecraft version (1.21.1, 26.1 or 26.2)
2. Drop `neoorigins-<version>.jar` into your `mods/` folder
3. Launch. The `config/originpacks/` folder is created automatically

---

## Origins Pack Compatibility (`originpacks/`)

NeoOrigins can load content from existing Origins mod packs without any modification. On first launch, a `config/originpacks/` folder is created. A legacy `originpacks/` folder at the game root is still read when `config/originpacks/` does not exist, with a log warning to move it.

**Supported pack formats:**
| Format | How to install |
|--------|---------------|
| `.jar` (Origins mod JAR) | Drop directly into `config/originpacks/` |
| `.zip` (datapack) | Drop directly into `config/originpacks/` |
| Folder | Drop the unpacked folder into `config/originpacks/` |

Packs are scanned at world load and on `/reload`. No `pack.mcmeta` is required.

### What translates automatically

NeoOrigins runs two translation passes over Origins-format JSON at load time.

**Route A** (direct type mapping, static translation to a NeoOrigins equivalent):

| Origins type | Result |
|---|---|
| `origins:attribute` | `neoorigins:attribute_modifier` |
| `origins:elytra_flight` / `origins:creative_flight` | `neoorigins:elytra_flight` / `neoorigins:creative_flight` |
| `origins:night_vision` | `neoorigins:night_vision` (legacy alias, loads as `neoorigins:persistent_effect`) |
| `origins:water_breathing` | `neoorigins:water_breathing` |
| `origins:stacking_status_effect` / `origins:status_effect` | `neoorigins:stacking_status_effects` / `neoorigins:status_effect` (legacy aliases, load as `neoorigins:persistent_effect`) |
| `origins:effect_immunity` | `neoorigins:effect_immunity` |
| `origins:modify_damage_taken` / `origins:modify_damage_dealt` | `neoorigins:modify_damage` |
| `origins:invulnerability` | `neoorigins:invulnerability` (a simple `damage_condition` is projected into damage-type / tag / name filters) |
| `origins:prevent_death` / `apace:prevent_death` | `neoorigins:prevent_death` (condition + `entity_action` honored; `damage_condition` packs fall back to Route B) |
| `origins:disable_regen` | `neoorigins:prevent_action` (sprint food) |
| `origins:slow_falling` | `neoorigins:prevent_action` (fall damage) |
| `origins:walk_speed` | `neoorigins:attribute_modifier` (movement speed) |
| `origins:modify_swim_speed` / `origins:swim_speed` | `neoorigins:attribute_modifier` (water movement efficiency) |
| `origins:climbing` | `neoorigins:wall_climbing` |
| `origins:keep_inventory` | `neoorigins:keep_inventory` |
| `origins:ignore_water` | `neoorigins:ignore_water` |
| `origins:phasing` | `neoorigins:wraith_phase` |
| `origins:burn` | `neoorigins:burn` |
| `origins:particle` | `neoorigins:particle` |
| `origins:overlay` | `neoorigins:overlay` |
| `origins:model_color` | `neoorigins:model_color` |
| `origins:lava_vision` | `neoorigins:lava_vision` |
| `origins:shader` | `neoorigins:shader` |
| `origins:food_restriction` / `origins:edible_item` | `neoorigins:food_restriction` (legacy alias, loads as `neoorigins:action_on_event`) / `neoorigins:edible_item` |
| `origins:multiple` | Expanded to individual sub-powers |

This is a selection: `OriginsPowerTranslator` has Route A cases for 55 `origins:` types. `apoli:` and `apugli:` types are read as their `origins:` equivalents.

**Route B** (compat power engine, compiled into live event-driven behaviour at load time):

| Origins type | What it does |
|---|---|
| `origins:active_self` | Full active ability with cooldown |
| `origins:toggle` | Toggled active power with optional cooldown |
| `origins:resource` | Integer resource bar with min/max |
| `origins:conditioned_attribute` | Attribute modifier gated on a condition |
| `origins:conditioned_status_effect` | Status effect gated on a condition |
| `origins:action_on_being_hit` | Triggers an action when the player takes damage |
| `origins:action_on_hit` | Triggers an action when the player deals damage |

Route B accepts action and condition fields in both Apoli forms: a single object or an array (an array is treated as an implicit all-of). Apoli's `command` action verb is an alias of `execute_command` and goes through the same command blacklist.

### What is skipped

A power whose type has neither a Route A nor a Route B handler is **skipped** (logged, not loaded); the rest of the origin still loads.

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
2. **On death when they have no bed or respawn anchor set**, instead of world spawn.

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
| `biomes` | list of Identifier | Find a position inside any of these biome IDs |
| `structure` | Identifier | Find a position inside this structure |
| `structure_tag` | Identifier | Find a position inside a structure with this tag |
| `min_y` / `max_y` | integer | Clamp the vertical band the column scan searches |
| `can_see_sky` | boolean | Whether a land spawn must see the sky. Default `true`, except in ceiling dimensions such as the Nether (`false`) |
| `allow_water_surface` | boolean | Default `false`. Accept the topmost water column (tried before the land pass): player spawns feet-in-water, head above. |
| `allow_ocean_floor` | boolean | Default `false`. Accept the seabed (tried first, before the water surface and land passes): player spawns submerged on the floor (needs water breathing to survive). |

All fields are optional. `dimension` picks the level to search (default: the player's current one); `biome`, `biome_tag` and `biomes` combine with OR; structure match takes precedence over biome when both are specified. The finder spirals out up to 16 blocks around the hit (a 33×33 column area) and scans each column top-down. If `allow_ocean_floor` is set it first looks for `(solid, water, water)`, then `allow_water_surface` looks for the water surface, and only then does the land pass look for a solid floor with air at feet and head, no lava and 3×3 clearance. Logical height is respected, with a 16-block margin under the bedrock ceiling in the Nether. Structure and biome searches reach 6400 blocks from that dimension's world spawn; if nothing matches, the origin selection/respawn proceeds without teleport (with a warning in the log). The teleport is skipped entirely when `teleports_enabled` is `false` under `[spawn_location]` in `gameplay.toml`.

On a respawn **with** a set bed or respawn anchor, vanilla behavior applies; `spawn_location` is only used when there's no respawn point to honor.

The same dimension/biome/structure fields can gate a `neoorigins:attribute_modifier` power effect as a `location_condition`. See [docs/POWER_TYPES.md](docs/POWER_TYPES.md#neooriginsattribute_modifier). (`allow_water_surface` / `allow_ocean_floor` are ignored in the gate path; they only influence the spawn finder.)

---

## Advancement-Based Origin Upgrades

Any origin can declare upgrade paths that fire when the player earns specific advancements. This is fully datapack-driven: no Java code required.

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
- **announcement** is optional: a translation key sent as a system message on upgrade

See the [examples/](examples/) folder for working datapacks demonstrating simple upgrades, multi-stage chains, and class-layer promotions.

---

## Building from Source

```bash
git clone https://github.com/CyberDay1/NeoOrigins.git
cd NeoOrigins
./gradlew build
# Output: build/libs/neoorigins-<version>.jar
```

Requires Java 21 on the `1.21.1` branch, and Java 25 on `master` (MC 26.1) and `26.2`.

---

## Credits

Original Origins mod:

- https://www.curseforge.com/minecraft/mc-mods/origins
- https://github.com/apace100/origins-fabric

### Translations

- **Russian (`ru_ru`)**: community translation by [@Nienya972](https://github.com/Nienya972)
- **Simplified Chinese (`zh_cn`), Spanish (`es_es`), German (`de_de`), Brazilian Portuguese (`pt_br`)**: machine-translated starting points, pending community review

Community translations are welcome: open a pull request (or an issue with the file attached) adding a `<locale>.json` to `src/main/resources/assets/neoorigins/lang/`. Native-speaker corrections to the machine-translated locales are especially appreciated.

## License

MIT. See [LICENSE](LICENSE)
