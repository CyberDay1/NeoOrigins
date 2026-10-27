---
title: Power Overrides Config
parent: Guides
nav_order: 13
---

# Power Overrides Config

`config/neoorigins/power_overrides.toml` retunes the numbers of the built-in powers without a datapack. It is a COMMON config, so on a dedicated server the server's copy is the one that counts.

## How an override is applied

- Each power has a `[power_overrides.<power>]` table, named by the power's path (the `neoorigins:` namespace is implied). The file is generated on first launch with every key at its default.
- **Only a changed value is applied.** A key left at its default writes nothing, so the power keeps whatever its JSON says.
- When a power loads, each changed value is written into the power's JSON before the JSON is parsed. Most keys write a top-level field of the same name; the ones in the "Writes to" column write a field nested inside the power instead (for example inside its `entity_action`).
- An override follows the power id, not the file. If a datapack replaces a built-in power with its own JSON, the override still lands on the replacement; a nested key whose parent object is missing is skipped and logged.
- Changes take effect on `/reload` or a restart. Ticks are game ticks: 20 ticks = 1 second.
- `enabled = false`, where offered, keeps the power on the origin but switches it off. See [Power basics](concepts/power-basics.md).

Example:

```toml
[power_overrides.class_warrior_damage]
amount = 2.0
```

## `config_version`

The mod writes `config_version` itself to track one-time config fixes: do not edit it by hand.

2.2.32 corrected the defaults of fifteen keys to the values their powers already used. Before 2.2.32 those keys reached no field, so their old defaults never applied. A file written by an older version still holds those old defaults, and from 2.2.32 on they would count as changed values and be applied. So on the first launch of 2.2.32, any of those keys still on its old default is moved to the new one, the change is written to the log, and the file is stamped. A value you set yourself is left alone, and once the file is stamped it is never touched again.

## Every key

The tables below list every key the file offers, grouped as the file groups them. They are generated from `PowerOverridesConfig.java`; the file itself carries the same list.

### Abyssal

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `abyssal_thorns_aura` | `return_ratio` | `0.3` | 0 to 10 | `entity_action.amount_ratio` |
| `abyssal_aquatic_speed` | `amount` | `0.8` | -1 to 10 |  |
| `abyssal_land_speed_penalty` | `amount` | `-0.1` | -1 to 1 |  |
| `abyssal_summon_guardian` | `max_count` | `2` | 1 to 100 |  |
|  | `cooldown_ticks` | `400` | 0 to 72000 |  |
|  | `hunger_cost` | `4` | 0 to 100 |  |
|  | `despawn_ticks` | `18000` | 0 to 1000000 |  |
|  | `death_damage` | `1.0` | 0 to 100 |  |

### Automaton

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `automaton_iron_frame` | `amplifier` | `0` | 0 to 4 |  |
| `automaton_heavy_chassis` | `amount` | `-0.15` | -1 to 1 |  |
| `automaton_knockback_resist` | `amount` | `0.4` | -1 to 1 |  |
| `automaton_no_hunger` | `multiplier` | `0.0` | 0 to 10 | `modifier.value` |
| `automaton_rigid_joints` | `multiplier` | `0.25` | 0 to 10 | `modifier.value` |

### Avian

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `avian_no_hunger_sprint` | `multiplier` | `0.25` | 0 to 10 | `modifier.value` |

### Blazeling

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `blazeling_water_damage` | `multiplier` | `1.0` | 0 to 100 |  |

### Breeze

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `breeze_wind_charge` | `speed` | `2.0` | 0 to 10 | `entity_action.speed` |
|  | `cooldown_ticks` | `100` | 0 to 72000 |  |
| `breeze_wind_dash` | `power` | `2.5` | 0 to 10 |  |
|  | `cooldown_ticks` | `80` | 0 to 72000 |  |
| `breeze_light_frame` | `scale` | `0.9` | 0.1 to 10 |  |
|  | `modify_reach` | `false` | true / false |  |
|  | `reach_bonus` | `0.0` | -10 to 10 |  |
| `breeze_reduced_health` | `amount` | `-8.0` | -100 to 100 |  |
| `breeze_speed_boost` | `amount` | `0.15` | -1 to 10 |  |

### Caveborn

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `caveborn_daylight_damage` | `damage_per_second` | `1.0` | 0 to 100 | `entity_action.actions.0.amount` |
| `caveborn_mining_speed` | `multiplier` | `2.0` | 0 to 100 |  |
| `caveborn_small_frame` | `scale` | `0.85` | 0.1 to 10 |  |
|  | `modify_reach` | `false` | true / false |  |
|  | `reach_bonus` | `0.0` | -10 to 10 |  |
| `caveborn_copper_tint` | `enabled` | `true` | true / false |  |
| `caveborn_iron_tint` | `enabled` | `true` | true / false |  |
| `caveborn_gold_tint` | `enabled` | `true` | true / false |  |
| `caveborn_diamond_tint` | `enabled` | `true` | true / false |  |
| `caveborn_emerald_tint` | `enabled` | `true` | true / false |  |
| `caveborn_netherite_tint` | `enabled` | `true` | true / false |  |

