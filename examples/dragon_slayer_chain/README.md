# Dragon Slayer Chain

**Multi-stage upgrade chain** driven by two separate advancements.

- A Human who enters The End becomes a **Revenant** (having touched the realm
  between worlds).
- A Revenant who kills the Ender Dragon ascends to **Voidwalker** (having
  consumed its power).

Humans and Voidwalkers have no further upgrade in this datapack — the chain
is a closed three-stage progression.

## How chains work

Chains are expressed as independent `upgrades` entries on each intermediate
origin. Each entry points at the **next** origin in the chain plus the
advancement that triggers the transition. `AdvancementEvent.AdvancementEarnEvent`
fires per-advancement, so nothing fires cascading logic — each stage waits
for its own advancement to be earned.

```
   Human
     | (minecraft:story/enter_the_end)
     v
   Revenant
     | (minecraft:end/kill_dragon)
     v
   Voidwalker
```

## File layout

Two origin overrides (the mod ships both origins — we replace them with
versions that include the relevant `upgrades` entry):

```
dragon_slayer_chain/
├── pack.mcmeta
├── data/neoorigins/origins/origins/
│   ├── human.json      (override: adds upgrade -> revenant on enter_the_end)
│   └── revenant.json   (override: adds upgrade -> voidwalker on kill_dragon)
└── README.md
```

Voidwalker does not need an override — it is the terminal stage.

## Testing

1. Install: `<world>/datapacks/dragon_slayer_chain/`
2. `/reload`
3. Start as Human
4. Throw an Ender Eye / build a portal / enter the End → you become **Revenant**
   mid-teleport.
5. Kill the Ender Dragon → you become **Voidwalker** while the credits roll.

To retest without restarting the world:

```
/origin set @s neoorigins:origin neoorigins:human
/advancement revoke @s only minecraft:story/enter_the_end
/advancement revoke @s only minecraft:end/kill_dragon
```

## Design notes

- **Not a tree, a chain**: each origin's `upgrades` list describes outgoing
  edges only. If you want branching (e.g. "Kill the dragon → Voidwalker OR
  beat the Wither → Revenant"), add multiple entries with different
  advancements on the same origin. Whichever fires first wins per layer.
- **No rollback**: once upgraded, a player stays on the new origin. There is
  no "revoke advancement → revert origin" flow. If you want this, write a
  datapack that listens for revocation in its own function and uses
  `/origin set` to reset the player.
- **Works across restarts**: the upgrades feature is stateless — once
  upgraded, the player's origin data on disk reflects the new origin
  permanently, so no server-side bookkeeping is needed.
