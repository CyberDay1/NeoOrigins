package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Set;

/**
 * Makes the player's empty hand behave like a specific vanilla tool for
 * block-break purposes — tool-tier drop eligibility and break speed both
 * match the configured tool item.
 *
 * <p>Configure by pointing at any tool item ID: {@code minecraft:stone_pickaxe},
 * {@code minecraft:iron_axe}, {@code minecraft:diamond_shovel}, etc.
 * The runtime looks up the item's tool component to determine which blocks
 * qualify and at what speed. An origin can stack multiple instances to
 * emulate several tool types simultaneously (e.g. a miner + lumberjack hybrid).
 *
 * <p>Emits a capability tag of the form {@code bare_hand_tool:<tool_id>}, which
 * both server and client can query — the tool ID is encoded in the tag so the
 * client-side break-speed predictor and the server-side harvest check can
 * reach the same answer without extra sync state.
 *
 * <p>Wired via {@link com.cyberday1.neoorigins.event.BareHandToolEvents} on
 * the NeoForge event bus. No mixins required — uses
 * {@code PlayerEvent.HarvestCheck} + {@code PlayerEvent.BreakSpeed}.
 */
public class BareHandToolPower extends PowerType<BareHandToolPower.Config> {

    private static final Identifier DEFAULT_TOOL =
        Identifier.fromNamespaceAndPath("minecraft", "stone_pickaxe");

    public record Config(Identifier tool, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.optionalFieldOf("tool", DEFAULT_TOOL).forGetter(Config::tool),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) {
        return Set.of("bare_hand_tool:" + config.tool().toString());
    }

    /** Prefix used for capability-tag encoding of the configured tool ID. */
    public static final String CAPABILITY_PREFIX = "bare_hand_tool:";
}
