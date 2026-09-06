# NeoOrigins Gameplay Config Reference

Player-facing gameplay tuning lives in the TOML file
`config/neoorigins/gameplay.toml`. This is a **COMMON** config, not a server
config: it loads early enough to be readable during the boot-time datapack
reload, it is **not** synced to clients, and there is **no** per-world
`<world>/serverconfig/` override (unlike [`content.toml`](CONTENT_CONFIG.md)).
Not syncing is fine because these values are baked into the power/origin data
that is synced separately: every rule here is enforced server-side.

The file is created with defaults on first launch. Edit it while the
game/server is closed.

> **Config split (2.2.2).** `gameplay.toml` is one of four config files.
> Its siblings are [`content.toml`](CONTENT_CONFIG.md) (SERVER: synced,
> per-world overridable), [`admin.toml`](ADMIN_CONFIG.md) (COMMON: operator
> policy) and `power_overrides.toml` (COMMON: per-power stat overrides).
> This doc covers `gameplay.toml` only.

## `config_version`

One top-level key sits outside any section:

| Option | Type | Default | Description |
|---|---|---|---|
| `config_version` | int | `1` | Written by the mod to track one-time config migrations: **do not edit by hand**. It lets the mod tell a config it has already fixed up from one written by an older version, so each migration runs exactly once. Deleting it makes the mod treat the file as pre-2.2.22 again and re-run the heals. |

## `[orb_of_origins]`: XP cost tuning

Controls XP cost behaviour when a player uses an Orb of Origin or Orb of Class.

| Option | Type | Default | Range | Description |
|---|---|---|---|---|
| `scale_cost` | bool | `true` | | Whether the XP cost scales with the number of prior orb uses. `true`: cost = `levels_per_use` * previous orb uses (first use free, then ramps). `false`: flat cost, every use (including the first) costs exactly `levels_per_use` levels. |
| `levels_per_use` | int | `5` | 0 – 1000 | XP levels charged per Orb of Origin use, per the `scale_cost` formula above. Set to `0` to disable XP cost entirely. |
| `class_levels_per_use` | int | `2` | 0 – 1000 | Flat XP levels charged per Orb of Class use. The Orb of Class only resets the class layer (keeping the main origin), so it is intentionally cheaper than the full Orb of Origin reset. Set to `0` to disable its XP cost entirely. |

## `[auto_human]`: skip origin selection

| Option | Type | Default | Description |
|---|---|---|---|
| `enabled` | bool | `false` | When true, new players are automatically assigned `neoorigins:human` on the origin layer and skip straight to the class selection screen. Useful for servers that want to bypass origin selection entirely. |

## `[skip_initial_selection]`: no origin at all

| Option | Type | Default | Description |
|---|---|---|---|
| `enabled` | bool | `false` | When true, new players spawn with **no** origin and the selection screen never opens on first join. They play as an origin-less player until granted one later (e.g. via an Orb of Origin). Unlike auto-human mode this assigns nothing, and unlike disabling every class it does not leave the player stuck invulnerable. **Takes priority** over auto-human and random-assignment modes. |

## `[random_assignment]`: random origins

| Option | Type | Default | Range | Description |
|---|---|---|---|---|
| `mode` | enum | `DISABLED` | | When to randomly assign origins. `DISABLED`: players choose normally. `FIRST_JOIN`: origins are randomly assigned on first join (no selection screen). `EVERY_DEATH`: origins are randomly re-assigned on each respawn. |
| `rerolls` | int | `0` | -1 – 100 | Number of times a player may reroll after random assignment. `0`: no rerolls (stuck with what you get). `-1`: unlimited rerolls via Orb of Origin. |

## `[evolution]`: essence evolution

Origins evolve through 3 tiers (Evolved, then Ascended, then Apex) by
accumulating mob kills. An Orb of Origin resets the player to base tier.

