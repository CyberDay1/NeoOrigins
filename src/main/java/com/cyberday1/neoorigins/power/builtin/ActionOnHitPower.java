package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;
import java.util.Random;

/**
 * Fires an action each time the player deals damage to a living entity, optionally
 * restricted to a target entity group (e.g. {@code undead}, {@code arthropod}),
 * a specific entity type, or a damage type.
 *
 * <p>Wired from {@link com.cyberday1.neoorigins.event.CombatPowerEvents#onLivingDamage}
 * — the event fires before damage is finalised, so {@code min_damage} is checked
 * against the (possibly multiplier-modified) incoming amount.
 *
 * <p>Action values:
 * <ul>
 *   <li>{@code restore_health} — heals the attacker by {@code amount}</li>
 *   <li>{@code restore_hunger} — feeds the attacker by {@code amount} food points</li>
 *   <li>{@code grant_effect}   — applies {@code effect} to the attacker (self)</li>
 *   <li>{@code target_effect}  — applies {@code effect} to the entity being hit</li>
 * </ul>
 */
public class ActionOnHitPower extends PowerType<ActionOnHitPower.Config> {

    private static final Random RANDOM = new Random();

    public record Config(
        String action,
        float amount,
        Optional<Identifier> effect,
        int duration,
        int amplifier,
        float minDamage,
        float chance,
        Optional<String> targetGroup,
        Optional<Identifier> targetType,
        Optional<String> damageType,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("action", "restore_health").forGetter(Config::action),
            Codec.FLOAT.optionalFieldOf("amount", 2.0f).forGetter(Config::amount),
            Identifier.CODEC.optionalFieldOf("effect").forGetter(Config::effect),
            Codec.INT.optionalFieldOf("duration", 100).forGetter(Config::duration),
            Codec.INT.optionalFieldOf("amplifier", 0).forGetter(Config::amplifier),
            Codec.FLOAT.optionalFieldOf("min_damage", 0.0f).forGetter(Config::minDamage),
            Codec.FLOAT.optionalFieldOf("chance", 1.0f).forGetter(Config::chance),
            Codec.STRING.optionalFieldOf("target_group").forGetter(Config::targetGroup),
            Identifier.CODEC.optionalFieldOf("target_type").forGetter(Config::targetType),
            Codec.STRING.optionalFieldOf("damage_type").forGetter(Config::damageType),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    /**
     * Executes the configured action. The roll for {@code chance} and the
     * {@code min_damage} / filter checks are expected to be handled by the
     * caller (CombatPowerEvents) before invoking this.
     */
    public static void execute(ServerPlayer player, Config config, LivingEntity target) {
        switch (config.action()) {
            case "restore_health" -> player.heal(config.amount());
            case "restore_hunger" -> player.getFoodData().eat((int) config.amount(), 0);
            case "grant_effect" -> applyEffect(player, config);
            case "target_effect" -> applyEffect(target, config);
            default -> NeoOrigins.LOGGER.warn(
                "action_on_hit action '{}' is unknown — expected one of restore_health, restore_hunger, grant_effect, target_effect.",
                config.action());
        }
    }

    public static boolean rollChance(Config config) {
        return config.chance() >= 1.0f || RANDOM.nextFloat() < config.chance();
    }

    private static void applyEffect(LivingEntity recipient, Config config) {
        if (config.effect().isEmpty()) return;
        BuiltInRegistries.MOB_EFFECT.getOptional(config.effect().get()).ifPresent(effect -> {
            var holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
            recipient.addEffect(new MobEffectInstance(holder, config.duration(), config.amplifier(), false, true));
        });
    }
}
