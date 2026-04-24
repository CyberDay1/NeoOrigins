# NeoOrigins Java API

For mods that want to integrate with NeoOrigins — check if a player has a
particular power, listen for origin changes, register a custom power type,
or exempt summoned minions from their own logic.

> Pack authors: this doc isn't for you. See [API.md](API.md) for the
> datapack-facing reference.

---

## Stability contract

Types under `com.cyberday1.neoorigins.api.**` follow semver:

| Release | What can change |
|---|---|
| `3.0.0` (major) | Any breaking change. Deprecations honoured for at least one minor cycle beforehand. |
| `2.x.0` (minor) | Additive only — new methods, new power types, new events. Existing signatures stable. |
| `2.0.x` (patch) | Bug fixes only. No API surface changes. |

Types under `service/`, `event/`, `power/builtin/`, `compat/`, `mixin/`,
`network/` are **internal**. They can change between patch releases. Don't
import them from your mod.

If an integration you need isn't available through `api/`, open an issue —
we'll promote the internal method to API rather than ask you to import a
service class directly.

---

## Dependency setup

`build.gradle`:

```gradle
repositories {
    maven { url "https://maven.neoforged.net/releases" }
    // NeoOrigins publishes to GitHub Packages + Modrinth.
    // Adjust the URL to your preferred mirror.
}

dependencies {
    // Compile against a stable minor release; runtime will use whatever
    // version of NeoOrigins the user has installed ≥ the declared version.
    implementation "com.cyberday1:neoorigins:2.0.0"
}
```

`neoforge.mods.toml`:

```toml
[[dependencies.your_mod]]
    modId = "neoorigins"
    type = "optional"   # or "required"
    versionRange = "[2.0.0,3.0.0)"
    ordering = "AFTER"
    side = "BOTH"
```

Use `optional` unless your mod is useless without NeoOrigins — gives
players the choice. Check with `ModList.get().isLoaded("neoorigins")`
before calling into the API.

---

## Entry point: `NeoOriginsAPI`

The preferred way to call into NeoOrigins.

```java
import com.cyberday1.neoorigins.api.NeoOriginsAPI;

// Does this player have the Aquatic origin's Water Breathing power?
if (NeoOriginsAPI.hasCapability(player, "water_breathing")) { ... }

// Exempt our own damage code from hitting tracked minions of their owner:
if (NeoOriginsAPI.isMinionOf(targetEntity, sourcePlayer)) return;

// Iterate all SummonMinionPower configs on the player:
NeoOriginsAPI.forEachOfType(player, SummonMinionPower.class, cfg -> {
    int maxCount = cfg.maxCount();
    // ...
});
```

Full method list:

| Method | Purpose |
|---|---|
| `powers(player)` | All active power holders. |
| `has(player, PowerClass, filter)` | True if player has at least one matching power. |
| `forEachOfType(player, PowerClass, visitor)` | Iterate configs of the given type. |
| `hasCapability(player, tag)` | True if the capability tag is emitted (shared vocabulary with client-side effect layers). |
| `summonerOf(entity)` | Reverse-lookup a summoned minion's owner. |
| `isMinionOf(entity, summoner)` | Cheap check: is this entity summoner's minion? |
| `isAnyMinion(entity)` | Cheap check: is this entity anyone's tracked minion? |

All methods are server-thread safe. Client-side use is read-only; mutating
power state from the client is undefined.

---

## Events (`api/event/`)

Listen on the NeoForge event bus:

```java
@EventBusSubscriber(modid = YourMod.MOD_ID)
public class YourOriginListener {
    @SubscribeEvent
    public static void onOriginChanged(OriginChangedEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        Identifier newOrigin = event.getNewOrigin();
        // React to the player picking a new origin.
    }
}
```

| Event | When it fires |
|---|---|
| `OriginChangedEvent` | Player's origin changes. Cancellable — cancelling prevents the set. |
| `OriginsLoadedEvent` | All origin JSONs finished parsing (datapack reload). |
| `PowerGrantedEvent` | A power was just granted to a player. |
| `PowerRevokedEvent` | A power was just revoked. |

All events carry the `ServerPlayer` via `getEntity()` plus event-specific
identifiers. See the Javadoc on each event class.

---

## Custom power types

Extend `com.cyberday1.neoorigins.api.power.PowerType<C>` and register it
in your mod's registration phase.

```java
public class MyBoostPower extends PowerType<MyBoostPower.Config> {
    public record Config(double amount, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("amount").forGetter(Config::amount),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(i, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        // Called every tick while the player has this power granted.
    }
}
```

Register during `RegisterEvent` for `PowerType` at your mod's
initialisation:

```java
@SubscribeEvent
public static void onRegister(RegisterEvent event) {
    event.register(NeoOriginsAPI.POWER_TYPE_REGISTRY_KEY,
        helper -> helper.register(
            ResourceLocation.fromNamespaceAndPath("mymod", "my_boost"),
            new MyBoostPower()));
}
```

