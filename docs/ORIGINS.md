---
title: Origins
nav_order: 5
---

# Origin Catalog

A per-origin reference for the default NeoOrigins origins. Class origins
(Warrior, Cleric, etc.) are documented separately in
[`CLASSES.md`](CLASSES.md); evolution mechanics live in
[`EVOLUTION.md`](EVOLUTION.md), and authoring your own evolution tiers
via the `tier_powers` origin field is covered in the
[COOKBOOK](COOKBOOK.md#adding-evolution-tiers-to-an-origin).

Origins are listed alphabetically by display name.

### Abyssal

**Impact:** High  
**Icon:** Heart of the Sea  
**Spawn:** Ocean biomes

A creature of the deep: armored, swift in water, and able to call guardian allies. Slow and dries out on land.

**Base powers**

- *Gill Breath*: never drown
- *Deep Current*: substantial swim-speed bonus
- *Open Water*: no swim-speed loss off the seafloor
- *Neutral Buoyancy*: holds depth while fully submerged instead of sinking
- *Deep Vision*: permanent night vision
- *Pressure Spines*: attackers take 30% of the damage they deal back as magic damage
- *Pressure-Hardened Skin*: +4 max health (2 hearts)
- *Hydro Adapted*: no mining-speed penalty submerged
- *Abyssal Trident*: spawn-in equipment
- *Abyssal Command*: active, calls a guardian ally
- *Pescivore*: fish-only diet
- *Raw Adapted*: extra hunger and saturation from raw cod and salmon
- *Landwalker*: reduced walk speed on dry ground
- *Dries Out*: air drains on land in any biome, and suffocates once it empties
- *Natural Swimmer*: built-in Depth Strider
- *Essence*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, +0.2 Swim Speed | Evolved HP |
| 3 - Apex | +6 HP, Conduit Power | Ascended HP |

### Air Mage

**Impact:** High  
**Icon:** Feather

A swift aeromancer who hurls wind charges, spins a whirlwind of displacement, and rides updrafts into the sky. Light as a breeze, but frail as a leaf.

**Base powers**

- *Wind Charge*: active, hurls a wind charge
- *Whirlwind*: active, hurls an orb that spawns a pulling, damaging tornado where it lands
- *Updraft*: active, launches self skyward
- *Cushioned Landing*: no fall damage
- *Zephyr*: movement-speed bonus
- *Featherfall*: natural slow fall
- *Fragile Frame*: reduced max health
- *Lightweight*: reduced knockback resistance
- *Mana*: resource gauge for active spells
- *Zephyr Attunement*: +0.1 cast time reduction, +30 max mana and +15% mana regeneration (only loads when Iron's Spells 'n Spellbooks is installed)

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Slow Falling | Evolved HP |
| 3 - Apex | +6 HP, +10% Speed | Ascended HP |

### Arachnid

**Impact:** Medium  
**Icon:** Cobweb

Nimble spider-folk who scale any wall, fling webs at foes, and strike with fangs. Kin to the eight-legged crawlers of the world.

**Base powers**

- *Wall Climbing*: scale vertical surfaces
- *Web Walker*: unhindered movement in cobwebs
- *Arthropod*: counts as an arthropod for effects
- *Spider's Fang*: melee hits poison the target (Poison I, 3s)
- *Web Shot*: active, fires a cobweb projectile
- *Energy*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Poison Immunity | Evolved HP |
| 3 - Apex | +6 HP, Night Vision | Ascended HP |

### Asura

**Impact:** High  
**Icon:** Netherite Axe

A frail-bodied berserker who grows faster and stronger the closer death looms: fragile when whole, monstrous when bleeding.

**Base powers**

- *Frail Frame*: maximum health reduced by 3 hearts
- *Bloodrage I*: Strength I and Speed I below two-thirds health
- *Bloodrage II*: Strength II and Speed II below 40% health
- *Bloodrage III*: Strength III and Speed III at 25% health
- *Unmovable Wrath*: full knockback resistance at 25% health
- *Asura Slam*: active, lunge forward and hurl nearby foes away
- *Frenzy*: active, a 6-second burst of Strength II, Speed II and Resistance I

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 | *Undying Rage*: regenerate and shrug off fire near death | — |
| 2 | *Blood Tithe*: drain life from every blow you land | — |
| 3 | *Wrath Eruption*: active shockwave that savages and flings nearby foes | — |

### Automaton

**Impact:** High  
**Icon:** Iron Ingot

A mechanical construct that never drowns or starves, but healing potions fizzle, natural recovery is glacial, and every step is heavy.

**Base powers**

- *Iron Frame*: permanent Resistance I
- *Sealed Chassis*: immune to drowning
- *Perpetual Engine*: does not get hungry
- *No Hunger Bar*: hides hunger UI
- *No Air Bar*: hides breath UI
- *Mechanical Body*: immune to Regeneration, Instant Health and Poison
- *Night Optics*: built-in night vision
- *Heavy Chassis*: reduced movement speed
- *Rigid Joints*: slower natural regeneration
- *Anchored Frame*: strong knockback resistance

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Overclock (Speed I and Haste I below 50% HP) | Evolved HP |
| 3 - Apex | +6 HP, Fire Resistance | Ascended HP |

### Avian

**Impact:** Low  
**Icon:** Feather

A graceful flier who drifts down slowly, fears no heights, and watches the world from above.

**Base powers**

- *Featherweight*: no fall damage
- *Slow Falling*: toggleable slow falling (starts off)
- *Athlete's Diet*: hunger drains 75% slower
- *Keen Sight*: sees better in low light
- *Hollow Bones*: reduced max health
- *Feather Hop*: active, small upward hop

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP, Improved Slow Fall | — |
| 2 - Ascended | +4 HP, Jump Boost | Evolved HP |
| 3 - Apex | +6 HP, +10% Speed | Ascended HP |

### Blazeling

**Impact:** High  
**Icon:** Blaze Rod  
**Spawn:** Nether

Born of the nether fires: scaled in brimstone, restored in the dark realm, but poisoned by water and rain.

**Base powers**

- *Heart of Embers*: fire immunity
- *Blaze Scales*: +6 max health
- *Nether-Born*: Speed while in the Nether
- *Internal Heat*: hunger drains 25% faster
- *Water Weakness*: damage in water and rain
- *Heat Sight*: thermal night vision
- *Firebolt*: active, hurls a small fireball
- *Brimstone Fists*: mines stone bare-handed
- *Fungal Diet*: eats nether fungi
- *Energy*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP | Evolved HP |
| 3 - Apex | +6 HP | Ascended HP, Water Damage |

### Breeze

**Impact:** High  
**Icon:** Wind Charge

A creature of wind born in the Trial Chambers: fast, airborne, and armed with gusts, but light and fragile.

**Base powers**

- *Wind Charge*: active, hurls a wind charge
- *Wind Dash*: active, short aerial dash
- *Cushion of Air*: toggleable slow falling
- *Updraft*: toggleable Jump Boost II
- *Tailwind*: increased movement speed
- *Wisp Frame*: reduced max health
- *Light Frame*: slightly smaller body (90% scale)

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP | Evolved HP |
| 3 - Apex | +6 HP, Jump Boost | Ascended HP |

### Cave Dragon

**Impact:** High  
**Icon:** Magma Block  
**Requires:** [Dragon Survival](https://www.curseforge.com/minecraft/mc-mods/dragon-survival). The origin only loads (and only appears in the picker) when the mod is installed

A dragon of fire and stone, born deep beneath the world. Thrives in heat and darkness, and grows into its full power over time.

**Base powers**

- *Cave Dragon Form*: become a Dragon Survival cave dragon; traits, growth and abilities are managed by Dragon Survival

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | Hardened Scales (+2 HP) | — |
| 2 - Ascended | Draconic Might (+2 attack) | — |
| 3 - Apex | Apex Wyrm (+6 HP) | Evolved HP |

### Caveborn

**Impact:** Medium  
**Icon:** Torch

Raised in the deep dark: eats raw stone, chews through ore with bare hands, but withers in sunlight.

**Base powers**

- *Dark Adapted*: permanent night vision
- *Cave Footing*: no fall damage
- *Miner's Hands*: double mining speed
- *Stone Fists*: mines stone bare-handed
- *Mining Fortune*: ores drop as if mined with Fortune II, but only while Luck is active
- *Stone Eater*: can eat raw stone
- *Copper Palate*: can eat copper
- *Iron Palate*: can eat iron
- *Gilded Palate*: can eat gold
- *Diamond Palate*: can eat diamond
- *Emerald Palate*: can eat emerald
- *Netherite Palate*: can eat netherite
- *Verdigris Lungs*: Water Breathing I for 60 seconds after eating copper
- *Iron Rush*: Haste I for 60 seconds after eating iron
- *Gold Rush*: Speed I for 2 minutes after eating gold
- *Diamond Clarity*: Luck II for 5 minutes after eating diamond
- *Emerald Reprieve*: Fire Resistance for 5 minutes after eating emerald
- *Netherite Core*: Strength I and Resistance I for 5 minutes after eating netherite
- *Sun Sensitive*: damaged by direct sunlight
- *Compact Build*: reduced hitbox

The seven eating powers are hunger-gated the way vanilla food is: on a full
hunger bar the right-click does nothing at all, with no message and no eating
animation, unless you are in creative or spectator, which eat regardless just
as they do with bread. Stone Eater also accepts cobbled deepslate. Stone is the
only one of the seven that is pure nutrition; the other six each grant an
effect on top.

What you eat is worth what it is worth as an item. A block of the material feeds
twice as much as an ingot or gem and holds its effect three times as long, and a
nugget is worth one hunger point and fifteen seconds. Hunger caps at twenty, so a
block deliberately does not pay the nine times a crafting table would suggest:
most of that food would be discarded, and the duration is where the value went
instead. Nine nuggets out-feed the ingot they craft into on the hunger bar
alone, nine points against four, but a nugget carries no saturation and fifteen
seconds of buff a second one restarts rather than extends. With saturation the
iron ingot is worth 10.4 and the gold one 15, so the nuggets nether gold ore
drops are a stopgap and not a shortcut. The full table is in
[POWER_TYPES.md](POWER_TYPES.md#neooriginsedible_item).

Diamond is the key to the origin's mining loop: Diamond Clarity is what
switches Mining Fortune on, so a Caveborn eats a diamond and then mines ore for
the next five minutes. Mining Fortune only checks that Luck is present, so a
luck potion turns it on just as well.

Emerald is the answer to Sun Sensitive. The sunlight damage is dealt as fire, so
Fire Resistance cancels it outright and an emerald buys five minutes on the
surface. One detail to expect rather than report as a bug: the power still sets
the player alight every second, and Fire Resistance only cancels fire *damage* —
it does not put the fire out. A Caveborn crossing open ground under Emerald
Reprieve is therefore wreathed in flames and taking no damage from them. Step
into water or shade and the burn runs out on its own.

While one of those six effects is running the player model is washed in the
colour of the ore that granted it: copper orange, iron pale grey, gold
yellow, diamond cyan, emerald green, netherite dark brown. Two meals at once
average their colours. The tint is cosmetic only and is driven by hidden powers, one per ore,
each with its own switch in `power_overrides.toml`:

```toml
[power_overrides.caveborn_copper_tint]
enabled = false
```

Turning one off keeps the buff and drops the colour. Stone has no tint because
it grants no effect.

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, permanent Night Vision | Evolved HP, Dark Adapted |
| 3 - Apex | +6 HP, permanent Haste | Ascended HP, Iron Rush |

The removals at tiers 2 and 3 are replacements rather than losses: Dark Adapted
gives way to the permanent night vision that supersedes it, and Iron Rush goes
because the Apex Haste never expires. Eating iron at Apex still feeds you; it
just no longer grants a buff.

### Cinderborn

**Impact:** High  
**Icon:** Magma Block

A volcanic fire elemental: immune to flame, terrifying to beasts, and healed by lava, but devastated by water.

**Base powers**

- *Molten Core*: fire immunity
- *Eruption*: active, a spread of four small fireballs
- *Basalt Skin*: permanent Resistance I
- *Magma Bath*: heals while in lava
- *Volcanic Strength*: Strength I in the Nether
- *Quenched*: damage in water and rain
- *Ember Glow*: natural night vision
- *Infernal Aura*: passive mobs flee from you
- *Fungal Diet*: eats nether fungi
- *Stamina*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Ember Shield (attackers take 1 damage and burn for 3s) | Evolved HP |
| 3 - Apex | +6 HP | Ascended HP |

### Darkness Mage

**Impact:** High  
**Icon:** Sculk

A shadow-weaving sorcerer who summons orbiting shadow orbs and steps between pools of darkness. Cloaked and deadly at night, but sunlight burns away your power.

**Base powers**

- *Shadow Orb*: active, places up to 3 stationary orbs that blind and darken nearby foes
- *Shadow Step*: active, teleport to the targeted spot up to 24 blocks away
- *Dark Vision*: natural night vision
- *Shadow Cloak*: mobs only notice you from much closer
- *Sunburn*: damage in direct sunlight
- *Light Sensitivity*: mines 30% slower
- *Mana*: resource gauge for active spells
- *Umbral Attunement*: +12% spell power, +10% casting movement speed and +0.05 cooldown reduction (only loads when Iron's Spells 'n Spellbooks is installed)

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Improved Shadow Cloak | Evolved HP |
| 3 - Apex | +6 HP, +2 Attack Damage | Ascended HP |

### Draconic

**Impact:** High  
**Icon:** Dragon Breath

Dragon-blooded and fearsome: immune to fire and terrifying to beasts, but the draconic flame burns through stamina fast.

**Base powers**

- *Dragonblood*: fire immunity
- *Molten Stride*: faster movement in lava
- *Wing Cushion*: no fall damage
- *Dragon Wings*: toggle; jump in mid-air to take elytra-style flight without an elytra
- *Flame Breath*: active, a spread of four small fireballs
- *Apex Presence*: passive mobs flee from you
- *Imposing Stature*: larger hitbox
- *Draconic Strength*: bonus melee damage
- *Quenched Flame*: damage in water and rain
- *Draconic Appetite*: increased hunger drain
- *Stamina*: resource gauge for active abilities
- *Molten Sight*: sees clearly through lava

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, +1 Attack Damage | Evolved HP |
| 3 - Apex | +6 HP, +2 Attack Damage, +0.1 Speed | Ascended HP, Ascended Attack |

### Dwarf

**Impact:** Medium  
**Icon:** Iron Pickaxe

A stout underground craftsman: compact, armored, and tireless at the forge, but short-limbed and slow on the surface.

**Base powers**

- *Compact Frame*: reduced hitbox
- *Stout Constitution*: permanent Resistance I
- *Darkvision*: natural night vision
- *Sturdy Legs*: reduced walk speed
- *Stonecunning*: 25% faster mining
- *Short Arms*: reduced block reach (-0.5)
- *Efficient Metabolism*: hunger drains 25% slower
- *Heirloom Pickaxe*: spawn-in equipment

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Haste | Evolved HP |
| 3 - Apex | +6 HP, +2 Armor | Ascended HP |

### Earth Mage

**Impact:** High  
**Icon:** Cobblestone

A stalwart geomancer who slams the ground to rupture foes and raises stone walls at a gesture. Immovable as a mountain, but the earth holds them close. Water and heights are not their friends.

**Base powers**

- *Ground Slam*: active, area shockwave
- *Stone Wall*: active, places a cobblestone block where you are looking (up to 8 blocks)
- *Stonecunning*: 50% faster mining
- *Rooted*: takes half knockback
- *Heavy*: cannot swim; sinks in water
- *Earthbound*: takes 50% more fall damage
- *Mana*: resource gauge for active spells
- *Stoneward Attunement*: +20% spell resistance, +5% spell power and +20 max mana (only loads when Iron's Spells 'n Spellbooks is installed)

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, +2 Armor, +0.25 Knockback Resist | Evolved HP |
| 3 - Apex | +6 HP, +4 Armor | Ascended HP, Ascended Armor |

### Elytrian

**Impact:** Medium  
**Icon:** Elytra

A pure flight specialist: soars through the skies, untouched by kinetic forces, but fragile of frame and incapable of bearing heavy armor.

**Base powers**

- *Natural Flight*: built-in elytra gliding
- *Elytra Boost*: active, midair speed boost
- *Sky Speed*: bonus glide speed
- *Feather Fall*: no fall damage
- *Wind Cushion*: no kinetic impact damage
- *Fragile Frame*: reduced max health
- *Can't Bear Heavy Armor*: cannot wear iron, gold, diamond or netherite armor

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Sky Piercer | Evolved HP |
| 3 - Apex | +6 HP, Apex Sky Piercer | Ascended HP, Ascended Sky Piercer |

### Enderian

**Impact:** Low  
**Icon:** Ender Pearl  
**Spawn:** The End

Born on the outer islands of the End: teleports at will, dodges projectiles into the void, and walks unseen by endermen. Water burns like acid.

**Base powers**

- *Ender Eyes*: endermen ignore your gaze
- *Void Step*: 50% chance to dodge a projectile and teleport up to 16 blocks away
- *Hydrophobia*: damage in water and rain
- *Ender Warp*: active, teleport to the targeted spot up to 50 blocks away
- *Energy*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Pearl Mastery (no fall damage, which covers ender pearl damage) | Evolved HP |
| 3 - Apex | +6 HP | Ascended HP, Water Damage |

### Enderite

**Impact:** High  
**Icon:** Chorus Fruit

A being born of the End: master of teleportation and phasing, graceful in descent, but vulnerable to water and daylight.

**Base powers**

- *Warp Step*: active, short-range teleport
- *Void Phase*: active, phase through a wall you are facing
- *Ender Drift*: natural slow fall
- *End Sight*: natural night vision
- *Void Authority*: endermites and silverfish flee from you
- *Ender Weakness*: damage in water and rain
- *Light Sensitivity*: take 50% more damage in direct sunlight
- *Void Claws*: bonus melee damage
- *Energy*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, +0.1 Speed | Evolved HP |
| 3 - Apex | +6 HP, Fire Resistance | Ascended HP |

### Feline

**Impact:** Medium  
**Icon:** Salmon

A quick cat-person with keen night vision and a pouncing leap: lands on their feet, goes unnoticed by creepers, but loathes water and burns through energy fast.

**Base powers**

- *Nine Lives*: no fall damage
- *Cat Eyes*: natural night vision
- *Agile*: bonus movement speed
- *Predator's Calm*: creepers ignore you
- *Pounce*: active, leaping dash
- *Hates Water*: damage in water and rain
- *High Metabolism*: increased hunger drain
- *Energy*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP, Fall Resist | — |
| 2 - Ascended | +4 HP, Fall Immunity, Night Vision | Evolved HP, Evolved Fall Resist |
| 3 - Apex | +6 HP, +15% Speed | Ascended HP |

### Fire Mage

**Impact:** High  
**Icon:** Fire Charge

A volatile pyromancer wreathed in flame: hurls fireballs, bursts into an inferno, and scorches those who strike you with reflected damage. Fire cannot harm you, but water is your undoing.

**Base powers**

- *Fireball*: active, hurls a spread of four small fireballs
- *Inferno Burst*: active, fire orb that deals instant damage in a 5-block radius on impact
- *Flame Cloak*: attackers take 30% of the damage they deal back as magic damage
- *Fire Immunity*: immune to fire damage
- *Water Weakness*: damage in water and rain
- *Internal Furnace*: increased hunger drain
- *Mana*: resource gauge for active spells
- *Pyromantic Attunement*: +15% spell power, +0.1 cooldown reduction and +20 max mana (only loads when Iron's Spells 'n Spellbooks is installed)

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP | Evolved HP |
| 3 - Apex | +6 HP, +2 Attack Damage | Ascended HP |

### Forest Dragon

**Impact:** High  
**Icon:** Moss Block  
**Requires:** [Dragon Survival](https://www.curseforge.com/minecraft/mc-mods/dragon-survival). The origin only loads (and only appears in the picker) when the mod is installed  
**Spawn:** Forest biomes

A dragon of root and thorn, at home among ancient trees. Patient and resilient, it grows stronger as it matures.

**Base powers**

- *Forest Dragon Form*: become a Dragon Survival forest dragon; traits, growth and abilities are managed by Dragon Survival

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | Bark Hide (+2 HP) | — |
| 2 - Ascended | Verdant Fury (+2 attack) | — |
| 3 - Apex | Elder Wyrm (+6 HP) | Evolved HP |

### Frostborn

**Impact:** Medium  
**Icon:** Blue Ice

An elemental of ice and cold: freezes enemies, thrives in taigas, but fire and the Nether are devastating.

**Base powers**

- *Frost Nova*: active, frost orb that leaves a 6-block field of Slowness IV
- *Cold Blooded*: Strength I while in taiga biomes
- *Meltdown*: take double damage while burning
- *Ice Walk*: active, place an ice block on a targeted surface up to 5 blocks away
- *Ice Shell*: permanent Resistance I
- *Heat Sickness*: damage in the Nether
- *Cold Immunity*: immune to powder-snow freeze
- *Stamina*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Reduced Fire Weakness | Evolved HP, Base Fire Weakness |
| 3 - Apex | +6 HP | Ascended HP, Ascended Fire Weakness |

### Golden Body

**Impact:** Medium  
**Icon:** Bell

A hard-qigong Iron Body master: an armored, immovable wall that punishes anyone who strikes it, at the cost of nimbleness.

**Base powers**

- *Iron Shirt*: conditioned flesh turns blades (+8 armor)
- *Rooted Stance*: hard to knock back
- *Hard Qigong*: strikes against you rebound on the attacker
- *Golden Body*: active, near-total protection (Resistance V) for 5 seconds
- *Heavy Stance*: reduced movement speed

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 | *Diamond Body*: harder still to wound | — |
| 2 | *Reflected Force*: attackers take 8 damage | — |
| 3 | *Bell Toll*: active toll that slows and weakens nearby foes | — |

### Golem

**Impact:** High  
**Icon:** Iron Ingot

A walking fortress of iron and will: nearly immovable, but slow and vulnerable to heat.

**Base powers**

- *Iron Hide*: permanent Resistance I
- *Immovable*: strong knockback resistance
- *Towering Frame*: larger hitbox
- *Iron Constitution*: immune to Poison, Wither and Hunger
- *Heavy*: reduced movement speed
- *Melting Point*: extra damage from fire
- *Ground Slam*: active, area shockwave
- *Stamina*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP, +2 Armor | — |
| 2 - Ascended | +4 HP, +4 Armor, +0.25 Knockback Resist | Evolved HP, Evolved Armor |
| 3 - Apex | +6 HP, +6 Armor, Fire Resistance | Ascended HP, Ascended Armor |

### Gorgon

**Impact:** High  
**Icon:** Stone

A stone-skinned monstrosity whose gaze petrifies and whose fists shatter, but the weight of stone slows every step.

**Base powers**

- *Petrifying Gaze*: active, petrifying orb that leaves a 6-block field of Slowness V
- *Stone Fists*: +4 attack damage
- *Granite Hide*: permanent Resistance I
- *Immovable*: strong knockback resistance
- *Ponderous*: reduced movement speed
- *Imposing Bulk*: larger hitbox
- *Stone Appetite*: increased hunger drain
- *Stamina*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, +2 Armor | Evolved HP |
| 3 - Apex | +6 HP, +0.15 Speed | Ascended HP |

### Gravity Mage

**Impact:** High  
**Icon:** Ender Eye

A warper of gravitational fields who bends space itself. Pull enemies into a crushing singularity or blast them away, but your own body is untethered from the earth.

**Base powers**

- *Gravity Well*: active, pulls enemies inward
- *Repulse*: active, blasts foes away
- *Levitation*: toggle, self-levitation
- *Gravitational Cushion*: no fall damage
- *Unmoored*: take 75% more knockback
- *Frail*: deal 25% less damage
- *Mana*: resource gauge for active spells
- *Graviturgic Attunement*: +12% spell power and +0.08 cooldown reduction (only loads when Iron's Spells 'n Spellbooks is installed)

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Slow Falling | Evolved HP |
| 3 - Apex | +6 HP, Jump Boost | Ascended HP |

### Hiveling

**Impact:** High  
**Icon:** Honeycomb

A bee-like insectoid who glides on its wings, stings foes with a venomous strike, and coaxes crops to grow. Fragile wings and endless hunger are the price of flight.

**Base powers**

- *Buzzing Wings*: built-in elytra gliding
- *Liftoff*: natural jump boost
- *Venomous Sting*: active, venom orb dealing 4 damage and Poison II in a 3-block radius
- *Pollinator*: accelerates nearby crops
- *Small Frame*: reduced hitbox
- *Arthropod*: counts as an arthropod for effects
- *Fragile Wings*: reduced max health
- *Busy Metabolism*: increased hunger drain
- *Energy*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, +0.1 Speed | Evolved HP |
| 3 - Apex | +6 HP, Poison Immunity | Ascended HP |

### Human

**Impact:** None  
**Icon:** Apple

A regular human being, with no special powers but well-rounded abilities.

_No origin-specific base powers._

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, +0.1 Speed | Evolved HP |
| 3 - Apex | +6 HP, Luck | Ascended HP |

### Inchling

**Impact:** Medium  
**Icon:** Poppy

A quarter-sized humanoid who scales walls with ease and moves with surprising speed, but a fragile body comes with the territory.

**Base powers**

- *Diminutive*: reduced hitbox
- *Wall Crawler*: scale vertical surfaces
- *Featherlight*: no fall damage
- *Fragile Body*: reduced max health
- *Quick Feet*: bonus movement speed
- *Small Appetite*: slower hunger drain

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, +0.15 Speed | Evolved HP |
| 3 - Apex | +6 HP, 15% Dodge Chance | Ascended HP |

### Iron Monk

**Impact:** Medium  
**Icon:** Shield

A defender monk whose guard, blocks and strikes all draw on one shared pool of stamina. Turn the fight aside, but an exhausted monk is wide open.

**Base powers**

- *Stamina*: fuels both guard and skills; recovers only with your guard lowered
- *Guard*: toggle, root in place and absorb almost all damage while draining stamina
- *Guard Upkeep*: holding the guard steadily drains stamina
- *Bulwark*: incoming damage cut by 90% while guarding with stamina to spare
- *Parry*: each blow you block rings out and bites a chunk of stamina
- *Palm Strike*: active, a shockwave that damages and tosses nearby foes (20 stamina)
- *Iron Resolve*: not easily shoved out of stance

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 | *Counter Stance*: blocked blows rebound on the attacker | — |
| 2 | *Sea of Stamina*: stamina trickles back even mid-guard | — |
| 3 | *Lohan Palm*: active, a wide stamina-fueled shockwave | — |

### Kraken

**Impact:** High  
**Icon:** Ink Sac  
**Spawn:** Ocean biomes

A colossal deep-sea predator born in the open ocean: devastating underwater with tentacle lashes, ink shots, and summoned guardians, but beached, sun-burned, and dried out on the surface.

**Base powers**

- *Tentacle Lash*: active, orb that slows everything in a 5-block radius (Slowness III)
- *Ink Shot*: active, blinding ink projectile
- *Deep Lungs*: never drown
- *Tidal Rush*: substantial swim-speed bonus
- *Open Water*: no swim-speed loss off the seafloor
- *Neutral Buoyancy*: holds depth while fully submerged instead of sinking
- *Pescivore*: fish-only diet
- *Raw Adapted*: bonus saturation from fish
- *Pressure Plating*: permanent Resistance I
- *Call of the Deep*: active, summons a guardian ally
- *Beached*: reduced walk speed on dry ground
- *Surface Agony*: damage in direct sunlight
- *Colossal*: larger hitbox
- *Dries Out*: air drains on land in any biome, and suffocates once it empties
- *Aqua Affinity*: no mining-speed penalty submerged
- *Natural Swimmer*: built-in Depth Strider
- *Essence*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, +0.2 Swim Speed | Evolved HP |
| 3 - Apex | +6 HP, Conduit Power | Ascended HP |

### Merling

**Impact:** Medium  
**Icon:** Cod  
**Spawn:** Ocean biomes

An aquatic people born in the open ocean: breathes water and swims with ease, but slow and dries out on dry land.

**Base powers**

- *Gills*: never drown
- *Aquatic Speed*: substantial swim-speed bonus
- *Open Water*: no swim-speed loss off the seafloor
- *Neutral Buoyancy*: holds depth while fully submerged instead of sinking
- *Pescivore*: fish-only diet
- *Raw Adapted*: bonus saturation from fish
- *Deep Sight*: natural night vision
- *Landlubber*: reduced walk speed on dry ground
- *Dries Out*: air drains on land in any biome, and suffocates once it empties
- *Aqua Affinity*: no mining-speed penalty submerged
- *Natural Swimmer*: built-in Depth Strider

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP, +25% Swim Speed | — |
| 2 - Ascended | +4 HP, Conduit Power | Evolved HP |
| 3 - Apex | +6 HP, Dolphin's Grace | Ascended HP |

### Monster Tamer

**Impact:** High  
**Icon:** Lead

A fearless beast handler who bends hostile mobs to their will. Alone they are frail, but with a loyal pack at their side, they are a force to be reckoned with.

**Base powers**

- *Dominate*: active, tames a hostile mob
- *Sic 'Em*: active, directs the pack at a target
- *Pack Bond*: tamed mobs slowly regenerate health while out of combat
- *Lone Weakness*: deals 25% less damage while no tamed mobs are alive
- *Feed the Pack*: increased hunger drain
- *Essence*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, +2 Armor | Evolved HP |
| 3 - Apex | +6 HP, +0.1 Speed | Ascended HP |

### Necromancer

**Impact:** High  
**Icon:** Wither Skeleton Skull

A master of death who commands undead minions: wither skeletons and archers fight at your side, but your own life force is bound to theirs.

**Base powers**

- *Raise Wither Skeleton*: active, summons a wither skeleton
- *Raise Skeleton Archer*: active, summons a skeleton archer
- *Undead Nature*: counts as undead for effects
- *Death Sight*: natural night vision
- *Sunlight Decay*: damage in direct sunlight
- *Withered Body*: reduced max health
- *Death's Embrace*: slower natural regeneration
- *Essence*: resource gauge for active abilities
- *Undying Breath*: does not need to breathe, so never drowns
- *Deathbound Attunement*: +20% summon damage, +8% spell power and +20 max mana (only loads when Iron's Spells 'n Spellbooks is installed)

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP | Evolved HP |
| 3 - Apex | +6 HP, Night Vision | Ascended HP |

### Phantom

**Impact:** High  
**Icon:** Phantom Membrane

A spectral being: gliding through the night on elytra wings, drawing strength from kills, but burned by sunlight and unable to rest.

**Base powers**

- *Night Eyes*: natural night vision
- *Spectral Wings*: built-in elytra gliding
- *Wind Beat*: active, speed boost while gliding
- *Moonplate*: Resistance I at night
- *Soul Drain*: heal on kill
- *Weightless*: no fall damage
- *Sunburn*: damage in direct sunlight
- *Fragile Form*: reduced max health
- *Sleepless Dread*: cannot sleep
- *Spectral Breath*: does not need to breathe, so never drowns

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, +10% Speed | Evolved HP |
| 3 - Apex | +6 HP, Reduced Daylight Damage, Spectral Dodge | Ascended HP, Base Sunburn |

### Piglin

**Impact:** High  
**Icon:** Gold Ingot

A gold-obsessed Nether warrior: powerful at home, feared by kin, but weakened outside the Nether.

**Base powers**

- *Nether Fury*: stronger in the Nether
- *Gold Kinship*: piglins and piglin brutes ignore you (no gold required)
- *Brutal Strikes*: bonus melee damage
- *Surface Sickness*: weakened in the Overworld
- *Nether Eyes*: natural night vision
- *Soul Dread*: takes 0.5 damage a second while in a Soul Sand Valley
- *Fungal Diet*: eats nether fungi

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Brute Rage (Strength II below 30% HP) | Evolved HP |
| 3 - Apex | +6 HP, Fire Resistance | Ascended HP |

### Qi Cultivator

**Impact:** Medium  
**Icon:** Amethyst Shard

A practitioner of inner arts who channels Qi into ranged palm-strikes and hardened flesh, meditating to refill reserves, but a cultivator run dry is just a monk.

**Base powers**

- *Qi*: inner energy spent on palm arts, replenished slowly or through meditation
- *Meditation*: crouch to gather Qi far faster
- *Inner Calm*: meditating mends your wounds
- *Vibrating Palm*: active, hurl a bolt of compressed Qi at range (25 Qi)
- *Hardened Qi*: active, steel the body with Resistance II for 6 seconds (30 Qi)

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 | *Dantian Expansion*: Qi circulates even while you move and fight | — |
| 2 | *Core Pressure*: strike harder while your core runs deep | — |
| 3 | *Flying Sword of Qi*: active, loose a blade of pure energy | — |

### Revenant

**Impact:** Medium  
**Icon:** Rotten Flesh

An undead wanderer who phases through matter and hurls bolts of withering rot: cannot drown and sees in darkness, but heals slowly and burns in sunlight.

**Base powers**

- *Undead Nature*: counts as undead for effects
- *No Breath*: never drown
- *Dead Man's Eyes*: natural night vision
- *Phase Step*: active, teleports you through a wall up to 8 blocks thick
- *Void Bolt*: active, projectile that bursts for magic damage and Weakness II
- *Sunlight's Curse*: damage in direct sunlight
- *Withered Form*: slower natural regeneration
- *Energy*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Undying Will (Strength II and Resistance I below 25% HP) | Evolved HP |
| 3 - Apex | +6 HP, +4 Armor, Fire Resistance | Ascended HP, Undying Will |

### Sculkborn

**Impact:** High  
**Icon:** Sculk

A being of the deep dark: armored, resistant, and armed with sonic power, but slow and burned by sunlight.

**Base powers**

- *Sonic Shriek*: active, ranged sonic blast
- *Sculk Pulse*: active, projectile that leaves a lingering Darkness zone where it lands
- *Echolocation*: natural night vision
- *Sculk Carapace*: permanent Resistance I
- *Deep Rooted*: strong knockback resistance
- *Surface Agony*: damage in direct sunlight
- *Sonic Deflection*: immune to arrows
- *Vibration Sense*: nearby creatures within 10 blocks glow
- *Lumbering*: reduced movement speed
- *Hollow Form*: reduced max health
- *Stamina*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Night Vision | Evolved HP |
| 3 - Apex | +6 HP, +2 Armor | Ascended HP |

### Sea Dragon

**Impact:** High  
**Icon:** Prismarine Crystals  
**Requires:** [Dragon Survival](https://www.curseforge.com/minecraft/mc-mods/dragon-survival). The origin only loads (and only appears in the picker) when the mod is installed  
**Spawn:** Ocean biomes (ocean floor allowed)

A dragon of tide and storm, born to the open water. Swift beneath the waves, it comes into its full strength with age.

**Base powers**

- *Sea Dragon Form*: become a Dragon Survival sea dragon; traits, growth and abilities are managed by Dragon Survival

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | Tidal Hide (+2 HP) | — |
| 2 - Ascended | Riptide Fury (+2 attack) | — |
| 3 - Apex | Leviathan (+6 HP) | Evolved HP |

### Shulk

**Impact:** High  
**Icon:** Shulker Shell

Armored like a shulker: shrugs off blows, blinks away when cornered, and launches foes skyward with a levitation burst. Slow, but unshakeable.

**Base powers**

- *Shell*: permanent Resistance I
- *Shulker Bulk*: reduced movement speed
- *Bullet Release*: active, levitates everything within 5 blocks
- *Shell Retreat*: active, teleports you to a random spot within 12 blocks
- *Grounded*: immune to levitation
- *Stamina*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, +4 Armor | Evolved HP |
| 3 - Apex | +6 HP, +0.25 Knockback Resistance | Ascended HP |

### Siren

**Impact:** Medium  
**Icon:** Nautilus Shell  
**Spawn:** Ocean biomes

An aquatic enchantress born in the open ocean: charms creatures with an alluring presence, but frail, slow, and dries out on land.

**Base powers**

- *Alluring Presence*: mobs won't target you unless you attack them first (bosses excepted)
- *Aquatic Lungs*: never drown
- *Tidal Grace*: substantial swim-speed bonus
- *Open Water*: no swim-speed loss off the seafloor
- *Neutral Buoyancy*: holds depth while fully submerged instead of sinking
- *Pescivore*: fish-only diet
- *Raw Adapted*: bonus saturation from fish
- *Ocean Eyes*: natural night vision
- *Beached*: reduced walk speed on dry ground
- *Delicate Form*: reduced max health
- *Dries Out*: air drains on land in any biome, and suffocates once it empties
- *Aqua Affinity*: no mining-speed penalty submerged
- *Natural Swimmer*: built-in Depth Strider

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, +0.15 Swim Speed | Evolved HP |
| 3 - Apex | +6 HP, Dolphin's Grace | Ascended HP |

### Skeleton

**Impact:** High  
**Icon:** Bone

A reanimated bag of bones: fast, light, and deadly with a bow. Fragile frame, shunned by the sun, and only able to stomach the foulest morsels.

**Base powers**

- *Undead*: counts as undead for effects
- *Marksmanship*: +50% arrow damage
- *Bone Light*: bonus movement speed
- *Lightweight*: natural jump boost
- *Brittle Frame*: reduced max health
- *Sun Scorched*: damage in direct sunlight
- *Boneless Diet*: restricted food list
- *Bone Appetite*: can eat bone meal
- *Hollow Lungs*: does not need to breathe, so never drowns

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP, Improved Marksmanship, Expanded Diet | Base Marksmanship, Base Diet |
| 2 - Ascended | +30% Speed, Reduced Daylight Damage | Base Speed, Base Daylight Damage |
| 3 - Apex | Less Fragile Frame, Fire Resistance | Base Brittle Frame, Ascended Daylight, Evolved Diet |

### Slime

**Impact:** High  
**Icon:** Slime Ball

A gelatinous being that must stay hydrated to survive. Bounces harmlessly off falls, splits away from death when well-moistened, and grows tougher with experience.

**Base powers**

- *Moisture*: hydration resource gauge
- *Split*: survives lethal damage when moist
- *Gelatinous Growth*: gains HP with XP levels
- *Bouncy*: no fall damage

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | Sticky | — |
| 3 - Apex | +6 HP, Fire Resistance | Evolved HP |

### Sporeling

**Impact:** Medium  
**Icon:** Red Mushroom

A fungal creature that thrives in darkness and mushroom fields: armed with toxic spores, but withered by sunlight.

**Base powers**

- *Spore Cloud*: active, fires a spore orb that leaves a lingering Poison II cloud where it lands
- *Toxic Resilience*: immune to poison
- *Mycelial Sight*: natural night vision
- *Mushroom Symbiosis*: Regeneration II while in a mushroom biome
- *Fungal Contact*: heals 1 HP a second while standing on or in mushroom blocks or mycelium
- *Fungal Shell*: permanent Resistance I
- *Sun Withering*: damage in direct sunlight
- *Rooted Gait*: reduced movement speed
- *Vitality*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Night Vision | Evolved HP |
| 3 - Apex | +6 HP | Ascended HP |

### Stoneguard

**Impact:** Medium  
**Icon:** Stone

Born of living stone: an unshakeable sentinel who sheds thorns onto attackers, mines rock at double speed, and sets glowstone into the dark. Slow, but unmoving ground never leaves them.

**Base powers**

- *Stone Skin*: +6 max health (3 hearts)
- *Rocky Rebound*: passive thorns damage
- *Grounded*: strong knockback resistance
- *Stone Light*: active, places a glowstone block where you aim (up to 5 blocks)
- *Stone-Footed*: reduced movement speed
- *Stonecrusher*: mines all blocks twice as fast
- *Warding Presence*: hostile mobs don't spawn naturally within 36 blocks (toggleable)
- *Stamina*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, +2 Armor, +0.25 Knockback Resist | Evolved HP |
| 3 - Apex | +6 HP, +4 Armor | Ascended HP, Ascended Armor |

### Strider

**Impact:** High  
**Icon:** Magma Cream

A Nether-native creature that is immune to fire and heals in lava's warmth, but the cold overworld and water are crippling.

**Base powers**

- *Lavaborn*: fire immunity
- *Nether Speed*: Speed II in the Nether
- *Hydrophobia*: damage in water and rain
- *Magma Recovery*: heals while in lava
- *Nether Sight*: natural night vision
- *Cold-Blooded*: reduced speed in the Overworld
- *Obsidian Hide*: permanent Resistance I
- *Stampede*: active, horizontal charging dash
- *Fungal Diet*: eats nether fungi
- *Stamina*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, +10% Speed | Evolved HP |
| 3 - Apex | +6 HP | Ascended HP |

### Sword Immortal

**Impact:** High  
**Icon:** Diamond Sword

Flies on their blade, hurls sword-qi at range and blinks through battle: every signature art is bound to the sword in hand; disarmed, they fall back to a fall-immune mortal.

**Base powers**

- *Sword Heart*: strike far harder with a blade in hand, merely mortal without one
- *Keen Edge*: your sword recovers almost instantly between cuts
- *Sword Qi*: active, loose a crescent of sword-energy that cuts at range (sword required)
- *Riding the Sword*: toggle, step onto your blade and fly; jump to rise, sneak to descend
- *Flickering Slash*: active, blink forward in a flash of steel
- *Immortal Body*: take no fall damage
- *Ten Thousand Swords Return*: active, hurl a spectral blade that rains a sword-storm where it strikes (sword required)

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 | *Sword Heart Unity*: your edge bites deeper with a sword in hand | — |
| 2 | *Heavenly Sword Formation*: active, rain a ring of blades around you | — |
| 3 | *Heaven-Severing Slash*: active, one gathered cleaving sword-qi crescent | — |

### Sylvan

**Impact:** Low  
**Icon:** Oak Sapling

A spirit of the old forest: swift among the trees, ignored by mobs, and able to entangle foes with a surge of roots. Healed by water, but scorched by the Nether.

**Base powers**

- *One With Nature*: mobs won't target you unless provoked (bosses excepted)
- *Rain's Embrace*: heals 1 HP a second while in water
- *Forest Born*: bonus speed in forests
- *Nature's Blessing*: accelerates nearby crops
- *Entangle*: active, fires a root orb that leaves a 6-block zone of Slowness VI
- *Corruption Bane*: damage in the Nether
- *Vitality*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Night Vision | Evolved HP |
| 3 - Apex | +6 HP, +0.1 Speed | Ascended HP |

### Tiny

**Impact:** Medium  
**Icon:** Poppy

Small in stature but quick and nimble: climbs anything, pulls in items, but hits like a feather.

**Base powers**

- *Diminutive*: reduced hitbox
- *Surface Grip*: scale vertical surfaces
- *Scurry*: bonus movement speed
- *Light as a Feather*: no fall damage
- *Collector*: passive item attraction
- *Tiny Arms*: reduced melee damage
- *Tiny Stomach*: increased hunger drain

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, +0.1 Speed | Evolved HP |
| 3 - Apex | +6 HP, Evasion (20% Dodge Chance) | Ascended HP |

### Umbral

**Impact:** Medium  
**Icon:** Coal

A child of shadow: plants orbs that blind all around them, dashes between positions, and deflects arrows. Burned by sunlight.

**Base powers**

- *Shadow Sight*: natural night vision
- *Shadow Orb*: active, plants a shadow anchor (up to 4) that blinds and darkens non-allied creatures within 28 blocks
- *Shadow Dash*: active, short shadow dash
- *Light Aversion*: damage in direct sunlight
- *Shadow Step*: immune to arrows
- *Shadowrun*: no hunger loss while sprinting
- *Energy*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Shadow Meld | Evolved HP |
| 3 - Apex | +6 HP, +15% Speed | Ascended HP |

### Vampire

**Impact:** High  
**Icon:** Red Dye

An undead predator of the night: swift, strong, and eternally hungry. Sunlight is agony, water burns, and only raw flesh sustains you.

**Base powers**

- *Undead Nature*: counts as undead for effects
- *Predator's Eyes*: natural night vision
- *Fangs*: bonus melee damage
- *Supernatural Speed*: bonus movement speed
- *Sunburn*: damage in direct sunlight
- *Corpse Vitality*: slower natural regeneration
- *Blood Diet*: only raw flesh sustains you
- *Bloodfeast*: raw and rotten flesh nourish you as if freshly cooked
- *Running Water*: damage in water and rain
- *Deathless Breath*: does not need to breathe, so never drowns

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +3 Attack Damage, +20% Speed | Base Attack, Base Speed |
| 2 - Ascended | Reduced Daylight Damage | Base Daylight Damage |
| 3 - Apex | +4 Attack Damage, Fire Resistance | Evolved Attack, Ascended Daylight |

### Verdant

**Impact:** Low  
**Icon:** Grass Block

A living embodiment of the green world: tireless, at peace with the wild, but utterly destroyed by the Nether's corruption.

**Base powers**

- *Wild Kin*: mobs won't target you unless provoked (bosses excepted)
- *Root Landing*: no fall damage
- *Tireless*: no hunger loss while sprinting
- *Bountiful Harvest*: bonus crop yields
- *Forest Heart*: heals while in forests
- *Corruption Rot*: damage in the Nether

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Thorns | Evolved HP |
| 3 - Apex | +6 HP, Regeneration | Ascended HP |

### Voidwalker

**Impact:** Medium  
**Icon:** Ender Pearl

A being touched by the End: phases through blocks, blinks short distances, and walks unseen by most threats. Sprints without tiring, but water is agony.

**Base powers**

- *Phase Walk*: active, teleport through a wall you're facing (up to 10 blocks deep)
- *Void Step*: active, short-range teleport
- *Void Sight*: natural night vision
- *Unseen*: passive mobs ignore you
- *Voidburned*: damage in water and rain
- *Weightless*: no hunger loss while sprinting
- *Energy*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Fall Resist | Evolved HP |
| 3 - Apex | +6 HP, Fall Immunity | Ascended HP, Ascended Fall Resist |

### Warden

**Impact:** High  
**Icon:** Sculk Shrieker

Born from the deep dark: senses the world through vibrations, unleashes a sonic boom at range, and hits like a battering ram. Lumbering and pained by sunlight.

**Base powers**

- *Sonic Boom*: active, long-range sonic blast
- *Echolocation*: vibration-based sight
- *Tremor Sense*: highlights all creatures within 24 blocks (toggleable)
- *Deep Dark Strength*: bonus melee damage
- *Ancient Hide*: permanent Resistance II
- *Deep Dark Sight*: natural night vision
- *Hulking Frame*: larger hitbox
- *Daylight Agony*: damage in direct sunlight
- *Lumbering*: reduced movement speed
- *Stamina*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, +4 Armor | Evolved HP |
| 3 - Apex | +6 HP, +6 Armor, +2 Attack Damage | Ascended HP, Ascended Armor |

### Water Mage

**Impact:** High  
**Icon:** Heart of the Sea

A fluid caster who wields tides and healing currents. At home in the ocean, but withers in arid wastelands.

**Base powers**

- *Tidal Wave*: active, sweeping water blast
- *Healing Mist*: active, area heal
- *Aquatic*: never drown
- *Swift Current*: bonus swim speed
- *Moisture Regen*: heals 0.5 HP a second while in water
- *Dehydration*: damage in arid biomes
- *Fragile*: deal 25% less damage
- *Mana*: resource gauge for active spells
- *Tidal Attunement*: +20% mana regeneration, +10% spell power and +0.05 cooldown reduction (only loads when Iron's Spells 'n Spellbooks is installed)

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, +0.2 Swim Speed | Evolved HP |
| 3 - Apex | +6 HP, Conduit Power | Ascended HP |

### Windwalker

**Impact:** Medium  
**Icon:** Feather

A master of *qinggong*: steps off the air, scales sheer walls, dashes on the wind and never fears a fall, but carries no extra strength into a fight.

**Base powers**

- *Featherfall*: never take fall damage
- *Air Steps*: mid-air jumps before you next touch the ground
- *Lofty Leap*: spring from the ground higher than any earthbound fighter
- *Cloud Steps*: press jump in mid-air to step off the air itself, keeping momentum
- *Wall Grace*: cling to and climb vertical surfaces
- *Gale Dash*: active, burst forward on a gust; aim up to vault skyward
- *Swift Current*: always a step quicker
- *Heaven-Rending Typhoon*: active, call a cyclone that drags foes in and hurls them skyward

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 | *Sky Dancer*: glide freely on the wind | — |
| 2 | *Riding the Wind*: true flight at will | — |
| 3 | *Eye of the Storm*: active, a cyclone far greater than the Typhoon | *Heaven-Rending Typhoon* |

### Wraith

**Impact:** High  
**Icon:** Soul Lantern

A tormented spirit that drifts through solid matter. Phases through most blocks but cannot pass obsidian. Sustaining a physical form drains hunger, and sunlight sears its spectral flesh.

**Base powers**

- *Spectral Form*: phases through most blocks
- *Sunlight Sensitivity*: damage in direct sunlight
- *Unstable Form*: increased hunger drain
- *Breathless*: does not need to breathe, so never drowns

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | Night Vision, Evolved Phase | Base Phase |
| 2 - Ascended | Weakness Aura, Reduced Daylight Damage | Base Daylight Damage |
| 3 - Apex | Apex Phase (bedrock only), Reduced Hunger Drain | Evolved Phase, Base Hunger Drain |
