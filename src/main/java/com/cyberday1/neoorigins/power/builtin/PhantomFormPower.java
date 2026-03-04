package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class PhantomFormPower extends PowerType<PhantomFormPower.Config> {

    public record Config(
        boolean invisibility,
        boolean noGravity,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.BOOL.optionalFieldOf("invisibility", true).forGetter(Config::invisibility),
            Codec.BOOL.optionalFieldOf("no_gravity", true).forGetter(Config::noGravity),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (config.invisibility()) {
            var existing = player.getEffect(MobEffects.INVISIBILITY);
            if (existing == null || existing.getDuration() < 210) {
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 300, 0, true, false));
            }
        }
        if (config.noGravity()) {
            player.setNoGravity(true);
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        if (config.invisibility()) {
            player.removeEffect(MobEffects.INVISIBILITY);
        }
        player.setNoGravity(false);
    }
}
