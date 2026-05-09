package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;

/**
 * Prevents the player from drowning underwater by telling vanilla the player
 * can breathe via {@link LivingBreatheEvent}. This avoids fighting with
 * vanilla's {@code baseTick} air management and eliminates bubble bar flicker.
 */
public class WaterBreathingPower extends PowerType<WaterBreathingPower.Config> {

    public record Config(String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @EventBusSubscriber(modid = NeoOrigins.MOD_ID)
    public static final class Handler {
        @SubscribeEvent
        public static void onBreathe(LivingBreatheEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            if (!sp.isUnderWater()) return;

            boolean[] has = {false};
            ActiveOriginService.forEachOfType(sp, WaterBreathingPower.class, cfg -> has[0] = true);
            if (!has[0]) return;

            // Tell vanilla the player can breathe — vanilla will refill air naturally.
            event.setCanBreathe(true);
            event.setRefillAirAmount(4);
        }
    }
}
