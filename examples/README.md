# NeoOrigins Datapack Examples

This folder contains working example datapacks that show how to use NeoOrigins
features that are driven entirely from datapack JSON — no Java code required.

Modpack authors, addon authors, and server operators can copy any of these
folders into a world's `datapacks/` directory (or distribute them as a
standalone datapack) and they will work against the base NeoOrigins mod.

## Feature: Advancement-Based Origin Upgrades

Shipped in **v1.11.0**.

Any origin JSON can declare an `upgrades` list. When a player currently has
that origin on some layer and earns one of the listed advancements, NeoOrigins
swaps them to the target origin on the same layer — powers are revoked and
re-granted through the normal origin-change pipeline.

### Schema

Add an `upgrades` list to any origin JSON at
`data/<namespace>/origins/origins/<id>.json`:

```json
{
  "name": "...",
  "description": "...",
  "upgrades": [
    {
      "advancement": "minecraft:story/enter_the_nether",
      "origin": "neoorigins:strider",
      "announcement": "origin.example.strider.evolved"
    }
  ]
}
```

| Field          | Required | Notes                                                                                           |
|----------------|----------|-------------------------------------------------------------------------------------------------|
| `advancement`  | yes      | Namespaced advancement id. Any advancement — vanilla, modded, or datapack-defined — is allowed. |
| `origin`       | yes      | Target origin id. Must resolve to a loaded origin, otherwise the upgrade is skipped + logged.   |
| `announcement` | no       | Translation key sent to the player as a system message on upgrade. Omit for silent upgrades.    |

### Behaviour

- **Per-layer**: each of the player's layers is checked independently. The
  same advancement can drive different swaps on different layers (e.g. an
  origin-layer evolution and an unrelated class-layer promotion).
- **One swap per layer per advancement**: authors who want multi-stage chains
  should use distinct advancements (see `dragon_slayer_chain` for an example).
- **Announcement is a translation key**: ship the localised string in a
  resource pack or language file, or use literal strings if you don't care
  about localisation.
- **Existing progress is not revalidated on datapack reload**: if a player
  already earned the advancement before the upgrade rule was loaded, the
  swap does **not** fire retroactively. Players need to earn the advancement
  again, or a server operator can use `/origin set` manually.

## Examples in this folder

| Folder                  | What it does                                                                 |
|-------------------------|------------------------------------------------------------------------------|
| `nether_evolution/`     | Overrides Human so entering the Nether evolves you into a Strider.           |
| `dragon_slayer_chain/`  | Multi-stage chain: Human -> Revenant (first dragon fight) -> Voidwalker (kill). |
| `class_tier_up/`        | Class-layer variant: Explorer class promotes to Master Explorer on a chain of exploration advancements. |

Each subfolder is a complete datapack — drop the folder into
`<world>/datapacks/` and `/reload`, or zip it up for distribution.

## Installing

For world-scoped testing:

```
<instance>/saves/<world>/datapacks/<example-name>/
```

Then in-game:

```
/reload
/datapack list
```

To test an upgrade from scratch (if you already earned the advancement on a
test character):

```
/advancement revoke @s only minecraft:story/enter_the_nether
```

## Authoring tips

- **Overriding a mod origin**: datapacks can override mod-provided origin JSONs
  by placing a file at the same namespaced path. The `nether_evolution/`
  example demonstrates this — it overrides `neoorigins:human` with a version
  that adds an `upgrades` entry but keeps every other field identical.
- **Adding upgrades without overriding**: there is no way today to add an
  upgrade to an existing origin without full-override. If you only want to
  extend, copy the mod's JSON verbatim and append your `upgrades` entries.
  Keep the override in sync with mod updates.
- **Chaining**: each stage in a chain must exist as a loadable origin.
  `dragon_slayer_chain/` ships overrides for every step so the chain is
  self-contained.
- **Test your advancement ids**: use `/advancement grant @s only <id>` to
  verify the advancement id you wrote in the upgrade actually fires. Typos
  in the `advancement` field fail silently (no log) because the listener
  only runs when a known advancement is earned.
