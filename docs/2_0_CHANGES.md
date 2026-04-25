# NeoOrigins 2.0 — Change Document

**Status:** ready for 2.0.0 release. All phases landed; alpha.12 → alpha.37 polish + dedicated-server boot port complete on both branches.
**Last updated:** 2026-04-25
**Audience:** contributors and future-you. This is not a pack-author doc — user-facing docs
(`POWER_TYPES.md`, `PACK_FORMAT.md`) reflect the post-Phase 7 generic power types.

This document tracks what 2.0 changed, why, and the running state of each phase.

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
- Generic types landed: `ActiveAbilityPower`, `PersistentEffectPower`, `ConditionPassivePower`, `ActionOnEventPower`

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

### Phase 5 — Dormant alias tables

**Active ability (done):** `active_launch`, `active_dash`, `repulse`, `active_aoe_effect`, `active_swap`, `active_fireball`, `active_bolt`, `healing_mist` → `active_ability` with DSL action trees (8 of 15 eligible legacy types — `active_phase` is deliberately out of scope as a movement-state toggle, not an active ability). The remaining 7 stay standalone because the DSL can't express their runtime model: `active_teleport` / `active_recall` / `active_place_block` / `shadow_orb` / `ground_slam` / `tidal_wave`. Phase 7 may add raycast/cone/mob-AoE verbs and shrink that set. Lossiness: `active_fireball` alias fires a single projectile where legacy fired 3–4 with spread; `active_swap` alias targets the nearest entity in radius where legacy targeted the look-direction pick.

**Persistent effect (done):** `status_effect`, `stacking_status_effects`, `night_vision`, `glow`, `water_breathing` → `persistent_effect`. Field remaps build the effect-spec array from legacy shapes; `stacking_status_effects` forces `toggleable: false`, `water_breathing` adds `condition: origins:in_water` and hides the HUD icon.

**Attribute modifier (done):** `less_item_use_slowdown` → `attribute_modifier` with `condition: origins:using_item`. Phase 3 architectural work (condition + edge-triggered apply/remove) was already complete; six of the originally-scoped ten classes moved to `action_on_event` under Phase 6 (`hunger_drain_modifier`, `natural_regen_modifier`, `knockback_modifier`, `longer_potions`, `teleport_range_modifier`, `food_restriction`); two more (`break_speed_modifier`, `underwater_mining_speed`) are deliberately skipped because NeoForge's `PlayerEvent.BreakSpeed` only fires client-side; `no_slowdown` stays bespoke pending a slowdown-source DSL. `less_item_use_slowdown` alias is lossy for `item_type != "any"` (the item-type filter drops).

**Condition passive (done):** Six legacy environmental passives aliased to `condition_passive` by composing existing ConditionParser verbs (`origins:biome` tag, `origins:exposed_to_sun`, `origins:relative_health`, `origins:submerged_in`, combined via `origins:and` / `origins:or` / `origins:not`) with ActionParser verbs (`origins:apply_effect`, `origins:damage`, `origins:set_on_fire`, `origins:heal`):
- `biome_buff`, `damage_in_biome`, `damage_in_daylight`, `damage_in_water`, `burn_at_health_threshold`, and `regen_in_fluid` (reassigned from Phase 2).
Four legacy types stay standalone because they don't fit a tick-condition model: `mobs_ignore_player` + `no_mob_spawns_nearby` are event interceptors; `item_magnetism` needs a `pull_items` DSL verb; `breath_in_fluid` needs a `drain_air` verb.

**Scope note:** three legacy types originally lumped under Phase 2 don't semantically fit `persistent_effect` and have been reassigned:
- `breath_in_fluid` / `regen_in_fluid` → Phase 4 `condition_passive` (tick-driven fluid checks, not MobEffect applications)
- `effect_immunity` → Phase 6 `action_on_event` with a `cancel_event` entity_action (event canceler, not a persistent effect)

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

**Known alias lossiness — all three fixed via context-aware DSL extensions:**
- `thorns_aura`: the `neoorigins:damage_attacker` action now accepts an `amount_ratio` field that reads `HitTakenContext.amount` and applies the ratio faithfully (min 0.5). Alias maps `return_ratio` → `amount_ratio`.
- `action_on_hit_taken`: `min_damage` now wraps the inner action in `origins:if_else` gated by a new `neoorigins:hit_taken_amount` context-aware condition.
- `food_restriction`: item-tag filter is expressed via a new `neoorigins:food_item_in_tag` context-aware condition that reads `FoodContext.stack`. Whitelist mode wraps it in `origins:not`.

### Phase 7 — Legacy class retirement (in progress)

**First cut (done):** deleted 14 loss-free classes — Phase 1 (`active_launch`, `active_aoe_effect`, `healing_mist`, `repulse`), Phase 2 (`status_effect`, `stacking_status_effects`, `night_vision`, `glow`), Phase 4 (`biome_buff`, `damage_in_biome`, `damage_in_daylight`, `damage_in_water`, `burn_at_health_threshold`, `regen_in_fluid`). Matching type IDs now route through `LegacyPowerTypeAliases` at load time.