> **1.21.1 vs 26.x:** the ore tints are drawn on 1.21.1 only. On 26.x the `model_color` powers behind them draw nothing, so each meal's effect and bonus powers still apply but the model keeps its usual colors.

### Cinderborn

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `cinderborn_fireball` | `speed` | `1.2` | 0 to 10 | `entity_action.actions.0.speed`, `entity_action.actions.1.speed`, `entity_action.actions.2.speed`, `entity_action.actions.3.speed` |
|  | `cooldown_ticks` | `100` | 0 to 72000 |  |
| `cinderborn_natural_armor` | `amplifier` | `0` | 0 to 4 |  |
| `cinderborn_water_weakness` | `multiplier` | `1.0` | 0 to 100 |  |

### Draconic

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `draconic_active_fireball` | `speed` | `1.5` | 0 to 10 | `entity_action.actions.0.speed`, `entity_action.actions.1.speed`, `entity_action.actions.2.speed`, `entity_action.actions.3.speed` |
|  | `cooldown_ticks` | `80` | 0 to 72000 |  |
| `draconic_size` | `scale` | `1.2` | 0.1 to 10 |  |
|  | `modify_reach` | `false` | true / false |  |
|  | `reach_bonus` | `0.0` | -10 to 10 |  |
| `draconic_attack_bonus` | `amount` | `2.0` | -100 to 100 |  |
| `draconic_hunger_drain` | `multiplier` | `1.5` | 0 to 10 | `modifier.value` |
| `draconic_water_weakness` | `multiplier` | `1.0` | 0 to 100 |  |

### Dwarf

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `dwarf_compact_frame` | `scale` | `0.8` | 0.1 to 10 |  |
|  | `modify_reach` | `false` | true / false |  |
|  | `reach_bonus` | `0.0` | -10 to 10 |  |
| `dwarf_stout_constitution` | `amplifier` | `0` | 0 to 4 |  |
| `dwarf_sturdy_legs` | `amount` | `-0.15` | -1 to 1 |  |
| `dwarf_short_reach` | `amount` | `-0.5` | -10 to 10 |  |
| `dwarf_stonecunning` | `multiplier` | `1.25` | 0 to 100 |  |
| `dwarf_mining_hunger` | `multiplier` | `0.75` | 0 to 10 | `modifier.value` |

### Elytrian

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `elytrian_elytra_boost` | `strength` | `1.5` | 0 to 10 |  |
|  | `cooldown_ticks` | `40` | 0 to 72000 |  |

### Enderian

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `enderian_teleport` | `range` | `50.0` | 1 to 256 |  |
|  | `cooldown_ticks` | `60` | 0 to 72000 |  |
| `enderian_water_damage` | `damage_per_second` | `2.0` | 0 to 100 |  |
|  | `include_rain` | `true` | true / false |  |

### Enderite

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `enderite_teleport` | `range` | `32.0` | 1 to 256 |  |
|  | `cooldown_ticks` | `60` | 0 to 72000 |  |
| `enderite_phase` | `max_depth` | `16` | 1 to 256 |  |
|  | `cooldown_ticks` | `200` | 0 to 72000 |  |
|  | `hunger_cost` | `0` | 0 to 100 |  |
| `enderite_attack_bonus` | `amount` | `3.0` | -100 to 100 |  |
| `enderite_water_damage` | `multiplier` | `1.0` | 0 to 100 |  |
| `enderite_daylight_weakness` | `multiplier` | `1.5` | 0 to 100 |  |

### Feline

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `feline_active_launch` | `power` | `2.2` | 0 to 10 | `entity_action.strength` |
|  | `cooldown_ticks` | `80` | 0 to 72000 |  |
| `feline_speed_boost` | `amount` | `0.1` | -1 to 10 |  |
| `feline_hunger_drain` | `multiplier` | `1.3` | 0 to 10 | `modifier.value` |
| `feline_water_weakness` | `multiplier` | `1.0` | 0 to 100 |  |

### Frostborn

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `frostborn_freeze_aura` | `amplifier` | `3` | 0 to 255 | `entity_action.on_hit_action.entity_action.entity_action.amplifier` |
|  | `duration_ticks` | `100` | 1 to 72000 | `entity_action.on_hit_action.entity_action.entity_action.duration`, `entity_action.on_hit_action.duration_ticks` |
|  | `radius` | `6.0` | 0 to 64 | `entity_action.on_hit_action.radius`, `entity_action.on_hit_action.entity_action.radius` |
|  | `cooldown_ticks` | `200` | 0 to 72000 |  |
| `frostborn_ice_walk` | `max_distance` | `5.0` | 1 to 64 |  |
|  | `cooldown_ticks` | `20` | 0 to 72000 |  |
| `frostborn_natural_armor` | `amplifier` | `0` | 0 to 4 |  |
| `frostborn_fire_weakness` | `multiplier` | `2.0` | 0 to 100 |  |
| `frostborn_nether_damage` | `damage_per_second` | `2.0` | 0 to 100 | `entity_action.amount` |

