---
title: Origin-Gated Recipes
parent: "DSL Reference"
nav_order: 7
---

# Origin-Gated Recipes

*Added in 2.2.0.*

Pack authors can gate crafting recipes on a player's current origin / power
state. Gated recipes still load and still appear in the recipe book; they
simply refuse to `match` while the crafting player fails any gate, so the
result slot stays empty.

This is a **runtime** per-player gate; there is no datapack reload required
when a player switches origin via the Orb of Origin, the picker, or admin
commands. The gate is re-evaluated every time the crafting grid changes.

> **Scope.** Only crafting-table / 2x2 inventory recipes are supported by
> the wrapper today. Cooking variants (smelting, blasting, smoking,
> campfire) use different menus and call paths and are not yet wrapped.
> See "Future work" below.

## Recipe form

Wrap any vanilla **crafting** recipe (shaped, shapeless, or a third-party
crafting serializer) by placing it under the `inner` key and listing one or
more `gates`:

```json
{
  "type":  "neoorigins:origin_gated_crafting",
  "gates": [ /* OriginGate, OriginGate, ... */ ],
  "inner": { /* any RecipeType.CRAFTING recipe */ }
}
```

Gates are evaluated with AND semantics: every gate in the list must pass.
For OR semantics, ship multiple recipes with the same output and one gate
each.

## Gate shapes

### `neoorigins:has_origin`

Passes if the player has the given origin in **any** layer.

```json
{ "type": "neoorigins:has_origin", "origin": "neoorigins:human" }
```

### `neoorigins:has_power`

Passes if the player currently has the given power (origin-granted,
layer-granted, dimension-permitted, or dynamically granted via the
`grant_power` action).

```json
{ "type": "neoorigins:has_power", "power": "neoorigins:merling_water_breathing" }
```

### `neoorigins:in_layer`

Passes if the player's selection for the given layer matches `origin`.
Use this when you want the gate to be specific to one layer (e.g. a class
layer that overlays an origin layer).

```json
{
  "type":   "neoorigins:in_layer",
  "layer":  "neoorigins:origin",
  "origin": "neoorigins:avian"
}
```

## Examples

See `data/neoorigins/recipe_examples/` in the mod jar for one example per
gate shape. The `recipe_examples/` folder is intentionally outside the
vanilla `recipe/` scan path, so the examples ship as in-tree documentation
only. Copy any of them into your own `data/<ns>/recipe/` to activate.

## Behavior notes

- **Recipe book**: gated recipes are marked `isSpecial()`, which suppresses
  the autofill "completable" highlight. The recipe is still visible in the
  book; clicking it just won't populate a result. This avoids teasing
  players with results they can't actually take.
- **JEI / REI**: gated recipes show up like any other crafting recipe.
  Players that try to craft them without the right origin will see an
  empty result slot.
- **Servers**: the gate is evaluated on the server side during the
  `slotChangedCraftingGrid` call. The recipe serializer is registered on
  both sides via `BuiltInRegistries.RECIPE_SERIALIZER`, so dedicated
  servers and integrated servers both load gated recipes correctly, and
  clients deserialize the recipe-book sync payload without warnings.
- **Hopper / automation**: hoppers feeding a crafter block do not provide
  a player context. Gated recipes will **not** craft via the Crafter
  block; the gate falls back to "deny" when no player is present. This is
  by design; gated recipes are a *player capability* check, not a
  recipe-book filter.

## Future work

- Cooking variants. Wrapping `AbstractCookingRecipe` would require a
  similar context mixin on `AbstractFurnaceBlockEntity#serverTick` and a
  policy decision about "which player owns the furnace?" (last user?
  area owner?). Open for backlog discussion in 2.2.
- `NOT` / `OR` composite gate. Today multi-gate is AND only.
