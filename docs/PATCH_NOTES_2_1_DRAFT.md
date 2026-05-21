# NeoOrigins 2.1 — Patch Notes (DRAFT)

> ℹ️ Draft — not the published changelog. This file is updated freely until
> v2.1.0 cuts.

---

## v2.1.1

### Bug Fixes

- **Origin Creator name/description rejected on save as "Not a string"** —
  the creator writes `name` / `description` as `{"text": "..."}` component
  JSON so author-entered text renders literally rather than as a
  translation key, but the field codec accepted only raw strings and
  validation failed on every save with
  `Not a string: {"text": "..."}`. The codec now accepts either form:
  raw strings continue to wrap as translatable components (so every
  shipped origin still resolves through the language file), and any
  vanilla component-JSON object decodes through
  `ComponentSerialization.CODEC`. Same helper is shared by the player
  Origin Creator, the Mob Origin Creator, and any custom
  `origin_layer` JSON.

---

## v2.1.0

### In-Game Origin Creator

A fresh tabbed GUI for authoring player origins without hand-writing JSON.
Identity / Powers / Appearance / JSON tabs. The form renderer covers every
native power type — a structured schema branch where one exists, falling back
to codec reflection for the rest, so there are no gaps. Output writes a real
datapack at `<world>/datapacks/neoorigins_custom/`; saved origins flow through
the normal datapack reload, and the GUI has an explicit Apply button so
authors control the reload hitch.

- **Item picker** for the icon field — registry-backed and searchable, so
  modded items appear free. Optional SNBT components.
- **Per-field hover tooltips** sourced from the schema and `docs/schema/field_docs.json`
  so every form field has at least a short hint.
- **Raw-JSON escape hatch** — per-power "raw" toggle for authors who want to
  drop straight to JSON for a single power.
- **Live JSON preview** mirrors the draft as it would land on disk; a built-in
  problems panel runs the same validation the server applies on save.
- **Server-authoritative writes** — open / save / apply gate on permission level
  ≥ 2 or creative (integrated server only), so non-OP clients can't drive the
  creator on a dedicated server.

Opened via `/neoorigins editor` or the "Open Origin Creator" keybind
(unbound by default).

### Mob Origin System

Origins for mobs. Pack authors and ops can attach a bundle of powers + custom
drops to any non-player `LivingEntity`. Origins live at
`data/<namespace>/origins/mob_origins/<id>.json`.

- **Weighted spawn rules** — per-origin filters for entity type / entity tag,
  biome, biome tag, structure, structure tag, dimension, Y range, light range,
  time-of-day, and the spawn-reason set; mutex groups so two origins can't both
  attach to the same spawn; optional `replace` flag for overriding another
  origin mid-spawn.
- **`neoorigins:mob_behavior` power** — configurable per-origin aggression.
  Modes: neutral / hostile / conditional (triggered by an entity-condition
  list with throttled re-evaluation). Wires the vanilla
  `NearestAttackableGoal` / `HurtByTargetGoal` automatically.
- **Per-origin drops** — additive or replace; either independent-chance per
  entry or weighted-pool sampling. Layered onto vanilla loot via a global loot
  modifier. Carrier files live in the world datapack for portability — copy
  `neoorigins_custom/` to another NeoOrigins instance and drops keep working.
- **Spawn-egg minting** — `/neoorigins mob egg <origin> [entity_type] [count]`
  mints a vanilla spawn egg pre-attached to the origin. Right-clicking the
  egg on a vanilla spawner reconfigures the spawner's next-spawn so every
  subsequent spawn inherits the origin too.

### In-Game Mob Origin Creator

Same Creator framework as the player side, one package over. Identity /
Powers / Spawn Rules / Drops / JSON Preview tabs. Open with `/neoorigins mob
editor` or the "Open Mob Origin Creator" keybind (unbound by default).

### Cross-Mod Status-Effect Reactions

`neoorigins:action_on_event` gains a new `effect_applied` event so pack
authors can react to (and probabilistically cancel) any mob effect landing
on the player — including effects from other mods. Pre-dispatch filters by
exact id (`effect`) or tag (`effect_tag`); the usual `condition` block runs
after the filter for things like a `random_chance` resistance roll. Cancel
the effect with the existing `neoorigins:cancel_event` action.

- **Post-cleanse grace window** — optional `immunity_ticks` field grants N
  ticks of full immunity to the same effect id after a successful cancel,
  so probabilistic resistance feels like a real cleanse: a 90% roll holds for
  ~2 seconds rather than re-rolling on every individual bite.
