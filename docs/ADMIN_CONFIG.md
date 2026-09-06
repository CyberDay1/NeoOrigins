# NeoOrigins Admin Config Reference

Server-operator policy knobs live in the TOML file
`config/neoorigins/admin.toml`: the command-power blacklist, command access,
per-power dimension restrictions, the global taming/scare entity blacklist,
compat origin filtering and debug flags.

This is a **COMMON** config, not a server config: it loads early enough to be
readable during the boot-time datapack reload, it is **not** synced to
clients, and there is **no** per-world `<world>/serverconfig/` override
(unlike [`content.toml`](CONTENT_CONFIG.md)). Every rule here is enforced
server-side.

The file is created with defaults on first launch. Edit it while the
game/server is closed.

> **Config split (2.2.2).** `admin.toml` is one of four config files.
> Its siblings are [`content.toml`](CONTENT_CONFIG.md) (SERVER: synced,
> per-world overridable), [`gameplay.toml`](GAMEPLAY_CONFIG.md) (COMMON:
> gameplay tuning) and `power_overrides.toml` (COMMON: per-power stat
> overrides). This doc covers `admin.toml` only.

## Debug flags

These live at the top of the file, outside any section (they migrate 1:1
from the legacy `neoorigins-common.toml`, where they were also top-level).

| Option | Type | Default | Description |
|---|---|---|---|
| `debug_power_loading` | bool | `false` | Log per-namespace power counts after each data reload. Useful for addon and datapack authors debugging load issues. |
| `debug_compat_actions` | bool | `false` | Send in-game chat feedback when a compat power action resolves to no-op (unsupported action type). Useful for pack authors debugging why their imported powers aren't working. |
| `debug_hud` | bool | `false` | Diagnostic logging for the ability HUD cluster and flight-ability syncing: logs hover-state changes on the cooldown cluster (client) and every server-side grant/clear/accept of flight abilities. One line per state change, not per tick. Leave off in normal play. |

## `[dimension_restrictions]`: per-power dimension deny lists

Powers listed here are suppressed while the player is in the specified
dimension(s).

| Behaviour | Detail |
|---|---|
| Key | `rules`, a list of strings. Default `[]`. |
| Format | `"<power_id> = <dimension1>, <dimension2>, ..."` |
| Example | `"neoorigins:elytrian_flight = minecraft:the_nether, minecraft:the_end"` |
| Validation | Each rule must contain an `=`, and the power id before it must contain a `:`. Invalid rules are rejected by the config loader. |
| Reloads | The parsed rule set is rebuilt whenever the list changes, so `/reload`-driven config refreshes take effect without a restart. |

```toml
[dimension_restrictions]
    rules = ["neoorigins:elytrian_flight = minecraft:the_nether, minecraft:the_end"]
```

## `[compat_filtering]`: hide broken addon origins

| Option | Type | Default | Range | Description |
|---|---|---|---|---|
| `min_power_ratio` | double | `0.5` | 0.0 – 1.0 | Origins from addon mods are hidden if fewer than this fraction of their powers loaded successfully. Default `0.5`: origins with less than 50% of powers working are hidden. Set to `0.0` to show all origins regardless. |

## `[commands]`: command access tuning

| Option | Type | Default | Description |
|---|---|---|---|
| `public_origin_get` | bool | `true` | Allow non-OP players to run `/neoorigins get <player>` to look up another player's origin. Operators (permission level 2) can always run it regardless of this setting. Set to false to make origins visible only to staff. |

## `[command_powers]`: the command-power blacklist

Datapack powers can run arbitrary server commands (the `command` /
`execute_command` actions, the raycast `command_along_ray` / `command_at_hit`
extensions, and the `command` condition). Without a guard, a pack could ship
`/op @s` and silently escalate any player to operator. This list names
command roots that are refused at execution time regardless of the power's
permission level: a blocked command is refused and logged instead of run.

| Behaviour | Detail |
|---|---|
| Key | `command_power_blacklist`, a list of command roots. |
| Default | `op`, `deop`, `ban`, `ban-ip`, `pardon`, `pardon-ip`, `kick`, `whitelist`, `stop`, `save-all`, `save-off`, `save-on`, `setidletimeout`, `debug`, `perf`, `datapack`, `reload` |
| Matching | Case-insensitive on the effective command root. `execute ... run X` is unwrapped so `X` is what gets checked: a blacklisted command can't be smuggled behind an execute chain. |
| Format | List the root only, no slash. |

## `[entity_exclusions]`: taming and scare blacklist

Global entity exclusions for the mob-control power family: `tame_mob`,
`scare_entities` and `mobs_ignore_player`. Entities listed here can never be
tamed, scared, or made to ignore a player by **any** power, on top of the
hardcoded boss-tier exclusion and any per-power `entity_blacklist`.

| Behaviour | Detail |
|---|---|
| Key | `tame_scare_entity_blacklist`, a list. Default `[]`. |
| Format | Entity ids (e.g. `"minecraft:elder_guardian"`) or entity-type tags (e.g. `"#mymod:untameable"`). |
| Always excluded | The Warden, Ender Dragon and Wither are hardcoded exclusions and need not be listed. |

Source of truth: `config/AdminConfig.java`.
