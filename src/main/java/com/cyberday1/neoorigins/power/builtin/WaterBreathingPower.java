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

    public record Config(String type, boolean enabled) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type),
            // Config kill-switch: a top-level "enabled":false (injected by the
            // power_overrides system) stops this instance from granting breathing.
            com.cyberday1.neoorigins.power.util.EnabledGate.field(Config::enabled)
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
            // Gate on the eye being submerged — this is the exact condition under
            // which vanilla baseTick depletes air. isUnderWater() additionally
            // requires the body to be in water, which can flicker false while the
            // eye stays under, causing the bubble bar to drain on the ticks where
            // we fail to fire and reset air to max.
            if (!sp.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) return;

            boolean[] has = {false};
            // Any enabled instance grants breathing; a disabled one (enabled:false
            // via power_overrides) must NOT set the flag.
            ActiveOriginService.forEachOfType(sp, WaterBreathingPower.class, cfg -> { if (cfg.enabled()) has[0] = true; });
            if (!has[0]) return;

            sp.setAirSupply(sp.getMaxAirSupply());
        }
    }
}
