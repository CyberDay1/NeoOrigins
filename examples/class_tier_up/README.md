# Class Tier Up

**Upgrade on the `neoorigins:class` layer**, not the origin layer.

The same `upgrades` feature works on any layer. This example promotes an
Explorer class to a Scout class after the player earns Minecraft's
`adventure/adventuring_time` advancement (visit all 40 biomes).

## Why this matters

Most examples you'll see use the origin layer (`neoorigins:origin`). This
one uses the class layer (`neoorigins:class`) — the upgrade handler iterates
every layer the player has, so nothing changes in your datapack syntax;
you just point the upgrade at a class rather than an origin, and the swap
runs on whatever layer currently holds the source origin.

This means you can ship a single datapack that drives upgrades on both
layers simultaneously — a player who is `neoorigins:human` + `neoorigins:class_explorer`
can evolve on the origin layer (Human → Strider on Nether entry) AND on
the class layer (Explorer → Scout on Adventuring Time) — independently,
with no interference.

## Testing

1. Install: `<world>/datapacks/class_tier_up/`
2. `/reload`
3. `/origin set @s neoorigins:class neoorigins:class_explorer`
4. For fast testing: `/advancement grant @s only minecraft:adventure/adventuring_time`
   (normally you'd need to actually visit 40 biomes)

Expected: your class immediately changes from Explorer to Scout. Explorer
powers (kit, clock, maps, stamina) revoked; Scout powers (night vision,
speed, no-fall) granted.

## Caveat — `adventuring_time` is a big advancement

`minecraft:adventure/adventuring_time` requires visiting all 40 vanilla
biomes. It's a long-haul late-game advancement. For a realistic player
progression you might prefer something earlier like
`minecraft:adventure/sleep_in_bed` or a custom advancement your pack
provides. Swap the `advancement` field in the JSON if you want a different
trigger.
