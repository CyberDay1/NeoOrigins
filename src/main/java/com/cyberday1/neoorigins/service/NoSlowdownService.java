package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.power.builtin.NoSlowdownPower;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-authoritative resolver for {@link NoSlowdownPower}: does the
 * player carry a no_slowdown power whose {@code block_tag} matches the
 * given slowdown block? An absent/blank tag matches everything.
 *
 * <p>The unrestricted (all-blocks) case is also mirrored client-side via
 * the {@code no_slowdown} capability for movement prediction; this class
 * is the authority for both the unrestricted and tag-restricted cases.
 */
public final class NoSlowdownService {

    private NoSlowdownService() {}

    public static boolean skipsSlowdown(ServerPlayer player, BlockState state) {
        return ActiveOriginService.has(player, NoSlowdownPower.class,
            config -> matches(config, state));
    }

    private static boolean matches(NoSlowdownPower.Config config, BlockState state) {
        if (config.appliesToAllBlocks()) return true;
        Identifier tagId = Identifier.tryParse(config.blockTag().get());
        if (tagId == null) return false;
        return state.is(TagKey.create(Registries.BLOCK, tagId));
    }
}
