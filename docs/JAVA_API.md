---
title: Java API
parent: "Scripting & Java"
nav_order: 2
---

# NeoOrigins Java API

For mods that want to integrate with NeoOrigins: check if a player has a
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
| `2.x.0` (minor) | Additive only: new methods, new power types, new events. Existing signatures stable. |
| `2.0.x` (patch) | Bug fixes only. No API surface changes. |

Types under `service/`, `event/`, `power/builtin/`, `compat/`, `mixin/`,
`network/` are **internal**. They can change between patch releases. Don't
import them from your mod. A few samples below still name internal classes
(`SummonMinionPower` and `TameMobPower` from `power/builtin/`, `GeckoLibCompat`
from `compat/`); treat those references as internal too.

If an integration you need isn't available through `api/`, open an issue, and
we'll promote the internal method to API rather than ask you to import a
service class directly.

---

## Dependency setup

`build.gradle`:

```gradle
repositories {
    maven { url "https://api.modrinth.com/maven" }
}

dependencies {
    // Compile against a stable minor release; runtime will use whatever
    // version of NeoOrigins the user has installed ≥ the declared version.
    // Replace <VERSION> with the latest release for your MC version — see the
    // releases page linked below.
    // For 26.2:
    compileOnly "maven.modrinth:neo-origins:<VERSION>+26.2"
    // For 26.3:
    // compileOnly "maven.modrinth:neo-origins:<VERSION>+26.3"
    // For 26.1.x:
    // compileOnly "maven.modrinth:neo-origins:<VERSION>+26.1"
    // For 1.21.1:
    // compileOnly "maven.modrinth:neo-origins:<VERSION>+1.21.1"
}
```

