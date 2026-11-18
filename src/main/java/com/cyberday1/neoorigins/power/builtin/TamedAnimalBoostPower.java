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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * Boosts tamed animals' stats (health, speed, attack) owned by the player.
 * Periodically scans nearby tamed animals and applies attribute modifiers.
 */
public class TamedAnimalBoostPower extends PowerType<TamedAnimalBoostPower.Config> {

    /** Per-power modifier id so multiple tamed_animal_boost powers stack additively
     *  on the boosted animals. The dispatch id is the granting player's power id and
     *  is stable between the onTick apply and the onRevoked removal. */
    private static ResourceLocation modId(String suffix) {
        ResourceLocation powerId = PowerHolder.currentDispatchId();
        String key = powerId != null
            ? (powerId.getNamespace() + "_" + powerId.getPath()).replace('/', '_')
            : "anon";
        return ResourceLocation.fromNamespaceAndPath("neoorigins", "tamed_" + key + "_" + suffix);
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

    /**
     * Animals whose boost a shut power_condition took off, keyed with the health
     * modifier id. Putting the boost back skips the heal to full for these, so
     * flipping the gate is not a free heal.
     */
    private static final java.util.Set<String> GATED_OFF = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (player.tickCount % 60 != 0) return;

        ResourceLocation healthModId = modId("health_boost");
        ResourceLocation speedModId = modId("speed_boost");
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
                if (!GATED_OFF.remove(animal.getUUID() + "|" + healthModId)) {
                    animal.setHealth(animal.getMaxHealth());
                }
            }

            AttributeInstance speedAttr = animal.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null && speedAttr.getModifier(speedModId) == null) {
                speedAttr.addPermanentModifier(new AttributeModifier(
                    speedModId, config.speedBonus(),
                    AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    // A shut power_condition strips the boost from the owner's animals in range,
    // on the same 60-tick scan; the next open scan puts it back.
    @Override
    public void onTickBlocked(ServerPlayer player, Config config) {
        if (player.tickCount % 60 != 0) return;
        strip(player, config, true);
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        strip(player, config, false);
    }

    private static void strip(ServerPlayer player, Config config, boolean gated) {
        ResourceLocation healthModId = modId("health_boost");
        ResourceLocation speedModId = modId("speed_boost");
        AABB area = player.getBoundingBox().inflate(config.radius());
        var animals = player.level().getEntitiesOfClass(Animal.class, area);
        for (Animal animal : animals) {
            if (!(animal instanceof OwnableEntity ownable)) continue;
            var owner = ownable.getOwner();
            if (owner == null || !player.getUUID().equals(owner.getUUID())) continue;

            AttributeInstance healthAttr = animal.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null && healthAttr.removeModifier(healthModId)) {
                String key = animal.getUUID() + "|" + healthModId;
                if (gated) GATED_OFF.add(key);
                else GATED_OFF.remove(key);
            }
            AttributeInstance speedAttr = animal.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) speedAttr.removeModifier(speedModId);
        }
    }
}
