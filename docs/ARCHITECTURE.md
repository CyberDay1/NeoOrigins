---
title: Architecture
nav_order: 12
---

# NeoOrigins — Architecture

## Overview

NeoOrigins is a NeoForge 1.21.11 mod that implements an Origins-style ability system.
Players choose an origin at first login; each origin grants a set of passive and active powers.
The mod also loads `.origins`-format packs (Route A + Route B compat layer) so that existing
Origins content packs work without modification.

---

## Data Pipeline

Data is loaded server-side via three hot-reloadable `SimplePreparableReloadListener` instances.
**Load order is critical** (declared in `NeoOrigins.java`):

```
power_data  →  origins_compat_b  →  origin_data  →  layer_data
    │                 │                  │                │
PowerDataManager  OriginsCompat     OriginDataManager  LayerDataManager
                  PowerLoader
```

Why this order?
- `OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP` is populated during `power_data`.
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
parsing. There are two translation routes:

### Route A — Static JSON Rewrite (`OriginsPowerTranslator`)

```
JSON file  →  OriginsFormatDetector  →  OriginsMultipleExpander  →  OriginsPowerTranslator
                 (detect ns)              (expand multiple)           (rewrite type + fields)
           →  codec parse  →  PowerHolder
```

`OriginsPowerTranslator` maps ~20 Origins power types to NeoOrigins equivalents. Translations
that lose information are marked with `// [LOSSY]` so they are grep-able.

### Route B — Dynamic Lambda Compilation (`OriginsCompatPowerLoader`)

Power types not handled by Route A are compiled into `CompatPower` lambdas by
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
- `NOOP_ACTION` — unknown Route B action type is silently skipped (safe)
- `FALSE_CONDITION` — unknown Route B condition type suppresses the ability (safe)

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
    isActive()   → type.isActive(config) → isActive() → isActivePower()
```

### Base classes

| Base class | Purpose |
|---|---|
| `AbstractActivePower<C>` | Cooldown-gated active abilities; `execute()` returns `boolean` (true = consume cooldown) |
| `PersistentEffectPower<C>` | Status effects that reapply every tick (NightVision, Glow pattern) |

`AbstractActivePower` sets `isActivePower() = true`, so all subclasses are automatically
recognised as active powers without any per-class override.

### Cooldown system

All cooldowns are stored in `PlayerOriginData` (NeoForge attachment, session-only):
- `data.isOnCooldown(typeId, tickCount)`
- `data.setCooldown(typeId, tickCount, durationTicks)`
- Cooldown key = `getClass().getName()` (unique per power class)

---

## Active Power Slots

Four keybind slots (V / G / N / B by default) map to slot indices 0–3.

Slot assignment flow:
1. `ActiveOriginService.activePowers(player)` collects all `PowerHolder`s where `isActive()` is true
2. Powers are ordered by `(layerId, powerIndex)` — deterministic across reloads
3. Slots 0–3 are assigned in order; any extras are silent (no slot)

Client sends `ActivatePowerPayload(slot)`. Server calls `activePowers.get(slot).onActivated()`.
A per-slot 5-tick debounce prevents key-spam without blocking adjacent slots.

---

## Player State

```
PlayerOriginData  (NeoForge attachment, survives respawn)
  origins: LinkedHashMap<String layerId, Identifier originId>
  cooldowns: HashMap<String typeId, Integer expiryTick>
  shadowOrbs: List<BlockPos>

ClientOriginState  (client-side cache, synced via SyncOriginsPayload)
```

Network payloads:
| Payload | Direction | Purpose |
|---|---|---|
| `SyncOriginsPayload` | S→C | Push all origins to client after login/reload |
| `ChooseOriginPayload` | C→S | Player confirms an origin selection |
| `OpenOriginScreenPayload` | S→C | Server tells client to open the selection screen |
| `ActivatePowerPayload` | C→S | Player pressed a skill keybind |

---

## Event Handler Structure

Event handlers are split into focused files under `event/`:

| File | Handles |
|---|---|
| `PlayerLifecycleEvents` | `onPlayerTick`, `onPlayerLogin`, `onPlayerRespawn` |
| `CombatPowerEvents` | `onLivingDamage`, `onLivingDeath`, `onLivingKnockBack`, `onProjectileImpact`, `onMobEffectApplicable` |
| `MovementPowerEvents` | `onLivingFall`, `onBreakSpeed`, `onItemUseStart` |
| `WorldPowerEvents` | `onLivingChangeTarget`, `onFinalizeSpawn`, `onLivingHeal`, `onBlockBreak` |

All event handlers use `ActiveOriginService` for power traversal — no direct map iteration.

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
    │       origin, powerNames, powerDescs, contentHeight
    │       OriginDetailViewModel.compute(Identifier selectedId)
    │
    └─ OriginListEntry  (list row data class)
            id, displayName, namespace, isSectionHeader
```

`OriginSelectionPresenter.init()` re-queries pending layers without resetting
`currentLayerIndex` — this preserves selection state across screen resize events.

---

## Content Packs (`originpacks/`)

`OriginsPackFinder` mounts `originpacks/` as both server-data and client-resources pack source.
Packs can be JARs, ZIPs, or plain folders. No `pack.mcmeta` required.
`PackItemAutoRegistrar` auto-registers items found in originpack asset models before registry freeze.
