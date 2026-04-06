package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public class AttributeModifierPower extends PowerType<AttributeModifierPower.Config> {

    public record Config(
        Identifier attribute,
        double amount,
        AttributeModifier.Operation operation,
        String type
    ) implements PowerConfiguration {

        private static final Codec<AttributeModifier.Operation> OPERATION_CODEC = Codec.STRING.xmap(
            s -> switch (s) {
                case "add_value" -> AttributeModifier.Operation.ADD_VALUE;
                case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                default -> AttributeModifier.Operation.ADD_VALUE;
            },
            op -> switch (op) {
                case ADD_VALUE -> "add_value";
                case ADD_MULTIPLIED_BASE -> "add_multiplied_base";
                case ADD_MULTIPLIED_TOTAL -> "add_multiplied_total";
            }
        );

        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.fieldOf("attribute").forGetter(Config::attribute),
            Codec.DOUBLE.fieldOf("amount").forGetter(Config::amount),
            OPERATION_CODEC.optionalFieldOf("operation", AttributeModifier.Operation.ADD_VALUE).forGetter(Config::operation),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        applyModifier(player, config, true);
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        applyModifier(player, config, false);
    }

    private void applyModifier(ServerPlayer player, Config config, boolean add) {
        // Normalize legacy "generic." prefix (removed in MC 1.21.2+)
        Identifier attrId = config.attribute();
        String path = attrId.getPath();
        if (path.startsWith("generic.")) {
            attrId = Identifier.fromNamespaceAndPath(attrId.getNamespace(), path.substring("generic.".length()));
        }
        var attrHolderOpt = BuiltInRegistries.ATTRIBUTE.get(attrId);
        if (attrHolderOpt.isEmpty()) return;
        var attrHolder = attrHolderOpt.get();
        AttributeInstance instance = player.getAttribute(attrHolder);
        if (instance == null) return;

        Identifier modId = Identifier.fromNamespaceAndPath("neoorigins", "power_" + attrId.getPath());
        if (add) {
            if (instance.getModifier(modId) == null) {
                instance.addPermanentModifier(new AttributeModifier(modId, config.amount(), config.operation()));
            }
        } else {
            instance.removeModifier(modId);
        }
    }
}
