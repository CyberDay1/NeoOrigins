# Mob Origins

Mob origins are to mobs what player origins are to players: a JSON-defined bundle of
powers attached to a `LivingEntity`, with weighted spawn rules and per-origin drops.

Authored at `data/<ns>/origins/mob_origins/<id>.json`; loaded by `MobOriginDataManager`
after the player-origin / layer reload pipeline. NeoOrigins-native concept: no
Origins-mod legacy format.

> 📖 See [`POWER_TYPES.md`](POWER_TYPES.md) for the power list shared with player
> origins. The `neoorigins:mob_behavior` power (configurable aggression) is mob-only.

> ℹ️ Status: this doc is a Phase-5 outline. Full field tables + examples land
> alongside Phase 6 and the v2.1.0 release.

## File shape

```json
{
  "name": {"text": "Brutal Zombie"},
  "description": {"text": "A hostile zombie variant"},
  "icon": "minecraft:zombie_head",
  "target": {"entity_type": "minecraft:zombie"},
  "powers": ["neoorigins_custom:brutal_zombie_buffs"],
  "spawn_rules": { },
  "drops": { },
  "hidden": false
}
```

`id` is **injected from the file path**. Do not write it in the JSON. (Same
convention as player origins.)

## Top-level fields

- **`target`**: exactly one of `entity_type` (single id), `entity_tag` (tag id), or
  `entity_types` (array of ids). _[TODO: examples + validation rules]_
- **`powers`**: list of resolved power ids; powers that aren't mob-applicable are
  silently filtered with a `[mob-compat]` line in `logs/neoorigins-compat.log`.
- **`spawn_rules`**: optional. Weighted natural-spawn application (Phase 2).
  _[TODO: weight, time_of_day, spawn_reasons, mutex_group, replace, y_range,
  light_range, location filter (dim / biome / biome_tag / biomes / structure /
  structure_tag / allow_water_surface / allow_ocean_floor / min_y / max_y /
  can_see_sky)]_
- **`drops`**: optional. Additive or replace drop table layered onto vanilla loot
  via a global loot modifier (Phase 5).
  _[TODO: `mode` (additive|replace) / `strategy` (independent_chance|weighted_pool)
  / `pool_rolls` / `entries: [{item, count, chance, rolls, weight}]`]_
- **`hidden`**: when `true`, the origin is omitted from in-game creator browsers.
  The mechanical effect still applies.

## The `neoorigins:mob_behavior` power

Configurable, piglin-style aggression. Three modes: **NEUTRAL** (no targeting),
**HOSTILE** (always targets), **CONDITIONAL** (targets only while every
`hostile_when` condition holds for the candidate player).

Config fields: `aggression`, `hostile_when`, `retaliate`, `anger_linger_ticks`,
`aggro_range`, `target_filter`, `call_for_help`. _[TODO: full field table +
examples; condition DSL reuses the existing entity-condition surface verbatim.]_

## Spawn-egg minting (Phase 4d)

Both surfaces target the **saved** origin id. Save the draft first if it's new.

- `/neoorigins mob egg <origin> [entity_type] [count]`: mints a vanilla
  `<type>_spawn_egg` ItemStack with the origin marker baked into `ENTITY_DATA`.
- "Give Spawn Egg" button in the Mob Origin Creator's Identity tab: same path,
  prompts for an entity type when the origin's target is a tag or list.

Right-clicking the egg on the ground spawns the mob with the origin pre-attached.
Right-clicking it on a Spawner reconfigures the spawner's next-spawn data so its
natural firings also carry the origin.

## Live Mode (Phase 6, pending)

_[TODO: looked-at apply / spawn here / active template. Section drafted when
Phase 6 lands.]_

## Power compatibility

Not every power type works on mobs (HUD / keybind / food / starting-equipment /
edible-item / orb-related types are player-only). Powers a mob origin lists are
filtered through `PowerType.appliesToMobs(config)`; ineligible powers are dropped
with a log line and the rest are applied via `MobOriginService.applyMobOriginPowers`.

_[TODO: full appliesToMobs() table (which power types fire on mobs and which are
filtered out).]_
