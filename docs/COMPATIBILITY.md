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
| **Iron's Spells 'n Spellbooks** | `irons_spellbooks` | Three surfaces: the `neoorigins:cast_iron_spell` action casts an Iron's spell from an origin power; a `neoorigins:resource` power can back its bar with the player's Iron's mana pool (`"backing": "irons_spellbooks:mana"`); and `attribute_modifier` powers can modify Iron's custom attributes (max mana, spell power, cooldown reduction, …). Compile-only soft dependency (never bundled). See the full [Iron's Spells 'n Spellbooks](#irons-spells-n-spellbooks) section below. |
| **Modded attributes** | (any) | `attribute_modifier` powers can target attributes added by other mods (e.g. Iron's Spells, Apothic Attributes). Attribute IDs resolve with or without the `generic.`/`player.` prefix. For Iron's specifically, see the [Iron's Spells 'n Spellbooks](#irons-spells-n-spellbooks) section. |
| **Figura** | `figura` | Exposes a read-only `neoorigins` Lua global to Figura avatars, so a custom-avatar script can react to the wearer's origin, active powers, capabilities, and evolution tier (e.g. swap models per origin or per tier). Origins declare opaque model keys via the `figura_model` / `figura_models` datapack fields. Compile-only soft dependency; Figura only ever reads NeoOrigins state. Full reference: [FIGURA.md](FIGURA.md). |

---

## Iron's Spells 'n Spellbooks

`irons_spellbooks` is a compile-only soft dependency — it is never bundled, and
nothing here is required. A pack that uses these surfaces runs fine without Iron's
installed: each degrades to a logged no-op rather than crashing. Where a whole
origin or power only makes sense with Iron's present, gate it with the top-level
`"required_mods": ["irons_spellbooks"]` array so it neither loads nor shows up in
the picker when the mod is absent. (`required_mods` is an all-of gate: every id
listed must be loaded.)

Three integration surfaces are available.

### 1. Cast an Iron's spell — `neoorigins:cast_iron_spell` action

Casts an Iron's spell as the player from an origin power: pick the spell id, level,
and `instant`/`channel` mode. By default the cast is free from Iron's mana (the
cost is charged on the NeoOrigins power); set `consume_mana: true` to draw from and
gate on the player's Iron's mana pool instead. Without Iron's installed the action
is a logged no-op. See [ACTIONS.md](ACTIONS.md#neooriginscast_iron_spell) for the
full field list.

### 2. Mana-backed resource bar — `neoorigins:resource` with `backing`

Set `"backing": "irons_spellbooks:mana"` on a `neoorigins:resource` power to make
its bar read from and write to the player's Iron's mana pool rather than
NeoOrigins' own per-player store. The mana pool stays authoritative:

- The bar auto-scales — omit `min`/`max`. It uses `min = 0` and `max =` Iron's
  **live** max mana (the `irons_spellbooks:max_mana` attribute, which moves with
  gear, level, and effects), so Iron's own bar and this one fill identically.
- Writes are additive-only: `change_resource`, `regen_rate`, and `resource_cost`
  each add or subtract a delta; NeoOrigins never overwrites mana absolutely, so it
  won't fight Iron's regen and casting bookkeeping. Drains floor-clamp at 0.
- Without Iron's installed the bar reads empty and writes do nothing (logged once)
  — it does **not** fall back to an internal value.

See the `backing` docs under
[POWER_TYPES.md → `neoorigins:resource`](POWER_TYPES.md#neooriginsresource) for the
full behaviour.

### 3. Modify Iron's attributes — `neoorigins:attribute_modifier`

`neoorigins:attribute_modifier` (and the auto-translated Apoli `origins:attribute`
/ `origins:modify_attribute`) resolve any registered attribute id from the game
registry, so they can target Iron's custom attributes directly — no NeoOrigins-side
list to opt into. Point the power's `attribute` field at one of the eight ids below.

| Attribute id | Base | What `add_value` does | Scale |
|---|---|---|---|
| `irons_spellbooks:max_mana` | — | Flat add to mana capacity | Flat points (e.g. `100.0` = +100 max mana) |
| `irons_spellbooks:mana_regen` | 1.0 | Mana regen multiplier | Fractional bonus (`0.5` ≈ +50% regen) |
| `irons_spellbooks:spell_power` | 1.0 | Overall spell power multiplier | Fractional bonus (`0.25` ≈ +25%) |
| `irons_spellbooks:spell_resist` | 1.0 | Incoming magic resistance | Fractional bonus (`0.2` ≈ +20% resist) |
| `irons_spellbooks:cooldown_reduction` | 0 | Spell cooldown reduction | Fraction 0–1 (`0.15` = 15% shorter cooldowns) |
| `irons_spellbooks:cast_time_reduction` | 0 | Cast-time reduction | Fraction 0–1 (`0.15` = 15% faster casts) |
| `irons_spellbooks:casting_movespeed` | — | Movement speed while casting | Movement-speed units |
| `irons_spellbooks:summon_damage` | — | Summoned-mob damage | Damage units |

**The operation and scale differ per attribute — match Iron's own scaling.**
`max_mana` is a flat add; `spell_power`, `mana_regen`, and `spell_resist` sit on a
base of `1.0`, so an `add_value` amount is a fractional bonus (`0.5` ≈ +50%);
`cooldown_reduction` and `cast_time_reduction` are `0`-based fractions in the range
0–1. There are **no** separate per-school (fire/ice/…) spell-power attributes in
3.14.0 — schools are handled by school-types layered over the single `spell_power`
/ `spell_resist` attributes, so those eight are the complete list.

Referencing an `irons_spellbooks:*` attribute on a server without Iron's installed
logs one warning per grant and applies nothing — the power still loads. Gate the
origin with `"required_mods": ["irons_spellbooks"]` if it should only exist when
Iron's is present.

**Example — +100 flat max mana:**
```json
{
  "type": "neoorigins:attribute_modifier",
  "attribute": "irons_spellbooks:max_mana",
  "amount": 100.0,
  "operation": "add_value",
  "name": "Deep Well",
  "description": "A larger mana pool."
}
```

**Example — +25% spell power (fractional, base 1.0):**
```json
{
  "type": "neoorigins:attribute_modifier",
  "attribute": "irons_spellbooks:spell_power",
  "amount": 0.25,
  "operation": "add_value",
  "name": "Arcane Focus",
  "description": "Spells hit 25% harder."
}
```

Both examples belong in an origin gated with
`"required_mods": ["irons_spellbooks"]` so they only apply where the attributes
exist.

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
