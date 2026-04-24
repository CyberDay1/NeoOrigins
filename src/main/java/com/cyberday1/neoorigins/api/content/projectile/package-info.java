/**
 * Public Java API for custom projectile entities that integrate with the
 * NeoOrigins 2.0 action/event pipeline.
 *
 * <p>See {@link com.cyberday1.neoorigins.api.content.projectile.AbstractNeoProjectile}
 * for the extension point. Reference implementations live in
 * {@code com.cyberday1.neoorigins.content} (e.g., {@code HomingProjectile}).
 *
 * <p>Typical use case: your mod wants a projectile with custom AI (homing,
 * chaining, trail particles, continuous effects during flight) that can't be
 * expressed as a {@code spawn_projectile} + {@code on_hit_action} DSL
 * composition. Subclass {@link com.cyberday1.neoorigins.api.content.projectile.AbstractNeoProjectile},
 * register an {@link net.minecraft.world.entity.EntityType}, and pack
 * authors reference it by its registered ID.
 *
 * <p>API status: stable. Added in 2.0.
 */
package com.cyberday1.neoorigins.api.content.projectile;
