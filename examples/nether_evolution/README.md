# Nether Evolution

**Smallest possible example of the `upgrades` feature.**

A player who is currently a Human and earns the vanilla
`minecraft:story/enter_the_nether` advancement — awarded the first time they
step through a Nether portal — is automatically converted to a Strider.

## How it works

This datapack overrides NeoOrigins' `human.json` origin. The only difference
from the mod's default is the added `upgrades` array at the bottom of the
file:

```json
"upgrades": [
  {
    "advancement": "minecraft:story/enter_the_nether",
    "origin": "neoorigins:strider",
    "announcement": "examples.nether_evolution.upgrade.strider"
  }
]
```

When the advancement fires, `AdvancementEvent.AdvancementEarnEvent` runs on
the server, finds the upgrade rule on the player's current origin, and
performs the swap through the normal `applyOriginPowers` path. All Human
powers are revoked, Strider powers are granted, and the client is re-synced.

## Testing

1. Place this folder at `<world>/datapacks/nether_evolution/`
2. `/reload`
3. `/datapack list` — confirm `[file/nether_evolution]` under enabled
4. Make a new character on the Human origin (or `/origin set @s neoorigins:origin neoorigins:human`)
5. If you have already earned the Nether advancement: `/advancement revoke @s only minecraft:story/enter_the_nether`
6. Walk through a Nether portal

Expected: you immediately switch to Strider, all Human powers are gone, and
Strider powers (lava immunity, etc.) are active.

## Localisation

The `announcement` field points at translation key
`examples.nether_evolution.upgrade.strider`. To get a user-facing message,
either:

- Bundle a resource pack with that key in a `.json` language file, or
- Change the field to a literal string and pass the literal directly, or
- Omit `announcement` entirely for a silent upgrade.
