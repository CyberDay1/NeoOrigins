package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Listed mob types do not aggro or target this player. They ignore them as if they were invisible.
 * Different from scare_entities — they don't flee, they simply never target this player.
 * Handled via LivingChangeTargetEvent in OriginEventHandler.
 */
public class MobsIgnorePlayerPower extends PowerType<MobsIgnorePlayerPower.Config> {

    public record Config(List<ResourceLocation> entityTypes, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf("entity_types", List.of()).forGetter(Config::entityTypes),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override public void onGranted(ServerPlayer player, Config config) {}
    @Override public void onRevoked(ServerPlayer player, Config config) {}
}
