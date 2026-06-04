---
title: "Events (action_on_event)"
parent: "DSL Reference"
nav_order: 5
---

# NeoOrigins 2.0 `action_on_event` Event Reference

`neoorigins:action_on_event` listens for one of the event keys below. When the
event fires, the power's `entity_action` (for action-style events) or
`modifier` (for `MOD_*` events) runs with the event's context in scope.

Source of truth for the key list:
[`EventPowerIndex.Event`](../src/main/java/com/cyberday1/neoorigins/service/EventPowerIndex.java).
Every key in the enum has a live `EventPowerIndex.dispatch(...)` call site.
Keys that earlier drafts exposed without a dispatch site have been removed
rather than left as silent no-ops; see the **Removed keys** section at the
bottom for the two that were dropped (and their replacements).

## Event shape

```json
{
  "type": "neoorigins:action_on_event",
  "event": "<KEY>",
  "condition": { ... },          // optional — entity-side gate
  "block_condition": { ... },    // optional — block-side gate (block events only)
  "entity_action": { ... },      // for action-style events
  "modifier": { ... }            // for MOD_* events; chains with other registered modifiers
}
```

`event` is the lowercase enum name (e.g. `"kill"`, `"mod_exhaustion"`).
`condition` is an `EntityCondition` evaluated against the player before the
action / modifier runs — use it for `origin:power_active`, item-in-hand,
fluid-in-eyes, and similar gates. `entity_action` and `modifier` may both be
set on one power; the dispatcher only calls whichever matches the site's call
shape.

`block_condition` is an optional gate that only applies when the event is
block-shaped (`block_break`, `block_place`, `block_use`, `bonemeal`). The dispatch
`BlockPos` is extracted from the event context and the predicate is evaluated
before the action runs. Supports `block` / `id` / `tag` fields, e.g.
`{ "type": "neoorigins:block", "id": "minecraft:stone" }`. On non-block events
the field is silently ignored. Use this instead of (or in addition to)
`condition` when you want to filter by what was broken/placed/used:

```json
{
  "type": "neoorigins:action_on_event",
  "event": "block_break",
  "block_condition": { "type": "neoorigins:block", "id": "minecraft:stone" },
  "entity_action": {
    "type": "neoorigins:drop_items",
    "items": [{ "item": "minecraft:emerald", "chance": 0.05 }]
  }
}
```

The event's context is published to `ActionContextHolder` for the duration of
the dispatch so context-aware action verbs (`neoorigins:damage_attacker`,
`neoorigins:cancel_event`, `neoorigins:food_item_in_tag`, ...) can read it
without needing to be parameterised at JSON authoring time.

---

# Action-style events

These run a side-effect action against the player actor when fired. Register
via `entity_action`.

## `attack`

Fires when the player attacks a living entity (pre-damage).

**Context:** the target `LivingEntity` itself — bientity conditions on the
power can read it as the target.

**Dispatch site:** `CombatPowerEvents.onAttackEntity` (hooks
`AttackEntityEvent`).

**Typical use:** apply mining fatigue / slow to anything you hit, charge a
rage meter, spawn a visual shockwave.

---

## `hit_taken`

Fires when the player takes damage (post-cancel, before final apply).

**Context:** `HitTakenContext(amount, source)` — the amount is the vanilla-
adjusted damage and the `DamageSource` is the original source. Action verbs
like `neoorigins:damage_attacker` read `amount` for amount-ratio thorns-style
retaliation.

**Dispatch site:** `CombatPowerEvents.onLivingDamage` (`LivingIncomingDamage`
victim branch).

**Typical use:** thorns/counter-damage, defensive-cooldown triggers,
hurt-noises, panic-mode buffs.

---

## `kill`

Fires when the player kills a living entity.

**Context:** `KillContext(killed)` — the `LivingEntity` that just died.
Bientity / entity-type conditions can filter on it.

