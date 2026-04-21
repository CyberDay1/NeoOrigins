package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.Optional;

public class DamageInBiomePower extends PowerType<DamageInBiomePower.Config> {

    /**
     * Accepts either a single {@code biome_tag} (TagKey Identifier) or a
     * {@code biomes} list of specific biome IDs. At least one must be supplied;
     * if both are present, both match paths are checked (union).
     */
    public record Config(Optional<Identifier> biomeTag, Optional<List<Identifier>> biomes,
                         float damagePerSecond, String damageType, String type)
            implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.optionalFieldOf("biome_tag").forGetter(Config::biomeTag),
            Identifier.CODEC.listOf().optionalFieldOf("biomes").forGetter(Config::biomes),
            Codec.FLOAT.optionalFieldOf("damage_per_second", 1.0f).forGetter(Config::damagePerSecond),
            Codec.STRING.optionalFieldOf("damage_type", "generic").forGetter(Config::damageType),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (player.tickCount % 20 != 0) return;
        if (!(player.level() instanceof ServerLevel sl)) return;
        Holder<Biome> biome = sl.getBiome(player.blockPosition());

        boolean match = false;
        if (config.biomeTag().isPresent()) {
            TagKey<Biome> tag = TagKey.create(Registries.BIOME, config.biomeTag().get());
            if (biome.is(tag)) match = true;
        }
        if (!match && config.biomes().isPresent()) {
            for (Identifier id : config.biomes().get()) {
                if (biome.is(ResourceKey.create(Registries.BIOME, id))) {
                    match = true;
                    break;
                }
            }
        }
        if (match) {
            player.hurt(player.damageSources().generic(), config.damagePerSecond());
        }
    }
}
