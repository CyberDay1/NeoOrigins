---
title: "Item actions"
parent: "Entity Actions"
grand_parent: "DSL Reference"
nav_order: 5
permalink: /actions/item
---

# Item actions

Actions that run on an item stack.

Item actions operate on a single `ItemStack` and are used inside `equipped_item_action`, `modify_inventory`, and other per-stack contexts. They use the `ItemActionParser` verb set.

## `neoorigins:merge_nbt`

Merges SNBT data into the stack's data components via `LegacyTagToComponents`. Pre-1.21 SNBT keys `Potion`, `CustomModelData`, `Damage`, `Unbreakable`, `RepairCost` and `display` (Name / Lore) are translated to their data components; other keys are merged into `minecraft:custom_data`. `Enchantments`, `StoredEnchantments` and `ench` are dropped, because this path has no registry access to resolve them.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `nbt` | string (SNBT) | yes | — | SNBT to merge |

## `neoorigins:consume`

Shrinks the stack by `amount`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `amount` | int | no | `1` | Items to remove |

## `neoorigins:damage`

Damages the stack's durability.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `amount` | int | no | `1` | Durability to remove |
| `ignore_unbreaking` | bool | no | `false` | Accepted, but changes nothing: the damage is always added directly and Unbreaking is never rolled |

A stack whose damage reaches its maximum durability is destroyed. Non-damageable stacks are left alone.

## `neoorigins:set_count`

Sets the stack's count.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `count` | int | no | `1` | New stack size |

## `neoorigins:remove_enchantment`

Strips the named enchantments off the stack, or knocks a set number of levels off them, optionally clearing its accumulated anvil repair cost. The classic use is a "Remove Curse" power that ticks over the worn armour and held weapon stripping `minecraft:vanishing_curse` and `minecraft:binding_curse`.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `enchantment` | string | no | - | Single enchantment id to strip |
| `enchantments` | array of string | no | - | Enchantment ids to strip; usable instead of or alongside `enchantment` |
| `levels` | int | no | - | Levels to subtract instead of removing outright; omit to always remove outright |
| `reset_repair_cost` | bool | no | `false` | Also clear the stack's anvil repair cost |

Both key spellings are read, and the union is used when both are present. `reset_repair_cost` is read from this object and from the enclosing `equipped_item_action`, where packs commonly put it as a sibling of `item_action`; `true` in either position wins.

`levels` is a number of levels to take off, not a threshold or a cap. With it omitted every named enchantment comes off outright. With it set, each named enchantment is reduced by that many levels and only disappears once the reduction would take it to zero or below: `"levels": 1` turns Sharpness V into Sharpness IV, and takes Sharpness I off entirely. `"levels": 0` therefore changes nothing. A negative value raises the level instead, which is the same plain subtraction upstream does; it is not clamped, though vanilla's own ceiling of 255 still applies. A value that is not a number at all, a string or an object say, warns and is treated as absent, so the enchantments are removed outright. A fractional value is not rejected: it truncates toward zero, so `"levels": 1.5` reduces by one and `"levels": -1.5` raises by one. That is deliberate. Upstream refuses the whole file, which a fail-soft parser cannot do, and treating a fraction as absent would strip the enchantment entirely, which is the very failure this field exists to end.

Ids are matched against the enchantments already on the stack, so an id no datapack registered simply matches nothing. A malformed id warns once at load and is skipped rather than taking the power down with it. Enchanted books are handled: removal routes to `stored_enchantments` for books and `enchantments` for everything else.

**Example: strip both curses off the held item and reset its repair cost**
```json
{
  "type": "neoorigins:equipped_item_action",
  "equipment_slot": "mainhand",
  "item_action": {
    "type": "neoorigins:remove_enchantment",
    "enchantments": ["minecraft:vanishing_curse", "minecraft:binding_curse"]
  },
  "reset_repair_cost": true
}
```

**Example: grind one level of Sharpness off the held weapon per tick**
```json
{
  "type": "neoorigins:equipped_item_action",
  "equipment_slot": "mainhand",
  "item_action": {
    "type": "neoorigins:remove_enchantment",
    "enchantment": "minecraft:sharpness",
    "levels": 1
  }
}
```

## `neoorigins:and` (item)

Runs multiple item actions in sequence.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `actions` | array of item action | yes | — | Actions to run in order. A single item action object is also accepted |

## `neoorigins:if_else` (item)

Conditional item action dispatch.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `condition` | item condition | no | always true | Guard; an absent condition passes |
| `if_action` | item action | no | noop | Runs when condition passes. Object only |
| `else_action` | item action | no | noop | Runs when condition fails. Object only |