**Dispatch site:** `CombatPowerEvents.onLivingDeath` (killer = ServerPlayer
branch).

**Typical use:** bloodthirst healing, kill-streak counters, soul-capture
mechanics.

---

## `death`

Fires when the player themself dies.

**Context:** none (`null`).

**Dispatch site:** `CombatPowerEvents.onLivingDeath` (dying-player branch,
fires before the killer branch).

**Typical use:** revival sigils (paired with `neoorigins:cancel_event`),
last-stand explosions, insurance payouts.

---

## `block_break`

Fires when the player successfully breaks a block (post-cancel).

**Context:** the `BlockEvent.BreakEvent` itself — readable by
`neoorigins:cancel_event` and any verb that reflects on the event object.

**Dispatch site:** `WorldPowerEvents.onBlockBreak`.

**Typical use:** XP from ores, mining-sound replacement, silk-touch
emulation.

---

## `block_place`

Fires when the player places a block.

**Context:** the `BlockEvent.EntityPlaceEvent`.

**Dispatch site:** `WorldPowerEvents.onBlockPlace`.

**Typical use:** place-and-enchant, build-mode dust particles,
builder-class XP.

---

## `item_use`

Fires at item-use **start** (right-click hold begins) for any item —
pre-prevent gate.

**Context:** the held `ItemStack`.

**Dispatch site:** `CompatEventPowers.onItemUseStart` (after the
`prevent_item_use` gate, only if not cancelled).

**Typical use:** rev-up animations, charging sounds, start cooldowns.

---

## `respawn`

Fires when the player respawns (after origin re-sync and
`modify_player_spawn` teleport).

**Context:** none.

**Dispatch site:** `PlayerLifecycleEvents.onPlayerRespawn`.

**Typical use:** gift-on-respawn, status-effect restore, hunger top-up.

---

## `tick`

Fires once per server tick for every online player.

**Context:** none.

**Dispatch site:** `PlayerLifecycleEvents.onPlayerTick` (`PlayerTickEvent.Pre`).

**Typical use:** anything periodic. Prefer the cheaper
`neoorigins:toggle` / `neoorigins:active_self` patterns where possible —
`tick` runs 20 times a second for every player.

---

## `dimension_change`

Fires when the player changes dimension.

**Context:** none. If you need the dimension ID, query
`player.level().dimension()` inside the action.

**Dispatch site:** `PlayerLifecycleEvents.onPlayerChangedDimension`.

**Typical use:** dimension-gated buffs, portal-sickness effects, re-equip
starter gear on Nether entry.

---

## `climb`

Fires once per server tick **while** the player is on a climbable block
(ladder, vine, scaffolding, ...).

**Context:** none. Query `player` state inside the action for finer detail.

**Dispatch site:** `PlayerLifecycleEvents.onPlayerTick` (gated on
`player.onClimbable()`, fired right after the generic `tick`).

**Typical use:** spider-class climb-speed buffs, wall-cling stamina drain,
fall-immunity-while-climbing. Like `tick`, this runs every tick the gate is
met — keep the action cheap and gate hard with `condition`.

---

## `jump`

Fires when the player jumps.

**Context:** none.

**Dispatch site:** `CombatPowerEvents.onLivingJump`
(`LivingEvent.LivingJumpEvent` filtered to `ServerPlayer`).

**Typical use:** double-jump priming, jump-boost particles, stamina drain.

---

## `projectile_hit`

Fires when a projectile owned by the player hits something (entity or block).

**Context:** `ProjectileHitContext(projectile, result)` — the projectile
entity and the `HitResult`. Check `result.getType()` for
`ENTITY` / `BLOCK` / `MISS`.

**Dispatch site:** `CombatPowerEvents.onProjectileImpact`.

**Typical use:** homing arrows, projectile-converts-to-lightning, ranged
debuff application.

---

## `craft_item`

Fires when the player crafts an item in a crafting grid (table or inventory).

