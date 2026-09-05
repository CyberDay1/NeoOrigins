---
title: Origin Catalog
parent: "Origins & Content"
nav_order: 1
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
- *Deep Vision*: full visibility submerged
- *Pressure Spines*: passive damage to attackers in water
- *Pressure-Hardened Skin*: flat armor bonus
- *Hydro Adapted*: no mining-speed penalty submerged
- *Abyssal Trident*: spawn-in equipment
- *Abyssal Command*: active, calls a guardian ally
- *Pescivore*: fish-only diet
- *Raw Adapted*: bonus saturation from fish
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
- *Whirlwind*: active, spinning displacement burst
- *Updraft*: active, launches self skyward
- *Cushioned Landing*: no fall damage
- *Zephyr*: passive wind affinity
- *Featherfall*: natural slow fall
- *Fragile Frame*: reduced max health
- *Lightweight*: reduced knockback resistance
- *Mana*: resource gauge for active spells

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
- *Spider's Fang*: bonus melee damage
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

- *Iron Frame*: built-in natural armor
- *Sealed Chassis*: immune to drowning
- *Perpetual Engine*: does not get hungry
- *No Hunger Bar*: hides hunger UI
- *No Air Bar*: hides breath UI
- *Mechanical Body*: potions and food effects fizzle
- *Night Optics*: built-in night vision
- *Heavy Chassis*: reduced movement speed
- *Rigid Joints*: slower natural regeneration
- *Anchored Frame*: strong knockback resistance

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, +2 Armor | Evolved HP |
| 3 - Apex | +6 HP, Fire Resistance | Ascended HP |

### Avian

**Impact:** Low  
**Icon:** Feather

A graceful flier who drifts down slowly, fears no heights, and watches the world from above.

**Base powers**

- *Featherweight*: no fall damage
- *Slow Falling*: drifts down gently
- *Athlete's Diet*: no hunger loss while sprinting
- *Keen Sight*: extended view distance
- *Hollow Bones*: reduced max health
- *Feather Hop*: small natural jump boost

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
- *Cushion of Air*: no fall damage
- *Updraft*: natural jump boost
- *Tailwind*: increased movement speed
- *Wisp Frame*: reduced max health
- *Light Frame*: reduced knockback resistance

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Slow Falling | Evolved HP |
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

- *Dark Adapted*: night vision underground
- *Cave Footing*: no fall damage
- *Miner's Hands*: bonus mining speed
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

The seven eating powers are hunger-gated: on a full hunger bar the right-click
does nothing at all, with no message and no eating animation. Stone Eater also
accepts cobbled deepslate. Stone is the only one of the seven that is pure
nutrition; the other six each grant an effect on top.

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

Each of those six effects also carries a hidden cosmetic power, one per ore,
each with its own switch in `power_overrides.toml`:

```toml
[power_overrides.caveborn_copper_tint]
enabled = false
```

Those powers are `model_color` tints, and on 26.1 and 26.2 they draw nothing:
the render call they relied on is gone from these versions, so the player model
keeps its usual colours while a meal is running. The powers are still granted
and the switches still take effect, so anything set here carries forward. Stone
has no tint because it grants no effect.

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
- *Eruption*: active, fireball attack
- *Basalt Skin*: built-in natural armor
- *Magma Bath*: heals while in lava
- *Volcanic Strength*: stronger in the Nether
- *Quenched*: damage in water and rain
- *Ember Glow*: natural night vision
- *Infernal Aura*: passive mobs flee from you
- *Fungal Diet*: eats nether fungi
- *Stamina*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, +2 Armor | Evolved HP |
| 3 - Apex | +6 HP, Fire Resistance | Ascended HP |

### Darkness Mage

**Impact:** High  
**Icon:** Sculk

A shadow-weaving sorcerer who summons orbiting shadow orbs and steps between pools of darkness. Cloaked and deadly at night, but sunlight burns away your power.

**Base powers**

