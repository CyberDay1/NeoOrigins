package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
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
 *   "reach_bonus"   (float, default 0.0) — flat reach added to BOTH block and
 *                    entity interaction range, on top of any proportional
 *                    modify_reach scaling. Lets a size origin keep usable reach
 *                    even when shrunk (e.g. inchling) without hand-tuning a
 *                    separate attribute_modifier power. Exposed per-origin in
 *                    power_overrides.toml so server owners can retune reach
 *                    without a datapack.
 *
 * To change reach independently of body size, use the generic
 * {@code attribute_modifier} power on {@code minecraft:block_interaction_range}
 * / {@code minecraft:entity_interaction_range}.
 */
public class SizeScalingPower extends PowerType<SizeScalingPower.Config> {

    /** Shared leading segment of every modifier id this power type attaches. */
    static final String MOD_ID_PREFIX = "size_";

    /** Generates a per-power modifier ID so multiple size_scaling powers don't collide. */
    private static Identifier modId(String suffix) {
        return modIdFor(PowerHolder.currentDispatchId(), suffix);
    }

    static Identifier modIdFor(Identifier powerId, String suffix) {
        return Identifier.fromNamespaceAndPath("neoorigins", ownIdPrefix(powerId) + suffix);
    }

    /**
     * Id prefix shared by every modifier {@code powerId} owns. The key segment is
     * derived by {@link AttributeModifierPower#powerKeyFor} rather than locally, so
     * the layer-change sweeper in that class cannot drift out of step with the ids
     * emitted here and start treating live modifiers as orphans.
     */
    static String ownIdPrefix(Identifier powerId) {
        return MOD_ID_PREFIX + AttributeModifierPower.powerKeyFor(powerId) + "_";
    }

    public record Config(float scale, boolean modifyReach, float reachBonus, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("scale", 1.0f).forGetter(Config::scale),
            Codec.BOOL.optionalFieldOf("modify_reach", true).forGetter(Config::modifyReach),
            Codec.FLOAT.optionalFieldOf("reach_bonus", 0.0f).forGetter(Config::reachBonus),
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
        clearOwnModifiers(player, PowerHolder.currentDispatchId());
    }

    /**
     * Removes the scale and interaction-range modifiers owned by this power, and
     * only those. Clearing every {@code neoorigins:size_*} id here instead also
     * erased the size modifiers granted by powers on the OTHER layers, and the
     * caller only re-grants the layer it changed: an origin and a class that both
     * scale the player therefore collapsed to whichever one was applied last
     * rather than stacking.
     *
     * <p>The orphan case this hook cannot see is still covered. If a power's JSON
     * is deleted or renamed the holder no longer resolves and this hook never runs
     * at all, so the layer-change sweep in
     * {@code ActiveOriginService.applyOriginPowers} removes every {@code size_*}
     * modifier whose owning power is no longer active anywhere. That sweep, not a
     * blanket wipe here, is what keeps GitHub #90 from coming back.
     */
    private static void clearOwnModifiers(ServerPlayer player, Identifier powerId) {
        String prefix = ownIdPrefix(powerId);
        clearPrefixed(player, Attributes.SCALE, prefix);
        clearPrefixed(player, Attributes.BLOCK_INTERACTION_RANGE, prefix);
        clearPrefixed(player, Attributes.ENTITY_INTERACTION_RANGE, prefix);
    }

    private static void clearPrefixed(ServerPlayer player,
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
            String prefix) {
        clearPrefixed(player.getAttribute(attr), prefix);
    }

    /** Removes every modifier on {@code inst} whose id starts with {@code prefix}. */
    static void clearPrefixed(AttributeInstance inst, String prefix) {
        if (inst == null) return;
        for (AttributeModifier mod : new java.util.ArrayList<>(inst.getModifiers())) {
            Identifier id = mod.id();
            if ("neoorigins".equals(id.getNamespace()) && id.getPath().startsWith(prefix)) {
                inst.removeModifier(id);
            }
        }
    }

    private void applyModifiers(ServerPlayer player, Config config, boolean add) {
        // scale attribute uses ADD_VALUE: base is 1.0, so delta = (scale - 1.0)
        double scaleDelta = config.scale() - 1.0;
        Identifier scaleId = modId("scale");
        Identifier reachBlockId = modId("reach_block");
        Identifier reachEntityId = modId("reach_entity");
        applyMod(player, Attributes.SCALE, scaleId, scaleDelta, AttributeModifier.Operation.ADD_VALUE, add);

        if (config.modifyReach()) {
            // reach attributes use ADD_MULTIPLIED_BASE so reach scales proportionally
            applyMod(player, Attributes.BLOCK_INTERACTION_RANGE,  reachBlockId,  scaleDelta, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, add);
            applyMod(player, Attributes.ENTITY_INTERACTION_RANGE, reachEntityId, scaleDelta, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, add);
        }

        // Flat reach bonus on top of any proportional scaling — ADD_VALUE on
        // both ranges. Skipped when zero so we don't register no-op modifiers.
        if (config.reachBonus() != 0.0f) {
            applyMod(player, Attributes.BLOCK_INTERACTION_RANGE,  modId("reach_bonus_block"),  config.reachBonus(), AttributeModifier.Operation.ADD_VALUE, add);
            applyMod(player, Attributes.ENTITY_INTERACTION_RANGE, modId("reach_bonus_entity"), config.reachBonus(), AttributeModifier.Operation.ADD_VALUE, add);
        }
    }

    private static void applyMod(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
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
