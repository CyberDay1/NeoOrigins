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
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class WaterBreathingPower extends PowerType<WaterBreathingPower.Config> {

    public record Config(String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override public void onGranted(ServerPlayer player, Config config) {}
    @Override public void onRevoked(ServerPlayer player, Config config) {}

    /**
     * Post-tick handler sets air supply to max after baseTick has already run
     * its drain logic, preventing the one-tick flicker that occurred when
     * onTick fought baseTick's air drain within the same tick.
     */
    @EventBusSubscriber(modid = NeoOrigins.MOD_ID)
    public static final class Handler {

        @SubscribeEvent
        public static void onPlayerTickPost(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            if (!sp.isUnderWater()) return;

            boolean[] has = {false};
            ActiveOriginService.forEachOfType(sp, WaterBreathingPower.class, cfg -> has[0] = true);
            if (!has[0]) return;

            sp.setAirSupply(sp.getMaxAirSupply());
        }
    }
}
