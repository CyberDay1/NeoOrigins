package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * Boosts tamed animals' stats (health, speed, attack) owned by the player.
 * Periodically scans nearby tamed animals and applies attribute modifiers.
 */
public class TamedAnimalBoostPower extends PowerType<TamedAnimalBoostPower.Config> {

    private static final ResourceLocation HEALTH_MOD_ID =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "tamed_health_boost");
    private static final ResourceLocation SPEED_MOD_ID =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "tamed_speed_boost");

    public record Config(float healthBonus, float speedBonus, double radius, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("health_bonus", 4.0f).forGetter(Config::healthBonus),
            Codec.FLOAT.optionalFieldOf("speed_bonus", 0.1f).forGetter(Config::speedBonus),
            Codec.DOUBLE.optionalFieldOf("radius", 32.0).forGetter(Config::radius),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (player.tickCount % 60 != 0) return;

        AABB area = player.getBoundingBox().inflate(config.radius());
        var animals = player.level().getEntitiesOfClass(Animal.class, area);
        for (Animal animal : animals) {
            if (!(animal instanceof OwnableEntity ownable)) continue;
            var owner = ownable.getOwner();
            if (owner == null || !player.getUUID().equals(owner.getUUID())) continue;

            AttributeInstance healthAttr = animal.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null && healthAttr.getModifier(HEALTH_MOD_ID) == null) {
                healthAttr.addPermanentModifier(new AttributeModifier(
                    HEALTH_MOD_ID, config.healthBonus(),
                    AttributeModifier.Operation.ADD_VALUE));
                animal.setHealth(animal.getMaxHealth());
            }

            AttributeInstance speedAttr = animal.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null && speedAttr.getModifier(SPEED_MOD_ID) == null) {
                speedAttr.addPermanentModifier(new AttributeModifier(
                    SPEED_MOD_ID, config.speedBonus(),
                    AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }
}
