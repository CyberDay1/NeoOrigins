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
| `picker_layout` | enum | `TWO_PANEL` | Arrangement of the origin/class selection screen. `TWO_PANEL` (default): scrolling list on the left, details on the right. `CAROUSEL`: one origin at a time with prev/next arrows, closest to the original Origins mod's chooser. `GRID`: a paged wall of origin cards, click a card for its details. This is layout only: colours and font stay under `theme_override` / `classic_picker_style`, and any skin works with any layout. See below. |
| `show_origin_editor` | bool | `false` | Show the in-game Origin Editor button on the origin info screen for **all** players, not just those in Creative. The editor is a pack-authoring tool, creative-only by default. Enable if you author origins in survival or want testers to reach it without `/gamemode`. |
| `default_sort` | enum | `MANUAL` | Initial sort order for the origin selection / info screens, used until you cycle the on-screen sort button (your cycled choice then wins for the rest of the session). Values: `MANUAL` (the author-set `order` field ascending, alphabetical tie-break), `CLASS` (grouped by mod/namespace, alphabetical within), `NAME_ASC` (flat alphabetical), `NAME_DESC` (flat reverse-alphabetical), `IMPACT_ASC` (by origin impact: none → low → medium → high). |

### Choosing a picker layout

`TWO_PANEL` is what the picker has always looked like: the origins scroll past in
a list on the left, and whichever one you have highlighted fills the detail panel
on the right.

`CAROUSEL` shows one origin at a time on a single centred panel, with `<` and `>`
arrows (or the left/right arrow keys) to page through the layer's origins. Whatever is on
screen is what Confirm applies to, so browsing and selecting are the same action.
This is the layout closest to the original Origins mod's chooser — but it is a
NeoOrigins screen shaped like that one, not a copy of it: it is wider so the power
list fits, and it is drawn in whichever UI theme is active rather than in the old
dirt-background window. Search, the sort cycle and Random / Back / Confirm all
work exactly as they do on the two-panel screen.

`GRID` lays the layer's origins out as a wall of cards, each carrying the origin's
icon, name and impact dots. It fits as many whole cards as your window allows and
pages the remainder: `<` / `>` beneath the wall, the left/right arrow keys, or the
scroll wheel. A card is far too small to hold a description, so clicking one both
selects it and opens the same full detail read the other two layouts use. Back
(or ESC) returns to the wall from there, and only once you are on the wall does
Back step to the previous layer. Random / Back / Confirm sit in the same three
places on both views, so the buttons never move under your cursor. Search and the sort
cycle work as they do elsewhere; note that `CLASS` sort loses its per-mod headers
here, since a wall of cards has nowhere to put them.

Layout is a separate axis from skin. Colours and font stay under `theme_override`
/ `classic_picker_style`, so any skin works with any layout.

## `[hud]`: heads-up display

| Option | Type | Default | Description |
|---|---|---|---|
| `hide_hud_bars` | bool | `true` | Hide the vanilla hunger / air HUD bars for origins that don't consume them (e.g. Automaton hunger; Merling / Kraken / Automaton air). Turn off to keep vanilla bars visible regardless of origin. |
| `show_cooldown_countdown` | bool | `true` | Master switch for the numeric seconds drawn on cooldown icons. Packs opt individual powers in via `"cooldown_countdown": true`; set this to `false` to suppress **all** countdown numbers on this client. |
| `cooldown_countdown_opacity` | int (0–100) | `70` | Opacity, in percent, of the countdown seconds drawn on cooldown icons. `100` = fully opaque, `0` = invisible. Values below 5 render as 5 (the font renderer drops text below that). |
| `hud_ability_display` | enum | `COOLDOWNS_AND_TOGGLES` | What the ability HUD cluster shows besides live cooldowns. `COOLDOWNS_AND_TOGGLES`: cooldown slots only while recharging, plus icon-bearing toggle powers (bright = on, dimmed = off). Idle non-toggle icons stay hidden unless the power declares `"always_show_icon": true` or you enable `always_show_ability_icons` below. `ALL_ACTIVE_ABILITIES`: every keybind ability with an icon keeps a persistent slot (full-bright while idle, cooldown sweep while recharging, bright/dim for toggles). |
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

## `[compat]`: cross-mod behaviour

| Option | Type | Default | Description |
|---|---|---|---|
| `dragon_species_screens` | enum | `DEFER` | What to do when Dragon Survival opens one of its own dragon-**species** screens (the altar / species-choice popup) while the NeoOrigins origin picker is on screen. `DEFER` (default): hold DS's screen back and open it as soon as the picker is finished, so neither screen is lost. `SUPPRESS`: cancel DS's species screens outright, making the origin picker the only way to choose or change a dragon species. `ALLOW`: never interfere — whichever screen opens last replaces the other. Only takes effect when Dragon Survival is installed. |

### Why the default is `DEFER`

Both mods open a screen when you join a world: NeoOrigins because the server
tells the client to show the origin picker, and Dragon Survival when its own
`start_with_dragon_choice` is on. Whichever one lands second calls `setScreen`
and replaces the first, which is what knocks players out of the origin picker.

`DEFER` resolves the race in the origin picker's favour without throwing DS's
screen away: the species choice is queued and reopened the moment the picker
closes, whether you confirmed, escaped or abandoned it. The race is handled in
both directions, so it does not matter which mod happens to win.

A species screen you open yourself — an altar block, the dragon inventory button
(`allow_dragon_choice_from_inventory`), or a command — is never intercepted under
`DEFER` or `ALLOW`, because no picker is on screen at the time. DS's appearance
editor, skins, abilities and inventory are never intercepted under any value.

Server operators can also set DS's own `start_with_dragon_choice = false`, but
that is a pack-wide decision; this option is per-client and needs no server
change.
