# NeoOrigins 2.0 `action_on_event` Event Reference

`neoorigins:action_on_event` listens for one of the event keys below. When the
event fires, the power's `entity_action` (for action-style events) or
`modifier` (for `MOD_*` events) runs with the event's context in scope.

Source of truth for the key list:
[`EventPowerIndex.Event`](../src/main/java/com/cyberday1/neoorigins/service/EventPowerIndex.java).
Keys wired in the 2.2.0 wave that don't have a full section yet are
summarised in the **Re-wired in 2.2.0** table at the bottom.

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
block-shaped (`block_break`, `block_place`, `block_use`). The dispatch
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

## `cancel_event` support

`neoorigins:cancel_event` as the `entity_action` vetoes the underlying game
event, but only where NeoForge exposes a cancellable event:

- **Cancellable**: `death` (lethal blow undone; a player still at 0 HP is left
  at 1 HP, totem-style), `kill` (spares the victim, same 1-HP patch),
  `hit_taken`, `attack`, `land` (negates fall damage), `projectile_hit`,
  `item_use`, `food_eaten`, `effect_applied`, `block_break`, `block_place`,
  `block_use`, `entity_use`, `villager_interact`, `breed`, `tame`, `bonemeal`.
- **Not cancellable** (post-hoc or uncancellable NeoForge event —
  `cancel_event` is a silent no-op): `jump`, `item_use_finish`,
  `food_finished`, `item_pickup`, `craft_item`, `smelt_item`, `enchant_item`,
  `anvil_repair`, `trade_completed`, `advancement_earned`, `wake_up`,
  `respawn`, `dimension_change`, `climb`, `tick`, `gained`, `lost`, `chosen`,
  `power_activated`, and all `mod_*` modifier events.

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

Fires when the player places a block. Cancellable via `neoorigins:cancel_event` (vetoes the placement).

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

## `food_finished`

Fires when the player **finishes eating** a food item (post-eat). **Not cancellable** — the food has already been consumed. This is distinct from `food_eaten` which fires at eat-start and can cancel the eat.

Also synthetically fired by `EdibleItemPower` after a successful bite, so custom edible items trigger the same post-eat hooks.

**Context:** `FoodContext(stack, event)` — the consumed food `ItemStack`. Context-aware conditions like `neoorigins:food_item_in_tag` and `neoorigins:food_item_id` read the stack.

**Dispatch site:** `InteractionPowerEvents.onItemUseFinish` (`LivingEntityUseItemEvent.Finish`, only dispatched when the stack has a FOOD component).

**Typical use:** post-eat nutrition bonuses, food-specific buffs, bonus saturation for certain food types.

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
**Cancellable** via `neoorigins:cancel_event` (vetoes the interaction).

**Context:** `BlockInteractContext(pos, state, event)` — carries the
underlying cancellable `RightClickBlock` event.

**Dispatch site:** `InteractionPowerEvents.onBlockUse`.

**Typical use:** right-click enchant, block-specific rituals, class-lock
interactions.

---

## `entity_use`

Fires when the player right-clicks a living entity. The narrower
`villager_interact` alias fires immediately after, only when the target is a
villager or wandering trader. Both are **cancellable** via
`neoorigins:cancel_event` (vetoes the interaction — e.g. blocking villager
trading for an origin).

**Context:** `EntityInteractContext(target, event)` — carries the underlying
cancellable `PlayerInteractEvent.EntityInteract` event. The `breed`, `tame`
and `bonemeal` dispatches share this pattern and are likewise cancellable.

**Dispatch site:** `InteractionPowerEvents.onEntityUse` (filtered to
`LivingEntity` targets).

**Typical use:** healing touch, taming-by-class, entity-hug particle,
trade-lock origins (`villager_interact` + `cancel_event`).

---

## `villager_interact`

Fires when the player right-clicks a villager or wandering trader
(`AbstractVillager` covers both) — a narrower alias of `entity_use`, fired
immediately after it so a power can target either granularity.
**Cancellable** via `neoorigins:cancel_event` (vetoes the interaction before
the trade screen opens).

**Context:** `EntityInteractContext(target, event)` — carries the underlying
cancellable `PlayerInteractEvent.EntityInteract` event.

**Dispatch site:** `InteractionPowerEvents.onEntityUse`.

**Typical use:** trade-lock origins (pillager-friendly origins that villagers
refuse to deal with), reputation hooks.

---

## `breed`

Fires when the player causes a baby animal to spawn (feeding two parents).
Fires regardless of any `twin_breeding` power. **Cancellable** via
`neoorigins:cancel_event` (vetoes the baby spawn).

**Context:** `EntityInteractContext(child, event)` — the target is the
newborn `AgeableMob`; carries the underlying cancellable
`BabyEntitySpawnEvent`.

**Dispatch site:** `WorldPowerEvents.onBabyEntitySpawn`.

**Typical use:** shepherd-class bonuses on breeding, sterile-origin breeding
bans (`cancel_event`).

---

## `tame`

Fires when the player tames an animal (wolf, cat, horse, parrot, etc. — any
`AnimalTameEvent`). Distinct from the `tame_mob` power, which tames via its
own active-ability pipeline. **Cancellable** via `neoorigins:cancel_event`
(the taming fails).

**Context:** `EntityInteractContext(animal, event)` — carries the underlying
cancellable `AnimalTameEvent`.

**Dispatch site:** `WorldPowerEvents.onAnimalTame`.

