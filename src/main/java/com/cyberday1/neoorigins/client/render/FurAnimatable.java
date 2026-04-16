package com.cyberday1.neoorigins.client.render;

import com.cyberday1.neoorigins.client.ClientOriginFurCache;
import com.cyberday1.neoorigins.client.ClientOriginState;
import com.cyberday1.neoorigins.client.RemoteOriginCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 *
 * <p><b>Per-origin animation names (Phase 2c):</b> each origin ships its own
 * animation file using the naming convention
 * {@code animation.neoorigins.<origin>.{idle,walk,jump}}. The state handler
 * derives the origin's path segment from the rendered player's current origin
 * (via {@link ClientOriginState} for the local player, {@link RemoteOriginCache}
 * for remote players) and builds {@link RawAnimation} instances on demand, cached
 * per base name to keep per-frame allocation free. Falls back to the original
 * feline names if no origin can be resolved (e.g. first-frame race).</p>
 */
@OnlyIn(Dist.CLIENT)
public final class FurAnimatable implements GeoAnimatable {

    /** Threshold (blocks/tick, squared) above which the player is considered to be walking. */
    private static final double WALK_SPEED_SQ_THRESHOLD = 1.0E-4;

    /** Fallback base name (feline) when no origin can be resolved. */
    private static final String FALLBACK_BASE = "feline";

    /** Layers consulted in priority order when picking which origin drives animation. */
    private static final ResourceLocation NEO_ORIGIN_LAYER =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "origin");
    private static final ResourceLocation ORIGINS_ORIGIN_LAYER =
        ResourceLocation.fromNamespaceAndPath("origins", "origin");

    /**
     * RawAnimation triple (idle / walk / jump) built on demand for a given base
     * name. Cached so we never allocate during the hot render path once a base
     * name has been observed.
     */
    private record AnimTriple(RawAnimation idle, RawAnimation walk, RawAnimation jump) {
        static AnimTriple build(String base) {
            return new AnimTriple(
                RawAnimation.begin().thenLoop("animation.neoorigins." + base + ".idle"),
                RawAnimation.begin().thenLoop("animation.neoorigins." + base + ".walk"),
                RawAnimation.begin().thenPlay("animation.neoorigins." + base + ".jump")
            );
        }
    }

    /** Shared across every {@link FurAnimatable} so all players share the same cached triples. */
    private static final Map<String, AnimTriple> ANIM_CACHE = new ConcurrentHashMap<>();

    private static AnimTriple animsFor(String base) {
        return ANIM_CACHE.computeIfAbsent(base, AnimTriple::build);
    }

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
        AnimTriple anims = animsFor(resolveBaseName(player));

        if (player == null) {
            state.setAnimation(anims.idle());
            return PlayState.CONTINUE;
        }

        if (!player.onGround()) {
            state.setAnimation(anims.jump());
            return PlayState.CONTINUE;
        }

        // Use horizontal delta-movement rather than getDeltaMovement().lengthSqr() so that
        // standing still on the ground (where vertical velocity oscillates due to gravity
        // clamping) still reads as idle.
        double dx = player.getX() - player.xOld;
        double dz = player.getZ() - player.zOld;
        if ((dx * dx + dz * dz) > WALK_SPEED_SQ_THRESHOLD) {
            state.setAnimation(anims.walk());
        } else {
            state.setAnimation(anims.idle());
        }
        return PlayState.CONTINUE;
    }

    /**
     * Derive the animation base name ({@code feline}, {@code draconic}, …) from
     * the player's current origin. Mirrors {@code PlayerFurRenderLayer}'s origin
     * resolution so the animation for the same frame matches the geo model
     * picked there. Returns {@link #FALLBACK_BASE} if no origin can be resolved
     * — animations keyed to that base still play because the feline file is
     * guaranteed to exist.
     */
    private static String resolveBaseName(AbstractClientPlayer player) {
        if (player == null) return FALLBACK_BASE;

        Minecraft mc = Minecraft.getInstance();
        Map<ResourceLocation, ResourceLocation> origins;
        if (mc.player != null && mc.player.getUUID().equals(player.getUUID())) {
            origins = ClientOriginState.getOrigins();
        } else {
            origins = RemoteOriginCache.get(player.getUUID());
        }
        if (origins.isEmpty()) return FALLBACK_BASE;

        ResourceLocation preferred = origins.get(NEO_ORIGIN_LAYER);
        if (preferred != null && ClientOriginFurCache.get(preferred) != null) {
            return basePath(preferred);
        }
        ResourceLocation compat = origins.get(ORIGINS_ORIGIN_LAYER);
        if (compat != null && ClientOriginFurCache.get(compat) != null) {
            return basePath(compat);
        }
        for (var entry : origins.entrySet()) {
            if (entry.getKey().equals(NEO_ORIGIN_LAYER) || entry.getKey().equals(ORIGINS_ORIGIN_LAYER)) continue;
            if (ClientOriginFurCache.get(entry.getValue()) != null) {
                return basePath(entry.getValue());
            }
        }
        return FALLBACK_BASE;
    }

    /**
     * Reduce an origin ResourceLocation to the trailing path segment used as
     * the animation base name: {@code neoorigins:draconic} → {@code draconic},
     * {@code origins:feline} → {@code feline}. Handles nested paths defensively
     * (takes the last slash-separated segment).
     */
    private static String basePath(ResourceLocation id) {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
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
