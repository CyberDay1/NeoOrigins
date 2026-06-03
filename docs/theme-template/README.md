# NeoOrigins UI theme — pack template

Copy this whole folder, rename the placeholders, and you have an addon pack
that re-skins NeoOrigins's selection / info screens without writing any Java.

## Getting this folder

This template lives inside the NeoOrigins repo at `docs/theme-template/`.
GitHub has no "download one subfolder" button, so grab it one of these ways:

- **Whole-repo ZIP** — on the [repo page](https://github.com/CyberDay1/NeoOrigins),
  click **Code → Download ZIP**, unzip, and find this folder under
  `docs/theme-template/`.
- **Just this folder** — paste this README's URL into
  [download-directory.github.io](https://download-directory.github.io/)
  (third-party tool) to get a ZIP of only `docs/theme-template/`.
- **git clone** — `git clone https://github.com/CyberDay1/NeoOrigins.git`,
  then copy `docs/theme-template/` out.
- **Release asset** — from **2.2.0** onward, a ready-to-edit
  `neoorigins-theme-template.zip` is attached to each
  [release](https://github.com/CyberDay1/NeoOrigins/releases); that zip *is*
  this folder, so you can skip the repo entirely.

Once you have the folder, continue below.

## What you get

A combined **resource pack + data pack** that:

- registers a new theme (`<your_ns>:<your_id>`)
- declares it as the active theme via `data/<ns>/neoorigins/active_theme.json`
- ships an optional custom panel PNG and font

Players just install the pack — no client config, no separate datapack juggling.

## Steps

1. **Rename the placeholders.** Every directory and file named `_replace_`
   should become your pack's namespace (e.g. `mymodpack`). Required renames:

   ```
   assets/_replace_/                    -> assets/<your_ns>/
   assets/<your_ns>/textures/gui/themes/_replace_/
                                        -> assets/<your_ns>/textures/gui/themes/<your_id>/
   data/_replace_/                      -> data/<your_ns>/
   ```

   Inside the JSONs, every `_replace_` string should be your namespace too.
   (Your editor's "find and replace in folder" is the fast path.)

2. **Pick a theme id.** Rename `assets/<your_ns>/ui_themes/my_theme.json` to
   `<your_id>.json`. The id the mod registers is `<your_ns>:<your_id>`.

3. **Edit the colours.** Open `ui_themes/<your_id>.json` and tune the ARGB
   hex values. Every field is optional — delete any you want to inherit from
   `neoorigins:parchment`.

4. **(Optional) Drop in a panel PNG.** Replace
   `textures/gui/themes/<your_id>/PANEL_PNG_GOES_HERE.txt` with a 256x256
   `panel.png`. Skip this step entirely if you only want to recolour text.

5. **(Optional) Ship a TTF font.** Edit `font/my_theme_font.json` and put the
   TTF beside it. Skip this step to inherit Newsreader from the base mod.

6. **Point `active_theme.json` at your id.** Open
   `data/<your_ns>/neoorigins/active_theme.json` and confirm `"theme"` matches
   `<your_ns>:<your_id>`.

## Shipping the pack

Three options:

- **Zip + resourcepacks/ + datapacks/.** Zip this folder so `pack.mcmeta` is at
  the zip root. Copy the zip into both `.minecraft/resourcepacks/` (so the
  textures load) and the world's `datapacks/` (so `active_theme.json`
  applies). Enable in both menus.

- **NeoForge mod jar.** Add a `neoforge.mods.toml` and ship as a normal mod
  jar. Both sides load automatically; no manual enable.

- **NeoOrigins `originpacks/` folder.** Drop the zip in
  `config/originpacks/` and the mod's pack finder picks it up on both sides
  at once. The least friction for end users.

## How the active-theme selection works

The mod scans every loaded pack for `data/<ns>/neoorigins/active_theme.json`.
If exactly one pack declares it, that pack wins. If several do, the one
loaded last wins (and a warning is logged listing the conflict). Players can
force a specific theme regardless via `config/neoorigins-client.toml`:

```toml
[ui]
theme_override = "<your_ns>:<your_id>"
```

The override is per-client, useful for solo players.

## Troubleshooting

- **The theme doesn't apply** — check the server log for
  `[theming] active UI theme set by ...`. If you see
  `[theming] active theme '<id>' is not loaded`, the resource side didn't
  load — your theme JSON likely isn't where the mod expects it.
- **Panel still shows parchment** — `panel_background` in the theme JSON
  must be a valid resource location pointing to a PNG that exists in your
  pack. Typos fall back silently to the parchment default.
- **Font didn't change** — Minecraft font reloads are picky. Hit `F3+T`
  after editing the JSON, or rejoin the world.
