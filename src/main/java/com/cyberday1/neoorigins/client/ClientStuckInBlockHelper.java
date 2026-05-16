package com.cyberday1.neoorigins.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Trampoline that hides a {@code LocalPlayer} reference behind a separate
 * class whose bytecode is only verified on the client.
 *
 * <p>{@code EntityMakeStuckInBlockMixin} / {@code EntityBlockSpeedFactorMixin}
 * run as common mixins so they can gate slowdown for both
 * {@code ServerPlayer} (server authority) and {@code LocalPlayer} (client
 * prediction, prevents rubberband). But the moment a common mixin's
 * bytecode contains
 * {@code instanceof net/minecraft/client/player/LocalPlayer}, Mixin
 * resolves the type during the transform pass and the dedicated server
 * crashes at boot with {@code ClassMetadataNotFoundException} because
 * the client distribution isn't on its classpath.
 *
 * <p>The fix: move the {@code LocalPlayer} reference into this helper.
 * The mixins call these methods only inside {@code level().isClientSide()},
 * so the helper class is loaded lazily and only ever on the physical
 * client. Server-side dispatch never touches this class, never resolves
 * {@code LocalPlayer}, server boots cleanly.
 *
 * <p>Same pattern as {@code feedback_new_clientclass_opcode}: keep the
 * {@code NEW}/{@code INSTANCEOF}/{@code GETSTATIC} of client-only types
 * inside lazy-verified method bodies on a class that only the client
 * reaches.
 */
public final class ClientStuckInBlockHelper {

    private ClientStuckInBlockHelper() {}

    /**
     * Client-side prediction for {@code makeStuckInBlock}: cobweb affinity
     * (cobweb only) or the unrestricted no_slowdown capability (any stuck
     * block). Tag-restricted no_slowdown stays server-authoritative.
     */
    public static boolean shouldSkipStuckOnClient(Entity self, BlockState state) {
        if (!(self instanceof net.minecraft.client.player.LocalPlayer)) return false;
        if (state.is(Blocks.COBWEB)
            && ClientActivePowers.hasCapability("cobweb_affinity")) {
            return true;
        }
        return ClientActivePowers.hasCapability("no_slowdown");
    }

    /**
     * Client-side prediction for the soul-sand / honey-block walk
     * slowdown ({@code getBlockSpeedFactor}). Only the unrestricted
     * no_slowdown variant predicts locally.
     */
    public static boolean shouldSkipSpeedFactorOnClient(Entity self) {
        return self instanceof net.minecraft.client.player.LocalPlayer
            && ClientActivePowers.hasCapability("no_slowdown");
    }
}
