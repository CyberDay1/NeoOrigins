---
title: Mob Origins
parent: "Origins & Content"
nav_order: 4
---

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

`id` is **injected from the file path**; do not write it in the JSON. (Same
convention as player origins.)

## Top-level fields

- **`target`**: exactly one of `entity_type` (single id), `entity_tag` (tag id), or
  `entity_types` (array of ids). _[TODO: examples + validation rules]_
- **`name`** is required; `description` and `icon` are optional.
- **`powers`**: list of resolved power ids; unknown ids and powers that aren't
  mob-applicable are skipped silently (no log line).
- **`spawn_rules`**: optional. When absent the origin is never rolled at natural
  spawn (weight `0`); it can still be attached with a spawn egg or
  `/neoorigins mob apply`. Fields:
  - `weight` (default `0`): chance from `0` to `1` that the origin attaches when
    every other rule passes; `1` or more always attaches, `0` or less opts out.
  - `spawn_reasons`: lowercase vanilla spawn-reason names (`EntitySpawnReason`);
    empty or absent means any reason.
  - `y_range`, `light_range`: `{"min": .., "max": ..}` or a single int. Light is the
    block-light level at the spawn position.
  - `time_of_day`: `any` (default), `day` or `night`.
  - `location`: `dimension`, `biome`, `biome_tag`, `biomes`, `structure`,
    `structure_tag`. The other location fields (`min_y`, `max_y`, `can_see_sky`,
    `allow_water_surface`, `allow_ocean_floor`) are parsed but ignored here; use
    `y_range` for height.
  - `mutex_group`: recorded on the mob when the origin attaches, and a candidate
    is skipped if its group is already recorded. Because the roll stops at the
    first origin that attaches, this has no visible effect today.
  - `replace` (default `false`): parsed, but the spawn roll does not read it.

  Candidates whose `target` matches are tried in id order and the first one that
  passes attaches; a mob gets at most one origin.
- **`drops`**: optional. Drop table layered onto vanilla loot by the
  `neoorigins:mob_origin_drops` global loot modifier. `mode`: `additive`
  (default) or `replace` (vanilla drops cleared). `strategy`:
  `independent_chance` (default: each entry rolls `rolls` times, default `1`, at
  `chance`, default `1.0`) or `weighted_pool` (`pool_rolls` picks, default `0`,
  weighted by each entry's `weight`, default `1`). `entries`:
  `[{item, count, chance, rolls, weight}]`, where `item` is required and `count`
  (default `1`) takes the same int-or-range form as `y_range`. The modifier only
  runs when its carrier files are present in a loaded datapack; the Mob Origin
  Creator writes them into the world's `neoorigins_custom` pack on save, and on
  server start they are written there if any loaded mob origin has drops (active
  after the next reload).
- **`hidden`** (default `false`): parsed and stored, but nothing reads it yet.

## The `neoorigins:mob_behavior` power

Configurable, piglin-style aggression. Three modes: **NEUTRAL** (no targeting),
**HOSTILE** (always targets), **CONDITIONAL** (targets only while every
`hostile_when` condition holds for the candidate player).

Config fields: `aggression`, `hostile_when`, `retaliate`, `anger_linger_ticks`,
`aggro_range`, `target_type`, `call_for_help`. _[TODO: full field table +
examples; condition DSL reuses the existing entity-condition surface verbatim.]_

## Spawn-egg minting (Phase 4d)

Both surfaces target the **saved** origin id. Save the draft first if it's new.

Other `/neoorigins mob` subcommands (permission level 2): `apply <targets> <origin>`
attaches an origin to non-player living entities (without checking `target`),
`clear <targets>` removes it, `get <targets>` reports it, and `editor` opens the
Mob Origin Creator.

- `/neoorigins mob egg <origin> [entity_type] [count]`: mints a vanilla
  `<type>_spawn_egg` ItemStack with the origin marker baked into `ENTITY_DATA`.
- "Give Spawn Egg" button in the Mob Origin Creator's Identity tab: same path,
  prompts for an entity type when the origin's target is a tag or list.

Right-clicking the egg on the ground spawns the mob with the origin pre-attached.
Right-clicking it on a Spawner reconfigures the spawner's next-spawn data so its
natural firings also carry the origin.

## Live Mode (Phase 6: pending)

_[TODO: looked-at apply / spawn here / active template. Section drafted when
Phase 6 lands.]_

## Power compatibility

Not every power type works on mobs (HUD / keybind / food / starting-equipment /
edible-item / orb-related types are player-only). Powers a mob origin lists are
filtered through `PowerType.appliesToMobs(config)`; ineligible powers are skipped
without a log line and the rest are applied via `MobOriginService.applyMobOriginPowers`.

_[TODO: full appliesToMobs() table, which power types fire on mobs and which are
filtered out.]_
