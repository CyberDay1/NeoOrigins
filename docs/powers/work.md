---
title: "Mining, farming, crafting & trade"
parent: "Power Types"
grand_parent: "DSL Reference"
nav_order: 10
permalink: /powers/work
---

# Mining, farming, crafting & trade

Mining speed and bare-hand tools, crops, crafting and smelting bonuses, villager trades and loot.

Fields every power accepts (`name`, `hidden`, `power_condition`, ...) are on [Power basics](../concepts/power-basics.md). See also: [Power basics](../concepts/power-basics.md), [Events](../EVENTS.md).

Types on this page: [`break_speed_modifier`](#neooriginsbreak_speed_modifier) · [`bare_hand_tool`](#neooriginsbare_hand_tool) · [`underwater_mining_speed`](#neooriginsunderwater_mining_speed) · [`tree_felling`](#neooriginstree_felling) · [`ultimine`](#neooriginsultimine) · [`fortune_when_effect`](#neooriginsfortune_when_effect) · [`crop_growth_accelerator`](#neooriginscrop_growth_accelerator) · [`crop_harvest_bonus`](#neooriginscrop_harvest_bonus) · [`quality_equipment`](#neooriginsquality_equipment) · [`craft_amount_bonus`](#neooriginscraft_amount_bonus) · [`more_smoker_xp`](#neooriginsmore_smoker_xp) · [`trade_availability`](#neooriginstrade_availability) · [`rare_wandering_loot`](#neooriginsrare_wandering_loot) · [`kill_loot_drops`](#neooriginskill_loot_drops) · [`loot_pool_grant`](#neooriginsloot_pool_grant).

---

## `neoorigins:break_speed_modifier`

Multiplies the player's mining speed when breaking blocks in the specified tag. When no tag is provided, applies to all blocks.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `block_tag` | Identifier | no | _(all blocks)_ | Block tag to restrict the modifier to, e.g. `#minecraft:base_stone_overworld`. A bare id is tried as a tag first, then as a single block; a leading `#` forces a tag. |
| `effective_tool_only` | boolean | no | `false` | Only apply while the main-hand item is a correct tool for the block (a pickaxe on stone) |
| `multiplier` | float | no | `2.0` | Speed multiplier (`2.0` = double speed) |

**Example: 2× speed on stone and deepslate**
```json
{
  "type": "neoorigins:break_speed_modifier",
  "block_tag": "#minecraft:base_stone_overworld",
  "multiplier": 2.0,
  "name": "Stone Affinity",
  "description": "Mines stone and deepslate twice as fast."
}
```

---

## `neoorigins:bare_hand_tool`

Makes the player's empty hand behave like a specific vanilla tool for block-break purposes: tool-tier drop eligibility **and** break speed both match the configured tool item. Point at any tool item ID and the runtime looks up its tool component to determine which blocks qualify and at what speed. The tool's speed multiplies the bare-hand speed the way a held tool would, so Haste, Mining Fatigue, the underwater and mid-air penalties and any `break_speed_modifier` all still apply.

Emits a capability tag of the form `bare_hand_tool:<tool_id>` (e.g. `bare_hand_tool:minecraft:stone_pickaxe`): the tool ID is encoded in the tag so the client-side break-speed predictor and the server-side harvest check can both reach the answer without extra sync state. Wired via `BareHandToolEvents` on the NeoForge event bus (`PlayerEvent.HarvestCheck` + `PlayerEvent.BreakSpeed`). No mixins required.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `tool` | Identifier | no | `minecraft:stone_pickaxe` | Any vanilla tool item ID. Determines both break-speed and drop-eligibility. |

**Example: Caveborn mines like a stone pickaxe with bare hands**
```json
{
  "type": "neoorigins:bare_hand_tool",
  "tool": "minecraft:stone_pickaxe",
  "name": "Stone Fists",
  "description": "Bare hands mine like a stone pickaxe — break ores and stone without tools."
}
```

Only the first active instance applies, so stacking several (e.g. a pickaxe and an axe) does not combine them: the second tool is never consulted.

Only fires when the main hand is empty. Holding any item delegates to vanilla behaviour normally.

---

## `neoorigins:underwater_mining_speed`

Removes the normal mining speed penalty for being submerged in water. The player mines at full speed underwater.

No additional fields beyond `name` and `description`.

**Example:**
```json
{
  "type": "neoorigins:underwater_mining_speed",
  "name": "Aquatic Miner",
  "description": "Mines at full speed underwater."
}
```

---

## `neoorigins:tree_felling`

When the player breaks a log with an axe, all connected logs above it are broken too (the search never goes downward). Skipped while sneaking so players can still harvest single logs. An axe is any item in `#minecraft:axes`, modded axes included; with an empty main hand, a `bare_hand_tool` axe counts.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `max_blocks` | int | no | `64` | Maximum connected logs to break per activation, not counting the log you chopped. Values below 64 have no effect: the limit is never lower than 64. |
| `require_axe` | boolean | no | `true` | Fell only when the log is broken with an axe. `false` fells with any tool or a bare hand, as the type did before this field existed. |

**Example:**
```json
{
  "type": "neoorigins:tree_felling",
  "max_blocks": 64,
  "name": "Arboreal Sense",
  "description": "Fells an entire tree when you chop its base. Sneak to break single logs."
}
```

---

## `neoorigins:ultimine`

Grants [FTB Ultimine](https://www.curseforge.com/minecraft/mc-mods/ftb-ultimine) vein-mining to the holder. Soft dependency on `ftbultimine`: when that mod is absent this power is an inert marker and does nothing.

> **1.21.1 vs 26.x:** vein-mining is gated on 1.21.1 and 26.1. FTB publishes no FTB Ultimine for 26.2 or 26.3, so there the power loads as an inert marker and vein-mining is left ungated.

When FTB Ultimine is installed, NeoOrigins registers a restriction handler that permits vein-mining only for players with an **active** `neoorigins:ultimine` power and denies it for everyone else. Because FTB Ultimine treats restriction handlers as an AND-gate (the first handler that disallows wins), adding this power to any origin turns vein-mining into an origin-gated ability: only that origin's holders may ultimine.

The integration is completely dormant unless a loaded pack defines a `neoorigins:ultimine` power. While no pack uses it, FTB Ultimine behaves exactly as vanilla (the AND-gate handler stays out of the way for everyone). Once at least one `ultimine` power is loaded, vein-mining is restricted to players who hold an ultimine power. This keeps the soft dependency truly opt-in: installing both mods together changes nothing until a pack adds the power.

This power carries no config. FTB Ultimine's restriction hook is a coarse allow/deny permission check with no setter for the per-player block limit, the require-tool toggle, or the mining shape, so those are **not** exposed here. The block count, tool requirement, cooldown, exhaustion/XP cost, and shape all follow FTB Ultimine's own server config (and any FTB Ranks / attribute overrides). Modeling fields the API cannot honour would be misleading, so the power is deliberately config-light: "while this power is active you may vein-mine."

**Example:**
```json
{
  "type": "neoorigins:ultimine",
  "name": "Excavator",
  "description": "Hold the ultimine key and break a block to vein-mine the whole vein. Limits follow the server's FTB Ultimine config."
}
```

---

## `neoorigins:fortune_when_effect`

Applies a virtual Fortune-level drop multiplier whenever a configured `MobEffect` is active on the player. The vanilla `ApplyBonusCount.ORE_DROPS` formula is used, giving the same rolling distribution as a real Fortune N pickaxe (`count × (max(0, random(level + 2) - 1) + 1)`).

Deliberately generic — any origin can emulate an enchantment-like buff by pairing this with any MobEffect (e.g. Caveborn's Mining Fortune is gated by `minecraft:luck` granted via eating diamond). Wired via `FortuneEffectEvents` subscribing to `BlockDropsEvent` server-side.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `effect` | Identifier | no | `minecraft:luck` | The gating MobEffect ID. Bonus only applies while this effect is active on the player. |
| `level` | int | no | `2` | Virtual Fortune level to roll. Higher = more extra drops. |
| `target` | string | no | `#c:ores` | Block tag the bonus applies to. Use `#tagname` syntax. Defaults to the NeoForge common ores tag; pack authors can narrow to a vanilla sub-tag like `#minecraft:diamond_ores` or a custom tag. |

**Vanilla parity:** `minecraft:ancient_debris` is hardcoded-excluded because netherite is the single vanilla ore that ignores Fortune. Every other vanilla ore (iron, gold, copper, coal, diamond, emerald, lapis, redstone, nether_gold, nether_quartz) is covered by `#c:ores` and will roll the bonus normally.

**Example: Caveborn Mining Fortune (gated by Luck from eating diamond)**
```json
{
  "type": "neoorigins:fortune_when_effect",
  "effect": "minecraft:luck",
  "level": 2,
  "target": "#c:ores",
  "name": "Mining Fortune",
  "description": "While Luck is active, ore blocks drop as if mined with Fortune II."
}
```

The held tool is ignored, and the bonus multiplies whatever drops the block produced, so it stacks with a real Fortune enchantment and also applies to an ore block mined with Silk Touch. Stacking isn't additive: only the first matching power fires per break. Author variants as separate powers with different effect gates rather than expecting them to compound.

---

## `neoorigins:crop_growth_accelerator`

Passively accelerates the growth of nearby crops by randomly applying a bonemeal tick to eligible blocks at a set interval.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | int | no | `4` | Scan radius in blocks |
| `tick_interval` | int | no | `40` | Ticks between growth attempts |
| `growths_per_interval` | int | no | `1` | Number of crops to accelerate per interval |

**Example:**
```json
{
  "type": "neoorigins:crop_growth_accelerator",
  "radius": 4,
  "tick_interval": 40,
  "growths_per_interval": 2,
  "name": "Verdant Touch",
  "description": "Crops nearby grow faster."
}
```

---

## `neoorigins:crop_harvest_bonus`

Passively grants extra item drops when the player harvests a fully-grown crop or breaks a log. The bonus drops are identical copies of the block's normal drops. Crops means `CropBlock` at max age (wheat, carrots, potatoes, beetroot and the like; not nether wart, cocoa, berries, melons or pumpkins). Logs means naturally grown logs, not stripped or player-placed ones.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `extra_drops` | int | no | `1` | Number of extra drop copies per break |

**Example:**
```json
{
  "type": "neoorigins:crop_harvest_bonus",
  "extra_drops": 1,
  "name": "Bountiful Harvest",
  "description": "Crops and trees yield extra resources."
}
```

---

## `neoorigins:quality_equipment`

Equipment the player crafts or upgrades at a smithing table receives bonus attributes. Tools mine faster, weapons hit harder, armor is tougher, and everything lasts longer.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `bonus_mining_speed` | double | no | `0.25` | `ADD_MULTIPLIED_BASE` modifier on `mining_efficiency`. That attribute's base is 0, so this only scales the bonus an Efficiency enchantment adds. |
| `bonus_attack_damage` | double | no | `0.20` | Attack damage multiplier for weapons (+20%) |
| `bonus_armor_toughness` | double | no | `1.0` | Flat armor toughness bonus for armor pieces |
| `durability_multiplier` | double | no | `0.10` | Max durability increase for all damageable items (+10%) |
| `intercept_menus` | array | no | `[]` | Extra menu-type ids whose result slot also grants the buff when the finished item is taken. |

**Working with modded workstations:** the buff normally rides the vanilla crafting and smithing paths. `intercept_menus` opts additional container screens in by menu-type id, so an item taken out of, say, a mod's own smithing anvil is buffed the same way:

```json
"intercept_menus": [
  "overgeared:smithing_anvil_menu",
  "overgeared:stone_smithing_anvil_menu"
]
```

Ids that no loaded mod registers simply never match, so listing a menu from a mod the player does not have is harmless: no `required_mods` guard is needed just for this field.

**What gets buffed:**
- **Tools** (pickaxe, axe, shovel, hoe, shears): +mining speed via `MINING_EFFICIENCY` attribute
- **Weapons** (sword, axe, trident): +attack damage via `ATTACK_DAMAGE` attribute
- **Armor** (helmet, chest, legs, boots): +armor toughness via `ARMOR_TOUGHNESS` attribute
- **All damageable items**: +max durability via `MAX_DAMAGE` component

**Example:**
```json
{
  "type": "neoorigins:quality_equipment",
  "name": "Quality Craftsmanship",
  "description": "Tools mine faster, weapons hit harder, armor is tougher, and everything lasts longer."
}
```

Bonuses are applied when the item is taken from a crafting or smithing result (or an `intercept_menus` result slot) via attribute modifiers stored on the item; smelting does not apply them. Items already carrying the quality modifier are not double-buffed.

Smithing-table upgrades are part of the buffed surface: when a quality item is upgraded (e.g. diamond → netherite), the attribute snapshot is rebuilt from the upgraded item's own base stats and the durability bonus is recomputed against the upgraded item's base durability, so the bonus scales with the new material instead of carrying the old item's stale values.

---

## `neoorigins:craft_amount_bonus`

Grants bonus items when crafting a specific output (e.g., more planks per log). Hooks `PlayerEvent.ItemCraftedEvent` directly, so bonuses only trigger on genuine crafting operations.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `output_item` | Identifier or `#tag` | no | `minecraft:oak_planks` | Item or item tag to watch for; the bonus copies whatever was crafted |
| `bonus_count` | int | no | `4` | Extra copies granted on every craft: exactly this many, with no roll or cap. Copies that do not fit are lost. |

**Example: quadruple planks from logs**
```json
{
  "type": "neoorigins:craft_amount_bonus",
  "output_item": "minecraft:oak_planks",
  "bonus_count": 4,
  "name": "Lumberwright",
  "description": "Gets extra planks when crafting from logs."
}
```

The bonus fires once per craft event; shift-clicking triggers one event per output stack.

---

## `neoorigins:more_smoker_xp`

When the player takes cooked food out of a furnace or smoker, they gain `round(multiplier)` extra nutrition and `multiplier × 0.25` extra saturation. It fires on taking the result, so output pulled by a hopper gives nothing. No XP is granted, despite the name.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `multiplier` | float | no | `2.0` | Nutrition/saturation bonus multiplier |

---

## `neoorigins:trade_availability`

Periodically resets villager trade uses for every villager within a radius of the player.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `scan_interval` | int | no | `40` | Ticks between scans |
| `radius` | double | no | `8.0` | Scan radius in blocks |

**Example:**
```json
{
  "type": "neoorigins:trade_availability",
  "scan_interval": 40,
  "radius": 8.0,
  "name": "Charismatic",
  "description": "Villagers near you reset their trades."
}
```

---

## `neoorigins:rare_wandering_loot`

When a holder right-clicks a wandering trader, that trader gains extra offers (master-tier villager trades and, by chance, a rare treasure trade). This happens once per trader and player; the added offers are then visible to anyone who trades with that trader.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `master_slots` | int | no | `3` | Random master-tier villager trades added to wandering traders. |
| `treasure_chance` | double (0.0–1.0) | no | `0.25` | Probability a rare treasure trade is also offered. |

**Example:**
```json
{
  "type": "neoorigins:rare_wandering_loot",
  "name": "Curio Magnet",
  "description": "Wandering traders bring rarer wares while you're around."
}
```

---

## `neoorigins:kill_loot_drops`

Passive power that layers extra item drops onto the vanilla loot of any mob the holder kills: the bonus items appear as real drops from the mob's corpse, flowing through the loot pipeline (drop events, other loot-modifying mods) rather than being pushed straight into the inventory. It is implemented as a NeoForge global loot modifier keyed on the killer (the mirror image of the mob-origin drop hook), so it fires only on entity-death loot tables, never on chests, blocks, or fishing.

One power can touch many mob loot tables at once: list an entry per mob type in `drops`, or match a whole category from a single entry with `entity_tag` / `entity_types`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `drops` | array | no | `[]` | The drop rules. Each entry rolls independently against the killed mob; an entry with no valid target or an unknown `item` is skipped with a warning at load time. Absent or empty is not a load error: the power simply drops nothing, so it is worth setting. |

Each entry in `drops` takes exactly one target form plus an item:

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `entity_type` | resource-loc | one of | — | A single exact mob type, e.g. `minecraft:zombie`. |
| `entity_tag` | resource-loc | one of | — | An entity-type tag, e.g. `minecraft:skeletons`, matches every type in the tag. Write it without a leading `#`; with one the entry is skipped with a warning. |
| `entity_types` | array | one of | — | An explicit list of exact mob types. |
| `item` | resource-loc | yes | — | The item to drop. An unknown id skips the entry. |
| `chance` | float | no | `1.0` | Probability in `[0,1]` that this entry drops on a matching kill. |
| `count` | int | no | `1` | Stack size dropped (minimum 1). |

Exactly one of `entity_type` / `entity_tag` / `entity_types` must be set per entry. The drop only rolls when the killer is a player who holds the power: mob-on-mob and environmental deaths are ignored. A player may hold several `kill_loot_drops` powers at once (e.g. one per origin tier); their rule lists are concatenated, and revoking one leaves the others intact.

### Worked example: Head Hunter

`data/neoorigins/origins/powers/head_hunter_drops.json`:
```json
{
  "type": "neoorigins:kill_loot_drops",
  "name": "Head Hunter",
  "description": "5% chance to drop the matching head when you kill a zombie, creeper, skeleton or piglin.",
  "drops": [
    { "entity_type": "minecraft:zombie",   "item": "minecraft:zombie_head",    "chance": 0.05 },
    { "entity_type": "minecraft:creeper",  "item": "minecraft:creeper_head",   "chance": 0.05 },
    { "entity_type": "minecraft:skeleton", "item": "minecraft:skeleton_skull", "chance": 0.05 },
    { "entity_type": "minecraft:piglin",   "item": "minecraft:piglin_head",    "chance": 0.05 }
  ]
}
```

The same idea, hooking every undead type from a single entry:
```json
{ "entity_tag": "minecraft:undead", "item": "minecraft:bone", "chance": 0.25 }
```

### Activation

The hook is a global loot modifier, so it has to be switched on by a carrier file: unlike `mob_origin_drops`, the mod does not auto-generate one for you. Ship these two files alongside the power (the Head Hunter example includes both):

`data/neoforge/loot_modifiers/global_loot_modifiers.json`:
```json
{
  "replace": false,
  "entries": [ "neoorigins:kill_loot_drops" ]
}
```

`data/neoorigins/loot_modifiers/kill_loot_drops.json`:
```json
{
  "type": "neoorigins:kill_loot_drops",
  "conditions": []
}
```

`replace: false` makes NeoForge merge the entry list additively across packs, so this coexists with the mod's own `mob_origin_drops` carrier and any other pack's modifiers. The per-power `drops` live in the power file, not here: this carrier is a data-free on-switch, so one carrier covers every `kill_loot_drops` power in the pack.

---

## `neoorigins:loot_pool_grant`

Active power that rolls a vanilla loot table on activation and grants every rolled stack to the player. Lets pack authors deliver weighted, conditional, function-driven rewards through the full vanilla loot infrastructure instead of a flat item list, and (when FTB Quests is installed) reuse the same loot table as a quest reward via a tag-marker. The grant fires once per `grant_id`: after it lands, further activations with the same id do nothing until an origin reset clears the record.

> **1.21.1 vs 26.x:** the FTB Quests reward type and tag marker work on 1.21.1 and 26.1; 26.2 and 26.3 have no working FTB Quests hook, so neither grants anything there. The power itself works on every version.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `grant_id` | string | yes | — | Unique id tracked so the bundle is granted only once per player. Shares the `starting_equipment` grant record, so a full `/neoorigins reset <player>` (no layer argument) clears both; an Orb does not. |
| `loot_table` | resource-loc | yes | — | The vanilla loot table to roll. Reuses the full vanilla loot infrastructure (weighted entries, conditions, functions, modifiers). |
| `rolls` | int | no | `1` | Times the table is rolled per activation. |
| `bonus_rolls` | int | no | `0` | Extra rolls added to `rolls`. Mirrors vanilla loot-pool naming. |
| `active` | string | no | `""` | Optional display-only translation key advertising which keybind slot the power expects (e.g. `key.use_skill_1`). |
| `cooldown` | int | no | `0` | Cooldown in ticks between activations (`20` = 1s). |

Overflow that does not fit in the inventory is dropped at the player's feet; no stacks are silently lost. Empty rolls (a table that returns no items) do not consume the `grant_id`, so the author can fix the table and the player can re-activate.

### Worked example: wood starter pack

`data/neoorigins/loot_tables/rewards/wood_starter.json`:
```json
{
  "type": "minecraft:gift",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        { "type": "minecraft:item", "name": "minecraft:oak_log", "weight": 3,
          "functions": [{ "function": "minecraft:set_count", "count": { "min": 8, "max": 16 } }] },
        { "type": "minecraft:item", "name": "minecraft:birch_log", "weight": 1,
          "functions": [{ "function": "minecraft:set_count", "count": { "min": 8, "max": 16 } }] }
      ]
    },
    {
      "rolls": 1,
      "entries": [
        { "type": "minecraft:item", "name": "minecraft:stone_axe" }
      ]
    }
  ]
}
```

`data/neoorigins/origins/powers/lumberjack_starter_pack.json`:
```json
{
  "type": "neoorigins:loot_pool_grant",
  "name": "Starter Pack",
  "description": "Rolls a weighted bundle of logs plus a stone axe on use.",
  "grant_id": "lumberjack:starter_pack_v1",
  "loot_table": "neoorigins:rewards/wood_starter",
  "rolls": 1,
  "bonus_rolls": 0,
  "active": "key.use_skill_1",
  "cooldown": 0
}
```

### FTB Quests soft-compat

When `ftbquests` is on the mod list, NeoOrigins listens for FTBQ quest completion. Any quest tagged

```
neoorigins_loot_pool_grant:<loot_table_id>
```

routes through the same `LootPoolGrantPower#fireLootPoolGrant` pipeline on completion: every online member of the completing team receives the rolled stacks, with dedup keyed on `ftbq:<quest_id>:<table_id>`. Authors get vanilla loot-table reuse for both origin powers and quest rewards without any hard FTBQ dependency.

This is a soft-compat layer: it is **not** an FTBQ `RewardType` registration. On 26.2 there is no FTB Quests build to hook into, so in practice the tag marker does nothing on this version; the power itself works without FTBQ.
