package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
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

    private static final Identifier MOD_ID =
        Identifier.fromNamespaceAndPath("neoorigins", "slime_level_hp_bonus");

    public record Config(
        int levelsPerHP,
        int maxBonusHP,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("levels_per_hp", 10).forGetter(Config::levelsPerHP),
            Codec.INT.optionalFieldOf("max_bonus_hp", 20).forGetter(Config::maxBonusHP),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        // Only recalculate every 20 ticks — level changes are infrequent
        if (player.tickCount % 20 != 0) return;

        int bonusHP = Math.min(player.experienceLevel / config.levelsPerHP(), config.maxBonusHP());

        var maxHpAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHpAttr == null) return;

        var existing = maxHpAttr.getModifier(MOD_ID);
        double currentBonus = existing != null ? existing.amount() : 0;

        if ((int) currentBonus != bonusHP) {
            maxHpAttr.removeModifier(MOD_ID);
            if (bonusHP > 0) {
                maxHpAttr.addTransientModifier(new AttributeModifier(
                    MOD_ID, bonusHP, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        var maxHpAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHpAttr != null) maxHpAttr.removeModifier(MOD_ID);
    }
}
