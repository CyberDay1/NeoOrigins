---
title: "Movement & body"
parent: "Power Types"
grand_parent: "DSL Reference"
nav_order: 4
permalink: /powers/movement
---

# Movement & body

Flight, gliding, climbing, swimming and breathing, size, pose, and how the player moves through fluids and blocks.

Fields every power accepts (`name`, `hidden`, `power_condition`, ...) are on [Power basics](../concepts/power-basics.md). See also: [Toggles](../concepts/toggles.md) (several of these switch on and off), [Costs & upkeep](../concepts/costs-and-upkeep.md#toggle-upkeep).

Types on this page: [`flight`](#neooriginsflight) · [`creative_flight`](#neooriginscreative_flight) · [`elytra_boost`](#neooriginselytra_boost) · [`elytra_flight`](#neooriginselytra_flight) · [`natural_glide`](#neooriginsnatural_glide) · [`wall_climbing`](#neooriginswall_climbing) · [`modify_flight_speed`](#neooriginsmodify_flight_speed) · [`modify_lava_speed`](#neooriginsmodify_lava_speed) · [`no_slowdown`](#neooriginsno_slowdown) · [`phantom_form`](#neooriginsphantom_form) · [`wraith_phase`](#neooriginswraith_phase) · [`size_scaling`](#neooriginssize_scaling) · [`pose`](#neooriginspose) · [`walk_on_fluid`](#neooriginswalk_on_fluid) · [`ignore_water`](#neooriginsignore_water) · [`ignore_fluid`](#neooriginsignore_fluid) · [`like_air`](#neooriginslike_air) · [`cobweb_affinity`](#neooriginscobweb_affinity) · [`bounce_on_land`](#neooriginsbounce_on_land) · [`water_breathing`](#neooriginswater_breathing) · [`breath_in_fluid`](#neooriginsbreath_in_fluid) · [`breath_out_of_fluid`](#neooriginsbreath_out_of_fluid) · [`conduit_power_on_land`](#neooriginsconduit_power_on_land).

---

## `neoorigins:flight`

Elytra-style fall-flight launched from a **mid-air jump**, as a toggle. While the toggle is on, jumping again in mid-air spreads the player's wings and starts fall-flying without an equipped elytra; toggling the power off lands them. It is not creative hover: for that, see `neoorigins:creative_flight` below.

The launch is refused on the ground, in water, while riding, in spectator, and while already fall-flying.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `cooldown_icon` | string | no | `""` | HUD icon (joins the ability cluster); full-bright while toggled on, dimmed while off |
| `always_show_icon` | boolean | no | `false` | Keep the icon on the HUD even while toggled off |
| `render_elytra` | boolean or string | no | `false` | When an elytra is drawn on the player's back: `false` / `"never"` (no wings), `true` / `"flying"` (only while flying), or `"always"` (whenever the power is active, folded when not flying). Flight works either way; this is cosmetic only. |
| `texture_location` | identifier | no | vanilla elytra | Custom texture for the drawn elytra. Only applies when `render_elytra` draws wings at all; the model stays the vanilla elytra. |

Also takes the shared [toggle upkeep](../concepts/costs-and-upkeep.md#toggle-upkeep) fields (`hunger_cost`, `resource_cost`, `resource_cost_amount`, `upkeep_interval`, `upkeep_condition`).

**Example:**
```json
{
  "type": "neoorigins:flight",
  "render_elytra": true,
  "cooldown_icon": "minecraft:elytra",
  "name": "Natural Flight",
  "description": "Jump again in mid-air to take to the sky, no elytra required."
}
```

As with `neoorigins:natural_glide`, the flight runs with an empty chest slot, so nothing is drawn on the player's back unless `render_elytra` is set. See the note under `natural_glide` for why that defaults off, and for the three values it accepts.

---

## `neoorigins:creative_flight`

True creative-style hover flight, as a **toggle**. Unlike `neoorigins:flight` and `natural_glide`, which are both elytra/fall-flying mechanics, this grants real `mayfly` hover: the player keeps solid block collision, normal visibility and gravity when not flying. Double-tap jump to take off, then jump to rise and sneak to descend, exactly like creative mode. Intended for "ride the sword" / levitating-cultivator fantasies.

While the power is on and its condition holds, `mayfly` is re-asserted every tick and pushed to the client whenever it had been cleared. When the power is removed or toggled off, survival defaults are restored, but never for a creative or spectator player, so toggling off can't lock them out of their own game mode.

Beyond `name` and `description`:

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `condition` | condition | no | always true | EntityCondition gating the flight, re-tested every tick |
| `cooldown_icon` | string | no | `""` | HUD icon resource path (joins the ability-icon cluster) |
| `always_show_icon` | bool | no | `false` | Keep the icon on the HUD even while toggled off |

Also takes the shared [toggle upkeep](../concepts/costs-and-upkeep.md#toggle-upkeep) fields (`hunger_cost`, `resource_cost`, `resource_cost_amount`, `upkeep_interval`, `upkeep_condition`).

**Requiring something of the flier.** With a `condition`, the flight is granted only while the condition passes, and — because it is re-tested every tick — a player who breaks it in mid-air has the ability stripped and falls. That drop is the point: it turns the condition into a real cost rather than a check made once at takeoff. Toggling on while the condition fails leaves the power armed but grounded; the flight begins the moment the condition is met. The built-in Sword Immortal uses this to bind flight to the blade in hand:

```json
{
  "type": "neoorigins:creative_flight",
  "condition": {
    "type": "neoorigins:equipped_item",
    "equipment_slot": "mainhand",
    "item_condition": { "tag": "minecraft:swords" }
  }
}
```

Pair a conditional flight with a fall-damage immunity (see `neoorigins:prevent_action`) if the drop should be survivable.

**Charging for the flight.** Like every toggle, `creative_flight` takes the [toggle upkeep](../concepts/costs-and-upkeep.md#toggle-upkeep) fields. This Sword Immortal style "Riding the Sword" flies only with a sword in hand and drains 2 qi a second, but only while the player is actually in the air; when the bar runs dry the toggle switches off and the player falls:

```json
{
  "type": "neoorigins:creative_flight",
  "condition": {
    "type": "neoorigins:equipped_item",
    "equipment_slot": "mainhand",
    "item_condition": { "tag": "minecraft:swords" }
  },
  "resource_cost": "mypack:qi",
  "resource_cost_amount": 2,
  "upkeep_interval": 20,
  "upkeep_condition": { "type": "neoorigins:creative_flying" }
}
```

`mypack:qi` is a [`neoorigins:resource`](state.md#neooriginsresource) granted by the same origin. Without the `upkeep_condition`, the qi would drain every second the toggle is on, standing or flying.

**Disabling it server-side.** `creative_flight` honors a top-level `enabled` flag (default `true`) that server owners can flip off in `config/neoorigins/power_overrides.toml`, keyed by the power id. When `enabled = false` the flight is stripped every tick and never re-granted; the origin keeps all its other powers. This is the intended way to ground a flying origin without editing datapacks; for example, the built-in Sword Immortal and Windwalker flights are:

```toml
[power_overrides.jianxian_riding_sword]
    enabled = false   # ground the Sword Immortal
[power_overrides.windwalker_riding_wind]
    enabled = false   # ground the Windwalker
```

**Example:**
```json
{
  "type": "neoorigins:creative_flight",
  "name": "Riding the Wind",
  "description": "Double-tap jump to take to the air and fly freely."
}
```

---

## `neoorigins:elytra_boost`

Active (keybind) power: while the player is already gliding, pressing the key gives a firework-like boost in the look direction. It never starts a glide; pair it with `natural_glide`, `elytra_flight` or a real elytra.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `strength` | double | no | `1.5` | Multiplier on the forward impulse applied while elytra gliding. |
| `cooldown_ticks` | int | no | `40` | Ticks before the boost can be triggered again. |

Also accepts the three shared cooldown HUD fields (`cooldown_icon`, `cooldown_countdown`, `always_show_icon`) described at the top of this file.

**Example:**
```json
{
  "type": "neoorigins:elytra_boost",
  "name": "Elytra Boost",
  "description": "Surge forward mid-glide."
}
```

---

## `neoorigins:elytra_flight`

The native mirror of `apoli:elytra_flight`: grants elytra-style fall-flight without an equipped elytra, and optionally draws an elytra on the player's back while gliding.

Flight itself reuses natural glide: this power emits the `natural_glide` capability, so it drives the exact same activation path (press jump while falling to spread your wings). Everything `neoorigins:natural_glide` does, `elytra_flight` does too; it just adds the cosmetic wing render on top.

The wings are purely cosmetic: gliding works the same whether or not an elytra is drawn. When drawn, the model is always the vanilla elytra; `texture_location` only swaps the texture on that model. If the player is wearing a real equipped elytra, vanilla renders it and this power stays out of the way (no double wings).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `render_elytra` | boolean or string | no | `true` | When an elytra is drawn on the player's back: `false` / `"never"` (no wings), `true` / `"flying"` (only while fall-flying), or `"always"` (whenever the power is active, folded against the back when not gliding). Flight works either way; this is cosmetic only. |
| `texture_location` | identifier | no | vanilla elytra | Custom texture for the drawn elytra, e.g. `mymod:textures/entity/my_wings.png`. Only applies when `render_elytra` draws wings at all; the model stays the vanilla elytra (texture swap only). Omit for the vanilla elytra texture. |

**Example: spectral wings you can see**
```json
{
  "type": "neoorigins:elytra_flight",
  "render_elytra": true,
  "texture_location": "neoorigins:textures/entity/spectral_wings.png",
  "name": "Spectral Wings",
  "description": "Glide like an elytra user — no item required — with shimmering wings on your back."
}
```

**Example: invisible glide (no wings drawn)**
```json
{
  "type": "neoorigins:elytra_flight",
  "render_elytra": false,
  "name": "Phantom Drift",
  "description": "Glide with no visible wings."
}
```

**Example: wings that are always on**
```json
{
  "type": "neoorigins:elytra_flight",
  "render_elytra": "always",
  "name": "Winged",
  "description": "Your wings are part of you: folded on your back when you walk, spread when you glide."
}
```

`"always"` draws the wings whenever the power is active rather than only during a glide; vanilla's elytra model folds them against the back when the player is not fall-flying, so the standing pose is the usual tucked one. The power's top-level `condition` still applies, so "always" means "whenever the power is active". This is what the built-in Elytrian origin uses: the wings are its body plan, not a flight effect. See the three-state table under `neoorigins:natural_glide` for the full value list.

If `render_elytra` is false, this is behaviourally identical to `neoorigins:natural_glide`. Pair either with `neoorigins:elytra_boost` for a full glide + launch-boost kit.

---

## `neoorigins:natural_glide`

Grants elytra-style gliding without needing to equip an elytra. Press jump while falling to start fall-flying, exactly as if the player were wearing one.

Emits the `natural_glide` capability tag. The `PlayerStartFallFlyingMixin` reads it at the head of `Player.tryToStartFallFlying` and bypasses the standard chest-slot elytra check, calling `startFallFlying()` directly.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `render_elytra` | boolean or string | no | `false` | When an elytra is drawn on the player's back: `false` / `"never"` (no wings), `true` / `"flying"` (only while fall-flying), or `"always"` (whenever the power is active, folded against the back when not gliding). Flight works either way; this is cosmetic only. Defaults `false`: this power glides with no visible wings unless you ask for them. |
| `texture_location` | identifier | no | vanilla elytra | Custom texture for the drawn elytra, e.g. `mymod:textures/entity/my_wings.png`. Only applies when `render_elytra` draws wings at all; the model stays the vanilla elytra (texture swap only). Omit for the vanilla elytra texture. |

**Example: Phantom spectral wings**
```json
{
  "type": "neoorigins:natural_glide",
  "render_elytra": true,
  "name": "Spectral Wings",
  "description": "Glide like an elytra user, no item required. Press jump while falling to spread your wings."
}
```

Preconditions match vanilla: not on ground, not already fall-flying, not in water, not levitating. Pair with `neoorigins:elytra_boost` for a full glide + launch-boost kit.

**Why wings need asking for.** The glide runs with an empty chest slot, and vanilla's elytra render layer keys off the equipped item, so without `render_elytra` the player flies with nothing on their back. The field defaults `false` here rather than `true` so packs authored against the wingless behaviour keep it, including origins that already supply their own wing model. `neoorigins:elytra_flight` is the same mechanic with the default flipped on.

**The three wing states.** `render_elytra` is a tri-state written as a widened boolean, so the old spellings still mean exactly what they always did:

| Value | Wings |
|---|---|
| `false` or `"never"` | Never drawn. |
| `true` or `"flying"` | Drawn only while fall-flying. |
| `"always"` | Drawn whenever the power is active, folded against the back when not gliding. |

`"always"` is gated by the power's own top-level `condition` like everything else, so it means "whenever the power is active", not literally always. The string spellings are case-sensitive. Any other value (`"Always"`, `"sometimes"`, the string `"true"`) logs a `Failed to parse power config` error naming the legal values, and the field falls back to the type's default (`false` here and on `flight`, `true` on `elytra_flight`); the power itself still loads. A real equipped elytra still wins in every case: vanilla draws that one and this power stays out of the way.

Contrast with `neoorigins:flight`: both are pitch-based elytra gliding, but `flight` is a toggle launched by a mid-air jump, while `natural_glide` is always available and starts from a fall. For creative-mode hover, see `neoorigins:creative_flight`.

---

## `neoorigins:wall_climbing`

Allows the player to cling to and climb any solid wall surface, similar to spiders. Fall damage is suppressed while climbing.

No additional fields beyond `name` and `description`.

**Example:**
```json
{
  "type": "neoorigins:wall_climbing",
  "name": "Wall Climbing",
  "description": "Can scale any solid surface."
}
```

**Making climbing a toggle.** Wall climbing is always on. To put it on a key, the way Origins' Arachnid does, gate it on a hidden [`toggle`](state.md#neooriginstoggle) with a top-level `condition`, and flip that toggle from an `active_ability`. The classic-mode Arachnid is built this way (`classic_arachnid_climbing*.json`), and gates `cobweb_affinity`'s `climb` on the same toggle so web climbing also needs climbing switched on.

```json
{ "type": "neoorigins:toggle", "hidden": true, "default": true }
```
```json
{
  "type": "neoorigins:wall_climbing",
  "hidden": true,
  "condition": { "type": "neoorigins:power_active", "power": "mypack:climbing_toggle" }
}
```
```json
{
  "type": "neoorigins:active_ability",
  "cooldown_ticks": 10,
  "cooldown_icon": "minecraft:cobweb",
  "entity_action": { "type": "neoorigins:toggle", "power": "mypack:climbing_toggle" },
  "name": "Climbing",
  "description": "Press your ability key to turn climbing on or off."
}
```

---

## `neoorigins:modify_flight_speed`

Scales creative / hover flight speed: the vanilla `Abilities.flyingSpeed` value (base `0.05`) that the game reads while a player is flying. This is the in-mod equivalent of Pehkui's `pehkui:flight` scale, so packs no longer need a Pehkui dependency to retune flight speed. It composes with any flight source (the `creative_flight` / `flight` powers, vanilla creative, or another mod's mayfly) because it edits the shared flight-speed input rather than a specific flight mechanic.

It does **not** affect elytra / fall-flying gliding, which is physics-driven and never reads `flyingSpeed`, matching Pehkui's flight scale.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `operation` | string | no | `"multiply_base"` | `"addition"`, `"multiply_base"`, or `"multiply_total"`. The two multiply operations are identical here; any other string is treated as `"addition"`. |
| `value` | double | yes | — | Amount applied to the `0.05` base. `multiply_base 1.0` doubles flight speed, `-0.5` halves it, `-0.25` ≈ Pehkui flight scale `0.75`. |

Multiple holders of this power stack the Apoli way (additions sum, then the multiplier deltas sum), exactly like `modify_lava_speed`.

**Example: Pehkui flight scale 0.75 equivalent**
```json
{
  "type": "neoorigins:modify_flight_speed",
  "operation": "multiply_base",
  "value": -0.25,
  "name": "Measured Flight",
  "description": "Hovers at three-quarters the usual flight speed."
}
```

---

## `neoorigins:modify_lava_speed`

Modifies the player's movement speed while submerged in lava. Uses the `NumericModifierRegistry` system consumed by `LivingEntityLavaSpeedMixin`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `operation` | string | no | `"addition"` | `"addition"`, `"multiply_base"`, or `"multiply_total"`. The two multiply operations are identical here: the result is `(base + additions) × (1 + Σ multiply values)`. Any other string is treated as `"addition"`. |
| `value` | double | yes | — | Amount to add or multiply. Vanilla lava-swim factor is `0.02`; an addition of `0.04` (3× vanilla) feels like swimming in water. |

**Example: water-swim-pace lava swimming**
```json
{
  "type": "neoorigins:modify_lava_speed",
  "operation": "addition",
  "value": 0.04,
  "name": "Molten Stride",
  "description": "Swims through lava as easily as others swim through water."
}
```

The modifier applies once, as written. Up to 2.2.27 it was added again on every respawn and every login, so a `multiply_base` of `0.5` became 2x after one death and kept growing until `/reload` or a restart. If you lowered a value to compensate, put it back.

---

## `neoorigins:no_slowdown`

Prevents the player from being slowed by movement-impeding blocks: cobwebs,
sweet berry bushes, and powder snow (the stuck-velocity clamp), plus soul
sand and honey blocks (the reduced walk-speed factor).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `block_tag` | string | no | _(all slowdown blocks)_ | Restrict immunity to blocks in this tag. Write the tag id without `#`; a `#`-prefixed value matches nothing. |

With `block_tag` omitted the immunity is unconditional and predicted
client-side, so there's no rubberbanding when entering a web. Restricting
it to a tag keeps the check server-authoritative: a brief correction may
be visible.

**Example: immune to all block slowdown**
```json
{
  "type": "neoorigins:no_slowdown",
  "name": "Unimpeded",
  "description": "Not slowed by webs, soul sand, or dense foliage."
}
```

---

## `neoorigins:phantom_form`

A toggle (on by default, switched with the skill key). While on, the player can fly, passes through blocks and takes no fall damage, whatever the fields say. `invisibility` adds a hidden Invisibility effect; `no_gravity` also turns gravity off.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `invisibility` | bool | no | `true` | Apply permanent Invisibility effect |
| `no_gravity` | bool | no | `true` | Disable gravity (player floats) |

Also takes the shared [toggle upkeep](../concepts/costs-and-upkeep.md#toggle-upkeep) fields (`hunger_cost`, `resource_cost`, `resource_cost_amount`, `upkeep_interval`, `upkeep_condition`).

**Example:**
```json
{
  "type": "neoorigins:phantom_form",
  "invisibility": true,
  "no_gravity": false,
  "name": "Phantom Form",
  "description": "Drift through walls unseen."
}
```

---

## `neoorigins:wraith_phase`

Toggleable spectral phasing. When active the player walks through solid blocks horizontally. While inside a solid block, jump and sneak give vertical control (jump = up, shift = down): that movement is velocity, and the power never grants flight. Holding shift on the surface phases downward into the ground. Certain blocks cannot be phased through.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `blocked_blocks` | list of string | no | `["minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:bedrock"]` | Blocks that cannot be phased through, each a block id or a block tag written as `#namespace:tag`. An entry that does not parse is skipped with a warning in the log rather than failing the power. |
| `exhaustion_per_tick` | float | no | `0.15` | Hunger drain per tick while inside solid blocks. |
| `always_on` | bool | no | `false` | When `true`, the power is passive (always active, no toggle, no skill key slot). Configurable per tier in the mod config. |
| `obscured_vision` | bool | no | `false` | When `true`, the player can't see through terrain while phasing: whenever their view is inside a solid block, thick dark fog closes in at `phase_view_distance`. When `false`, the player sees through walls as before. |
| `phase_view_distance` | float | no | `1.5` | With `obscured_vision`, how far the player can see, in blocks, while their view is inside a solid block. |

Also takes the shared [toggle upkeep](../concepts/costs-and-upkeep.md#toggle-upkeep) fields (`hunger_cost`, `resource_cost`, `resource_cost_amount`, `upkeep_interval`, `upkeep_condition`).

**Limited sight while phasing.** Vanilla darkens the screen when your head is inside a block, but phasing switches that off, so a phasing player normally sees caves, ores and bases straight through the rock. With `"obscured_vision": true` the view is fogged instead: as soon as the camera enters a solid block the world fades to near-black beyond `phase_view_distance`, and it clears the moment the camera is back in open air. Only the phasing player's own view changes, and only while the power is on.

Emits the `wall_phase` capability while active (toggled on or always_on).

**Example: base wraith phase**
```json
{
  "type": "neoorigins:wraith_phase",
  "blocked_blocks": ["minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:bedrock"],
  "exhaustion_per_tick": 0.15
}
```

**Example: phasing blind**
```json
{
  "type": "neoorigins:wraith_phase",
  "obscured_vision": true,
  "phase_view_distance": 2
}
```

**Example: apex tier (only bedrock blocks, minimal drain)**
```json
{
  "type": "neoorigins:wraith_phase",
  "blocked_blocks": ["minecraft:bedrock"],
  "exhaustion_per_tick": 0.075
}
```

---

## `neoorigins:size_scaling`

Scales the player's visual and collision size via the `minecraft:generic.scale` attribute. Optionally also scales block/entity interaction reach. The scale is not mirrored into Pehkui. Applied when the power is granted, so a top-level `condition` does not gate it.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `scale` | float | no | `1.0` | Target scale multiplier (`0.5` = half size, `2.0` = double) |
| `modify_reach` | bool | no | `true` | Also adjust block/entity interaction reach proportionally |
| `reach_bonus` | float | no | `0.0` | Flat reach (blocks) added to **both** block and entity interaction range, on top of any `modify_reach` scaling |

**Example: half-size origin that keeps usable reach**
```json
{
  "type": "neoorigins:size_scaling",
  "scale": 0.5,
  "modify_reach": false,
  "reach_bonus": 0.5,
  "name": "Tiny",
  "description": "Half-sized, but reach is kept near normal."
}
```

The scale attribute uses `ADD_VALUE` against a base of `1.0` (so delta = `scale - 1.0`); proportional reach (`modify_reach`) uses `ADD_MULTIPLIED_BASE` so reach tracks visual size, while `reach_bonus` uses a flat `ADD_VALUE` on both ranges. Missing attributes (older MC or NeoForge versions) are skipped silently, without a warning.

For the bundled size origins, `scale`, `modify_reach` and `reach_bonus` are all exposed per-origin in `config/neoorigins/power_overrides.toml`, so server owners can retune a shrunk origin's reach without a datapack; small origins (`inchling_size`, `tiny_size`) ship with `modify_reach: false` and a positive `reach_bonus` so they stay playable out of the box.

**Size stacks across layers.** Every `size_scaling` power owns its own modifier ids, so an origin scaling to `1.3` and a class scaling to `1.25` leave the player at `1.55`, not at whichever layer was picked last. Re-picking one layer removes only that layer's contribution and leaves the rest standing, in either order. Reach modifiers follow the same rule. If you want two layers to be mutually exclusive rather than cumulative, gate them with conditions; do not rely on one overwriting the other.

Deleting or renaming a size power's JSON is still safe: its leftover modifiers are swept the next time the player's powers change, so a power that no longer exists cannot strand someone at the wrong size.

---

## `neoorigins:pose`

Holds the player's body in a chosen pose for as long as the power is on: prone (crawling), sneak height, or upright. Vanilla recomputes the pose every tick and would fight a one-off change, so this is a power rather than an action — it re-asserts the pose while the origin says so and releases it the moment it stops.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `pose` | string | **yes** | — | `standing`, `crouching` or `swimming` (case-insensitive). Any other value makes the whole power fail to load, with an error naming the three it accepts: there is no fallback to `standing`, so a typo is loud rather than a power that silently does nothing |
| `toggleable` | bool | no | `true` | Binds a keybind that flips the pose on and off |
| `default_off` | bool | no | `false` | Toggleable powers only: the pose starts released, so the player opts in via the keybind |
| `enabled` | bool | no | `true` | Kill switch: when false the power stays attached but holds no pose |
| `cooldown_icon` | string | no | `""` | Item id or `.png` path shown on the HUD ability cluster: full-bright while on, dimmed while off |
| `always_show_icon` | bool | no | `false` | Keep the icon on the cluster while the power is idle |

Also takes the shared [toggle upkeep](../concepts/costs-and-upkeep.md#toggle-upkeep) fields (`hunger_cost`, `resource_cost`, `resource_cost_amount`, `upkeep_interval`, `upkeep_condition`).

**Example: a burrowing origin that crawls on command**
```json
{
  "type": "neoorigins:pose",
  "pose": "swimming",
  "toggleable": true,
  "default_off": true,
  "cooldown_icon": "minecraft:dirt",
  "name": "Burrow",
  "description": "Drop prone to squeeze through a one-block gap."
}
```

`swimming` is the crawl pose: a 0.6x0.6 body with the eye at 0.4. **The pose is not clearance-checked**, so it fits wherever its box fits, including gaps the player could not otherwise enter — a crawling origin can pass under a one-block ceiling anywhere, not only where vanilla would let it. That is deliberate; narrow it with a `condition` if your pack wants it narrower. Releasing the pose hands the player back to vanilla, which *does* check clearance and settles on whatever fits where they are standing.

**What the pose does and does not change.** It sets the hitbox and the animation, and it costs speed: vanilla scales movement input by the `minecraft:sneaking_speed` attribute whenever the player is crouched or crawling, and that test reads the pose, so a forced `crouching` or a forced `swimming` on land walks at sneak pace. It does **not** set the sneak flag, so there is no ledge-stop and no quiet footsteps — hold `pose: crouching` and the player still walks off edges. It also does **not** grant swimming: the swim propulsion in `travel` runs off the separate swimming flag, which vanilla sets from sprinting in water and never from a pose, so a forced `swimming` out of water is a crawl and nothing more.

Two `pose` powers active at once resolve smallest-first (`swimming`, then `crouching`, then `standing`), so the winning pose always fits wherever the loser would have. The top-level `condition` gates the pose like any other passive: while it fails the player is released to their normal pose, and picks it back up when the condition returns.

`sleeping`, `fall_flying` and `spin_attack` are vanilla poses too, but each carries behaviour the pose alone does not deliver (a bed, an elytra, a trident), so they are not offered here. For gliding, use `neoorigins:elytra_flight` or `neoorigins:natural_glide`.

---

## `neoorigins:walk_on_fluid`

Allows the player to walk on the surface of a fluid (water, lava, or both), using the same vanilla mechanic as Striders (`LivingEntity.canStandOnFluid`). The player can still dive by jumping into the fluid.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `fluid` | string | no | `both` | Which fluid to walk on: `water`, `lava`, or `both` |

**Example: walk on water**
```json
{
  "type": "neoorigins:walk_on_fluid",
  "fluid": "water",
  "name": "Water Walking",
  "description": "Can walk on water surfaces."
}
```

Origins compat: translates `origins:walk_on_fluid`.

---

## `neoorigins:ignore_water`

Makes the player unaffected by water: full movement speed in water (via `water_movement_efficiency` attribute) and immune to water-current pushing (via `EntityIgnoreWaterMixin`). Emits the `ignore_water` capability tag.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| (no fields) | — | — | — | Marker power. |

Origins compat: translates `origins:ignore_water`.

**Example:**
```json
{
  "type": "neoorigins:ignore_water",
  "name": "Hydrophobic",
  "description": "Water doesn't slow you down or push you around."
}
```

---

## `neoorigins:ignore_fluid`

Makes the player **totally** ignore one or more fluids. Where `ignore_water` only removes water's speed penalty and current pushing, this power removes the fluid from the player's world entirely: no buoyancy, no drag, no current push, no drowning, no lava burn, no fog or screen overlay, no swim pose.

> **1.21.1 vs 26.x:** modded fluids are tracked and ignored on 1.21.1 and 26.3; on 26.1 this needs NeoForge 26.1.2.99 or later and on 26.2 NeoForge 26.2.0.50-beta or later. Older 26.1 and 26.2 builds track only fluids in the `minecraft:water` and `minecraft:lava` tags, though the `entityInside` guard still cancels a modded fluid's own damage or effects.

It does that by intercepting fluid **detection** rather than each individual effect. `EntityFluidInteractionIgnoreFluidMixin` hands back an empty fluid state for ignored fluids inside `EntityFluidInteraction.update`, the single body scan that feeds buoyancy, drag, current push, `isInWater()`/`isInLava()`, `lavaHurt()`, the swim pose, drowning and the air bar. `CameraIgnoreFluidMixin` does the same for `Camera.getFluidInCamera`, which decides fog and the underwater distortion without ever consulting the entity. `BlockStateBaseIgnoreFluidMixin` cancels `entityInside` for the fluid's own block, which is how a mod-authored fluid applies its own damage or effects.

Because every vanilla fluid behaviour reads those same values, they all fall away together, and so do most third-party mods, since they query the same methods.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `fluid` | string or array | no | — | Fluid to ignore: a fluid id (`"minecraft:lava"`), a fluid tag (`"#c:milk"`), or an array of either |
| `fluids` | array | no | — | Plural spelling of `fluid`. Both keys are read and their entries merged |

Naming any fluid of a fluid type ignores the whole type, so `"minecraft:water"` also covers `minecraft:flowing_water` — that is what makes poured buckets work. Entries without a namespace get `minecraft:` (so `"lava"` means `"minecraft:lava"`). If neither key is given the power defaults to water and lava, so a marker-only entry is never a silent no-op. Unknown or misspelled ids are dropped quietly rather than failing the datapack load: the power simply ignores nothing for that entry.

Emits the `ignore_fluid` capability tag plus one `ignore_fluid:<id>` tag per entry, so the client and server agree without any extra sync.

**Modded fluids on this version.** The field shape is identical across every branch of the mod, so a datapack naming a modded fluid loads and validates here exactly as it does on 1.21.1. Whether its physics are suppressed depends on the NeoForge build. From NeoForge 26.1.2.99, entity fluid tracking is keyed by fluid type, so a modded fluid is tracked and ignored as on 1.21.1. Older 26.1 builds track only fluids in the `minecraft:water` and `minecraft:lava` fluid tags; there an entry naming another fluid is accepted and syncs its capability tag but drives nothing on the movement side, though the `entityInside` guard still cancels the fluid's own damage or effects.

**Not covered.** A mod that runs its own `level.getFluidState(pos)` check per tick — rather than asking the entity whether it is in a fluid — is doing its own detection and cannot be intercepted from here; it will still see the fluid. The same goes for anything keyed off the block state rather than the fluid state (a mod checking `state.is(Blocks.LAVA)` directly). Fluid **rendering** is untouched: the fluid is still drawn normally, you simply pass through it as if it were air.

One deliberate side effect: the `entityInside` guard is keyed on the block's fluid state, so a *waterlogged* block's own `entityInside` behaviour is suppressed too while you ignore water. In practice that means things like bubble columns stop pushing you, which is the intended reading of "water does not affect me".

`ignore_water` is unchanged and still supported; use it when you only want water's movement penalty gone, and this power when you want the fluid to stop existing for the player.

**Example: swim through lava unharmed**
```json
{
  "type": "neoorigins:ignore_fluid",
  "fluid": "minecraft:lava",
  "name": "Magma Born",
  "description": "Lava does not slow, push, burn or blind you."
}
```

**Example: ignore several fluids, including a modded one**
```json
{
  "type": "neoorigins:ignore_fluid",
  "fluids": ["minecraft:water", "minecraft:lava", "create:honey"],
  "name": "Untouchable",
  "description": "Liquids simply don't register."
}
```

---

## `neoorigins:like_air`

The player's walking-speed modifiers also apply to their speed while airborne. Vanilla airborne control is a flat 0.02 (0.026 while sprinting) that ignores the movement speed attribute, so speed effects and attribute bonuses do nothing mid-jump. With this power that value goes through every `movement_speed` modifier the player has, in vanilla's order, which includes the sprint modifier. Sprint is therefore counted twice while airborne, once by vanilla's sprinting value and once by the modifier, so a sprinting holder moves about 30% faster in the air than vanilla allows. Origins does the same, and this power keeps it on purpose. Creative and ability flight are not changed. Origins' Avian power of the same name.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| (no fields) | — | — | — | Marker power. |

**Example:**
```json
{
  "type": "neoorigins:like_air",
  "name": "Like Air",
  "description": "Modifiers to your walking speed also apply while you're airborne."
}
```

---

## `neoorigins:cobweb_affinity`

Spider-like mobility through cobwebs. Emits the `cobweb_affinity` capability tag: `EntityMakeStuckInBlockMixin` reads it to suppress the usual slowdown inside cobwebs, and `MovementPowerEvents.onBreakSpeed` reads it to multiply cobweb break speed by 10×. Both act on any block in `#neoorigins:cobwebs` (cobweb and the temporary cobweb). With `climb` on, a block in that same tag that the player is inside also works as a ladder: jump to climb, sneak to hold, on the ground or off it. That is the climbing half of Origins' Arachnid Master of Webs.

All three `cobweb_affinity` behaviours read `#neoorigins:cobwebs`: the slowdown suppression, the 10x break speed and `climb`. The tag ships with `minecraft:cobweb` and the mod's own `neoorigins:temporary_cobweb`, so an Arachnid is not caught by their own web, and a pack that adds its own web to the tag gets all three behaviours rather than climbing alone. A second tag, `#origins:cobwebs`, is read separately by the cobweb passthrough that the compat `origins:prevent_block_selection` power installs (a web stops blocking what you aim at), and it lists `origins:temporary_cobweb` as well, so add a modded web there too for crosshair passthrough.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `climb` | bool | no | `false` | When true, a cobweb the player is inside works as a ladder. |

**Example:**
```json
{
  "type": "neoorigins:cobweb_affinity",
  "climb": true,
  "name": "Spider Affinity",
  "description": "Move through, break and climb cobwebs easily."
}
```

---

## `neoorigins:bounce_on_land`

Reflects the player's downward impact velocity back upward on landing, mimicking a slime block: a slime-morphed player springs off the ground after a fall. The bounce fires only on the airborne→ground transition, using the impact speed captured the tick before landing (by the landing tick the player's own velocity has already been collision-clamped to ~0). The new velocity is pushed to the client through the same packet path vanilla uses for server-applied knockback, so the launch feels responsive.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `restitution` | number | no | `0.8` | Fraction of downward impact velocity reflected back up on landing; `<1` damps each successive bounce so the player settles. |
| `min_velocity` | number | no | `0.63` | Minimum downward speed (blocks/tick) needed to trigger a bounce, so jumps and short drops don't micro-bounce; 0.63 is the floor a 4-block fall clears and a 3-block fall does not. |
| `max_velocity` | number | no | `1.6` | Cap on the upward launch speed (blocks/tick) so terminal-velocity falls don't fling the player absurdly high. |

Notes:

- **Sneaking suppresses the bounce**: matching slime-block behavior and giving players a deliberate way to stop bouncing.
- **Running speed carries through the bounce.** The launch keeps the horizontal speed the player landed with, so landing mid-sprint springs them onward instead of stopping them where they land.
- Pair with `neoorigins:prevent_action` (`"action": "fall_damage"`) so the impact driving the bounce doesn't also hurt.

**Example: springy slime body**
```json
{
  "type": "neoorigins:bounce_on_land",
  "restitution": 0.8,
  "name": "Springy Body",
  "description": "Like a slime, you rebound off the ground after a fall. Hold sneak to stay put."
}
```

---

## `neoorigins:water_breathing`

Grants permanent Water Breathing. The player never loses air while underwater.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `enabled` | bool | no | `true` | Kill switch: when false the power stays attached but the player breathes normally. Usually flipped from `power_overrides.toml` rather than the datapack. |

**Example:**
```json
{
  "type": "neoorigins:water_breathing",
  "name": "Gills",
  "description": "Can breathe underwater indefinitely."
}
```

---

## `neoorigins:breath_in_fluid`

Drains the player's air supply when their eyes are submerged in the specified fluid. Useful for fire-themed origins that "drown" in water. Surfacing (head above water) restores air normally via vanilla breathing. While submerged, fully overrides vanilla's air management (suppresses both vanilla drowning and water-breathing refill), so the configured drain rate is the sole authority on how fast air depletes. Respiration enchantment extends survival time. Drown damage (2 HP/sec) applies once air is exhausted.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `fluid` | string | no | `water` | Fluid: `water` or `lava` |
| `air_loss_per_second` | int | no | — | Intuitive form: how many air points are lost per second. Higher = faster drain. Internally converted to ticks via `20 / value`. |
| `drain_interval_ticks` | int | no | — | Ticks between each air drain. Higher = slower drain. Equivalent to the legacy `drain_rate`. |
| `drain_rate` | int | no | `20` | Legacy alias for `drain_interval_ticks`. Resolution priority: `air_loss_per_second` > `drain_interval_ticks` > `drain_rate` > default 20 (one decrement per second). |

**Example:**
```json
{
  "type": "neoorigins:breath_in_fluid",
  "fluid": "water",
  "air_loss_per_second": 1,
  "name": "Hydrophobic",
  "description": "Cannot breathe in water."
}
```

---

## `neoorigins:breath_out_of_fluid`

Drains the player's air supply while they are **not** submerged in the specified fluid: a fish out of water. Once the air supply reaches 0, vanilla drown damage applies. For the water form, rain, a bubble column and standing in a water cauldron all count as being in the fluid. Water Breathing and Conduit Power effects pause the drain, and drinking a water bottle restores half the air bar. Vanilla only grants Conduit Power to a player who is already in water or rain, which would put it out of reach of the one situation this power creates; the mod lifts that restriction for players holding this power, so an active conduit reaches them anywhere in its normal radius and the air bar refills while they stand there. Compatible with Create's Copper Backtank (worn in chestplate slot, consumes pressurized air to pause drain). Respiration enchantment extends land time using the same probability curve vanilla uses underwater. `fluid: lava` is the bare check: none of these reprieves reach it, only being in lava itself.

The drain rate is per-power. Set one of the three fields below and that power drains at your rate; omit all three and the power falls back to the global `ocean_origins.drain_rate_ticks` config, which is how the four built-in `*_dries_out` powers are written. If a player holds more than one of these powers, the tightest interval wins.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `fluid` | string | no | `water` | Fluid the player must stay in: `water` or `lava` |
| `air_loss_per_second` | int | no | config | Highest priority: air points lost per **second** (higher = faster drain) |
| `drain_interval_ticks` | int | no | config | Ticks between each 1-point drain (higher = slower drain; 20 = 1s) |
| `drain_rate` | int | no | config | Legacy alias for `drain_interval_ticks`, lowest priority |

Air starts at 300, so land time in seconds is roughly `(300 × drain_interval_ticks) / 20`: `1` gives 15s (vanilla cod parity), `2` gives 30s, `4` gives a minute.

**Count bars, not bubbles.** The interval is ticks per single air **point**, and the HUD draws 300 air as 10 bubbles, so one bubble is 30 points. At `1` a bubble lasts 1.5s and the whole bar 15s; at `10` a bubble lasts a full 15s and the bar 150s. Both figures have been reported as bugs by players reading the bubble as the unit, so it is worth checking which one you are timing. If your bar empties far slower than the table says, check `config/neoorigins/gameplay.toml`: `ModConfigSpec` writes that file once and never re-defaults it, so a world that was played before the default changed from `10` to `1` still holds the old value. As of 2.2.24 the mod corrects that one case itself on the first start and stamps the file with a `config_version`, after which the setting is yours: put it back to `10` and it stays there.

**Example: aquatic origin that drowns on land**
```json
{
  "type": "neoorigins:breath_out_of_fluid",
  "fluid": "water",
  "name": "Dries Out",
  "description": "Suffocates when not in water."
}
```

> Pair with `neoorigins:water_breathing` so the player never loses air underwater while the bubble row depletes on land.

---

## `neoorigins:conduit_power_on_land`

An active conduit gives the holder Conduit Power on dry land too. Vanilla only grants it to players in water or rain. The power does not grant the effect on its own: a conduit still has to be built and active within its usual range. Origins' Merling power of the same name.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| (no fields) | — | — | — | Marker power. |

**Example:**
```json
{
  "type": "neoorigins:conduit_power_on_land",
  "name": "Sea Blessed",
  "description": "Conduits empower you on land as well as in water."
}
```
