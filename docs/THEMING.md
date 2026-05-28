# UI Theming (resource-pack overrides)

NeoOrigins ships a single built-in UI theme — **parchment** — used by the
origin-selection and origin-info screens. Everything about that theme
(panel texture, font, text colours, 9-slice insets) is loaded from
client resources, so resource packs can retheme the screens without code.

All overrides are **client-side** (resource pack, not datapack) and take
effect on the next `F3+T` / `/reload` or rejoin.

## Override the panel background

Drop a PNG at:

```
assets/neoorigins/textures/gui/themes/parchment/panel.png
```

The mod expects a 256×256 image with a 12 px burnt-edge border (used as a
9-slice). If your texture uses a different size, override `texture_width`,
`texture_height`, and the four `inset_*` fields in `ui_themes/parchment.json`
(see below).

## Override the font

The theme's font is `neoorigins:parchment`, resolved from:

```
assets/neoorigins/font/parchment.json
```

Standard Minecraft font-provider JSON. Ship that file (and any referenced
TTF) in your pack and the screens will pick it up. The bundled default
points at the included Newsreader variable TTF (SIL OFL 1.1).

## Override / swap the theme JSON

The active theme JSON lives at:

```
assets/neoorigins/ui_themes/parchment.json
```

Drop a file at that path in your pack to change colours, panel texture,
or font without touching the PNG/TTF.

### Schema

All fields are optional — missing fields keep the built-in parchment
default. Colours are ARGB hex strings (`"0xFF2A1810"`) or raw ints.

| Field                      | Type           | Default                                                     |
|----------------------------|----------------|-------------------------------------------------------------|
| `panel_background`         | ResourceLocation | `neoorigins:textures/gui/themes/parchment/panel.png`      |
| `overlay_color`            | ARGB           | `0xCC060610` (full-screen scrim)                            |
| `name_color`               | ARGB           | `0xFF2A1810` (origin display name)                          |
| `description_color`        | ARGB           | `0xFF3A2410` (body description)                             |
| `power_name_color`         | ARGB           | `0xFF6B3B10`                                                |
| `power_description_color`  | ARGB           | `0xFF4A2A10`                                                |
| `header_color`             | ARGB           | `0xFF2A1810` (section headers)                              |
| `border_color`             | ARGB           | `0xFF6B4A20`                                                |
| `muted_color`              | ARGB           | `0xFF4A2A10` (secondary text)                               |
| `accent_color`             | ARGB           | `0xFFB87328` (bullets, dots)                                |
| `font`                     | ResourceLocation | `neoorigins:parchment`                                    |
| `inset_left`               | int (px)       | `12`                                                        |
| `inset_top`                | int (px)       | `12`                                                        |
| `inset_right`              | int (px)       | `12`                                                        |
| `inset_bottom`             | int (px)       | `12`                                                        |
| `texture_width`            | int (px)       | `256`                                                       |
| `texture_height`           | int (px)       | `256`                                                       |

Currently the screens load **only** `neoorigins:parchment`. Additional
JSONs under `ui_themes/` are accepted by the reload listener but not yet
selectable from the UI — treat `parchment.json` as the single override
point.

## Bundled font license

`Newsreader.ttf` is redistributed under the SIL Open Font License 1.1.
The licence text ships at `assets/neoorigins/font/OFL.txt`. If you
rebundle the TTF in your own pack you must keep OFL.txt alongside it.
