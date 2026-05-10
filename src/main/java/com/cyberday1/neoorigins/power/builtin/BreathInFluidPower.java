package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDrownEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drains the player's air supply when submerged in the specified fluid.
 * Useful for fire elementals that "drown" in water.
 *
 * <p><b>Why Post-tick + event suppression.</b> Vanilla's {@code baseTick} manages
 * air supply via {@link net.neoforged.neoforge.common.CommonHooks#onLivingBreathe},
 * which either refills air (if the player has water_breathing) or drains it
 * (normal drowning). Both cases fight with a simple {@code onTick} approach:
 * vanilla's refill overwrites our drain, or vanilla's drain double-stacks on
 * ours. We suppress vanilla's air management via {@link LivingBreatheEvent}
 * and {@link LivingDrownEvent} when this power is active, and fully own the
 * air supply from {@link PlayerTickEvent.Post} using a per-player virtual air
 * counter — the same proven pattern used by {@link BreathOutOfFluidPower}.
 */
public class BreathInFluidPower extends PowerType<BreathInFluidPower.Config> {

    public record Config(String fluid, int drainRate, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("fluid", "water").forGetter(Config::fluid),
            Codec.INT.optionalFieldOf("drain_rate", 20).forGetter(Config::drainRate),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    // onTick is intentionally a no-op — see class javadoc. The drain runs from
    // Handler below on PlayerTickEvent.Post so it overwrites baseTick's changes.
    @Override
    public void onTick(ServerPlayer player, Config config) {}

    @EventBusSubscriber(modid = NeoOrigins.MOD_ID)
    public static final class Handler {

        /** Virtual-air counter per player. Absent entry means "not currently in target fluid". */
        private static final Map<UUID, Integer> VIRTUAL_AIR = new ConcurrentHashMap<>();

        /** Scratch holder for collecting the active config. */
        private static final class Chosen {
            int drainRate = -1;
            String fluid = "water";
        }

        /**
         * Suppress vanilla's air management (both drain and refill) while the
         * player is submerged in the target fluid with this power active.
         * Our Post-tick handler fully owns the air supply value.
         */
        @SubscribeEvent
        public static void onBreathe(LivingBreatheEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            if (!isInTargetFluid(sp)) return;

            // Tell vanilla the player can breathe (prevents vanilla drain) but
            // set refill to 0 (prevents vanilla from refilling air).
            event.setCanBreathe(true);
            event.setRefillAirAmount(0);
        }

        /**
         * Cancel vanilla's drown damage/bubbles while we control the air supply.
         * Our Post-tick handler applies drown damage at the correct cadence.
         */
        @SubscribeEvent
        public static void onDrown(LivingDrownEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            if (!isInTargetFluid(sp)) return;
            event.setCanceled(true);
        }

        @SubscribeEvent
        public static void onPlayerTickPost(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;

            Chosen chosen = new Chosen();
            ActiveOriginService.forEachOfType(sp, BreathInFluidPower.class, cfg -> {
                if (chosen.drainRate < 0) {
                    chosen.drainRate = cfg.drainRate();
                    chosen.fluid = cfg.fluid();
                }
            });
            if (chosen.drainRate <= 0) {
                VIRTUAL_AIR.remove(sp.getUUID());
                return;
            }

            boolean inFluid;
            if ("lava".equalsIgnoreCase(chosen.fluid)) {
                inFluid = sp.isInLava();
            } else {
                // Use isUnderWater (eyes submerged) — not isInWater (any body part).
                // Players at the surface with head above water should breathe normally.
                inFluid = sp.isUnderWater();
            }

            int maxAir = sp.getMaxAirSupply();
            if (!inFluid) {
                // Out of target fluid — reset tracker so re-entering starts
                // from full air, and let vanilla manage air normally.
                VIRTUAL_AIR.remove(sp.getUUID());
                return;
            }

            // Decrement once per drainRate ticks. Floor of -20 matches vanilla's
            // drown-supply lower bound.
            //
            // Respiration enchantment (OXYGEN_BONUS attribute) extends survival
            // time using the same probability vanilla uses for underwater air:
            // each drain tick has a 1/(oxygenBonus+1) chance to actually
            // decrement, so Resp III gives ~4x the survival time.
            int tracked = VIRTUAL_AIR.getOrDefault(sp.getUUID(), maxAir);
            if (sp.tickCount % chosen.drainRate == 0 && tracked > -20) {
                AttributeInstance oxygenAttr = sp.getAttribute(Attributes.OXYGEN_BONUS);
                double oxygenBonus = oxygenAttr != null ? oxygenAttr.getValue() : 0.0;
                if (oxygenBonus <= 0.0 || sp.getRandom().nextDouble() < 1.0 / (oxygenBonus + 1.0)) {
                    tracked--;
                }
            }
            VIRTUAL_AIR.put(sp.getUUID(), tracked);

            // Sync the bubble HUD. Clamp at 0 so the display doesn't go negative.
            sp.setAirSupply(Math.max(tracked, 0));

            // Apply drown damage once tracked crosses zero, every 20 ticks
            // (matching vanilla's cadence for WaterAnimal.handleAirSupply).
            if (tracked < 0 && sp.tickCount % 20 == 0) {
                sp.hurt(sp.damageSources().drown(), 2.0F);
            }
        }

        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            VIRTUAL_AIR.remove(event.getEntity().getUUID());
        }

        @SubscribeEvent
        public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
            VIRTUAL_AIR.remove(event.getEntity().getUUID());
        }

        /**
         * Check if the player has breath_in_fluid and is currently in the target fluid.
         */
        private static boolean isInTargetFluid(ServerPlayer sp) {
            boolean[] result = {false};
            ActiveOriginService.forEachOfType(sp, BreathInFluidPower.class, cfg -> {
                if (result[0]) return;
                boolean inFluid = "lava".equalsIgnoreCase(cfg.fluid()) ? sp.isInLava() : sp.isUnderWater();
                if (inFluid) result[0] = true;
            });
            return result[0];
        }
    }
}
