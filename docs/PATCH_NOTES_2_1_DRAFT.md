# NeoOrigins 2.1 — Patch Notes (DRAFT)

> ℹ️ Draft — not the published changelog. This file is updated freely until
> v2.1.0 cuts.

---

## v2.1.3

### Bug Fixes

- **`origins:set_resource` action was a silent no-op.** The legacy
  compat layer rewrote `origins:set_resource` to
  `neoorigins:set_resource` but the action dispatcher had no arm
  for it, so packs porting from Origins++ saw the action listed
  as "unknown" with no effect. Wired the dispatch arm to read
  `resource` + `value` (or fallback `change`) and write through
  `CompatAttachments.resourceState()`. Same commit aliases
  `xp_levels` to the existing `xp_level` condition (Apoli's
  plural form) and adds a new `saturation_level` entity condition
  reading `FoodData.getSaturationLevel()`.
- **`origins:flame_particles` failed codec validation.** Route B's
  WELL_KNOWN synth emitted a fieldless `neoorigins:particle` stub
  and `ParticlePower.CODEC` rejected the entry with
  `particle: missing or unknown 'particle' field`, dropping the
  whole power. Synth now passes `minecraft:flame` as the default
  particle id so legacy `flame_particles` references load.
- **Legacy `forge:` attribute ids resolved to nothing on
  NeoForge.** Packs authored against Forge-era Origins++ pinned
  attributes like `forge:generic.entity_reach`, which NeoForge
  re-registered under `neoforge:entity_reach`.
  `AttributeModifierPower.resolveAttribute` now retries any
  unresolved `forge:*` id under the `neoforge:` namespace with
  `generic.` / `player.` prefix permutations, so existing packs
  don't need to be rewritten.
- **Origin layer screens displayed the raw layer id** instead of
  the layer JSON's `name` field. `OriginInfoScreen.getLayerDisplayName`
  and `OriginEditorScreen.drawLayerLabels` both fell back to the
  translation key / capitalized path even when the layer had a
  populated `name`. Both screens now read `layer.name()` first,
  only falling back to the translation key / prettified path if
  blank.
- **Warding Presence ignored its toggle.** The spawn-cancel
  handler used `forEachOfType`, which doesn't consult
  `AbstractTogglePower.isToggledOff` — turning the power off in
  the HUD still suppressed mob spawns. New
  `forEachOfTypeActive` helper on `ActiveOriginService` gates
  toggleable powers; event handlers reading toggle-style powers
  must use it. (Only `onTick` honors the toggle automatically.)
- **Warding Presence was a no-op for monsters at the default
  radius.** The power's radius default was 24, at or below
  vanilla's `MobCategory.MONSTER` 24-block player-distance
  spawn rule — so monsters were already blocked by vanilla
  and the power did nothing for the case authors actually
  wanted. Stoneguard's JSON now sets radius 36 explicitly; the
  code default stays at 24 to force pack authors to opt in to
  a meaningful radius.
- **`longer_potions` didn't extend the first dose.**
  `MobEffectEvent.Added` fires *before* vanilla
  `LivingEntity#addEffect` inserts the instance into
  `activeEffects`. The old handler re-called `sp.addEffect(extended)`
  from inside the event; the nested put ran first, then the
  outer vanilla put overwrote with the original un-extended
  instance. Net result: first dose looked normal, subsequent
  doses extended correctly via the merge path. Now mutates the
  incoming `MobEffectInstance` via `update()` so vanilla's
  subsequent put sees the already-extended values.
