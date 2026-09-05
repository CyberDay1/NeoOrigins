package com.cyberday1.neoorigins.compat.animation;

import com.cyberday1.neoorigins.compat.GeckoLibCompat;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The ordered set of animation libraries {@code neoorigins:trigger_morph_animation}
 * knows how to talk to, and the single fan-out entry point the client handler calls.
 *
 * <p>Today that set is exactly one entry: GeckoLib, via
 * {@link SelfBranchingAnimBridge}. The list exists so a second library is an added
 * constant rather than a rewrite — see that class's javadoc for what would and
 * would not fit (AzureLib fits; Citadel does not). Neither is implemented.
 *
 * <p>Fan-out is first-match-wins: each bridge answers {@code false} for an entity
 * it does not recognise, so the morph dummy's actual animatable interface decides
 * which bridge handles it. If none does, the call is a silent no-op — a pack that
 * morphs into a vanilla cow and asks for an animation is not doing anything wrong
 * enough to log about every time the power fires.
 */
public final class MorphAnimationBridges {

    private MorphAnimationBridges() {}

    /**
     * Bound lazily on first use, not in a static initialiser of a class the
     * network layer might touch early: {@code ModList} has to exist before
     * {@link GeckoLibCompat#isLoaded()} means anything, and this class is only
     * ever reached from a client packet handler, long after mod loading.
     */
    private static volatile List<SelfBranchingAnimBridge> bridges;

    private static List<SelfBranchingAnimBridge> bridges() {
        List<SelfBranchingAnimBridge> local = bridges;
        if (local == null) {
            synchronized (MorphAnimationBridges.class) {
                local = bridges;
                if (local == null) {
                    local = List.of(
                        // GeckoLib 4.x (MC 1.21.1) and 5.x (MC 26.x) differ only in
                        // the interface's package; both names are probed in order.
                        new SelfBranchingAnimBridge("geckolib", GeckoLibCompat.isLoaded(),
                            "software.bernie.geckolib.animatable.GeoEntity",
                            "com.geckolib.animatable.GeoEntity")
                    );
                    bridges = local;
                }
            }
        }
        return local;
    }

    /** True if at least one animation library is present and bound. */
    public static boolean anyAvailable() {
        for (SelfBranchingAnimBridge bridge : bridges()) {
            if (bridge.isAvailable()) return true;
        }
        return false;
    }

    /**
     * Ask each bound bridge, in order, to trigger (or stop) {@code animation} on
     * {@code target}; stops at the first one that recognises the entity.
     *
     * @return true if some bridge dispatched the call.
     */
    public static boolean trigger(Entity target, @Nullable String controller,
                                  String animation, boolean stop) {
        for (SelfBranchingAnimBridge bridge : bridges()) {
            if (bridge.trigger(target, controller, animation, stop)) return true;
        }
        return false;
    }
}
