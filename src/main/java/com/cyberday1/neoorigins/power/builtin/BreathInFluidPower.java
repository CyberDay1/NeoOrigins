package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
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

    /**
     * @param drainIntervalTicks ticks between air-bubble decrements. Higher
     *     value = slower drain. Default 20 = one decrement per second (full
     *     air → 0 in 5 minutes). Authors who think in "units lost per second"
     *     can instead set {@code air_loss_per_second}, which is converted to
     *     this internally via {@code 20 / air_loss_per_second}.
     */
    public record Config(String fluid, int drainIntervalTicks, String type) implements PowerConfiguration {
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
                int intervalTicks;
                if (obj.has("air_loss_per_second") && obj.get("air_loss_per_second").isJsonPrimitive()) {
                    int perSec = Math.max(1, obj.get("air_loss_per_second").getAsInt());
                    intervalTicks = Math.max(1, 20 / perSec);
                } else if (obj.has("drain_interval_ticks") && obj.get("drain_interval_ticks").isJsonPrimitive()) {
                    intervalTicks = Math.max(1, obj.get("drain_interval_ticks").getAsInt());
                } else if (obj.has("drain_rate") && obj.get("drain_rate").isJsonPrimitive()) {
                    intervalTicks = Math.max(1, obj.get("drain_rate").getAsInt());
                } else {
                    intervalTicks = 20;
                }

                return DataResult.success(Pair.of(new Config(fluid, intervalTicks, t), ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                return DataResult.success(prefix);
            }
        };
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
            int drainIntervalTicks = -1;
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
                if (chosen.drainIntervalTicks < 0) {
                    chosen.drainIntervalTicks = cfg.drainIntervalTicks();
                    chosen.fluid = cfg.fluid();
                }
            });
            if (chosen.drainIntervalTicks <= 0) {
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

            // Decrement once per drainIntervalTicks. Floor of -20 matches vanilla's
            // drown-supply lower bound.
            //
            // Respiration enchantment (OXYGEN_BONUS attribute) extends survival
            // time using the same probability vanilla uses for underwater air:
            // each drain tick has a 1/(oxygenBonus+1) chance to actually
            // decrement, so Resp III gives ~4x the survival time.
            //
            // Seed the tracker from the player's CURRENT air on first submerge
            // (not maxAir). When the player breaches the surface, our tracker
            // is removed (line 171) and vanilla's refill takes over; on re-entry
            // we must pick up from whatever air the player actually has, or the
            // drain visually "refreshes" to full on every bob.
            int tracked = VIRTUAL_AIR.getOrDefault(sp.getUUID(), sp.getAirSupply());
            if (sp.tickCount % chosen.drainIntervalTicks == 0 && tracked > -20) {
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
