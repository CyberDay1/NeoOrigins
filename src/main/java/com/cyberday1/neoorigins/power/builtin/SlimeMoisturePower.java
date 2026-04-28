package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.biome.Biome;

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
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        setMoisture(player, 1.0F);
    }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        float moisture = getMoisture(player);

        // ── Drain / refill ─────────────────────────────────────────────
        boolean inWater = player.isInWaterOrRain();
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
        Identifier modId = Identifier.fromNamespaceAndPath("neoorigins", "slime_moisture_armor_penalty");
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
            armorAttr.removeModifier(Identifier.fromNamespaceAndPath("neoorigins", "slime_moisture_armor_penalty"));
        }
        player.removeEffect(MobEffects.REGENERATION);
        // Clear HUD bar on client
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
            new com.cyberday1.neoorigins.network.payload.SyncMoisturePayload(-1.0F));
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
