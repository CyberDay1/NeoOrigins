# UI Theming

NeoOrigins lets addon packs reskin the origin selection / info screens
(panel texture, font, text colours, 9-slice insets) without code. As of the
addon-pack rewrite the system is **multi-theme aware**: pack authors register
themes by id, then declare which one is active from inside the pack itself.
Players don't have to install separate resource packs and datapacks: a single
addon (zip or mod jar) carries everything.

## Quickstart: make your own theme

A copy-and-edit template lives in [`docs/theme-template/`](./theme-template).
The short version:

1. Copy `docs/theme-template/` somewhere.
2. Rename every `_replace_` folder + string to your pack's namespace
   (e.g. `mymodpack`) and rename `my_theme.json` to whatever you want the id
   to be.
3. (Optional) drop a 256x256 `panel.png` into
   `assets/<ns>/textures/gui/themes/<id>/`.
4. (Optional) put a `.ttf` in `assets/<ns>/font/` and update
   `<id>_font.json`.
5. Zip it (or wrap it in a mod jar) and ship.

The template's README walks through each step in more detail.

## Theme discovery

The mod scans every loaded resource pack for files matching:

```
assets/<any_ns>/ui_themes/<id>.json
```

Each file is registered as the theme `<ns>:<id>`. The built-in
`neoorigins:parchment` is always present even when no JSON ships for it, so
themes that omit fields fall through to its defaults.

## Theme selection (which theme is active)

Two layers, applied in order (the first that resolves to a *loaded* theme
wins):

1. **Player override**
   (`config/neoorigins/client.toml`):
   ```toml
   [ui]
   theme_override = "examplepack:dark_woods"
   ```
   Leave empty (the default) to defer to the server.

2. **Datapack-declared theme**. Any pack can ship
   `data/<ns>/neoorigins/active_theme.json`:
   ```json
   { "theme": "examplepack:dark_woods" }
   ```
   The server reads these on world load + each `/reload` and broadcasts the
   selection to every client at login. Conflict rule: if multiple packs each
   declare an `active_theme.json`, the one loaded **last** wins and a warning
   is logged listing every contributor. Adjust pack load order if you want
   a different outcome.

3. Falls back to `neoorigins:parchment` when neither resolves.

### The classic-picker accessibility override

`classic_picker_style` under `[ui]` in `config/neoorigins/client.toml` outranks
every layer listed above. Selection itself still runs (an unknown id is still
warned about, and the resolved theme is still stored), but while the option is
`true` the screens read the built-in flat high-contrast skin (dark navy panels,
light text, vanilla font) instead, so whatever resolved has no visible effect.
If a pack's theme appears to do nothing, check this setting first.

## Theme JSON schema

`assets/<ns>/ui_themes/<id>.json`. All fields optional: missing fields keep
the parchment default. Colours are ARGB hex strings (`"0xFF2A1810"`) or raw
ints.

| Field                      | Type             | Default                                              |
|----------------------------|------------------|------------------------------------------------------|
| `panel_background`         | ResourceLocation | `neoorigins:textures/gui/themes/parchment/panel.png` |
| `overlay_color`            | ARGB             | `0xCC060610` (full-screen scrim)                     |
| `name_color`               | ARGB             | `0xFF2A1810` (origin display name)                   |
| `description_color`        | ARGB             | `0xFF3A2410` (body description)                      |
| `power_name_color`         | ARGB             | `0xFF6B3B10`                                         |
| `power_description_color`  | ARGB             | `0xFF4A2A10`                                         |
| `header_color`             | ARGB             | `0xFF2A1810` (section headers)                       |
| `border_color`             | ARGB             | `0xFF6B4A20`                                         |
| `muted_color`              | ARGB             | `0xFF4A2A10` (secondary text)                        |
| `accent_color`             | ARGB             | `0xFFB87328` (bullets, dots)                         |
| `font`                     | ResourceLocation | `neoorigins:parchment`                               |
| `inset_left` / `top` / `right` / `bottom` | int (px) | `12` each                                  |
| `texture_width` / `texture_height`        | int (px) | `256`                                      |
| `flat`                     | bool             | `false`: paint a plain fill + outline instead of the 9-slice panel art; `panel_background` and the `inset_*` / `texture_*` fields are then unused |
| `panel_color`              | ARGB             | `0x00000000`: panel fill, used only when `flat` is true |

## Direct resource-pack overrides

You don't *have* to author a new theme; a resource pack can also swap the
parchment texture or font in place:

- **Panel PNG**: drop a file at
  `assets/neoorigins/textures/gui/themes/parchment/panel.png`. 256x256 with a
  12 px 9-slice border, or override the `inset_*` / `texture_*` fields.
- **Font provider**: `assets/neoorigins/font/parchment.json` is a standard
  Minecraft font-provider JSON; replace it to swap in your own TTF.
- **Theme JSON**: `assets/neoorigins/ui_themes/parchment.json`. Same schema
  as above; ships overrides for the built-in theme directly.

The new-theme workflow is preferred (cleaner, doesn't collide with other
packs touching the parchment assets), but the in-place overrides are kept for
small "I just want different colours" packs.

## Bundled font license

`newsreader_regular.ttf` (Newsreader 16pt Regular, static cut) is
redistributed under the SIL Open Font License 1.1.
The licence text ships at `assets/neoorigins/font/ofl.txt`. If you rebundle
the TTF in your own pack you must keep `ofl.txt` alongside it.

Both filenames are lowercase on disk because NeoForge's `PathPackResources`
validator rejects uppercase letters in path segments: the TTF is referenced
by resource id from `parchment.json`, so an uppercase name would not resolve.
