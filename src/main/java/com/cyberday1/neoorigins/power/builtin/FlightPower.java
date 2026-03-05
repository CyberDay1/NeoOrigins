package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.common.NeoForgeMod;

public class FlightPower extends PowerType<FlightPower.Config> {

    private static final Identifier FLIGHT_MODIFIER_ID = Identifier.fromNamespaceAndPath("neoorigins", "flight_power");

    public record Config(String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        grantFlight(player);
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        AttributeInstance attr = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (attr != null) {
            attr.removeModifier(FLIGHT_MODIFIER_ID);
        }
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (!player.mayFly()) {
            grantFlight(player);
        }
    }

    private static void grantFlight(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (attr != null && !attr.hasModifier(FLIGHT_MODIFIER_ID)) {
            attr.addPermanentModifier(new AttributeModifier(FLIGHT_MODIFIER_ID, 1.0, AttributeModifier.Operation.ADD_VALUE));
        }
        player.onUpdateAbilities();
    }
}
