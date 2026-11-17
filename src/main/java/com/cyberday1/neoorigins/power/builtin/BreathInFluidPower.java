package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.power.capability.PowerCapabilities;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDrownEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.Set;
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
 *
 * <p><b>Both sides.</b> {@code baseTick} runs on the client too, so the
 * breathe suppression has to run there as well: the client otherwise drains
 * one point a tick on its own and snaps back up each time the server's value
 * lands, which reads as air going up and down at once (issue #150). The client
 * knows the power from the synced {@code breath_in_fluid:<fluid>} capability.
 *
 * <p><b>Units.</b> Air runs from 300 (full) down to 0, then on to -20 before
 * drowning damage would start in vanilla. Vanilla loses 20 a second underwater,
 * so a full bar lasts 15 seconds.
 */
public class BreathInFluidPower extends PowerType<BreathInFluidPower.Config> {

    /** Prefix of the synced capability, e.g. {@code breath_in_fluid:water}. */
    public static final String CAPABILITY_PREFIX = "breath_in_fluid:";

    /**
     * @param drainIntervalTicks ticks between 1-point air drains. Higher
     *     value = slower drain. Default 20 = one point per second (full
     *     air → 0 in 5 minutes). Ignored when {@code airLossPerSecond} is set.
     * @param airLossPerSecond air points lost per second, 0 when unset. Read
     *     as a decimal and carried between ticks, so 3 drains exactly 3 a
     *     second and values above 20 drain faster than once a tick.
     */
    public record Config(String fluid, int drainIntervalTicks, double airLossPerSecond, String type)
            implements PowerConfiguration {

        /** Air points the drain removes each tick, before Respiration. */
        public double airPerTick() {
            return airLossPerSecond > 0 ? airLossPerSecond / 20.0 : 1.0 / drainIntervalTicks;
        }

        /** {@code "water"} or {@code "lava"}: anything but lava means water, as the drain reads it. */
        public String fluidKey() {
            return "lava".equalsIgnoreCase(fluid) ? "lava" : "water";
        }

        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json;
                try {
                    json = ops.convertTo(JsonOps.INSTANCE, input);
                } catch (Exception e) {
                    return DataResult.error(() -> "breath_in_fluid: could not convert to JSON: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "breath_in_fluid: expected JSON object");
                }
                JsonObject obj = json.getAsJsonObject();
                String fluid = obj.has("fluid") ? obj.get("fluid").getAsString() : "water";
                String t = obj.has("type") ? obj.get("type").getAsString() : "neoorigins:breath_in_fluid";

                // Field resolution, in priority order:
                //   1. air_loss_per_second (intuitive — higher = faster drain)
                //   2. drain_interval_ticks (clearer alias for drain_rate)
                //   3. drain_rate (legacy)
                //   4. default 20 (one decrement per second)
                // Mollan-reported confusion: "drain_rate" reads like
                // "units per second" but historically meant "ticks between
                // drains". The two newer fields make intent explicit.
                // air_loss_per_second used to be turned into 20 / value whole
                // ticks, which made 3 drain at 3.3 a second, 7 at 10, and
                // everything from 11 to 20 at 20; it is now kept as a rate. A
                // value of 0 or less reads as unset.
                double perSec = 0;
                if (obj.has("air_loss_per_second") && obj.get("air_loss_per_second").isJsonPrimitive()) {
                    perSec = Math.max(0, obj.get("air_loss_per_second").getAsDouble());
                }
                int intervalTicks;
                if (obj.has("drain_interval_ticks") && obj.get("drain_interval_ticks").isJsonPrimitive()) {
                    intervalTicks = Math.max(1, obj.get("drain_interval_ticks").getAsInt());
                } else if (obj.has("drain_rate") && obj.get("drain_rate").isJsonPrimitive()) {
                    intervalTicks = Math.max(1, obj.get("drain_rate").getAsInt());
                } else {
                    intervalTicks = 20;
                }

                return DataResult.success(Pair.of(new Config(fluid, intervalTicks, perSec, t), ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                return DataResult.success(prefix);
            }
        };
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) {
        return Set.of(CAPABILITY_PREFIX + config.fluidKey());
    }

    // onTick is intentionally a no-op — see class javadoc. The drain runs from
    // Handler below on PlayerTickEvent.Post so it overwrites baseTick's changes.
    @Override
    public void onTick(ServerPlayer player, Config config) {}

    @EventBusSubscriber(modid = NeoOrigins.MOD_ID)
    public static final class Handler {

        /**
         * Virtual-air counter per player, fractional so a drain slower or faster
         * than one point a tick stays exact. Absent entry means "not currently
         * in target fluid".
         */
        private static final Map<UUID, Double> VIRTUAL_AIR = new ConcurrentHashMap<>();

        /** Scratch holder for collecting the active config. */
        private static final class Chosen {
            double airPerTick = -1;
            String fluid = "water";
        }

        /**
         * Suppress vanilla's air management (both drain and refill) while the
         * player is submerged in the target fluid with this power active.
         * Our Post-tick handler fully owns the air supply value. Runs on both
         * logical sides; see the class javadoc.
         */
        @SubscribeEvent
        public static void onBreathe(LivingBreatheEvent event) {
            if (!(event.getEntity() instanceof Player player)) return;
            if (!isInTargetFluid(player)) return;

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
                if (chosen.airPerTick < 0) {
                    chosen.airPerTick = cfg.airPerTick();
                    chosen.fluid = cfg.fluid();
                }
            });
            if (chosen.airPerTick <= 0) {
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

            if (!inFluid) {
                // Out of target fluid — drop the tracker and let vanilla refill
                // air normally. On re-entry we seed from sp.getAirSupply() so
                // the drain picks up from whatever air the player currently has.
                VIRTUAL_AIR.remove(sp.getUUID());
                return;
            }

            // Drain airPerTick every tick, carrying the fraction. Floor of -20
            // matches vanilla's drown-supply lower bound.
            //
            // Respiration enchantment (OXYGEN_BONUS attribute) extends survival
            // time by the same factor vanilla's underwater air gets: vanilla
            // skips each point with chance oxygenBonus/(oxygenBonus+1), so the
            // drain here is scaled by 1/(oxygenBonus+1) and Resp III gives 4x
            // the survival time.
            //
            // Seed the tracker from the player's CURRENT air on first submerge
            // (not maxAir). When the player breaches the surface, our tracker
            // is removed above and vanilla's refill takes over; on re-entry
            // we must pick up from whatever air the player actually has, or the
            // drain visually "refreshes" to full on every bob.
            double tracked = VIRTUAL_AIR.getOrDefault(sp.getUUID(), (double) sp.getAirSupply());
            if (tracked > -20) {
                AttributeInstance oxygenAttr = sp.getAttribute(Attributes.OXYGEN_BONUS);
                double oxygenBonus = oxygenAttr != null ? oxygenAttr.getValue() : 0.0;
                double loss = chosen.airPerTick / (Math.max(0.0, oxygenBonus) + 1.0);
                tracked = Math.max(-20, tracked - loss);
            }
            VIRTUAL_AIR.put(sp.getUUID(), tracked);

            // Sync the bubble HUD. Clamp at 0 so the display doesn't go negative.
            sp.setAirSupply(Math.max((int) Math.floor(tracked), 0));

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
         * Check if the player has breath_in_fluid and is currently in the target
         * fluid. Reads the synced capability, so the client answers the same way
         * for its own player.
         */
        private static boolean isInTargetFluid(Player player) {
            if (PowerCapabilities.hasActive(player, CAPABILITY_PREFIX + "water") && player.isUnderWater()) {
                return true;
            }
            return PowerCapabilities.hasActive(player, CAPABILITY_PREFIX + "lava") && player.isInLava();
        }
    }
}
