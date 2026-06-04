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
| **Origins / Apoli** | Reads Origins-format origin and power datapacks and translates them into NeoOrigins powers at load time. | Most power types are supported directly; a few translate with reduced fidelity. Unsupported types are logged at reload and become no-ops rather than crashing the pack. |
| **Apugli / Apace** | Legacy power-type vocabularies are remapped to the 2.0 type names. | See [MIGRATION.md](MIGRATION.md) for the remap table and the known DSL gaps. |

You do not need any of these mods installed — NeoOrigins reads their pack format
natively. Drop an Origins pack into `originpacks/` and it loads.

---

## Gameplay integrations

| Mod | Mod id | What it adds |
|---|---|---|
| **Curios API** | `curios` | Lets conditions inspect equipped curio slots (e.g. detecting a held/worn umbrella). Reflection-based, no hard dependency. |
| **Vampires Need Umbrellas** | `vampiresneedumbrellas` | Recognises that mod's umbrella items for umbrella-based conditions. |
| **Ars Nouveau** | `ars_nouveau` | Undead-type origins are healed (rather than harmed) by Ars Nouveau harm spells, mirroring vanilla undead behaviour. |
| **FTB Teams** | `ftbteams` | Players on the same FTB team are treated as trusted for mount consent — a teammate can ride your mountable origin without sending a consent request. |
| **Open Parties & Claims** | `openpartiesandclaims` | Same as FTB Teams, but using OPAC party membership. |
| **FTB Quests** | `ftbquests` | Quests tagged `neoorigins_loot_pool_grant:<table_id>` grant a loot pool on completion. The tag-marker path is used for soft-dependency robustness across FTBQ versions; the in-quest reward-type configuration GUI is not yet wired up (see caveat below). |
| **Pehkui** | `pehkui` | Origin body-scale powers drive the Pehkui scale system so resizing renders and collides correctly. See the caveat below. |
| **Epic Fight** | `epicfight` | Origin scaling is applied to Epic Fight's custom entity renderer via a mixin, so scaled origins render correctly with Epic Fight installed. |
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
- **FTB Quests reward GUI is stubbed.** Granting/awarding origins via the
  tag-marker path works, but the in-quest reward-type configuration GUI is not
  yet wired up.
- **GeckoLib is not an active integration (roadmap).** NeoOrigins only probes
  for `geckolib`; the planned `AnimatedProjectileRenderer` has not shipped, so
  projectiles fall back to standard item rendering. Listed here so its absence
  from the table above isn't mistaken for an oversight.
- **Lossy datapack translation.** Some Origins/Apoli modifier types translate
  with reduced fidelity, and a handful of types are unsupported. These are
  logged at reload — check the log if an imported pack behaves unexpectedly.
