package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

/**
 * Classifies the player as a mob entity group, affecting how potion effects and enchantments interact.
 * group values: "undead", "arthropod", "water", "undefined"
 *
 * undead: Hurt by Instant Health, healed by Instant Damage, immune to Poison, harmed by Smite.
 * arthropod: Affected by Bane of Arthropods slowness, targeted by spider-type AI.
 * water: Damaged by Impaling enchantment.
 *
 * Handled via MobEffectEvent.Applicable and LivingIncomingDamageEvent in OriginEventHandler.
 */
public class EntityGroupPower extends PowerType<EntityGroupPower.Config> {

    public record Config(String group, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("group", "undefined").forGetter(Config::group),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));

        public boolean isUndead() { return "undead".equalsIgnoreCase(group); }
        public boolean isArthropod() { return "arthropod".equalsIgnoreCase(group); }
        public boolean isWater() { return "water".equalsIgnoreCase(group); }
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override public void onGranted(ServerPlayer player, Config config) {}
    @Override public void onRevoked(ServerPlayer player, Config config) {}
}
