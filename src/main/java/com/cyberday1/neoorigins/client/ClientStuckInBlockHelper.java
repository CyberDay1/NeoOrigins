package com.cyberday1.neoorigins.client;

import net.minecraft.world.entity.Entity;

/**
 * Trampoline that hides a {@code LocalPlayer} reference behind a separate
 * class whose bytecode is only verified on the client.
 *
 * <p>{@code EntityMakeStuckInBlockMixin} runs as a common mixin so it can
 * gate cobweb slowdown for both {@code ServerPlayer} (server authority)
 * and {@code LocalPlayer} (client prediction, prevents rubberband). But
 * the moment a common mixin's bytecode contains
 * {@code instanceof net/minecraft/client/player/LocalPlayer}, Mixin
 * resolves the type during the transform pass and the dedicated server
 * crashes at boot with {@code ClassMetadataNotFoundException} because
 * the client distribution isn't on its classpath.
 *
 * <p>The fix: move the {@code LocalPlayer} reference into this helper.
 * The mixin calls {@link #shouldSkipCobwebStuckOnClient(Entity)} only
 * inside {@code level().isClientSide()}, so the helper class is loaded
 * lazily and only ever on the physical client. Server-side dispatch
 * never touches this class, never resolves {@code LocalPlayer},
 * server boots cleanly.
 *
 * <p>Same pattern as {@code feedback_new_clientclass_opcode}: keep the
 * {@code NEW}/{@code INSTANCEOF}/{@code GETSTATIC} of client-only types
 * inside lazy-verified method bodies on a class that only the client
 * reaches.
 */
public final class ClientStuckInBlockHelper {

    private ClientStuckInBlockHelper() {}

    public static boolean shouldSkipCobwebStuckOnClient(Entity self) {
        return self instanceof net.minecraft.client.player.LocalPlayer
            && ClientActivePowers.hasCapability("cobweb_affinity");
    }
}