Pack authors can now use `"type": "mymod:my_boost"` in their power JSONs.

### Lifecycle hooks

| Hook | When it fires |
|---|---|
| `onGranted(player, config)` | Power was just added to the player's active set. |
| `onRevoked(player, config)` | Power was just removed. |
| `onTick(player, config)` | Every server tick while granted. Keep this cheap. |
| `onLogin(player, config)` | Player logged in with this power. Defaults to calling `onGranted`. |
| `onRespawn(player, config)` | Player respawned with this power. Defaults to calling `onGranted`. |
| `onHit(player, amount)` | Player took damage. Reaction hook. |

> Idempotency note: `onLogin` and `onRespawn` default to invoking
> `onGranted`. If your implementation registers a listener or adds an
> attribute modifier in `onGranted`, make sure it's safe to run multiple
> times — use modifier UUIDs and removal-before-add to avoid stacking.
> See `feedback_powertype_onGranted_idempotent` for the canonical pattern.

---

## Origin data model (`api/origin/`)

Read-only data types representing loaded origin JSON.

| Type | Purpose |
|---|---|
| `Origin` | One origin record (name, description, impact, icon, powers). |
| `OriginLayer` | One picker layer (list of origin IDs, name, order). |
| `Impact` | Enum: `NONE`, `LOW`, `MEDIUM`, `HIGH`. |
| `OriginUpgrade` | Upgrade condition (vanilla advancement) that migrates one origin to another. |
| `ConditionedOrigin` | Wraps an origin with a predicate determining availability. |

Typical read:

```java
Origin current = NeoOriginsAPI.currentOrigin(player, "neoorigins:origin");
if (current != null && current.impact() == Impact.HIGH) { ... }
```

---

## Common integration patterns

### "My mod adds a damage source; should I exempt minions of the victim?"

Yes. Before applying damage:
```java
if (victim instanceof ServerPlayer sp
        && sourceEntity instanceof LivingEntity le
        && NeoOriginsAPI.summonerOf(le).filter(s -> s == sp).isPresent()) {
    return; // friendly fire from a summoner's own minion
}
```

### "My mod has a tamed-pet concept; should I honour NeoOrigins' tamer?"

If you're iterating tamed mobs belonging to a player, include those
tracked by NeoOrigins:

```java
NeoOriginsAPI.forEachOfType(player, TameMobPower.class, cfg -> {
    // NeoOrigins tracks the mobs internally via MinionTracker —
    // use NeoOriginsAPI.isAnyMinion(entity) as the filter.
});
```

### "I want to block a player with a specific origin from entering a region."

Listen for `OriginChangedEvent` or poll:
```java
Origin o = NeoOriginsAPI.currentOrigin(player, myLayerId);
if (o != null && o.id().equals(Identifier.parse("mypack:forbidden"))) {
    // kick / teleport / deny
}
```

---

## Custom projectiles

For projectiles with behavior that can't be expressed via
`spawn_projectile` + `on_hit_action` (homing, chaining, trail effects,
continuous in-flight ticks), subclass
`com.cyberday1.neoorigins.api.content.projectile.AbstractNeoProjectile`
and register an {@link net.minecraft.world.entity.EntityType}.

```java
public class MySeekerProjectile extends AbstractNeoProjectile {
    public MySeekerProjectile(EntityType<? extends MySeekerProjectile> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getVisualItem() { return Items.ARROW; }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() || this.isRemoved()) return;
        // Your per-tick AI — seeking, trail particles, speed clamp, etc.
    }

    @Override
    protected void onImpact(ServerLevel level, HitResult result) {
        // Your impact behavior. The projectile is discarded automatically
        // after this method returns.
    }
}
```

Register the entity type during `RegisterEvent` / `DeferredRegister`,
register the renderer in `EntityRenderersEvent.RegisterRenderers`. See
`HomingProjectile` + `ModEntities#HOMING_PROJECTILE` in the NeoOrigins
source for the canonical pattern.

Pack authors reference your entity by its registered ID from any
`spawn_projectile` action:

```json
{
  "type": "neoorigins:spawn_projectile",
  "entity_type": "yourmod:seeker",
  "speed": 1.2,
  "on_hit_action": { "type": "neoorigins:heal", "amount": 2 }
}
```

The `on_hit_action` still fires independently of your subclass's
`onImpact` — the DSL callback and the entity-class callback complement
each other.

### GeckoLib soft-dep