- *Shadow Orb*: active, hurls orbiting dark orbs
- *Shadow Step*: active, dash between dark pools
- *Dark Vision*: natural night vision
- *Shadow Cloak*: invisibility in low light
- *Sunburn*: damage in direct sunlight
- *Light Sensitivity*: weakened in bright areas
- *Mana*: resource gauge for active spells

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
- *Molten Stride*: swims in lava
- *Wing Cushion*: natural slow fall
- *Dragon Wings*: active, controlled flight
- *Flame Breath*: active, fireball attack
- *Apex Presence*: passive mobs flee from you
- *Imposing Stature*: larger hitbox
- *Draconic Strength*: bonus melee damage
- *Quenched Flame*: damage in water and rain
- *Draconic Appetite*: increased hunger drain
- *Stamina*: resource gauge for active abilities

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
- *Stout Constitution*: bonus max health
- *Darkvision*: natural night vision
- *Sturdy Legs*: reduced walk speed
- *Stonecunning*: faster stone mining
- *Short Arms*: reduced attack reach
- *Efficient Metabolism*: slower hunger while mining
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
- *Stone Wall*: active, raises a stone barrier
- *Stonecunning*: faster stone mining
- *Rooted*: strong knockback resistance
- *Heavy*: reduced movement speed
- *Earthbound*: fall damage at heights
- *Mana*: resource gauge for active spells

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
- *Can't Bear Heavy Armor*: cannot wear iron or heavier

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Sky Piercer, +0.1 Speed | Evolved HP |
| 3 - Apex | +6 HP, Apex Sky Piercer, Fall Immunity | Ascended HP, Ascended Sky Piercer |

### Enderian

**Impact:** Low  
**Icon:** Ender Pearl  
**Spawn:** The End

Born on the outer islands of the End: teleports at will, dodges projectiles into the void, and walks unseen by endermen. Water burns like acid.

**Base powers**

- *Ender Eyes*: endermen ignore your gaze
- *Void Step*: dodges projectiles into the void
- *Hydrophobia*: damage in water and rain
- *Ender Warp*: active, short-range teleport
- *Energy*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Pearl Immunity | Evolved HP |
| 3 - Apex | +6 HP | Ascended HP, Water Damage |

### Enderite

**Impact:** High  
**Icon:** Chorus Fruit

A being born of the End: master of teleportation and phasing, graceful in descent, but vulnerable to water and daylight.

**Base powers**

- *Warp Step*: active, short-range teleport
- *Void Phase*: active, brief intangibility
- *Ender Drift*: natural slow fall
- *End Sight*: natural night vision
- *Void Authority*: endermites flee from you
- *Ender Weakness*: damage in water and rain
- *Light Sensitivity*: weakened in bright daylight
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

A quick cat-person with keen night vision and a pouncing leap: lands on their feet, slips past wolves and ocelots, but loathes water and burns through energy fast.

**Base powers**

- *Nine Lives*: reduced fall damage
- *Cat Eyes*: natural night vision
- *Agile*: bonus movement speed
- *Predator's Calm*: wolves and ocelots ignore you
- *Pounce*: active, leaping pounce attack
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

A volatile pyromancer wreathed in flame: hurls fireballs, bursts into an inferno, and burns those who strike you. Fire cannot harm you, but water is your undoing.

**Base powers**

- *Fireball*: active, hurls a fireball
- *Inferno Burst*: active, area fire explosion
- *Flame Cloak*: burns nearby attackers
- *Fire Immunity*: immune to fire damage
- *Water Weakness*: damage in water and rain
- *Internal Furnace*: increased hunger drain
- *Mana*: resource gauge for active spells

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Fire Resistance | Evolved HP |
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

- *Frost Nova*: active, area freeze attack
- *Cold Blooded*: stronger in cold biomes
- *Meltdown*: extra damage from fire
- *Ice Walk*: built-in Frost Walker
- *Ice Shell*: built-in natural armor
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
| 2 | *Reflected Force*: rebound twice the force of every blow | — |
| 3 | *Bell Toll*: active toll that slows and weakens nearby foes | — |

### Golem

**Impact:** High  
**Icon:** Iron Ingot

A walking fortress of iron and will: nearly immovable, but slow and vulnerable to heat.

**Base powers**

- *Iron Hide*: built-in natural armor
- *Immovable*: strong knockback resistance
- *Towering Frame*: larger hitbox
- *Iron Constitution*: immune to most status effects
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

