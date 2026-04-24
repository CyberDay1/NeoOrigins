package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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
        BlockPos pos = player.blockPosition();
        orbs.add(pos);
        data.setShadowOrbs(orbs);
        // Placement feedback — Darkness is subtle, give the player something
        // tangible on activation. Particles at eye level; a short low-pitched
        // soul-sand cue so the keybind clearly fires.
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
            pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 20, 0.5, 0.5, 0.5, 0.02);
        level.playSound(null, pos,
            net.minecraft.sounds.SoundEvents.SOUL_ESCAPE.value(),
            net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.6F);
        return true;
    }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        List<BlockPos> orbs = data.getShadowOrbs();
        if (orbs.isEmpty()) return;

        ServerLevel level = (ServerLevel) player.level();

        // Orb visual — spawn a sculk soul particle at each orb every 3 ticks
        // so the anchor is visible. Previously the orb was invisible after
        // placement and testers couldn't tell where it was.
        if (player.tickCount % 3 == 0) {
            for (BlockPos orbPos : orbs) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                    orbPos.getX() + 0.5, orbPos.getY() + 1.0, orbPos.getZ() + 0.5,
                    2, 0.15, 0.15, 0.15, 0.005);
            }
        }

        if (player.tickCount % config.tickInterval() != 0) return;

        var reg = BuiltInRegistries.MOB_EFFECT;
        var darkness = reg.get(Identifier.parse("minecraft:darkness")).orElse(null);
        var blindness = reg.get(Identifier.parse("minecraft:blindness")).orElse(null);
        if (darkness == null) return;

        int duration = config.tickInterval() * 2;
        for (BlockPos orbPos : orbs) {
            AABB box = new AABB(orbPos).inflate(config.radius());
            var playerTeam = player.getTeam();
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player)) {
                if (playerTeam != null && entity.isAlliedTo(playerTeam)) continue;
                // Darkness for the screen pulse, Blindness for the actual gameplay
                // impact (vision limited to 1 block). Without Blindness the
                // Darkness effect alone is too subtle for pack authors to notice.
                entity.addEffect(new MobEffectInstance(darkness, duration, 0, true, false));
                if (blindness != null) {
                    entity.addEffect(new MobEffectInstance(blindness, duration, 0, true, false));
                }
            }
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        player.getData(OriginAttachments.originData()).setShadowOrbs(List.of());
    }
}
