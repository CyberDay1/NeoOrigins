package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.Set;

/**
 * Negates block-induced movement slowdown:
 * <ul>
 *   <li>the {@code stuckSpeedMultiplier} velocity clamp from
 *       {@link net.minecraft.world.entity.Entity#makeStuckInBlock}
 *       (cobweb, sweet berry bush, powder snow) — via
 *       {@code EntityMakeStuckInBlockMixin}</li>
 *   <li>the sub-1.0 {@code getSpeedFactor} walk slowdown of soul sand
 *       and honey blocks — via {@code EntityBlockSpeedFactorMixin}</li>
 * </ul>
 *
 * <p>When {@code block_tag} is absent the immunity is unconditional and
 * also emits the {@code no_slowdown} capability so the client predicts
 * the movement locally (no rubberband entering a web). A tag-restricted
 * variant stays server-authoritative — the client lacks the per-power
 * config needed to evaluate the tag, so it accepts a brief correction.
 */
public class NoSlowdownPower extends PowerType<NoSlowdownPower.Config> {

    public record Config(Optional<String> blockTag, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("block_tag").forGetter(Config::blockTag),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));

        /** True when this power is an unrestricted "immune to all slowdown" variant. */
        public boolean appliesToAllBlocks() {
            return blockTag.isEmpty() || blockTag.get().isBlank();
        }
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) {
        return config.appliesToAllBlocks() ? Set.of("no_slowdown") : Set.of();
    }

    @Override public void onGranted(ServerPlayer player, Config config) {}
    @Override public void onRevoked(ServerPlayer player, Config config) {}
}
