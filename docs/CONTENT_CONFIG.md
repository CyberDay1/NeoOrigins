# NeoOrigins Content Config Reference

Server-controlled content toggles live in the TOML file
`config/neoorigins/content.toml`. Unlike [`client.toml`](CLIENT_CONFIG.md)
(per-player), this is a **SERVER** config: NeoForge auto-syncs it to every
connecting client, so whatever the server decides is what every player sees.
Disabling an origin here genuinely hides it from remote clients' selection
screens: the server is the single source of truth.

The file is created with defaults on first launch. Edit it while the game/server
is closed. Resolution follows standard NeoForge server-config rules:

- `config/neoorigins/content.toml`: the global default for this install.
- `<world>/serverconfig/neoorigins/content.toml`: a per-world copy, if present,
  **overrides** the global file for that world.

> **Minecraft 26.3.** NeoForge 26.3 renamed the config types: SERVER is now
> SYNCED and COMMON is now LOCAL, with the same load and sync behaviour. The
> per-world copy moved with it, to `<world>/syncedconfig/neoorigins/content.toml`.
> The first time a world starts on 26.3, NeoOrigins copies its
> `serverconfig/neoorigins/content.toml` (or the pre-2.2.2
> `serverconfig/neoorigins-server.toml`) into `syncedconfig/` and applies it
> that same boot. An existing `syncedconfig/` copy is never overwritten, and
> the 26.2 `serverconfig/neoorigins/content.toml` stays where it is, so the
> world still opens on an older build.

These values are read only after a world is active, so they do not participate in
the boot-time datapack reload.

> **Config split (2.2.2).** `content.toml` is one of four config files, and the
> only true SERVER config among them: its siblings
> [`gameplay.toml`](GAMEPLAY_CONFIG.md), [`admin.toml`](ADMIN_CONFIG.md) and
> `power_overrides.toml` are COMMON configs (loaded early, not synced to
> clients, no per-world copy). This doc covers `content.toml` only.

## Global toggles

These four live at the top of the file, outside any section.

| Option | Type | Default | Description |
|---|---|---|---|
| `disable_resource_bars` | bool | `false` | Hide all resource bars (mana, stamina, rage, etc.) from the HUD globally. Any active power that would normally cost a resource instead falls back to costing **hunger**: `resource_cost_amount` food points are deducted. Useful for packs that prefer vanilla hunger as the universal ability cost. |
| `disable_night_vision` | bool | `false` | Suppress the `minecraft:night_vision` effect globally. Any power that would apply it (the `neoorigins:night_vision` / `persistent_effect` path) no longer brightens the screen. **Only** night vision is stripped; other effects on the same power still apply. For packs that want darkness to stay threatening. |
| `disable_enhanced_vision` | bool | `false` | Withhold the `neoorigins:enhanced_vision` capability globally. The client-side low-light brightness boost (cat eyes, salamander, oculus drone, etc.) never turns on because the capability is never sent to clients. **Independent** of `disable_night_vision`: these are two separate mechanisms and can be toggled separately. |
| `classic_mode` | bool | `false` | Offer only the original Origins mod's origins and the original Origins: Classes classes, with their original power sets. See [Classic mode](#classic-mode) below. |

The two vision toggles are intentionally separate: `disable_night_vision` targets
the vanilla potion-effect brightening, while `disable_enhanced_vision` targets
NeoOrigins' own brightness-curve capability. Turn off one, both, or neither.

