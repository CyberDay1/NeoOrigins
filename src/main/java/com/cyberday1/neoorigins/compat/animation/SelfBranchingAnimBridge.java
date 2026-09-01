package com.cyberday1.neoorigins.compat.animation;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Reflective bridge to an animation library whose animatable interface exposes
 * {@code triggerAnim(String controller, String animation)} /
 * {@code stopTriggeredAnim(String, String)} and <em>branches on the side itself</em>.
 *
 * <p>"Self-branching" is the property this whole class depends on. GeckoLib's
 * {@code GeoEntity#triggerAnim} tests {@code level().isClientSide()} and, on the
 * client arm, calls
 * {@code getAnimatableInstanceCache().getManagerForId(id).tryTriggerAnimation(...)}
 * directly — no packet, no server round trip (verified by disassembling
 * geckolib-neoforge-1.21.1-4.9.2). Only the server arm goes near GeckoLib's own
 * networking. The morph dummy is a client-side entity created from a
 * {@code ClientLevel}, so calling the public interface method on it lands on the
 * direct arm and never touches a network stack that has no idea our dummy exists.
 *
 * <p>Follows the {@code compat/pehkui/PehkuiBridge} pattern: static init, mod
 * gate, {@code Class.forName}, cached {@link Method}s, warn-once on failure,
 * silent no-op when absent. No animation-library type is imported at the top of
 * this file (the {@code GeckoLibCompat} javadoc's standing requirement).
 *
 * <p><b>Why a candidate list of interface names.</b> The interface moved package
 * between versions — GeckoLib 4.9.2 (MC 1.21.1) and 5.4.2 are
 * {@code software.bernie.geckolib.animatable.GeoEntity}, while 5.5.2/5.5.3
 * (MC 26.1/26.2) are {@code com.geckolib.animatable.GeoEntity}. Both were
 * confirmed by disassembling the shipped jars, and both carry the identical
 * two-arg {@code triggerAnim}/{@code stopTriggeredAnim} pair. Probing an ordered
 * list means the eventual 26.x port needs no code change here instead of
 * silently degrading to "GeckoLib absent".
 *
 * <p><b>Adding another library later.</b> AzureLib has the same self-branching
 * shape, so it is one more {@code new SelfBranchingAnimBridge(...)} constant in
 * {@link MorphAnimationBridges} — nothing here needs to change. Citadel does
 * <em>not</em> fit: its animations are {@code int} ids that need a per-tick pump,
 * so it would be a different bridge implementation appended to the same list.
 * Neither is implemented today.
 */
public final class SelfBranchingAnimBridge {

    private final String modId;
    private final boolean loaded;
    @Nullable private final Class<?> animatable;
    @Nullable private final Method triggerAnim;
    @Nullable private final Method stopTriggeredAnim;

    private volatile boolean warnedOnce = false;

    /**
     * @param modId               mod id to gate on, checked through {@code ModList}.
     * @param interfaceCandidates fully-qualified animatable-interface names to try
     *                            in order; the first one that resolves AND exposes
     *                            both methods wins.
     */
    SelfBranchingAnimBridge(String modId, boolean present, String... interfaceCandidates) {
        this.modId = modId;
        Class<?> found = null;
        Method trigger = null;
        Method stop = null;
        if (present) {
            for (String candidate : interfaceCandidates) {
                try {
                    Class<?> cls = Class.forName(candidate);
                    trigger = cls.getMethod("triggerAnim", String.class, String.class);
                    stop = cls.getMethod("stopTriggeredAnim", String.class, String.class);
                    found = cls;
                    break;
                } catch (Throwable ignored) {
                    // Wrong package for this version, or the method pair moved —
                    // keep probing the remaining candidates.
                    trigger = null;
                    stop = null;
                }
            }
            if (found == null) {
                NeoOrigins.LOGGER.warn(
                    "{} is present but its animatable interface could not be resolved from {}; "
                        + "trigger_morph_animation will no-op for it.",
                    modId, String.join(", ", interfaceCandidates));
            } else {
                NeoOrigins.LOGGER.info("{} detected — morph animation triggers bound to {}.",
                    modId, found.getName());
            }
        }
        this.animatable = found;
        this.triggerAnim = trigger;
        this.stopTriggeredAnim = stop;
        this.loaded = found != null;
    }

    /** True if the mod is loaded and the reflective bind succeeded. */
    public boolean isAvailable() {
        return loaded;
    }

    /** The mod id this bridge speaks for, for logging. */
    public String modId() {
        return modId;
    }

    /**
     * Trigger (or stop) a triggerable animation on {@code target}.
     *
     * <p>Returns {@code false} — without logging — when this bridge is unbound or
     * the entity is not one of its animatables, so a caller can fan out over
     * several bridges and let the one that recognises the entity answer.
     *
     * @param target     the entity to animate. Must be client-side; passing a
     *                   server entity would take the library's networking arm.
     * @param controller controller name, or {@code null} to let the library
     *                   search every controller (GeckoLib's one-arg
     *                   {@code tryTriggerAnimation} path).
     * @param animation  the triggerable animation name.
     * @param stop       {@code true} to call {@code stopTriggeredAnim} instead.
     * @return true if the call was dispatched.
     */
    public boolean trigger(Entity target, @Nullable String controller, String animation, boolean stop) {
        if (!loaded || target == null || animation == null) return false;
        if (!animatable.isInstance(target)) return false;
        try {
            Method method = stop ? stopTriggeredAnim : triggerAnim;
            method.invoke(target, controller, animation);
            return true;
        } catch (Throwable t) {
            if (!warnedOnce) {
                warnedOnce = true;
                NeoOrigins.LOGGER.warn("{} animation bridge call failed: {}", modId, t.toString());
            }
            return false;
        }
    }
}
