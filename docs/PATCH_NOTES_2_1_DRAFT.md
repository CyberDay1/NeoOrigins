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

## Reworks

_[TODO]_

## Bug fixes

_[TODO: spawn-egg hotfixes #1-#4 from the Phase 4d landing — crash on inventory
save, survival NBT gate, post-spawn apply ordering, spawner block reconfigure.
26.1-only IconCodec NPE + post-bind icon reload. Elytra wind sound suppression
when fall-flying without an elytra.]_

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