**Second cut (done):** retired the 14 Phase 6 dual-path classes — pruned the `forEachOfType(player, XxxPower.class, ...)` legacy scan from each handler site and relied on the existing `EventPowerIndex.dispatchModifier` / `.dispatch` call that was already chaining. Classes deleted: `HungerDrainModifierPower`, `NaturalRegenModifierPower`, `KnockbackModifierPower`, `LongerPotionsPower`, `MoreAnimalLootPower`, `EfficientRepairsPower`, `BetterEnchantingPower`, `BetterCraftedFoodPower`, `BetterBoneMealPower`, `TeleportRangeModifierPower`, `ActionOnKillPower`, `ActionOnHitTakenPower`, `ThornsAuraPower`, `FoodRestrictionPower`. `FoodContext` gained an optional `event` field so `food_item_in_tag` conditions and `cancel_event` actions share a single dispatch context.

**Total Phase 7 deletions:** 28 classes — 88 → 60.

**Deferred:**
- Generalise `ConditionalPower` into the full `ConditionPassivePower` pipeline. Current shape is a wrapper around an `inner_power` lookup — semantically different from the tick-passive model; needs careful rework.
- Collapse `tick_action` into `condition_passive`. Current `TickActionPower` has externally-dispatched behavior (`TELEPORT_ON_DAMAGE` action-type resolved in `OriginEventHandler`), not a DSL action — aliasing requires that handler to move to event dispatch first.
- Remove the migration gradle task (still useful as a diagnostic; retire when no legacy IDs remain in internal JSON).
- User-facing `POWER_TYPES.md` rewrite to document `persistent_effect` / `condition_passive` / `action_on_event` / `active_ability` as the canonical types and drop retired entries.

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

- [x] Close the 3 known alias lossiness gaps (thorns ratio, food-tag condition, hit-taken min_damage)
- [x] Delete migrated legacy PowerType classes
- [x] Verify each dormant alias activates (JSON smoke tests)
- [x] Merge `ConditionalPower` into full `ConditionPassivePower`
- [x] Migrate `scare_entities` + `crop_harvest_bonus` (last 2 holdouts)
- [x] Update `docs/POWER_TYPES.md` — new generic types at top, legacy section marked deprecated with migration recipes
- [x] Update `docs/PACK_FORMAT.md` — note alias behaviour, deprecation timeline
- [x] Wire remaining enum entries or remove them (`MOD_FALL_DAMAGE`, `MOD_TRADE_PRICE`, etc.)
- [x] Phase 8 regression: full in-game playthrough with mixed legacy/new pack
- [x] Dedicated-server boot validated on both 1.21.1 and 26.1 (alpha.34+)
- [ ] Merge `2.0-dev` → `master`, tag `v2.0.0` *(awaiting `publish` command)*

---

## Phase 8 — alpha.12 → alpha.37 polish & dedicated-server port (2026-04-24/25)

The 2.0 line was singleplayer-tested through alpha.27. Pushing to a dedicated server surfaced classes of crash that singleplayer hides; alpha.28 → alpha.34 fixed those. alpha.35 → alpha.37 is post-boot polish (config audit, EnderMan re-target, mixin priority).

### Aquatic-origin overhaul (alpha.11 → alpha.24)

