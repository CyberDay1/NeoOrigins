package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Slime Death Save — when the player would die with >75% moisture, they
 * "split" instead: teleported 50 blocks in a random direction (±10 Y),
 * max HP set to 4 (2 hearts), which regenerates back to normal over 2 minutes.
 *
 * <p>This power hooks into the damage pipeline via the
 * {@link #shouldPreventDeath} method, checked by the damage event handler.
 */
public class SlimeDeathSavePower extends PowerType<SlimeDeathSavePower.Config> {

    private static final String SPLIT_KEY = "slime_split_active";
    private static final ResourceLocation SPLIT_HP_MOD =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "slime_split_hp_reduction");

    public record Config(
        float moistureThreshold,
        int teleportDistance,
        int teleportYRange,
        float splitMaxHP,
        int recoveryTicks,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("moisture_threshold", 0.75F).forGetter(Config::moistureThreshold),
            Codec.INT.optionalFieldOf("teleport_distance", 50).forGetter(Config::teleportDistance),
            Codec.INT.optionalFieldOf("teleport_y_range", 10).forGetter(Config::teleportYRange),
            Codec.FLOAT.optionalFieldOf("split_max_hp", 4.0F).forGetter(Config::splitMaxHP),
            Codec.INT.optionalFieldOf("recovery_ticks", 2400).forGetter(Config::recoveryTicks),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    /**
     * Called from the damage event handler when the player would die.
     * Returns true if the death was prevented (split triggered).
     */
    public static boolean shouldPreventDeath(ServerPlayer player, SlimeDeathSavePower.Config config) {
        float moisture = SlimeMoisturePower.getMoisture(player);
        if (moisture < config.moistureThreshold()) return false;

        // Teleport to a random location
        var random = player.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        int dx = (int) (Math.cos(angle) * config.teleportDistance());
        int dz = (int) (Math.sin(angle) * config.teleportDistance());
        int dy = random.nextIntBetweenInclusive(-config.teleportYRange(), config.teleportYRange());

        double newX = player.getX() + dx;
        double newZ = player.getZ() + dz;
        double newY = Math.max(player.level().getMinBuildHeight() + 1,
                     Math.min(player.getY() + dy, player.level().getMaxBuildHeight() - 1));

        // Find a safe Y at the target XZ
        BlockPos target = BlockPos.containing(newX, newY, newZ);
        player.level().getChunk(target.getX() >> 4, target.getZ() >> 4);
        // Scan down for solid ground
        for (int y = target.getY(); y > player.level().getMinBuildHeight(); y--) {
            BlockPos check = new BlockPos(target.getX(), y, target.getZ());
            if (player.level().getBlockState(check).isSolid()
                && player.level().getBlockState(check.above()).isAir()
                && player.level().getBlockState(check.above(2)).isAir()) {
                newY = y + 1;
                break;
            }
        }

        TeleportEffects.teleportWithEffects(player, newX, newY, newZ);

        // Set HP to split max and apply the HP reduction modifier
        float normalMax = (float) player.getAttributeValue(Attributes.MAX_HEALTH);
        float reduction = -(normalMax - config.splitMaxHP());
        var maxHpAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHpAttr != null) {
            maxHpAttr.removeModifier(SPLIT_HP_MOD);
            maxHpAttr.addTransientModifier(new AttributeModifier(
                SPLIT_HP_MOD, reduction, AttributeModifier.Operation.ADD_VALUE));
        }
        player.setHealth(config.splitMaxHP());

        // Store the split state — remaining recovery ticks and total reduction.
        // We store remaining ticks (not a start tickCount) so recovery survives
        // relogs and avoids float precision loss on large tick values.
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        data.setCustomFloat(SPLIT_KEY, (float) config.recoveryTicks());
        data.setCustomFloat("slime_split_reduction", reduction);

        return true;
    }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        float remaining = data.getCustomFloat(SPLIT_KEY, -1);
        if (remaining < 0) return;

        float totalReduction = data.getCustomFloat("slime_split_reduction", 0);

        remaining--;
        if (remaining <= 0 || totalReduction >= 0) {
            // Recovery complete — remove the modifier
            var maxHpAttr = player.getAttribute(Attributes.MAX_HEALTH);
            if (maxHpAttr != null) maxHpAttr.removeModifier(SPLIT_HP_MOD);
            data.setCustomFloat(SPLIT_KEY, -1);
            data.setCustomFloat("slime_split_reduction", 0);
        } else {
            data.setCustomFloat(SPLIT_KEY, remaining);
            // Gradually ease the reduction toward 0
            float progress = 1.0F - (remaining / config.recoveryTicks());
            float currentReduction = totalReduction * (1.0F - progress);
            var maxHpAttr = player.getAttribute(Attributes.MAX_HEALTH);
            if (maxHpAttr != null) {
                maxHpAttr.removeModifier(SPLIT_HP_MOD);
                maxHpAttr.addTransientModifier(new AttributeModifier(
                    SPLIT_HP_MOD, currentReduction, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        var maxHpAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHpAttr != null) maxHpAttr.removeModifier(SPLIT_HP_MOD);
    }
}
