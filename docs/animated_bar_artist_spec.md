# NeoOrigins — Animated Resource Bar Art Spec

Spec for artists producing animated "element" fills for NeoOrigins resource bars
(mana / stamina / rage / custom origin resources). The animation plays inside the
**fill** portion of a resource bar on the in-game HUD.

---

## 1. Where it renders (context)

A resource bar is composed of three pieces, drawn at **1 texel = 1 GUI pixel**
(Minecraft then scales the whole HUD 2×–4× with nearest-neighbor):

| Piece            | Size (logical px) | Notes |
|------------------|-------------------|-------|
| Frame / backing  | 71 × 5            | Static. Already exists — **artist does NOT need to make this.** |
| **Fill**         | **71 × 8**        | **This is what you're animating.** Overhangs the frame up by 2 px. |
| Icon             | 8 × 8             | Static, sits left of the bar. Out of scope here. |

The fill is clipped to the current resource level: only the leftmost
`round(71 × fraction)` pixels are shown. So the **left edge is the "empty" cutoff
edge** and must read well when partially revealed. Bars auto-hide at 100%.

Bars draw over arbitrary world backdrops (sky, caves, grass) — **assume a
busy/variable background, not a flat color.**

---

## 2. Format rules (hard constraints — engine limits)

- **PNG, RGBA, straight (un-premultiplied) alpha.** No GIF/APNG/WebP — Minecraft
  cannot load them.
- **Transparent background, not black.** The bar composites with additive-style
  blending, so any near-black pixels read as "glow over background." Deliver wisps
  on full transparency; let alpha fall to 0 in the empty gaps. Do **not** leave a
  black box behind the art.
- **No anti-aliased outer edges against black** — that creates grey halos in game.
  Fade with alpha, not with dark pixels.
- Color space: sRGB.

---

## 3. Two delivery formats — pick per element (A is preferred)

### Format A — Seamless scroll strip  *(recommended for fire/water/wind "wisps")*
A single horizontally-**seamless** strip. The engine scrolls the U coordinate over
time and samples a 71-px-wide window, so motion = lateral drift baked into the strip
plus the engine's scroll. Tiny file, infinite loop, no frame budget.

- **Author at native: `128 × 8` px, seamless left↔right.** This is pixel-art — the
  PNG ships 1:1 with no downsampling, so every pixel you place is a pixel in game
  (the GUI only scales it by the player's integer GUI scale, nearest-neighbor).
- The pixel column at x=0 must tile perfectly against x=127.
- Width is flexible: 128 is the sweet spot, but anything ~96–256 works — the visible
  window is only 71px and the wisps move, so the loop reads as continuous. Don't go
  bigger than you need.
- Bake in gentle drift (rising curl for fire, flow for water) — the engine adds
  the horizontal scroll on top.
- Keep it **sparse**: discrete wisps/tongues separated by transparency, *not* a
  solid band. (This was the explicit ask — wisps moving through, not a filled bar.)

### Format B — Vertical frame strip  *(use for tightly art-directed loops, e.g. a heartbeat pulse)*
A stack of equal frames played at **20 fps** (game tick rate), looping
frame N → frame 1.

- **Author at native: `71 × 8` per frame** (pixel-art, 1:1, no downsample).
- **16–24 frames** → sheet `71 × 128` to `71 × 192`. 24 frames ≈ 1.2 s loop.
- Frame 1 and frame N must loop seamlessly (no pop).

---

## 4. Alpha / luminance authoring

- Brightest cores → alpha 255 (fully opaque, reads as a bright element).
- Mid glow → partial alpha.
- Empty gaps → alpha 0.
- Think of it as **emissive**: the bar should look like it's glowing, because the
  blend adds the art's light over whatever is behind it.
- Avoid large fully-opaque flat regions — they hide the "level" readout and look
  like a solid bar.

---

## 5. Element palettes & motion direction

| Element | Palette | Motion / feel |
|---------|---------|---------------|
| **Fire**  (first deliverable) | deep red → orange → yellow-white cores | sparse tongues that rise, curl, flicker; hot cores, soft falloff |
| **Water** | teal/blue, white caustic highlights | slow horizontal flow, gentle ripple/caustic shimmer |
| **Wind / Air** | pale cyan-white, very transparent | thin fast lateral streaks, mostly empty |
| **Earth / Nature** | green + amber | slow drifting motes / leaves, or pulsing veins |
| **Soul** | cyan-white (soul-fire palette) | slow, eerie low-amplitude drift |
| **End** | purple/magenta + star flecks | shimmer + occasional sparkle travelling along the bar |
| **Sculk** | dark teal base + bright cyan | a cyan pulse that travels left→right along the bar |

---

## 6. Style

**Pixel-art, authored at native resolution** (`128 × 8` scroll tile / `71 × 8` per
frame). The PNG ships 1:1 — no downsampling — so place pixels deliberately; what you
draw is exactly what shows in game (only integer GUI-scale upscaling applies). Bias
brightness/alpha a little hot, since these read tiny on the HUD.

---

## 7. Optional "tintable" variant

The engine supports an optional `tint` hex applied as a **multiply** over the fill.
If you also deliver a **grayscale/white-hot** version of an element, one piece of art
can be recolored per-origin via tint (e.g. one generic "flame" tinted blue for soul,
purple for end). Hero full-color versions still preferred for the headline elements.

---

## 8. Deliverables checklist

- [ ] Layered source (PSD / Procreate / Krita) per element.
- [ ] Final PNG(s) per §3 (Format A: one seamless strip; Format B: one sheet).
- [ ] RGBA, transparent bg, sRGB, per §2.
- [ ] File names: lowercase element id — `fire.png`, `water.png`, `wind.png`,
      `earth.png`, `soul.png`, `end.png`, `sculk.png`.
- [ ] (Optional) grayscale tintable variant: `<element>_mask.png`.
- [ ] QA: preview each over a **dark** and a **bright** backdrop — wisps must read on
      both, with no grey/black halo and no solid-band look.

---

## 9. Quick reference

```
Fill area (animated):     71 × 8 px  (overhangs frame top by 2px)
Visible width:            left  round(71 × fill%)  pixels only
Scroll strip:             128 × 8, seamless L↔R, pixel-art 1:1 (no downsample)
Frame strip:              71 × 8 per frame, 16–24 frames @20fps, stacked vertically
Format:                   PNG RGBA, straight alpha, sRGB, transparent bg
Blend:                    additive/emissive — author as glow, no black box
Style:                    pixel-art, native res, ships 1:1
```
