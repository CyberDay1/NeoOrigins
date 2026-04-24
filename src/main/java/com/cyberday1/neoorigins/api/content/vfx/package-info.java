/**
 * Public Java API for visual-effect entities — lingering areas, magic orbs,
 * black holes, tornados, and similar non-projectile or hybrid VFX.
 *
 * <p>Four foundations:
 * <ul>
 *   <li>{@link com.cyberday1.neoorigins.api.content.vfx.AbstractVfxEntity}
 *       — base class for lingering / stationary VFX entities. Owns lifetime,
 *       caster UUID, synched range + effect-type, despawn logic, particle
 *       helper.</li>
 *   <li>{@link com.cyberday1.neoorigins.api.content.vfx.AbstractVfxRenderState}
 *       — base render-state with common fields (range, effectType, lifetime,
 *       lifetimeProgress).</li>
 *   <li>{@link com.cyberday1.neoorigins.api.content.vfx.VfxEffectTypes}
 *       — string→color registry. Pack authors reference keys from JSON,
 *       renderers look up colors. {@code register(...)} is the extension
 *       point for other mods.</li>
 *   <li>{@link com.cyberday1.neoorigins.api.content.vfx.GeoJsonModel}
 *       — classpath loader for Bedrock-format {@code .geo.json} models.
 *       Bakes face-culled vertex data for fast rendering.</li>
 *   <li>{@link com.cyberday1.neoorigins.api.content.vfx.ProceduralQuadRenderer}
 *       — base renderer for the crossed-quads + time-math-animation pattern
 *       (no model file needed).</li>
 * </ul>
 *
 * <p>Two rendering paths supported:
 * <ul>
 *   <li><b>Procedural</b> (no asset files): extend {@code ProceduralQuadRenderer}
 *       and override the time-math parameters. Ideal for magic orbs, glowing
 *       pulses, simple spell effects.</li>
 *   <li><b>Model-loaded</b> (Bedrock .geo.json asset): use {@code GeoJsonModel.load(...)}
 *       and render via {@code render(...)} or {@code renderTinted(...)} inside
 *       a custom {@link net.minecraft.client.renderer.entity.EntityRenderer}.
 *       Ideal for complex shapes (black holes, tornados, structural effects).</li>
 * </ul>
 *
 * <p>For moving projectiles, see
 * {@link com.cyberday1.neoorigins.api.content.projectile.AbstractNeoProjectile}
 * — that handles physics + on-impact semantics. A "magic orb projectile" can
 * extend {@code AbstractNeoProjectile} for physics and use
 * {@code ProceduralQuadRenderer} for rendering; the two concepts are orthogonal.
 *
 * <p>API status: stable. Added in 2.0. See
 * {@code docs/CUSTOM_PROJECTILES.md} for a step-by-step extension guide.
 */
package com.cyberday1.neoorigins.api.content.vfx;
