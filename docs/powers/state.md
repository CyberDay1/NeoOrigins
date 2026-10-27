---
title: "Resources, toggles & state"
parent: "Power Types"
grand_parent: "DSL Reference"
nav_order: 3
permalink: /powers/state
---

# Resources, toggles & state

Values a power keeps per player: resource bars, hidden counters, true/false flags and entity sets.

Fields every power accepts (`name`, `hidden`, `power_condition`, ...) are on [Power basics](../concepts/power-basics.md). See also: [Costs & upkeep](../concepts/costs-and-upkeep.md), [Toggles](../concepts/toggles.md), [Make a toggle](../guides/toggles.md).

Types on this page: [`resource`](#neooriginsresource) · [`variable`](#neooriginsvariable) · [`toggle`](#neooriginstoggle) · [`entity_set`](#neooriginsentity_set).

---

## `neoorigins:resource`

A named, persistent, HUD-visible resource bar. Values are stored per-player and synced to the client for rendering. Supports regeneration with conditions, threshold actions when min/max are hit, and compatibility with the Origins `change_resource` / `resource` condition system.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `min` | int | no | `0` | Minimum resource value |
| `max` | int | no | `100` | Maximum resource value |
| `start_value` | int | no | `max` | Initial value when granted |
| `regen_rate` | int | no | `0` | Amount regenerated per interval (0 = no regen) |
| `regen_interval` | int | no | `20` | Ticks between regen ticks |
| `regen_condition` | EntityCondition | no | always-true | Condition for when regeneration occurs |
| `min_action` | EntityAction | no | noop | Action run once each time the value drops to `min` (not every tick while it stays there) |
| `max_action` | EntityAction | no | noop | Action run once each time the value rises to `max` (not every tick while it stays there) |
| `hud_render` | object | no | — | HUD display settings (see below) |
| `hidden` | bool | no | `false` | Hides the power from the origin menu and its bar from the HUD. Set `hud_render.should_render` to `true` to keep the bar on the HUD while the menu row stays hidden |
| `backing` | string | no | `""` | Optional external pool that backs the bar's value instead of the internal store. Only `irons_spellbooks:mana` is supported (see below); when set, `min`/`max` are ignored and the bar auto-scales to Iron's live max mana. Empty = internally stored. |

**`backing`: bind the bar to Iron's Spells mana**

Set `"backing": "irons_spellbooks:mana"` to make the bar's value **read from and write to the player's [Iron's Spells 'n Spellbooks](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks) mana pool** rather than NeoOrigins' own per-player store. The mana pool stays authoritative:

- **Reads** (the HUD bar, the `resource` condition, `resource_cost` gating) observe the live mana value.
- **Writes** are **additive-only**: `change_resource` adds its `change` amount (positive to grant, negative to drain), `regen_rate` adds per interval, and `resource_cost` deductions subtract. All of these add a delta to the pool; NeoOrigins never overwrites mana absolutely (that would fight Iron's own regen and casting bookkeeping).
- **Absolute sets are ignored.** `set_resource` and `change_resource` with `"operation": "set"` are no-ops on a mana-backed bar (logged once). Use additive `change_resource` instead.
- **`min`/`max` are optional and ignored: the bar auto-scales.** A mana-backed bar uses `min = 0` and `max =` Iron's **live** max mana, which is the `MAX_MANA` attribute (it moves with gear, level, and effects), so you don't declare a static scale. Iron's own mana bar reads the same attribute, so the two bars fill identically. Any author `min`/`max` on a mana-backed power is accepted but has no effect (they still matter for ordinary, non-backed resources). A drain is floor-clamped so mana never goes below 0.
- **The dynamic max is pushed live.** Because the max can change mid-game (e.g. a gear swap that grants more max mana), the periodic value sync carries the current max, so the HUD re-scales without waiting for a full re-sync.
- **Iron's Spells is a soft dependency.** If it isn't installed, the bar reads empty and writes do nothing (logged once); it does **not** fall back to an internal value. Gate the origin/power with `"required_mods": ["irons_spellbooks"]` if the bar should only exist when Iron's is present.

Because Iron's changes mana out from under the game (regen, spellcasting), a mana-backed bar re-syncs its value (and live max) to the client every 10 ticks so the HUD tracks the pool live.

```json
{
  "type": "neoorigins:resource",
  "backing": "irons_spellbooks:mana",
  "hud_render": {
    "label": "Mana",
    "color": "#55AAFF"
  },
  "name": "Arcane Reserve",
  "description": "Your Iron's Spells mana, shown as an origin resource bar."
}
```

> `min`/`max` are omitted above on purpose: a mana-backed bar auto-scales to Iron's live max mana.

**Multiplayer and `/reload`.** Each player's bar and value are their own. When one player loses the power (an origin change, a revoke), only that player's bar goes; every other holder keeps theirs, and a mana-backed bar is sent only to players who hold the power. After `/reload`, online players get their bars back straight away with their stored values intact, instead of waiting for a relog. Up to 2.2.27 neither held: one player's revoke took the bar off every other holder's HUD, a mana-backed bar was sent to every player, and a reload dropped bars until the holder relogged.

**`hud_render` object:**

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `label` | string | no | `"Resource"` | Display label on the HUD bar |
| `color` | string | no | `"#55AAFF"` | Bar color in `#RRGGBB` or `#AARRGGBB` hex format. Any other value, such as `#FFF`, falls back to `#55AAFF` |
| `should_render` | bool | no | `true` | When `false`, hides the bar (Origins compat). When set to `true`, the bar shows even on a power with `"hidden": true`, so the resource stays out of the origin menu but keeps its HUD bar. Left unset, the bar follows `hidden` |
| `text_color` | string | no | `"#CCCCCC"` | Color of the bar's label and value readout, in `#RRGGBB` or `#AARRGGBB` hex. Any other value falls back to `#CCCCCC`, the default light grey |
| `text_outline` | bool | no | `false` | Draws a 1px dark outline around the label and value readout, so they stay readable over any background |
| `always_render` | bool | no | `false` | Keep the bar on-screen even when full. By default the HUD hides a full bar (the Apoli convention), but only after the resource has been spent once: a bar that starts at max stays visible until its value first drops, so a new player can see it is there. The client forgets that first spend on each join, respawn and origin change, so the bar shows again at those points until it is next spent. Set this to keep the bar visible all the time. |
| `animated` | string | no | — | Id of a bar style (an FX preset), e.g. `"neoorigins:fire"`. When set and the preset is loaded, the bar draws in that style instead of the flat `color` fill. See **Bar styles** below for the built-in ones. |
| `tint` | string | no | — | Hex color (`#RRGGBB` or `#AARRGGBB`) multiplied over the style's art, so one texture can be recolored per power. Ignored when `animated` is unset. On `neoorigins:tinted`, `color` is used when `tint` is not set. |
| `tint_end` | string | no | — | Second hex color for a framed style that does not scroll, such as `neoorigins:tinted`: the fill shades from `tint` at the left end of the bar to `tint_end` at the right. With no `tint`, it starts from `color` on `neoorigins:tinted` (or any style whose preset sets `tint_from_color`), and from the untinted art otherwise. Ignored on a scrolling style. When unset, the bar is one flat tint. |
| `dynamic` | bool | no | `false` | Stack this bar above every bar that does not set it. Dynamic bars form their own group on top of the static group, each group still in resource id order, so a bar that comes and goes never shuffles the fixed bars under it. |

**Example: a bar with no menu row**

```json
{
  "type": "neoorigins:resource",
  "hidden": true,
  "hud_render": {
    "label": "Heat",
    "color": "#FF6A00",
    "should_render": true,
    "text_color": "#FFFFFF",
    "text_outline": true
  }
}
```

The power is left out of the origin menu, and the bar still draws, with a white label and value outlined in black.

**Bar order and stacking.** Bars stack upwards from the hotbar in **resource id order**: sorted by the power id that owns the bar, lowest id nearest the hotbar. The order does not depend on the order the powers were granted, so a pack can predict where its bar will sit and describe that to a player. Adding a resource inserts its bar at the position its id sorts to, moving the bars above it up one slot.

Only bars that are drawn take a slot. A bar hidden by `should_render: false`, or by the full-bar hiding described under `always_render`, holds no slot, and neither does one the player has dragged to a saved position in the HUD editor: those draw where they were placed, and the automatic stack closes up behind them.

Each slot is as tall as what its bar draws: the bar itself, the `label` line above it and the value line below it, with a small gap between one bar's value line and the label of the bar above. A taller style takes a taller slot, so a stack of the built-in framed styles steps 29 pixels and a bar with no `animated` style 26. A bar with an empty `label` (`"label": ""`) draws no name line and its slot shrinks to match, but never below the 16 pixels every bar used before 2.2.30. The value line always draws.

Up to 2.2.29 none of this held. The vertical order followed an internal hash of the resource key, stable for a fixed set of bars but not predictable and not controllable. A hidden or repositioned bar still reserved its slot, leaving a gap in the stack. And every slot was 16 pixels whatever the bar drew, so each bar's value line ran into the label of the bar stacked on top of it.

**Bar styles:**

NeoOrigins ships eight bar styles. Each one draws a grey frame with the fill inside it, cut off at the current level, and value ticks over the fill: a short, faint tick every 5 points and a taller, brighter one every 25. A set of ticks that would sit closer than 2 pixels apart is left out. Set one with `animated` in `hud_render`:

| Style | Fill | Scroll speed |
|---|---|---|
| `neoorigins:fire` | Red and orange flames. | 16 |
| `neoorigins:water` | Deep blue water with bubbles. | 10 |
| `neoorigins:wind` | White cloud wisps over grey. | 24 |
| `neoorigins:earth` | Brown earth. | 6 |
| `neoorigins:soul` | Cyan soul flames. | 8 |
| `neoorigins:void` | Purple veins. | 10 |
| `neoorigins:sculk` | Dark teal sculk with cyan sparks. | 12 |
| `neoorigins:tinted` | A plain grey fill, colored by `tint` or `color`. | does not move |

The scrolling styles drift left at a fixed speed per style, in pixels per second, set by the preset's `scroll_speed`. The strip moves with game time, so it stops while the game is paused. A player whose resource packs do not have the style sees the flat `color` fill instead.

**Coloring a bar with `neoorigins:tinted`.** The `tinted` fill is grey, so it takes on whatever color you multiply it by:

- **The field:** `tint` in `hud_render`. If `tint` is not set, the bar uses its `color` instead, so a bar that already has a `color` only needs `"animated": "neoorigins:tinted"` added.
- **The format:** a hex string, `#RRGGBB` or `#AARRGGBB`; the `#` is optional. The alpha byte is ignored, since the fill is always drawn opaque. The one exception is `#00000000`, which reads as no tint at all. Any other value, such as the shorthand `#FFF`, falls back to `#55AAFF`, and the same goes for `color` and `tint_end`.
- **How it multiplies:** each channel of the grey fill is multiplied by the same channel of the color. The fill's lightest pixels are white, so they come out as the color itself; its mid and dark greys come out as darker shades of it, which keeps the fill's shading. A bright, saturated color gives the clearest bar. Black gives a black bar.

- **A gradient:** add `tint_end` and the fill shades from `tint` (or `color`) at the left end of the bar to `tint_end` at the right. The gradient spans the whole bar rather than the filled part, so each point of the bar keeps its color as the value rises and falls. This only applies to a style that does not scroll.

`tint` works on the themed styles too, multiplying over their art in the same way, but those are drawn in color already, so it is mainly useful on `tinted`.

**Example: red bar**
```json
"hud_render": {
  "label": "Rage",
  "color": "#FF3030",
  "animated": "neoorigins:tinted"
}
```

**Example: teal bar, with `tint` set separately from `color`**
```json
"hud_render": {
  "label": "Tide",
  "color": "#2AA198",
  "animated": "neoorigins:tinted",
  "tint": "#30D5C8"
}
```
Here the bar draws in `#30D5C8`. `color` still matters: it is the flat fill shown to a player who does not have the style.

**Example: ice bar, shading from blue to frost white**
```json
"hud_render": {
  "label": "Stamina",
  "color": "#3FA9F5",
  "animated": "neoorigins:tinted",
  "tint_end": "#BFF0FF"
}
```
This is the Frostborn bar: `#3FA9F5` at the empty end, `#BFF0FF` at the full end.

**Example: fire bar**
```json
"hud_render": {
  "label": "Heat",
  "color": "#FF8800",
  "animated": "neoorigins:fire"
}
```

**Writing your own style.** A style is a resource-pack JSON under `assets/<namespace>/bar_fx/<name>.json`, looked up by the `animated` id. Because the bar render is entirely client-side, the preset and its textures ship in a resource pack: only the preset id travels in the power JSON. This is the built-in `fire` preset:

```json
{
  "texture": "neoorigins:textures/gui/bar_fx/fire.png",
  "mode": "scroll",
  "tile_width": 126,
  "tile_height": 6,
  "loop_width": 125,
  "scroll_speed": 16,
  "frame": "neoorigins:textures/gui/bar_fx/empty_bar.png",
  "frame_width": 71,
  "frame_height": 8
}
```

| Field | Type | Default | Description |
|---|---|---|---|
| `texture` | resource location | required | The fill texture. |
| `mode` | string | `"scroll"` | `scroll` moves the texture across the bar; `static` draws it in place. |
| `tile_width` / `tile_height` | int | `64` / `8` | Size of `texture` in pixels. |
| `loop_width` | int | `tile_width` | How many pixels of the texture one scroll loop covers. Use one less than `tile_width` when the texture's last column repeats its first, so that column is not shown twice at the seam. |
| `scroll_speed` | float | `24` | Pixels per second the texture drifts left. A negative value drifts right. Ignored when `mode` is `static`. |
| `frame` | resource location | — | Frame texture drawn behind the fill. When set, the bar is `frame_width` × `frame_height` and the fill sits inside it, one pixel in from each edge: a 71 × 8 frame holds a 69 × 6 fill. |
| `frame_width` / `frame_height` | int | `71` / `8` | Size of `frame` in pixels. |
| `tint_from_color` | bool | `false` | When the power sets no `tint`, multiply the fill by its `color`. `neoorigins:tinted` sets this. |
| `track_color` | hex color | `#AA000000` | Without a `frame`: ARGB backing drawn under the empty remainder of the bar. |
| `level_color` | hex color | `track_color` | Without a `frame`: ARGB backing under the filled portion; pick a brighter tone so the current level reads through transparent gaps in the texture. |

`track_color` and `level_color` take `#RRGGBB` or `#AARRGGBB`; a preset with any other value is skipped, with a warning in the log.

A preset without a `frame` draws the older 60 × 10 box with a backing color, and scales its texture to the box height. That box scrolls the whole scaled texture, so `loop_width` does not apply to it, and neither do `tint_from_color` and `tint_end`; `tint` still does.

See [animated_bar_artist_spec.md](../animated_bar_artist_spec.md) for texture-authoring guidance.

**Example: mana bar that regens while not in combat**
```json
{
  "type": "neoorigins:resource",
  "min": 0,
  "max": 100,
  "start_value": 100,
  "regen_rate": 1,
  "regen_interval": 20,
  "regen_condition": { "type": "neoorigins:out_of_combat" },
  "hud_render": {
    "label": "Mana",
    "color": "#55AAFF"
  },
  "name": "Mana Pool",
  "description": "Magical energy that regenerates outside of combat."
}
```

---

## `neoorigins:variable`

A named, persistent, **always-hidden** integer counter — a "local variable" for your origin. Unlike [`neoorigins:resource`](#neooriginsresource), a variable has no HUD bar, no regeneration, and no per-tick cost: it only changes when an action explicitly writes to it. It's the lightweight way to track state (combos, charges, kill counts, stages) and gate other abilities on it.

The counter is stored per-player and saved across login sessions. Its storage key is the **power's own id**, so it shares the same keyspace as resources: read it with the [`resource` condition](../CONDITIONS.md) and write it with [`change_resource` / `set_resource`](../ACTIONS.md), exactly as you would a resource. Because two powers can never share an id, a variable can never collide with a resource of the same name.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `start` | int | no | `0` | Value seeded when the power is granted. Also the value reads fall back to before the first write. `start_value` is accepted as an alias. Clamped into `min`/`max` when those are set. |
| `min` | int | no | unbounded | Lower clamp applied to additive (`change_resource`) writes. Omit for an unbounded counter. |
| `max` | int | no | unbounded | Upper clamp applied to additive (`change_resource`) writes. Omit for an unbounded counter. |

Declare more than one by adding a `neoorigins:variable` power for each counter. Because a read resolves the declared `start` value even before the power's own seed runs (declarations are registered when powers load), the counter reads correctly no matter where it sits in the origin's power list, but listing your variable powers first keeps intent clear ("declared at the start of the power stack").

**Example: a combo counter that powers a finisher**
```json
{
  "type": "neoorigins:variable",
  "start": 0,
  "min": 0,
  "max": 5,
  "name": "Combo"
}
```
Increment it on hit (via an `action_on_event` / `change_resource` of `{ "resource": "<this power's id>", "change": 1 }`), gate the finisher ability behind a `resource` condition (`comparison: ">=", compare_to: 5`), and reset it with `set_resource` once the finisher fires.

---

## `neoorigins:toggle`

A bare, stateless boolean power: purely a data-holder whose state lives in `CompatAttachments.toggleState()` keyed by the registered id. Unlike `active_ability`, `toggle` does not consume a keybind slot; it's a named boolean other powers gate on.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `default` | bool | no | `false` | Value reads see before the toggle has ever been flipped on this player. Set `true` for "on until flipped off." |

Read the current value with `neoorigins:power_active { power: "mypack:my_toggle" }`. Flip it with `neoorigins:toggle { power: "mypack:my_toggle" }` (optionally `value: true/false` to set explicitly).

**Example:**
```json
{
  "type": "neoorigins:toggle",
  "default": false,
  "hidden": true,
  "name": "Hunter's Mark",
  "description": "Internal flag gated by other powers."
}
```

`hidden: true` keeps the toggle out of the origin info panel, recommended for any purely-internal flag a player doesn't need to see listed.

> **This type takes no `key`.** A `key` on a `neoorigins:toggle` is ignored: the type is not an active power, so no keypress can reach it. To put a toggle on a hotkey, keep the flag here and give the key to a [`neoorigins:active_ability`](active.md#neooriginsactive_ability) whose `entity_action` is `neoorigins:toggle { power: "mypack:my_toggle" }`. The Apoli-style `origins:toggle` is a different type and does accept `key`.

See [Make a toggle → Toggleable abilities (no keybind slot)](../guides/toggles.md) for full recipes.

---

## `neoorigins:entity_set`

Pure data-holder power. Its presence in a player's active power set declares that the player participates in a named UUID set. Actual set storage lives on `PlayerOriginData`; the `neoorigins:in_set` / `neoorigins:add_to_set` / `neoorigins:remove_from_set` verbs read and mutate it.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `name` | string | no | `""` | Name of the UUID set this power declares. Conventionally namespaced like `mypack:kill_streak`. |

**Example:**
```json
{
  "type": "neoorigins:entity_set",
  "name": "mypack:hunted",
  "description": "Tracks entities marked as hunted by this origin."
}
```

The colon in `name` is allowed and carries no mechanical meaning; it's a soft convention for avoiding collisions between packs. Pair this with `action_on_event` (to add entries) and `condition_passive` or direct DSL predicates (to query membership).
