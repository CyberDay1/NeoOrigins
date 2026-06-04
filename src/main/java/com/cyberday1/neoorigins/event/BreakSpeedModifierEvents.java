package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.client.ClientActivePowers;
import com.cyberday1.neoorigins.power.builtin.BreakSpeedModifierPower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Applies {@link BreakSpeedModifierPower} through {@code PlayerEvent.BreakSpeed},
 * honouring its {@code block_tag} filter — something the previous attribute-based
 * implementation could not do (the {@code player.block_break_speed} attribute is
 * global and never sees the target block).
 *
 * <p>Handled on BOTH sides. The break-speed event fires client-side for the local
 * player (that is where mining destroy-progress is predicted), so a server-only
 * handler would leave the client animating at the un-boosted speed. The server
 * reads the live power configs; the client reconstructs the same multiplier + tag
 * from the synced capability tag ({@link BreakSpeedModifierPower#CAPABILITY_PREFIX}).
 * This is the same client/server-parity pattern as {@link BareHandToolEvents}.
 */
@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public final class BreakSpeedModifierEvents {

    private BreakSpeedModifierEvents() {}

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        float product = combinedMultiplier(event.getEntity(), event.getState());
        if (product != 1.0f && Float.isFinite(product)) {
            event.setNewSpeed(event.getNewSpeed() * product);
        }
    }

    /**
     * Product of every active break_speed_modifier multiplier whose filter matches
     * {@code state}. Server reads configs directly; client parses the capability tag.
     */
    private static float combinedMultiplier(Player player, BlockState state) {
        float[] product = {1.0f};
        if (player instanceof ServerPlayer sp) {
            ActiveOriginService.forEachOfType(sp, BreakSpeedModifierPower.class, cfg -> {
                if (matches(state, cfg.blockTag())) {
                    float m = cfg.multiplier();
                    if (Float.isFinite(m) && m >= 0f) product[0] *= m;
                }
            });
        } else if (player.level().isClientSide()) {
            for (String cap : ClientActivePowers.activeCapabilities()) {
                if (!cap.startsWith(BreakSpeedModifierPower.CAPABILITY_PREFIX)) continue;
                String payload = cap.substring(BreakSpeedModifierPower.CAPABILITY_PREFIX.length());
                // payload = "<multiplier>|<block_tag>"; tag may be empty (all blocks).
                String[] parts = payload.split("\\|", 2);
                float m;
                try {
                    m = Float.parseFloat(parts[0]);
                } catch (NumberFormatException e) {
                    continue;
                }
                String tag = parts.length > 1 ? parts[1] : "";
                if (matches(state, tag) && Float.isFinite(m) && m >= 0f) product[0] *= m;
            }
        }
        return product[0];
    }

    /**
     * True if {@code state} is covered by {@code spec}. Blank spec = every block.
     * A leading {@code #} forces tag-only matching; otherwise the value is tried as
     * a block tag and then as a single block id (so {@code minecraft:stone} works even
     * though no {@code minecraft:stone} block tag exists).
     */
    private static boolean matches(BlockState state, String spec) {
        if (spec == null || spec.isBlank()) return true;
        boolean explicitTag = spec.startsWith("#");
        String body = explicitTag ? spec.substring(1) : spec;
        Identifier rl = Identifier.tryParse(body);
        if (rl == null) return false;
        if (state.is(TagKey.create(Registries.BLOCK, rl))) return true;
        if (!explicitTag) {
            Block block = BuiltInRegistries.BLOCK.getOptional(rl).orElse(null);
            if (block != null && state.is(block)) return true;
        }
        return false;
    }
}