| Option | Type | Default | Range | Description |
|---|---|---|---|---|
| `enabled` | bool | `true` | | Enable the evolution system. When false, kills are not tracked and evolution prompts never appear. |
| `tier_1_kills` | int | `1000` | 1 – 1000000 | Mob kills required to reach Evolved (tier 1). |
| `tier_2_kills` | int | `2500` | 1 – 1000000 | Mob kills required to reach Ascended (tier 2). |
| `tier_3_kills` | int | `5000` | 1 – 1000000 | Mob kills required to reach Apex (tier 3). |
| `message_interval` | int | `100` | 10 – 10000 | Chat milestone message interval (every N kills). |

## `[spawn_location]`: origin spawn teleports

Origins may declare a `spawn_location` (e.g. ocean origins, Nether origins)
that teleports the player there when the origin is first picked.

| Option | Type | Default | Description |
|---|---|---|---|
| `teleports_enabled` | bool | `true` | Master toggle for **all** origin `spawn_location` teleports. When false, no origin relocates the player on origin pick: built-in, datapack and compat origins all spawn at the world's normal spawn point. Overrides `ocean_origins.spawn_in_ocean`. |

## `[ocean_origins]`: aquatic origin behaviour

Per-feature toggles for the built-in ocean origins (Abyssal, Kraken, Merling,
Siren).