**Context:** `CraftContext(stack)` — the crafted result `ItemStack`.

**Dispatch site:** `CraftingPowerEvents.onItemCrafted`
(`PlayerEvent.ItemCraftedEvent`).

**Typical use:** artisan-class craft XP, recipe-discovery rewards,
craft-and-enchant.

---

## `smelt_item`

Fires when the player removes a smelted item from a furnace / smoker / blast
furnace output.

**Context:** `CraftContext(stack)` — the smelted result `ItemStack`.

**Dispatch site:** `CraftingPowerEvents.onItemSmelted`
(`PlayerEvent.ItemSmeltedEvent`).

**Typical use:** smelting XP bonus, auto-quench effects, cook-class progress.

---

## `enchant_item`

Fires when the player applies enchantments at an enchanting table (post-apply).

**Context:** `CraftContext(stack)` — the freshly-enchanted `ItemStack`.

**Dispatch site:** `CraftingPowerEvents.onItemEnchanted`
(`PlayerEnchantItemEvent`).

**Typical use:** arcane-class enchant rituals, lapis refund, bonus-curse
application. Distinct from `mod_enchant_level`, which changes the *offered*
level before the player commits.

---

## `anvil_repair`

Fires when the player takes the repaired / combined output from an anvil.

**Context:** `CraftContext(stack)` — the finished `ItemStack`.

**Dispatch site:** `CraftingPowerEvents.onAnvilRepair`
(`AnvilCraftEvent.Post`). In 26.1 the legacy `AnvilRepairEvent` was removed;
the post-craft hook is now `AnvilCraftEvent.Post`, whose `getOutput()` carries
the result.

**Typical use:** smith-class repair XP, durability bonus on repair,
free-naming perks. Distinct from `mod_anvil_cost`, which only scales the XP
cost preview.

---

## `bonemeal`

Fires when the player applies bone meal to a block.

**Context:** `BlockInteractContext(pos, state)` — the bonemealed block's
position and state. `block_condition` filters by block type.

**Dispatch site:** `CraftingPowerEvents.onBonemeal` (`BonemealEvent`).

**Typical use:** druid-class growth particles, biome-spread effects,
bonemeal-as-fertilizer-XP. Distinct from `mod_bonemeal_extra`, which scales the
number of extra growth applications.

---

## `breed`

Fires when two animals the player bred produce a baby.

**Context:** `EntityInteractContext(child)` — the spawned baby
`LivingEntity`.

**Dispatch site:** `WorldPowerEvents.onBabyEntitySpawn`
(`BabyEntitySpawnEvent`, gated on a causing `ServerPlayer`). Dispatched before
the `twin_breeding` gate, so a `breed` power and `twin_breeding` can coexist.

**Typical use:** rancher-class breeding rewards, bonus-baby chance hooks,
breeding-streak counters.

---

## `tame`

Fires when an animal is tamed by the player.

**Context:** `EntityInteractContext(animal)` — the tamed `LivingEntity`.

**Dispatch site:** `WorldPowerEvents.onAnimalTame` (`AnimalTameEvent`, gated on
`getTamer() instanceof ServerPlayer`).

**Typical use:** beastmaster-class taming buffs, minion registration, tame
particle effects. For minion-tracking integration see the dedicated `tame_mob`
power.

---

## `food_eaten`

Fires at item-use start for any stack carrying a vanilla `FOOD` data
component. **Cancellable** via `neoorigins:cancel_event`.

**Context:** `FoodContext(stack, event)` — the food `ItemStack` plus the
underlying `LivingEntityUseItemEvent.Start` (so `cancel_event` can veto the
eat). `neoorigins:food_item_in_tag` reads the stack.

**Dispatch site:** `MovementPowerEvents.onItemUseStart` (only dispatched
when the stack has a FOOD component).

**Typical use:** vegetarian / carnivore restrictions, allergen damage,
bonus-effect-on-eat.