**Typical use:** beastmaster buffs on tame, feral origins that animals refuse
to bond with (`cancel_event`).

---

## `bonemeal`

Fires when the player applies bone meal to a block. Distinct from
`mod_bonemeal_extra`, which only scales the extra-application count.
**Cancellable** via `neoorigins:cancel_event` (the bone meal is not
consumed and nothing grows).

**Context:** `BlockInteractContext(pos, state, event)` — the bonemealed
block; carries the underlying cancellable `BonemealEvent`. Supports
`block_condition` like the other block events.

**Dispatch site:** `CraftingPowerEvents.onBonemeal`.

**Typical use:** druid growth side-effects, blighted origins that kill
instead of grow (`cancel_event` + replacement action).

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

## `power_activated`

Fires when **another power on the same player is successfully activated** —
an active power fired past its cooldown / hunger / resource gates, or a
toggle power flipped (either direction; pair with a `power_active` condition
on the toggled power if you only want one direction). Compat-layer (Route B)
actives fire per dispatched attempt, since their gating lives inside the
compat consumer and exposes no success signal.

**Context:** `EventPowerIndex.PowerActivatedContext` — carries the activated
power's id.

**Dispatch sites:** `AbstractActivePower.onActivated` (success branch),
`AbstractTogglePower.onActivated`, `CompatPower.onActivated` — all via
`EventPowerIndex.dispatchPowerActivated`.

**Filter (`power`, optional):** a single power id or an array of ids; the
action only fires when the activated power's id matches. Omit to fire on
**any** activation.

**Re-entrancy:** if a `power_activated` listener's action itself activates a
power (e.g. via a command), that nested activation does **not** re-dispatch
`power_activated` — A→B→A loops are cut at the first level. The nested
activation itself still happens.

```json
{
  "type": "neoorigins:action_on_event",
  "event": "power_activated",
  "power": "neoorigins:ground_slam",
  "entity_action": {
    "type": "neoorigins:execute_command",
    "command": "say Ground slam unleashed!"
  }
}
```

**Typical use:** announce ability use in chat, chain a secondary effect onto
another ability, build combo systems (resource gain on each activation).

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

## `mod_xp_gain`

Scales experience points gained from any source (mob kills, ore drops,
bottles, etc.).

**Context:** `null`. Base value is `event.getAmount()` from
`PlayerXpEvent.XpChange`.

**Dispatch site:** `CompatEventPowers.onXpChange`.

**Typical use:** scholar class 1.5× XP, cursed class 0.5× XP. Uses
Apoli modifier math (addition + multiply_base/multiply_total collapse
via `NumericModifierRegistry`).

---

# Re-wired in 2.2.0

An earlier draft of the enum included keys that were parseable in JSON but
had no runtime dispatch site; they were removed rather than left as silent
no-ops. The 2.2.0 wave wired them back up. `breed`, `tame`,
`villager_interact` and `bonemeal` have full sections above; the rest in
brief:

| Event | Context | Dispatch site | Notes |
|---|---|---|---|
| `climb` | none (the player is the subject) | `PlayerLifecycleEvents` player tick | Fires once per tick while the player is on a climbable block (ladder/vine/scaffolding). No NeoForge event exists for this, so it rides the tick — gate frequency with a cooldown or condition. |
| `craft_item` | `CraftContext(stack)` | `CraftingPowerEvents.onItemCrafted` | Item taken from a crafting result. |
| `smelt_item` | `CraftContext(stack)` | `CraftingPowerEvents.onItemSmelted` | Item taken from furnace / smoker output. |
| `enchant_item` | `CraftContext(stack)` | `CraftingPowerEvents.onItemEnchanted` | Enchantment applied at a table (post-apply). Distinct from `mod_enchant_level`, which modifies the offered level. |
| `anvil_repair` | `CraftContext(output)` | `CraftingPowerEvents.onAnvilRepair` | Repaired/combined output taken from an anvil. Distinct from `mod_anvil_cost`, which previews the cost. |
| `advancement_earned` | `AdvancementContext(id)` | `PlayerLifecycleEvents.onAdvancementEarn` | Any advancement, including recipe unlocks — filter with a condition. |
| `trade_completed` | `TradeContext(offer)` | `InteractionPowerEvents.onTradeCompleted` | Villager trade finished. Post-hoc — not cancellable (veto trading with `villager_interact` + `cancel_event` instead). |
| `mod_trade_price` | modifier | `AbstractVillagerTradePriceMixin` | Villager trade cost multiplier. |
| `mod_craft_amount` | modifier | `CraftingMenuOriginContextMixin` | Crafting output count multiplier. |
| `mod_fall_damage` | modifier | `MovementPowerEvents` (`LivingFallEvent`) | Chains on the event's damage multiplier, so it stacks with Feather Falling etc. For outright immunity use `prevent_action: FALL_DAMAGE`. |

Still unwired: `MOD_BREAK_SPEED` — use the `break_speed_modifier` power
(attribute-backed) instead.

---

## Cross-reference

- [`EventPowerIndex.java`](../src/main/java/com/cyberday1/neoorigins/service/EventPowerIndex.java) — enum declaration and context records.
- [`ActionOnEventPower.java`](../src/main/java/com/cyberday1/neoorigins/power/builtin/ActionOnEventPower.java) — the power type that consumes these events.
- [`POWER_TYPES.md`](POWER_TYPES.md) — full power-type catalogue including `action_on_event`.
