package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

public class DamageInBiomePower extends PowerType<DamageInBiomePower.Config> {

    public record Config(ResourceLocation biomeTag, float damagePerSecond, String damageType, String type)
            implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ResourceLocation.CODEC.fieldOf("biome_tag").forGetter(Config::biomeTag),
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
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        TagKey<Biome> tag = TagKey.create(Registries.BIOME, config.biomeTag());
        Holder<Biome> biome = sl.getBiome(player.blockPosition());
        if (biome.is(tag)) {
            player.hurt(player.damageSources().generic(), config.damagePerSecond());
        }
    }
}
