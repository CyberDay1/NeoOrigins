---
title: Architecture
parent: "Project & Internals"
nav_order: 1
---

# NeoOrigins: Architecture

## Overview

NeoOrigins is a NeoForge mod (MC 1.21.1, 26.1 and 26.2) that implements an Origins-style ability system.
Players choose an origin at first login; each origin grants a set of passive and active powers.
The mod also loads `.origins`-format packs (Route A + Route B compat layer) so that existing
Origins content packs work without modification.

---

## Data Pipeline

Data is loaded server-side via four hot-reloadable `SimplePreparableReloadListener` instances.
**Load order is critical** (declared in `NeoOrigins.onAddReloadListeners`). Further listeners
(mob origins, global power sets, entity groups, morphs, active UI theme) are registered after these four.

```
power_data  →  origins_compat_b  →  origin_data  →  layer_data
    │                 │                  │                │
PowerDataManager  OriginsCompat     OriginDataManager  LayerDataManager
                  PowerLoader
```

Why this order?
- `OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP` is populated during `power_data`, and `origins_compat_b` adds the Route B sub-power IDs to it.
- `OriginDataManager` reads this map to rewrite `origins:multiple` power references.
- `LayerDataManager` reads origin IDs which must already exist in `OriginDataManager`.

### Path scanning (each manager checks both formats)

| Manager | NeoOrigins path | Origins-compat path |
|---|---|---|
| `PowerDataManager` | `data/<ns>/origins/powers/` | `data/<ns>/powers/` |
| `OriginDataManager` | `data/<ns>/origins/origins/` | `data/<ns>/origins/` |
| `LayerDataManager` | `data/<ns>/origins/origin_layers/` | `data/<ns>/origin_layers/` |

Native-format files win on ID collision.

---

## Compat Translation Layer

When a power JSON has an `origins:` or `apace:` type namespace it is processed before codec
parsing (`apoli:` and `apugli:` types are first rewritten to `origins:` by
`OriginsFormatDetector.canonicalizePowerType`). There are two translation routes:

### Route A: Static JSON Rewrite (`OriginsPowerTranslator`)

```
JSON file  →  OriginsMultipleExpander  →  OriginsFormatDetector  →  OriginsPowerTranslator
               (expand multiple)          (canonicalize + detect)     (rewrite type + fields)
           →  LegacyPowerTypeAliases  →  power_overrides config  →  codec parse  →  PowerHolder
```

`OriginsPowerTranslator` maps 55 `origins:` power types (most also under `apace:`) to NeoOrigins equivalents. Translations
that lose information are marked with `// [LOSSY]` so they are grep-able.

### Route B: Dynamic Lambda Compilation (`OriginsCompatPowerLoader`)

Power types listed in `OriginsCompatPowerLoader.ROUTE_B_TYPES` are compiled into `CompatPower` lambdas by
`OriginsCompatPowerLoader`, using `ActionParser` and `ConditionParser`. The resulting
`PowerHolder<CompatPower.Config>` is injected into `PowerDataManager` via
`injectExternalPowers()`.

```
origins_compat_b reload  →  OriginsCompatPowerLoader.load()
  for each unhandled power JSON:
    ActionParser  →  EntityAction lambdas  (fail-open: unknown action → NOOP)
    ConditionParser → EntityCondition lambdas (fail-closed: unknown condition → FALSE)
    → CompatPower.Config(onTick, onActivated, condition, ...)
    → PowerDataManager.injectExternalPowers(id, holder)
```

**Fail policies** (see `CompatPolicy`):
- `NOOP_ACTION`: unknown Route B action type is silently skipped (safe)
- `FALSE_CONDITION`: unknown Route B condition type suppresses the ability (safe)

### Compat Translation Log

`CompatTranslationLog` writes `logs/neoorigins-compat.log` with `[PASS]`/`[FAIL]`/`[SKIP]`
per power. The log is opened in `PowerDataManager.apply()` and closed in
`OriginDataManager.apply()`.

---

## Power Type System

```
PowerType<C extends PowerConfiguration>   ← registered singleton (DeferredRegister)
    │
    ├─ isActivePower()  → false (passive default)
    ├─ onTick(ServerPlayer, C)
    ├─ onGranted(ServerPlayer, C)
    └─ onRevoked(ServerPlayer, C)
            │
            ▼
PowerHolder<C>   ← pairs type + parsed config
    isActive()   → type.isActivePower(config) → isActivePower()
```

### Base classes

| Base class | Purpose |
|---|---|
| `AbstractActivePower<C>` | Cooldown-gated active abilities; `execute()` returns `boolean` (true = consume cooldown) |
| `AbstractTogglePower<C>` | Keybind on/off toggles; off-state persisted in `PlayerOriginData` |
| `base.PersistentEffectPower<C>` | Abstract base for a permanent status effect. Nothing extends it today; the registered `persistent_effect` type is the separate `power/builtin/PersistentEffectPower` |

`AbstractActivePower` and `AbstractTogglePower` set `isActivePower() = true` (final), so all subclasses are automatically
recognised as active powers without any per-class override.

