package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

import java.util.UUID;

/**
 * Slime Moisture — a custom resource bar that depletes over time.
 *
 * <ul>
 *   <li>Drains passively; faster in dry biomes (desert, badlands, savanna); even faster when on fire</li>
 *   <li>Replenished by standing in water or rain</li>
 *   <li>Above 75%: Regen 1 (ambient, no particles, no icon)</li>
 *   <li>Below 10%: -4 armor (clamped to 0)</li>
 *   <li>At 0%: damage over time (drying out)</li>
 * </ul>
 *
 * <p>Moisture is stored as a float 0.0–1.0 in the player's origin data
 * under the key {@code "slime_moisture"}.
 */
public class SlimeMoisturePower extends PowerType<SlimeMoisturePower.Config> {

    private static final String MOISTURE_KEY = "slime_moisture";

    /** Per-power modifier id so multiple slime_moisture powers don't collide on the armor penalty. */
    private static Identifier armorPenaltyModId() {
        Identifier powerId = PowerHolder.currentDispatchId();
        String key = powerId != null
            ? (powerId.getNamespace() + "_" + powerId.getPath()).replace('/', '_')
            : "anon";
        return Identifier.fromNamespaceAndPath("neoorigins", "slime_moisture_" + key + "_armor_penalty");
    }

    public record Config(
        float drainPerTick,
        float dryBiomeDrainMultiplier,
        float fireDrainMultiplier,
        float waterRefillPerTick,
        float regenThreshold,
        float armorPenaltyThreshold,
        float dotThreshold,
        float dotDamage,
        int dotInterval,
        float waterBottleRefill,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("drain_per_tick", 0.0004F).forGetter(Config::drainPerTick),
            Codec.FLOAT.optionalFieldOf("dry_biome_drain_multiplier", 3.0F).forGetter(Config::dryBiomeDrainMultiplier),
            Codec.FLOAT.optionalFieldOf("fire_drain_multiplier", 10.0F).forGetter(Config::fireDrainMultiplier),
            Codec.FLOAT.optionalFieldOf("water_refill_per_tick", 0.005F).forGetter(Config::waterRefillPerTick),
            Codec.FLOAT.optionalFieldOf("regen_threshold", 0.75F).forGetter(Config::regenThreshold),
            Codec.FLOAT.optionalFieldOf("armor_penalty_threshold", 0.10F).forGetter(Config::armorPenaltyThreshold),
            Codec.FLOAT.optionalFieldOf("dot_threshold", 0.0F).forGetter(Config::dotThreshold),
            Codec.FLOAT.optionalFieldOf("dot_damage", 1.0F).forGetter(Config::dotDamage),
            Codec.INT.optionalFieldOf("dot_interval", 40).forGetter(Config::dotInterval),
            Codec.FLOAT.optionalFieldOf("water_bottle_refill", 0.5F).forGetter(Config::waterBottleRefill),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        // Only initialize moisture if no saved value exists yet
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        if (data.getCustomFloat(MOISTURE_KEY, -1.0F) < 0) {
            setMoisture(player, 1.0F);
        }
    }

    @Override
    public void onLogin(ServerPlayer player, Config config) {
        // Do NOT delegate to onGranted — moisture is already persisted
    }

    @Override
    public void onRespawn(ServerPlayer player, Config config) {
        // Do NOT delegate to onGranted — preserve moisture across respawn
    }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        float moisture = getMoisture(player);

