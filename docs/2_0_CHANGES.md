# NeoOrigins 2.0 — Running Change Document

**Status:** in-progress on `1.21.1` working tree + `2.0-dev` branch (uncommitted at time of writing).
**Last updated:** 2026-04-19
**Audience:** contributors and future-you. This is not a pack-author doc — user-facing docs
(`POWER_TYPES.md`, `PACK_FORMAT.md`) will be updated once the legacy classes retire in Phase 7.

This document tracks what 2.0 is changing, why, and the running state of each phase.
Update it whenever a phase completes a chunk of work.

---

## Mission

Collapse **88 PowerType classes → ~25 generic composable types** so that future powers are
authored as JSON configs against the composable types rather than as new Java classes.

**Non-negotiables:**
1. Every legacy JSON payload keeps working for at least 2 major versions.
2. Pack authors never see a hard break — old class IDs are aliased to new generic types.
3. Dual-path dispatch during the deprecation window: legacy Java class and new generic
   type both run side-by-side at every migrated event site.

---

## Architecture additions (vs. 1.x)

### `EventPowerIndex` — `service/EventPowerIndex.java`

O(1) event → handler dispatch, indexed per player + per event. Replaces linear
`ActiveOriginService.forEachOfType` scans for event-carrying powers.

- **`Event` enum** — 35 entries: lifecycle/combat (`ATTACK`, `HIT_TAKEN`, `KILL`, `DEATH`,
  `BLOCK_BREAK`, ...), Origins-Classes actions (`CRAFT_ITEM`, `FOOD_EATEN`, `BONEMEAL`, ...),
  and modifier-style events (`MOD_EXHAUSTION`, `MOD_KNOCKBACK`, `MOD_HARVEST_DROPS`, ...).
- **`Handler`** — `BiConsumer<ServerPlayer, Object>`; registered per player at
  `PowerType.onGranted` and removed at `onRevoked`.
- **`ModifierHandler`** — `(player, ctx, float base) -> float`; multiple handlers chain in
  registration order. Used for the `MOD_*` events.
- **Context records** — `HitTakenContext`, `KillContext`, `ProjectileHitContext`,
  `CraftContext`, `FoodContext`, `BlockInteractContext`, `EntityInteractContext`,
  `TradeContext`, `AdvancementContext`.
- **Lifecycle** — `invalidate(uuid)` on logout clears both action and modifier indexes.

### `ActionContextHolder` — `service/ActionContextHolder.java`

ThreadLocal that holds the event context currently being dispatched. Allows action verbs
to reach event-specific data (attacker from `HIT_TAKEN`, cancellable event from
`FOOD_EATEN`) without changing the `EntityAction = Consumer<ServerPlayer>` signature.

- `EventPowerIndex.dispatch` / `dispatchModifier` set the context around their handler
  loop and restore it afterwards — nested dispatches are safe.
- Context-aware action verbs read it: `damage_attacker`, `ignite_attacker`,
  `effect_on_attacker`, `cancel_event`.

### `ActionOnEventPower` — `power/builtin/ActionOnEventPower.java`

The generic event-hook power. Collapses 26 Origins-Classes hook powers into one.

```json
{
  "type": "neoorigins:action_on_event",
  "event": "food_eaten",
  "condition": { ... optional EntityCondition ... },
  "entity_action": { ... optional EntityAction ... },
  "modifier": { ... optional FloatModifier, or array of them ... }
}
```

- Custom `Codec<Config>` parses JSON via `ops.convertTo(JsonOps.INSTANCE, input)`.
- Eager DSL compilation at power-load: condition/action/modifier become closed-over
  lambdas stored on the config.
- `onGranted` registers action and/or modifier handlers separately (a single power can
  do both) and stores the tokens per-player-per-config so revoke is clean.

### `LegacyPowerTypeAliases` — `power/registry/LegacyPowerTypeAliases.java`

JSON rewrite table applied by `PowerDataManager` before codec parsing. Key guard:

```java
if (PowerTypes.get(typeId) != null) return typeId;   // stay dormant while Java class exists
```

All aliases are registered up-front but don't steal traffic until the corresponding
legacy Java class is deleted in Phase 7. One deprecation warning per unique old type
per boot (`WARNED` set).

---

## Phase status

### Phase 1 — Foundation (done)
- Client-side power sync infrastructure, capability-tag system
- Flight capability + dimension-change sync
- Wall-climb via `onClimbable` mixin
- Generic types landed: `ActiveAbilityPower`, `PersistentEffectPower`, `ConditionPassivePower`, `EventTriggeredPower`

### Phase 2 — Action/Condition DSL (done)
- `ActionParser` with ~30 verbs (Origins + Apace + NeoOrigins namespaces)
- `ConditionParser` with broad Apace compat
- `ModifierParser` supporting `add_base` / `multiplication` / `multiply_total_additive` /
  `set_total` / `min_total` / `max_total`

