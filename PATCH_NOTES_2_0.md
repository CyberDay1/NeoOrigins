# NeoOrigins 2.0 — Patch Notes

---

## v2.0.30

### New Power Types

- **`neoorigins:walk_on_fluid`** — Walk on water and/or lava surfaces (same mechanic as vanilla Striders). Configurable: `water`, `lava`, or `both`.
- **`neoorigins:extra_inventory`** — Extra inventory opened via skill keybind. Vanilla chest UI, configurable size up to 54 slots, persistent, supports `drop_on_death`.

### Origins++ Compatibility — 100%

Origins++ 2.4 power compat raised from ~96% to **100%** (all ~1326 powers handled). 17 new power type handlers covering `action_on_block_break`, `action_on_entity_use`, `action_on_item_use`, `action_on_wake_up`, `status_bar_texture`, `prevent_elytra_flight`, `modify_projectile_damage`, `modify_air_speed`, `exhaust`, `starting_equipment`, `walk_on_fluid`, `inventory`, `conditioned_restrict_armor`, `freeze`, `modify_harvest`, `recipe`.

### Origins++ mcfunction Compatibility

- **`/resource change|set|get`** command — mirrors Origins-mod resource API
- **`/power grant|revoke`** command — mirrors Origins-mod power API
- **`LegacyCommandRewriter`** — runtime rewriting of 1.20→1.21 attribute names, item data paths, and modifier IDs in mcfunction commands

### Other

- Added `elytra` value to `prevent_action` power type
- `LivingEntityWalkOnFluidMixin` — overrides `canStandOnFluid()` for walk capabilities

### Documentation

- 8 new power type sections, 5 stale entry fixes, `food_finished` event added to EVENTS.md, v2.0.29 patch notes merged

---

## v2.0.29

### Features

- **Custom origin icons with data components** — Origins can now use custom model data in their icon field. Supports string (`"icon": "minecraft:ink_sac"`), object with legacy tag (`"icon": {"item": "minecraft:ink_sac", "tag": "{CustomModelData:1}"}`), and full ItemStack format.
- **Ars Nouveau compatibility** — Undead-origin players now correctly have Ars Nouveau's Harm spell damage inverted into healing, matching vanilla undead potion behavior.
- **New power type: `neoorigins:modify_food_nutrition`** — Overrides all food to give a fixed nutrition value. Example: `{"type": "neoorigins:modify_food_nutrition", "nutrition": 1}`
- **New condition type: `neoorigins:cooldown`** — Returns true when a power is off cooldown. Use with `trigger_cooldown` to gate event-driven powers.
- **Stoneguard Warding Presence is now toggleable** — Players can turn the mob spawn suppression on/off via skill keybind.

### Reworks

- **Blacksmith Quality Craftsmanship** — Complete rework. Equipment you craft now receives: tools +25% mining speed, weapons +20% attack damage, armor +1 armor toughness, all damageable items +10% max durability. All values configurable in JSON.
- **Cook Good Meals** — Now adds +1 nutrition (hunger) alongside the saturation bonus.
- **Cook Smoking Expert** — Was previously inert. Now grants +2 nutrition and +0.5 saturation to food cooked in a smoker or furnace.
- **Stoneguard Warding Presence** — Radius increased from 24 to 48 blocks (24 was the vanilla default and had no effect).

### Bug Fixes

- **`origins:modify_food` compat power was inert** — The compat layer accepted the power type without errors but never applied `food_modifier`/`saturation_modifier` at eat time. Now fully functional with item_condition filtering and Apoli-compatible modifier math.
- **26.1.2 crash on startup** — Fixed `ExceptionInInitializerError` caused by `RareWanderingLootPower` creating ItemStack/ItemCost objects in static initializers before component registries were bound.
- **26.1.2 mixin failures** — Fixed three mixin target renames for 26.1.2: `renderFoodLevel` → `extractFoodLevel`, `renderAirLevel` → `extractAirLevel`, `MerchantScreen.render` → `extractContents`.
- **Mod armor helmets not blocking sunburn** — Any helmet now blocks sun damage, not just damageable ones.
- **Model color transparency broken with nametag off** — Fixed by flushing the render buffer before and after applying the shader color tint.
- **Unknown icon items no longer crash origin loading** — Origins with unrecognized icon items now fall back to stone with a warning.
- **Compat translator stripping icon data** — The Origins compat translator was unwrapping icon objects to plain strings, discarding the `tag` field.