        // ── Drain / refill ─────────────────────────────────────────────
        // isInWaterOrRain covers water blocks and rain, but a water cauldron
        // is a block with a level property rather than a water fluid, so it
        // misses entirely. Same clause BreathOutOfFluidPower uses, kept
        // identical so both wet-origin families rehydrate alike.
        boolean inWater = player.isInWaterOrRain()
            || player.level().getBlockState(player.blockPosition())
                   .is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON);
        if (inWater) {
            moisture = Math.min(1.0F, moisture + config.waterRefillPerTick());
        } else {
            float drain = config.drainPerTick();
            if (isInDryBiome(player)) drain *= config.dryBiomeDrainMultiplier();
            if (player.isOnFire()) drain *= config.fireDrainMultiplier();
            moisture = Math.max(0.0F, moisture - drain);
        }

        setMoisture(player, moisture);

        // Sync to client for HUD rendering (every 10 ticks = 0.5s)
        if (player.tickCount % 10 == 0) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new com.cyberday1.neoorigins.network.payload.SyncMoisturePayload(moisture));
        }

        // ── Regen at > 75% moisture ────────────────────────────────────
        if (moisture > config.regenThreshold()) {
            var existing = player.getEffect(MobEffects.REGENERATION);
            if (existing == null || existing.getDuration() < 30) {
                player.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION, 60, 0, true, false, false));
            }
        }

        // ── Armor penalty at < 10% moisture ────────────────────────────
        // Handled via attribute modifier in a companion attribute_modifier
        // power gated by condition — or we can apply it inline here using
        // a transient modifier. Using inline for simplicity.
        var armorAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        Identifier modId = armorPenaltyModId();
        if (armorAttr != null) {
            var existingMod = armorAttr.getModifier(modId);
            if (moisture < config.armorPenaltyThreshold()) {
                if (existingMod == null) {
                    armorAttr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        modId, -4.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
                }
            } else {
                if (existingMod != null) {
                    armorAttr.removeModifier(modId);
                }
            }
        }

        // ── Damage over time at 0% moisture ────────────────────────────
        if (moisture <= config.dotThreshold() && player.tickCount % config.dotInterval() == 0) {
            player.hurt(player.damageSources().magic(), config.dotDamage());
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        // Clean up armor modifier
        var armorAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        if (armorAttr != null) {
            armorAttr.removeModifier(armorPenaltyModId());
        }
        player.removeEffect(MobEffects.REGENERATION);
        // Clear HUD bar on client
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
            new com.cyberday1.neoorigins.network.payload.SyncMoisturePayload(-1.0F));
    }

    /**
     * Drinking a water bottle tops the moisture bar back up, giving a drying
     * slime an emergency option away from any water source. Mirrors the same
     * affordance {@code BreathOutOfFluidPower} gives aquatic origins.
     *
     * <p>Nested so the {@code @EventBusSubscriber} scan activates the handler
     * exactly once.
     */
    @EventBusSubscriber(modid = NeoOrigins.MOD_ID)
    public static final class Handler {

        @SubscribeEvent
        public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            ItemStack stack = event.getItem();
            if (!stack.is(Items.POTION)) return;
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents == null || !contents.is(Potions.WATER)) return;

            // Take the most generous refill among the player's moisture powers;
            // a player without one is left alone entirely.
            float[] refill = {-1.0F};
            ActiveOriginService.forEachOfType(sp, SlimeMoisturePower.class,
                cfg -> refill[0] = Math.max(refill[0], cfg.waterBottleRefill()));
            if (refill[0] < 0.0F) return;

            setMoisture(sp, getMoisture(sp) + refill[0]);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                new com.cyberday1.neoorigins.network.payload.SyncMoisturePayload(getMoisture(sp)));
        }
    }

    // ── Moisture storage ───────────────────────────────────────────────

    public static float getMoisture(ServerPlayer player) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        return data.getCustomFloat(MOISTURE_KEY, 1.0F);
    }

    public static void setMoisture(ServerPlayer player, float value) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        data.setCustomFloat(MOISTURE_KEY, Math.max(0.0F, Math.min(1.0F, value)));
    }

    // ── Biome checks ───────────────────────────────────────────────────

    private static boolean isInDryBiome(ServerPlayer player) {
        Holder<Biome> biome = player.level().getBiome(player.blockPosition());
        // Check biome tags and names for hot/dry biomes
        if (biome.is(BiomeTags.IS_BADLANDS)) return true;
        var key = biome.unwrapKey();
        if (key.isEmpty()) return false;
        String path = key.get().identifier().getPath();
        return path.contains("desert") || path.contains("savanna")
            || path.contains("mesa") || path.contains("badlands");
    }
}