- **Use case** — drop-in compat with infection-style mods (e.g. Fungal
  Infection: SPORE's `spore:mycelium_ef`) without a bespoke power type per
  mod. Same hook covers debuff-pruning antidotes, "resist this potion"
  talents, particle reactions to incoming buffs, and so on.

### Bug Fixes

- **Mob-origin spawn egg crashed the server on world save** when held in
  inventory. The egg's NBT was missing a required `id` field that vanilla's
  `ENTITY_DATA` codec validates at encode time.
- **Mob-origin spawn eggs didn't attach the origin in survival.** Vanilla's
  spawn-egg NBT-injection is gated to creative + permission 2; the minted
  egg now spawns the mob through a custom path that bypasses the gate and
  applies the origin directly on the returned entity.
- **Mob-origin spawn eggs didn't propagate the origin to vanilla spawners.**
  The spawner's next-spawn block didn't copy the egg's `ENTITY_DATA`; the
  egg now reconfigures the spawner with the origin marker so every
  subsequent spawn inherits it.
- **All origins silently failed to load on world startup (26.1 only)** — the
  icon codec read item components that weren't bound yet during the early
  datapack reload, NPE'd, and aborted parsing for the entire origin. All
  origins fell out silently with a `0 origins loaded` log line. Origins now
  load with empty icons during the early reload and re-resolve their icons
  on `ServerStarting` once components are bound.
- **Elytrian-style flight powers triggered the vanilla elytra wind sound**
  even when the player wasn't wearing an elytra. The fall-fly sound is now
  suppressed unless the chest slot actually contains `minecraft:elytra`.
- **Piglin "Fire Ward" was redundant** — base Piglin already had blanket
  fire/lava immunity, so the tier-3 evolution overlay added nothing. Base
  immunity removed; the apex overlay actually grants new resistance now.
- **Hiveling "Liftoff" and Elytrian "Fragile Frame" had no display names.**
  Lang keys added.
- **Origin re-selection bypass** — non-OP players could reset their chosen
  origin for free via `/origin gui` or a crafted `ChooseOrigin` packet,
  skipping the Orb of Origin XP cost and orb consumption. Re-selection now
  requires an Orb commit, an OP-granted re-selection, or sender-OP.
- **Creator validation false-rejected modded ids** — origins referencing
  modded items / attributes / entities or dynamic registry contents (biomes,
  dimensions) were incorrectly flagged as invalid on save. Validation now
  consults the live `RegistryAccess`.
- **Blacksmith / Cook crafted equipment lost its base stats.** Quality
  Equipment Power was seeding from the raw `ATTRIBUTE_MODIFIERS` data
  component, which is usually empty on freshly-crafted vanilla items
  (their base stats come from the item's default modifiers, not a
  component patch). The subsequent component-set wiped the base armor
  toughness, mining speed, or attack damage and left only the Blacksmith
  bonus on top — so a crafted iron chestplate would show toughness 1
  with no armor value. Now seeds from `ItemStack#getAttributeModifiers()`
  (the resolved effective modifiers), so base stats are preserved and
  the quality bonus stacks on top.
- **`/attribute` commands targeting vanilla attribute ids returned
  "Can't find element"** when this mod was loaded. The legacy-command
  rewriter (which fixes 1.20-era Origins++ mcfunction syntax to 1.21+)
  was running on every command, including modern ones, and silently
  corrupting attribute references in the process. Now gates on whether
  the original command already parses cleanly — if vanilla can resolve
  it, the rewriter leaves it alone and only intervenes on commands
  vanilla rejects.
- **Size-scaling powers left the player permanently rescaled after an
  origin change.** The clear-on-revoke step was using a per-power id
  derived at dispatch time, which doesn't always resolve to the same
  value during a revoke triggered by an origin swap or orb reroll —
  the modifier we added and the one we tried to remove didn't match,
  so the scale stuck. Now clears any `neoorigins:size_*` modifier by
  prefix sweep across the scale and interaction-range attributes,
  regardless of which power originally set it.
- **`neoorigins:crop_harvest_bonus` duplicated logs from world-gen
  trees, stripped logs, and player-placed log walls.** The bonus is
  intended for chopping naturally-grown wood. It now skips stripped
  logs entirely (registry-id prefix check on `stripped_`) and tracks
  logs placed by hand in a per-chunk attachment so breaking your own
  log walls back down doesn't dupe the materials. Naturally-generated
  trees and player-grown trees from saplings still earn the bonus —
  tree generation places logs via the feature system, not the player
  place path.

### Commands & Config

- **Canonical NeoOrigins command surface is now `/neoorigins`.** The
  `/origin` command-tree alias is no longer registered (it claimed the
  Origins mod's namespace, which isn't ours). Tab-complete and `/help` will
  only suggest `/neoorigins ...`. Existing mcfunctions or chat habits that
  still use `/origin set @p ...`, `/origin mob apply ...`, etc. continue to
  work via the compat layer — `LegacyCommandRewriter` transparently rewrites
  the leading verb so dispatch succeeds. The real Origins-mod-compat
  commands (`/resource`, `/power`) are untouched.
- **New `/neoorigins mob` subtree** — `apply`, `clear`, `get`, `editor`,
  `egg`. Permission level 2 required.
- **In-game origin creator gated** — open / save / apply requires permission
  level ≥ 2 or creative (integrated server only); rate-limited per player to
  prevent payload spam against the reload pipeline.
- **Custom-pack file paths hardened** — the writer enforces a strict id
  grammar and verifies all output stays inside the `neoorigins_custom/`
  folder before writing anything.

### Documentation

- New `docs/MOB_ORIGINS.md` — pack-author reference for the mob origin
  format (every field of `MobOrigin`, `EntityTargetSpec`, `SpawnRules`,
  `DropRules` documented with type, default, example).
- New `effect_applied` event entry in `docs/EVENTS.md`, with its filters and
  grace-window semantics.
- `docs/POWER_TYPES.md` — extended the `neoorigins:action_on_event` field
  table with `block_condition`, `effect`, `effect_tag`, `immunity_ticks`;
  added `EFFECT_APPLIED` to the event categories; new example showing 90%
  mycelium resistance with a 2-second grace window.
- Schema (`docs/schema/power.schema.json`, `field_docs.json`) extended for
  the new fields.
