package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * After sneaking for a threshold number of ticks, grants invisibility and mob-ignore.
 * Clears when the player stops sneaking.
 */
public class StealthPower extends PowerType<StealthPower.Config> {

    private static final Map<UUID, Integer> SNEAK_TICKS = new ConcurrentHashMap<>();

    public record Config(int activationTicks, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("activation_ticks", 200).forGetter(Config::activationTicks),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        UUID id = player.getUUID();
        if (player.isShiftKeyDown()) {
            int ticks = SNEAK_TICKS.merge(id, 1, Integer::sum);
            if (ticks >= config.activationTicks()) {
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, true, false));
            }
        } else {
            SNEAK_TICKS.remove(id);
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        SNEAK_TICKS.remove(player.getUUID());
    }
}
