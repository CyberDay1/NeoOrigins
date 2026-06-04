package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Set;

/**
 * Multiplies the player's mining speed, optionally restricted to blocks in a
 * given block tag (or matching a single block id). When {@code block_tag} is
 * absent or blank the multiplier applies to every block.
 *
 * <p>Applied through {@code PlayerEvent.BreakSpeed} (see
 * {@link com.cyberday1.neoorigins.event.BreakSpeedModifierEvents}) rather than
 * the vanilla {@code minecraft:player.block_break_speed} attribute. The attribute
 * is global by construction — it scales <i>all</i> block breaking and cannot read
 * the block being mined — so it could never honour {@code block_tag}. The break
 * event carries the target {@link net.minecraft.world.level.block.state.BlockState},
 * so per-tag filtering is possible there. The event fires client-side for the local
 * player too, so the mining animation predicts at the boosted speed; the power's
 * config is encoded into a capability tag ({@link #CAPABILITY_PREFIX}) so the client
 * can reach the same multiplier without extra sync state, mirroring
 * {@link BareHandToolPower}.
 *
 * <p>Multiple active break_speed_modifier powers whose filters match the same block
 * stack multiplicatively (1.5x * 2.0x = 3.0x).
 */
public class BreakSpeedModifierPower extends PowerType<BreakSpeedModifierPower.Config> {

    /** Capability-tag prefix; payload is {@code <multiplier>|<block_tag>} (tag may be empty). */
    public static final String CAPABILITY_PREFIX = "break_speed_modifier|";

    public record Config(float multiplier, String blockTag, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("multiplier", 2.0f).forGetter(Config::multiplier),
            Codec.STRING.optionalFieldOf("block_tag", "").forGetter(Config::blockTag),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) {
        return Set.of(CAPABILITY_PREFIX + config.multiplier() + "|" + config.blockTag());
    }
}
