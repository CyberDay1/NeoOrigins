package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Multiplies the player's mining speed via the vanilla
 * {@code minecraft:player.block_break_speed} attribute. Combined multiplier
 * across all active break_speed_modifier powers is recomputed on grant/revoke
 * and applied as a single ADD_MULTIPLIED_TOTAL modifier so multiple powers
 * stack correctly (1.5x * 2.0x = 3.0x effective).
 *
 * Attribute-based instead of PlayerEvent.BreakSpeed because that event fires
 * client-side for the local player, where ServerPlayer-gated handlers never
 * see it. Attribute modifiers sync server→client automatically.
 *
 * Note: block_tag filtering was removed in v1.10.x — attributes apply to all
 * blocks. Only one power (Stoneguard's Stonecrusher) used the filter; it was
 * rebalanced to a smaller global multiplier.
 */
public class BreakSpeedModifierPower extends PowerType<BreakSpeedModifierPower.Config> {

    private static final ResourceLocation MODIFIER_ID =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "break_speed_combined");

    public record Config(float multiplier, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("multiplier", 2.0f).forGetter(Config::multiplier),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        refreshModifier(player);
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        refreshModifier(player);
    }

    /**
     * Recomputes the combined break-speed multiplier from all active
     * break_speed_modifier powers and applies it as a single attribute modifier.
     * Called on grant/revoke (and respawn via the default onRespawn → onGranted).
     */
    private static void refreshModifier(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        if (instance == null) {
            NeoOrigins.LOGGER.warn(
                "[break_speed_modifier] player {} has no BLOCK_BREAK_SPEED attribute — modifier cannot apply. "
                + "This usually means the power was granted before attributes were registered. "
                + "Fires once per grant attempt.",
                player.getGameProfile().getName());
            return;
        }

        instance.removeModifier(MODIFIER_ID);

        double[] product = {1.0};
        ActiveOriginService.forEachOfType(player, BreakSpeedModifierPower.class, cfg -> {
            // Skip poison values from authored datapacks so one bad power
            // can't corrupt the combined modifier for every other one.
            float m = cfg.multiplier();
            if (Float.isFinite(m) && m >= 0.0f) product[0] *= m;
        });

        if (product[0] != 1.0 && Double.isFinite(product[0])) {
            // ADD_MULTIPLIED_TOTAL: applied as `* (1 + value)` at the final stage,
            // so a 2.0x power contributes value=1.0 and stacks multiplicatively.
            instance.addPermanentModifier(new AttributeModifier(
                MODIFIER_ID, product[0] - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    // Safety net: re-apply the modifier periodically. The attribute is NOT on
    // LivingEntity's default supplier for older NeoForge builds, and there
    // have been reports of Miner's Hands appearing inert (#29). This self-
    // heals any timing issue without being meaningfully expensive.
    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (player.tickCount % 40 != 0) return;
        AttributeInstance instance = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        if (instance == null) return;
        if (instance.getModifier(MODIFIER_ID) == null) refreshModifier(player);
    }
}
