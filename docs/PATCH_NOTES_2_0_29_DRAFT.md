# v2.0.29 — Patch Notes

## Features

- **Custom origin icons with data components** — Origins can now use custom model data in their icon field. Supports three formats:
  - String: `"icon": "minecraft:ink_sac"`
  - Object with legacy tag: `"icon": {"item": "minecraft:ink_sac", "tag": "{CustomModelData:1}"}`
  - Full ItemStack format
- **Ars Nouveau compatibility** — Undead-origin players now correctly have Ars Nouveau's Harm spell damage inverted into healing, matching vanilla undead potion behavior. Heal spell inversion already worked via the existing `isInvertedHealAndHarm` mixin.
- **New power type: `neoorigins:modify_food_nutrition`** — Overrides all food to give a fixed nutrition value. Example: `{"type": "neoorigins:modify_food_nutrition", "nutrition": 1}`
- **New condition type: `neoorigins:cooldown`** — Returns true when a power is off cooldown. Use with `trigger_cooldown` to gate event-driven powers: `{"type": "neoorigins:cooldown", "power": "mypack:my_power"}`
- **Stoneguard Warding Presence is now toggleable** — Players can turn the mob spawn suppression on/off via skill keybind

## Reworks

- **Blacksmith Quality Craftsmanship** — Complete rework. Equipment you craft now receives:
  - Tools: +25% mining speed
  - Weapons: +20% attack damage
  - Armor: +1 armor toughness
  - All damageable items: +10% max durability
  - All values configurable in JSON
- **Cook Good Meals** — Now adds +1 nutrition (hunger) alongside the saturation bonus
- **Cook Smoking Expert** — Was previously inert (no furnace XP event). Now grants +2 nutrition and +0.5 saturation to food cooked in a smoker or furnace
- **Stoneguard Warding Presence** — Radius increased from 24 to 48 blocks (24 was the vanilla default and had no effect)

## Bug Fixes

- **`origins:modify_food` compat power was inert** — The compat layer accepted the power type without errors but never applied `food_modifier`/`saturation_modifier` at eat time. Now fully functional with item_condition filtering and Apoli-compatible modifier math.
- **26.1.2 crash on startup** — Fixed `ExceptionInInitializerError` caused by `RareWanderingLootPower` creating ItemStack/ItemCost objects in static initializers before component registries were bound. Lazy-initialized trade pools.
- **26.1.2 mixin failures** — Fixed three mixin target renames for 26.1.2: `renderFoodLevel` → `extractFoodLevel`, `renderAirLevel` → `extractAirLevel`, `MerchantScreen.render` → `extractContents`
- **Mod armor helmets not blocking sunburn** — Any helmet now blocks sun damage, not just damageable ones. Invulnerable/unbreakable helmets (e.g. AllTheModium) protect indefinitely.
- **Model color transparency broken with nametag off** — Fixed by flushing the render buffer before and after applying the shader color tint, ensuring geometry is drawn with the correct color regardless of nametag visibility.
- **Unknown icon items no longer crash origin loading** — Origins with unrecognized icon items (e.g. `minecraft:scute` renamed to `minecraft:turtle_scute`) now fall back to stone with a warning instead of failing the entire origin parse.
- **Compat translator stripping icon data** — The Origins compat translator was unwrapping icon objects to plain strings, discarding the `tag` field. Now passes icon JSON through untouched for IconCodec to handle.

## Description Fixes

- **Rogue Hidden Presence** — Added mob detection reduction to description
- **Feline Predator's Calm** — Updated to correctly describe creeper-only behavior
- **Verdant Wild Kin** — Clarified that all mobs (including hostile) ignore you
- **Stoneguard Warding Presence** — Updated radius to 48 blocks
- **Feline, Draconic, Hiveling, Gorgon** — Updated hunger-related descriptions to reference energy/stamina resource bars instead of hunger

## Issues Closed

- #85 — NeoOrigins failed to load correctly (26.1.2)
- #64 — Crash on startup with ExceptionInInitializerError (26.1.2)
- #51 — NeoOrigins 2.0+ method name changes (26.1.2)
- #39 — Origin packs not working (migration help)
- #33 — Crash report (NeoForge version mismatch)
- #83 — Armor helmets from other mods don't save from sunburn
- #80 — Resource bar location overlaps vanilla UI (use HUD editor)
- #62 — Entities do not hide the player from rain (fixed in v2.0.18)
- #61 — Model color doesn't work if nametag is set to off
- #76 — Rogue extra power description
- #75 — Feline mob ignore ability description
- #69 — Verdant mob aggro description
- #68 — Stoneguard mob spawn range
- #81 — Resource bar description updates
