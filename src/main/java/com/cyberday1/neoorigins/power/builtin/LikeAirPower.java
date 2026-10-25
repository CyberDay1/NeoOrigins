package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.Set;

/**
 * Walking-speed modifiers also apply while airborne. Origins' Avian
 * {@code like_air} power, which is Apoli's {@code attribute_modify_transfer}
 * moving every {@code movement_speed} modifier onto {@code modify_air_speed}.
 *
 * <p>Vanilla airborne control does not read the movement speed attribute at all:
 * {@code Player.getFlyingSpeed} returns a flat 0.02 (0.026 sprinting). With this
 * power, that value goes through the player's {@code movement_speed} modifiers,
 * all of them and in vanilla's order, exactly as Apoli transfers them. That
 * includes the sprint modifier, so a sprinting holder gets the vanilla sprint
 * bonus and the modifier's +30% on top, as in Origins. Creative/ability flight is
 * left alone. Applied in {@code PlayerFlyingSpeedMixin} on both sides, since
 * airborne movement is predicted by the client.
 */
public class LikeAirPower extends PowerType<LikeAirPower.Config> {

    public static final String CAPABILITY = "like_air";

    public record Config(String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) {
        return Set.of(CAPABILITY);
    }

    /**
     * {@code airSpeed} with every modifier on {@code movementSpeed} applied to it:
     * additions first, then the summed multiplied-base terms, then each
     * multiplied-total factor, which is the order vanilla attributes use.
     */
    public static float transfer(float airSpeed, AttributeInstance movementSpeed) {
        if (movementSpeed == null) return airSpeed;
        return (float) transfer(airSpeed, movementSpeed.getModifiers());
    }

    static double transfer(double base, Iterable<AttributeModifier> modifiers) {
        double value = base;
        for (AttributeModifier m : modifiers) {
            if (m.operation() == AttributeModifier.Operation.ADD_VALUE) value += m.amount();
        }
        double afterAdd = value;
        for (AttributeModifier m : modifiers) {
            if (m.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) value += afterAdd * m.amount();
        }
        for (AttributeModifier m : modifiers) {
            if (m.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) value *= 1.0 + m.amount();
        }
        return Math.max(0.0, value);
    }
}
