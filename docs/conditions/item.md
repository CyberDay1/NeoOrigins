---
title: "Item conditions"
parent: "Conditions"
grand_parent: "DSL Reference"
nav_order: 4
permalink: /conditions/item
---

# Item conditions

Conditions on an item stack, used by item filters such as `equipped_item`.

Item conditions evaluate to true/false against an `ItemStack`. Used inside `equipped_item` and `inventory` entity conditions, event-power `item_condition` filters, and `if_else` item actions. They use the `ItemConditionParser` verb set. All item conditions support the universal `"inverted": true` flag.

## `neoorigins:empty`

True when the stack is empty. No fields.

## `neoorigins:nbt`

True when the stack's NBT contains the given SNBT compound (subset match: every expected key must exist with an equal-or-containing value; list elements match if any actual element contains them).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `nbt` | string (SNBT) | yes | — | SNBT subtree to match against |

> **Legacy component-view bridge.** Apoli packs wrote this condition against the pre-1.21 item NBT layout, which 1.21 replaced with data components. The stack's components are therefore projected back into a legacy-shaped view (`Potion`, `CustomPotionColor`, `CustomPotionEffects`, `Enchantments`/`StoredEnchantments`, `display.Name`/`display.Lore`, `Damage`, `RepairCost`, `Unbreakable`, `CustomModelData`) merged over `minecraft:custom_data`, and the subtree match runs against that view. So `{ "nbt": "{Potion:\"minecraft:swiftness\"}" }` still matches a swiftness potion, and scalar types mirror the 1.20.1 save format exactly (enchantment `lvl` is a short: write `5s`).

**Example:**
```json
{ "type": "neoorigins:nbt", "nbt": "{Potion:\"minecraft:swiftness\"}" }
```

## `neoorigins:enchantment`

True when the stack has the given enchantment at or above a minimum level.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `enchantment` | resource id | yes | — | Enchantment ID |
| `comparison` | string | no | `>=` | Operator against the enchantment level: `==`, `!=`, `>`, `>=`, `<`, `<=` |
| `compare_to` | int | no | `1` | Enchantment level threshold |

## `neoorigins:ingredient`

True when the stack matches a vanilla ingredient (item ID or tag).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `item` | resource id | no | — | Exact item ID |
| `tag` | resource id | no | — | Item tag |

Bare item ID strings (no `type` field) also match via the ingredient fallback path.

The Origins wrapper form nests the item/tag under an `ingredient` key, and vanilla's union (array) form is accepted there too. It matches when *any* entry matches:

```json
{
  "type": "neoorigins:ingredient",
  "ingredient": [
    { "tag": "origins:ignore_diet" },
    { "tag": "origins:vegetarian" }
  ]
}
```

## `neoorigins:amount`

Numeric comparison against the stack count.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | int | no | `1` | Stack count threshold |

**Example:**
```json
{ "type": "neoorigins:amount", "comparison": ">=", "compare_to": 16 }
```

## `neoorigins:name`

True when the stack's display name (hover name: custom name if renamed, else the default item name) exactly equals the given text. False on empty stacks.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `name` | string | yes | — | Exact display-name text; always-false when absent |

**Example:**
```json
{ "type": "neoorigins:name", "name": "Excalibur" }
```

## `neoorigins:food`

True when the item has a food component (anything edible). No fields.

**Example:**
```json
{ "type": "neoorigins:food" }
```

## `neoorigins:meat`

True when the item is meat, raw or cooked. No fields.

> Apoli read this off `FoodProperties.isMeat()`, a flag Mojang removed when food became a data component, so there is no literal equivalent left. It resolves through the NeoForge common food tags (`c:foods/raw_meat`, `c:foods/cooked_meat`) instead, which — unlike a hardcoded item list — picks up modded meats for free. That is what a pack author writing a herbivore restriction actually wants.

**Example:**
```json
{ "type": "neoorigins:meat", "inverted": true }
```

## `neoorigins:armor_value`

Numeric comparison against the armor this **one stack** confers. Not the wearer's total: that is the identically named *entity* condition in [CONDITIONS.md](../CONDITIONS.md). Items that grant no armor report `0`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | number | no | `0.0` | Armor points |

> Read off the stack's `minecraft:attribute_modifiers` (flat `add_value` bonuses on `minecraft:armor`), not from an `ArmorItem` cast. Armor has been data-driven since 1.20.5, so this also covers datapack-defined and modded armor — and on the 26.x branches `ArmorItem` no longer exists at all.

**Example: only chestplates worth more than leather**
```json
{ "type": "neoorigins:armor_value", "comparison": ">", "compare_to": 3 }
```

## `neoorigins:harvest_level`

Numeric comparison against the tool's mining tier, using Apoli's numbering: wood and gold `0`, stone `1`, iron `2`, diamond `3`, netherite `4`. Anything that is not a tool reports `0`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | int | no | `0` | Tier threshold |

> Minecraft removed numeric harvest levels in 1.20.5 and replaced them with per-tier "blocks this tool cannot drop" tags, so the level is recovered from the tag the stack's `minecraft:tool` component denies drops for. Modded tools that follow the vanilla tier tags are numbered correctly for free. On 26.x the copper tier shares stone's `1`, because Apoli has no number for it and shifting the scale would silently re-tune every pack that gates on iron.

**Example: netherite-only**
```json
{ "type": "neoorigins:harvest_level", "comparison": ">=", "compare_to": 4 }
```

## `neoorigins:durability`

Numeric comparison against **remaining** durability, i.e. how many more hits the stack has left. Items with no durability report `0`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `comparison` | string | no | `">="` | Comparison operator |
| `compare_to` | int | no | `0` | Remaining durability threshold |

**Example: nearly broken**
```json
{ "type": "neoorigins:durability", "comparison": "<=", "compare_to": 10 }
```

## `neoorigins:constant` (item)

Fixed result, ignoring the stack. Useful as a placeholder while authoring.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `value` | boolean | no | `false` | Result this condition always returns |

## `neoorigins:and` / `neoorigins:or` / `neoorigins:not` (item)

Standard boolean combinators, same shape as entity conditions but operating on `ItemStack`. `all_of` / `any_of` are accepted as aliases of `and` / `or` (the Apoli 2.9+ renames), matching the entity-side conditions.
