# NeoOrigins Mod Compatibility

NeoOrigins ships with built-in support for a number of other mods. Every
integration is a **soft dependency**: each is gated behind a mod-list check (or
runtime reflection), so none of these mods are required. If a mod is absent the
integration simply stays dormant — NeoOrigins runs normally without it.

If a prose doc disagrees with the code, the code wins.

---

## Datapack formats

| Mod / format | What NeoOrigins does | Notes |
|---|---|---|
| **Origins / Apoli** | Reads Origins-format origin and power datapacks and translates them into NeoOrigins powers at load time. | Most power types are supported directly; a few translate with reduced fidelity. Unsupported types are logged at reload and become no-ops rather than crashing the pack. Action and condition fields are accepted in both Apoli forms: a single object or an array (an array is an implicit all-of). Apoli's `command` action verb dispatches as `execute_command`, subject to the same command blacklist. |
| **Apugli / Apace** | Legacy power-type vocabularies are remapped to the 2.0 type names. | See [MIGRATION.md](MIGRATION.md) for the remap table and the known DSL gaps. |

You do not need any of these mods installed — NeoOrigins reads their pack format
natively. Drop an Origins pack into `originpacks/` and it loads.

---

## Gameplay integrations

| Mod | Mod id | What it adds |
|---|---|---|
| **Curios API** | `curios` | Lets conditions inspect equipped curio slots — the `equipped_item` condition's `accessory` slot and umbrella detection both read worn curios. Reflection-based, no hard dependency. |
| **Accessories** | `accessories` | Wisp Forest's accessory system. The `equipped_item` condition's `accessory` slot (and umbrella detection) also reads worn Accessories, alongside Curios. Compile-only soft dependency; **1.21.1 only** (no 26.1 build exists). |
| **Vampires Need Umbrellas** | `vampiresneedumbrellas` | An equipped umbrella shields the holder from both weather-damage conditions: `exposed_to_sun` (sun-burn origins) and `in_rain` (rain/water-damage origins like Wet Fur and True Hydrophobia). The umbrella is detected in either hand or any Curios/Accessories slot. |
| **Ars Nouveau** | `ars_nouveau` | Undead-type origins are healed (rather than harmed) by Ars Nouveau harm spells, mirroring vanilla undead behaviour. |
| **FTB Teams** | `ftbteams` | Players on the same or an allied FTB team are treated as trusted for mount consent — a teammate or ally can ride your mountable origin without sending a consent request. |
| **Open Parties & Claims** | `openpartiesandclaims` | Same as FTB Teams, but using OPAC party membership. |
| **FTB Quests** | `ftbquests` | Adds a first-class "NeoOrigins: Grant Loot Pool" reward type to the quest editor — set the loot table id and roll count directly on the quest. Quests tagged `neoorigins_loot_pool_grant:<table_id>` grant a loot pool on completion too; both routes share the same roll-and-grant pipeline. |
| **FTB Ultimine** | `ftbultimine` | Powers the `neoorigins:ultimine` power: NeoOrigins registers a restriction handler so vein-mining is gated to players holding an active `ultimine` power. The integration is completely dormant unless a loaded pack defines a `neoorigins:ultimine` power — while no pack uses it, FTB Ultimine behaves exactly as vanilla. Once at least one `ultimine` power is loaded, vein-mining is restricted to players who hold one. Block count, tool requirement, and shape follow FTB Ultimine's own server config — the restriction API exposes no override for them. Compile-only soft dependency. |
| **Dragon Survival** | `dragonsurvival` | The `neoorigins:become_dragon` power drives Dragon Survival's own dragon state, so an origin can make its holder a DS dragon of a configured species and stage — DS supplies the actual traits, growth, abilities, altar economy and hunters. Ships with three built-in origins (Cave / Forest / Sea Dragon) gated behind `"required_mods": ["dragonsurvival"]`, so they only load and appear in the picker when DS is installed. Reflection-based, no hard dependency. See the caveat below. |
| **Pehkui** | `pehkui` | Origin body-scale powers drive the Pehkui scale system so resizing renders and collides correctly. See the caveat below. |
| **Epic Fight** | `epicfight` | Origin scaling is applied to Epic Fight's custom entity renderer via a mixin, so scaled origins render correctly with Epic Fight installed. |
| **Iron's Spells 'n Spellbooks** | `irons_spellbooks` | The `neoorigins:cast_iron_spell` action casts an Iron's spell as the player from an origin power — pick the spell id, level, and `instant`/`channel` mode. By default the cast is free from Iron's mana (cost is charged on the NeoOrigins power); set `consume_mana: true` to draw from and gate on the player's Iron's mana pool instead. Compile-only soft dependency (never bundled). Gate the power with `"required_mods": ["irons_spellbooks"]`; without the mod the action is a logged no-op. See [ACTIONS.md](ACTIONS.md#neooriginscast_iron_spell). |
| **Modded attributes** | (any) | `attribute_modifier` powers can target attributes added by other mods (e.g. Iron's Spells, Apothic Attributes). Attribute IDs resolve with or without the `generic.`/`player.` prefix. |

---

## Scripting

| Mod | Mod id | What it adds |
|---|---|---|
| **KubeJS** | `kubejs` | A scripting plugin exposing NeoOrigins to KubeJS: register custom powers, actions, and conditions, and hook origin lifecycle events from JS. |
| **KeybindJS** | `keybindjs` | Hotkey assignment for active powers integrates with KeybindJS bindings on the client. |

---

## Recipe viewers

| Mod | Mod id | What it adds |
|---|---|---|
| **JEI** | `jei` | Adds an information panel for the Orb of Origin item. |
| **REI** | `roughlyenoughitems` | Adds the Orb of Origin to the item list. |
| **EMI** | `emi` | Adds an information panel for the Orb of Origin item (the same copy as the JEI panel). |

---

## Tooltips / probes

| Mod | Mod id | What it adds |
|---|---|---|
| **Jade** | `jade` | Shows the looked-at entity's NeoOrigins origin in the tooltip/probe overlay. |
| **The One Probe** | `theoneprobe` | Shows the looked-at entity's NeoOrigins origin in the tooltip/probe overlay. |

---

## Caveats and known gaps

- **Pehkui scaling is last-write-wins.** NeoOrigins sets the Pehkui scale
  directly rather than composing with it, so an origin scale power and a manual
  `/scale` command will clobber one another — whichever ran last takes effect.
  There is no additive/multiplicative composition between the two.
- **FTB Quests grants loot pools, not origins.** Both the reward type and the
  tag-marker path roll a loot table and deposit the items; neither assigns an
  origin directly. Use a loot table that yields the relevant items, or pair the
  grant with an origin-granting power.
- **GeckoLib is not an active integration (roadmap).** NeoOrigins only probes
  for `geckolib`; the planned `AnimatedProjectileRenderer` has not shipped, so
  projectiles fall back to standard item rendering. Listed here so its absence
  from the table above isn't mistaken for an oversight.
- **Dragon Survival binds reflectively.** DS exposes no public addon API, so
  the bridge resolves its internal classes and methods by name at runtime. If a
  future DS release renames them, `become_dragon` logs one warning and stops
  transforming players (everything else keeps working) — report the DS version
  and the bridge gets re-pointed.
- **Lossy datapack translation.** Some Origins/Apoli modifier types translate
  with reduced fidelity, and a handful of types are unsupported. These are
  logged at reload — check the log if an imported pack behaves unexpectedly.
