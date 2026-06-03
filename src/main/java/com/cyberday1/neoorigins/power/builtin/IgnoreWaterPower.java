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

import java.util.Set;

/**
 * Makes the player unaffected by water: full movement speed in water and
 * not pushed by water currents.
 *
 * <p>Movement speed is handled via a {@code water_movement_efficiency} attribute
 * modifier set to 1.0 (full land speed). Current pushing is suppressed by
 * {@code EntityIgnoreWaterMixin} checking the {@code ignore_water} capability.
 *
 * <pre>{ "type": "neoorigins:ignore_water" }</pre>
 */
public class IgnoreWaterPower extends PowerType<IgnoreWaterPower.Config> {

    /** Per-power modifier id so multiple ignore_water powers stack additively. */
    private static ResourceLocation modWaterSpeed() {
        ResourceLocation powerId = PowerHolder.currentDispatchId();
        String key = powerId != null
            ? (powerId.getNamespace() + "_" + powerId.getPath()).replace('/', '_')
            : "anon";
        return ResourceLocation.fromNamespaceAndPath("neoorigins", "ignore_water_" + key + "_speed");
    }

    public record Config(String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    private static final Set<String> CAPS = Set.of("ignore_water");

    @Override
    public Set<String> capabilities(Config config) { return CAPS; }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        AttributeInstance inst = player.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY);
        ResourceLocation modId = modWaterSpeed();
        if (inst != null && inst.getModifier(modId) == null) {
            inst.addPermanentModifier(new AttributeModifier(
                modId, 1.0, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        AttributeInstance inst = player.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY);
        if (inst != null) inst.removeModifier(modWaterSpeed());
    }
}