Both outrank the player-facing **Toggle Night Vision** keybind. That key is a
personal preference switch and only ever turns night vision *off* on top of what
the server already permits: on a server with `disable_night_vision = true`,
pressing it reports that night vision is disabled and changes nothing. See the
[player toggle](POWER_TYPES.md#player-toggle) section for the player-side half.

## `[origins]`: per-origin enable toggles

The built-in origins each have a boolean here, defaulting to `true`. Set one to
`false` to hide that origin from the selection screen.

| Behaviour | Detail |
|---|---|
| Key | The origin's path id (e.g. `merling`, `automaton`, `stoneguard`). |
| Keys generated | `human`, `merling`, `avian`, `blazeling`, `elytrian`, `enderian`, `arachnid`, `shulk`, `phantom`, `feline`, `golem`, `caveborn`, `sylvan`, `draconic`, `revenant`, `tiny`, `abyssal`, `voidwalker`, `stoneguard`, `verdant`, `umbral`, `inchling`, `sporeling`, `frostborn`, `strider`, `siren`, `piglin`, `hiveling`, `cinderborn`, `sculkborn`, `enderite`, `necromancer`, `gorgon`, `automaton`, `kraken`, `warden`, `dwarf`, `breeze`, `vampire`, `air_mage`, `darkness_mage`, `earth_mage`, `fire_mage`, `gravity_mage`, `water_mage`, `monster_tamer`, `skeleton`, `slime`, `wraith`, `asura`, `windwalker`, `qi_cultivator`, `golden_body`, `iron_monk`, `sword_immortal` (55 keys). The Dragon Survival origins (`cave_dragon`, `forest_dragon`, `sea_dragon`) have **no** toggle here. |
| Default | `true` (enabled). |
| When disabled | Hidden from the selection screen, but still **assignable** via `/neoorigins set <player> neoorigins:<id>`. The origin stays registered. |
| Scope | Matched by id: a toggle applies to `neoorigins:<key>` only. Origins in any other namespace, whether from datapacks or `originpacks/`, are **not** affected by these toggles. |

```toml
[origins]
    human = true
    merling = true
    automaton = false   # hidden from the picker, still /set-able
```

## `[classes]`: per-class enable toggles

The same mechanism for the built-in classes (`class_warrior`, `class_archer`,
`class_cleric`, …), each defaulting to `true`.

| Behaviour | Detail |
|---|---|
| Key | The class's path id (e.g. `class_warrior`). |
| Keys generated | `class_warrior`, `class_archer`, `class_miner`, `class_beastmaster`, `class_explorer`, `class_sentinel`, `class_herbalist`, `class_scout`, `class_berserker`, `class_titan`, `class_rogue`, `class_lumberjack`, `class_blacksmith`, `class_cook`, `class_merchant`, `class_cleric`, `class_nitwit`, `class_fisher`, `class_mason`, `class_paladin` (20 keys). |
| Default | `true` (enabled). |
| When disabled | Hidden from the class selection screen, the same way as a disabled origin: the class stays registered and is still assignable via `/neoorigins set`. |
| All disabled | If **every** class is set to `false`, the class selection screen is skipped entirely. |

```toml
[classes]
    class_warrior = true
    class_nitwit = false
```

## Classic mode

`classic_mode = true` turns NeoOrigins into the original Origins experience:

- **Origins:** only Human, Merling, Avian, Blazeborn (`blazeling`), Elytrian, Enderian, Arachnid, Shulk, Phantom and Feline are offered. Every other built-in origin is hidden, the Dragon Survival dragons included.
- **Classes:** only the fourteen Origins: Classes classes are offered: Archer, Beastmaster, Blacksmith, Cleric, Cook, Explorer, Farmer, Lumberjack, Merchant, Miner, Nitwit, Rancher, Rogue and Warrior. Farmer and Rancher ship for classic mode: they stay registered either way, but outside the mode they are hidden from the picker, the way a class switched off by its own toggle is.
- **Powers:** each of those origins and classes swaps its NeoOrigins power list for one modelled on the original mod. The classic definitions live in `data/neoorigins/origins/classic_origins/<id>.json`, one full origin body per id, and replace `neoorigins:<id>` while the mode is on. They have no evolution tiers. A datapack can override a classic definition by shipping the same path.

| Behaviour | Detail |
|---|---|
| Toggles | Classic mode hides every non-classic built-in whatever its `[origins]`/`[classes]` toggle says. A classic origin or class whose own toggle is `false` stays hidden. |
| Datapack origins | Not affected. Origins and classes from datapacks or `originpacks/` stay offered in classic mode. |
| Existing players | A player who already holds an origin or class that classic mode hides keeps it, with its powers, until they change it (an orb, `/neoorigins set`, a reset). This is the same as disabling it with its toggle. A player holding a classic origin gets its classic powers. |
| Switching mid-session | Editing the file while the server runs applies at once: online players' classic origins swap power sets (powers shared by both versions keep their state), and every client's picker is resent. Restarting is not needed. |
| `/neoorigins list` | Marks hidden entries `[disabled]` and says `(classic mode)` in the header while it is on. |

```toml
classic_mode = true
```

Source of truth: `config/ContentTogglesConfig.java`.
