# NeoOrigins 2.0 `action_on_event` Event Reference

`neoorigins:action_on_event` listens for one of the event keys below. When the
event fires, the power's `entity_action` (for action-style events) or
`modifier` (for `MOD_*` events) runs with the event's context in scope.

Source of truth for the key list:
[`EventPowerIndex.Event`](../src/main/java/com/cyberday1/neoorigins/service/EventPowerIndex.java).
Events with no `EventPowerIndex.dispatch(...)` call site anywhere in the tree
are flagged in the **Not yet wired** section at the bottom — pack-authored
powers that reference them will parse and register but never fire.

## Event shape

```json
{
  "type": "neoorigins:action_on_event",
  "event": "<KEY>",
  "condition": { ... },          // optional — gates the dispatch
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

## `gained`

Fires when a power has just been granted to the player.

**Context:** the power's `ResourceLocation` ID (as a raw `Object`).

**Dispatch site:** `ActiveOriginService.applyOriginPowers` (after
`onGranted` + `PowerGrantedEvent`).

**Typical use:** welcome-message broadcasts, one-shot starter equipment,
origin-pick particle effect. Fires every origin change — use
`condition` to gate on a specific origin.

---

## `lost`

Fires when a power has just been revoked from the player.

**Context:** the power's `ResourceLocation` ID.

**Dispatch site:** `ActiveOriginService.applyOriginPowers` (after
`onRevoked` + `PowerRevokedEvent`).

**Typical use:** clean-up rituals, revoke-gained items, farewell message.

---

## `chosen`

Fires when the player picks an origin from the selection screen
(`ChooseOriginPayload`).

**Context:** the newly-chosen origin's `ResourceLocation`.

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

# Not yet wired

The following enum values are declared in `EventPowerIndex.Event` but have
**no** `EventPowerIndex.dispatch(...)` or `dispatchModifier(...)` call site
anywhere in the tree as of this writing. A pack can register an
`action_on_event` power against any of these keys — the registration will
succeed, but the handler will never fire until the dispatch site is added.

| Key | Expected context | Notes |
|---|---|---|
| `CLIMB` | TBD | No climbing-tick hook yet; consider a `PlayerTickEvent` branch that checks `onClimbable()` edge-triggered. |
| `CRAFT_ITEM` | `CraftContext(result)` | `PlayerEvent.ItemCraftedEvent` handler exists (see `CraftingPowerEvents.onItemCrafted`) but only calls `boostFoodIfCook` → `MOD_CRAFTED_FOOD_SATURATION`; no `CRAFT_ITEM` dispatch. |
| `SMELT_ITEM` | `CraftContext(result)` | Same as above — `onItemSmelted` is wired for saturation bonus only. |
| `ENCHANT_ITEM` | TBD | Distinct from the table-level modifier `MOD_ENCHANT_LEVEL`; no post-enchant dispatch. |
| `ANVIL_REPAIR` | TBD | Distinct from the cost modifier `MOD_ANVIL_COST`. |
| `BREED` | `EntityInteractContext(target)` | `BabyEntitySpawnEvent` is handled by `WorldPowerEvents.onBabyEntitySpawn` for `TwinBreedingPower` but does not dispatch `BREED`. |
| `TAME` | `EntityInteractContext(target)` | No taming hook. |
| `ADVANCEMENT_EARNED` | `AdvancementContext(advancementId)` | `PlayerLifecycleEvents.onAdvancementEarned` handles origin upgrades but doesn't call `EventPowerIndex.dispatch`. |
| `TRADE_COMPLETED` | `TradeContext(offer)` | No trade completion hook. |
| `VILLAGER_INTERACT` | `EntityInteractContext(villager)` | Distinct from generic `ENTITY_USE`; no villager-specific filter is wired. |
| `MOD_BREAK_SPEED` | TBD | Replaced by vanilla `player.block_break_speed` attribute at the `BreakSpeedModifierPower` level — see `MovementPowerEvents` note. No dispatch exists. |
| `MOD_XP_GAIN` | TBD | No XP-orb pickup multiplier hook. |
| `MOD_TRADE_PRICE` | TBD | Needs a `MerchantEvent` / trade-offer hook. |
| `MOD_CRAFT_AMOUNT` | TBD | No craft-output multiplier hook. |
| `MOD_FALL_DAMAGE` | TBD | `LAND` fires on `LivingFallEvent`, but no modifier-style dispatch for fall-damage multiplier (cancelling fall-damage goes via `prevent_action: FALL_DAMAGE`). |

When wiring any of these, add the dispatch call site, pick the appropriate
context record from the bottom of `EventPowerIndex.java` (create a new one
if none fit), and move the row out of this table into the relevant section
above.

---

## Cross-reference

- [`EventPowerIndex.java`](../src/main/java/com/cyberday1/neoorigins/service/EventPowerIndex.java) — enum declaration and context records.
- [`ActionOnEventPower.java`](../src/main/java/com/cyberday1/neoorigins/power/builtin/ActionOnEventPower.java) — the power type that consumes these events.
- [`POWER_TYPES.md`](POWER_TYPES.md) — full power-type catalogue including `action_on_event`.
- [`2_0_CHANGES.md`](2_0_CHANGES.md) — architectural context for the 2.0 event-dispatcher consolidation.