> **Version format:** Modrinth Maven uses the exact version string from the
> [releases page](https://modrinth.com/mod/neo-origins/versions).
> The format is `v{version}+{mc_version}`, where `{mc_version}` is `1.21.1`,
> `26.1`, `26.2` or `26.3` (e.g. `v{version}+26.2`). Always
> pull the newest published string from the releases page rather than
> hard-coding a value here, so this snippet doesn't go stale between releases.

`neoforge.mods.toml`:

```toml
[[dependencies.your_mod]]
    modId = "neoorigins"
    type = "optional"   # or "required"
    versionRange = "[2.0.0,3.0.0)"
    ordering = "AFTER"
    side = "BOTH"
```

Use `optional` unless your mod is useless without NeoOrigins. This gives
players the choice. Check with `ModList.get().isLoaded("neoorigins")`
before calling into the API.

---

## Entry point: `NeoOriginsAPI`

The preferred way to call into NeoOrigins.

```java
import com.cyberday1.neoorigins.api.NeoOriginsAPI;

// Does any active power on this player emit the "enhanced_vision" capability tag?
if (NeoOriginsAPI.hasCapability(player, "enhanced_vision")) { ... }

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
| `hasCapability(player, tag)` | True if the capability tag is emitted (shared vocabulary with client-side effect layers). Tags are emitted by specific power types, e.g. `enhanced_vision`, `wall_climb`, `walk_on_water`; there is no `water_breathing` tag. |
| `summonerOf(entity)` | Reverse-lookup a summoned minion's owner. |
| `isMinionOf(entity, summoner)` | Cheap check: is this entity summoner's minion? |
| `isAnyMinion(entity)` | Is this entity anyone's tracked minion? Falls back to a scan of tracked minions when the entity carries no minion marker. |
| `getOrigin(player, layer)` | The player's origin on a layer, as an `Optional`. Empty if the layer holds none. |
| `getOrigins(player)` | Every layer the player holds an origin on, mapped to that origin. Unmodifiable copy. |
| `setOrigin(player, layer, origin)` | Change the player's origin on a layer. Returns a `SetOriginResult`. See [Changing a player's origin](#changing-a-players-origin). |
| `clearOrigin(player, layer)` | Empty one layer, revoking only that layer's powers. Returns `false` if it held nothing. |
| `grantPower(player, power, source[, layer])` | Grant a power by id on behalf of your mod, optionally tied to a layer. Returns a `GrantPowerResult`. See [Granting powers at runtime](#granting-powers-at-runtime). |
| `revokePower(player, power, source)` | End your mod's grant of one power. |
| `revokePowers(player, source[, filter])` | End all of your mod's grants, or those whose power matches a `Predicate<PowerHolder<?>>`. |
| `grantedPowers(player, source)` | The power ids your mod currently grants the player. |
| `hasPower(player, power)` | True if the player holds the power from any origin, command, global set or API grant. |
| `registerPower(id, type, config, display[, options])` | Register a power built in code rather than JSON, optionally with a condition gate and toggle upkeep. See [Powers registered in code](#powers-registered-in-code). |
| `registerPowerFromJson(id, type, config, display[, options])` | Register a code power whose config is the JSON a datapack power file would hold. |
| `parseCondition(json, contextId)` | Parse a NeoOrigins entity condition into a `Predicate<ServerPlayer>`. See [Conditions in your own code](#conditions-in-your-own-code). |
| `conditionCodec()` | A `Codec` for a condition field in your own power config. |
| `parseAction(json, contextId)` | Parse a NeoOrigins entity action into a `Consumer<ServerPlayer>`. See [Actions in your own code](#actions-in-your-own-code). |
| `actionCodec()` | A `Codec` for an action field in your own power config. |
| `registerResourceBacking(id, backing)` | Register an external pool that resource bars can name with `"backing"`. See [Resource backings](#resource-backings). |

Call these on the logical server: the player-facing methods take a
`ServerPlayer` and read server-side state.

---

## Changing a player's origin

```java
SetOriginResult result = NeoOriginsAPI.setOrigin(player,
    Identifier.fromNamespaceAndPath("neoorigins", "origin"),
    Identifier.fromNamespaceAndPath("mypack", "archmage"));
```

`setOrigin` changes an origin the same way the game does: it revokes the old
origin's powers on that layer only, grants the new one's, updates unique-origin
claims and syncs the client. It fires `OriginChangedEvent`, so a listener can
cancel the change or redirect it to another origin.

| Result | Meaning |
|---|---|
| `SUCCESS` | The layer now holds the origin (or the origin a listener redirected to). |
| `UNCHANGED` | The layer already held that origin. |
| `UNKNOWN_LAYER` / `UNKNOWN_ORIGIN` | The id isn't loaded. |
| `CLAIMED` | The layer is unique and another player holds that origin. |
| `CANCELLED` | An `OriginChangedEvent` listener cancelled it. |

- It doesn't check the layer's own origin list or conditions, so it can assign
  any loaded origin, the same as `/origin set`.
- It doesn't ask the player to confirm, open the picker or change the evolution
  tier.
- `clearOrigin(player, layer)` empties one layer and revokes only its powers.
  Other layers, powers granted by `/power grant` or the `grant_power` action,
  and API grants not tied to that layer are kept. It fires no event.
- `OriginChangedEvent` fires when an origin is set by the picker,
  `/origin set`, an advancement upgrade, random assignment, auto-human or this
  API. `/origin set`, random assignment and auto-human ignore a listener's
  cancel or redirect. Emptying a layer fires nothing.
- An advancement upgrade into a unique origin another player holds doesn't
  happen.

---

## Granting powers at runtime

```java
Identifier source = Identifier.fromNamespaceAndPath("mymod", "quest_reward");
GrantPowerResult result = NeoOriginsAPI.grantPower(player,
    Identifier.fromNamespaceAndPath("mypack", "fire_immunity"), source);
// later
NeoOriginsAPI.revokePower(player, Identifier.fromNamespaceAndPath("mypack", "fire_immunity"), source);
```

A granted power behaves exactly like one from an origin: its effects, toggles,
upkeep, resource bar, keybind slot and client display all work. `source` is an
id in your mod's namespace. Each mod's grants are kept apart, so a power that
two sources grant stays until both revoke it, and no other mod, `/power revoke`
or `revoke_power` action can remove yours.

| Result | Meaning |
|---|---|
| `GRANTED` | The source now grants the power. |
| `UPDATED` | The source already granted it; its layer moved to the one given. |
| `ALREADY_GRANTED` | The source already granted it on that layer. Nothing changed. |
| `UNKNOWN_POWER` | No power with that id is loaded. Nothing was stored. |

How long a grant lasts:

- It is saved with the player, so it survives relog, death and a server restart.
- `grantPower(player, power, source, layer)` ties it to a layer. It ends when
  that layer's origin changes, is cleared or is reset. If the layer holds no
  origin yet, it ends at the layer's next change.
- A grant with no layer lasts until revoked, including through a full
  `/origin reset`.
- If a datapack reload removes the power, the grant is kept but does nothing,
  and it works again if the power comes back.
- Granting a power the player already has from an origin doesn't apply it
  twice. Changing away from that origin leaves it in place while the grant holds.
- A `multiple` power id grants all its sub-powers.

`revokePowers(player, source, filter)` only ever looks at the given source's
grants, so a filter like `holder -> holder.type() instanceof MyPowerType` can't
remove origin powers or another mod's grants.

---

## Powers registered in code

To grant a power that has no JSON, register it once with an id, then grant it
by that id:

```java
// In your mod's constructor or FMLCommonSetupEvent, on every launch:
NeoOriginsAPI.registerPower(
    Identifier.fromNamespaceAndPath("mymod", "stone_skin"),
    MyPowerTypes.STONE_SKIN.get(),
    new StoneSkinConfig(4.0),
    PowerDisplay.of(Component.literal("Stone Skin"), Component.literal("Take less damage.")));

// Whenever needed:
NeoOriginsAPI.grantPower(player, Identifier.fromNamespaceAndPath("mymod", "stone_skin"), source);
```

This is how to add a power from a `PowerType` and a configuration at runtime:
grants are saved by id, so the id has to resolve again after a relog or restart.

- The type must be registered in the `neoorigins:power_type` registry (see
  [Custom power types](#custom-power-types)), or be a built-in type.
- A registered power works like a datapack one: origins can list it, it syncs
  to clients with its `PowerDisplay` name, description and hidden flag, and a
  datapack reload doesn't drop it.
- Registering the same id twice throws `IllegalStateException`. A datapack
  power with the same id overrides the registered one, with a warning, so pack
  authors can still replace it. Players already holding it switch to the
  datapack version at that reload.
- A resource or variable power stores its value under the power's own id,
  whatever `powerId` the config you pass carries.

### Condition gate, upkeep and toggle messages

Pass a `CodePowerOptions` to set what a JSON power gets from `power_condition`,
`power_condition_mode`, the toggle upkeep fields and `toggle_messages`:

```java
NeoOriginsAPI.registerPower(id, type, config, display,
    CodePowerOptions.builder()
        .condition(player -> player.isUnderWater())   // only works underwater
        .hungerCost(1)                                // toggle upkeep while on
        .upkeepInterval(40)
        .toggleMessages(false)
        .build());
```

- `condition(predicate)` defaults to `ALLOW`: the power only works while the
  predicate is true. `condition(predicate, ConditionMode.DENY)` blocks it while
  true instead, which is what a JSON `power_condition` does by default. The gate
  covers the same hooks as `power_condition`: `onTick`, activation, `onHit`,
  `onKill`, and the power queries on `NeoOriginsAPI`.
- The predicate can be a lambda or a condition from `parseCondition`.
- `hungerCost`, `resourceCost(resource, amount)`, `upkeepInterval` (default 20)
  and `upkeepCondition` are the toggle upkeep fields. Types with no toggle ignore
  them, with a warning.
- These settings survive `/reload`. A datapack power with the same id uses its
  own settings while it overrides the registered one.

### Config from JSON

`registerPowerFromJson(id, type, config, display[, options])` takes the config
as the JSON a datapack power file would hold, without `name` and `description`,
and decodes it the same way: the power's id is injected (resource bars and the
other types keyed by their own id need it), the upkeep fields and
`toggle_messages` are read, and `power_condition` / `power_condition_mode` gate
the power with the datapack default of `DENY`. Anything set in `options`
replaces the matching JSON fields. JSON that doesn't decode throws
`IllegalArgumentException`.

```java
NeoOriginsAPI.registerPowerFromJson(
    Identifier.fromNamespaceAndPath("mymod", "focus"),
    resourceType, // neoorigins:resource, looked up in the neoorigins:power_type registry
    JsonParser.parseString("{\"max\": 50, \"hud_render\": {\"label\": \"Focus\"}}").getAsJsonObject(),
    PowerDisplay.of(Component.literal("Focus"), Component.literal("A focus bar.")));
```

### Conditions in your own code

`parseCondition(json, contextId)` turns the JSON of any NeoOrigins entity
condition into a `Predicate<ServerPlayer>`:

```java
Predicate<ServerPlayer> submerged = NeoOriginsAPI.parseCondition(
    JsonParser.parseString("{\"type\": \"neoorigins:submerged_in\", \"fluid\": \"minecraft:water\"}").getAsJsonObject(),
    "mymod:stone_skin");
```

`conditionCodec()` lets pack authors set conditions inside your own power
type's config:

```java
public record StoneSkinConfig(double armor, Predicate<ServerPlayer> condition) implements PowerConfiguration {
    public static final Codec<StoneSkinConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.DOUBLE.fieldOf("armor").forGetter(StoneSkinConfig::armor),
        NeoOriginsAPI.conditionCodec().optionalFieldOf("condition", p -> true).forGetter(StoneSkinConfig::condition)
    ).apply(i, StoneSkinConfig::new));
}
```

- The field takes a condition object, or an array of them that must all pass.
- An unknown or malformed condition evaluates false and is logged with its
  context: the `contextId` you pass, or the power being loaded when the codec
  decodes a datapack power.
- Only conditions decoded from JSON can be encoded back; encoding a lambda is a
  codec error.

### Actions in your own code

`parseAction(json, contextId)` turns the JSON of any NeoOrigins entity action
into a `Consumer<ServerPlayer>`:

```java
Consumer<ServerPlayer> burst = NeoOriginsAPI.parseAction(
    JsonParser.parseString("{\"type\": \"neoorigins:heal\", \"amount\": 4}").getAsJsonObject(),
    "mymod:second_wind");
