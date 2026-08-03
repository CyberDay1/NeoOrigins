---
title: KubeJS Integration
parent: "Scripting & Java"
nav_order: 1
---

# KubeJS Integration
{: .no_toc }

Drive NeoOrigins from KubeJS scripts: listen to origin, power, mob, and mount
events, invoke JS from JSON powers, and define whole power behaviors in
JavaScript without touching Java.

> **This page describes a Minecraft 1.21.1 feature.** The 26.1 and 26.2 builds
> ship none of it: no globals, no `js_*` power types, no `kubejs_callback`.
> Read [Availability](#availability) before authoring against anything below.

1. TOC
{:toc}

---

## Availability

**Everything on this page is Minecraft 1.21.1 only.** KubeJS support is a
**soft dependency** that ships in the NeoOrigins 2.1+ builds for Minecraft
1.21.1. The 26.1 and 26.2 builds do **not** bundle the integration: the
`NeoOrigins` / `NeoOriginsEvents` globals, the `neoorigins:kubejs_callback`
action and the `neoorigins:js_custom` / `neoorigins:js_active` power types are
all absent there, and no amount of KubeJS being installed brings them back.

Two different reasons sit behind that, and they are worth keeping straight.
Checked 2026-07-29: KubeJS's newest NeoForge release is `26.1.2-8.0.4+neoforge`,
published against Minecraft 26.1.2. On the 26.2 build the integration is therefore
blocked upstream, as there is no artifact to compile against. On the 26.1.2 build
there is one, and the integration is absent by our own choice: the port was
deferred, not prevented. Re-check KubeJS's release list before repeating either
claim.

That matters because an unregistered `type` is not a soft failure. A power file
whose `type` the build doesn't know is dropped **whole** at load: no error in
game, no partial behaviour, the power simply never appears on the origin. So a
`js_custom` power copied into a 26.x pack looks like it silently vanished. Gate
those files behind a pack that only ships on 1.21.1, or keep the behaviour in
Java for now.

On 1.21.1, when KubeJS is absent the whole subsystem short-circuits on a single
cached check, so there is no overhead and no class-loading risk for packs that
don't use it.

All scripts here are **server-side**. Origin, power, and mob state only changes
on the logical server. Put them in `server_scripts/` (or register from a server
lifecycle event). Everything is cleared and must be re-registered on
`/kubejs reload`; register inside a startup or server-loaded handler so stale
handlers don't pile up.

---

## Events

Events live on the global `NeoOriginsEvents` group:

```js
NeoOriginsEvents.originChanged(event => {
    console.log(`${event.player.name.string}: ${event.oldOriginId} -> ${event.newOriginId}`)
})
```

Event fields are exposed as KubeJS bean properties. `event.player` is the same
as the Java `getPlayer()`. The tables below list the properties available on
each event.

### Origin lifecycle

| Event | Fires when | Properties |
|-------|-----------|------------|
| `originChosen` | A player picks an origin on a layer for the **first** time | `player`, `layerId`, `originId` |
| `originChanged` | A player's active origin on a layer changes (first pick, `/set`, or reset) | `player`, `layerId`, `oldOriginId`*, `newOriginId`* |
| `evolutionTierChanged` | A player's evolution tier changes | `player`, `oldTier`, `newTier` |
| `evolutionDeclined` | A player declines an evolution prompt | `player` |

\* `oldOriginId` is `null` on a first-time selection; `newOriginId` is `null`
when the origin is cleared/reset. Evolution tiers are integers:
`0` base, `1` evolved, `2` ascended, `3` apex.

### Power lifecycle

| Event | Fires when | Properties |
|-------|-----------|------------|
| `powerGranted` | A power is granted (origin change, world load, re-grant sweep) | `player`, `powerId` |
| `powerRevoked` | A power is revoked (origin change away) | `player`, `powerId` |
| `powerActivated` | A keybind power **successfully** fires (after cooldown/cost is paid) | `player`, `powerId` |
| `powerTick` | Every server tick, per active power, per player | `player`, `powerId` |

`powerActivated` does **not** fire when a use is aborted by cooldown, hunger,
resource cost, or a no-op return. `powerTick` is high-frequency and only fires at
all when at least one JS listener is registered, but inside the listener,
assume it runs constantly and keep the body cheap.

### Mob origins

| Event | Fires when | Properties |
|-------|-----------|------------|
| `mobOriginAssigned` | A mob is assigned a mob-origin (command, egg, or spawn rule) | `mob`, `originId` |
| `mobOriginCleared` | A mob's mob-origin is cleared | `mob`, `previousOriginId` |

`mob` is a `LivingEntity`.

### Mounts & consent

| Event | Fires when | Properties |
|-------|-----------|------------|
| `mountRequested` | A mount-power consent prompt is sent | `requester`, `target` |
| `mountAccepted` | The target accepts the prompt | `requester`, `target` |
| `mountDeclined` | The target declines the prompt | `requester`, `target` |
| `mountStarted` | A player starts riding a target | `rider`, `vehicle`, `position` |
| `mountEnded` | A player ends a mount-power ride | `rider`, `vehicle` |

`position` is `"centered"` or `"shoulder"`. `mountEnded` only covers explicit
dismounts via the mount power. Vanilla dismount paths (jumping off, the vehicle
dying) are not currently reported.

---

## The `NeoOrigins` global

A `NeoOrigins` object is exposed to every script for registering callbacks and
JS-defined power behaviors.

### Callbacks

Register a function by id, then invoke it from any JSON action with the
`neoorigins:kubejs_callback` action type. The callback receives a `ServerPlayer`.

```js
NeoOrigins.registerCallback('mypack:shout', player => {
    player.tell('You triggered the power!')
})
```

| Method | Description |
|--------|-------------|
| `NeoOrigins.registerCallback(id, fn)` | Register `fn(player)` under `id`. Re-registering the same id overwrites. |
| `NeoOrigins.unregisterCallback(id)` | Remove a callback. |
| `NeoOrigins.hasCallback(id)` | `true` if a callback with that id is registered. |

Invoke it from JSON anywhere an entity action is accepted:

```json
{
  "type": "neoorigins:kubejs_callback",
  "id": "mypack:shout"
}
```

If no callback is registered for the id (KubeJS absent, or the script hasn't run
yet), the action is silently dropped with a debug log. It never errors.

`neoorigins:kubejs_callback` is likewise 1.21.1 only. On 26.1 / 26.2 the action
type is unknown, so it is skipped at parse time rather than at call time. The
surrounding power still loads, but that step of the action never runs.

### JS-defined powers

Two power types let a JSON power delegate its whole behavior to a JS handler,
keyed by `js_id`. This means new power behaviors are authorable from JS without
any Java registry mutation.

**1.21.1 only.** `neoorigins:js_custom` and `neoorigins:js_active` are not
registered in the 26.1 or 26.2 builds, so a power file using either type is
dropped whole at load there. See [Availability](#availability).

| Method | Description |
|--------|-------------|
| `NeoOrigins.registerPower(id, handler)` | Passive power: pairs with `neoorigins:js_custom`. |
| `NeoOrigins.registerActivePower(id, handler)` | Active (keybind) power: pairs with `neoorigins:js_active`. |
| `NeoOrigins.hasPower(id)` / `NeoOrigins.hasActivePower(id)` | Registration checks. |

The handler is a plain JS object; Rhino adapts it to the matching interface and
fills any hook you omit with a no-op.

#### Passive power: `neoorigins:js_custom`

```js
NeoOrigins.registerPower('mypack:slow_fall', {
    onGranted: player => player.tell('You gained slow fall!'),
    onTick: player => {
        if (player.deltaMovement.y < 0) {
            player.setDeltaMovement(player.deltaMovement.multiply(1, 0.9, 1))
        }
    },
    onRevoked: player => player.tell('You lost slow fall')
})
```

```json
{
  "type": "neoorigins:js_custom",
  "js_id": "mypack:slow_fall",
  "name": "Slow Fall",
  "description": "You drift gently downward"
}
```

Hooks (all optional): `onGranted(player)`, `onRevoked(player)`,
`onTick(player)`.

#### Active power: `neoorigins:js_active`

`onUse` returns a boolean: `true` means the power fired and the cooldown/hunger
cost should be paid; `false` is a no-op (nothing is consumed). This mirrors the
contract every other active power uses.

```js
NeoOrigins.registerActivePower('mypack:teleport_forward', {
    onUse: player => {
        let look = player.getForward()
        let target = player.position().add(look.scale(10))
        player.teleportTo(target.x, target.y, target.z)
        return true   // cooldown / hunger consumed
    },
    onGranted: player => player.tell('You gained the teleport power!')
})
```

```json
{
  "type": "neoorigins:js_active",
  "js_id": "mypack:teleport_forward",
  "name": "Blink",
  "description": "Teleport ten blocks forward",
  "cooldown_ticks": 40,
  "hunger_cost": 2
}
```

| Field | Default | Description |
|-------|---------|-------------|
| `js_id` | *(required)* | Id passed to `registerActivePower`. |
| `cooldown_ticks` | `20` | Ticks before the power can be used again. |
| `hunger_cost` | `0` | Hunger paid on a successful use. |

Hooks: `onUse(player) -> boolean` (required), `onGranted(player)` and
`onRevoked(player)` (optional).

---

## Reload behavior

`/kubejs reload` wipes every registered callback and JS power handler. Register
from a handler that runs on (re)load so your registrations come back:

```js
ServerEvents.loaded(event => {
    NeoOrigins.registerCallback('mypack:shout', player => player.tell('hi'))
    NeoOrigins.registerActivePower('mypack:dash', { onUse: p => { /* ... */ return true } })
})
```

Events registered on `NeoOriginsEvents` follow normal KubeJS reload rules and do
not need this treatment.
