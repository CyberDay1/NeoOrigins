# Evolution System

Origins evolve through three tiers as players accumulate mob kills. Each tier
grants new powers and may replace or remove earlier ones. Classes do not evolve.

## Kill Thresholds (configurable)

| Tier | Name | Default Kills |
|------|------|---------------|
| 0 | Base | 0 |
| 1 | Evolved | 1,000 |
| 2 | Ascended | 2,500 |
| 3 | Apex | 5,000 |

Thresholds are configurable in `neoorigins-common.toml` under `[evolution]`.
A chat milestone message fires every 100 kills (also configurable).

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
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Swim Speed | Evolved HP |
| 3 - Apex | +3 HP, Conduit Power | Ascended HP |

### Air Mage
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Slow Falling | Evolved HP |
| 3 - Apex | +3 HP, Speed Boost | Ascended HP |

### Arachnid
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Poison Immunity | Evolved HP |
| 3 - Apex | +3 HP, Night Vision | Ascended HP |

### Automaton
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Overclock | Evolved HP |
| 3 - Apex | +3 HP, Fire Resistance | Ascended HP |

### Avian
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP, Improved Slow Fall | -- |
| 2 - Ascended | +2 HP, Jump Boost | Evolved HP |
| 3 - Apex | +3 HP, Speed Boost | Ascended HP |

### Blazeling
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | Fire Resistance | -- |
| 3 - Apex | +3 HP | Evolved HP, Water Damage |

### Breeze
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Slow Falling | Evolved HP |
| 3 - Apex | +3 HP, Jump Boost | Ascended HP |

### Caveborn
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Night Vision | Evolved HP |
| 3 - Apex | +3 HP, Haste | Ascended HP |

### Cinderborn
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Ember Shield | Evolved HP |
| 3 - Apex | +3 HP, Fire Resistance | Ascended HP |

### Darkness Mage
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Improved Shadow Cloak | Evolved HP |
| 3 - Apex | +3 HP, Attack Bonus | Ascended HP |

### Draconic
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Attack Bonus | Evolved HP |
| 3 - Apex | +3 HP, Apex Attack, Speed | Ascended HP, Ascended Attack |

### Dwarf
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Haste | Evolved HP |
| 3 - Apex | +3 HP, Armor | Ascended HP |

### Earth Mage
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Armor, Knockback Resist | Evolved HP |
| 3 - Apex | +3 HP, Apex Armor | Ascended HP, Ascended Armor |

### Elytrian
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Sky Piercer | Evolved HP |
| 3 - Apex | +3 HP, Apex Sky Piercer | Ascended HP, Ascended Sky Piercer |

### Enderian
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | Pearl Immunity, +2 HP | Evolved HP |
| 3 - Apex | +3 HP | Ascended HP, Water Damage |

### Enderite
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Speed | Evolved HP |
| 3 - Apex | +3 HP, Fire Resistance | Ascended HP |

### Feline
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP, Fall Resist | -- |
| 2 - Ascended | +2 HP, Fall Immunity, Night Vision | Evolved HP, Evolved Fall Resist |
| 3 - Apex | +3 HP, Speed | Ascended HP |

### Fire Mage
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Fire Resistance | Evolved HP |
| 3 - Apex | +3 HP, Attack Bonus | Ascended HP |

### Frostborn
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Reduced Fire Weakness | Evolved HP, Base Fire Weakness |
| 3 - Apex | +3 HP | Ascended HP, Ascended Fire Weakness |

### Golem
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP, Armor | -- |
| 2 - Ascended | +2 HP, Armor, Knockback | Evolved HP, Evolved Armor |
| 3 - Apex | +3 HP, Armor, Fire Resistance | Ascended HP, Ascended Armor |

### Gorgon
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Armor | Evolved HP |
| 3 - Apex | +3 HP, Speed | Ascended HP |

### Gravity Mage
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Slow Falling | Evolved HP |
| 3 - Apex | +3 HP, Jump Boost | Ascended HP |

