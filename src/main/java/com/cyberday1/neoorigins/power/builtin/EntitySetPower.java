package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

/**
 * Pure data-holder power. Its presence in a player's active power set declares that
 * the player participates in the named UUID set — the actual set storage lives on
 * {@link com.cyberday1.neoorigins.attachment.PlayerOriginData}, and the
 * {@code origins:in_set} / {@code neoorigins:add_to_set} / {@code neoorigins:remove_from_set}
 * verbs read and mutate it.
 *
 * <p>Pack authors will typically namespace the {@code name} (e.g. {@code "mypack:kill_streak"})
 * to avoid collision with other packs — the colon is allowed and carries no mechanical meaning.
 */
public class EntitySetPower extends PowerType<EntitySetPower.Config> {

    public record Config(String name, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(Config::name),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override public void onGranted(ServerPlayer player, Config config) {}
    @Override public void onRevoked(ServerPlayer player, Config config) {}
}
