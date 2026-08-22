package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.config.GameplayConfig;
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
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
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

    /**
     * Sentinel for "this power authored no drain field", which is how the four
     * built-in {@code *_dries_out} JSONs are written. Such powers defer to
     * {@code [ocean_origins] drain_rate_ticks} so the config keeps driving the
     * built-ins, per issue #120. Any positive value means the pack author set a
     * drain explicitly and that value wins over the config.
     *
     * <p>Deliberately NOT a plain numeric default: a literal default here would
     * be indistinguishable from an authored value, so the built-ins would stop
     * tracking the config and #120 would silently regress.
     */
    public static final int UNSET = -1;

    /**
     * @param drainIntervalTicks ticks between air-bubble decrements while out
     *     of the fluid. Higher value = slower drain. {@link #UNSET} means the
     *     author set nothing and the global config supplies the value. Authors
     *     who think in "units lost per second" can set {@code air_loss_per_second}
     *     instead, which is converted to this internally via
     *     {@code 20 / air_loss_per_second}.
     */
    public record Config(String fluid, int drainIntervalTicks, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json;
                try {
                    json = ops.convertTo(JsonOps.INSTANCE, input);
                } catch (Exception e) {
                    return DataResult.error(() -> "breath_out_of_fluid: could not convert to JSON: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "breath_out_of_fluid: expected JSON object");
                }
                JsonObject obj = json.getAsJsonObject();
                String fluid = obj.has("fluid") ? obj.get("fluid").getAsString() : "water";
                String t = obj.has("type") ? obj.get("type").getAsString() : "neoorigins:breath_out_of_fluid";

                // Field resolution, in priority order — mirrors
                // BreathInFluidPower.Config.CODEC so the two halves of the same
                // mechanic read the same keys:
                //   1. air_loss_per_second (intuitive — higher = faster drain)
                //   2. drain_interval_ticks (clearer alias for drain_rate)
                //   3. drain_rate (legacy)
                //   4. UNSET → the global config decides
                int intervalTicks;
                if (obj.has("air_loss_per_second") && obj.get("air_loss_per_second").isJsonPrimitive()) {
                    int perSec = Math.max(1, obj.get("air_loss_per_second").getAsInt());
                    intervalTicks = Math.max(1, 20 / perSec);
                } else if (obj.has("drain_interval_ticks") && obj.get("drain_interval_ticks").isJsonPrimitive()) {
                    intervalTicks = Math.max(1, obj.get("drain_interval_ticks").getAsInt());
                } else if (obj.has("drain_rate") && obj.get("drain_rate").isJsonPrimitive()) {
                    intervalTicks = Math.max(1, obj.get("drain_rate").getAsInt());
                } else {
                    intervalTicks = UNSET;
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

    /**
     * Resolves the drain interval for one power: an authored value wins, an
     * omitted one ({@link #UNSET}) defers to the global config.
     *
     * <p>Extracted from the tick handler purely so it is reachable from a unit
     * test. The original defect was NOT in parsing — the codec had always read
     * {@code drain_rate} correctly — it was that this resolution step did not
     * exist and the config was substituted unconditionally. A parse-only test
     * passes happily while that bug is live, so the resolution needs its own
     * cover. The config value is passed in rather than read here to keep the
     * method free of any Minecraft/NeoForge bootstrap.
     */
    static int resolveIntervalTicks(Config cfg, int configFallbackTicks) {
        return cfg.drainIntervalTicks() > 0 ? cfg.drainIntervalTicks() : configFallbackTicks;
    }

    /**
     * Marker capability used by the client HUD to suppress the bubble row
     * while submerged. Aquatic origins keep air at max underwater (via
     * water_breathing), so the bubble row carries no useful information when
     * the player is in water — and the row reappears the moment they surface
     * so dry-out depletion is visible as expected.
     */
    public static final String DRIES_OUT_CAPABILITY = "dries_out_of_water";

    private static final java.util.Set<String> CAPS = java.util.Set.of(DRIES_OUT_CAPABILITY);

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

        /** Scratch holder used to pull the tightest (min interval) config for the player. */
        private static final class Chosen {
            int drainRate = -1;
            String fluid = "water";
        }

        @SubscribeEvent
        public static void onPlayerTickPost(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            if (!GameplayConfig.isOceanOriginsDriesOutEnabled()) {
                VIRTUAL_AIR.remove(sp.getUUID());
                return;
            }

            // Find every breath_out_of_fluid power on the player and keep the
            // tightest (smallest interval = fastest drain), so a pack that
            // stacks a harsher dries-out power on top of a milder one gets the
            // harsher behaviour rather than whichever happened to iterate first.
            //
            // A power that authored no drain field resolves to the global
            // `ocean_origins.drain_rate_ticks` config, which is what the four
            // built-in *_dries_out JSONs rely on (issue #120). An authored
            // value overrides the config for that power only.
            final int configTicks = GameplayConfig.oceanOriginsDrainRateTicks();
            Chosen chosen = new Chosen();
            ActiveOriginService.forEachOfType(sp, BreathOutOfFluidPower.class, cfg -> {
                int interval = resolveIntervalTicks(cfg, configTicks);
                if (chosen.drainRate < 0 || interval < chosen.drainRate) {
                    chosen.drainRate = interval;
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
                // isInWaterRainOrBubble covers water blocks, rain, and bubble columns.
                // Also treat standing inside a water cauldron as "in fluid" so aquatic
                // origins can rehydrate from cauldrons.
                // Water Breathing and Conduit Power effects act as a magical air
                // supply — pauses the land drain entirely so aquatic players can
                // explore on land with a potion or near an active conduit.
                //
                // The Conduit Power clause only means anything because of
                // ConduitBlockEntityMixin. Vanilla's ConduitBlockEntity gates
                // the effect behind isInWaterOrRain(), so a player standing on
                // dry land beside an active conduit is never granted it and
                // this test could only ever pass in a state the
                // isInWaterRainOrBubble() clause above had already returned on.
                // The mixin lifts that gate for players carrying this power,
                // which is what puts the effect on a dry-land player at all.
                inFluid = sp.isInWaterRainOrBubble()
                    || sp.level().getBlockState(sp.blockPosition())
                           .is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON)
                    || sp.hasEffect(MobEffects.WATER_BREATHING)
                    || sp.hasEffect(MobEffects.CONDUIT_POWER)
                    || consumeBacktankAir(sp);
            }
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
            //
            // Respiration enchantment (OXYGEN_BONUS attribute) extends land
            // time using the same probability vanilla uses for underwater air:
            // each drain tick has a 1/(oxygenBonus+1) chance to actually
            // decrement, so Resp III gives ~4x the land time.
            int tracked = VIRTUAL_AIR.getOrDefault(sp.getUUID(), maxAir);
            if (sp.tickCount % chosen.drainRate == 0 && tracked > -20) {
                AttributeInstance oxygenAttr = sp.getAttribute(Attributes.OXYGEN_BONUS);
                double oxygenBonus = oxygenAttr != null ? oxygenAttr.getValue() : 0.0;
                if (oxygenBonus <= 0.0 || sp.getRandom().nextDouble() < 1.0 / (oxygenBonus + 1.0)) {
                    tracked--;
                }
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
                float dmg = GameplayConfig.oceanOriginsDrownDamage();
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

        /**
         * Drinking a water bottle restores half the max air supply for aquatic
         * origins that are drying out on land. Gives players an emergency
         * rehydration option without requiring a full water source.
         */
        @SubscribeEvent
        public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            var stack = event.getItem();
            if (!stack.is(Items.POTION)) return;
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents == null || !contents.is(Potions.WATER)) return;

            // Only restore air if the player actually has a breath_out_of_fluid power
            boolean[] has = {false};
            ActiveOriginService.forEachOfType(sp, BreathOutOfFluidPower.class, cfg -> has[0] = true);
            if (!has[0]) return;

            int maxAir = sp.getMaxAirSupply();
            int current = VIRTUAL_AIR.getOrDefault(sp.getUUID(), maxAir);
            int restored = Math.min(current + maxAir / 2, maxAir);
            VIRTUAL_AIR.put(sp.getUUID(), restored);
            sp.setAirSupply(Math.max(restored, 0));
        }

        // ---- Create Copper Backtank compat ----

        /** Item tag used by Create for pressurized air source items (backtanks). */
        private static final TagKey<net.minecraft.world.item.Item> PRESSURIZED_AIR_SOURCES =
            TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("create", "pressurized_air_sources"));

        /** Lazily resolved reference to Create's backtank air DataComponent. Null if Create isn't loaded.
         *  Note: Create registers this as "banktank_air" (typo in their source). */
        private static volatile DataComponentType<Integer> backtankAirComponent;
        private static boolean backtankAirResolved;

        @SuppressWarnings("unchecked")
        private static DataComponentType<Integer> getBacktankAirComponent() {
            if (!backtankAirResolved) {
                backtankAirResolved = true;
                // Create's AllDataComponents registers BACKTANK_AIR with the
                // string "banktank_air" (typo in their source, confirmed in
                // create-1.21.1-6.0.10.jar bytecode).
                var opt = BuiltInRegistries.DATA_COMPONENT_TYPE
                    .getOptional(ResourceLocation.fromNamespaceAndPath("create", "banktank_air"));
                if (opt.isPresent()) {
                    backtankAirComponent = (DataComponentType<Integer>) opt.get();
                }
            }
            return backtankAirComponent;
        }

        /**
         * Inverted backtank: for aquatic origins on land, a pressurized air
         * source in the chestplate slot acts as a portable water supply —
         * pausing the dries-out drain while consuming air from the tank.
         * Consumes 1 air per 20 ticks (same rate Create uses underwater).
         *
         * @return true if a backtank with air remaining is equipped
         */
        private static boolean consumeBacktankAir(ServerPlayer sp) {
            DataComponentType<Integer> comp = getBacktankAirComponent();
            if (comp == null) return false; // Create not loaded

            ItemStack chest = sp.getItemBySlot(EquipmentSlot.CHEST);
            if (chest.isEmpty() || !chest.is(PRESSURIZED_AIR_SOURCES)) return false;

            Integer air = chest.get(comp);
            if (air == null || air <= 0) return false;

            // Consume 1 air per second (every 20 ticks), matching Create's
            // underwater consumption rate.
            if (sp.tickCount % 20 == 0) {
                chest.set(comp, air - 1);
            }
            return true;
        }
    }
}