### Golem

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `golem_natural_armor` | `amplifier` | `0` | 0 to 4 |  |
| `golem_knockback_resist` | `amount` | `0.8` | -1 to 1 |  |
| `golem_size` | `scale` | `1.3` | 0.1 to 10 |  |
|  | `modify_reach` | `false` | true / false |  |
|  | `reach_bonus` | `0.0` | -10 to 10 |  |
| `golem_slow_movement` | `amount` | `-0.25` | -1 to 1 |  |
| `golem_fire_weakness` | `multiplier` | `1.8` | 0 to 100 |  |

### Gorgon

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `gorgon_petrifying_gaze` | `amplifier` | `4` | 0 to 255 | `entity_action.on_hit_action.entity_action.entity_action.amplifier` |
|  | `duration_ticks` | `100` | 1 to 72000 | `entity_action.on_hit_action.entity_action.entity_action.duration`, `entity_action.on_hit_action.duration_ticks` |
|  | `radius` | `6.0` | 0 to 64 | `entity_action.on_hit_action.radius`, `entity_action.on_hit_action.entity_action.radius` |
|  | `cooldown_ticks` | `300` | 0 to 72000 |  |
| `gorgon_stone_fists` | `amount` | `4.0` | -100 to 100 |  |
| `gorgon_granite_hide` | `amplifier` | `0` | 0 to 4 |  |
| `gorgon_knockback_resist` | `amount` | `0.5` | -1 to 1 |  |
| `gorgon_size` | `scale` | `1.15` | 0.1 to 10 |  |
|  | `modify_reach` | `true` | true / false |  |
|  | `reach_bonus` | `0.0` | -10 to 10 |  |
| `gorgon_heavy_frame` | `amount` | `-0.2` | -1 to 1 |  |
| `gorgon_hunger_drain` | `multiplier` | `1.5` | 0 to 10 | `modifier.value` |

### Hiveling

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `hiveling_sting` | `speed` | `1.8` | 0 to 10 | `entity_action.speed` |
|  | `cooldown_ticks` | `120` | 0 to 72000 |  |
| `hiveling_crop_growth` | `radius` | `6` | 1 to 64 |  |
|  | `tick_interval` | `30` | 1 to 72000 |  |
|  | `growths_per_interval` | `3` | 1 to 100 |  |
| `hiveling_size` | `scale` | `0.6` | 0.1 to 10 |  |
|  | `modify_reach` | `false` | true / false |  |
|  | `reach_bonus` | `0.0` | -10 to 10 |  |
| `hiveling_reduced_health` | `amount` | `-6.0` | -100 to 100 |  |
| `hiveling_hunger_drain` | `multiplier` | `1.5` | 0 to 10 | `modifier.value` |

### Inchling

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `inchling_size` | `scale` | `0.25` | 0.1 to 10 |  |
|  | `modify_reach` | `false` | true / false |  |
|  | `reach_bonus` | `1.0` | -10 to 10 |  |
| `inchling_speed_boost` | `amount` | `0.15` | -1 to 10 |  |
| `inchling_reduced_health` | `amount` | `-10.0` | -100 to 100 |  |
| `inchling_hunger_efficiency` | `multiplier` | `0.5` | 0 to 10 | `modifier.value` |

### Kraken

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `kraken_tentacle_lash` | `amplifier` | `2` | 0 to 255 | `entity_action.on_hit_action.entity_action.amplifier` |
|  | `duration_ticks` | `80` | 1 to 72000 | `entity_action.on_hit_action.entity_action.duration` |
|  | `radius` | `5.0` | 0 to 64 | `entity_action.on_hit_action.radius` |
|  | `cooldown_ticks` | `160` | 0 to 72000 |  |
| `kraken_ink_shot` | `speed` | `1.4` | 0 to 10 | `entity_action.speed` |
|  | `cooldown_ticks` | `120` | 0 to 72000 |  |
| `kraken_massive` | `scale` | `1.3` | 0.1 to 10 |  |
|  | `modify_reach` | `true` | true / false |  |
|  | `reach_bonus` | `0.0` | -10 to 10 |  |
| `kraken_pressure_armor` | `amplifier` | `0` | 0 to 4 |  |
| `kraken_deep_current` | `amount` | `0.8` | -1 to 10 |  |
| `kraken_summon_guardian` | `max_count` | `2` | 1 to 100 |  |
|  | `cooldown_ticks` | `400` | 0 to 72000 |  |
|  | `hunger_cost` | `4` | 0 to 100 |  |
|  | `despawn_ticks` | `18000` | 0 to 1000000 |  |
|  | `death_damage` | `1.0` | 0 to 100 |  |
| `kraken_beached` | `amount` | `-0.3` | -1 to 1 |  |
| `kraken_daylight_damage` | `damage_per_second` | `1.0` | 0 to 100 | `entity_action.amount` |

### Merling

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `merling_aquatic_speed` | `amount` | `0.6` | -1 to 10 |  |
| `merling_land_slowdown` | `amount` | `-0.1` | -1 to 1 |  |

