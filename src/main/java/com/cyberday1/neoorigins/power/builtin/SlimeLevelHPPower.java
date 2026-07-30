package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Slime Level HP — grants +1 max HP per N experience levels.
 * The bonus resets on death (handled by onGranted recalculating from level 0
 * after respawn, since the player loses XP on death).
 */
public class SlimeLevelHPPower extends PowerType<SlimeLevelHPPower.Config> {

    /** Per-power modifier id so multiple slime_level_hp powers stack additively
     *  instead of sharing one fixed id (which would collapse to a single bonus). */
    private static Identifier modId() {
        Identifier powerId = PowerHolder.currentDispatchId();
        String key = powerId != null
            ? (powerId.getNamespace() + "_" + powerId.getPath()).replace('/', '_')
            : "anon";
        return Identifier.fromNamespaceAndPath("neoorigins", "slime_level_hp_" + key + "_bonus");
    }

    public record Config(
        int levelsPerHp,
        int maxBonusHp,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("levels_per_hp", 10).forGetter(Config::levelsPerHp),
            Codec.INT.optionalFieldOf("max_bonus_hp", 20).forGetter(Config::maxBonusHp),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        // Only recalculate every 20 ticks — level changes are infrequent
        if (player.tickCount % 20 != 0) return;

        int bonusHP = Math.min(player.experienceLevel / config.levelsPerHp(), config.maxBonusHp());

        var maxHpAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHpAttr == null) return;

        Identifier modId = modId();
        var existing = maxHpAttr.getModifier(modId);
        double currentBonus = existing != null ? existing.amount() : 0;

        if ((int) currentBonus != bonusHP) {
            maxHpAttr.removeModifier(modId);
            if (bonusHP > 0) {
                maxHpAttr.addTransientModifier(new AttributeModifier(
                    modId, bonusHP, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        var maxHpAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHpAttr != null) maxHpAttr.removeModifier(modId());
    }
}
