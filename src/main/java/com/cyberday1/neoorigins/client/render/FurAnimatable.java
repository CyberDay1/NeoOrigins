package com.cyberday1.neoorigins.client.render;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Lightweight singleton animatable that the Origin Furs render layer drives a
 * {@link software.bernie.geckolib.renderer.GeoObjectRenderer} with.
 *
 * <p>The fur overlay does not need per-player state for Phase 1 — the model,
 * texture and animation vary per origin (handled by {@link FurGeoModel}), not
 * per entity. A single shared instance keeps GeckoLib's animation caches warm
 * and avoids allocating a new animatable every frame.</p>
 *
 * <p>No controllers are registered; Phase 1 renders a static pose. Phase 2 may
 * add idle/walk/run controllers keyed off {@code LocalPlayer} velocity.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class FurAnimatable implements GeoAnimatable {

    public static final FurAnimatable INSTANCE = new FurAnimatable();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private FurAnimatable() {}

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // TODO(Phase 2): add idle + walk controllers once per-player animation state is wired up.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    /**
     * GeckoLib uses this as the animation clock. Phase 1 has no keyframed
     * animations, but GeckoLib still needs a monotonically increasing value, so
     * we pull {@code System.nanoTime} converted to ticks (20 tps).
     */
    @Override
    public double getTick(Object object) {
        return System.nanoTime() / 50_000_000.0;
    }
}
