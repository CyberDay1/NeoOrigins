package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.NeoOriginsConfig;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Inverse of {@link BreathInFluidPower}: drains the player's air supply while
 * they are <b>not</b> submerged in the specified fluid. Useful for aquatic
 * origins that must stay wet — like a fish out of water, they gradually
 * suffocate on land.
 *
 * <p>Once the air supply reaches 0 the game applies vanilla drown damage,
 * exactly as with normal underwater suffocation.
 *
 * <p><b>Why Post-tick.</b> {@link net.minecraft.world.entity.LivingEntity#baseTick}
 * runs {@code setAirSupply(increaseAirSupply(airSupply))} every tick while out of
 * water, refilling +4 air/tick up to the max. A Pre-tick {@code setAirSupply(air-1)}
 * call is immediately overwritten by that refill and never reaches zero — the
 * bubble UI stays pinned to full and drown damage never fires. We therefore run
 * the drain from {@link PlayerTickEvent.Post}, after {@code baseTick}, and keep
 * a per-player "virtual air" counter so each tick's final airSupply reflects
 * our accumulated drain rather than the vanilla refill.
 */
public class BreathOutOfFluidPower extends PowerType<BreathOutOfFluidPower.Config> {

    public record Config(String fluid, int drainRate, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("fluid", "water").forGetter(Config::fluid),
            Codec.INT.optionalFieldOf("drain_rate", 40).forGetter(Config::drainRate),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    /**
     * Marker capability used by the client HUD to suppress the bubble row
     * while submerged. Aquatic origins keep air at max underwater (via
     * water_breathing), so the bubble row carries no useful information when
     * the player is in water — and the row reappears the moment they surface
     * so dry-out depletion is visible as expected.
     */
    private static final java.util.Set<String> CAPS = java.util.Set.of("dries_out_of_water");

    @Override
    public java.util.Set<String> capabilities(Config config) { return CAPS; }

    // onTick is intentionally a no-op — see class javadoc. The drain runs from
    // Handler below on PlayerTickEvent.Post so it overwrites baseTick's refill.
    @Override
    public void onTick(ServerPlayer player, Config config) {}

    /**
     * Per-player virtual air tracker + Post-tick drain handler. Kept in a
     * nested class so the @EventBusSubscriber annotation only activates the
     * handlers once (FML scans for the annotation on classes).
     */
    @EventBusSubscriber(modid = NeoOrigins.MOD_ID)
    public static final class Handler {

        /** Virtual-air counter per player. Absent entry means "not currently drying". */
        private static final Map<UUID, Integer> VIRTUAL_AIR = new ConcurrentHashMap<>();

        /** Scratch holder used to pull the tightest (min drain_rate) config for the player. */
        private static final class Chosen {
            int drainRate = -1;
            String fluid = "water";
        }

        @SubscribeEvent
        public static void onPlayerTickPost(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            if (!NeoOriginsConfig.isOceanOriginsDriesOutEnabled()) {
                VIRTUAL_AIR.remove(sp.getUUID());
                return;
            }

            // Find any breath_out_of_fluid power on the player. Multiple powers
            // (pack + origin) all share the same fluid type via the first match
            // — the per-power drain_rate is no longer respected, since the
            // master `ocean_origins.drain_rate_ticks` config now drives drain
            // for all dries-out powers globally. Pack authors who want
            // per-power tuning should override the config or extend the power.
            Chosen chosen = new Chosen();
            ActiveOriginService.forEachOfType(sp, BreathOutOfFluidPower.class, cfg -> {
                if (chosen.drainRate < 0) {
                    chosen.drainRate = NeoOriginsConfig.oceanOriginsDrainRateTicks();
                    chosen.fluid = cfg.fluid();
                }
            });
            if (chosen.drainRate <= 0) {
                VIRTUAL_AIR.remove(sp.getUUID());
                return;
            }

            boolean inFluid = "lava".equalsIgnoreCase(chosen.fluid)
                ? sp.isInLava() : sp.isInWater();
            int maxAir = sp.getMaxAirSupply();
            if (inFluid) {
                // Reset on re-entry so stepping back into water visibly
                // refills the bubble row instead of resuming where we left off.
                VIRTUAL_AIR.remove(sp.getUUID());
                return;
            }

            // Decrement once per drainRate ticks. Floor of -20 matches
            // vanilla's drown-supply lower bound; each subsequent damage cycle
            // happens every 20 ticks below 0 (mirroring AbstractFish /
            // WaterAnimal cadence).
            int tracked = VIRTUAL_AIR.getOrDefault(sp.getUUID(), maxAir);
            if (sp.tickCount % chosen.drainRate == 0 && tracked > -20) {
                tracked--;
            }
            VIRTUAL_AIR.put(sp.getUUID(), tracked);

            // Sync the bubble HUD by setting airSupply directly. The companion
            // LivingEntityAirRefillMixin suppresses vanilla's +4/tick out-of-water
            // refill so this value is no longer fought tick-to-tick — the
            // bubble row just slowly empties as tracked decrements.
            sp.setAirSupply(Math.max(tracked, 0));

            // Vanilla's drown damage code only fires when isEyeInFluid(WATER),
            // so on land the airSupply hitting 0 does nothing on its own.
            // Apply the damage ourselves once tracked has crossed zero, and
            // tick at config-driven rate (default 2 HP/sec, every 20 ticks)
            // which matches the WaterAnimal.handleAirSupply cadence used by
            // vanilla cod / salmon.
            if (tracked < 0 && sp.tickCount % 20 == 0) {
                float dmg = NeoOriginsConfig.oceanOriginsDrownDamage();
                if (dmg > 0.0F) sp.hurt(sp.damageSources().drown(), dmg);
            }
        }

        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            VIRTUAL_AIR.remove(event.getEntity().getUUID());
        }

        // Respawn replaces the ServerPlayer instance but keeps the UUID, so
        // any leftover negative tracked value would carry over and apply drown
        // damage on the very next Post-tick — re-killing the player before
        // they can move. Always start a respawning player with a clean
        // tracker; the next out-of-water Post-tick re-initialises it from
        // maxAirSupply.
        @SubscribeEvent
        public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
            VIRTUAL_AIR.remove(event.getEntity().getUUID());
        }
    }
}
