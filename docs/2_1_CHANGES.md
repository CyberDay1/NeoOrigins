# NeoOrigins 2.1 — Change Document

**Status:** in progress. Player Origin Creator landed; Mob Origin System P1–5
landed both branches; P6 (Live mode) pending; in-game smoke debt tracked per phase.
**Last updated:** 2026-05-20
**Audience:** contributors. Pack-author docs live at [`MOB_ORIGINS.md`](MOB_ORIGINS.md)
and [`PACK_FORMAT.md`](PACK_FORMAT.md).

> ℹ️ This document is a Phase-5 outline. Architecture / file-list / twin-port-delta
> sections fill in as Phase 6 lands.

## Mission

Add an **in-game authoring surface** for origins (players + mobs) plus a runtime
control surface for mob origins (spawn-time application, drops, behavior).

**Non-negotiables:**
1. Server-authoritative writes — `CreatorAccess.canUse(sp)` gates every open / save /
   apply payload; the client UI gates too but server is truth.
2. Pack output is portable — copy `<world>/datapacks/neoorigins_custom/` to another
   instance with NeoOrigins installed and it keeps working.
3. Dual-branch parity (1.21.1 + 26.1); twin-port every phase commit.
4. NeoOrigins-specific commands live ONLY under `/neoorigins`. `/origin` is the
   Origins-mod namespace — leave it to the compat layer.

## Architecture additions

### Player Origin Creator (player track, Phases 0–4)

_[TODO: 4 tabs (Identity / Powers / Appearance / JSON), hybrid schema/codec form
renderer, datapack-on-disk writer, atomic stageAndCommit, schema-driven validator,
load/clone/override/manage foundation.]_

### Mob Origin System

- `api/mob_origin/` records — `MobOrigin`, `EntityTargetSpec`, `SpawnRules`,
  `DropRules`, `DropEntry`, `IntRange`, `TimeOfDay`
- `data/MobOriginDataManager` — `SimplePreparableReloadListener` registered last
  in the data reload chain (`power_data` → `origins_compat_b` → `origin_data` →
  `layer_data` → `mob_origin_data`)
- `attachment/MobOriginData` — per-`LivingEntity`, no `copyOnDeath` (mob death
  re-rolls). `MAP_CODEC` form for the 26.1 attachment-builder signature.
- `event/MobOriginEventHandler` — `@FinalizeSpawnEvent` for SpawnRules eval +
  spawn-egg marker check; `@PlayerInteractEvent.RightClickBlock` for survival
  spawn-egg path; throttled `mob_behavior` re-evaluation
- `power/builtin/MobBehaviorPower` — `neoorigins:mob_behavior`. Aggression
  (NEUTRAL/HOSTILE/CONDITIONAL), `hostile_when` condition list, retaliate +
  anger linger, vanilla `NearestAttackableTargetGoal` / `HurtByTargetGoal`
  add/remove via `onGranted` / `onRevoked`
- `screen/mobcreator/` — `MobOriginCreatorScreen` + 5 tabs (Identity / Powers /
  Spawn Rules / Drops / JSON Preview). Reuses `CreatorHost`, `CreatorStyle`,
  `SearchPickerOverlay`, `ItemPickerOverlay`, `PowerFormPanel`, `ScrollPanel`,
  `FieldWidgetFactory` from the player side
- `service/MobOriginService` — power apply/remove on mobs; `appliesToMobs(C)`
  filter on `PowerType`
- `service/MobOriginSpawnEggService` — Phase 4d. Builds vanilla `<type>_spawn_egg`
  ItemStack with ENTITY_DATA marker. Spawner-target path uses cached reflection on
  `BaseSpawner.nextSpawnData` (cross-branch portable; no AT machinery)
- `service/MobLootModifierGenerator` — Phase 5c. Writes carrier files into the
  world datapack: `data/neoorigins/loot_modifiers/mob_origin_drops.json` +
  `data/neoforge/loot_modifiers/global_loot_modifiers.json`. Atomic; idempotent
  (skip-if-byte-identical)
- `event/MobOriginDropsLootModifier` + `MobOriginLootModifiers` — `LootModifier`
  subclass registered on `GLOBAL_LOOT_MODIFIER_SERIALIZERS`. Fast-path returns
  unchanged when no attachment / origin / drops
- `command/OriginCommand` mob subtree — `apply`, `clear`, `get`, `egg` (Phase 1–4);
  `live {apply,spawn,template}` + `editor` (Phase 6, pending)

## Phase status

| Phase | Status | Notes |
|---|---|---|
| 1: data + manager + attachment + filter + command | DONE both branches | in-game verified |
| 2: spawn-time application + sync | DONE both branches | in-game verified |
| 2b: `neoorigins:mob_behavior` power | DONE both branches | in-game verified |
| 3: Creator GUI (Identity + Powers + JSON) | DONE both branches | in-game verified |
| 4: Spawn Rules tab + spawn-egg minting (4a/b/c/d + 4 hotfixes) | DONE both branches | in-game verified |
| 5: Drops tab + loot modifier + carrier gen (5a/b/c) | DONE both branches | **smoke OWED** |
| 6: Live mode + `/neoorigins mob {live,editor}` | NOT STARTED | next |

## Twin-port deltas (recorded for future ports)

_[TODO: roll up the per-phase 26.1 deltas — `ResourceLocation` → `Identifier`
(class rename, package unchanged); `MAP_CODEC` attachment serialize; reload-
listener id; `MobSpawnType` → `EntitySpawnReason`; `CustomData` → `TypedEntityData`;
`Entity.getTags()` → `entityTags()`; `GuiGraphicsExtractor` + `drawString` →
`text` + no `flush()`; `ClientPacketDistributor` for C→S; `IdentifierArgument`;
`LootModifier(conditions, priority)` (codecStart P1 → P2); `LootContext.getParamOrNull`
→ `getOptionalParameter(ContextKey<T>)`. Full table after Phase 6 lands.]_

## Phase 5 commit ledger (`2.1` / `master`)

- 5a `1509e654` / `9ec37d10` — DropEntry/DropRules codec extension + draft fields
  + serializer drops block + customPackCheck §8–10
- 5b `d08d50cb` / `1b7fdcde` — MobDropsTab UI (header cycles + 32-row cap +
  per-row ItemPickerOverlay + ScrollPanel)
- 5c `25cc783e` / `3782eeb2` — MobOriginDropsLootModifier + MobOriginLootModifiers
  DeferredRegister + MobLootModifierGenerator + CustomPackWriter post-write hook
  + ServerStartingEvent safety net

_[TODO: prior-phase commit ledgers if useful for contributors.]_

## Files of note

_[TODO: per-package file list mirroring 2_0_CHANGES.md "Files of note" section.]_

## Launch checklist (Phase 6 → v2.1.0 release)

_[TODO: smoke matrix, deploy targets, version bump checklist, patch-notes draft
sign-off, twin-tag command, Modrinth + CurseForge publish steps.]_
