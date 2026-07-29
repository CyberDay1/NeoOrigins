package com.cyberday1.neoorigins.power.morph;

import com.cyberday1.neoorigins.client.ClientMorphState;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * The one place that answers "what is this player morphed into", whichever
 * logical side is asking.
 *
 * <p>The two sides keep the answer in different shapes for good reasons: the
 * client is handed a morph by entity id, because that is what the render events
 * give it, while the server records the one it last resolved under the player's
 * UUID. Anything that runs on both sides — sounds, hitboxes — would otherwise
 * have to repeat that branch, and the moment two copies of it disagree the
 * player sounds like one thing and collides like another.
 *
 * <p>Importing the client class here is safe despite its package: it is a plain
 * map of {@link MorphSpec} with no client-only types in it, so a dedicated
 * server loads it fine, and never populates it because the branch that reads it
 * needs {@code isClientSide} true.
 */
public final class MorphState {

    private MorphState() {}

    /** The player's current morph, or null when they aren't morphed. */
    @Nullable
    public static MorphSpec of(Player player) {
        return player.level().isClientSide
            ? ClientMorphState.getSpec(player.getId())
            : ServerMorphState.get(player.getUUID());
    }
}