### Necromancer

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `necromancer_summon_skeleton` | `max_count` | `3` | 1 to 100 |  |
|  | `cooldown_ticks` | `400` | 0 to 72000 |  |
|  | `hunger_cost` | `4` | 0 to 100 |  |
|  | `despawn_ticks` | `18000` | 0 to 1000000 |  |
|  | `death_damage` | `1.0` | 0 to 100 |  |
| `necromancer_summon_wither` | `max_count` | `2` | 1 to 100 |  |
|  | `cooldown_ticks` | `600` | 0 to 72000 |  |
|  | `hunger_cost` | `4` | 0 to 100 |  |
|  | `despawn_ticks` | `18000` | 0 to 1000000 |  |
|  | `death_damage` | `1.0` | 0 to 100 |  |
| `necromancer_reduced_health` | `amount` | `-6.0` | -100 to 100 |  |
| `necromancer_slow_regen` | `multiplier` | `0.4` | 0 to 10 |  |
| `necromancer_daylight_damage` | `damage_per_second` | `1.5` | 0 to 100 | `entity_action.amount` |
| `necromancer_water_breathing` | `enabled` | `true` | true / false |  |

### Phantom

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `phantom_form` | `invisibility` | `true` | true / false |  |
|  | `no_gravity` | `true` | true / false |  |
| `phantom_water_breathing` | `enabled` | `true` | true / false |  |

### Piglin

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `piglin_attack_bonus` | `amount` | `2.0` | -100 to 100 |  |
| `piglin_soul_fire_damage` | `damage_per_second` | `0.5` | 0 to 100 | `entity_action.amount` |

### Revenant

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `revenant_active_bolt` | `speed` | `1.6` | 0 to 10 | `entity_action.speed` |
|  | `cooldown_ticks` | `80` | 0 to 72000 |  |
| `revenant_active_phase` | `max_depth` | `8` | 1 to 256 |  |
|  | `cooldown_ticks` | `60` | 0 to 72000 |  |
|  | `hunger_cost` | `0` | 0 to 100 |  |
| `revenant_slow_regen` | `multiplier` | `0.4` | 0 to 10 |  |
| `revenant_daylight_damage` | `damage_per_second` | `2.0` | 0 to 100 | `entity_action.actions.0.amount` |
| `revenant_water_breathing` | `enabled` | `true` | true / false |  |

### Sculkborn

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `sculkborn_sonic_bolt` | `speed` | `1.8` | 0 to 10 | `entity_action.speed` |
|  | `cooldown_ticks` | `120` | 0 to 72000 |  |
| `sculkborn_darkness_aura` | `amplifier` | `0` | 0 to 255 | `entity_action.on_hit_action.entity_action.entity_action.amplifier` |
|  | `duration_ticks` | `200` | 1 to 72000 | `entity_action.on_hit_action.entity_action.entity_action.duration`, `entity_action.on_hit_action.duration_ticks` |
|  | `radius` | `8.0` | 0 to 64 | `entity_action.on_hit_action.radius`, `entity_action.on_hit_action.entity_action.radius` |
|  | `cooldown_ticks` | `300` | 0 to 72000 |  |
| `sculkborn_natural_armor` | `amplifier` | `0` | 0 to 4 |  |
| `sculkborn_knockback_resist` | `amount` | `0.5` | -1 to 1 |  |
| `sculkborn_reduced_health` | `amount` | `-4.0` | -100 to 100 |  |
| `sculkborn_slow_movement` | `amount` | `-0.15` | -1 to 1 |  |
| `sculkborn_daylight_damage` | `damage_per_second` | `2.0` | 0 to 100 | `entity_action.amount` |

### Shulk

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `shulk_natural_armor` | `amplifier` | `0` | 0 to 4 |  |
| `shulk_slow_movement` | `amount` | `-0.25` | -1 to 1 |  |

### Siren

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `siren_aquatic_speed` | `amount` | `0.8` | -1 to 10 |  |
| `siren_land_slowdown` | `amount` | `-0.15` | -1 to 1 |  |
| `siren_reduced_health` | `amount` | `-4.0` | -100 to 100 |  |

### Skeleton

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `skeleton_speed` | `amount` | `0.2` | -1 to 10 |  |
| `skeleton_jump` | `amount` | `0.3` | -1 to 10 |  |
| `skeleton_brittle_frame` | `amount` | `-6.0` | -100 to 100 |  |
| `skeleton_marksmanship` | `multiplier` | `1.5` | 0 to 100 |  |
| `skeleton_eat_bone_meal` | `nutrition` | `3` | 0 to 20 |  |
|  | `saturation` | `0.4` | 0 to 10 |  |
| `skeleton_evolved_marksmanship` | `multiplier` | `1.75` | 0 to 100 |  |
| `skeleton_evolved_hp` | `amount` | `2.0` | -100 to 100 |  |
| `skeleton_ascended_speed` | `amount` | `0.3` | -1 to 10 |  |
| `skeleton_apex_brittle_frame` | `amount` | `-2.0` | -100 to 100 |  |
| `skeleton_water_breathing` | `enabled` | `true` | true / false |  |

