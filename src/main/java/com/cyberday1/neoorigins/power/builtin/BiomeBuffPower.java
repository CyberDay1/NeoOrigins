package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.biome.Biome;

public class BiomeBuffPower extends PowerType<BiomeBuffPower.Config> {

    public record Config(Identifier biomeTag, Identifier effect, int amplifier, String type)
            implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.fieldOf("biome_tag").forGetter(Config::biomeTag),
            Identifier.CODEC.fieldOf("effect").forGetter(Config::effect),
            Codec.INT.optionalFieldOf("amplifier", 0).forGetter(Config::amplifier),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        TagKey<Biome> tag = TagKey.create(Registries.BIOME, config.biomeTag());
        Holder<Biome> biome = sl.getBiome(player.blockPosition());
        if (!biome.is(tag)) return;

        BuiltInRegistries.MOB_EFFECT.get(config.effect()).ifPresent(effectHolder -> {
            var existing = player.getEffect(effectHolder);
            if (existing == null || existing.getDuration() < 210) {
                player.addEffect(new MobEffectInstance(effectHolder, 300, config.amplifier(), true, false));
            }
        });
    }
}
