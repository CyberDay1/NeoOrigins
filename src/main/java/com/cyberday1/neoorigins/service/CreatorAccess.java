package com.cyberday1.neoorigins.service;

import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;

/**
 * Server-authoritative gate for the 2.1 in-game creator. The client UI is
 * gated too, but this is the truth: it guards opening the creator <em>and</em>
 * every write/apply payload, because the creator mutates shared world
 * datapacks.
 *
 * <p>Game-master permission (the {@code REQUIRE_GM} gate every admin command in
 * this codebase uses — 26.1 {@code Permissions.COMMANDS_GAMEMASTER}) OR
 * creative mode (covers singleplayer/builders without an explicit op level).
 */
public final class CreatorAccess {

    private static final java.util.function.Predicate<net.minecraft.commands.CommandSourceStack> GM =
        Commands.hasPermission(new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER));

    private CreatorAccess() {}

    public static boolean canUse(ServerPlayer sp) {
        return sp.isCreative() || GM.test(sp.createCommandSourceStack());
    }
}