### Slime

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `slime_moisture` | `drain_per_tick` | `0.0004` | 0 to 0.01 |  |
|  | `dry_biome_drain_multiplier` | `3.0` | 0 to 100 |  |
|  | `fire_drain_multiplier` | `10.0` | 0 to 100 |  |
|  | `water_refill_per_tick` | `0.005` | 0 to 0.1 |  |
|  | `regen_threshold` | `0.75` | 0 to 1.0 |  |
|  | `armor_penalty_threshold` | `0.10` | 0 to 1.0 |  |
|  | `dot_damage` | `1.0` | 0 to 100 |  |
|  | `dot_interval` | `40` | 1 to 72000 |  |
| `slime_death_save` | `moisture_threshold` | `0.75` | 0 to 1.0 |  |
|  | `teleport_distance` | `50` | 1 to 256 |  |
|  | `teleport_y_range` | `10` | 0 to 256 |  |
|  | `split_max_hp` | `4.0` | 1 to 100 |  |
|  | `recovery_ticks` | `2400` | 0 to 72000 |  |
| `slime_level_hp` | `levels_per_hp` | `10` | 1 to 100 |  |
|  | `max_bonus_hp` | `20` | 0 to 100 |  |
| `slime_evolved_hp` | `amount` | `2.0` | -100 to 100 |  |
| `slime_apex_hp` | `amount` | `6.0` | -100 to 100 |  |

### Sporeling

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `sporeling_spore_cloud` | `amplifier` | `1` | 0 to 255 | `entity_action.on_hit_action.entity_action.entity_action.amplifier` |
|  | `duration_ticks` | `100` | 1 to 72000 | `entity_action.on_hit_action.entity_action.entity_action.duration`, `entity_action.on_hit_action.duration_ticks` |
|  | `radius` | `5.0` | 0 to 64 | `entity_action.on_hit_action.radius`, `entity_action.on_hit_action.entity_action.radius` |
|  | `cooldown_ticks` | `240` | 0 to 72000 |  |
| `sporeling_natural_armor` | `amplifier` | `0` | 0 to 4 |  |
| `sporeling_slow_movement` | `amount` | `-0.1` | -1 to 1 |  |
| `sporeling_daylight_damage` | `damage_per_second` | `1.0` | 0 to 100 | `entity_action.amount` |

### Stoneguard

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `stoneguard_thorns_aura` | `return_ratio` | `0.2` | 0 to 10 | `entity_action.amount_ratio` |
| `stoneguard_natural_armor` | `amount` | `6.0` | -100 to 100 |  |
| `stoneguard_knockback_resist` | `amount` | `0.5` | -1 to 1 |  |
| `stoneguard_active_glowstone` | `max_distance` | `5.0` | 1 to 64 |  |
|  | `cooldown_ticks` | `100` | 0 to 72000 |  |
| `stoneguard_stone_mining` | `multiplier` | `2.0` | 0 to 100 |  |
| `stoneguard_slow_movement` | `amount` | `-0.1` | -1 to 1 |  |
| `stoneguard_no_mob_spawns` | `radius` | `36` | 1 to 256 |  |

### Strider

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `strider_natural_armor` | `amplifier` | `0` | 0 to 4 |  |
| `strider_water_weakness` | `multiplier` | `1.0` | 0 to 100 |  |

### Sylvan

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `sylvan_active_root` | `amplifier` | `5` | 0 to 255 | `entity_action.on_hit_action.entity_action.entity_action.amplifier` |
|  | `duration_ticks` | `80` | 1 to 72000 | `entity_action.on_hit_action.entity_action.entity_action.duration`, `entity_action.on_hit_action.duration_ticks` |
|  | `radius` | `6.0` | 0 to 64 | `entity_action.on_hit_action.radius`, `entity_action.on_hit_action.entity_action.radius` |
|  | `cooldown_ticks` | `200` | 0 to 72000 |  |
| `sylvan_crop_growth` | `radius` | `4` | 1 to 64 |  |
|  | `tick_interval` | `40` | 1 to 72000 |  |
|  | `growths_per_interval` | `2` | 1 to 100 |  |
| `sylvan_regen_in_water` | `amount_per_second` | `1.0` | 0 to 100 | `entity_action.amount` |
| `sylvan_nether_damage` | `damage_per_second` | `1.0` | 0 to 100 | `entity_action.amount` |

### Tiny

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `tiny_size` | `scale` | `0.5` | 0.1 to 10 |  |
|  | `modify_reach` | `false` | true / false |  |
|  | `reach_bonus` | `0.5` | -10 to 10 |  |
| `tiny_speed_boost` | `amount` | `0.2` | -1 to 10 |  |
| `tiny_item_magnetism` | `radius` | `4.0` | 0 to 64 |  |
| `tiny_attack_penalty` | `amount` | `-2.0` | -100 to 100 |  |
| `tiny_hunger_drain` | `multiplier` | `1.8` | 0 to 10 | `modifier.value` |

