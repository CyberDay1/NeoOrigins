package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/**
 * Allows the player to walk on the surface of a fluid (water, lava, or both).
 * Uses the same mechanic as Striders — overrides
 * {@code LivingEntity.canStandOnFluid(FluidState)} via a mixin that checks
 * for the {@code walk_on_water} / {@code walk_on_lava} capabilities.
 *
 * <pre>{@code
 * { "type": "neoorigins:walk_on_fluid", "fluid": "water" }
 * { "type": "neoorigins:walk_on_fluid", "fluid": "lava" }
 * { "type": "neoorigins:walk_on_fluid" }  // both
 * }</pre>
 */
public class WalkOnFluidPower extends PowerType<WalkOnFluidPower.Config> {

    public record Config(String fluid, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("fluid", "both").forGetter(Config::fluid),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) {
        return switch (config.fluid().toLowerCase(java.util.Locale.ROOT)) {
            case "water" -> Set.of("walk_on_water");
            case "lava"  -> Set.of("walk_on_lava");
            default      -> Set.of("walk_on_water", "walk_on_lava");
        };
    }
}