### Description Fixes

- Rogue Hidden Presence — added mob detection reduction
- Feline Predator's Calm — updated to describe creeper-only behavior
- Verdant Wild Kin — clarified that all mobs (including hostile) ignore you
- Stoneguard Warding Presence — updated radius to 48 blocks
- Feline, Draconic, Hiveling, Gorgon — updated hunger-related descriptions to reference energy/stamina

---

## v2.0.11

### Headline: Essence Evolution System

Kill mobs to evolve your origin through three tiers — **Evolved**, **Ascended**, and **Apex** — each granting new powers on top of your base kit. All 49 non-class origins have unique evolution tracks with 3–5 tier-specific powers (HP boosts, new immunities, upgraded abilities).

**How to evolve:**
1. Pick any non-class origin and start playing normally.
2. Kill mobs — every kill accumulates essence. You'll see periodic chat messages tracking your progress (every 100 kills by default).
3. When you hit a tier threshold, a chat prompt appears with clickable **[EVOLVE]** and **[DECLINE]** buttons.
4. Click **[EVOLVE]** (or type `/neoorigins evolve accept`) to ascend. Your origin gains new tier-specific powers immediately.
5. Decline if you want to wait — the prompt will reappear on your next kill.

**Default kill thresholds** (configurable in `neoorigins-common.toml` under `[evolution]`):

| Tier | Name | Kills Required |
|---|---|---|
| 1 | Evolved | 1,000 |
| 2 | Ascended | 2,500 |
| 3 | Apex | 5,000 |

Server admins can also force-evolve players with `/neoorigins evolve <player> <tier>`.

**What's included:**
- **Per-origin evolution powers** — ~250 new evolution power JSONs across all origins. Examples: Elytrian gains Sky Piercer (boosted dash at Ascended/Apex), Wraith gets reduced-cost phasing, Phantom unlocks reduced daylight vulnerability.
- **4 new thematic power types** — `dodge_chance`, `light_level_effect`, `low_hp_threshold`, `thorns_on_hit` for evolution-specific mechanics.
- **Configurable armor classes** — heavy/light armor tags with restrict_armor integration. Elytrian's flight restriction now uses the `neoorigins:heavy_armor` tag.
- **Evolution config sync** — server pushes evolution thresholds + tier names to clients on join via `SyncEvolutionConfigPayload`.
- **Commands moved** to `/neoorigins` namespace (`/neoorigins evolve`, `/neoorigins origin`, etc.).

