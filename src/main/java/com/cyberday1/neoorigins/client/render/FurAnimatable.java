package com.cyberday1.neoorigins.client.render;

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
 * Per-player animatable for the fur render layer. Holds a reference to the
 * player currently being rendered so GeckoLib's animation controllers can
 * choose walk / idle / jump based on that specific entity's state.
 *
 * <p>Earlier phases used a shared singleton which worked only while fur was
 * gated to the local player. Once {@code PlayerFurRenderLayer} started
 * rendering remote players too (Phase 2a), the singleton's single controller
 * would smear state across every remote cat — they'd all play the same
 * animation at the same phase regardless of what each player was actually
 * doing. {@link PlayerFurRenderLayer} now keeps a per-UUID map of these
 * animatables so every player drives their own controller.</p>
 *
 * <p>The {@link #setCurrentPlayer(AbstractClientPlayer)} call before render
 * is important — GeckoLib's state handler runs {@link #handleAnimation} and
 * inspects the player stored here to decide the pose.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class FurAnimatable implements GeoAnimatable {

    /** Threshold (blocks/tick, squared) above which the player is considered to be walking. */
    private static final double WALK_SPEED_SQ_THRESHOLD = 1.0E-4;

    private static final RawAnimation IDLE_ANIM =
        RawAnimation.begin().thenLoop("animation.neoorigins.feline.idle");
    private static final RawAnimation WALK_ANIM =
        RawAnimation.begin().thenLoop("animation.neoorigins.feline.walk");
    private static final RawAnimation JUMP_ANIM =
        RawAnimation.begin().thenPlay("animation.neoorigins.feline.jump");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** The player currently being rendered. Set by the render layer each frame. */
    private AbstractClientPlayer currentPlayer;

    public FurAnimatable() {}

    /**
     * Point this animatable at the player about to be rendered. The state
     * handler will use {@code player}'s velocity/onGround in its next tick.
     */
    public void setCurrentPlayer(AbstractClientPlayer player) {
        this.currentPlayer = player;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "fur_pose", 4, this::handleAnimation));
    }

    private PlayState handleAnimation(software.bernie.geckolib.animation.AnimationState<FurAnimatable> state) {
        AbstractClientPlayer player = this.currentPlayer;
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

    @Override
    public double getTick(Object object) {
        return System.nanoTime() / 50_000_000.0;
    }
}