### Phase 3 — Native upgrades (done)
- Conditioned `modify_damage_taken` via Route B
- Native `invulnerability` (replaces lossy FIRE-only translation)
- Native `stacking_status_effects`
- `area_of_effect` iterates nearby players with shape + condition

### Phase 4 — Tooling (done)
- In-game origin editor and power tester GUI
- Debug screens for active powers / condition evaluation
- Pehkui scale bridge for sized origins
- Epic Fight compat mixins for sized origins

### Phase 5 — Active ability aliases (done)
- `active_launch`, `active_dash`, `repulse`, `active_aoe_effect` → `active_ability` with DSL action trees

### Phase 6 — Origins-Classes hook consolidation (in progress)

Collapses 26 modifier/action hook powers into `action_on_event`.

**Migrated (14 powers — dormant alias + dual-path dispatch):**

| Legacy type | Event | Dispatch site |
|---|---|---|
| `hunger_drain_modifier` | `MOD_EXHAUSTION` | `mixin/FoodDataMixin.neoorigins$modifyExhaustion` |
| `natural_regen_modifier` | `MOD_NATURAL_REGEN` | `event/WorldPowerEvents.onLivingHeal` |
| `knockback_modifier` | `MOD_KNOCKBACK` | `event/CombatPowerEvents.onLivingKnockBack` |
| `longer_potions` | `MOD_POTION_DURATION` | `event/CombatPowerEvents.onMobEffectAdded` |
| `more_animal_loot` | `MOD_HARVEST_DROPS` | `event/CombatPowerEvents.onLivingDrops` |
| `efficient_repairs` | `MOD_ANVIL_COST` | `event/CraftingPowerEvents.onAnvilUpdate` |
| `better_enchanting` | `MOD_ENCHANT_LEVEL` (additive) | `event/CraftingPowerEvents.onEnchantmentLevelSet` |
| `better_crafted_food` | `MOD_CRAFTED_FOOD_SATURATION` (additive) | `event/CraftingPowerEvents.boostFoodIfCook` |
| `better_bone_meal` | `MOD_BONEMEAL_EXTRA` (additive) | `event/CraftingPowerEvents.onBonemeal` |
| `teleport_range_modifier` | `MOD_TELEPORT_RANGE` | `power/builtin/ActiveTeleportPower.execute` |
| `action_on_kill` | `KILL` (action) | `event/CombatPowerEvents.onLivingDeath` |
| `action_on_hit_taken` | `HIT_TAKEN` (action) | `event/CombatPowerEvents.onIncomingDamage` |
| `thorns_aura` | `HIT_TAKEN` (action, lossy alias) | same |
| `food_restriction` | `FOOD_EATEN` (cancel, lossy alias) | `event/MovementPowerEvents.onItemUseStart` |

**Phase 6.5 — context-aware verbs (done):** `ActionContextHolder` + action verbs
`damage_attacker`, `ignite_attacker`, `effect_on_attacker`, `random_teleport`,
`cancel_event`. Enabled the last three migrations above.

**Not migrated (blocked):**
- `scare_entities` — TICK-based iteration over nearby mobs; shape mismatch with
  `EntityAction`. Belongs in `ConditionPassivePower` (Phase 7).
- `crop_harvest_bonus` — partly `MOD_HARVEST_DROPS` (drop count), partly a regrow-block
  action that needs `BlockInteractContext`. Needs a `neoorigins:regrow_crop` action verb
  or ConditionPassive migration.

**Skipped (deliberately — no viable event):**
- `break_speed_modifier`, `underwater_mining_speed` — attribute-based; `PlayerEvent.BreakSpeed`
  is client-side only in current NeoForge
- `craft_amount_bonus` — no reliable `ItemCraftedEvent` fire point; currently tick-polled
- `more_smoker_xp` — no furnace XP event in NeoForge 21.11.38

**Known alias lossiness (document pack-author limitations before retiring Java classes):**
- `thorns_aura` alias reflects a flat `amount` instead of ratio-of-incoming-damage. Fix:
  add an `amount_from: "hit_taken_amount"` field on `damage_attacker`, or a ratio multiplier.
- `food_restriction` alias cancels ALL food — loses the item-tag filter. Fix: add an
  item-tag condition that reads `FoodContext.stack` from `ActionContextHolder`.
- `action_on_hit_taken` alias drops `min_damage`. Fix: add context-aware condition
  reading `HitTakenContext.amount`.

### Phase 7 — Legacy class retirement (not started)
- Delete migrated legacy PowerType classes once the aliases are loss-free.
- Generalise `ConditionalPower` into the full `ConditionPassivePower` pipeline.
- Collapse `tick_action` into `condition_passive`.
- Grep audit for deleted class references across events/services.
- Remove the migration gradle task (currently disabled-writes mode).
- Update user-facing `POWER_TYPES.md` to document `action_on_event` and drop legacy entries.

