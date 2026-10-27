package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * When a log block is broken, BFS/DFS upward to break connected logs.
 * Skipped if the player is sneaking, and by default unless the log was broken
 * with an axe ({@code require_axe}).
 * Handled via BlockEvent.BreakEvent in WorldPowerEvents.
 */
public class TreeFellingPower extends PowerType<TreeFellingPower.Config> {

    public record Config(int maxBlocks, boolean requireAxe, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("max_blocks", 64).forGetter(Config::maxBlocks),
            Codec.BOOL.optionalFieldOf("require_axe", true).forGetter(Config::requireAxe),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }
}