- *Petrifying Gaze*: active, slows targets you look at
- *Stone Fists*: mines stone bare-handed
- *Granite Hide*: built-in natural armor
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
- *Levitation*: active, self-levitation
- *Gravitational Cushion*: no fall damage
- *Unmoored*: reduced friction
- *Frail*: reduced max health
- *Mana*: resource gauge for active spells

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Slow Falling | Evolved HP |
| 3 - Apex | +6 HP, Jump Boost | Ascended HP |

### Hiveling

**Impact:** High  
**Icon:** Honeycomb

A bee-like insectoid who flies freely, stings foes with a venomous strike, and coaxes crops to grow. Fragile wings and endless hunger are the price of flight.

**Base powers**

- *Buzzing Wings*: sustained flight
- *Liftoff*: natural jump boost
- *Venomous Sting*: active, poison melee strike
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
| 3 - Apex | +6 HP, Slow Falling (Dodge) | Ascended HP |

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

- *Tentacle Lash*: active, ranged tentacle strike
- *Ink Shot*: active, blinding ink projectile
- *Deep Lungs*: never drown
- *Tidal Rush*: substantial swim-speed bonus
- *Open Water*: no swim-speed loss off the seafloor
- *Neutral Buoyancy*: holds depth while fully submerged instead of sinking
- *Pescivore*: fish-only diet
- *Raw Adapted*: bonus saturation from fish
- *Pressure Plating*: built-in natural armor
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
- *Pack Bond*: regenerates while pack is near
- *Lone Weakness*: frail without nearby pack
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
- *Wind Beat*: active, midair speed boost
- *Moonplate*: bonus armor at night
- *Soul Drain*: heal on kill
- *Weightless*: no fall damage
- *Sunburn*: damage in direct sunlight
- *Fragile Form*: reduced max health
- *Sleepless Dread*: cannot sleep

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
- *Gold Kinship*: piglins ignore you while wearing gold
- *Brutal Strikes*: bonus melee damage
- *Surface Sickness*: weakened in the Overworld
- *Nether Eyes*: natural night vision
- *Soul Dread*: extra damage from soul fire
- *Fungal Diet*: eats nether fungi

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, +2 Armor | Evolved HP |
| 3 - Apex | +6 HP, Fire Resistance | Ascended HP |

### Qi Cultivator

**Impact:** Medium  
**Icon:** Amethyst Shard

A practitioner of inner arts who channels Qi into ranged palm-strikes and hardened flesh, meditating to refill reserves, but a cultivator run dry is just a monk.

**Base powers**

- *Qi*: inner energy spent on palm arts, replenished slowly or through meditation
- *Meditation*: crouch and be still to gather Qi far faster
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
- *Phase Step*: active, brief intangibility
- *Void Bolt*: active, withering projectile
- *Sunlight's Curse*: damage in direct sunlight
- *Withered Form*: slower natural regeneration
- *Energy*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, +2 Armor | Evolved HP |
| 3 - Apex | +6 HP, +4 Armor, Fire Resistance | Ascended HP, Ascended Armor |

### Sculkborn

**Impact:** High  
**Icon:** Sculk

A being of the deep dark: armored, resistant, and armed with sonic power, but slow, frail in sunlight, and weakened above ground.

**Base powers**

- *Sonic Shriek*: active, ranged sonic blast
- *Sculk Pulse*: active, darkness aura burst
- *Echolocation*: natural night vision
- *Sculk Carapace*: built-in natural armor
- *Deep Rooted*: strong knockback resistance
- *Surface Agony*: damage in direct sunlight
- *Sonic Deflection*: immune to projectiles
- *Vibration Sense*: detects nearby movement
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

Armored like a shulker: shrugs off blows, retreats into a shell when cornered, and launches foes skyward with a levitation burst. Slow, but unshakeable.

**Base powers**

- *Shell*: built-in natural armor
- *Shulker Bulk*: reduced movement speed
- *Bullet Release*: active, levitation projectile
- *Shell Retreat*: active, defensive shell mode
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

An aquatic enchantress born in the open ocean: charms creatures with an alluring presence and heals in the tides, but frail, slow, and dries out on land.

**Base powers**

- *Alluring Presence*: passive mobs are charmed
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
- *Marksmanship*: bonus bow damage and accuracy
- *Bone Light*: bonus movement speed
- *Lightweight*: natural jump boost
- *Brittle Frame*: reduced max health
- *Sun Scorched*: damage in direct sunlight
- *Boneless Diet*: restricted food list
- *Bone Appetite*: can eat bone meal

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

