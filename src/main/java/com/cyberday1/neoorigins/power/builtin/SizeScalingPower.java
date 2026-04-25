package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.pehkui.PehkuiBridge;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.HashSet;
import java.util.Set;

/**
 * Scales the player's visual and collision size using the minecraft:scale attribute.
 * Optionally also scales block/entity interaction reach.
 *
 * JSON fields:
 *   "scale"         (float, default 1.0) — target scale multiplier (0.5 = half size, 2.0 = double)
 *   "modify_reach"  (boolean, default true) — also adjust reach proportionally
 */
public class SizeScalingPower extends PowerType<SizeScalingPower.Config> {

    private static final ResourceLocation MOD_SCALE        = ResourceLocation.fromNamespaceAndPath("neoorigins", "size_scale");
    private static final ResourceLocation MOD_REACH_BLOCK  = ResourceLocation.fromNamespaceAndPath("neoorigins", "size_reach_block");
    private static final ResourceLocation MOD_REACH_ENTITY = ResourceLocation.fromNamespaceAndPath("neoorigins", "size_reach_entity");

    private static final ResourceLocation ATTR_SCALE        = ResourceLocation.fromNamespaceAndPath("minecraft", "generic.scale");
    private static final ResourceLocation ATTR_REACH_BLOCK  = ResourceLocation.fromNamespaceAndPath("minecraft", "player.block_interaction_range");
    private static final ResourceLocation ATTR_REACH_ENTITY = ResourceLocation.fromNamespaceAndPath("minecraft", "player.entity_interaction_range");

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
        // Mirror to Pehkui so other mods querying ScaleType.BASE see the origin scale.
        PehkuiBridge.applyOriginScale(player, config.scale());
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        applyModifiers(player, config, false);
        PehkuiBridge.clearOriginScale(player);
    }

    private void applyModifiers(ServerPlayer player, Config config, boolean add) {
        // scale attribute uses ADD_VALUE: base is 1.0, so delta = (scale - 1.0)
        double scaleDelta = config.scale() - 1.0;
        applyMod(player, ATTR_SCALE, MOD_SCALE, scaleDelta, AttributeModifier.Operation.ADD_VALUE, add);

        if (config.modifyReach()) {
            // reach attributes use ADD_MULTIPLIED_BASE so reach scales proportionally
            applyMod(player, ATTR_REACH_BLOCK,  MOD_REACH_BLOCK,  scaleDelta, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, add);
            applyMod(player, ATTR_REACH_ENTITY, MOD_REACH_ENTITY, scaleDelta, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, add);
        }
    }

    /** Attribute IDs we've already warned about, so missing attrs don't spam logs on every grant. */
    private static final Set<ResourceLocation> WARNED_MISSING_ATTRS = new HashSet<>();

    private static void applyMod(ServerPlayer player, ResourceLocation attrId, ResourceLocation modId,
                                  double amount, AttributeModifier.Operation op, boolean add) {
        var attrOpt = BuiltInRegistries.ATTRIBUTE.getOptional(attrId);
        if (attrOpt.isEmpty()) {
            if (WARNED_MISSING_ATTRS.add(attrId)) {
                NeoOrigins.LOGGER.warn(
                    "SizeScalingPower: attribute '{}' not found in registry — scaling power will skip this attribute. "
                    + "This usually indicates a missing mod or an incompatible MC version.", attrId);
            }
            return;
        }
        AttributeInstance inst = player.getAttribute(BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attrOpt.get()));
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
