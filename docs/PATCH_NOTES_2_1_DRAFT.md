# v2.1.0 — Patch Notes (DRAFT)

> ℹ️ Draft. Ship date is TBD; sections fill in as Phase 6 lands, the smoke run
> completes, and the version gets cut. This is **not** a published changelog
> until v2.1.0 ships — edits up to that point are free.

## Headline features

- **In-game Origin Creator** — Author player origins from a tabbed GUI without
  hand-writing JSON. Identity / Powers / Appearance / JSON tabs. Writes a real
  datapack at `<world>/datapacks/neoorigins_custom/`. The schema-driven form
  renderer covers every native power type (structured branch for ~19; codec
  reflection fallback for the rest — single guard, no gaps). _[TODO blurb polish
  + screenshot placeholder.]_

- **Mob Origin System** — Origins for mobs. Pack authors and ops can attach a
  bundle of powers + custom drops to any non-player `LivingEntity`.
  - **Weighted spawn rules** with location / time / light / Y / mutex filters.
  - **`neoorigins:mob_behavior` power** — configurable, piglin-style aggression.
    Modes: neutral / hostile / conditional (triggers re-evaluated on a throttled
    cadence using the existing entity-condition DSL).
  - **Per-origin drops** — additive or replace; either per-item independent
    chance or weighted-pool strategy. Layered onto vanilla loot via a global
    loot modifier; carrier files live in the world datapack for portability.
  - **Spawn-egg minting** — `/neoorigins mob egg <origin>` mints a vanilla
    spawn egg that spawns the mob with the origin pre-attached. Right-clicking
    the egg on a spawner reconfigures the spawner's next-spawn to inherit too.

- **Mob Origin Creator GUI** — Identity / Powers / Spawn Rules / Drops / JSON
  tabs in the same Creator framework as the player side. Open with
  `/neoorigins mob editor` _(Phase 6, pending)_ or the *Open Mob Origin Creator*
  keybind (default: unbound).

- **Live Mode (Phase 6, pending)** — Apply your in-progress draft to the mob
  you're looking at, spawn an instance pre-attached to your draft, or toggle a
  server-wide "active template" that rolls draft against every natural spawn
  matching its target. Rate-limited per player. _[TODO blurb after Phase 6
  lands.]_

- **Cross-mod status-effect reactions** — `action_on_event` gains a new
  `effect_applied` event so pack authors can react to (and probabilistically
  cancel) any mob effect landing on the player — including effects from other
  mods. Pre-dispatch filters by exact id (`effect`) or tag (`effect_tag`); the
  usual `condition` block runs after the filter for things like a
  `random_chance` resistance roll. Cancel an effect with the existing
  `neoorigins:cancel_event` action.
  - **Post-cleanse grace window** — optional `immunity_ticks` field grants
    N ticks of full immunity to the same effect id after a successful cancel,
    so probabilistic resistance feels like a real cleanse (a 90% roll holds
    for ~2 seconds rather than re-rolling on every individual bite).
  - **Use case** — drop-in compat with infection-style mods (e.g. Fungal
    Infection: SPORE's `spore:mycelium_ef`) without a bespoke power type per
    mod. Same hook covers debuff-pruning antidotes, "resist this potion"
    talents, particle reactions to incoming buffs, etc.

## Reworks

_[TODO]_

## Bug fixes

**Mob Origin spawn eggs (Phase 4d landing):**
- Fixed a crash on world save when a minted mob-origin spawn egg was in
  inventory. The egg's NBT was missing a required `id` field that vanilla's
  `ENTITY_DATA` codec validates at encode time.
- Fixed mob-origin spawn eggs not actually attaching the origin when used in
  survival. Vanilla's spawn-egg NBT-injection is gated to creative+op; the
  minted egg now spawns the mob through a custom path that bypasses the gate
  and applies the origin directly on the returned entity.
- Fixed mob-origin spawn eggs not propagating the origin when right-clicked
  on a vanilla spawner. The spawner's next-spawn block didn't copy the egg's
  ENTITY_DATA; the egg now reconfigures the spawner with the origin marker so
  every subsequent spawn inherits it.

**26.1 only:**
- Fixed origins (player and mob) silently failing to load at world startup.
  The icon codec's `new ItemStack(item)` call eagerly read item components
  that weren't bound yet during the early datapack reload, NPE'd, and aborted
  parsing for the entire origin. All origins fell out silently.
- Fixed mob-origin spawn-egg icons appearing empty after world load — paired
  with the above; icons re-resolve on server startup once components are
  bound.

**General:**
- Fixed Elytrian-style flight powers triggering the vanilla elytra wind sound
  when fall-flying without an actual elytra equipped.
- Fixed Piglin "Fire Ward" apex overlay being redundant — base Piglin no
  longer carries blanket fire/lava immunity, so the tier-3 overlay actually
  adds new resistance.
- Fixed missing display names for Hiveling's "Liftoff" jump-boost power and
  Elytrian's "Fragile Frame" -2 hearts penalty.

**Creator hardening:**
- Closed a server-side origin re-selection bypass. Non-OP players could reset
  their chosen origin for free via `/origin gui` or a crafted ChooseOrigin
  packet, skipping the Orb of Origin XP cost and orb consumption.
  Re-selection now requires an Orb commit, an OP-granted re-selection, or
  sender-OP.
- Added per-player Save/Apply cooldowns on the in-game origin creator to
  prevent payload spam against the reload pipeline.
- Tightened custom-pack file paths — the writer enforces a strict id grammar
  and verifies all output stays inside the `neoorigins_custom/` folder before
  writing anything.
- Fixed creator validation false-rejecting origins that reference modded ids
  (items, attributes, entities) or dynamic registry contents (biomes,
  dimensions).

## Behavior changes

- **Canonical NeoOrigins command surface is now `/neoorigins`.** The `/origin`
  command-tree alias is no longer registered (it claimed the Origins mod's
  namespace, which isn't ours). Tab-complete + `/help` will only suggest
  `/neoorigins ...`. Existing mcfunctions or chat habits that still use
  `/origin set @p ...`, `/origin mob apply ...`, etc. continue to work via the
  compat layer — `LegacyCommandRewriter` transparently rewrites the leading
  verb so dispatch succeeds. The real Origins-mod-compat commands (`/resource`,
  `/power`) are untouched.

## Compatibility notes

_[TODO: backward compat with 2.0.x JSON; no breaking renames in the power-type
DSL; addon power loaders unchanged.]_

## Issues closed

_[TODO: link issues as they resolve through smoke + Phase 6.]_
