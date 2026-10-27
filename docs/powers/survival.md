---
title: "Health, hunger & survival"
parent: "Power Types"
grand_parent: "DSL Reference"
nav_order: 8
permalink: /powers/survival
---

# Health, hunger & survival

Regeneration, hunger and diet, respawn location and items kept on death.

Fields every power accepts (`name`, `hidden`, `power_condition`, ...) are on [Power basics](../concepts/power-basics.md). See also: [Power basics](../concepts/power-basics.md).

Types on this page: [`no_natural_regen`](#neooriginsno_natural_regen) · [`exhaustion_filter`](#neooriginsexhaustion_filter) · [`modify_food_nutrition`](#neooriginsmodify_food_nutrition) · [`edible_item`](#neooriginsedible_item) · [`slime_moisture`](#neooriginsslime_moisture) · [`slime_level_hp`](#neooriginsslime_level_hp) · [`slime_death_save`](#neooriginsslime_death_save) · [`modify_player_spawn`](#neooriginsmodify_player_spawn) · [`keep_inventory`](#neooriginskeep_inventory).

---

## `neoorigins:no_natural_regen`

Cancels vanilla food-based natural regeneration only. Both regen branches (saturation-based fast regen and food-level-≥18 slow regen) are skipped. Other heal sources (Regeneration potion, beacon, totem, `neoorigins:heal`) still work normally. Pair with an alternate healing mechanic for "metabolism-less" origins like Automaton variants.

No fields. Presence of the power is the entire configuration.

**Example:**
```json
{
  "type": "neoorigins:no_natural_regen",
  "name": "No Pulse",
  "description": "Hunger doesn't restore HP. Only direct healing effects do."
}
```

---

## `neoorigins:exhaustion_filter`

Cancels the hunger exhaustion vanilla charges for specific actions, leaving every other source alone. `sprint` is sprinting on the ground, `mining` is breaking a block, `jump` is any jump, and `swim` is swimming, walking underwater or wading. Imported `origins-classes:no_sprint_exhaustion` / `no_mining_exhaustion` translate onto this type. In 2.2.30 and earlier the power loaded but nothing read it, so it did nothing.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `sources` | list of string | no | `["sprint"]` | Exhaustion sources to cancel: `sprint`, `mining`, `jump`, `swim` |

**Example:**
```json
{
  "type": "neoorigins:exhaustion_filter",
  "sources": ["sprint"],
  "name": "Tireless",
  "description": "Sprinting no longer drains hunger."
}
```

---

## `neoorigins:modify_food_nutrition`

Overrides the nutrition (hunger) value of food the player eats. Matching food gives exactly the configured number of hunger points regardless of its original value. Saturation is scaled proportionally. Use `food_item` or `food_tag` to filter which foods are affected; if neither is set, ALL food is affected.

The override is applied at the moment of eating rather than written onto the item, so the item's own food data is never changed. That is invisible in play but it matters to anything that reads the stack: with AppleSkin installed, NeoOrigins feeds the adjusted figures to AppleSkin's tooltip and held-food preview so they agree with what eating will actually give. See [COMPATIBILITY.md](../COMPATIBILITY.md). Other mods that read `DataComponents.FOOD` off the stack directly will still see the vanilla value.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `nutrition` | int | no | `1` | Fixed hunger points matching food gives |
| `food_item` | Identifier | no | — | Only affect this specific item (e.g. `minecraft:sweet_berries`) |
| `food_tag` | string | no | — | Only affect items in this tag (e.g. `#minecraft:meat`). Supports `#` prefix. |

**Example: all food gives 1 hunger point**
```json
{
  "type": "neoorigins:modify_food_nutrition",
  "nutrition": 1,
  "name": "Picky Eater",
  "description": "Gains almost no nutrition from food."
}
```

**Example: meat gives 8 hunger points**
```json
{
  "type": "neoorigins:modify_food_nutrition",
  "nutrition": 8,
  "food_tag": "#minecraft:meat",
  "name": "Carnivore",
  "description": "Thrives on meat."
}
```

---

## `neoorigins:edible_item`

Makes arbitrary non-food items edible. A matching item plays the normal 32-tick eating animation; when it finishes, the configured nutrition/saturation is applied, one is removed from the stack, and an `action_on_event.ITEM_USE_FINISH` dispatch fires with the stack as context. Items that are already food keep their vanilla values: the configured `nutrition`, `saturation` and `consume_sound` are ignored for anything that has a food component (raw fish, rotten flesh).

> **1.21.1 vs 26.x:** the hidden `model_color` tints the Caveborn meals drive are drawn on 1.21.1 only; on 26.x the effects and bonus powers still apply but the model is not recolored.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `items` | list of Identifier | no | `[]` | Exact item IDs that qualify |
| `tags` | list of Identifier | no | `[]` | Item tag IDs that qualify |
| `tiers` | list of Tier | no | `[]` | Value bands inside the set; see below |
| `nutrition` | int | no | `4` | Food points restored (1 shank = 2) |
| `saturation` | float | no | `0.3` | Saturation restored |
| `always_edible` | bool | no | `true` | If true, can be eaten at full hunger |
| `consume_sound` | Identifier | no | _(none)_ | Optional sound ID played on consume |

At least one of `items`, `tags` or `tiers` should be non-empty, otherwise nothing will ever match. Matching is inclusive: an item qualifies if it appears in any of the three.

Eligibility follows the game's own rule for food (`always_edible`, or an unfilled hunger bar, or creative and spectator mode), so a matching item eats in creative exactly as bread does. Creative also keeps the stack, the same way vanilla food does.

### Tiers

A tier gives one part of the set its own values without splitting the power in two. Each entry is an object:

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `items` | list of Identifier | no | `[]` | Exact item IDs in this band |
| `tags` | list of Identifier | no | `[]` | Item tag IDs in this band |
| `nutrition` | int | **yes** | _(none)_ | Food points for this band |
| `saturation` | float | no | _(inherits)_ | Saturation for this band |

The first tier whose `items` or `tags` match wins; an item matching none of them falls back to the power's own `nutrition` and `saturation`. Order matters, so put the narrow bands first. Tiers match on top of the top-level lists rather than inside them, so a tier can introduce items the power never otherwise mentions.

Leave `saturation` off unless you mean to change it. Saturation is restored as `nutrition × saturation × 2`, so a tier that doubles both ends up granting four times the saturation, which is almost never what was intended.

```json
{
  "type": "neoorigins:edible_item",
  "tags": ["neoorigins:caveborn_eat_iron"],
  "nutrition": 4,
  "saturation": 0.8,
  "tiers": [
    { "tags": ["c:nuggets/iron"], "nutrition": 1, "saturation": 0.0 },
    { "tags": ["c:storage_blocks/iron", "c:storage_blocks/raw_iron"], "nutrition": 8 }
  ]
}
```

**Example: a sea origin grazes on kelp and seagrass**
```json
{
  "type": "neoorigins:edible_item",
  "items": ["minecraft:kelp", "minecraft:seagrass"],
  "nutrition": 2,
  "saturation": 0.3,
  "always_edible": true,
  "name": "Grazer",
  "description": "Can eat kelp and seagrass at any time."
}
```

### Caveborn diet tags

The Caveborn's seven eating powers each read one shipped item tag, so a datapack can extend any of them without overriding the power. A tag file lives under the namespace of the tag it extends, not your own, so declare `data/neoorigins/tags/item/<name>.json` inside your pack with `"replace": false` and append your own items.

| Tag | Backing power | Nugget | Base | Block |
|---|---|---|---|---|
| `neoorigins:caveborn_eat_stone` | *Stone Eater* | _(none)_ | 2 / 0.4 | _(none)_ |
| `neoorigins:caveborn_eat_copper` | *Copper Palate* | _(none)_ | 3 / 0.6 | 6 / 0.6 |
| `neoorigins:caveborn_eat_iron` | *Iron Palate* | 1 / 0.0 | 4 / 0.8 | 8 / 0.8 |
| `neoorigins:caveborn_eat_gold` | *Gilded Palate* | 1 / 0.0 | 5 / 1.0 | 10 / 1.0 |
| `neoorigins:caveborn_eat_diamond` | *Diamond Palate* | _(none)_ | 6 / 1.2 | 12 / 1.2 |
| `neoorigins:caveborn_eat_emerald` | *Emerald Palate* | _(none)_ | 7 / 1.4 | 14 / 1.4 |
| `neoorigins:caveborn_eat_netherite` | *Netherite Palate* | _(none)_ | 8 / 1.6 | 16 / 1.6 |

The three columns are `tiers` on each power. The base column is what an ingot, gem, raw material or ore is worth; the block column covers both the refined and the raw storage block; the nugget column exists only where the material has a nugget. Adding an item to a tag makes it edible at that tag's base values unless it also falls inside one of the tiers, and the tiers read the `c:` tags rather than the `neoorigins:` one, so a modded iron block dropped into `caveborn_eat_iron` is worth eight only if it also registers in `c:storage_blocks/iron` or `c:storage_blocks/raw_iron`, and four if it does not. To grant an amount none of the bands cover, write your own `edible_item` power instead.

Every one of these powers is `always_edible: false`, so none of them go down on a full hunger bar.

The seven tags are built out of NeoForge's `c:` common tags rather than lists of vanilla item ids, so a modded metal or stone that registers itself in the usual places is eaten without the pack doing anything. The six metal and gem tags take the material's `ingots/`, `nuggets/`, `gems/`, `raw_materials/`, `ores/` and `storage_blocks/` entries, which is what pulls in deepslate ore variants, nether gold ore and ancient debris. Three literal ids are left in the set. `netherite_scrap` is one, because NeoForge publishes no common tag for the item itself: its `c:ores/netherite_scrap` covers ancient debris, the ore block. The other two are on `caveborn_eat_stone`, which is `#c:stones` plus `#c:cobblestones` plus basalt and blackstone, and carries no ore at all.

Each material also has a bonus power that fires on finishing the meal, and those are tiered the same way: a nugget grants fifteen seconds, the base form grants the listed duration, and a block grants three times it. Copper is Water Breathing I for sixty seconds, iron Haste I for sixty, gold Speed I for two minutes, diamond Luck II for five, emerald Fire Resistance for five, and netherite Strength I with Resistance I for five. A block is worth twice the food and three times the duration rather than the nine you would get from crafting it apart, since hunger caps at twenty and the extra food would be thrown away. Each meal additionally drives a hidden `model_color` tint for as long as its effect runs; those six tints are individually switchable from `power_overrides.toml`.

Worth knowing for the ore and stone entries: both are placeable blocks, so aiming at a surface places one as normal. Eating one means aiming where it cannot be placed — the same rule the stone diet has always followed.

---

## `neoorigins:slime_moisture`

Custom resource bar (0.0–1.0 float) that drains passively over time, faster in dry biomes (desert, badlands, savanna) and much faster when on fire. Replenished by standing in water, rain or a water cauldron, and by drinking a water bottle. Triggers threshold effects: Regeneration above 75%, armor penalty below 10%, and damage-over-time at 0%.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `drain_per_tick` | float | no | `0.0004` | Base moisture drain per tick |
| `dry_biome_drain_multiplier` | float | no | `3.0` | Drain multiplier in desert/badlands/savanna biomes |
| `fire_drain_multiplier` | float | no | `10.0` | Drain multiplier when player is on fire |
| `water_refill_per_tick` | float | no | `0.005` | Moisture gained per tick in water, rain or a water cauldron |
| `water_bottle_refill` | float | no | `0.5` | Moisture restored by drinking one water bottle; `0` leaves the bar unchanged |
| `regen_threshold` | float | no | `0.75` | Moisture level above which Regeneration I is applied |
| `armor_penalty_threshold` | float | no | `0.10` | Below this, -4 armor is applied |
| `dot_threshold` | float | no | `0.0` | At or below this, damage-over-time triggers |
| `dot_damage` | float | no | `1.0` | Damage per interval when at or below dot_threshold |
| `dot_interval` | int | no | `40` | Ticks between damage ticks |

A cauldron is checked as a block rather than as a fluid, so the player has to be standing in it. A water bottle refills on the swallow, not on the click, and where a player holds more than one moisture power the most generous `water_bottle_refill` among them is the one that applies. Both affordances match what `breath_out_of_fluid` already gives aquatic origins.

**Example:**
```json
{
  "type": "neoorigins:slime_moisture",
  "name": "Moisture",
  "description": "Must stay hydrated. Dries out faster in hot biomes."
}
```

---

## `neoorigins:slime_level_hp`

Grants bonus max HP based on the player's experience level. The bonus tracks your current level: it is recomputed from the level you have right now, so it falls when you lose levels and comes back when you earn them again.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `levels_per_hp` | int | no | `10` | Experience levels needed per +1 max HP |
| `max_bonus_hp` | int | no | `20` | Maximum HP bonus cap |

**Example: +1 HP every 10 levels, capped at +20**
```json
{
  "type": "neoorigins:slime_level_hp",
  "levels_per_hp": 10,
  "max_bonus_hp": 20,
  "name": "Growth",
  "description": "Grows tougher with experience."
}
```

---

## `neoorigins:slime_death_save`

Death prevention mechanic for slime-themed origins. When the player would die with moisture above the threshold, they "split" instead: teleported to a random location, max HP reduced to 2 hearts, which gradually recovers over time.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `moisture_threshold` | float | no | `0.75` | Moisture level required to trigger the save |
| `teleport_distance` | int | no | `50` | Horizontal blocks to teleport |
| `teleport_y_range` | int | no | `10` | Random Y offset range (±) |
| `split_max_hp` | float | no | `4.0` | Max HP while "split" (2 hearts) |
| `recovery_ticks` | int | no | `2400` | Ticks for HP to recover to normal (default: 120 seconds) |

**Example:**
```json
{
  "type": "neoorigins:slime_death_save",
  "name": "Slime Split",
  "description": "Instead of dying, splits and teleports away — but at 2 hearts."
}
```

> Pair it with a companion `slime_moisture` power. Without one, moisture reads as 1.0 and never changes, so the save fires on **every** death. The save costs no moisture and has no cooldown.

---

## `neoorigins:modify_player_spawn`

Overrides the player's respawn location to a power-configured target. Unlike the origin's `spawn_location` (first-join and bed-less respawn only), this power fires on every respawn except the one after leaving the End. The teleport lands a tick or more after the respawn. Optionally also overrides the bed/respawn-anchor spawn point.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `location` | LocationCondition | yes | — | Target location descriptor: `dimension` / `biome` / `biome_tag` / `structure` / `structure_tag`. See `attribute_modifier`'s `location_condition` for the shape. |
| `override_bed` | bool | no | `false` | When true, also overrides bed/respawn-anchor spawn. When false, respects a valid bed/anchor and only fires if none is set. |

**Example: always respawn in an End city**
```json
{
  "type": "neoorigins:modify_player_spawn",
  "location": {
    "dimension": "minecraft:the_end",
    "structure": "minecraft:end_city"
  },
  "override_bed": true,
  "name": "Void-Bound",
  "description": "Always respawns in the nearest End city."
}
```

---

## `neoorigins:keep_inventory`

On death, selectively retain inventory items matching the power's filters. Matching items are removed from drops and restored on respawn into the exact slot they were taken from, trinket slots included: armour comes back worn, the hotbar keeps its layout, and a full inventory can no longer push the leftovers onto the ground.

> **1.21.1 vs 26.x:** items worn in Accessories (Wisp Forest) slots are kept on 1.21.1 only; 26.x reads Curios slots alone, since Accessories has no 26.x build.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `slots` | list of string | no | `["*"]` | Slot categories to keep from: `hotbar`, `main`, `armor`, `offhand`, `all` (alias `*`). `main` also includes `hotbar`. Also accepts trinket slots: `curio`, `accessory`, or `trinket` cover every Curios/Accessories slot, or name a specific slot id like `ring` or `necklace`. |
| `items` | list of Identifier | no | `[]` | Specific items to keep |
| `tags` | list of Identifier | no | `[]` | Item tags to keep |

If both `items` and `tags` are empty, every item in the configured slots is kept.

**Curios / Accessories:** trinket slots are covered when the [Curios](https://www.curseforge.com/minecraft/mc-mods/curios) or [Accessories](https://modrinth.com/mod/accessories) mod is installed; without them these slot tokens simply match nothing. The wildcard `*`/`all` includes trinkets too.

A kept trinket is re-equipped into its original slot on respawn. Those mods rebuild their slot counts on their own schedule after a respawn, so if the slot isn't writable yet the trinket is held and retried for three seconds rather than given up on. Only once that window is spent does it fall back to the regular inventory, and to the ground only if the inventory is full as well. Logging out mid-retry settles it the same way instead of losing it.

**Example: always keep tools**
```json
{
  "type": "neoorigins:keep_inventory",
  "slots": ["main", "hotbar", "offhand"],
  "tags": ["minecraft:pickaxes", "minecraft:swords", "minecraft:axes", "minecraft:shovels"],
  "name": "Tool Preservation",
  "description": "Tools are never dropped on death."
}
```
