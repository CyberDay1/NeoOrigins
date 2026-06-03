# Global Power Sets

Global power sets grant a bundle of powers to entities **without an origin
assignment**. This is NeoOrigins' port of Apoli's `apoli:global` ("Global Power
Set") feature, and the authored JSON uses the same shape Apoli uses.

Authored at `data/<ns>/global_powers/<id>.json`; loaded by
`GlobalPowerSetDataManager` after the player-origin / layer / mob-origin reload
pipeline (it only needs powers, which load earlier).

> 📖 See [`POWER_TYPES.md`](POWER_TYPES.md) for the power list. Powers that aren't
> mob-applicable are silently skipped when a global set targets a mob, but still
> apply to players.

## File shape

```json
{
  "entity_types": ["minecraft:player", "#minecraft:skeletons"],
  "powers": ["neoorigins_custom:night_vision", "neoorigins_custom:slow_fall"],
  "order": 0
}
```

`id` is **injected from the file path** — do not write it in the JSON. (Same
convention as player and mob origins.)

## Top-level fields

- **`entity_types`** — _optional._ A list that may **mix** literal entity ids
  (`"minecraft:creeper"`) and entity-type **tag refs** (`"#minecraft:skeletons"`)
  in the same array. Each entry is matched individually: a leading `#` makes it a
  tag, anything else is a literal id.
  - When **absent or empty**, the set applies to **all entities** — every player
    **and** every mob.
  - Players are matched by the literal id `minecraft:player` (or a tag the player
    type belongs to), or by an absent `entity_types`.
- **`powers`** — required. An array of power ids to grant. Unknown ids are logged
  and skipped.
- **`order`** — _optional, default `0`._ Apply ordering across sets — **lower
  applies first**. Powers referenced by multiple sets are granted once, in the
  order the first referencing set produces them (sets ordered by `order`, then by
  id; powers in declaration order within each set).

## Who gets the powers, and when

- **Players** — matching global powers are granted **on login** and re-reconciled
  **on datapack sync** (`/reload`). They are granted through the same dynamic-grant
  mechanism as the `grant_power` action, so they activate normally, persist across
  respawn, and survive datapack reloads. If a global set is removed (or stops
  matching) the player's grant is revoked on the next login / `/reload` — unless an
  origin still supplies that power.
- **Mobs** — matching, **mob-applicable** powers are applied at spawn
  (`FinalizeSpawnEvent`), the same hook mob origins use. Already-spawned mobs do
  **not** live-update; they pick up datapack changes on respawn / reload only.

## Examples

Grant night vision to **every** entity (no `entity_types`):

```json
{ "powers": ["neoorigins_custom:always_night_vision"] }
```

Give all skeletons a custom power, and apply it before lower-priority sets:

```json
{
  "entity_types": ["#minecraft:skeletons"],
  "powers": ["neoorigins_custom:brittle_bones"],
  "order": -10
}
```

Players-only buff:

```json
{
  "entity_types": ["minecraft:player"],
  "powers": ["neoorigins_custom:starter_kit"]
}
```