### Umbral

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `umbral_shadow_orb` | `max_orbs` | `4` | 1 to 100 |  |
|  | `radius` | `28.0` | 1 to 128 |  |
|  | `cooldown_ticks` | `60` | 0 to 72000 |  |
|  | `tick_interval` | `20` | 1 to 72000 |  |
| `umbral_active_dash` | `cooldown_ticks` | `60` | 0 to 72000 |  |
| `umbral_daylight_damage` | `damage_per_second` | `1.0` | 0 to 100 | `entity_action.amount` |

### Wraith

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `wraith_phase` | `always_on` | `false` | true / false |  |
|  | `exhaustion_per_tick` | `0.15` | 0 to 1.0 |  |
| `wraith_evolved_phase` | `always_on` | `false` | true / false |  |
|  | `exhaustion_per_tick` | `0.1125` | 0 to 1.0 |  |
| `wraith_apex_phase` | `always_on` | `false` | true / false |  |
|  | `exhaustion_per_tick` | `0.075` | 0 to 1.0 |  |
| `wraith_daylight_damage` | `damage_per_second` | `2.0` | 0 to 100 | `entity_action.actions.0.amount` |
| `wraith_hunger_drain` | `value` | `1.75` | 0 to 100 | `modifier.value` |
| `wraith_apex_hunger_drain` | `value` | `1.25` | 0 to 100 | `modifier.value` |
| `wraith_water_breathing` | `enabled` | `true` | true / false |  |

### Vampire

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `vampire_attack_bonus` | `amount` | `2.0` | -100 to 100 |  |
| `vampire_speed_boost` | `amount` | `0.15` | -1 to 10 |  |
| `vampire_daylight_damage` | `damage_per_second` | `2.0` | 0 to 100 | `entity_action.actions.0.amount` |
| `vampire_slow_regen` | `multiplier` | `0.4` | 0 to 10 |  |
| `vampire_water_weakness` | `multiplier` | `1.0` | 0 to 100 |  |
| `vampire_water_breathing` | `enabled` | `true` | true / false |  |

### Verdant

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `verdant_harvest_bonus` | `extra_drops` | `1` | 0 to 100 |  |
| `verdant_nether_damage` | `damage_per_second` | `2.0` | 0 to 100 | `entity_action.amount` |

### Voidwalker

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `voidwalker_active_teleport` | `range` | `24.0` | 1 to 256 |  |
|  | `cooldown_ticks` | `80` | 0 to 72000 |  |
| `voidwalker_active_phase` | `max_depth` | `10` | 1 to 256 |  |
|  | `cooldown_ticks` | `80` | 0 to 72000 |  |
|  | `hunger_cost` | `0` | 0 to 100 |  |
| `voidwalker_water_damage` | `multiplier` | `1.0` | 0 to 100 |  |

### Warden

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `warden_sonic_boom` | `speed` | `2.0` | 0 to 10 | `entity_action.speed` |
|  | `cooldown_ticks` | `400` | 0 to 72000 |  |
| `warden_strength` | `amount` | `4.0` | -100 to 100 |  |
| `warden_ancient_hide` | `amplifier` | `1` | 0 to 4 |  |
| `warden_hulking_frame` | `scale` | `1.15` | 0.1 to 10 |  |
|  | `modify_reach` | `true` | true / false |  |
|  | `reach_bonus` | `0.0` | -10 to 10 |  |
| `warden_lumbering` | `amount` | `-0.30` | -1 to 1 |  |
| `warden_daylight_damage` | `damage_per_second` | `1.0` | 0 to 100 | `entity_action.amount` |
| `warden_night_vision` | `enabled` | `true` | true / false |  |
| `warden_echolocation` | `enabled` | `true` | true / false |  |
| `warden_tremor_sense` | `enabled` | `true` | true / false |  |

