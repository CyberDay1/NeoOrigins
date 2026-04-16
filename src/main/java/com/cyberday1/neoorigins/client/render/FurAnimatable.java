package com.cyberday1.neoorigins.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Lightweight singleton animatable that the Origin Furs render layer drives a
 * {@link software.bernie.geckolib.renderer.GeoObjectRenderer} with.
 *
 * <p>The fur overlay does not need per-player state — the model, texture and
 * animation vary per origin (handled by {@link FurGeoModel}), not per entity.
 * A single shared instance keeps GeckoLib's animation caches warm and avoids
 * allocating a new animatable every frame.</p>
 *
 * <p>Phase 2b adds idle / walk / jump controllers keyed off the local player's
 * velocity and {@code onGround} state. Because Phase 1 already gates rendering
 * to the local player only (see {@code PlayerFurRenderLayer}), the state
 * handler reads {@code Minecraft.getInstance().player} directly — no extra
 * plumbing is required to thread the player through the model. If a future
 * phase renders remote players too, the singleton pattern will need to be
 * revisited so controller state doesn't smear between players.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class FurAnimatable implements GeoAnimatable {

    public static final FurAnimatable INSTANCE = new FurAnimatable();

    /** Threshold (blocks/tick, squared) above which the player is considered to be walking. */
    private static final double WALK_SPEED_SQ_THRESHOLD = 1.0E-4;

    private static final RawAnimation IDLE_ANIM =
        RawAnimation.begin().thenLoop("animation.neoorigins.feline.idle");
    private static final RawAnimation WALK_ANIM =
        RawAnimation.begin().thenLoop("animation.neoorigins.feline.walk");
    private static final RawAnimation JUMP_ANIM =
        RawAnimation.begin().thenPlay("animation.neoorigins.feline.jump");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private FurAnimatable() {}

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "fur_pose", 4, this::handleAnimation));
    }

    /**
     * Chooses which raw animation to play based on the local player's current
     * movement state. The render layer only invokes the renderer for the local
     * player, so reading {@code mc.player} here is equivalent to inspecting
     * the entity currently being rendered.
     */
    private PlayState handleAnimation(software.bernie.geckolib.animation.AnimationState<FurAnimatable> state) {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null) {
            state.setAnimation(IDLE_ANIM);
            return PlayState.CONTINUE;
        }

        if (!player.onGround()) {
            state.setAnimation(JUMP_ANIM);
            return PlayState.CONTINUE;
        }

        // Use horizontal delta-movement rather than getDeltaMovement().lengthSqr() so that
        // standing still on the ground (where vertical velocity oscillates due to gravity
        // clamping) still reads as idle.
        double dx = player.getX() - player.xOld;
        double dz = player.getZ() - player.zOld;
        if ((dx * dx + dz * dz) > WALK_SPEED_SQ_THRESHOLD) {
            state.setAnimation(WALK_ANIM);
        } else {
            state.setAnimation(IDLE_ANIM);
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    /**
     * GeckoLib uses this as the animation clock. Phase 2b keyframed animations
     * still need a monotonically increasing tick, so we pull
     * {@code System.nanoTime} converted to ticks (20 tps).
     */
    @Override
    public double getTick(Object object) {
        return System.nanoTime() / 50_000_000.0;
    }
}
