package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Cancels the hunger exhaustion vanilla charges for specific actions:
 * {@code sprint} (sprinting on the ground), {@code mining} (breaking a block),
 * {@code jump} (any jump, sprinting or not) and {@code swim} (swimming, walking
 * underwater or wading). Each source is the one vanilla call that charges it,
 * wrapped in {@code ExhaustionMiningMixin}, {@code ExhaustionJumpMixin} and
 * {@code ExhaustionMovementMixin}, so every other source of exhaustion is left
 * alone. This is what Origins: Classes' {@code no_sprint_exhaustion} and
 * {@code no_mining_exhaustion} do.
 *
 * <p>Through 2.2.30 nothing read this power, so it loaded and did nothing.
 */
public class ExhaustionFilterPower extends PowerType<ExhaustionFilterPower.Config> {

    public record Config(List<String> sources, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.listOf().optionalFieldOf("sources", List.of("sprint")).forGetter(Config::sources),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    public static final java.util.Set<String> SOURCES = java.util.Set.of("sprint", "mining", "jump", "swim");

    /** True when one of the player's active exhaustion_filter powers cancels {@code source}. */
    public static boolean filters(net.minecraft.world.entity.player.Player player, String source) {
        return player instanceof net.minecraft.server.level.ServerPlayer sp
            && com.cyberday1.neoorigins.service.ActiveOriginService.has(
                sp, ExhaustionFilterPower.class, cfg -> cfg.sources().contains(source));
    }
}
