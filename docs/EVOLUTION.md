---
title: Evolution System
nav_order: 6
---

# Evolution System

Origins evolve through three tiers as players accumulate mob kills. Each tier
grants new powers and may replace or remove earlier ones. Classes do not evolve.

When you evolve, your health is restored to full.

## Kill Thresholds (configurable)

| Tier | Name | Default Kills |
|------|------|---------------|
| 0 | Base | 0 |
| 1 | Evolved | 1,000 |
| 2 | Ascended | 2,500 |
| 3 | Apex | 5,000 |

Thresholds are configurable in `config/neoorigins/gameplay.toml` under `[evolution]`.
A chat milestone message fires every 100 kills (also configurable).

## Standard HP Progression

Most origins follow this pattern (1 heart = 2 HP):

| Tier | Max Health Bonus |
|------|-----------------|
| Evolved | +2 HP (+1 heart) |
| Ascended | +4 HP (+2 hearts) — replaces Evolved |
| Apex | +6 HP (+3 hearts) — replaces Ascended |

Origins with non-standard HP or unique tier bonuses are noted below.

## Config Options

```toml
[evolution]
evolution_enabled = true
evolution_tier_1_kills = 1000
evolution_tier_2_kills = 2500
evolution_tier_3_kills = 5000
evolution_message_interval = 100
```

---

## Evolution by Origin

### Abyssal

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, +0.2 Swim Speed | Evolved HP |
| 3 - Apex | +6 HP, Conduit Power | Ascended HP |

### Air Mage

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, Slow Falling | Evolved HP |
| 3 - Apex | +6 HP, +10% Speed | Ascended HP |

### Arachnid

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, Poison Immunity | Evolved HP |
| 3 - Apex | +6 HP, Night Vision | Ascended HP |

### Automaton

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, +2 Armor | Evolved HP |
| 3 - Apex | +6 HP, Fire Resistance | Ascended HP |

### Avian

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP, Improved Slow Fall | -- |
| 2 - Ascended | +4 HP, Jump Boost | Evolved HP |
| 3 - Apex | +6 HP, +10% Speed | Ascended HP |

### Blazeling

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | Fire Resistance | -- |
| 3 - Apex | +6 HP | Evolved HP, Water Damage |

### Breeze

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, Slow Falling | Evolved HP |
| 3 - Apex | +6 HP, Jump Boost | Ascended HP |

### Caveborn

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, Night Vision | Evolved HP |
| 3 - Apex | +6 HP, Haste | Ascended HP |

### Cinderborn

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, +2 Armor | Evolved HP |
| 3 - Apex | +6 HP, Fire Resistance | Ascended HP |

### Darkness Mage

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, Improved Shadow Cloak | Evolved HP |
| 3 - Apex | +6 HP, +2 Attack Damage | Ascended HP |

### Draconic

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, +1 Attack Damage | Evolved HP |
| 3 - Apex | +6 HP, +2 Attack Damage, +0.1 Speed | Ascended HP, Ascended Attack |

### Dwarf

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, Haste | Evolved HP |
| 3 - Apex | +6 HP, +2 Armor | Ascended HP |

### Earth Mage

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, +2 Armor, +0.25 Knockback Resist | Evolved HP |
| 3 - Apex | +6 HP, +4 Armor | Ascended HP, Ascended Armor |

### Elytrian

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, Sky Piercer, +0.1 Speed | Evolved HP |
| 3 - Apex | +6 HP, Apex Sky Piercer, Fall Immunity | Ascended HP, Ascended Sky Piercer |

### Enderian

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, Pearl Immunity | Evolved HP |
| 3 - Apex | +6 HP | Ascended HP, Water Damage |

### Enderite

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, +0.1 Speed | Evolved HP |
| 3 - Apex | +6 HP, Fire Resistance | Ascended HP |

### Feline

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP, Fall Resist | -- |
| 2 - Ascended | +4 HP, Fall Immunity, Night Vision | Evolved HP, Evolved Fall Resist |
| 3 - Apex | +6 HP, +15% Speed | Ascended HP |

### Fire Mage

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, Fire Resistance | Evolved HP |
| 3 - Apex | +6 HP, +2 Attack Damage | Ascended HP |

### Frostborn

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, Reduced Fire Weakness | Evolved HP, Base Fire Weakness |
| 3 - Apex | +6 HP | Ascended HP, Ascended Fire Weakness |

### Golem

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP, +2 Armor | -- |
| 2 - Ascended | +4 HP, +4 Armor, +0.25 Knockback Resist | Evolved HP, Evolved Armor |
| 3 - Apex | +6 HP, +6 Armor, Fire Resistance | Ascended HP, Ascended Armor |

### Gorgon

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, +2 Armor | Evolved HP |
| 3 - Apex | +6 HP, +0.15 Speed | Ascended HP |

### Gravity Mage

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, Slow Falling | Evolved HP |
| 3 - Apex | +6 HP, Jump Boost | Ascended HP |

### Hiveling

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, +0.1 Speed | Evolved HP |
| 3 - Apex | +6 HP, Poison Immunity | Ascended HP |

### Human

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, +0.1 Speed | Evolved HP |
| 3 - Apex | +6 HP, Luck | Ascended HP |

