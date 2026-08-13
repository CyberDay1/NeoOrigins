# KubeJS Integration

Drive NeoOrigins from KubeJS scripts: listen to origin, power, mob, and mount
events, invoke JS from JSON powers, and define whole power behaviors in
JavaScript without touching Java.

---

## Availability

KubeJS support is a **soft dependency**. It ships in the NeoOrigins 2.1+ builds
for Minecraft 1.21.1 and, from 2.2.24, in the Minecraft 26.1 build as well. The
**26.2 build does not bundle it**: the `NeoOrigins` / `NeoOriginsEvents` globals,
the `neoorigins:kubejs_callback` action and the `neoorigins:js_custom` /
`neoorigins:js_active` power types are all absent there, and no amount of KubeJS
being installed brings them back.

The reason is upstream, not a choice of ours. Checked 2026-08-09: across all 323
of KubeJS's published NeoForge releases the highest Minecraft version is 26.1.2,
so on 26.2 there is simply no artifact to compile against. Re-check KubeJS's
release list before repeating that claim.

On 26.1, KubeJS `8.0.4` itself requires NeoForge `26.1.2.84` or newer, so a pack
on an older 26.1 loader has to move up before KubeJS will load at all. That is
KubeJS's floor, not ours: NeoOrigins still accepts any 26.1.

The two shipping branches are on different KubeJS major lines. 1.21.1 builds
against the 7.x line, 26.1 against `26.1.2-8.0.4`. The API break between those
lines lands inside our plugin entrypoint rather than on any surface a script can
see, so scripts move across unchanged, with one exception worth knowing about.

`registerPower` hands Rhino a plain JS object to adapt to an interface whose
hooks are **all** optional. Rhino `2101.2.7-build.81`, which the 7.x KubeJS
builds pull in, will not adapt an object literal to an interface that has no
required method, so on 1.21.1 `registerPower` throws `Can't find method
...registerPower(string,object)`. Dropping Rhino `2101.2.8-build.91` or newer
into the pack fixes it. 26.1 is unaffected, because KubeJS 8.0.4 already requires
that Rhino build. `registerActivePower` works on both lines either way: its
`onUse` is required, and one required method is all the adapter needs.

The 26.2 gap matters because an unregistered `type` is not a soft failure. A
power file whose `type` the build doesn't know is dropped **whole** at load: no
error in game, no partial behaviour, the power simply never appears on the
origin. So a `js_custom` power copied into a 26.2 pack looks like it silently
vanished. Gate those files behind a pack that doesn't ship on 26.2, or keep the
behaviour in Java there.

When KubeJS is absent the whole subsystem short-circuits on a single cached
check, so there is no overhead and no class-loading risk for packs that don't
use it.

All scripts here are **server-side**: origin, power, and mob state only changes
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

Event fields are exposed as KubeJS bean properties; `event.player` is the same
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
dismounts via the mount power; vanilla dismount paths (jumping off, the vehicle
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
yet), the action is silently dropped with a debug log; it never errors.

`neoorigins:kubejs_callback` is unavailable on 26.2. There the action type is
unknown, so it is skipped at parse time rather than at call time; the surrounding
power still loads, but that step of the action never runs.

### JS-defined powers

Two power types let a JSON power delegate its whole behavior to a JS handler,
keyed by `js_id`. This means new power behaviors are authorable from JS without
any Java registry mutation.

**Not on 26.2.** `neoorigins:js_custom` and `neoorigins:js_active` are not
registered in the 26.2 build, so a power file using either type is dropped whole
at load there. See [Availability](#availability).

| Method | Description |
|--------|-------------|
| `NeoOrigins.registerPower(id, handler)` | Passive power. Pairs with `neoorigins:js_custom`. |
| `NeoOrigins.registerActivePower(id, handler)` | Active (keybind) power. Pairs with `neoorigins:js_active`. |
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