If you want custom-modeled / animated projectiles rather than
item-textured ones, GeckoLib is a runtime-optional integration. NeoOrigins
ships {@code com.cyberday1.neoorigins.compat.GeckoLibCompat#isLoaded()}
for the presence probe — gate any renderer that touches GeckoLib classes
on that call and provide a {@link
net.minecraft.client.renderer.entity.ThrownItemRenderer}-based fallback
for the no-GeckoLib case. Full animated-projectile support is planned
for 2.1.

---

## VFX entities (`api/content/vfx/`)

Non-moving visual-effect entities — lingering clouds, black holes,
tornados, ground markers. NeoOrigins ships three reference subclasses
(`LingeringAreaEntity`, `BlackHoleVfxEntity`, `TornadoVfxEntity`) and a
base class + renderer stack for custom VFX.

### Base class: `AbstractVfxEntity`

```java
public class MyAuraEntity extends AbstractVfxEntity {
    public MyAuraEntity(EntityType<? extends MyAuraEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void onVfxTick(ServerLevel level) {
        // Per-tick server-side behavior. Range, lifetime, caster, and
        // effect-type are already tracked by the base class.
        if (getLifetime() % 20 == 0) {
            // ...apply an effect to entities in getRange()
        }
        emitParticles(ParticleTypes.SOUL, 3, getRange() * 0.5, 0.1, getRange() * 0.5);
    }

    @Override
    protected void onExpire(ServerLevel level) {
        // Optional — fires once before discard when lifetime ends.
    }
}
```

Public API the base provides for free:
- `getRange()` / `setRange(float)` — synched to client
- `getEffectType()` / `setEffectType(String)` — synched color key
- `getLifetime()` / `getMaxLifetime()` / `setMaxLifetime(int)`
- `getCasterUuid()` / `setCaster(UUID)` / `resolveCaster()`
- `emitParticles(ParticleOptions, count, xSpread, ySpread, zSpread)`

The base handles `tick()`, lifetime countdown, expiry, and the
`hurt()`/`hurtServer()` disable so your VFX can't be killed by damage.

### Procedural quad renderer: `ProceduralQuadRenderer<T, S>`

For projectile-style VFX (orbs, crossed billboards, pulsing glows), extend
`ProceduralQuadRenderer` and implement the abstract hooks:

```java
public class MyOrbRenderer extends ProceduralQuadRenderer<MyOrbEntity, MyOrbRenderState> {
    public MyOrbRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override protected MyOrbRenderState createRenderState() { return new MyOrbRenderState(); }

    @Override
    protected void extractRenderState(MyOrbEntity entity, MyOrbRenderState state, float partialTick) {
        AbstractVfxRenderState.extract(entity, state);
    }

    @Override protected RenderType renderType() { return MY_RENDER_TYPE; }

    // Optional animation tuning:
    @Override protected float coreYawPerTick() { return 25f; }
    @Override protected float glowPulseAmplitude() { return 0.12f; }
}
```

The same subclass compiles unchanged on 1.21.1 and 26.1 — only the base
class's render-flow internals differ. See
[CUSTOM_PROJECTILES.md](CUSTOM_PROJECTILES.md) for the three-tier
extension guide.

### Effect-type registry: `VfxEffectTypes`

Colour keys used by the built-in VFX. Register your own for other mods to
reuse from JSON via the `effect_type` field on `spawn_projectile` /
`spawn_lingering_area`:

```java
VfxEffectTypes.register("yourmod:radiant", 255, 240, 200);
```

Then in a datapack:
```json
{ "type": "neoorigins:spawn_projectile",
  "entity_type": "neoorigins:magic_orb",
  "effect_type": "yourmod:radiant" }
```

### Bedrock-model loader: `GeoJsonModel`

Loads a Bedrock `.geo.json` model from the classpath, bakes vertex data
once, and renders via a `PoseStack` + `VertexConsumer`. Face culling is
occupancy-based (adjacent cubes hide touching faces). No bone animation
— spin/transform models from the renderer directly.

```java
private static final GeoJsonModel MODEL =
    GeoJsonModel.load("/assets/yourmod/geo/runestone.geo.json");

// In the renderer:
MODEL.render(poseStack, buffer.getBuffer(RENDER_TYPE), packedLight, OverlayTexture.NO_OVERLAY);
```

Graceful fallback to a unit cube if the model fails to load — the
exception is logged but the renderer continues so a broken asset doesn't
crash the game.

---

## What's NOT in the API

The following commonly-requested things are intentionally internal. Open
an issue if you need them elevated:

- **Picker UI payloads** — the network protocol between client and server
  for origin selection. Bound to break as the UI evolves.
- **PowerHolder internals** — the wrapper around power type + config +
  origin. You see it through `powers()` but shouldn't mutate it.
- **Mixin targets** — the internal mixin classes. Depending on them
  couples your mod to our injection points.
- **Capability cache internals** — `ActiveOriginService` version numbers
  and dimension-scoped caches.

If you find yourself wanting to reach into these, we've probably missed a
proper API surface — please file an issue.