- **Dry-out mechanic** rewritten as Post-tick handler in `BreathOutOfFluidPower` with per-player virtual-air tracker, capability emission (`dries_out_of_water`), respawn cleanup, and direct drown damage on land. New `LivingEntityAirRefillMixin` cancels vanilla's `+4/tick` air refill so the drain actually progresses on land. New `PlayerWaterVisionMixin` short-circuits `LocalPlayer.getWaterVision` to clear underwater fog. `GuiHudBarsMixin` hides the bubble row when an aquatic origin is submerged.
- **Master configs**: `[ocean_origins].drain_rate_ticks` (default 10) and `drown_damage_per_second` (default 2.0). The per-power `drain_rate` JSON field on built-in `*_dries_out` powers is no longer respected — config drives all four origins globally.
- **Spawn placement**: `LocationCondition` tries water passes (ocean floor, water surface) **before** the land-column pass when `allow_ocean_floor` / `allow_water_surface` is set, and rejects land columns that lack sky access. Aquatic origins no longer spawn on tiny islands or in cave air-pockets in ocean biomes. `NeoOriginsNetwork.allFilled` now skips hidden / empty layers — disabling the class layer no longer strands the player in first-pick invulnerability.
- **Power list cleanup** (abyssal / merling / kraken / siren): drop `daylight_damage` (abyssal), `scare_ocean` (abyssal), and `regen_in_water` (abyssal / kraken / siren). Add `abyssal_aquatic_speed` (the only aquatic origin without one) and the shared `aquatic_fish_diet` + `aquatic_fish_diet_bonus` Pescivore set.
- **Fish-only diet**: cancels non-fish eat at `food_eaten` event-START (cancellable), and applies cooked-equivalent food/saturation bonuses for raw cod / salmon at the new `food_finished` event (post-eat, can't be exploited by releasing right-click). New `food_item_id` condition for per-item branches inside `if_else_list`. New `fish_foods` item tag at `data/neoorigins/tags/item/fish_foods.json`.
- **`scare_entities` water-mob fix**: was filtering by `instanceof PathfinderMob`, which excludes every `WaterAnimal` (cod, salmon, squid, dolphin). Loosen to `Mob` and add a velocity-push branch for water mobs whose `WaterBoundPathNavigation` silently fails on unreachable flee targets.

### New action: `throw_target` (alpha.25 → alpha.28)

Raycast the entity under the actor's crosshair and push them away horizontally + upward with tunable `force` / `vertical_lift` / `max_distance`. Documented in `ACTIONS.md` and `API.md`; schema enum updated. 26.1's `ProjectileUtil.getEntityHitResult` signature differs from 1.21.1 — added to `reference_26_1_api_map.md`.

### Dedicated-server boot port (alpha.28 → alpha.34)

Six classes of crash that only surface on dedicated server (singleplayer always has client classes already loaded). Captured in `feedback_dedicated_server_validation.md`. Key fixes:

- **`new ClientScreen(...)` opcode in common-side class** — `NeoOriginsNetwork.handleOpenEditorScreen` constructed `OriginEditorScreen` directly. RuntimeDistCleaner walks NEW opcodes during dist verification and tries to load the target; loading `OriginEditorScreen` triggers `Screen` load, rejected on dedicated server. Routed through `ClientOriginState.openEditorScreen()` trampoline.
- **NeoForge event class moved between minor versions** — 26.1.2.29-beta replaced inner `BlockEvent.BreakEvent` with top-level `net.neoforged.neoforge.event.level.block.BreakBlockEvent`. Bumped `deps.neoforge` to match server.
- **Mixin INVOKE owner mismatch on refactored vanilla method** — 26.1's `FoodData.tick` takes `ServerPlayer` instead of `Player`; INVOKE owner inside the method body shifted accordingly. Updated the `FoodDataNoRegenMixin` target.
- **PowerType class on disk but never registered** — `natural_glide` (26.1) and `hide_hud_bar` + `cobweb_affinity` (1.21.1) had Java classes but missed the `reg(...)` line in `PowerTypes`. Powers silently dropped with `Unknown power type` warnings; matching origins (Elytrian / Hiveling / Phantom flight on 26.1, Automaton hide-bars + Arachnid cobweb on 1.21.1) had no-op kits.
- **Mixin target rename** — `EnderMan.isLookingAtMe(Player)` was renamed to private `isBeingStaredBy(Player)` on 26.1 with a synced `DATA_STARED_AT` field. Re-targeted `EnderManLookMixin` (alpha.36).

### Mod-compat mixin priority bumps (alpha.37)

- `LightTextureMixin` and `PlayerWaterVisionMixin` priority raised to 1500 (default 1000) so they apply after mods like Alex's Caves that also mixin into the lightmap pipeline. Tester reported `enhanced_vision` broken under AC; this is the standard mitigation when two mods both write the lightmap.

### Config audit (alpha.35)

- Removed dead `power_overrides` entries for the four aquatic powers deleted earlier (`abyssal_daylight_damage`, `abyssal_regen_in_water`, `kraken_regen_in_water`, `siren_regen_in_water`).
- Added `abyssal_aquatic_speed` override.
- Renamed `breeze_wind_dash` field `strength` → `power` (was silently broken — JSON has top-level `power`).
- Removed `cinderborn_lava_regen` / `strider_lava_regen` / `umbral_active_dash` `amount` overrides — values are nested inside `entity_action` and the shallow override system cannot reach them. Comments left explaining; pack authors who want to retune copy the JSON.

### Validation status

- Both branches: dedicated server boots clean.
- Both branches: 1.21.1 + 26.1 commits land in `2.0-dev-1.21.1` / `2.0-dev` (5–6 commits each since alpha.10).
- Tester smoke-test of alpha.37 specific items pending: Enderian gaze immunity on 26.1, AC enhanced-vision compat, fish-diet bonus timing, master drain-rate config behaviour, ocean-floor spawn placement.

### Carry-forward / non-blocking

- **Sodium-specific enhanced-vision compat** — separate workstream, not yet attempted.
- **~130 orphan power JSONs** without `power_overrides` entries — only add when a tester asks for a knob.
- **Schema files** (`docs/schema/*.json`) — session additions (`throw_target`, `food_item_id`) wired into the appropriate enums. Pre-existing drift remains: condition schema is short several pre-2.0 verbs, action schema is short a few, and power schema lists ~half of the 74 power types. Out of scope for 2.0 launch; track separately.
