# NeoOrigins 2.0 — Patch Notes

**Released:** 2026-04-24
**Supports:** Minecraft 26.1 (Java 25) · Minecraft 1.21.1 (Java 21)

A ground-up V2 rewrite focused on data-driven authoring, cleaner internals, and a big content pass across every built-in origin and class. Pack authors get 130+ composable power types, a DSL for conditions and actions, new powers for ambient buffs / crafting / combat, and a cookbook's worth of copy-paste recipes. Players get reworked kits, three new classes, and dozens of long-standing bugs fixed.

Pack-author docs are now hosted on GitHub Pages: <https://cyberday1.github.io/NeoOrigins/>.

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
