package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Suppresses natural mob spawning within a radius of the player.
 * categories: "monster", "creature", "ambient", "water_creature", "all"
 * Handled via MobSpawnEvent.FinalizeSpawn in WorldPowerEvents.
 * Toggleable — players can turn the warding aura on/off.
 */
public class NoMobSpawnsNearbyPower extends AbstractTogglePower<NoMobSpawnsNearbyPower.Config> {

    public record Config(int radius, List<String> categories, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("radius", 48).forGetter(Config::radius),
            Codec.STRING.listOf().optionalFieldOf("categories", List.of("monster")).forGetter(Config::categories),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));

        public boolean coversAll() { return categories.stream().anyMatch("all"::equalsIgnoreCase); }
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override protected void tickEffect(ServerPlayer player, Config config) {}
    @Override protected void removeEffect(ServerPlayer player, Config config) {}
}
