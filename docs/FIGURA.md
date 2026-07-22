# NeoOrigins + Figura Integration

[Figura](https://modrinth.com/mod/figura) is a client-side custom-avatar mod: it
lets each player run a Lua-scripted avatar that renders on everyone's screen.
NeoOrigins ships a soft-dependency integration that exposes a read-only
`neoorigins` global to those Lua scripts, so an avatar author can react to the
wearer's live origin state: which origin they picked, which powers are active,
which capabilities are present, and which evolution tier they have reached.

This lets an avatar swap models, toggle parts, or play animations based on the
player's NeoOrigins state, all decided on the Figura side. A common use is picking
a different avatar model per origin, per evolution tier, or while a power is active.

## Soft-dependency nature

The integration is entirely optional and one-directional:

- **Figura is not required.** NeoOrigins builds and runs with Figura absent. All
  Figura-facing code lives under `compat/figura/` and is only ever classloaded by
  Figura's own API scanner. If Figura is missing, none of it loads and nothing
  breaks. Figura is a compile-only dependency of the mod.
- **Figura only ever READS NeoOrigins state, never the reverse.** NeoOrigins pushes
  each visible player's state down to every client (so any observer's copy of that
  player's avatar answers correctly), and the Lua `neoorigins` global is read-only.
- **The datapack fields are opaque.** The model keys and labels you declare on an
  origin (`figura_model` / `figura_models`) are strings NeoOrigins never interprets
  or validates. They are passed straight through to the Lua sandbox. Their meaning
  lives entirely in the avatar author's script.

If Figura is installed but the wearer's avatar has no NeoOrigins-aware script, the
model keys simply go unused.

## Datapack author side

You declare model keys on an origin JSON
(`data/<namespace>/origins/origins/<origin>.json`). Both fields are optional; an
origin with neither loads and behaves exactly as before.

### `figura_model` (string)

The base model key: a single opaque string, e.g. `"knight"`. Read from Lua as
`neoorigins:getFiguraModel()`. Use this when one origin maps to one model.

```json
{
  "name": "origins.mypack.knight.name",
  "description": "origins.mypack.knight.description",
  "powers": ["mypack:shield_wall"],
  "figura_model": "knight"
}
```

### `figura_models` (object)

Reactive maps for advanced setups. Every key and value is an opaque string.

| Sub-map | Key | Value | Meaning |
|---|---|---|---|
| `tiers` | evolution tier index, as a string integer (`"1"`, `"2"`, `"3"`) | model key | Tier-driven model. The highest index that is at most the player's current evolution tier overrides the base `figura_model`. Non-integer keys are ignored. |
| `powers` | power id (`mypack:shield_wall`) | model key | The key is reported "on" (by `getActiveFiguraModelKeys`) while that power is active on the player. |
| `capabilities` | capability tag (`natural_glide`) | model key | The key is reported "on" while that capability tag is present on the player. |
| `vocab` | model key | friendly label | Purely for discovery: lets a generic avatar UI list the managed keys with human names. Has no gameplay effect. |

```json
{
  "name": "origins.mypack.knight.name",
  "description": "origins.mypack.knight.description",
  "icon": "minecraft:iron_chestplate",
  "powers": ["mypack:shield_wall"],
  "figura_model": "knight",
  "figura_models": {
    "tiers":        { "1": "knight_ascended", "2": "knight_apex" },
    "powers":       { "mypack:shield_wall": "knight_guard" },
    "capabilities": { "natural_glide": "knight_winged" },
    "vocab": {
      "knight":          "Knight Base",
      "knight_ascended": "Ascended Knight",
      "knight_apex":     "Apex Knight",
      "knight_guard":    "Shield Wall",
      "knight_winged":   "Winged Knight"
    }
  }
}
```

### Resolution rules

- **Base** = `figura_model`.
- **Tier model** (`getFiguraModelTier`) = the base, overridden by the highest
  `tiers` entry whose integer index is at most the player's current evolution tier.
  At tier 0 (or with no matching entry) it stays the base.
- **Active reactive keys** (`getActiveFiguraModelKeys`) = every `powers` key whose
  power is currently active, plus every `capabilities` key whose tag is currently
  present. These describe transient state ("is the shield wall up right now?").
- **Vocab** = the author-declared `key` to `label` map, for discovery only.

## Avatar author side (Lua)

Inside a Figura avatar script, the read-only `neoorigins` global answers for the
player the avatar belongs to (resolved on every observer's client, so it is correct
for other players too, not only yourself). All methods are namespaced `neoorigins:`.

| Lua call | Returns | Description |
|---|---|---|
| `neoorigins:getOrigin()` | string or nil | The player's origin id on the primary `neoorigins:origin` layer (falls back to the first origin on any layer), or nil. |
| `neoorigins:getOrigins()` | table of strings | Every origin id the player has, across all layers. |
| `neoorigins:hasPower(id)` | boolean | True if the player has the given power granted, regardless of toggle state. |
| `neoorigins:isPowerActive(id)` | boolean | True if the player has the power AND it is currently active (toggled on, condition satisfied). |
| `neoorigins:getPowers()` | table of strings | Every power id granted to the player, regardless of toggle state. |
| `neoorigins:hasCapability(tag)` | boolean | True if any currently-active power grants the given capability tag. |
| `neoorigins:getCapabilities()` | table of strings | All active capability tags on the player. |
| `neoorigins:getFiguraModel()` | string or nil | The origin's static base `figura_model` key. Never tier-aware (kept stable for back-compat). |
| `neoorigins:getFiguraModels()` | table of strings | Every distinct `figura_model` key declared across all loaded origins (deduped). |
| `neoorigins:getFiguraModelTier()` | string or nil | The base key overridden by the highest `figura_models.tiers` entry at most the player's current evolution tier. |
| `neoorigins:getActiveFiguraModelKeys()` | table of strings | Every `figura_models` key currently "on": capabilities (sorted by tag) then powers (sorted by id). Order is stable, not a priority ranking. |
| `neoorigins:getFiguraModelVocab()` | table (key to label) | The origin's `figura_models.vocab` map as a Lua table, indexed `vocab[key] == label`. |

`getFiguraModelVocab()` returns a Lua table keyed by the model key: iterate it with
`pairs()`. All list-returning methods return array-style Lua tables (iterate with
`ipairs()`). Absent values come back as `nil`.

### Example avatar script

```lua
-- Swap the whole avatar model to match the wearer's origin, tier, and active state.
function events.tick()
    -- Prefer the most specific active state, then tier, then base.
    local active = neoorigins:getActiveFiguraModelKeys()
    local chosen = active[1]
        or neoorigins:getFiguraModelTier()
        or neoorigins:getFiguraModel()

    for key, _ in pairs(modelsByKey) do
        modelsByKey[key]:setVisible(key == chosen)
    end
end

-- Print the author-declared vocab once, for debugging.
if host:isHost() then
    for key, label in pairs(neoorigins:getFiguraModelVocab()) do
        print(key .. " => " .. label)
    end
end
```

`modelsByKey` above is your own table mapping each declared model key to a model
part in your `.bbmodel`.

## See also

- [PACK_FORMAT.md](PACK_FORMAT.md#figura-model-key): the origin JSON fields.
- [COMPATIBILITY.md](COMPATIBILITY.md): the full mod-compatibility overview.
