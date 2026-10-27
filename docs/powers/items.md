---
title: "Items & inventory"
parent: "Power Types"
grand_parent: "DSL Reference"
nav_order: 9
permalink: /powers/items
---

# Items & inventory

Starting items, extra inventory, and limits on what the player can wear, hold and use.

Fields every power accepts (`name`, `hidden`, `power_condition`, ...) are on [Power basics](../concepts/power-basics.md). See also: [Power basics](../concepts/power-basics.md), [Toggles](../concepts/toggles.md).

Types on this page: [`starting_equipment`](#neooriginsstarting_equipment) · [`extra_inventory`](#neooriginsextra_inventory) · [`restrict_armor`](#neooriginsrestrict_armor) · [`restrict_items`](#neooriginsrestrict_items) · [`prevent_item_damage`](#neooriginsprevent_item_damage) · [`item_magnetism`](#neooriginsitem_magnetism) · [`less_item_use_slowdown`](#neooriginsless_item_use_slowdown).

---

## `neoorigins:starting_equipment`

Grants the player one or more items (optionally with enchantments) once on first origin assignment. Tracks grant state per-player so the items are not re-granted on respawn.

There are two shapes. The **singular** shape sets `item` at the root and grants one stack. The **plural** shape sets `stacks` and grants several. Pick one: they do not combine. See [Singular vs plural](#singular-vs-plural) below.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `grant_id` | string | yes | — | Unique ID tracked so the bundle is granted only once per player. Dedups the whole power, not individual stacks. |
| `stacks` | list | no | `[]` | Plural shape: array of stack entries, each taking the same per-stack fields as the singular shape (`item`, `count`, `enchantments`, `legacy_tag`, `components`). Granted in array order. |
| `item` | Identifier | no | `""` | Singular shape: item to grant, e.g. `minecraft:trident`. Optional only when `stacks` is provided; one of the two must be present. |
| `count` | int | no | `1` | Singular shape: stack count |
| `enchantments` | list | no | `[]` | Singular shape: list of `{"id": "...", "level": N}` enchantment entries |
| `legacy_tag` | string | no | `""` | SNBT string for legacy NBT data (Potion, display.Name, etc.). Recognised keys are translated to data components; unrecognised keys go to `minecraft:custom_data`. |
| `components` | string | no | `""` | SNBT string representing a `DataComponentPatch`. Supports any registered data component (vanilla or modded). Parsed with registry context at grant time. |

**Example: enchanted trident**
```json
{
  "type": "neoorigins:starting_equipment",
  "grant_id": "abyssal_trident",
  "item": "minecraft:trident",
  "enchantments": [
    {"id": "minecraft:mending", "level": 1},
    {"id": "minecraft:unbreaking", "level": 3},
    {"id": "minecraft:riptide", "level": 3}
  ],
  "name": "Deep-Sea Armament",
  "description": "Begins life with an enchanted trident."
}
```

**Example: modded item with custom data components (Iron's Spellbooks)**
```json
{
  "type": "neoorigins:starting_equipment",
  "grant_id": "mage_spellbook",
  "item": "irons_spellbooks:iron_spell_book",
  "components": "{\"irons_spellbooks:spell_container\":{data:[{id:\"irons_spellbooks:firebolt\",index:1,level:1}],maxSpells:5,mustEquip:1b,spellWheel:1b}}",
  "name": "Arcane Tome",
  "description": "Begins life with a pre-inscribed spell book."
}
```

**Example: a multi-item starting kit**
```json
{
  "type": "neoorigins:starting_equipment",
  "grant_id": "ranger_kit",
  "stacks": [
    {
      "item": "minecraft:bow",
      "enchantments": [{"id": "minecraft:power", "level": 2}]
    },
    {"item": "minecraft:arrow", "count": 32},
    {"item": "minecraft:leather_boots"}
  ],
  "name": "Ranger's Kit",
  "description": "Begins life with a bow, arrows and boots."
}
```

### Singular vs plural

`stacks` does not add to the root `item` — it **replaces** it. When `stacks` is non-empty the root `item`, `count`, `enchantments`, `legacy_tag` and `components` are all ignored, so a power declaring both silently drops the root item. Put every item in `stacks`, or use the singular shape alone.

One `grant_id` covers the whole bundle. Two consequences worth planning around:

- The bundle is marked granted if **at least one** stack went in. If one item id is a registry miss — a typo, or a mod that is not installed — the rest still arrive and the bundle is sealed, so correcting the id later does not deliver the missing item to players who already have the bundle. Give a genuinely optional item its own power with its own `grant_id`.
- If **every** id fails, the bundle is deliberately left un-deduped, so fixing the typo and running `/reload` retries it.

A stack with a blank `item` is skipped with a warning rather than failing the power, so an unfilled row left behind in the in-game editor is tolerated.

`/reload` does not grant anything by itself: players who are already online receive a new or retried bundle on their next login, respawn or origin pick.

---

## `neoorigins:extra_inventory`

Gives the player an extra inventory opened via the skill keybind. Uses vanilla's chest UI, dynamically sized to fit the configured slot count (1-6 rows). Contents are persisted across sessions.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `size` | int | no | `9` | Number of slots (rounded up to nearest row of 9, max 54 = 6 rows) |
| `drop_on_death` | bool | no | `false` | Whether to drop contents on death |
| `title` | string | no | `""` | Literal title drawn on the container screen. Blank/absent uses the translatable `container.neoorigins.extra_inventory`. Plain text, not a JSON text component. |
| `hunger_cost` | int | no | `0` | Food points charged every `upkeep_interval` while the inventory is open. Saturation is spent first. |
| `resource_cost` | string | no | `""` | Id of a [`neoorigins:resource`](state.md#neooriginsresource) power drained every `upkeep_interval` while the inventory is open. While resource bars are disabled server-wide, the amount is charged as hunger instead. |
| `resource_cost_amount` | int | no | `0` | Amount of `resource_cost` drained per charge. |
| `upkeep_interval` | int | no | `20` | Ticks between charges, clamped to at least 1. |

**Paying to keep it open.** With a cost set, the inventory is charged on a fixed beat while its menu is open, the same way a toggle pays its [upkeep](../concepts/costs-and-upkeep.md#toggle-upkeep): opening it is free, and each charge is all or nothing. The key does nothing while the player can't pay one charge, and the menu closes at the first charge they can't pay. Nothing in the inventory is lost when it closes: the contents stay stored, and an item held on the cursor goes back to the player's inventory. Charging stops as soon as the menu closes, and on death or logout.

**Example: 27-slot extra inventory**
```json
{
  "type": "neoorigins:extra_inventory",
  "size": 27,
  "name": "Shulker Inventory",
  "description": "Press your skill key to open an extra inventory."
}
```

**Example: a pocket that costs hunger to keep open**
```json
{
  "type": "neoorigins:extra_inventory",
  "size": 9,
  "hunger_cost": 1,
  "upkeep_interval": 40
}
```

Origins compat: translates `origins:inventory` / `origins:shulker_inventory`.

---

## `neoorigins:restrict_armor`

Prevents certain items from being equipped in certain slots. When a matching item is placed in a restricted slot, the power ejects it back to the player's inventory (or drops it to the ground if inventory is full).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `restrictions` | list of SlotRestriction | no | `[]` | Per-slot filters. An empty list disables the power. |

**`SlotRestriction` object:**

| Field | Type | Required | Description |
|---|---|---|---|
| `slot` | string | yes | One of `mainhand`, `offhand`, `head`, `chest`, `legs`, `feet`, `body` |
| `item` | Identifier | no | Exact item ID to restrict |
| `tag` | Identifier | no | Item tag to restrict |

If neither `item` nor `tag` is set on a restriction entry, any non-empty stack in that slot is restricted.

**Example: no iron or diamond chestplates**
```json
{
  "type": "neoorigins:restrict_armor",
  "restrictions": [
    { "slot": "chest", "item": "minecraft:iron_chestplate" },
    { "slot": "chest", "item": "minecraft:diamond_chestplate" }
  ],
  "name": "Light Armor Only",
  "description": "Cannot wear iron or diamond chestplates."
}
```

### Armor classes

NeoOrigins ships two item tags that categorize vanilla armor into **light** and **heavy** classes. Use these with `restrict_armor` to broadly gate what an origin can wear without listing every item individually.

| Tag | Contents |
|---|---|
| `neoorigins:light_armor` | Leather, Chainmail (all slots) |
| `neoorigins:heavy_armor` | Iron, Gold, Diamond, Netherite (all slots) |

**Example: restrict an origin to light armor only**
```json
{
  "type": "neoorigins:restrict_armor",
  "restrictions": [
    { "slot": "head",  "tag": "neoorigins:heavy_armor" },
    { "slot": "chest", "tag": "neoorigins:heavy_armor" },
    { "slot": "legs",  "tag": "neoorigins:heavy_armor" },
    { "slot": "feet",  "tag": "neoorigins:heavy_armor" }
  ],
  "name": "Light Armor Only",
  "description": "Cannot wear heavy armor."
}
```

**Adding modded armor to a class:**

Modpack authors can extend the classes in two ways:

1. **Datapack**: add entries to the `neoorigins:heavy_armor` or `neoorigins:light_armor` item tags via a higher-priority datapack.

2. **Config**: add item IDs or `#tags` to the `[armor_classes]` section in `config/neoorigins/gameplay.toml`.

```toml
[armor_classes]
    # Items/tags added here supplement the neoorigins:heavy_armor tag
    heavy_armor = ["modid:steel_chestplate", "#modid:titanium_armor"]
    # Items/tags added here supplement the neoorigins:light_armor tag
    light_armor = ["modid:cloth_robe"]
```

Config entries are checked alongside the tags; items in either source count as that armor class.

---

## `neoorigins:restrict_items`

One modular, multipurpose gate over **equipping** and **using** items. A single flexible power that covers both blacklist and allow-list semantics for any item matched by a reusable item condition, across any equipment slot and/or hand. Unlike `restrict_armor` (equip-only), this can also cancel item *use*, and, done properly, that means stopping a totem of undying from saving the holder (vanilla consumes the totem before the death event, so this is enforced via a mixin into `checkTotemDeathProtection`).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `item_condition` | item condition | no | matches all | Apoli-style item condition (`id` / `tag` / `nbt` / `enchantment` / `empty`, with `and`/`or`/`not` composition) selecting which stacks the gate matches. Omit to match every stack. |
| `slots` | list of strings | no | `[]` (all) | EquipmentSlot names the *equip* gate applies to: `mainhand`, `offhand`, `head`, `chest`, `legs`, `feet`, `body`. Empty = all slots. |
| `hands` | list of strings | no | `[]` (both) | InteractionHand names the *use* gate applies to: `mainhand`, `offhand`. Empty = both hands. |
| `prevent_equip` | boolean | no | `false` | When true, equipping a matched item into a gated slot is rejected (ejected back to the inventory). |
| `prevent_use` | boolean | no | `false` | When true, *using* a matched item (right-click / raise: shields, totems, bows, food, ...) in a gated hand is cancelled, including stopping a totem of undying from popping. |
| `deny` | boolean | no | `true` | `true` = blacklist: matched items are forbidden. `false` = allow-list: ONLY matched items are permitted for the gated action; everything else is forbidden. |
| `condition` | EntityCondition | no | always true | Optional whole-power gate: the power is inert unless it passes. |

The single decision is `match → forbidden`: in blacklist mode a matched item is forbidden; in allow-list mode an *un*matched item is forbidden. The same decision drives equip, use, and the totem mixin, so they can never disagree.

**Example: a "fragile" origin can't use shields and can't be saved by totems**
```json
{
  "type": "neoorigins:restrict_items",
  "item_condition": {
    "type": "neoorigins:or",
    "conditions": [
      { "item": "minecraft:shield" },
      { "item": "minecraft:totem_of_undying" }
    ]
  },
  "prevent_use": true,
  "deny": true,
  "name": "Fragile",
  "description": "Cannot raise a shield, and a totem of undying will not save you."
}
```

**Example (allow-list): can only ever hold a wooden sword in the main hand**
```json
{
  "type": "neoorigins:restrict_items",
  "item_condition": { "item": "minecraft:wooden_sword" },
  "slots": ["mainhand"],
  "prevent_equip": true,
  "deny": false,
  "name": "Pacifist's Vow",
  "description": "Refuses to wield anything but a wooden sword."
}
```

---

## `neoorigins:prevent_item_damage`

Stops items from losing durability while the holder has the power. Unlike handing out an unbreakable item at spawn, this is a trait of the origin: any matching tool, weapon, or armor the player uses simply never takes durability damage. Implemented via a `ItemStack.hurtAndBreak` mixin, so it covers every durability path (mining, attacking, blocking, casting).

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `items` | array | no | `[]` | Item ids or `#tags` spared from durability loss. Empty list (default) protects **every** damageable item the holder uses. |

**Example: unbreaking everything**
```json
{
  "type": "neoorigins:prevent_item_damage",
  "name": "Tireless Hands",
  "description": "Your gear never wears down."
}
```

**Example: only tools and bows**
```json
{
  "type": "neoorigins:prevent_item_damage",
  "items": ["#minecraft:pickaxes", "#minecraft:axes", "minecraft:bow"],
  "name": "Curator",
  "description": "Your tools and bow never break."
}
```

---

## `neoorigins:item_magnetism`

Items on the ground within a radius are pulled toward the player automatically. A toggle, on by default.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `radius` | float | no | `4.0` | Pull radius in blocks |

Also takes the shared [toggle upkeep](../concepts/costs-and-upkeep.md#toggle-upkeep) fields (`hunger_cost`, `resource_cost`, `resource_cost_amount`, `upkeep_interval`, `upkeep_condition`).

**Example:**
```json
{
  "type": "neoorigins:item_magnetism",
  "radius": 4.0,
  "name": "Item Attraction",
  "description": "Items on the ground are drawn toward you."
}
```

---

## `neoorigins:less_item_use_slowdown`

Reduces movement slowdown while using items (bow, shield, etc.). Applies a transient `generic.movement_speed` modifier while `isUsingItem()` is true and the held item matches.

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `item_type` | string | no | `any` | `any`, `bow`, `shield`, or any substring matched against the held item's ID |
| `speed_multiplier` | float | no | `0.5` | `ADD_MULTIPLIED_BASE` value: `0.5` = +50% walk speed while using |

**Example: full-speed archery**
```json
{
  "type": "neoorigins:less_item_use_slowdown",
  "item_type": "bow",
  "speed_multiplier": 0.8,
  "name": "Strider Archer",
  "description": "Barely slowed while drawing a bow."
}
```