burst.accept(player);
```

`actionCodec()` lets pack authors set actions inside your own power type's
config:

```java
public record SecondWindConfig(int cooldown, Consumer<ServerPlayer> action) implements PowerConfiguration {
    public static final Codec<SecondWindConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.fieldOf("cooldown").forGetter(SecondWindConfig::cooldown),
        NeoOriginsAPI.actionCodec().optionalFieldOf("action", p -> {}).forGetter(SecondWindConfig::action)
    ).apply(i, SecondWindConfig::new));
}
```

- The field takes an action object, or an array of them that run in order.
- An unknown or malformed action does nothing and is logged with its context:
  the `contextId` you pass, or the power being loaded when the codec decodes a
  datapack power.
- Only actions decoded from JSON can be encoded back; encoding a lambda is a
  codec error.
- Run actions on the server thread.

Some actions read the event that triggered them: the `*_attacker` actions,
`target_action`, `add_to_set` and the other target actions, `cancel_event`, and
the block actions such as `strip` and `grow`. Run from your own code, outside a
NeoOrigins event, these find no event and do nothing. Actions with a fallback
use it instead: `set_block`, `execute_command` and the spawn actions act at the
player, `offset` runs its inner action unshifted, and `tame_target` tames the
mob the player is looking at. A `raycast` still hands its hit to the actions
inside it. Actions name the power whose state they change (`"power": "..."`),
so they work the same from your code.

---

## Resource backings

A `neoorigins:resource` bar normally keeps its value in NeoOrigins' own
per-player store. A bar with `"backing": "<id>"` reads and writes another
mod's pool instead, and that pool stays authoritative. `irons_spellbooks:mana`
is built in. To add your own, implement
`com.cyberday1.neoorigins.api.resource.ResourceBacking` and register it once,
from your mod's constructor or `FMLCommonSetupEvent`:

```java
NeoOriginsAPI.registerResourceBacking(
    Identifier.fromNamespaceAndPath("mymod", "stamina"),
    new ResourceBacking() {
        @Override public int read(ServerPlayer player) { return Stamina.get(player); }
        @Override public void add(ServerPlayer player, int delta) { Stamina.add(player, delta); }
        @Override public int max(ServerPlayer player) { return Stamina.max(player); }
    });
