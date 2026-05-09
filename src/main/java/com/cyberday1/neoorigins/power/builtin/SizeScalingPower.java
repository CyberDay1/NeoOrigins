package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Scales the player's visual and collision size using the minecraft:scale attribute.
 * Optionally also scales block/entity interaction reach.
 *
 * JSON fields:
 *   "scale"         (float, default 1.0) — target scale multiplier (0.5 = half size, 2.0 = double)
 *   "modify_reach"  (boolean, default true) — also adjust reach proportionally
 */
public class SizeScalingPower extends PowerType<SizeScalingPower.Config> {

    /**
     * Generate a modifier ID incorporating the power's dispatch type to avoid
     * collisions when multiple SizeScalingPower instances exist (e.g., from
     * different origins/layers with different scale values).
     */
    private static Identifier modId(Config config, String suffix) {
        // Use the power's type field (e.g. "origins:fairy_size") to namespace the modifier.
        // Fall back to class name if type is empty.
        String base = (config.type() != null && !config.type().isEmpty())
            ? config.type().replace(':', '_')
            : "size_scaling";
        // Ensure valid Identifier path characters
        String path = (base + "_" + suffix).replaceAll("[^a-z0-9_./\\-]", "_");
        return Identifier.fromNamespaceAndPath("neoorigins", path);
    }

    public record Config(float scale, boolean modifyReach, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("scale", 1.0f).forGetter(Config::scale),
            Codec.BOOL.optionalFieldOf("modify_reach", true).forGetter(Config::modifyReach),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        applyModifiers(player, config, true);
        // On 1.20.5+ the vanilla minecraft:scale attribute is authoritative and
        // Pehkui reads it directly. Mirroring to Pehkui's BASE on top of the
        // vanilla attribute caused double-scaling (1.2 * 1.2 = 1.44x).
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        applyModifiers(player, config, false);
    }

    private void applyModifiers(ServerPlayer player, Config config, boolean add) {
        // scale attribute uses ADD_VALUE: base is 1.0, so delta = (scale - 1.0)
        double scaleDelta = config.scale() - 1.0;
        applyMod(player, Attributes.SCALE, modId(config, "scale"), scaleDelta, AttributeModifier.Operation.ADD_VALUE, add);

        if (config.modifyReach()) {
            // reach attributes use ADD_MULTIPLIED_BASE so reach scales proportionally
            applyMod(player, Attributes.BLOCK_INTERACTION_RANGE,  modId(config, "reach_block"),  scaleDelta, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, add);
            applyMod(player, Attributes.ENTITY_INTERACTION_RANGE, modId(config, "reach_entity"), scaleDelta, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, add);
        }
    }

    private static void applyMod(ServerPlayer player, net.minecraft.core.Holder<Attribute> attr,
                                  Identifier modId, double amount, AttributeModifier.Operation op, boolean add) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;
        if (add) {
            if (inst.getModifier(modId) == null) {
                inst.addPermanentModifier(new AttributeModifier(modId, amount, op));
            }
        } else {
            inst.removeModifier(modId);
        }
    }
}
