package com.cyberday1.neoorigins.api.content.projectile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * Base class for custom projectile entities that integrate with the
 * NeoOrigins 2.0 action/event pipeline.
 *
 * <p>Extends {@link ThrowableItemProjectile} and funnels the vanilla
 * {@link #onHit(HitResult)} call into {@link #onImpact(ServerLevel, HitResult)}
 * — a simpler hook that runs server-side only, passes the level, and
 * discards the projectile afterward so subclasses don't have to remember
 * to clean up.
 *
 * <p><b>What this class gives you:</b>
 * <ul>
 *   <li>Server-thread-only impact callback — no client/server check needed in subclass</li>
 *   <li>Automatic {@code discard()} after impact, matching vanilla splash-potion semantics</li>
 *   <li>Inherits vanilla throwable physics (gravity, drag, collision)</li>
 * </ul>
 *
 * <p><b>What you still need to do in your subclass:</b>
 * <ul>
 *   <li>Override {@link #onImpact(ServerLevel, HitResult)} with the impact behavior</li>
 *   <li>Override {@link #getDefaultItem()} to return the fallback visual item (used when
 *       no custom renderer is registered — pack authors see a thrown-item visual)</li>
 *   <li>Register an {@link EntityType} for the subclass and its renderer. See
 *       {@code ModEntities} for the registration pattern.</li>
 * </ul>
 *
 * <p>Pack authors reference the registered entity via
 * {@code "entity_type": "mymod:my_projectile"} inside a
 * {@code neoorigins:spawn_projectile} action. The DSL-side
 * {@code on_hit_action} still fires independently of your subclass's
 * {@code onImpact} — both run on hit, the subclass first.
 *
 * <p>API status: stable. Added in 2.0.
 */
public abstract class AbstractNeoProjectile extends ThrowableItemProjectile {

    protected AbstractNeoProjectile(EntityType<? extends AbstractNeoProjectile> type, Level level) {
        super(type, level);
    }

    /**
     * Server-side impact handler. Called exactly once per projectile, on
     * the server thread, with the level already cast. The projectile is
     * {@linkplain #discard() discarded} automatically after this method
     * returns.
     *
     * @param level  the server level where the impact occurred
     * @param result the vanilla hit result — check {@code instanceof BlockHitResult} or
     *               {@code EntityHitResult} and cast for the details you need
     */
    protected abstract void onImpact(ServerLevel level, HitResult result);

    @Override
    public final Item getDefaultItem() {
        return getVisualItem();
    }

    /** Which item to render when no custom renderer is registered. */
    protected abstract Item getVisualItem();

    @Override
    protected final void onHit(HitResult result) {
        super.onHit(result);
        if (!(this.level() instanceof ServerLevel sl)) return;
        try {
            onImpact(sl, result);
        } finally {
            this.discard();
        }
    }
}