### Eastern / wuxia origins

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `asura_frail_frame` | `amount` | `-6` | -100 to 100 |  |
| `asura_bloodrage_1` | `enabled` | `true` | true / false |  |
|  | `amplifier` | `0` | 0 to 255 |  |
| `asura_bloodrage_2` | `enabled` | `true` | true / false |  |
|  | `amplifier` | `1` | 0 to 255 |  |
| `asura_bloodrage_3` | `enabled` | `true` | true / false |  |
|  | `amplifier` | `2` | 0 to 255 |  |
| `asura_unmovable` | `amount` | `1.0` | -1 to 1 |  |
| `asura_slam` | `cooldown_ticks` | `80` | 0 to 72000 |  |
| `asura_frenzy` | `cooldown_ticks` | `400` | 0 to 72000 |  |
| `asura_undying_rage` | `enabled` | `true` | true / false |  |
|  | `amplifier` | `1` | 0 to 255 |  |
| `asura_wrath_eruption` | `cooldown_ticks` | `500` | 0 to 72000 |  |
| `windwalker_air_jumps` | `min` | `0` | 0 to 10000 |  |
|  | `max` | `2` | 1 to 100000 |  |
|  | `start_value` | `2` | 0 to 100000 |  |
|  | `regen_rate` | `2` | -1000 to 1000 |  |
|  | `regen_interval` | `1` | 1 to 72000 |  |
| `windwalker_lofty_leap` | `amount` | `0.12` | -1 to 10 |  |
| `windwalker_cloud_steps` | `cooldown_ticks` | `0` | 0 to 72000 |  |
| `windwalker_gale_dash` | `power` | `2.0` | 0 to 10 |  |
|  | `cooldown_ticks` | `60` | 0 to 72000 |  |
| `windwalker_swift_current` | `amount` | `0.02` | -1 to 10 |  |
| `windwalker_typhoon` | `cooldown_ticks` | `800` | 0 to 72000 |  |
| `windwalker_riding_wind` | `enabled` | `true` | true / false |  |
| `windwalker_eye_of_storm` | `cooldown_ticks` | `700` | 0 to 72000 |  |
| `qi_resource` | `min` | `0` | 0 to 10000 |  |
|  | `max` | `100` | 1 to 100000 |  |
|  | `start_value` | `50` | 0 to 100000 |  |
|  | `regen_rate` | `1` | -1000 to 1000 |  |
|  | `regen_interval` | `20` | 1 to 72000 |  |
| `qi_meditation_charge` | `interval` | `10` | 1 to 72000 |  |
|  | `enabled` | `true` | true / false |  |
| `qi_meditation_heal` | `enabled` | `true` | true / false |  |
|  | `amplifier` | `0` | 0 to 255 |  |
| `qi_vibrating_palm` | `cooldown_ticks` | `20` | 0 to 72000 |  |
|  | `resource_cost_amount` | `25` | 0 to 100000 |  |
| `qi_hardened` | `cooldown_ticks` | `160` | 0 to 72000 |  |
|  | `resource_cost_amount` | `30` | 0 to 100000 |  |
| `qi_dantian_expansion` | `interval` | `20` | 1 to 72000 |  |
|  | `enabled` | `true` | true / false |  |
| `qi_core_pressure` | `multiplier` | `1.3` | 0 to 100 |  |
| `qi_flying_sword` | `cooldown_ticks` | `100` | 0 to 72000 |  |
|  | `resource_cost_amount` | `40` | 0 to 100000 |  |
| `golden_bell_iron_shirt` | `amount` | `8` | -100 to 100 |  |
| `golden_bell_rooted` | `amount` | `0.6` | -1 to 1 |  |
| `golden_bell_hard_qigong` | `damage` | `4` | 0 to 100 |  |
|  | `fire_ticks` | `0` | 0 to 72000 |  |
| `golden_bell_ring` | `cooldown_ticks` | `600` | 0 to 72000 |  |
| `golden_bell_heavy_stance` | `amount` | `-0.02` | -1 to 1 |  |
| `golden_bell_diamond_body` | `amount` | `6` | -100 to 100 |  |
| `golden_bell_reflected_force` | `damage` | `8` | 0 to 100 |  |
|  | `fire_ticks` | `0` | 0 to 72000 |  |
| `golden_bell_bell_toll` | `cooldown_ticks` | `400` | 0 to 72000 |  |
| `iron_monk_stamina` | `min` | `0` | 0 to 10000 |  |
|  | `max` | `100` | 1 to 100000 |  |
|  | `start_value` | `100` | 0 to 100000 |  |
|  | `regen_rate` | `2` | -1000 to 1000 |  |
|  | `regen_interval` | `10` | 1 to 72000 |  |
| `iron_monk_guard` | `enabled` | `true` | true / false |  |
|  | `amplifier` | `0` | 0 to 255 |  |
| `iron_monk_guard_drain` | `interval` | `20` | 1 to 72000 |  |
|  | `enabled` | `true` | true / false |  |
| `iron_monk_guard_block` | `multiplier` | `0.1` | 0 to 100 |  |
| `iron_monk_palm_strike` | `cooldown_ticks` | `40` | 0 to 72000 |  |
|  | `resource_cost_amount` | `20` | 0 to 100000 |  |
| `iron_monk_iron_resolve` | `amount` | `0.4` | -1 to 1 |  |
| `iron_monk_sea_of_stamina` | `interval` | `20` | 1 to 72000 |  |
|  | `enabled` | `true` | true / false |  |
| `iron_monk_lohan_palm` | `cooldown_ticks` | `120` | 0 to 72000 |  |
|  | `resource_cost_amount` | `30` | 0 to 100000 |  |
| `jianxian_sword_heart` | `amount` | `4` | -100 to 100 |  |
| `jianxian_keen_edge` | `amount` | `1` | -100 to 100 |  |
| `jianxian_sword_qi` | `cooldown_ticks` | `25` | 0 to 72000 |  |
| `jianxian_riding_sword` | `enabled` | `true` | true / false |  |
|  | `hunger_cost` | `1` | 0 to 20 |  |
|  | `upkeep_interval` | `60` | 1 to 72000 |  |
| `jianxian_flickering_slash` | `power` | `2.5` | 0 to 10 |  |
|  | `cooldown_ticks` | `80` | 0 to 72000 |  |
|  | `damage` | `6.0` | 0 to 100 |  |
|  | `damage_radius` | `2.5` | 0 to 64 |  |
|  | `weapon_damage_scale` | `0.5` | 0 to 10 |  |
| `jianxian_ten_thousand_swords` | `cooldown_ticks` | `600` | 0 to 72000 |  |
| `jianxian_sword_heart_unity` | `amount` | `2` | -100 to 100 |  |
| `jianxian_heavenly_formation` | `cooldown_ticks` | `400` | 0 to 72000 |  |
| `jianxian_heaven_severing_slash` | `cooldown_ticks` | `300` | 0 to 72000 |  |