---

## `food_finished`

Fires when the player **finishes eating** a food item (post-eat). **Not cancellable** — the food has already been consumed. This is distinct from `food_eaten` which fires at eat-start and can cancel the eat.

Also synthetically fired by `EdibleItemPower` after a successful bite, so custom edible items trigger the same post-eat hooks.

**Context:** `FoodContext(stack, event)` — the consumed food `ItemStack`. Context-aware conditions like `neoorigins:food_item_in_tag` and `neoorigins:food_item_id` read the stack.

**Dispatch site:** `InteractionPowerEvents.onItemUseFinish` (`LivingEntityUseItemEvent.Finish`, only dispatched when the stack has a FOOD component).

**Typical use:** post-eat nutrition bonuses, food-specific buffs, bonus saturation for certain food types.

---

## `advancement_earned`

Fires when the player earns an advancement.

**Context:** `AdvancementContext(id)` — the advancement's `Identifier`.

**Dispatch site:** `PlayerLifecycleEvents.onAdvancementEarned`
(`AdvancementEvent.AdvancementEarnEvent`). Folded into the existing
advancement handler, so it fires for every earned advancement — gate with
`condition` if you only care about specific ones.

**Typical use:** milestone rewards, achievement-gated power unlocks,
progression-tied buffs.

---

## `trade_completed`

Fires when the player completes a trade with a villager or wandering trader.

**Context:** `TradeContext(offer)` — the `MerchantOffer` that was traded.

**Dispatch site:** `InteractionPowerEvents.onTradeCompleted`
(`TradeWithVillagerEvent`).

**Typical use:** merchant-class trade XP, haggle-streak counters,
trade-completion sound / particle.

---

## `villager_interact`

Fires when the player right-clicks a villager or wandering trader — a narrower
alias for `entity_use` that only matches `AbstractVillager` targets. Fired
*after* the generic `entity_use` so a power can target either granularity.

**Context:** `EntityInteractContext(target)` — the villager / trader
`LivingEntity`.

**Dispatch site:** `InteractionPowerEvents.onEntityUse` (the
`AbstractVillager` branch).

**Typical use:** charisma discounts, villager-specific dialogue hooks,
reputation gestures.

---

## `gained`

Fires when a power has just been granted to the player.

**Context:** the power's `Identifier` ID (as a raw `Object`).

**Dispatch site:** `ActiveOriginService.applyOriginPowers` (after
`onGranted` + `PowerGrantedEvent`).

**Typical use:** welcome-message broadcasts, one-shot starter equipment,
origin-pick particle effect. Fires every origin change — use
`condition` to gate on a specific origin.

---

## `lost`

Fires when a power has just been revoked from the player.

**Context:** the power's `Identifier` ID.

**Dispatch site:** `ActiveOriginService.applyOriginPowers` (after
`onRevoked` + `PowerRevokedEvent`).

**Typical use:** clean-up rituals, revoke-gained items, farewell message.

---

## `chosen`

Fires when the player picks an origin from the selection screen
(`ChooseOriginPayload`).

**Context:** the newly-chosen origin's `Identifier`.

**Dispatch site:** `NeoOriginsNetwork.ChooseOriginPayload` handler.

**Typical use:** first-choice welcome flow, origin-lock gates, server-wide
announcement. Fires on every pick — gate on `hadAllOrigins` if you only
want the first time.

---

## `wake_up`

Fires when the player wakes from sleeping.

**Context:** none.

**Dispatch site:** `PlayerLifecycleEvents.onPlayerWakeUp`.

**Typical use:** well-rested buff, dream-power cooldown reset.

---

## `land`

Fires when the player lands after a fall.

**Context:** the fall distance as a boxed `Float`.

**Dispatch site:** `MovementPowerEvents.onLivingFall` (runs after the
`prevent_action: FALL_DAMAGE` gate).

**Typical use:** landing shockwave, impact-damage scaling, parkour
streak reset.

