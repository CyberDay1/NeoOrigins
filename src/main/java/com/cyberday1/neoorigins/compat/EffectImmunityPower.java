package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Cancels application of specific mob effects to the player.
 * Event handling is performed in OriginEventHandler (MobEffectEvent.Applicable).
 *
 * <p>{@code inverted} flips the list into an exception list (Apoli semantics):
 * the player is immune to every effect EXCEPT those listed — so
 * {@code inverted: true} with an empty list means immunity to all effects
 * (e.g. Chaotic Chemist's Immunity Shot, gated by a power condition).
 */
public class EffectImmunityPower extends PowerType<EffectImmunityPower.Config> {

    public record Config(List<String> effects, boolean inverted, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.listOf().optionalFieldOf("effects", List.of()).forGetter(Config::effects),
            Codec.BOOL.optionalFieldOf("inverted", false).forGetter(Config::inverted),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));

        /** True when this power blocks the given effect id (list membership, XOR inverted). */
        public boolean blocks(String effectId) {
            return inverted != effects.contains(effectId);
        }
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    // Effect cancellation is handled via MobEffectEvent.Applicable in OriginEventHandler
    @Override public void onGranted(ServerPlayer player, Config config) {}
    @Override public void onRevoked(ServerPlayer player, Config config) {}
}
