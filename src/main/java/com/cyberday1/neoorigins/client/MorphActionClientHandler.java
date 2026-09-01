package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.compat.animation.MorphAnimationBridges;
import com.cyberday1.neoorigins.power.morph.MorphEntityEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * Client end of the two morph action verbs: {@code neoorigins:trigger_morph_animation}
 * and {@code neoorigins:morph_entity_event}.
 *
 * <p>Both verbs are server-triggered and client-executed. The thing they act on —
 * the morph dummy from {@code MorphRenderHandler} — only exists on the client, one
 * private instance per observing client, so the server can do nothing but name the
 * player and let each client redirect onto its own copy.
 *
 * <p>This class is the dist trampoline. {@code NeoOriginsNetwork} is common-side and
 * must not carry constant-pool references to client-only types (RuntimeDistCleaner
 * walks them during dedicated-server verification), so its handlers guard on
 * {@code FMLEnvironment.dist} and call in here, where {@code Minecraft} and
 * {@code ClientLevel} are safe to name. Same arrangement as
 * {@code ClientOriginState.openEditorScreen}.
 */
public final class MorphActionClientHandler {

    private MorphActionClientHandler() {}

    /**
     * Trigger (or stop) {@code animation} on the morph dummy for {@code entityId}.
     *
     * <p>Silent when the player is not morphed, the morph's entity is not animated
     * by any bridged library, or the animation name is not one the mob's own author
     * registered as triggerable. That last case is not something we can detect:
     * GeckoLib's {@code tryTriggerAnimation} returns void and simply does nothing
     * for an unregistered name.
     */
    public static void triggerAnimation(int entityId, @Nullable String controller,
                                        String animation, boolean stop) {
        Entity dummy = dummyFor(entityId);
        if (dummy == null) return;
        MorphAnimationBridges.trigger(dummy, controller, animation, stop);
    }

    /**
     * Run {@code handleEntityEvent(event)} on the morph dummy for {@code entityId}.
     *
     * <p>The range/death check is repeated here even though the parser already made
     * it: a payload is an untrusted input path, and the byte-3 rule is a safety
     * limit protecting third-party client-side death listeners, so it has to hold
     * on the side that would actually fire the event.
     */
    public static void entityEvent(int entityId, byte event) {
        if (!MorphEntityEvents.isDispatchable(event)) return;
        Entity dummy = dummyFor(entityId);
        if (dummy == null) return;
        dummy.handleEntityEvent(event);
    }

    /** The morph dummy for a player entity id on this client, or null. */
    @Nullable
    private static Entity dummyFor(int entityId) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return null;
        // The morph is keyed by the PLAYER's entity id; the dummy has an id of its
        // own that the server has never seen.
        if (!(level.getEntity(entityId) instanceof Player player)) return null;
        return MorphRenderHandler.activeDummy(player);
    }
}