```

A pack then uses `"backing": "mymod:stamina"` on a resource power.

| Method | Required | Purpose |
|---|---|---|
| `read(player)` | yes | The player's current value. Read by the HUD, the `resource` condition and `resource_cost`. |
| `add(player, delta)` | yes | Add `delta` (negative to spend). Regen, `change_resource` and `resource_cost` all arrive here. Your pool applies its own limits. |
| `max(player)` | no | The live maximum the bar scales to. Return 0 or less (the default) to use the bar's declared `max`. |
| `set(player, value)` | no | Absolute set, for `set_resource` and `change_resource` with `"operation": "set"`. Return `false` (the default) to refuse it; the set is then ignored with one warning. |
| `available()` | no | Return `false` while the pool can't be used, e.g. when an optional dependency is missing. The bar then reads empty and writes do nothing, with one warning. Default `true`. |

- All methods are called on the server thread. NeoOrigins re-reads `read` and
  `max` every 10 ticks for the HUD, so keep them cheap.
- A backed bar runs from 0 to `max`. NeoOrigins never stores a value for it,
  and `start_value` is not applied.
- An exception thrown from a backing is caught and logged once. While it keeps
  throwing, the bar reads empty and writes do nothing.
- Registering an id twice, including `irons_spellbooks:mana`, throws
  `IllegalStateException`.
- A `backing` id that no mod registers leaves the bar in NeoOrigins' own store,
  with one warning.

---

## Events (`api/event/`)

Listen on the NeoForge event bus:

```java
@EventBusSubscriber(modid = YourMod.MOD_ID)
public class YourOriginListener {
    @SubscribeEvent
    public static void onOriginChanged(OriginChangedEvent event) {
        ServerPlayer player = event.getEntity();
        Identifier newOrigin = event.getNewOrigin();
        // React to the player picking a new origin.
    }
}
```

All four are posted on `NeoForge.EVENT_BUS`.

| Event | When it fires | Data |
|---|---|---|
| `OriginChangedEvent` | A player picks an origin in the selection screen (the server's handler for the pick packet). Not posted for `/neoorigins set`, reset, advancement upgrades, or automatic/random assignment. Cancellable: cancelling prevents the set. | `getEntity()` (`ServerPlayer`), `getLayer()`, `getOldOrigin()` (nullable), `getNewOrigin()`, `setNewOrigin(id)` |
| `OriginsLoadedEvent` | After the origin JSONs are applied, on every datapack load, including the first one at server start. Layers are reloaded after it, so layer data is not yet current when it fires. | None: it extends `Event` and has no getters. |
| `PowerGrantedEvent` | A power was granted through an origin change or a global power. | `getEntity()` (`ServerPlayer`), `getPowerId()` |
| `PowerRevokedEvent` | A power was revoked through an origin change or a global power. | `getEntity()` (`ServerPlayer`), `getPowerId()` |

`PowerGrantedEvent` / `PowerRevokedEvent` are not posted on every path that
runs a power's `onGranted` / `onRevoked`: an evolution-tier change and a
full tear-down (origin reset, profile re-apply, random-origin-on-death) call
the hooks without posting the events.

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

Register it in the `neoorigins:power_type` registry from your mod's
constructor. NeoOriginsAPI exposes no constant for the registry, so build the key
yourself. A `DeferredRegister` is the simplest way, and it is how NeoOrigins
registers its own types:

```java
public static final ResourceKey<Registry<PowerType<?>>> POWER_TYPE_KEY =
    ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("neoorigins", "power_type"));
