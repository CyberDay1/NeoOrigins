package com.cyberday1.neoorigins.util;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Restores the vanilla sneak-at-a-ledge stop for players carrying one of our
 * step-height powers.
 *
 * <p>{@code Player#maybeBackOffFromEdge} probes for floor exactly
 * {@code maxUpStep()} below the feet. A step height that clears a full block
 * therefore probes past a one-block drop, finds the floor, and concludes the
 * player is not over a ledge at all — so the stop never engages and a sneaking
 * player walks off. Two reporters hit this on issue #134.
 *
 * <p>The substitution is scoped to that one probe: the player still steps up as
 * high as the power allows. Only the "would I fall?" question is asked at
 * vanilla depth.
 */
// hub: neoorigins/sneak-ledge-guard.md
public final class SneakLedgeGuard {

    private SneakLedgeGuard() {}

    /**
     * The step height {@code maybeBackOffFromEdge} should search with.
     *
     * <p>Returns {@code original} unchanged unless the player is sneaking
     * <em>and</em> one of our powers is raising their step height, so a vanilla
     * player and a foreign mod's step height are both left exactly as they were.
     *
     * @param original       what {@code maxUpStep()} returned
     * @param sneaking       {@code isShiftKeyDown()}, the same flag the method's
     *                       own {@code isStayingOnGroundSurface()} gate reads
     * @param unmodifiedBase the step-height attribute's base value, i.e. what the
     *                       player would have with no modifiers at all
     * @param oursIsApplied  whether any modifier on that attribute is ours
     */
    public static float edgeProbeDepth(float original, boolean sneaking,
                                       double unmodifiedBase, boolean oursIsApplied) {
        if (!sneaking || !oursIsApplied) return original;
        // Narrow the base first so the comparison happens in the same float space
        // vanilla's own local lives in, rather than rounding a double result back.
        return Math.min(original, (float) unmodifiedBase);
    }

    /**
     * Reads the three live inputs off the player and applies
     * {@link #edgeProbeDepth}. Safe on both logical sides: step height is a
     * syncable attribute, so client and server see the same modifiers and agree
     * on the result, which is what keeps the movement prediction from fighting.
     */
    public static float edgeProbeDepth(Player player, float original) {
        AttributeInstance step = player.getAttribute(Attributes.STEP_HEIGHT);
        if (step == null) return original;
        return edgeProbeDepth(original, player.isShiftKeyDown(),
                              step.getBaseValue(), hasOurModifier(step));
    }

    private static boolean hasOurModifier(AttributeInstance step) {
        for (AttributeModifier modifier : step.getModifiers()) {
            if (NeoOrigins.MOD_ID.equals(modifier.id().getNamespace())) return true;
        }
        return false;
    }
}