- **Cook food bonus stripped `eatSeconds`, consume effects, and
  `usingConvertsTo`** (1.21.1 only). The bonus rebuilt
  `FoodProperties` through `FoodProperties.Builder`, which only
  exposes nutrition / saturation / `canAlwaysEat` — so suspicious
  stew lost its random potion effect when boosted, golden apple
  lost regen, bowls/bottles no longer returned an empty container.
  New `rebuildFood()` helper goes through the builder for the
  bonus fields then constructs the record directly to copy the
  remaining fields through unchanged. **Wayfarer's smoker bonus
  (`MoreSmokerXpPower`) gets the same treatment**, and re-reads
  the FOOD component before rebuilding so it stacks cleanly on
  top of Cook's earlier rebuild when a player has both. The bug
  is 1.21.1-specific: 26.1's `FoodProperties` record no longer
  carries `eatSeconds` / `usingConvertsTo` / `effects` (Mojang
  moved them to `DataComponents.CONSUMABLE` and
  `DataComponents.USE_REMAINDER`), so the boost path never
  touches them and the original symptom can't reproduce.
- **`MobsIgnorePlayer` had no way to disable retaliation.** By
  default the power lets vanilla's `getLastHurtByMob()` window
  apply — once the player hits an "ignoring" mob, the mob is
  allowed to target back briefly so combat feedback loops still
  work. Pack authors who wanted true peace (Bonewalker, druidic
  neutrality, etc.) had no knob to turn that off. New
  `passive: true` config flag bypasses the retaliation window
  so the mob never targets the player even if attacked first.
  Default `false` keeps existing pack behavior intact.
- **`[CompatB]` parser warnings deduped per reload.** Pack-port
  sessions were dumping hundreds of `WARN` lines during datapack
  load — one per occurrence of every unsupported
  action/condition/modifier op. One porting log had 268 such
  lines (22% of bootup output) that collapsed into 6 unique
  action types + 4 unique condition types. New
  `CompatWarningCollector` batches parser-side warnings
  (unsupported actions / conditions / item-actions /
  item-conditions, modifier defaults, parse errors, malformed
  SNBT, per-power compile failures) during a session held open
  by `OriginsCompatPowerLoader.apply()`, then emits one sorted
  summary block at the end. Outside a session each `record*`
  call falls back to immediate `LOGGER.warn`. Wrapped in
  try/finally so a mid-reload throw can't leak the session.
- **Dropped orphaned lang keys** (`skeleton_apex_hp`,
  `skeleton_rattling`, `merling_no_drowning`) with no power JSON
  or origin reference, and broadened the stamina power
  description across all 10 pools from "Physical energy for
  powerful attacks." to "Physical reserves spent on active
  abilities." (The old line lied for Stoneguard (utility-only)
  and Shulk (CC + mobility) and was misleadingly narrow for the
  rest.)
- **Caveborn / Skeleton edible-power JSONs ate without hunger
  gating.** The six `EdibleItemPower` configs for stone, iron,
  gold, diamond, netherite, and bone meal shipped with
  `always_edible: true`, so the hunger check at
  `InteractionPowerEvents.java:115` short-circuited and the
  right-click animation fired even at a full food bar — players
  could spam-eat the mineral indefinitely. Flipped to `false` on
  all six. The codec default in `EdibleItemPower.java` stays
  `true` so external packs that omit the field keep their
  current behavior.
- **Caveborn mineral effects (haste / speed / luck / strength +
  resistance) never applied after eating off-tier minerals.**
  The 4 `caveborn_*_bonus` powers listened on
  `event: item_use_finish`, but `food_item_in_tag` reads the
  dispatch context as a `FoodContext`. Per
  `InteractionPowerEvents.java:172–177`, `ITEM_USE_FINISH`
  dispatches the raw `ItemStack`; only `FOOD_FINISHED` wraps it
  in `FoodContext`. The cast failed silently so the `if_else`
  action took the noop branch and no status indicator appeared.
  Switched all 4 to `food_finished`, matching the working
  `aquatic_fish_diet_bonus` template.

### Documentation

- New `docs/ORIGINS.md` — per-origin reference covering every
  default origin and its evolution tree. Each entry has impact
  rating, icon, spawn behavior (where applicable), base-power
  list with one-line glosses, and a tier-delta table for the
  evolution path. Catalogues all 49 non-class origins.
