package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.List;

/**
 * Applies status effects when the player's HP drops below a percentage
 * threshold. Effects are removed when HP rises above the threshold.
 *
 * <p>Use cases: Automaton Overclock (speed/haste at <50%), Revenant
 * Death's Embrace (strength at <25%), Piglin Brute Force (strength at <30%).
 */
public class LowHPThresholdPower extends PowerType<LowHPThresholdPower.Config> {

    public record EffectEntry(String effect, int amplifier) {
        public static final Codec<EffectEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("effect").forGetter(EffectEntry::effect),
            Codec.INT.optionalFieldOf("amplifier", 0).forGetter(EffectEntry::amplifier)
        ).apply(inst, EffectEntry::new));
    }

    public record Config(
        float threshold,
        List<EffectEntry> effects,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("threshold", 0.5F).forGetter(Config::threshold),
            EffectEntry.CODEC.listOf().fieldOf("effects").forGetter(Config::effects),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        float maxHealth = player.getMaxHealth();
        if (maxHealth <= 0) return;
        float hpPercent = player.getHealth() / maxHealth;
        boolean belowThreshold = hpPercent < config.threshold();

        for (EffectEntry entry : config.effects()) {
            var holder = BuiltInRegistries.MOB_EFFECT.getHolder(
                ResourceLocation.parse(entry.effect())).orElse(null);
            if (holder == null) continue;

            if (belowThreshold) {
                var existing = player.getEffect(holder);
                if (existing == null || existing.getDuration() < 30) {
                    player.addEffect(new MobEffectInstance(
                        holder, 60, entry.amplifier(), true, false, true));
                }
            } else {
                if (player.hasEffect(holder)) {
                    player.removeEffect(holder);
                }
            }
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        for (EffectEntry entry : config.effects()) {
            var holder = BuiltInRegistries.MOB_EFFECT.getHolder(
                ResourceLocation.parse(entry.effect())).orElse(null);
            if (holder != null) player.removeEffect(holder);
        }
    }
}
