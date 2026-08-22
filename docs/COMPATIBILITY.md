# NeoOrigins Mod Compatibility

NeoOrigins ships with built-in support for a number of other mods. Every
integration is a **soft dependency**: each is gated behind a mod-list check (or
runtime reflection), so none of these mods are required. If a mod is absent the
integration simply stays dormant; NeoOrigins runs normally without it.

If a prose doc disagrees with the code, the code wins.

---

## Datapack formats

| Mod / format | What NeoOrigins does | Notes |
|---|---|---|
| **Origins / Apoli** | Reads Origins-format origin and power datapacks and translates them into NeoOrigins powers at load time. | Most power types are supported directly; a few translate with reduced fidelity. Unsupported types are logged at reload and become no-ops rather than crashing the pack. Action and condition fields are accepted in both Apoli forms: a single object or an array (an array is an implicit all-of). Apoli's `command` action verb dispatches as `execute_command`, subject to the same command blacklist. |
| **Apugli / Apace** | Legacy power-type vocabularies are remapped to the 2.0 type names. | See [MIGRATION.md](MIGRATION.md) for the remap table and the known DSL gaps. |

You do not need any of these mods installed; NeoOrigins reads their pack format
natively. Drop an Origins pack into `originpacks/` and it loads.

---

## Gameplay integrations

