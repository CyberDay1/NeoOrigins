package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ActiveDashPower extends AbstractActivePower<ActiveDashPower.Config> {

    public record Config(
        float power,
        int cooldownTicks,
        boolean allowVertical,
        boolean setVelocity,
        float damage,
        float damageRadius,
        float weaponDamageScale,
        String type,
        String cooldownIcon,
        boolean cooldownCountdown,
        boolean alwaysShowIcon
    ) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("power", 1.5f).forGetter(Config::power),
            Codec.INT.optionalFieldOf("cooldown_ticks", 40).forGetter(Config::cooldownTicks),
            Codec.BOOL.optionalFieldOf("allow_vertical", false).forGetter(Config::allowVertical),
            Codec.BOOL.optionalFieldOf("set_velocity", false).forGetter(Config::setVelocity),
            Codec.FLOAT.optionalFieldOf("damage", 0f).forGetter(Config::damage),
            Codec.FLOAT.optionalFieldOf("damage_radius", 2.0f).forGetter(Config::damageRadius),
            Codec.FLOAT.optionalFieldOf("weapon_damage_scale", 0f).forGetter(Config::weaponDamageScale),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type),
            Codec.STRING.optionalFieldOf("cooldown_icon", "").forGetter(Config::cooldownIcon),
            Codec.BOOL.optionalFieldOf("cooldown_countdown", true).forGetter(Config::cooldownCountdown),
            Codec.BOOL.optionalFieldOf("always_show_icon", false).forGetter(Config::alwaysShowIcon)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        Vec3 look = player.getLookAngle();
        Vec3 dash = config.allowVertical()
            ? look.scale(config.power())
            : new Vec3(look.x, 0.2, look.z).normalize().scale(config.power());
        if (config.setVelocity()) {
            player.setDeltaMovement(dash);
        } else {
            player.setDeltaMovement(player.getDeltaMovement().add(dash));
        }
        player.hurtMarked = true;

        if (config.damage() > 0f || config.weaponDamageScale() > 0f) {
            dealPathDamage(player, config, dash);
        }
        return true;
    }

    /**
     * Damage every living entity within {@code damage_radius} of the dash path.
     * The dash itself is an instantaneous velocity push, so we approximate the
     * swept volume as the segment from the player's mid-height out along the
     * dash vector and hit anything whose centre is close to that segment.
     */
    private void dealPathDamage(ServerPlayer player, Config config, Vec3 dash) {
        Vec3 start = player.position().add(0, player.getBbHeight() * 0.5, 0);
        Vec3 dir = dash.lengthSqr() < 1.0e-6 ? player.getLookAngle() : dash.normalize();
        double length = Math.max(1.0, dash.length());
        Vec3 end = start.add(dir.scale(length));
        float radius = config.damageRadius();

        float damage = config.damage();
        if (config.weaponDamageScale() > 0f) {
            double atk = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            damage += (float) (config.weaponDamageScale() * atk);
        }

        AABB box = new AABB(start, end).inflate(radius + 1.0);
        var source = player.damageSources().playerAttack(player);
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (target == player) continue;
            Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
            if (distanceToSegment(c, start, end) <= radius) {
                target.hurt(source, damage);
            }
        }
    }

    /** Shortest distance from point {@code p} to the segment [a, b]. */
    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double abLenSq = ab.lengthSqr();
        double t = abLenSq < 1.0e-6 ? 0.0 : p.subtract(a).dot(ab) / abLenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        return p.distanceTo(a.add(ab.scale(t)));
    }
}
