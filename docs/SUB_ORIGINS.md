# Sub-Origins (Conditioned Layers)

Sub-origins let pack authors create origin trees where picking an origin in one layer unlocks sub-choices in a later layer. Think D&D race/subrace: pick **Elf** → then pick **High Elf / Wood Elf / Dark Elf** → then pick a **Cantrip**.

NeoOrigins implements this through **conditioned origin layers** — layers whose origin entries have a `condition` that checks the player's choice in a parent layer.

---

## Quick Start

### 1. Define the parent origins

```json
// data/mypack/origins/origins/elf.json
{
  "name": "Elf",
  "description": "A graceful, long-lived race.",
  "icon": "minecraft:bow",
  "impact": "medium",
  "powers": ["mypack:elf/darkvision", "mypack:elf/trance"]
}
```

```json
// data/mypack/origins/origins/dwarf.json
{
  "name": "Dwarf",
  "description": "A stout, hardy race.",
  "icon": "minecraft:iron_pickaxe",
  "impact": "medium",
  "powers": ["mypack:dwarf/stonecunning", "mypack:dwarf/sturdy"]
}
```

### 2. Put parents in the main layer

```json
// data/origins/origin_layers/origin.json
{
  "replace": false,
  "origins": ["mypack:elf", "mypack:dwarf"]
}
```

### 3. Create a conditioned sub-layer

```json
// data/origins/origin_layers/elven_lineage.json
{
  "order": 2,
  "name": "Elven Lineage",
  "origins": [
    {
      "condition": {
        "type": "origins:origin",
        "layer": "origins:origin",
        "origin": "mypack:elf"
      },
      "origins": [
        "mypack:elf/high",
        "mypack:elf/wood",
        "mypack:elf/dark"
      ]
    }
  ]
}
```

This sub-layer only appears in the picker when the player chose **Elf** in the `origins:origin` layer. If they chose Dwarf, this layer is automatically skipped.

### 4. Define sub-origin JSONs

```json
// data/mypack/origins/origins/elf/high.json
{
  "name": "High Elf",
  "description": "Keen mind, mastery of basic magic.",
  "icon": "minecraft:amethyst_shard",
  "impact": "medium",
  "powers": ["mypack:elf/high/cantrip", "mypack:elf/high/weapon_training"]
}
```

---

## JSON Format

### Conditioned Batch (recommended)

Multiple origins sharing one condition. This is the format used by most packs:

```json
{
  "condition": { ... },
  "origins": ["namespace:id1", "namespace:id2", ...]
}
```

### Individual Conditioned Entry

A single origin with its own condition:

```json
{
  "origin": "namespace:id",
  "condition": { ... }
}
```

### Plain Entry (no condition)

Always visible:

```json
"namespace:id"
```

All three formats can be mixed in the same layer's `"origins"` array.

---

## Condition Types

### `origins:origin` — Check parent layer choice

The primary condition type. Checks if a specific origin was chosen in a specific layer.

```json
{
  "type": "origins:origin",
  "layer": "origins:origin",
  "origin": "mypack:elf"
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `type` | string | required | `"origins:origin"`, `"apace:origin"`, or `"neoorigins:origin"` |
| `layer` | string | `"origins:origin"` | The layer ID to check |
| `origin` | string | required | The origin ID the player must have chosen |
| `inverted` | boolean | `false` | When `true`, passes when the origin was NOT chosen |

### `origins:and` — All conditions must pass

```json
{
  "type": "origins:and",
  "conditions": [
    { "type": "origins:origin", "layer": "origins:origin", "origin": "mypack:half_elf" },
    { "type": "origins:origin", "origin": "mypack:half_elf/skills/stealth", "inverted": true }
  ]
}
```

### `origins:or` — Any condition must pass

```json
{
  "type": "origins:or",
  "conditions": [
    { "type": "origins:origin", "origin": "mypack:elf" },
    { "type": "origins:origin", "origin": "mypack:half_elf" }
  ]
}
```

---

## Layer Ordering

Sub-layers **must** have a higher `order` value than their parent layer. The picker shows layers in ascending order, so a parent layer at `order: 1` is always picked before a sub-layer at `order: 2`.

```
order: 1  → "Race"           (Elf, Dwarf, Human)
order: 2  → "Elven Lineage"  (High, Wood, Dark — only if Race=Elf)
order: 2  → "Dwarven Clan"   (Hill, Mountain — only if Race=Dwarf)
order: 3  → "Cantrip"        (only if Lineage=High Elf)
```

Multiple sub-layers can share the same `order` value — they'll be shown in registration order, and layers with no visible origins are automatically skipped.

---

## Cascade Behavior

When a player changes their parent layer choice (via Orb of Origin or `/origin gui`), sub-layer choices that are no longer valid are automatically cleared:

1. Player picks **Elf** → **High Elf** → **Firebolt**
2. Player uses Orb of Origin, changes race to **Dwarf**
3. Server detects "High Elf" and "Firebolt" are no longer valid (their conditions fail)
4. Both sub-layer choices are revoked — powers removed, layer slots cleared
5. Player picks **Mountain Dwarf** in the newly-visible Dwarven Clan sub-layer

---

## Advanced Patterns

### Skill picking (choose N from M, no repeats)

Half-Elf in D&D picks 2 skills from a list. Use two layers with `inverted` conditions to prevent picking the same skill twice:

```json
// data/origins/origin_layers/skill_1.json
{
  "order": 3,
  "name": "Skill 1",
  "origins": [
    {
      "condition": {
        "type": "origins:origin",
        "layer": "origins:origin",
        "origin": "mypack:half_elf"
      },
      "origins": ["mypack:skill/stealth", "mypack:skill/athletics", "mypack:skill/arcana"]
    }
  ]
}

// data/origins/origin_layers/skill_2.json
{
  "order": 4,
  "name": "Skill 2",
  "origins": [
    {
      "condition": {
        "type": "origins:and",
        "conditions": [
          { "type": "origins:origin", "layer": "origins:origin", "origin": "mypack:half_elf" },
          { "type": "origins:origin", "layer": "origins:skill_1", "origin": "mypack:skill/stealth", "inverted": true }
        ]
      },
      "origins": ["mypack:skill/stealth"]
    },
    {
      "condition": {
        "type": "origins:and",
        "conditions": [
          { "type": "origins:origin", "layer": "origins:origin", "origin": "mypack:half_elf" },
          { "type": "origins:origin", "layer": "origins:skill_1", "origin": "mypack:skill/athletics", "inverted": true }
        ]
      },
      "origins": ["mypack:skill/athletics"]
    }
  ]
}
```

### Multi-parent sub-layer

A sub-layer that appears for multiple parent choices:

```json
{
  "condition": {
    "type": "origins:or",
    "conditions": [
      { "type": "origins:origin", "origin": "mypack:elf" },
      { "type": "origins:origin", "origin": "mypack:half_elf" }
    ]
  },
  "origins": ["mypack:darkvision_type/normal", "mypack:darkvision_type/superior"]
}
```

---

## Compatibility

This system is fully compatible with Origins mod's conditioned layer format. Packs written for Origins (like `digs_dnd_origins`) work without modification — NeoOrigins parses the same JSON structure and evaluates the same condition types.

The `apace:` and `neoorigins:` type prefixes are accepted as aliases alongside `origins:`.
