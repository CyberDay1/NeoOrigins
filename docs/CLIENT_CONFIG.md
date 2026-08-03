# NeoOrigins Client Config Reference

Per-client (not per-world, not server-controlled) options live in the TOML file
`config/neoorigins/client.toml` in your game directory. They affect only your
own client (UI skin, HUD layout, and how many named hotkeys are registered)
and are safe to differ from player to player on the same server.

The file is created with defaults on first launch. Edit it while the game is
closed, or change values in-game via the mod's config screen (Mods → NeoOrigins
→ Config) where exposed.

## Built-in keybinds

These are ordinary keybinds, not config values. Rebind any of them in
*Controls → Key Binds → NeoOrigins*. Defaults are chosen to stay clear of
vanilla bindings; slots left unbound are for players who want them.

| Binding | Default | What it does |
|---|---|---|
| Skill 1 | **V** | Fires the first active ability granted by your origin. |
| Skill 2 | **G** | Second active ability. |
| Skill 3 | **N** | Third active ability. |
| Skill 4 | **B** | Fourth active ability. |
| Skill 5 | unbound | Fifth active ability. |
| Skill 6 | unbound | Sixth active ability. |
| Class Skill | **H** | Fires your class's active ability, separate from the six origin slots. |
| View Origin Info | **O** | Opens the origin/class info screen. |
| Toggle Night Vision | **K** | Personal on/off switch for origin-granted night vision. |
| Edit HUD | unbound | Opens HUD layout editing. |
| Open Origin Creator | unbound | Pack-authoring tool; server-gated. |
| Open Mob Origin Creator | unbound | Pack-authoring tool; server-gated. |

**Toggle Night Vision** exists because some origins (Caveborn, Feline, Phantom
and friends) light the world up permanently, which a few players find worse than
the dark. Night vision starts **on**, so if you never press K you never notice
the key. Pressing it toggles only *your* view; it does not touch the power, the
origin, or anyone else, and it survives relogging.

It cannot turn night vision *on* where the server hasn't granted it. If a server
sets `disable_night_vision = true` (see
[CONTENT_CONFIG.md](CONTENT_CONFIG.md#global-toggles)), pressing K just reports
that night vision is disabled there.

Pack-declared abilities beyond the six origin slots land in a second category,
*NeoOrigins (Hotkeys)*. See [`[hotkeys]`](#hotkeys) below.

## `[ui]`: selection / info screens

| Option | Type | Default | Description |
|---|---|---|---|
| `theme_override` | string | `""` | Force a specific UI theme id (e.g. `neoorigins:parchment`, `examplepack:dark_woods`) regardless of what the server's datapacks declared. Only takes effect when the named theme is actually loaded. Empty = follow the server / datapack-declared theme. See [THEMING.md](THEMING.md). |
| `classic_picker_style` | bool | `false` | Revert the origin/class selection screens to the original flat high-contrast skin (dark panels, light text, vanilla font) instead of the parchment scroll. Enable if the parchment theme's brown-on-paper text is hard to read. |
| `show_origin_editor` | bool | `false` | Show the in-game Origin Editor button on the origin info screen for **all** players, not just those in Creative. The editor is a pack-authoring tool, creative-only by default. Enable if you author origins in survival or want testers to reach it without `/gamemode`. |
| `default_sort` | enum | `CLASS` | Initial sort order for the origin selection / info screens, used until you cycle the on-screen sort button (your cycled choice then wins for the rest of the session). Values: `CLASS` (grouped by mod/namespace, alphabetical within), `NAME_ASC` (flat alphabetical), `NAME_DESC` (flat reverse-alphabetical), `IMPACT_ASC` (by origin impact: none → low → medium → high). |

## `[hud]`: heads-up display

| Option | Type | Default | Description |
|---|---|---|---|
| `hide_hud_bars` | bool | `true` | Hide the vanilla hunger / air HUD bars for origins that don't consume them (e.g. Automaton hunger; Merling / Kraken / Automaton air). Turn off to keep vanilla bars visible regardless of origin. |
| `show_cooldown_countdown` | bool | `true` | Master switch for the numeric seconds drawn on cooldown icons. Packs opt individual powers in via `"cooldown_countdown": true`; set this to `false` to suppress **all** countdown numbers on this client. |
| `cooldown_countdown_opacity` | int (0–100) | `70` | Opacity, in percent, of the countdown seconds drawn on cooldown icons. `100` = fully opaque, `0` = invisible. Values below 5 render as 5 (the font renderer drops text below that). |
| `hud_ability_display` | enum | `ALL_ACTIVE_ABILITIES` | What the ability HUD cluster shows besides live cooldowns. `ALL_ACTIVE_ABILITIES`: every keybind ability with an icon keeps a persistent slot (full-bright while idle, cooldown sweep while recharging, bright/dim for toggles). `COOLDOWNS_AND_TOGGLES`: cooldown slots only while recharging, plus icon-bearing toggle powers (bright = on, dimmed = off). |
| `always_show_ability_icons` | bool | `false` | Force every icon-bearing ability to stay on the HUD cluster even while off cooldown (as if each power declared `"always_show_icon": true`). Default `false`: idle cooldown icons disappear unless the power itself opts in. |

The cooldown icon and countdown fields that powers declare (`cooldown_icon`,
`cooldown_countdown`, `always_show_icon`) are documented in
[POWER_TYPES.md](POWER_TYPES.md); the switches above are the per-client
overrides for them.

## `[hotkeys]`

| Option | Type | Default | Description |
|---|---|---|---|
| `pool_size` | int (1–256) | `32` | Number of named-keybind slots registered at client startup. Each pack-declared `"key": "translation.key.id"` on an active power consumes one slot; the Controls screen shows that many unassigned "Hotkey N" entries. Increase if your packs declare more than 32 distinct keys. This is a client setting: keybinds register at startup, so the slot count is chosen locally and cannot be dictated by the server. |
| `slot_defaults` | list of strings | `[]` | Default physical keys for named-hotkey pool slots, so a modpack can ship pre-bound hotkeys instead of leaving every slot unbound. Each entry is `"N=key.keyboard.X"`, where `N` is the 1-indexed slot (matching a power's numeric `"key": N`) and `key.keyboard.X` is a vanilla input id (e.g. `"1=key.keyboard.r"`, `"2=key.keyboard.z"`, `"3=key.mouse.4"`). A bad entry is logged and skipped, so one typo can't break the whole pool. This is a client setting applied at key registration; a datapack cannot set it, because keybinds register before datapacks load. Players can still rebind any slot in Controls; this only sets the default. |

### Pre-binding pool slots for a modpack

Leaving every "Hotkey N" row unbound is fine for a single pack, but a curated
modpack often wants its abilities to work out of the box. `slot_defaults` lets
the pack author assign a default physical key to any pool slot:

```toml
[hotkeys]
pool_size = 32
slot_defaults = ["1=key.keyboard.r", "2=key.keyboard.z", "3=key.mouse.4"]
```

Slot numbers line up with the numeric `"key": N` shorthand on a power (see
[API → Named keybinds](API.md#named-keybinds)): a power authored with `"key": 1`
lands on "Hotkey 01", which `"1=key.keyboard.r"` pre-binds to **R**. The player
can rebind any of these in *Controls → Key Binds → NeoOrigins (Hotkeys)*; the
config value only supplies the starting binding.

Why here and not in the datapack: Minecraft fixes a key's default the moment it
registers the KeyMapping, which happens at client startup, before any datapack
(or the server) has loaded. So a pre-bound default has to come from the client,
which is what this option is for.
