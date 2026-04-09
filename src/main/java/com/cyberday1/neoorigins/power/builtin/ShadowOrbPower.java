package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Lets the player place persistent "shadow orbs" (invisible anchors) that apply Darkness
 * to all nearby entities within a configurable radius. Up to maxOrbs can be active at once;
 * placing a new one when at max removes the oldest. Orb positions are stored in PlayerOriginData.
 */
public class ShadowOrbPower extends AbstractActivePower<ShadowOrbPower.Config> {

    public record Config(
        int maxOrbs,
        double radius,
        int cooldownTicks,
        int tickInterval,
        String type
    ) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("max_orbs", 4).forGetter(Config::maxOrbs),
            Codec.DOUBLE.optionalFieldOf("radius", 28.0).forGetter(Config::radius),
            Codec.INT.optionalFieldOf("cooldown_ticks", 100).forGetter(Config::cooldownTicks),
            Codec.INT.optionalFieldOf("tick_interval", 20).forGetter(Config::tickInterval),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        List<BlockPos> orbs = new ArrayList<>(data.getShadowOrbs());
        if (orbs.size() >= config.maxOrbs()) orbs.remove(0);
        orbs.add(player.blockPosition());
        data.setShadowOrbs(orbs);
        return true;
    }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (player.tickCount % config.tickInterval() != 0) return;
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        List<BlockPos> orbs = data.getShadowOrbs();
        if (orbs.isEmpty()) return;

        ServerLevel level = (ServerLevel) player.level();
        var darknessOpt = BuiltInRegistries.MOB_EFFECT.getOptional(ResourceLocation.parse("minecraft:darkness"));
        if (darknessOpt.isEmpty()) return;
        var darkness = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(darknessOpt.get());

        for (BlockPos orbPos : orbs) {
            AABB box = new AABB(orbPos).inflate(config.radius());
            var playerTeam = player.getTeam();
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player)) {
                if (playerTeam != null && entity.isAlliedTo(playerTeam)) continue;
                entity.addEffect(new MobEffectInstance(darkness, 40, 0, true, false));
            }
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        player.getData(OriginAttachments.originData()).setShadowOrbs(List.of());
    }
}
