---
title: Class Catalog
parent: "Origins & Content"
nav_order: 2
---

# Class Catalog

Every built-in class and exactly what each of its powers does, with the numbers the code uses. Classes are the second pick, after the origin: see [Custom Classes](CLASSES.md) for how the class layer works and how to add your own. Every number below can be checked against the class or method named in the Source column.

## Reading the tables

- **Time:** 20 ticks = 1 second.
- **Vanilla bases used in the maths:** walk speed 0.1, max health 20, armor 0, attack damage 1, knockback resistance 0, step height 0.6 blocks, entity reach 3.0 blocks, block reach 4.5 blocks, scale 1.0.
- **Operations:** `ADD_VALUE` adds a flat amount. `ADD_MULTIPLIED_BASE` adds a percentage of the base plus every `ADD_VALUE` bonus, so it also scales flat bonuses from gear, enchantments and other powers: +0.5 entity reach and +25% stack as (3.0 + 0.5) × 1.25.
- **When attribute bonuses apply:** an unconditional bonus attaches when you take the class and stays on. A bonus with a condition (sneaking, standing in water, step assist switched on) is re-checked every 5 ticks (0.25 s), so it can switch up to a quarter-second late.
- **Config:** a key in the Config column is a line in `config/neoorigins/power_overrides.toml`, in the table named by the power id: `class_archer_agility.amount` is the `amount` line of `[power_overrides.class_archer_agility]`. Only a value changed from its default is applied, on `/reload` or a restart. "—" means the power has no key; change it with a datapack. See [Power Overrides Config](POWER_OVERRIDES_CONFIG.md).
- **Classic mode** (`classic_mode` in [`content.toml`](CONTENT_CONFIG.md#classic-mode)): a class with a classic version swaps to that version's power list, a class without one is hidden, and Farmer and Rancher are offered only in classic mode. Each class says what its classic version keeps.
- **Version notes** look like this, and only appear where 1.21.1 and the 26.x builds behave differently:
  > **1.21.1 vs 26.x:** example.

## Rules several classes share

- **The Class Skill key** (default H) fires the first active power in a class's list. Rogue and Scout spend it on the step-assist switch.
- **Starting items** are granted once per character, after every layer has been picked (browsing the picker hands out nothing), and not again on respawn or after an Orb. The item goes straight into the inventory: if the inventory is full it is lost, and the grant still counts. A full `/neoorigins reset` clears the record, so the items are granted again.
- **Attack damage bonuses** add to every melee hit, bare hands included. They do nothing for bows, crossbows or thrown tridents.
- **Knockback resistance** cuts knockback strength by that fraction (0.4 = 40% less), capped at 1.0. It is not a chance to ignore knockback, and it does not affect explosion knockback.
- **Incoming-damage powers** (Soft Landing, Retribution) see the hit before shield blocking, armor and enchantments reduce it.
- **Effect-duration powers** (Alchemist's Touch, Extended Potions, Seasoned Palate, Silver Tongue) stretch every status effect added to you, from any source: potions, beacons, conduits, food and mob attacks. Harmful effects such as Poison, Wither and Darkness last longer too, and so does every refresh of an effect you already have. They only lengthen, never touch instant effects, and multiply with any duration power your origin has.
- **Effect immunities** stop new applications of the effect. An effect you already had when the class was granted runs out normally.
- **Enhanced vision** (Hawk Eye, Divine Sight, Deep Sense) gives full night-vision brightness with no screen tint and no HUD icon; a real Night Vision effect adds nothing on top. The "Toggle Night Vision" key (K by default) switches it off and on, saved per player. Server owners can turn it off for everyone with `disable_enhanced_vision` in `content.toml`. The power's `exposure` field is ignored.
- **Bare-hand tools** work only with an empty main hand. If you have more than one (an origin with pickaxe hands plus a class with axe hands), only the first one found applies; they do not combine. On the blocks it suits, the virtual tool's speed multiplies your bare-hand mining speed exactly as holding that tool would, so Haste, Mining Fatigue and the underwater and mid-air penalties still apply. It has no enchantments and deals no extra damage.
- **Break-speed multipliers** multiply with each other, with any origin's (Caveborn ×2.0 with Miner ×1.5 is ×3.0) and with a bare-hand tool (Mason bare-handed on stone: 4.0 × 1.25 = 5.0).
- **Luck** only changes loot tables that weigh entries by luck, which in vanilla is mainly fishing; fishing reads your Luck when you reel in. Luck does nothing for villager trades.
- **"In water"** means touching water, so wading in shallow water counts. Rain does not.

## Archer

A ranged class with dead-straight shots, extra running speed and night sight.

Classic mode: keeps Sharpshooter and swaps Fleet-Footed for Agility; no Hawk Eye, Arthropod Bane, bow or arrows.

| Power | What it does | Numbers | Config (`power_overrides.toml`) | Source |
|---|---|---|---|---|
| **Sharpshooter** (`class_archer_accuracy`) | Your projectiles fly exactly where you look. | Every projectile you own is re-aimed along your look direction when it spawns, keeping its speed: arrows, tridents, snowballs, eggs, ender pearls, thrown potions, fishing bobbers. Gotcha: Multishot's side arrows are re-aimed too, so a Multishot crossbow fires all three arrows in one line. | — | `MovementPowerEvents#onEntityJoinLevel` |
| **Fleet-Footed** (`class_archer_agility`) | You move faster. | +15% movement speed (`ADD_MULTIPLIED_BASE`), always on. | `class_archer_agility.amount` 0.15 (-1 to 10) | `AttributeModifierPower` |
| **Hawk Eye** (`class_archer_hawk_eye`) | You see in the dark. | Enhanced vision: see the notes above. | — | `EnhancedVisionPower#capabilities`; `LightTextureMixin` |
| **Arthropod Bane** (`class_archer_arthropod_bane`) | Hitting an arthropod slows it. | Any damage you deal to a `#minecraft:arthropod` mob (spiders, cave spiders, bees, silverfish, endermites), arrows included: Slowness I for 40 ticks (2 s). Every hit, no minimum damage. | — | `ActionOnHitPower`; `CombatPowerEvents#onLivingDamage` |
| **Starting Bow** (`class_archer_starting_bow`) | You start with a bow. | 1 bow with Power I. | — | `StartingEquipmentPower#grantIfUngranted` |
| **Starting Arrows** (`class_archer_starting_arrows`) | You start with arrows. | 16 arrows. | — | `StartingEquipmentPower#grantIfUngranted` |
| **Agility** (`classic_archer_agility`, classic only) | You slow down less while drawing a bow. | While using any item whose id contains "bow" (bows and crossbows): +50% movement speed (`ADD_MULTIPLIED_BASE`). Vanilla's item-use slowdown still applies on top, so you move at about 30% of walking speed instead of 20%. | — | `LessItemUseSlowdownPower#onTick`, `#matchesItem` |

## Beastmaster

A tamer whose pets share the good effects on them, and whose own effects last longer.

Classic mode: keeps Empathic Bond and swaps Alchemist's Touch for Fauna Friends.

| Power | What it does | Numbers | Config (`power_overrides.toml`) | Source |
|---|---|---|---|---|
| **Empathic Bond** (`class_beastmaster_diffusal`) | Good effects you gain also go to your pets. | Each beneficial effect you gain (any source, refreshes included) is copied, with the same level and the same duration (after Alchemist's Touch), to every tamed animal you own within 16 blocks: wolves, cats, parrots, horses, donkeys, mules, llamas, camels. Harmful effects are not shared. | `class_beastmaster_diffusal.radius` 16.0 (0 to 128) | `TamedPotionDiffusalPower.Config#radius`; `CombatPowerEvents#onMobEffectAdded` |
| **Alchemist's Touch** (`class_beastmaster_potions`) | Effects on you last longer. | Every effect added to you: duration ×1.5. Harmful effects too: see the notes above. | `class_beastmaster_potions.multiplier` 1.5 (0 to 10) | `CombatPowerEvents#onMobEffectAdded` |
| **Fauna Friends** (`classic_beastmaster_fauna_friends`, classic only) | Your tamed animals get tougher and faster. | Every 60 ticks (3 s), each tamed animal you own within 32 blocks gets +4 max health (2 hearts; healed to full when first boosted) and +0.1 movement speed (`ADD_VALUE`: a wolf goes from 0.3 to 0.4). The boost stays on the animal. Losing the power removes it only from animals within 32 blocks of you at that moment. | — | `TamedAnimalBoostPower.Config`; `#onTick`, `#onRevoked` |

## Berserker

A melee brawler who hits much harder, gets hungry faster and wears armor badly.

Classic mode: not offered in classic mode.

| Power | What it does | Numbers | Config (`power_overrides.toml`) | Source |
|---|---|---|---|---|
| **Frenzy** (`class_berserker_damage`) | Your melee hits deal more damage. | +3 attack damage (`ADD_VALUE`), always on. Melee only, fists included: bows, crossbows and thrown tridents don't benefit. | `class_berserker_damage.amount` 3.0 (-100 to 100) | `AttributeModifierPower` |
| **Ravenous** (`class_berserker_hunger`) | You get hungry faster. | All exhaustion ×1.5: moving, jumping, attacking, mining, taking damage and natural regeneration. | `class_berserker_hunger.multiplier` 1.5 (0 to 10) | `FoodDataMixin`; `FoodDataTickMixin` |
| **Rage Within** (`class_berserker_rage_within`) | You hit harder when badly hurt. | +2 attack damage (`ADD_VALUE`) while your health is at or below 50% of max. Stacks with Frenzy for +5. | — | `AttributeModifierPower`; `ConditionParser#parseRelativeHealth` |
| **Reckless** (`class_berserker_reckless`) | You have less armor. | -2 armor (`ADD_VALUE`). Armor can't go below 0, so it costs nothing while unarmored. | — | `AttributeModifierPower` |
| **Thick Skin** (`class_berserker_thick_skin`) | You are harder to knock back. | +0.2 knockback resistance (20%). | — | `AttributeModifierPower` |

## Blacksmith

A crafter whose handmade gear is better, whose anvil work is cheaper, and who shrugs off fire.

Classic mode: keeps Quality Craftsmanship and Efficient Repairs.

| Power | What it does | Numbers | Config (`power_overrides.toml`) | Source |
|---|---|---|---|---|
| **Quality Craftsmanship** (`class_blacksmith_quality`) | Gear you craft or smith comes out better. | Weapons (swords, axes, tridents): +20% attack damage (`ADD_MULTIPLIED_BASE`, while held). Armor: +1 armor toughness per piece (full set +4). Tools (pickaxes, axes, shovels, hoes, shears): +25% `mining_efficiency` (`ADD_MULTIPLIED_BASE`; the base is 0, so it only amplifies Efficiency). All damageable items: +10% of the item's base durability, rounded up. Applies on the crafting grid (shift-click included), on smithing-table upgrades (recalculated for the upgraded item) and on Overgeared smithing anvils. Not on anvils, loot, trades or enchanting. | — | `QualityEquipmentPower.Config`; `QualityEquipmentPower#onItemCrafted`; `CraftingPowerEvents#applyQualityAttributes` |
| **Efficient Repairs** (`class_blacksmith_repairs`) | Anvil work costs fewer levels. | Anvil XP level cost ×0.5, rounded to the nearest level, minimum 1 (a 3-level job costs 2). Material cost and repair amount are unchanged. Gotcha: "Too Expensive!" (40+ levels) is still judged on the full cost. | `class_blacksmith_repairs.cost_multiplier` 0.5 (0 to 10) | `AnvilMenuCostMixin#neoorigins$applyAnvilCostModifier` (1.21.1); `CraftingPowerEvents#onAnvilUpdate` -> `anvilCostFromModifier` (26.x) |
| **Forge Hands** (`class_blacksmith_forge_hands`) | Your bare hands mine like a stone pickaxe. | With an empty main hand: every pickaxe-mineable block breaks at stone-pickaxe speed (4.0) and drops what a stone pickaxe would get (coal, copper, iron and lapis ore; not gold, redstone, diamond or emerald). | — | `BareHandToolPower`; `BareHandToolEvents#onHarvestCheck`, `#onBreakSpeed` |
| **Forge-tempered** (`class_blacksmith_forge_tempered`) | You take less fire damage. | Damage in the `#minecraft:is_fire` tag ×0.5: fire, burning, lava, magma blocks, campfires, fireballs. You still catch fire. | — | `ModifyDamagePower`; `CombatPowerEvents#onLivingDamage` |
| **Starting Iron** (`class_blacksmith_starting_iron`) | You start with iron. | 4 iron ingots. | — | `StartingEquipmentPower#grantIfUngranted` |

> **1.21.1 vs 26.x:** Quality Craftsmanship decides what counts as a weapon, tool or armor by item class on 1.21.1 and by data component on 26.x. On 26.x every item with a weapon component gets the damage bonus and every item with a tool component gets the mining bonus, so pickaxes, axes, shovels, hoes, swords, maces, tridents and spears get both. Anything equippable in a head, chest, legs or feet slot gets the toughness bonus.

## Cleric

A scholar who enchants better, keeps effects twice as long and weakens the undead.

Classic mode: keeps Enchantment Mastery and Extended Potions.

| Power | What it does | Numbers | Config (`power_overrides.toml`) | Source |
|---|---|---|---|---|
| **Enchantment Mastery** (`class_cleric_enchanting`) | Enchanting tables near you offer stronger enchantments. | +5 levels on each of the three offers at any enchanting table within 8 blocks of you, with no cap (the top slot can reach 35 instead of 30). The XP charge stays 1, 2 or 3 levels, but the level you need to have goes up with the offer. Gotcha: it works for anyone enchanting at that table, and several Clerics nearby don't stack. | `class_cleric_enchanting.bonus_levels` 5 (0 to 100) | `CraftingPowerEvents#onEnchantmentLevelSet` |
| **Extended Potions** (`class_cleric_potions`) | Effects on you last twice as long. | Every effect added to you: duration ×2. Harmful effects too: see the notes above. | `class_cleric_potions.multiplier` 2.0 (0 to 10) | `CombatPowerEvents#onMobEffectAdded` |
| **Turn Undead** (`class_cleric_turn_undead`) | Hitting the undead weakens them. | Any damage you deal to a `#minecraft:undead` mob, arrows included: Weakness I for 80 ticks (4 s). Every hit. | — | `ActionOnHitPower`; `CombatPowerEvents#onLivingDamage` |
| **Divine Sight** (`class_cleric_divine_sight`) | You see in the dark. | Enhanced vision: see the notes above. Identical to Hawk Eye. | — | `EnhancedVisionPower#capabilities`; `LightTextureMixin` |
| **Starting Book** (`class_cleric_starting_book`) | You start with a book. | 1 Book and Quill. | — | `StartingEquipmentPower#grantIfUngranted` |

## Cook

A cook whose crafted and cooked food fills you up more, with a stomach for spoiled food.

Classic mode: keeps Good Meals and Smoking Expert.

| Power | What it does | Numbers | Config (`power_overrides.toml`) | Source |
|---|---|---|---|---|
| **Good Meals** (`class_cook_food`) | Food you make is more filling. | Food you take from a crafting grid, furnace, smoker or blast furnace: +1 food point and +0.5 saturation. Campfire cooking doesn't count. Boosted food no longer stacks with plain food of the same kind. Taking the food any way works: a normal click, shift-click, or the recipe book crafting a stack at once. | `class_cook_food.saturation_bonus` 0.5 (0 to 10) | `CraftingPowerEvents#boostFoodIfCook` |
| **Smoking Expert** (`class_cook_smoker_xp`) | Food you cook in a furnace or smoker is more filling still. | Food you take from a furnace or smoker: a further +2 food points and +0.5 saturation, so +3 and +1.0 together with Good Meals. Gives no XP, despite the id. | `class_cook_smoker_xp.multiplier` 2.0 (0 to 100): adds the value rounded as food points and a quarter of it as saturation | `MoreSmokerXpPower.Config#multiplier`; `CraftingPowerEvents#applySmokingExpertBonus` |
| **Iron Stomach** (`class_cook_iron_stomach`) | Hunger and Nausea can't affect you. | Hunger and Nausea never apply, from any source (rotten flesh, raw chicken, pufferfish, potions, mobs). | — | `EffectImmunityPower.Config#blocks`; `CombatPowerEvents#onMobEffectApplicable` |
| **Seasoned Palate** (`class_cook_seasoned_palate`) | Effects on you last a little longer. | Every effect added to you: duration ×1.25. Harmful effects too: see the notes above. | — | `CombatPowerEvents#onMobEffectAdded` |
| **Cook's Knife** (`class_cook_knife`) | You start with a blade. | 1 iron sword, unenchanted. | — | `StartingEquipmentPower#grantIfUngranted` |

## Explorer

A traveler who starts with navigation gear, tires slowly, never takes fall damage and steps up full blocks.

Classic mode: keeps the compass, clock and maps and adds Fit; no Endurance, Sure-footed, Climber's Step, Step Assist or Campfire Rest.

| Power | What it does | Numbers | Config (`power_overrides.toml`) | Source |
|---|---|---|---|---|
| **Starting Compass** (`class_explorer_kit`) | You start with a compass. | 1 compass. | — | `StartingEquipmentPower#grantIfUngranted` |
| **Starting Clock** (`class_explorer_clock`) | You start with a clock. | 1 clock. | — | `StartingEquipmentPower#grantIfUngranted` |
| **Starting Maps** (`class_explorer_maps`) | You start with maps. | 9 empty maps. | — | `StartingEquipmentPower#grantIfUngranted` |
| **Endurance** (`class_explorer_stamina`) | You get hungry more slowly. | All exhaustion ×0.6 (40% less): moving, jumping, attacking, mining, taking damage and natural regeneration. | `class_explorer_stamina.multiplier` 0.6 (0 to 10) | `FoodDataMixin`; `FoodDataTickMixin` |
| **Sure-footed** (`class_explorer_sure_footed`) | You never take fall damage. | Fall damage is cancelled from any height. | — | `MovementPowerEvents#onLivingFall` |
| **Climber's Step** (`class_explorer_climbers_step`) | You walk up full blocks without jumping. | +0.5 step height (`ADD_VALUE`: 0.6 to 1.1 blocks), only while Step Assist is on. | — | `AttributeModifierPower` |
| *(hidden)* (`step_assist_toggle`) | Holds whether Step Assist is on. Shared with Rogue and Scout. | On by default. The setting survives death and relog. | — | `TogglePower.Config#defaultValue`; `CompatAttachments#TOGGLE_STATE` |
| **Step Assist** (`step_assist_switch`) | Switches Climber's Step on and off. Shared with Rogue and Scout. | Class Skill key (H by default). 10-tick (0.5 s) cooldown, no hunger cost. A high note means on, a low note means off. | — | `ActiveAbilityPower` |
| **Campfire Rest** (`class_explorer_campfire_rest`) | You heal slowly near a campfire when not fighting. | Every 40 ticks (2 s), if a campfire or soul campfire (lit or not) is within 5 blocks of you in any direction (an 11×11×11 cube) and you haven't taken damage from any source for 100 ticks (5 s): Regeneration I for 100 ticks (5 s), topped up on each check. That works out to about 1 health (half a heart) every 2 s. | — | `ConditionPassivePower#onTick`; `ConditionParser#parseNearBlock`, `#parseOutOfCombat` |
| **Fit** (`classic_explorer_fit`, classic only) | Sprinting doesn't make you hungry. | While you sprint, all exhaustion is zero, jumping and attacking included. Natural regeneration still drains hunger. | — | `FoodDataMixin#neoorigins$modifyExhaustion` |

## Farmer

A farmer who gets more from every harvest and from every piece of bone meal.

Classic mode: classic mode only.

| Power | What it does | Numbers | Config (`power_overrides.toml`) | Source |
|---|---|---|---|---|
| **Bountiful Harvest** (`classic_farmer_bountiful_harvest`) | Fully grown crops and natural logs drop extra. | Breaking a fully grown crop (wheat, carrots, potatoes, beetroots, torchflowers) or a log that isn't stripped and wasn't placed by a player drops its whole drop set once more, seeds and Fortune bonus included. Not nether wart, cocoa, sweet berries, melons, pumpkins or pitcher plants. | — | `CropHarvestBonusPower.Config#extraDrops`; `WorldPowerEvents#onBlockBreak` |
| **Blessed Bone Meal** (`class_herbalist_green_thumb`) | Each bone meal works twice. | One bone meal gives two applications. The first rolls vanilla's success chance (saplings 45%); the second always applies if the block can still grow. On crops each application is +2 to 5 growth stages. Only when a player uses it, not dispensers. | — | `CraftingPowerEvents#onBonemeal`, `#resolveBonemealApplications` |

## Fisher

A water-side class that fishes luckier, swims faster, drowns slower and sees in murky water.

Classic mode: not offered in classic mode.

| Power | What it does | Numbers | Config (`power_overrides.toml`) | Source |
|---|---|---|---|---|
| **Water's Luck** (`class_fisher_waters_luck`) | More Luck while you are in water. | +1 Luck (`ADD_VALUE`), only while you are in water yourself. Fishing from the shore or a boat gets nothing, because Luck is read from you when you reel in. | `class_fisher_waters_luck.amount` 1.0 (−10 to 10) | `AttributeModifierPower.Config#parseCondition` |
| **Seal's Grace** (`class_fisher_swim_speed`) | You swim faster. | +15% `neoforge:swim_speed` (`ADD_MULTIPLIED_BASE`, 1.0 → 1.15), always attached. Raises swimming speed through water and the rate you rise when holding jump. No effect on land. | `class_fisher_swim_speed.amount` 0.15 (−1 to 10) | `AttributeModifierPower#onGranted` |
| **Sea Legs** (`class_fisher_sea_legs`) | Drowning hurts half as much. | Drowning damage ×0.5: once your air runs out you take 1 damage (half a heart) per second instead of 2. | `class_fisher_sea_legs.multiplier` 0.5 (0–10) | `ModifyDamagePower.Config#apply`, `CombatPowerEvents#onLivingDamage` |
| **Blessed Rod** (`class_fisher_starting_rod`) | Starting item. | One fishing rod with Luck of the Sea I and Lure I. | — | `StartingEquipmentPower#grantOneStack` |
| **Murky Sight** (`class_fisher_night_sight_water`) | Night Vision while in water. | Every 40 ticks (2 seconds) while in water (wading counts), Night Vision I for 260 ticks (13 seconds), with particles and icon. The refresh comes while more than 200 ticks remain, so it never reaches vanilla's end-of-effect flicker while you stay in water. It lingers up to 13 seconds after you leave, flickering in its last 10. Not affected by the Toggle Night Vision key or `content.toml` `disable_night_vision`. | — | `ConditionPassivePower#onTick` |

## Herbalist

A farming class that speeds up nearby crops, gets bigger harvests, doubles bone meal and shrugs off poison.

Classic mode: not offered in classic mode.

| Power | What it does | Numbers | Config (`power_overrides.toml`) | Source |
|---|---|---|---|---|
| **Green Thumb** (`class_herbalist_growth`) | Nearby crops grow on their own. | Every 40 ticks (2 seconds), up to 2 random crops in an 11×11×11 cube around you (radius 5 blocks) get one bone-meal growth each, with bone-meal particles. Crops means `#minecraft:crops`: wheat, carrots, potatoes, beetroot, melon and pumpkin stems, torchflower and pitcher crops. Wheat, carrots, potatoes and stems gain 2–5 stages per growth, beetroot 0–1, torchflower and pitcher 1. Nether wart, cocoa, sweet berries, saplings and grass are not touched. | `class_herbalist_growth.radius` 5 (1–64), `class_herbalist_growth.tick_interval` 40 (1–72000), `class_herbalist_growth.growths_per_interval` 2 (1–100) | `CropGrowthAcceleratorPower#onTick` |
| **Natural Remedy** (`class_herbalist_poison_immunity`) | Poison cannot take hold. | Blocks every new Poison effect. | — | `EffectImmunityPower`, `CombatPowerEvents#onMobEffectApplicable` |
| **Bountiful Harvest** (`class_herbalist_harvest_bonus`) | Fully grown crops and natural logs drop twice. | One tick after the break, the block's loot is rolled again with your held tool (Fortune counts) and dropped once more. Crops: wheat, carrots, potatoes and beetroot at full growth (not pitcher, torchflower, nether wart, cocoa, berries, melons or pumpkins). Logs: any `#minecraft:logs` block (logs, wood, stems, hyphae) that is not stripped and was not placed by a player; placed logs are tracked per chunk and survive restarts. | `class_herbalist_harvest_bonus.extra_drops` 1 (0–100) | `WorldPowerEvents#onBlockBreak` |
| **Blessed Bone Meal** (`class_herbalist_green_thumb`) | Bone meal works twice. | Each bone meal gives 2 applications instead of 1, for one bone meal. The first has the block's normal success chance (always for crops, 45% for saplings), the second always lands. Works on any block bone meal works on (grass, saplings, moss), not only crops. | — | `CraftingPowerEvents#onBonemeal` |
| **Starting Seeds** (`class_herbalist_starting_seeds`) | Starting item. | One wheat seed. | — | `StartingEquipmentPower#grantOneStack` |
| **Starting Bone Meal** (`class_herbalist_starting_bone_meal`) | Starting item. | One bone meal. | — | `StartingEquipmentPower#grantOneStack` |

## Lumberjack

A woodcutting class that fells whole trees, gets extra planks and chops bare-handed.

Classic mode: keeps Timber! and Efficient Cuts.

| Power | What it does | Numbers | Config (`power_overrides.toml`) | Source |
|---|---|---|---|---|
| **Timber!** (`class_lumberjack_tree_felling`) | Breaking a log fells the logs above it. | Breaking any `#minecraft:logs` block with an axe while not sneaking also breaks up to 64 connected logs, not counting the one you broke. The search starts in the layer above and spreads up and sideways, never down, so logs below your cut stay. The axe is any item in `#minecraft:axes` (modded axes join it); with an empty hand, Axe Hands counts. Any other tool or a bare hand breaks just the one log. Extra logs drop as items with no tool durability, hunger, Fortune or Silk Touch, and leaves are left alone. Player-built log structures are felled too. Sneak to break a single log. | `class_lumberjack_tree_felling.max_blocks` 64 (1–1024); values below 64 change nothing | `WorldPowerEvents#fellsTrees`, `#fellTree` |
| **Efficient Cuts** (`class_lumberjack_bonus_planks`) | Crafting planks gives extra planks. | +2 planks of the crafted kind for every craft whose result is in `#minecraft:planks` (shift-click counts each craft): a log gives 6 instead of 4, a bamboo block 4 instead of 2. Extra planks that do not fit in the inventory are lost. | `class_lumberjack_bonus_planks.bonus_count` 2 (0–100) | `CraftAmountBonusPower.Handler#onItemCrafted` |
| **Axe Hands** (`class_lumberjack_axe_hands`) | An empty hand chops like an iron axe. | Mining speed 6.0 on `#minecraft:mineable/axe` blocks, with iron-tier drops. See the bare-hand note at the top. | — | `BareHandToolEvents#onBreakSpeed`, `#onHarvestCheck` |
| **Starting Axe** (`class_lumberjack_starting_axe`) | Starting item. | One iron axe with Unbreaking II. | — | `StartingEquipmentPower#grantOneStack` |

## Mason

A stonework class that mines faster, reaches farther and mines stone bare-handed.

Classic mode: not offered in classic mode.

| Power | What it does | Numbers | Config (`power_overrides.toml`) | Source |
|---|---|---|---|---|
| **Stone Hands** (`class_mason_stone_hands`) | An empty hand mines like a stone pickaxe. | Mining speed 4.0 on `#minecraft:mineable/pickaxe` blocks, with stone-tier drops: stone, coal, copper, iron and lapis, not gold, redstone, emerald or diamond. See the bare-hand note at the top. | — | `BareHandToolEvents#onBreakSpeed`, `#onHarvestCheck` |
| **Strong Grip** (`class_mason_strong_grip`) | Extra armor. | +1 armor (`ADD_VALUE`), half an armor icon. | `class_mason_strong_grip.amount` 1.0 (−100 to 100) | `AttributeModifierPower#onGranted` |
| **Practiced Swing** (`class_mason_stone_speed`) | You mine every block faster. | Mining speed ×1.25 on every block, with any tool or none, Stone Hands included. | `class_mason_stone_speed.multiplier` 1.25 (0–100) | `BreakSpeedModifierEvents#onBreakSpeed` |
| **Starting Pickaxe** (`class_mason_starting_pickaxe`) | Starting item. | One stone pickaxe with Efficiency I. | — | `StartingEquipmentPower#grantOneStack` |
| **Mason's Reach** (`class_mason_block_reach`) | You reach blocks from farther away. | +1 `block_interaction_range` (`ADD_VALUE`), 4.5 → 5.5 blocks in survival. Covers breaking and using blocks as well as placing them. | `class_mason_block_reach.amount` 1.0 (−10 to 10) | `AttributeModifierPower#onGranted` |

## Merchant

A trading class whose nearby villagers keep restocking and whose wandering traders bring rare stock.

Classic mode: keeps Restocking and Charisma.

| Power | What it does | Numbers | Config (`power_overrides.toml`) | Source |
|---|---|---|---|---|
| **Restocking** (`class_merchant_trades`) | Nearby villagers refill their trades. | Every 40 ticks (2 seconds), every villager inside your hitbox grown by 8 blocks in each direction has all its used trades refilled, and an open trade screen updates at once. A sold-out trade can show its cross for up to 2 seconds. The refill is for everyone trading with those villagers, not only you. Refills do not count toward demand, so heavy buying does not push prices up. Wandering traders are not included. | `class_merchant_trades.scan_interval` 40 (1–72000), `class_merchant_trades.radius` 8.0 (0–128) | `TradeAvailabilityPower#onTick` |
| **Charisma** (`class_merchant_rare_loot`) | Wandering traders offer you extra rare trades. | The first time you right-click each wandering trader, it adds 3 different trades drawn from every Expert (level 4) and Master (level 5) villager trade of all 13 professions. Then a 25% chance adds one treasure trade: a heart of the sea, nether star, totem of undying, enchanted golden apple, trident, elytra or dragon head, for 32–64 emeralds plus 1–6 netherite ingots, one use, shown with a golden glow. Each trader remembers who it has served, so this happens once per trader and player, restarts included. The added trades go into the trader's shared list, so other players can buy them. | — (`master_slots` 3 and `treasure_chance` 0.25 are set in the power JSON only) | `RareWanderingLootPower#injectTrades`, `InteractionPowerEvents#onCharismaTraderInteract` |
| **Starting Emeralds** (`class_merchant_starting_emeralds`) | Starting item. | One emerald. | — | `StartingEquipmentPower#grantOneStack` |
| **Silver Tongue** (`class_merchant_silver_tongue`) | Effects on you last longer. | Every effect added to you lasts ×1.15 as long (rounded down), harmful ones included (see the potion-duration note at the top). It has nothing to do with trading. | — | `CombatPowerEvents#onMobEffectAdded` |
| **Lucky Trader** (`class_merchant_lucky_trader`) | +1 Luck and cheaper trades. | +1 Luck (`ADD_VALUE`), always on: helps fishing (see the Luck note at the top). Every villager and wandering-trader offer's first cost is ×0.9 then −0.5 before rounding, never below 1: at least 1 off any cost of 2 or more (2→1, 5→4, 10→9, 20→18, 64→57). Applied when the trade screen opens, on top of Hero of the Village and reputation discounts. | — | `neoorigins:multiple` of `AttributeModifierPower#onGranted` and the `mod_trade_price` event (`AbstractVillagerTradePriceMixin`) |

> **1.21.1 vs 26.x:** villager trades are data-driven on 26.x, so Charisma draws its 3 trades from a fixed list of 18 unenchanted offers instead: golden carrots, glistering melon slices, a bottle o' enchanting, an ender pearl, a name tag, the globe banner pattern, diamond armor (all four pieces), diamond tools and weapons (the axe is listed twice), a saddle, paintings and a crossbow. There are no enchanted books or enchanted gear. The treasure trade is the same on every version.

## Miner

A mining class that digs faster, tires slower, mines stone bare-handed and sees in the dark.

Classic mode: replaces all five powers with the two classic-only powers marked below.

| Power | What it does | Numbers | Config (`power_overrides.toml`) | Source |
|---|---|---|---|---|
| **Mining Expert** (`class_miner_speed`) | You mine every block faster. | Mining speed ×1.5 on every block, with any tool or none, Stone Fists included. | `class_miner_speed.multiplier` 1.5 (0–100) | `BreakSpeedModifierEvents#onBreakSpeed` |
| **Efficient Digger** (`class_miner_efficiency`) | You get hungry more slowly. | All hunger exhaustion ×0.7 (30% less): sprinting, jumping, swimming, fighting, taking damage, mining, and the exhaustion that natural regeneration costs. | `class_miner_efficiency.multiplier` 0.7 (0–10) | `FoodDataMixin`, `FoodDataTickMixin` |
| **Stone Fists** (`class_miner_stone_fists`) | An empty hand mines like a stone pickaxe. | Mining speed 4.0 on `#minecraft:mineable/pickaxe` blocks, with stone-tier drops (stone, coal, copper, iron, lapis). See the bare-hand note at the top. | — | `BareHandToolEvents#onBreakSpeed`, `#onHarvestCheck` |
| **Deep Sense** (`class_miner_deep_sense`) | You see in the dark. | Full Night Vision brightness, with no effect icon and no end-of-effect flicker. Toggle it with the Toggle Night Vision key (K by default, on until you switch it off, remembered across relogs and deaths). A server can turn it off for everyone with `content.toml` `disable_enhanced_vision`. The JSON `exposure` value (0.7) is ignored. | — | `EnhancedVisionPower#capabilities`, `LightTextureMixin` |
| **Tough Back** (`class_miner_tough_back`) | Extra health. | +2 max health (`ADD_VALUE`), 20 → 22, one extra heart. | — | `AttributeModifierPower#onGranted` |
| **Mining Expert** (`classic_miner_mining_expert`, classic only) | You mine faster with the right pickaxe. | Mining speed ×1.45 on `#minecraft:mineable/pickaxe` blocks, only while the item in your main hand is a correct tool for that block (a pickaxe of high enough tier). Checked on both client and server, so the speed-up shows while you mine. | — | `BreakSpeedModifierEvents#toolAllows` |
| **Experienced Excavator** (`classic_miner_experienced_excavator`, classic only) | Breaking blocks costs no hunger. | Cancels the 0.005 exhaustion charged for each block you break. Every other source of exhaustion is unchanged. | — | `ExhaustionFilterPower#filters`, `ExhaustionMiningMixin` |

## Nitwit

A class with no powers, for players who want to play without one.

Classic mode: offered in classic mode, also with no powers.

Nitwit has no powers on any version. It is listed first among the built-in classes, and a player who closes the class picker after choosing an origin but no class is given Nitwit, so their starting items still arrive. That fallback is skipped when Nitwit is switched off in `content.toml` (`[classes] class_nitwit = false`).

## Paladin

A holy-warrior class that weakens undead, wears natural armor, heals near beacons and is immune to Wither.

Classic mode: not offered in classic mode.

| Power | What it does | Numbers | Config (`power_overrides.toml`) | Source |
|---|---|---|---|---|
| **Turn Undead** (`class_paladin_turn_undead`) | Hitting undead weakens them. | Every hit credited to you on a `#minecraft:undead` mob (skeletons, zombies, the Wither, phantoms), melee or your own arrows and tridents, gives it Weakness I for 80 ticks (4 seconds), with particles. Weakness I is −4 melee attack damage, so it does not weaken skeleton arrows. No cooldown: each hit refreshes it. | `class_paladin_turn_undead.duration` 80 (0–72000), `class_paladin_turn_undead.amplifier` 0 (0–255) | `ActionOnHitPower#execute`, `CombatPowerEvents#onLivingDamage` |
| **Holy Armor** (`class_paladin_holy_armor`) | Extra armor. | +2 armor (`ADD_VALUE`), one armor icon. | `class_paladin_holy_armor.amount` 2.0 (−100 to 100) | `AttributeModifierPower#onGranted` |
| **Beacon Regeneration** (`class_paladin_beacon_regen`) | You heal near a beacon. | Every 40 ticks (2 seconds), if an active beacon is within a 17×17×17 cube around you (radius 8 blocks, up to about 13.9 blocks away on the diagonal), Regeneration I for 100 ticks (5 seconds). Net healing is about 1 health (half a heart) every 2 seconds, plus one more heal after you leave. Active means what vanilla requires before a beacon gives effects: a pyramid under it and an unobstructed beam. A bare beacon block does not count. | `class_paladin_beacon_regen.radius` 8.0 (1–16), whole blocks only (a fraction is dropped) | `ConditionPassivePower#onTick`, `ConditionParser#parseNearBlock`, `#isActiveBeacon` |
| **Consecrated Blade** (`class_paladin_starting_sword`) | Starting item. | One iron sword with Smite I (+2.5 damage against undead). | — | `StartingEquipmentPower#grantOneStack` |
| **Holy Ward** (`class_paladin_effect_immunity`) | Wither cannot take hold. | Blocks every new Wither effect (wither skeletons, wither roses, the Wither's skulls). The hit or explosion that carries it still does its damage. | — | `EffectImmunityPower`, `CombatPowerEvents#onMobEffectApplicable` |

## Rancher

A farm-animal class from the original Origins: Classes: animals you breed have twins, and animals you kill drop double loot.

Classic mode: classic mode only (the class has no `[classes]` toggle in `content.toml`).

| Power | What it does | Numbers | Config (`power_overrides.toml`) | Source |
|---|---|---|---|---|
| **Twin Births** (`classic_rancher_twin_births`) | Every animal you breed has a twin. | Chance 1.0 (100%, the codec default). Fires when you fed either parent. The twin spawns at the first parent as a fresh baby of the same type: it inherits nothing from the parents (default variant and wool color; a twin horse gets the unrandomized base stats of 53 health, 0.225 speed and 0.7 jump strength) and gives no extra breeding XP. Turtles, frogs and sniffers lay eggs or spawn instead, so they never have twins. | — | `TwinBreedingPower.Config#chance`; `WorldPowerEvents#onBabyEntitySpawn` |
| **Scavenging** (`classic_rancher_scavenging`) | Animals you kill drop twice the loot. | Multiplier ×2.0: each loot-table drop is copied once. Counts kills where you are the damage source, by melee or your own projectile; a tamed wolf's kill does not. Saddles, horse armor, carried chests and worn gear are not copied, and XP is not doubled. Only vanilla `Animal` mobs count (cows, pigs, sheep, chickens, rabbits, hoglins and the like), not squid, fish or bats. | — | `CombatPowerEvents#onLivingDrops`, `#scavengedCopies`; `LootTableDrops` |

## Rogue

A stealth class: mobs notice you late, a long crouch turns you invisible, and you hit harder while crouched.

Classic mode: keeps Hidden Presence and Stealth and adds the Vanish description card; drops step assist, Soft Landing, Parkour and Backstab. With no step-assist switch in the list, Stealth gets the Class Skill key (H) there and can be switched off.

| Power | What it does | Numbers | Config (`power_overrides.toml`) | Source |
|---|---|---|---|---|
| **Hidden Presence** (`class_rogue_sneaky`) | Mobs only notice you up close. | A mob can only pick you as a target from within 30% of its follow range: zombie 35 → 10.5 blocks; skeleton, creeper and spider 16 → 4.8 blocks. Always on, whether you sneak or not. It also blocks retaliation: a mob you hit from farther out still cannot target you. A mob already chasing you keeps chasing. | `class_rogue_sneaky.detection_multiplier` 0.3 (0 to 10) | `SneakyPower.Config#detectionMultiplier`; `WorldPowerEvents#onLivingChangeTarget` |
| **Stealth** (`class_rogue_stealth`) | Crouch long enough and you turn invisible. | Sneak for 200 ticks (10 s) without letting go: you then get Invisibility I for 40 ticks (2 s), renewed every tick while you keep sneaking, with no particles and no effect icon. Standing up resets the count, and the last 2 s run out on their own. Attacking does not end it, and armor and held items stay visible. While you have Invisibility from any source, a potion included, mobs cannot pick you as a new target. No key in this class: the step-assist switch takes the Class Skill key, so Stealth is always on and is switched back on at every login. | `class_rogue_stealth.activation_ticks` 200 (0 to 72000) | `StealthPower#tickEffect`; `WorldPowerEvents#onLivingChangeTarget`; `KeylessToggleRelease#release` |
| **Soft Landing** (`class_rogue_soft_landing`) | Falls hurt half as much. | Fall damage (`minecraft:fall` only) × 0.5, before Feather Falling and Protection, which still reduce what is left. Elytra wall impacts and pointed-dripstone landings take full damage. | — | `ModifyDamagePower.Config#apply`; `CombatPowerEvents#onLivingDamage` |
| **Parkour** (`class_rogue_parkour`), with the Step Assist switch | Step assist (shared, see Explorer). | See Explorer. | — | `AttributeModifierPower`; `step_assist_switch` |
| **Backstab** (`class_rogue_backstab`) | Hit harder while crouched. | +2 attack damage (`ADD_VALUE`) while you hold sneak, from any angle: there is no behind-the-target check. | — | `AttributeModifierPower`; `sneaking` condition |
| **Vanish** (`classic_rogue_stealth_descriptor`, classic only) | Description card for the classic Stealth power. | Does nothing by itself. | — | `SimplePower` |

## Scout

A pathfinder class: night vision, extra speed, no fall damage and step assist.

Classic mode: not offered in classic mode.

| Power | What it does | Numbers | Config (`power_overrides.toml`) | Source |
|---|---|---|---|---|
| **Keen Eyes** (`class_scout_night_vision`) | See in the dark. | Night Vision I, infinite duration, no particles, no effect icon. Uses no skill slot: switch it with the Toggle Night Vision key (default K). Servers can switch it off for everyone with `disable_night_vision` in `content.toml`. | — | `LegacyPowerTypeAliases` (`night_vision` → `persistent_effect`); `PersistentEffectPower#isNightVisionSuppressed` |
| **Swift Stride** (`class_scout_speed`) | Move faster. | +20% movement speed (`ADD_MULTIPLIED_BASE` 0.2): walk speed 0.1 → 0.12. Always on. | `class_scout_speed.amount` 0.2 (−1 to 10) | `AttributeModifierPower` |
| **Sure-Footed** (`class_scout_no_fall_damage`) | No fall damage. | Cancels the whole fall, so 0 fall damage from any height, pointed-dripstone landings included. Elytra wall impacts are not falls and still hurt. | — | `PreventActionPower` (`fall_damage`); `MovementPowerEvents#onLivingFall` |
| **Tracker's Stride** (`class_scout_trackers_stride`), with the Step Assist switch | Step assist (shared, see Explorer). | See Explorer. | — | `AttributeModifierPower`; `step_assist_switch` |
| **Trail Rations** (`class_scout_trail_rations`) | A loaf of bread to start with. | 1 bread, given once per player after the whole origin pick is confirmed. Not given again on death or respawn, and neither the Orb of Origin nor the Orb of Class re-arms it: only a full `/neoorigins reset <player>` does. | — | `StartingEquipmentPower#onGranted`, `#grantAllPending` |

## Sentinel

A defensive class: natural armor, damage reflection and firm footing.

Classic mode: not offered in classic mode.

| Power | What it does | Numbers | Config (`power_overrides.toml`) | Source |
|---|---|---|---|---|
| **Guardian's Plate** (`class_sentinel_armor`) | Natural armor. | +4 armor (`ADD_VALUE`), 2 armor icons. Always on. | `class_sentinel_armor.amount` 4.0 (−100 to 100) | `AttributeModifierPower` |
| **Retribution** (`class_sentinel_thorns`) | Attackers take a share of the damage back. | Deals `max(0.5, hit × 0.25)` magic damage, which ignores armor, to whoever hit you. `hit` is the amount before your shield and armor reduce it. Works on melee attackers and on the shooter of a projectile at any range. Fires even when your shield blocks the hit; a hit another power cancels does not count. | `class_sentinel_thorns.return_ratio` 0.25 (0 to 10) | `BuiltinActions` (`damage_attacker`); `CombatPowerEvents#onLivingDamage` |
| **Anchored** (`class_sentinel_knockback_resist`) | Hard to knock back. | +0.4 knockback resistance (`ADD_VALUE`): 40% less knockback. Always on. | `class_sentinel_knockback_resist.amount` 0.4 (−1 to 1) | `AttributeModifierPower` |
| **Iron Will** (`class_sentinel_iron_will`) | Immune to Weakness and Slowness. | Blocks new Weakness and Slowness effects from any source. | — | `EffectImmunityPower`; `CombatPowerEvents#onMobEffectApplicable` |
| **Guard Endurance** (`class_sentinel_guard_endurance`) | One extra heart. | +2 max health (`ADD_VALUE`): 20 → 22. | — | `AttributeModifierPower` |
| **Guard Stance** (`class_sentinel_guard_stance`) | Brace while crouched. | +0.35 knockback resistance (`ADD_VALUE`) while you hold sneak: 0.75 (75%) in total with Anchored. | — | `AttributeModifierPower`; `sneaking` condition |

## Titan

A big, tough, slow class with longer reach.

Classic mode: not offered in classic mode.

| Power | What it does | Numbers | Config (`power_overrides.toml`) | Source |
|---|---|---|---|---|
| **Imposing** (`class_titan_size`) | 25% larger. | Scale 1.25 (`minecraft:scale` +0.25, `ADD_VALUE`): hitbox 0.75 wide × 2.25 tall, 1.875 tall crouching. That is too tall for a 2-block gap, so the game makes you crouch through one. Also adds +25% block reach (4.5 → 5.625 blocks) and +25% entity reach (`ADD_MULTIPLIED_BASE` 0.25). | `class_titan_size.scale` 1.25 (0.1 to 10); `class_titan_size.modify_reach` true; `class_titan_size.reach_bonus` 0.0 (−10 to 10, flat blocks added to both reaches) | `SizeScalingPower#applyModifiers` |
| **Thick-Skinned** (`class_titan_health`) | Two extra hearts. | +4 max health (`ADD_VALUE`): 20 → 24. | `class_titan_health.amount` 4.0 (−100 to 100) | `AttributeModifierPower` |
| **Long Arms** (`class_titan_reach`) | Reach entities from farther away. | +0.5 entity reach (`ADD_VALUE`). With Imposing: (3.0 + 0.5) × 1.25 = 4.375 blocks to hit or use entities, against 3.0 in vanilla. Block reach is unchanged by this power. | `class_titan_reach.amount` 0.5 (−10 to 10) | `AttributeModifierPower` |
| **Crushing Grip** (`class_titan_crushing_grip`) | Hit harder. | +1 attack damage (`ADD_VALUE`). | — | `AttributeModifierPower` |
| **Unmoving** (`class_titan_unmoving`) | Hard to knock back. | +0.2 knockback resistance (`ADD_VALUE`): 20% less knockback. | — | `AttributeModifierPower` |
| **Heavy Steps** (`class_titan_heavy_steps`) | Move slower. | −10% movement speed (`ADD_MULTIPLIED_BASE` −0.1): walk speed 0.1 → 0.09. | — | `AttributeModifierPower` |

## Warrior

A melee class that hits harder, takes hits better and shrugs off knockback.

Classic mode: keeps Weapon Mastery and adds Shield Wield; drops Battle Stance, Battle-Hardened, Warrior's Endurance and Iron Will.

| Power | What it does | Numbers | Config (`power_overrides.toml`) | Source |
|---|---|---|---|---|
| **Weapon Mastery** (`class_warrior_damage`) | Hit harder in melee. | +1 attack damage (`ADD_VALUE`). Always on. | `class_warrior_damage.amount` 1.0 (−100 to 100) | `AttributeModifierPower` |
| **Battle Stance** (`class_warrior_knockback_resist`) | Hard to knock back. | +0.3 knockback resistance (`ADD_VALUE`): 30% less knockback. Always on. | `class_warrior_knockback_resist.amount` 0.3 (−1 to 1) | `AttributeModifierPower` |
| **Battle-Hardened** (`class_warrior_battle_stance`) | Natural armor. | +2 armor (`ADD_VALUE`), 1 armor icon. Always on. | — | `AttributeModifierPower` |
| **Warrior's Endurance** (`class_warrior_endurance`) | One extra heart. | +2 max health (`ADD_VALUE`): 20 → 22. | — | `AttributeModifierPower` |
| **Iron Will** (`class_warrior_iron_will`) | Immune to Weakness. | Blocks new Weakness effects from any source. | — | `EffectImmunityPower`; `CombatPowerEvents#onMobEffectApplicable` |
| **Shield Wield** (`classic_warrior_shield_wield`, classic only) | Less slowdown while blocking with a shield. | +50% movement speed (`ADD_MULTIPLIED_BASE` 0.5) while you use an item whose id contains `shield`, modded shields included. Vanilla's item-use slowdown still applies, so blocking speed goes from 20% to about 30% of walking speed. | — | `LessItemUseSlowdownPower#onTick`, `#matchesItem` |
