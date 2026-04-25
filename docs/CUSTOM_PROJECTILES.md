---
title: Custom Projectiles & VFX
nav_order: 11
---

# Custom Projectiles & VFX Entities

Guide for pack authors and mod developers who want to extend NeoOrigins's
visual-effects pipeline beyond the built-in `magic_orb` / `lingering_area` /
vanilla-projectile options.

**Three levels of customisation** cover most cases:

1. **[Pack-author level](#level-1-pack-author-new-effect_type-color)** —
   register a custom `effect_type` color from a companion mod, no new entity.
2. **[Procedural custom renderer](#level-2-procedural-custom-renderer)** —
   write a Java subclass of `ProceduralQuadRenderer` with custom animation
   math. No asset files needed.
3. **[Model-loaded custom entity](#level-3-model-loaded-custom-entity)** —
   ship a Bedrock `.geo.json` model + texture, use `GeoJsonModel` to load
   it, write a custom renderer that draws the baked mesh.

Each builds on the previous. All three paths plug into the existing
`spawn_projectile` / `spawn_lingering_area` DSL verbs — pack authors
reference your entity by its registered ID.

---

## Prerequisites

- NeoOrigins 2.0+ (API under `com.cyberday1.neoorigins.api.content.vfx`)
- **MC 1.21.1 or 26.1** — the public API (abstract hooks, animation
  parameters, effect-type registry, model loader) is identical on both
  versions. Only the base classes' internal render flow differs (1.21.1
  uses the classic `render()` + `MultiBufferSource` path; 26.1 uses the
  state-pattern `submit()` + `SubmitNodeCollector`). Subclass code
  compiles unchanged across both, so the same mod jar is rarely the goal
  — multi-version builds are.
- A companion mod project — these are Java examples, not datapack JSON.
  For a pure-datapack approach, use the pre-registered `neoorigins:magic_orb`
  with one of the built-in `effect_type` keys (see the
  `neoorigins:spawn_projectile` / `spawn_lingering_area` /
  `spawn_black_hole` / `spawn_tornado` verbs in
  [ACTIONS.md](ACTIONS.md) and recipes 12–14 in [COOKBOOK.md](COOKBOOK.md)).

---

## Level 1: Pack-author — new `effect_type` color

**Goal:** register a new effect type key so pack JSON can use
`"effect_type": "verdant_glow"` and get a specific green-yellow color.

**What you ship:** one small class in your mod's common initialiser.

```java
package yourmod.example;

import com.cyberday1.neoorigins.api.content.vfx.VfxEffectTypes;

public class YourModVfx {
    public static void registerColors() {
        // RGB 0-255. Case-insensitive key.
        VfxEffectTypes.register("verdant_glow",  80, 200,  60);
        VfxEffectTypes.register("shadow_pulse", 40,  20, 100);
    }
}
```

Call `YourModVfx.registerColors()` once during your mod's common setup
(a `@Mod` constructor or `FMLCommonSetupEvent` handler). After that,
any pack JSON can reference the key:

```json
{
  "type": "neoorigins:spawn_projectile",
  "entity_type": "neoorigins:magic_orb",
  "effect_type": "verdant_glow",
  "speed": 1.6
}
```

**That's it.** No entity class, no renderer, no assets. Pack authors get
a new colored orb keyed by the name you picked.

**When to use this:** your mod adds themed spells or origins and you want
a distinctive color-name without the overhead of a new renderer.

---

## Level 2: Procedural custom renderer

**Goal:** a spinning crystal shard that scales larger as it travels and
pulses red → orange → red instead of spinning. Can't be done with the
default `magic_orb` because the animation is different.

**What you ship:** one entity class, one renderer, one render state, one
registration call. No asset files.

### Entity class (reuse AbstractNeoProjectile)

```java
package yourmod.entity;

import com.cyberday1.neoorigins.api.content.projectile.AbstractNeoProjectile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

public class CrystalShardProjectile extends AbstractNeoProjectile {
    public CrystalShardProjectile(EntityType<? extends CrystalShardProjectile> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getVisualItem() { return Items.PRISMARINE_SHARD; } // fallback if renderer isn't wired

    @Override
    protected void onImpact(ServerLevel level, HitResult result) {
        // Left empty — the DSL on_hit_action does the damage/effects.
    }
}
```

### Render state (carries time-math inputs)

```java
package yourmod.client;

import com.cyberday1.neoorigins.api.content.vfx.AbstractVfxRenderState;

public class CrystalShardRenderState extends AbstractVfxRenderState {
    // No extra fields — base class has what we need.
}
```

### Renderer (override the procedural parameters)

```java
package yourmod.client;

import com.cyberday1.neoorigins.api.content.vfx.ProceduralQuadRenderer;
import yourmod.entity.CrystalShardProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;

public class CrystalShardRenderer extends ProceduralQuadRenderer<CrystalShardProjectile, CrystalShardRenderState> {
    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath("yourmod", "textures/entity/crystal_shard.png");
    private static final RenderType RENDER_TYPE = RenderTypes.entityTranslucentEmissive(TEXTURE);

    public CrystalShardRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    // Override the procedural parameters inherited from ProceduralQuadRenderer
    @Override protected float coreYawPerTick() { return 8.0f; }   // slower spin
    @Override protected float corePitchPerTick() { return 0f; }   // no pitch, just yaw
    @Override protected float coreScale() { return 0.5f; }        // bigger core
    @Override protected float glowBaseScale() { return 1.0f; }    // bigger halo
    @Override protected float glowPulseAmplitude() { return 0.2f; } // stronger pulse
    @Override protected float glowPulseFrequency() { return 0.3f; } // faster pulse

    // Custom color — red pulsing to orange, overriding the effect_type lookup
    @Override
    protected int[] resolveColor(CrystalShardRenderState state) {
        float t = state.lifetime + state.partialTick;
        // Interpolate between red and orange based on sin wave
        float phase = (float) (0.5 + 0.5 * Math.sin(t * 0.2));
        int r = 255;
        int g = (int) (60 + phase * 100); // 60 → 160
        int b = 40;
        return new int[]{r, g, b};
    }

    @Override
    public CrystalShardRenderState createRenderState() { return new CrystalShardRenderState(); }

    @Override
    public void extractRenderState(CrystalShardProjectile entity, CrystalShardRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.lifetime = entity.tickCount;
    }

    @Override
    public void submit(CrystalShardRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        submitQuads(state, poseStack, collector, RENDER_TYPE);
        super.submit(state, poseStack, collector, camera);
    }
}
```

### Registration

```java
// In your mod's ModEntities (DeferredRegister for EntityType)
public static final DeferredHolder<EntityType<?>, EntityType<CrystalShardProjectile>> CRYSTAL_SHARD =
    ENTITY_TYPES.register("crystal_shard", () ->
        EntityType.Builder.<CrystalShardProjectile>of(CrystalShardProjectile::new, MobCategory.MISC)
            .sized(0.3F, 0.3F)
            .clientTrackingRange(4)
            .updateInterval(10)
            .build(CRYSTAL_SHARD_KEY));

// In your client events handler (EntityRenderersEvent.RegisterRenderers)
event.registerEntityRenderer(ModEntities.CRYSTAL_SHARD.get(), CrystalShardRenderer::new);
```

### Pack authors reference by entity ID

```json
{
  "type": "neoorigins:spawn_projectile",
  "entity_type": "yourmod:crystal_shard",
  "speed": 1.8
}
```

**When to use this:** your projectile needs a distinctive *animation*
pattern (different spin rate, pulse math, colour curve) that the default
isn't producing. Still no assets required.

---

## Level 3: Model-loaded custom entity

**Goal:** a giant rotating runestone that spins in place for 10 seconds,
rendering a complex geometric shape too detailed for procedural quads.

**What you ship:** one `.geo.json` + one `.png` texture, plus three Java
classes.

### The asset files

Create these in your mod's resources:

```
assets/yourmod/geo/runestone.geo.json       <- Bedrock model, exported from Blockbench
assets/yourmod/textures/entity/runestone.png <- texture
```

The `.geo.json` must follow the Bedrock 1.12.0+ format (single geometry,
at least one bone with cubes). Animations inside the `.geo.json` are
**ignored** — procedurally spin the model in the renderer instead.

### Entity class (extends AbstractVfxEntity for lifetime + particles)

```java
package yourmod.entity;

import com.cyberday1.neoorigins.api.content.vfx.AbstractVfxEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class RunestoneVfx extends AbstractVfxEntity {
    public RunestoneVfx(EntityType<? extends RunestoneVfx> type, Level level) {
        super(type, level);
        // 10 seconds default
        setMaxLifetime(200);
    }

    @Override
    protected void onVfxTick(ServerLevel level) {
        // Emit ENCHANT particles in a ring once per second
        if (lifetime % 20 == 0) {
            emitParticles(ParticleTypes.ENCHANT, 12, getRange() * 0.8, 0.1, getRange() * 0.8);
        }
    }
}
```

### Render state

```java
public class RunestoneRenderState extends AbstractVfxRenderState {
    // Inherits range + lifetime — that's all the render math needs.
}
```

### Renderer — uses GeoJsonModel

```java
package yourmod.client;

import com.cyberday1.neoorigins.api.content.vfx.AbstractVfxRenderState;
import com.cyberday1.neoorigins.api.content.vfx.GeoJsonModel;
import com.cyberday1.neoorigins.api.content.vfx.VfxEffectTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import yourmod.entity.RunestoneVfx;

public class RunestoneRenderer extends EntityRenderer<RunestoneVfx, RunestoneRenderState> {
    // Load the model once at class load.
    private static final GeoJsonModel MODEL = GeoJsonModel.load("/assets/yourmod/geo/runestone.geo.json");

    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath("yourmod", "textures/entity/runestone.png");
    private static final RenderType RENDER_TYPE = RenderTypes.entityTranslucentEmissive(TEXTURE);

    public RunestoneRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override
    public RunestoneRenderState createRenderState() { return new RunestoneRenderState(); }

    @Override
    public void extractRenderState(RunestoneVfx entity, RunestoneRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        AbstractVfxRenderState.extract(entity, state);
    }

    @Override
    public void submit(RunestoneRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        float time = state.lifetime + state.partialTick;

        // Scale by range so larger radii get bigger runestones.
        float scale = state.range / MODEL.getRadius();
        // Tint by the effect_type color for themed variants.
        int[] color = VfxEffectTypes.get(state.effectType);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 2.0f)); // slow spin
        poseStack.scale(scale, scale, scale);
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, consumer) ->
            MODEL.renderTinted(
                new PoseStack() {{ /* unused; the model uses the passed pose internally */ }},
                consumer, color[0], color[1], color[2], 255,
                0xF000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY));
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
```

(Note: the exact renderer wiring uses `GeoJsonModel.renderTinted(poseStack, consumer, ...)` — the
intermediate stack adapter in the example above is illustrative. See
`BlackHoleRenderer` in the NeoOrigins source for the real pattern.)

### Registration & DSL

Same pattern as Level 2 — register the entity type, register the
renderer, and pack JSON references `"entity_type": "yourmod:runestone"`.

**When to use this:** your projectile or VFX entity has a shape that
genuinely requires geometry — rings, gears, crystals, multi-bone
structures. `GeoJsonModel` loads the mesh once at classload, bakes
face-culled vertex data, and renders it efficiently every frame.

---

## How the pieces fit together

Two rendering paths:

| Path | Asset files | Java classes | Best for |
|---|---|---|---|
| Level 1 (pack-author) | none | 0 | Themed color variants of the default magic orb |
| Level 2 (procedural) | none | 3 (entity + state + renderer) | Custom animation math without geometry |
| Level 3 (model-loaded) | .geo.json + .png | 3 (entity + state + renderer) + assets | Distinctive geometric shapes |

All three plug into `spawn_projectile` identically — the pack author
doesn't know (or care) which level implemented the visual.

### When you need the entity to actually *do* things during flight

Level 2 entities can override any `Entity` method — `tick()` to adjust
velocity (homing), `onHit()` to trigger custom impact behaviour, etc. See
`HomingProjectile` in the NeoOrigins source for a working example that
steers toward the nearest living entity each tick.

### When you need lingering AoE behavior

Use `neoorigins:spawn_lingering_area` — no custom entity needed. The
action accepts any nested `entity_action` to run on interval. Pair it
with a `spawn_projectile` + `on_hit_action` and the lingering area
lands at the projectile's impact point. See `docs/COOKBOOK.md` recipe 11
for a worked example.

### Stability contract

Types under `com.cyberday1.neoorigins.api.content.vfx.**` follow
NeoOrigins's semver — stable in minor releases, additive changes only.
See `docs/JAVA_API.md` for the full contract.

---

## Common pitfalls

**"My renderer compiles but the projectile is invisible."**
Most likely the renderer isn't registered, or you're on a dedicated
server without the client event subscriber. `registerEntityRenderer` is
client-only — put it in an `@EventBusSubscriber(value = Dist.CLIENT)`
class or guard it with `FMLEnvironment.dist.isClient()`.

**"The pose/transform looks wrong — my model is off-center or tiny."**
Blockbench exports use pixel-unit coordinates. `GeoJsonModel` divides by
16 to convert to Minecraft blocks. If your model's `origin` values are
huge (e.g., `[14, -5, 0]` — which is 14 pixels from origin), the mesh
sits 14/16 ≈ 0.88 blocks away from the entity position. Recenter the
model in Blockbench or offset the `poseStack.translate(...)` in your
renderer.

**"The effect_type color isn't applying."**
Check `VfxEffectTypes.isRegistered("your_key")`. If false, your
`register()` call didn't run — likely because the caller is in a
`@OnlyIn(Dist.CLIENT)` class and the registration needs to happen on
both sides (entities sync via `SynchedEntityData`; renderers read the
registry on render).

**"`.geo.json` loads but the model shape is wrong."**
`GeoJsonModel` only reads cubes from the first bone of the first
geometry. Multi-bone skeletal models are out of scope — use a
single-bone cube soup (Blockbench: merge all cubes into one bone before
exporting) or invest in GeckoLib.

**"Pack authors don't see my entity in `spawn_projectile`."**
Registered entities appear in the DSL as soon as they're registered in
`Registries.ENTITY_TYPE` — no separate NeoOrigins-side registration
needed. If `spawn_projectile` logs "unknown entity," your registry
timing is off — check that `ModEntities.register(modEventBus)` runs in
your mod's constructor before common setup.

---

## Reference implementations in the NeoOrigins source

- `content/MagicOrbProjectile` + `client/renderer/MagicOrbRenderer` — Level 2
  procedural, what the example pack origins use
- `content/LingeringAreaEntity` + `client/renderer/LingeringAreaRenderer` —
  `AbstractVfxEntity` subclass with server-emitted particles
- `content/HomingProjectile` — custom per-tick AI on a projectile
- `api/content/vfx/GeoJsonModel` — Level 3 model loader internals

All free to copy, adapt, or extend from.
