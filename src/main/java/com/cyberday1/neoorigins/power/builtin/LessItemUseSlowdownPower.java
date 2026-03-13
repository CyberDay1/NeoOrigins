package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Reduces movement slowdown while using items (bow/shield).
 * Applies a temporary speed modifier while the player is using an item.
 */
public class LessItemUseSlowdownPower extends PowerType<LessItemUseSlowdownPower.Config> {

    private static final Identifier MODIFIER_ID =
        Identifier.fromNamespaceAndPath("neoorigins", "less_item_use_slowdown");

    public record Config(String itemType, float speedMultiplier, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("item_type", "any").forGetter(Config::itemType),
            Codec.FLOAT.optionalFieldOf("speed_multiplier", 0.5f).forGetter(Config::speedMultiplier),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr == null) return;

        boolean using = player.isUsingItem();
        boolean matches = using && matchesItem(player, config.itemType());

        if (matches) {
            if (speedAttr.getModifier(MODIFIER_ID) == null) {
                speedAttr.addTransientModifier(new AttributeModifier(
                    MODIFIER_ID, config.speedMultiplier(),
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        } else {
            if (speedAttr.getModifier(MODIFIER_ID) != null) {
                speedAttr.removeModifier(MODIFIER_ID);
            }
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.removeModifier(MODIFIER_ID);
    }

    private boolean matchesItem(ServerPlayer player, String itemType) {
        if ("any".equalsIgnoreCase(itemType)) return true;
        var item = player.getUseItem();
        var key = BuiltInRegistries.ITEM.getKey(item.getItem());
        String itemId = key != null ? key.toString() : "";
        if ("bow".equalsIgnoreCase(itemType)) return itemId.contains("bow");
        if ("shield".equalsIgnoreCase(itemType)) return itemId.contains("shield");
        return itemId.contains(itemType.toLowerCase());
    }
}