### Classes

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `class_warrior_damage` | `amount` | `1.0` | -100 to 100 |  |
| `class_warrior_knockback_resist` | `amount` | `0.3` | -1 to 1 |  |
| `class_archer_agility` | `amount` | `0.15` | -1 to 10 |  |
| `class_miner_speed` | `multiplier` | `1.5` | 0 to 100 |  |
| `class_miner_efficiency` | `multiplier` | `0.7` | 0 to 10 | `modifier.value` |
| `class_beastmaster_diffusal` | `radius` | `16.0` | 0 to 128 |  |
| `class_beastmaster_potions` | `multiplier` | `1.5` | 0 to 10 | `modifier.value` |
| `class_explorer_stamina` | `multiplier` | `0.6` | 0 to 10 | `modifier.value` |
| `class_sentinel_armor` | `amount` | `4.0` | -100 to 100 |  |
| `class_sentinel_knockback_resist` | `amount` | `0.4` | -1 to 1 |  |
| `class_sentinel_thorns` | `return_ratio` | `0.25` | 0 to 10 | `entity_action.amount_ratio` |
| `class_herbalist_growth` | `radius` | `5` | 1 to 64 |  |
|  | `tick_interval` | `40` | 1 to 72000 |  |
|  | `growths_per_interval` | `2` | 1 to 100 |  |
| `class_herbalist_harvest_bonus` | `extra_drops` | `1` | 0 to 100 |  |
| `class_scout_speed` | `amount` | `0.2` | -1 to 10 |  |
| `class_berserker_damage` | `amount` | `3.0` | -100 to 100 |  |
| `class_berserker_hunger` | `multiplier` | `1.5` | 0 to 10 | `modifier.value` |
| `class_titan_size` | `scale` | `1.25` | 0.1 to 10 |  |
|  | `modify_reach` | `true` | true / false |  |
|  | `reach_bonus` | `0.0` | -10 to 10 |  |
| `class_titan_health` | `amount` | `4.0` | -100 to 100 |  |
| `class_titan_reach` | `amount` | `0.5` | -10 to 10 |  |
| `class_rogue_sneaky` | `detection_multiplier` | `0.3` | 0 to 10 |  |
| `class_rogue_stealth` | `activation_ticks` | `200` | 0 to 72000 |  |
| `class_lumberjack_tree_felling` | `max_blocks` | `64` | 1 to 1024 |  |
| `class_lumberjack_bonus_planks` | `bonus_count` | `2` | 0 to 100 |  |
| `class_merchant_trades` | `scan_interval` | `40` | 1 to 72000 |  |
|  | `radius` | `8.0` | 0 to 128 |  |
| `class_cleric_enchanting` | `bonus_levels` | `5` | 0 to 100 | `modifier.value` |
| `class_cleric_potions` | `multiplier` | `2.0` | 0 to 10 | `modifier.value` |
| `class_cook_food` | `saturation_bonus` | `0.5` | 0 to 10 | `modifier.value` |
| `class_cook_smoker_xp` | `multiplier` | `2.0` | 0 to 100 |  |
| `class_blacksmith_repairs` | `cost_multiplier` | `0.5` | 0 to 10 | `modifier.value` |

### Fisher

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `class_fisher_swim_speed` | `amount` | `0.15` | -1 to 10 |  |
| `class_fisher_waters_luck` | `amount` | `1.0` | -10 to 10 |  |
| `class_fisher_sea_legs` | `multiplier` | `0.5` | 0 to 10 |  |

### Mason

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `class_mason_block_reach` | `amount` | `1.0` | -10 to 10 |  |
| `class_mason_strong_grip` | `amount` | `1.0` | -100 to 100 |  |
| `class_mason_stone_speed` | `multiplier` | `1.25` | 0 to 100 |  |

### Paladin

| Power | Key | Default | Range | Writes to |
|---|---|---|---|---|
| `class_paladin_holy_armor` | `amount` | `2.0` | -100 to 100 |  |
| `class_paladin_turn_undead` | `duration` | `80` | 0 to 72000 |  |
|  | `amplifier` | `0` | 0 to 255 |  |
| `class_paladin_beacon_regen` | `radius` | `8.0` | 1 to 16 | `condition.radius` |