public static final DeferredRegister<PowerType<?>> POWER_TYPES =
    DeferredRegister.create(POWER_TYPE_KEY, "mymod");
public static final DeferredHolder<PowerType<?>, MyBoostPower> MY_BOOST =
    POWER_TYPES.register("my_boost", MyBoostPower::new);

// In your mod's constructor:
public MyMod(IEventBus modEventBus) {
    POWER_TYPES.register(modEventBus);
}
```

To use `RegisterEvent` instead, add the listener to the same mod event bus.
`RegisterEvent` fires only on the mod bus, not on `NeoForge.EVENT_BUS`:

```java
public MyMod(IEventBus modEventBus) {
    modEventBus.addListener(MyMod::onRegister);
}

private static void onRegister(RegisterEvent event) {
    event.register(POWER_TYPE_KEY,
        helper -> helper.register(
            Identifier.fromNamespaceAndPath("mymod", "my_boost"),
            new MyBoostPower()));
}
```

`modEventBus.register(this)` only finds non-static `@SubscribeEvent` methods, so
a static handler registered that way fails at startup. Use `addListener`, or
`register(MyMod.class)` for a class of static handlers.

`MY_BOOST.get()` is the type to pass to [`registerPower`](#powers-registered-in-code).

Pack authors can now use `"type": "mymod:my_boost"` in their power JSONs.

### Lifecycle hooks

| Hook | When it fires |
|---|---|
| `onGranted(player, config)` | Power was just added to the player's active set. |
| `onRevoked(player, config)` | Power was just removed. |
| `onTick(player, config)` | Every server tick while granted and the power's condition passes. Keep this cheap. |
| `onLogin(player, config)` | Player logged in with this power. Defaults to calling `onGranted`. |
| `onRespawn(player, config)` | Player respawned with this power. Defaults to calling `onGranted`. |
| `onActivated(player, config)` | The power was activated: its skill hotkey was pressed, or an `activate_power` action targeted it. |
| `onHit(player, config, amount)` | Player took damage. Reaction hook. Skipped while the power's condition fails. |
| `onKill(player, config, killed)` | Player killed a `LivingEntity`. Skipped while the power's condition fails. |

`PowerType` also has overridable queries: `isActivePower()` /
`isActivePower(config)`, `occupiesHotkeySlot(config)`, `capabilities(config)` /
`capabilities(player, config)`, and the mob-origin hooks `appliesToMobs(config)`,
`applyToMob(...)` and `removeFromMob(...)`.

> Idempotency note: `onLogin` and `onRespawn` default to invoking
> `onGranted`. If your implementation registers a listener or adds an
> attribute modifier in `onGranted`, make sure it's safe to run multiple
> times. Give modifiers a fixed id and remove before adding to avoid stacking.

---

## Origin data model (`api/origin/`)

Read-only data types representing loaded origin JSON.

| Type | Purpose |
|---|---|
| `Origin` | One origin record (name, description, impact, icon, powers). |
| `OriginLayer` | One picker layer (a `List<ConditionedOrigin>`, name, order). `getAvailableOriginIds(...)` resolves the ids. |
| `Impact` | Enum: `NONE`, `LOW`, `MEDIUM`, `HIGH`. |
| `OriginUpgrade` | Upgrade condition (vanilla advancement) that migrates one origin to another. |
| `ConditionedOrigin` | Wraps an origin with a predicate determining availability. |

A player's chosen origin per layer is read with `NeoOriginsAPI.getOrigin` and
`getOrigins`, and changed with `setOrigin` and `clearOrigin`; see
[Changing a player's origin](#changing-a-players-origin).

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

Listen for `OriginChangedEvent` (it only covers picks made in the selection
screen, see [Events](#events-apievent)):
```java
@SubscribeEvent
public static void onOriginChanged(OriginChangedEvent event) {
    if (event.getNewOrigin().equals(Identifier.parse("mypack:forbidden"))) {
        // kick / teleport / deny, or event.setCanceled(true) to refuse the pick
    }
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
`onImpact`. The DSL callback and the entity-class callback complement
each other. The action runs with the **shooter** as its actor, so the
`heal` above heals the player who fired, not the entity that was hit.

### GeckoLib soft-dep

If you want custom-modeled / animated projectiles rather than
item-textured ones, GeckoLib is a runtime-optional integration. NeoOrigins
ships {@code com.cyberday1.neoorigins.compat.GeckoLibCompat#isLoaded()}
for the presence probe. Gate any renderer that touches GeckoLib classes
on that call and provide a {@link
net.minecraft.client.renderer.entity.ThrownItemRenderer}-based fallback
for the no-GeckoLib case. NeoOrigins itself ships no GeckoLib projectile
renderer; its only GeckoLib use is the `trigger_morph_animation` action.

---

## VFX entities (`api/content/vfx/`)

Non-moving visual-effect entities: lingering clouds, black holes,
tornados, ground markers. NeoOrigins ships five reference subclasses
(`LingeringAreaEntity`, `BlackHoleVfxEntity`, `TornadoVfxEntity`,
`ProjectileRainVfxEntity`, `TelegraphVfxEntity`) and a base class + renderer
stack for custom VFX.

> **1.21.1 vs 26.x:** on 26.x the VFX base saves lifetime, range, effect type and caster with the world and overrides `hurtServer()`; on 1.21.1 it saves none of them and overrides `hurt()`. Renderers need `getTextureLocation(T)` and `protected` state hooks on 1.21.1, and `public` state hooks with `submit()` on 26.x.

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
- `getRange()` / `setRange(float)`: synched to client
- `getEffectType()` / `setEffectType(String)`: synched color key
- `getLifetime()` / `getMaxLifetime()` / `setMaxLifetime(int)` / `getLifetimeProgress()`
- `getCasterUuid()` / `setCaster(UUID)` / `resolveCaster()`
- `emitParticles(ParticleOptions, count, xSpread, ySpread, zSpread)`

The base handles `tick()`, lifetime countdown, expiry, saving lifetime,
range, effect type and caster with the world, and overrides `hurtServer()`
so your VFX can't be killed by damage.

### Procedural quad renderer: `ProceduralQuadRenderer<T, S>`

For projectile-style VFX (orbs, crossed billboards, pulsing glows), extend
`ProceduralQuadRenderer` and implement the abstract hooks:

```java
public class MyOrbRenderer extends ProceduralQuadRenderer<MyOrbEntity, MyOrbRenderState> {
    public MyOrbRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    // public, not protected: on 26.x these override EntityRenderer's public methods.
    @Override public MyOrbRenderState createRenderState() { return new MyOrbRenderState(); }

    @Override
    public void extractRenderState(MyOrbEntity entity, MyOrbRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        AbstractVfxRenderState.extract(entity, state);
    }

    @Override protected RenderType renderType() { return MY_RENDER_TYPE; }

    // Optional animation tuning:
    @Override protected float coreYawPerTick() { return 25f; }
    @Override protected float glowPulseAmplitude() { return 0.12f; }
}
```

`AbstractVfxRenderState.extract(entity, state)` takes an `AbstractVfxEntity`,
so `MyOrbEntity` must extend it; a projectile-based orb fills the state
fields itself. `RenderType` here is `net.minecraft.client.renderer.rendertype.RenderType`.
The subclass does **not** compile unchanged on 1.21.1, where the base extends
`EntityRenderer<T>`: there it also needs `getTextureLocation(T)`, the two
render-state hooks are `protected`, and `RenderType` lives in
`net.minecraft.client.renderer`. See
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
occupancy-based (adjacent cubes hide touching faces). No bone animation:
spin/transform models from the renderer directly.

```java
private static final GeoJsonModel MODEL =
    GeoJsonModel.load("/assets/yourmod/geo/runestone.geo.json");

// In the renderer's submit(state, poseStack, collector, camera):
collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, consumer) ->
    MODEL.render(pose, consumer, packedLight, OverlayTexture.NO_OVERLAY));
```

Graceful fallback to a unit cube if the model fails to load. The
exception is logged but the renderer continues so a broken asset doesn't
crash the game.

---

## What's NOT in the API

The following commonly-requested things are intentionally internal. Open
an issue if you need them elevated:

- **Picker UI payloads**: the network protocol between client and server
  for origin selection. Bound to break as the UI evolves.
- **PowerHolder internals**: the wrapper around power type + config +
  origin. You see it through `powers()` but shouldn't mutate it.
- **Mixin targets**: the internal mixin classes. Depending on them
  couples your mod to our injection points.
- **Capability cache internals**: `ActiveOriginService` version numbers
  and dimension-scoped caches.

If you find yourself wanting to reach into these, we've probably missed a
proper API surface. Please file an issue.
