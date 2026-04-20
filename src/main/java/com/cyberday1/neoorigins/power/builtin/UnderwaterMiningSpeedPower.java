package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class UnderwaterMiningSpeedPower extends PowerType<UnderwaterMiningSpeedPower.Config> {

    private static final ResourceLocation MODIFIER_ID =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "underwater_mining_cancel_penalty");

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
        if (instance.getModifier(MODIFIER_ID) != null) return;
        instance.addPermanentModifier(new AttributeModifier(
            MODIFIER_ID, 4.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        AttributeInstance instance = player.getAttribute(Attributes.SUBMERGED_MINING_SPEED);
        if (instance == null) return;
        instance.removeModifier(MODIFIER_ID);
    }
}
