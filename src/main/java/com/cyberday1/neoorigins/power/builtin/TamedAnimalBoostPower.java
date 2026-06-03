package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

/**
 * Boosts tamed animals' stats (health, speed, attack) owned by the player.
 * Periodically scans nearby tamed animals and applies attribute modifiers.
 */
public class TamedAnimalBoostPower extends PowerType<TamedAnimalBoostPower.Config> {

    /** Per-power modifier id so multiple tamed_animal_boost powers stack additively
     *  on the boosted animals. The dispatch id is the granting player's power id and
     *  is stable between the onTick apply and the onRevoked removal. */
    private static Identifier modId(String suffix) {
        Identifier powerId = PowerHolder.currentDispatchId();
        String key = powerId != null
            ? (powerId.getNamespace() + "_" + powerId.getPath()).replace('/', '_')
            : "anon";
        return Identifier.fromNamespaceAndPath("neoorigins", "tamed_" + key + "_" + suffix);
    }

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

        Identifier healthModId = modId("health_boost");
        Identifier speedModId = modId("speed_boost");
        AABB area = player.getBoundingBox().inflate(config.radius());
        var animals = player.level().getEntitiesOfClass(Animal.class, area);
        for (Animal animal : animals) {
            if (!(animal instanceof OwnableEntity ownable)) continue;
            var owner = ownable.getOwner();
            if (owner == null || !player.getUUID().equals(owner.getUUID())) continue;

            // Permanent (not transient) modifiers — transient modifiers aren't
            // serialized with the entity, so the boost evaporates on chunk/world
            // reload. Mollan-reported: boosted wolves came back at base HP after
            // every relog. onRevoked() still removes these by id, so the cleanup
            // path is unchanged.
            AttributeInstance healthAttr = animal.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null && healthAttr.getModifier(healthModId) == null) {
                healthAttr.addPermanentModifier(new AttributeModifier(
                    healthModId, config.healthBonus(),
                    AttributeModifier.Operation.ADD_VALUE));
                animal.setHealth(animal.getMaxHealth());
            }

            AttributeInstance speedAttr = animal.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null && speedAttr.getModifier(speedModId) == null) {
                speedAttr.addPermanentModifier(new AttributeModifier(
                    speedModId, config.speedBonus(),
                    AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        Identifier healthModId = modId("health_boost");
        Identifier speedModId = modId("speed_boost");
        AABB area = player.getBoundingBox().inflate(config.radius());
        var animals = player.level().getEntitiesOfClass(Animal.class, area);
        for (Animal animal : animals) {
            if (!(animal instanceof OwnableEntity ownable)) continue;
            var owner = ownable.getOwner();
            if (owner == null || !player.getUUID().equals(owner.getUUID())) continue;

            AttributeInstance healthAttr = animal.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) healthAttr.removeModifier(healthModId);

            AttributeInstance speedAttr = animal.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) speedAttr.removeModifier(speedModId);
        }
    }
}