| Mod | Mod id | What it adds |
|---|---|---|
| **Curios API** | `curios` | Umbrella-based conditions (e.g. `exposed_to_sun`) also scan worn Curios slots for an umbrella. Reflection-based, no hard dependency. |
| **Vampires Need Umbrellas** | `vampiresneedumbrellas` | An equipped umbrella shields the holder from both weather-damage conditions: `exposed_to_sun` (sun-burn origins) and `in_rain` (rain/water-damage origins like Wet Fur and True Hydrophobia). Every item in the `vampiresneedumbrellas` namespace is treated as an umbrella, detected in either hand or any Curios slot. |
| **Artifacts** | `artifacts` | The Artifacts umbrella (`artifacts:umbrella`) shields the holder from `exposed_to_sun` and `in_rain`, the same as a Vampires Need Umbrellas umbrella. Wired through the `neoorigins:umbrellas` item tag as an optional entry — no dependency, and the entry no-ops when Artifacts is absent. Datapacks can add their own umbrellas to that tag; see [Umbrella items](CONDITIONS.md#umbrella-items). |
| **Ars Nouveau** | `ars_nouveau` | Undead-type origins are healed (rather than harmed) by Ars Nouveau harm spells, mirroring vanilla undead behaviour. |
| **FTB Teams** | `ftbteams` | Players on the same or an allied FTB team are treated as trusted for mount consent: a teammate or ally can ride your mountable origin without sending a consent request. |
| **Open Parties & Claims** | `openpartiesandclaims` | Same as FTB Teams, but using OPAC party membership. |
| **FTB Quests** | `ftbquests` | Quests tagged `neoorigins_loot_pool_grant:<table_id>` grant a loot pool from that table on completion, routed through the same roll-and-grant pipeline as the `loot_pool_grant` power. |
| **FTB Ultimine** | `ftbultimine` | Powers the `neoorigins:ultimine` power: NeoOrigins registers a restriction handler so vein-mining is gated to players holding an active `ultimine` power. The integration is completely dormant unless a loaded pack defines a `neoorigins:ultimine` power; while no pack uses it, FTB Ultimine behaves exactly as vanilla. Once at least one `ultimine` power is loaded, vein-mining is restricted to players who hold one. Block count, tool requirement, and shape follow FTB Ultimine's own server config; the restriction API exposes no override for them. Compile-only soft dependency. |
| **Dragon Survival** | `dragonsurvival` | The `neoorigins:become_dragon` power drives Dragon Survival's own dragon state, so an origin can make its holder a DS dragon of a configured species and stage; DS supplies the actual traits, growth, abilities, altar economy and hunters. Ships with three built-in origins (Cave / Forest / Sea Dragon) gated behind `"required_mods": ["dragonsurvival"]`, so they only load and appear in the picker when DS is installed. Reflection-based, no hard dependency. See the caveat below. |
| **Pehkui** | `pehkui` | Origin body-scale powers drive the Pehkui scale system so resizing renders and collides correctly. See the caveat below. |
| **Epic Fight** | `epicfight` | Origin scaling is applied to Epic Fight's custom entity renderer via a mixin, so scaled origins render correctly with Epic Fight installed. |
| **Iron's Spells 'n Spellbooks** | `irons_spellbooks` | Three surfaces: the `neoorigins:cast_iron_spell` action casts an Iron's spell from an origin power; a `neoorigins:resource` power can back its bar with the player's Iron's mana pool (`"backing": "irons_spellbooks:mana"`); and `attribute_modifier` powers can modify Iron's custom attributes (max mana, spell power, cooldown reduction, …). Compile-only soft dependency (never bundled). See the full [Iron's Spells 'n Spellbooks](#irons-spells-n-spellbooks) section below. |
| **Modded attributes** | (any) | `attribute_modifier` powers can target attributes added by other mods (e.g. Iron's Spells, Apothic Attributes). Attribute IDs resolve with or without the `generic.`/`player.` prefix. For Iron's specifically, see the [Iron's Spells 'n Spellbooks](#irons-spells-n-spellbooks) section. |
| **Figura** | `figura` | Exposes a read-only `neoorigins` Lua global to Figura avatars, so a custom-avatar script can react to the wearer's origin, active powers, capabilities, and evolution tier (e.g. swap models per origin or per tier). Origins declare opaque model keys via the `figura_model` / `figura_models` datapack fields. Compile-only soft dependency; Figura only ever reads NeoOrigins state. Full reference: [FIGURA.md](FIGURA.md). |
| **Cold Sweat** | `cold_sweat` | Two author-facing primitives (the `neoorigins:modify_temperature` action writes a temperature trait, the `neoorigins:body_temperature` condition reads and compares one) plus a built-in resistance package: 18 origins ship hidden heat/cold resistances via `cold_sweat:*` attributes, so a Cold Sweat install makes them feel at home (or vulnerable) in the right biomes. Compile-only soft dependency (never bundled). See the full [Cold Sweat](#cold-sweat) section below. |

---

## Iron's Spells 'n Spellbooks

`irons_spellbooks` is a compile-only soft dependency; it is never bundled, and
nothing here is required. A pack that uses these surfaces runs fine without Iron's
installed: each degrades to a logged no-op rather than crashing. Where a whole
origin or power only makes sense with Iron's present, gate it with the top-level
`"required_mods": ["irons_spellbooks"]` array so it neither loads nor shows up in
the picker when the mod is absent. (`required_mods` is an all-of gate: every id
listed must be loaded.)

Three integration surfaces are available.

### 1. Cast an Iron's spell: `neoorigins:cast_iron_spell` action

Casts an Iron's spell as the player from an origin power: pick the spell id, level,
and `instant`/`channel` mode. By default the cast is free from Iron's mana (the
cost is charged on the NeoOrigins power); set `consume_mana: true` to draw from and
gate on the player's Iron's mana pool instead. Without Iron's installed the action
is a logged no-op. See [ACTIONS.md](ACTIONS.md#neooriginscast_iron_spell) for the
full field list.

### 2. Mana-backed resource bar: `neoorigins:resource` with `backing`

Set `"backing": "irons_spellbooks:mana"` on a `neoorigins:resource` power to make
its bar read from and write to the player's Iron's mana pool rather than
NeoOrigins' own per-player store. The mana pool stays authoritative:

- The bar auto-scales. Omit `min`/`max`. It uses `min = 0` and `max =` Iron's
  **live** max mana (the `irons_spellbooks:max_mana` attribute, which moves with
  gear, level, and effects), so Iron's own bar and this one fill identically.
- Writes are additive-only: `change_resource`, `regen_rate`, and `resource_cost`
  each add or subtract a delta; NeoOrigins never overwrites mana absolutely, so it
  won't fight Iron's regen and casting bookkeeping. Drains floor-clamp at 0.
- Without Iron's installed the bar reads empty and writes do nothing (logged once);
  it does **not** fall back to an internal value.

See the `backing` docs under
[POWER_TYPES.md → `neoorigins:resource`](POWER_TYPES.md#neooriginsresource) for the
full behaviour.

### 3. Modify Iron's attributes: `neoorigins:attribute_modifier`

`neoorigins:attribute_modifier` (and the auto-translated Apoli `origins:attribute`
/ `origins:modify_attribute`) resolve any registered attribute id from the game
registry, so they can target Iron's custom attributes directly: no NeoOrigins-side
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

**The operation and scale differ per attribute: match Iron's own scaling.**
`max_mana` is a flat add; `spell_power`, `mana_regen`, and `spell_resist` sit on a
base of `1.0`, so an `add_value` amount is a fractional bonus (`0.5` ≈ +50%);
`cooldown_reduction` and `cast_time_reduction` are `0`-based fractions in the range
0–1. There are **no** separate per-school (fire/ice/…) spell-power attributes in
3.14.0; schools are handled by school-types layered over the single `spell_power`
/ `spell_resist` attributes, so those eight are the complete list.

Referencing an `irons_spellbooks:*` attribute on a server without Iron's installed
logs one warning per grant and applies nothing; the power still loads. Gate the
origin with `"required_mods": ["irons_spellbooks"]` if it should only exist when
Iron's is present.

**Example (+100 flat max mana):**
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

**Example (+25% spell power, fractional, base 1.0):**
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

## Cold Sweat

`cold_sweat` is a compile-only soft dependency; it is never bundled, and nothing
here is required. Every Cold-Sweat-typed reference is isolated in a single bridge
class that is only loaded once Cold Sweat is confirmed present, so a pack that uses
these surfaces runs fine without it installed: the action degrades to a logged
no-op, the condition reads false, and the built-in resistances simply don't load.
Where a power only makes sense with Cold Sweat present, gate it with
`"required_mods": ["cold_sweat"]` so it neither loads nor shows up in the picker
when the mod is absent.

Three integration surfaces are available.

### 1. Write a temperature: `neoorigins:modify_temperature` action

Changes one of the player's Cold Sweat temperature traits from an origin power:
pick the `trait`, an `amount`, and an `operation` (`add` the delta, default, or
`set` an absolute value). `core` is the player's body temperature on a roughly
−100 (freezing death) … +100 (burning death) scale, 0 being neutral; positive
amounts warm, negative amounts cool. Without Cold Sweat installed the action is a
logged no-op. See [ACTIONS.md](ACTIONS.md#neooriginsmodify_temperature) for the
full field list.

### 2. Read a temperature: `neoorigins:body_temperature` condition

Reads a Cold Sweat temperature trait and compares it against `compare_to` with a
`comparison` operator, so a power can react to the player getting dangerously hot
or cold (e.g. `trait: core, compare_to: 50, comparison: ">="` fires once the body
is overheating; `compare_to: -50, comparison: "<="` catches dangerous cold). It is
named `body_temperature` rather than `temperature` because `neoorigins:temperature`
is already the biome base-temperature condition. Without Cold Sweat installed the
condition fails closed, always false, with one logged warning. See
[CONDITIONS.md](CONDITIONS.md#neooriginsbody_temperature) for the full field list.

Both surfaces share the same trait vocabulary: `core`, `base`, and `world` are
temperature readings (`core` is the body, `base`/`world` the ambient scale);
`heat_resistance`, `cold_resistance`, `heat_dampening`, and `cold_dampening` are
the resistance traits (roughly 0..1); `freezing_point`/`burning_point` are the
body-temperature thresholds; `rate` scales how fast body temp changes.

### 3. Modify Cold Sweat attributes: `neoorigins:attribute_modifier`

`neoorigins:attribute_modifier` resolves any registered attribute id, so it can
target Cold Sweat's resistance attributes directly. Point the power's `attribute`
field at one of the four ids below.

| Attribute id | What `add_value` does | Scale |
|---|---|---|
| `cold_sweat:cold_resistance` | Insulates against cold ambient temperature | ~0..1 (`1.0` ≈ immune to cold) |
| `cold_sweat:heat_resistance` | Insulates against hot ambient temperature | ~0..1 (`1.0` ≈ immune to heat) |
| `cold_sweat:cold_dampening` | Scales how strongly cold moves body temp | ~−1..1 (negative = more vulnerable to cold) |
| `cold_sweat:heat_dampening` | Scales how strongly heat moves body temp | ~−1..1 (negative = more vulnerable to heat) |

A positive resistance protects; a negative dampening value makes the origin *more*
affected by that temperature; the built-in package below uses both to give each
origin a home climate and a weak one.

**Built-in resistance package.** Eighteen origins ship hidden, Cold-Sweat-gated
`attribute_modifier` powers so they feel adapted to their element when Cold Sweat
is installed (and are unaffected when it isn't):

- **Heat-adapted**: Blazeling and Strider (`+1.0` heat resist, `−0.5` cold
  dampening), Piglin (`+0.75` heat resist, `−0.25` cold dampening), Cinderborn and
  Fire Mage (`+0.5` heat resist, `−0.25` cold dampening), Cave Dragon (`+0.5` heat
  resist).
- **Cold-adapted**: Frostborn (`+1.0` cold resist, `−0.5` heat dampening), Sea
  Dragon (`+0.5` cold resist, `−0.25` heat dampening), Abyssal, Kraken, and Merling
  (`+0.5` cold resist), Enderian, Enderite, Avian, Windwalker, Sculkborn, Warden,
  and Siren (`+0.25` cold resist).

These powers are `hidden` and gated with `"required_mods": ["cold_sweat"]`, so they
never appear in the power list and never load on a server without Cold Sweat.

---

## Recipe viewers

| Mod | Mod id | What it adds |
|---|---|---|
| **JEI** | `jei` | Adds an information panel for the Orb of Origin item. |
| **REI** | `roughlyenoughitems` | Adds the Orb of Origin to the item list. |

---

## Tooltips / probes

| Mod | Mod id | What it adds |
|---|---|---|
| **Jade** | `jade` | Shows the looked-at entity's NeoOrigins origin in the tooltip/probe overlay. |
| **AppleSkin** | `appleskin` | Makes the food tooltip and the held-food HUD preview show what a `modify_food_nutrition` power will actually give, rather than the item's vanilla value. |

**AppleSkin, in more detail.** `neoorigins:modify_food_nutrition` never
rewrites the item's food data. It lets vanilla eat the item and then corrects
the player's hunger and saturation server-side, so the numbers only become
right at the moment of eating. AppleSkin builds its preview by reading the food
data off the stack on the client, where the override does not exist, so without
this bridge every diet origin was previewed at vanilla values. The bridge
answers AppleSkin's own `FoodValuesEvent` with the origin-adjusted figures and
leaves the vanilla figure in place, so AppleSkin still draws its usual
struck-through comparison next to ours. Because that event fires on the client,
the configured overrides are synced down alongside the existing active-power
sync. Nothing here classloads when AppleSkin is absent.

---

## Caveats and known gaps

- **Pehkui scaling is last-write-wins.** NeoOrigins sets the Pehkui scale
  directly rather than composing with it, so an origin scale power and a manual
  `/scale` command will clobber one another; whichever ran last takes effect.
  There is no additive/multiplicative composition between the two.
- **FTB Quests grants loot pools, not origins.** The `neoorigins_loot_pool_grant`
  tag rolls a loot table and deposits the items; it does not assign an origin
  directly. Use a loot table that yields the relevant items, or pair the grant
  with an origin-granting power.
- **GeckoLib is not an active integration (roadmap).** NeoOrigins only probes
  for `geckolib`; the planned `AnimatedProjectileRenderer` has not shipped, so
  projectiles fall back to standard item rendering. Listed here so its absence
  from the table above isn't mistaken for an oversight.
- **Dragon Survival binds reflectively.** DS exposes no public addon API, so
  the bridge resolves its internal classes and methods by name at runtime. If a
  future DS release renames them, `become_dragon` logs one warning and stops
  transforming players (everything else keeps working). Report the DS version
  and the bridge gets re-pointed.
- **Lossy datapack translation.** Some Origins/Apoli modifier types translate
  with reduced fidelity, and a handful of types are unsupported. These are
  logged at reload. Check the log if an imported pack behaves unexpectedly.
