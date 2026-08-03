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

These values are read only after a world is active, so they do not participate in
the boot-time datapack reload.

> **Config split (2.2.2).** `content.toml` is one of four server config files.
> Its siblings are `gameplay.toml`, `admin.toml`, and `power_overrides.toml`.
> This doc covers `content.toml` only.

## Global toggles

These three live at the top of the file, outside any section.

| Option | Type | Default | Description |
|---|---|---|---|
| `disable_resource_bars` | bool | `false` | Hide all resource bars (mana, stamina, rage, etc.) from the HUD globally. Any active power that would normally cost a resource instead falls back to costing **hunger**: `resource_cost_amount` food points are deducted. Useful for packs that prefer vanilla hunger as the universal ability cost. |
| `disable_night_vision` | bool | `false` | Suppress the `minecraft:night_vision` effect globally. Any power that would apply it (the `neoorigins:night_vision` / `persistent_effect` path) no longer brightens the screen. **Only** night vision is stripped: other effects on the same power still apply. For packs that want darkness to stay threatening. |
| `disable_enhanced_vision` | bool | `false` | Withhold the `neoorigins:enhanced_vision` capability globally. The client-side low-light brightness boost (cat eyes, salamander, oculus drone, etc.) never turns on because the capability is never sent to clients. **Independent** of `disable_night_vision`: these are two separate mechanisms and can be toggled separately. |

The two vision toggles are intentionally separate: `disable_night_vision` targets
the vanilla potion-effect brightening, while `disable_enhanced_vision` targets
NeoOrigins' own brightness-curve capability. Turn off one, both, or neither.

Both outrank the player-facing **Toggle Night Vision** keybind. That key is a
personal preference switch and only ever turns night vision *off* on top of what
the server already permits. On a server with `disable_night_vision = true`,
pressing it reports that night vision is disabled and changes nothing. See the
[player toggle](POWER_TYPES.md#player-toggle) section for the player-side half.

## `[origins]`: per-origin enable toggles

Every built-in origin has a boolean here, defaulting to `true`. Set one to
`false` to hide that origin from the selection screen.

| Behaviour | Detail |
|---|---|
| Key | The origin's path id (e.g. `merling`, `automaton`, `stoneguard`). |
| Default | `true` (enabled). |
| When disabled | Hidden from the selection screen, but still **assignable** via `/neoorigins set <player> neoorigins:<id>`. The origin stays registered. |
| Scope | Only built-in `neoorigins:*` origins listed in this section. Origins added by datapacks or `originpacks/` are **not** affected by these toggles. |

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
| Default | `true` (enabled). |
| When disabled | Removed from the class selection screen after data loading. |
| All disabled | If **every** class is set to `false`, the class selection screen is skipped entirely. |

```toml
[classes]
    class_warrior = true
    class_nitwit = false
```

Source of truth: `config/ContentTogglesConfig.java`.