### Hiveling
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Speed | Evolved HP |
| 3 - Apex | +3 HP, Poison Immunity | Ascended HP |

### Human
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Speed | Evolved HP |
| 3 - Apex | +3 HP, Luck | Ascended HP |

### Inchling
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Speed | Evolved HP |
| 3 - Apex | +3 HP, Dodge | Ascended HP |

### Kraken
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Swim Speed | Evolved HP |
| 3 - Apex | +3 HP, Conduit Power | Ascended HP |

### Merling
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP, Swim Speed | -- |
| 2 - Ascended | +2 HP, Conduit Power | Evolved HP |
| 3 - Apex | +3 HP, Dolphin's Grace | Ascended HP |

### Monster Tamer
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Armor | Evolved HP |
| 3 - Apex | +3 HP, Speed | Ascended HP |

### Necromancer
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP | Evolved HP |
| 3 - Apex | +3 HP, Night Vision | Ascended HP |

### Phantom
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Speed | Evolved HP |
| 3 - Apex | +3 HP, Reduced Daylight Damage, Spectral Dodge | Ascended HP, Base Sunburn |

### Piglin
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Brute Rage | Evolved HP |
| 3 - Apex | +3 HP, Fire Resistance | Ascended HP |

### Revenant
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Undying Will | Evolved HP |
| 3 - Apex | +3 HP, Armor, Fire Resistance | Ascended HP, Undying Will |

### Sculkborn
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Night Vision | Evolved HP |
| 3 - Apex | +3 HP, Armor | Ascended HP |

### Shulk
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Armor | Evolved HP |
| 3 - Apex | +3 HP, Knockback Resistance | Ascended HP |

### Siren
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Swim Speed | Evolved HP |
| 3 - Apex | +3 HP, Dolphin's Grace | Ascended HP |

### Skeleton
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | Improved Marksmanship, +1 HP, Expanded Diet | Base Marksmanship, Base Diet |
| 2 - Ascended | Improved Speed, Reduced Daylight Damage | Base Speed, Base Daylight Damage |
| 3 - Apex | Less Fragile Frame, Fire Resistance | Base Brittle Frame, Ascended Daylight, Evolved Diet |

### Slime
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | Sticky | -- |
| 3 - Apex | +3 HP, Fire Resistance | Evolved HP |

### Sporeling
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Night Vision | Evolved HP |
| 3 - Apex | +3 HP | Ascended HP |

### Stoneguard
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Armor, Knockback Resistance | Evolved HP |
| 3 - Apex | +3 HP, Apex Armor | Ascended HP, Ascended Armor |

### Strider
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Speed | Evolved HP |
| 3 - Apex | +3 HP, Fire Resistance | Ascended HP |

### Sylvan
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Night Vision | Evolved HP |
| 3 - Apex | +3 HP, Speed | Ascended HP |

### Tiny
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Speed | Evolved HP |
| 3 - Apex | +3 HP, Evasion | Ascended HP |

### Umbral
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Shadow Meld | Evolved HP |
| 3 - Apex | +3 HP, Speed | Ascended HP |

### Vampire
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | Improved Attack, Improved Speed | Base Attack, Base Speed |
| 2 - Ascended | Reduced Daylight Damage | Base Daylight Damage |
| 3 - Apex | Apex Attack, Fire Resistance | Evolved Attack, Ascended Daylight |

### Verdant
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Thorns | Evolved HP |
| 3 - Apex | +3 HP, Regeneration | Ascended HP |

### Voidwalker
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Fall Resist | Evolved HP |
| 3 - Apex | +3 HP, Fall Immunity | Ascended HP, Ascended Fall Resist |

### Warden
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Armor | Evolved HP |
| 3 - Apex | +3 HP, Apex Armor, Attack Damage | Ascended HP, Ascended Armor |

### Water Mage
| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +1 HP | -- |
| 2 - Ascended | +2 HP, Swim Speed | Evolved HP |
| 3 - Apex | +3 HP, Conduit Power | Ascended HP |

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