- *Spore Cloud*: active, area poison burst
- *Toxic Resilience*: immune to poison
- *Mycelial Sight*: natural night vision
- *Mushroom Symbiosis*: heals near mushrooms
- *Fungal Contact*: passive spores on attackers
- *Fungal Shell*: built-in natural armor
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

Born of living stone: an unshakeable sentinel who sheds thorns onto attackers, mines rock with bare hands, and sets glowstone torches into the dark. Slow, but unmoving ground never leaves them.

**Base powers**

- *Stone Skin*: built-in natural armor
- *Rocky Rebound*: passive thorns damage
- *Grounded*: strong knockback resistance
- *Stone Light*: active, places a glowstone torch
- *Stone-Footed*: reduced movement speed
- *Stonecrusher*: mines stone bare-handed
- *Warding Presence*: mobs do not spawn near you
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

A Nether-native creature that walks on lava and heals in its warmth, but the cold overworld and water are crippling.

**Base powers**

- *Lavaborn*: fire immunity
- *Nether Speed*: stronger in the Nether
- *Hydrophobia*: damage in water and rain
- *Magma Recovery*: heals while in lava
- *Nether Sight*: natural night vision
- *Cold-Blooded*: reduced speed in the Overworld
- *Obsidian Hide*: built-in natural armor
- *Stampede*: active, charging dash attack
- *Fungal Diet*: eats nether fungi
- *Stamina*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, +10% Speed | Evolved HP |
| 3 - Apex | +6 HP, Fire Resistance | Ascended HP |

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

A spirit of the old forest: swift among the trees, loved by wildlife, and able to entangle foes with a surge of roots. Healed by rain, but scorched by the Nether.

**Base powers**

- *One With Nature*: passive animals are friendly
- *Rain's Embrace*: heals while in rain or water
- *Forest Born*: bonus speed in forests
- *Nature's Blessing*: accelerates nearby crops
- *Entangle*: active, roots a target in place
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
| 3 - Apex | +6 HP, Jump Boost (Evasion) | Ascended HP |

### Umbral

**Impact:** Medium  
**Icon:** Coal

A child of shadow: hurls orbs of dark energy, dashes between positions, and deflects arrows. Swift in the dark, burned by sunlight.

**Base powers**

- *Shadow Sight*: natural night vision
- *Shadow Orb*: active, hurls a dark orb
- *Shadow Dash*: active, short shadow dash
- *Light Aversion*: damage in direct sunlight
- *Shadow Step*: passive projectile deflection
- *Shadowrun*: no hunger loss while sprinting
- *Energy*: resource gauge for active abilities

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | +2 HP | — |
| 2 - Ascended | +4 HP, Shadow Meld, Night Vision | Evolved HP |
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
- *Running Water*: damage in water and rain

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

- *Wild Kin*: passive animals are friendly
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

- *Phase Walk*: active, brief block intangibility
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
- *Tremor Sense*: detects nearby movement
- *Deep Dark Strength*: bonus melee damage
- *Ancient Hide*: built-in natural armor
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
- *Moisture Regen*: heals while in water or rain
- *Dehydration*: damage in arid biomes
- *Fragile*: reduced max health
- *Mana*: resource gauge for active spells

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

- *Featherfall*: drift like a leaf and never take fall damage
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
| 3 | *Eye of the Storm*: active, a cyclone far greater than the Typhoon | — |

### Wraith

**Impact:** High  
**Icon:** Soul Lantern

A tormented spirit that drifts through solid matter. Phases through most blocks but cannot pass obsidian. Sustaining a physical form drains hunger, and sunlight sears its spectral flesh.

**Base powers**

- *Spectral Form*: phases through most blocks
- *Sunlight Sensitivity*: damage in direct sunlight
- *Unstable Form*: increased hunger drain

**Evolution**

| Tier | Added | Removed |
|------|-------|---------|
| 1 - Evolved | Night Vision, Evolved Phase | Base Phase |
| 2 - Ascended | Weakness Aura, Reduced Daylight Damage | Base Daylight Damage |
| 3 - Apex | Apex Phase (bedrock only), Reduced Hunger Drain | Evolved Phase, Base Hunger Drain |
