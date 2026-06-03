package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class UnderwaterMiningSpeedPower extends PowerType<UnderwaterMiningSpeedPower.Config> {

    /** Per-power modifier id so multiple underwater_mining_speed powers stack additively. */
    private static ResourceLocation modId() {
        ResourceLocation powerId = PowerHolder.currentDispatchId();
        String key = powerId != null
            ? (powerId.getNamespace() + "_" + powerId.getPath()).replace('/', '_')
            : "anon";
        return ResourceLocation.fromNamespaceAndPath("neoorigins", "underwater_mining_" + key + "_cancel_penalty");
    }

    public record Config(String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        AttributeInstance instance = player.getAttribute(Attributes.SUBMERGED_MINING_SPEED);
        if (instance == null) return;
        ResourceLocation modId = modId();
        if (instance.getModifier(modId) != null) return;
        instance.addPermanentModifier(new AttributeModifier(
            modId, 4.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        AttributeInstance instance = player.getAttribute(Attributes.SUBMERGED_MINING_SPEED);
        if (instance == null) return;
        instance.removeModifier(modId());
    }
}