### Phase 8 — Regression pass (not started)
- Requires runtime; deferred until after Phase 7.
- Full playthrough of all shipped origins with a pack mix of legacy + new JSON.
- Validate dormant aliases activate correctly when legacy classes retire.

---

## Dispatch sites still unwired in `Event` enum

Reserved for when the first consuming power lands. No current power depends on them.

| Enum entry | Status |
|---|---|
| `ATTACK` | wired, but no action/modifier power uses it yet |
| `CLIMB` | no NeoForge event; belongs in `ConditionPassivePower`, deferred |
| `MOD_FALL_DAMAGE` | unused |
| `MOD_TRADE_PRICE`, `TRADE_COMPLETED`, `VILLAGER_INTERACT` | unused |
| `ADVANCEMENT_EARNED` | `PlayerLifecycleEvents.onAdvancementEarned` handles origin upgrades but doesn't call `EventPowerIndex.dispatch` — wire when first action-on-advancement power lands |

---

## Compat guarantees

All Phase 6 migrations run **three paths in parallel** so nothing breaks during the
deprecation window:

1. **Legacy JSON + live Java class** — e.g. a pack still declaring
   `neoorigins:hunger_drain_modifier`. The Java class (`HungerDrainModifierPower`) is
   still registered in `PowerTypes`, so the JSON parses normally and the dispatch site
   still runs `ActiveOriginService.forEachOfType(sp, HungerDrainModifierPower.class, ...)`
   against it.
2. **Dormant alias** — same legacy JSON; `LegacyPowerTypeAliases.apply()` sees the old
   type ID but the dormancy guard (`if (PowerTypes.get(typeId) != null) return typeId;`)
   short-circuits so the alias never rewrites. Activates automatically the day the
   legacy Java class is deleted.
3. **New JSON** — a pack using `neoorigins:action_on_event` goes through
   `ActionOnEventPower.onGranted` which calls `EventPowerIndex.registerModifier` or
   `register`. The dispatch site calls `EventPowerIndex.dispatchModifier(...)` and
   the new chain runs alongside path (1).

Multipliers fold multiplicatively across paths (1) + (3) at every migrated site — a pack
mixing old and new modifiers for the same semantic produces the combined multiplier, not
either-or.

---

## Files of note

### New (2.0)
- `service/EventPowerIndex.java` — event dispatch core
- `service/ActionContextHolder.java` — ThreadLocal context bridge
- `power/builtin/ActionOnEventPower.java` — the Phase 6 generic type
- `power/builtin/ActiveAbilityPower.java` — Phase 1 active generic
- `power/builtin/ConditionPassivePower.java` — Phase 1 passive generic
- `power/builtin/EventTriggeredPower.java` — predecessor to `ActionOnEventPower` (kept during transition)
- `power/builtin/PersistentEffectPower.java` — Phase 1 persistent effect generic
- `power/registry/LegacyPowerTypeAliases.java` — the alias rewrite table
- `compat/modifier/ModifierParser.java` — FloatModifier DSL

### Modified (2.0)
- `compat/action/ActionParser.java` — +5 context-aware verbs
- `compat/condition/ConditionParser.java` — broadened Apace compat
- `data/PowerDataManager.java` — calls `LegacyPowerTypeAliases.apply` before codec parse
- `event/*.java` — every event class gained `EventPowerIndex.dispatch(...)` calls alongside legacy scans
- `mixin/FoodDataMixin.java` — `MOD_EXHAUSTION` dispatch
- `power/registry/PowerTypes.java` — registers the new generic types
- `service/ActiveOriginService.java` — cached per-player resolved power list

---

## Launch checklist (Phase 7 → 2.0 release)

- [ ] Close the 3 known alias lossiness gaps (thorns ratio, food-tag condition, hit-taken min_damage)
- [ ] Delete migrated legacy PowerType classes
- [ ] Verify each dormant alias activates (JSON smoke tests)
- [ ] Merge `ConditionalPower` into full `ConditionPassivePower`
- [ ] Migrate `scare_entities` + `crop_harvest_bonus` (last 2 holdouts) or formally skip with notes
- [ ] Update `docs/POWER_TYPES.md` — new generic types at top, legacy section marked deprecated with migration recipes
- [ ] Update `docs/PACK_FORMAT.md` — note alias behaviour, deprecation timeline
- [ ] Wire remaining enum entries or remove them (`MOD_FALL_DAMAGE`, `MOD_TRADE_PRICE`, etc.)
- [ ] Phase 8 regression: full in-game playthrough with mixed legacy/new pack
- [ ] Merge `2.0-dev` → `master`, tag `v2.0.0`
