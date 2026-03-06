# Contributing to NeoOrigins

## Build Requirements

- Java 21
- Gradle (wrapper included — use `./gradlew`)
- NeoForge 21.11.38-beta (resolved automatically)

```bash
./gradlew build          # Compile → build/libs/neoorigins-1.0.0.jar
./gradlew runClient      # Launch Minecraft client with the mod
./gradlew runServer      # Launch dedicated server (headless)
```

Deploy for in-game testing:
```
build/libs/neoorigins-1.0.0.jar  →  <curseforge-instance>/mods/
```

---

## Adding a New Passive Power Type

1. **Implement the power class** in `power/builtin/`:
   ```java
   public class MyPower extends PowerType<MyPower.Config> {
       public record Config(int someParam, String type) implements PowerConfiguration {
           public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
               Codec.INT.optionalFieldOf("some_param", 5).forGetter(Config::someParam),
               Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
           ).apply(inst, Config::new));
       }

       @Override public Codec<Config> codec() { return Config.CODEC; }

       // Override onGranted / onRevoked / onTick as needed
   }
   ```
   For status effects that must reapply continuously, extend `PersistentEffectPower<C>` instead.

2. **Register it** in `power/registry/PowerTypes.java`:
   ```java
   public static final DeferredHolder<PowerType<?>, MyPower> MY_POWER =
       reg("my_power", new MyPower());
   ```
   Place it under the appropriate section comment (`// --- Passive: combat ---`, etc.).

3. **Handle the event** in the appropriate event class under `event/`:
   - Use `ActiveOriginService.forEachOfType(player, MyPower.class, (type, config) -> { ... })`
   - Register with `@SubscribeEvent` in the matching `@EventBusSubscriber` class.

4. **Add a power JSON** at `data/neoorigins/origins/powers/<name>.json`:
   ```json
   { "type": "neoorigins:my_power", "some_param": 10 }
   ```

5. **Reference the power** in an origin JSON at `data/neoorigins/origins/origins/<origin>.json`:
   ```json
   { "powers": ["neoorigins:my_power"] }
   ```

6. **Add translations** to `assets/neoorigins/lang/en_us.json`:
   ```json
   "power.neoorigins.my_power.name": "My Power",
   "power.neoorigins.my_power.description": "Does something cool."
   ```

---

## Adding a New Active Power Type

Active powers extend `AbstractActivePower<C>` (located in `power/builtin/base/`).

1. **Implement the power class**:
   ```java
   public class MyActivePower extends AbstractActivePower<MyActivePower.Config> {
       public record Config(int cooldownTicks, String type)
               implements AbstractActivePower.Config {
           public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
               Codec.INT.optionalFieldOf("cooldown_ticks", 60).forGetter(Config::cooldownTicks),
               Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
           ).apply(inst, Config::new));
       }

       @Override public Codec<Config> codec() { return Config.CODEC; }

       @Override
       protected boolean execute(ServerPlayer player, Config config) {
           // Return true  → cooldown is consumed
           // Return false → cooldown is NOT consumed (nothing happened)
           return true;
       }
   }
   ```

2. **Register, add JSON, and add translations** as described in steps 2–6 above.

`AbstractActivePower` automatically:
- Gates `execute()` behind the cooldown check
- Sets `isActivePower() = true` so the slot system picks it up
- No `@SubscribeEvent` needed for the activation itself — only for passive side-effects (e.g. `onTick`)

---

## Adding a Route A Translation

Route A handles Origins power types by rewriting the JSON before codec parsing.

Edit `compat/OriginsPowerTranslator.java`:

1. Add a `case "origins:my_type":` branch in the `translate()` switch.
2. Build and return a new `JsonObject` with the NeoOrigins `type` and mapped fields.
3. If the translation is lossy, add a `// [LOSSY] reason` comment.
4. Add the type to `SKIP_TYPES` in `OriginsCompatPowerLoader` if Route B should not also attempt it.

```java
case "origins:my_type" -> {
    out.addProperty("type", "neoorigins:equivalent_type");
    out.addProperty("some_field", obj.get("origins_field").getAsInt());
    // [LOSSY] some information from origins_field_2 is not representable
    yield out;
}
```

---

## Adding an Origin

Create a JSON file at `data/<namespace>/origins/origins/<name>.json`:

```json
{
  "name": "My Origin",
  "description": "A one-line description shown in the UI.",
  "icon": "minecraft:some_item",
  "impact": "medium",
  "powers": [
    "neoorigins:some_power",
    "neoorigins:another_power"
  ]
}
```

Valid `impact` values: `"none"`, `"low"`, `"medium"`, `"high"`.

Register the origin in the layer file at `data/<namespace>/origins/origin_layers/origin.json`:
```json
{ "origins": [{"type": "origins", "origins": ["<namespace>:<name>"]}] }
```

---

## Code Style

- No wildcard imports
- Prefer `var` for local variables where the type is obvious from the right-hand side
- Active power configs must have `cooldown_ticks` as a field and implement `AbstractActivePower.Config`
- Log messages in the compat layer use `[CompatA]` or `[CompatB]` prefix (WARN for unknown types)
- Lossy translations use `// [LOSSY] reason` annotations (grep-able)
