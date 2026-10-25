---
title: Animated Bar Artist Spec
parent: "Project & Internals"
nav_order: 2
---

# NeoOrigins: Resource Bar Art Spec

Spec for artists producing fills for NeoOrigins resource bars (mana, stamina, rage
and custom origin resources). A bar style is a preset JSON plus its textures; the
preset format and every field are in
[POWER_TYPES.md → resource](POWER_TYPES.md#neooriginsresource). This page covers
the art.

---

## 1. Where it renders

The shipped styles (`neoorigins:fire`, `water`, `wind`, `earth`, `soul`, `void`,
`sculk` and `tinted`) use the **framed layout**, drawn at **1 texel = 1 GUI pixel**
(the game then scales the whole HUD by the player's GUI scale, nearest-neighbor):

| Piece | Size (GUI px) | Notes |
|---|---|---|
| Frame | 71 × 8 | `neoorigins:textures/gui/bar_fx/empty_bar.png`, shared by every shipped style. Set by the preset's `frame`, `frame_width` and `frame_height`. |
| **Fill** | **69 × 6** | **This is what you are drawing.** It sits inside the frame, one pixel in from each edge. |
| Value ticks | 1 px wide | Drawn by the game over the top of the fill: a faint 1 px tick every 5 points and a brighter 2 px tick every 25. |

The fill is drawn over the frame and cut off at the current level: only the
leftmost `round(69 × fraction)` pixels show. So the **left edge is the empty end**,
and the cut edge must read well at any width.

A bar hides when full once it has been spent, so the fill is mostly seen partly
drawn. Bars sit over arbitrary world backdrops, but inside the frame the fill
covers its own area, so the frame and fill decide how the bar reads.

---

## 2. Format rules

- **PNG, RGBA, sRGB.** No GIF, APNG or WebP: Minecraft cannot load them.
- **Draw the fill at its in-game height.** A framed fill is drawn `frame_height − 2`
  pixels tall (6 for the shipped frame). Set `tile_height` to the texture's real
  height; a texture of another height is stretched to fit, which breaks the pixel grid.
- **Make the fill opaque.** All the shipped fills are fully opaque. Blending is
  ordinary alpha blending, not additive: a transparent pixel shows the frame's dark
  interior behind it, not a glow.
- There is **no frame-by-frame animation**. A fill either scrolls sideways or stands
  still (section 3).

---

## 3. Two kinds of fill

### Scrolling strip (`"mode": "scroll"`, the default)

A horizontal strip that drifts left at the preset's `scroll_speed`, in GUI pixels
per second (negative drifts right). The scroll follows game time, so it stops while
the game is paused, and it steps one screen pixel at a time.

- **Shipped size: `126 × 6`.** Width is free; the strip repeats every
  `loop_width` texels.
- **Pick one seam convention and set `loop_width` to match:**
  - The last column repeats the first: set `loop_width` to `tile_width − 1`, so that
    column is not shown twice. Six shipped strips do this (126 wide, `loop_width` 125).
  - The strip wraps cleanly across its full width: set `loop_width` to `tile_width`.
    `sculk` does this (126 wide, `loop_width` 126).
- Bake any motion you want beyond the sideways drift (rising flame, flowing water)
  into the strip itself. The game adds only the scroll.

### Static fill (`"mode": "static"`)

Drawn in place, with no scroll, and cut off at the current level. Pixel column
`c` of the texture lands on column `c` of the fill, so make it as wide as the fill
(69 for the shipped frame).

`neoorigins:tinted` is the shipped example: `grayscale.png`, 69 × 6.

---

## 4. Tintable art

A power can set `tint` in `hud_render`, and the game multiplies the fill by it,
channel by channel. A preset with `"tint_from_color": true` also uses the bar's
`color` when no `tint` is given.

To make a fill that takes any color, draw it in greys: white comes out as the tint
itself, and darker greys as deeper shades of it, so the shading survives. The
`tinted` fill is mostly mid-grey, with white and darker greys for its shading.

A static fill can also shade across the bar with `tint_end`: column by column, from
the tint at the left end to `tint_end` at the right. This applies only to a framed
fill that does not scroll.

---

## 5. Shipped textures

All under `assets/neoorigins/textures/gui/bar_fx/`:

| Style | File | Size | `loop_width` | `scroll_speed` | Look |
|---|---|---|---|---|---|
| `fire` | `fire.png` | 126 × 6 | 125 | 16 | Red and orange flames |
| `water` | `water.png` | 126 × 6 | 125 | 10 | Deep blue water with bubbles |
| `wind` | `wind.png` | 126 × 6 | 125 | 24 | White cloud wisps over grey |
| `earth` | `earth.png` | 126 × 6 | 125 | 6 | Brown earth |
| `soul` | `soul.png` | 126 × 6 | 125 | 8 | Cyan soul flames |
| `void` | `void.png` | 126 × 6 | 125 | 10 | Purple veins |
| `sculk` | `sculk.png` | 126 × 6 | 126 | 12 | Dark teal sculk with cyan sparks |
| `tinted` | `grayscale.png` | 69 × 6 | static | static | Grey, colored by `tint` or `color` |

---

## 6. Style

**Pixel art, authored at native resolution.** The PNG ships 1:1 with no
downsampling, so place pixels deliberately: what you draw is exactly what shows in
game, apart from integer GUI-scale upscaling. Bias brightness a little hot, since
the bar reads tiny on the HUD.

---

## 7. Deliverables checklist

- [ ] Layered source (PSD, Procreate or Krita) per fill.
- [ ] Final PNG per fill, RGBA, sRGB, opaque, at the in-game height (section 2).
- [ ] For a scrolling strip: which seam convention it uses, so `loop_width` can be set.
- [ ] File names: the lowercase style id (`fire.png`, `water.png`, …).
- [ ] QA: preview each fill inside the frame at several fill levels, and check the
      cut edge and the scroll seam.

---

## 8. Quick reference

```
Frame:            71 × 8 (empty_bar.png)
Fill:             69 × 6, one pixel in from each frame edge
Visible width:    leftmost round(69 × fill%) pixels
Scroll strip:     126 × 6 shipped; loops every loop_width texels
Static fill:      69 × 6; column c of the texture = column c of the fill
Speed:            scroll_speed GUI px/s, follows game time
Blend:            normal alpha; ship opaque fills
Tint:             multiply; author tintable fills in greys
Style:            pixel art, native res, ships 1:1
```

### The unframed box

A preset with no `frame` draws the layout from before 2.2.30: a 60 × 10 box with a
1 px border, the preset's `track_color` under the empty part and `level_color` under
the filled part, and the texture scaled to 10 px tall, keeping its aspect ratio,
tiled across the filled part. The box scrolls the whole scaled texture, so
`loop_width`, `tint_from_color` and `tint_end` do not apply to it; `tint` does. The
backing colors are drawn first, so art for the box can leave gaps for them to show
through.

---

## 9. Apoli-compat `sprite_location` sheets (static restyled bars)

Separate from the bar styles above: Apoli-style resource powers can declare
`hud_render.sprite_location` to swap the whole **static** bar sheet for a custom
one. NeoOrigins honors that override. This section is the spec for authoring
such a sheet.

### Where the texture lives

`sprite_location` is a full resource path used **verbatim**: e.g.
`"sprite_location": "mypack:textures/gui/my_bars.png"` loads
`assets/mypack/textures/gui/my_bars.png` from your resource pack / datapack
assets. An empty or malformed id, or one naming a texture no loaded pack has,
falls back to the vendored default sheet
(`neoorigins:textures/gui/resource_bar.png`). Community Apoli restyle sheets use
the same coordinates and drop in unchanged.

### Required sheet dimensions

**256 × 256 PNG.** The renderer samples UVs against a 256 × 256 texture: any
other canvas size misaligns every element. Only the top-left region is used;
leave the rest transparent.

### Sheet layout (what `bar_index` / `icon_index` select)

| Element | Position in sheet | Size | Selected by |
|---|---|---|---|
| Frame / backing | u 0, v 0 | 71 × 5 | fixed (shared by all bars) |
| Fill row | u 0, v `8 + bar_index × 10` | 71 × 8 | `bar_index` (row) |
| Icon | u `73 + icon_index × 9`, v `8 + bar_index × 10` | 8 × 8 | `icon_index` (column) + `bar_index` (row) |

- Vertical stride between fill rows is **10 px** (8 px fill + 2 px gap):
  `bar_index` 0 → v 8, 1 → v 18, 2 → v 28, …
- Icons sit to the **right of the fill area** in the sheet (first column at
  u 73), on the **same v row as their fill**. Each bar row carries its own
  icon variants, 9 px horizontal stride (8 px icon + 1 px gap).

### How it draws in game

The frame strip renders at the bar position; the fill renders clipped to the
leftmost `round(71 × fill%)` pixels and overhangs the frame **upward by 2 px**
(exactly as Apoli draws it); the icon renders 10 px left of the bar, top-aligned
with the fill. Pixel art at native resolution, RGBA, transparent background.