| Option | Type | Default | Range | Description |
|---|---|---|---|---|
| `spawn_in_ocean` | bool | `true` | | Teleport ocean origins to a random ocean biome on first origin pick. Turn off to let them spawn at the world's normal spawn point. Also gated by `spawn_location.teleports_enabled` above. |
| `dries_out` | bool | `true` | | Ocean origins slowly lose air while out of water (Minecraft-fish style). Turn off to disable the on-land suffocation entirely. |
| `drain_rate_ticks` | int | `1` | 1 – 1200 | Ticks per single air point lost while out of water. Default `1` matches vanilla cod and salmon exactly: air starts at 300, so 300 ticks (15 seconds) on land before drown damage begins. Larger values mean a slower drain: total land time in seconds is roughly `(300 * value) / 20`, so `2` gives 30s and `4` gives 1 minute. Replaces the per-power `drain_rate` field in built-in `dries_out` JSONs. |
| `drown_damage_per_second` | double | `2.0` | 0.0 – 100.0 | Damage applied per second once a dried-out aquatic player's virtual air is exhausted. Mirrors `WaterAnimal.handleAirSupply` cadence (vanilla cod / salmon). Default `2.0` is 1 heart per second. Set to `0` to make dry-out non-lethal. |
| `fish_diet_required` | bool | `true` | | Pescivore restriction: when true, ocean origins can only eat items in the `neoorigins:fish_foods` tag; non-fish food is silently cancelled. Set to false to let them eat anything (powered by the `aquatic_fish_diet` power's runtime check on this flag). Default true matches the long-standing pescivore design. |
| `extra_fish_foods` | list | `[]` | | Extra items ocean origins may **also** eat on top of the `neoorigins:fish_foods` tag. Additive: an item counts as fish food if it is in that tag OR listed here. Use this to whitelist modded fish (Aquaculture, Hybrid Aquatic, etc.) without editing a datapack. Format: item ids (e.g. `"aquaculture:tuna"`) or tags (e.g. `"#aquaculture:fishes"`). Referenced from JSON via the `food_item_in_config_list` condition with key `ocean_origins.extra_fish_foods`. |

## `[sun_damage]`: helmet absorption

Tuning for the helmet absorption rule on sun-damage origins (Abyssal,
Caveborn, Vampire, Phantom, Warden, etc.). When the player is in direct sun
and wearing a damageable helmet, the helmet absorbs the burn at the cost of
its own durability.

| Option | Type | Default | Range | Description |
|---|---|---|---|---|
| `helmet_protection` | bool | `true` | | When true (default), wearing any helmet cancels sun burn for sun-damage origins: the current, vanilla-like behaviour. When false, sun-damage origins burn in daylight even with a helmet equipped, and the helmet takes no durability damage since it is no longer protecting the player. |
| `helmet_dura_damage_chance` | double | `0.07` | 0.0 – 1.0 | Per-evaluation chance that a damageable helmet takes 1 durability while the player is in direct sun. Evaluated once per `condition_passive` interval (~1 second), so `0.07` averages 1 durability per ~14 seconds: about 40 minutes of continuous sun for a 165-durability iron helmet. Unbreaking still stacks via vanilla `hurtAndBreak`. Set to `0` so helmets never lose durability from sun protection; set to `1` to match the vanilla zombie/skeleton wear rate (very fast). Fire-resistant helmets (the `minecraft:fire_resistant` component: netherite in vanilla) and unbreakable helmets ignore this value and never wear out. |

## `[mount]`: player-to-player mounting consent

| Option | Type | Default | Range | Description |
|---|---|---|---|---|
| `consent_mode` | enum | `ALWAYS` | | How mounting consent works. `ALWAYS`: mount any player without consent. `PROMPT`: the target must click **[ACCEPT]** or run `/neoorigins mount accept`. `TEAM`: auto-allow if both players share a team (FTB Teams or Open Parties and Claims); falls back to `ALWAYS` if no team mod is loaded. |
| `request_timeout_seconds` | int | `30` | 5 – 300 | Seconds before a mount request expires (only used in `PROMPT` mode). |

## `[friendly_fire]`: AOE target filtering

Controls which entity types are excluded from origin area-of-effect actions
(poison sting, fire burst, ink shot, etc.). When a toggle is true, that
category of mob is excluded from the AOE target list and will not take
effects or damage from the player's own abilities.

| Option | Type | Default | Description |
|---|---|---|---|
| `protect_owned_pets` | bool | `true` | Protect TamableAnimals owned by the casting player (wolves, cats, etc.). |
| `protect_minions` | bool | `true` | Protect mobs tracked as minions of the casting player (necromancer skeletons, beastmaster summons, etc.). |
| `protect_animals` | bool | `false` | Protect **all** passive animals (sheep, cow, pig, horse, fox, ...). Default false: an active combat AOE should hit livestock; otherwise abilities like Hiveling Sting silently no-op against passive mobs. Turn on if your pack treats farm animals as untouchable allies. |
| `protect_villagers` | bool | `true` | Protect villagers and wandering traders from origin AOEs. Default true: avoids accidentally aggroing or killing your trade hub. |
| `protect_iron_golems` | bool | `true` | Protect iron golems from origin AOEs. Default true: village-built golems represent player investment, and their protection is consistent with vanilla golem AI rules. |

## `[armor_classes]`: heavy/light armor lists

Configurable armor categories used by `restrict_armor` powers. These lists
are **additive** to the `neoorigins:heavy_armor` and `neoorigins:light_armor`
item tags: an item counts as part of a class if it is in **either** the tag
or the config list. Use them to add modded armor to the correct class without
a datapack.

| Option | Type | Default | Description |
|---|---|---|---|
| `heavy_armor` | list | `[]` | Additional items/tags to treat as heavy armor. Default heavy armor (iron, gold, diamond, netherite) is defined in the `neoorigins:heavy_armor` item tag and does not need to be listed here. Format: item IDs (e.g. `"modid:my_helmet"`) or tags (e.g. `"#modid:my_armor_tag"`). |
| `light_armor` | list | `[]` | Additional items/tags to treat as light armor. Default light armor (leather + chainmail) is defined in the `neoorigins:light_armor` item tag. Same format. |

## `[cooldowns]`: active-ability cooldowns

| Option | Type | Default | Description |
|---|---|---|---|
| `creative_no_cooldown` | bool | `true` | When true, players in Creative mode ignore active-ability cooldowns entirely: every keybind / on-hit / on-kill power can fire without waiting. Cooldowns still apply normally in Survival/Adventure, so a creative-mode player switching back to survival resumes any in-flight cooldown. Useful for testing powers without spam-waiting. |

Source of truth: `config/GameplayConfig.java`.
