---
title: "Vision, rendering & HUD"
parent: "Power Types"
grand_parent: "DSL Reference"
nav_order: 5
permalink: /powers/visuals
---

# Vision, rendering & HUD

What the player sees and how the player looks: vision, overlays, shaders, tints, morphs, outlines, particles and HUD bars.

Fields every power accepts (`name`, `hidden`, `power_condition`, ...) are on [Power basics](../concepts/power-basics.md). See also: [Power basics](../concepts/power-basics.md), [Toggles](../concepts/toggles.md).

Types on this page: [`enhanced_vision`](#neooriginsenhanced_vision) · [`lava_vision`](#neooriginslava_vision) · [`overlay`](#neooriginsoverlay) · [`shader`](#neooriginsshader) · [`model_color`](#neooriginsmodel_color) · [`entity_model`](#neooriginsentity_model) · [`invisibility`](#neooriginsinvisibility) · [`prevent_entity_render`](#neooriginsprevent_entity_render) · [`entity_glow`](#neooriginsentity_glow) · [`particle`](#neooriginsparticle) · [`hide_hud_bar`](#neooriginshide_hud_bar).

---

## `neoorigins:enhanced_vision`

> **Status (2.2.x):** active again. On 26.x the brightness boost is applied
> through the lightmap render state (`LightmapRenderStateExtractor` mixin) at
> higher priority, so it survives other mods (Alex's Caves, etc.) that touch
> the same pipeline. A handful of built-in powers (Avian keen sight, the
> Archer / Cleric / Miner class sights) use `enhanced_vision`; most origin
> night vision remains on `neoorigins:night_vision`.

Passive low-light vision: emits an `enhanced_vision` capability tag and scales the client brightness curve directly via a lightmap mixin (`LightmapRenderStateExtractor`). Unlike the full `minecraft:night_vision` status effect, there's no screen tint, HUD icon, or max-brightness ramp at end of duration, just exposure-style compensation.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `exposure` | float (0.0–1.0) | no | `0.7` | Target brightness scalar (advisory: the client mixin currently applies full night-vision-equivalent brightness; a real Night Vision effect still wins where stronger) |

**Example:**
```json
{
  "type": "neoorigins:enhanced_vision",
  "exposure": 0.7,
  "name": "Cat Eyes",
  "description": "Sees clearly in dim light without the green tint."
}
```

All exposure work happens on the logical client; the server only publishes the capability tag. If per-origin variance is needed later, the value will be wired through a client-synced power-config payload.

The **Toggle Night Vision** keybind (default `K`) switches this off too, so the
key means the same thing whichever mechanism an origin uses. Like the status-effect
path it starts on, so nothing changes for a player who never presses it. No
ability slot is claimed either way: the dedicated key sits outside the skill-slot
system entirely. See [`neoorigins:night_vision`](retired.md#neooriginsnight_vision).

---

## `neoorigins:lava_vision`

Increases the player's vision distance while the camera is submerged in lava by pushing back the lava fog planes. Holders also lose the first-person burning-screen fire overlay: the power is meant for fire-immune origins, where the flame animation is noise. Client-side rendering is handled by `VisualEffectsHandler` via `ViewportEvent.RenderFog` and `RenderBlockScreenEffectEvent`.

The planes can be set two ways. `strength` scales vanilla's own values, while `start` and `end` replace them outright, in blocks. An absolute field always wins for the plane it names; a plane with no absolute value falls back to the multiplier.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `strength` | float | no | `3.0` | Fog distance multiplier (higher = further vision in lava). Ignored for whichever plane `start` or `end` sets. |
| `start` | float | no | — | Absolute fog start, in blocks: the distance at which fog begins. |
| `end` | float | no | — | Absolute fog end, in blocks: effectively how far you can see through lava. |

Note that `strength` is relative to values that vanilla itself varies, so the same multiplier gives different distances depending on status effects: without fire resistance the lava fog runs 0.25 to 1.0 blocks, with it 0.0 to 5.0. Use `start`/`end` when you want a distance you can count on. A `strength` of 0, or an `end` at or before `start`, is discarded rather than applied, since collapsing both planes onto each other paints the whole screen a flat fog colour.

Origins compat: `origins:lava_vision` maps `s` to `start` and `v` to `end`. Both are absolute distances upstream too, so the common `{"s": 0, "v": 15}` spelling carries over unchanged and means "see 15 blocks through lava".

**Example:**
```json
{
  "type": "neoorigins:lava_vision",
  "strength": 5.0,
  "name": "Magma Sight",
  "description": "See clearly through molten rock."
}
```

**Example: an exact sight distance, independent of fire resistance**
```json
{
  "type": "neoorigins:lava_vision",
  "start": 0.0,
  "end": 15.0,
  "name": "Magma Sight",
  "description": "See fifteen blocks through molten rock."
}
```

---

## `neoorigins:overlay`

Renders a full-screen texture overlay on the player's HUD. Client-side only: the server emits a capability tag encoding the texture path and strength; `VisualEffectsHandler` on the client reads it and draws the overlay.

> **1.21.1 vs 26.x:** `strength` sets the overlay's opacity on 1.21.1; on 26.x any value above 0 draws at full opacity, and 0 or below draws nothing.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `texture` | resource location | yes | — | Overlay texture (e.g. `"minecraft:textures/misc/pumpkinblur.png"`). |
| `strength` | float | no | `1.0` | On this version any value above 0 draws at full opacity, and 0 or below draws nothing. Only the first active overlay is drawn. |

Origins compat: translates `origins:overlay`.

**Example: dim vignette**
```json
{
  "type": "neoorigins:overlay",
  "texture": "minecraft:textures/misc/pumpkinblur.png",
  "strength": 0.3,
  "name": "Tunnel Vision",
  "description": "Your peripheral vision is dimmed."
}
```

---

## `neoorigins:shader`

Meant to apply a post-processing shader to the player's view. It currently has no visible effect on this version: the power loads, but the client side has not been ported to the new post-processing pipeline, so nothing is drawn.

> **1.21.1 vs 26.x:** nothing is drawn on any version, for different reasons: on 1.21.1 the shortened shader id fails to load in `PostChain`, and on 26.x the client side has not been ported to the new post-processing pipeline.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `shader` | resource location | yes | — | Shader id (e.g. `"minecraft:desaturate"`, `"minecraft:spider"`). |

Origins compat: translates `origins:shader`.

**Example: desaturated phantom view**
```json
{
  "type": "neoorigins:shader",
  "shader": "minecraft:desaturate",
  "name": "Phantom Eyes",
  "description": "The world appears washed of colour."
}
```

---

## `neoorigins:model_color`

Tints the player model with an RGBA colour.

> **1.21.1 vs 26.x:** the tint is drawn on 1.21.1 only. 26.x removed `RenderSystem.setShaderColor` and the mod has no replacement pass yet, so the power still loads, honors `enabled` and `condition` and syncs its state, but the model shows no color.

The tint is drawn on 1.21.1, where the client applies a colour multiply during the player render pass. It is not drawn on 26.1, 26.2 or 26.3, because those versions removed `RenderSystem.setShaderColor` and the mod has no replacement pass yet, so nothing paints the model. Everything else about the power is unchanged here: it loads, validates its fields, honours `enabled` and `condition`, and syncs its state to the client. A datapack naming this type is not broken on these versions, it simply shows no colour.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `red` | float | no | `1.0` | Red channel (0.0–1.0). |
| `green` | float | no | `1.0` | Green channel (0.0–1.0). |
| `blue` | float | no | `1.0` | Blue channel (0.0–1.0). |
| `alpha` | float | no | `1.0` | Alpha channel (0.0–1.0). |
| `condition` | object | no | — | EntityCondition that gates when the tint is applied. When absent, the tint is always active. When present, the colour only shows while the condition evaluates true. |
| `enabled` | bool | no | `true` | Kill switch: when false the power stays attached but never tints. Lets a server owner drop one tint from `power_overrides.toml` without editing the datapack. |

When two or more `model_color` powers are active at once, the blend rule is to average the channels rather than let one win: two tints produce their midpoint, three their mean. That is what 1.21.1 draws; on 26.1, 26.2 and 26.3 it stands as the defined rule with no render pass to apply it. A conditioned tint is re-evaluated once a second on every version, so the synced state appears and clears as the condition flips rather than only on login or toggle.

Origins compat: translates `origins:model_color`.

**Example: ghostly blue tint**
```json
{
  "type": "neoorigins:model_color",
  "red": 0.6,
  "green": 0.7,
  "blue": 1.0,
  "alpha": 0.8,
  "name": "Spectral Hue",
  "description": "Your form shimmers with a faint blue glow."
}
```

**Example: red glow at low health**
```json
{
  "type": "neoorigins:model_color",
  "red": 0.9, "green": 0.2, "blue": 0.2, "alpha": 0.7,
  "condition": { "type": "neoorigins:health", "comparison": "<=", "compare_to": 6 },
  "name": "Blood Rage",
  "description": "Your body glows red when near death."
}
```

---

## `neoorigins:entity_model`

Overrides the player's rendered model with another entity's model: a "morph". The server resolves the power's config into a single morph description and broadcasts it to every client tracking the player, which renders a cached stand-in entity of that type in place of the player, for both the morphed player (third-person) and everyone who can see them. A morph changes how the player sounds and how big they are as well as how they look.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `morph` | string | no | — | Named morph definition to use, e.g. `neoorigins:sheep`. A bare name resolves in the `neoorigins` namespace. See [Morph definitions](#morph-definitions) below. |
| `entity_type` | string | no | — | Entity id whose model replaces the player's, e.g. `minecraft:slime`. It also supplies the morph's voice and collision box. |
| `nbt` | object | no | — | Partial NBT applied to the stand-in to pick a variant, e.g. `{"Color": 14}` for a red sheep. |
| `scale` | number | no | `1.0` | Uniform scale of the morph model, and of its collision box along with it. |
| `hitbox` | boolean | no | `true` | Whether the player collides at `entity_type`'s size instead of their own. See [Morph hitbox](#morph-hitbox) below. |
| `render_held_item` | boolean | no | `true` | Whether the player's held items are drawn on the morph, so others can see what they're carrying. A morph with hands holds the item; one without floats it in front of the body instead. |
| `render_armor` | boolean | no | `true` | Whether the player's worn armour is drawn on the morph. Only takes effect on morphs whose entity renders armour at all: zombies, skeletons and piglins do; sheep, slimes and villagers do not. |
| `first_person` | enum | no | `item` | What the morphed player sees in their own view: `item` draws the held item without the vanilla arm, `arm` also draws the morph's own arm, `hidden` draws nothing. See [First-person view](#first-person-view) below. |
| `arm` | string | no | — | Name of the bone to draw as the morph's arm in `arm` mode. See [First-person view](#first-person-view) below. |
| `skin` | object | no | — | Reskin the player instead of replacing their model. See [Skin morphs](#skin-morphs) below. |
| `entity_sounds` | boolean | no | `true` | Whether the player borrows the voice of `entity_type`. See [Morph sounds](#morph-sounds) below. |
| `sounds` | object | no | — | Explicit sound overrides, layered over that voice. See [Morph sounds](#morph-sounds) below. |

Every field is optional because a morph can be described two ways: inline, or by referencing a named definition with `morph`. When both are present the inline fields win, so a pack can reuse a definition and tweak one detail. A power with neither leaves the player looking like themselves.

### First-person view

Everyone else sees the morph. The morphed player mostly sees their own hands, and those hands are the one part of the player a morph cannot simply replace: vanilla draws a player arm there, and a slime has no arm to put in its place. `first_person` picks which of the three honest answers you want:

| Value | What the player sees |
|---|---|
| `item` | The held item, floating, with no arm behind it. The default: it works for every morph, because it never needs the morph to have an arm. |
| `arm` | The morph's own arm when the hand is empty, and the held item when it isn't, the same rule vanilla uses for the player's arm. |
| `hidden` | Nothing. Right for a morph that shouldn't have visible hands at all. |

`arm` is worth setting for humanoid morphs (a zombie, a skeleton, a piglin, a villager) where the arm exists and reads correctly. On a morph with no arm bone it costs nothing: the view falls back to the `item` behaviour rather than showing something wrong.

The bone is found automatically. Humanoid mobs are recognised outright, and other models are searched for a bone named `right_arm` or `left_arm` (and the usual variants: `rightArm`, `arm_right`, `right_hand`, or a plain `arm`). Set `arm` only when that picks the wrong bone or finds nothing, and set it to the bone's name as the model defines it. Nothing stops you naming a bone that isn't an arm; a morph that should raise a wing or a claw into view is a legitimate use. A name the model doesn't have logs a warning once and falls back to auto-detection, so a typo shows up in the log rather than silently emptying the view.

The arm is drawn where a *player's* arm would be, because that is where the hand belongs on screen. A morph whose arm is much longer or shorter than a player's will look it: that's the cost of borrowing another mob's geometry, and it's why the default doesn't do this.

```json
{
  "type": "neoorigins:entity_model",
  "entity_type": "minecraft:zombie",
  "first_person": "arm"
}
```

### Skin morphs

`entity_type` replaces the player's model with a mob's. `skin` does something different and often more useful: it keeps the player's own model and swaps the textures drawn on it. Because the player is still being rendered as a player, arms, animations, armour, held items and first-person view all keep working with nothing extra to configure. Reach for `skin` for anything humanoid, and `entity_type` when you actually want a different shape.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `texture` | string | no | — | Body texture, as an *asset id*. |
| `cape` | string | no | — | Cape texture. |
| `elytra` | string | no | — | Elytra texture. Vanilla falls back to the cape texture when unset. |
| `model` | enum | no | — | Arm width: `slim` (three-pixel arms) or `wide` (four). |

Textures are addressed as **asset ids, not file paths**: `neoorigins:morph/fox` loads `assets/neoorigins/textures/morph/fox.png`. The file must be a 64×64 player skin; a mob texture will not map onto the player model.

Every key is layered over the player's real skin rather than replacing it wholesale, so a `skin` block that only sets `cape` leaves the body alone, and one that only sets `texture` doesn't strip a player's real cape. Leaving `model` unset keeps whatever arm width the player already had, which is what a texture-only reskin normally wants; set it when your texture was drawn for a specific arm width.

```json
{
  "type": "neoorigins:entity_model",
  "skin": {
    "texture": "neoorigins:morph/frostborn",
    "model": "slim"
  },
  "name": "Frostborn",
  "description": "Your skin pales to frost-bitten blue."
}
```

### Morph sounds

A morph is audible as well as visible. Naming an `entity_type` also hands the player that mob's voice: its hurt sound, its death sound, its landing sounds and its swimming sounds. A morphed creeper hisses when it takes a hit; a morphed slime squelches when it lands. Nothing has to be configured for this; it is what `entity_type` does now.

Set `entity_sounds` to `false` to look like the mob and still sound like yourself.

`sounds` overrides individual sounds on top of that. It is the way to give a voice to a morph that has no `entity_type` at all (a `skin` morph), to use a sound your own pack ships, or to change just one of the sounds a mob would otherwise supply.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `hurt` | string | no | — | Played when the player takes damage. |
| `death` | string | no | — | Played once when the player dies. |
| `fall_small` | string | no | — | Landing from a short fall (four blocks or less). |
| `fall_big` | string | no | — | Landing from a long fall. |
| `swim` | string | no | — | Looping sound while swimming. |
| `splash` | string | no | — | Entering or leaving water. |
| `splash_high_speed` | string | no | — | The faster splash used when hitting water hard. |

Every value is a **sound event id**: `minecraft:entity.fox.hurt`, not a path to an `.ogg`. Custom sounds need an entry in your pack's `sounds.json` the same as anywhere else. An id that isn't registered is reported once in the log and then ignored, so a typo costs you one sound rather than silencing the player.

Like `skin`, each key is layered rather than wholesale: a `sounds` block that only sets `hurt` leaves the rest of the mob's voice alone, and `fall_small` and `fall_big` resolve independently of each other.

```json
{
  "type": "neoorigins:entity_model",
  "morph": "creeper",
  "sounds": {
    "death": "mypack:morph.creeper.unmaking"
  },
  "name": "Creeping Form",
  "description": "You hiss when struck, and go out with a sound of your own."
}
```

Two sounds deliberately do not change. **Step sounds** come from the block underfoot, not from the entity, so morphing into a chicken does not make you sound like one walking. **Ambient sounds** (a zombie's groan, a cat's meow) have no player equivalent to hook, so a morph never idles aloud.

### Morph hitbox

A morph is tangible, not just a picture. Naming an `entity_type` gives the player that mob's collision box: a small slime fits through a one-block gap, a spider walks under a slab, an iron golem no longer does. Eye height follows the box, so a short morph genuinely sees from lower down and a tall one sees over a fence. `scale` resizes the box along with the model, so the two never disagree.

The size is read from the morph target with its variant NBT already applied, which means `{"Size": 0}` on a slime gives you the small slime's box, not the default one. `neoorigins:size_scaling` still applies on top, so a scaled-up player morphed into a slime collides as a scaled-up slime.

Set `hitbox` to `false` for a look-only morph: the player keeps their own box and eye height, and only the model changes.

```json
{
  "type": "neoorigins:entity_model",
  "entity_type": "minecraft:slime",
  "nbt": { "Size": 0 },
  "name": "Slimeling",
  "description": "Small enough to slip through a gap a person cannot."
}
```

**Growing into a wall.** A morph that would make the player *bigger* than the space they are standing in is held back rather than forced: the player keeps their own size, and the morph takes hold the moment they step somewhere it fits. Nothing is cancelled and nothing teleports. So crouching into a one-block gap as a small slime works, and a power that would grow you back while you are still in that gap simply waits until you are out of it. The same check runs while a morph is already applied, so a player walled in mid-morph shrinks back to their own size instead of suffocating.

A morph target far larger than a player (an ender dragon, a ravager) is not rejected, but in practice it will rarely find room, so expect it to spend most of its time held back. Very large morphs are better authored with `hitbox` set to `false`.

### Morph definitions

`morph` refers to a reusable definition: the same entity type, variant NBT and scale used across several origins, written once. The built-ins are `slime`, `magma_cube`, `sheep`, `cat`, `villager`, `creeper`, `zombie`, `skeleton`, `enderman` and `spider`.

Define your own (or replace a built-in) at `data/<ns>/neoorigins/morphs/<name>.json`, using the same fields as above minus `morph` itself:

```json
{
  "entity_type": "minecraft:cat",
  "nbt": { "variant": "minecraft:siamese" },
  "scale": 0.9,
  "first_person": "hidden"
}
```

A datapack file always overrides the built-in of the same id. An unknown `morph` id logs a warning once and is ignored, rather than silently rendering nothing.

Notes:

- **The hitbox does change**, unless you set `hitbox` to `false`. See [Morph hitbox](#morph-hitbox). It composes with `neoorigins:size_scaling` rather than fighting it: that power writes the vanilla `minecraft:scale` attribute, which is applied on top of the morph's own size.
- **Sounds do change**, unless you set `entity_sounds` to `false`. See [Morph sounds](#morph-sounds).
- **Variants come from `nbt`**, so any variant the entity itself reads from save data works with no per-mob support needed: sheep colour, cat and villager types, slime size, and so on. The `Team` key is ignored — a render stand-in has no business joining a scoreboard team.
- **Held items and armour** carry over to the morph unless you turn them off. Where the morph's model draws equipment itself (zombies, skeletons, piglins, anything humanoid), the items go into its real hands and armour slots and animate with it. Where it does not (a slime has no hand bone), the held item floats just above and in front of the body instead. `render_armor` therefore has no visible effect on a morph whose entity never draws armour.
- **Player state carries across.** Invisibility, the red damage flash, the freezing/powder-snow overlay, the death topple, and the swing and use animations all mirror the player onto the morph, so a morphed player still reads as hurt, frozen or dying at a glance.
- **Nameplate** is preserved: the player's display name still renders above the morph under the same visibility rules vanilla uses.
- An entity type that can't be rendered (unknown id, no registered renderer, players) is reported once in the log and the player keeps their normal model, rather than turning invisible.

**Example: slime morph, using the built-in definition**
```json
{
  "type": "neoorigins:entity_model",
  "morph": "slime",
  "name": "Slime Form",
  "description": "You appear as a slime to yourself and others."
}
```

**Example: a big red sheep, defined entirely inline**
```json
{
  "type": "neoorigins:entity_model",
  "entity_type": "minecraft:sheep",
  "nbt": { "Color": 14 },
  "scale": 1.4,
  "first_person": "hidden",
  "name": "Ram Form",
  "description": "You take on the shape of a great red ram."
}
```

---

## `neoorigins:invisibility`

Makes the player invisible. Unlike a plain invisibility status effect, this power can also hide worn armor for true invisibility (vanilla leaves armor visible on an invisible body). The invisibility rides the vanilla invisibility effect, so it is gated by the power's top-level `condition`: it applies only while the condition holds and lapses when it stops.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `render_armor` | bool | no | `true` | When `true`, worn armor stays visible (vanilla behaviour). When `false`, worn armor and held items are also hidden client-side for true invisibility. That hiding applies whenever the player is invisible from any source, even while this power's condition is false. |

**Example:**
```json
{
  "type": "neoorigins:invisibility",
  "render_armor": false,
  "name": "Vanish",
  "description": "Become completely invisible, armor and all."
}
```

This is the native type the Apoli compat layer maps `origins:invisibility` (and `apace:invisibility`) onto, carrying the `render_armor` field through.

---

## `neoorigins:prevent_entity_render`

Hides other entities from the holder. Every living entity matching `entity_condition` stops being drawn for this player only — everyone else still sees it normally, and the entity is otherwise untouched: it still moves, still attacks, and still collides. Only living entities are candidates, so boats, minecarts, item frames, dropped items and projectiles are never hidden. Pair it with a power that applies glowing to build a "you only see what you have revealed" origin.

The condition is evaluated on the server, which then tells the client which entities to skip. That is what lets verbs like `status_effect` and `nbt` work here, since a client never receives another entity's effects or full NBT.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_condition` | [condition](../schema/condition.schema.json) | no | — | Tested against each nearby living entity; every one that passes becomes invisible to the holder. Omit it to hide every living entity. Because the subject is an arbitrary entity rather than the holder, only entity-general verbs apply: `entity_type`, `in_tag`, `target_group`, `living`, `on_fire`, `health`, `relative_health`, `status_effect` / `has_effect`, `nbt`, `in_block_anywhere`, `equipped_item` (any of the six vanilla slots, reading that entity's own equipment; the `accessory` slot is not general), `constant`, and `and` / `or` / `not` over those. A condition using anything else is not refused — the power loads, warns, and hides nothing rather than hiding everything. Takes one condition object, or an array of them, which is read as all of them AND-combined. |

**Refresh rate and scope.** The hidden set is recomputed every 5 ticks, over the living entities within the server's view distance and no further than 256 blocks. An entity that starts or stops matching therefore appears or disappears up to a quarter of a second later. At most 1024 entities are hidden from one player at a time; past that, the remainder stay visible.

**Example: only glowing creatures are visible**
```json
{
  "type": "neoorigins:prevent_entity_render",
  "entity_condition": {
    "type": "neoorigins:and",
    "conditions": [
      { "type": "neoorigins:living" },
      { "type": "neoorigins:status_effect", "effect": "minecraft:glowing", "inverted": true }
    ]
  },
  "name": "Blind Sight",
  "description": "You see nothing but what you have marked."
}
```

This is the native type the Apoli compat layer maps `origins:prevent_entity_render` (and `apace:prevent_entity_render`) onto, carrying `entity_condition` through. Apoli's `bientity_condition` variant is not translated, because half of the pair it compares is the observer.

---

## `neoorigins:entity_glow`

Living entities matching `entity_condition` are drawn with the glowing outline for the holder only, the way a Spectral Arrow outlines its target. Nobody else sees the outline and the entity gets no Glowing effect. Origins' Arachnid uses it to sense creatures caught in cobweb. The selection works exactly like [`prevent_entity_render`](#neooriginsprevent_entity_render): evaluated on the server every 5 ticks over the entities in view distance, and sent to the holder alone. The fields are Apoli's, so imported `origins:entity_glow` powers map straight onto this type.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_condition` | [condition](../schema/condition.schema.json) | no | — | Tested against each nearby living entity; every one that passes glows for the holder. Omit it to outline every living entity. Takes the same entity-general verbs as `prevent_entity_render`. |
| `bientity_condition` | [condition](../schema/condition.schema.json) | no | — | Pair condition with the holder as actor and the candidate as target; it must pass too. Pair verbs: `can_see`, `distance` (holder to candidate, Apoli's feet-to-feet measure), `constant`, `target_condition` / `actor_condition`, and `and` / `or` / `not` over those. One using anything else loads, warns and outlines nothing. |
| `use_teams` | bool | no | `true` | An entity on a coloured team is outlined in its team colour. |
| `red` | float | no | `1.0` | Outline colour, 0.0 to 1.0, used when the team colour does not apply. |
| `green` | float | no | `1.0` | As `red`. |
| `blue` | float | no | `1.0` | As `red`. |

**Example: sense anything stuck in a web that is not a spider**
```json
{
  "type": "neoorigins:entity_glow",
  "entity_condition": {
    "type": "neoorigins:and",
    "conditions": [
      { "type": "neoorigins:in_block_anywhere", "block_condition": { "type": "neoorigins:block", "tag": "neoorigins:cobwebs" } },
      { "type": "neoorigins:in_tag", "tag": "minecraft:arthropod", "inverted": true }
    ]
  },
  "name": "Web Sense",
  "description": "Non-arthropods stuck in cobweb glow for you."
}
```

---

## `neoorigins:particle`

Spawns vanilla particles on the player at a fixed cadence. Server-side `ServerLevel.sendParticles` packetizes to nearby clients, so this works on dedicated servers without a client-side mixin.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `particle` | string \| object | yes | — | Registry id of any `SimpleParticleType` (e.g. `"minecraft:end_rod"`). For parameterized particles use the object form: `{ "type": "minecraft:dust", "color": [1.0, 0.85, 0.2], "scale": 0.6 }` |
| `frequency` | int | no | `8` | Emit every N server ticks. Lower = denser. |
| `count` | int | no | `1` | Particles per emission. |
| `spread` | `[x, y, z]` floats | no | `[0.25, 0.5, 0.25]` | Random offset spread per particle. |
| `offset` | `[x, y, z]` floats | no | `[0.0, 1.0, 0.0]` | Origin offset from player feet (defaults to ~chest height). |
| `speed` | float | no | `0.0` | Vanilla "speed" parameter: passes through to `sendParticles`. Most particle types use this as initial-velocity scale; some ignore it. |
| `condition` | EntityCondition | no | always-true | Optional gate evaluated each emission tick. |

The Origins/Apoli `particle` power type is auto-translated to this; packs that already use `origins:particle`, `apoli:particle`, `apace:particle`, or `apugli:particle` work without modification (`frequency` and `particle` fields map 1:1).

**Example: ambient sparkle aura**
```json
{
  "type": "neoorigins:particle",
  "particle": "minecraft:end_rod",
  "frequency": 6,
  "count": 1,
  "spread": [0.4, 0.6, 0.4],
  "name": "Blessed Aura",
  "description": "Faint sparkles trail you wherever you walk."
}
```

**Example: colored gold dust, water-only**
```json
{
  "type": "neoorigins:particle",
  "particle": { "type": "minecraft:dust", "color": [1.0, 0.85, 0.2], "scale": 0.6 },
  "frequency": 10,
  "count": 2,
  "condition": { "type": "neoorigins:in_water" },
  "name": "Gilded Wake"
}
```

Sparkle-aesthetic vanilla particle picks: `minecraft:end_rod` (cleanest white twinkle), `minecraft:firework` (bright sparks, heavier), `minecraft:enchant` (swirling glyphs), `minecraft:wax_on` (puff burst), `minecraft:scrape` (copper sparks), `minecraft:totem_of_undying` (green/gold festive), `minecraft:glow` (Allay-style soft dots), `minecraft:nautilus` (subtle blue specs).

---

## `neoorigins:hide_hud_bar`

Hides a HUD bar while the power is active. Emits `hide_hunger_bar` or `hide_air_bar` capability tags; `GuiHudBarsMixin` reads them to cancel the matching render call. Governed by the `hide_hud_bars` client config (`[hud]` in `config/neoorigins/client.toml`, default true), so each player decides; if they disable it, the power still applies but their HUD still renders the bar.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `bar` | string | no | `"hunger"` | Which bar to hide. `"hunger"` / `"food"` hide the hunger bar; `"air"` / `"oxygen"` / `"breath"` hide the air bar. Any other value is a no-op. |

**Example:**
```json
{
  "type": "neoorigins:hide_hud_bar",
  "bar": "air",
  "name": "No Need to Breathe",
  "hidden": true
}
```

Typically paired with `neoorigins:water_breathing` or the Automaton pattern so the hidden bar is also being suppressed mechanically; hiding a bar that still ticks is disorienting.