### Inchling

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, +0.15 Speed | Evolved HP |
| 3 - Apex | +6 HP, Slow Falling (Dodge) | Ascended HP |

### Kraken

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, +0.2 Swim Speed | Evolved HP |
| 3 - Apex | +6 HP, Conduit Power | Ascended HP |

### Merling

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP, +25% Swim Speed | -- |
| 2 - Ascended | +4 HP, Conduit Power | Evolved HP |
| 3 - Apex | +6 HP, Dolphin's Grace | Ascended HP |

### Monster Tamer

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, +2 Armor | Evolved HP |
| 3 - Apex | +6 HP, +0.1 Speed | Ascended HP |

### Necromancer

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP | Evolved HP |
| 3 - Apex | +6 HP, Night Vision | Ascended HP |

### Phantom

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, +10% Speed | Evolved HP |
| 3 - Apex | +6 HP, Reduced Daylight Damage, Spectral Dodge | Ascended HP, Base Sunburn |

### Piglin

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, +2 Armor | Evolved HP |
| 3 - Apex | +6 HP, Fire Resistance | Ascended HP |

### Revenant

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, +2 Armor | Evolved HP |
| 3 - Apex | +6 HP, +4 Armor, Fire Resistance | Ascended HP, Ascended Armor |

### Sculkborn

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, Night Vision | Evolved HP |
| 3 - Apex | +6 HP, +2 Armor | Ascended HP |

### Shulk

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, +4 Armor | Evolved HP |
| 3 - Apex | +6 HP, +0.25 Knockback Resistance | Ascended HP |

### Siren

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, +0.15 Swim Speed | Evolved HP |
| 3 - Apex | +6 HP, Dolphin's Grace | Ascended HP |

### Skeleton

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP, Improved Marksmanship, Expanded Diet | Base Marksmanship, Base Diet |
| 2 - Ascended | +30% Speed, Reduced Daylight Damage | Base Speed, Base Daylight Damage |
| 3 - Apex | Less Fragile Frame, Fire Resistance | Base Brittle Frame, Ascended Daylight, Evolved Diet |

### Slime

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | Sticky | -- |
| 3 - Apex | +6 HP, Fire Resistance | Evolved HP |

### Sporeling

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, Night Vision | Evolved HP |
| 3 - Apex | +6 HP | Ascended HP |

### Stoneguard

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, +2 Armor, +0.25 Knockback Resist | Evolved HP |
| 3 - Apex | +6 HP, +4 Armor | Ascended HP, Ascended Armor |

### Strider

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, +10% Speed | Evolved HP |
| 3 - Apex | +6 HP, Fire Resistance | Ascended HP |

### Sylvan

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, Night Vision | Evolved HP |
| 3 - Apex | +6 HP, +0.1 Speed | Ascended HP |

### Tiny

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, +0.1 Speed | Evolved HP |
| 3 - Apex | +6 HP, Jump Boost (Evasion) | Ascended HP |

### Umbral

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, Shadow Meld, Night Vision | Evolved HP |
| 3 - Apex | +6 HP, +15% Speed | Ascended HP |

### Vampire

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +3 Attack Damage, +20% Speed | Base Attack, Base Speed |
| 2 - Ascended | Reduced Daylight Damage | Base Daylight Damage |
| 3 - Apex | +4 Attack Damage, Fire Resistance | Evolved Attack, Ascended Daylight |

### Verdant

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, Thorns | Evolved HP |
| 3 - Apex | +6 HP, Regeneration | Ascended HP |

### Voidwalker

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, Fall Resist | Evolved HP |
| 3 - Apex | +6 HP, Fall Immunity | Ascended HP, Ascended Fall Resist |

### Warden

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, +4 Armor | Evolved HP |
| 3 - Apex | +6 HP, +6 Armor, +2 Attack Damage | Ascended HP, Ascended Armor |

### Water Mage

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | -- |
| 2 - Ascended | +4 HP, +0.2 Swim Speed | Evolved HP |
| 3 - Apex | +6 HP, Conduit Power | Ascended HP |

### Wraith

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | Night Vision, Evolved Phase | Base Phase |
| 2 - Ascended | Weakness Aura, Reduced Daylight Damage | Base Daylight Damage |
| 3 - Apex | Apex Phase (bedrock only), Reduced Hunger Drain | Evolved Phase, Base Hunger Drain |

---

## Origins Without Evolution

All **class origins** (20 total) do not evolve. They provide static bonuses:

Archer, Beastmaster, Berserker, Blacksmith, Cleric, Cook, Explorer,
Fisher, Herbalist, Lumberjack, Mason, Merchant, Miner, Nitwit, Paladin,
Rogue, Scout, Sentinel, Titan, Warrior.

---

## Datapack Customization

Evolution tiers are defined per-origin in JSON via `tier_powers`:

```json
{
  "powers": [ "mod:base_power_1", "mod:base_power_2" ],
  "tier_powers": [
    {
      "tier": 1,
      "add": [ "mod:evolved_power" ],
      "remove": []
    },
    {
      "tier": 2,
      "add": [ "mod:ascended_power" ],
      "remove": [ "mod:evolved_power" ]
    },
    {
      "tier": 3,
      "add": [ "mod:apex_power" ],
      "remove": [ "mod:ascended_power" ]
    }
  ]
}
```

Pack authors can add, modify, or remove tiers for any origin.
