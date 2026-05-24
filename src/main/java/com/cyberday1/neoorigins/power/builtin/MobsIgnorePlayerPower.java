package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Listed mob types do not aggro or target this player. They ignore them as if they were invisible.
 * Different from scare_entities — they don't flee, they simply never target this player.
 *
 * <p>{@code entity_types} entries accept both raw IDs ({@code "minecraft:zombie"})
 * and tag references ({@code "#mymod:my_tag"}). An empty/omitted list matches
 * every mob.
 *
 * <p>By default a retaliation window applies — if the player has recently
 * hit the mob, the mob is allowed to target them back so combat feedback
 * loops still work. Set {@code "passive": true} to disable that and make
 * the ignore unconditional (the mob never targets this player, even if
 * the player attacks first).
 */
public class MobsIgnorePlayerPower extends PowerType<MobsIgnorePlayerPower.Config> {

    public record Config(List<String> entityTypes, boolean passive, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.listOf().optionalFieldOf("entity_types", List.of()).forGetter(Config::entityTypes),
            Codec.BOOL.optionalFieldOf("passive", false).forGetter(Config::passive),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override public void onGranted(ServerPlayer player, Config config) {}
    @Override public void onRevoked(ServerPlayer player, Config config) {}
}