---

## `block_use`

Fires when the player right-clicks a block (general — runs for every
`RightClickBlock` event, including cancelled ones from other mods' gates).

**Context:** `BlockInteractContext(pos, state)`.

**Dispatch site:** `InteractionPowerEvents.onBlockUse`.

**Typical use:** right-click enchant, block-specific rituals, class-lock
interactions.

---

## `entity_use`

Fires when the player right-clicks a living entity.

**Context:** `EntityInteractContext(target)`.

**Dispatch site:** `InteractionPowerEvents.onEntityUse` (filtered to
`LivingEntity` targets).

**Typical use:** healing touch, taming-by-class, entity-hug particle.

---

## `item_pickup`

Fires when the player picks up an item entity off the ground.

**Context:** the `ItemStack` that was picked up.

**Dispatch site:** `InteractionPowerEvents.onItemPickup`
(`ItemEntityPickupEvent.Post`).

**Typical use:** coin-magnet XP, allergy damage, auto-sort hooks.

---

## `item_use_finish`

Fires when the player **finishes** using an item (distinct from `ITEM_USE`
which fires at use-start). Also synthetically fired by
`EdibleItemPower` after a successful bite.

**Context:** the finished `ItemStack`.

**Dispatch site:** `InteractionPowerEvents.onItemUseFinish`
(`LivingEntityUseItemEvent.Finish`) and
`InteractionPowerEvents.onRightClickItem` (for `EdibleItemPower` consumes).

**Typical use:** post-eat buffs, empty-bottle return, drink-finish sound.

---

## `effect_applied`

Fires when a `MobEffect` is about to be added to the player, after vanilla
`EffectImmunityPower` / `EntityGroupPower` immunity rules but before the
effect lands. Cancelling with `neoorigins:cancel_event` denies the
application (calls `setResult(DO_NOT_APPLY)` on the underlying
`MobEffectEvent.Applicable`).

**Context:** `EventPowerIndex.EffectAppliedContext` — carries the
`MobEffectInstance`, its registry id, and the cancellable event.

**Dispatch site:** `CombatPowerEvents.onMobEffectApplicable`
(`MobEffectEvent.Applicable`).

**Filters:** the `action_on_event` config accepts two optional pre-dispatch
filters that work only on this event:
- `effect` — a single registry id (e.g. `spore:mycelium_ef`)
- `effect_tag` — a `TagKey<MobEffect>` (e.g. `#minecraft:harmful`; leading `#` optional)

If both are set they OR-match. If neither is set the action fires for every
effect that reaches dispatch.

**Post-cleanse grace (`immunity_ticks`, optional, default 0):** when the
action successfully cancels an EFFECT_APPLIED event AND `immunity_ticks > 0`,
opens a per-effect-id grace window. Subsequent EFFECT_APPLIED dispatches for
the same effect short-circuit to `DO_NOT_APPLY` until the window closes,
skipping the user's condition entirely. Lets pack authors smooth out
probabilistic cancels — a successful 50% `random_chance` cleanse with
`immunity_ticks: 40` means the player is fully immune for 2 seconds before
the next re-roll. The window max-merges across powers (multiple cleanses
take the longest expiry, not the latest) and is cleared on logout.

**Typical use:** infection resistance (probabilistic cancel via
`random_chance` + `cancel_event` + grace window); class-specific antidote
behaviour; sound/particle reactions to incoming buffs.

---

# Modifier-style events (return a float)

These chain modifiers on a vanilla float value. Base value enters, each
modifier transforms, result goes back to vanilla. Register via `modifier`
(or a list of them).

## `mod_exhaustion`

Scales hunger exhaustion (which drives food drain).

**Context:** `null`. The base value is the unscaled exhaustion delta.

**Dispatch site:** `FoodDataMixin.causeFoodExhaustion` (movement, damage,
mining, ...) **and** `FoodDataTickMixin.addExhaustion` (regen-tick
exhaustion). Both routes use the same event so a single `MOD_EXHAUSTION`
power covers both.

**Typical use:** draconic 1.5× drain, feline 1.3× drain, avian 0.25× drain.

---

## `mod_natural_regen`

Scales the amount the player heals from natural food-based regeneration.

**Context:** `null`. Base value is `event.getAmount()` from `LivingHealEvent`.

**Dispatch site:** `WorldPowerEvents.onLivingHeal`.

**Typical use:** fast/slow regen class modifiers, regen-on-sunlight, low-
HP healing buff.

---

## `mod_trade_price`

Per-player adjustment of a merchant offer's cost-A count. The chained result is
treated as the new absolute count (clamped to `[1, maxStackSize]`) and folded
into vanilla's `specialPriceDiff` channel — the same knob Hero of the Village
and villager reputation use — so it stacks on top of natural discounts and
shows correctly on both client and server.

**Context:** `TradeContext(offer)` — the `MerchantOffer`. Base value is the
currently-displayed cost-A count.

**Dispatch site:** `AbstractVillagerTradePriceMixin` (TAIL of
`AbstractVillager.setTradingPlayer`, applied on trade-screen open). The mixin's
HEAD inject resets the prior contribution on close so the delta never compounds
across repeated opens of the same wandering trader.

**Typical use:** charisma-class cheaper trades, cursed-class price hikes,
faction-discount perks.

---

## `mod_craft_amount`

Scales the count of the assembled crafting-grid result. The chained result is
treated as the new count (clamped to `[1, maxStackSize]`) and written to the
result slot in place, so vanilla's later `broadcastChanges` syncs it to the
client.

**Context:** the result `ItemStack`. Base value is the recipe's natural output
count.

**Dispatch site:** `CraftingMenuCraftAmountMixin` (RETURN of the static
`CraftingMenu.slotChangedCraftingGrid`, which covers both the 3×3 table and the
2×2 inventory grid). Server-only in 26.1 (the method's level parameter is a
`ServerLevel`).

**Typical use:** artisan-class double-output, efficient-crafting perks,
resource-multiplier classes.

---

## `mod_harvest_drops`

Multiplies extra-drop count when the player kills an animal.

**Context:** the killed `LivingEntity`. Base value is `1.0f`; final value
is rounded and `extraCopies = Math.max(0, Math.round(value) - 1)`.

**Dispatch site:** `CombatPowerEvents.onLivingDrops` (filtered to `Animal`
victims).

**Typical use:** hunter class double-drops, butcher bonus wool/leather.

---

## `mod_knockback`

Scales incoming knockback strength for the player as victim. A result
≤ 0 cancels the knockback entirely.

**Context:** `null`. Base value is `event.getStrength()`.

**Dispatch site:** `CombatPowerEvents.onLivingKnockBack`.

**Typical use:** heavy-frame knockback resistance, stone-armour no-push,
mid-air combat anti-knockback.

---

## `mod_potion_duration`

Multiplies the duration of status effects newly added to the player.
Re-entry guarded so the replacement `addEffect` doesn't recurse.

**Context:** the `MobEffectInstance` being added (readable for
effect-specific gating from conditions). Base value is `1.0f`.

**Dispatch site:** `CombatPowerEvents.onMobEffectAdded`.

**Typical use:** longer potions, shorter debuffs, witch-class effect
amplifier scaling (use with care — this scales duration, not amplifier).

---

## `mod_bonemeal_extra`

Adds extra bonemeal applications when the player uses bonemeal on a block.
Base value is `0f` (vanilla behaviour is one application total); the final
value is `Math.round(result)` and that many extra `performBonemeal` calls
run.

**Context:** the `BonemealEvent` itself.

**Dispatch site:** `CraftingPowerEvents.onBonemeal`.

**Typical use:** druid / forester class — 2× / 3× growth per bonemeal.

---

## `mod_enchant_level`

Modifies the level shown on an enchanting-table slot. Note: the
`EnchantmentLevelSetEvent` has no player reference, so NeoOrigins runs a
spatial query for players within 8 blocks of the table and dispatches to
each. First player whose modifier changes the level wins.

**Context:** the `EnchantmentLevelSetEvent`. Base value is the vanilla
level as a float.

**Dispatch site:** `CraftingPowerEvents.onEnchantmentLevelSet`.

**Typical use:** arcane class +3 enchant levels, cursed class -2.

---

## `mod_crafted_food_saturation`

**Additive** saturation bonus applied to any food item freshly crafted or
smelted by the player. Base value is `0f`; final bonus is added to the
item's existing `saturation` field. Values `≤ 0` are ignored.

**Context:** the resulting `ItemStack`.

**Dispatch site:** `CraftingPowerEvents.onItemCrafted` **and**
`onItemSmelted` (via `boostFoodIfCook`).

**Typical use:** chef class — crafted/smelted food gives +0.2 saturation.

---

## `mod_anvil_cost`

Multiplies the XP-level cost of an anvil repair / combine.

**Context:** the `AnvilUpdateEvent`. Base value is `1.0f`; final cost is
`max(1, (int)(originalCost * value))`.

**Dispatch site:** `CraftingPowerEvents.onAnvilUpdate`.

**Typical use:** artisan / smith class 0.5× anvil cost, cursed class 2×.

---

## `mod_teleport_range`

Scales the range of built-in `neoorigins:active_teleport` (the
replacement for the legacy `teleport_range_modifier`).

**Context:** `null`. Base value is the power config's declared `range`.

**Dispatch site:** `ActiveTeleportPower.execute`.

**Typical use:** ender class — +4 blocks teleport distance, cursed class
halved.

**Note:** only affects the built-in active-teleport power. Ender pearls
and mod teleports are not routed through this event.

---

## `mod_fall_damage`

Scales the fall-damage multiplier when the player lands. Chains on the event's
current multiplier so it stacks with feather-falling and similar effects. A
result `≤ 0` (or non-finite) zeroes fall damage; the final value is clamped to
`≥ 0`.

**Context:** the `LivingFallEvent`. Base value is `event.getDamageMultiplier()`.

**Dispatch site:** `MovementPowerEvents.onLivingFall` (runs after the `land`
dispatch and the `prevent_action: FALL_DAMAGE` gate).

**Typical use:** acrobat-class reduced fall damage, heavy-class increased
impact, partial fall mitigation that stacks with vanilla effects. For a full
no-fall immunity, prefer `prevent_action: FALL_DAMAGE`.

---

# Removed keys

Two keys that earlier drafts exposed were dropped because a better-fitting
mechanism already covers them — exposing a second, redundant handler would
only invite divergent behaviour:

- `mod_break_speed` → use the `break_speed_modifier` power (backed by the
  vanilla `player.block_break_speed` attribute, which auto-syncs to the
  client; the event route silently no-op'd because `PlayerEvent.BreakSpeed`
  fires client-side for the local player).
- `mod_xp_gain` → use the native `neoorigins:xp_gain_modifier` power (or the
  `origins:modify_xp_gain` alias inside the Apoli compat layer). Both feed the
  same `NumericModifierRegistry` XP_GAIN path with full Apoli modifier math
  (`addition` + `multiply_base` / `multiply_total`).

---

## Cross-reference

- [`EventPowerIndex.java`](../src/main/java/com/cyberday1/neoorigins/service/EventPowerIndex.java) — enum declaration and context records.
- [`ActionOnEventPower.java`](../src/main/java/com/cyberday1/neoorigins/power/builtin/ActionOnEventPower.java) — the power type that consumes these events.
- [`POWER_TYPES.md`](POWER_TYPES.md) — full power-type catalogue including `action_on_event`.