- `docs/POWER_TYPES.md` and `field_docs.json` aligned with the
  new `no_mob_spawns_nearby` 24-block code default. The old
  docs advertised 48 and used a 48-block example; now reflect
  the 24 default, call out the vanilla 24-block cutoff, and use
  a 36-block example matching Stoneguard's own JSON.
- `MobsIgnorePlayer` field docs updated to add the `passive` row
  + an example combining `#tag` syntax with `passive`, and to
  clarify that `entity_types` already accepts `#tag` references
  and that an empty list matches every mob (both were true
  before but undocumented).

## v2.1.2

### KubeJS Integration

Soft dependency on KubeJS 2101.7.x — when KubeJS is installed, pack
authors can hook into origin and power lifecycle from JavaScript and
register their own power behaviors without writing Java. When KubeJS
is absent the mod runs unchanged (every fire site short-circuits on a
single cached load check; no reflection, no class-not-found risk).

> ℹ️ **1.21.1 build only for this release.** KubeJS hasn't published a
> Minecraft 26.1 build yet, so the 26.1 jar ships without the
> integration. It'll land on 26.1 the release after KubeJS cuts a
> 2601.x build.

- **15 events** covering origin lifecycle (chosen / changed),
  power lifecycle (granted / revoked / activated / tick), evolution
  (tier_changed / declined), mob origin (assigned / cleared), and
  mount (requested / accepted / declined / started / ended). Fired from
  central choke points so admin commands and cascade invalidations are
  covered, not just user-driven paths. `power_tick` is gated by
  `hasListeners()` so zero overhead when no script subscribes.
- **`neoorigins:kubejs_callback` entity action** — JS-registered
  `Consumer<ServerPlayer>` invokable by id from any action slot the DSL
  already supports (`active_ability.on_use`, `action_on_event`,
  `condition_passive`, `chance`, `choice`, …). Register via
  `NeoOrigins.registerCallback('mypack:foo', player => { ... })`.
- **JS-defined power types** — `neoorigins:js_custom` (passive,
  `onGranted` / `onRevoked` / `onTick`) and `neoorigins:js_active`
  (extends the AbstractActivePower contract, `onUse` returning a
  boolean to consume cooldown/hunger). Register a handler object
  literal — Rhino auto-adapts to the Java interface, missing hooks
  default to no-ops.
- **`mount_ended` covers all dismount paths** — vanilla sneak-dismount,
  vehicle death, server stop, and force-mount swap all fire via an
  `Entity#removePassenger` mixin, scoped to mounts created by our power
  (non-empty `mountPosition` attachment) so vanilla horses don't trip
  the event.
- **Hot-reload safe** — `/kubejs reload` wipes registered callbacks
  and JS power handlers via the plugin's `clearCaches` hook.

### Mount Power

New `neoorigins:mount` active power — ride any living entity, with a
consent system for player-to-player mounts.

- Raycast pickup with configurable range; press the keybind a second
  time while riding to dismount.
- **Player consent** with three modes: `ALWAYS` (immediate),
  `TEAM` (immediate if same FTB Teams team or Open Parties & Claims
  party — falls through to prompt otherwise), and `PROMPT` (chat
  request with clickable `[ACCEPT]` / `[DECLINE]`, configurable
  timeout). Configured under `[mount]` in `neoorigins-common.toml`.
- **Mob mounts** — `allow_mobs` toggle, `block_bosses` flag to refuse
  Wither / Ender Dragon, and a check that the target isn't already
  carrying a passenger.
- **`mount_position`** field — `centered` (default) or `shoulder`,
  offsetting the passenger ~0.4 blocks to one side.
- `/neoorigins mount accept` and `/neoorigins mount decline` resolve
  pending prompts. Soft compat: FTB Teams and Open Parties & Claims
  detection is gated behind the same load-check pattern as our other
  compats, so neither mod is required.

### Internal

- **Origin layer canonicalized on `neoorigins:origin`.** The layer id
  emitted for built-in origins is now namespaced under our mod
  consistently instead of straddling `origins:origin` and
  `neoorigins:origin`. Existing user packs that pin the old layer
  continue to load through the compat translator.

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