### Cooldown system

All cooldowns are stored in `PlayerOriginData`, in a map that is not serialized (session-only):
- `data.isOnCooldown(typeId, tickCount)`
- `data.setCooldown(typeId, tickCount, durationTicks)`
- Cooldown key = the dispatching power's id (`PowerHolder.currentDispatchId()`), falling back to
  `getClass().getName()` when there is none

---

## Active Power Slots

Six skill slots (Skill 1-4 on V / G / N / B by default, Skill 5-6 unbound) map to slot indices 0–5.

Slot assignment flow:
1. `ActiveOriginService.activePowers(player)` collects the origin-layer (non-class) `PowerHolder`s whose
   `occupiesHotkeySlot()` is true and that are not bound to a named hotkey via `"key"`
2. Powers are ordered by layer id (sorted), then the origin's declared power order, then
   dynamically granted powers; deterministic across reloads
3. Slots 0–5 are assigned in order; any extras are silent (no slot)

Client sends `ActivatePowerPayload(slot)`. Server calls `activePowers.get(slot).onActivatedByKeypress(player)`.
The class layer is separate: the Class Skill key (H) sends `ActivateClassPowerPayload`, which fires the
first entry of `ActiveOriginService.activeClassPowers(player)`. Named hotkeys send
`ActivatePowerByKeyPayload(translationKey, held)`.
A per-slot 5-tick debounce prevents key-spam without blocking adjacent slots.

---

## Player State

```
PlayerOriginData  (NeoForge attachment, survives respawn)
  origins: TreeMap<Identifier layerId, Identifier originId>
  activeCooldowns: ConcurrentHashMap<String cooldownKey, Integer expiryTick>  (not serialized)
  shadowOrbs: List<BlockPos>

ClientOriginState  (client-side cache, synced via SyncOriginsPayload)
```

Network payloads (selection):
| Payload | Direction | Purpose |
|---|---|---|
| `SyncOriginRegistryPayload` | S→C | Full origin / layer / power registry, so dedicated-server clients can render the GUI |
| `SyncOriginsPayload` | S→C | The player's layer → origin map |
| `ChooseOriginPayload` | C→S | Player confirms an origin selection |
| `OpenOriginScreenPayload` | S→C | Server tells client to open the selection screen |
| `ActivatePowerPayload` | C→S | Player pressed a skill keybind (slot 0–5) |
| `ActivateClassPowerPayload` | C→S | Player pressed the Class Skill key |
| `ActivatePowerByKeyPayload` | C→S | Player pressed or held a named hotkey |
| `SyncResourcePayload` | S→C | Full resource-bar sync (values plus label/bounds/color/FX metadata); sent at the chokepoints that can create or remove bars: login, power grant/revoke, datapack reload |
| `SyncResourceValuesPayload` | S→C | Lightweight value-only resource sync (key → value); sent on the high-frequency paths (10-tick dirty sync, immediate mutations from actions/commands) where only values change |

---

## Event Handler Structure

Event handlers are split into focused files under `event/` (20 files); the four general ones:

| File | Handles |
|---|---|
| `PlayerLifecycleEvents` | `onPlayerTick`, `onPlayerLogin`, `onPlayerRespawn` |
| `CombatPowerEvents` | `onLivingDamage`, `onLivingDeath`, `onLivingKnockBack`, `onProjectileImpact`, `onMobEffectApplicable` |
| `MovementPowerEvents` | `onLivingFall`, `onBreakSpeed`, `onItemUseStart` |
| `WorldPowerEvents` | `onLivingChangeTarget`, `onFinalizeSpawn`, `onLivingHeal`, `onBlockBreak` |

All event handlers use `ActiveOriginService` for power traversal: no direct map iteration.

---

## UI Architecture

```
OriginSelectionScreen  (rendering only — init/render/mouseScrolled)
    │
    ├─ OriginSelectionPresenter  (state + logic — no rendering imports)
    │       pendingLayers, currentLayerIndex, selectedOriginId
    │       searchText, allRows, filteredRows, listScrollOffset
    │       buildRows() / applySearch() / select() / confirm() / back() / randomId()
    │
    ├─ OriginDetailViewModel  (computed detail state — pure data)
    │       origin, powerNames, powerDescs, powerKeyTags
    │       OriginDetailViewModel.compute(Identifier selectedId, boolean classLayer)
    │
    └─ OriginListEntry  (list row data class)
            id, displayName, namespace, isSectionHeader
```

`OriginSelectionPresenter.init()` re-queries pending layers without resetting
`currentLayerIndex`. This preserves selection state across screen resize events.

---

## Content Packs (`originpacks/`)

`OriginsPackFinder` mounts `config/originpacks/` (or a legacy game-root `originpacks/` when
the config one does not exist) as both server-data and client-resources pack source.
Packs can be JARs, ZIPs, or plain folders. No `pack.mcmeta` required.
`PackItemAutoRegistrar` auto-registers items found in originpack asset models before registry freeze.