> **Note:** The evolution system is largely untested and may have rough edges. If you encounter any issues, please report them on [Discord](https://discord.gg/Ukph2budfy) or on the [GitHub issue tracker](https://github.com/CyberDay1/NeoOrigins/issues).

### Bug Fixes

- **Aquatic origins can't eat anything** — `food_item_in_tag` conditions were missing the `#` prefix on tag references, causing the tag lookup to silently fail and cancel all food. Aquatic origins (Merling, Siren, Kraken, Abyssal) can now eat fish again.
- **Aquatic diet config toggle does nothing** — the `ocean_origins.fish_diet_required` config flag existed but the power JSON never checked it. Diet restriction is now properly gated on the config.
- **Aquatic origins don't rehydrate from rain or water cauldrons** — the dries-out power only counted full submersion (`isUnderWater`). Now rain, bubble columns, and water cauldrons all count as "in water" for rehydration.
- **Draconic water weakness description wrong** — said "drowning damage is doubled" but the power actually deals 1.5 DPS on water/rain contact. Description updated.
- **Draconic size ~40% instead of 20% with Pehkui** — the vanilla `minecraft:scale` attribute AND Pehkui's BASE scale were both set to 1.2x, compounding to ~1.44x. Removed the Pehkui mirror since the vanilla attribute is authoritative on 1.20.5+.
- **Caveborn mineral-eating bonuses never trigger** — all 4 bonus powers (diamond/gold/iron/netherite) had the same missing `#` prefix bug in their `food_item_in_tag` conditions.
- **`food_restriction` carnivore/diet powers broken** — the legacy alias only read `item_tag` (not the documented `allowed_tags`), and didn't prepend `#` for tag lookups. Both field names now work and `#` is auto-prepended.
- **Cookbook carnivore example uses wrong field name** — changed `allowed_tags` to `item_tag` with proper `#` prefix and `mode: whitelist`.

### New Power Types

- **`neoorigins:burn`** — sets the player on fire at a configurable interval/duration. Compat: translates `origins:burn`.
- **`neoorigins:ignore_water`** — full land-speed movement in water + no current pushing. Compat: translates `origins:ignore_water`.
- **`neoorigins:overlay`** — full-screen texture overlay with configurable opacity. Compat: translates `origins:overlay`. *(26.1: renders without alpha blending — rendering pipeline rework in progress.)*
- **`neoorigins:model_color`** — RGBA tint on the player model. Compat: translates `origins:model_color`. *(26.1: server-side capability syncs; client-side tint stubbed — needs custom RenderType.)*
- **`neoorigins:lava_vision`** — configurable lava fog distance multiplier. Compat: translates `origins:lava_vision`. *(26.1: server-side capability syncs; client-side fog stubbed — `ViewportEvent.RenderFog` no longer cancellable.)*
- **`neoorigins:shader`** — applies a post-processing shader to the player's view. Auto-normalises Origins-style full paths. Compat: translates `origins:shader`. *(26.1: server-side capability syncs; client-side post-processing stubbed — needs PostChain pipeline.)*

### New Condition

- **`neoorigins:near_entity`** — true when an entity of the given type (or `#tag`) is within `distance` blocks. Supports entity tags, Euclidean distance filtering, and `inverted: true`.

### Compat Improvements

- `origins:keep_inventory` now translates to `neoorigins:keep_inventory` (was silently skipped).
- `origins:swim_speed` now translates to attribute modifier on `water_movement_efficiency` (was silently skipped).
- `origins:phasing` now translates to `neoorigins:wraith_phase` (was silently skipped).
- The "silently skipped" list is now down to just `origins:shaking` — every other Origins power type has an equivalent or translation.

---

## v2.0.0

**Released:** 2026-04-24
**Supports:** Minecraft 26.1 (Java 25) · Minecraft 1.21.1 (Java 21)

A ground-up V2 rewrite focused on data-driven authoring, cleaner internals, and a big content pass across every built-in origin and class. Pack authors get 130+ composable power types, a DSL for conditions and actions, new powers for ambient buffs / crafting / combat, and a cookbook's worth of copy-paste recipes. Players get reworked kits, three new classes, and dozens of long-standing bugs fixed.

Pack-author docs are now hosted on GitHub Pages: <https://cyberday1.github.io/NeoOrigins/>.

---

## ⚠️ Migrating from 1.x — please read

We **forgot to call this out** in the original 2.0 release notes (issue #38) — sorry about that.

- **`originpacks/` has moved** from `<game_dir>/originpacks/` to **`<game_dir>/config/originpacks/`**. Move your packs there to find them in the new location.
- A **legacy fallback** is in place — if you don't have a `config/originpacks/` folder yet, the mod will keep loading from the old `<game_dir>/originpacks/` location and log a one-time warning telling you to migrate. The fallback will be removed in a future major release, so plan to move them when convenient.
- **No JSON changes are required** for existing packs. The only thing that moved is where the pack files live; the contents work the same way.
- Datapack-shipped origin layers (e.g. `<your_pack>:origin`) now **auto-merge into the main origin picker** as of 2.0.3 instead of creating a separate picker screen — pack authors who want a separate tab can opt out with `"standalone": true` on the layer JSON. Pack authors who already used `"replace": false` on `data/neoorigins/origins/origin_layers/origin.json` will see no change in behavior.

---

## Headline changes

- **Two-layer selection** — every player picks an Origin *and* a Class. 46 origins × 20 classes.
- **Class rework** — all 17 existing classes rebalanced to 4-5 passive/condition-gated powers each. **Zero keybind slots** consumed by classes (no actives at all).
- **3 new classes** — **Fisher**, **Mason**, **Paladin**.
- **V2 power consolidation** — 88 legacy PowerType classes collapsed into ~60 composable types. Every first-party power is now written in the canonical DSL forms; legacy types remain for pack compat through v4.0.
- **New power types** — `bare_hand_tool`, `fortune_when_effect`, `natural_glide`, `hide_hud_bar`, `cobweb_affinity`, `ender_gaze_immunity`, plus V2 consolidations (`persistent_effect`, `action_on_event`, `condition_passive`, `active_ability`).
- **New DSL conditions** — 10+ including `night`, `thundering`, `has_effect`, `climbing`, `near_block`, `out_of_combat`, `moon_phase`, `exposed_to_sun` (full-day fixed).
- **New DSL action** — `dash` (look-direction forward thrust with variable strength).
- **Orb of Origin** — consumable to re-pick your origins; XP-gated scaling cost with a first-free use; rage-quit safe (inventory is only consumed on successful re-pick).
- **Origin spawn locations** — origins can declare dimension / biome / structure spawn anchors (ocean-floor and water-surface fallbacks supported).
- **Advancement-based upgrades** — origins can auto-swap to another origin when the player earns a specific advancement. Datapack-only.

---

## Players — what's new

### Reworked origins (partial list)

| Origin | What changed |
|---|---|
| **Avian** | Expanded kit — now has Keen Sight, Hollow Bones, Feather Hop in addition to Featherweight + Slow Falling + Athlete's Diet |
| **Abyssal** | Pressure-Hardened Skin (+2 armor) added; Landwalker speed penalty gated to land only; Dries Out power now has a readable name |
| **Blazeling** | Rewritten — now spawns in the Nether, gains +3 armor (Blaze Scales), Nether regen, Internal Heat hunger penalty, Firebolt multi-shot active, rain damage honored |
| **Phantom** | Full rework — Spectral Wings (elytra-free gliding), Wind Beat (elytra boost), Moonplate (+4 armor at night), Soul Drain (+1 HP on kill), Weightless, Sunburn, Fragile Form, Sleepless Dread |
| **Elytrian** | Pure flight specialist — natural elytra glide + Sky Speed (+30% flight), Wind Cushion (no kinetic damage), Frail Frame, can't wear heavy armor (iron+ gated) |
| **Enderian** | Spawns in the End outer biomes (not main island) |
| **Cinderborn** | Eruption is now a 4-shot fireball burst (with hunger cost); water damage is actual passive water/rain damage, not drown-only |
| **Caveborn** | Full rework — Stone Fists (bare-hand stone pick), Mining Fortune (Fortune II on ores while Luck is active), Stone Eater diet (eat stone/iron/gold/diamond/netherite for food + tier-specific buffs) |
| **Strider / Golem** | Each got a new active: Stampede (forward dash) and Ground Slam (5-block AoE + Slowness II) |
| **Air Mage** | Featherfall now toggleable persistent_effect; attribute fields normalized |
| **Breeze** | Cushion of Air and Updraft are toggleable; "Hollow Bones" renamed "Wisp Frame" to avoid Avian collision |

### Class rework (all 20 classes)

Every class is now **all-passive** — no keybinds, no skill slots. 2-power classes like Warrior/Blacksmith/Cook expanded to 4-5 passives each. New content is condition-gated where appropriate (Berserker rage when HP≤50%, Rogue backstab when sneaking, Explorer regen only out of combat near a campfire).

### 3 new classes

- **Fisher** — luck in water, swim speed, drown resistance, starting rod (Luck of the Sea I + Lure I), night vision underwater
- **Mason** — bare-hand stone pickaxe, +1 block placement reach, break speed bonus, starting pickaxe (Efficiency I), +1 armor
- **Paladin** — weakness-on-hit vs undead, +2 armor, regen near beacons, starting iron sword (Smite I), wither immunity

### Quality of life

- **O key** opens a minimal origin info screen (works even if JEI/REI isn't installed)
- **First-pick invulnerability** — the player can't be killed or shoved around while the origin picker is open
- **Origin picker sorts Human/Nitwit first** for quick default choice
- **originpacks/** folder lives in `config/` now (easier to find); still accepts `.jar`, `.zip`, or folder drops
- **Hidden HUD bars** — origins that don't use hunger/air hide those bars automatically (Automaton, Kraken, Merling, Automaton again for air)
- **Dev screens** (Debug / Edit) only show in Creative mode — no accidental clicks in survival

### Combat fixes

- Hunger cost on active abilities **actually deducts now** — it was being silently dropped pre-2.0 (the field was in JSON but never read). Air Mage Whirlwind, Fire Mage Fireball, Cinderborn Eruption, Draconic Flame Breath, Shulk abilities, etc. now all pay their advertised hunger costs.
- `ModifyDamagePower direction:out` now fires against mob victims, not just player-vs-player
- Fire immunity now covers magma-block hot-floor damage, not just open flame
- Mobs summoned by origins (Abyssal guardians, Necromancer undead, Tame mobs, etc.) no longer target their owner, don't drop items or XP when killed, and owner UUID persists cross-dimension / relog
- `exposed_to_sun` condition now covers the full day (was previously noon-to-sunset only, breaking morning sun-damage origins)
- Night vision / enhanced vision now stable — no silent disable on skill-key press, no toggle-collision
- `action_on_event` handlers no longer leak on respawn / login / origin-swap (was causing compounding buffs like Fire Mage Internal Furnace ×1.5 hunger becoming ×7.6 after 5 respawns)
- Quenched Flame / Water Weakness descriptions now match their mechanics

---

## Pack authors — what's new

### Canonical V2 power types

Three main consolidation targets. Legacy types still work through `LegacyPowerTypeAliases` for two major versions; new content should use canonical forms:

| Canonical | Replaces |
|---|---|
| `persistent_effect` | `status_effect`, `stacking_status_effects`, `night_vision`, `glow`, `water_breathing` |
| `condition_passive` | `damage_in_daylight`, `damage_in_biome`, `damage_in_water`, `biome_buff`, `burn_at_health_threshold`, `regen_in_fluid` |
| `action_on_event` | `thorns_aura`, `knockback_modifier`, `hunger_drain_modifier`, `natural_regen_modifier`, `longer_potions`, `action_on_kill`, `action_on_hit_taken`, `food_restriction`, `more_animal_loot`, `better_enchanting`, `better_crafted_food`, `better_bone_meal`, `efficient_repairs`, `teleport_range_modifier` |
| `active_ability` | `active_launch`, `active_bolt`, `active_aoe_effect`, `healing_mist`, `repulse` (+ dash/fireball/swap via LOSSY remaps) |

### New power types

- **`neoorigins:bare_hand_tool`** — make the player's empty hand behave as any vanilla tool item. Used by Caveborn (stone pickaxe), Miner, Mason, Blacksmith, Lumberjack (iron axe).
- **`neoorigins:fortune_when_effect`** — apply Fortune-style loot rolls while a gating MobEffect is active. Uses vanilla `ApplyBonusCount.ORE_DROPS` math. Hardcoded-excludes ancient debris (vanilla parity).
- **`neoorigins:natural_glide`** — grants elytra-style gliding without equipping an elytra. Used by Phantom (Spectral Wings), Elytrian, Hiveling. Pair with `elytra_boost` for full launch-and-glide.
- **`neoorigins:cobweb_affinity`** — walk through cobwebs at normal speed + 10× break speed.
- **`neoorigins:ender_gaze_immunity`** — Endermen don't aggro on eye contact.
- **`neoorigins:hide_hud_bar`** — hide hunger or air HUD bar per-origin (gated by config; default on).
- **`neoorigins:persistent_effect`** — apply MobEffects over time with optional toggle and condition gate. Replaces multiple legacy types.
- **`neoorigins:action_on_event`** — generic event-triggered action + modifier dispatch. 20+ events (`hit_taken`, `kill`, `jump`, `food_eaten`, `mod_exhaustion`, `mod_knockback`, etc.)

### New DSL conditions

- `night` / `daytime` — time-of-day gates
- `thundering` — weather + position (must be raining at the player AND globally thundering)
- `has_effect` — player has a specific MobEffect active (great for pairing with `fortune_when_effect`)
- `climbing` — player is on a climbable block
- `near_block` — cubic AABB scan for block IDs and/or tags (radius 1-8, logical OR across matchers)
- `out_of_combat` — N ticks since last damage hit (default 100 / 5 seconds)
- `moon_phase` — moon phase check with comparison operators
- `exposed_to_sun` — fixed to cover full day (0–13000 in-game ticks)

### New DSL action

- `dash` — applies a forward impulse along the player's look vector. Variable `strength`, optional `allow_vertical` for ground-lock dashes. Sets `hurtMarked = true` so client prediction doesn't discard the impulse. Canonical replacement for the legacy `active_dash`.

### Attribute modifier enhancements

- **Optional `condition` field** — a full DSL condition object (or a string shorthand: `in_water` / `on_land` / `in_lava`). Edge-triggered add/remove every 5 ticks. Gate any stat buff on sneaking, low HP, night-time, proximity, anything.
- **Optional `equipment_condition`** — match a worn item by ID or tag in a specific slot.
- **Optional `location_condition`** — match dimension / biome / biome tag / structure / structure tag.
- **Stable modifier IDs** — `modIdFor` no longer hashes enums by identity, so HP/speed/armor don't stack on relog.

### Active ability hunger gating

Every power extending `AbstractActivePower` inherits a `hungerCost()` method — add `"hunger_cost": N` to JSON and the base class debits food before running `execute()`. Powers with insufficient food silently abort (cooldown not consumed). Active types with hunger wired: `active_ability`, `active_teleport`, `active_place_block`, `shadow_orb`, `tidal_wave`, `summon_minion`, `tame_mob`, `active_phase`.

### Event & modifier dispatch

`EventPowerIndex` gives O(1) handler lookup per player per event. Powers register on `onGranted`, unregister on `onRevoked`. Context records (`HitTakenContext`, `KillContext`, `FoodContext`, etc.) pass event-specific data through the compiled `EntityAction` pipeline via a ThreadLocal `ActionContextHolder`.

### Edible items + chained bonuses

- `edible_item` makes arbitrary items consumable (nutrition + saturation + always-edible flag)
- Consuming fires `ITEM_USE_FINISH` with the stack as context
- Pair with `action_on_event` gated on `food_item_in_tag` to apply per-item bonus effects (Caveborn eating diamond → Luck II → unlocks Mining Fortune)

### Config

- Per-origin and per-class enable/disable toggles (`[origins]` / `[classes]` sections)
- Per-power dimension restrictions (`[dimension_restrictions]`)
- Random-assignment mode (FIRST_JOIN / EVERY_DEATH / DISABLED) with reroll count

### Docs

- `docs/POWER_TYPES.md` — every power type with field tables and examples
- `docs/CONDITIONS.md` — 60+ condition verbs
- `docs/ACTIONS.md` — 40+ action verbs
- `docs/EVENTS.md` — event keys for `action_on_event`
- `docs/COOKBOOK.md` — end-to-end recipes (bare-hand mining, eat-minerals-for-buffs, moon-blessed armor, sleepless origins, elytra-free flight, dash, campfire rest, Fortune-from-effect, etc.)
- `docs/MIGRATION.md` — porting pre-2.0 packs and upstream Origins/Apoli/Apugli packs

### Cross-mod compat

- **Origins / Apoli / Apugli** packs drop into `config/originpacks/` and load automatically. Two translation passes: direct type mapping for the common types, and a compat power engine that compiles `origins:active_self` / `origins:toggle` / `origins:resource` / `origins:conditioned_*` into live event-driven behavior.
- **Ars Nouveau** — Harm spell damage inverted into healing for undead-origin players (v2.0.29+)
- **Epic Fight** — sized origins maintain their scale in combat mode
- **GeckoLib (optional)** — custom projectile / VFX animations when present

---

## Breaking / migration notes

- **Legacy types still work** — two major versions of compat (through v4.0). Every retired legacy type has an alias entry in `LegacyPowerTypeAliases` that remaps to the canonical V2 form on load. Pack authors see a `[2.0-legacy]` deprecation warning once per type per boot.
- **`active_dash` / `active_bolt` / `active_fireball`** stay registered as separate legacy types during the deprecation window — their alias paths are LOSSY (dash becomes a radius-0 pull, single fireball instead of the 3-4 burst). Migrate deliberately using the new `neoorigins:dash` action or `origins:and` of multiple `spawn_projectile` with `inaccuracy`.
- **Attribute format** — first-party files use `minecraft:attack_damage` (no `generic.` prefix), lowercase operation names (`add_value` / `add_multiplied_base`), no explicit `modifier_id`. The legacy formats (`minecraft:generic.*`, UPPERCASE operation) still parse via fallback, but first-party content is clean.
- **Hunger cost** — if your pre-2.0 pack relied on the field being silently dropped, your active abilities will now actually debit food. Review your `hunger_cost` values; the convention is food points (1 shank = 2 points), not hunger bars.
- **Description convention** — `Costs N hunger` matches the JSON `hunger_cost` value exactly, in food points.

---

## Java API (for addon authors)

- **`neoorigins.api` package** is now stable — `PowerType`, `PowerConfiguration`, `EntityCondition`, `EntityAction` all API-safe.
- **`PowerTypes` registry** — `DeferredRegister` over `ResourceKey<Registry<PowerType<?>>>`. Addons can register their own types.
- **`EventPowerIndex`** — external callers can `register(player, event, handler)` to participate in dispatch.
- **`SpawnHelper.setBedSpawn(player, dim, pos, angle, forced, sendMessage)`** — cross-version wrapper hiding the 26.1 `RespawnConfig` vs 1.21.1 5-arg API divergence.
- **`CombatTracker`** — simple last-damage-tick lookup, backs `out_of_combat` condition.

---

## Minimum versions

| Minecraft | Java | NeoForge |
|---|---|---|
| 26.1.x | 25 | 27.x |
| 1.21.1 | 21 | 21.1.x |

---

## Credits

Built by CyberDay1 and contributors. Credits to the Origins / Apoli / Apugli teams whose pack format this mod is compatible with.

Huge thanks to our testers — **NyxBorne**, **Soul**, and **Jams** — who burned through V1 and V2 alphas catching every weird interaction, broken hitbox, missing bubble, and silently-disabled power. And near-uncountable small and large bugs that came up during playtesting both V1 and V2.

Report bugs: <https://github.com/CyberDay1/NeoOrigins/issues>
