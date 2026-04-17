package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

/**
 * Boosts saturation of food items when crafted or smelted by a player with
 * this power active. Applied once at craft/smelt time via
 * {@code PlayerEvent.ItemCraftedEvent} / {@code ItemSmeltedEvent} in
 * {@link com.cyberday1.neoorigins.event.CraftingPowerEvents} — no per-tick
 * scanning, no identity-hash tracking, no risk of compound re-application.
 */
public class BetterCraftedFoodPower extends PowerType<BetterCraftedFoodPower.Config> {

    public record Config(float saturationBonus, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("saturation_bonus", 0.4f).forGetter(Config::saturationBonus),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }
}
